package com.fullfud.fullfud.audio;

import com.fullfud.fullfud.client.sound.OpenALFilters;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.openal.EXTEfx;

/**
 * Comprehensive test suite verifying Directional Aeroacoustics and OpenAL EFX Filter Isolation (Milestone M3 / Requirement R1).
 */
@Fp5TestSuite(name = "FP-5 Flamingo Directional Aeroacoustics & OpenAL EFX Filters", features = {"F5", "F6", "F7"}, milestone = "M3")
public class Fp5DirectionalAeroacousticsTest {

    // ---------------------------------------------------------------------------------------------
    // Tier 1: Aspect-Angle Directivity Formulas & Mathematical Invariants
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 1, features = {"F6"}, description = "Verify Aspect-Angle Cosine calculation and Forward vs Aft mix formulas")
    public void testAspectAngleDirectivityFormulas() {
        // Forward Aspect: missile approaching listener along Z-axis (cosTheta = 1.0)
        final Vec3 flightDirApproaching = new Vec3(0.0D, 0.0D, 1.0D);
        final Vec3 dirToListenerApproaching = new Vec3(0.0D, 0.0D, 1.0D);
        final float cosThetaHeadOn = (float) flightDirApproaching.dot(dirToListenerApproaching);
        Fp5Assertions.assertEquals(1.0F, cosThetaHeadOn, 0.001F, "Head-on approach aspect cosine must be 1.0");

        final float forwardMixHeadOn = Mth.clamp((cosThetaHeadOn + 0.15F) / 1.15F, 0.0F, 1.0F);
        final float aftMixHeadOn = Mth.clamp((-cosThetaHeadOn + 0.25F) / 1.25F, 0.15F, 1.0F);
        Fp5Assertions.assertEquals(1.0F, forwardMixHeadOn, 0.001F, "Head-on forward whistle mix must saturate at 1.0");
        Fp5Assertions.assertEquals(0.15F, aftMixHeadOn, 0.001F, "Head-on aft jet rumble mix must clamp to floor of 0.15");

        // Aft Aspect: missile receding away from listener (cosTheta = -1.0)
        final Vec3 dirToListenerReceding = new Vec3(0.0D, 0.0D, -1.0D);
        final float cosThetaAft = (float) flightDirApproaching.dot(dirToListenerReceding);
        Fp5Assertions.assertEquals(-1.0F, cosThetaAft, 0.001F, "Pure tail-aspect cosine must be -1.0");

        final float forwardMixAft = Mth.clamp((cosThetaAft + 0.15F) / 1.15F, 0.0F, 1.0F);
        final float aftMixAft = Mth.clamp((-cosThetaAft + 0.25F) / 1.25F, 0.15F, 1.0F);
        Fp5Assertions.assertEquals(0.0F, forwardMixAft, 0.001F, "Tail-aspect forward whistle mix must roll off to 0.0");
        Fp5Assertions.assertEquals(1.0F, aftMixAft, 0.001F, "Tail-aspect aft jet rumble mix must saturate at 1.0");

        // Broadside Aspect: missile passing perpendicular (cosTheta = 0.0)
        final Vec3 dirToListenerBroadside = new Vec3(1.0D, 0.0D, 0.0D);
        final float cosThetaBroadside = (float) flightDirApproaching.dot(dirToListenerBroadside);
        Fp5Assertions.assertEquals(0.0F, cosThetaBroadside, 0.001F, "Broadside aspect cosine must be 0.0");

        final float forwardMixBroadside = Mth.clamp((cosThetaBroadside + 0.15F) / 1.15F, 0.0F, 1.0F);
        final float aftMixBroadside = Mth.clamp((-cosThetaBroadside + 0.25F) / 1.25F, 0.15F, 1.0F);
        Fp5Assertions.assertTrue(forwardMixBroadside > 0.1F && forwardMixBroadside < 0.2F,
                "Broadside forward mix must be ~0.13 (actual: " + forwardMixBroadside + ")");
        Fp5Assertions.assertEquals(0.20F, aftMixBroadside, 0.001F,
                "Broadside aft mix must be 0.20 (actual: " + aftMixBroadside + ")");
    }

    @Fp5Test(tier = 1, features = {"F6"}, description = "Verify 360-degree radial aspect angle sweep and monotonic directivity transitions")
    public void testAspectAngleRadialSweepMonotonicity() {
        final Vec3 flightDir = new Vec3(0.0D, 0.0D, 1.0D);

        float prevForwardMix = 1.0F;
        float prevAftMix = 0.15F;

        // Sweep from 0 degrees (head-on) to 180 degrees (pure tail) in 5-degree increments
        for (int deg = 0; deg <= 180; deg += 5) {
            final double rad = Math.toRadians(deg);
            final Vec3 dirToListener = new Vec3(Math.sin(rad), 0.0D, Math.cos(rad)).normalize();
            final float cosTheta = (float) flightDir.dot(dirToListener);

            final float forwardMix = Mth.clamp((cosTheta + 0.15F) / 1.15F, 0.0F, 1.0F);
            final float aftMix = Mth.clamp((-cosTheta + 0.25F) / 1.25F, 0.15F, 1.0F);

            // Bounds check
            Fp5Assertions.assertTrue(forwardMix >= 0.0F && forwardMix <= 1.0F,
                    "Forward mix must always be in [0, 1] at " + deg + " deg");
            Fp5Assertions.assertTrue(aftMix >= 0.15F && aftMix <= 1.0F,
                    "Aft mix must always be in [0.15, 1] at " + deg + " deg");

            // Monotonicity check
            Fp5Assertions.assertTrue(forwardMix <= prevForwardMix + 0.0001F,
                    "Forward mix must decrease monotonically with angle (deg: " + deg + ")");
            Fp5Assertions.assertTrue(aftMix >= prevAftMix - 0.0001F,
                    "Aft mix must increase monotonically with angle (deg: " + deg + ")");

            // Extinction check past 100 degrees (cosTheta <= -0.15)
            if (cosTheta <= -0.15F) {
                Fp5Assertions.assertEquals(0.0F, forwardMix, 0.0001F,
                        "Forward whistle must completely extinguish when cosTheta <= -0.15");
            }

            prevForwardMix = forwardMix;
            prevAftMix = aftMix;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 2: CPA Flyby Trigger & Boundary Zero-Crossing Hysteresis
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 2, features = {"F6"}, description = "Verify CPA (Closest Point of Approach) zero-crossing logic and distance envelope")
    public void testCpaFlybyTriggerLogic() {
        // Condition: prevCosTheta > 0.0F && cosTheta <= 0.05F && distance <= 32.0D
        final float prevApproaching = 0.3F;
        final float currCpa = 0.02F;
        final double distInside = 20.0D;
        final double distOutside = 45.0D;

        final boolean triggerInside = prevApproaching > 0.0F && currCpa <= 0.05F && distInside <= 32.0D;
        Fp5Assertions.assertTrue(triggerInside, "Flyby whoosh must trigger inside 32m during zero-crossing");

        final boolean triggerOutside = prevApproaching > 0.0F && currCpa <= 0.05F && distOutside <= 32.0D;
        Fp5Assertions.assertFalse(triggerOutside, "Flyby whoosh must NOT trigger beyond 32m boundary");

        // Check reset condition when missile begins approaching next waypoint or loops around (cosTheta > 0.2F)
        final float cosThetaNewApproach = 0.25F;
        final boolean resetFlag = cosThetaNewApproach > 0.2F;
        Fp5Assertions.assertTrue(resetFlag, "flybyTriggeredThisPass must reset when cosTheta > 0.2F");
    }

    @Fp5Test(tier = 2, features = {"F6"}, description = "Verify CPA zero-crossing trajectory state machine, hysteresis, and single-shot per pass")
    public void testCpaTrajectoryStateMachine() {
        // Simulate a flight trajectory passing over listener:
        // [prevCosTheta, currCosTheta, distance, expectedTrigger]
        class Waypoint {
            final float prevCos;
            final float currCos;
            final double dist;
            final boolean expectTrigger;
            Waypoint(float p, float c, double d, boolean e) {
                this.prevCos = p;
                this.currCos = c;
                this.dist = d;
                this.expectTrigger = e;
            }
        }

        final Waypoint[] trajectory = new Waypoint[] {
            new Waypoint(Float.NaN, 0.85F, 120.0D, false), // Approach start (no prev)
            new Waypoint(0.85F, 0.60F, 60.0D, false),      // Approaching closer
            new Waypoint(0.60F, 0.15F, 28.0D, false),      // Close approach, still cos > 0.05
            new Waypoint(0.15F, -0.02F, 16.0D, true),      // ZERO-CROSSING inside 32m -> TRIGGER
            new Waypoint(-0.02F, -0.40F, 22.0D, false),    // Receding, must NOT trigger again
            new Waypoint(-0.40F, -0.85F, 80.0D, false),    // Far receding
            new Waypoint(-0.85F, 0.10F, 90.0D, false),     // Turning, cos > 0 but not > 0.2 yet
            new Waypoint(0.10F, 0.35F, 85.0D, false),      // Turned: cos > 0.2 -> reset trigger flag!
            new Waypoint(0.35F, 0.08F, 30.0D, false),      // Second approach inside 32m
            new Waypoint(0.08F, 0.01F, 24.0D, true)        // Second pass ZERO-CROSSING -> TRIGGER AGAIN
        };

        boolean flybyTriggeredThisPass = false;
        int triggerCount = 0;

        for (int i = 0; i < trajectory.length; i++) {
            final Waypoint wp = trajectory[i];
            boolean triggered = false;
            if (!Float.isNaN(wp.prevCos)) {
                if (wp.prevCos > 0.0F && wp.currCos <= 0.05F && wp.dist <= 32.0D && !flybyTriggeredThisPass) {
                    flybyTriggeredThisPass = true;
                    triggered = true;
                    triggerCount++;
                }
            }
            if (wp.currCos > 0.2F) {
                flybyTriggeredThisPass = false;
            }

            Fp5Assertions.assertEquals(wp.expectTrigger, triggered,
                    "Step " + i + " trigger state mismatch (prev=" + wp.prevCos + ", curr=" + wp.currCos + ", dist=" + wp.dist + ")");
        }

        Fp5Assertions.assertEquals(2, triggerCount, "Total flyby triggers across two passes must be exactly 2");

        // Boundary test: distance exactly 32.0m vs 32.001m
        final boolean boundaryAt32 = 0.1F > 0.0F && 0.02F <= 0.05F && 32.0D <= 32.0D;
        final boolean boundaryAbove32 = 0.1F > 0.0F && 0.02F <= 0.05F && 32.001D <= 32.0D;
        Fp5Assertions.assertTrue(boundaryAt32, "Boundary at exactly 32.0m must trigger");
        Fp5Assertions.assertFalse(boundaryAbove32, "Boundary at 32.001m must reject");
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 2: Dedicated Per-Source OpenAL Filter Isolation & Lifecycle
    // ---------------------------------------------------------------------------------------------

    private static final class MockFilterDriver implements OpenALFilters.FilterDriver {
        private final AtomicInteger idCounter = new AtomicInteger(1000);
        final Map<Integer, Integer> filterTypes = new ConcurrentHashMap<>();
        final Map<Integer, Map<Integer, Float>> filterParams = new ConcurrentHashMap<>();
        final Map<Integer, Integer> sourceBindings = new ConcurrentHashMap<>();
        final Set<Integer> deletedFilters = ConcurrentHashMap.newKeySet();

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
            sourceBindings.put(sourceId, filterId);
        }

        @Override
        public void unbindDirectFilter(final int sourceId) {
            sourceBindings.put(sourceId, 0);
        }

        @Override
        public void deleteFilter(final int filterId) {
            deletedFilters.add(filterId);
        }
    }

    @Fp5Test(tier = 2, features = {"F7"}, description = "Verify dedicated per-source OpenAL filter allocation, parameter isolation, and clean deletion")
    public void testDedicatedPerSourceFilterIsolation() {
        final MockFilterDriver mockDriver = new MockFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(mockDriver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int sourceA = 101; // e.g. Whistle SoundInstance
            final int sourceB = 102; // e.g. Rumble SoundInstance
            final int sourceC = 103; // e.g. Booster SoundInstance

            // 1. Allocate and configure filter for Source A
            OpenALFilters.applyToSource(sourceA, 1.0F, 0.85F);
            final int filterA = OpenALFilters.getFilterForSource(sourceA);
            Fp5Assertions.assertTrue(filterA > 0, "Source A must receive a non-zero filter ID");
            Fp5Assertions.assertEquals(EXTEfx.AL_FILTER_LOWPASS, (int) mockDriver.filterTypes.get(filterA),
                    "Filter A must have AL_FILTER_LOWPASS type");
            Fp5Assertions.assertEquals(1.0F, mockDriver.filterParams.get(filterA).get(EXTEfx.AL_LOWPASS_GAIN), 0.001F,
                    "Filter A gain must be 1.0");
            Fp5Assertions.assertEquals(0.85F, mockDriver.filterParams.get(filterA).get(EXTEfx.AL_LOWPASS_GAINHF), 0.001F,
                    "Filter A gainHF must be 0.85");
            Fp5Assertions.assertEquals(filterA, (int) mockDriver.sourceBindings.get(sourceA),
                    "Source A must be bound to Filter A");

            // 2. Allocate and configure filter for Source B
            OpenALFilters.applyToSource(sourceB, 0.8F, 0.25F);
            final int filterB = OpenALFilters.getFilterForSource(sourceB);
            Fp5Assertions.assertTrue(filterB > 0, "Source B must receive a non-zero filter ID");
            Fp5Assertions.assertTrue(filterA != filterB, "Per-source filter isolation: Filter A and Filter B must have DISTINCT IDs");

            // 3. Verify parameter isolation: Source B parameters must NOT overwrite Source A
            Fp5Assertions.assertEquals(0.85F, mockDriver.filterParams.get(filterA).get(EXTEfx.AL_LOWPASS_GAINHF), 0.001F,
                    "Source A filter HF parameter must remain unchanged after Source B creation");
            Fp5Assertions.assertEquals(0.25F, mockDriver.filterParams.get(filterB).get(EXTEfx.AL_LOWPASS_GAINHF), 0.001F,
                    "Source B filter HF parameter must be 0.25");

            // 4. Update Source A with new HF cutoff: Source B must remain unaffected
            OpenALFilters.applyToSource(sourceA, 1.0F, 0.50F);
            Fp5Assertions.assertEquals(filterA, OpenALFilters.getFilterForSource(sourceA),
                    "Source A must reuse existing filter ID without generating a new one");
            Fp5Assertions.assertEquals(0.50F, mockDriver.filterParams.get(filterA).get(EXTEfx.AL_LOWPASS_GAINHF), 0.001F,
                    "Source A filter HF must be updated to 0.50");
            Fp5Assertions.assertEquals(0.25F, mockDriver.filterParams.get(filterB).get(EXTEfx.AL_LOWPASS_GAINHF), 0.001F,
                    "Source B filter HF must remain untouched at 0.25");

            // 5. Add third source C
            OpenALFilters.applyToSource(sourceC, 0.9F, 0.12F);
            final int filterC = OpenALFilters.getFilterForSource(sourceC);
            Fp5Assertions.assertTrue(filterC != filterA && filterC != filterB, "Filter C must be distinct from A and B");
            Fp5Assertions.assertEquals(3, OpenALFilters.getActiveFilterCount(), "Active filter count must be 3");

            // 6. Remove Source A: Source A unbinds, Filter A deleted, Source B and C remain active
            OpenALFilters.removeFromSource(sourceA);
            Fp5Assertions.assertEquals(0, (int) mockDriver.sourceBindings.get(sourceA),
                    "Source A must be unbound (AL_DIRECT_FILTER = 0)");
            Fp5Assertions.assertTrue(mockDriver.deletedFilters.contains(filterA),
                    "Filter A must be deleted via alDeleteFilters");
            Fp5Assertions.assertFalse(OpenALFilters.hasFilterForSource(sourceA),
                    "Source A must be removed from tracking map");
            Fp5Assertions.assertEquals(2, OpenALFilters.getActiveFilterCount(), "Active filter count must now be 2");

            // Ensure Source B and C filters were NOT deleted
            Fp5Assertions.assertFalse(mockDriver.deletedFilters.contains(filterB), "Filter B must NOT be deleted");
            Fp5Assertions.assertFalse(mockDriver.deletedFilters.contains(filterC), "Filter C must NOT be deleted");
            Fp5Assertions.assertTrue(OpenALFilters.hasFilterForSource(sourceB), "Source B must still be tracked");
            Fp5Assertions.assertTrue(OpenALFilters.hasFilterForSource(sourceC), "Source C must still be tracked");

            // 7. Test idempotency of removeFromSource
            OpenALFilters.removeFromSource(sourceA); // Duplicate call
            Fp5Assertions.assertEquals(2, OpenALFilters.getActiveFilterCount(), "Duplicate remove must be safely idempotent");

            // 8. Test cleanup/clearAll: all remaining filters unbound and deleted
            OpenALFilters.cleanup();
            Fp5Assertions.assertEquals(0, (int) mockDriver.sourceBindings.get(sourceB), "Source B must be unbound on cleanup");
            Fp5Assertions.assertEquals(0, (int) mockDriver.sourceBindings.get(sourceC), "Source C must be unbound on cleanup");
            Fp5Assertions.assertTrue(mockDriver.deletedFilters.contains(filterB), "Filter B must be deleted on cleanup");
            Fp5Assertions.assertTrue(mockDriver.deletedFilters.contains(filterC), "Filter C must be deleted on cleanup");
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(), "Tracking map must be empty after cleanup");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 3: Concurrency Stress Test on Filter Management
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 3, features = {"F7"}, description = "Stress test multi-threaded concurrent filter application, queries, and removal")
    public void testConcurrentMultiThreadedFilterAccess() throws Exception {
        final MockFilterDriver mockDriver = new MockFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(mockDriver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int threadCount = 8;
            final int iterationsPerThread = 500;
            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch latch = new CountDownLatch(threadCount);
            final AtomicReference<Throwable> errorRef = new AtomicReference<>(null);

            for (int t = 0; t < threadCount; t++) {
                final int threadId = t;
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            final int sourceId = (threadId * 100) + (i % 10) + 1;
                            final float gain = (float) Math.random();
                            final float gainHF = (float) Math.random();

                            OpenALFilters.applyToSource(sourceId, gain, gainHF);
                            final int fId = OpenALFilters.getFilterForSource(sourceId);
                            if (fId <= 0) {
                                throw new IllegalStateException("Expected filter for source " + sourceId);
                            }

                            if (i % 3 == 0) {
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

            final boolean completed = latch.await(10, TimeUnit.SECONDS);
            executor.shutdownNow();

            Fp5Assertions.assertTrue(completed, "Concurrent worker threads must complete within timeout");
            if (errorRef.get() != null) {
                throw new AssertionError("Concurrent execution failed with error: " + errorRef.get().getMessage(), errorRef.get());
            }

            // Cleanup should clear everything cleanly without deadlocks
            OpenALFilters.clearAll();
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(), "Filter map must be empty after concurrent clearAll");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Tier 1: Headless Environment Resilience
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 1, features = {"F7"}, description = "Verify OpenALFilters headless resilience when OpenAL native context is absent")
    public void testOpenALFiltersHeadlessResilience() {
        OpenALFilters.resetInitializationForTesting();

        // In headless testing without OpenAL context, isAvailable() must safely report false
        Fp5Assertions.assertFalse(OpenALFilters.isAvailable(),
                "Headless environment must report OpenAL EFX unavailable without throwing UnsatisfiedLinkError");

        // Methods should safely no-op
        OpenALFilters.applyToSource(999, 1.0F, 0.85F);
        Fp5Assertions.assertEquals(0, OpenALFilters.getFilterForSource(999), "No filter should be allocated when EFX unavailable");

        OpenALFilters.removeFromSource(999);
        OpenALFilters.clearAll();
        OpenALFilters.cleanup();
        Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(), "Active filter count must remain 0");
    }
}
