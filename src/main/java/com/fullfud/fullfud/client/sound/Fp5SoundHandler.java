package com.fullfud.fullfud.client.sound;

import com.fullfud.fullfud.client.particle.Fp5ClientVfx;
import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@OnlyIn(Dist.CLIENT)
public final class Fp5SoundHandler {
    private static final Map<UUID, FlamingoAudioController> CONTROLLERS = new HashMap<>();

    private Fp5SoundHandler() {
    }

    public static void init(final FMLClientSetupEvent event) {
        MinecraftForge.EVENT_BUS.<TickEvent.ClientTickEvent>addListener(Fp5SoundHandler::onClientTick);
        MinecraftForge.EVENT_BUS.<LevelEvent.Unload>addListener(Fp5SoundHandler::onLevelUnload);
        MinecraftForge.EVENT_BUS.<ClientPlayerNetworkEvent.LoggingOut>addListener(Fp5SoundHandler::onLoggingOut);
    }

    public static void onLevelUnload(final LevelEvent.Unload event) {
        if (event.getLevel() != null && event.getLevel().isClientSide()) {
            clear();
        }
    }

    public static void onLoggingOut(final ClientPlayerNetworkEvent.LoggingOut event) {
        clear();
    }

    public static void onClientTick(final TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.isPaused()) {
            clear();
            return;
        }

        CONTROLLERS.values().forEach(controller -> controller.seen = false);

        for (final Entity entity : minecraft.level.entitiesForRendering()) {
            if (entity instanceof Fp5FlamingoEntity flamingo) {
                onClientTick(flamingo);
            }
        }

