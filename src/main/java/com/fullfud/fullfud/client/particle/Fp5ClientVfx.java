package com.fullfud.fullfud.client.particle;

import com.fullfud.fullfud.client.vfx.VfxLightingRegistry;
import com.fullfud.fullfud.client.vfx.VfxRenderPipeline;
import com.fullfud.fullfud.client.vfx.VfxTrailManager;
import com.fullfud.fullfud.core.config.FullfudClientConfig;
import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import net.minecraft.util.Mth;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@OnlyIn(Dist.CLIENT)
public final class Fp5ClientVfx {
    private static final Map<UUID, Vec3> LAST_POSITIONS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_BOOSTER_ANCHORS = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_JET_ANCHORS = new HashMap<>();
    private static final Map<UUID, Integer> BOOSTER_FADE_TICKS = new HashMap<>();
    private static final Map<UUID, Boolean> LAUNCH_CLOUD_SPAWNED = new HashMap<>();

    private static float cameraShakeIntensity = 0.0F;
    private static int cameraShakeTick = 0;

    private Fp5ClientVfx() {
    }

    public static void init(final FMLClientSetupEvent event) {
        VfxRenderPipeline.init();
        MinecraftForge.EVENT_BUS.<ViewportEvent.ComputeCameraAngles>addListener(Fp5ClientVfx::onComputeCameraAngles);
        MinecraftForge.EVENT_BUS.<TickEvent.ClientTickEvent>addListener(Fp5ClientVfx::onClientTick);
    }

    public static float getCameraShakeIntensity() {
        return cameraShakeIntensity;
    }

    public static int getCameraShakeTick() {
        return cameraShakeTick;
    }

    public static void triggerCameraShake(final double distance, final double speed) {
        if (distance > 32.0D || speed < 0.8D) {
            return;
        }
        final float distFactor = (float) Math.max(0.0D, 1.0D - (distance / 32.0D));
        final float speedFactor = (float) Math.min(1.5D, speed / 1.2D);
        final float impulse = distFactor * distFactor * speedFactor;
        if (impulse > cameraShakeIntensity) {
            cameraShakeIntensity = impulse;
        }
    }

    public static void updateDecay() {
        if (cameraShakeIntensity > 0.001F) {
            cameraShakeTick++;
            cameraShakeIntensity *= 0.85F;
            if (cameraShakeIntensity < 0.001F) {
                cameraShakeIntensity = 0.0F;
                cameraShakeTick = 0;
            }
        }
    }

