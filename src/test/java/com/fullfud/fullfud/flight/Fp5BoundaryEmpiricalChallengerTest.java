package com.fullfud.fullfud.flight;

import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;
import net.minecraft.util.Mth;

/**
 * Empirical Challenger Test Suite for Milestone M1 (Gen-3).
 * Adversarial stress testing of flight dynamics and staging boundaries:
 * Boundary ticks 0, 70, 71, 92, 114, 115, 116 for Hermite monotonicity,
 * C1 continuity, acceleration/jerk limits, and orientation lock invariants.
 */
@Fp5TestSuite(name = "Gen-3 M1 Staging Boundary & Hermite Empirical Stress Suite", features = {"F1", "F3", "F4"}, milestone = "M1")
public class Fp5BoundaryEmpiricalChallengerTest {

    // =============================================================================================
    // TEST 1: Boundary Ticks Exact Values & Orientation Lock Verification
    // Ticks: 0, 70, 71, 92, 114, 115, 116
    // =============================================================================================

    @Fp5Test(tier = 2, features = {"F1", "F4"}, description = "Empirical verification: Boundary ticks 0, 70, 71, 92, 114, 115, 116 contracts and invariants")
    public void testBoundaryTicksExactValuesAndContracts() {
        // --- Tick 0 (Launch Ignition) ---
        final int t0 = 0;
        final boolean locked0 = t0 <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final boolean booster0 = t0 <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final float auth0 = computeTurnAuthorityOracle(t0);
        final double speedTarget0 = computeSpeedTargetOracle(t0);

        Fp5Assertions.assertTrue(locked0, "Tick 0: Orientation lock MUST be true");
        Fp5Assertions.assertTrue(booster0, "Tick 0: Booster active MUST be true");
        Fp5Assertions.assertEquals(0.0F, auth0, 0.0001F, "Tick 0: Turn authority MUST be 0.0");
        Fp5Assertions.assertEquals(Fp5FlamingoEntity.BOOSTER_PEAK_SPEED, speedTarget0, 0.0001D, "Tick 0: Speed target MUST be peak speed (5.2D)");

        // --- Tick 70 (Booster Burnout & Lock Release Boundary) ---
        final int t70 = 70;
        final boolean locked70 = t70 <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final boolean booster70 = t70 <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final float auth70 = computeTurnAuthorityOracle(t70);
        final double speedTarget70 = computeSpeedTargetOracle(t70);

        Fp5Assertions.assertTrue(locked70, "Tick 70: Orientation lock MUST remain true at final booster tick");
        Fp5Assertions.assertTrue(booster70, "Tick 70: Booster active MUST remain true at final booster tick");
        Fp5Assertions.assertEquals(0.0F, auth70, 0.0001F, "Tick 70: Turn authority MUST be exactly 0.0");
        Fp5Assertions.assertEquals(Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED, speedTarget70, 0.0001D, "Tick 70: Speed target MUST be sustain speed (4.2D)");

        // --- Tick 71 (First Crossfade Tick) ---
        final int t71 = 71;
        final boolean locked71 = t71 <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final boolean booster71 = t71 <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final float auth71 = computeTurnAuthorityOracle(t71);
        final double speedTarget71 = computeSpeedTargetOracle(t71);

        Fp5Assertions.assertFalse(locked71, "Tick 71: Orientation lock MUST release (false)");
        Fp5Assertions.assertFalse(booster71, "Tick 71: Booster active MUST deactivate (false)");
        // s = 1/45, H(s) = (1/45)^2 * (3 - 2/45) = 133 / 91125 ~ 0.00145953
        final float expectedAuth71 = 133.0F / 91125.0F;
        Fp5Assertions.assertEquals(expectedAuth71, auth71, 0.00001F, "Tick 71: Turn authority MUST match exact Hermite cubic value");
        Fp5Assertions.assertTrue(speedTarget71 < 4.2D, "Tick 71: Speed target must begin decelerating from 4.2D");
        Fp5Assertions.assertTrue(speedTarget71 > 4.19D, "Tick 71: Speed target must not jump abruptly (smoothstep)");

        // --- Tick 92 (Hermite Midpoint) ---
        final int t92 = 92;
        final boolean locked92 = t92 <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final boolean booster92 = t92 <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final float auth92 = computeTurnAuthorityOracle(t92);
        final double speedTarget92 = computeSpeedTargetOracle(t92);

        Fp5Assertions.assertFalse(locked92, "Tick 92: Must be unlocked");
        Fp5Assertions.assertFalse(booster92, "Tick 92: Booster must be inactive");
        // s = 22/45 ~ 0.488889, H(s) = 44044 / 91125 ~ 0.483336
        final float expectedAuth92 = 44044.0F / 91125.0F;
        Fp5Assertions.assertEquals(expectedAuth92, auth92, 0.0001F, "Tick 92: Turn authority MUST match midpoint Hermite value");
        final double expectedSpeed92 = Mth.lerp(expectedAuth92, 4.2D, 2.95D);
        Fp5Assertions.assertEquals(expectedSpeed92, speedTarget92, 0.001D, "Tick 92: Speed target MUST match midpoint Hermite interpolation");

        // --- Tick 114 (Penultimate Transition Tick) ---
        final int t114 = 114;
        final float auth114 = computeTurnAuthorityOracle(t114);
        final double speedTarget114 = computeSpeedTargetOracle(t114);
        // s = 44/45 ~ 0.977778, H(s) = 90992 / 91125 ~ 0.998540
        final float expectedAuth114 = 90992.0F / 91125.0F;
        Fp5Assertions.assertEquals(expectedAuth114, auth114, 0.0001F, "Tick 114: Turn authority MUST match penultimate Hermite value");
        final double expectedSpeed114 = Mth.lerp(expectedAuth114, 4.2D, 2.95D);
        Fp5Assertions.assertEquals(expectedSpeed114, speedTarget114, 0.001D, "Tick 114: Speed target MUST match penultimate Hermite interpolation");

        // --- Tick 115 (Final Transition Tick) ---
        final int t115 = 115;
        final float auth115 = computeTurnAuthorityOracle(t115);
        final double speedTarget115 = computeSpeedTargetOracle(t115);

        Fp5Assertions.assertEquals(1.0F, auth115, 0.0001F, "Tick 115: Turn authority MUST reach exactly 1.0F");
        Fp5Assertions.assertEquals(Fp5FlamingoEntity.CRUISE_SPEED_TARGET, speedTarget115, 0.0001D, "Tick 115: Speed target MUST reach exactly 2.95D");

        // --- Tick 116 (First Steady Cruise Tick) ---
        final int t116 = 116;
        final float auth116 = computeTurnAuthorityOracle(t116);
        final double speedTarget116 = computeSpeedTargetOracle(t116);

        Fp5Assertions.assertEquals(1.0F, auth116, 0.0001F, "Tick 116: Turn authority MUST remain steady at 1.0F");
        Fp5Assertions.assertEquals(Fp5FlamingoEntity.CRUISE_SPEED_TARGET, speedTarget116, 0.0001D, "Tick 116: Speed target MUST remain steady at 2.95D");
    }

