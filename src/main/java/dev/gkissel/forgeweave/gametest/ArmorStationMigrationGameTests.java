package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlockEntities;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * Issue #1006's save compatibility: a world saved while the Armor Station (issue #782) still existed
 * has to keep loading, and the items inside a placed one have to survive. The maintainer's call was
 * that such a block becomes a Tool Station rather than staying behind inert, and that the mechanism
 * should be the platform's own rather than a hand-written chunk-load migrator.
 *
 * <p>So the conversion is two registry aliases, {@code ForgeweaveBlocks}' and
 * {@code ForgeweaveItems}', and there is nothing to migrate: NeoForge patches
 * {@code MappedRegistry#get} and {@code #getHolder} to resolve aliases, which is what every read of
 * a saved id goes through, so a chunk palette's {@code forgeweave:armor_station} comes back as the
 * Tool Station and an {@code ItemStack} of that id comes back as the Tool Station's item. The
 * inventory needs no alias at all: all three blocks always shared one block entity type, saved under
 * {@code forgeweave:tool_station}, so the container tag a pre-#1006 world holds is already this
 * block entity's own.
 *
 * <p>The fixtures below are hand-built tags rather than a round trip through today's save path, so
 * they say what an old region file actually contains and cannot drift with the code they test.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArmorStationMigrationGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    /** The block id a pre-#1006 world wrote into its chunk palette for a placed Armor Station. */
    private static final String OLD_ID = "forgeweave:armor_station";

    /** The palette entry itself: the retired block's id plus the facing every table block carries. */
    private static CompoundTag savedBlockState() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", OLD_ID);
        CompoundTag properties = new CompoundTag();
        properties.putString("facing", "north");
        tag.put("Properties", properties);
        return tag;
    }

    /**
     * The block entity tag beside it: {@code forgeweave:tool_station}, which is what the Armor
     * Station always saved, with one part in the head slot. {@code "inventory"} is
     * {@code ToolStationBlockEntity}'s own key.
     */
    private static CompoundTag savedBlockEntity(HolderLookup.Provider registries, ItemStack head) {
        SimpleContainer contents = new SimpleContainer(ToolStationMenu.CONTAINER_SLOTS);
        contents.setItem(ToolStationMenu.HEAD_SLOT, head);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "forgeweave:tool_station");
        tag.put("inventory", contents.createTag(registries));
        return tag;
    }

    @GameTest(template = "empty")
    public static void anOldArmorStationLoadsAsAToolStationKeepingItsItems(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack head = ToolAssembly.part(ForgeweaveItems.PART_PLATING_CHESTPLATE.get(), "iron");

        BlockState loaded = NbtUtils.readBlockState(
                helper.getLevel().holderLookup(Registries.BLOCK), savedBlockState());
        helper.assertTrue(loaded.is(ForgeweaveBlocks.TOOL_STATION.get()),
                OLD_ID + " must read back as a Tool Station, got " + loaded);
        helper.assertTrue(ForgeweaveBlockEntities.TOOL_STATION.get().isValid(loaded),
                "and the shared block entity type must accept that state, or the chunk would drop it");

        BlockEntity reloaded = BlockEntity.loadStatic(helper.absolutePos(STATION), loaded,
                savedBlockEntity(registries, head), registries);
        helper.assertTrue(reloaded instanceof ToolStationBlockEntity,
                "the saved block entity must load, got " + reloaded);
        ToolStationBlockEntity station = (ToolStationBlockEntity) reloaded;
        ItemStack kept = station.container().getItem(ToolStationMenu.HEAD_SLOT);
        helper.assertTrue(ItemStack.isSameItemSameComponents(kept, head),
                "the item inside must survive the conversion, got " + kept);
        helper.assertFalse(station.isForge(), "and the converted block is a Tool Station, not a forge");

        // The block really is placeable and openable as a Tool Station, not merely decodable.
        helper.setBlock(STATION, loaded);
        ToolStationBlockEntity placed = helper.getBlockEntity(STATION);
        helper.assertTrue(placed != null, "the converted state must still carry a Tool Station block entity");
        helper.succeed();
    }

    /** The other half of the maintainer's call: the item in an old inventory or chest converts too. */
    @GameTest(template = "empty")
    public static void anOldArmorStationItemLoadsAsAToolStationItem(GameTestHelper helper) {
        CompoundTag saved = new CompoundTag();
        saved.putString("id", OLD_ID);
        saved.putInt("count", 2);

        ItemStack stack = ItemStack.parse(helper.getLevel().registryAccess(), saved).orElse(ItemStack.EMPTY);
        helper.assertTrue(stack.is(ForgeweaveItems.TOOL_STATION.get()),
                OLD_ID + " in an old inventory must read back as the Tool Station item, got " + stack);
        helper.assertTrue(stack.getCount() == 2, "and keep its count, got " + stack.getCount());
        helper.succeed();
    }

}
