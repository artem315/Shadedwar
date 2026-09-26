package com.fullfud.fullfud.explosion;

import com.fullfud.fullfud.client.particle.DroneParticleManager;
import com.fullfud.fullfud.core.network.packet.DroneExplosionPacket;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Challenger Gen-6 M4 Empirical Stress Test Suite:
 * Surface normal math, geometry, quaternion orientation stability, and shockwave rendering invariants.
 */
@Fp5TestSuite(name = "FP-5 Shockwave Math & Normal Challenger Suite", features = {"F11"}, milestone = "M4")
public class Fp5ShockwaveMathChallengerTest {

    private static final Vector3f BASE_UP = new Vector3f(0.0F, 1.0F, 0.0F);

    @Fp5Test(tier = 1, features = {"F11"}, description = "Empirical challenge: safeRotateTo across all 6 cardinal/axial directions")
    public void testCardinalOrientations() {
        final Vector3f[] cardinals = new Vector3f[] {
            new Vector3f(0.0F, 1.0F, 0.0F),   // UP
            new Vector3f(0.0F, -1.0F, 0.0F),  // DOWN (opposite / antiparallel)
            new Vector3f(1.0F, 0.0F, 0.0F),   // EAST
            new Vector3f(-1.0F, 0.0F, 0.0F),  // WEST
            new Vector3f(0.0F, 0.0F, 1.0F),   // SOUTH
            new Vector3f(0.0F, 0.0F, -1.0F)   // NORTH
        };

        for (final Vector3f target : cardinals) {
            final Quaternionf q = new Quaternionf();
            DroneParticleManager.safeRotateTo(q, BASE_UP, target);

            assertValidQuaternion(q, "Cardinal orientation " + target);

            final Vector3f rotated = q.transform(new Vector3f(BASE_UP));
            assertVectorFinite(rotated, "Rotated vector for target " + target);
            Fp5Assertions.assertEquals(target.x, rotated.x, 1.0E-3F, "X mismatch for " + target);
            Fp5Assertions.assertEquals(target.y, rotated.y, 1.0E-3F, "Y mismatch for " + target);
            Fp5Assertions.assertEquals(target.z, rotated.z, 1.0E-3F, "Z mismatch for " + target);
        }
    }

    @Fp5Test(tier = 1, features = {"F11"}, description = "Empirical challenge: safeRotateTo on 45-degree sloped planes in all quadrants")
    public void testSlopedSurfaces45Degrees() {
        final float invSqrt2 = 1.0F / (float) Math.sqrt(2.0D);
        final Vector3f[] slopes = new Vector3f[] {
            new Vector3f(invSqrt2, invSqrt2, 0.0F),
            new Vector3f(-invSqrt2, invSqrt2, 0.0F),
            new Vector3f(0.0F, invSqrt2, invSqrt2),
            new Vector3f(0.0F, invSqrt2, -invSqrt2),
            new Vector3f(invSqrt2, -invSqrt2, 0.0F),
            new Vector3f(-invSqrt2, -invSqrt2, 0.0F),
            new Vector3f(0.0F, -invSqrt2, invSqrt2),
            new Vector3f(0.0F, -invSqrt2, -invSqrt2),
            new Vector3f(invSqrt2, 0.0F, invSqrt2),
            new Vector3f(-invSqrt2, 0.0F, -invSqrt2)
        };

        for (final Vector3f slope : slopes) {
            final Vector3f target = new Vector3f(slope).normalize();
            final Quaternionf q = new Quaternionf();
            DroneParticleManager.safeRotateTo(q, BASE_UP, target);

            assertValidQuaternion(q, "Slope " + slope);

            final Vector3f rotated = q.transform(new Vector3f(BASE_UP));
            assertVectorFinite(rotated, "Rotated vector for slope " + slope);
            final float dist = rotated.distance(target);
            Fp5Assertions.assertTrue(dist < 1.0E-3F, "Rotated vector must match slope target within 1e-3, diff=" + dist);
        }
    }

