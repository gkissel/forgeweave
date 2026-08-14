package dev.gkissel.forgeweave.data;

import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The Part Builder and Tool Station drop themselves, keeping their inventory contents dropped
 * separately on removal. They also copy their {@link ForgeweaveDataComponents#TEXTURE} component
 * from the block entity onto the dropped item (issue #43: breaking a spruce-textured table should
 * still drop a spruce-textured table item, not reset to the default), the same
 * {@code minecraft:copy_components} mechanism vanilla uses for nameable-block-entity drops -- the
 * block entities' {@code collectImplicitComponents} overrides expose the stored wood as that
 * component so this loot function can find it via {@code LootContextParams.BLOCK_ENTITY}.
 */
public class ForgeweaveBlockLootSubProvider extends BlockLootSubProvider {
    public ForgeweaveBlockLootSubProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        add(ForgeweaveBlocks.PART_BUILDER.get(), retexturedTableDrop(ForgeweaveBlocks.PART_BUILDER.get()));
        add(ForgeweaveBlocks.TOOL_STATION.get(), retexturedTableDrop(ForgeweaveBlocks.TOOL_STATION.get()));
        add(ForgeweaveBlocks.TOOL_FORGE.get(), retexturedTableDrop(ForgeweaveBlocks.TOOL_FORGE.get()));
        add(ForgeweaveBlocks.CRAFTING_STATION.get(), retexturedTableDrop(ForgeweaveBlocks.CRAFTING_STATION.get()));
        add(ForgeweaveBlocks.STENCIL_TABLE.get(), retexturedTableDrop(ForgeweaveBlocks.STENCIL_TABLE.get()));

        // The Pattern Chest and Part Chest (issue #66) carry no TEXTURE component, so they use a
        // plain self-drop rather than retexturedTableDrop's component-copying loot function.
        dropSelf(ForgeweaveBlocks.PATTERN_CHEST.get());
        dropSelf(ForgeweaveBlocks.PART_CHEST.get());

        // Grout (docs/SCOPE.md M2 issue #93; issue #129): drops itself, matching upstream's BlockSoil
        // (no getDrops override for the GROUT type).
        dropSelf(ForgeweaveBlocks.GROUT.get());

        // #339 -- the slimy muds, same BlockSoil self-drop as grout.
        dropSelf(ForgeweaveBlocks.SLIMY_MUD_GREEN.get());
        dropSelf(ForgeweaveBlocks.SLIMY_MUD_MAGMA.get());

        // The seared brick block family (docs/SCOPE.md M2 issue #93): plain decorative blocks, no
        // tool-tier gating (ForgeweaveBlocks javadoc), so every variant just drops itself -- matching
        // upstream 1.12's own BlockSeared, which never overrides loot ("Safe for decoration").
        dropSelf(ForgeweaveBlocks.SEARED_STONE.get());
        dropSelf(ForgeweaveBlocks.SEARED_COBBLESTONE.get());
        dropSelf(ForgeweaveBlocks.SEARED_PAVER.get());
        dropSelf(ForgeweaveBlocks.SEARED_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_CRACKED_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_FANCY_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_SQUARE_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_SMALL_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_ROAD.get());
        dropSelf(ForgeweaveBlocks.SEARED_TILE.get());
        dropSelf(ForgeweaveBlocks.SEARED_CREEPER.get());

        // Seared stairs + slabs (docs/SCOPE.md M3.4-5 issue #274): stairs always drop themselves
        // (upstream's BlockSearedStairs overrides no loot either); slabs use vanilla's own
        // createSlabItemTable, which drops 2 from the double-slab state, matching every vanilla slab.
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_STONE.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_COBBLESTONE.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_PAVER.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_CRACKED_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_FANCY_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_SQUARE_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_TRIANGLE_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_SMALL_BRICKS.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_ROAD.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_TILE.get());
        dropSelf(ForgeweaveBlocks.SEARED_STAIRS_CREEPER.get());

        add(ForgeweaveBlocks.SEARED_SLAB_STONE.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_STONE.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_COBBLESTONE.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_COBBLESTONE.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_PAVER.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_PAVER.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_BRICKS.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_BRICKS.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_CRACKED_BRICKS.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_CRACKED_BRICKS.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_FANCY_BRICKS.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_FANCY_BRICKS.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_SQUARE_BRICKS.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_SQUARE_BRICKS.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_TRIANGLE_BRICKS.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_TRIANGLE_BRICKS.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_SMALL_BRICKS.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_SMALL_BRICKS.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_ROAD.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_ROAD.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_TILE.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_TILE.get()));
        add(ForgeweaveBlocks.SEARED_SLAB_CREEPER.get(), createSlabItemTable(ForgeweaveBlocks.SEARED_SLAB_CREEPER.get()));

        // The smeltery multiblock (docs/SCOPE.md M2 issue #95). The cores and the drain carry no
        // component worth keeping, but a broken tank keeps whatever fluid it held -- upstream 1.12's
        // BlockTank#getDrops writes the same thing onto the dropped stack (NOTICE.md).
        dropSelf(ForgeweaveBlocks.STANDARD_CORE.get());
        dropSelf(ForgeweaveBlocks.NETHER_CORE.get());
        dropSelf(ForgeweaveBlocks.SEARED_DRAIN.get());
        // #277 -- the duct's filter item is dropped by SearedDuctBlock#onRemove, not by this table.
        dropSelf(ForgeweaveBlocks.SEARED_DUCT.get());
        dropSelf(ForgeweaveBlocks.SEARED_CHUTE.get());
        add(ForgeweaveBlocks.SEARED_TANK.get(), tankDrop(ForgeweaveBlocks.SEARED_TANK.get()));
        add(ForgeweaveBlocks.SEARED_GAUGE.get(), tankDrop(ForgeweaveBlocks.SEARED_GAUGE.get()));
        add(ForgeweaveBlocks.SEARED_WINDOW.get(), tankDrop(ForgeweaveBlocks.SEARED_WINDOW.get()));

        // Plain seared glass (docs/SCOPE.md M3.3 issue #289): no BlockEntity/held state, plain self-drop.
        dropSelf(ForgeweaveBlocks.SEARED_GLASS.get());

        // #100 -- casting (docs/SCOPE.md M2 issue #100). Held items and any in-flight fluid are
        // dropped by the block entity itself (CastingBlock#onRemove, FaucetBlockEntity), so the
        // blocks themselves just drop as blocks.
        dropSelf(ForgeweaveBlocks.CASTING_TABLE.get());
        dropSelf(ForgeweaveBlocks.CASTING_BASIN.get());
        dropSelf(ForgeweaveBlocks.FAUCET.get());

        // #104 -- cobalt + ardite nether ore (docs/SCOPE.md M2 issue #104): always drops one raw
        // item, matching upstream 1.12's BlockOre (no getDrops override there -- unconditional
        // self-drop, no fortune scaling). Forgeweave's adaptation (#103) is dropping the raw-ore item
        // rather than the ore block itself, and SCOPE.md's "no separate silk-touch yield axis" means
        // that stays true even under Silk Touch -- createOreDrop's silk-touch/fortune split is
        // deliberately not used here.
        add(ForgeweaveBlocks.COBALT_ORE.get(), oreDrop(ForgeweaveBlocks.COBALT_ORE.get(), ForgeweaveItems.RAW_COBALT.get()));
        add(ForgeweaveBlocks.ARDITE_ORE.get(), oreDrop(ForgeweaveBlocks.ARDITE_ORE.get(), ForgeweaveItems.RAW_ARDITE.get()));

        // #206 -- storage blocks for cobalt/ardite/manyullyn/rose gold: plain self-drops, matching
        // vanilla's own iron/gold/copper/netherite storage blocks (no component worth keeping).
        dropSelf(ForgeweaveBlocks.COBALT_BLOCK.get());
        dropSelf(ForgeweaveBlocks.ARDITE_BLOCK.get());
        dropSelf(ForgeweaveBlocks.MANYULLYN_BLOCK.get());
        dropSelf(ForgeweaveBlocks.ROSE_GOLD_BLOCK.get());
        dropSelf(ForgeweaveBlocks.STEEL_BLOCK.get());
        dropSelf(ForgeweaveBlocks.KNIGHTSLIME_BLOCK.get()); // #232

        // #233 -- pig iron's storage block and firewood: plain self-drops (upstream's BlockMetal and
        // BlockFirewood override no loot either).
        dropSelf(ForgeweaveBlocks.PIG_IRON_BLOCK.get());
        dropSelf(ForgeweaveBlocks.FIREWOOD.get());

        dropSelf(ForgeweaveBlocks.AMETHYST_BRONZE_BLOCK.get());

        // #275 -- clear glass and its 16 clear stained glass colors: plain self-drops, matching
        // upstream's BlockClearGlass/BlockClearStainedGlass (neither overrides loot).
        dropSelf(ForgeweaveBlocks.CLEAR_GLASS.get());
        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            dropSelf(color.block().get());
        }
    }

    private LootTable.Builder oreDrop(Block block, Item item) {
        return LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(item))));
    }

    private LootTable.Builder tankDrop(Block block) {
        return LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(block)
                        .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                                .include(ForgeweaveDataComponents.FLUID_CONTENT.get())))));
    }

    private LootTable.Builder retexturedTableDrop(Block block) {
        return LootTable.lootTable().withPool(applyExplosionCondition(block, LootPool.lootPool()
                .setRolls(ConstantValue.exactly(1.0F))
                .add(LootItem.lootTableItem(block)
                        .apply(CopyComponentsFunction.copyComponents(CopyComponentsFunction.Source.BLOCK_ENTITY)
                                .include(ForgeweaveDataComponents.TEXTURE.get())))));
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return ForgeweaveBlocks.BLOCKS.getEntries().stream().map(entry -> (Block) entry.get())::iterator;
    }
}
