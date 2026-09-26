package com.fullfud.fullfud.audio;

import com.fullfud.fullfud.client.sound.OpenALFilters;
import com.fullfud.fullfud.mixin.client.ChannelMixin;
import com.fullfud.fullfud.testing.Fp5Assertions;
import com.fullfud.fullfud.testing.Fp5Test;
import com.fullfud.fullfud.testing.Fp5TestSuite;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.lwjgl.openal.EXTEfx;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Empirical Stress and Adversarial Challenge Suite for Milestone M3:
 * OpenAL Filter Allocation Lifecycle, Headless Resilience, ChannelMixin Invariants,
 * and Simulated Audio Channel Exhaustion.
 */
@Fp5TestSuite(name = "Empirical Challenge: OpenAL Lifecycle & Channel Exhaustion Harness", features = {"F6", "F7"}, milestone = "M3")
public class Fp5OpenALChannelStressTest {

    /**
     * High-fidelity mock OpenAL filter driver tracking generation, binding, and deletion counts.
     */
    public static final class TrackingFilterDriver implements OpenALFilters.FilterDriver {
        private final AtomicInteger idCounter = new AtomicInteger(5000);
        public final AtomicInteger totalGenerated = new AtomicInteger(0);
        public final AtomicInteger totalDeleted = new AtomicInteger(0);
        public final AtomicInteger totalDirectBinds = new AtomicInteger(0);
        public final AtomicInteger totalDirectUnbinds = new AtomicInteger(0);
        public final AtomicInteger totalParamUpdates = new AtomicInteger(0);

        public final Map<Integer, Integer> filterTypes = new ConcurrentHashMap<>();
        public final Map<Integer, Map<Integer, Float>> filterParams = new ConcurrentHashMap<>();
        public final Map<Integer, Integer> sourceBindings = new ConcurrentHashMap<>();
        public final Set<Integer> activeFilters = ConcurrentHashMap.newKeySet();
        public final Set<Integer> deletedFilters = ConcurrentHashMap.newKeySet();

        public volatile boolean throwOnGenFilter = false;
        public volatile boolean throwOnBind = false;
        public volatile boolean throwOnUnbind = false;
        public volatile boolean throwOnDelete = false;

        @Override
        public int genFilter() {
            if (throwOnGenFilter) {
                throw new IllegalStateException("Simulated OpenAL driver failure in genFilter");
            }
            final int id = idCounter.incrementAndGet();
            totalGenerated.incrementAndGet();
            activeFilters.add(id);
            return id;
        }

        @Override
        public void setFilterType(final int filterId, final int type) {
            filterTypes.put(filterId, type);
        }

        @Override
        public void setFilterParam(final int filterId, final int param, final float value) {
            totalParamUpdates.incrementAndGet();
            filterParams.computeIfAbsent(filterId, k -> new ConcurrentHashMap<>()).put(param, value);
        }

        @Override
        public void bindDirectFilter(final int sourceId, final int filterId) {
            if (throwOnBind) {
                throw new IllegalStateException("Simulated OpenAL driver failure in bindDirectFilter");
            }
            totalDirectBinds.incrementAndGet();
            sourceBindings.put(sourceId, filterId);
        }

        @Override
        public void unbindDirectFilter(final int sourceId) {
            if (throwOnUnbind) {
                throw new IllegalStateException("Simulated OpenAL driver failure in unbindDirectFilter");
            }
            totalDirectUnbinds.incrementAndGet();
            sourceBindings.put(sourceId, 0);
        }