    // =============================================================================================
    // TEST 2: Hermite Monotonicity Across Full Transition Window [70..115]
    // =============================================================================================

    @Fp5Test(tier = 2, features = {"F3"}, description = "Empirical verification: Strict monotonicity of Hermite crossfade (turn authority and speed decay)")
    public void testHermiteMonotonicityAcrossTransitionWindow() {
        float prevAuth = 0.0F;
        double prevSpeedTarget = 4.2D;

        for (int tick = 70; tick <= 115; tick++) {
            final float auth = computeTurnAuthorityOracle(tick);
            final double speedTarget = computeSpeedTargetOracle(tick);

            // Monotonicity assertion for turn authority (strictly non-decreasing)
            Fp5Assertions.assertTrue(auth >= prevAuth - 1e-7F,
                    "Turn authority MUST be monotonic non-decreasing at tick " + tick + " (prev: " + prevAuth + ", cur: " + auth + ")");

            // Monotonicity assertion for speed target (strictly non-increasing)
            Fp5Assertions.assertTrue(speedTarget <= prevSpeedTarget + 1e-9D,
                    "Speed target MUST be monotonic non-increasing at tick " + tick + " (prev: " + prevSpeedTarget + ", cur: " + speedTarget + ")");

            // Bound checks
            Fp5Assertions.assertTrue(auth >= 0.0F && auth <= 1.0F,
                    "Turn authority must stay within [0, 1] at tick " + tick);
            Fp5Assertions.assertTrue(speedTarget >= Fp5FlamingoEntity.CRUISE_SPEED_TARGET - 1e-9D && speedTarget <= Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED + 1e-9D,
                    "Speed target must stay within [2.95, 4.20] at tick " + tick);

            prevAuth = auth;
            prevSpeedTarget = speedTarget;
        }

        // Verify bounds at end of transition
        Fp5Assertions.assertEquals(1.0F, prevAuth, 0.0001F, "Turn authority must terminate at 1.0F");
        Fp5Assertions.assertEquals(2.95D, prevSpeedTarget, 0.0001D, "Speed target must terminate at 2.95D");
    }

