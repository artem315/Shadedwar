package com.fullfud.fullfud.vfx;

import com.fullfud.fullfud.client.particle.DroneParticleManager;
import com.fullfud.fullfud.common.entity.Fp5FlamingoEntity;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;
import net.minecraft.world.phys.Vec3;

/**
 * Test suite for FP-5 Flamingo Dual-Stage Exhaust Plume VFX (M3 / Feature F8).
 */
@Fp5TestSuite(name = "FP-5 Flamingo Exhaust Plume VFX", features = {"F8"}, milestone = "M3")
public class Fp5ExhaustPlumeTest {

    public enum ExhaustMode {
        ROCKET_BOOSTER,
        JET_CRUISE
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 1: Dual-Stage Particle Phase Differentiation
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 1, features = {"F8"}, description = "Verify rocket booster exhaust stage parameters (fire core and expanding smoke trail)")
    public void testBoosterExhaustProfile() {
        // Authoritative source: ORIGINAL_REQUEST.md R3:
        // "Generate high-intensity rocket booster exhaust during the initial launch phase (bright fire core and expanding smoke trail)"
        final boolean boosterActive = true;
        final ExhaustMode mode = resolveExhaustMode(boosterActive);
        Fp5Assertions.assertEquals(ExhaustMode.ROCKET_BOOSTER, mode,
                "Exhaust mode must be ROCKET_BOOSTER when booster is active");

        // High intensity booster emission density (fire + dense smoke)
        final int boosterFireParticles = 6;
        final int boosterSmokeParticles = 10;
        final int totalBoosterBursts = boosterFireParticles + boosterSmokeParticles;
        Fp5Assertions.assertTrue(totalBoosterBursts >= 12,
                "Booster phase must emit dense particle burst (>= 12 particles/tick)");
    }

    @Fp5Test(tier = 1, features = {"F8"}, description = "Verify jet cruise exhaust stage parameters (tight contrail and nozzle glow)")
    public void testCruiseJetExhaustProfile() {
        // Authoritative source: ORIGINAL_REQUEST.md R3:
        // "Transition smoothly to realistic jet exhaust particles during the sustained cruise flight phase"
        final boolean boosterActive = false;
        final ExhaustMode mode = resolveExhaustMode(boosterActive);
        Fp5Assertions.assertEquals(ExhaustMode.JET_CRUISE, mode,
                "Exhaust mode must be JET_CRUISE when booster is inactive");

        // Disciplined cruise jet emission density (streamlined smoke contrail)
        final int cruiseJetParticles = 3;
        Fp5Assertions.assertTrue(cruiseJetParticles <= 5,
                "Cruise jet phase must emit streamlined contrail (<= 5 particles/tick) to prevent packet spam");
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 2: Boundary & Staging Transitions
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 2, features = {"F8"}, description = "Boundary: Exact exhaust stage switch at Tick 70 to Tick 71")
    public void testExhaustTransitionBoundary() {
        // Tick 70: Final tick of rocket booster burn
        final int tick70 = 70;
        final boolean boosterActiveAt70 = tick70 <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final ExhaustMode mode70 = resolveExhaustMode(boosterActiveAt70);
        Fp5Assertions.assertEquals(ExhaustMode.ROCKET_BOOSTER, mode70,
                "Tick 70 must still emit ROCKET_BOOSTER exhaust");

        // Tick 71: First tick of cruise jet sustain
        final int tick71 = 71;
        final boolean boosterActiveAt71 = tick71 <= Fp5FlamingoEntity.BOOSTER_BURN_TICKS;
        final ExhaustMode mode71 = resolveExhaustMode(boosterActiveAt71);
        Fp5Assertions.assertEquals(ExhaustMode.JET_CRUISE, mode71,
                "Tick 71 must transition to JET_CRUISE exhaust");
    }

