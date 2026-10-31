package dev.gkissel.forgeweave.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A class whose {@link GameTest} methods {@link ForgeweaveGameTestRegistrar} registers, found through
 * the mod file's annotation scan. {@link #value} is the namespace the tests and their templates live
 * in. Replaces NeoForge's own {@code GameTestHolder}, which went away with vanilla's annotation-driven
 * GameTests in 1.21.5.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface GameTestHolder {
    String value();
}