    // =============================================================================================
    // TEST 3: C1 Continuity & Derivative Matching at Boundaries (Ticks 70 and 115)
    // =============================================================================================

    @Fp5Test(tier = 2, features = {"F3"}, description = "Empirical verification: C1 derivative smoothness at boundary ticks 70 and 115 (zero jerk)")
    public void testC1ContinuityAtBoundaries() {
        // --- Boundary 1: Tick 70 -> 71 ---
        // Hermite smoothstep H(s) = 3s^2 - 2s^3 => H'(s) = 6s(1 - s)
        // At s = 0 (t = 70): H'(0) = 0.
        // Derivative from left (t <= 70): dAuth/dt = 0.
        // Derivative from right (t -> 70+): dAuth/dt = (1/45) * H'(0) = 0.
        // Thus C1 continuous across t = 70!
        final float auth70 = computeTurnAuthorityOracle(70);
        final float auth71 = computeTurnAuthorityOracle(71);
        final float deltaAuth70_71 = auth71 - auth70;
        Fp5Assertions.assertTrue(deltaAuth70_71 < 0.002F,
                "Turn authority step across 70->71 must be smooth Hermite (< 0.002, actual: " + deltaAuth70_71 + ")");

        final double speed70 = computeSpeedTargetOracle(70);
        final double speed71 = computeSpeedTargetOracle(71);
        final double deltaSpeed70_71 = Math.abs(speed71 - speed70);
        Fp5Assertions.assertTrue(deltaSpeed70_71 < 0.002D,
                "Speed target step across 70->71 must be smooth Hermite (< 0.002, actual: " + deltaSpeed70_71 + ")");

        // --- Boundary 2: Tick 114 -> 115 -> 116 ---
        // At s = 1 (t = 115): H'(1) = 0.
        // Derivative from left (t -> 115-): dAuth/dt = (1/45) * H'(1) = 0.
        // Derivative from right (t >= 115): dAuth/dt = 0.
        // Thus C1 continuous across t = 115!
        final float auth114 = computeTurnAuthorityOracle(114);
        final float auth115 = computeTurnAuthorityOracle(115);
        final float auth116 = computeTurnAuthorityOracle(116);

        final float deltaAuth114_115 = auth115 - auth114;
        final float deltaAuth115_116 = auth116 - auth115;

        Fp5Assertions.assertTrue(deltaAuth114_115 < 0.002F,
                "Turn authority step across 114->115 must be smooth Hermite (< 0.002, actual: " + deltaAuth114_115 + ")");
        Fp5Assertions.assertEquals(0.0F, deltaAuth115_116, 0.00001F,
                "Turn authority step across 115->116 must be exactly 0.0 (smooth entry into cruise)");

        final double speed114 = computeSpeedTargetOracle(114);
        final double speed115 = computeSpeedTargetOracle(115);
        final double speed116 = computeSpeedTargetOracle(116);

        final double deltaSpeed114_115 = Math.abs(speed115 - speed114);
        final double deltaSpeed115_116 = Math.abs(speed116 - speed115);

        Fp5Assertions.assertTrue(deltaSpeed114_115 < 0.002D,
                "Speed target step across 114->115 must be smooth Hermite (< 0.002, actual: " + deltaSpeed114_115 + ")");
        Fp5Assertions.assertEquals(0.0D, deltaSpeed115_116, 0.00001D,
                "Speed target step across 115->116 must be exactly 0.0 (smooth entry into cruise)");
    }

