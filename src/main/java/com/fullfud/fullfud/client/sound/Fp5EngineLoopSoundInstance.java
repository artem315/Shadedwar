package com.fullfud.fullfud.client.sound;

import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.core.FullfudRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Fp5EngineLoopSoundInstance extends AbstractTickableSoundInstance {
    private static final int MAX_FADE_TICKS = 20;

    private final Fp5FlamingoEntity flamingo;
    private final boolean booster;
    private final double maxAudibleDistance;
    private final DroneSoundEffects.SoundProfile soundProfile;

    private boolean dying;
    private int fadeTicks;
    private float mix = 1.0F;
    private float baseVolume;
    private float basePitch;
    private float targetDistVol = 1.0F;
    private float targetDopplerPitch = 1.0F;
    private float currentDopplerPitch = 1.0F;
    private float targetGainHF = 1.0F;
    private float currentGainHF = 1.0F;

    private Vec3 prevPos;
    private Vec3 velocity = Vec3.ZERO;

    public Fp5EngineLoopSoundInstance(final Fp5FlamingoEntity flamingo) {
        this(flamingo, flamingo != null && flamingo.isBoosterActive());
    }

    public Fp5EngineLoopSoundInstance(final Fp5FlamingoEntity flamingo, final boolean booster) {
        this(
            flamingo,
            booster,
            booster ? FullfudRegistries.FP5_BOOSTER_LOOP.get() : FullfudRegistries.FP5_ENGINE_LOOP.get(),
            booster ? 480.0D : 420.0D,
            DroneSoundEffects.SoundProfile.FP5_FLAMINGO
        );
    }

    public Fp5EngineLoopSoundInstance(
        final Fp5FlamingoEntity flamingo,
        final SoundEvent soundEvent,
        final float baseVolume,
        final double maxAudibleDistance
    ) {
        this(
            flamingo,
            soundEvent != null && soundEvent == FullfudRegistries.FP5_BOOSTER_LOOP.get(),
            soundEvent,
            maxAudibleDistance,
            DroneSoundEffects.SoundProfile.FP5_FLAMINGO
        );
        this.baseVolume = baseVolume;
    }


    public Fp5EngineLoopSoundInstance(
        final Fp5FlamingoEntity flamingo,
        final boolean booster,
        final SoundEvent soundEvent,
        final double maxAudibleDistance,
        final DroneSoundEffects.SoundProfile soundProfile
    ) {
        super(soundEvent, SoundSource.NEUTRAL, SoundInstance.createUnseededRandom());
        this.flamingo = flamingo;
        this.booster = booster;
        this.maxAudibleDistance = maxAudibleDistance;
        this.soundProfile = soundProfile;
        this.looping = true;
        this.delay = 0;
        this.relative = false;
        this.attenuation = Attenuation.NONE;
        this.volume = 0.0F;
        this.pitch = 1.0F;
        this.fadeTicks = booster ? MAX_FADE_TICKS : 0;
        this.baseVolume = booster ? 1.8F : 1.2F;
        this.basePitch = 1.0F;

        if (flamingo != null) {
            this.x = flamingo.getX();
            this.y = flamingo.getY();
            this.z = flamingo.getZ();
            this.prevPos = flamingo.position();
            this.velocity = flamingo.getDeltaMovement();
        }
    }

    public boolean isBooster() {
        return booster;
    }

    public Fp5FlamingoEntity getFlamingo() {
        return flamingo;
    }

    public double getMaxAudibleDistance() {
        return maxAudibleDistance;
    }

    public DroneSoundEffects.SoundProfile getSoundProfile() {
        return soundProfile;
    }

    public void setMix(final float mix) {
        this.mix = Mth.clamp(mix, 0.0F, 1.0F);
    }

    public float getMix() {
        return mix;
    }

    public void requestFadeOut() {
        this.dying = true;
    }

    public void forceStop() {
        this.dying = true;
        this.volume = 0.0F;
        removeFilter();
        this.stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (this.isStopped()) {
            removeFilter();
            return;
        }

        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.level == null || minecraft.isPaused()) {
            forceStop();
            return;
        }

        if (flamingo == null || flamingo.isRemoved() || !flamingo.isAlive() || !flamingo.isLaunched()) {
            forceStop();
            return;
        }

        this.x = flamingo.getX();
        this.y = flamingo.getY();
        this.z = flamingo.getZ();

        if (dying) {
            if (fadeTicks <= 0) {
                forceStop();
                return;
            }
            --fadeTicks;
        } else if (fadeTicks < MAX_FADE_TICKS) {
            ++fadeTicks;
        }

        currentDopplerPitch = Mth.lerp(0.20F, currentDopplerPitch, targetDopplerPitch);
        currentGainHF = Mth.lerp(0.15F, currentGainHF, targetGainHF);

        final float fadeMult = (float) fadeTicks / (float) MAX_FADE_TICKS;
        final float desiredVolume = baseVolume * mix * targetDistVol * fadeMult;

        this.volume = Mth.lerp(0.25F, this.volume, desiredVolume);
        this.pitch = Mth.lerp(0.25F, this.pitch, Mth.clamp(basePitch * currentDopplerPitch, 0.4F, 2.2F));

        applyFilter();

        if (dying && this.volume <= 5.0E-4F) {
            forceStop();
        }
    }

    public void update(
        final double x,
        final double y,
        final double z,
        final float mix,
        final float dopplerPitch,
        final float gainHF,
        final float distVol
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.mix = Mth.clamp(mix, 0.0F, 1.0F);
        this.targetDopplerPitch = dopplerPitch;
        this.targetGainHF = gainHF;
        this.targetDistVol = distVol;
        if (booster && this.volume <= 0.001F && !dying) {
            this.currentDopplerPitch = dopplerPitch;
            this.currentGainHF = gainHF;
            this.volume = baseVolume * this.mix * distVol;
            this.pitch = Mth.clamp(basePitch * dopplerPitch, 0.4F, 2.2F);
        }
    }

    public void updateSelf() {
        final Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || flamingo == null) {
            return;
        }

        final Vec3 currentPos = flamingo.position();
        this.x = currentPos.x;
        this.y = currentPos.y;
        this.z = currentPos.z;

        if (prevPos != null) {
            velocity = currentPos.subtract(prevPos);
        } else {
            velocity = flamingo.getDeltaMovement();
        }
        prevPos = currentPos;

        Vec3 cameraPos = currentPos;
        if (minecraft.getCameraEntity() != null) {
            cameraPos = minecraft.getCameraEntity().position();
        }

        final double distance = currentPos.distanceTo(cameraPos);
        final float distVolFactor = DroneSoundEffects.computeDistanceVolumeFactor(distance, maxAudibleDistance, soundProfile);
        final float dopplerPitch = DroneSoundEffects.computeDopplerPitch(currentPos, velocity, cameraPos);
        final float gainHF = DroneSoundEffects.computeCombinedGainHF(
            distance,
            currentPos.y,
            cameraPos.y,
            maxAudibleDistance,
            soundProfile
        );

        this.targetDistVol = distVolFactor;
        this.targetDopplerPitch = dopplerPitch;
        this.targetGainHF = gainHF;
    }

    private void applyFilter() {
        OpenALFilters.applyFilterForInstance(this, 1.0F, currentGainHF);
    }

    private void removeFilter() {
        OpenALFilters.removeFilterForInstance(this);
    }
}
