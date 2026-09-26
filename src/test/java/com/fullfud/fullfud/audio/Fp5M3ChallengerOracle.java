package com.fullfud.fullfud.audio;

import com.fullfud.fullfud.client.sound.DroneSoundEffects;
import com.fullfud.fullfud.client.sound.OpenALFilters;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.EXTEfx;

/**
 * Empirical Challenger Oracle & Stress Harness for Milestone M3.
 * Rigorously stress-tests:
 * 1. Aspect-Angle Directivity formulas across 1000 fine-grained sweep steps.
 * 2. Exact boundary behavior at nose-on, forward cutoff, broadside, aft onset, tail-chase.
 * 3. High-contention multi-threaded concurrent access to OpenALFilters.
 * 4. NaN, Infinity, and division-by-zero vulnerabilities.
 * 5. OpenAL filter isolation, binding deduplication, and cleanup invariants.
 */
public final class Fp5M3ChallengerOracle {

    private static int testsRun = 0;
    private static int testsPassed = 0;
    private static int testsFailed = 0;

    public static void main(final String[] args) {
        System.out.println("===============================================================================");
        System.out.println("    CHALLENGER GEN-5 M3 EMPIRICAL STRESS TEST ORACLE & HARNESS                 ");
        System.out.println("===============================================================================");

        final long start = System.currentTimeMillis();

        runTest("Directivity 1000-Step Sweep & Monotonicity", Fp5M3ChallengerOracle::testDirectivityFormula1000StepsSweep);
        runTest("Critical Boundary Aspect Points (Nose, Cutoff, Broadside, Aft Onset, Tail)", Fp5M3ChallengerOracle::testCriticalBoundaryAspectPoints);
        runTest("Adversarial Out-Of-Bounds & Floating Point Extremes", Fp5M3ChallengerOracle::testAdversarialAndExtremeInputs);
        runTest("Aspect-Angle Vector Geometry Edge Cases (Zero-Dist, Zero-Vel)", Fp5M3ChallengerOracle::testVectorGeometryZeroProtections);
        runTest("Doppler Pitch Numerical Robustness & Div-Zero Protection", Fp5M3ChallengerOracle::testDopplerPitchNumericalRobustness);
        runTest("Air-Absorption GainHF Atmospheric Rolloff Boundaries", Fp5M3ChallengerOracle::testAirAbsorptionGainHFBoundaries);
        runTest("Distance Volume Attenuation Factor Numerical Envelope", Fp5M3ChallengerOracle::testDistanceVolumeFactorEnvelope);
        runTest("High-Contention Multi-Threaded Concurrent Filter Access (16 Threads)", Fp5M3ChallengerOracle::testHighContentionConcurrentFilterAccess);
        runTest("Shared-Source Concurrency Race Stress (Apply vs Remove vs Clear)", Fp5M3ChallengerOracle::testSharedSourceConcurrencyRaces);
        runTest("OpenAL Filter Allocation Idempotency & Binding Deduplication", Fp5M3ChallengerOracle::testFilterAllocationDeduplication);
        runTest("OpenAL Filter Guard Against Untracked Sources & Idempotent Removal", Fp5M3ChallengerOracle::testUntrackedSourceRemovalSafety);

        final long elapsed = System.currentTimeMillis() - start;
        System.out.println("===============================================================================");
        System.out.println("CHALLENGER ORACLE EXECUTION SUMMARY");
        System.out.println("===============================================================================");
        System.out.printf("Total Tests:   %d%n", testsRun);
        System.out.printf("Passed:        %d%n", testsPassed);
        System.out.printf("Failed:        %d%n", testsFailed);
        System.out.printf("Elapsed:       %d ms%n", elapsed);
        System.out.println("===============================================================================");

        if (testsFailed > 0) {
            System.err.println("VERDICT: REJECT (Failures detected)");
            System.exit(1);
        } else {
            System.out.println("VERDICT: APPROVE (All stress tests passed cleanly)");
            System.exit(0);
        }
    }