    private static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        updateDecay();
    }

    private static void onComputeCameraAngles(final ViewportEvent.ComputeCameraAngles event) {
        if (cameraShakeIntensity <= 0.001F) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.isPaused() || minecraft.player == null) {
            return;
        }
        final float time = cameraShakeTick + (float) event.getPartialTick();
        final float yawShake = (float) Math.sin(time * 2.1F) * cameraShakeIntensity * 3.2F;
        final float pitchShake = (float) Math.cos(time * 2.7F) * cameraShakeIntensity * 2.8F;
        final float rollShake = (float) Math.sin(time * 1.8F) * cameraShakeIntensity * 4.0F;

        event.setYaw(event.getYaw() + yawShake);
        event.setPitch(event.getPitch() + pitchShake);
        event.setRoll(event.getRoll() + rollShake);
    }

    /**
     * World-space position of the exhaust nozzle, matching the
     * renderer transform (yaw 180-yaw, pitch, roll, SCALE) and the model
     * offsets for the respective stage.
     */
    private static Vec3 computeNozzleAnchor(final Fp5FlamingoEntity flamingo, final boolean booster) {
        final float roll = flamingo.getVisualRoll(1.0F);
        final float pitch = flamingo.isOnLauncher() ? flamingo.getXRot() : Mth.rotLerp(1.0F, flamingo.xRotO, flamingo.getXRot());
        final float yaw = flamingo.isOnLauncher() ? flamingo.getYRot() : Mth.rotLerp(1.0F, flamingo.yRotO, flamingo.getYRot());
        final Vec3 offset = DroneParticleManager.nozzleOffset(booster, yaw, pitch, roll);
        return new Vec3(
            flamingo.getX() + offset.x,
            flamingo.getY() + offset.y,
            flamingo.getZ() + offset.z
        );
    }

    private static Vec3 computeNozzleAnchor(final Fp5FlamingoEntity flamingo) {
        return computeNozzleAnchor(flamingo, flamingo.isBoosterActive());
    }

    public static void tick(final Fp5FlamingoEntity flamingo) {
        if (flamingo == null || !flamingo.isAlive() || !flamingo.isLaunched()) {
            return;
        }

        final UUID id = flamingo.getUUID();
        final Vec3 currPos = flamingo.position();
        final Vec3 forward = flamingo.getCourseDirection();
        final boolean boosterActive = flamingo.isBoosterActive();

        final Vec3 boosterAnchor = computeNozzleAnchor(flamingo, true);
        final Vec3 jetAnchor = computeNozzleAnchor(flamingo, false);
        final Vec3 activeAnchor = boosterActive ? boosterAnchor : jetAnchor;

        VfxTrailManager.sampleEntity(flamingo, activeAnchor);

        final Vec3 prevPos = LAST_POSITIONS.getOrDefault(id, currPos);
        LAST_POSITIONS.put(id, currPos);

        final Vec3 prevBoosterAnchor = LAST_BOOSTER_ANCHORS.getOrDefault(id, boosterAnchor);
        LAST_BOOSTER_ANCHORS.put(id, boosterAnchor);

        final Vec3 prevJetAnchor = LAST_JET_ANCHORS.getOrDefault(id, jetAnchor);
        LAST_JET_ANCHORS.put(id, jetAnchor);

        // Direct proximity camera shake evaluation against local client player
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.player != null) {
            final Vec3 playerPos = minecraft.player.position();
            final double distance = currPos.distanceTo(playerPos);
            if (distance <= 32.0D) {
                final double speed = currPos.distanceTo(prevPos) > 0.001D
                    ? currPos.distanceTo(prevPos)
                    : flamingo.getDeltaMovement().length();
                triggerCameraShake(distance, speed);
            }
        }

        if (boosterActive && !LAUNCH_CLOUD_SPAWNED.getOrDefault(id, false)) {
            LAUNCH_CLOUD_SPAWNED.put(id, true);
            DroneParticleManager.spawnFlamingoLaunchPadCloud(currPos.x, currPos.y, currPos.z, forward);
            // Flood the pad: stays at the rail while the engine light climbs away.
            VfxLightingRegistry.addLaunchPadLight(boosterAnchor.add(0.0D, 0.8D, 0.0D));
        }

        final double travelled = currPos.distanceTo(prevPos);
        final double speed = travelled > 0.001D ? travelled : flamingo.getDeltaMovement().length();

        if (boosterActive) {
            BOOSTER_FADE_TICKS.put(id, 10);
            DroneParticleManager.spawnFlamingoExhaustFromNozzle(
                prevBoosterAnchor,
                boosterAnchor,
                forward,
                true,
                (float) speed
            );
        } else {
            // Sustained cruise jet contrail from dorsal engine nozzle
            DroneParticleManager.spawnFlamingoExhaustFromNozzle(
                prevJetAnchor,
                jetAnchor,
                forward,
                false,
                (float) speed
            );
            // Smooth booster burnout tail-off over 10 ticks: eliminate sudden breaks or diagonal pops
            final int remainingFade = BOOSTER_FADE_TICKS.getOrDefault(id, 0);
            if (remainingFade > 0) {
                BOOSTER_FADE_TICKS.put(id, remainingFade - 1);
                final float fadeFactor = (float) remainingFade / 10.0F;
                DroneParticleManager.spawnFlamingoBurnoutPuff(
                    prevBoosterAnchor,
                    boosterAnchor,
                    forward,
                    fadeFactor,
                    (float) speed
                );
            }
        }
    }

    public static void remove(final UUID id) {
        if (id != null) {
            LAST_POSITIONS.remove(id);
            LAST_BOOSTER_ANCHORS.remove(id);
            LAST_JET_ANCHORS.remove(id);
            BOOSTER_FADE_TICKS.remove(id);
            LAUNCH_CLOUD_SPAWNED.remove(id);
            VfxTrailManager.detach(id);
            VfxLightingRegistry.removeRocket(id);
        }
    }

    public static void clear() {
        LAST_POSITIONS.clear();
        LAST_BOOSTER_ANCHORS.clear();
        LAST_JET_ANCHORS.clear();
        BOOSTER_FADE_TICKS.clear();
        LAUNCH_CLOUD_SPAWNED.clear();
        VfxRenderPipeline.clear();
        cameraShakeIntensity = 0.0F;
        cameraShakeTick = 0;
    }
}
