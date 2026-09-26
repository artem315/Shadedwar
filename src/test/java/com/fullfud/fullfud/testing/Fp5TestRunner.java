package com.fullfud.fullfud.testing;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Headless test runner and reporter for FP-5 Flamingo test suites.
 */
public final class Fp5TestRunner {
    private static final List<Class<?>> REGISTERED_SUITES = new ArrayList<>();

    public static void registerSuite(final Class<?> suiteClass) {
        REGISTERED_SUITES.add(suiteClass);
    }

    public static void main(final String[] args) {
        // Bootstrap Minecraft registries if running in a Minecraft runtime environment
        try {
            final Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            sharedConstants.getMethod("tryDetectVersion").invoke(null);
            final Class<?> bootstrap = Class.forName("net.minecraft.server.Bootstrap");
            bootstrap.getMethod("bootStrap").invoke(null);
            System.out.println("  [BOOTSTRAP] Minecraft Registries successfully bootstrapped.");
        } catch (Throwable t) {
            // Silently continue if running in plain Java environment without Minecraft classes
        }

        if (args != null && args.length > 0) {
            for (final String arg : args) {
                if (arg != null && !arg.isEmpty() && !arg.startsWith("-")) {
                    registerByName(arg.trim());
                }
            }
        }

        if (REGISTERED_SUITES.isEmpty()) {
            // Automatically register standard test suites
            registerByName("com.fullfud.fullfud.flight.Fp5FlamingoFlightTest");
            registerByName("com.fullfud.fullfud.flight.Fp5FlamingoChallengerEmpiricalTest");
            registerByName("com.fullfud.fullfud.flight.Fp5Challenger2FlightPhysicsTest");
            registerByName("com.fullfud.fullfud.flight.Fp5BoundaryEmpiricalChallengerTest");
            registerByName("com.fullfud.fullfud.audio.Fp5AudioRegistrationTest");
            registerByName("com.fullfud.fullfud.audio.Fp5SoundAssetVerificationTest");
            registerByName("com.fullfud.fullfud.audio.Fp5DirectionalAeroacousticsTest");
            registerByName("com.fullfud.fullfud.explosion.Fp5ExplosionProfileTest");
            registerByName("com.fullfud.fullfud.explosion.Fp5ShockwaveMathChallengerTest");
            registerByName("com.fullfud.fullfud.explosion.Fp5M4ChallengerBlastDemolitionStressTest");
            registerByName("com.fullfud.fullfud.vfx.Fp5ExhaustPlumeTest");
            registerByName("com.fullfud.fullfud.vfx.Fp5VfxLightingTest");
            registerByName("com.fullfud.fullfud.vfx.Fp5CameraShakeTest");
            registerByName("com.fullfud.fullfud.vfx.Fp5CameraShakeChallengerTest");
            registerByName("com.fullfud.fullfud.integration.Fp5CrossFeatureIntegrationTest");
            registerByName("com.fullfud.fullfud.model.ShahedModelGeometryTest");
        }

        System.out.println("===============================================================================");
        System.out.println("            FP-5 FLAMINGO OVERHAUL — AUTOMATED TEST RUNNER                     ");
        System.out.println("===============================================================================");
        System.out.println("Discovered " + REGISTERED_SUITES.size() + " test suites for execution.\n");

        int totalRan = 0;
        int passed = 0;
        int failed = 0;
        final long suiteStartTime = System.currentTimeMillis();

        for (final Class<?> suite : REGISTERED_SUITES) {
            final Fp5TestSuite meta = suite.getAnnotation(Fp5TestSuite.class);
            final String suiteName = meta != null ? meta.name() : suite.getSimpleName();
            final String milestone = meta != null && !meta.milestone().isEmpty() ? " [" + meta.milestone() + "]" : "";

            System.out.println("-------------------------------------------------------------------------------");
            System.out.println("SUITE: " + suiteName + milestone);
            System.out.println("-------------------------------------------------------------------------------");

            Object instance = null;
            try {
                instance = suite.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                System.err.println("  [ERROR] Failed to instantiate test suite " + suite.getName() + ": " + e.getMessage());
                failed++;
                continue;
            }

            final Method[] methods = suite.getDeclaredMethods();
            for (final Method method : methods) {
                final Fp5Test testAnn = method.getAnnotation(Fp5Test.class);
                if (testAnn == null) {
                    continue;
                }

                totalRan++;
                final String features = testAnn.features().length > 0 ? " (" + Arrays.toString(testAnn.features()) + ")" : "";
                final String desc = !testAnn.description().isEmpty() ? " — " + testAnn.description() : "";
                final String testHeader = String.format("  [Tier %d] %-38s%s%s", testAnn.tier(), method.getName(), features, desc);

                final long testStart = System.nanoTime();
                try {
                    method.setAccessible(true);
                    method.invoke(instance);
                    final double elapsedMs = (System.nanoTime() - testStart) / 1_000_000.0D;
                    System.out.printf("%s ... PASS (%.2f ms)%n", testHeader, elapsedMs);
                    passed++;
                } catch (InvocationTargetException ite) {
                    final double elapsedMs = (System.nanoTime() - testStart) / 1_000_000.0D;
                    final Throwable cause = ite.getCause();
                    System.out.printf("%s ... FAIL (%.2f ms)%n", testHeader, elapsedMs);
                    System.err.println("      FAILURE: " + cause.getMessage());
                    if (!(cause instanceof AssertionError)) {
                        cause.printStackTrace(System.err);
                    }
                    failed++;
                } catch (Exception ex) {
                    final double elapsedMs = (System.nanoTime() - testStart) / 1_000_000.0D;
                    System.out.printf("%s ... ERROR (%.2f ms)%n", testHeader, elapsedMs);
                    System.err.println("      ERROR: " + ex.getMessage());
                    ex.printStackTrace(System.err);
                    failed++;
                }
            }
            System.out.println();
        }

        final long totalTime = System.currentTimeMillis() - suiteStartTime;
        System.out.println("===============================================================================");
        System.out.println("                               EXECUTION SUMMARY                               ");
        System.out.println("===============================================================================");
        System.out.printf("Total Tests:   %d%n", totalRan);
        System.out.printf("Passed:        %d%n", passed);
        System.out.printf("Failed:        %d%n", failed);
        System.out.printf("Total Time:    %d ms%n", totalTime);
        System.out.println("===============================================================================");

        if (failed > 0) {
            System.err.println("STATUS: FAILED — " + failed + " test(s) failed.");
            System.exit(1);
        } else {
            System.out.println("STATUS: SUCCESS — All tests passed cleanly.");
            System.exit(0);
        }
    }

    private static void registerByName(final String className) {
        try {
            final Class<?> clazz = Class.forName(className);
            REGISTERED_SUITES.add(clazz);
        } catch (ClassNotFoundException e) {
            System.err.println("  [INFO] Test suite class not found on classpath: " + className);
        }
    }
}
