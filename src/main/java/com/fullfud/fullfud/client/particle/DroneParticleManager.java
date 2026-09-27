package com.fullfud.fullfud.client.particle;

import com.fullfud.fullfud.client.vfx.VfxLightingRegistry;
import com.fullfud.fullfud.client.vfx.VfxRenderPipeline;
import com.fullfud.fullfud.core.FullfudRegistries;
import com.fullfud.fullfud.core.config.FullfudClientConfig;
import com.fullfud.fullfud.core.network.packet.DroneExplosionPacket;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.level.LevelEvent;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

@OnlyIn(Dist.CLIENT)
public final class DroneParticleManager {
    private static final int MAX_PARTICLES = 2500;
    private static final List<ExplosionParticle> PARTICLES = new ArrayList<>();
    private static final List<DelayedSound> DELAYED_SOUNDS = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();
    /** Reused scratch buffer for CPU-side custom-light sampling (render thread only). */
    private static final float[] LIGHT_SAMPLE = new float[3];

    // Textures from mtsofficialpack
    private static final ResourceLocation TEX_FLASH_FLARE = new ResourceLocation("mtsofficialpack", "textures/bullets/bombflare_translucent.png");
    private static final ResourceLocation TEX_FLASH_BRIGHT = new ResourceLocation("mtsofficialpack", "textures/bullets/bombflarebright_translucent.png");
    private static final ResourceLocation TEX_FLASH_64 = new ResourceLocation("mtsofficialpack", "textures/bullets/flash64_translucent.png");

    private static final ResourceLocation[] TEX_BANG_FRAMES = new ResourceLocation[] {
        new ResourceLocation("mtsofficialpack", "textures/bullets/bang0.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/bang1.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/bang2.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/bang3.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/bang4.png")
    };

    private static final ResourceLocation TEX_SHOCKWAVE = new ResourceLocation("mtsofficialpack", "textures/bullets/shockwave_translucent.png");
    private static final ResourceLocation TEX_SHOCKWAVE2 = new ResourceLocation("mtsofficialpack", "textures/bullets/shockwave2_translucent.png");

    private static final ResourceLocation[] TEX_FIRE_FRAMES = new ResourceLocation[] {
        new ResourceLocation("mtsofficialpack", "textures/bullets/fire0.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/fire1.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/fire2.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/fire3.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/fire4.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/fire5.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/fire6.png")
    };

    private static final ResourceLocation[] TEX_SPARK_FRAMES = new ResourceLocation[] {
        new ResourceLocation("mtsofficialpack", "textures/bullets/sparkbullet0_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/sparkbullet1_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/sparkbullet2_translucent.png")
    };

    private static final ResourceLocation TEX_VECSPARK = new ResourceLocation("mtsofficialpack", "textures/bullets/vecspark.png");

    private static final ResourceLocation[] TEX_SMOKE_CLUSTERS = new ResourceLocation[] {
        new ResourceLocation("mtsofficialpack", "textures/bullets/smokecluster0_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/smokecluster1_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/smokecluster2_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/smokecluster3_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/smokecluster4_translucent.png")
    };

    private static final ResourceLocation[] TEX_ALT_SMOKE = new ResourceLocation[] {
        new ResourceLocation("mtsofficialpack", "textures/bullets/alt0_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/alt1_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/alt2_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/alt3_translucent.png")
    };

    private static final ResourceLocation[] TEX_BIG_SMOKE = new ResourceLocation[] {
        new ResourceLocation("mts", "textures/particles/big_smoke_0.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_1.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_2.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_3.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_4.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_5.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_6.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_7.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_8.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_9.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_10.png"),
        new ResourceLocation("mts", "textures/particles/big_smoke_11.png")
    };

    private static final ResourceLocation[] TEX_SPARK_CLUSTERS = new ResourceLocation[] {
        new ResourceLocation("mtsofficialpack", "textures/bullets/sparkcluster0_translucent.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/sparkcluster1_translucent.png")
    };

    private static final ResourceLocation[] TEX_DEBRIS = new ResourceLocation[] {
        new ResourceLocation("mtsofficialpack", "textures/bullets/debris0.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/debris1.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/debris2.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/debris3.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/debris4.png")
    };

    private static final ResourceLocation[] TEX_SPLASH = new ResourceLocation[] {
        new ResourceLocation("mtsofficialpack", "textures/bullets/singlesplash_0.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/singlesplash_1.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/singlesplash_2.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/splash_0.png"),
        new ResourceLocation("mtsofficialpack", "textures/bullets/splash_1.png")
    };

    private static final float[][] FAB_SOOT_COLORS = new float[][] {
        {0.306F, 0.298F, 0.286F}, // 4E4C49
        {0.228F, 0.216F, 0.200F}, // 3A3733
        {0.263F, 0.255F, 0.239F}, // 43413D
        {0.302F, 0.286F, 0.259F}, // 4D4942
        {0.404F, 0.384F, 0.349F}, // 676259
        {0.286F, 0.275F, 0.255F}, // 494641
        {0.325F, 0.314F, 0.294F}, // 53504B
        {0.212F, 0.204F, 0.196F}, // 363432
        {0.180F, 0.165F, 0.157F}, // 2e2a28
        {0.478F, 0.467F, 0.447F}, // 7a7772
        {0.345F, 0.329F, 0.302F}  // 58544d
    };

    private DroneParticleManager() {
    }

    public static void handleExplosionPacket(final DroneExplosionPacket packet) {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }

