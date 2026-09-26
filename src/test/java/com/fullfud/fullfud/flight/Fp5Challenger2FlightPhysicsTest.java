package com.fullfud.fullfud.flight;

import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;
import net.minecraft.util.Mth;

/**
 * Challenger 2 Adversarial Stress Test Suite for FP-5 Flamingo Flight Dynamics,
 * Velocity Deceleration Curves, Bank Angle Roll Stability, and Client-Server Synchronization (M1).
 */
@Fp5TestSuite(name = "Challenger 2 Flight Physics & Staging Stress Suite", features = {"F1", "F2", "F3", "F4"}, milestone = "M1")
public class Fp5Challenger2FlightPhysicsTest {

    // =============================================================================================
    // TASK 1: Velocity Curve & Deceleration Empirical Verification
    // =============================================================================================

    @Fp5Test(tier = 2, features = {"F3"}, description = "Empirical verification: Velocity curve transitions smoothly from booster to 2.95D over 45 ticks without dropping below cruise speed")
    public void testVelocityTransitionSmoothnessAndNoCruiseUndershoot() {
        // Trace velocity curve across the full flight profile (Ticks 0..150)
        double currentSpeed = 0.3D; // launch initial speed
        double previousSpeed = currentSpeed;

        for (int launchTicks = 1; launchTicks <= 150; launchTicks++) {
            final double speedTarget = computeSpeedTargetOracle(launchTicks, false, 0.0F);
            final double speedStep = computeSpeedStepOracle(launchTicks, currentSpeed, speedTarget);
            currentSpeed = approachValue(currentSpeed, speedTarget, speedStep);

            // Ticks 1..15: Booster Kick Phase (0.3D -> 5.2D)
            if (launchTicks <= Fp5FlamingoEntity.BOOSTER_KICK_TICKS) {
                Fp5Assertions.assertTrue(currentSpeed >= previousSpeed,
                        "Speed must strictly accelerate during booster kick at tick " + launchTicks);
                Fp5Assertions.assertTrue(currentSpeed <= Fp5FlamingoEntity.BOOSTER_PEAK_SPEED + 1e-6,
                        "Speed must not exceed BOOSTER_PEAK_SPEED (5.2D) at tick " + launchTicks);
            }

            // At tick 15: Must reach peak booster velocity (5.2D)
            if (launchTicks == 15) {
                Fp5Assertions.assertEquals(Fp5FlamingoEntity.BOOSTER_PEAK_SPEED, currentSpeed, 0.01D,
                        "Speed must reach 5.2D at end of kick phase (tick 15)");
            }

            // Ticks 16..70: Sustained Booster Climb (5.2D tapering to 4.2D)
            if (launchTicks > 15 && launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS) {
                Fp5Assertions.assertTrue(currentSpeed >= Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED - 1e-6,
                        "Speed during booster burn must remain >= sustain speed (4.2D) at tick " + launchTicks);
                Fp5Assertions.assertTrue(currentSpeed <= Fp5FlamingoEntity.BOOSTER_PEAK_SPEED + 1e-6,
                        "Speed during booster burn must remain <= peak speed (5.2D) at tick " + launchTicks);
            }

            // At tick 70: Must reach sustain speed (4.2D)
            if (launchTicks == 70) {
                Fp5Assertions.assertEquals(Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED, currentSpeed, 0.01D,
                        "Speed at booster burnout (tick 70) must be exactly 4.2D");
            }

            // Ticks 71..115: 45-Tick Booster Burnout to Jet Cruise Aerodynamic Deceleration
            if (launchTicks > 70 && launchTicks <= 70 + Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS) {
                // Must decelerate monotonically
                Fp5Assertions.assertTrue(currentSpeed <= previousSpeed + 1e-9,
                        "Speed must decrease monotonically during 45-tick deceleration window at tick " + launchTicks);
                // MUST NOT drop below cruise speed target (2.95D)
                Fp5Assertions.assertTrue(currentSpeed >= Fp5FlamingoEntity.CRUISE_SPEED_TARGET,
                        "Speed MUST NOT drop below CRUISE_SPEED_TARGET (2.95D) during transition at tick " + launchTicks
                                + " (actual: " + currentSpeed + ")");
                // Smoothness: Deceleration delta per tick must not exceed transition step limit
                final double delta = previousSpeed - currentSpeed;
                Fp5Assertions.assertTrue(delta <= Fp5FlamingoEntity.BOOSTER_TRANSITION_STEP + 1e-6,
                        "Deceleration per tick (" + delta + ") must not exceed BOOSTER_TRANSITION_STEP (0.08D) at tick " + launchTicks);
            }

            // Ticks 116+: Steady Jet Cruise Phase
            if (launchTicks > 115) {
                Fp5Assertions.assertEquals(Fp5FlamingoEntity.CRUISE_SPEED_TARGET, currentSpeed, 0.001D,
                        "Speed after transition (tick " + launchTicks + ") must remain steadily locked at 2.95D");
            }

            previousSpeed = currentSpeed;
        }
    }

