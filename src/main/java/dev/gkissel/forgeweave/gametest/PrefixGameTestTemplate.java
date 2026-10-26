package dev.gkissel.forgeweave.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Kept from the 1.21.1 suite, where {@code @PrefixGameTestTemplate(false)} stopped NeoForge prefixing
 * a test's template with its class name. {@link ForgeweaveGameTestRegistrar} never prefixes, so every
 * holder's {@code false} is now just the default; the annotation stays so the 177 holder classes did
 * not need touching beyond their imports.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface PrefixGameTestTemplate {
    boolean value() default true;
}