        final double x = packet.x();
        final double y = packet.y();
        final double z = packet.z();
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            return;
        }
        final float nx = packet.normalX();
        final float ny = packet.normalY();
        final float nz = packet.normalZ();
        final byte expType = packet.explosionType();
        final byte matType = packet.materialType();
        final float power = Float.isFinite(packet.power()) ? Math.max(1.0F, packet.power()) : 1.0F;
        final List<ExplosionParticle> spawnList = new ArrayList<>(280);
        final ExplosionVisualProfile visualProfile = resolveExplosionVisualProfile(expType, power);
        if (visualProfile != null) {
            playExplosionSounds(minecraft, x, y, z, matType, visualProfile.soundScale());
        }
        if (visualProfile != null && spawnLayeredExplosion(
            spawnList,
            x, y, z,
            nx, ny, nz,
            matType,
            visualProfile
        )) {
            enqueueParticles(spawnList);
            return;
        }

        final float audioScale;

        if (expType == DroneExplosionPacket.TYPE_FLAMINGO) {
            audioScale = 4.5F;
            spawnLayeredExplosion(
                spawnList, x, y, z, nx, ny, nz, matType,
                resolveExplosionVisualProfile(DroneExplosionPacket.TYPE_FLAMINGO, power)
            );
        } else {
            final float scaleMul;
            final int sparkCount;
            final int smokeCount;
            final int debrisCount;

            switch (expType) {
                case DroneExplosionPacket.TYPE_SHAHED -> {
                    scaleMul = 4.8F;
                    sparkCount = 110;
                    smokeCount = 76;
                    debrisCount = 70;
                }
                case DroneExplosionPacket.TYPE_FPV_STRIKE -> {
                    scaleMul = 1.15F;
                    sparkCount = 32;
                    smokeCount = 18;
                    debrisCount = 16;
                }
                default -> { // FPV standard
                    scaleMul = 1.0F;
                    sparkCount = 28;
                    smokeCount = 16;
                    debrisCount = 14;
                }
            }
            audioScale = scaleMul;

            // 1. Blinding Flash & Flare (Additive Fullbright Glow)
            final ExplosionParticle flare = new ExplosionParticle(
                x, y + 0.1D, z,
                0.0D, 0.05D, 0.0D,
                0.5F * scaleMul, 16.0F * scaleMul,
                1.0F, 0.0F,
                1.0F, 0.95F, 0.85F,
                0.0F, 0.0F,
                0, 4,
                0.0F, 0.99F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                TEX_FLASH_FLARE, null, 1, 0, 0, 0,
                0
            );
            spawnList.add(flare);

            final ExplosionParticle coreFlash = new ExplosionParticle(
                x, y + 0.2D, z,
                0.0D, 0.1D, 0.0D,
                1.0F * scaleMul, 10.0F * scaleMul,
                1.0F, 0.0F,
                1.0F, 0.8F, 0.5F,
                RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.2F,
                0, 6,
                0.0F, 0.99F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                null, TEX_BANG_FRAMES, 1, 0, 0, 0,
                0
            );
            spawnList.add(coreFlash);

            // 2. Ground-Aligned Shockwave Ring or Aerial Vapor Bubble
            final float normLenSq = nx * nx + ny * ny + nz * nz;
            final boolean isAirburst = normLenSq < 1.0E-4F;
            final float unitNx;
            final float unitNy;
            final float unitNz;
            if (!isAirburst) {
                final float invNorm = 1.0F / (float) Math.sqrt(normLenSq);
                unitNx = nx * invNorm;
                unitNy = ny * invNorm;
                unitNz = nz * invNorm;
            } else {
                unitNx = 0.0F;
                unitNy = 0.0F;
                unitNz = 0.0F;
            }

            if (!isAirburst) {
                final ExplosionParticle shockwave = new ExplosionParticle(
                    x + unitNx * 0.08D, y + unitNy * 0.08D, z + unitNz * 0.08D,
                    0.0D, 0.0D, 0.0D,
                    0.5F, 12.0F * scaleMul,
                    0.85F, 0.0F,
                    0.95F, 0.9F, 0.8F,
                    0.0F, 0.0F,
                    0, 14,
                    0.0F, 0.99F,
                    false, false,
                    ParticleOrientation.GROUND_ALIGNED, BlendMode.ALPHA,
                    TEX_SHOCKWAVE, null, 1, unitNx, unitNy, unitNz,
                    0
                );
                spawnList.add(shockwave);

                final ExplosionParticle shockwave2 = new ExplosionParticle(
                    x + unitNx * 0.09D, y + unitNy * 0.09D, z + unitNz * 0.09D,
                    0.0D, 0.0D, 0.0D,
                    0.2F, 8.0F * scaleMul,
                    0.95F, 0.0F,
                    1.0F, 1.0F, 0.95F,
                    0.0F, 0.0F,
                    0, 9,
                    0.0F, 0.99F,
                    false, false,
                    ParticleOrientation.GROUND_ALIGNED, BlendMode.ADDITIVE,
                    TEX_SHOCKWAVE2, null, 1, unitNx, unitNy, unitNz,
                    0
                );
                spawnList.add(shockwave2);
            } else {
                final ExplosionParticle vaporBubble = new ExplosionParticle(
                    x, y, z,
                    0.0D, 0.0D, 0.0D,
                    0.5F * scaleMul, 12.0F * scaleMul,
                    0.80F, 0.0F,
                    0.95F, 0.9F, 0.8F,
                    0.0F, 0.0F,
                    0, 14,
                    0.0F, 0.99F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    TEX_SHOCKWAVE, null, 1, 0, 0, 0,
                    0
                );
                spawnList.add(vaporBubble);
            }

            // 3. Expanding Fireball Core
            final int fireCount = (int) (8 * scaleMul);
            for (int i = 0; i < fireCount; i++) {
                final double fx = (RANDOM.nextDouble() - 0.5D) * 0.8D * scaleMul;
                final double fy = RANDOM.nextDouble() * 0.6D * scaleMul;
                final double fz = (RANDOM.nextDouble() - 0.5D) * 0.8D * scaleMul;
                final double fvx = (RANDOM.nextDouble() - 0.5D) * 0.25D * scaleMul;
                final double fvy = (0.2D + RANDOM.nextDouble() * 0.5D) * scaleMul;
                final double fvz = (RANDOM.nextDouble() - 0.5D) * 0.25D * scaleMul;

                final ExplosionParticle fire = new ExplosionParticle(
                    x + fx, y + fy, z + fz,
                    fvx, fvy, fvz,
                    2.0F * scaleMul, 6.0F * scaleMul,
                    1.0F, 0.0F,
                    1.0F, 0.85F, 0.4F,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.15F,
                    0, 14 + RANDOM.nextInt(8),
                    -0.005F, 0.92F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                    null, TEX_FIRE_FRAMES, 2, 0, 0, 0,
                    1
                );
                spawnList.add(fire);
            }

            // 4. Ballistic Glowing Sparks & Shrapnel
            final boolean isDirectional = expType == DroneExplosionPacket.TYPE_FPV_STRIKE && (nx * nx + ny * ny + nz * nz) > 0.01F;
            for (int i = 0; i < sparkCount; i++) {
                double dirX, dirY, dirZ;
                if (isDirectional) {
                    dirX = nx + (RANDOM.nextDouble() - 0.5D) * 0.7D;
                    dirY = ny + (RANDOM.nextDouble() - 0.5D) * 0.7D;
                    dirZ = nz + (RANDOM.nextDouble() - 0.5D) * 0.7D;
                } else {
                    final double theta = RANDOM.nextDouble() * Math.PI * 2.0D;
                    final double phi = RANDOM.nextDouble() * Math.PI * 0.48D;
                    dirX = Math.sin(phi) * Math.cos(theta);
                    dirY = Math.cos(phi);
                    dirZ = Math.sin(phi) * Math.sin(theta);
                }
                final double speed = (0.6D + RANDOM.nextDouble() * 1.8D) * scaleMul;
                final double svx = dirX * speed;
                final double svy = (dirY * speed) + 0.15D;
                final double svz = dirZ * speed;

                final ExplosionParticle spark = new ExplosionParticle(
                    x, y + 0.3D, z,
                    svx, svy, svz,
                    0.8F * scaleMul, 0.2F,
                    1.0F, 0.0F,
                    1.0F, 0.9F, 0.45F,
                    0.0F, 0.0F,
                    0, 20 + RANDOM.nextInt(25),
                    0.04F, 0.95F,
                    true, true,
                    ParticleOrientation.MOTION, BlendMode.ADDITIVE,
                    null, TEX_SPARK_FRAMES, 2, 0, 0, 0,
                    1
                );
                spawnList.add(spark);
            }

            // 5. Material-Aware Debris & Dust Ejection
            for (int i = 0; i < debrisCount; i++) {
                final double dvx = (RANDOM.nextDouble() - 0.5D) * 0.8D * scaleMul;
                final double dvy = (0.35D + RANDOM.nextDouble() * 0.9D) * scaleMul;
                final double dvz = (RANDOM.nextDouble() - 0.5D) * 0.8D * scaleMul;

                final ResourceLocation debrisTex;
                final float red, green, blue;
                if (matType == DroneExplosionPacket.MAT_WATER) {
                    debrisTex = TEX_SPLASH[RANDOM.nextInt(TEX_SPLASH.length)];
                    red = 0.8F; green = 0.9F; blue = 1.0F;
                } else if (matType == DroneExplosionPacket.MAT_SAND) {
                    debrisTex = TEX_DEBRIS[RANDOM.nextInt(TEX_DEBRIS.length)];
                    red = 0.88F; green = 0.8F; blue = 0.55F;
                } else if (matType == DroneExplosionPacket.MAT_STONE) {
                    debrisTex = TEX_DEBRIS[RANDOM.nextInt(TEX_DEBRIS.length)];
                    red = 0.65F; green = 0.65F; blue = 0.65F;
                } else if (matType == DroneExplosionPacket.MAT_WOOD) {
                    debrisTex = TEX_DEBRIS[RANDOM.nextInt(TEX_DEBRIS.length)];
                    red = 0.55F; green = 0.38F; blue = 0.22F;
                } else {
                    debrisTex = TEX_DEBRIS[RANDOM.nextInt(TEX_DEBRIS.length)];
                    red = 0.45F; green = 0.35F; blue = 0.25F;
                }

                final ExplosionParticle debris = new ExplosionParticle(
                    x, y + 0.2D, z,
                    dvx, dvy, dvz,
                    0.9F * scaleMul, 1.2F * scaleMul,
                    1.0F, 0.0F,
                    red, green, blue,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.4F,
                    0, 40 + RANDOM.nextInt(35),
                    0.06F, 0.96F,
                    true, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    debrisTex, null, 1, 0, 0, 0,
                    2
                );
                spawnList.add(debris);
            }

            // 6. Volumetric Smoke
            for (int i = 0; i < smokeCount; i++) {
                final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double radius = RANDOM.nextDouble() * 1.5D * scaleMul;
                final double smx = x + Math.cos(angle) * radius;
                final double smz = z + Math.sin(angle) * radius;
                final double smy = y + RANDOM.nextDouble() * 0.8D * scaleMul;

                final double smvx = (Math.cos(angle) * 0.08D + (RANDOM.nextDouble() - 0.5D) * 0.04D) * scaleMul;
                final double smvy = (0.08D + RANDOM.nextDouble() * 0.22D) * scaleMul;
                final double smvz = (Math.sin(angle) * 0.08D + (RANDOM.nextDouble() - 0.5D) * 0.04D) * scaleMul;

                final float soot = 0.18F + RANDOM.nextFloat() * 0.18F;
                final ResourceLocation smokeTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                final int lifetime = (int) ((80 + RANDOM.nextInt(100)) * (scaleMul > 1.5F ? 1.5F : 1.0F));

                final ExplosionParticle smoke = new ExplosionParticle(
                    smx, smy, smz,
                    smvx, smvy, smvz,
                    2.5F * scaleMul, (6.0F + RANDOM.nextFloat() * 5.0F) * scaleMul,
                    0.75F, 0.0F,
                    soot, soot, soot,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.04F,
                    0, lifetime,
                    -0.002F, 0.985F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    smokeTex, null, 1, 0, 0, 0,
                    3
                );
                spawnList.add(smoke);
            }
        }

        enqueueParticles(spawnList);

        // 7. Local custom light for every explosion profile.  This is additive
        // to the scene lighting pass and never writes a vanilla light level.
        final float lightRadius = expType == DroneExplosionPacket.TYPE_FLAMINGO
            ? 42.0F
            : expType == DroneExplosionPacket.TYPE_SHAHED ? 36.0F : 9.0F;
        final float lightIntensity = expType == DroneExplosionPacket.TYPE_FLAMINGO
            ? 5.8F
            : expType == DroneExplosionPacket.TYPE_SHAHED ? 4.5F : 1.45F;
        VfxLightingRegistry.addTransientLight(
            new Vec3(x, y + 0.3D, z),
            3.8F,
            0.82F,
            0.18F,
            lightIntensity,
            lightRadius,
            expType == DroneExplosionPacket.TYPE_FLAMINGO ? 34 : 18
        );

        // 8. Audio Choreography
        playExplosionSounds(minecraft, x, y, z, matType, audioScale);
    }

    /**
     * Visual-only explosion profile.  Damage and the packet contract remain
     * server authoritative; these values only control the client composition.
     * The profile deliberately keeps the flash short and spends most of the
     * budget on a pressure halo, cooling fire shell, dust and smoke layers.
     */
    public record ExplosionVisualProfile(
        float visualScale,
        int fireParticles,
        int smokeParticles,
        int sparkParticles,
        int debrisParticles,
        int dustParticles,
        float haloRadius,
        int haloLifetimeTicks,
        float lightRadius,
        float lightIntensity,
        float soundScale,
        byte explosionType
    ) {
        public boolean heavy() {
            return visualScale >= 3.0F;
        }

        public boolean flamingo() {
            return explosionType == DroneExplosionPacket.TYPE_FLAMINGO;
        }
    }

    private record MaterialPalette(
        float debrisRed,
        float debrisGreen,
        float debrisBlue,
        float dustRed,
        float dustGreen,
        float dustBlue
    ) {
    }

    /**
     * Resolves a bounded visual profile without touching the world.  Keeping
     * this pure makes the explosion composition auditable in headless tests.
     */
    public static ExplosionVisualProfile resolveExplosionVisualProfile(final byte explosionType, final float power) {
        final float safePower = Float.isFinite(power) ? Mth.clamp(power, 0.5F, 12.0F) : 1.0F;
        // At the FP-5 contract power of 8 this is exactly 1.5, preserving the
        // 110 m outer halo while still making reduced test/custom payloads
        // visibly smaller instead of pretending every packet is a warhead.
        final float powerFactor = Mth.clamp(0.85F + safePower * 0.08125F, 0.90F, 1.55F);
        return switch (explosionType) {
            case DroneExplosionPacket.TYPE_FPV_STRIKE -> new ExplosionVisualProfile(
                1.12F * powerFactor, 18, 32, 36, 20, 28,
                18.0F * powerFactor, 13, 16.0F * powerFactor,
                1.85F * powerFactor, 1.15F, DroneExplosionPacket.TYPE_FPV_STRIKE
            );
            case DroneExplosionPacket.TYPE_SHAHED -> new ExplosionVisualProfile(
                4.40F * powerFactor, 110, 145, 135, 90, 110,
                58.0F * powerFactor, 24, 55.0F * powerFactor,
                4.80F * powerFactor, 3.6F, DroneExplosionPacket.TYPE_SHAHED
            );
            case DroneExplosionPacket.TYPE_FLAMINGO -> new ExplosionVisualProfile(
                4.20F * powerFactor, 140, 190, 130, 64, 110,
                73.33334F * powerFactor, 24, 56.14035F * powerFactor,
                17.777779F * powerFactor, 4.5F, DroneExplosionPacket.TYPE_FLAMINGO
            );
            case DroneExplosionPacket.TYPE_FPV_STANDARD -> new ExplosionVisualProfile(
                1.00F * powerFactor, 14, 24, 26, 16, 22,
                14.0F * powerFactor, 12, 12.0F * powerFactor,
                1.55F * powerFactor, 1.0F, DroneExplosionPacket.TYPE_FPV_STANDARD
            );
            default -> null;
        };
    }

    public static boolean isAirburst(final float nx, final float ny, final float nz) {
        return !Float.isFinite(nx)
            || !Float.isFinite(ny)
            || !Float.isFinite(nz)
            || (nx * nx + ny * ny + nz * nz) < 1.0E-4F;
    }

    private static boolean spawnLayeredExplosion(
        final List<ExplosionParticle> spawnList,
        final double x,
        final double y,
        final double z,
        final float nx,
        final float ny,
        final float nz,
        final byte materialType,
        final ExplosionVisualProfile profile
    ) {
        if (profile == null) {
            return false;
        }
        final Vec3 center = new Vec3(x, y, z);
        final Vec3 normal = normalizedImpactNormal(nx, ny, nz);
        final boolean airburst = isAirburst(nx, ny, nz);
        final float density = particleDensity();

        spawnPressureHalo(spawnList, center, normal, airburst, profile, density);
        spawnFlashLayer(spawnList, center, normal, airburst, profile, density);
        spawnFireShell(spawnList, center, normal, airburst, profile, density);
        spawnSmokeCloud(spawnList, center, normal, airburst, profile, density);
        spawnGroundDust(spawnList, center, normal, airburst, materialType, profile, density);
        spawnDebris(spawnList, center, normal, materialType, profile, density);
        spawnSparkShrapnel(spawnList, center, normal, airburst, materialType, profile, density);
        registerExplosionLights(center, normal, materialType, profile);
        return true;
    }

    private static Vec3 normalizedImpactNormal(final float nx, final float ny, final float nz) {
        if (isAirburst(nx, ny, nz)) {
            return Vec3.ZERO;
        }
        final float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (!Float.isFinite(length) || length < 1.0E-4F) {
            return Vec3.ZERO;
        }
        return new Vec3(nx / length, ny / length, nz / length);
    }

    private static float particleDensity() {
        try {
            return switch (FullfudClientConfig.CLIENT.vfxQuality.get()) {
                case LOW -> 0.52F;
                case MEDIUM -> 0.76F;
                case HIGH -> 1.0F;
                case ULTRA -> 1.18F;
                case AUTO -> 1.0F;
            };
        } catch (Throwable ignored) {
            return 1.0F;
        }
    }

    private static int qualityCount(final int baseCount, final float density) {
        return Math.max(1, Math.round(baseCount * density));
    }

    private static void appendParticle(final List<ExplosionParticle> particles, final ExplosionParticle particle) {
        if (particles.size() < MAX_PARTICLES) {
            particles.add(particle);
        }
    }

    private static void enqueueParticles(final List<ExplosionParticle> newParticles) {
        synchronized (PARTICLES) {
            final int incoming = Math.min(MAX_PARTICLES, newParticles.size());
            final int toRemove = Math.min(PARTICLES.size(), Math.max(0, PARTICLES.size() + incoming - MAX_PARTICLES));
            if (toRemove > 0) {
                PARTICLES.subList(0, toRemove).clear();
            }
            PARTICLES.addAll(newParticles.subList(newParticles.size() - incoming, newParticles.size()));
        }
    }

    /**
     * Pressure halo: a bright, thin leading ring, a slower dust ring and a
     * camera-facing spherical halo.  The surface variant is aligned to the
     * impact plane; airbursts use only the spherical/billboard layers so they
     * never leave a floating ground disc in the air.
     */
    private static void spawnPressureHalo(
        final List<ExplosionParticle> particles,
        final Vec3 center,
        final Vec3 normal,
        final boolean airburst,
        final ExplosionVisualProfile profile,
        final float density
    ) {
        final float radius = Math.max(5.0F, profile.haloRadius());
        // Particle scale is quad width, while the profile stores the desired
        // visible radius.  The MTS shockwave texture reaches the quad edge.
        final float diameter = radius * 2.0F;
        final int life = Math.max(8, profile.haloLifetimeTicks());
        final int innerLife = Math.max(5, life - 5);
        final Vec3 offset = normal.scale(0.12D);

        if (!airburst) {
            final ExplosionParticle leading = new ExplosionParticle(
                center.x + offset.x, center.y + offset.y, center.z + offset.z,
                0.0D, 0.0D, 0.0D,
                0.45F, diameter,
                0.16F, 0.0F,
                0.90F, 0.84F, 0.70F,
                0.0F, 0.0F,
                0, life,
                0.0F, 0.985F,
                false, false,
                ParticleOrientation.GROUND_ALIGNED, BlendMode.ALPHA,
                TEX_SHOCKWAVE, null, 1, (float) normal.x, (float) normal.y, (float) normal.z,
                0
            );
            leading.scaleGamma = 0.68F;
            leading.alphaGamma = 1.35F;
            leading.toRed = 0.56F;
            leading.toGreen = 0.45F;
            leading.toBlue = 0.36F;
            appendParticle(particles, leading);

            final ExplosionParticle hotLeading = new ExplosionParticle(
                center.x + normal.x * 0.18D, center.y + normal.y * 0.18D, center.z + normal.z * 0.18D,
                0.0D, 0.0D, 0.0D,
                0.22F, diameter * 0.62F,
                0.10F, 0.0F,
                0.95F, 0.93F, 0.88F,
                0.0F, 0.0F,
                1, Math.max(5, life - 3),
                0.0F, 0.975F,
                false, false,
                ParticleOrientation.GROUND_ALIGNED, BlendMode.ADDITIVE,
                TEX_SHOCKWAVE2, null, 1, (float) normal.x, (float) normal.y, (float) normal.z,
                1
            );
            hotLeading.scaleGamma = 0.62F;
            hotLeading.alphaGamma = 1.55F;
            hotLeading.toRed = 0.55F;
            hotLeading.toGreen = 0.52F;
            hotLeading.toBlue = 0.48F;
            appendParticle(particles, hotLeading);

            final ExplosionParticle inner = new ExplosionParticle(
                center.x + normal.x * 0.08D, center.y + normal.y * 0.08D, center.z + normal.z * 0.08D,
                0.0D, 0.0D, 0.0D,
                0.18F, diameter * 0.28F,
                0.18F, 0.0F,
                0.92F, 0.90F, 0.85F,
                0.0F, 0.0F,
                2, innerLife,
                0.0F, 0.96F,
                false, false,
                ParticleOrientation.GROUND_ALIGNED, BlendMode.ALPHA,
                TEX_SHOCKWAVE, null, 1, (float) normal.x, (float) normal.y, (float) normal.z,
                0
            );
            inner.scaleGamma = 0.74F;
            inner.alphaGamma = 1.2F;
            appendParticle(particles, inner);
        } else {
            final ExplosionParticle spherical = new ExplosionParticle(
                center.x, center.y, center.z,
                0.0D, 0.0D, 0.0D,
                0.35F, diameter,
                0.16F, 0.0F,
                0.82F, 0.76F, 0.68F,
                0.0F, 0.0F,
                0, life,
                0.0F, 0.985F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                TEX_SHOCKWAVE, null, 1, 0.0F, 0.0F, 0.0F,
                0
            );
            spherical.scaleGamma = 0.70F;
            spherical.alphaGamma = 1.35F;
            appendParticle(particles, spherical);

            final ExplosionParticle hotSpherical = new ExplosionParticle(
                center.x, center.y, center.z,
                0.0D, 0.0D, 0.0D,
                0.18F, diameter * 0.58F,
                0.10F, 0.0F,
                0.95F, 0.76F, 0.50F,
                0.0F, 0.0F,
                1, Math.max(5, life - 3),
                0.0F, 0.97F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                TEX_SHOCKWAVE2, null, 1, 0.0F, 0.0F, 0.0F,
                1
            );
            hotSpherical.scaleGamma = 0.62F;
            hotSpherical.alphaGamma = 1.55F;
            hotSpherical.toRed = 0.55F;
            hotSpherical.toGreen = 0.52F;
            hotSpherical.toBlue = 0.48F;
            appendParticle(particles, hotSpherical);
        }

        // A second, camera-facing ring reads as a spherical pressure wave even
        // when the impact plane is oblique or the camera is close to the blast.
        final ExplosionParticle halo = new ExplosionParticle(
            center.x + normal.x * 0.32D, center.y + normal.y * 0.32D, center.z + normal.z * 0.32D,
            0.0D, 0.0D, 0.0D,
            0.28F, diameter * 0.76F,
            0.08F, 0.0F,
            0.92F, 0.90F, 0.86F,
            0.0F, 0.0F,
            1, Math.max(5, life - 4),
            0.0F, 0.975F,
            false, false,
            ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
            TEX_SHOCKWAVE2, null, 1, 0.0F, 0.0F, 0.0F,
            2
        );
        halo.scaleGamma = 0.76F;
        halo.alphaGamma = 1.7F;
        halo.toRed = 0.50F;
        halo.toGreen = 0.48F;
        halo.toBlue = 0.45F;
        appendParticle(particles, halo);
    }

    private static void spawnFlashLayer(
        final List<ExplosionParticle> particles,
        final Vec3 center,
        final Vec3 normal,
        final boolean airburst,
        final ExplosionVisualProfile profile,
        final float density
    ) {
        if (profile.heavy()) {
            // Warm initial flare so the soot cloud does not appear to snap from white to black.
            final ExplosionParticle flareExpand = new ExplosionParticle(
                center.x, center.y + 0.1D, center.z,
                0.0D, 0.0D, 0.0D,
                0.8F, profile.flamingo() ? 22.0F : 15.0F,
                0.75F, 0.0F,
                1.0F, 0.72F, 0.38F,
                0.0F, 0.0F,
                0, 4,
                0.0F, 1.0F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                TEX_FLASH_FLARE, null, 1, 0.0F, 0.0F, 0.0F,
                5
            );
            flareExpand.litFactor = 0.0F;
            appendParticle(particles, flareExpand);

            // 2. Main animated fireball bang0..bang4
            final ExplosionParticle mainBang = new ExplosionParticle(
                center.x, center.y + 0.2D, center.z,
                0.0D, 0.04D, 0.0D,
                profile.flamingo() ? 15.0F : 10.0F, 0.8F,
                0.82F, 0.0F,
                1.0F, 0.70F, 0.36F,
                0.0F, 0.0F,
                0, 9,
                0.0F, 0.96F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                null, TEX_BANG_FRAMES, 2, 0.0F, 0.0F, 0.0F,
                5
            );
            mainBang.litFactor = 0.0F;
            appendParticle(particles, mainBang);

            // 3. Central shrinking bombflare
            final ExplosionParticle flareShrink = new ExplosionParticle(
                center.x, center.y + 0.2D, center.z,
                0.0D, 0.0D, 0.0D,
                profile.flamingo() ? 15.0F : 10.0F, 0.08F,
                0.58F, 0.0F,
                1.0F, 0.82F, 0.52F,
                0.0F, 0.0F,
                0, 5,
                0.0F, 1.0F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                TEX_FLASH_FLARE, null, 1, 0.0F, 0.0F, 0.0F,
                5
            );
            flareShrink.litFactor = 0.0F;
            appendParticle(particles, flareShrink);

            // 4. Subtle short crater glare
            final ExplosionParticle flareLinger = new ExplosionParticle(
                center.x, center.y + 0.1D, center.z,
                0.0D, 0.0D, 0.0D,
                3.0F, 0.15F,
                0.06F, 0.001F,
                0.92F, 0.68F, 0.38F,
                0.0F, 0.0F,
                0, 12,
                0.0F, 1.0F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                TEX_FLASH_FLARE, null, 1, 0.0F, 0.0F, 0.0F,
                5
            );
            flareLinger.litFactor = 0.0F;
            appendParticle(particles, flareLinger);

            if (!airburst) {
                final ExplosionParticle disc = new ExplosionParticle(
                    center.x, center.y + 0.05D, center.z,
                    0.0D, 0.0D, 0.0D,
                    profile.flamingo() ? 17.0F : 11.0F, 0.01F,
                    0.32F, 0.0F,
                    0.95F, 0.72F, 0.42F,
                    0.0F, 0.0F,
                    0, 4,
                    0.0F, 1.0F,
                    false, false,
                    ParticleOrientation.GROUND_ALIGNED, BlendMode.ADDITIVE,
                    TEX_FLASH_FLARE, null, 1, (float) normal.x, (float) normal.y, (float) normal.z,
                    4
                );
                disc.litFactor = 0.0F;
                appendParticle(particles, disc);
            }

            final ExplosionParticle flashL = new ExplosionParticle(
                center.x, center.y + 0.4D, center.z,
                0.0D, 0.0D, 0.0D,
                profile.flamingo() ? 18.0F : 12.0F, 0.10F,
                0.26F, 0.0F,
                1.0F, 0.78F, 0.46F,
                0.0F, 0.0F,
                0, 3,
                0.0F, 1.0F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                TEX_FLASH_64, null, 1, 0.0F, 0.0F, 0.0F,
                5
            );
            flashL.litFactor = 0.0F;
            appendParticle(particles, flashL);

            final ExplosionParticle flashM = new ExplosionParticle(
                center.x, center.y + 0.4D, center.z,
                0.0D, 0.0D, 0.0D,
                profile.flamingo() ? 12.0F : 8.0F, 0.10F,
                0.20F, 0.0F,
                1.0F, 0.78F, 0.46F,
                0.0F, 0.0F,
                0, 3,
                0.0F, 1.0F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                TEX_FLASH_64, null, 1, 0.0F, 0.0F, 0.0F,
                5
            );
            flashM.litFactor = 0.0F;
            appendParticle(particles, flashM);

            final ExplosionParticle farFlash = new ExplosionParticle(
                center.x, center.y + 0.6D, center.z,
                0.0D, 0.0D, 0.0D,
                profile.flamingo() ? 20.0F : 12.0F, 6.0F,
                0.10F, 0.01F,
                1.0F, 0.80F, 0.52F,
                0.0F, 0.0F,
                0, 4,
                0.0F, 1.0F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                TEX_FLASH_64, null, 1, 0.0F, 0.0F, 0.0F,
                5
            );
            farFlash.litFactor = 0.0F;
            appendParticle(particles, farFlash);

            // A few hot fragments escape the fireball without turning the blast into fireworks.
            for (int k = 0; k < qualityCount(profile.flamingo() ? 22 : 14, density); k++) {
                final double theta = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double spreadR = 0.2D + RANDOM.nextDouble() * 1.5D;
                final double sx = Math.cos(theta) * spreadR;
                final double sz = Math.sin(theta) * spreadR;
                final double sy = 1.2D + RANDOM.nextDouble() * 2.2D;
                final ResourceLocation sparkTex = TEX_BANG_FRAMES[RANDOM.nextInt(TEX_BANG_FRAMES.length)];
                final ExplosionParticle fSpark = new ExplosionParticle(
                    center.x + sx * 0.10D, center.y + 0.2D, center.z + sz * 0.10D,
                    sx * 0.22D, sy, sz * 0.22D,
                    0.95F, 0.001F,
                    1.0F, 0.0F,
                    1.0F, 0.98F, 0.90F,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.20F,
                    0, 8,
                    0.035F, 0.94F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                    sparkTex, null, 1, 0.0F, 0.0F, 0.0F,
                    5
                );
                fSpark.litFactor = 0.0F;
                appendParticle(particles, fSpark);
            }

            final ResourceLocation clusterTex1 = (RANDOM.nextBoolean()) ? TEX_SPARK_CLUSTERS[RANDOM.nextInt(TEX_SPARK_CLUSTERS.length)] : TEX_ALT_SMOKE[RANDOM.nextInt(TEX_ALT_SMOKE.length)];
            final ExplosionParticle cluster1 = new ExplosionParticle(
                center.x, center.y + 0.3D, center.z,
                0.0D, 0.0D, 0.0D,
                0.08F, 12.0F,
                1.0F, 0.0F,
                0.96F, 0.95F, 0.92F,
                0.0F, 0.0F,
                0, 5,
                0.0F, 1.0F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                clusterTex1, null, 1, 0.0F, 0.0F, 0.0F,
                5
            );
            cluster1.litFactor = 0.0F;
            appendParticle(particles, cluster1);

            final ResourceLocation clusterTex2 = (RANDOM.nextBoolean()) ? TEX_SPARK_CLUSTERS[RANDOM.nextInt(TEX_SPARK_CLUSTERS.length)] : TEX_ALT_SMOKE[RANDOM.nextInt(TEX_ALT_SMOKE.length)];
            final ExplosionParticle cluster2 = new ExplosionParticle(
                center.x, center.y + 0.3D, center.z,
                0.0D, 0.0D, 0.0D,
                0.08F, 7.5F,
                1.0F, 0.0F,
                0.96F, 0.95F, 0.92F,
                0.0F, 0.0F,
                0, 5,
                0.0F, 1.0F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                clusterTex2, null, 1, 0.0F, 0.0F, 0.0F,
                5
            );
            cluster2.litFactor = 0.0F;
            appendParticle(particles, cluster2);
        } else {
            final int petals = qualityCount(Math.max(4, Math.round(5.0F + profile.visualScale())), density);
            for (int i = 0; i < petals; i++) {
                final double angle = (double) i * Math.PI * 2.0D / petals + (RANDOM.nextDouble() - 0.5D) * 0.28D;
                final double vertical = (RANDOM.nextDouble() - 0.35D) * 0.55D;
                final Vec3 direction = new Vec3(Math.cos(angle), vertical, Math.sin(angle)).normalize();
                final double distance = (0.10D + RANDOM.nextDouble() * 0.48D) * Math.min(2.4D, profile.visualScale() * 0.55D);
                final Vec3 position = center.add(direction.scale(distance)).add(normal.scale(0.18D));
                final ExplosionParticle flash = new ExplosionParticle(
                    position.x, position.y, position.z,
                    direction.x * 0.12D, 0.04D + Math.abs(direction.y) * 0.08D, direction.z * 0.12D,
                    0.18F + profile.visualScale() * 0.035F,
                    (1.25F + RANDOM.nextFloat() * 0.85F) * profile.visualScale(),
                    0.55F + RANDOM.nextFloat() * 0.25F, 0.0F,
                    1.0F, 0.78F + RANDOM.nextFloat() * 0.18F, 0.42F,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.12F,
                    i % 3 == 0 ? 1 : 0, 4 + RANDOM.nextInt(4),
                    -0.002F, 0.94F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                    TEX_FLASH_64, null, 1, 0.0F, 0.0F, 0.0F,
                    5
                );
                flash.scaleGamma = 0.78F;
                flash.alphaGamma = 1.45F;
                flash.toRed = 0.95F;
                flash.toGreen = 0.20F;
                flash.toBlue = 0.025F;
                flash.litFactor = 0.0F;
                appendParticle(particles, flash);
            }

            final ExplosionParticle core = new ExplosionParticle(
                center.x + normal.x * 0.26D, center.y + normal.y * 0.26D + 0.08D, center.z + normal.z * 0.26D,
                0.0D, 0.06D, 0.0D,
                0.28F * profile.visualScale(), 1.65F * profile.visualScale(),
                0.88F, 0.0F,
                1.0F, 0.91F, 0.70F,
                0.0F, 0.0F,
                0, 5,
                0.0F, 0.90F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                TEX_FLASH_BRIGHT, null, 1, 0.0F, 0.0F, 0.0F,
                5
            );
            core.scaleGamma = 0.72F;
            core.alphaGamma = 1.55F;
            core.toRed = 0.92F;
            core.toGreen = 0.18F;
            core.toBlue = 0.02F;
            core.litFactor = 0.0F;
            appendParticle(particles, core);
        }
    }
    private static void spawnFireShell(
        final List<ExplosionParticle> particles,
        final Vec3 center,
        final Vec3 normal,
        final boolean airburst,
        final ExplosionVisualProfile profile,
        final float density
    ) {
        final int count = qualityCount(profile.fireParticles(), density);
        final float scale = profile.visualScale();
        for (int i = 0; i < count; i++) {
            final double theta = RANDOM.nextDouble() * Math.PI * 2.0D;
            final double z = RANDOM.nextDouble() * 2.0D - 1.0D;
            final double planar = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
            double dx = planar * Math.cos(theta);
            double dy = z;
            double dz = planar * Math.sin(theta);
            if (!airburst && dy < 0.0D) {
                dy = -dy * 0.30D + 0.12D;
            }
            final double length = Math.max(1.0E-5D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            dx /= length;
            dy /= length;
            dz /= length;
            final double distance = profile.heavy()
                ? (0.05D + RANDOM.nextDouble() * 0.30D) * Math.min(1.8D, scale * 0.35D)
                : (0.10D + RANDOM.nextDouble() * 0.55D) * Math.min(3.0D, scale * 0.55D);
            final Vec3 position = center.add(new Vec3(dx, dy, dz).scale(distance)).add(normal.scale(0.12D));
            final double speed = profile.heavy()
                ? (0.15D + RANDOM.nextDouble() * 0.40D) * Math.min(1.8D, Math.max(0.5D, scale * 0.35D))
                : (0.35D + RANDOM.nextDouble() * 1.35D) * Math.min(4.5D, Math.max(0.8D, scale * 0.90D));
            final Vec3 velocity = profile.heavy()
                ? new Vec3(dx * 0.45D, dy * 0.45D + 0.30D, dz * 0.45D).scale(speed).add(normal.scale(0.12D))
                : new Vec3(dx, dy + 0.20D, dz).scale(speed).add(normal.scale(0.16D));
            final float fireG = profile.heavy() ? (0.54F + RANDOM.nextFloat() * 0.24F) : (0.34F + RANDOM.nextFloat() * 0.46F);
            final float fireB = profile.heavy() ? (0.12F + RANDOM.nextFloat() * 0.18F) : (0.025F + RANDOM.nextFloat() * 0.10F);
            final ExplosionParticle fire = new ExplosionParticle(
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                profile.heavy() ? 0.20F + RANDOM.nextFloat() * 0.15F : (0.18F + RANDOM.nextFloat() * 0.22F + scale * 0.035F),
                profile.heavy() ? Math.min(3.5F, 0.60F + scale * 0.25F + RANDOM.nextFloat() * 0.35F) : Math.min(7.0F, 0.80F + scale * 0.42F + RANDOM.nextFloat() * 0.65F),
                0.72F + RANDOM.nextFloat() * 0.25F, 0.0F,
                1.0F, fireG, fireB,
                RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.18F,
                0, profile.heavy() ? (6 + RANDOM.nextInt(6)) : (9 + RANDOM.nextInt(13)),
                -0.006F, 0.93F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                null, TEX_FIRE_FRAMES, 2, 0.0F, 0.0F, 0.0F,
                5
            );
            fire.scaleGamma = 0.80F;
            fire.alphaGamma = 1.18F;
            fire.toRed = profile.heavy() ? 0.28F : 0.88F;
            fire.toGreen = profile.heavy() ? 0.26F : 0.08F;
            fire.toBlue = profile.heavy() ? 0.24F : 0.012F;
            // Mostly self luminous, but a little environment light keeps the
            // flame edges anchored to the blast light instead of floating.
            fire.litFactor = profile.heavy() ? 0.08F : 0.22F;
            // The fireball condenses: dying fire particles seed the soot column
            // that then rises as the mushroom cloud.
            fire.emitsSmoke = !profile.heavy() || i % 4 == 0;
            appendParticle(particles, fire);
        }

        if (profile.heavy()) {
            // Tight crater residual smolder: 6 brief ground embers
            for (int k = 0; k < 6; k++) {
                final double theta = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double dist = (0.1D + RANDOM.nextDouble() * 1.2D);
                final double fx = Math.cos(theta) * dist;
                final double fz = Math.sin(theta) * dist;
                final ExplosionParticle craterFire = new ExplosionParticle(
                    center.x + fx, center.y + 0.10D, center.z + fz,
                    (RANDOM.nextDouble() - 0.5D) * 0.04D, 0.08D + RANDOM.nextDouble() * 0.12D, (RANDOM.nextDouble() - 0.5D) * 0.04D,
                    0.65F + RANDOM.nextFloat() * 0.25F, 0.02F,
                    0.90F, 0.0F,
                    0.95F, 0.90F, 0.78F,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.10F,
                    0, 90 + RANDOM.nextInt(60),
                    -0.005F, 0.95F,
                    true, true,
                    ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                    null, TEX_FIRE_FRAMES, 3, 0.0F, 0.0F, 0.0F,
                    5
                );
                craterFire.toRed = 0.25F;
                craterFire.toGreen = 0.23F;
                craterFire.toBlue = 0.21F;
                craterFire.litFactor = 0.08F;
                appendParticle(particles, craterFire);
            }

            // Compact ballistic clumps
            for (int j = 0; j < 5; j++) {
                final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double radVel = (0.15D + RANDOM.nextDouble() * 0.40D);
                final double upVel = (0.50D + RANDOM.nextDouble() * 0.70D);
                final ExplosionParticle clump = new ExplosionParticle(
                    center.x + normal.x * 0.12D, center.y + normal.y * 0.12D + 0.10D, center.z + normal.z * 0.12D,
                    Math.cos(angle) * radVel, upVel, Math.sin(angle) * radVel,
                    0.80F, 0.10F,
                    0.90F, 0.0F,
                    0.95F, 0.88F, 0.72F,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.15F,
                    0, 70 + RANDOM.nextInt(50),
                    0.035F, 0.965F,
                    true, true,
                    ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                    null, TEX_FIRE_FRAMES, 3, 0.0F, 0.0F, 0.0F,
                    5
                );
                clump.toRed = 0.25F;
                clump.toGreen = 0.23F;
                clump.toBlue = 0.21F;
                clump.litFactor = 0.08F;
                appendParticle(particles, clump);
            }
        }
    }

    private static void spawnSmokeCloud(
        final List<ExplosionParticle> particles,
        final Vec3 center,
        final Vec3 normal,
        final boolean airburst,
        final ExplosionVisualProfile profile,
        final float density
    ) {
        final int count = qualityCount(profile.smokeParticles(), density);
        final float scale = Math.min(4.8F, profile.visualScale());
        final float plumeScale = profile.flamingo() ? 1.45F : 1.0F;
        final int plumeLifetime = profile.flamingo() ? 105 : 0;
        // Mushroom/stem caps are grounded shapes: a heavy AIRBURST must use
        // the spherical cloud so it never draws a floating mushroom in the sky.
        final boolean heavyGrounded = profile.heavy() && !airburst;

        if (heavyGrounded) {
            // A low fireball lifts a narrow column; the cap spreads after the pressure wave.
            final int capCount = Math.round(count * 0.40F);
            for (int i = 0; i < capCount; i++) {
                final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double radDist = (0.4D + RANDOM.nextDouble() * 1.8D) * plumeScale;
                final double height = (2.0D + RANDOM.nextDouble() * 2.5D) * plumeScale;
                final double cos = Math.cos(angle);
                final double sin = Math.sin(angle);
                final double radSpeed = (0.09D + RANDOM.nextDouble() * 0.16D) * plumeScale;
                final double rise = (0.36D + RANDOM.nextDouble() * 0.36D) * plumeScale;
                final double driftX = (RANDOM.nextDouble() - 0.5D) * 0.015D;
                final double driftZ = (RANDOM.nextDouble() - 0.5D) * 0.015D;
                final float[] col = FAB_SOOT_COLORS[RANDOM.nextInt(FAB_SOOT_COLORS.length)];
                final ResourceLocation tex = RANDOM.nextBoolean()
                    ? TEX_BIG_SMOKE[RANDOM.nextInt(TEX_BIG_SMOKE.length)]
                    : TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                final ExplosionParticle smoke = new ExplosionParticle(
                    center.x + cos * radDist, center.y + height, center.z + sin * radDist,
                    cos * radSpeed + driftX, rise, sin * radSpeed + driftZ,
                    (2.2F + RANDOM.nextFloat() * 0.8F) * plumeScale,
                    (7.0F + RANDOM.nextFloat() * 5.0F) * plumeScale,
                    0.58F + RANDOM.nextFloat() * 0.18F, 0.0F,
                    col[0], col[1], col[2],
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.025F,
                    0, 180 + plumeLifetime + RANDOM.nextInt(90),
                    -0.0012F, 0.982F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    tex, null, 1, 0.0F, 0.0F, 0.0F,
                    3
                );
                smoke.scaleGamma = 0.75F;
                smoke.alphaGamma = 1.25F;
                smoke.litFactor = 0.18F;
                smoke.toRed = col[0] * 0.85F;
                smoke.toGreen = col[1] * 0.85F;
                smoke.toBlue = col[2] * 0.85F;
                appendParticle(particles, smoke);
            }

            // The stem rises faster than the cap and remains visibly narrower.
            final int stemCount = Math.round(count * 0.25F);
            for (int i = 0; i < stemCount; i++) {
                final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double radDist = RANDOM.nextDouble() * 0.55D * plumeScale;
                final double height = (0.2D + RANDOM.nextDouble() * 1.8D) * plumeScale;
                final double cos = Math.cos(angle);
                final double sin = Math.sin(angle);
                final double radSpeed = (0.03D + RANDOM.nextDouble() * 0.06D) * plumeScale;
                final double rise = (0.48D + RANDOM.nextDouble() * 0.42D) * plumeScale;
                final float[] col = FAB_SOOT_COLORS[RANDOM.nextInt(FAB_SOOT_COLORS.length)];
                final ResourceLocation tex = TEX_BIG_SMOKE[RANDOM.nextInt(TEX_BIG_SMOKE.length)];
                final ExplosionParticle stem = new ExplosionParticle(
                    center.x + cos * radDist, center.y + height, center.z + sin * radDist,
                    cos * radSpeed, rise, sin * radSpeed,
                    (1.6F + RANDOM.nextFloat() * 0.6F) * plumeScale,
                    (4.0F + RANDOM.nextFloat() * 3.5F) * plumeScale,
                    0.64F + RANDOM.nextFloat() * 0.18F, 0.0F,
                    col[0], col[1], col[2],
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.02F,
                    0, 210 + plumeLifetime + RANDOM.nextInt(110),
                    -0.0014F, 0.983F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    tex, null, 1, 0.0F, 0.0F, 0.0F,
                    3
                );
                stem.scaleGamma = 0.80F;
                stem.alphaGamma = 1.20F;
                stem.litFactor = 0.18F;
                stem.toRed = col[0] * 0.80F;
                stem.toGreen = col[1] * 0.80F;
                stem.toBlue = col[2] * 0.80F;
                appendParticle(particles, stem);
            }

            // A fast, low rolling dust front reads as displaced ground material.
            final int discCount = Math.round(count * 0.35F);
            for (int i = 0; i < discCount; i++) {
                final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double radDist = (0.2D + RANDOM.nextDouble() * 1.8D) * plumeScale;
                final double height = 0.06D + RANDOM.nextDouble() * 0.18D;
                final double cos = Math.cos(angle);
                final double sin = Math.sin(angle);
                final double radSpeed = (0.34D + RANDOM.nextDouble() * 0.44D) * plumeScale;
                final double rise = 0.02D + RANDOM.nextDouble() * 0.08D;
                final float[] col = FAB_SOOT_COLORS[RANDOM.nextInt(FAB_SOOT_COLORS.length)];
                final ResourceLocation tex = (i % 2 == 0)
                    ? TEX_BIG_SMOKE[RANDOM.nextInt(TEX_BIG_SMOKE.length)]
                    : TEX_ALT_SMOKE[RANDOM.nextInt(TEX_ALT_SMOKE.length)];
                final ExplosionParticle groundRing = new ExplosionParticle(
                    center.x + cos * radDist, center.y + height, center.z + sin * radDist,
                    cos * radSpeed, rise, sin * radSpeed,
                    (2.0F + RANDOM.nextFloat() * 0.8F) * plumeScale,
                    (4.5F + RANDOM.nextFloat() * 3.5F) * plumeScale,
                    0.45F + RANDOM.nextFloat() * 0.20F, 0.0F,
                    col[0], col[1], col[2],
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.03F,
                    0, 105 + plumeLifetime / 2 + RANDOM.nextInt(65),
                    -0.0008F, 0.973F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    tex, null, 1, 0.0F, 0.0F, 0.0F,
                    3
                );
                groundRing.scaleGamma = 0.72F;
                groundRing.alphaGamma = 1.30F;
                groundRing.litFactor = 0.16F;
                appendParticle(particles, groundRing);
            }

            // Crater smoke stays behind when the expanding dust front has passed.
            for (int k = 0; k < (profile.flamingo() ? 14 : 8); k++) {
                final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double dist = RANDOM.nextDouble() * 0.9D;
                final float[] col = FAB_SOOT_COLORS[8]; // dark soot 2e2a28
                final ResourceLocation tex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                final ExplosionParticle smolder = new ExplosionParticle(
                    center.x + Math.cos(angle) * dist, center.y + 0.10D, center.z + Math.sin(angle) * dist,
                    (RANDOM.nextDouble() - 0.5D) * 0.03D, 0.04D + RANDOM.nextDouble() * 0.06D, (RANDOM.nextDouble() - 0.5D) * 0.03D,
                    1.4F + RANDOM.nextFloat() * 0.5F,
                    3.5F + RANDOM.nextFloat() * 1.8F,
                    0.85F, 0.001F,
                    col[0], col[1], col[2],
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.02F,
                    0, 180 + plumeLifetime + RANDOM.nextInt(90),
                    -0.0012F, 0.988F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    tex, null, 1, 0.0F, 0.0F, 0.0F,
                    3
                );
                smolder.scaleGamma = 0.78F;
                smolder.alphaGamma = 1.15F;
                smolder.litFactor = 0.12F;
                appendParticle(particles, smolder);
            }
            return;
        }

        for (int i = 0; i < count; i++) {
            final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            final double radialDistance;
            final double height;
            if (heavyGrounded) {
                final int phase = i % 3;
                if (phase == 0) {
                    radialDistance = RANDOM.nextDouble() * 1.2D * Math.min(2.5D, scale);
                    height = 0.18D + RANDOM.nextDouble() * 0.85D;
                } else if (phase == 1) {
                    // The cap is offset from the stem and expands more slowly,
                    // producing a readable mushroom silhouette instead of a
                    // uniform black sphere.
                    radialDistance = (0.8D + RANDOM.nextDouble() * 2.2D) * Math.min(2.4D, scale * 0.55D);
                    height = 1.4D + RANDOM.nextDouble() * 2.1D;
                } else {
                    radialDistance = (0.35D + RANDOM.nextDouble() * 1.1D) * Math.min(2.0D, scale * 0.5D);
                    height = 0.35D + RANDOM.nextDouble() * 1.2D;
                }
            } else if (airburst) {
                radialDistance = RANDOM.nextDouble() * 1.3D * scale;
                height = (RANDOM.nextDouble() - 0.35D) * 1.2D * scale;
            } else {
                radialDistance = RANDOM.nextDouble() * 1.2D * scale;
                height = 0.12D + RANDOM.nextDouble() * 0.75D * scale;
            }

            final double cos = Math.cos(angle);
            final double sin = Math.sin(angle);
            final Vec3 position = center.add(new Vec3(cos * radialDistance, height, sin * radialDistance));
            final double radialSpeed = (0.035D + RANDOM.nextDouble() * (heavyGrounded ? 0.22D : 0.11D)) * scale;
            final double rise = heavyGrounded
                ? (0.16D + RANDOM.nextDouble() * 0.42D)
                : (0.07D + RANDOM.nextDouble() * 0.18D);
            // Gentle net drift so the column bends with the weather instead of
            // rising as a perfectly rigid tube.
            final double driftX = (RANDOM.nextDouble() - 0.5D) * 0.012D * scale;
            final double driftZ = (RANDOM.nextDouble() - 0.5D) * 0.012D * scale;
            final Vec3 velocity = new Vec3(cos * radialSpeed + driftX, rise, sin * radialSpeed + driftZ);
            final float soot = 0.11F + RANDOM.nextFloat() * 0.16F;
            final int lifetime = (int) ((heavyGrounded ? 150.0F : 74.0F) + RANDOM.nextFloat() * (heavyGrounded ? 170.0F : 95.0F));
            final ExplosionParticle smoke = new ExplosionParticle(
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                0.85F + RANDOM.nextFloat() * 0.70F,
                Math.min(26.0F, (4.0F + RANDOM.nextFloat() * 4.2F) * Math.max(1.0F, scale * 0.95F)),
                0.30F + RANDOM.nextFloat() * 0.30F, 0.0F,
                soot, soot * 0.96F, soot * 0.90F,
                RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.035F,
                0, lifetime,
                heavyGrounded ? -0.0042F : -0.0020F, 0.988F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)], null, 1, 0.0F, 0.0F, 0.0F,
                3
            );
            smoke.scaleGamma = 0.78F;
            smoke.alphaGamma = 1.22F;
            smoke.litFactor = 0.16F;
            smoke.toRed = soot * 0.72F;
            smoke.toGreen = soot * 0.70F;
            smoke.toBlue = soot * 0.68F;
            appendParticle(particles, smoke);
        }
    }

    private static void spawnGroundDust(
        final List<ExplosionParticle> particles,
        final Vec3 center,
        final Vec3 normal,
        final boolean airburst,
        final byte materialType,
        final ExplosionVisualProfile profile,
        final float density
    ) {
        if (airburst) {
            return;
        }
        final int count = qualityCount(profile.dustParticles(), density);
        final MaterialPalette palette = paletteFor(materialType);
        final float scale = Math.min(4.0F, profile.visualScale());
        final float blastScale = profile.flamingo() ? 1.45F : 1.0F;

        if (profile.heavy()) {
            final int heavyCount = Math.max(70, qualityCount(profile.dustParticles(), density));
            for (int i = 0; i < heavyCount; i++) {
                final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double dist = (0.2D + RANDOM.nextDouble() * 1.2D) * blastScale;
                final double cos = Math.cos(angle);
                final double sin = Math.sin(angle);
                final double speed = (0.34D + RANDOM.nextDouble() * 0.50D) * blastScale;
                final ResourceLocation tex = (i % 2 == 0)
                    ? TEX_BIG_SMOKE[RANDOM.nextInt(TEX_BIG_SMOKE.length)]
                    : TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                final ExplosionParticle dust = new ExplosionParticle(
                    center.x + cos * dist, center.y + 0.08D + RANDOM.nextDouble() * 0.20D, center.z + sin * dist,
                    cos * speed, 0.05D + RANDOM.nextDouble() * 0.12D, sin * speed,
                    (1.4F + RANDOM.nextFloat() * 0.6F) * blastScale,
                    (3.5F + RANDOM.nextFloat() * 2.5F) * blastScale,
                    0.42F + RANDOM.nextFloat() * 0.18F, 0.0F,
                    palette.dustRed(), palette.dustGreen(), palette.dustBlue(),
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.03F,
                    0, (profile.flamingo() ? 145 : 90) + RANDOM.nextInt(70),
                    -0.0008F, 0.973F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    tex, null, 1, 0.0F, 0.0F, 0.0F,
                    2
                );
                dust.scaleGamma = 0.74F;
                dust.alphaGamma = 1.25F;
                dust.toRed = palette.dustRed() * 0.78F;
                dust.toGreen = palette.dustGreen() * 0.78F;
                dust.toBlue = palette.dustBlue() * 0.78F;
                appendParticle(particles, dust);
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            final double distance = (0.18D + RANDOM.nextDouble() * 1.15D) * scale;
            final double cos = Math.cos(angle);
            final double sin = Math.sin(angle);
            final Vec3 position = center.add(new Vec3(cos * distance, 0.08D + RANDOM.nextDouble() * 0.28D, sin * distance));
            final double speed = (0.18D + RANDOM.nextDouble() * 0.62D) * scale;
            final ExplosionParticle dust = new ExplosionParticle(
                position.x, position.y, position.z,
                cos * speed, 0.015D + RANDOM.nextDouble() * 0.05D, sin * speed,
                0.28F + RANDOM.nextFloat() * 0.38F,
                Math.min(10.0F, (1.8F + RANDOM.nextFloat() * 2.2F) * Math.max(0.8F, scale * 0.65F)),
                0.10F + RANDOM.nextFloat() * 0.16F, 0.0F,
                palette.dustRed(), palette.dustGreen(), palette.dustBlue(),
                RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.025F,
                0, 24 + RANDOM.nextInt(38),
                -0.0005F, 0.968F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)], null, 1, 0.0F, 0.0F, 0.0F,
                2
            );
            dust.scaleGamma = 0.86F;
            dust.alphaGamma = 1.22F;
            dust.toRed = palette.dustRed() * 0.72F;
            dust.toGreen = palette.dustGreen() * 0.72F;
            dust.toBlue = palette.dustBlue() * 0.72F;
            appendParticle(particles, dust);
        }
    }

    private static void spawnDebris(
        final List<ExplosionParticle> particles,
        final Vec3 center,
        final Vec3 normal,
        final byte materialType,
        final ExplosionVisualProfile profile,
        final float density
    ) {
        final int count = qualityCount(profile.debrisParticles(), density);
        final MaterialPalette palette = paletteFor(materialType);
        final float scale = Math.min(3.0F, profile.visualScale());

        if (profile.heavy()) {
            final int heavyCount = Math.max(45, qualityCount(profile.debrisParticles(), density));
            for (int i = 0; i < heavyCount; i++) {
                final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double speed = (0.20D + RANDOM.nextDouble() * 0.60D) * (profile.flamingo() ? 1.35D : 1.0D);
                final double upVel = (0.80D + RANDOM.nextDouble() * 1.40D) * (profile.flamingo() ? 1.25D : 1.0D);
                final Vec3 velocity = new Vec3(
                    Math.cos(angle) * speed,
                    upVel,
                    Math.sin(angle) * speed
                ).add(normal.scale(0.15D));
                final ResourceLocation texture = materialType == DroneExplosionPacket.MAT_WATER
                    ? TEX_SPLASH[RANDOM.nextInt(TEX_SPLASH.length)]
                    : TEX_DEBRIS[RANDOM.nextInt(TEX_DEBRIS.length)];
                final ExplosionParticle debris = new ExplosionParticle(
                    center.x + normal.x * 0.15D, center.y + normal.y * 0.15D + 0.12D, center.z + normal.z * 0.15D,
                    velocity.x, velocity.y, velocity.z,
                    0.60F + RANDOM.nextFloat() * 0.40F,
                    1.40F + RANDOM.nextFloat() * 0.60F,
                    0.88F, 0.0F,
                    palette.debrisRed(), palette.debrisGreen(), palette.debrisBlue(),
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.30F,
                    0, 55 + RANDOM.nextInt(35),
                    0.055F, 0.965F,
                    true, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    texture, null, 1, 0.0F, 0.0F, 0.0F,
                    2
                );
                debris.scaleGamma = 0.88F;
                debris.alphaGamma = 1.15F;
                debris.toRed = palette.debrisRed() * 0.62F;
                debris.toGreen = palette.debrisGreen() * 0.62F;
                debris.toBlue = palette.debrisBlue() * 0.62F;
                appendParticle(particles, debris);
            }
            return;
        }
        for (int i = 0; i < count; i++) {
            final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            final double speed = (0.22D + RANDOM.nextDouble() * 0.72D) * Math.max(0.7D, scale * 0.65D);
            final Vec3 velocity = new Vec3(
                Math.cos(angle) * speed,
                (0.28D + RANDOM.nextDouble() * 0.95D) * Math.max(0.55D, scale * 0.55D),
                Math.sin(angle) * speed
            ).add(normal.scale(0.12D));
            final ResourceLocation texture = materialType == DroneExplosionPacket.MAT_WATER
                ? TEX_SPLASH[RANDOM.nextInt(TEX_SPLASH.length)]
                : TEX_DEBRIS[RANDOM.nextInt(TEX_DEBRIS.length)];
            final ExplosionParticle debris = new ExplosionParticle(
                center.x + normal.x * 0.12D, center.y + normal.y * 0.12D + 0.12D, center.z + normal.z * 0.12D,
                velocity.x, velocity.y, velocity.z,
                0.24F + RANDOM.nextFloat() * 0.42F,
                0.55F + RANDOM.nextFloat() * 0.95F,
                0.76F, 0.0F,
                palette.debrisRed(), palette.debrisGreen(), palette.debrisBlue(),
                RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.34F,
                0, 28 + RANDOM.nextInt(42),
                0.060F, 0.958F,
                true, false,
                ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                texture, null, 1, 0.0F, 0.0F, 0.0F,
                2
            );
            debris.scaleGamma = 0.92F;
            debris.alphaGamma = 1.12F;
            debris.toRed = palette.debrisRed() * 0.58F;
            debris.toGreen = palette.debrisGreen() * 0.58F;
            debris.toBlue = palette.debrisBlue() * 0.58F;
            appendParticle(particles, debris);
        }
    }

    private static void spawnSparkShrapnel(
        final List<ExplosionParticle> particles,
        final Vec3 center,
        final Vec3 normal,
        final boolean airburst,
        final byte materialType,
        final ExplosionVisualProfile profile,
        final float density
    ) {
        final int count = qualityCount(profile.sparkParticles(), density);
        final float speedScale = Math.min(3.5F, Math.max(0.85F, profile.visualScale() * 0.60F));

        if (profile.heavy()) {
            final int heavySparks = Math.max(65, count);
            for (int i = 0; i < heavySparks; i++) {
                final double theta = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double z = RANDOM.nextDouble() * 2.0D - 1.0D;
                final double planar = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
                double dx = planar * Math.cos(theta);
                double dy = Math.abs(z);
                double dz = planar * Math.sin(theta);
                if (normal.lengthSqr() > 1.0E-4D && i % 4 == 0) {
                    dx = normal.x + (RANDOM.nextDouble() - 0.5D) * 0.40D;
                    dy = normal.y + 0.35D + RANDOM.nextDouble() * 0.40D;
                    dz = normal.z + (RANDOM.nextDouble() - 0.5D) * 0.40D;
                }
                final double len = Math.max(1.0E-5D, Math.sqrt(dx * dx + dy * dy + dz * dz));
                dx /= len;
                dy /= len;
                dz /= len;
                final double horizSpeed = (0.20D + RANDOM.nextDouble() * 0.70D) * (profile.flamingo() ? 1.35D : 1.0D);
                final double vertSpeed = (1.20D + RANDOM.nextDouble() * 1.50D) * (profile.flamingo() ? 1.25D : 1.0D);
                final ExplosionParticle spark = new ExplosionParticle(
                    center.x + normal.x * 0.15D, center.y + normal.y * 0.15D + 0.12D, center.z + normal.z * 0.15D,
                    dx * horizSpeed, dy * 0.40D + vertSpeed, dz * horizSpeed,
                    0.22F + RANDOM.nextFloat() * 0.25F, 0.04F,
                    1.0F, 0.0F,
                    1.0F, 0.72F, 0.38F,
                    0.0F, 0.0F,
                    0, 40 + RANDOM.nextInt(30),
                    0.040F, 0.955F,
                    true, i % 5 == 0,
                    ParticleOrientation.MOTION, BlendMode.ADDITIVE,
                    null, TEX_SPARK_CLUSTERS, 2, 0.0F, 0.0F, 0.0F,
                    5
                );
                spark.scaleGamma = 0.74F;
                spark.alphaGamma = 1.08F;
                spark.toRed = 0.65F;
                spark.toGreen = 0.62F;
                spark.toBlue = 0.58F;
                spark.litFactor = 0.05F;
                appendParticle(particles, spark);
            }

            // A few slower embers remain after the initial burst.
            for (int k = 0; k < (profile.flamingo() ? 12 : 8); k++) {
                final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double sp = 0.10D + RANDOM.nextDouble() * 0.30D;
                final ResourceLocation tex = TEX_ALT_SMOKE[RANDOM.nextInt(TEX_ALT_SMOKE.length)];
                final ExplosionParticle ember = new ExplosionParticle(
                    center.x, center.y + 0.25D, center.z,
                    Math.cos(angle) * sp, 0.80D + RANDOM.nextDouble() * 0.60D, Math.sin(angle) * sp,
                    1.2F, 2.0F,
                    0.90F, 0.0F,
                    0.96F, 0.95F, 0.90F,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.05F,
                    0, 90,
                    0.015F, 0.975F,
                    false, true,
                    ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                    tex, null, 1, 0.0F, 0.0F, 0.0F,
                    5
                );
                ember.scaleGamma = 0.85F;
                ember.alphaGamma = 1.15F;
                ember.toRed = 0.55F;
                ember.toGreen = 0.52F;
                ember.toBlue = 0.48F;
                ember.litFactor = 0.08F;
                appendParticle(particles, ember);
            }

            // Vertical spark column
            final ExplosionParticle vCol = new ExplosionParticle(
                center.x, center.y + 0.25D, center.z,
                (RANDOM.nextDouble() - 0.5D) * 0.04D, 1.6D, (RANDOM.nextDouble() - 0.5D) * 0.04D,
                2.2F, 1.6F,
                0.90F, 0.0F,
                0.98F, 0.96F, 0.92F,
                0.0F, 0.0F,
                0, 50,
                0.010F, 0.982F,
                false, true,
                ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                null, TEX_SPARK_CLUSTERS, 2, 0.0F, 0.0F, 0.0F,
                5
            );
            vCol.toRed = 0.60F;
            vCol.toGreen = 0.58F;
            vCol.toBlue = 0.55F;
            vCol.litFactor = 0.08F;
            appendParticle(particles, vCol);
            return;
        }
        for (int i = 0; i < count; i++) {
            double dx;
            double dy;
            double dz;
            if (!airburst && materialType != DroneExplosionPacket.MAT_WATER && normal.lengthSqr() > 1.0E-4D && i % 3 == 0) {
                dx = normal.x + (RANDOM.nextDouble() - 0.5D) * 0.85D;
                dy = normal.y + (RANDOM.nextDouble() - 0.5D) * 0.85D + 0.12D;
                dz = normal.z + (RANDOM.nextDouble() - 0.5D) * 0.85D;
            } else {
                final double theta = RANDOM.nextDouble() * Math.PI * 2.0D;
                final double z = RANDOM.nextDouble() * 2.0D - 1.0D;
                final double planar = Math.sqrt(Math.max(0.0D, 1.0D - z * z));
                dx = planar * Math.cos(theta);
                dy = z;
                dz = planar * Math.sin(theta);
                if (!airburst && dy < 0.0D) {
                    dy = -dy * 0.25D;
                }
            }
            final double length = Math.max(1.0E-5D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            dx /= length;
            dy /= length;
            dz /= length;
            final double speed = (0.75D + RANDOM.nextDouble() * 2.35D) * speedScale;
            final ExplosionParticle spark = new ExplosionParticle(
                center.x + normal.x * 0.22D, center.y + normal.y * 0.22D + 0.18D, center.z + normal.z * 0.22D,
                dx * speed, dy * speed + 0.08D, dz * speed,
                0.11F + RANDOM.nextFloat() * 0.16F, 0.025F,
                0.95F, 0.0F,
                1.0F, 0.72F + RANDOM.nextFloat() * 0.24F, 0.18F + RANDOM.nextFloat() * 0.18F,
                0.0F, 0.0F,
                0, 12 + RANDOM.nextInt(20),
                profile.heavy() ? 0.04F : 0.025F, 0.94F,
                profile.heavy(), profile.heavy(),
                ParticleOrientation.MOTION, BlendMode.ADDITIVE,
                null, profile.heavy() ? TEX_SPARK_CLUSTERS : TEX_SPARK_FRAMES, 2, 0.0F, 0.0F, 0.0F,
                5
            );
            spark.scaleGamma = 0.74F;
            spark.alphaGamma = 1.08F;
            spark.toRed = 0.95F;
            spark.toGreen = 0.18F;
            spark.toBlue = 0.025F;
            spark.litFactor = 0.10F;
            appendParticle(particles, spark);
        }
    }

    private static MaterialPalette paletteFor(final byte materialType) {
        return switch (materialType) {
            case DroneExplosionPacket.MAT_WATER -> new MaterialPalette(0.72F, 0.86F, 0.96F, 0.62F, 0.78F, 0.90F);
            case DroneExplosionPacket.MAT_SAND -> new MaterialPalette(0.86F, 0.76F, 0.50F, 0.78F, 0.68F, 0.45F);
            case DroneExplosionPacket.MAT_STONE -> new MaterialPalette(0.62F, 0.64F, 0.66F, 0.52F, 0.54F, 0.56F);
            case DroneExplosionPacket.MAT_WOOD -> new MaterialPalette(0.55F, 0.36F, 0.20F, 0.42F, 0.29F, 0.18F);
            default -> new MaterialPalette(0.46F, 0.34F, 0.23F, 0.38F, 0.28F, 0.19F);
        };
    }

    /**
     * Registers the real illumination of a detonation.
     *
     * <p>Instead of one flash frame plus a weak stub, three sources cooperate:
     * a dynamic fireball core that expands and decays over its whole life, a
     * long cool afterglow that keeps the ground lit while the fireball
     * collapses, and a wide low source inside the rising column so the smoke
     * cloud is lit from within rather than appearing as a black blob.</p>
     */
    private static void registerExplosionLights(
        final Vec3 center,
        final Vec3 normal,
        final byte materialType,
        final ExplosionVisualProfile profile
    ) {
        final Vec3 lightPosition = center.add(normal.scale(0.40D)).add(0.0D, 0.65D, 0.0D);
        final boolean water = materialType == DroneExplosionPacket.MAT_WATER;
        final float coreRed = water ? 1.45F : (profile.heavy() ? 3.40F : 5.10F);
        final float coreGreen = water ? 2.05F : (profile.heavy() ? 1.75F : 1.25F);
        final float coreBlue = water ? 2.65F : (profile.heavy() ? 0.52F : 0.20F);
        final int coreTicks = profile.heavy() ? (profile.flamingo() ? 8 : 6) : 10;
        final int afterglowTicks = profile.heavy() ? (profile.flamingo() ? 44 : 30) : 40;

        // Fireball core: the primary explosion light.  Dynamic envelope means
        // this genuinely brightens the terrain and nearby smoke for the whole
        // burn instead of one frame of flash.
        VfxLightingRegistry.addTransientLight(
            lightPosition,
            coreRed,
            coreGreen,
            coreBlue,
            profile.lightIntensity() * 1.20F,
            profile.lightRadius() * 0.95F,
            coreTicks,
            true
        );

        // Cool afterglow: keeps the crater and smoke lit while everything cools down
        VfxLightingRegistry.addTransientLight(
            lightPosition,
            water ? 0.46F : (profile.heavy() ? 0.82F : 1.85F),
            water ? 0.72F : (profile.heavy() ? 0.42F : 0.40F),
            water ? 1.05F : (profile.heavy() ? 0.17F : 0.075F),
            profile.lightIntensity() * (profile.heavy() ? 0.17F : 0.36F),
            profile.lightRadius() * 0.82F,
            afterglowTicks,
            true
        );

        // Column source: wide, dim, centred in the rising smoke so the plume is
        // lit from inside as it climbs away from the fireball.
        final double columnHeight = Math.min(22.0D, profile.visualScale() * 2.8D);
        VfxLightingRegistry.addTransientLight(
            center.add(0.0D, columnHeight, 0.0D),
            water ? 0.36F : (profile.heavy() ? 0.46F : 1.30F),
            water ? 0.55F : (profile.heavy() ? 0.30F : 0.34F),
            water ? 0.82F : (profile.heavy() ? 0.18F : 0.09F),
            profile.lightIntensity() * (profile.heavy() ? 0.18F : 0.46F),
            profile.lightRadius() * 1.35F,
            afterglowTicks,
            false
        );
    }

    private static void playExplosionSounds(
        final Minecraft minecraft,
        final double x,
        final double y,
        final double z,
        final byte matType,
        final float scaleMul
    ) {
        if (minecraft.player == null) return;
        final double distSqr = minecraft.player.distanceToSqr(x, y, z);
        final float dist = (float) Math.sqrt(distSqr);

        // Close impact sound
        final SoundEvent closeSound = FullfudRegistries.EXPLOSION_OCP_HEAVY.get();
        if (closeSound != null) {
            final float vol = Math.min(10.0F, 2.5F * scaleMul);
            minecraft.getSoundManager().play(new SimpleSoundInstance(
                closeSound.getLocation(), SoundSource.NEUTRAL, vol, 0.95F + (RANDOM.nextFloat() - 0.5F) * 0.1F,
                RandomSource.create(), false, 0, net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR,
                x, y, z, false
            ));
        }

        // Distant rumble (if beyond 40 blocks, heard up to 1200 blocks)
        if (dist > 35.0F) {
            final SoundEvent distantSound = FullfudRegistries.EXPLOSION_OCP_DISTANT.get();
            if (distantSound != null) {
                final float distVol = Math.min(16.0F, 4.0F * scaleMul);
                minecraft.getSoundManager().play(new SimpleSoundInstance(
                    distantSound.getLocation(), SoundSource.NEUTRAL, distVol, 1.0F,
                    RandomSource.create(), false, 0, net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR,
                    x, y, z, false
                ));
            }
        }

        // Debris settle sound (delayed 12-18 ticks)
        final SoundEvent settleSound;
        switch (matType) {
            case DroneExplosionPacket.MAT_SAND -> settleSound = FullfudRegistries.DEBRIS_SETTLE_SAND.get();
            case DroneExplosionPacket.MAT_STONE -> settleSound = FullfudRegistries.DEBRIS_SETTLE_STONE.get();
            case DroneExplosionPacket.MAT_WATER -> settleSound = FullfudRegistries.DEBRIS_SETTLE_WATER.get();
            case DroneExplosionPacket.MAT_WOOD -> settleSound = FullfudRegistries.DEBRIS_SETTLE_WOOD.get();
            default -> settleSound = FullfudRegistries.DEBRIS_SETTLE_DIRT.get();
        }

        if (settleSound != null) {
            synchronized (DELAYED_SOUNDS) {
                DELAYED_SOUNDS.add(new DelayedSound(settleSound, x, y, z, 1.2F, 1.0F, 14 + RANDOM.nextInt(6)));
            }
        }

        // Lingering fire crackle at impact
        if (scaleMul >= 1.5F) {
            final SoundEvent fireSound = FullfudRegistries.FIRE_CRACKLE_OCP.get();
            if (fireSound != null) {
                minecraft.getSoundManager().play(new SimpleSoundInstance(
                    fireSound.getLocation(), SoundSource.AMBIENT, 1.0F, 1.0F,
                    RandomSource.create(), false, 0, net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR,
                    x, y, z, false
                ));
            }
        }
    }

    /**
     * World-space FP-5 nozzle offset for the given orientation.  The transform
     * order mirrors {@code Fp5FlamingoRenderer} (roll around Z, then pitch
     * around X, then yaw of 180 - yaw) so the engine light, the plume and the
     * rendered model can never disagree about where the exhaust exits.
     */
    public static Vec3 nozzleOffset(final boolean booster, final float yaw, final float pitch, final float roll) {
        final double yLocal = booster ? 2.3564D : 4.3252D;
        final double zLocal = booster ? 2.8174D : 5.9815D;
        final double rollRad = Math.toRadians((double) roll);
        final double pitchRad = Math.toRadians((double) pitch);
        final double yawRad = Math.toRadians((double) yaw);
        final double x1 = -yLocal * Math.sin(rollRad);
        final double y1 = yLocal * Math.cos(rollRad);
        final double z1 = zLocal;
        final double y2 = y1 * Math.cos(pitchRad) - z1 * Math.sin(pitchRad);
        final double z2 = y1 * Math.sin(pitchRad) + z1 * Math.cos(pitchRad);
        final double cosYaw = Math.cos(yawRad);
        final double sinYaw = Math.sin(yawRad);
        return new Vec3(
            x1 * (-cosYaw) + z2 * sinYaw,
            y2,
            -x1 * sinYaw + z2 * (-cosYaw)
        );
    }

    public static void spawnFlamingoExhaust(
        final double prevX, final double prevY, final double prevZ,
        final double currX, final double currY, final double currZ,
        final Vec3 forward,
        final float yaw,
        final float pitch,
        final float roll,
        final boolean booster
    ) {
        final List<ExplosionParticle> newParticles = new ArrayList<>();
        final double dx = currX - prevX;
        final double dy = currY - prevY;
        final double dz = currZ - prevZ;
        final double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        final int steps = Math.max(1, (int) Math.ceil(dist / 0.8D));
        final double stepX = dx / (double) steps;
        final double stepY = dy / (double) steps;
        final double stepZ = dz / (double) steps;

        // Exact model-space nozzle positions derived from GeckoLib geometry and renderer scaling (SCALE = 2.25F):
        // Booster nozzle: Y_local = (21.763 / 16) * 2.25 = 3.0604m, Z_local = (46.375 / 16) * 2.25 = 6.5215m
        // Jet nozzle:     Y_local = (30.757 / 16) * 2.25 = 4.3252m, Z_local = (42.535 / 16) * 2.25 = 5.9815m
        final double yLocal = booster ? 3.0604D : 4.3252D;
        final double zLocal = booster ? 6.5215D : 5.9815D;

        final double rollRad = Math.toRadians((double) roll);
        final double pitchRad = Math.toRadians((double) pitch);
        final double yawRad = Math.toRadians((double) yaw);

        // 1. Roll rotation around Z (matching poseStack.mulPose(Axis.ZP.rotationDegrees(roll)))
        final double x1 = -yLocal * Math.sin(rollRad);
        final double y1 =  yLocal * Math.cos(rollRad);
        final double z1 =  zLocal;

        // 2. Pitch rotation around X (matching poseStack.mulPose(Axis.XP.rotationDegrees(pitch)))
        final double x2 = x1;
        final double y2 = y1 * Math.cos(pitchRad) - z1 * Math.sin(pitchRad);
        final double z2 = y1 * Math.sin(pitchRad) + z1 * Math.cos(pitchRad);

        // 3. Yaw rotation around Y (matching poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw)))
        final double cosYaw = Math.cos(yawRad);
        final double sinYaw = Math.sin(yawRad);
        final double ox = x2 * (-cosYaw) + z2 * sinYaw;
        final double oy = y2;
        final double oz = -x2 * sinYaw + z2 * (-cosYaw);

        final Vec3 exhaustOffset = new Vec3(ox, oy, oz);
        final Vec3 exhaustBackVel = forward.scale(booster ? -0.42D : -0.28D);

        for (int i = 0; i <= steps; i++) {
            final double px = prevX + stepX * i + exhaustOffset.x;
            final double py = prevY + stepY * i + exhaustOffset.y;
            final double pz = prevZ + stepZ * i + exhaustOffset.z;

            if (booster) {
                // Expanding dense white/silver smoke plume (solid rocket booster contrail)
                final ResourceLocation smokeTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                final float soot = 0.90F + RANDOM.nextFloat() * 0.08F;
                final double svx = exhaustBackVel.x + (RANDOM.nextDouble() - 0.5D) * 0.06D;
                final double svy = exhaustBackVel.y + (RANDOM.nextDouble() - 0.5D) * 0.06D;
                final double svz = exhaustBackVel.z + (RANDOM.nextDouble() - 0.5D) * 0.06D;
                final int smokeLifetime = 90 + RANDOM.nextInt(40);

                newParticles.add(new ExplosionParticle(
                    px, py, pz,
                    svx, svy, svz,
                    1.6F, 6.8F,
                    0.92F, 0.0F,
                    soot, soot, soot,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.03F,
                    0, smokeLifetime,
                    -0.0012F, 0.985F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    smokeTex, null, 1, 0, 0, 0,
                    3
                ));

                // Blazing fire core at nozzle
                if (i % 2 == 0) {
                    newParticles.add(new ExplosionParticle(
                        px, py, pz,
                        exhaustBackVel.x * 1.2D, exhaustBackVel.y * 1.2D, exhaustBackVel.z * 1.2D,
                        2.2F, 0.6F,
                        1.0F, 0.0F,
                        1.0F, 0.88F, 0.55F,
                        RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.15F,
                        0, 10 + RANDOM.nextInt(6),
                        -0.002F, 0.95F,
                        false, false,
                        ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                        null, TEX_FIRE_FRAMES, 2, 0, 0, 0,
                        1
                    ));
                }

                // Ballistic combustion sparks
                if (i % 3 == 0) {
                    newParticles.add(new ExplosionParticle(
                        px, py, pz,
                        exhaustBackVel.x * 1.5D + (RANDOM.nextDouble() - 0.5D) * 0.25D,
                        exhaustBackVel.y * 1.5D + (RANDOM.nextDouble() - 0.5D) * 0.25D,
                        exhaustBackVel.z * 1.5D + (RANDOM.nextDouble() - 0.5D) * 0.25D,
                        0.8F, 0.1F,
                        1.0F, 0.0F,
                        1.0F, 0.9F, 0.45F,
                        0.0F, 0.0F,
                        0, 15 + RANDOM.nextInt(12),
                        0.03F, 0.96F,
                        false, false,
                        ParticleOrientation.MOTION, BlendMode.ADDITIVE,
                        null, TEX_SPARK_FRAMES, 2, 0, 0, 0,
                        1
                    ));
                }
            } else {
                // Cruise Jet contrail (semi-transparent expanding aerodynamic contrail)
                if (i % 2 == 0) {
                    final ResourceLocation smokeTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                    final float soot = 0.85F + RANDOM.nextFloat() * 0.1F;
                    newParticles.add(new ExplosionParticle(
                        px, py, pz,
                        exhaustBackVel.x * 0.6D, exhaustBackVel.y * 0.6D, exhaustBackVel.z * 0.6D,
                        0.9F, 3.2F,
                        0.55F, 0.0F,
                        soot, soot, soot,
                        RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.02F,
                        0, 60 + RANDOM.nextInt(25),
                        -0.0005F, 0.985F,
                        false, false,
                        ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                        smokeTex, null, 1, 0, 0, 0,
                        3
                    ));
                }
            }
        }

        enqueueParticles(newParticles);
    }

    /**
     * Engine exhaust smoke puff trail for propeller-driven Shahed-136.
     * Emits realistic piston engine smoke puffs with full partial-tick interpolation.
     */
    public static void spawnShahed136Exhaust(
        final Vec3 prevAnchor,
        final Vec3 currAnchor,
        final Vec3 forward,
        final float speed,
        final boolean damaged
    ) {
        if (prevAnchor == null || currAnchor == null || forward == null) {
            return;
        }
        final double dx = currAnchor.x - prevAnchor.x;
        final double dy = currAnchor.y - prevAnchor.y;
        final double dz = currAnchor.z - prevAnchor.z;
        final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance) || distance < 0.001D) {
            return;
        }
        final int steps = Math.max(1, Math.min(8, (int) Math.ceil(distance / 0.55D)));
        final double stepX = dx / (double) steps;
        final double stepY = dy / (double) steps;
        final double stepZ = dz / (double) steps;

        final double backSpeed = 0.08D + Math.min(0.12D, speed * 0.04D);
        final List<ExplosionParticle> newParticles = new ArrayList<>(steps + 2);

        final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        final net.minecraft.world.entity.Entity cameraEntity = (minecraft != null) ? minecraft.getCameraEntity() : null;
        final net.minecraft.client.Camera mainCamera = (minecraft != null && minecraft.gameRenderer != null) ? minecraft.gameRenderer.getMainCamera() : null;
        final Vec3 camPos = (mainCamera != null) ? mainCamera.getPosition() : null;
        final Vec3 camForward = (mainCamera != null) ? Vec3.directionFromRotation(mainCamera.getXRot(), mainCamera.getYRot()).normalize() : null;

        final boolean isFirstPersonCockpit = (camPos != null && camForward != null) &&
            ((cameraEntity instanceof com.fullfud.fullfud.common.entity.ShahedDroneEntity)
                || (camForward.dot(forward) > 0.65D && camPos.distanceToSqr(currAnchor) < 16.0D));

        for (int i = 0; i <= steps; i++) {
            final double px = prevAnchor.x + stepX * i;
            final double py = prevAnchor.y + stepY * i;
            final double pz = prevAnchor.z + stepZ * i;

            if (isFirstPersonCockpit && camPos != null && camForward != null) {
                final double toX = px - camPos.x;
                final double toY = py - camPos.y;
                final double toZ = pz - camPos.z;
                final double forwardProj = toX * camForward.x + toY * camForward.y + toZ * camForward.z;
                if (forwardProj > -1.0D) {
                    continue;
                }
            }

            final double jitterX = (RANDOM.nextDouble() - 0.5D) * 0.04D;
            final double jitterY = (RANDOM.nextDouble() - 0.5D) * 0.04D;
            final double jitterZ = (RANDOM.nextDouble() - 0.5D) * 0.04D;

            final ResourceLocation smokeTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
            final float baseColor = damaged ? (0.15F + RANDOM.nextFloat() * 0.15F) : (0.60F + RANDOM.nextFloat() * 0.15F);
            final float startAlpha = damaged ? (0.60F + RANDOM.nextFloat() * 0.20F) : (0.22F + RANDOM.nextFloat() * 0.10F);
            final float initialScale = damaged ? (0.65F + RANDOM.nextFloat() * 0.20F) : (0.42F + RANDOM.nextFloat() * 0.15F);
            final float targetScale = damaged ? (2.70F + RANDOM.nextFloat() * 0.90F) : (1.65F + RANDOM.nextFloat() * 0.50F);

            final ExplosionParticle smokePuff = new ExplosionParticle(
                px + jitterX, py + jitterY, pz + jitterZ,
                -forward.x * backSpeed + (RANDOM.nextDouble() - 0.5D) * 0.02D,
                -forward.y * backSpeed + 0.01D + (RANDOM.nextDouble() - 0.5D) * 0.015D,
                -forward.z * backSpeed + (RANDOM.nextDouble() - 0.5D) * 0.02D,
                initialScale, targetScale,
                startAlpha, 0.0F,
                baseColor, baseColor, baseColor,
                RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.03F,
                0, damaged ? (80 + RANDOM.nextInt(60)) : (45 + RANDOM.nextInt(35)),
                -0.0008F, 0.980F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                smokeTex, null, 1, 0.0F, 0.0F, 0.0F,
                2
            );
            smokePuff.scaleGamma = 0.85F;
            smokePuff.alphaGamma = 1.05F;
            newParticles.add(smokePuff);
        }

        enqueueParticles(newParticles);
    }

    /**
     * Semi-transparent soft jet exhaust contrail for Shahed-238.
     * Emits a clean, non-harsh, translucent smoke stream from the rear jet nozzle.
     */
    public static void spawnShahed238JetExhaust(
        final Vec3 prevAnchor,
        final Vec3 currAnchor,
        final Vec3 forward,
        final float speed
    ) {
        if (prevAnchor == null || currAnchor == null || forward == null) {
            return;
        }
        final double dx = currAnchor.x - prevAnchor.x;
        final double dy = currAnchor.y - prevAnchor.y;
        final double dz = currAnchor.z - prevAnchor.z;
        final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance) || distance < 0.001D) {
            return;
        }
        final int steps = Math.max(1, Math.min(10, (int) Math.ceil(distance / 0.45D)));
        final double stepX = dx / (double) steps;
        final double stepY = dy / (double) steps;
        final double stepZ = dz / (double) steps;

        final double backSpeed = 0.14D + Math.min(0.18D, speed * 0.06D);
        final List<ExplosionParticle> newParticles = new ArrayList<>(steps + 3);
        Vec3 right = forward.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (right.lengthSqr() < 0.01D) {
            right = forward.cross(new Vec3(1.0D, 0.0D, 0.0D));
        }
        right = right.normalize();
        final Vec3 up = right.cross(forward).normalize();

        final net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        final net.minecraft.world.entity.Entity cameraEntity = (minecraft != null) ? minecraft.getCameraEntity() : null;
        final net.minecraft.client.Camera mainCamera = (minecraft != null && minecraft.gameRenderer != null) ? minecraft.gameRenderer.getMainCamera() : null;
        final Vec3 camPos = (mainCamera != null) ? mainCamera.getPosition() : null;
        final Vec3 camForward = (mainCamera != null) ? Vec3.directionFromRotation(mainCamera.getXRot(), mainCamera.getYRot()).normalize() : null;

        final boolean isFirstPersonCockpit = camPos != null && camForward != null
            && camPos.distanceToSqr(currAnchor) < 16.0D
            && (cameraEntity instanceof com.fullfud.fullfud.common.entity.Shahed238DroneEntity
                || camForward.dot(forward) > 0.65D);

        for (int i = 0; i <= steps; i++) {
            final double px = prevAnchor.x + stepX * i;
            final double py = prevAnchor.y + stepY * i;
            final double pz = prevAnchor.z + stepZ * i;

            if (isFirstPersonCockpit && camPos != null && camForward != null) {
                final double toX = px - camPos.x;
                final double toY = py - camPos.y;
                final double toZ = pz - camPos.z;
                final double forwardProj = toX * camForward.x + toY * camForward.y + toZ * camForward.z;
                if (forwardProj > -1.0D) {
                    continue;
                }
            }

            final float spread = i % 4 == 0 ? 0.22F : (i % 3 == 0 ? 0.12F : 0.045F);
            final double jitterX = (RANDOM.nextDouble() - 0.5D) * spread;
            final double jitterY = (RANDOM.nextDouble() - 0.5D) * spread;
            final double jitterZ = (RANDOM.nextDouble() - 0.5D) * spread;
            final int strand = i % 3 - 1;
            final double strandSpread = (0.10D + (steps - i) * 0.018D) * strand;
            final double ribbonWave = Math.sin((px + py + pz) * 0.7D + i * 1.8D) * 0.05D;

            final ResourceLocation smokeTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
            final float baseColor = (i % 5 == 0 ? 0.48F : i % 3 == 0 ? 0.63F : 0.78F) + RANDOM.nextFloat() * 0.08F;
            final float startAlpha = (i % 4 == 0 ? 0.22F : 0.32F) + RANDOM.nextFloat() * 0.10F;

            final ExplosionParticle smokePuff = new ExplosionParticle(
                px + right.x * strandSpread + up.x * ribbonWave + jitterX,
                py + right.y * strandSpread + up.y * ribbonWave + jitterY,
                pz + right.z * strandSpread + up.z * ribbonWave + jitterZ,
                -forward.x * backSpeed + right.x * strand * 0.014D + (RANDOM.nextDouble() - 0.5D) * 0.025D,
                -forward.y * backSpeed + right.y * strand * 0.014D + 0.012D + (RANDOM.nextDouble() - 0.5D) * 0.015D,
                -forward.z * backSpeed + right.z * strand * 0.014D + (RANDOM.nextDouble() - 0.5D) * 0.025D,
                ((i % 4 == 0 ? 0.33F : 0.50F) + RANDOM.nextFloat() * 0.22F),
                ((i % 3 == 0 ? 2.60F : 1.85F) + RANDOM.nextFloat() * 0.75F),
                startAlpha, 0.0F,
                baseColor, baseColor * 0.98F, baseColor * 0.96F,
                RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.03F,
                0, 60 + RANDOM.nextInt(40),
                -0.0010F, 0.978F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                smokeTex, null, 1, 0.0F, 0.0F, 0.0F,
                2
            );
            smokePuff.scaleGamma = 0.85F;
            smokePuff.alphaGamma = 1.05F;
            newParticles.add(smokePuff);

            if (i >= steps - 1) {
                // Subtle warm nozzle glow directly inside the tailpipe
                final ExplosionParticle nozzleGlow = new ExplosionParticle(
                    px, py, pz,
                    -forward.x * backSpeed * 0.5D, -forward.y * backSpeed * 0.5D, -forward.z * backSpeed * 0.5D,
                    0.22F, 0.10F,
                    0.30F, 0.0F,
                    1.0F, 0.75F, 0.40F,
                    0.0F, 0.0F,
                    0, 2,
                    -0.002F, 0.94F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                    TEX_FLASH_64, null, 1, 0.0F, 0.0F, 0.0F,
                    0
                );
                nozzleGlow.litFactor = 0.0F;
                newParticles.add(nozzleGlow);
            }
        }

        enqueueParticles(newParticles);
    }

    /**
     * Nozzle-anchored exhaust.  Both ends of the segment are already world-space
     * nozzle positions (the same ones the engine light uses), so the plume, the
     * light and the model can never drift apart into the "smoke from nowhere"
     * look the two-position centre-based reconstruction produced.
     *
     * <p>Smoke is deliberately dense and long lived: a booster leaves a thick
     * opaque column that slowly thins, and the particles are environment lit so
     * the exhaust glow bleeds into the plume instead of only onto the ground.</p>
     */
    public static void spawnFlamingoExhaustFromNozzle(
        final Vec3 prevAnchor,
        final Vec3 currAnchor,
        final Vec3 forward,
        final boolean booster,
        final float speed
    ) {
        if (prevAnchor == null || currAnchor == null || forward == null) {
            return;
        }
        final double dx = currAnchor.x - prevAnchor.x;
        final double dy = currAnchor.y - prevAnchor.y;
        final double dz = currAnchor.z - prevAnchor.z;
        final double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Double.isFinite(distance)) {
            return;
        }
        final int steps = Math.max(1, Math.min(24, (int) Math.ceil(distance / 0.7D)));
        final double stepX = dx / (double) steps;
        final double stepY = dy / (double) steps;
        final double stepZ = dz / (double) steps;

        final double speedFactor = Mth.clamp(speed / 3.0D, 0.0D, 1.6D);
        final double backSpeed = booster ? 0.52D + speedFactor * 0.18D : 0.34D + speedFactor * 0.10D;
        // The plume is intentionally dense, so scale the secondary layers with
        // the configured quality tier to keep low-end clients playable.
        final float density = particleDensity();
        final List<ExplosionParticle> newParticles = new ArrayList<>(steps * (booster ? 5 : 3) + 8);

        for (int i = 0; i <= steps; i++) {
            final double px = prevAnchor.x + stepX * i;
            final double py = prevAnchor.y + stepY * i;
            final double pz = prevAnchor.z + stepZ * i;
            // Slight inboard pull keeps the near-nozzle puffs hugging the
            // airframe before they are dragged backwards.
            final double spread = 0.10D + speedFactor * 0.06D;

            if (booster) {
                // Two-layer dense smoke: a dark sooty core wrapped in a bright
                // silver skirt, which reads as a real solid-motor contrail.
                final double jitterX = (RANDOM.nextDouble() - 0.5D) * spread;
                final double jitterY = (RANDOM.nextDouble() - 0.5D) * spread;
                final double jitterZ = (RANDOM.nextDouble() - 0.5D) * spread;
                final ResourceLocation coreTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                final float coreSoot = 0.10F + RANDOM.nextFloat() * 0.08F;
                final ExplosionParticle core = new ExplosionParticle(
                    px + jitterX, py + jitterY, pz + jitterZ,
                    -forward.x * backSpeed + (RANDOM.nextDouble() - 0.5D) * 0.10D,
                    -forward.y * backSpeed - 0.03D + (RANDOM.nextDouble() - 0.5D) * 0.08D,
                    -forward.z * backSpeed + (RANDOM.nextDouble() - 0.5D) * 0.10D,
                    1.10F + RANDOM.nextFloat() * 0.55F, 9.5F + RANDOM.nextFloat() * 5.0F,
                    0.62F + RANDOM.nextFloat() * 0.22F, 0.0F,
                    coreSoot, coreSoot, coreSoot,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.05F,
                    0, 95 + RANDOM.nextInt(55),
                    -0.0022F, 0.986F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    coreTex, null, 1, 0.0F, 0.0F, 0.0F,
                    3
                );
                core.scaleGamma = 0.80F;
                core.alphaGamma = 1.25F;
                newParticles.add(core);

                final ResourceLocation skirtTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                final float skirt = 0.72F + RANDOM.nextFloat() * 0.22F;
                final ExplosionParticle skirtPuff = new ExplosionParticle(
                    px - jitterX * 1.4D, py - jitterY * 0.6D, pz - jitterZ * 1.4D,
                    -forward.x * backSpeed * 0.72D + (RANDOM.nextDouble() - 0.5D) * 0.14D,
                    -forward.y * backSpeed * 0.72D + 0.05D + (RANDOM.nextDouble() - 0.5D) * 0.10D,
                    -forward.z * backSpeed * 0.72D + (RANDOM.nextDouble() - 0.5D) * 0.14D,
                    1.45F + RANDOM.nextFloat() * 0.85F, 13.0F + RANDOM.nextFloat() * 6.5F,
                    0.42F + RANDOM.nextFloat() * 0.24F, 0.0F,
                    skirt, skirt * 0.985F, skirt * 0.965F,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.03F,
                    0, 120 + RANDOM.nextInt(70),
                    -0.0016F, 0.989F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    skirtTex, null, 1, 0.0F, 0.0F, 0.0F,
                    3
                );
                skirtPuff.scaleGamma = 0.76F;
                skirtPuff.alphaGamma = 1.18F;
                if (density > 0.55F) {
                    newParticles.add(skirtPuff);
                }

                // Tight nozzle flame jet: short-lived flame tongue right at the
                // exhaust exit that quickly turns into smoke.  NO trailing flash
                // billboards (TEX_FLASH_64 / TEX_FLASH_BRIGHT) following the missile!
                if (i <= 1) {
                    final ExplosionParticle flame = new ExplosionParticle(
                        px, py, pz,
                        -forward.x * backSpeed * 0.95D, -forward.y * backSpeed * 0.95D - 0.01D, -forward.z * backSpeed * 0.95D,
                        0.95F, 0.30F,
                        1.0F, 0.0F,
                        1.0F, 0.88F, 0.52F,
                        RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.18F,
                        0, 3 + RANDOM.nextInt(2),
                        -0.002F, 0.94F,
                        false, false,
                        ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                        null, TEX_FIRE_FRAMES, 2, 0.0F, 0.0F, 0.0F,
                        1
                    );
                    flame.litFactor = 0.15F;
                    newParticles.add(flame);
                }

                if (density > 0.75F && i % 2 == 0) {
                    final ExplosionParticle spark = new ExplosionParticle(
                        px, py, pz,
                        -forward.x * backSpeed * 1.6D + (RANDOM.nextDouble() - 0.5D) * 0.30D,
                        -forward.y * backSpeed * 1.6D + (RANDOM.nextDouble() - 0.5D) * 0.30D,
                        -forward.z * backSpeed * 1.6D + (RANDOM.nextDouble() - 0.5D) * 0.30D,
                        0.85F, 0.15F,
                        1.0F, 0.0F,
                        1.0F, 0.9F, 0.45F,
                        0.0F, 0.0F,
                        0, 16 + RANDOM.nextInt(12),
                        0.03F, 0.96F,
                        false, false,
                        ParticleOrientation.MOTION, BlendMode.ADDITIVE,
                        null, TEX_SPARK_FRAMES, 2, 0.0F, 0.0F, 0.0F,
                        1
                    );
                    spark.litFactor = 0.08F;
                    newParticles.add(spark);
                }
            } else {
                // Cruise contrail: tighter, cooler, but still environment lit so
                // it catches the engine glow at night.
                final ResourceLocation smokeTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                final float soot = 0.62F + RANDOM.nextFloat() * 0.24F;
                final ExplosionParticle contrail = new ExplosionParticle(
                    px, py, pz,
                    -forward.x * backSpeed + (RANDOM.nextDouble() - 0.5D) * 0.06D,
                    -forward.y * backSpeed - 0.01D + (RANDOM.nextDouble() - 0.5D) * 0.05D,
                    -forward.z * backSpeed + (RANDOM.nextDouble() - 0.5D) * 0.06D,
                    1.05F + RANDOM.nextFloat() * 0.55F, 5.5F + RANDOM.nextFloat() * 4.0F,
                    0.30F + RANDOM.nextFloat() * 0.22F, 0.0F,
                    soot, soot, soot,
                    RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.03F,
                    0, 68 + RANDOM.nextInt(36),
                    -0.0012F, 0.987F,
                    false, false,
                    ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                    smokeTex, null, 1, 0.0F, 0.0F, 0.0F,
                    3
                );
                contrail.scaleGamma = 0.82F;
                contrail.alphaGamma = 1.24F;
                newParticles.add(contrail);

                if (i <= 1) {
                    final ExplosionParticle nozzleGlow = new ExplosionParticle(
                        px, py, pz,
                        -forward.x * backSpeed * 0.6D, -forward.y * backSpeed * 0.6D, -forward.z * backSpeed * 0.6D,
                        0.45F, 0.15F,
                        0.35F, 0.0F,
                        0.95F, 0.55F, 0.28F,
                        0.0F, 0.0F,
                        0, 3 + RANDOM.nextInt(2),
                        -0.002F, 0.94F,
                        false, false,
                        ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
                        TEX_FLASH_64, null, 1, 0.0F, 0.0F, 0.0F,
                        0
                    );
                    nozzleGlow.litFactor = 0.0F;
                    newParticles.add(nozzleGlow);
                }
            }
        }

        enqueueParticles(newParticles);
    }

    public static void spawnFlamingoLaunchPadCloud(final double x, final double y, final double z, final Vec3 forward) {
        final List<ExplosionParticle> padParticles = new ArrayList<>();
        padParticles.add(new ExplosionParticle(
            x, y + 0.2D, z,
            0.0D, 0.02D, 0.0D,
            1.0F, 8.0F,
            1.0F, 0.0F,
            1.0F, 0.9F, 0.7F,
            0.0F, 0.0F,
            0, 8,
            0.0F, 0.98F,
            false, false,
            ParticleOrientation.BILLBOARD, BlendMode.ADDITIVE,
            TEX_FLASH_FLARE, null, 1, 0, 0, 0, 0
        ));
        // Ignition flare: a wide, short, blinding white-hot source so the pad
        // and the surrounding structures are lit hard for the first second of
        // the launch, then it decays into the steady engine light.
        VfxLightingRegistry.addTransientLight(
            new Vec3(x, y + 1.2D, z),
            6.4F, 4.8F, 3.0F,
            15.5F,
            30.0F,
            45,
            true
        );

        for (int i = 0; i < 24; i++) {
            final double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            final double radius = 0.5D + RANDOM.nextDouble() * 2.0D;
            final double sx = x + Math.cos(angle) * radius;
            final double sz = z + Math.sin(angle) * radius;
            final double sy = y + 0.2D + RANDOM.nextDouble() * 0.8D;
            final double svx = (Math.cos(angle) * 0.08D - forward.x * 0.12D) + (RANDOM.nextDouble() - 0.5D) * 0.05D;
            final double svy = 0.08D + RANDOM.nextDouble() * 0.14D;
            final double svz = (Math.sin(angle) * 0.08D - forward.z * 0.12D) + (RANDOM.nextDouble() - 0.5D) * 0.05D;
            final ResourceLocation smokeTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
            final float soot = 0.88F + RANDOM.nextFloat() * 0.1F;
            padParticles.add(new ExplosionParticle(
                sx, sy, sz,
                svx, svy, svz,
                2.2F, 6.8F,
                0.90F, 0.0F,
                soot, soot, soot,
                RANDOM.nextFloat() * 6.28F, (RANDOM.nextFloat() - 0.5F) * 0.03F,
                0, 100 + RANDOM.nextInt(60),
                -0.001F, 0.980F,
                false, false,
                ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                smokeTex, null, 1, 0, 0, 0, 3
            ));
        }
        enqueueParticles(padParticles);
    }

    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        final Level level = minecraft.level;

        // 1. Process delayed sounds safely
        synchronized (DELAYED_SOUNDS) {
            DELAYED_SOUNDS.removeIf(ds -> {
                ds.delayTicks--;
                if (ds.delayTicks <= 0) {
                    if (minecraft.player != null) {
                        minecraft.getSoundManager().play(new SimpleSoundInstance(
                            ds.sound.getLocation(), SoundSource.NEUTRAL, ds.volume, ds.pitch,
                            RandomSource.create(), false, 0, net.minecraft.client.resources.sounds.SoundInstance.Attenuation.LINEAR,
                            ds.x, ds.y, ds.z, false
                        ));
                    }
                    return true;
                }
                return false;
            });
        }

        if (level == null) {
            return;
        }

        // 2. Process active particles safely with O(N) compaction
        final List<ExplosionParticle> toAdd = new ArrayList<>();
        synchronized (PARTICLES) {
            if (PARTICLES.isEmpty()) {
                return;
            }

            final int currentCount = PARTICLES.size();
            PARTICLES.removeIf(p -> {
                p.prevX = p.x;
                p.prevY = p.y;
                p.prevZ = p.z;

                p.x += p.vx;
                p.y += p.vy;
                p.z += p.vz;

                p.vy -= p.gravity;
                p.vx *= p.drag;
                p.vy *= p.drag;
                p.vz *= p.drag;

                p.rot += p.rotVel;

                // Ground collision
                if (p.stopsOnGround && p.vy < 0.0D) {
                    final BlockPos bp = BlockPos.containing(p.x, p.y - 0.15D, p.z);
                    if (level.hasChunkAt(bp) && !level.getBlockState(bp).isAir()) {
                        p.vy = 0.0D;
                        p.vx *= 0.5D;
                        p.vz *= 0.5D;
                        p.stopsOnGround = false; // settled
                    }
                }

                // Subparticle smoke emission capped at MAX_PARTICLES
                if (p.emitsSmoke && p.age % 2 == 0 && p.age < p.maxAge * 0.75F && (currentCount + toAdd.size()) < MAX_PARTICLES) {
                    final ResourceLocation smokeTex = TEX_SMOKE_CLUSTERS[RANDOM.nextInt(TEX_SMOKE_CLUSTERS.length)];
                    toAdd.add(new ExplosionParticle(
                        p.x, p.y, p.z,
                        (RANDOM.nextDouble() - 0.5D) * 0.03D, 0.02D + RANDOM.nextDouble() * 0.03D, (RANDOM.nextDouble() - 0.5D) * 0.03D,
                        0.6F, 2.2F,
                        0.6F, 0.0F,
                        0.3F, 0.3F, 0.3F,
                        RANDOM.nextFloat() * 6.28F, 0.02F,
                        0, 30 + RANDOM.nextInt(20),
                        -0.001F, 0.98F,
                        false, false,
                        ParticleOrientation.BILLBOARD, BlendMode.ALPHA,
                        smokeTex, null, 1, 0, 0, 0,
                        3
                    ));
                }

                p.age++;
                return p.age >= p.maxAge;
            });

            if (!toAdd.isEmpty()) {
                PARTICLES.addAll(toAdd);
            }
        }
    }

    public static void clear() {
        synchronized (PARTICLES) {
            PARTICLES.clear();
        }
        synchronized (DELAYED_SOUNDS) {
            DELAYED_SOUNDS.clear();
        }
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            clear();
        }
    }

    public static void onRenderLevelStage(final RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        boolean hasParticles;
        synchronized (PARTICLES) {
            hasParticles = !PARTICLES.isEmpty();
        }
        if (!hasParticles) {
            return;
        }

        final Camera camera = event.getCamera();
        final Vec3 camPos = camera.getPosition();
        final PoseStack poseStack = event.getPoseStack();
        final float partialTicks = event.getPartialTick();
        final Frustum frustum = event.getFrustum();

        // Render Additive Particles first (Glow / Flash / Sparks / Fire)
        renderBatch(poseStack, camPos, camera, partialTicks, BlendMode.ADDITIVE, frustum);

        // Render Alpha Particles second (Shockwaves / Smoke / Debris)
        renderBatch(poseStack, camPos, camera, partialTicks, BlendMode.ALPHA, frustum);
    }

    public static void safeRotateTo(final Quaternionf dest, final Vector3f from, final Vector3f to) {
        if (dest == null) {
            return;
        }
        if (from == null || to == null) {
            dest.identity();
            return;
        }
        final float fromLenSq = from.lengthSquared();
        final float toLenSq = to.lengthSquared();
        if (!Float.isFinite(fromLenSq) || !Float.isFinite(toLenSq) || fromLenSq < 1.0E-4F || toLenSq < 1.0E-4F) {
            dest.identity();
            return;
        }
        // Normalized local copies: JOML rotateTo mutates its arguments and its
        // degenerate behaviour is version-dependent, so the shortest-arc
        // rotation is derived here with a plain axis-angle construction.
        final float fromLen = (float) Math.sqrt(fromLenSq);
        final float toLen = (float) Math.sqrt(toLenSq);
        final float fx = from.x / fromLen;
        final float fy = from.y / fromLen;
        final float fz = from.z / fromLen;
        final float tx = to.x / toLen;
        final float ty = to.y / toLen;
        final float tz = to.z / toLen;
        if (!Float.isFinite(fx) || !Float.isFinite(fy) || !Float.isFinite(fz)
            || !Float.isFinite(tx) || !Float.isFinite(ty) || !Float.isFinite(tz)) {
            dest.identity();
            return;
        }
        final float dot = fx * tx + fy * ty + fz * tz;
        if (!Float.isFinite(dot)) {
            dest.identity();
            return;
        }
        if (dot > 0.999999F) {
            dest.identity();
            return;
        }
        if (dot < -0.999999F) {
            // Antiparallel: rotate PI around any axis orthogonal to `from`.
            float ox = 0.0F;
            float oy = 0.0F;
            float oz = 1.0F;
            float crossLen = 0.0F;
            if (Math.abs(fz) < 0.9F) {
                ox = fy * 0.0F - fz * 1.0F;
                oy = fz * 0.0F - fx * 0.0F;
                oz = fx * 1.0F - fy * 0.0F;
                crossLen = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            }
            if (crossLen < 1.0E-4F) {
                // Fallback axis: X cross from (robust unless from ~ +/-X).
                ox = 0.0F;
                oy = fz;
                oz = -fy;
                crossLen = (float) Math.sqrt(ox * ox + oy * oy + oz * oz);
            }
            if (crossLen < 1.0E-4F) {
                ox = 0.0F;
                oy = 0.0F;
                oz = 1.0F;
                crossLen = 1.0F;
            }
            dest.setAngleAxis((float) Math.PI, ox / crossLen, oy / crossLen, oz / crossLen);
            return;
        }
        // Half-way vector gives the shortest arc without library behaviour.
        final float hx = fx + tx;
        final float hy = fy + ty;
        final float hz = fz + tz;
        final float halfLenSq = hx * hx + hy * hy + hz * hz;
        if (!Float.isFinite(halfLenSq) || halfLenSq < 1.0E-8F) {
            dest.identity();
            return;
        }
        // axis = from x half (unit because |from| = |to| = 1), angle from dot.
        float ax = fy * hz - fz * hy;
        float ay = fz * hx - fx * hz;
        float az = fx * hy - fy * hx;
        final float axisLen = (float) Math.sqrt(ax * ax + ay * ay + az * az);
        if (!Float.isFinite(axisLen) || axisLen < 1.0E-6F) {
            dest.identity();
            return;
        }
        ax /= axisLen;
        ay /= axisLen;
        az /= axisLen;
        final float angle = (float) Math.atan2(axisLen, dot);
        dest.setAngleAxis(angle, ax, ay, az);
    }

    private static void renderBatch(
        final PoseStack poseStack,
        final Vec3 camPos,
        final Camera camera,
        final float partialTicks,
        final BlendMode targetBlend,
        @javax.annotation.Nullable final Frustum frustum
    ) {
        final List<ExplosionParticle> candidates = new ArrayList<>();
        synchronized (PARTICLES) {
            for (final ExplosionParticle p : PARTICLES) {
                if (p.blendMode == targetBlend && p.getActiveTexture() != null) {
                    candidates.add(p);
                }
            }
        }

        if (candidates.isEmpty()) {
            return;
        }

        // Sort by render layer then texture location to batch draw calls
        candidates.sort((p1, p2) -> {
            int cmp = Integer.compare(p1.renderLayer, p2.renderLayer);
            if (cmp != 0) return cmp;
            ResourceLocation t1 = p1.getActiveTexture();
            ResourceLocation t2 = p2.getActiveTexture();
            if (t1 == null && t2 == null) return 0;
            if (t1 == null) return -1;
            if (t2 == null) return 1;
            return t1.compareTo(t2);
        });

        RenderSystem.enableBlend();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();

        if (targetBlend == BlendMode.ADDITIVE) {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        }

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        ResourceLocation currentTex = null;
        BufferBuilder builder = null;
        boolean isBuilding = false;
        final float nightBoost = VfxRenderPipeline.nightBoost();
        final float dayFactor = VfxRenderPipeline.dayFactor();

        for (final ExplosionParticle p : candidates) {
            final ResourceLocation activeTex = p.getActiveTexture();

            final double worldX = Mth.lerp(partialTicks, p.prevX, p.x);
            final double worldY = Mth.lerp(partialTicks, p.prevY, p.y);
            final double worldZ = Mth.lerp(partialTicks, p.prevZ, p.z);

            final double px = worldX - camPos.x;
            final double py = worldY - camPos.y;
            final double pz = worldZ - camPos.z;

            // Distance culling: skip particles beyond 4096 blocks
            final double distSq = px * px + py * py + pz * pz;
            if (distSq > 4096.0D * 4096.0D) {
                continue;
            }

            final float progress = Math.min(1.0F, Math.max(0.0F, ((float) p.age + partialTicks) / (float) p.maxAge));
            final float scaleProgress = (float) Math.pow(progress, Mth.clamp(p.scaleGamma, 0.05F, 8.0F));
            final float alphaProgress = (float) Math.pow(progress, Mth.clamp(p.alphaGamma, 0.05F, 8.0F));
            final float curScale = Mth.lerp(scaleProgress, p.scale, p.toScale);
            final float curAlpha = Math.max(0.0F, Math.min(1.0F, Mth.lerp(alphaProgress, p.alpha, p.toAlpha)));

            final float baseR = Mth.lerp(scaleProgress, p.r, p.toRed);
            final float baseG = Mth.lerp(scaleProgress, p.g, p.toGreen);
            final float baseB = Mth.lerp(scaleProgress, p.b, p.toBlue);

            // Custom-light shading.  Vanilla particle shaders only know the
            // block lightmap, so smoke behind a booster used to stay black while
            // the engine light illuminated the ground next to it.  Sampling the
            // floating point light field here folds engine/explosion radiance
            // into the billboard colour: the plume now glows from the nozzle
            // outward and fades naturally at distance and during the day.
            final float lit = Mth.clamp(p.litFactor, 0.0F, 1.0F);
            VfxLightingRegistry.sampleRadiance(worldX, worldY, worldZ, nightBoost, LIGHT_SAMPLE);
            final float ambient = Mth.lerp(lit, 1.0F, 0.34F + dayFactor * 0.66F);
            final float shadedR = baseR * ambient + LIGHT_SAMPLE[0] * lit;
            final float shadedG = baseG * ambient + LIGHT_SAMPLE[1] * lit;
            final float shadedB = baseB * ambient + LIGHT_SAMPLE[2] * lit;

            final int r = Mth.clamp((int) (shadedR * 255.0F), 0, 255);
            final int g = Mth.clamp((int) (shadedG * 255.0F), 0, 255);
            final int b = Mth.clamp((int) (shadedB * 255.0F), 0, 255);
            final int a = Mth.clamp((int) (curAlpha * 255.0F), 0, 255);

            if (a <= 2) {
                continue;
            }

            final float half = curScale * 0.5F;

            // Frustum culling: skip particles outside player's camera viewport
            if (frustum != null) {
                if (!frustum.isVisible(new AABB(worldX - half, worldY - half, worldZ - half, worldX + half, worldY + half, worldZ + half))) {
                    continue;
                }
            }

            if (!activeTex.equals(currentTex)) {
                if (isBuilding) {
                    BufferUploader.drawWithShader(builder.end());
                    isBuilding = false;
                }
                currentTex = activeTex;
                RenderSystem.setShaderTexture(0, currentTex);
                builder = Tesselator.getInstance().getBuilder();
                builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                isBuilding = true;
            }

            poseStack.pushPose();
            poseStack.translate(px, py, pz);

            if (p.orientation == ParticleOrientation.GROUND_ALIGNED) {
                final Vector3f up = new Vector3f(0.0F, 1.0F, 0.0F);
                final Vector3f norm = new Vector3f(p.normX, p.normY, p.normZ);
                if (norm.lengthSquared() <= 0.01F) {
                    poseStack.popPose();
                    continue;
                }
                norm.normalize();
                final Quaternionf q = new Quaternionf();
                safeRotateTo(q, up, norm);
                poseStack.mulPose(q);
                appendQuad(builder, poseStack, -half, 0.03F, -half, half, 0.03F, half, true, r, g, b, a);
            } else if (p.orientation == ParticleOrientation.MOTION) {
                final Vec3 v = new Vec3(p.vx, p.vy, p.vz);
                if (v.lengthSqr() > 1.0E-4D) {
                    final Vec3 normV = v.normalize();
                    final Quaternionf q = new Quaternionf();
                    safeRotateTo(q, new Vector3f(0.0F, 1.0F, 0.0F), new Vector3f((float) normV.x, (float) normV.y, (float) normV.z));
                    poseStack.mulPose(q);
                }
                final float thickness = half * 0.35F;
                final float length = half * 2.5F;
                appendQuad(builder, poseStack, -thickness, -length, 0.0F, thickness, length, 0.0F, false, r, g, b, a);
            } else {
                poseStack.mulPose(camera.rotation());
                if (Math.abs(p.rot) > 1.0E-4F) {
                    poseStack.mulPose(Axis.ZP.rotation(p.rot));
                }
                appendQuad(builder, poseStack, -half, -half, 0.0F, half, half, 0.0F, false, r, g, b, a);
            }

            poseStack.popPose();
        }

        if (isBuilding) {
            BufferUploader.drawWithShader(builder.end());
        }

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.defaultBlendFunc();
    }

    private static void appendQuad(
        final BufferBuilder builder,
        final PoseStack poseStack,
        final float minX, final float minY, final float minZ,
        final float maxX, final float maxY, final float maxZ,
        final boolean horizontal,
        final int r, final int g, final int b, final int a
    ) {
        final Matrix4f pose = poseStack.last().pose();
        if (horizontal) {
            builder.vertex(pose, minX, minY, minZ).uv(0.0F, 0.0F).color(r, g, b, a).endVertex();
            builder.vertex(pose, minX, maxY, maxZ).uv(0.0F, 1.0F).color(r, g, b, a).endVertex();
            builder.vertex(pose, maxX, maxY, maxZ).uv(1.0F, 1.0F).color(r, g, b, a).endVertex();
            builder.vertex(pose, maxX, minY, minZ).uv(1.0F, 0.0F).color(r, g, b, a).endVertex();
        } else {
            builder.vertex(pose, minX, minY, minZ).uv(0.0F, 1.0F).color(r, g, b, a).endVertex();
            builder.vertex(pose, maxX, minY, minZ).uv(1.0F, 1.0F).color(r, g, b, a).endVertex();
            builder.vertex(pose, maxX, maxY, maxZ).uv(1.0F, 0.0F).color(r, g, b, a).endVertex();
            builder.vertex(pose, minX, maxY, maxZ).uv(0.0F, 0.0F).color(r, g, b, a).endVertex();
        }
    }

    public enum ParticleOrientation {
        BILLBOARD,
        GROUND_ALIGNED,
        MOTION
    }

    public enum BlendMode {
        ADDITIVE,
        ALPHA
    }

    public static class ExplosionParticle {
        public double x, y, z;
        public double prevX, prevY, prevZ;
        public double vx, vy, vz;
        public float scale, toScale;
        public float alpha, toAlpha;
        public float r, g, b;
        public float toRed, toGreen, toBlue;
        public float scaleGamma = 1.0F;
        public float alphaGamma = 1.0F;
        public float rot, rotVel;
        public int age, maxAge;
        public float gravity, drag;
        public boolean stopsOnGround;
        public boolean emitsSmoke;
        public ParticleOrientation orientation;
        public BlendMode blendMode;
        public ResourceLocation staticTexture;
        public ResourceLocation[] animFrames;
        public int frameDelay;
        public float normX, normY, normZ;
        public int renderLayer;
        /**
         * How strongly this particle reacts to the custom light field.
         * 1.0 = fully environment lit (smoke, dust, debris), 0.0 = self
         * luminous (fire, flash, sparks).  This is what makes engine and
         * explosion light actually land on the plume instead of the smoke
         * staying a flat dark billboard at night.
         */
        public float litFactor = 1.0F;

        public ExplosionParticle(
            final double x, final double y, final double z,
            final double vx, final double vy, final double vz,
            final float scale, final float toScale,
            final float alpha, final float toAlpha,
            final float r, final float g, final float b,
            final float rot, final float rotVel,
            final int age, final int maxAge,
            final float gravity, final float drag,
            final boolean stopsOnGround, final boolean emitsSmoke,
            final ParticleOrientation orientation, final BlendMode blendMode,
            final ResourceLocation staticTexture, final ResourceLocation[] animFrames,
            final int frameDelay, final float normX, final float normY, final float normZ
        ) {
            this(x, y, z, vx, vy, vz, scale, toScale, alpha, toAlpha, r, g, b, rot, rotVel, age, maxAge, gravity, drag, stopsOnGround, emitsSmoke, orientation, blendMode, staticTexture, animFrames, frameDelay, normX, normY, normZ, 0);
        }

        public ExplosionParticle(
            final double x, final double y, final double z,
            final double vx, final double vy, final double vz,
            final float scale, final float toScale,
            final float alpha, final float toAlpha,
            final float r, final float g, final float b,
            final float rot, final float rotVel,
            final int age, final int maxAge,
            final float gravity, final float drag,
            final boolean stopsOnGround, final boolean emitsSmoke,
            final ParticleOrientation orientation, final BlendMode blendMode,
            final ResourceLocation staticTexture, final ResourceLocation[] animFrames,
            final int frameDelay, final float normX, final float normY, final float normZ,
            final int renderLayer
        ) {
            this.x = x; this.y = y; this.z = z;
            this.prevX = x; this.prevY = y; this.prevZ = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.scale = scale; this.toScale = toScale;
            this.alpha = alpha; this.toAlpha = toAlpha;
            this.r = r; this.g = g; this.b = b;
            this.toRed = r; this.toGreen = g; this.toBlue = b;
            this.rot = rot; this.rotVel = rotVel;
            this.maxAge = Math.max(1, maxAge);
            this.age = Math.max(0, Math.min(age, this.maxAge - 1));
            this.gravity = gravity; this.drag = drag;
            this.stopsOnGround = stopsOnGround; this.emitsSmoke = emitsSmoke;
            this.orientation = orientation; this.blendMode = blendMode;
            this.staticTexture = staticTexture; this.animFrames = animFrames;
            this.frameDelay = Math.max(1, frameDelay);
            this.normX = normX; this.normY = normY; this.normZ = normZ;
            this.renderLayer = renderLayer;
        }

        public ResourceLocation getActiveTexture() {
            if (staticTexture != null) {
                return staticTexture;
            }
            if (animFrames != null && animFrames.length > 0) {
                final int rawIdx = (age / frameDelay) % animFrames.length;
                final int frameIdx = (rawIdx + animFrames.length) % animFrames.length;
                return animFrames[frameIdx];
            }
            return null;
        }
    }

    private static class DelayedSound {
        public final SoundEvent sound;
        public final double x, y, z;
        public final float volume;
        public final float pitch;
        public int delayTicks;

        public DelayedSound(final SoundEvent sound, final double x, final double y, final double z, final float volume, final float pitch, final int delayTicks) {
            this.sound = sound;
            this.x = x; this.y = y; this.z = z;
            this.volume = volume;
            this.pitch = pitch;
            this.delayTicks = delayTicks;
        }
    }

    public static void spawnFlamingoBurnoutPuff(Vec3 prevAnchor, Vec3 currentAnchor, Vec3 forward, float fadeFactor, float speed) {}
}
