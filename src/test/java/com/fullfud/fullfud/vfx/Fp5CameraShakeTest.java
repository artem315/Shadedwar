package com.fullfud.fullfud.vfx;

import com.fullfud.fullfud.client.particle.Fp5ClientVfx;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;

/**
 * Unit tests for FP-5 Flamingo Close-Proximity Aerodynamic Camera Shake (M1 / Feature F4).
 */
@Fp5TestSuite(name = "FP-5 Camera Shake & Proximity Buffeting Suite", features = {"F4"}, milestone = "M1")
public class Fp5CameraShakeTest {

    @Fp5Test(tier = 1, features = {"F4"}, description = "Verify camera shake trigger conditions and mathematical formula")
    public void testCameraShakeFormulaAndThresholds() {
        Fp5ClientVfx.clear();
        Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.0001F, "Intensity must be 0 after clear");

        // Distance > 32m: rejected
        Fp5ClientVfx.triggerCameraShake(32.5D, 1.5D);
        Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.0001F, "Shake must not trigger beyond 32m");

        // Speed < 0.8: rejected
        Fp5ClientVfx.triggerCameraShake(10.0D, 0.75D);
        Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.0001F, "Shake must not trigger below 0.8 speed");

        // D = 0, speed = 1.2 -> I = (1 - 0)^2 * min(1.5, 1.2/1.2) = 1.0F
        Fp5ClientVfx.triggerCameraShake(0.0D, 1.2D);
        Fp5Assertions.assertEquals(1.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F, "Intensity at D=0, v=1.2 must be 1.0");

        // D = 0, speed = 2.0 -> I = 1.5F (clamped)
        Fp5ClientVfx.clear();
        Fp5ClientVfx.triggerCameraShake(0.0D, 2.0D);
        Fp5Assertions.assertEquals(1.5F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F, "Intensity at D=0, v=2.0 must be 1.5");

        // D = 16, speed = 1.8 -> I = (1 - 16/32)^2 * 1.5 = 0.25 * 1.5 = 0.375F
        Fp5ClientVfx.clear();
        Fp5ClientVfx.triggerCameraShake(16.0D, 1.8D);
        Fp5Assertions.assertEquals(0.375F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F, "Intensity at D=16, v=1.8 must be 0.375");

        // Boundary D = 32.0 -> I = 0.0F
        Fp5ClientVfx.clear();
        Fp5ClientVfx.triggerCameraShake(32.0D, 1.5D);
        Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.0001F, "Intensity at boundary D=32 must be 0.0");
    }

    @Fp5Test(tier = 2, features = {"F4"}, description = "Verify exponential damped decay (0.85^t) and cutoff under 0.001")
    public void testExponentialDampedDecay() {
        Fp5ClientVfx.clear();
        Fp5ClientVfx.triggerCameraShake(0.0D, 2.0D); // Intensity = 1.5F
        Fp5Assertions.assertEquals(1.5F, Fp5ClientVfx.getCameraShakeIntensity(), 0.001F, "Initial intensity must be 1.5");

        float prevIntensity = 1.5F;
        for (int tick = 1; tick <= 20; tick++) {
            Fp5ClientVfx.updateDecay();
            final float currentIntensity = Fp5ClientVfx.getCameraShakeIntensity();
            final float expected = prevIntensity * 0.85F;
            Fp5Assertions.assertEquals(expected, currentIntensity, 0.001F, "Decay at tick " + tick + " must match 0.85x");
            prevIntensity = currentIntensity;
        }

        // After 20 ticks: 1.5 * 0.85^20 ~ 0.0581F
        Fp5Assertions.assertTrue(Fp5ClientVfx.getCameraShakeIntensity() > 0.01F, "Intensity after 20 ticks should be ~0.058");

        // Continue decaying until cutoff (< 0.001)
        for (int tick = 21; tick <= 50; tick++) {
            Fp5ClientVfx.updateDecay();
        }

        Fp5Assertions.assertEquals(0.0F, Fp5ClientVfx.getCameraShakeIntensity(), 0.00001F, "Intensity must clear to 0.0 when below 0.001");
        Fp5Assertions.assertEquals(0, Fp5ClientVfx.getCameraShakeTick(), "Shake tick counter must reset to 0 after cutoff");
    }
}
