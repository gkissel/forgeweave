package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;

/**
 * Pins the ladder, the wrap and the hue against Tinkers' Tool Leveling's {@code Tooltips}
 * (tool-leveling-1.12, pinned commit in NOTICE.md; issue #922, D-M7-8): the four values a naive port
 * of upstream's {@code I18n.canTranslate} probing would have gotten wrong on a dedicated server are
 * exactly the ones asserted here as plain Java constants instead.
 */
class ToolLevelNameTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String key(Component component) {
        assertTrue(component.getContents() instanceof TranslatableContents,
                "expected a translatable component, got " + component.getContents());
        return ((TranslatableContents) component.getContents()).getKey();
    }

    private static String wrapSuffix(Component component) {
        StringBuilder plusses = new StringBuilder();
        for (Component sibling : component.getSiblings()) {
            assertTrue(sibling.getContents() instanceof PlainTextContents,
                    "expected a literal '+' sibling, got " + sibling.getContents());
            plusses.append(((PlainTextContents) sibling.getContents()).text());
        }
        return plusses.toString();
    }

    /** The ladder resolves 0-11 directly -- no wrap, no easter egg, one key per level. */
    @Test
    void theLadderResolvesZeroToElevenDirectly() {
        String[] ladder = {"tooltip.forgeweave.level.0", "tooltip.forgeweave.level.1", "tooltip.forgeweave.level.2",
                "tooltip.forgeweave.level.3", "tooltip.forgeweave.level.4", "tooltip.forgeweave.level.5",
                "tooltip.forgeweave.level.6", "tooltip.forgeweave.level.7", "tooltip.forgeweave.level.8",
                "tooltip.forgeweave.level.9", "tooltip.forgeweave.level.10", "tooltip.forgeweave.level.11"};
        for (int level = 0; level < ladder.length; level++) {
            assertEquals(ladder[level], key(ToolLevelName.name(level)), "level " + level);
            assertEquals("", wrapSuffix(ToolLevelName.name(level)), "level " + level + " must not wrap");
        }
    }

    /** Level 12 wraps once: the level-0 key ("Like new") plus a single "+". */
    @Test
    void levelTwelveWrapsOnceToLikeNewPlus() {
        Component name = ToolLevelName.name(12);
        assertEquals("tooltip.forgeweave.level.0", key(name));
        assertEquals("+", wrapSuffix(name));
    }

    /** Level 24 wraps twice: two laps of the ladder, two "+". */
    @Test
    void levelTwentyFourWrapsTwice() {
        Component name = ToolLevelName.name(24);
        assertEquals("tooltip.forgeweave.level.0", key(name));
        assertEquals("++", wrapSuffix(name));
    }

    /** 19/42/66/99 hit their own key directly, never the {@code level % 12} wrap path. */
    @Test
    void theEasterEggLevelsHitTheirOwnKeyNotTheWrapPath() {
        assertEquals("tooltip.forgeweave.level.19", key(ToolLevelName.name(19)));
        assertEquals("tooltip.forgeweave.level.42", key(ToolLevelName.name(42)));
        assertEquals("tooltip.forgeweave.level.66", key(ToolLevelName.name(66)));
        assertEquals("tooltip.forgeweave.level.99", key(ToolLevelName.name(99)));

        // None of the four wrap, even though e.g. 42 % 12 = 6 would otherwise look plausible.
        assertEquals("", wrapSuffix(ToolLevelName.name(19)));
        assertEquals("", wrapSuffix(ToolLevelName.name(42)));
        assertEquals("", wrapSuffix(ToolLevelName.name(66)));
        assertEquals("", wrapSuffix(ToolLevelName.name(99)));
    }

    /** Upstream's exact multiplier, {@code frac(0.277777 x level)}, for a spread of levels. */
    @Test
    void theHueIsTheFractionalPartOfPoint277777TimesLevel() {
        assertEquals(0.0F, ToolLevelName.hue(0), 1e-6F);
        assertEquals(frac(0.277777F * 1), ToolLevelName.hue(1), 1e-6F);
        assertEquals(frac(0.277777F * 5), ToolLevelName.hue(5), 1e-6F);
        assertEquals(frac(0.277777F * 12), ToolLevelName.hue(12), 1e-6F);
        assertEquals(frac(0.277777F * 42), ToolLevelName.hue(42), 1e-6F);
        assertEquals(frac(0.277777F * 100), ToolLevelName.hue(100), 1e-6F);
    }

    /** The colour is a full lap of hue every level 0-11, wrapping identically to the name's own ladder. */
    @Test
    void theColorMatchesTheHueDirectly() {
        TextColor expected = TextColor.fromRgb(java.awt.Color.HSBtoRGB(ToolLevelName.hue(7), 0.75F, 0.8F) & 0xFFFFFF);
        assertEquals(expected, ToolLevelName.color(7));
    }

    private static float frac(float value) {
        return value - (int) value;
    }
}