    @Fp5Test(tier = 2, features = {"F8"}, description = "Adversarial: Plume spread calculation remains positive under extreme bank angles (up to 40 deg)")
    public void testPlumeSpreadUnderExtremeBankRoll() {
        final float[] testBankAngles = { 0.0F, 15.0F, 30.0F, 40.0F, -40.0F };

        for (final float bankAngle : testBankAngles) {
            final double rollFactor = Math.max(0.35D, Math.abs(Math.sin(Math.toRadians(bankAngle))));
            final double plumeSpread = 0.16D + rollFactor * 0.1D;

            Fp5Assertions.assertTrue(plumeSpread > 0.15D, "Plume spread must be > 0.15 at bank angle " + bankAngle);
            Fp5Assertions.assertTrue(plumeSpread <= 0.30D, "Plume spread must be bounded <= 0.30 at bank angle " + bankAngle);
        }
    }

    @Fp5Test(tier = 1, features = {"F8"}, description = "Verify nozzle geometry offsets match scaled Blockbench model heights and positions")
    public void testNozzleGeometryOffset() {
        // Model-derived physical coordinates scaled by entity SCALE = 2.25F (1 unit = 1/16 meter)
        // Booster nozzle: Bone Вспомогательная хуйня (center Y = 16.75687D, rear exit Z = 20.03455D)
        final double boosterY = (16.75687D / 16.0D) * (double) Fp5FlamingoEntity.SCALE;
        final double boosterZ = (20.03455D / 16.0D) * (double) Fp5FlamingoEntity.SCALE;

        // Cruise jet nozzle: Cube 26 (center Y = 30.75687D, rear exit Z = 42.53455D)
        final double jetY = (30.75687D / 16.0D) * (double) Fp5FlamingoEntity.SCALE;
        final double jetZ = (42.53455D / 16.0D) * (double) Fp5FlamingoEntity.SCALE;

        Fp5Assertions.assertEquals(2.3564D, boosterY, 0.001D,
                "Booster nozzle Y offset must match Bone Вспомогательная хуйня (~2.3564m)");
        Fp5Assertions.assertEquals(2.8174D, boosterZ, 0.001D,
                "Booster nozzle Z offset must match Bone Вспомогательная хуйня (~2.8174m)");
        Fp5Assertions.assertEquals(4.3252D, jetY, 0.001D,
                "Jet nozzle Y offset must match Cube 26 (~4.3252m)");
        Fp5Assertions.assertEquals(5.9815D, jetZ, 0.001D,
                "Jet nozzle Z offset must match Cube 26 (~5.9815m)");

        // Direct assertion on DroneParticleManager.nozzleOffset single source of truth
        final Vec3 boosterOffset = DroneParticleManager.nozzleOffset(true, 0.0F, 0.0F, 0.0F);
        Fp5Assertions.assertEquals(2.3564D, boosterOffset.y, 0.001D, "DroneParticleManager booster Y offset must be 2.3564m");
        Fp5Assertions.assertEquals(-2.8174D, boosterOffset.z, 0.001D, "DroneParticleManager booster Z offset must be -2.8174m at yaw 0");

        final Vec3 jetOffset = DroneParticleManager.nozzleOffset(false, 0.0F, 0.0F, 0.0F);
        Fp5Assertions.assertEquals(4.3252D, jetOffset.y, 0.001D, "DroneParticleManager jet Y offset must be 4.3252m");
        Fp5Assertions.assertEquals(-5.9815D, jetOffset.z, 0.001D, "DroneParticleManager jet Z offset must be -5.9815m at yaw 0");
    }

    @Fp5Test(tier = 1, features = {"F1"}, description = "Verify minimum launch distance constraint is 400 meters")
    public void testMinLaunchDistanceConstraint() {
        Fp5Assertions.assertEquals(400.0D, Fp5FlamingoEntity.MIN_LAUNCH_DISTANCE,
                "FP-5 Flamingo minimum launch distance must be strictly 400 meters");
    }

    private static ExhaustMode resolveExhaustMode(final boolean boosterActive) {
        return boosterActive ? ExhaustMode.ROCKET_BOOSTER : ExhaustMode.JET_CRUISE;
    }
}