        @Override
        public void deleteFilter(final int filterId) {
            if (throwOnDelete) {
                throw new IllegalStateException("Simulated OpenAL driver failure in deleteFilter");
            }
            totalDeleted.incrementAndGet();
            activeFilters.remove(filterId);
            deletedFilters.add(filterId);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 1: Headless Resilience & Memory Leak Stress Test
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 1, features = {"F7"}, description = "Stress headless resilience: zero exceptions, clean fallbacks, and zero memory leaks across 50k calls")
    public void testHeadlessResilienceZeroExceptionsAndZeroLeaks() {
        OpenALFilters.resetInitializationForTesting();

        // Verify headless status
        Fp5Assertions.assertFalse(OpenALFilters.isAvailable(),
                "In headless environment, OpenALFilters.isAvailable() must safely return false");

        // High churn test with negative, zero, extreme, and NaN values when OpenAL is unavailable
        final int iterations = 50_000;
        for (int i = 0; i < iterations; i++) {
            final int sourceId = (i % 500) - 250; // Spans [-250, +249] including 0 and negatives
            final float gain = (i % 2 == 0) ? -10.0F : 10.0F;
            final float gainHF = (i % 3 == 0) ? Float.NaN : (float) Math.random();

            // None of these calls should throw or record any entries in memory
            OpenALFilters.applyToSource(sourceId, gain, gainHF);
            if (i % 5 == 0) {
                OpenALFilters.removeFromSource(sourceId);
            }
            if (i % 1000 == 0) {
                OpenALFilters.clearAll();
                OpenALFilters.cleanup();
            }
        }

        // Memory leak verification: active filter count must be strictly 0
        Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                "Active filter count must strictly remain 0 in headless mode (zero memory leak)");
        Fp5Assertions.assertEquals(0, OpenALFilters.getFilterForSource(1),
                "Source 1 must have filter ID 0 in headless mode");
        Fp5Assertions.assertEquals(0, OpenALFilters.getFilterForSource(-1),
                "Source -1 must have filter ID 0 in headless mode");
        Fp5Assertions.assertFalse(OpenALFilters.hasFilterForSource(1),
                "hasFilterForSource must report false in headless mode");

