package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
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
