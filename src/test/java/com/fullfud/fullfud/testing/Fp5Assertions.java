package com.fullfud.fullfud.testing;

import java.util.Objects;

/**
 * Self-contained fluent assertion engine for the FP-5 test harness.
 */
public final class Fp5Assertions {
    private Fp5Assertions() {
    }

    public static void assertTrue(final boolean condition, final String message) {
        if (!condition) {
            fail(message + " — Expected [true] but was [false]");
        }
    }

    public static void assertFalse(final boolean condition, final String message) {
        if (condition) {
            fail(message + " — Expected [false] but was [true]");
        }
    }

    public static void assertEquals(final Object expected, final Object actual, final String message) {
        if (!Objects.equals(expected, actual)) {
            fail(message + " — Expected [" + expected + "] but was [" + actual + "]");
        }
    }

    public static void assertEquals(final double expected, final double actual, final double delta, final String message) {
        if (Math.abs(expected - actual) > delta) {
            fail(message + " — Expected [" + expected + " ±" + delta + "] but was [" + actual + "] (diff: " + Math.abs(expected - actual) + ")");
        }
    }

    public static void assertEquals(final float expected, final float actual, final float delta, final String message) {
        if (Math.abs(expected - actual) > delta) {
            fail(message + " — Expected [" + expected + " ±" + delta + "] but was [" + actual + "] (diff: " + Math.abs(expected - actual) + ")");
        }
    }

    public static void assertInRange(final double value, final double min, final double max, final String message) {
        if (value < min || value > max) {
            fail(message + " — Expected value in range [" + min + " .. " + max + "] but was [" + value + "]");
        }
    }

    public static void assertNotNull(final Object obj, final String message) {
        if (obj == null) {
            fail(message + " — Expected non-null object, but was null");
        }
    }

    public static void assertNull(final Object obj, final String message) {
        if (obj != null) {
            fail(message + " — Expected null object, but was [" + obj + "]");
        }
    }

    public static void fail(final String message) {
        throw new AssertionError(message);
    }
}