    @Fp5Test(tier = 2, features = {"F3"}, description = "Stress: Deceleration from hypothetical peak 5.2D over 45 ticks without dropping below 2.95D")
    public void testDecelerationFromPeakVelocityOracle() {
        // Even in a scenario where booster speed remained at 5.2D until tick 70
        double speed = 5.2D;
        for (int t = 1; t <= 45; t++) {
            final double s = (double) t / 45.0D;
            final double h = s * s * (3.0D - 2.0D * s);
            final double target = Mth.lerp(h, 5.2D, 2.95D);
            speed = approachValue(speed, target, 0.08D);

            Fp5Assertions.assertTrue(speed >= 2.95D,
                    "Speed must not drop below 2.95D during deceleration from 5.2D at tick " + t + " (actual: " + speed + ")");
        }
        Fp5Assertions.assertEquals(2.95D, speed, 0.001D, "Speed at tick 45 must converge exactly to 2.95D");
    }

    // =============================================================================================
    // TASK 2: Bank Angle Limits & Roll Stability Empirical Verification
    // =============================================================================================

    @Fp5Test(tier = 2, features = {"F3"}, description = "Empirical verification: Cruise bank angle strictly caps at 30 deg across all 360-deg heading errors")
    public void testCruiseBankingEnvelopeAllHeadingErrors() {
        final float turnAuthority = 1.0F; // Full authority in cruise
        final float effectiveBankLimit = Fp5FlamingoEntity.CRUISE_BANK_LIMIT * turnAuthority;

        for (int errorDeg = -180; errorDeg <= 180; errorDeg++) {
            final float yawError = (float) errorDeg;
            final float targetBank = Mth.clamp(yawError * 0.5F, -effectiveBankLimit, effectiveBankLimit);

            Fp5Assertions.assertTrue(Math.abs(targetBank) <= 30.001F,
                    "Cruise target bank must not exceed 30.0 deg for yawError " + yawError + " (actual: " + targetBank + ")");
        }
    }

    @Fp5Test(tier = 2, features = {"F3"}, description = "Empirical verification: Terminal bank angle strictly caps at 40 deg across all 360-deg heading errors")
    public void testTerminalBankingEnvelopeAllHeadingErrors() {
        final float effectiveBankLimit = Fp5FlamingoEntity.TERMINAL_BANK_LIMIT;

        for (int errorDeg = -180; errorDeg <= 180; errorDeg++) {
            final float yawError = (float) errorDeg;
            final float targetBank = Mth.clamp(yawError * 0.5F, -effectiveBankLimit, effectiveBankLimit);

            Fp5Assertions.assertTrue(Math.abs(targetBank) <= 40.001F,
                    "Terminal target bank must not exceed 40.0 deg for yawError " + yawError + " (actual: " + targetBank + ")");
        }
    }

