package com.fullfud.fullfud.client.sound;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class Shahed238EngineLoopSoundInstance extends ShahedEngineLoopSoundInstance {

    public Shahed238EngineLoopSoundInstance(
        final SoundEvent sound,
        final double maxAudibleDistance,
        final DroneSoundEffects.SoundProfile soundProfile
    ) {
        super(sound, maxAudibleDistance, soundProfile);
    }

    @Override
    public void update(
        final double x,
        final double y,
        final double z,
        final float engineMix,
        final float diveFactor,
        final float climbFactor,
        final float speedFactor,
        final float turnFactor,
        final float dopplerPitch,
        final float gainHF,
        final long gameTick
    ) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.lastUpdateTick = gameTick;
        this.dying = false;
        this.targetDopplerPitch = dopplerPitch;
        // Filter out harsh high-frequency sizzle, giving the booster sound a smooth jet-turbine rolloff
        this.targetGainHF = gainHF * 0.72F;

        final float mix = Mth.clamp(engineMix, 0.0F, 1.0F);
        // Smaller drone (50-90 kg warhead) -> quieter base volume than FP-5 Flamingo booster
        final float baseVolume = 0.58F + 0.38F * mix;
        // Higher base pitch reflecting higher-RPM micro-turbojet engine (e.g. Toloue-10 / TJ100)
        final float basePitch = 1.18F + 0.12F * mix;

        final float flightVolumeMult = 1.0F + diveFactor * 0.45F + speedFactor * 0.20F + turnFactor * 0.15F;
        final float flightPitchOffset = diveFactor * 0.32F - climbFactor * 0.10F + speedFactor * 0.18F;

        this.targetVolume = Mth.clamp(baseVolume * flightVolumeMult, 0.0F, 1.6F);
        this.targetPitch = Mth.clamp(basePitch + flightPitchOffset, 0.4F, 2.2F);
    }

    @Override
    protected void applyFilter() {
        if (OpenALFilters.isAvailable()) {
            // Apply OpenAL Low-Pass direct filter: 0.88 overall gain, rolled-off gainHF
            OpenALFilters.applyFilterForInstance(this, 0.88F, currentGainHF);
        }
    }
}
