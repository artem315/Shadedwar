package com.fullfud.fullfud.client.vfx;

import com.fullfud.fullfud.FullfudMod;
import com.fullfud.fullfud.core.config.FullfudClientConfig;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import com.mojang.blaze3d.pipeline.TextureTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterShadersEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.TickEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.List;

/**
 * Mod-owned VFX renderer.
 *
 * <p>The pipeline intentionally does not use a packed 0..15 light value for
 * FP-5.  It registers small GLSL programs, uploads a floating point light
 * field, and draws local light volumes plus contact shadows.  The FP-5
 * exhaust trail itself is particle-driven (DroneParticleManager legacy
 * plume); this pipeline only lights the scene and the particles.  The scene
 * lighting pass reconstructs positions from the main target depth buffer and
 * adds the same custom light field back onto visible terrain/entities.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class VfxRenderPipeline {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation VOLUME_TEXTURE = new ResourceLocation(
        "mtsofficialpack",
        "textures/bullets/flash64_translucent.png"
    );

    private static ShaderInstance volumeShader;
    private static ShaderInstance sceneLightingShader;
    private static ShaderInstance shadowShader;
    private static ShaderInstance emissiveShader;
    private static TextureTarget sceneTarget;
    // Reusable per-frame ribbon scratch buffers (camera-relative).
    private static float[] ribbonX = new float[VfxTrailManager.MAX_SAMPLES + 2];
    private static float[] ribbonY = new float[VfxTrailManager.MAX_SAMPLES + 2];
    private static float[] ribbonZ = new float[VfxTrailManager.MAX_SAMPLES + 2];
    private static float[] ribbonGlow = new float[VfxTrailManager.MAX_SAMPLES + 2];
    private static float[] ribbonHot = new float[VfxTrailManager.MAX_SAMPLES + 2];
    private static boolean initialized;
    private static boolean loggedRenderFailure;
    private static long startNanos;
    private static long nextDebugLogNanos;
    private static float nightBoost = 1.0F;
    private static float dayFactor = 1.0F;
    private static float nightAmount = 0.0F;

    private VfxRenderPipeline() {
    }

    public static void registerShaders(final RegisterShadersEvent event) {
        try {
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    new ResourceLocation(FullfudMod.MOD_ID, "vfx_volume"),
                    DefaultVertexFormat.POSITION_COLOR_TEX
                ),
                shader -> volumeShader = shader
            );
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    new ResourceLocation(FullfudMod.MOD_ID, "vfx_scene_lighting"),
                    DefaultVertexFormat.POSITION_TEX
                ),
                shader -> sceneLightingShader = shader
            );
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    new ResourceLocation(FullfudMod.MOD_ID, "vfx_shadow"),
                    DefaultVertexFormat.POSITION_COLOR_TEX
                ),
                shader -> shadowShader = shader
            );
            event.registerShader(
                new ShaderInstance(
                    event.getResourceProvider(),
                    new ResourceLocation(FullfudMod.MOD_ID, "vfx_emissive"),
                    DefaultVertexFormat.POSITION_COLOR_TEX
                ),
                shader -> emissiveShader = shader
            );
        } catch (IOException exception) {
            LOGGER.error("[FULLFUD] Could not register custom VFX shaders; legacy particle renderer will be used", exception);
        }
    }

    public static void init() {
        if (initialized) {
            return;
        }
        initialized = true;
        startNanos = System.nanoTime();
        MinecraftForge.EVENT_BUS.addListener(VfxRenderPipeline::onClientTick);
        MinecraftForge.EVENT_BUS.addListener(VfxRenderPipeline::onRenderLevelStage);
        MinecraftForge.EVENT_BUS.addListener(VfxRenderPipeline::onLoggingOut);
        MinecraftForge.EVENT_BUS.addListener(VfxRenderPipeline::onLevelUnload);
    }

    /**
     * Returns whether the mod-owned custom-lighting programs are available.
     * Kept separate from the config flag so the legacy particle path can act
     * as a safe fallback when shader registration fails (for example after a
     * resource-pack error).
     */
    public static boolean isTrailRendererReady() {
        return sceneLightingShader != null;
    }

    public static void clear() {
        VfxTrailManager.clear();
        VfxLightingRegistry.clear();
        if (sceneTarget != null) {
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
        loggedRenderFailure = false;
        nextDebugLogNanos = 0L;
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() != null && event.getLevel().isClientSide()) {
            clear();
        }
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        VfxTrailManager.tick();
        VfxLightingRegistry.tick();
        updateDayNight();
    }

    /**
     * Custom lights must actually win against a dark night sky, otherwise an
     * explosion looks like a decal instead of a light source.  The boost is
     * derived from the world clock and weather so the same detonation is bright
     * at 02:00 and stays physical at noon.
     */
    private static void updateDayNight() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            nightBoost = 1.0F;
            dayFactor = 1.0F;
            return;
        }
        final long dayTime = Math.floorMod(minecraft.level.getDayTime(), 24000L);
        float night;
        if (dayTime >= 13000L && dayTime <= 23000L) {
            night = 1.0F;
        } else if (dayTime > 23000L) {
            night = Math.max(0.0F, (24000.0F - (float) dayTime) / 1000.0F);
        } else if (dayTime >= 12000L) {
            night = Math.max(0.0F, ((float) dayTime - 12000.0F) / 1000.0F);
        } else {
            night = 0.0F;
        }
        try {
            // Weather and dimension ambient darken the scene the same way the
            // sky does, so the boost tracks them instead of assuming clear noon.
            final float rain = minecraft.level.getRainLevel(1.0F);
            final float thunder = minecraft.level.getThunderLevel(1.0F);
            final float ambient = minecraft.level.dimensionType().ambientLight();
            night = Math.max(night, Math.min(0.65F, rain * 0.35F + thunder * 0.5F));
            night = Math.max(night, Mth.clamp(ambient * 4.0F, 0.0F, 0.8F));
        } catch (Throwable ignored) {
            // Headless tests and unusual dimensions: fall back to the clock.
        }
        nightBoost = 1.0F + night * 1.85F;
        dayFactor = 1.0F - night * 0.72F;
        nightAmount = Mth.clamp(night, 0.0F, 1.0F);
    }

    public static float nightBoost() {
        return nightBoost;
    }

    public static float dayFactor() {
        return dayFactor;
    }

    public static float nightAmount() {
        return nightAmount;
    }

    private static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        final FullfudClientConfig.Client config = FullfudClientConfig.CLIENT;
        if (!config.vfxEnabled.get()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        final Camera camera = event.getCamera();
        if (camera == null) {
            return;
        }
        final List<VfxLightingRegistry.Light> lights = VfxLightingRegistry.snapshot(
            camera.getPosition(),
            resolveLightBudget(config, minecraft)
        );
        if (lights.isEmpty() && VfxTrailManager.activeTrailCount() == 0) {
            return;
        }

        if (config.vfxDebug.get()) {
            final long now = System.nanoTime();
            if (now >= nextDebugLogNanos) {
                LOGGER.info(
                    "[FULLFUD] VFX debug: lights={}, trails={}, quality={}, sceneShader={}",
                    lights.size(),
                    VfxTrailManager.activeTrailCount(),
                    config.vfxQuality.get(),
                    sceneLightingShader != null
                );
                nextDebugLogNanos = now + 5_000_000_000L;
            }
        }

        try {
            renderSceneLighting(event, camera, lights, config);
            if (config.vfxShadowsEnabled.get()) {
                renderContactShadows(event, camera, lights);
            }
            if (config.vfxVolumetricEnabled.get()) {
                renderLightVolumes(event, camera, lights);
            }
            // Emitters last: they are the light sources themselves and must
            // not be darkened or re-lit by the scene pass.
            renderEmissive(event, camera, config);
        } catch (Throwable throwable) {
            if (!loggedRenderFailure) {
                loggedRenderFailure = true;
                LOGGER.warn("[FULLFUD] Custom VFX render pass failed; using legacy particle path", throwable);
            }
        }
    }

    private static void ensureSceneTarget(final Minecraft minecraft) {
        final int width = minecraft.getMainRenderTarget().width;
        final int height = minecraft.getMainRenderTarget().height;
        if (sceneTarget != null && sceneTarget.width == width && sceneTarget.height == height) {
            return;
        }
        if (sceneTarget != null) {
            sceneTarget.destroyBuffers();
        }
        try {
            // useDepth MUST be true.  RenderTarget.createBuffers skips depth
            // attachment creation entirely when useDepth is false, so the
            // depth copy and DepthSampler would silently read an unallocated
            // texture (id -1) and every lighting result would be garbage.
            sceneTarget = new TextureTarget(width, height, true, true);
            sceneTarget.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
        } catch (Throwable throwable) {
            sceneTarget = null;
            if (!loggedRenderFailure) {
                LOGGER.warn("[FULLFUD] Could not allocate the VFX scene target; custom scene lighting is disabled", throwable);
            }
        }
    }

    private static int resolveLightBudget(final FullfudClientConfig.Client config, final Minecraft minecraft) {
        final int configured = Mth.clamp(config.vfxMaxLights.get(), 1, VfxLightingRegistry.SHADER_LIGHT_SLOTS);
        if (config.vfxQuality.get() == FullfudClientConfig.VfxQuality.LOW) {
            return Math.min(configured, 4);
        }
        if (config.vfxQuality.get() == FullfudClientConfig.VfxQuality.AUTO && minecraft.getFps() < 45) {
            return Math.min(configured, 4);
        }
        return configured;
    }

    private static void renderSceneLighting(
        final RenderLevelStageEvent event,
        final Camera camera,
        final List<VfxLightingRegistry.Light> lights,
        final FullfudClientConfig.Client config
    ) {
        if (!config.vfxCustomLightingEnabled.get() || sceneLightingShader == null || lights.isEmpty()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final int depthTexture = minecraft.getMainRenderTarget().getDepthTextureId();
        if (depthTexture <= 0) {
            return;
        }

        // At AFTER_PARTICLES the camera rotation lives in the event PoseStack;
        // RenderSystem's model-view is ~identity there.  Using the latter made
        // every reconstructed position view-space while lights were world
        // space, so N.L, falloff and shadows were computed against the wrong
        // points.  Everything is now camera-relative: rotation-only view
        // matrix, lights offset by the camera, CameraPos = origin.
        final Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        final Matrix4f view = new Matrix4f(event.getPoseStack().last().pose());
        // JOML invert() mutates in place, so invert copies.
        final Matrix4f inverseProjection = new Matrix4f(projection).invert();
        final Matrix4f inverseView = new Matrix4f(view).invert();
        if (inverseProjection == null || inverseView == null) {
            return;
        }

        ensureSceneTarget(minecraft);
        if (sceneTarget == null) {
            return;
        }
        if (!sceneTarget.useDepth) {
            // Defensive guard: a colour-only target silently breaks the depth
            // reconstruction instead of failing loudly, so bail out here.
            return;
        }
        // Keep the depth copy isolated from the active framebuffer.  Reading
        // and writing the same target would be undefined on some drivers.
        final com.mojang.blaze3d.pipeline.RenderTarget mainTarget = minecraft.getMainRenderTarget();
        sceneTarget.copyDepthFrom(mainTarget);
        // The colour copy is what makes this a real diffuse relight: the shader
        // multiplies light by the surface colour and writes the final pixel,
        // rather than blending a glow over a frame it cannot read.
        copyColor(mainTarget, sceneTarget);
        // RenderTarget.copyDepthFrom deliberately leaves the default
        // framebuffer bound.  The contribution must still be composited into
        // Minecraft's main target, not into an offscreen/default FBO.
        mainTarget.bindWrite(false);
        final int sceneDepthTexture = sceneTarget.getDepthTextureId();
        if (sceneDepthTexture <= 0) {
            // Null depth must disable the pass instead of binding texture -1 and
            // producing a full-screen garbage light reconstruction.
            return;
        }
        sceneLightingShader.setSampler("DepthSampler", sceneDepthTexture);
        sceneLightingShader.setSampler("SceneSampler", sceneTarget.getColorTextureId());
        setMatrix(sceneLightingShader, "ProjectionMat", projection);
        setMatrix(sceneLightingShader, "ViewMat", view);
        setMatrix(sceneLightingShader, "InvProjectionMat", inverseProjection);
        setMatrix(sceneLightingShader, "InvViewMat", inverseView);
        setVector3(sceneLightingShader, "CameraPos", 0.0F, 0.0F, 0.0F);
        final float ambientSetting = config.vfxAmbientStrength.get().floatValue();
        setFloat(sceneLightingShader, "AmbientStrength", ambientSetting);
        setFloat(sceneLightingShader, "NightBoost", nightBoost);
        setFloat(sceneLightingShader, "NightAmount", nightAmount);
        // AmbientFloor: how dark the vanilla night ambient gets INSIDE a light's
        // footprint (outside it the frame is untouched, so there is no global
        // pop).  0.7 (default) -> 0.62 gives the deep unlit corners of the
        // reference; higher config values soften it, 1.0 disables it.
        final float ambientFloor = Mth.clamp(0.62F + (ambientSetting - 0.7F) * 0.60F, 0.35F, 1.0F);
        setFloat(sceneLightingShader, "AmbientFloor", ambientFloor);
        setFloat2(
            sceneLightingShader,
            "TexelSize",
            1.0F / Math.max(1.0F, (float) sceneTarget.width),
            1.0F / Math.max(1.0F, (float) sceneTarget.height)
        );
        if (!config.vfxBloomEnabled.get()) {
            setFloat(sceneLightingShader, "BloomStrength", 0.0F);
        } else {
            setFloat(sceneLightingShader, "BloomStrength", 1.0F);
        }
        setInt(sceneLightingShader, "LightCount", Math.min(lights.size(), VfxLightingRegistry.SHADER_LIGHT_SLOTS));
        setCameraRelativeLightUniforms(sceneLightingShader, lights, camera.getPosition());
        setFloat(sceneLightingShader, "SceneContribution", sceneContribution(config));

        // Single composite pass: the shader reads the copied frame and writes
        // final = frame * ambient * AO * shadow + albedo * diffuse + bloom.
        // Blending is off because the full result is computed in the shader
        // (a blend-based multiply/add could not modulate light by albedo, and
        // UNORM targets clamp blend factors to 1).  Pixels no light reaches
        // are discarded and keep the vanilla frame exactly.
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        // The fullscreen light contribution must never write its quad depth
        // into the main target; doing so corrupts later world/particles.
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> sceneLightingShader);
        try {
            // The vertex shader emits clip-space positions directly, so no
            // projection swap is needed.
            drawFullscreenQuad();
        } finally {
            // copyDepthFrom and the screen quad can both change FBO state;
            // leave the level renderer on the main target in every path.
            minecraft.getMainRenderTarget().bindWrite(false);
            RenderSystem.depthMask(true);
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
    }

    /**
     * Blits the colour attachment of {@code source} into {@code destination}.
     * Mirrors {@code RenderTarget.copyDepthFrom}, which only copies depth.
     */
    private static void copyColor(
        final com.mojang.blaze3d.pipeline.RenderTarget source,
        final com.mojang.blaze3d.pipeline.RenderTarget destination
    ) {
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, destination.frameBufferId);
        GlStateManager._glBlitFrameBuffer(
            0, 0, source.width, source.height,
            0, 0, destination.width, destination.height,
            GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST
        );
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    private static void drawFullscreenQuad() {
        final BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(-1.0D, -1.0D, 0.0D).uv(0.0F, 0.0F).endVertex();
        builder.vertex(-1.0D, 1.0D, 0.0D).uv(0.0F, 1.0F).endVertex();
        builder.vertex(1.0D, 1.0D, 0.0D).uv(1.0F, 1.0F).endVertex();
        builder.vertex(1.0D, -1.0D, 0.0D).uv(1.0F, 0.0F).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }

    private static float sceneContribution(final FullfudClientConfig.Client config) {
        // The Forge-only pass is an additive HDR contribution layered over
        // the already-rendered vanilla frame.  It cannot literally replace
        // the vanilla framebuffer, so keep the modes as calibrated strengths
        // instead of silently disabling the custom light field.
        return switch (config.vfxLightingMode.get()) {
            case CUSTOM_REPLACE -> 1.0F;
            case CUSTOM_PLUS -> 0.72F;
            case CUSTOM_ONLY -> 1.35F;
        };
    }

    private static void renderContactShadows(
        final RenderLevelStageEvent event,
        final Camera camera,
        final List<VfxLightingRegistry.Light> lights
    ) {
        if (shadowShader == null || lights.isEmpty()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final Vec3 cameraPosition = camera.getPosition();
        final BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        boolean hasGeometry = false;
        for (final VfxLightingRegistry.Light light : lights) {
            if (light.shadowStrength <= 0.01F) {
                continue;
            }
            final int surface = minecraft.level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(light.x),
                Mth.floor(light.z)
            );
            final double heightAboveGround = light.y - surface;
            if (heightAboveGround < -1.0D || heightAboveGround > 90.0D) {
                continue;
            }
            final float distanceScale = (float) Math.max(0.25D, 1.0D - heightAboveGround / 100.0D);
            final float radius = Math.min(18.0F, Math.max(0.8F, light.shadowRadius * distanceScale));
            final int alpha = Mth.clamp((int) (light.shadowStrength * distanceScale * 145.0F), 0, 150);
            if (alpha <= 1) {
                continue;
            }
            final float y = surface + 0.035F;
            final float x0 = (float) (light.x - radius - cameraPosition.x);
            final float x1 = (float) (light.x + radius - cameraPosition.x);
            final float z0 = (float) (light.z - radius - cameraPosition.z);
            final float z1 = (float) (light.z + radius - cameraPosition.z);
            final float wy = (float) (y - cameraPosition.y);
            appendVertex(builder, event.getPoseStack(), x0, wy, z0, 0.0F, 0.0F, 0, 0, 0, alpha);
            appendVertex(builder, event.getPoseStack(), x1, wy, z0, 1.0F, 0.0F, 0, 0, 0, alpha);
            appendVertex(builder, event.getPoseStack(), x1, wy, z1, 1.0F, 1.0F, 0, 0, 0, alpha);
            appendVertex(builder, event.getPoseStack(), x0, wy, z1, 0.0F, 1.0F, 0, 0, 0, alpha);
            hasGeometry = true;
        }
        if (!hasGeometry) {
            builder.endOrDiscardIfEmpty();
            return;
        }

        setCommonUniforms(shadowShader, event.getPoseStack());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> shadowShader);
        try {
            shadowShader.apply();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static void renderLightVolumes(
        final RenderLevelStageEvent event,
        final Camera camera,
        final List<VfxLightingRegistry.Light> lights
    ) {
        if (volumeShader == null || lights.isEmpty()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final AbstractTexture texture = minecraft.getTextureManager().getTexture(VOLUME_TEXTURE);
        if (texture == null || texture.getId() == 0) {
            return;
        }

        final Vec3 cameraPosition = camera.getPosition();
        final BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        boolean hasGeometry = false;
        for (final VfxLightingRegistry.Light light : lights) {
            if (!light.volumetric) {
                continue;
            }
            final double dx = light.x - cameraPosition.x;
            final double dy = light.y - cameraPosition.y;
            final double dz = light.z - cameraPosition.z;
            final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (distance > 150.0D || distance < 0.1D) {
                continue;
            }
            final float size = Math.min(64.0F, Math.max(1.0F, light.radius * 0.86F));
            final float alpha = Math.min(0.78F,
                (0.055F + effectiveLightIntensity(light) * 0.034F) * nightBoost);
            final int red = Mth.clamp((int) (light.red * 45.0F), 0, 255);
            final int green = Mth.clamp((int) (light.green * 45.0F), 0, 255);
            final int blue = Mth.clamp((int) (light.blue * 45.0F), 0, 255);
            final int colorAlpha = Mth.clamp((int) (alpha * 255.0F), 0, 255);
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(dx, dy, dz);
            event.getPoseStack().mulPose(camera.rotation());
            appendVertex(builder, event.getPoseStack(), -size, -size, 0.0F, 0.0F, 0.0F, red, green, blue, colorAlpha);
            appendVertex(builder, event.getPoseStack(), size, -size, 0.0F, 1.0F, 0.0F, red, green, blue, colorAlpha);
            appendVertex(builder, event.getPoseStack(), size, size, 0.0F, 1.0F, 1.0F, red, green, blue, colorAlpha);
            appendVertex(builder, event.getPoseStack(), -size, size, 0.0F, 0.0F, 1.0F, red, green, blue, colorAlpha);
            event.getPoseStack().popPose();
            hasGeometry = true;
        }
        if (!hasGeometry) {
            builder.endOrDiscardIfEmpty();
            return;
        }

        setCommonUniforms(volumeShader, event.getPoseStack());
        setFloat(volumeShader, "Time", elapsedSeconds());
        volumeShader.setSampler("Sampler0", texture.getId());
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> volumeShader);
        try {
            volumeShader.apply();
            BufferUploader.drawWithShader(builder.end());
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
        }
    }

    /**
     * Emissive FP-5 visuals: the thin continuous glowing exhaust streak built
     * from the resampled trail history, and the blinding white-hot glare at
     * the nozzle.  Drawn additively after the scene pass so they read as the
     * light sources themselves instead of lit or darkened geometry.
     */
    private static void renderEmissive(
        final RenderLevelStageEvent event,
        final Camera camera,
        final FullfudClientConfig.Client config
    ) {
        if (emissiveShader == null || VfxTrailManager.states().isEmpty()) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        final Vec3 cameraPosition = camera.getPosition();
        final float partialTick = event.getPartialTick();
        final PoseStack poseStack = event.getPoseStack();

        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> emissiveShader);
        setFloat(emissiveShader, "Time", elapsedSeconds());
        setFloat(emissiveShader, "GlowStrength", config.vfxBloomEnabled.get() ? 1.0F : 0.35F);
        try {
            // Streak: depth tested so terrain and buildings in front hide it.
            RenderSystem.enableDepthTest();
            final BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            boolean hasRibbon = false;
            for (final VfxTrailManager.TrailState state : VfxTrailManager.states()) {
                hasRibbon |= appendTrailRibbon(builder, poseStack, state, cameraPosition, partialTick);
            }
            if (hasRibbon) {
                setFloat(emissiveShader, "Mode", 0.0F);
                BufferUploader.drawWithShader(builder.end());
            } else {
                builder.endOrDiscardIfEmpty();
            }

            // Glare: drawn with depth test enabled and slight camera-directed nudge
            // so clouds and terrain in front of the missile properly occlude glare,
            // while the missile's own nozzle does not slice through the billboard.
            RenderSystem.enableDepthTest();
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
            boolean hasGlare = false;
            for (final VfxTrailManager.TrailState state : VfxTrailManager.states()) {
                hasGlare |= appendGlare(builder, poseStack, camera, state, cameraPosition, partialTick, minecraft);
            }
            if (hasGlare) {
                setFloat(emissiveShader, "Mode", 1.0F);
                BufferUploader.drawWithShader(builder.end());
            } else {
                builder.endOrDiscardIfEmpty();
            }
        } finally {
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.defaultBlendFunc();
        }
    }

    private static boolean appendTrailRibbon(
        final BufferBuilder builder,
        final PoseStack poseStack,
        final VfxTrailManager.TrailState state,
        final Vec3 camera,
        final float partialTick
    ) {
        final List<VfxTrailManager.Sample> samples = state.samples();
        if (samples.size() < 2) {
            return false;
        }
        final float lifetime = Math.max(0.05F, VfxTrailManager.maxLifetimeSeconds());
        final boolean clipHead = state.hasHead();
        final Vec3 prevHead = state.prevHead();
        final Vec3 head = state.head();
        final long headTick = state.headTick();

        int count = 0;
        for (final VfxTrailManager.Sample sample : samples) {
            // Samples of the newest tick that lie ahead of the interpolated
            // (rendered) nozzle would poke out in front of the missile.
            if (clipHead && sample.createdTick == headTick
                && fractionAlong(sample, prevHead, head) > partialTick + 1.0E-3F) {
                continue;
            }
            if (count >= ribbonX.length - 1) {
                break;
            }
            final float age = VfxTrailManager.ageSeconds(sample, partialTick);
            final float t = Mth.clamp(age / lifetime, 0.0F, 1.0F);
            // Freshly burnt gas is white-hot and fades to a faint orange line
            // that lingers like a long-exposure streak.
            final float hot = (float) Math.exp(-age * 2.2F);
            final float fade = (float) Math.pow(1.0F - t, 2.0D);
            ribbonX[count] = (float) (sample.x - camera.x);
            ribbonY[count] = (float) (sample.y - camera.y);
            ribbonZ[count] = (float) (sample.z - camera.z);
            ribbonHot[count] = hot;
            ribbonGlow[count] = sample.heat * (0.05F + 0.30F * hot) * fade;
            count++;
        }
        if (clipHead) {
            final Vec3 tip = state.interpolatedHead(partialTick);
            ribbonX[count] = (float) (tip.x - camera.x);
            ribbonY[count] = (float) (tip.y - camera.y);
            ribbonZ[count] = (float) (tip.z - camera.z);
            ribbonHot[count] = 0.60F;
            ribbonGlow[count] = state.isBoosterActive() ? 0.30F : 0.12F;
            count++;
        }
        if (count < 2) {
            return false;
        }

        boolean appended = false;
        float dirX = 0.0F;
        float dirY = 1.0F;
        float dirZ = 0.0F;
        float prevSideX = 0.0F;
        float prevSideY = 0.0F;
        float prevSideZ = 0.0F;
        for (int i = 0; i < count; i++) {
            final int a = Math.max(0, i - 1);
            final int b = Math.min(count - 1, i + 1);
            final float tx = ribbonX[b] - ribbonX[a];
            final float ty = ribbonY[b] - ribbonY[a];
            final float tz = ribbonZ[b] - ribbonZ[a];
            final float cx = -ribbonX[i];
            final float cy = -ribbonY[i];
            final float cz = -ribbonZ[i];
            // Camera-facing ribbon: side = tangent x view vector.
            final float nx = ty * cz - tz * cy;
            final float ny = tz * cx - tx * cz;
            final float nz = tx * cy - ty * cx;
            final float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
            if (length > 1.0E-5F) {
                dirX = nx / length;
                dirY = ny / length;
                dirZ = nz / length;
            }
            // Width grows with distance so the streak keeps a stable ~2 px core
            // at range instead of vanishing into sub-pixel aliasing.
            final float distance = Mth.sqrt(cx * cx + cy * cy + cz * cz);
            final float halfWidth = Math.max(0.10F, distance * 0.0022F);
            final float sideX = dirX * halfWidth;
            final float sideY = dirY * halfWidth;
            final float sideZ = dirZ * halfWidth;
            if (i > 0 && (ribbonGlow[i - 1] > 0.004F || ribbonGlow[i] > 0.004F)) {
                final int p = i - 1;
                appendEmissiveVertex(builder, poseStack, ribbonX[p] - prevSideX, ribbonY[p] - prevSideY, ribbonZ[p] - prevSideZ, 0.0F, 0.0F, ribbonHot[p], ribbonGlow[p]);
                appendEmissiveVertex(builder, poseStack, ribbonX[p] + prevSideX, ribbonY[p] + prevSideY, ribbonZ[p] + prevSideZ, 0.0F, 1.0F, ribbonHot[p], ribbonGlow[p]);
                appendEmissiveVertex(builder, poseStack, ribbonX[i] + sideX, ribbonY[i] + sideY, ribbonZ[i] + sideZ, 0.0F, 1.0F, ribbonHot[i], ribbonGlow[i]);
                appendEmissiveVertex(builder, poseStack, ribbonX[i] - sideX, ribbonY[i] - sideY, ribbonZ[i] - sideZ, 0.0F, 0.0F, ribbonHot[i], ribbonGlow[i]);
                appended = true;
            }
            prevSideX = sideX;
            prevSideY = sideY;
            prevSideZ = sideZ;
        }
        return appended;
    }

    private static boolean appendGlare(
        final BufferBuilder builder,
        final PoseStack poseStack,
        final Camera camera,
        final VfxTrailManager.TrailState state,
        final Vec3 cameraPosition,
        final float partialTick,
        final Minecraft minecraft
    ) {
        if (!state.hasHead()) {
            return false;
        }
        final Vec3 tip = state.interpolatedHead(partialTick);
        final double dx = tip.x - cameraPosition.x;
        final double dy = tip.y - cameraPosition.y;
        final double dz = tip.z - cameraPosition.z;
        final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance < 0.3D || distance > 480.0D) {
            return false;
        }
        if (minecraft.level != null && minecraft.player != null) {
            final BlockHitResult hit = minecraft.level.clip(new ClipContext(
                cameraPosition, tip, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, minecraft.player
            ));
            final double visible = Math.max(0.0D, distance - 0.75D);
            if (hit.getType() == HitResult.Type.BLOCK && hit.getLocation().distanceToSqr(cameraPosition) < visible * visible) {
                return false;
            }
        }
        final boolean booster = state.isBoosterActive();
        // Tight, realistic nozzle throat glow: no oversized lens flares following the missile.
        final float size = booster
            ? Math.max(0.65F, (float) distance * 0.008F)
            : Math.max(0.50F, (float) distance * 0.006F);
        final float green = booster ? 0.72F : 0.58F;
        final float blue = booster ? 0.42F : 0.34F;
        final float alpha = booster ? 0.35F : 0.25F;
        final double nudge = Math.min(0.25D, distance * 0.04D);
        final double glareX = dx - (dx / distance) * nudge;
        final double glareY = dy - (dy / distance) * nudge;
        final double glareZ = dz - (dz / distance) * nudge;
        poseStack.pushPose();
        poseStack.translate(glareX, glareY, glareZ);
        poseStack.mulPose(camera.rotation());
        final org.joml.Matrix4f pose = poseStack.last().pose();
        builder.vertex(pose, -size, -size, 0.0F).color(1.0F, green, blue, alpha).uv(0.0F, 0.0F).endVertex();
        builder.vertex(pose, size, -size, 0.0F).color(1.0F, green, blue, alpha).uv(1.0F, 0.0F).endVertex();
        builder.vertex(pose, size, size, 0.0F).color(1.0F, green, blue, alpha).uv(1.0F, 1.0F).endVertex();
        builder.vertex(pose, -size, size, 0.0F).color(1.0F, green, blue, alpha).uv(0.0F, 1.0F).endVertex();
        poseStack.popPose();
        return true;
    }

    private static float fractionAlong(final VfxTrailManager.Sample sample, final Vec3 from, final Vec3 to) {
        final double dx = to.x - from.x;
        final double dy = to.y - from.y;
        final double dz = to.z - from.z;
        final double lengthSq = dx * dx + dy * dy + dz * dz;
        if (lengthSq < 1.0E-8D) {
            return 0.0F;
        }
        return (float) (((sample.x - from.x) * dx + (sample.y - from.y) * dy + (sample.z - from.z) * dz) / lengthSq);
    }

    private static void appendEmissiveVertex(
        final BufferBuilder builder,
        final PoseStack poseStack,
        final float x,
        final float y,
        final float z,
        final float u,
        final float v,
        final float hot,
        final float glow
    ) {
        // Cooling gradient: warm white at the nozzle -> deep orange downstream.
        final float green = 0.42F + 0.44F * hot;
        final float blue = 0.12F + 0.54F * hot;
        builder.vertex(poseStack.last().pose(), x, y, z)
            .color(1.0F, green, blue, Mth.clamp(glow, 0.0F, 1.0F))
            .uv(u, v)
            .endVertex();
    }

    private static void appendVertex(
        final BufferBuilder builder,
        final PoseStack poseStack,
        final float x,
        final float y,
        final float z,
        final float u,
        final float v,
        final int red,
        final int green,
        final int blue,
        final int alpha
    ) {
        builder.vertex(poseStack.last().pose(), x, y, z)
            .color(red, green, blue, alpha)
            .uv(u, v)
            .endVertex();
    }

    private static void setCommonUniforms(final ShaderInstance shader, final PoseStack poseStack) {
        if (shader == null) {
            return;
        }
        // Vertices are already transformed by PoseStack in BufferBuilder.
        // Applying that matrix again as ModelViewMat double-transforms every
        // custom VFX quad (and is especially visible for camera-relative
        // trails/volumes).  The render-level model-view matrix is the camera
        // transform that the vanilla vertex path expects here.
        shader.MODEL_VIEW_MATRIX.set(RenderSystem.getModelViewMatrix());
        shader.PROJECTION_MATRIX.set(RenderSystem.getProjectionMatrix());
        shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        setFloat(shader, "Time", elapsedSeconds());
        setFloat(shader, "FogStart", RenderSystem.getShaderFogStart());
        setFloat(shader, "FogEnd", RenderSystem.getShaderFogEnd());
        final float[] fog = RenderSystem.getShaderFogColor();
        if (fog != null && fog.length >= 4) {
            setVector4(shader, "FogColor", fog[0], fog[1], fog[2], fog[3]);
        }
    }

    private static void setWorldLightUniforms(
        final ShaderInstance shader,
        final List<VfxLightingRegistry.Light> lights
    ) {
        for (int i = 0; i < VfxLightingRegistry.SHADER_LIGHT_SLOTS; i++) {
            final String suffix = Integer.toString(i);
            if (i < lights.size()) {
                final VfxLightingRegistry.Light light = lights.get(i);
                setVector3(
                    shader,
                    "LightPos" + suffix,
                    (float) light.x,
                    (float) light.y,
                    (float) light.z
                );
                setVector3(shader, "LightColor" + suffix, light.red, light.green, light.blue);
                setFloat(shader, "LightRadius" + suffix, light.radius);
                setFloat(shader, "LightIntensity" + suffix, effectiveLightIntensity(light));
            } else {
                setVector3(shader, "LightPos" + suffix, 0.0F, 0.0F, 0.0F);
                setVector3(shader, "LightColor" + suffix, 0.0F, 0.0F, 0.0F);
                setFloat(shader, "LightRadius" + suffix, 1.0F);
                setFloat(shader, "LightIntensity" + suffix, 0.0F);
            }
        }
    }

    private static void setCameraRelativeLightUniforms(
        final ShaderInstance shader,
        final List<VfxLightingRegistry.Light> lights,
        final Vec3 camera
    ) {
        for (int i = 0; i < VfxLightingRegistry.SHADER_LIGHT_SLOTS; i++) {
            final String suffix = Integer.toString(i);
            if (i < lights.size()) {
                final VfxLightingRegistry.Light light = lights.get(i);
                setVector3(
                    shader,
                    "LightPos" + suffix,
                    (float) (light.x - camera.x),
                    (float) (light.y - camera.y),
                    (float) (light.z - camera.z)
                );
                setVector3(shader, "LightColor" + suffix, light.red, light.green, light.blue);
                setFloat(shader, "LightRadius" + suffix, light.radius);
                setFloat(shader, "LightIntensity" + suffix, effectiveLightIntensity(light));
            } else {
                setVector3(shader, "LightPos" + suffix, 0.0F, 0.0F, 0.0F);
                setVector3(shader, "LightColor" + suffix, 0.0F, 0.0F, 0.0F);
                setFloat(shader, "LightRadius" + suffix, 1.0F);
                setFloat(shader, "LightIntensity" + suffix, 0.0F);
            }
        }
    }

    private static void setMatrix(final ShaderInstance shader, final String name, final Matrix4f matrix) {
        if (shader != null && matrix != null) {
            final com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(matrix);
            }
        }
    }

    private static void setFloat2(final ShaderInstance shader, final String name, final float x, final float y) {
        if (shader != null) {
            final com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(x, y);
            }
        }
    }

    private static void setVector3(final ShaderInstance shader, final String name, final float x, final float y, final float z) {
        if (shader != null) {
            final com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(x, y, z);
            }
        }
    }

    private static void setVector4(final ShaderInstance shader, final String name, final float x, final float y, final float z, final float w) {
        if (shader != null) {
            final com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(x, y, z, w);
            }
        }
    }

    private static void setFloat(final ShaderInstance shader, final String name, final float value) {
        if (shader != null) {
            final com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(value);
            }
        }
    }

    private static void setInt(final ShaderInstance shader, final String name, final int value) {
        if (shader != null) {
            final com.mojang.blaze3d.shaders.Uniform uniform = shader.getUniform(name);
            if (uniform != null) {
                uniform.set(value);
            }
        }
    }

    private static float effectiveLightIntensity(final VfxLightingRegistry.Light light) {
        // Single source of truth lives in the registry so particle lighting and
        // scene lighting can never disagree about how bright a source is.
        return VfxLightingRegistry.effectiveLightIntensity(light);
    }

    private static float elapsedSeconds() {
        return (System.nanoTime() - startNanos) * 0.000000001F;
    }

}