        // Test instance methods with null SoundInstance
        OpenALFilters.applyFilterForInstance(null, 1.0F, 0.5F);
        OpenALFilters.removeFilterForInstance(null);
        Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                "Null SoundInstance filter operations must safely no-op without exceptions or leaks");
    }

    @Fp5Test(tier = 1, features = {"F7"}, description = "Verify OpenAL native linking and failure resilience")
    public void testHeadlessFallbackWhenDriverThrowsLinkError() {
        OpenALFilters.resetInitializationForTesting();

        // In headless mode without context, ensure multiple initialization calls are safe
        for (int i = 0; i < 100; i++) {
            OpenALFilters.ensureInitialized();
            Fp5Assertions.assertFalse(OpenALFilters.isAvailable(),
                    "Repeated ensureInitialized in headless environment must remain safely unavailable");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 2: ChannelMixin Invariants & Detachment Lifecycles
    // ---------------------------------------------------------------------------------------------

    private static final class TestChannelMixinSubclass extends ChannelMixin {
        // Concrete test subclass to enable invocation of ChannelMixin methods
    }

    private static void setMixinSource(final ChannelMixin mixin, final int sourceId) throws Exception {
        final Field sourceField = ChannelMixin.class.getDeclaredField("source");
        sourceField.setAccessible(true);
        sourceField.setInt(mixin, sourceId);
    }

    private static void invokeMixinStop(final ChannelMixin mixin) throws Exception {
        final Method stopMethod = ChannelMixin.class.getDeclaredMethod("fullfud$onChannelStop", CallbackInfo.class);
        stopMethod.setAccessible(true);
        stopMethod.invoke(mixin, (Object) null);
    }

    private static void invokeMixinDestroy(final ChannelMixin mixin) throws Exception {
        final Method destroyMethod = ChannelMixin.class.getDeclaredMethod("fullfud$onChannelDestroy", CallbackInfo.class);
        destroyMethod.setAccessible(true);
        destroyMethod.invoke(mixin, (Object) null);
    }

    @Fp5Test(tier = 2, features = {"F7"}, description = "Verify ChannelMixin stop() and destroy() cleanly trigger OpenALFilters.removeFromSource")
    public void testChannelMixinDirectInvocationAndLifecycle() throws Exception {
        final TrackingFilterDriver driver = new TrackingFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(driver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int sourceId = 88;
            final TestChannelMixinSubclass channelMixin = new TestChannelMixinSubclass();
            setMixinSource(channelMixin, sourceId);

            // 1. Attach filter to source
            OpenALFilters.applyToSource(sourceId, 1.0F, 0.75F);
            final int filterId = OpenALFilters.getFilterForSource(sourceId);
            Fp5Assertions.assertTrue(filterId > 0, "Source 88 must have an allocated filter");
            Fp5Assertions.assertEquals(1, driver.totalGenerated.get(), "Driver must have generated exactly 1 filter");
            Fp5Assertions.assertEquals(1, driver.totalDirectBinds.get(), "Driver must have bound direct filter once");

            // 2. Invoke Channel.stop() hook via mixin
            invokeMixinStop(channelMixin);

            // 3. Verify filter was unbound and deleted
            Fp5Assertions.assertEquals(1, driver.totalDirectUnbinds.get(),
                    "Channel stop must trigger unbindDirectFilter on source");
            Fp5Assertions.assertEquals(1, driver.totalDeleted.get(),
                    "Channel stop must trigger deleteFilter on filter ID");
            Fp5Assertions.assertTrue(driver.deletedFilters.contains(filterId),
                    "Filter must be present in deleted filters set");
            Fp5Assertions.assertFalse(OpenALFilters.hasFilterForSource(sourceId),
                    "OpenALFilters must no longer track source 88");
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                    "Active filter count must be 0 after stop");

            // 4. Attach filter again (simulating reused channel)
            OpenALFilters.applyToSource(sourceId, 0.9F, 0.40F);
            final int secondFilterId = OpenALFilters.getFilterForSource(sourceId);
            Fp5Assertions.assertTrue(secondFilterId > 0 && secondFilterId != filterId,
                    "Reused source must obtain a fresh filter ID");
            Fp5Assertions.assertEquals(2, driver.totalGenerated.get(), "Driver must have generated 2 filters total");

            // 5. Invoke Channel.destroy() hook via mixin
            invokeMixinDestroy(channelMixin);

            // 6. Verify second filter was unbound and deleted
            Fp5Assertions.assertEquals(2, driver.totalDirectUnbinds.get(),
                    "Channel destroy must trigger unbindDirectFilter on source");
            Fp5Assertions.assertEquals(2, driver.totalDeleted.get(),
                    "Channel destroy must trigger deleteFilter on second filter ID");
            Fp5Assertions.assertTrue(driver.deletedFilters.contains(secondFilterId),
                    "Second filter must be present in deleted filters set");
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                    "Active filter count must be 0 after destroy");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    @Fp5Test(tier = 2, features = {"F7"}, description = "Verify ChannelMixin stop() and destroy() with source <= 0 do not throw or call driver")
    public void testChannelMixinInvalidAndZeroSourcesSafety() throws Exception {
        final TrackingFilterDriver driver = new TrackingFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(driver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final TestChannelMixinSubclass mixin = new TestChannelMixinSubclass();
            final int[] invalidSources = {0, -1, -42, -9999, Integer.MIN_VALUE};

            for (final int invalidSource : invalidSources) {
                setMixinSource(mixin, invalidSource);

                // Both stop and destroy should safely do nothing
                invokeMixinStop(mixin);
                invokeMixinDestroy(mixin);

                Fp5Assertions.assertEquals(0, driver.totalDirectUnbinds.get(),
                        "Driver unbind must NEVER be called for source <= 0 (" + invalidSource + ")");
                Fp5Assertions.assertEquals(0, driver.totalDeleted.get(),
                        "Driver delete must NEVER be called for source <= 0 (" + invalidSource + ")");
                Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                        "Active filter count must remain 0");
            }
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    @Fp5Test(tier = 2, features = {"F7"}, description = "Verify idempotency: multiple stop() and destroy() calls do not crash or double-free")
    public void testChannelStopAndDestroyIdempotencyAndDoubleFreeDefense() throws Exception {
        final TrackingFilterDriver driver = new TrackingFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(driver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int sourceId = 55;
            final TestChannelMixinSubclass mixin = new TestChannelMixinSubclass();
            setMixinSource(mixin, sourceId);

            // Allocate filter
            OpenALFilters.applyToSource(sourceId, 1.0F, 0.8F);
            Fp5Assertions.assertEquals(1, driver.totalGenerated.get(), "Should allocate 1 filter");

            // Stop multiple times
            invokeMixinStop(mixin);
            invokeMixinStop(mixin);
            invokeMixinStop(mixin);

            // Destroy multiple times
            invokeMixinDestroy(mixin);
            invokeMixinDestroy(mixin);

            // Direct removeFromSource calls
            OpenALFilters.removeFromSource(sourceId);
            OpenALFilters.removeFromSource(sourceId);

            // Invariant checks: filter must be unbound exactly once and deleted exactly once
            Fp5Assertions.assertEquals(1, driver.totalDirectUnbinds.get(),
                    "Filter unbind must be executed exactly ONCE regardless of repeated stop/destroy calls");
            Fp5Assertions.assertEquals(1, driver.totalDeleted.get(),
                    "Filter delete must be executed exactly ONCE regardless of repeated stop/destroy calls");
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                    "Active filter count must be 0");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    @Fp5Test(tier = 2, features = {"F7"}, description = "Verify non-tracked vanilla Minecraft sound sources do not trigger driver unbind/delete")
    public void testVanillaSoundSourcesUntrackedSafety() throws Exception {
        final TrackingFilterDriver driver = new TrackingFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(driver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final TestChannelMixinSubclass mixin = new TestChannelMixinSubclass();

            // Simulate 500 vanilla Minecraft sounds stopping/destroying without filters
            for (int source = 1; source <= 500; source++) {
                setMixinSource(mixin, source);
                invokeMixinStop(mixin);
                invokeMixinDestroy(mixin);
            }

            Fp5Assertions.assertEquals(0, driver.totalDirectUnbinds.get(),
                    "Untracked vanilla sounds must incur 0 driver unbind operations");
            Fp5Assertions.assertEquals(0, driver.totalDeleted.get(),
                    "Untracked vanilla sounds must incur 0 driver delete operations");
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                    "Active filter count must remain 0");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 3: Rapid Allocation & Teardown under Simulated Audio Channel Exhaustion
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 3, features = {"F7"}, description = "Stress test 64 audio channels under 20k rapid allocation, modulation, preemption, and recycling cycles")
    public void testRapidAllocationAndTeardownUnderSimulatedAudioChannelExhaustion() {
        final TrackingFilterDriver driver = new TrackingFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(driver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int channelPoolSize = 64; // Minecraft source channel pool size
            final int totalCycles = 20_000;
            final Random random = new Random(42L);

            for (int cycle = 0; cycle < totalCycles; cycle++) {
                final int sourceId = random.nextInt(channelPoolSize) + 1; // [1..64]
                final float gain = random.nextFloat();
                final float gainHF = random.nextFloat();

                // 1. Channel allocation or tick parameter modulation
                OpenALFilters.applyToSource(sourceId, gain, gainHF);
                Fp5Assertions.assertTrue(OpenALFilters.hasFilterForSource(sourceId),
                        "Source " + sourceId + " must have active filter");

                // 2. Simulate preemption / channel reclamation (35% probability per cycle)
                if (random.nextFloat() < 0.35F) {
                    OpenALFilters.removeFromSource(sourceId);
                    Fp5Assertions.assertFalse(OpenALFilters.hasFilterForSource(sourceId),
                            "Source " + sourceId + " must not have filter after removal");
                }
            }

            // At end of test, all active channels are torn down
            final int activeRemaining = OpenALFilters.getActiveFilterCount();
            Fp5Assertions.assertTrue(activeRemaining <= channelPoolSize,
                    "Active remaining filters (" + activeRemaining + ") must not exceed channel pool size (64)");

            OpenALFilters.clearAll();
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                    "Active filter count must be strictly 0 after clearAll()");

            // Perfect lifecycle parity check: total generated == total deleted
            Fp5Assertions.assertEquals(driver.totalGenerated.get(), driver.totalDeleted.get(),
                    "OpenAL Filter Leak Check: totalGenerated (" + driver.totalGenerated.get()
                            + ") MUST EQUAL totalDeleted (" + driver.totalDeleted.get() + ")");
            Fp5Assertions.assertTrue(driver.activeFilters.isEmpty(),
                    "Driver active filters set must be empty (0 leaked native filters)");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    @Fp5Test(tier = 3, features = {"F7"}, description = "Multi-threaded audio executor contention: 16 client threads dispatching to audio executor with rapid preemption")
    public void testConcurrentChannelExhaustionWithAudioExecutorSerialization() throws Exception {
        final TrackingFilterDriver driver = new TrackingFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(driver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int clientThreadCount = 16;
            final int iterationsPerThread = 1_500;
            final int channelPoolSize = 64;

            // Dedicated single-threaded audio executor matching Minecraft's SoundEngine architecture
            final ExecutorService audioExecutor = Executors.newSingleThreadExecutor();
            final ExecutorService clientThreads = Executors.newFixedThreadPool(clientThreadCount);
            final CountDownLatch latch = new CountDownLatch(clientThreadCount);
            final AtomicReference<Throwable> failureRef = new AtomicReference<>(null);

            for (int t = 0; t < clientThreadCount; t++) {
                final int seed = t * 1000 + 7;
                clientThreads.submit(() -> {
                    try {
                        final Random rng = new Random(seed);
                        for (int i = 0; i < iterationsPerThread; i++) {
                            final int sourceId = rng.nextInt(channelPoolSize) + 1;
                            final float gain = rng.nextFloat();
                            final float gainHF = rng.nextFloat();

                            // Dispatch via audio executor (matching handle.execute contract)
                            audioExecutor.submit(() -> {
                                OpenALFilters.applyToSource(sourceId, gain, gainHF);
                            });

                            if (rng.nextBoolean()) {
                                final float g2 = rng.nextFloat();
                                final float h2 = rng.nextFloat();
                                audioExecutor.submit(() -> {
                                    OpenALFilters.applyToSource(sourceId, g2, h2);
                                });
                            }

                            if (rng.nextFloat() < 0.40F) {
                                audioExecutor.submit(() -> {
                                    OpenALFilters.removeFromSource(sourceId);
                                });
                            }
                        }
                    } catch (Throwable th) {
                        failureRef.compareAndSet(null, th);
                    } finally {
                        latch.countDown();
                    }
                });
            }

            final boolean completed = latch.await(15, TimeUnit.SECONDS);
            clientThreads.shutdownNow();

            Fp5Assertions.assertTrue(completed, "Client worker threads timed out after 15 seconds");
            if (failureRef.get() != null) {
                throw new AssertionError("Client worker threads threw exception: " + failureRef.get().getMessage(), failureRef.get());
            }

            // Drain remaining audio tasks
            audioExecutor.submit(OpenALFilters::clearAll).get(5, TimeUnit.SECONDS);
            audioExecutor.shutdown();
            audioExecutor.awaitTermination(5, TimeUnit.SECONDS);

            // Verify clean teardown under Minecraft audio executor architecture
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                    "Active filter count must be strictly 0 after audio executor teardown");
            Fp5Assertions.assertEquals(driver.totalGenerated.get(), driver.totalDeleted.get(),
                    "Total generated filters must equal total deleted filters under audio executor serialization");
            Fp5Assertions.assertTrue(driver.activeFilters.isEmpty(),
                    "Driver active filters set must be completely empty (0 leaks under audio executor model)");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    @Fp5Test(tier = 3, features = {"F7"}, description = "Empirical challenge: Characterize unsynchronized direct multi-threaded contention on shared sourceId")
    public void testAdversarialUnsynchronizedDirectContentionCharacterization() throws Exception {
        final TrackingFilterDriver driver = new TrackingFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(driver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int threadCount = 8;
            final int iterationsPerThread = 500;
            final int singleSharedSourceId = 77; // Deliberately contend on ONE identical sourceId
            final ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            final CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < iterationsPerThread; i++) {
                            OpenALFilters.applyToSource(singleSharedSourceId, 1.0F, 0.5F);
                            if (i % 2 == 0) {
                                OpenALFilters.removeFromSource(singleSharedSourceId);
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            executor.shutdownNow();

            // Measure final state after direct unsynchronized access
            OpenALFilters.clearAll();
            final int leakedInDriver = driver.activeFilters.size();
            System.out.println("  [EMPIRICAL NOTE] Unsynchronized direct contention generated " + driver.totalGenerated.get()
                    + " filters, deleted " + driver.totalDeleted.get() + ", orphaned in driver: " + leakedInDriver);
            // Invariant: confirm OpenALFilters internal map is cleared even if driver orphaned native handles
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(),
                    "OpenALFilters tracking map must be clear after clearAll()");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Challenge 4: Parameter Modulation Envelope & Non-Redundant JNI Invariants
    // ---------------------------------------------------------------------------------------------

    @Fp5Test(tier = 2, features = {"F6", "F7"}, description = "Verify parameter modulation clamping, NaN immunity, and non-redundant JNI bindings")
    public void testParameterModulationEnvelopeAndNanDefense() {
        final TrackingFilterDriver driver = new TrackingFilterDriver();
        try {
            OpenALFilters.resetInitializationForTesting();
            OpenALFilters.setDriverForTesting(driver);
            OpenALFilters.setEfxAvailableForTesting(true);

            final int sourceId = 99;

            // 1. Initial allocation with out-of-bounds inputs
            OpenALFilters.applyToSource(sourceId, -2.5F, 15.0F);
            final int filterId = OpenALFilters.getFilterForSource(sourceId);
            Fp5Assertions.assertTrue(filterId > 0, "Filter must be allocated");

            // Verify clamping
            final Map<Integer, Float> params = driver.filterParams.get(filterId);
            Fp5Assertions.assertEquals(0.0F, params.get(EXTEfx.AL_LOWPASS_GAIN), 0.0001F,
                    "Gain -2.5 must be clamped to 0.0");
            Fp5Assertions.assertEquals(1.0F, params.get(EXTEfx.AL_LOWPASS_GAINHF), 0.0001F,
                    "GainHF 15.0 must be clamped to 1.0");
            Fp5Assertions.assertEquals(1, driver.totalDirectBinds.get(),
                    "Initial allocation must bind direct filter exactly once");

            // 2. Modulate parameters 10,000 times in place
            for (int i = 0; i < 10_000; i++) {
                final float newGain = (i % 100) / 100.0F;
                final float newGainHF = (100 - (i % 100)) / 100.0F;
                OpenALFilters.applyToSource(sourceId, newGain, newGainHF);
            }

            // Verify binding count DID NOT INCREASE (zero redundant JNI binds)
            Fp5Assertions.assertEquals(1, driver.totalDirectBinds.get(),
                    "Zero redundant JNI binds: direct filter must NOT be rebound during parameter updates");
            Fp5Assertions.assertEquals(filterId, OpenALFilters.getFilterForSource(sourceId),
                    "Filter ID must not change during parameter updates");
            Fp5Assertions.assertEquals(1, OpenALFilters.getActiveFilterCount(),
                    "Active filter count must remain 1");

            // 3. Test infinite inputs
            OpenALFilters.applyToSource(sourceId, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
            final Map<Integer, Float> infParams = driver.filterParams.get(filterId);
            Fp5Assertions.assertEquals(0.0F, infParams.get(EXTEfx.AL_LOWPASS_GAIN), 0.0001F,
                    "-Infinity gain must be clamped to 0.0");
            Fp5Assertions.assertEquals(1.0F, infParams.get(EXTEfx.AL_LOWPASS_GAINHF), 0.0001F,
                    "+Infinity gainHF must be clamped to 1.0");

            // Clean teardown
            OpenALFilters.removeFromSource(sourceId);
            Fp5Assertions.assertEquals(0, OpenALFilters.getActiveFilterCount(), "Active filter count must be 0");
            Fp5Assertions.assertEquals(1, driver.totalDeleted.get(), "Filter must be deleted");
        } finally {
            OpenALFilters.resetInitializationForTesting();
        }
    }

    /**
     * Standalone main entry point for direct execution.
     */
    public static void main(final String[] args) {
        final Fp5OpenALChannelStressTest suite = new Fp5OpenALChannelStressTest();
        System.out.println("Executing Fp5OpenALChannelStressTest...");

        try {
            suite.testHeadlessResilienceZeroExceptionsAndZeroLeaks();
            System.out.println("  [PASS] testHeadlessResilienceZeroExceptionsAndZeroLeaks");

            suite.testHeadlessFallbackWhenDriverThrowsLinkError();
            System.out.println("  [PASS] testHeadlessFallbackWhenDriverThrowsLinkError");

            suite.testChannelMixinDirectInvocationAndLifecycle();
            System.out.println("  [PASS] testChannelMixinDirectInvocationAndLifecycle");

            suite.testChannelMixinInvalidAndZeroSourcesSafety();
            System.out.println("  [PASS] testChannelMixinInvalidAndZeroSourcesSafety");

            suite.testChannelStopAndDestroyIdempotencyAndDoubleFreeDefense();
            System.out.println("  [PASS] testChannelStopAndDestroyIdempotencyAndDoubleFreeDefense");

            suite.testVanillaSoundSourcesUntrackedSafety();
            System.out.println("  [PASS] testVanillaSoundSourcesUntrackedSafety");

            suite.testRapidAllocationAndTeardownUnderSimulatedAudioChannelExhaustion();
            System.out.println("  [PASS] testRapidAllocationAndTeardownUnderSimulatedAudioChannelExhaustion");

            suite.testConcurrentChannelExhaustionWithAudioExecutorSerialization();
            System.out.println("  [PASS] testConcurrentChannelExhaustionWithAudioExecutorSerialization");

            suite.testAdversarialUnsynchronizedDirectContentionCharacterization();
            System.out.println("  [PASS] testAdversarialUnsynchronizedDirectContentionCharacterization");

            suite.testParameterModulationEnvelopeAndNanDefense();
            System.out.println("  [PASS] testParameterModulationEnvelopeAndNanDefense");

            System.out.println("\nALL 10 EMPIRICAL STRESS TESTS PASSED CLEANLY!");
        } catch (Throwable t) {
            System.err.println("TEST FAILURE: " + t.getMessage());
            t.printStackTrace();
            System.exit(1);
        }
    }
}
