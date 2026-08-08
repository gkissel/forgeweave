package dev.gkissel.forgeweave.block;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Forgeweave's blocks: the Part Builder (docs/SCOPE.md M1 issue #9), Tool Station (issue #10),
 * Crafting Station (issue #40), and Stencil Table (issue #44).
 */
public final class ForgeweaveBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Forgeweave.MODID);

    public static final DeferredBlock<PartBuilderBlock> PART_BUILDER = BLOCKS.register("part_builder",
            () -> new PartBuilderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final DeferredBlock<ToolStationBlock> TOOL_STATION = BLOCKS.register("tool_station",
            () -> new ToolStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final DeferredBlock<CraftingStationBlock> CRAFTING_STATION = BLOCKS.register("crafting_station",
            () -> new CraftingStationBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    public static final DeferredBlock<StencilTableBlock> STENCIL_TABLE = BLOCKS.register("stencil_table",
            () -> new StencilTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD)));

    private ForgeweaveBlocks() {}
}
