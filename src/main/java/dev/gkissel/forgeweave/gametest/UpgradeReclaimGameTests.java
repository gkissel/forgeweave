package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.compat.mekanism.ForgeweaveMekanismCompat;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.api.upgrade.UpgradeHosts;

/**
 * The maintainer rule of 2026-09-18: a partner mod's upgrade that a part swap invalidates comes back
 * to the player as items, never silently lost and never stranded on gear that cannot use it.
 *
 * <p>{@code UpgradeHosts} is the seam and {@code ToolAssemblyRecipes#resolveExchange} is the one place
 * it is asked. These tests drive it with a fixture host rather than a real integration, for the reason
 * {@code MekanismModuleGameTests} gives: {@code runGameTestServer} runs with none of the partner mods
 * present, so the class that answers for Mekanism cannot be classloaded here at all. What is under
 * test is Forgeweave's plumbing -- the ask happens once per swap, the items join the ones the swap
 * already returns, and a swap the host is happy with changes nothing.
 *
 * <p>Registering into a global registry means these must not run concurrently with anything that reads
 * it, so each one registers, asserts and clears inside a single synchronous method, and clears in a
 * {@code finally}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class UpgradeReclaimGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /** A stand-in upgrade item: what a fixture host hands back. */
    private static ItemStack upgrade(int count) {
        return new ItemStack(Items.NETHER_STAR, count);
    }

    /**
     * A swap that invalidates the upgrade returns exactly the items the host names, and leaves the
     * marker the host strips off the tool.
     */
    @GameTest(template = "empty")
    public static void anInvalidatingSwapHandsTheUpgradeBack(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "stone", "wood", "wood");

        // The fixture host does what a real one does: strips its own state off the replacement and
        // says what that state turns back into. The custom name stands in for the component a real
        // host clears, because a partner mod's own component type does not exist on this run.
        UpgradeHosts.register((original, replacement) -> {
            replacement.set(DataComponents.CUSTOM_NAME, Component.literal("stripped"));
            return List.of(upgrade(3));
        });
        try {
            ItemStack swapped = swapHead(helper, player, pickaxe, "iron");
            helper.assertTrue(swapped.is(ForgeweaveItems.TOOL_PICKAXE.get()),
                    "the swap itself must still happen, got " + swapped);
            helper.assertTrue("stripped".equals(swapped.getHoverName().getString()),
                    "the host's edit to the replacement must reach the tool the player takes, got "
                            + swapped.getHoverName().getString());
            helper.assertTrue(player.getInventory().countItem(Items.NETHER_STAR) == 3,
                    "the invalidated upgrade must come back as items, found "
                            + player.getInventory().countItem(Items.NETHER_STAR));
        } finally {
            UpgradeHosts.clear();
        }
        helper.succeed();
    }

    /** A swap the host is happy with returns nothing extra, so a valid upgrade stays where it was. */
    @GameTest(template = "empty")
    public static void aSwapTheHostAcceptsChangesNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "stone", "wood", "wood");

        UpgradeHosts.register((original, replacement) -> List.of());
        try {
            ItemStack swapped = swapHead(helper, player, pickaxe, "iron");
            helper.assertTrue(swapped.is(ForgeweaveItems.TOOL_PICKAXE.get()),
                    "the swap must still happen, got " + swapped);
            helper.assertTrue(player.getInventory().countItem(Items.NETHER_STAR) == 0,
                    "nothing may be handed back for a swap the host accepts, found "
                            + player.getInventory().countItem(Items.NETHER_STAR));
        } finally {
            UpgradeHosts.clear();
        }
        helper.succeed();
    }

    /** With no host registered at all, which is a Forgeweave-only install, a swap is untouched. */
    @GameTest(template = "empty")
    public static void withNoHostASwapIsExactlyWhatItWas(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "stone", "wood", "wood");

        helper.assertTrue(UpgradeHosts.reclaim(pickaxe, pickaxe.copy()).isEmpty(),
                "no host means nothing to reclaim");
        ItemStack swapped = swapHead(helper, player, pickaxe, "iron");
        helper.assertTrue(player.getInventory().countItem(ForgeweaveItems.PART_PICKAXE_HEAD.get()) == 1,
                "the displaced stone head still comes back, which is issue #813's own path");
        helper.assertTrue(swapped.is(ForgeweaveItems.TOOL_PICKAXE.get()), "and the swap happens");
        helper.succeed();
    }

    /**
     * Off is inert, not destructive: the Mekanism host answers nothing while {@code mekanismModules} is
     * off, so a part swap in that state strips no module and hands nothing back. Asserted through the
     * seam rather than through the host itself, which cannot be classloaded on this run.
     */
    @GameTest(template = "empty")
    public static void theToggleOffStateReclaimsNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "atomic_matter_alloy", "wood", "wood");

        helper.assertTrue(ForgeweaveMekanismCompat.isContainerStack(pickaxe),
                "the fixture must be a container stack, or this test proves nothing");

        ForgeweaveConfig.MEKANISM_MODULES.set(false);
        try {
            helper.assertTrue(UpgradeHosts.reclaim(pickaxe, pickaxe.copy()).isEmpty(),
                    "nothing may be reclaimed while mekanismModules is off");
            ItemStack swapped = swapHead(helper, player, pickaxe, "iron");
            helper.assertTrue(player.getInventory().countItem(Items.NETHER_STAR) == 0,
                    "and a swap in that state hands nothing back");
            helper.assertTrue(swapped.is(ForgeweaveItems.TOOL_PICKAXE.get()), "while still swapping");
        } finally {
            ForgeweaveConfig.MEKANISM_MODULES.set(true);
        }
        helper.succeed();
    }

    /** Swaps the pickaxe's head for one of {@code material} at the Tool Station and takes the result. */
    private static ItemStack swapHead(GameTestHelper helper, Player player, ItemStack tool, String material) {
        helper.setBlock(POS, ForgeweaveBlocks.TOOL_STATION.get());
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(POS);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, tool);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT,
                ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), material));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, POS, blockEntity);
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "expected the station to produce a swapped tool"
                + (menu.rejection() == null ? "" : "; it says: " + menu.rejection().message().getString()));
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }
}
