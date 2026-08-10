package dev.gkissel.forgeweave.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Forgeweave's blocks: the Part Builder (docs/SCOPE.md M1 issue #9), Tool Station (issue #10),
 * Crafting Station (issue #40), Stencil Table (issue #44), the Pattern Chest/Part Chest
 * (issue #66), and the seared brick block family (docs/SCOPE.md M2 issue #93).
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

    public static final DeferredBlock<ChestBlock> PATTERN_CHEST = BLOCKS.register("pattern_chest",
            () -> new ChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD), ChestKind.PATTERN));

    public static final DeferredBlock<ChestBlock> PART_CHEST = BLOCKS.register("part_chest",
            () -> new ChestBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5F)
                    .sound(SoundType.WOOD), ChestKind.PART));

    // The seared brick block family (docs/SCOPE.md M2 issue #93): the 12 variants of upstream
    // 1.12's BlockSeared (BlockSeared.SearedType, NOTICE.md), each split into its own plain block
    // rather than upstream's single PropertyEnum blockstate -- Forgeweave has no smeltery-structure
    // logic yet (issue #95), so these are decorative cubes only: no BlockEntity, no tool-tier
    // gating, matching how Part Builder/Tool Station also leave tool-tier ungated (issue #9). Strength
    // and sound are ported from BlockSeared's constructor.
    public static final DeferredBlock<Block> SEARED_STONE = searedBlock("seared_stone");
    public static final DeferredBlock<Block> SEARED_COBBLESTONE = searedBlock("seared_cobblestone");
    public static final DeferredBlock<Block> SEARED_PAVER = searedBlock("seared_paver");
    public static final DeferredBlock<Block> SEARED_BRICKS = searedBlock("seared_bricks");
    public static final DeferredBlock<Block> SEARED_CRACKED_BRICKS = searedBlock("seared_cracked_bricks");
    public static final DeferredBlock<Block> SEARED_FANCY_BRICKS = searedBlock("seared_fancy_bricks");
    public static final DeferredBlock<Block> SEARED_SQUARE_BRICKS = searedBlock("seared_square_bricks");
    public static final DeferredBlock<Block> SEARED_TRIANGLE_BRICKS = searedBlock("seared_triangle_bricks");
    public static final DeferredBlock<Block> SEARED_SMALL_BRICKS = searedBlock("seared_small_bricks");
    public static final DeferredBlock<Block> SEARED_ROAD = searedBlock("seared_road");
    public static final DeferredBlock<Block> SEARED_TILE = searedBlock("seared_tile");
    public static final DeferredBlock<Block> SEARED_CREEPER = searedBlock("seared_creeper");

    private static DeferredBlock<Block> searedBlock(String name) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(3.0F, 20.0F)
                .sound(SoundType.METAL));
    }

    private ForgeweaveBlocks() {}
}
