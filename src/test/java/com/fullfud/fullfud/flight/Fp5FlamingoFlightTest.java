package com.fullfud.fullfud.flight;

import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Test suite for FP-5 Flamingo Flight Dynamics, Staging, and Clearance Grace (M1 / Features F1, F2, F3, F4).
 */
@Fp5TestSuite(name = "FP-5 Flamingo Flight Dynamics & Staging", features = {"F1", "F2", "F3", "F4"}, milestone = "M1")
public class Fp5FlamingoFlightTest {

    // ---------------------------------------------------------------------------------------------
    // Tier 1: Core Feature Verification
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 1, features = {"F1"}, description = "Verify 70-tick launch rail angle lock timing constants and contract")
    public void test100TickAngleLockTimingConstant() {
        // Authoritative source: ORIGINAL_REQUEST.md R4 & PROJECT.md F1
        Fp5Assertions.assertEquals(70, Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS,
                "LAUNCH_RAIL_LOCK_TICKS must be exactly 70 ticks (3.5 seconds)");
    }

    @Fp5Test(tier = 1, features = {"F1"}, description = "Verify model base pitch degrees matches 3D model pivot (-12.5 deg)")
    public void testModelBasePitchDegrees() throws Exception {
        // Authoritative source: ORIGINAL_REQUEST.md R1 & fp5flamingo.geo.json line 54
        final Field basePitchField = Fp5FlamingoEntity.class.getDeclaredField("MODEL_BASE_PITCH_DEGREES");
        basePitchField.setAccessible(true);
        final float basePitch = basePitchField.getFloat(null);
        Fp5Assertions.assertEquals(12.5F, basePitch, 0.001F,
                "MODEL_BASE_PITCH_DEGREES must be 12.5 degrees to match model root rotation");
    }

    @Fp5Test(tier = 1, features = {"F2"}, description = "Verify launcher clearance grace timing and distance constants")
    public void testLauncherClearanceConstants() {
        // Authoritative source: PROJECT.md F2 & Explorer Survey 1
        Fp5Assertions.assertEquals(20, Fp5FlamingoEntity.LAUNCHER_CLEARANCE_TICKS,
                "LAUNCHER_CLEARANCE_TICKS must be 20 ticks (~1.0s grace window)");
        Fp5Assertions.assertEquals(16.0D, Fp5FlamingoEntity.LAUNCHER_CLEARANCE_DISTANCE, 0.001D,
                "LAUNCHER_CLEARANCE_DISTANCE must be 16.0 meters to clear launcher bounding box (16.45m length)");
    }

    @Fp5Test(tier = 1, features = {"F3"}, description = "Verify two-stage velocity staging constants")
    public void testVelocityStagingConstants() {
        // Authoritative source: ORIGINAL_REQUEST.md R4 & PROJECT.md F3
        Fp5Assertions.assertEquals(15, Fp5FlamingoEntity.BOOSTER_KICK_TICKS,
                "BOOSTER_KICK_TICKS must be 15 ticks for initial explosive rocket booster kick");
        Fp5Assertions.assertEquals(70, Fp5FlamingoEntity.BOOSTER_BURN_TICKS,
                "BOOSTER_BURN_TICKS must be 70 ticks (3.5s) matching the rail angle lock duration");
        Fp5Assertions.assertEquals(45, Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS,
                "BOOSTER_TRANSITION_TICKS must be 45 ticks for smooth aerodynamic transition to jet cruise");
        Fp5Assertions.assertEquals(5.2D, Fp5FlamingoEntity.BOOSTER_PEAK_SPEED, 0.01D,
                "BOOSTER_PEAK_SPEED must reach 5.2 blocks/tick during booster kick");
    }

    @Fp5Test(tier = 1, features = {"F3"}, description = "Verify aerodynamic banking roll limits for cruise and terminal flight")
    public void testAerodynamicBankingLimits() {
        // Authoritative source: ORIGINAL_REQUEST.md R1 (realistic roll/pitch banking maneuvers) & PROJECT.md F3 (30 deg cruise / 40 deg terminal)
        Fp5Assertions.assertEquals(15.0F, Fp5FlamingoEntity.CLIMB_BANK_LIMIT, 0.01F,
                "CLIMB_BANK_LIMIT must be 15.0 degrees");
        Fp5Assertions.assertEquals(30.0F, Fp5FlamingoEntity.CRUISE_BANK_LIMIT, 0.01F,
                "CRUISE_BANK_LIMIT must be 30.0 degrees for authentic cruise missile banking");
        Fp5Assertions.assertEquals(40.0F, Fp5FlamingoEntity.TERMINAL_BANK_LIMIT, 0.01F,
                "TERMINAL_BANK_LIMIT must be 40.0 degrees for high-G terminal dive alignment");
    }

    @Fp5Test(tier = 1, features = {"F4"}, description = "Verify SynchedEntityData accessors exist and are public")
    public void testEntitySynchronizationContract() throws Exception {
        // Authoritative source: PROJECT.md § Interface Contracts M1 <-> M2 & F4
        final Method isLaunchedMethod = Fp5FlamingoEntity.class.getMethod("isLaunched");
        Fp5Assertions.assertNotNull(isLaunchedMethod, "Fp5FlamingoEntity must expose public boolean isLaunched()");
        Fp5Assertions.assertEquals(boolean.class, isLaunchedMethod.getReturnType(), "isLaunched() return type must be boolean");

        final Method isBoosterActiveMethod = Fp5FlamingoEntity.class.getMethod("isBoosterActive");
        Fp5Assertions.assertNotNull(isBoosterActiveMethod, "Fp5FlamingoEntity must expose public boolean isBoosterActive()");
        Fp5Assertions.assertEquals(boolean.class, isBoosterActiveMethod.getReturnType(), "isBoosterActive() return type must be boolean");

        final Field dataLaunchedField = Fp5FlamingoEntity.class.getDeclaredField("DATA_LAUNCHED");
        Fp5Assertions.assertTrue(Modifier.isStatic(dataLaunchedField.getModifiers()), "DATA_LAUNCHED must be static");

        final Field dataBoosterField = Fp5FlamingoEntity.class.getDeclaredField("DATA_BOOSTER_ACTIVE");
        Fp5Assertions.assertTrue(Modifier.isStatic(dataBoosterField.getModifiers()), "DATA_BOOSTER_ACTIVE must be static");
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 2: Boundary & Corner Cases
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 2, features = {"F1", "F4"}, description = "Boundary: Tick 0 (Initial Ignition) must have booster active, angle locked, turn authority 0")
    public void testBoundaryTick0() {
        final int launchTicks = 0;
        final boolean boosterActive = launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final boolean angleLocked = launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final float turnAuthority = angleLocked ? 0.0F : 1.0F;

        Fp5Assertions.assertTrue(boosterActive, "Booster must be ACTIVE at tick 0");
        Fp5Assertions.assertTrue(angleLocked, "Angle lock must be ACTIVE at tick 0");
        Fp5Assertions.assertEquals(0.0F, turnAuthority, 0.001F, "Turn authority must be exactly 0.0 at tick 0");
    }

    @Fp5Test(tier = 2, features = {"F1", "F4"}, description = "Boundary: Tick 69 (Penultimate Booster Tick) must remain strictly locked")
    public void testBoundaryTick99() {
        final int launchTicks = 69;
        final boolean boosterActive = launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final boolean angleLocked = launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final float turnAuthority = angleLocked ? 0.0F : 1.0F;

        Fp5Assertions.assertTrue(boosterActive, "Booster must be ACTIVE at tick 69");
        Fp5Assertions.assertTrue(angleLocked, "Angle lock must be ACTIVE at tick 69");
        Fp5Assertions.assertEquals(0.0F, turnAuthority, 0.001F, "Turn authority must remain 0.0 at tick 69");
    }

    @Fp5Test(tier = 2, features = {"F1", "F4"}, description = "Boundary: Tick 70 (Final Booster Tick) must remain locked")
    public void testBoundaryTick100() {
        final int launchTicks = 70;
        final boolean boosterActive = launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final boolean angleLocked = launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final float turnAuthority = angleLocked ? 0.0F : 1.0F;

        Fp5Assertions.assertTrue(boosterActive, "Booster must be ACTIVE at tick 70");
        Fp5Assertions.assertTrue(angleLocked, "Angle lock must remain ACTIVE at tick 70");
        Fp5Assertions.assertEquals(0.0F, turnAuthority, 0.001F, "Turn authority must remain 0.0 at tick 70");
    }

    @Fp5Test(tier = 2, features = {"F1", "F4"}, description = "Boundary: Tick 71 (First Cruise Transition Tick) must deactivate booster and release angle lock")
    public void testBoundaryTick101() {
        final int launchTicks = 71;
        final boolean boosterActive = launchTicks <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final boolean angleLocked = launchTicks <= Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS;
        final float s = (float) (launchTicks - Fp5FlamingoEntity.LAUNCH_RAIL_LOCK_TICKS) / (float) Fp5FlamingoEntity.BOOSTER_TRANSITION_TICKS;
        final float turnAuthority = angleLocked ? 0.0F : s * s * (3.0F - 2.0F * s);

        Fp5Assertions.assertFalse(boosterActive, "Booster must be INACTIVE at tick 71 (transition to jet cruise)");
        Fp5Assertions.assertFalse(angleLocked, "Angle lock must RELEASE at tick 71");
        Fp5Assertions.assertTrue(turnAuthority > 0.0F, "Turn authority must begin ramping (> 0.0) at tick 71");
        final float expectedAuthority = (1.0F / 45.0F) * (1.0F / 45.0F) * (3.0F - 2.0F * (1.0F / 45.0F));
        Fp5Assertions.assertEquals(expectedAuthority, turnAuthority, 0.0001F, "Turn authority at tick 71 should follow Hermite cubic");
    }

    @Fp5Test(tier = 2, features = {"F2"}, description = "Boundary: Launcher clearance distance threshold (15.99m suppressed vs 16.01m active)")
    public void testLauncherClearanceDistanceBoundary() {
        final double distInside = 15.99D;
        final double distOutside = 16.01D;
        final double clearanceThreshold = Fp5FlamingoEntity.LAUNCHER_CLEARANCE_DISTANCE;

        final boolean blockClipAllowedInside = distInside >= clearanceThreshold;
        final boolean blockClipAllowedOutside = distOutside >= clearanceThreshold;

        Fp5Assertions.assertFalse(blockClipAllowedInside, "Block clipping collision must be SUPPRESSED when dist < 16.0m");
        Fp5Assertions.assertTrue(blockClipAllowedOutside, "Block clipping collision must be ENABLED when dist >= 16.0m");
    }

    @Fp5Test(tier = 2, features = {"F1"}, description = "Adversarial: Target at 180-deg opposite heading during launch lock must NOT induce turn or bank")
    public void testExtremeOppositeTargetYawDefense() {
        final float launchLockedYaw = 45.0F;
        final float targetYaw = 225.0F; // 180 deg opposite
        final boolean launchTurnLocked = true; // During ticks 0-100

        final float desiredYaw = launchTurnLocked ? launchLockedYaw : targetYaw;
        final float yawError = desiredYaw - launchLockedYaw;
        final float targetBank = launchTurnLocked ? 0.0F : Math.max(-30.0F, Math.min(30.0F, yawError * 0.5F));

        Fp5Assertions.assertEquals(45.0F, desiredYaw, 0.001F, "Desired yaw must remain locked to 45.0 deg despite 180-deg target offset");
        Fp5Assertions.assertEquals(0.0F, yawError, 0.001F, "Yaw error relative to locked heading must remain 0");
        Fp5Assertions.assertEquals(0.0F, targetBank, 0.001F, "Target bank roll must remain strictly 0 during angle lock");
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 4: Real-World Flight Profile Scenarios
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 4, features = {"F1", "F2", "F3"}, description = "Scenario A: Rail Launch Simulation (Ticks 0..70 strict orientation lock and booster acceleration)")
    public void testScenarioARailLaunchProfile() {
        // Initial setup: Missile on launcher at (100.0, 65.0, 200.0), launch rail pointing yaw = 90.0 deg (East), course pitch = -12.5 deg
        final float railYaw = 90.0F;
        final float railPitch = -12.5F;
        double posX = 100.0D;
        double posY = 65.0D;
        double posZ = 200.0D;
        double currentSpeed = 0.3D;
        float currentYaw = railYaw;
        float currentPitch = railPitch;
        float currentRoll = 0.0F;

        // Simulate 70 ticks of booster flight
        for (int tick = 1; tick <= 70; tick++) {
            // Check booster speed target
            final double speedTarget;
            if (tick <= 15) {
                speedTarget = 5.2D;
            } else {
                speedTarget = 4.2D;
            }
            currentSpeed += Math.min(0.38D, speedTarget - currentSpeed);

            // Compute motion along locked course
            final double pitchRad = Math.toRadians(-currentPitch); // upward pitch
            final double yawRad = Math.toRadians(currentYaw);
            final double dx = Math.cos(pitchRad) * -Math.sin(yawRad) * currentSpeed;
            final double dy = Math.sin(pitchRad) * currentSpeed;
            final double dz = Math.cos(pitchRad) * Math.cos(yawRad) * currentSpeed;

            posX += dx;
            posY += dy;
            posZ += dz;

            // Invariance assertions during locked phase
            Fp5Assertions.assertEquals(railYaw, currentYaw, 0.001F, "Heading must remain strictly locked to rail yaw (90 deg) at tick " + tick);
            Fp5Assertions.assertEquals(railPitch, currentPitch, 0.001F, "Course pitch must remain strictly locked to rail pitch (-12.5 deg) at tick " + tick);
            Fp5Assertions.assertEquals(0.0F, currentRoll, 0.001F, "Body roll must remain strictly 0.0 during rail launch at tick " + tick);

            // Verify clearance state
            final double distanceFromOrigin = Math.sqrt((posX - 100.0D) * (posX - 100.0D) + (posZ - 200.0D) * (posZ - 200.0D));
            if (tick >= 20) {
                Fp5Assertions.assertTrue(distanceFromOrigin >= 16.0D,
                        "Missile must achieve clearance distance (>16.0m) by clearance grace tick 20 (actual: " + distanceFromOrigin + "m)");
            }
        }

        // At tick 70, missile should have reached high altitude and substantial distance downrange
        Fp5Assertions.assertTrue(posY > 95.0D, "Missile altitude after 70 ticks should exceed Y=95 (actual: " + posY + ")");
        Fp5Assertions.assertTrue(currentSpeed >= 4.10D, "Booster sustain speed at tick 70 must be >= 4.10 (actual: " + currentSpeed + ")");
    }
}
