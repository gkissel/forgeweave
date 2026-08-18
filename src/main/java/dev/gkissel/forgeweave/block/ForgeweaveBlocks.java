package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
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

    // #442: upstream's TinkerSmeltery.searedStairsSlabs, the seared furnace's ceiling roster
    // (SearedFurnaceScan#isCeilingBlock). Filled by searedStairs()/searedSlab() as the variants
    // register, so a new one cannot be forgotten from it; declared up here because those helpers
    // run during this class's static initialization, before anything declared below them exists.
    private static final List<DeferredBlock<? extends Block>> SEARED_STAIRS_SLABS = new ArrayList<>();

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
    // slipperiness of 0.8 (default is 0.6) -- ported via soilProperties() below. Harvest level
    // ("shovel", -1) splits into two modern tags: the "shovel" tool class is mineable/shovel (the
    // correct, faster tool), and level -1 -- no minimum tier -- means no needs_*_tool tag alongside
    // it, matching every other Forgeweave block (see the seared family javadoc below). Not a
    // falling block: BlockSoil extends EnumBlock, not BlockFalling.
    public static final DeferredBlock<Block> GROUT = BLOCKS.registerSimpleBlock("grout", soilProperties(MapColor.SAND));

    // #339 -- green and magma slimy mud: two more states of the same upstream BlockSoil as grout
    // above (SoilTypes.SLIMY_MUD_GREEN / SLIMY_MUD_MAGMA, NOTICE.md), so they share its properties
    // verbatim. Furnace-smelting one yields its slime crystal (upstream TinkerTools, 0.75 xp) --
    // that smelt is the whole reason these blocks exist here; upstream's other mud behaviors
    // (onEntityWalk slowdown, sustaining slime plants) belong to world content and stay out of
    // scope until #181. Blue mud needs blue slime balls, which have no world source yet, so it is
    // deliberately absent.
    public static final DeferredBlock<Block> SLIMY_MUD_GREEN =
            BLOCKS.registerSimpleBlock("slimy_mud_green", soilProperties(MapColor.COLOR_GREEN));
    public static final DeferredBlock<Block> SLIMY_MUD_MAGMA =
            BLOCKS.registerSimpleBlock("slimy_mud_magma", soilProperties(MapColor.COLOR_ORANGE));

    // #429 -- graveyard soil and consecrated soil, the last two BlockSoil states Forgeweave was
    // missing (SoilTypes.GRAVEYARD / CONSECRATED, NOTICE.md). Same soilProperties() as grout and
    // the muds above; unlike them they carry upstream's onEntityWalk behavior, see UndeadSoilBlock.
    // Smelting graveyard soil gives consecrated soil, which is smite's upstream reagent.
    public static final DeferredBlock<UndeadSoilBlock> GRAVEYARD_SOIL = BLOCKS.register("graveyard_soil",
            () -> new UndeadSoilBlock(soilProperties(MapColor.TERRACOTTA_BROWN), false));
    public static final DeferredBlock<UndeadSoilBlock> CONSECRATED_SOIL = BLOCKS.register("consecrated_soil",
            () -> new UndeadSoilBlock(soilProperties(MapColor.DIRT), true));

    // #502 (T71 parity audit): mud brick block, upstream's one BlockDecoGround state
    // (DecoGroundType.MUDBRICK, NOTICE.md) -- hardness 2.0, Material.GROUND, SoundType.GROUND,
    // shovel harvest tool with no minimum tier, same "shovel"/-1 tag split every other Forgeweave
    // block uses. A plain cube, not a BlockSoil sibling of grout/graveyard soil above (upstream
    // keeps it on its own EnumBlock, not BlockSoil), so it gets its own properties rather than
    // reusing soilProperties()'s 3.0 hardness/SAND sound.
    public static final DeferredBlock<Block> MUD_BRICK_BLOCK = BLOCKS.registerSimpleBlock("mud_brick_block",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIRT)
                    .strength(2.0F)
                    .sound(SoundType.GRAVEL));

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

    // Seared stairs + slabs (docs/SCOPE.md M3.4-5 issue #274): upstream 1.12's BlockSearedStairs and
    // the two BlockSearedSlab(2) blocks together cover all 12 SearedType variants above, split here
    // into one StairBlock/SlabBlock per variant -- matching how the 12 plain blocks above already
    // split upstream's single PropertyEnum block, and how upstream itself needed two slab blocks only
    // because 1.12's 16-state-per-block limit couldn't fit all 12 in one (BlockSearedSlab/
    // BlockSearedSlab2, NOTICE.md); that limit doesn't exist here, so one SlabBlock per variant.
    //
    // Deviation, flagged in the PR body: upstream's BlockSearedStairs/BlockSearedSlab carry a
    // TileSmelteryComponent and are valid smeltery structure blocks on the ceiling only
    // (MultiblockSearedFurnace#isCeilingBlock/MultiblockTinkerTank, NOTICE.md). The 12 plain seared
    // blocks above are already TE-less "decorative cubes only" because Forgeweave's smeltery-structure
    // scan (SmelteryScan) has no stairs/slab support yet -- these follow suit as plain vanilla
    // StairBlock/SlabBlock with no BlockEntity, same as their parents, until SmelteryScan grows that.
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_STONE = searedStairs("seared_stairs_stone", SEARED_STONE);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_COBBLESTONE = searedStairs("seared_stairs_cobblestone", SEARED_COBBLESTONE);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_PAVER = searedStairs("seared_stairs_paver", SEARED_PAVER);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_BRICKS = searedStairs("seared_stairs_bricks", SEARED_BRICKS);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_CRACKED_BRICKS = searedStairs("seared_stairs_cracked_bricks", SEARED_CRACKED_BRICKS);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_FANCY_BRICKS = searedStairs("seared_stairs_fancy_bricks", SEARED_FANCY_BRICKS);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_SQUARE_BRICKS = searedStairs("seared_stairs_square_bricks", SEARED_SQUARE_BRICKS);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_TRIANGLE_BRICKS = searedStairs("seared_stairs_triangle_bricks", SEARED_TRIANGLE_BRICKS);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_SMALL_BRICKS = searedStairs("seared_stairs_small_bricks", SEARED_SMALL_BRICKS);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_ROAD = searedStairs("seared_stairs_road", SEARED_ROAD);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_TILE = searedStairs("seared_stairs_tile", SEARED_TILE);
    public static final DeferredBlock<StairBlock> SEARED_STAIRS_CREEPER = searedStairs("seared_stairs_creeper", SEARED_CREEPER);

    public static final DeferredBlock<SlabBlock> SEARED_SLAB_STONE = searedSlab("seared_slab_stone");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_COBBLESTONE = searedSlab("seared_slab_cobblestone");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_PAVER = searedSlab("seared_slab_paver");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_BRICKS = searedSlab("seared_slab_bricks");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_CRACKED_BRICKS = searedSlab("seared_slab_cracked_bricks");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_FANCY_BRICKS = searedSlab("seared_slab_fancy_bricks");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_SQUARE_BRICKS = searedSlab("seared_slab_square_bricks");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_TRIANGLE_BRICKS = searedSlab("seared_slab_triangle_bricks");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_SMALL_BRICKS = searedSlab("seared_slab_small_bricks");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_ROAD = searedSlab("seared_slab_road");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_TILE = searedSlab("seared_slab_tile");
    public static final DeferredBlock<SlabBlock> SEARED_SLAB_CREEPER = searedSlab("seared_slab_creeper");

    // The smeltery multiblock's own blocks (docs/SCOPE.md M2 issue #95). All share BlockSeared's
    // strength and sound so a smeltery mines as one material.
    public static final DeferredBlock<SmelteryControllerBlock> STANDARD_CORE = BLOCKS.register("standard_core",
            () -> new SmelteryControllerBlock(searedProperties(), SmelteryCore.STANDARD));

    public static final DeferredBlock<SmelteryControllerBlock> NETHER_CORE = BLOCKS.register("nether_core",
            () -> new SmelteryControllerBlock(searedProperties(), SmelteryCore.NETHER));

    // #442 -- the seared furnace controller. Upstream 1.12's BlockSearedFurnaceController
    // (NOTICE.md): Material.ROCK, hardness 3, resistance 20, SoundType.METAL -- deliberately not the
    // seared family's strength, so it is spelled out rather than taken from searedProperties().
    public static final DeferredBlock<SearedFurnaceControllerBlock> SEARED_FURNACE_CONTROLLER = BLOCKS.register("seared_furnace_controller",
            () -> new SearedFurnaceControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 20.0F)
                    .sound(SoundType.METAL)));

    // The gauge and window are see-through, so all three skip occlusion culling (upstream's BlockTank
    // is likewise not a full/opaque cube).
    public static final DeferredBlock<SearedTankBlock> SEARED_TANK = tankBlock("seared_tank");
    public static final DeferredBlock<SearedTankBlock> SEARED_GAUGE = tankBlock("seared_gauge");
    public static final DeferredBlock<SearedTankBlock> SEARED_WINDOW = tankBlock("seared_window");

    public static final DeferredBlock<SearedDrainBlock> SEARED_DRAIN = BLOCKS.register("seared_drain",
            () -> new SearedDrainBlock(searedProperties()));

    // #277 -- the seared duct and seared chute (docs/SCOPE.md M3.4). Maintainer-approved deviation
    // from the 1.12 parity default, recorded on issue #277: neither block exists in the 1.12
    // generation, so both follow the 1.20 clone's SearedDuctBlock and seared_chute (NOTICE.md). Same
    // seared strength and sound as the drain they sit alongside; the chute's model has a trough
    // hanging off three of its faces, so it skips occlusion culling like the tank family does.
    public static final DeferredBlock<SearedDuctBlock> SEARED_DUCT = BLOCKS.register("seared_duct",
            () -> new SearedDuctBlock(searedProperties()));

    public static final DeferredBlock<SearedChuteBlock> SEARED_CHUTE = BLOCKS.register("seared_chute",
            () -> new SearedChuteBlock(searedProperties().noOcclusion()));

    // Plain seared glass (docs/SCOPE.md M3.3 issue #289): a wall-only smeltery block, upstream's
    // BlockSearedGlass (NOTICE.md). Upstream's block adds BlockConnectedTexture rendering and no
    // other behavior -- no BlockEntity, no multiblock role of its own beyond "valid wall, not floor"
    // (SmelteryScan#Valid) -- so this is a plain non-opaque block, same searedProperties() as the
    // rest of the family. The connected-texture rendering itself is left plain (PR #289 body); its
    // single texture is upstream's own "no neighbours" sprite (NOTICE.md).
    public static final DeferredBlock<Block> SEARED_GLASS = BLOCKS.registerSimpleBlock("seared_glass",
            searedProperties().noOcclusion());

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
    // its top tool tier (TinkerMaterials: obsidian's tools mine at COBALT level, so an obsidian-tier
    // tool is upstream's entry point). Issue #433: COBALT is level 4, i.e. the netherite tier, and
    // ForgeweaveBlockTagsProvider spells that gate out of two vanilla tags since 1.21 has no
    // needs_netherite_tool -- see its javadoc.
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
    // #234 -- steel's storage block (M3.2), same upstream BlockMetal-derived properties as the four above.
    public static final DeferredBlock<Block> STEEL_BLOCK = metalBlock("steel_block");
    // #235 -- amethyst bronze's storage block (M3.2), same properties; texture is the 1.20 clone's
    // own storage-block art, copied byte-for-byte (NOTICE.md).
    public static final DeferredBlock<Block> AMETHYST_BRONZE_BLOCK = metalBlock("amethyst_bronze_block");

    // #232 -- knightslime's storage block (docs/SCOPE.md M3.2): upstream 1.12's BlockMetal carries
    // knightslime as one of its types on the same hardness-5 iron-material block, so it shares
    // metalBlock() with the four above; texture is the clone's block_knightslime (NOTICE.md).
    public static final DeferredBlock<Block> KNIGHTSLIME_BLOCK = metalBlock("knightslime_block");

    // #233 -- pig iron's storage block, same BlockMetal-derived properties as the four above.
    public static final DeferredBlock<Block> PIG_IRON_BLOCK = metalBlock("pig_iron_block");

    // #233 -- firewood (docs/SCOPE.md M3.2). Upstream 1.12's BlockFirewood (NOTICE.md):
    // Material.WOOD, hardness 2, resistance 7, SoundType.WOOD, setLightLevel(0.5f) -- i.e. light 7
    // -- and setHarvestLevel("axe", -1), meaning no tool is required at all, so like grout it
    // carries no mineable/* tag (see this class's javadoc on tool-tier gating). Only the firewood
    // half of upstream's two-state block is in scope: lavawood, its precursor, is not on
    // docs/SCOPE.md's M3.2 roster.
    public static final DeferredBlock<Block> FIREWOOD = BLOCKS.registerSimpleBlock("firewood",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.0F, 7.0F)
                    .sound(SoundType.WOOD)
                    .lightLevel(state -> 7));

    // #275 -- clear glass and its 16 clear stained glass colors. Upstream 1.12's TinkerCommons
    // registers both on shared.block.BlockClearGlass/BlockClearStainedGlass (NOTICE.md): Material.GLASS,
    // hardness 0.3, SoundType.GLASS, setHarvestLevel("pickaxe", -1) (no tool required -- same "no
    // mineable tag" reasoning as grout/firewood above), isOpaqueCube/isFullCube false. Upstream ships
    // no glass pane form of either (grep of TinkerCommons -- verified against the clone), so none is
    // registered here. Connected-texture rendering is left plain, the same simplification #289 already
    // made for seared glass (PR body): both blocks use upstream's own "no neighbours" sprite as a
    // single cube_all texture.
    public static final DeferredBlock<Block> CLEAR_GLASS = BLOCKS.registerSimpleBlock("clear_glass",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NONE)
                    .strength(0.3F)
                    .sound(SoundType.GLASS)
                    .noOcclusion());

    /**
     * A registered clear stained glass color (issue #275): its {@link DyeColor}, the exact ARGB tint
     * upstream's {@code BlockClearStainedGlass.EnumGlassColor} paints its one shared texture with
     * (NOTICE.md), and the block itself. Upstream tints one grayscale texture per color rather than
     * shipping 16 textures ({@code CommonsClientProxy#init}'s block/item color handlers) -- the same
     * "one texture + tint" idiom {@code ForgeweaveFluidClientExtensions} already uses for the molten
     * metals, applied here via {@code dev.gkissel.forgeweave.client.ForgeweaveGlassColors}.
     */
    public record StainedGlassColor(DyeColor dye, int tint, DeferredBlock<Block> block) {}

    // Every clear stained glass color, in declaration order -- datagen (blockstate, lang, loot,
    // recipe) and the client tint handler all walk this instead of a hand list, the same anti-drift
    // shape ForgeweaveFluids#all uses for the molten metals.
    private static final List<StainedGlassColor> CLEAR_STAINED_GLASS = new ArrayList<>();
    private static final List<StainedGlassColor> CLEAR_STAINED_GLASS_VIEW = Collections.unmodifiableList(CLEAR_STAINED_GLASS);

    /** Every registered clear stained glass color, in {@link DyeColor} declaration order. */
    public static List<StainedGlassColor> clearStainedGlassColors() {
        return CLEAR_STAINED_GLASS_VIEW;
    }

    // Ported 1:1 from BlockClearStainedGlass.EnumGlassColor (NOTICE.md); DyeColor.getMapColor()
    // matches upstream's own per-color MapColor exactly (both are vanilla's 16-wool palette).
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_WHITE = stainedGlassBlock(DyeColor.WHITE, 0xffffff);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_ORANGE = stainedGlassBlock(DyeColor.ORANGE, 0xd87f33);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_MAGENTA = stainedGlassBlock(DyeColor.MAGENTA, 0xb24cd8);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_LIGHT_BLUE = stainedGlassBlock(DyeColor.LIGHT_BLUE, 0x6699d8);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_YELLOW = stainedGlassBlock(DyeColor.YELLOW, 0xe5e533);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_LIME = stainedGlassBlock(DyeColor.LIME, 0x7fcc19);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_PINK = stainedGlassBlock(DyeColor.PINK, 0xf27fa5);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_GRAY = stainedGlassBlock(DyeColor.GRAY, 0x4c4c4c);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_LIGHT_GRAY = stainedGlassBlock(DyeColor.LIGHT_GRAY, 0x999999);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_CYAN = stainedGlassBlock(DyeColor.CYAN, 0x4c7f99);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_PURPLE = stainedGlassBlock(DyeColor.PURPLE, 0x7f3fb2);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_BLUE = stainedGlassBlock(DyeColor.BLUE, 0x334cb2);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_BROWN = stainedGlassBlock(DyeColor.BROWN, 0x664c33);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_GREEN = stainedGlassBlock(DyeColor.GREEN, 0x667f33);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_RED = stainedGlassBlock(DyeColor.RED, 0x993333);
    public static final DeferredBlock<Block> CLEAR_STAINED_GLASS_BLACK = stainedGlassBlock(DyeColor.BLACK, 0x191919);

    private static DeferredBlock<Block> stainedGlassBlock(DyeColor dye, int tint) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(dye.getName() + "_stained_clear_glass",
                BlockBehaviour.Properties.of()
                        .mapColor(dye.getMapColor())
                        .strength(0.3F)
                        .sound(SoundType.GLASS)
                        .noOcclusion());
        CLEAR_STAINED_GLASS.add(new StainedGlassColor(dye, tint, block));
        return block;
    }

    /** Upstream 1.12 {@code BlockSoil}'s block properties, shared by grout, the slimy muds and the two soils. */
    private static BlockBehaviour.Properties soilProperties(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(3.0F)
                .sound(SoundType.SAND)
                .friction(0.8F);
    }

    private static DeferredBlock<Block> metalBlock(String name) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .strength(5.0F, 6.0F)
                .sound(SoundType.METAL));
    }

    private static DeferredBlock<Block> searedBlock(String name) {
        return BLOCKS.registerSimpleBlock(name, searedProperties());
    }

    /** Whether {@code block} is one of the seared stairs or slabs (issue #274). */
    public static boolean isSearedStairsOrSlab(Block block) {
        return SEARED_STAIRS_SLABS.stream().anyMatch(entry -> entry.get() == block);
    }

    private static DeferredBlock<StairBlock> searedStairs(String name, DeferredBlock<Block> base) {
        DeferredBlock<StairBlock> block = BLOCKS.register(name, () -> new StairBlock(base.get().defaultBlockState(), searedProperties()));
        SEARED_STAIRS_SLABS.add(block);
        return block;
    }

    private static DeferredBlock<SlabBlock> searedSlab(String name) {
        DeferredBlock<SlabBlock> block = BLOCKS.register(name, () -> new SlabBlock(searedProperties()));
        SEARED_STAIRS_SLABS.add(block);
        return block;
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
