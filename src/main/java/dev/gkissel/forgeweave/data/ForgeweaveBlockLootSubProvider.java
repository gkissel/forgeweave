package dev.gkissel.forgeweave.data;

import java.util.Set;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.CopyComponentsFunction;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

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
        add(ForgeweaveBlocks.CRAFTING_STATION.get(), retexturedTableDrop(ForgeweaveBlocks.CRAFTING_STATION.get()));
        add(ForgeweaveBlocks.STENCIL_TABLE.get(), retexturedTableDrop(ForgeweaveBlocks.STENCIL_TABLE.get()));
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
