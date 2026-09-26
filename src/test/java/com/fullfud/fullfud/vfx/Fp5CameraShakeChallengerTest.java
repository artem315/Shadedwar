package com.fullfud.fullfud.vfx;

import com.fullfud.fullfud.client.particle.Fp5ClientVfx;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;

/**
 * Challenger Gen-3 M1-2 Empirical Stress Test Suite for FP-5 Flamingo Camera Shake & Buffeting.
 * Adversarially tests:
 * 1. Distance cutoff: D = 32m boundary, D > 32m rejection, quadratic falloff (1 - D/32)^2.
 * 2. Speed cutoff: v = 0.8 threshold, v < 0.8 rejection, linear scaling v / 1.2.
 * 3. Speed cap: v >= 1.8 saturation at min(1.5, v/1.2) = 1.5 across supersonic / hypersonic regimes.
 * 4. Exponential decay: exact 0.85^t decay trajectory across 20 consecutive client ticks.
 * 5. Edge cases: negative speed, D = 0, NaN / Infinity protection, multi-missile peak preservation.
 */
@Fp5TestSuite(name = "FP-5 Camera Shake Challenger Stress Test Suite", features = {"F4"}, milestone = "M1")
public class Fp5CameraShakeChallengerTest {

    @Fp5Test(tier = 1, features = {"F4"}, description = "Empirical challenge: Distance cutoff at 32m boundary, D > 32m rejection sweep, and (1 - D/32)^2 falloff")
    public void testDistanceCutoffAndFalloffOracle() {
        Fp5ClientVfx.clear();

        // 1. D = 32.0m boundary condition: (1 - 32/32)^2 = 0, impulse = 0
        Fp5ClientVfx.triggerCameraShake(32.0D, 1.5D);
        Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.0001F,
            "Impulse at exact boundary D=32.0m must be 0.0");

