package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

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

    // The Armor Station (docs/SCOPE.md M4 issue #782, reversing D13): a Tool Station body wearing a
    // distinct top so the two are visually related without sharing a texture -- see
    // ArmorStationBlock's own javadoc for why it subclasses ToolStationBlock exactly as ToolForgeBlock
    // does. Wood properties, matching the Tool Station rather than the Tool Forge's metal ones: its
    // recipe is a plain plank-and-pattern shape (issue #782), not a metal one.
    public static final DeferredBlock<ArmorStationBlock> ARMOR_STATION = BLOCKS.register("armor_station",
            () -> new ArmorStationBlock(BlockBehaviour.Properties.of()
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

    // The Wooden Hopper (docs/SCOPE.md M5, issue #822). Upstream 1.12's BlockWoodenHopper: hardness
    // 3.0, resistance 8.0, SoundType.WOOD (NOTICE.md) -- ported verbatim; the half-speed transfer
    // behavior lives in WoodenHopperBlock/WoodenHopperBlockEntity, not in these properties.
    public static final DeferredBlock<WoodenHopperBlock> WOODEN_HOPPER = BLOCKS.register("wooden_hopper",
            () -> new WoodenHopperBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(3.0F, 8.0F)
                    .sound(SoundType.WOOD)));

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
    // scope until #181. Blue mud joined them in #635, once blue slime balls existed to craft it.
    public static final DeferredBlock<Block> SLIMY_MUD_GREEN =
            BLOCKS.registerSimpleBlock("slimy_mud_green", soilProperties(MapColor.COLOR_GREEN));
    public static final DeferredBlock<Block> SLIMY_MUD_MAGMA =
            BLOCKS.registerSimpleBlock("slimy_mud_magma", soilProperties(MapColor.COLOR_ORANGE));
    // #635 (parity audit T57): blue slimy mud, the third BlockSoil mud state (SoilTypes.SLIMY_MUD_BLUE,
    // NOTICE.md). #339 left it out because upstream's slimy_mud_blue.json wants four blue slime balls
    // and there were none; there are now, so it arrives with them and its furnace smelt replaces
    // #232's interim "green crystal + lapis" craft for the blue slime crystal.
    public static final DeferredBlock<Block> SLIMY_MUD_BLUE =
            BLOCKS.registerSimpleBlock("slimy_mud_blue", soilProperties(MapColor.COLOR_LIGHT_BLUE));

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

    // #845 -- the top two tiers, reached only by pouring the right fluid over the tier below
    // (CoreTransformRecipe), never crafted directly -- see ForgeweaveRecipeProvider's smelteryRecipes
    // javadoc for why only the Standard and Nether Core get a shaped recipe.
    public static final DeferredBlock<SmelteryControllerBlock> END_CORE = BLOCKS.register("end_core",
            () -> new SmelteryControllerBlock(searedProperties(), SmelteryCore.END));

    public static final DeferredBlock<SmelteryControllerBlock> DEEP_CORE = BLOCKS.register("deep_core",
            () -> new SmelteryControllerBlock(searedProperties(), SmelteryCore.DEEP));

    // #442 -- the seared furnace controller. Upstream 1.12's BlockSearedFurnaceController
    // (NOTICE.md): Material.ROCK, hardness 3, resistance 20, SoundType.METAL -- deliberately not the
    // seared family's strength, so it is spelled out rather than taken from searedProperties().
    public static final DeferredBlock<SearedFurnaceControllerBlock> SEARED_FURNACE_CONTROLLER = BLOCKS.register("seared_furnace_controller",
            () -> new SearedFurnaceControllerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0F, 20.0F)
                    .sound(SoundType.METAL)));

    // T44/#475 -- the seared reservoir controller. Upstream 1.12's BlockTinkerTankController
    // (NOTICE.md) uses the same Material.ROCK / hardness 3 / resistance 20 / SoundType.METAL as the
    // seared furnace controller, and emits no light: a reservoir holds no fire.
    public static final DeferredBlock<SearedReservoirControllerBlock> SEARED_RESERVOIR_CONTROLLER = BLOCKS.register("seared_reservoir_controller",
            () -> new SearedReservoirControllerBlock(BlockBehaviour.Properties.of()
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

    // #441 (parity audit T9) -- the seared channel, upstream 1.12's BlockChannel (NOTICE.md). Same
    // seared strength and sound as the rest of the smeltery family; a channel is a thin trough
    // rather than a cube, so it skips occlusion culling.
    public static final DeferredBlock<SearedChannelBlock> SEARED_CHANNEL = BLOCKS.register("seared_channel",
            () -> new SearedChannelBlock(searedProperties().noOcclusion()));

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

    // #903 -- brimspar, the Nether fuel ore whose crystals melt into the ladder's 1900-degree rung.
    // Cobalt/ardite's own oreBlock() shape (hardness 10, stone sound, requiresCorrectToolForDrops,
    // MapColor.NETHER) on its own unstable subclass -- see BrimsparOreBlock for the two explosion
    // rolls and their numbers -- with one deliberate departure: blast resistance 1.5 instead of the
    // hardness-matching 10 every other ore here carries. A vein that chains when caught in an
    // explosion has to actually be destructible by one; at resistance 10 its own 2.5-power blast could
    // not reach the neighbour it is supposed to set off, and the chain rule would be dead code.
    public static final DeferredBlock<BrimsparOreBlock> BRIMSPAR_ORE = BLOCKS.register("brimspar_ore",
            () -> new BrimsparOreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER)
                    .strength(10.0F, 1.5F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()));

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

    // #843 (closes #180) -- queen's slime and hepatizon storage blocks, the 1.20-branch material gap's
    // two brand-new T4 alloy metals (maintainer authorization on the issue). Same BlockMetal-derived
    // properties as every other Forgeweave metal block; no upstream art to derive (M9's Forged-art
    // policy applies going forward, CLAUDE.md), so both ride a fresh recolor like rose gold did.
    public static final DeferredBlock<Block> QUEENS_SLIME_BLOCK = metalBlock("queens_slime_block");
    public static final DeferredBlock<Block> HEPATIZON_BLOCK = metalBlock("hepatizon_block");

    // #839 -- Track B's ore family (M6 epic #824, Track B: self-contained materials). See
    // dev.gkissel.forgeweave.trackb.TrackBOre for the 12-material roster and its distribution table.
    // Ore blocks reuse cobalt/ardite's oreBlock() strength/sound/requiresCorrectToolForDrops, but the
    // map color follows each ore's own base rock (stone-look for the four Overworld ores, the existing
    // NETHER color for the three Nether ones, and vanilla end_stone's own MapColor.SAND for the four
    // End ores, #883/#909) rather than hardcoding NETHER for all. Storage and raw-storage blocks reuse
    // metalBlock() -- same "no per-block property differentiation" precedent every other Forgeweave
    // metal's storage block already follows.
    private static final Map<String, DeferredBlock<Block>> TRACK_B_ORE_BLOCKS = new LinkedHashMap<>();
    private static final Map<String, DeferredBlock<Block>> TRACK_B_STORAGE_BLOCKS = new LinkedHashMap<>();
    private static final Map<String, DeferredBlock<Block>> TRACK_B_RAW_BLOCKS = new LinkedHashMap<>();

    static {
        for (TrackBOre ore : TrackBOre.ALL) {
            MapColor mapColor = switch (ore.host()) {
                case NETHER -> MapColor.NETHER;
                case END -> MapColor.SAND;
                case OVERWORLD_STONE, OVERWORLD_DEEPSLATE -> MapColor.STONE;
            };
            TRACK_B_ORE_BLOCKS.put(ore.id(), trackBOreBlock(ore.oreBlockId(), mapColor));
            TRACK_B_STORAGE_BLOCKS.put(ore.id(), metalBlock(ore.storageBlockId()));
            TRACK_B_RAW_BLOCKS.put(ore.id(), metalBlock(ore.rawBlockId()));
        }
    }

    // #840 -- Track B's 18 alloy tool materials (M6 epic #824). See
    // dev.gkissel.forgeweave.trackb.TrackBAlloy for the roster; every one is alloy-only (no ore block,
    // no raw-storage block), so it needs only the one metalBlock() storage block the pattern
    // pig_iron/knightslime already use above.
    private static final Map<String, DeferredBlock<Block>> TRACK_B_ALLOY_BLOCKS = new LinkedHashMap<>();

    static {
        for (TrackBAlloy alloy : TrackBAlloy.ALL) {
            TRACK_B_ALLOY_BLOCKS.put(alloy.id(), metalBlock(alloy.blockId()));
        }
    }

    /** A Track B alloy's storage block by material id, or {@code null} if unknown. */
    public static DeferredBlock<Block> trackBAlloyBlock(String id) {
        return TRACK_B_ALLOY_BLOCKS.get(id);
    }

    private static DeferredBlock<Block> trackBOreBlock(String name, MapColor mapColor) {
        return BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .strength(10.0F)
                .sound(SoundType.STONE)
                .requiresCorrectToolForDrops());
    }

    /** A Track B ore block by material id (e.g. {@code "fulmenite"}), or {@code null} if unknown. */
    public static DeferredBlock<Block> trackBOre(String id) {
        return TRACK_B_ORE_BLOCKS.get(id);
    }

    /** A Track B storage block by material id, or {@code null} if unknown. */
    public static DeferredBlock<Block> trackBStorageBlock(String id) {
        return TRACK_B_STORAGE_BLOCKS.get(id);
    }

    /** A Track B raw-storage block by material id, or {@code null} if unknown. */
    public static DeferredBlock<Block> trackBRawBlock(String id) {
        return TRACK_B_RAW_BLOCKS.get(id);
    }

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

    // ------------------------------------------------------------------------------------------
    // Slime island world content (#449, parity audit T18). Upstream 1.12 keeps each of these as one
    // metadata block -- BlockSlimeDirt (4 colours), BlockSlimeGrass (5 dirts x 3 foliages),
    // BlockSlimeCongealed (6 colours), BlockSlimeLeaves and BlockTallSlimeGrass (3 foliages x 2
    // shapes) -- all NOTICE.md rows. Modern Minecraft has no metadata, so each state a registry id
    // has to reach becomes its own block; the roster here is exactly what
    // SlimeIslandGenerator#generateIslandInChunk places on an overworld island plus what
    // MagmaSlimeIslandGenerator places on a Nether one (#450, parity audit T19), and the rest
    // (blood dirt and grass, the vanilla-dirt grass) waits on a later ticket; the remaining congealed
    // colours and every coloured slime block shipped with #635.

    /**
     * One slime soil colour: the dirt, the grass that sits on top of it, and the foliage colour that
     * grass is tinted with. Upstream lets any of its three foliage colours sit on any of its dirts;
     * Forgeweave pins one per dirt -- the pairing {@code SlimeIslandGenerator} itself uses -- so a
     * grass block needs no second property. See {@link SlimeGrassBlock}'s javadoc.
     */
    public record SlimeSoil(FoliageType foliage, DeferredBlock<Block> dirt, DeferredBlock<SlimeGrassBlock> grass) {}

    private static final List<SlimeSoil> SLIME_SOILS = new ArrayList<>();
    private static final List<SlimeSoil> SLIME_SOILS_VIEW = Collections.unmodifiableList(SLIME_SOILS);

    /** Every slime dirt/grass pair, in declaration order -- datagen, tinting and worldgen all walk this. */
    public static List<SlimeSoil> slimeSoils() {
        return SLIME_SOILS_VIEW;
    }

    public static final SlimeSoil GREEN_SLIME_SOIL = slimeSoil("green", FoliageType.BLUE, MapColor.COLOR_GREEN);
    public static final SlimeSoil BLUE_SLIME_SOIL = slimeSoil("blue", FoliageType.BLUE, MapColor.COLOR_LIGHT_BLUE);
    public static final SlimeSoil PURPLE_SLIME_SOIL = slimeSoil("purple", FoliageType.PURPLE, MapColor.COLOR_PURPLE);
    /** #450 (parity audit T19): the Nether magma island's soil, upstream's {@code DirtType.MAGMA}. */
    public static final SlimeSoil MAGMA_SLIME_SOIL = slimeSoil("magma", FoliageType.ORANGE, MapColor.COLOR_ORANGE);

    /**
     * One colour's pair of solid slime blocks: the congealed block, and -- for every colour but
     * green -- the bouncy coloured slime block. Upstream 1.12 keeps each as one metadata block
     * ({@code BlockSlimeCongealed} and {@code BlockSlime}, NOTICE.md); modern Minecraft has no
     * metadata, so every colour gets its own registration, the same reduction #449 already made for
     * the dirts, grasses, leaves and plants.
     */
    public record SlimeFamily(SlimeColour colour, DeferredBlock<CongealedSlimeBlock> congealed,
                              @Nullable DeferredBlock<ColouredSlimeBlock> slimeBlock) {}

    private static final List<SlimeFamily> SLIME_FAMILIES = new ArrayList<>();
    private static final List<SlimeFamily> SLIME_FAMILIES_VIEW = Collections.unmodifiableList(SLIME_FAMILIES);

    /** Every slime colour's solid blocks, in declaration order -- datagen, loot and recipes all walk this. */
    public static List<SlimeFamily> slimeFamilies() {
        return SLIME_FAMILIES_VIEW;
    }

    /**
     * The six slime colours' congealed and coloured slime blocks. Green congealed slime is the block
     * {@code SlimeIslandShape} builds every tree trunk from (#449), magma congealed slime the Nether
     * island's (#450), and blue and purple congealed slime what the slime lake is bottomed and rimmed
     * with (#625); blood and pink congealed slime and all five coloured slime blocks arrive with
     * #635 (parity audit T57), alongside the coloured slime balls that craft every one of them.
     * Registry ids are unchanged from where each block was first registered.
     *
     * <p>Upstream {@code BlockSlimeCongealed}: {@code Material.CLAY}, hardness 0.5, slipperiness
     * 0.5, {@code SoundType.SLIME}; its sunken collision box lives in {@link CongealedSlimeBlock}.
     * Upstream {@code BlockSlime} extends vanilla's own slime block and re-declares only its sound
     * and its stickiness, which is what {@link ColouredSlimeBlock} does; the remaining properties
     * here are vanilla {@code Blocks.SLIME_BLOCK}'s.
     */
    public static final SlimeFamily GREEN_SLIME = registerSlimeFamily(SlimeColour.GREEN);
    public static final SlimeFamily BLUE_SLIME = registerSlimeFamily(SlimeColour.BLUE);
    public static final SlimeFamily PURPLE_SLIME = registerSlimeFamily(SlimeColour.PURPLE);
    public static final SlimeFamily BLOOD_SLIME = registerSlimeFamily(SlimeColour.BLOOD);
    public static final SlimeFamily MAGMA_SLIME = registerSlimeFamily(SlimeColour.MAGMA);
    public static final SlimeFamily PINK_SLIME = registerSlimeFamily(SlimeColour.PINK);

    /** Green congealed slime, the block every overworld slime tree's trunk is built from (#449). */
    public static final DeferredBlock<CongealedSlimeBlock> GREEN_CONGEALED_SLIME = GREEN_SLIME.congealed();
    /** Blue congealed slime, a blue or green island's lake bed and shore (#625). */
    public static final DeferredBlock<CongealedSlimeBlock> BLUE_CONGEALED_SLIME = BLUE_SLIME.congealed();
    /** Purple congealed slime, a purple island's lake bed and shore (#625). */
    public static final DeferredBlock<CongealedSlimeBlock> PURPLE_CONGEALED_SLIME = PURPLE_SLIME.congealed();
    /** Magma congealed slime, the Nether magma island's trunk block and lake shore (#450). */
    public static final DeferredBlock<CongealedSlimeBlock> MAGMA_CONGEALED_SLIME = MAGMA_SLIME.congealed();

    /** One colour's slime blocks. */
    public static SlimeFamily slimeFamily(SlimeColour colour) {
        return SLIME_FAMILIES.stream().filter(family -> family.colour() == colour).findFirst()
                .orElseThrow(() -> new IllegalStateException("no slime blocks registered for " + colour));
    }

    private static SlimeFamily registerSlimeFamily(SlimeColour colour) {
        DeferredBlock<CongealedSlimeBlock> congealed = BLOCKS.register(colour.id() + "_congealed_slime",
                () -> new CongealedSlimeBlock(BlockBehaviour.Properties.of()
                        .mapColor(colour.mapColor())
                        .strength(0.5F)
                        .friction(0.5F)
                        .sound(SoundType.SLIME_BLOCK)));
        DeferredBlock<ColouredSlimeBlock> slimeBlock = colour.hasSlimeBlock()
                ? BLOCKS.register(colour.id() + "_slime_block",
                        () -> new ColouredSlimeBlock(BlockBehaviour.Properties.of()
                                .mapColor(colour.mapColor())
                                .friction(0.8F)
                                .sound(SoundType.SLIME_BLOCK)
                                .noOcclusion()))
                : null;
        SlimeFamily family = new SlimeFamily(colour, congealed, slimeBlock);
        SLIME_FAMILIES.add(family);
        return family;
    }

    /**
     * One slime foliage colour's plant life: leaves, tall grass, fern, the sapling that grows the
     * leaves, and the vine's three stages (#488, parity audit T57 -- upstream keeps the stages as
     * three separate blocks too, each pointing at the next).
     */
    public record SlimePlants(FoliageType foliage, DeferredBlock<Block> leaves,
                              DeferredBlock<SlimeTallGrassBlock> tallGrass, DeferredBlock<SlimeTallGrassBlock> fern,
                              DeferredBlock<SlimeSaplingBlock> sapling, @Nullable DeferredBlock<SlimeVineBlock> vine,
                              @Nullable DeferredBlock<SlimeVineBlock> vineMid,
                              @Nullable DeferredBlock<SlimeVineBlock> vineEnd) {

        /**
         * The vine's three stages, thickest first -- datagen, tinting and loot all walk this. Empty
         * for a foliage colour that has no vines: upstream's {@code TinkerWorld} registers
         * {@code slimeVineBlue1..3} and {@code slimeVinePurple1..3} and nothing else, so the magma
         * island's orange foliage has no vine of any stage (issue #450, parity audit T19).
         */
        public List<DeferredBlock<SlimeVineBlock>> vines() {
            return vine == null ? List.of() : List.of(vine, vineMid, vineEnd);
        }
    }

    private static final List<SlimePlants> SLIME_PLANTS = new ArrayList<>();
    private static final List<SlimePlants> SLIME_PLANTS_VIEW = Collections.unmodifiableList(SLIME_PLANTS);

    /** Every slime foliage colour's plants, in declaration order. */
    public static List<SlimePlants> slimePlants() {
        return SLIME_PLANTS_VIEW;
    }

    public static final SlimePlants BLUE_SLIME_PLANTS = slimePlants(FoliageType.BLUE, MapColor.COLOR_LIGHT_BLUE, true);
    public static final SlimePlants PURPLE_SLIME_PLANTS = slimePlants(FoliageType.PURPLE, MapColor.COLOR_PURPLE, true);
    /**
     * #450 (parity audit T19): the Nether magma island's canopy and ground cover. No vines --
     * upstream registers none for orange, and {@code MagmaSlimeIslandGenerator}'s tree generator is
     * built with a {@code null} vine, so nothing would ever place one.
     */
    public static final SlimePlants ORANGE_SLIME_PLANTS = slimePlants(FoliageType.ORANGE, MapColor.COLOR_ORANGE, false);

    /** The grass that grows on {@code dirt}, if it is one of the slime dirts. Drives {@link SlimeGrassBlock}'s spread. */
    public static Optional<Block> slimeGrassForDirt(Block dirt) {
        return SLIME_SOILS.stream().filter(soil -> soil.dirt().get() == dirt).<Block>map(soil -> soil.grass().get()).findFirst();
    }

    /**
     * Whether {@code block} is one of the slime grasses -- upstream's
     * {@code EntityBlueSlime#getCanSpawnHere} test, and so the island's spawn footprint (#451).
     */
    public static boolean isSlimeGrass(Block block) {
        return SLIME_SOILS.stream().anyMatch(soil -> soil.grass().get() == block);
    }

    /** Whether {@code block} is a slime dirt or slime grass -- upstream's "can a slime plant stand here". */
    public static boolean isSlimeSoil(Block block) {
        return SLIME_SOILS.stream().anyMatch(soil -> soil.dirt().get() == block || soil.grass().get() == block);
    }

    /** The tall grass of a given foliage colour. */
    public static Block slimeTallGrass(FoliageType foliage) {
        return plantsOf(foliage).tallGrass().get();
    }

    /** The fern of a given foliage colour. */
    public static Block slimeFern(FoliageType foliage) {
        return plantsOf(foliage).fern().get();
    }

    /** The leaves of a given foliage colour. */
    public static Block slimeLeaves(FoliageType foliage) {
        return plantsOf(foliage).leaves().get();
    }

    /** Whether {@code block} is one of the slime leaves -- what a canopy vine hangs from (#488). */
    public static boolean isSlimeLeaves(Block block) {
        return SLIME_PLANTS.stream().anyMatch(plants -> plants.leaves().get() == block);
    }

    /** Every plant of a given foliage colour. */
    public static SlimePlants slimePlants(FoliageType foliage) {
        return plantsOf(foliage);
    }

    private static SlimePlants plantsOf(FoliageType foliage) {
        return SLIME_PLANTS.stream().filter(plants -> plants.foliage() == foliage).findFirst()
                .orElseThrow(() -> new IllegalStateException("no slime plants registered for " + foliage));
    }

    private static SlimeSoil slimeSoil(String color, FoliageType foliage, MapColor mapColor) {
        // Upstream BlockSlimeDirt: Material.GROUND, hardness 0.55, SoundType.SLIME. BlockSlimeGrass:
        // Material.GRASS, hardness 0.65, SoundType.PLANT, slipperiness default + 0.05 (0.65) and
        // setTickRandomly for its spread.
        DeferredBlock<Block> dirt = BLOCKS.registerSimpleBlock(color + "_slime_dirt",
                BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .strength(0.55F)
                        .sound(SoundType.SLIME_BLOCK));
        DeferredBlock<SlimeGrassBlock> grass = BLOCKS.register(color + "_slime_grass",
                () -> new SlimeGrassBlock(BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .strength(0.65F)
                        .friction(0.65F)
                        .randomTicks()
                        .sound(SoundType.GRASS), foliage));
        SlimeSoil soil = new SlimeSoil(foliage, dirt, grass);
        SLIME_SOILS.add(soil);
        return soil;
    }

    private static SlimePlants slimePlants(FoliageType foliage, MapColor mapColor, boolean hasVines) {
        // Upstream BlockSlimeLeaves: hardness 0.3, vanilla leaves behaviour otherwise. Persistent by
        // default because a slime tree's trunk is congealed slime, not a log: upstream's own decay
        // never triggers either (its 1.12 leaf decay only starts when a nearby *wood* block breaks),
        // and modern LeavesBlock would otherwise delete every worldgen canopy on the first tick.
        DeferredBlock<Block> leaves = BLOCKS.register(foliage.id() + "_slime_leaves",
                () -> new LeavesBlock(BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .strength(0.3F)
                        .randomTicks()
                        .sound(SoundType.GRASS)
                        .noOcclusion()
                        .isValidSpawn((state, level, pos, type) -> false)
                        .isSuffocating((state, level, pos) -> false)
                        .isViewBlocking((state, level, pos) -> false)));
        DeferredBlock<SlimeTallGrassBlock> tallGrass = slimePlant(foliage, mapColor, "slime_tall_grass");
        DeferredBlock<SlimeTallGrassBlock> fern = slimePlant(foliage, mapColor, "slime_fern");

        // #488 (parity audit T57) -- upstream BlockSlimeSapling: BlockSapling behaviour with
        // SoundType.PLANT, and BlockSlimeVine: BlockVine behaviour with SoundType.PLANT. The vine's
        // three stages are registered end-first so each can hand the next to its constructor, which
        // is upstream's own registration order in TinkerWorld#registerBlocks.
        DeferredBlock<SlimeSaplingBlock> sapling = BLOCKS.register(foliage.id() + "_slime_sapling",
                () -> new SlimeSaplingBlock(BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .noCollission()
                        .randomTicks()
                        .instabreak()
                        .sound(SoundType.GRASS)
                        .pushReaction(PushReaction.DESTROY), foliage));
        DeferredBlock<SlimeVineBlock> vineEnd = hasVines ? slimeVine(foliage, mapColor, "_slime_vine_end", null) : null;
        DeferredBlock<SlimeVineBlock> vineMid = hasVines ? slimeVine(foliage, mapColor, "_slime_vine_mid", vineEnd) : null;
        DeferredBlock<SlimeVineBlock> vine = hasVines ? slimeVine(foliage, mapColor, "_slime_vine", vineMid) : null;

        SlimePlants plants = new SlimePlants(foliage, leaves, tallGrass, fern, sapling, vine, vineMid, vineEnd);
        SLIME_PLANTS.add(plants);
        return plants;
    }

    private static DeferredBlock<SlimeVineBlock> slimeVine(FoliageType foliage, MapColor mapColor, String suffix,
                                                           DeferredBlock<SlimeVineBlock> nextStage) {
        return BLOCKS.register(foliage.id() + suffix,
                () -> new SlimeVineBlock(BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .replaceable()
                        .noCollission()
                        .strength(0.2F)
                        .randomTicks()
                        .sound(SoundType.VINE)
                        .ignitedByLava()
                        .pushReaction(PushReaction.DESTROY), foliage,
                        nextStage == null ? null : nextStage::get));
    }

    private static DeferredBlock<SlimeTallGrassBlock> slimePlant(FoliageType foliage, MapColor mapColor, String name) {
        return BLOCKS.register(foliage.id() + "_" + name,
                () -> new SlimeTallGrassBlock(BlockBehaviour.Properties.of()
                        .mapColor(mapColor)
                        .replaceable()
                        .noCollission()
                        .instabreak()
                        .sound(SoundType.GRASS)
                        .offsetType(BlockBehaviour.OffsetType.XYZ)
                        .pushReaction(PushReaction.DESTROY), foliage));
    }

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
