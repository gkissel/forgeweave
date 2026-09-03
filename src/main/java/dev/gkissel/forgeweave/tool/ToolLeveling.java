package dev.gkissel.forgeweave.tool;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * The tool leveling curve and the one API every XP source calls (docs/SCOPE.md M7, D-M7-3/D-M7-5/
 * D-M7-9; issue #918). Ported from Tinkers' Tool Leveling's {@code ModToolLeveling#getXpForLevelup}
 * and {@code Config#canLevelUp} (MIT).
 *
 * <p>No XP is granted anywhere by this class. {@link #addXp} is the seam the mining, melee, ranged,
 * utility and armor grants land on in the later M7 issues. It also fires the level-up feedback
 * (chat line and chime, {@link ToolLevelFeedback}) once per level a grant crosses -- the tooltip
 * lines are the one piece that hangs off the stack's state instead, since they render on demand
 * rather than at the moment XP is granted; see {@link dev.gkissel.forgeweave.item.ToolTooltip}.
 */
public final class ToolLeveling {
    private ToolLeveling() {}

    /**
     * What crossing from {@code level} to {@code level + 1} costs. Ported exactly, shape at the
     * bottom included: <b>levels 0 to 1 and 1 to 2 both cost {@code baseXp}</b>, 2 to 3 costs
     * {@code baseXp x 2} and 3 to 4 costs {@code baseXp x 4}. That is upstream's real behavior, not a
     * transcription slip, and {@code ToolLevelingTest} pins it so a later cleanup cannot quietly
     * change the progression.
     *
     * <p>Written as a loop over upstream's recursion, and accumulated in a {@code long} clamped at
     * {@link Integer#MAX_VALUE}: the cost doubles per level at the default multiplier, so an
     * uncapped tool would otherwise overflow into a negative cost around level 25 and start leveling
     * on every point of XP. Each step still truncates to a whole number the way upstream's per-call
     * {@code int} cast does.
     */
    public static int xpForLevelup(int level, int baseXp, double levelMultiplier) {
        long xp = baseXp;
        for (int step = 2; step <= level; step++) {
            xp = (long) (xp * levelMultiplier);
            if (xp >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) xp;
    }

    /**
     * Whether a tool at {@code level} may still level. Upstream's {@code Config#canLevelUp} reads
     * {@code maximumLevels >= currentLevel}, so a cap of N lets a tool reach N + 1; Forgeweave
     * corrects that to {@code level < cap} (D-M7-9), a deviation that is unobservable at the default
     * cap of -1.
     */
    public static boolean canLevelUp(int level, int maximumLevels) {
        return maximumLevels <= 0 || level < maximumLevels;
    }

    /**
     * What this tool's first level costs: the configured {@code defaultBaseXP}, times the tool's own
     * {@link ToolConstants.Entry#baseXpMultiplier()} -- {@link ToolConstants#AOE_BASE_XP} for the
     * area-of-effect shapes, 1 for everything else (D-M7-5). A stack with no assembly row at all (a
     * part item, a vanilla item) takes the plain default, which is also what the pickaxe, shovel and
     * hatchet get: they predate {@code ToolConstants} and their entries are built inline in
     * {@code ToolAssemblyRecipes} with the default multiplier.
     */
    public static int baseXp(ItemStack stack) {
        int multiplier = ToolAssemblyRecipes.entryFor(stack)
                .map(entry -> entry.constants().baseXpMultiplier())
                .orElse(1);
        return multiplier * ForgeweaveConfig.defaultBaseXp();
    }

    /**
     * Adds {@code amount} XP to {@code stack} and applies any levels it buys, each granting one
     * modifier slot. Returns whether a level was crossed, which is what the feedback in M7-5 keys
     * off.
     *
     * <p>Inert when {@code toolLeveling} is off (D-M7-3) and inert past the level cap. Levels
     * already earned are never revoked either way: the earned slot count lives on the stack.
     *
     * @param player the player who earned it -- fed the chat line and chime for each level crossed
     *     (issue #922); {@code null} skips feedback (a null player means no one to tell) but still
     *     applies the XP and any levels it buys
     */
    public static boolean addXp(ItemStack stack, int amount, @Nullable ServerPlayer player) {
        return addXp(stack, amount, player, ForgeweaveConfig.enabled(ForgeweaveConfig.TOOL_LEVELING));
    }

    /**
     * {@link #addXp} with the {@code toolLeveling} flag passed in rather than read, so a unit test
     * can exercise the off path that a {@code SERVER} config spec no test environment loads would
     * otherwise always answer "on" for.
     */
    static boolean addXp(ItemStack stack, int amount, @Nullable ServerPlayer player, boolean levelingEnabled) {
        if (!levelingEnabled) {
            return false;
        }
        ToolLevel before = ToolLevel.of(stack);
        ToolLevel after = before.plusXp(amount, baseXp(stack), ForgeweaveConfig.levelMultiplier(),
                ForgeweaveConfig.maximumLevels());
        if (after.equals(before)) {
            return false;
        }
        stack.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), after);
        boolean leveledUp = after.level() > before.level();
        // Issue #922: once per level a single grant crosses, not once per grant -- a burst of XP
        // that buys two levels in one call (ToolLevel#plusXp's own while loop) rings and prints
        // twice, matching "once per level" rather than "once per addXp call".
        if (leveledUp && player != null) {
            for (int level = before.level() + 1; level <= after.level(); level++) {
                ToolLevelFeedback.onLevelUp(stack, level, player);
            }
        }
        return leveledUp;
    }
}
