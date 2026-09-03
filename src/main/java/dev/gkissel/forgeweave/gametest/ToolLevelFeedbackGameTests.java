package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.sound.ForgeweaveSounds;
import dev.gkissel.forgeweave.tool.ToolLevel;
import dev.gkissel.forgeweave.tool.ToolLeveling;

/**
 * The level-up feedback's dedicated-server half (docs/SCOPE.md M7, D-M7-8; issue #922):
 * {@link ToolLeveling#addXp} firing the chime through a real {@link ServerPlayer} on a running
 * {@code GameTestHelper} server, once per level a grant crosses. The chat line's exact wording and
 * key selection is unit-tested instead ({@code ToolLevelFeedbackTest}, in the same package as the
 * package-private {@code ToolLevelFeedback} it drives) -- {@link GameTestHelper#makeMockServerPlayerInLevel()}'s
 * mock connection is on no listener the resulting chat packet could be read back from, the same
 * limitation {@code BowReleaseGameTests} already documents for a different packet.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolLevelFeedbackGameTests {

    /**
     * One grant worth several levels (docs/SCOPE.md D-M7-8's "once per level", not once per grant --
     * {@code ToolLevel#plusXp}'s own while loop can cross several in a single {@link
     * ToolLeveling#addXp} call). {@link SoundCapture} observes {@code Level#playSound} regardless of
     * whether the mock connection could deliver the resulting packet to anyone.
     */
    @GameTest(template = "empty")
    public static void levelingUpPlaysTheChimeOncePerLevelCrossed(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        ToolLevel before = ToolLevel.of(pickaxe);

        // A huge grant to guarantee crossing several levels regardless of the loaded config's curve.
        List<SoundCapture.Played> played =
                SoundCapture.playedDuring(helper, () -> ToolLeveling.addXp(pickaxe, 1_000_000, player));

        ToolLevel after = ToolLevel.of(pickaxe);
        int crossed = after.level() - before.level();
        helper.assertTrue(crossed > 0, "expected a 1,000,000 XP grant to cross at least one level, got " + after);

        List<SoundCapture.Played> chimes = played.stream()
                .filter(cue -> cue.sound().value() == ForgeweaveSounds.TOOL_LEVEL_UP.get())
                .toList();
        helper.assertTrue(chimes.size() == crossed,
                "expected one chime per level crossed (" + crossed + "), got " + chimes.size());

        helper.succeed();
    }

    /** Whether any tooltip line is a translatable component with exactly this key at its root. */
    private static boolean hasLine(List<Component> lines, String key) {
        return lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key));
    }

    /**
     * D-M7-9's gate line: {@code maximumLevels = N} stops the tool at exactly N, and the XP line
     * disappears from its tooltip there because there is no next level to count toward. Both halves
     * need a loaded {@code SERVER} config spec -- {@code ForgeweaveConfig.maximumLevels()} answers
     * "no cap" with none loaded, which is every unit test -- so this is the only place the capped
     * tooltip can be exercised at all. The lines are matched on their translation keys rather than
     * their rendered text, which a dedicated server resolves through whatever language happens to be
     * loaded.
     */
    @GameTest(template = "empty")
    public static void aLevelCapStopsTheToolAtTheCapAndHidesTheXpLine(GameTestHelper helper) {
        ForgeweaveConfig.MAXIMUM_LEVELS.set(2);
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "wood", "wood");

            ToolLeveling.addXp(pickaxe, 1_000_000, player);

            helper.assertTrue(ToolLevel.of(pickaxe).level() == 2,
                    "a cap of 2 must stop the tool at exactly 2, got " + ToolLevel.of(pickaxe));
            List<Component> lines = pickaxe.getTooltipLines(Item.TooltipContext.of(helper.getLevel()), player,
                    TooltipFlag.NORMAL);
            helper.assertTrue(hasLine(lines, "tooltip.forgeweave.level"),
                    "the level name line still shows at the cap, got " + lines);
            helper.assertFalse(hasLine(lines, "tooltip.forgeweave.xp"),
                    "the XP line must be hidden at the cap, got " + lines);
        } finally {
            ForgeweaveConfig.MAXIMUM_LEVELS.set(ForgeweaveConfig.NO_LEVEL_CAP);
        }
        helper.succeed();
    }

    /** A grant that does not cross a level rings no chime -- the gate is "crossed", not "any XP granted". */
    @GameTest(template = "empty")
    public static void noLevelCrossedPlaysNoChime(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);

        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());

        List<SoundCapture.Played> played = SoundCapture.playedDuring(helper, () -> ToolLeveling.addXp(pickaxe, 1, player));

        boolean anyChime = played.stream().anyMatch(cue -> cue.sound().value() == ForgeweaveSounds.TOOL_LEVEL_UP.get());
        helper.assertTrue(!anyChime, "expected a 1 XP grant to bank without leveling and ring no chime");

        helper.succeed();
    }
}
