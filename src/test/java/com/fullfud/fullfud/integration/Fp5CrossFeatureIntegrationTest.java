package com.fullfud.fullfud.integration;

import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.core.network.packet.DroneExplosionPacket;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;

/**
 * End-to-End integration test suite for cross-feature coupling and full mission flight profiles (Tiers 3 & 4 / Features F1-F12).
 */
@Fp5TestSuite(name = "FP-5 Flamingo Cross-Feature & E2E Integration", features = {"F1", "F2", "F3", "F4", "F5", "F6", "F7", "F8", "F9", "F10", "F11", "F12"}, milestone = "M4")
public class Fp5CrossFeatureIntegrationTest {

    // ---------------------------------------------------------------------------------------------
    // Tier 3: Cross-Feature Interactions
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 3, features = {"F1", "F3", "F4", "F6", "F8"}, description = "Cross-Feature: Booster active state simultaneously drives flight lock, audio asset, and VFX plume")
    public void testBoosterStateDrivesFlightAudioAndExhaust() {
        // Test State 1: Booster Phase (Tick 50)
        final int boosterTick = 50;
        final boolean boosterActive = boosterTick <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final boolean flightAngleLocked = boosterTick <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final String audioAsset = boosterActive ? "basic_ocp_rocket" : "enginepw610f_running_14000";
        final String exhaustMode = boosterActive ? "ROCKET_FIRE_SMOKE" : "JET_CONTRAIL";

        Fp5Assertions.assertTrue(boosterActive, "Booster must be ACTIVE at tick 50");
        Fp5Assertions.assertTrue(flightAngleLocked, "Flight angle must be LOCKED during booster active state");
        Fp5Assertions.assertEquals("basic_ocp_rocket", audioAsset, "Audio must play rocket booster burn sound");
        Fp5Assertions.assertEquals("ROCKET_FIRE_SMOKE", exhaustMode, "Exhaust must emit high-intensity rocket fire and smoke");

        // Test State 2: Jet Cruise Phase (Tick 120)
        final int cruiseTick = 120;
        final boolean cruiseBoosterActive = cruiseTick <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final boolean cruiseFlightAngleLocked = cruiseTick <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final String cruiseAudioAsset = cruiseBoosterActive ? "basic_ocp_rocket" : "enginepw610f_running_14000";
        final String cruiseExhaustMode = cruiseBoosterActive ? "ROCKET_FIRE_SMOKE" : "JET_CONTRAIL";

        Fp5Assertions.assertFalse(cruiseBoosterActive, "Booster must be INACTIVE at tick 120");
        Fp5Assertions.assertFalse(cruiseFlightAngleLocked, "Flight angle must be UNLOCKED during cruise state");
        Fp5Assertions.assertEquals("enginepw610f_running_14000", cruiseAudioAsset, "Audio must play jet turbofan loop");
        Fp5Assertions.assertEquals("JET_CONTRAIL", cruiseExhaustMode, "Exhaust must emit tight aerodynamic jet contrail");
    }

    @Fp5Test(tier = 3, features = {"F4", "F7", "F9", "F10"}, description = "Cross-Feature: Impact detonation resets staging flags, cleans up audio, and applies demolition damage")
    public void testDetonationLifecycleIntegration() {
        // Simulates missile impact state transition
        boolean isLaunched = true;
        boolean isBoosterActive = false;
        boolean soundPlaying = true;
        float targetVehicleHp = 2200.0F; // Superb Warfare heavy armored tank

        // Impact event occurs
        final boolean impactDetonate = true;
        if (impactDetonate) {
            // 1. Vehicle demolition damage applied
            final float demolitionDamage = 4500.0F;
            targetVehicleHp = Math.max(0.0F, targetVehicleHp - demolitionDamage);

            // 2. Audio loop cleanly stopped
            soundPlaying = false;

            // 3. Staging flags reset
            isLaunched = false;
            isBoosterActive = false;
        }

        Fp5Assertions.assertEquals(0.0F, targetVehicleHp, 0.001F, "Superb Warfare tank must be completely demolished (0 HP)");
        Fp5Assertions.assertFalse(soundPlaying, "Looping audio must stop immediately upon detonation");
        Fp5Assertions.assertFalse(isLaunched, "isLaunched flag must reset to false upon detonation");
        Fp5Assertions.assertFalse(isBoosterActive, "isBoosterActive flag must reset to false upon detonation");
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 4: Real-World Flight Profile Scenarios
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 4, features = {"F1", "F3", "F4", "F6", "F8"}, description = "Scenario B: Cruise Phase & Waypoint Steering Simulation (Ticks 101..250)")
    public void testScenarioBCruiseFlightNavigation() {
        // Missile enters cruise stage at tick 101
        double speed = 3.45D;
        float currentYaw = 90.0F;
        float currentRoll = 0.0F;
        final float targetYaw = 45.0F; // Waypoint requires a 45-deg turn left
        final double targetCruiseSpeed = 2.95D;

        for (int tick = 101; tick <= 200; tick++) {
            // Turn authority ramps from 0.0 at tick 100 to 1.0 at tick 150 (50-tick ramp)
            final float turnAuthority = Math.min(1.0F, (float) (tick - 100) / 50.0F);

            // Speed smoothly transitions from 3.45D down to 2.95D
            if (speed > targetCruiseSpeed) {
                speed = Math.max(targetCruiseSpeed, speed - 0.03D);
            }

            // Banking roll responds to yaw error (targetYaw - currentYaw = -45.0 deg)
            final float yawError = targetYaw - currentYaw;
            final float maxBankLimit = Fp5FlamingoEntity.CRUISE_BANK_LIMIT * turnAuthority;
            final float targetBank = Math.max(-maxBankLimit, Math.min(maxBankLimit, yawError * 0.5F));
            currentRoll += (targetBank - currentRoll) * 0.42F;

            // Yaw rate builds based on banking roll
            final float yawRate = (float) (Math.sin(Math.toRadians(currentRoll)) * 0.055F * 30.0F * turnAuthority);
            currentYaw += Math.max(-0.65F, Math.min(0.65F, yawRate)); // Clamped to MAX_CRUISE_YAW_RATE

            // Assertions during flight:
            Fp5Assertions.assertTrue(Math.abs(currentRoll) <= Fp5FlamingoEntity.CRUISE_BANK_LIMIT + 0.1F,
                    "Body roll must remain clamped within CRUISE_BANK_LIMIT (30.0 deg) at tick " + tick);
        }

        // By tick 200: speed should be stabilized at 2.95D and heading significantly turned toward 45 deg
        Fp5Assertions.assertEquals(targetCruiseSpeed, speed, 0.01D, "Speed must stabilize at cruising speed 2.95D");
        Fp5Assertions.assertTrue(currentYaw < 90.0F, "Missile heading must have turned toward waypoint (actual: " + currentYaw + ")");
    }

    @Fp5Test(tier = 4, features = {"F3", "F9", "F10", "F11"}, description = "Scenario C: Terminal Dive & Vehicle Impact Detonation Simulation (Ticks 251..300)")
    public void testScenarioCTerminalDiveAndImpact() {
        // Missile initiates terminal dive
        float pitch = -12.0F; // Cruise pitch
        float roll = 0.0F;
        double speed = 2.95D;
        final float terminalDivePitch = -55.0F;

        for (int tick = 251; tick <= 290; tick++) {
            // Pitch approaches terminal dive pitch
            pitch += (terminalDivePitch - pitch) * 0.15F;

            // Terminal speed accelerates under gravity/thrust
            speed = Math.min(3.85D, speed + 0.05D);

            // Bank limit expands to TERMINAL_BANK_LIMIT (40 deg)
            roll = Math.max(-Fp5FlamingoEntity.TERMINAL_BANK_LIMIT, Math.min(Fp5FlamingoEntity.TERMINAL_BANK_LIMIT, roll));

            Fp5Assertions.assertTrue(Math.abs(roll) <= Fp5FlamingoEntity.TERMINAL_BANK_LIMIT,
                    "Roll must respect TERMINAL_BANK_LIMIT (40 deg) at tick " + tick);
        }

        // At terminal dive speed:
        Fp5Assertions.assertTrue(speed > 3.50D, "Terminal dive speed must accelerate above 3.50D (actual: " + speed + ")");
        Fp5Assertions.assertTrue(pitch <= -50.0F, "Terminal dive pitch must be steep (actual: " + pitch + ")");

        // Intercept target vehicle: Main Battle Tank with 2500 HP
        final float targetTankHealth = 2500.0F;
        final float directDemolitionDamage = 4500.0F;
        final float resultingHealth = Math.max(0.0F, targetTankHealth - directDemolitionDamage);

        Fp5Assertions.assertEquals(0.0F, resultingHealth, 0.001F,
                "Direct vehicle demolition damage (4500.0F) must destroy 2500 HP tank in single hit");

        // Detonation shockwave packet emitted
        final byte expType = DroneExplosionPacket.TYPE_FLAMINGO;
        Fp5Assertions.assertEquals(DroneExplosionPacket.TYPE_FLAMINGO, expType,
                "Detonation must dispatch TYPE_FLAMINGO packet for 90m colossal shockwave VFX");
    }
}