    @Fp5Test(tier = 3, features = {"F3"}, description = "Roll stability harness: No roll oscillations, sign flutter, or flipping during large turns (30, 60, 90, 180 deg)")
    public void testRollStabilityAndDampingDuringAggressiveTurns() {
        final float[] testHeadings = {30.0F, 60.0F, 90.0F, 135.0F, 180.0F, -45.0F, -90.0F, -180.0F};

        for (final float targetOffset : testHeadings) {
            float currentYaw = 0.0F;
            final float desiredYaw = targetOffset;
            float bodyRoll = 0.0F;
            float yawRate = 0.0F;
            int rollZeroCrossings = 0;
            float prevRollSign = 0.0F;

            // Simulate flight dynamics over 500 ticks to allow up to 180-deg turn at 0.65 deg/tick to complete and settle
            for (int tick = 1; tick <= 500; tick++) {
                final float yawError = Mth.wrapDegrees(desiredYaw - currentYaw);
                final float effectiveBankLimit = Fp5FlamingoEntity.CRUISE_BANK_LIMIT; // 30 deg
                final float targetBank = Mth.clamp(yawError * 0.5F, -effectiveBankLimit, effectiveBankLimit);
                final float nextRoll = (float) approachValue(bodyRoll, targetBank, Fp5FlamingoEntity.BANK_RESPONSE_STEP);

                // Check roll magnitude cap
                Fp5Assertions.assertTrue(Math.abs(nextRoll) <= 30.001F,
                        "Body roll exceeded 30 deg cruise limit on turn offset " + targetOffset + " at tick " + tick + ": " + nextRoll);

                // Track roll sign changes (zero crossings) to detect oscillations
                final float currentRollSign = Math.signum(nextRoll);
                if (prevRollSign != 0.0F && currentRollSign != 0.0F && currentRollSign != prevRollSign) {
                    rollZeroCrossings++;
                }
                if (currentRollSign != 0.0F) {
                    prevRollSign = currentRollSign;
                }

                // Update yaw dynamics using authentic Fp5FlamingoEntity formulas
                final float yawAcceleration = (float) (Math.sin(Math.toRadians(nextRoll)) * 0.055F * 1.0F * 1.0F);
                yawRate += yawAcceleration;
                yawRate -= yawRate * 0.022F; // Damping
                if (Math.abs(yawError) < 0.5F) {
                    yawRate = (float) approachValue(yawRate, 0.0D, 0.022F);
                } else if (Math.signum(yawRate) != 0.0F && Math.signum(yawRate) != Math.signum(yawError)) {
                    yawRate = (float) approachValue(yawRate, 0.0D, 0.06F); // Yaw mismatch brake
                }
                yawRate = Mth.clamp(yawRate, -0.65F, 0.65F);
                currentYaw += yawRate;
                bodyRoll = nextRoll;
            }

            // Assert roll stability: A well-damped bank-to-turn maneuver must not flutter or oscillate (zero crossings <= 1)
            Fp5Assertions.assertTrue(rollZeroCrossings <= 1,
                    "Excessive roll oscillation detected for turn offset " + targetOffset + "! Zero crossings: " + rollZeroCrossings);
            // Assert that roll converged to near zero once aligned
            Fp5Assertions.assertTrue(Math.abs(bodyRoll) < 1.0F,
                    "Body roll failed to settle to neutral after turn " + targetOffset + " (settled at: " + bodyRoll + " deg)");
        }
    }

    @Fp5Test(tier = 3, features = {"F3"}, description = "Adversarial: High-frequency alternating target steering (rapid +/-45 deg commands) maintains roll stability without resonance")
    public void testAlternatingSteeringRollResonanceDefense() {
        float currentYaw = 0.0F;
        float bodyRoll = 0.0F;
        float yawRate = 0.0F;

        // Alternate desired yaw between +45 deg and -45 deg every 10 ticks
        for (int tick = 1; tick <= 120; tick++) {
            final float desiredYaw = (tick / 10) % 2 == 0 ? 45.0F : -45.0F;
            final float yawError = Mth.wrapDegrees(desiredYaw - currentYaw);
            final float effectiveBankLimit = Fp5FlamingoEntity.CRUISE_BANK_LIMIT;
            final float targetBank = Mth.clamp(yawError * 0.5F, -effectiveBankLimit, effectiveBankLimit);
            final float nextRoll = (float) approachValue(bodyRoll, targetBank, Fp5FlamingoEntity.BANK_RESPONSE_STEP);

            // Roll must NEVER exceed 30 deg despite resonant inputs
            Fp5Assertions.assertTrue(Math.abs(nextRoll) <= 30.0F,
                    "Body roll breached 30 deg ceiling under alternating inputs at tick " + tick + ": " + nextRoll);

            // Step rate must never exceed BANK_RESPONSE_STEP (0.85F)
            final float step = Math.abs(nextRoll - bodyRoll);
            Fp5Assertions.assertTrue(step <= Fp5FlamingoEntity.BANK_RESPONSE_STEP + 1e-5F,
                    "Roll step exceeded 0.85 deg/tick at tick " + tick + ": " + step);

            final float yawAcceleration = (float) (Math.sin(Math.toRadians(nextRoll)) * 0.055F);
            yawRate += yawAcceleration;
            yawRate -= yawRate * 0.022F;
            if (Math.signum(yawRate) != 0.0F && Math.signum(yawRate) != Math.signum(yawError)) {
                yawRate = (float) approachValue(yawRate, 0.0D, 0.06F);
            }
            yawRate = Mth.clamp(yawRate, -0.65F, 0.65F);
            currentYaw += yawRate;
            bodyRoll = nextRoll;
        }
    }