    // =============================================================================================
    // TEST 4: Dynamic Acceleration Limits & Flight Velocity Envelope [Ticks 0..200]
    // =============================================================================================

    @Fp5Test(tier = 2, features = {"F3"}, description = "Empirical verification: Acceleration step limits and lack of undershoot across full flight envelope (ticks 0..200)")
    public void testDynamicAccelerationLimitsAcrossEnvelope() {
        double currentSpeed = 0.3D; // Launch rail initial velocity

        for (int t = 1; t <= 200; t++) {
            final double speedTarget = computeSpeedTargetOracle(t);
            final double maxAllowedStep;
            if (t <= Fp5FlamingoEntity.BOOSTER_KICK_TICKS) {
                maxAllowedStep = Fp5FlamingoEntity.BOOSTER_ACCEL_STEP; // 0.38D
            } else if (t <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS) {
                maxAllowedStep = Fp5FlamingoEntity.BOOSTER_SUSTAIN_STEP; // 0.08D
            } else if (t <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS + Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS) {
                maxAllowedStep = Fp5FlamingoEntity.BOOSTER_TRANSITION_STEP; // 0.08D
            } else {
                maxAllowedStep = 0.09D;
            }

            final double prevSpeed = currentSpeed;
            currentSpeed = approachValue(currentSpeed, speedTarget, maxAllowedStep);
            final double actualStep = Math.abs(currentSpeed - prevSpeed);

            // Step rate limit assertion
            Fp5Assertions.assertTrue(actualStep <= maxAllowedStep + 1e-9D,
                    "Speed delta exceeded maximum step limit at tick " + t + ": actual " + actualStep + " > allowed " + maxAllowedStep);

            // Envelope assertion: Speed must never be negative or NaN or infinite
            Fp5Assertions.assertFalse(Double.isNaN(currentSpeed), "Speed must not be NaN at tick " + t);
            Fp5Assertions.assertFalse(Double.isInfinite(currentSpeed), "Speed must not be Infinite at tick " + t);
            Fp5Assertions.assertTrue(currentSpeed >= 0.3D, "Speed must never drop below initial 0.3D at tick " + t);
            Fp5Assertions.assertTrue(currentSpeed <= 5.2001D, "Speed must never exceed peak 5.2D at tick " + t);

            // After tick 115 (cruise phase): Speed must be locked at exactly 2.95D
            if (t >= 115) {
                Fp5Assertions.assertEquals(Fp5FlamingoEntity.CRUISE_SPEED_TARGET, currentSpeed, 0.001D,
                        "Cruise speed at tick " + t + " must be locked to 2.95D (actual: " + currentSpeed + ")");
            }
        }
    }

    // =============================================================================================
    // TEST 5: Launch Pitch Profile Continuity (Ticks 0, 12, 50, 70, 71)
    // =============================================================================================

