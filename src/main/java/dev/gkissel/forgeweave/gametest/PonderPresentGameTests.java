package dev.gkissel.forgeweave.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.ponder.ForgeweavePonderHint;

/**
 * The GameTests that need Ponder on the GameTest server's classpath, split out of
 * {@link AdvancementGameTests} so build.gradle's COMPAT_DISABLED can leave them out of the compile
 * while Ponder has no build for the current Minecraft (26.2). The test ids moved with them.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PonderPresentGameTests {

    /**
     * The install-Ponder chat hint ({@code ForgeweavePonderHint}) must stay silent when Ponder is
     * present -- and since issue #664 embeds Ponder jar-in-jar (and puts it on this GameTest
     * server's classpath), presence is the shipped configuration. This replaces issue #110's
     * shown-only-once test, whose "Ponder absent" premise no longer exists on any runtime this
     * suite can build: the hint survives only as the degraded fallback for a repackaged install
     * that strips the embedded jars, and its persisted-flag bookkeeping is unreachable here.
     */
    @GameTest(template = "smeltery")
    public static void ponderHintStaysSilentWhenPonderIsPresent(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        helper.assertFalse(hintShown(player), "expected a fresh player to not have seen the hint yet");

        helper.useBlock(SmelteryGameTests.CORE_POS, player);
        helper.assertFalse(hintShown(player),
                "expected the hint to stay un-recorded with Ponder loaded (its scenes carry the tutorial)");

        // A direct call, same as any further controller click, must stay silent too.
        ForgeweavePonderHint.maybeShow(player);
        helper.assertFalse(hintShown(player), "expected a direct maybeShow call to remain a no-op with Ponder loaded");
        helper.succeed();
    }

    private static boolean hintShown(ServerPlayer player) {
        return player.getPersistentData().getCompoundOrEmpty("PlayerPersisted").getBooleanOr("forgeweave_ponder_hint_shown", false);
    }

    private PonderPresentGameTests() {}
}
