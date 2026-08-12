package dev.gkissel.forgeweave.block;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.casting.CastingRecipe;

/**
 * Forgeweave's blocks: the Part Builder (docs/SCOPE.md M1 issue #9), Tool Station (issue #10),
 * Crafting Station (issue #40), Stencil Table (issue #44), the Pattern Chest/Part Chest
 * (issue #66), the seared brick block family (docs/SCOPE.md M2 issue #93), and the smeltery
 * multiblock's cores, tanks and drain (issue #95).
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

    // The Tool Forge (docs/SCOPE.md M3 issue #152). Upstream 1.12's BlockToolForge exists as its own
    // block purely for its material: Material.IRON, SoundType.METAL, hardness 2, resistance 10,
    // harvest level ("pickaxe", 0) -- ported verbatim below (NOTICE.md). The harvest level is why
    // this is the first Forgeweave table to carry a mineable/pickaxe tag.
    public static final DeferredBlock<ToolForgeBlock> TOOL_FORGE = BLOCKS.register("tool_forge",
            () -> new ToolForgeBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .destroyTime(2.0F)
                    .explosionResistance(10.0F)
                    .sound(SoundType.METAL)));

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

    // Grout (docs/SCOPE.md M2 issue #93; issue #129 fix). Upstream 1.12 ships it as one state of
    // BlockSoil (SoilTypes.GROUT, NOTICE.md): Material.SAND, hardness 3.0, SoundType.SAND, and a
    // slipperiness of 0.8 (default is 0.6) -- ported below via strength()/sound()/friction(). Harvest
    // level ("shovel", -1) means no minimum tool tier is required, so -- matching every other
    // Forgeweave block, none of which gate tool tier either (see the seared family javadoc below) --
    // this carries no mineable/* tag. Not a falling block: BlockSoil extends EnumBlock, not
    // BlockFalling.
    public static final DeferredBlock<Block> GROUT = BLOCKS.registerSimpleBlock("grout",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .strength(3.0F)
                    .sound(SoundType.SAND)
                    .friction(0.8F));

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

    // The smeltery multiblock's own blocks (docs/SCOPE.md M2 issue #95). All share BlockSeared's
    // strength and sound so a smeltery mines as one material.
    public static final DeferredBlock<SmelteryControllerBlock> STANDARD_CORE = BLOCKS.register("standard_core",
            () -> new SmelteryControllerBlock(searedProperties(), SmelteryCore.STANDARD));

    public static final DeferredBlock<SmelteryControllerBlock> NETHER_CORE = BLOCKS.register("nether_core",
            () -> new SmelteryControllerBlock(searedProperties(), SmelteryCore.NETHER));

    // The gauge and window are see-through, so all three skip occlusion culling (upstream's BlockTank
    // is likewise not a full/opaque cube).
    public static final DeferredBlock<SearedTankBlock> SEARED_TANK = tankBlock("seared_tank");
    public static final DeferredBlock<SearedTankBlock> SEARED_GAUGE = tankBlock("seared_gauge");
    public static final DeferredBlock<SearedTankBlock> SEARED_WINDOW = tankBlock("seared_window");

    public static final DeferredBlock<SearedDrainBlock> SEARED_DRAIN = BLOCKS.register("seared_drain",
            () -> new SearedDrainBlock(searedProperties()));

    // #100 -- casting (docs/SCOPE.md M2 issue #100). Same seared strength/sound as the rest of the
    // smeltery; none of the three is a full cube, so all three skip occlusion culling.
    public static final DeferredBlock<CastingBlock> CASTING_TABLE = BLOCKS.register("casting_table",
            () -> new CastingBlock(searedProperties().noOcclusion(), CastingRecipe.Station.TABLE));

    public static final DeferredBlock<CastingBlock> CASTING_BASIN = BLOCKS.register("casting_basin",
            () -> new CastingBlock(searedProperties().noOcclusion(), CastingRecipe.Station.BASIN));

    public static final DeferredBlock<FaucetBlock> FAUCET = BLOCKS.register("faucet",
            () -> new FaucetBlock(searedProperties().noOcclusion()));

    // #104 -- cobalt + ardite nether ore (docs/SCOPE.md M2 issue #104). Upstream 1.12's BlockOre
    // (NOTICE.md) sets only setHardness(10f) and setHarvestLevel("pickaxe", HarvestLevels.COBALT) --
    // its own above-diamond tool tier (TinkerMaterials: obsidian's tools mine at COBALT level, so an
    // obsidian-tier tool is upstream's entry point). CONTEXT.md forbids a custom numeric harvest
    // level, so this maps onto the vanilla tag ladder's tightest tier below netherite:
    // needs_diamond_tool (a plain diamond pickaxe suffices) -- the PR body records this adaptation.
    // Upstream never calls setResistance, so its blast resistance is Block's own unset default;
    // strength(10.0F) applies that same 10 to both hardness and resistance, matching how every other
    // Forgeweave block with no upstream resistance override (searedProperties et al.) uses the
    // single-argument form. Material.ROCK's implicit sound is STONE, same as vanilla ore blocks.
    public static final DeferredBlock<Block> COBALT_ORE = oreBlock("cobalt_ore");
    public static final DeferredBlock<Block> ARDITE_ORE = oreBlock("ardite_ore");

    private static DeferredBlock<Block> oreBlock(String name) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of()
                .mapColor(MapColor.NETHER)
                .strength(10.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
    }

    // #206 -- storage blocks for the four M2 metals with no vanilla block form (parity gap: the
    // basin had no metal block to cast for them at all). Upstream 1.12's BlockMetal (NOTICE.md)
    // registers all of its metal types on one Material.IRON block: hardness 5, no harvest level
    // ("we're generous. no harvest level required"), which is vanilla's own iron_block strength
    // (5.0F, 6.0F) with no tool-tier gate -- matching how no other Forgeweave block gates tool tier
    // either (see this class's javadoc). Rose gold has no 1.12 counterpart; its block follows the
    // same maintainer recolor-of-manyullyn precedent as its ingot/nugget (issue #103, NOTICE.md).
    public static final DeferredBlock<Block> COBALT_BLOCK = metalBlock("cobalt_block");
    public static final DeferredBlock<Block> ARDITE_BLOCK = metalBlock("ardite_block");
    public static final DeferredBlock<Block> MANYULLYN_BLOCK = metalBlock("manyullyn_block");
    public static final DeferredBlock<Block> ROSE_GOLD_BLOCK = metalBlock("rose_gold_block");

    private static DeferredBlock<Block> metalBlock(String name) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL));
    }

    private static DeferredBlock<Block> searedBlock(String name) {
        return BLOCKS.registerSimpleBlock(name, searedProperties());
    }

    private static DeferredBlock<SearedTankBlock> tankBlock(String name) {
        return BLOCKS.register(name, () -> new SearedTankBlock(searedProperties().noOcclusion()));
    }

    private static BlockBehaviour.Properties searedProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.STONE)
                .strength(3.0F, 20.0F)
                .sound(SoundType.METAL);
    }

    private ForgeweaveBlocks() {}
}