        // 2. D > 32.0m rejection sweep: [32.0001, 32.05, 32.1, 35.0, 50.0, 100.0, 500.0]
        final double[] rejectedDistances = {32.0001D, 32.05D, 32.1D, 35.0D, 50.0D, 100.0D, 500.0D};
        for (final double dist : rejectedDistances) {
            Fp5ClientVfx.clear();
            Fp5ClientVfx.triggerCameraShake(dist, 2.0D);
            Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.0001F,
                "Distance " + dist + "m > 32m must strictly reject shake trigger");
        }

        // 3. Fine-grained falloff verification across valid distances with speed = 1.2 (speedFactor = 1.0)
        final double[] testDistances = {0.0D, 4.0D, 8.0D, 16.0D, 24.0D, 28.0D, 31.0D, 31.9D};
        for (final double dist : testDistances) {
            Fp5ClientVfx.clear();
            Fp5ClientVfx.triggerCameraShake(dist, 1.2D);
            final float distFactor = (float) (1.0D - (dist / 32.0D));
            final float expectedImpulse = distFactor * distFactor * 1.0F;
            Fp5Assertions.assertEquals(expectedImpulse, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
                "Falloff at distance " + dist + "m must match analytical formula (1 - D/32)^2");
        }
    }

    @Fp5Test(tier = 1, features = {"F4"}, description = "Empirical challenge: Speed cutoff at 0.8 blocks/tick threshold and v < 0.8 rejection sweep")
    public void testSpeedCutoffAndThresholdBoundary() {
        Fp5ClientVfx.clear();

        // 1. Sub-cutoff rejection sweep: [0.0, 0.1, 0.5, 0.7, 0.75, 0.79, 0.7999]
        final double[] subCutoffSpeeds = {0.0D, 0.1D, 0.5D, 0.7D, 0.75D, 0.79D, 0.7999D};
        for (final double spd : subCutoffSpeeds) {
            Fp5ClientVfx.clear();
            Fp5ClientVfx.triggerCameraShake(10.0D, spd);
            Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.0001F,
                "Speed " + spd + " < 0.8 blocks/tick must strictly reject shake trigger");
        }

        // 2. Exact cutoff threshold v = 0.8 blocks/tick at D = 0m (distFactor = 1.0)
        Fp5ClientVfx.clear();
        Fp5ClientVfx.triggerCameraShake(0.0D, 0.8D);
        final float expectedCutoffImpulse = (float) (0.8D / 1.2D); // 2/3 ~ 0.6667F
        Fp5Assertions.assertEquals(expectedCutoffImpulse, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
            "Speed at threshold v=0.8 must trigger with speedFactor = 0.8 / 1.2 (~0.6667)");

        // 3. Linear scaling sweep between 0.8 and 1.8 blocks/tick at D = 0m
        final double[] scalingSpeeds = {0.85D, 1.0D, 1.2D, 1.4D, 1.6D, 1.75D};
        for (final double spd : scalingSpeeds) {
            Fp5ClientVfx.clear();
            Fp5ClientVfx.triggerCameraShake(0.0D, spd);
            final float expected = (float) (spd / 1.2D);
            Fp5Assertions.assertEquals(expected, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
                "Speed " + spd + " must scale linearly as v / 1.2");
        }
    }

    @Fp5Test(tier = 1, features = {"F4"}, description = "Empirical challenge: Speed cap at 1.5 saturation for v >= 1.8 across cruise, sustain, and hypersonic regimes")
    public void testSpeedCapSaturationOracle() {
        Fp5ClientVfx.clear();

        // 1. Boundary at v = 1.8: speedFactor = 1.8 / 1.2 = 1.5 (cap point)
        Fp5ClientVfx.triggerCameraShake(0.0D, 1.8D);
        Fp5Assertions.assertEquals(1.5F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
            "Intensity at v=1.8 must reach exact speed cap of 1.5F");

        // 2. Speeds above 1.8 must be strictly capped at 1.5: [1.8001, 2.0, 2.95 (cruise), 4.2 (sustain), 5.2 (peak booster), 10.0, 50.0]
        final double[] aboveCapSpeeds = {1.8001D, 2.0D, 2.95D, 4.2D, 5.2D, 10.0D, 50.0D};
        for (final double spd : aboveCapSpeeds) {
            Fp5ClientVfx.clear();
            Fp5ClientVfx.triggerCameraShake(0.0D, spd);
            Fp5Assertions.assertEquals(1.5F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
                "Speed " + spd + " >= 1.8 must saturate strictly at 1.5F");
        }
    }

    @Fp5Test(tier = 2, features = {"F4"}, description = "Empirical challenge: 20-tick exponential decay trajectory 0.85^t and cutoff reset")
    public void testTwentyTickExponentialDecayOracle() {
        Fp5ClientVfx.clear();
        // Initialize at max intensity 1.5F (D = 0, v = 2.0)
        final float initialIntensity = 1.5F;
        Fp5ClientVfx.triggerCameraShake(0.0D, 2.0D);
        Fp5Assertions.assertEquals(initialIntensity, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
            "Initial intensity must be 1.5");
        Fp5Assertions.assertEquals(0, Fp5ClientVfx.getCameraShakeTick(), "Initial tick must be 0");

        // Track exact 0.85^t decay across 20 consecutive client ticks
        for (int t = 1; t <= 20; t++) {
            Fp5ClientVfx.updateDecay();
            final float actual = Fp5ClientVfx.getCameraShakeIntensity();
            final float expected = (float) (initialIntensity * Math.pow(0.85D, t));

            Fp5Assertions.assertEquals(t, Fp5ClientVfx.getCameraShakeTick(),
                "Camera shake tick counter must equal " + t + " at tick " + t);
            Fp5Assertions.assertEquals(expected, actual, 0.0005F,
                "Tick " + t + " intensity must match analytical 1.5 * 0.85^" + t + " (expected: " + expected + ", actual: " + actual + ")");
        }

        // At tick 20: 1.5 * 0.85^20 ~ 0.058139F
        final float tick20Expected = (float) (1.5D * Math.pow(0.85D, 20));
        Fp5Assertions.assertEquals(tick20Expected, Fp5ClientVfx.getCameraShakeIntensity(), 0.0005F,
            "Intensity at tick 20 must be approximately 0.05814F");
        Fp5Assertions.assertTrue(Fp5ClientVfx.getCameraShakeIntensity() > 0.001F,
            "Intensity at tick 20 must remain above cutoff threshold (0.001)");

        // Continue decaying to cutoff (< 0.001F)
        int extraTicks = 0;
        while (Fp5ClientVfx.getCameraShakeIntensity() > 0.0F && extraTicks < 100) {
            Fp5ClientVfx.updateDecay();
            extraTicks++;
        }

        Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.00001F,
            "Intensity must cleanly reset to 0.0F after dropping below cutoff 0.001");
        Fp5Assertions.assertEquals(0, Fp5ClientVfx.getCameraShakeTick(),
            "Camera shake tick counter must cleanly reset to 0 after cutoff");
    }

    @Fp5Test(tier = 2, features = {"F4"}, description = "Empirical challenge: Adversarial edge cases (negative speed, D=0, NaN / Infinity protection, multi-missile peak preservation)")
    public void testAdversarialEdgeCasesAndPeakPreservation() {
        Fp5ClientVfx.clear();

        // 1. Negative speeds must be rejected
        Fp5ClientVfx.triggerCameraShake(10.0D, -1.0D);
        Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.0001F,
            "Negative speed must be rejected");
        Fp5ClientVfx.triggerCameraShake(10.0D, -100.0D);
        Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.0001F,
            "Large negative speed must be rejected");

        // 2. D = 0.0m exact zero distance: maximum spatial factor (1 - 0/32)^2 = 1.0
        Fp5ClientVfx.clear();
        Fp5ClientVfx.triggerCameraShake(0.0D, 1.2D);
        Fp5Assertions.assertEquals(1.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
            "Distance D=0 must evaluate to exact spatial factor 1.0");

        // 3. NaN and Infinity robustness
        // Set an established baseline intensity of 0.375F
        Fp5ClientVfx.clear();
        Fp5ClientVfx.triggerCameraShake(16.0D, 1.8D); // (1 - 16/32)^2 * 1.5 = 0.25 * 1.5 = 0.375F
        final float baseline = Fp5ClientVfx.getCameraShakeIntensity();
        Fp5Assertions.assertEquals(0.375F, baseline, 0.001F, "Baseline shake established");

        // Passing NaN distance should not corrupt intensity to NaN
        Fp5ClientVfx.triggerCameraShake(Double.NaN, 1.5D);
        Fp5Assertions.assertFalse(Float.isNaN(Fp5ClientVfx.getCameraShakeIntensity()),
            "Camera shake intensity must not be corrupted to NaN when distance is NaN");
        Fp5Assertions.assertEquals(0.375F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
            "Camera shake intensity must retain valid baseline when distance is NaN");

        // Passing NaN speed should not corrupt intensity to NaN
        Fp5ClientVfx.triggerCameraShake(10.0D, Double.NaN);
        Fp5Assertions.assertFalse(Float.isNaN(Fp5ClientVfx.getCameraShakeIntensity()),
            "Camera shake intensity must not be corrupted to NaN when speed is NaN");
        Fp5Assertions.assertEquals(0.375F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
            "Camera shake intensity must retain valid baseline when speed is NaN");

        // Positive infinity distance must be rejected
        Fp5ClientVfx.triggerCameraShake(Double.POSITIVE_INFINITY, 2.0D);
        Fp5Assertions.assertEquals(0.375F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
            "Infinite distance must not trigger");

        // 4. Multi-missile peak preservation: Weaker impulses must NOT diminish existing stronger shake
        // Baseline is 0.375F. Triggering a weaker impulse at D = 28m, v = 1.2 (impulse = (4/32)^2 * 1.0 = 0.0156)
        Fp5ClientVfx.triggerCameraShake(28.0D, 1.2D);
        Fp5Assertions.assertEquals(0.375F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
            "Weaker flyby must not overwrite or diminish stronger ongoing shake intensity");

        // Triggering a stronger impulse at D = 8m, v = 1.8 (impulse = (24/32)^2 * 1.5 = 0.5625 * 1.5 = 0.84375F)
        Fp5ClientVfx.triggerCameraShake(8.0D, 1.8D);
        Fp5Assertions.assertEquals(0.84375F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F,
            "Stronger flyby must override and elevate shake intensity to new peak");
    }
}