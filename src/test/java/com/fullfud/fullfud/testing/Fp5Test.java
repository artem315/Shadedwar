package com.fullfud.fullfud.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an automated test case for FP-5 Flamingo validation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Fp5Test {
    /**
     * Tier classification (1 = Feature, 2 = Boundary, 3 = Cross-Feature, 4 = Scenario).
     */
    int tier() default 1;

    /**
     * Feature IDs covered (e.g. "F1", "F2", "F3").
     */
    String[] features() default {};

    /**
     * Brief human-readable description of what this test verifies.
     */
    String description() default "";
}