        CONTROLLERS.entrySet().removeIf(entry -> {
            final FlamingoAudioController controller = entry.getValue();
            if (!controller.seen || controller.isInvalid()) {
                controller.stop();
                return true;
            }
            return false;
        });
    }

    public static void onClientTick(final Fp5FlamingoEntity flamingo) {
        if (flamingo == null) {
            return;
        }
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null) {
            return;
        }

        if (flamingo.isRemoved() || !flamingo.isAlive() || !flamingo.isLaunched()) {
            stopForFlamingo(flamingo.getUUID());
            return;
        }

        final FlamingoAudioController controller = CONTROLLERS.computeIfAbsent(
            flamingo.getUUID(),
            id -> new FlamingoAudioController(flamingo)
        );
        controller.seen = true;
        controller.tick();
    }

    public static void stopForFlamingo(final UUID flamingoId) {
        if (flamingoId == null) {
            return;
        }
        final FlamingoAudioController controller = CONTROLLERS.remove(flamingoId);
        if (controller != null) {
            controller.stop();
        }
    }

    public static void clear() {
        CONTROLLERS.values().forEach(FlamingoAudioController::stop);
        CONTROLLERS.clear();
        Fp5ClientVfx.clear();
        OpenALFilters.cleanup();
    }

    private static final class FlamingoAudioController {
        private final Fp5FlamingoEntity flamingo;
        private Fp5EngineLoopSoundInstance boosterSound;
        private Fp5EngineLoopSoundInstance whistleSound;
        private Fp5EngineLoopSoundInstance rumbleSound;
        private Vec3 prevPos;
        private Vec3 velocity = Vec3.ZERO;
        private float prevCosTheta = Float.NaN;
        private boolean flybyTriggeredThisPass = false;
        private int activeTicks;
        private boolean seen;

        private FlamingoAudioController(final Fp5FlamingoEntity flamingo) {
            this.flamingo = flamingo;
            this.activeTicks = 0;
        }

        private boolean isInvalid() {
            return flamingo == null || flamingo.isRemoved() || !flamingo.isAlive() || !flamingo.isLaunched();
        }

        private void tick() {
            final Minecraft minecraft = Minecraft.getInstance();
            if (minecraft == null || minecraft.level == null || isInvalid()) {
                stop();
                return;
            }

            activeTicks++;

            final Vec3 flamingoPos = flamingo.position();
            if (prevPos != null) {
                velocity = flamingoPos.subtract(prevPos);
            } else {
                velocity = flamingo.getDeltaMovement();
            }
            prevPos = flamingoPos;

            final Vec3 cameraPos = minecraft.getCameraEntity() != null ? minecraft.getCameraEntity().position() : flamingoPos;
            final double distance = flamingoPos.distanceTo(cameraPos);
            final double maxDistance = 480.0D;
            final double maxDistanceWhistle = 320.0D;
            final double maxDistanceBooster = 600.0D;
            final double speed = velocity.length();

            final float dopplerPitch = DroneSoundEffects.computeDopplerPitch(flamingoPos, velocity, cameraPos);
            final float gainHF = DroneSoundEffects.computeCombinedGainHF(
                distance,
                flamingoPos.y,
                cameraPos.y,
                maxDistance,
                DroneSoundEffects.SoundProfile.FP5_FLAMINGO
            );
            final float distVolRumble = DroneSoundEffects.computeDistanceVolumeFactor(
                distance,
                maxDistance,
                DroneSoundEffects.SoundProfile.FP5_FLAMINGO
            );
            final float distVolWhistle = DroneSoundEffects.computeDistanceVolumeFactor(
                distance,
                maxDistanceWhistle,
                DroneSoundEffects.SoundProfile.FP5_FLAMINGO
            );
            final float distVolBooster = DroneSoundEffects.computeDistanceVolumeFactor(
                distance,
                maxDistanceBooster,
                DroneSoundEffects.SoundProfile.FP5_FLAMINGO
            );
            final float gainHFBooster = DroneSoundEffects.computeCombinedGainHF(
                distance,
                flamingoPos.y,
                cameraPos.y,
                maxDistanceBooster,
                DroneSoundEffects.SoundProfile.FP5_FLAMINGO
            );

            // Solid Rocket Booster staging is tied directly to synchronized entity state: flamingo.isBoosterActive()
            // When booster is active: full volume with 600m acoustic reach and distance low-pass filtering.
            // When booster burns out (70 ticks / 3.5s): booster cuts off INSTANTLY without lingering fade.
            final boolean boosterActive = flamingo.isBoosterActive();
            final float boosterMix = boosterActive ? 1.0F : 0.0F;
            final float cruiseMix = boosterActive ? 0.0F : 1.0F;

            // Aspect Angle Directivity calculation: cos(theta) = dot(flightDir, dirToListener)
            // flightDir: where the missile is heading
            // dirToListener: vector from missile towards the listener
            // cosTheta > 0: missile is approaching the listener (Forward Aspect: compressor whine + air shear whistle)
            // cosTheta < 0: missile is receding from the listener (Aft Aspect: jet mixing noise + exhaust plume roar)
            final Vec3 flightDir = speed > 0.05D ? velocity.normalize() : flamingo.getCourseDirection().normalize();
            final Vec3 dirToListener = distance > 0.01D ? cameraPos.subtract(flamingoPos).normalize() : flightDir;
            final float cosTheta = (float) flightDir.dot(dirToListener);

            final float forwardMix = Mth.clamp((cosTheta + 0.15F) / 1.15F, 0.0F, 1.0F);
            final float aftMix = Mth.clamp((-cosTheta + 0.25F) / 1.25F, 0.15F, 1.0F);

            // Flyby CPA (Closest Point of Approach) detection and camera shake
            if (!Float.isNaN(prevCosTheta)) {
                if (prevCosTheta > 0.0F && cosTheta <= 0.05F && distance <= 32.0D && !flybyTriggeredThisPass) {
                    flybyTriggeredThisPass = true;
                    // Play dynamic flyby whoosh at missile position
                    minecraft.getSoundManager().play(new SimpleSoundInstance(
                        FullfudRegistries.FP5_FLYBY.get().getLocation(),
                        SoundSource.NEUTRAL,
                        1.4F,
                        0.95F + (float) Math.random() * 0.1F,
                        RandomSource.create(),
                        false,
                        0,
                        SoundInstance.Attenuation.LINEAR,
                        flamingoPos.x,
                        flamingoPos.y,
                        flamingoPos.z,
                        false
                    ));
                    // Trigger camera shake impulse
                    Fp5ClientVfx.triggerCameraShake(distance, speed);
                }
            }
            if (cosTheta > 0.2F) {
                flybyTriggeredThisPass = false;
            }
            prevCosTheta = cosTheta;

            // Continuous proximity camera shake if missile screams past within 24 blocks
            if (distance <= 24.0D && speed > 0.8D) {
                Fp5ClientVfx.triggerCameraShake(distance, speed);
            }

            // 1. Solid Booster sound stream
            if (boosterMix > 0.001F) {
                if (boosterSound == null || boosterSound.isStopped()) {
                    boosterSound = new Fp5EngineLoopSoundInstance(
                        flamingo,
                        true,
                        FullfudRegistries.FP5_BOOSTER_LOOP.get(),
                        maxDistanceBooster,
                        DroneSoundEffects.SoundProfile.FP5_FLAMINGO
                    );
                    boosterSound.update(flamingoPos.x, flamingoPos.y, flamingoPos.z, boosterMix, dopplerPitch, gainHFBooster, distVolBooster);
                    minecraft.getSoundManager().play(boosterSound);
                } else {
                    boosterSound.update(flamingoPos.x, flamingoPos.y, flamingoPos.z, boosterMix, dopplerPitch, gainHFBooster, distVolBooster);
                }
            } else if (boosterSound != null) {
                boosterSound.forceStop();
                boosterSound = null;
            }

            // 2. Cruise Forward Whistle stream (inlet turbine scream + air shearing)
            final float whistleMix = cruiseMix * forwardMix;
            if (whistleMix > 0.001F) {
                if (whistleSound == null || whistleSound.isStopped()) {
                    whistleSound = new Fp5EngineLoopSoundInstance(
                        flamingo,
                        FullfudRegistries.FP5_CRUISE_WHISTLE.get(),
                        1.1F,
                        maxDistanceWhistle
                    );
                    minecraft.getSoundManager().play(whistleSound);
                }
                whistleSound.update(flamingoPos.x, flamingoPos.y, flamingoPos.z, whistleMix, dopplerPitch, gainHF, distVolWhistle);
            } else if (whistleSound != null) {
                whistleSound.update(flamingoPos.x, flamingoPos.y, flamingoPos.z, 0.0F, dopplerPitch, gainHF, distVolWhistle);
            }

            // 3. Cruise Aft Jet Rumble stream (exhaust jet mixing roar + trailing wake)
            final float rumbleMix = cruiseMix * aftMix;
            if (rumbleMix > 0.001F) {
                if (rumbleSound == null || rumbleSound.isStopped()) {
                    rumbleSound = new Fp5EngineLoopSoundInstance(
                        flamingo,
                        FullfudRegistries.FP5_CRUISE_RUMBLE.get(),
                        1.3F,
                        maxDistance
                    );
                    minecraft.getSoundManager().play(rumbleSound);
                }
                rumbleSound.update(flamingoPos.x, flamingoPos.y, flamingoPos.z, rumbleMix, dopplerPitch, gainHF, distVolRumble);
            } else if (rumbleSound != null) {
                rumbleSound.update(flamingoPos.x, flamingoPos.y, flamingoPos.z, 0.0F, dopplerPitch, gainHF, distVolRumble);
            }
        }

        private void stop() {
            if (boosterSound != null) {
                boosterSound.forceStop();
                boosterSound = null;
            }
            if (whistleSound != null) {
                whistleSound.forceStop();
                whistleSound = null;
            }
            if (rumbleSound != null) {
                rumbleSound.forceStop();
                rumbleSound = null;
            }
        }
    }
}