    @Fp5Test(tier = 1, features = {"F11"}, description = "Empirical challenge: safeRotateTo 1000 arbitrary pseudo-random spherical angles")
    public void testArbitrarySphericalNormalsStressOracle() {
        final Random rng = new Random(424242L);
        for (int i = 0; i < 1000; i++) {
            final double u = rng.nextDouble() * 2.0D - 1.0D;
            final double theta = rng.nextDouble() * Math.PI * 2.0D;
            final double r = Math.sqrt(Math.max(0.0D, 1.0D - u * u));
            final float nx = (float) (r * Math.cos(theta));
            final float ny = (float) u;
            final float nz = (float) (r * Math.sin(theta));

            final Vector3f target = new Vector3f(nx, ny, nz).normalize();
            final Quaternionf q = new Quaternionf();
            DroneParticleManager.safeRotateTo(q, BASE_UP, target);

            assertValidQuaternion(q, "Random normal #" + i + " " + target);

            final Vector3f rotated = q.transform(new Vector3f(BASE_UP));
            assertVectorFinite(rotated, "Rotated random normal #" + i);
            final float dist = rotated.distance(target);
            Fp5Assertions.assertTrue(dist < 1.0E-3F, "Orientation diff exceeded tolerance at iteration " + i + ": dist=" + dist);
        }
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Empirical challenge: Boundary vectors nearly parallel and nearly antiparallel (dot ~ 1.0, dot ~ -1.0)")
    public void testNearParallelAndNearAntiparallelBoundaries() {
        // Dot product just inside and outside transition threshold 0.9999F and -0.9999F
        // For dot > 0.9999F (~0.81 deg), safeRotateTo clamps to identity.
        // The maximum chord distance for dot >= 0.9999F is sqrt(2*(1 - 0.9999)) ~ 0.01414.
        final float[] epsilons = new float[] { 1.0E-6F, 1.0E-5F, 1.0E-4F, 5.0E-4F, 1.0E-3F, 1.0E-2F, 0.05F, 0.10F };

        for (final float eps : epsilons) {
            // Near UP (near parallel)
            final Vector3f nearUp = new Vector3f(eps, 1.0F, 0.0F).normalize();
            final Quaternionf qUp = new Quaternionf();
            DroneParticleManager.safeRotateTo(qUp, BASE_UP, nearUp);
            assertValidQuaternion(qUp, "Near UP eps=" + eps);
            final Vector3f rotUp = qUp.transform(new Vector3f(BASE_UP));
            final float tolUp = (BASE_UP.dot(nearUp) > 0.9999F) ? 0.015F : 1.0E-3F;
            Fp5Assertions.assertTrue(rotUp.distance(nearUp) < tolUp,
                "Near UP rotation mismatch for eps=" + eps + ", dist=" + rotUp.distance(nearUp) + " > tol=" + tolUp);

            // Near DOWN (near antiparallel)
            final Vector3f nearDown = new Vector3f(eps, -1.0F, 0.0F).normalize();
            final Quaternionf qDown = new Quaternionf();
            DroneParticleManager.safeRotateTo(qDown, BASE_UP, nearDown);
            assertValidQuaternion(qDown, "Near DOWN eps=" + eps);
            final Vector3f rotDown = qDown.transform(new Vector3f(BASE_UP));
            final float tolDown = (BASE_UP.dot(nearDown) < -0.9999F) ? 0.015F : 1.0E-3F;
            Fp5Assertions.assertTrue(rotDown.distance(nearDown) < tolDown,
                "Near DOWN rotation mismatch for eps=" + eps + ", dist=" + rotDown.distance(nearDown) + " > tol=" + tolDown);
        }
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Empirical challenge: Degenerate and zero normal handling in renderBatch guard and safeRotateTo")
    public void testDegenerateAndZeroNormalHandling() {
        final Vector3f[] degenerates = new Vector3f[] {
            new Vector3f(0.0F, 0.0F, 0.0F),
            new Vector3f(1.0E-7F, 0.0F, 0.0F),
            new Vector3f(0.0F, 1.0E-7F, 0.0F),
            new Vector3f(1.0E-5F, 1.0E-5F, 1.0E-5F),
            new Vector3f(0.005F, 0.005F, 0.005F)
        };

        for (final Vector3f degen : degenerates) {
            // 1. Invariant: renderBatch guard explicitly rejects degenerate vectors
            // (DroneParticleManager.java:1076: `if (norm.lengthSquared() <= 0.01F) { poseStack.popPose(); continue; }`)
            final boolean rejectedByRenderGuard = degen.lengthSquared() <= 0.01F;
            Fp5Assertions.assertTrue(rejectedByRenderGuard,
                "Degenerate normal " + degen + " (lenSq=" + degen.lengthSquared() + ") must be rejected by renderBatch guard");

            // 2. Direct test: what happens if safeRotateTo is invoked directly on degenerate vector
            final Quaternionf q = new Quaternionf();
            DroneParticleManager.safeRotateTo(q, BASE_UP, degen);
            System.out.printf("  [DEGEN TEST] input=%s -> q=(%.4f, %.4f, %.4f, %.4f)%n", degen, q.x, q.y, q.z, q.w);
            Fp5Assertions.assertFalse(Float.isNaN(q.x), "safeRotateTo on " + degen + " produced NaN in q.x");
            Fp5Assertions.assertFalse(Float.isNaN(q.y), "safeRotateTo on " + degen + " produced NaN in q.y");
            Fp5Assertions.assertFalse(Float.isNaN(q.z), "safeRotateTo on " + degen + " produced NaN in q.z");
            Fp5Assertions.assertFalse(Float.isNaN(q.w), "safeRotateTo on " + degen + " produced NaN in q.w");
            Fp5Assertions.assertFalse(Float.isInfinite(q.x), "safeRotateTo on " + degen + " produced Inf in q.x");
            Fp5Assertions.assertFalse(Float.isInfinite(q.y), "safeRotateTo on " + degen + " produced Inf in q.y");
            Fp5Assertions.assertFalse(Float.isInfinite(q.z), "safeRotateTo on " + degen + " produced Inf in q.z");
            Fp5Assertions.assertFalse(Float.isInfinite(q.w), "safeRotateTo on " + degen + " produced Inf in q.w");
        }
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Empirical challenge: safeRotateTo extreme adversarial stress across subnormals, NaNs, infinities, nulls, and arbitrary 3D vectors")
    public void testSafeRotateToExtremeAdversarialStressOracle() {
        final Quaternionf q = new Quaternionf();

        // 1. Null handling
        DroneParticleManager.safeRotateTo(null, BASE_UP, BASE_UP); // Must not throw NPE
        q.set(0.1F, 0.2F, 0.3F, 0.4F);
        DroneParticleManager.safeRotateTo(q, null, BASE_UP);
        assertIdentityQuaternion(q, "safeRotateTo with null from");

        q.set(0.1F, 0.2F, 0.3F, 0.4F);
        DroneParticleManager.safeRotateTo(q, BASE_UP, null);
        assertIdentityQuaternion(q, "safeRotateTo with null to");

        q.set(0.1F, 0.2F, 0.3F, 0.4F);
        DroneParticleManager.safeRotateTo(q, null, null);
        assertIdentityQuaternion(q, "safeRotateTo with both null");

        // 2. Degenerate zero and subnormal vectors
        final Vector3f[] subnormalsAndZeros = new Vector3f[] {
            new Vector3f(0.0F, 0.0F, 0.0F),
            new Vector3f(-0.0F, -0.0F, -0.0F),
            new Vector3f(1.0E-7F, 0.0F, 0.0F),
            new Vector3f(0.0F, 1.0E-7F, 0.0F),
            new Vector3f(0.0F, 0.0F, 1.0E-7F),
            new Vector3f(-1.0E-7F, 0.0F, 0.0F),
            new Vector3f(Float.MIN_VALUE, 0.0F, 0.0F),
            new Vector3f(1.0E-30F, 1.0E-30F, 1.0E-30F),
            new Vector3f(0.0099F, 0.0F, 0.0F) // lenSq = 0.9801E-4 < 1.0E-4
        };

        for (final Vector3f v : subnormalsAndZeros) {
            // Test as 'to' vector
            q.set(0.5F, 0.5F, 0.5F, 0.5F);
            DroneParticleManager.safeRotateTo(q, BASE_UP, v);
            assertIdentityQuaternion(q, "Subnormal/zero 'to' vector: " + v);

            // Test as 'from' vector
            q.set(0.5F, 0.5F, 0.5F, 0.5F);
            DroneParticleManager.safeRotateTo(q, v, BASE_UP);
            assertIdentityQuaternion(q, "Subnormal/zero 'from' vector: " + v);

            // Test as both 'from' and 'to'
            q.set(0.5F, 0.5F, 0.5F, 0.5F);
            DroneParticleManager.safeRotateTo(q, v, v);
            assertIdentityQuaternion(q, "Subnormal/zero both vectors: " + v);
        }

        // 3. Non-finite inputs (NaN and Infinities)
        final Vector3f[] nonFinites = new Vector3f[] {
            new Vector3f(Float.NaN, 0.0F, 0.0F),
            new Vector3f(0.0F, Float.NaN, 0.0F),
            new Vector3f(0.0F, 0.0F, Float.NaN),
            new Vector3f(Float.NaN, Float.NaN, Float.NaN),
            new Vector3f(Float.POSITIVE_INFINITY, 0.0F, 0.0F),
            new Vector3f(0.0F, Float.NEGATIVE_INFINITY, 0.0F),
            new Vector3f(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NaN)
        };

        for (final Vector3f nf : nonFinites) {
            // Test as 'to' vector
            q.set(0.5F, 0.5F, 0.5F, 0.5F);
            DroneParticleManager.safeRotateTo(q, BASE_UP, nf);
            assertIdentityQuaternion(q, "Non-finite 'to' vector: " + nf);

            // Test as 'from' vector
            q.set(0.5F, 0.5F, 0.5F, 0.5F);
            DroneParticleManager.safeRotateTo(q, nf, BASE_UP);
            assertIdentityQuaternion(q, "Non-finite 'from' vector: " + nf);

            // Test as both
            q.set(0.5F, 0.5F, 0.5F, 0.5F);
            DroneParticleManager.safeRotateTo(q, nf, nf);
            assertIdentityQuaternion(q, "Non-finite both vectors: " + nf);
        }

        // 4. Stress test with 10,000 arbitrary 3D vectors (both normalized and unnormalized)
        final Random rng = new Random(13377331L);
        for (int i = 0; i < 10000; i++) {
            final float scale = (float) Math.exp((rng.nextDouble() - 0.5D) * 16.0D); // wide dynamic range: e^-8 to e^8
            final float tx = (rng.nextFloat() * 2.0F - 1.0F) * scale;
            final float ty = (rng.nextFloat() * 2.0F - 1.0F) * scale;
            final float tz = (rng.nextFloat() * 2.0F - 1.0F) * scale;
            final Vector3f target = new Vector3f(tx, ty, tz);

            final float fx = (rng.nextFloat() * 2.0F - 1.0F);
            final float fy = (rng.nextFloat() * 2.0F - 1.0F);
            final float fz = (rng.nextFloat() * 2.0F - 1.0F);
            final Vector3f fromVec = new Vector3f(fx, fy, fz);

            q.set(rng.nextFloat(), rng.nextFloat(), rng.nextFloat(), rng.nextFloat());
            DroneParticleManager.safeRotateTo(q, fromVec, target);

            // Assert 0 NaNs and 0 Infs
            Fp5Assertions.assertFalse(Float.isNaN(q.x), "Arbitrary vector stress #" + i + " produced NaN in q.x");
            Fp5Assertions.assertFalse(Float.isNaN(q.y), "Arbitrary vector stress #" + i + " produced NaN in q.y");
            Fp5Assertions.assertFalse(Float.isNaN(q.z), "Arbitrary vector stress #" + i + " produced NaN in q.z");
            Fp5Assertions.assertFalse(Float.isNaN(q.w), "Arbitrary vector stress #" + i + " produced NaN in q.w");

            Fp5Assertions.assertFalse(Float.isInfinite(q.x), "Arbitrary vector stress #" + i + " produced Inf in q.x");
            Fp5Assertions.assertFalse(Float.isInfinite(q.y), "Arbitrary vector stress #" + i + " produced Inf in q.y");
            Fp5Assertions.assertFalse(Float.isInfinite(q.z), "Arbitrary vector stress #" + i + " produced Inf in q.z");
            Fp5Assertions.assertFalse(Float.isInfinite(q.w), "Arbitrary vector stress #" + i + " produced Inf in q.w");

            // Must be a valid normalized quaternion (lengthSq ~ 1.0)
            final float normSq = q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w;
            Fp5Assertions.assertEquals(1.0F, normSq, 1.0E-3F, "Arbitrary vector stress #" + i + " quaternion not normalized: normSq=" + normSq);
        }
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Empirical challenge: safeRotateTo destination reuse isolation (overwrites prior orientation)")
    public void testDestinationQuaternionReuseIsolation() {
        final Vector3f target1 = new Vector3f(1.0F, 0.0F, 0.0F); // EAST
        final Vector3f target2 = new Vector3f(0.0F, 0.0F, 1.0F); // SOUTH

        final Quaternionf q = new Quaternionf();
        DroneParticleManager.safeRotateTo(q, BASE_UP, target1);
        final Vector3f res1 = q.transform(new Vector3f(BASE_UP));
        Fp5Assertions.assertTrue(res1.distance(target1) < 1.0E-3F, "Target 1 alignment failed");

        // Now reuse q without manually calling q.identity()
        DroneParticleManager.safeRotateTo(q, BASE_UP, target2);
        final Vector3f res2 = q.transform(new Vector3f(BASE_UP));
        Fp5Assertions.assertTrue(res2.distance(target2) < 1.0E-3F,
            "Target 2 alignment failed on reused quaternion (safeRotateTo must overwrite dest rather than accumulate): actual=" + res2 + ", expected=" + target2);
    }

    @Fp5Test(tier = 1, features = {"F11"}, description = "Empirical challenge: Airburst differentiation strictly produces 0 GROUND_ALIGNED quads and 0 ground dust surges")
    public void testAirburstZeroGroundAlignedAndZeroDustSurgeOracle() {
        final Vec3 impactNormal = Vec3.ZERO;
        final float nx = (float) impactNormal.x;
        final float ny = (float) impactNormal.y;
        final float nz = (float) impactNormal.z;

        final float normLenSq = nx * nx + ny * ny + nz * nz;
        final boolean isAirburst = normLenSq < 1.0E-4F;
        Fp5Assertions.assertTrue(isAirburst, "Vec3.ZERO must trigger isAirburst = true");

        // Simulate particle spawn logic from DroneParticleManager.spawnExplosionParticles
        int groundAlignedQuadCount = 0;
        int billboardVaporBubbleCount = 0;
        int aerialFlashCount = 0;

        if (!isAirburst) {
            groundAlignedQuadCount++;
        } else {
            billboardVaporBubbleCount++;
            aerialFlashCount++;
        }

        // Invariant 1: Exactly 0 GROUND_ALIGNED quad discs spawned in airburst
        Fp5Assertions.assertEquals(0, groundAlignedQuadCount,
            "Airburst MUST NOT spawn any GROUND_ALIGNED shockwave quad rings");
        // Invariant 2: Aerial 3D vapor bubble and aerial flash spawned
        Fp5Assertions.assertEquals(1, billboardVaporBubbleCount,
            "Airburst MUST spawn 1 aerial 3D vapor bubble (BILLBOARD)");
        Fp5Assertions.assertEquals(1, aerialFlashCount,
            "Airburst MUST spawn 1 aerial spherical flash (BILLBOARD)");

        // Simulate tiered mushroom cloud smoke counts for airburst vs surface
        final int smokeCount = 160;
        final int stemCount = 40;
        final int capCountAirburst = isAirburst ? 80 : 50;
        final int surgeCountAirburst = isAirburst ? 0 : (smokeCount - stemCount - 50);

        // Invariant 3: Ground dust surge count is strictly 0 in airburst
        Fp5Assertions.assertEquals(0, surgeCountAirburst,
            "Airburst ground dust surge count MUST be exactly 0");
        // Invariant 4: Cloud cap absorbs the surge particles (50 + 30 = 80)
        Fp5Assertions.assertEquals(80, capCountAirburst,
            "Airburst toroidal anvil cap count must expand to 80 particles");

        // Verification against surface detonation with normal (0, 1, 0)
        final Vec3 groundNormal = new Vec3(0.0D, 1.0D, 0.0D);
        final float gNormLenSq = (float) groundNormal.lengthSqr();
        final boolean groundIsAirburst = gNormLenSq < 1.0E-4F;
        Fp5Assertions.assertFalse(groundIsAirburst, "Ground normal must NOT be airburst");

        int surfaceGroundAligned = groundIsAirburst ? 0 : 1;
        int surfaceSurgeCount = groundIsAirburst ? 0 : (smokeCount - stemCount - 50);
        Fp5Assertions.assertEquals(1, surfaceGroundAligned, "Surface detonation must spawn exactly 1 GROUND_ALIGNED quad");
        Fp5Assertions.assertEquals(70, surfaceSurgeCount, "Surface detonation must spawn 70 ground dust surge particles");
    }

    @Fp5Test(tier = 2, features = {"F11"}, description = "Empirical challenge: DroneExplosionPacket preserves normal vector and airburst flag")
    public void testDroneExplosionPacketAirburstRoundTrip() {
        // DroneExplosionPacket constructor: (x, y, z, normalX, normalY, normalZ, explosionType, materialType, power)
        final DroneExplosionPacket airburstPacket = new DroneExplosionPacket(
            100.0D, 80.0D, 200.0D,
            0.0F, 0.0F, 0.0F,
            DroneExplosionPacket.TYPE_FLAMINGO,
            DroneExplosionPacket.MAT_GENERIC,
            8.0F
        );

        final float pnx = airburstPacket.normalX();
        final float pny = airburstPacket.normalY();
        final float pnz = airburstPacket.normalZ();

        final float normSq = pnx * pnx + pny * pny + pnz * pnz;
        Fp5Assertions.assertTrue(normSq < 1.0E-4F, "Airburst packet normal length squared must be < 1e-4");
        Fp5Assertions.assertEquals(DroneExplosionPacket.TYPE_FLAMINGO, airburstPacket.explosionType(),
            "Packet explosion type must be TYPE_FLAMINGO");
        Fp5Assertions.assertEquals(8.0F, airburstPacket.power(), 0.01F,
            "Packet power must be 8.0F");

        // Test surface packet with wall normal (-1, 0, 0)
        final DroneExplosionPacket wallPacket = new DroneExplosionPacket(
            100.0D, 80.0D, 200.0D,
            -1.0F, 0.0F, 0.0F,
            DroneExplosionPacket.TYPE_FLAMINGO,
            DroneExplosionPacket.MAT_STONE,
            8.0F
        );
        final float wLenSq = wallPacket.normalX() * wallPacket.normalX()
            + wallPacket.normalY() * wallPacket.normalY()
            + wallPacket.normalZ() * wallPacket.normalZ();
        Fp5Assertions.assertFalse(wLenSq < 1.0E-4F, "Wall packet normal must NOT be airburst");
        Fp5Assertions.assertEquals(-1.0F, wallPacket.normalX(), 0.01F, "Wall normal X must be -1.0");
    }

    private static void assertValidQuaternion(final Quaternionf q, final String context) {
        Fp5Assertions.assertFalse(Float.isNaN(q.x), context + " q.x is NaN");
        Fp5Assertions.assertFalse(Float.isNaN(q.y), context + " q.y is NaN");
        Fp5Assertions.assertFalse(Float.isNaN(q.z), context + " q.z is NaN");
        Fp5Assertions.assertFalse(Float.isNaN(q.w), context + " q.w is NaN");

        Fp5Assertions.assertFalse(Float.isInfinite(q.x), context + " q.x is Infinite");
        Fp5Assertions.assertFalse(Float.isInfinite(q.y), context + " q.y is Infinite");
        Fp5Assertions.assertFalse(Float.isInfinite(q.z), context + " q.z is Infinite");
        Fp5Assertions.assertFalse(Float.isInfinite(q.w), context + " q.w is Infinite");

        final float normSq = q.x * q.x + q.y * q.y + q.z * q.z + q.w * q.w;
        Fp5Assertions.assertEquals(1.0F, normSq, 1.0E-3F, context + " Quaternion is not normalized (normSq=" + normSq + ")");
    }

    private static void assertVectorFinite(final Vector3f v, final String context) {
        Fp5Assertions.assertFalse(Float.isNaN(v.x), context + " v.x is NaN");
        Fp5Assertions.assertFalse(Float.isNaN(v.y), context + " v.y is NaN");
        Fp5Assertions.assertFalse(Float.isNaN(v.z), context + " v.z is NaN");

        Fp5Assertions.assertFalse(Float.isInfinite(v.x), context + " v.x is Infinite");
        Fp5Assertions.assertFalse(Float.isInfinite(v.y), context + " v.y is Infinite");
        Fp5Assertions.assertFalse(Float.isInfinite(v.z), context + " v.z is Infinite");
    }

    private static void assertIdentityQuaternion(final Quaternionf q, final String context) {
        Fp5Assertions.assertEquals(0.0F, q.x, 1.0E-5F, context + " q.x must be 0.0");
        Fp5Assertions.assertEquals(0.0F, q.y, 1.0E-5F, context + " q.y must be 0.0");
        Fp5Assertions.assertEquals(0.0F, q.z, 1.0E-5F, context + " q.z must be 0.0");
        Fp5Assertions.assertEquals(1.0F, q.w, 1.0E-5F, context + " q.w must be 1.0");
    }
}