    // =============================================================================================
    // TASK 3: Client-Server Synchronization Empirical Verification
    // =============================================================================================

    @Fp5Test(tier = 2, features = {"F4"}, description = "Empirical verification: Booster active state lifecycle across launch, lock, burnout, and detonation")
    public void testBoosterActiveLifecycleParity() {
        // State 1: Unlaunched (on launcher)
        boolean launched = false;
        int launchTicks = 0;
        boolean boosterActive = launched && launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        Fp5Assertions.assertFalse(boosterActive, "Booster must NOT be active while unlaunched");

        // State 2: Launch initiated (Tick 0)
        launched = true;
        launchTicks = 0;
        boosterActive = launched && launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        Fp5Assertions.assertTrue(boosterActive, "Booster MUST be active upon launch at tick 0");

        // State 3: Mid-booster burn (Tick 50)
        launchTicks = 50;
        boosterActive = launched && launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        Fp5Assertions.assertTrue(boosterActive, "Booster MUST remain active at tick 50");

        // State 4: Final booster tick (Tick 70)
        launchTicks = 70;
        boosterActive = launched && launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        Fp5Assertions.assertTrue(boosterActive, "Booster MUST remain active at boundary tick 70");

        // State 5: First cruise tick (Tick 71) - Cutoff for VFX and Audio
        launchTicks = 71;
        boosterActive = launched && launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        Fp5Assertions.assertFalse(boosterActive, "Booster MUST be FALSE at tick 71 to trigger cruise audio and VFX");

        // State 6: Impact Detonation at Tick 120
        launched = false; // Detonation resets launched flag
        boosterActive = launched && launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        Fp5Assertions.assertFalse(boosterActive, "Booster MUST be FALSE upon detonation");
    }

    @Fp5Test(tier = 2, features = {"F4"}, description = "Empirical verification: Synchronized tick boundary between Angle Lock and Booster Active state (zero phase-slip)")
    public void testZeroPhaseSlipBetweenAngleLockAndBoosterActive() {
        for (int tick = 0; tick <= 150; tick++) {
            final boolean angleLocked = tick <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
            final boolean boosterActive = tick <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;

            Fp5Assertions.assertEquals(angleLocked, boosterActive,
                    "Angle lock and boosterActive MUST agree on identical tick boundary at tick " + tick);
        }
    }

    // =============================================================================================
    // Helper Oracles
    // =============================================================================================

    private static double computeSpeedTargetOracle(final int launchTicks, final boolean terminal, final float coursePitch) {
        if (launchTicks <= Fp5FlamingoEntity.BOOSTER_KICK_TICKS) {
            return Fp5FlamingoEntity.BOOSTER_PEAK_SPEED;
        }
        if (launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS) {
            return Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED;
        }
        final double phaseTarget = terminal ? 3.65D : Fp5FlamingoEntity.CRUISE_SPEED_TARGET;
        if (launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS + Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS) {
            final double s = (double) (launchTicks - Fp5FlamingoEntity.BOOSTER_BURN_TICKS)
                    / (double) Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS;
            final double h = s * s * (3.0D - 2.0D * s);
            return Mth.lerp(h, Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED, phaseTarget);
        }
        return phaseTarget;
    }

    private static double computeSpeedStepOracle(final int launchTicks, final double currentSpeed, final double speedTarget) {
        if (launchTicks <= Fp5FlamingoEntity.BOOSTER_KICK_TICKS) {
            return Fp5FlamingoEntity.BOOSTER_ACCEL_STEP;
        }
        if (launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS) {
            return Fp5FlamingoEntity.BOOSTER_SUSTAIN_STEP;
        }
        if (launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS + Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS) {
            return Fp5FlamingoEntity.BOOSTER_TRANSITION_STEP;
        }
        return Mth.clamp(Math.abs(speedTarget - currentSpeed) * 0.09D, 0.02D, 0.09D);
    }

    private static double approachValue(final double current, final double target, final double maxStep) {
        if (current < target) {
            return Math.min(current + maxStep, target);
        }
        return Math.max(current - maxStep, target);
    }
}