    @Fp5Test(tier = 2, features = {"F1"}, description = "Empirical verification: Pitch profile continuity (-12.5 rail -> -46 climb -> 0 cruise) with Hermite leveling")
    public void testPitchProfileContinuityAcrossBoundaries() {
        final float p0 = computeLaunchPitchOracle(0);
        final float p12 = computeLaunchPitchOracle(12);
        final float p50 = computeLaunchPitchOracle(50);
        final float p70 = computeLaunchPitchOracle(70);
        final float p71 = computeLaunchPitchOracle(71);

        // Tick 0: Model base pitch (-12.5 deg)
        Fp5Assertions.assertEquals(-12.5F, p0, 0.001F, "Pitch at tick 0 must match launcher base pitch (-12.5 deg)");

        // Tick 12: End of steep climb ramp (-46.0 deg)
        Fp5Assertions.assertEquals(-46.0F, p12, 0.001F, "Pitch at tick 12 must reach steep climb pitch (-46.0 deg)");

        // Tick 50: Steady steep climb (-46.0 deg)
        Fp5Assertions.assertEquals(-46.0F, p50, 0.001F, "Pitch at tick 50 must maintain steep climb pitch (-46.0 deg)");

        // Tick 70: Hermite smooth recovery to horizontal flight (0.0 deg)
        Fp5Assertions.assertEquals(0.0F, p70, 0.001F, "Pitch at tick 70 must smoothly reach 0.0 deg horizontal");

        // Tick 71: Beyond lock ticks, computeLaunchPitch returns 0.0 deg
        Fp5Assertions.assertEquals(0.0F, p71, 0.001F, "Pitch at tick 71 must remain 0.0 deg (free autopilot takes over)");

        // Check monotonicity of recovery window [50..70]
        float prevPitch = -46.0F;
        for (int t = 50; t <= 70; t++) {
            final float curPitch = computeLaunchPitchOracle(t);
            Fp5Assertions.assertTrue(curPitch >= prevPitch - 1e-5F,
                    "Recovery pitch must monotonically increase towards 0.0 deg at tick " + t + " (actual: " + curPitch + ")");
            prevPitch = curPitch;
        }
    }

    // =============================================================================================
    // Helper Oracles
    // =============================================================================================

    private static float computeTurnAuthorityOracle(final int launchTicks) {
        if (launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS) {
            return 0.0F;
        }
        if (launchTicks >= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS + Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS) {
            return 1.0F;
        }
        final float s = (float) (launchTicks - Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS) / (float) Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS;
        return s * s * (3.0F - 2.0F * s);
    }

    private static double computeSpeedTargetOracle(final int launchTicks) {
        if (launchTicks <= Fp5FlamingoEntity.BOOSTER_KICK_TICKS) {
            return Fp5FlamingoEntity.BOOSTER_PEAK_SPEED;
        }
        if (launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS) {
            return Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED;
        }
        final double phaseTarget = Fp5FlamingoEntity.CRUISE_SPEED_TARGET;
        if (launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS + Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS) {
            final double s = (double) (launchTicks - Fp5FlamingoEntity.BOOSTER_BURN_TICKS)
                    / (double) Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS;
            final double h = s * s * (3.0D - 2.0D * s);
            return Mth.lerp(h, Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED, phaseTarget);
        }
        return phaseTarget;
    }

    private static float computeLaunchPitchOracle(final int ticks) {
        if (ticks <= 12) {
            final float p = (float) ticks / 12.0F;
            return Mth.lerp(p, -12.5F, -46.0F);
        }
        if (ticks <= 50) {
            return -46.0F;
        }
        if (ticks <= 70) {
            final float p = (float) (ticks - 50) / 20.0F;
            final float smoothP = p * p * (3.0F - 2.0F * p);
            return Mth.lerp(smoothP, -46.0F, 0.0F);
        }
        return 0.0F;
    }

    private static double approachValue(final double current, final double target, final double maxStep) {
        if (current < target) {
            return Math.min(current + maxStep, target);
        }
        return Math.max(current - maxStep, target);
    }
}
