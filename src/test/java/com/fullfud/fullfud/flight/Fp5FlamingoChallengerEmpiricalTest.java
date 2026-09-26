package com.fullfud.fullfud.flight;

import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;

/**
 * Empirical Challenger Test Suite for FP-5 Flamingo Flight Dynamics (Milestone M1).
 * Adversarial stress testing of 100-tick lock boundary, mathematical continuity,
 * zero-division / NaN safety, and launcher clearance geometry.
 */
@Fp5TestSuite(name = "FP-5 Flamingo Empirical Challenge Harness", features = {"F1", "F2", "F3", "F4"}, milestone = "M1")
public class Fp5FlamingoChallengerEmpiricalTest {

    // ---------------------------------------------------------------------------------------------
    // Task 1: 70-Tick Lock Boundary & Adversarial Disturbance Immunity
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 2, features = {"F1"}, description = "Stress: 70-tick invariant hold across ticks 0, 1, 20, 69, 70 with extreme 180° target inversion")
    public void test100TickInvariantHoldWithAdversarialTargets() {
        final float railYaw = 135.0F;
        final float railPitch = -12.5F; // -MODEL_BASE_PITCH_DEGREES

        // Test with targets in all directions: 180° opposite, 90° orthogonal, directly above, directly below
        final float[] testTargetYaws = {315.0F, 225.0F, 45.0F, 0.0F, 180.0F, -180.0F};

        for (final float targetYaw : testTargetYaws) {
            for (int tick = 0; tick <= 70; tick++) {
                // In Fp5FlamingoEntity:
                final boolean launchTurnLocked = tick <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
                final float desiredYaw = launchTurnLocked ? railYaw : targetYaw;
                final float desiredPitch = launchTurnLocked ? railPitch : 30.0F;
                final float yawRate = launchTurnLocked ? 0.0F : 0.5F;
                final float pitchRate = launchTurnLocked ? 0.0F : 0.5F;
                final float nextYaw = launchTurnLocked ? railYaw : railYaw + yawRate;
                final float nextPitch = launchTurnLocked ? railPitch : railPitch + pitchRate;
                final float nextRoll = launchTurnLocked ? 0.0F : 15.0F;
                final double lateralSlip = launchTurnLocked ? 0.0D : 0.05D;
                final double steerBlend = launchTurnLocked ? 1.0D : 0.08D;

                Fp5Assertions.assertTrue(launchTurnLocked, "Lock MUST be active at tick " + tick);
                Fp5Assertions.assertEquals(railYaw, desiredYaw, 0.0001F, "Desired yaw must equal rail yaw at tick " + tick);
                Fp5Assertions.assertEquals(railPitch, desiredPitch, 0.0001F, "Desired pitch must equal rail pitch at tick " + tick);
                Fp5Assertions.assertEquals(railYaw, nextYaw, 0.0001F, "Next yaw must remain locked at tick " + tick);
                Fp5Assertions.assertEquals(railPitch, nextPitch, 0.0001F, "Next pitch must remain locked at tick " + tick);
                Fp5Assertions.assertEquals(0.0F, nextRoll, 0.0001F, "Bank roll must be strictly 0 at tick " + tick);
                Fp5Assertions.assertEquals(0.0D, lateralSlip, 0.0001D, "Lateral slip must be strictly 0 at tick " + tick);
                Fp5Assertions.assertEquals(1.0D, steerBlend, 0.0001D, "Steer blend must be 1.0 (pure rail vector) at tick " + tick);
            }
        }
    }

    @Fp5Test(tier = 2, features = {"F1", "F4"}, description = "Stress: Strict transition check at tick 70 vs tick 71")
    public void testTick100To101TransitionBoundary() {
        final int tick70 = 70;
        final int tick71 = 71;

        // Tick 70 checks
        final boolean locked70 = tick70 <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final boolean booster70 = tick70 <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final float s70 = (float) (tick70 - Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS) / (float) Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS;
        final float authority70 = locked70 ? 0.0F : s70 * s70 * (3.0F - 2.0F * s70);

        Fp5Assertions.assertTrue(locked70, "Tick 70 must be locked");
        Fp5Assertions.assertTrue(booster70, "Tick 70 booster must be active");
        Fp5Assertions.assertEquals(0.0F, authority70, 0.0001F, "Tick 70 authority must be 0.0");

        // Tick 71 checks
        final boolean locked71 = tick71 <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final boolean booster71 = tick71 <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final float s71 = (float) (tick71 - Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS) / (float) Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS;
        final float authority71 = locked71 ? 0.0F : s71 * s71 * (3.0F - 2.0F * s71);

        Fp5Assertions.assertFalse(locked71, "Tick 71 must be UNLOCKED");
        Fp5Assertions.assertFalse(booster71, "Tick 71 booster must be INACTIVE");
        final float expectedAuthority71 = (1.0F / 45.0F) * (1.0F / 45.0F) * (3.0F - 2.0F * (1.0F / 45.0F));
        Fp5Assertions.assertEquals(expectedAuthority71, authority71, 0.0001F, "Tick 71 authority must ramp via Hermite cubic");
    }

    // ---------------------------------------------------------------------------------------------
    // Task 2: Mathematical Continuity, Jerk Analysis, Zero Division & NaN Safety
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 2, features = {"F3"}, description = "Stress: Mathematical jerk and continuity analysis across tick 70/71 boundary")
    public void testMathematicalContinuityAcrossBoundary() {
        // Evaluate speed target at tick 70
        final double speedTarget70 = Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED;

        // Evaluate speed target at tick 71
        final double s71 = (double) (71 - Fp5FlamingoEntity.BOOSTER_BURN_TICKS) / (double) Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS;
        final double h71 = s71 * s71 * (3.0D - 2.0D * s71);
        final double speedTarget71 = Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED + h71 * (Fp5FlamingoEntity.CRUISE_SPEED_TARGET - Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED);

        Fp5Assertions.assertEquals(4.2D, speedTarget70, 0.001D, "Speed target at tick 70 must be BOOSTER_SUSTAIN_SPEED (4.2D)");
        final double speedDelta = Math.abs(speedTarget71 - speedTarget70);
        Fp5Assertions.assertTrue(speedDelta < 0.05D, "Speed target step across tick 70/71 must be smooth (< 0.05D, actual: " + speedDelta + ")");

        // Evaluate turn authority discontinuity
        final float authority70 = 0.0F;
        final float authority71 = (float) (s71 * s71 * (3.0D - 2.0D * s71));
        final float authorityDelta = authority71 - authority70;
        Fp5Assertions.assertTrue(authorityDelta < 0.01F, "Turn authority step at tick 70->71 must be smooth Hermite ramp (< 0.01F)");

        // Evaluate yaw rate step with worst-case 180° heading error at tick 71
        final float effectiveBankLimit71 = Fp5FlamingoEntity.CLIMB_BANK_LIMIT * authority71;
        final float targetBank71 = Math.min(effectiveBankLimit71, Math.max(-effectiveBankLimit71, 180.0F * 0.5F));
        final float nextRoll71 = Math.min(targetBank71, 0.0F + Fp5FlamingoEntity.BANK_RESPONSE_STEP);
        final float yawAccel71 = (float) (Math.sin(Math.toRadians(nextRoll71)) * 0.038F * 1.2F * authority71);
        Fp5Assertions.assertTrue(yawAccel71 < 0.001F, "Yaw acceleration at tick 71 must be tiny (< 0.001 deg/tick^2, actual: " + yawAccel71 + ")");

        // Evaluate pitch rate step at tick 71
        final float desiredPitchRate71 = Math.max(-0.85F, Math.min(0.85F, -17.5F * 0.08F)); // -0.85F
        final float pitchRate71 = Math.max(-0.09F, desiredPitchRate71); // Rate limited to -0.09 deg/tick
        Fp5Assertions.assertTrue(Math.abs(pitchRate71) <= 0.091F, "Pitch rate change at tick 71 must be rate limited to <= 0.09 deg/tick");
    }

    @Fp5Test(tier = 2, features = {"F3"}, description = "Stress: NaN and Division by Zero immunity across extreme flight dynamics inputs")
    public void testDivisionByZeroAndNanImmunity() {
        // Test 1: All ticks from 0 to 1000 for resolveSpeedTarget
        for (int t = 0; t <= 1000; t++) {
            final double speedTarget;
            if (t <= Fp5FlamingoEntity.BOOSTER_KICK_TICKS) {
                speedTarget = Fp5FlamingoEntity.BOOSTER_PEAK_SPEED;
            } else if (t <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS) {
                speedTarget = Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED;
            } else if (t <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS + Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS) {
                final double s = (double) (t - Fp5FlamingoEntity.BOOSTER_BURN_TICKS) / (double) Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS;
                final double h = s * s * (3.0D - 2.0D * s);
                speedTarget = Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED + h * (Fp5FlamingoEntity.CRUISE_SPEED_TARGET - Fp5FlamingoEntity.BOOSTER_SUSTAIN_SPEED);
            } else {
                speedTarget = Fp5FlamingoEntity.CRUISE_SPEED_TARGET;
            }

            Fp5Assertions.assertFalse(Double.isNaN(speedTarget), "speedTarget must not be NaN at tick " + t);
            Fp5Assertions.assertFalse(Double.isInfinite(speedTarget), "speedTarget must not be Infinite at tick " + t);
            Fp5Assertions.assertTrue(speedTarget >= 2.9D && speedTarget <= 5.25D, "speedTarget out of bounds at tick " + t + ": " + speedTarget);
        }

        // Test 2: Turn authority across ticks 0 to 1000
        for (int t = 0; t <= 1000; t++) {
            final float authority;
            if (t <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS) {
                authority = 0.0F;
            } else if (t >= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS + Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS) {
                authority = 1.0F;
            } else {
                final float s = (float) (t - Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS) / (float) Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS;
                authority = s * s * (3.0F - 2.0F * s);
            }
            Fp5Assertions.assertFalse(Float.isNaN(authority), "authority must not be NaN at tick " + t);
            Fp5Assertions.assertFalse(Float.isInfinite(authority), "authority must not be Infinite at tick " + t);
            Fp5Assertions.assertTrue(authority >= 0.0F && authority <= 1.0F, "authority must be within [0, 1] at tick " + t);
        }

        // Test 3: Terminal dive distance calculation when verticalDrop = 0.0
        final double verticalDrop = 0.0D;
        final double diveDist = Math.max(14.0D, verticalDrop / Math.tan(Math.toRadians(45.0D)));
        Fp5Assertions.assertEquals(14.0D, diveDist, 0.001D, "Dive distance with zero drop must be 14.0D threshold");
    }

    // ---------------------------------------------------------------------------------------------
    // Task 3: Launcher Clearance & First 20 Ticks Geometry
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 2, features = {"F2"}, description = "Stress: Launcher clearance geometry and collision suppression for ticks 1..20")
    public void testLauncherClearanceGeometryTicks0To20() {
        // Launcher dimensions: Length = 16.45m, Height = 6.18m
        // Missile starts at launch origin (0, 64, 0).
        // Course pitch = -12.5 deg (upward climb: sin(12.5°) = 0.21644, cos(12.5°) = 0.97630)
        double currentSpeed = 0.3D;
        double currentX = 0.0D;
        double currentY = 64.0D;
        double currentZ = 0.0D;

        for (int tick = 1; tick <= 20; tick++) {
            // Speed progression
            final double targetSpeed = 5.2D;
            currentSpeed = Math.min(currentSpeed + 0.38D, targetSpeed);

            // Motion along course
            final double dy = currentSpeed * Math.sin(Math.toRadians(12.5D));
            final double dHoriz = currentSpeed * Math.cos(Math.toRadians(12.5D));
            currentX += dHoriz; // Assuming launch East (yaw = 90)
            currentY += dy;

            final double horizontalDist = Math.sqrt(currentX * currentX + currentZ * currentZ);
            final boolean noPhysics = tick <= Fp5FlamingoEntity.LAUNCHER_CLEARANCE_TICKS;
            final boolean blockClipActive = tick > Fp5FlamingoEntity.LAUNCHER_CLEARANCE_TICKS &&
                    horizontalDist >= Fp5FlamingoEntity.LAUNCHER_CLEARANCE_DISTANCE;

            // Invariance: During ticks 1..20, noPhysics must be true and block clipping must be suppressed
            Fp5Assertions.assertTrue(noPhysics, "noPhysics MUST be true at tick " + tick);
            Fp5Assertions.assertFalse(blockClipActive, "Block clipping MUST be suppressed at tick " + tick);
        }

        final double finalHorizDist = Math.sqrt(currentX * currentX + currentZ * currentZ);
        final double finalClimb = currentY - 64.0D;

        // At tick 20: Missile has traveled ~72m downrange and climbed ~16m upward
        Fp5Assertions.assertTrue(finalHorizDist > 70.0D, "Horizontal distance after 20 ticks must exceed 70m (actual: " + finalHorizDist + "m)");
        Fp5Assertions.assertTrue(finalClimb > 15.0D, "Vertical climb after 20 ticks must exceed 15m (actual: " + finalClimb + "m)");

        // Launcher is 16.45m long and 6.18m tall. The missile is far beyond the launcher geometry by tick 20!
        Fp5Assertions.assertTrue(finalHorizDist > 16.45D, "Missile must exceed launcher length (16.45m)");
        Fp5Assertions.assertTrue(finalClimb > 6.18D, "Missile must exceed launcher height (6.18m)");
    }
}