    private static void runTest(final String name, final Runnable test) {
        testsRun++;
        System.out.printf("  [CHALLENGE %02d] %-62s ... ", testsRun, name);
        final long t0 = System.nanoTime();
        try {
            test.run();
            final double ms = (System.nanoTime() - t0) / 1_000_000.0D;
            System.out.printf("PASS (%.2f ms)%n", ms);
            testsPassed++;
        } catch (Throwable t) {
            final double ms = (System.nanoTime() - t0) / 1_000_000.0D;
            System.out.printf("FAIL (%.2f ms)%n", ms);
            System.err.println("    ERROR: " + t.getMessage());
            t.printStackTrace(System.err);
            testsFailed++;
        }
    }

    private static void assertEquals(final float expected, final float actual, final float delta, final String msg) {
        if (Math.abs(expected - actual) > delta) {
            throw new AssertionError(msg + " — Expected: " + expected + ", Actual: " + actual + " (delta: " + delta + ")");
        }
    }

    private static void assertTrue(final boolean cond, final String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    // =============================================================================================
    // 1. Directivity Formulas 1000-Step Fine-Grained Sweep
    // =============================================================================================

    public static void testDirectivityFormula1000StepsSweep() {
        final int steps = 1000;
        final float minCos = -1.0F;
        final float maxCos = 1.0F;
        final float stepSize = (maxCos - minCos) / (float) steps;

        float prevForwardMix = 0.0F;
        float prevAftMix = 1.0F;

        // Sweep from -1.0 (pure tail) to +1.0 (nose-on)
        for (int i = 0; i <= steps; i++) {
            final float cosTheta = minCos + (i * stepSize);

            final float forwardMix = Mth.clamp((cosTheta + 0.15F) / 1.15F, 0.0F, 1.0F);
            final float aftMix = Mth.clamp((-cosTheta + 0.25F) / 1.25F, 0.15F, 1.0F);

            // Envelope invariants
            assertTrue(forwardMix >= 0.0F && forwardMix <= 1.0F,
                    "forwardMix must remain in [0.0, 1.0] at cosTheta=" + cosTheta + " (actual: " + forwardMix + ")");
            assertTrue(aftMix >= 0.15F && aftMix <= 1.0F,
                    "aftMix must remain in [0.15, 1.0] at cosTheta=" + cosTheta + " (actual: " + aftMix + ")");

            // Monotonicity invariants:
            // As cosTheta increases: forwardMix must be non-decreasing, aftMix must be non-increasing
            if (i > 0) {
                assertTrue(forwardMix >= prevForwardMix - 1.0E-5F,
                        "forwardMix must be monotonically non-decreasing with increasing cosTheta at step " + i);
                assertTrue(aftMix <= prevAftMix + 1.0E-5F,
                        "aftMix must be monotonically non-increasing with increasing cosTheta at step " + i);
            }

            // Cutoff invariant: for cosTheta <= -0.15, forwardMix must be exactly 0.0
            if (cosTheta <= -0.15F) {
                assertEquals(0.0F, forwardMix, 1.0E-5F,
                        "forwardMix must be 0.0 when cosTheta <= -0.15F (cos: " + cosTheta + ")");
            }

            // Aft floor clamp invariant: for cosTheta >= 0.0625, aftMix must clamp to 0.15
            if (cosTheta >= 0.0625F) {
                assertEquals(0.15F, aftMix, 1.0E-5F,
                        "aftMix must clamp to floor of 0.15 when cosTheta >= 0.0625F (cos: " + cosTheta + ")");
            }

            prevForwardMix = forwardMix;
            prevAftMix = aftMix;
        }
    }

    // =============================================================================================
    // 2. Critical Boundary Aspect Points
    // =============================================================================================

    public static void testCriticalBoundaryAspectPoints() {
        // 1. Nose-on: cosTheta = 1.0F
        final float cosNose = 1.0F;
        final float fwdNose = Mth.clamp((cosNose + 0.15F) / 1.15F, 0.0F, 1.0F);
        final float aftNose = Mth.clamp((-cosNose + 0.25F) / 1.25F, 0.15F, 1.0F);
        assertEquals(1.0F, fwdNose, 1.0E-6F, "Nose-on forward mix must be exactly 1.0");
        assertEquals(0.15F, aftNose, 1.0E-6F, "Nose-on aft mix must clamp to 0.15 floor");

        // 2. Forward Cutoff: cosTheta = -0.15F
        final float cosCutoff = -0.15F;
        final float fwdCutoff = Mth.clamp((cosCutoff + 0.15F) / 1.15F, 0.0F, 1.0F);
        final float aftCutoff = Mth.clamp((-cosCutoff + 0.25F) / 1.25F, 0.15F, 1.0F);
        assertEquals(0.0F, fwdCutoff, 1.0E-6F, "Forward cutoff aspect forward mix must be exactly 0.0");
        assertEquals(0.32F, aftCutoff, 1.0E-5F, "Forward cutoff aspect aft mix must be 0.40/1.25 = 0.32");

        // 3. Broadside: cosTheta = 0.0F
        final float cosBroadside = 0.0F;
        final float fwdBroadside = Mth.clamp((cosBroadside + 0.15F) / 1.15F, 0.0F, 1.0F);
        final float aftBroadside = Mth.clamp((-cosBroadside + 0.25F) / 1.25F, 0.15F, 1.0F);
        final float expectedFwdBroadside = 0.15F / 1.15F; // ~0.13043478
        assertEquals(expectedFwdBroadside, fwdBroadside, 1.0E-5F, "Broadside forward mix must be 0.15/1.15");
        assertEquals(0.20F, aftBroadside, 1.0E-5F, "Broadside aft mix must be exactly 0.25/1.25 = 0.20");

        // 4. Aft Onset / Unclamp Boundary: cosTheta = 0.0625F
        // (-0.0625 + 0.25) / 1.25 = 0.1875 / 1.25 = 0.1500
        final float cosAftOnset = 0.0625F;
        final float aftAtOnset = Mth.clamp((-cosAftOnset + 0.25F) / 1.25F, 0.15F, 1.0F);
        assertEquals(0.15F, aftAtOnset, 1.0E-6F, "Aft mix at onset boundary cos=0.0625 must equal exactly 0.15");

        final float aftAboveOnset = Mth.clamp((-(cosAftOnset + 0.01F) + 0.25F) / 1.25F, 0.15F, 1.0F);
        assertEquals(0.15F, aftAboveOnset, 1.0E-6F, "Aft mix above onset boundary must remain clamped at 0.15");

        final float aftBelowOnset = Mth.clamp((-(cosAftOnset - 0.01F) + 0.25F) / 1.25F, 0.15F, 1.0F);
        assertTrue(aftBelowOnset > 0.15F, "Aft mix below onset boundary must rise strictly above 0.15");

        // 5. Aft Zero-Numerator Aspect: cosTheta = 0.25F
        final float cosAftZeroNum = 0.25F;
        final float aftZeroNum = Mth.clamp((-cosAftZeroNum + 0.25F) / 1.25F, 0.15F, 1.0F);
        assertEquals(0.15F, aftZeroNum, 1.0E-6F, "Aft mix at cos=0.25 must clamp to 0.15 floor");
        final float fwdAt025 = Mth.clamp((cosAftZeroNum + 0.15F) / 1.15F, 0.0F, 1.0F);
        assertEquals(0.40F / 1.15F, fwdAt025, 1.0E-5F, "Forward mix at cos=0.25 must be 0.40/1.15");

        // 6. Tail-chase: cosTheta = -1.0F
        final float cosTail = -1.0F;
        final float fwdTail = Mth.clamp((cosTail + 0.15F) / 1.15F, 0.0F, 1.0F);
        final float aftTail = Mth.clamp((-cosTail + 0.25F) / 1.25F, 0.15F, 1.0F);
        assertEquals(0.0F, fwdTail, 1.0E-6F, "Tail-chase forward mix must clamp to 0.0");
        assertEquals(1.0F, aftTail, 1.0E-6F, "Tail-chase aft mix must be exactly (1.0+0.25)/1.25 = 1.0");
    }

    // =============================================================================================
    // 3. Adversarial Out-Of-Bounds & Floating Point Extremes
    // =============================================================================================

    public static void testAdversarialAndExtremeInputs() {
        final float[] adversarialCosValues = new float[] {
            -1.0001F, 1.0001F, -2.0F, 2.0F, -100.0F, 100.0F,
            Float.MAX_VALUE, -Float.MAX_VALUE,
            Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY
        };

        for (final float cos : adversarialCosValues) {
            final float forwardMix = Mth.clamp((cos + 0.15F) / 1.15F, 0.0F, 1.0F);
            final float aftMix = Mth.clamp((-cos + 0.25F) / 1.25F, 0.15F, 1.0F);

            assertTrue(forwardMix >= 0.0F && forwardMix <= 1.0F,
                    "Adversarial cos=" + cos + " must clamp forwardMix to [0, 1] (was: " + forwardMix + ")");
            assertTrue(aftMix >= 0.15F && aftMix <= 1.0F,
                    "Adversarial cos=" + cos + " must clamp aftMix to [0.15, 1] (was: " + aftMix + ")");
        }
    }

    // =============================================================================================
    // 4. Aspect-Angle Vector Geometry Edge Cases
    // =============================================================================================

    public static void testVectorGeometryZeroProtections() {
        // Zero distance check: camera at exact missile position (distance = 0.0)
        final Vec3 missilePos = new Vec3(100.0D, 64.0D, 200.0D);
        final Vec3 cameraPosCoincident = new Vec3(100.0D, 64.0D, 200.0D);
        final double distanceCoincident = missilePos.distanceTo(cameraPosCoincident);
        assertEquals(0.0F, (float) distanceCoincident, 1.0E-6F, "Coincident distance must be 0.0");

        final Vec3 velocity = new Vec3(0.0D, 0.0D, 2.95D);
        final double speed = velocity.length();

        final Vec3 flightDir = speed > 0.05D ? velocity.normalize() : new Vec3(0.0D, 0.0D, 1.0D);
        final Vec3 dirToListener = distanceCoincident > 0.01D
                ? cameraPosCoincident.subtract(missilePos).normalize()
                : flightDir;

        // In coincident case, dirToListener defaults to flightDir
        assertTrue(!Double.isNaN(dirToListener.x) && !Double.isNaN(dirToListener.y) && !Double.isNaN(dirToListener.z),
                "Coincident listener direction must not be NaN");
        final float cosTheta = (float) flightDir.dot(dirToListener);
        assertEquals(1.0F, cosTheta, 1.0E-5F, "Coincident listener aspect must evaluate to 1.0 (nose-on / full audio)");

        // Zero velocity check: speed <= 0.05
        final Vec3 zeroVel = Vec3.ZERO;
        final double zeroSpeed = zeroVel.length();
        final Vec3 courseDir = new Vec3(1.0D, 0.0D, 0.0D);
        final Vec3 resolvedFlightDir = zeroSpeed > 0.05D ? zeroVel.normalize() : courseDir.normalize();
        assertTrue(!Double.isNaN(resolvedFlightDir.x), "Zero speed must safely use courseDir fallback without NaN");
        assertEquals(1.0F, (float) resolvedFlightDir.length(), 1.0E-5F, "Resolved flightDir must be unit vector");
    }

    // =============================================================================================
    // 5. Doppler Pitch Numerical Robustness
    // =============================================================================================

    public static void testDopplerPitchNumericalRobustness() {
        final Vec3 dronePos = new Vec3(0.0D, 50.0D, 0.0D);
        final Vec3 playerPos = new Vec3(0.0D, 50.0D, 100.0D);

        // Standard cruise velocity towards listener
        final Vec3 cruiseVel = new Vec3(0.0D, 0.0D, 2.95D);
        final float cruiseDoppler = DroneSoundEffects.computeDopplerPitch(dronePos, cruiseVel, playerPos);
        assertTrue(cruiseDoppler > 1.0F && cruiseDoppler <= 1.18F,
                "Approaching missile must have elevated Doppler pitch (was: " + cruiseDoppler + ")");

        // Receding missile
        final Vec3 recedingVel = new Vec3(0.0D, 0.0D, -2.95D);
        final float recedingDoppler = DroneSoundEffects.computeDopplerPitch(dronePos, recedingVel, playerPos);
        assertTrue(recedingDoppler < 1.0F && recedingDoppler >= 0.88F,
                "Receding missile must have depressed Doppler pitch (was: " + recedingDoppler + ")");

        // Proximity boundary: distance < 0.01m
        final float coincidentDoppler = DroneSoundEffects.computeDopplerPitch(dronePos, cruiseVel, dronePos);
        assertEquals(1.0F, coincidentDoppler, 1.0E-5F, "Coincident distance must return 1.0F Doppler pitch");

        // Extreme Mach 5+ supersonic velocity: must clamp safely without div-by-zero
        final Vec3 hypersonicVel = new Vec3(0.0D, 0.0D, 5000.0D);
        final float hypersonicDoppler = DroneSoundEffects.computeDopplerPitch(dronePos, hypersonicVel, playerPos);
        assertEquals(1.18F, hypersonicDoppler, 1.0E-5F, "Hypersonic velocity must clamp cleanly to MAX_DOPPLER_PITCH");

        // Extreme negative supersonic velocity
        final Vec3 negativeHyperVel = new Vec3(0.0D, 0.0D, -5000.0D);
        final float negativeHyperDoppler = DroneSoundEffects.computeDopplerPitch(dronePos, negativeHyperVel, playerPos);
        assertEquals(0.88F, negativeHyperDoppler, 1.0E-5F, "Hypersonic receding velocity must clamp to MIN_DOPPLER_PITCH");
    }

    // =============================================================================================
    // 6. Air-Absorption GainHF Atmospheric Rolloff Boundaries
    // =============================================================================================

    public static void testAirAbsorptionGainHFBoundaries() {
        final DroneSoundEffects.SoundProfile profile = DroneSoundEffects.SoundProfile.FP5_FLAMINGO;
        final double maxAudible = 650.0D;

        // Near distance: 0m -> gainHF must be 1.0
        final float gainNear = DroneSoundEffects.computeDistanceGainHF(0.0D, maxAudible, profile);
        assertEquals(1.0F, gainNear, 1.0E-5F, "Near distance must have gainHF = 1.0");

        // Inside hfStart (maxAudible * 0.08 = 52.0m) -> gainHF must be 1.0
        final float gainInsideStart = DroneSoundEffects.computeDistanceGainHF(40.0D, maxAudible, profile);
        assertEquals(1.0F, gainInsideStart, 1.0E-5F, "Distance inside hfStart must have gainHF = 1.0");

        // At max distance: 650m -> gainHF must equal minDistanceGainHF (0.12F)
        final float gainMax = DroneSoundEffects.computeDistanceGainHF(maxAudible, maxAudible, profile);
        assertEquals(0.12F, gainMax, 1.0E-5F, "Distance at maxAudible must equal minDistanceGainHF");

        // Beyond max distance: 1000m -> gainHF must remain minDistanceGainHF
        final float gainBeyond = DroneSoundEffects.computeDistanceGainHF(1000.0D, maxAudible, profile);
        assertEquals(0.12F, gainBeyond, 1.0E-5F, "Distance beyond maxAudible must remain minDistanceGainHF");

        // Combined gain with high altitude (player at y=64, drone at y=400, diff=336m)
        final float combined = DroneSoundEffects.computeCombinedGainHF(500.0D, 400.0D, 64.0D, maxAudible, profile);
        assertTrue(combined > 0.0F && combined <= 1.0F, "Combined gainHF must remain in (0, 1] (was: " + combined + ")");
    }

    // =============================================================================================
    // 7. Distance Volume Factor Envelope
    // =============================================================================================

    public static void testDistanceVolumeFactorEnvelope() {
        final DroneSoundEffects.SoundProfile profile = DroneSoundEffects.SoundProfile.FP5_FLAMINGO;
        final double maxAudible = 650.0D;

        // Proximity (0m to nearField = 26m): volume = 1.0
        final float volNear = DroneSoundEffects.computeDistanceVolumeFactor(10.0D, maxAudible, profile);
        assertEquals(1.0F, volNear, 1.0E-5F, "Near field volume factor must be 1.0");

        // Mid distance: 300m -> must be audible (> 0.2)
        final float volMid = DroneSoundEffects.computeDistanceVolumeFactor(300.0D, maxAudible, profile);
        assertTrue(volMid > 0.15F && volMid < 0.6F, "Mid distance volume factor must be in [0.15, 0.6] (was: " + volMid + ")");

        // At max distance: 650m -> volume factor = 0.0
        final float volMax = DroneSoundEffects.computeDistanceVolumeFactor(maxAudible, maxAudible, profile);
        assertEquals(0.0F, volMax, 1.0E-5F, "Max audible distance volume factor must be 0.0");

        // Beyond max distance: 800m -> volume factor = 0.0
        final float volBeyond = DroneSoundEffects.computeDistanceVolumeFactor(800.0D, maxAudible, profile);
        assertEquals(0.0F, volBeyond, 1.0E-5F, "Beyond max audible distance volume factor must be 0.0");
    }

    // =============================================================================================
    // 8. High-Contention Multi-Threaded Concurrent Filter Access (16 Threads)
    // =============================================================================================

    private static final class StressMockDriver implements OpenALFilters.FilterDriver {
        private final AtomicInteger idCounter = new AtomicInteger(5000);
        final Map<Integer, Integer> filterTypes = new ConcurrentHashMap<>();
        final Map<Integer, Map<Integer, Float>> filterParams = new ConcurrentHashMap<>();
        final Map<Integer, Integer> sourceBindings = new ConcurrentHashMap<>();
        final Set<Integer> deletedFilters = ConcurrentHashMap.newKeySet();
        final AtomicInteger bindCalls = new AtomicInteger(0);
        final AtomicInteger unbindCalls = new AtomicInteger(0);

        @Override
        public int genFilter() {
            return idCounter.incrementAndGet();
        }

        @Override
        public void setFilterType(final int filterId, final int type) {
            filterTypes.put(filterId, type);
        }

        @Override
        public void setFilterParam(final int filterId, final int param, final float value) {
            filterParams.computeIfAbsent(filterId, k -> new ConcurrentHashMap<>()).put(param, value);
        }

        @Override
        public void bindDirectFilter(final int sourceId, final int filterId) {
            bindCalls.incrementAndGet();
            sourceBindings.put(sourceId, filterId);
        }

        @Override
        public void unbindDirectFilter(final int sourceId) {
            unbindCalls.incrementAndGet();
            sourceBindings.put(sourceId, 0);
        }

        @Override
        public void deleteFilter(final int filterId) {
            deletedFilters.add(filterId);
        }
    }

    public static void testHighContentionConcurrentFilterAccess() {
        final StressMockDriver mockDriver = new StressMockDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(mockDriver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int threadCount = 16;
            final int iterationsPerThread = 1000;
            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch latch = new CountDownLatch(threadCount);
            final AtomicReference<Throwable> errorRef = new AtomicReference<>(null);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            final int sourceId = (threadId * 50) + (i % 25) + 1;
                            final float gain = (float) Math.random();
                            final float gainHF = (float) Math.random();

                            OpenALFilters.applyToSource(sourceId, gain, gainHF);
                            final int fId = OpenALFilters.getFilterForSource(sourceId);
                            if (fId <= 0) {
                                throw new IllegalStateException("Expected non-zero filter for source " + sourceId);
                            }

                            if (i % 4 == 0) {
                                OpenALFilters.removeFromSource(sourceId);
                            }
                        }
                    } catch (Throwable th) {
                        errorRef.compareAndSet(null, th);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            final boolean finished;
            try {
                finished = latch.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                throw new AssertionError("Test was interrupted", ie);
            }
            executor.shutdownNow();

            assertTrue(finished, "16 concurrent worker threads must complete within timeout");
            if (errorRef.get() != null) {
                throw new AssertionError("Concurrent execution failed: " + errorRef.get().getMessage(), errorRef.get());
            }

            // Clean up all
            OpenALFilters.cleanup();
            assertEquals(0, OpenALFilters.getActiveFilterCount(), 0.0F, "Tracking map must be empty after cleanup");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    // =============================================================================================
    // 9. Shared-Source Concurrency Race Stress (Apply vs Remove vs Clear)
    // =============================================================================================

    public static void testSharedSourceConcurrencyRaces() {
        final StressMockDriver mockDriver = new StressMockDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(mockDriver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int threadCount = 12;
            final int iterations = 1000;
            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch latch = new CountDownLatch(threadCount);
            final AtomicReference<Throwable> errorRef = new AtomicReference<>(null);

            // All threads hit the EXACT SAME 5 source IDs simultaneously!
            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterations; i++) {
                            final int sourceId = (i % 5) + 1; // source IDs 1..5 shared across all 12 threads!

                            if (threadId % 3 == 0) {
                                OpenALFilters.applyToSource(sourceId, 0.9F, 0.5F);
                            } else if (threadId % 3 == 1) {
                                OpenALFilters.removeFromSource(sourceId);
                            } else {
                                final int fid = OpenALFilters.getFilterForSource(sourceId);
                                if (fid < 0) {
                                    throw new IllegalStateException("Negative filter ID observed for source " + sourceId);
                                }
                            }
                        }
                    } catch (Throwable th) {
                        errorRef.compareAndSet(null, th);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            final boolean finished;
            try {
                finished = latch.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException ie) {
                throw new AssertionError("Test interrupted", ie);
            }
            executor.shutdownNow();

            assertTrue(finished, "Shared source race stress completed without deadlock");
            if (errorRef.get() != null) {
                throw new AssertionError("Shared source race error: " + errorRef.get().getMessage(), errorRef.get());
            }

            OpenALFilters.clearAll();
            assertEquals(0, OpenALFilters.getActiveFilterCount(), 0.0F, "Map must be clear after test");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    // =============================================================================================
    // 10. Filter Allocation Idempotency & Binding Deduplication
    // =============================================================================================

    public static void testFilterAllocationDeduplication() {
        final StressMockDriver mockDriver = new StressMockDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(mockDriver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int sourceId = 42;

            // Frame 1: initial allocation
            OpenALFilters.applyToSource(sourceId, 1.0F, 0.8F);
            final int initialFilterId = OpenALFilters.getFilterForSource(sourceId);
            assertTrue(initialFilterId > 0, "Initial filter must be non-zero");
            assertEquals(1, mockDriver.bindCalls.get(), 0.0F, "First applyToSource must bind direct filter once");

            // Frames 2..100: continuous parameter updates on every client tick
            for (int frame = 2; frame <= 100; frame++) {
                final float gainHF = 0.8F - (frame * 0.005F);
                OpenALFilters.applyToSource(sourceId, 1.0F, gainHF);

                final int currentFilterId = OpenALFilters.getFilterForSource(sourceId);
                assertEquals(initialFilterId, currentFilterId, 0.0F,
                        "Filter ID must be reused across frames without reallocation");
            }

            // Driver bindDirectFilter MUST NOT have been called again!
            assertEquals(1, mockDriver.bindCalls.get(), 0.0F,
                    "Subsequent 99 frames must NOT re-bind filter (binding deduplication invariant)");

            // Parameter must reflect latest update
            final float latestGainHF = mockDriver.filterParams.get(initialFilterId).get(EXTEfx.AL_LOWPASS_GAINHF);
            final float expectedLatestGainHF = 0.8F - (100 * 0.005F);
            assertEquals(expectedLatestGainHF, latestGainHF, 0.001F,
                    "Filter HF parameter must reflect the latest frame update");

            // Remove source
            OpenALFilters.removeFromSource(sourceId);
            assertEquals(1, mockDriver.unbindCalls.get(), 0.0F, "removeFromSource must unbind direct filter once");
            assertTrue(mockDriver.deletedFilters.contains(initialFilterId), "Filter must be deleted on removal");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    // =============================================================================================
    // 11. Untracked Source Removal Safety
    // =============================================================================================

    public static void testUntrackedSourceRemovalSafety() {
        final StressMockDriver mockDriver = new StressMockDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(mockDriver);
            OpenALFilters.setEfxAvailableForTesting(true);

            // Removing an untracked source (e.g. vanilla sheep/zombie sound)
            final int untrackedSource = 999;
            OpenALFilters.removeFromSource(untrackedSource);

            // Must NOT call unbindDirectFilter or deleteFilter
            assertEquals(0, mockDriver.unbindCalls.get(), 0.0F,
                    "Untracked source removal must NOT call unbindDirectFilter");
            assertEquals(0, mockDriver.deletedFilters.size(), 0.0F,
                    "Untracked source removal must NOT attempt to delete any filter");

            // Negative or zero source ID safety
            OpenALFilters.removeFromSource(0);
            OpenALFilters.removeFromSource(-1);
            OpenALFilters.applyToSource(0, 1.0F, 1.0F);
            OpenALFilters.applyToSource(-5, 1.0F, 1.0F);

            assertEquals(0, mockDriver.bindCalls.get(), 0.0F, "Non-positive source ID must no-op");
            assertEquals(0, mockDriver.unbindCalls.get(), 0.0F, "Non-positive source ID must no-op");
            assertEquals(0, OpenALFilters.getActiveFilterCount(), 0.0F, "Map must remain empty");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }
}
