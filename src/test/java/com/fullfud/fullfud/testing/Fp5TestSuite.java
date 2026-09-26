package com.fullfud.fullfud.testing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadata annotation for an FP-5 test suite class.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Fp5TestSuite {
    /**
     * Name of the test suite.
     */
    String name();

    /**
     * Scope of features covered.
     */
    String[] features() default {};

    /**
     * Milestone association (M1, M2, M3, M4).
     */
    String milestone() default "";
}
