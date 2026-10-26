package dev.gkissel.forgeweave.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@code public static void name(GameTestHelper)} method in a {@link GameTestHolder} class as
 * a GameTest. Minecraft 1.21.5 dropped vanilla's annotation-driven GameTests for a registry of test
 * functions and test instances; {@link ForgeweaveGameTestRegistrar} turns each annotated method back
 * into one of each, so the suite keeps the attributes it used on 1.21.1.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    /** The structure the test runs in, under the holder's namespace ({@code empty} or {@code smeltery}). */
    String template();

    /** Ticks the test may run before it times out. 100, as on 1.21.1. */
    int timeoutTicks() default 100;

    /** Ticks to wait after placing the structure, before the test function runs. */
    int setupTicks() default 0;

    /** Whether a failure fails the run. */
    boolean required() default true;
}
