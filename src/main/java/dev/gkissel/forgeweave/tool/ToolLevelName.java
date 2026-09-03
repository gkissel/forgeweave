package dev.gkissel.forgeweave.tool;

import java.awt.Color;
import java.util.Set;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

/**
 * The tool level ladder's name and its rotating hue (docs/SCOPE.md M7, D-M7-8; issue #922), ported
 * from Tinkers' Tool Leveling's {@code Tooltips#getLevelString}/{@code getRawLevelString}/
 * {@code getLevelColor} (MIT, NOTICE.md).
 *
 * <p>Upstream picks the ladder entry and the four easter-egg levels by asking
 * {@code I18n.canTranslate} at the point of use -- a 1.12 server-side-I18n idiom that does not
 * survive 1.21.1: the server only ever emits a {@code Component.translatable}, and only the client
 * resolves it, so the server has nothing to probe. Forgeweave makes both sets explicit Java
 * constants instead (D-M7-8): {@link #LADDER_SIZE} for the 0-11 wrap and {@link #EASTER_EGGS} for
 * the four levels that never wrap. The ladder text, the easter eggs and the hue formula are ported
 * verbatim per maintainer decision -- the easter eggs name upstream contributors (MoxieGrrl, boni,
 * Jadedcat), which is attribution, not an oversight, so they are not renamed or "localised".
 */
public final class ToolLevelName {
    private ToolLevelName() {}

    /** The 0-11 adjective ladder's length; a level past it wraps, one "+" per lap (level 12 = "Like new+"). */
    public static final int LADDER_SIZE = 12;

    /** Levels with their own name outside the 0-11 wrap -- upstream's {@code tooltip.level.19/42/66/99}. */
    private static final Set<Integer> EASTER_EGGS = Set.of(19, 42, 66, 99);

    /**
     * {@code Like new+}: the ladder name for {@code level}, styled in {@link #color}. Easter-egg
     * levels resolve their own key directly rather than through the wrap, matching upstream's
     * {@code I18n.canTranslate} check running before the modulo fallback.
     */
    public static Component name(int level) {
        MutableComponent name = Component.translatable(nameKey(level));
        int wraps = wrapCount(level);
        if (wraps > 0) {
            name = name.append(Component.literal("+".repeat(wraps)));
        }
        return name.withStyle(Style.EMPTY.withColor(color(level)));
    }

    /** {@code tooltip.forgeweave.level.<n>}: the easter-egg key for an easter-egg level, else {@code level % 12}. */
    static String nameKey(int level) {
        int index = EASTER_EGGS.contains(level) ? level : Math.floorMod(level, LADDER_SIZE);
        return "tooltip.forgeweave.level." + index;
    }

    /** How many "+" the wrap adds -- always 0 for an easter-egg level, which never wraps. */
    static int wrapCount(int level) {
        return EASTER_EGGS.contains(level) ? 0 : Math.floorDiv(level, LADDER_SIZE);
    }

    /** Upstream {@code getLevelColor}: a full lap of hue every 12 levels, at fixed saturation/value. */
    public static TextColor color(int level) {
        return TextColor.fromRgb(Color.HSBtoRGB(hue(level), 0.75F, 0.8F) & 0xFFFFFF);
    }

    /** {@code frac(0.277777 x level)} -- upstream's exact multiplier, kept rather than simplified to 1/12. */
    static float hue(int level) {
        float raw = 0.277777F * level;
        return raw - (int) raw;
    }
}
