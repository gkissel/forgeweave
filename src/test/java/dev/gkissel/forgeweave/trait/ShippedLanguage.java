package dev.gkissel.forgeweave.trait;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * Renders a component the way a player reads it, against the shipped {@code en_us.json}, so a test
 * can assert the sentence rather than the key and its arguments. Keys the mod does not ship (vanilla's
 * {@code enchantment.level.N} numerals) fall through to whatever language was installed before.
 *
 * <p>{@link Language#inject} is global and the module's tests share a JVM, so the previous language
 * always goes back in a finally.
 */
public final class ShippedLanguage {

    private static final String GENERATED_LANG = "src/generated/resources/assets/forgeweave/lang/en_us.json";

    private static JsonObject lang;

    static JsonObject lang() {
        if (lang == null) {
            Path dir = Path.of("").toAbsolutePath();
            while (dir != null && !Files.exists(dir.resolve("settings.gradle"))) {
                dir = dir.getParent();
            }
            if (dir == null) {
                throw new AssertionError("could not locate the project root (no settings.gradle found)");
            }
            try {
                lang = JsonParser.parseString(Files.readString(dir.resolve(GENERATED_LANG), StandardCharsets.UTF_8))
                        .getAsJsonObject();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return lang;
    }

    public static String render(Component component) {
        JsonObject shipped = lang();
        Language previous = Language.getInstance();
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return shipped.has(key) ? shipped.get(key).getAsString() : previous.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return shipped.has(key) || previous.has(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.EMPTY;
            }
        });
        try {
            return component.getString();
        } finally {
            Language.inject(previous);
        }
    }

    private ShippedLanguage() {}
}
