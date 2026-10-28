package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;


import dev.gkissel.forgeweave.client.book.BookGeometry;
import dev.gkissel.forgeweave.client.book.BookLayout;

/**
 * Playtest 1.0.0 line 98: the cover subtitle ran off the cover and the index labels ran into each
 * other. {@code BookPaginationTest} already keeps text from running off the bottom of a leaf; this
 * is the sideways half.
 *
 * <p>Widths come from vanilla's own {@code ascii.png}, measured the way {@code BitmapProvider}
 * does it (the rightmost opaque column, plus one pixel of spacing) with the space provider's 4px
 * advance, so no client is needed and a wider translation fails here too.
 */
class BookTextFitTest {

    private static final int GLYPH = 8;
    private static final int SPACE_ADVANCE = 4;
    private static final int[] ADVANCE = new int[128];
    private static JsonObject lang;

    @BeforeAll
    static void loadGlyphsAndLang() throws IOException {
        // Through the application class loader: 26.x unit tests boot a dedicated server, whose game
        // layer hides the client's assets, but the Minecraft jar on the test classpath still has them.
        try (InputStream in = ClassLoader.getSystemResourceAsStream("assets/minecraft/textures/font/ascii.png")) {
            assertNotNull(in, "vanilla ascii.png is not on the test classpath");
            BufferedImage sheet = ImageIO.read(in);
            for (int c = 0; c < ADVANCE.length; c++) {
                int width = 0;
                for (int col = GLYPH - 1; col >= 0 && width == 0; col--) {
                    for (int row = 0; row < GLYPH; row++) {
                        int argb = sheet.getRGB((c % 16) * GLYPH + col, (c / 16) * GLYPH + row);
                        if ((argb >>> 24) != 0) {
                            width = col + 1;
                            break;
                        }
                    }
                }
                ADVANCE[c] = width + 1;
            }
        }
        ADVANCE[' '] = SPACE_ADVANCE;
        lang = JsonParser.parseString(Files.readString(projectRoot()
                .resolve("src/generated/resources/assets/forgeweave/lang/en_us.json"), StandardCharsets.UTF_8))
                .getAsJsonObject();
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static int width(String text) {
        int width = 0;
        for (char c : text.toCharArray()) {
            assertTrue(c < ADVANCE.length, "no ascii glyph for '" + c + "' in " + text);
            width += ADVANCE[c];
        }
        return width;
    }

    @Test
    void theGlyphTableMatchesVanillasKnownAdvances() {
        assertTrue(ADVANCE['a'] == 6 && ADVANCE['i'] == 2 && ADVANCE['l'] == 3 && ADVANCE['t'] == 4,
                "ascii.png was not measured the way vanilla's Font does");
    }

    @Test
    void theCoverSubtitleStaysOnTheCoverAndLeftOfTheNextArrow() {
        String subtitle = lang.get("book.forgeweave.subtitle").getAsString();
        int textWidth = width(subtitle);
        float scale = BookLayout.fitScale(textWidth, BookLayout.COVER_SUBTITLE_W, BookLayout.COVER_SUBTITLE_SCALE);

        // BookScreen#renderCover centres the subtitle 7 scaled pixels right of the screen centre.
        float centre = 7 * scale;
        float left = centre - textWidth * scale / 2;
        float right = centre + textWidth * scale / 2;
        int screenWidth = 427;
        int arrowLeft = BookGeometry.nextArrowX(screenWidth, true) - screenWidth / 2;

        assertTrue(left >= -BookGeometry.PAGE_WIDTH_UNSCALED / 2F,
                "\"" + subtitle + "\" starts " + left + "px from the centre, past the cover's left edge");
        assertTrue(right <= arrowLeft,
                "\"" + subtitle + "\" ends " + right + "px from the centre, under the next arrow at " + arrowLeft);
    }

    @Test
    void everyIndexLabelFitsItsColumn() {
        Map<String, Integer> labels = new TreeMap<>();
        lang.entrySet().stream()
                .filter(entry -> entry.getKey().matches("book\\.forgeweave\\.section\\.[a-z_]+"))
                .forEach(entry -> labels.put(entry.getValue().getAsString(), width(entry.getValue().getAsString())));
        assertFalse(labels.isEmpty(), "non-vacuity: no book.forgeweave.section.* labels in en_us.json");

        for (Map.Entry<String, Integer> label : labels.entrySet()) {
            float drawn = label.getValue() * BookLayout.fitScale(label.getValue(), BookLayout.INDEX_COLUMN_W, 1.0F);
            assertTrue(drawn <= BookLayout.INDEX_COLUMN_W,
                    "\"" + label.getKey() + "\" draws " + drawn + "px wide in a " + BookLayout.INDEX_COLUMN_W
                            + "px index column");
        }
    }
}
