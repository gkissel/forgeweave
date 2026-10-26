package dev.gkissel.forgeweave.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ChestBlock;
import dev.gkissel.forgeweave.block.CraftingStationBlock;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ModifierWorktableBlock;
import dev.gkissel.forgeweave.block.PartBuilderBlock;
import dev.gkissel.forgeweave.block.StencilTableBlock;
import dev.gkissel.forgeweave.block.ToolStationBlock;
import dev.gkissel.forgeweave.block.WoodenHopperBlock;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Playtest checklist 1.0.0, line 20: an idle server pays nothing per tick for Forgeweave's stations
 * or for worn armor. A profiler run cannot be automated here, so these pin the two causes it would
 * show: a block entity ticker on a station or chest, and a worn piece that rewrites its own data
 * components every tick.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class IdleTickCostGameTests {

    /** The station and chest block classes the sweep covers; every one of them has to be found. */
    private static final List<Class<? extends Block>> STATION_CLASSES = List.of(ToolStationBlock.class,
            PartBuilderBlock.class, CraftingStationBlock.class, StencilTableBlock.class,
            ModifierWorktableBlock.class, ChestBlock.class);

    /** How many inventory ticks the worn set gets, 30 seconds of server time at 20 ticks a second. */
    private static final int TICKS = 600;

    /**
     * Playtest checklist 1.0.0, line 20, the stations half. Every registered Forgeweave block of a
     * station or chest class (tool station and tool forge, part builder, crafting station, stencil
     * table, modifier worktable, pattern and part chests, in every wood variant) has to answer a
     * null ticker for the block entity it creates, asked on the GameTest server level. The wooden
     * hopper is the one block that defines a ticker, since it moves items like a vanilla hopper, and
     * it has to keep it.
     */
    @GameTest(template = "empty")
    public static void noStationOrChestBlockHasATicker(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(BlockPos.ZERO);
        Set<Class<? extends Block>> seen = new HashSet<>();
        int swept = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            if (!id.getNamespace().equals(Forgeweave.MODID) || !(block instanceof EntityBlock entityBlock)) {
                continue;
            }
            Class<? extends Block> stationClass = STATION_CLASSES.stream()
                    .filter(type -> type.isInstance(block)).findFirst().orElse(null);
            if (stationClass == null) {
                continue;
            }
            seen.add(stationClass);
            swept++;
            BlockState state = block.defaultBlockState();
            BlockEntity blockEntity = entityBlock.newBlockEntity(pos, state);
            helper.assertTrue(blockEntity != null, id + " must create a block entity");
            helper.assertTrue(entityBlock.getTicker(helper.getLevel(), state, blockEntity.getType()) == null,
                    id + " must not tick its block entity");
        }
        helper.assertTrue(seen.size() == STATION_CLASSES.size(),
                "the sweep must find every station and chest class, found " + seen + " over " + swept + " blocks");

        WoodenHopperBlock hopper = ForgeweaveBlocks.WOODEN_HOPPER.get();
        BlockState hopperState = hopper.defaultBlockState();
        BlockEntity hopperEntity = hopper.newBlockEntity(pos, hopperState);
        helper.assertTrue(hopper.getTicker(helper.getLevel(), hopperState, hopperEntity.getType()) != null,
                "the wooden hopper is the one block that keeps a ticker");
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 20, the worn armor half. A light iron set with no modifiers,
     * built at a real Tool Station and worn by a survival player, gets {@value #TICKS} calls of
     * {@code ArmorPieceItem#inventoryTick} per piece. Every piece's data components have to come
     * out exactly as they went in: a component written each tick is what would make a worn set cost
     * something on an idle server and resend the stack to the client.
     */
    @GameTest(template = "empty")
    public static void aPlainWornSetWritesNothingWhileItTicks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos station = new BlockPos(1, 1, 1);
        for (ToolConstants.Entry entry : ToolConstants.ARMOR) {
            ItemStack piece = ToolAssembly.assembleAt(helper, player, station, ForgeweaveBlocks.TOOL_STATION.get(),
                    ToolAssembly.entryOf(entry), List.of("iron", "iron"));
            helper.assertFalse(piece.isEmpty(), "the station must build a " + entry.id());
            player.setItemSlot(((ArmorPieceItem) piece.getItem()).getEquipmentSlot(), piece);
        }

        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            ItemStack worn = player.getItemBySlot(slot);
            helper.assertFalse(worn.isEmpty(), "the set must cover the " + slot.getName() + " slot");
            ItemStack before = worn.copy();
            for (int tick = 0; tick < TICKS; tick++) {
                worn.inventoryTick(helper.getLevel(), player, slot.getIndex(), false);
            }
            helper.assertTrue(ItemStack.isSameItemSameComponents(before, worn) && before.getCount() == worn.getCount(),
                    "the worn " + slot.getName() + " piece must be unchanged after " + TICKS + " ticks: "
                            + before.getComponents() + " vs " + worn.getComponents());
        }
        helper.succeed();
    }
}
