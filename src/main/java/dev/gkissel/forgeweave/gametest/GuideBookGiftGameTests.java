package dev.gkissel.forgeweave.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.GuideBookGift;

/**
 * Parity audit T13, issue #445: upstream 1.12's {@code PlayerDataEvents#onPlayerLoggedIn}, given a
 * once-per-player flag ({@link GuideBookGift}). {@code GameTestHelper#makeMockServerPlayerInLevel}
 * calls the real {@code PlayerList#placeNewPlayer}, which fires a genuine
 * {@code PlayerEvent.PlayerLoggedInEvent} -- so the grant wired in {@code Forgeweave}'s constructor
 * already runs by the time the helper returns, and every test below observes that real event path
 * rather than calling {@link GuideBookGift#maybeGrant} to simulate it.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class GuideBookGiftGameTests {

    @GameTest(template = "empty")
    public static void aFreshPlayerReceivesTheGuideBookOnFirstLoginExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(hasGuideBook(player),
                "expected the real PlayerLoggedInEvent fired by makeMockServerPlayerInLevel to grant the guide book");

        player.getInventory().clearContent();
        GuideBookGift.maybeGrant(player);
        helper.assertFalse(hasGuideBook(player),
                "expected a second grant attempt to stay a no-op rather than grant a second book");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void spawnWithBookOffGrantsNoBookOnLogin(GameTestHelper helper) {
        boolean previous = ForgeweaveConfig.SPAWN_WITH_BOOK.get();
        ForgeweaveConfig.SPAWN_WITH_BOOK.set(false);
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            helper.assertFalse(hasGuideBook(player),
                    "expected spawnWithBook=false to grant no book on first login");
        } finally {
            ForgeweaveConfig.SPAWN_WITH_BOOK.set(previous);
        }
        helper.succeed();
    }

    private static boolean hasGuideBook(ServerPlayer player) {
        return player.getInventory().contains(new ItemStack(ForgeweaveItems.GUIDE_BOOK.get()));
    }

    private GuideBookGiftGameTests() {}
}
