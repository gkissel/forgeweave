package dev.gkissel.forgeweave.fluid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathType;

import net.neoforged.neoforge.common.SoundActions;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * The nine molten metal fluids (docs/SCOPE.md M2 issue #92): iron, copper, gold, cobalt, ardite,
 * manyullyn, rose gold, netherite, netherite scrap. Temperatures and tint colors for the six with a
 * 1.12 counterpart are ported from upstream's {@code TinkerFluids#setupFluids} (fluid temperature)
 * and {@code TinkerMaterials}'s {@code materialTextColor} (NOTICE.md); every molten metal there
 * shares one greyscale still/flowing texture pair ({@code FluidMolten}'s default {@code
 * ICON_MetalStill}/{@code ICON_MetalFlowing}) tinted per fluid, which this class ports the same way
 * via {@code dev.gkissel.forgeweave.client.ForgeweaveFluidClientExtensions} rather than one texture
 * per metal.
 *
 * <p>Rose gold, netherite and netherite scrap have no 1.12 counterpart -- their temperature and
 * color are a maintainer pick consistent with upstream's scale (alloys run hotter than their
 * inputs, same as manyullyn running hotter than cobalt/ardite), recorded as a deviation in the
 * issue #92 PR rather than an upstream-derived constant.
 *
 * <p>#286 reverses the earlier scope decision recorded here ("no bucket item is registered: M2's
 * smeltery moves fluid through tanks/faucets/casting, not buckets"). Maintainer decision on issue
 * #286 (2026-08-14): every molten fluid gets a bucket, matching upstream 1.12, where every smeltery
 * fluid is bucketable -- {@code TinkerFluids#registerItems} calls {@code
 * FluidRegistry.addBucketForFluid} for each non-metal fluid and {@code MaterialIntegration#preInit}
 * does the same for every registered material's molten fluid. Buckets also make each fluid
 * browsable in JEI, which had no other item form to hang a fluid off (0.3.2 playtest note).
 * {@link #register} therefore registers one {@link BucketItem} per fluid and wires it both ways --
 * {@code BaseFlowingFluid.Properties#bucket} so {@code Fluid#getBucket} answers it (pickup), and
 * the {@code BucketItem}'s own {@code content} so emptying it places the fluid back.
 */
public final class ForgeweaveFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Forgeweave.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Forgeweave.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Forgeweave.MODID);
    /** #286 -- the per-fluid buckets. Their own register rather than {@code ForgeweaveItems.ITEMS} so a fluid stays defined in one file. */
    public static final DeferredRegister.Items BUCKETS = DeferredRegister.createItems(Forgeweave.MODID);

    // Every fluid this class registers, in declaration order. Client tints, bucket item models,
    // lang keys and the creative tab all walk this instead of keeping their own hand list -- the
    // exact drift that shipped #256's untinted parts and #139's missing creative-tab entries.
    private static final List<MoltenMetal> ALL = new ArrayList<>();
    private static final List<MoltenMetal> ALL_VIEW = Collections.unmodifiableList(ALL);

    /** Every registered molten fluid, in declaration order. */
    public static List<MoltenMetal> all() {
        return ALL_VIEW;
    }

    private static final ResourceLocation STILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/molten_metal");
    private static final ResourceLocation FLOWING_TEXTURE = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/molten_metal_flow");

    // #233 -- the two non-metal smeltery fluids' own texture pairs, ported the same way the shared
    // molten metal pair was: upstream's FluidColored.ICON_LiquidStill/Flowing (blood) and
    // ICON_StoneStill/Flowing (molten clay), each greyscale-ish base tinted per fluid (NOTICE.md).
    private static final ResourceLocation LIQUID_STILL = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid");
    private static final ResourceLocation LIQUID_FLOWING = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_flow");
    private static final ResourceLocation STONE_STILL = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_stone");
    private static final ResourceLocation STONE_FLOWING = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_stone_flow");

    /** A registered smeltery fluid: its registry name, its client tint and textures, its {@link BucketItem} (#286), and the smeltery-fuel-gating temperature that governs it (upstream's {@code FluidType#getTemperature}). */
    public record MoltenMetal(String name, DeferredHolder<FluidType, FluidType> fluidType, DeferredHolder<Fluid, FlowingFluid> still,
            DeferredHolder<Fluid, FlowingFluid> flowing, DeferredBlock<LiquidBlock> block, DeferredItem<BucketItem> bucket,
            int color, int temperature, ResourceLocation stillTexture, ResourceLocation flowingTexture) {}

    // Ported 1:1 from TinkerFluids#setupFluids (temperature) and TinkerMaterials (materialTextColor).
    public static final MoltenMetal IRON = register("iron", 0xA81212, 769);
    public static final MoltenMetal COPPER = register("copper", 0xED9F07, 542);
    public static final MoltenMetal GOLD = register("gold", 0xF6D609, 532);
    public static final MoltenMetal COBALT = register("cobalt", 0x2882D4, 950);
    public static final MoltenMetal ARDITE = register("ardite", 0xD14210, 860);
    public static final MoltenMetal MANYULLYN = register("manyullyn", 0xA15CF8, 1000);
    // #234 M3.2: steel, ported the same way (TinkerFluids#setupFluids 681; materialTextColor 0xa7a7a7).
    public static final MoltenMetal STEEL = register("steel", 0xA7A7A7, 681);

    // M3.2 issue #231: upstream's TinkerFluids#setupFluids obsidian (fluidStone, 0x2c0d59, 1000).
    // Not a metal -- #285: it rides molten clay's stone still/flowing texture pair (upstream's own
    // FluidColored ICON_StoneStill/Flowing), not the shared metal texture.
    public static final MoltenMetal OBSIDIAN = register("molten_obsidian", 0x2C0D59, 1000,
            () -> moltenFluidType(1000), STONE_STILL, STONE_FLOWING);

    // Issue #884 (1): basalt replaces the retired "cinderstone" Track B ore -- a Part-Builder-only
    // material (material/basalt.json), not a TrackBOre entry, so it needs its own standalone fluid
    // registration rather than TRACK_B_ORE_TEMPERATURES' loop. The only thing that consumes this
    // fluid today is quakestone's alloy recipe (cinderstone's old input, swapped to basalt); rides
    // obsidian's stone texture pair for the same reason obsidian does -- it is rock, not metal.
    // Temperature kept at cinderstone's old 850 (below lava, the roster's one stone-tier slot).
    public static final MoltenMetal BASALT = register("molten_basalt", 0x5C5F66, 850,
            () -> moltenFluidType(850), STONE_STILL, STONE_FLOWING);

    // No 1.12 counterpart -- deviation recorded in the issue #92 PR (see class javadoc).
    public static final MoltenMetal ROSE_GOLD = register("rose_gold", 0xB76E79, 550);
    public static final MoltenMetal NETHERITE_SCRAP = register("netherite_scrap", 0x6B4A34, 1100);
    public static final MoltenMetal NETHERITE = register("netherite", 0x4A3B47, 1200);
    // #234: molten carbon, steel's alloy partner (coal/charcoal melt into it). Upstream 1.12 has no
    // steel recipe at all, so this fluid and its 600-degree temperature are a maintainer pick on
    // issue #234, slotted between copper (542) and steel's own 681 on upstream's scale.
    public static final MoltenMetal CARBON = register("carbon", 0x31302E, 600);

    // #232 -- the knightslime alloy chain (docs/SCOPE.md M3.2). Knightslime and seared stone are
    // straight 1.12 ports: TinkerFluids' knightslime (fluidMetal of TinkerMaterials.knightslime's
    // 0xf18ff0, temperature 520) and searedStone (0x777777, temperature 800). Molten slime is the
    // maintainer-decided green substitute for upstream's purple slime alloy input (issue #232): its
    // color is the slime material's own 0x82c873 and its temperature is upstream's slime-fluid 310
    // (TinkerFluids#blueslime, the 1.12 generation's slime fluid temperature).
    // #285: molten slime gets its own FluidType, de-tuned from the shared lava-tier density/viscosity
    // the same way upstream's TinkerFluids#slime does (cool(name).density(1600).viscosity(1600) vs.
    // hot()'s density(2000).viscosity(10000)) -- still lava-hazardous like every other molten fluid
    // here, just thinner.
    public static final MoltenMetal SLIME = register("molten_slime", 0x82c873, 310,
            () -> slimeFluidType(310), STILL_TEXTURE, FLOWING_TEXTURE);
    // #285: seared stone rides the stone still/flowing texture pair too (see OBSIDIAN above).
    public static final MoltenMetal SEARED_STONE = register("molten_seared_stone", 0x777777, 800,
            () -> moltenFluidType(800), STONE_STILL, STONE_FLOWING);
    public static final MoltenMetal KNIGHTSLIME = register("knightslime", 0xf18ff0, 520);

    // #233 -- the pig iron alloy chain (docs/SCOPE.md M3.2, maintainer decision on the issue: real
    // blood, no substitute). Pig iron is a molten metal like the nine above (TinkerFluids: 600,
    // TinkerMaterials.pigiron's 0xef9e9b). Blood and molten clay are upstream's two non-metal
    // smeltery fluids: blood is a water-like FluidColored ("classic", 336, 0x540000, no glow) and
    // clay a stone-textured FluidMolten (700, 0xc67453) -- each keeps its upstream texture pair
    // rather than the shared metal one (NOTICE.md).
    public static final MoltenMetal PIG_IRON = register("pig_iron", 0xEF9E9B, 600);
    public static final MoltenMetal BLOOD = register("blood", 0x540000, 336,
            () -> new FluidType(FluidType.Properties.create().temperature(336)), LIQUID_STILL, LIQUID_FLOWING);
    public static final MoltenMetal MOLTEN_CLAY = register("molten_clay", 0xC67453, 700,
            () -> moltenFluidType(700), STONE_STILL, STONE_FLOWING);

    // #502 (T71 parity audit): molten dirt, upstream's third and last non-metal smeltery fluid
    // (TinkerFluids#dirt: fluidStone("dirt", 0xa68564), temperature 500, NOTICE.md). Upstream's own
    // registry name is bare "dirt" (fluidStone("dirt", ...), the same helper that names MOLTEN_CLAY
    // "clay" upstream); named "molten_dirt" here for the same reason MOLTEN_CLAY departs from its
    // own upstream id -- consistency with the "molten_" family every other non-blood fluid in this
    // class uses. Rides the same stone still/flowing texture pair as MOLTEN_CLAY and OBSIDIAN above.
    // Melting any dirt block and casting it back out as mud bricks lives in the datapack recipes
    // (melting_recipe/casting_recipe), not Java, same as every other smeltery recipe here.
    public static final MoltenMetal MOLTEN_DIRT = register("molten_dirt", 0xA68564, 500,
            () -> moltenFluidType(500), STONE_STILL, STONE_FLOWING);

    // #235 M3.2: amethyst and amethyst bronze, from the 1.20 clone (the by-name modern-branch
    // additions, docs/SCOPE.md M3.2). The 1.20 branch measures its fluid temperatures 300 degrees
    // above the 1.12 scale this class uses (its obsidian is 1300 where 1.12's -- and OBSIDIAN
    // above -- is 1000), so TinkerFluids#moltenAmethyst 1250 and #moltenAmethystBronze 1120 land
    // here as 950 and 820. Tints are the clone's mantle/colors.json material colours (NOTICE.md);
    // like OBSIDIAN, amethyst is not a metal but rides the same shared tinted texture.
    public static final MoltenMetal AMETHYST = register("amethyst", 0xB38EF1, 950);
    public static final MoltenMetal AMETHYST_BRONZE = register("amethyst_bronze", 0xC687BD, 820);

    // Molten emerald is what the #270 entity-melting parity set's villager/vindicator/evoker/illusioner
    // row pours, and what #272's gem cast consumes. Ported 1:1 from TinkerFluids#setupFluids
    // (fluidMetal("emerald", 0x58e78e), setTemperature(999)); like AMETHYST it is a gem rather than a
    // metal but rides the same shared tinted texture upstream does.
    public static final MoltenMetal EMERALD = register("emerald", 0x58E78E, 999);

    // #473 (T42) -- molten glass, what sand/glass/panes melt into and what casts back out as a glass
    // pane or clear glass. Ported 1:1 from TinkerFluids#setupFluids (fluidMetal("glass", 0xc0f5fe),
    // setTemperature(625)); a fluidMetal, so like EMERALD and AMETHYST it rides the shared tinted
    // metal texture pair rather than the stone one OBSIDIAN/MOLTEN_CLAY/MOLTEN_DIRT use.
    public static final MoltenMetal GLASS = register("glass", 0xC0F5FE, 625);

    // #270 -- the two blood variants the maintainer added on top of the 1.12 set. Both are bloods, so
    // both are named without a molten_ prefix the way BLOOD is.
    //
    // Blazing blood has no 1.12 counterpart but does exist by name in the 1.20 clone
    // (TinkerFluids#blazingBlood: temperature 1800, lightLevel 15, density 3500), so it lands the same
    // way AMETHYST did -- the 1.20 branch's fluid temperatures sit 300 above the 1.12 scale this class
    // uses, making its 1800 a 1500 here. That is hotter than every other fluid in this file and hotter
    // than lava's own 1000 on the 1.12 smeltery scale, which is the point (issue #270: "future hot
    // smeltery fuel, hotter than lava"). Its tint is the 1.20 clone's mantle/colors.json "blaze"
    // (#FFC100), and it takes the shared molten-metal lava-like FluidType rather than a bespoke one --
    // ponytail: upstream's lightLevel 15 and density 3500 are cosmetic next to the temperature that
    // actually gates fuel, so it shares moltenFluidType until something needs them apart.
    public static final MoltenMetal BLAZING_BLOOD = register("blazing_blood", 0xFFC100, 1500,
            () -> moltenFluidType(1500), STILL_TEXTURE, FLOWING_TEXTURE);

    // Deep blood has no counterpart in either clone -- the warden it comes from postdates both. It is
    // a maintainer addition on issue #270 (future Deep-smeltery reagent, see #181), so it inherits
    // BLOOD's own shape wholesale: the same water-like non-hazardous FluidType at blood's own 1.12
    // temperature of 336, and the same classic liquid texture pair. Only the tint differs, taken from
    // the sculk palette the warden is drawn in.
    public static final MoltenMetal DEEP_BLOOD = register("deep_blood", 0x1B4B4E, 336,
            () -> new FluidType(FluidType.Properties.create().temperature(336)), LIQUID_STILL, LIQUID_FLOWING);

    // #844 -- meltable dragon breath (issue #181): the sibling issue #845 pours this into a Nether
    // Core smeltery to transform it into an End Core, but the fluid itself and its melting recipe
    // belong here with the rest of the fluid work. No counterpart in either upstream clone -- 1.12
    // has no dragon-breath fluid at all, so temperature and tint are a maintainer pick recorded as a
    // deviation here rather than a port: BLOOD/DEEP_BLOOD's own non-hazardous water-like FluidType
    // (a vapor, not a molten metal, so no lava-like burn/drown behavior), tinted the vanilla dragon's
    // breath particle's own purple, at a mild 400 degrees -- just past ambient, since nothing about
    // #845's pour-to-transform mechanic depends on this fluid running hot.
    public static final MoltenMetal DRAGON_BREATH = register("molten_dragon_breath", 0x9B3DA5, 400,
            () -> new FluidType(FluidType.Properties.create().temperature(400)), LIQUID_STILL, LIQUID_FLOWING);

    // #625 (parity audit T18/T57) -- the two cool slime fluids a slime island's lake is filled with,
    // upstream's TinkerFluids#blueslime (0xef67f0f5, temperature 310, viscosity and density 1500) and
    // #purpleSlime (0xefd236ff, 370, 1600), both fluidClassic and so both on the FluidColored
    // ICON_LiquidStill/Flowing pair the blood fluids above already ride. Upstream's leading 0xef is
    // the render alpha its FluidColored packs into one int; every Forgeweave tint is plain 0xRRGGBB
    // (ForgeweaveFluidClientExtensions forces alpha opaque), so only the colour half carries over.
    //
    // Unlike every fluid above these are *not* lava-like: upstream registers their blocks as
    // BlockLiquidSlime(fluid, Material.WATER) -- swimmable, non-damaging, water pathfinding and
    // water bucket sounds -- which is what slimeFluidType below builds. Their temperature is
    // upstream's own and gates nothing today; the melting recipes that pour them are T57 (#635).
    public static final MoltenMetal BLUE_SLIME = register("blue_slime", 0x67F0F5, 310,
            () -> waterLikeSlimeFluidType(310, 1500), LIQUID_STILL, LIQUID_FLOWING);
    public static final MoltenMetal PURPLE_SLIME = register("purple_slime", 0xD236FF, 370,
            () -> waterLikeSlimeFluidType(370, 1600), LIQUID_STILL, LIQUID_FLOWING);

    // #843 (closes #180) -- the 1.20-branch material gap's two brand-new T4 alloy fluids and their
    // two prerequisite fluids, all with no 1.12 counterpart, so all four follow the AMETHYST/
    // AMETHYST_BRONZE precedent (issue #235): the 1.20 clone's own temperatures land 300 degrees
    // above the 1.12 scale this class uses, so its moltenHepatizon 1700 and moltenQueensSlime 1450
    // become 1400 and 1150 here. Tints are the clone's mantle/colors.json material colours (NOTICE.md).
    public static final MoltenMetal QUEENS_SLIME = register("queens_slime", 0x236C45, 1150);
    public static final MoltenMetal HEPATIZON = register("hepatizon", 0x60496B, 1400);
    // Prerequisite fluids the audit named: a liquid magma cream (queens slime's alloy input, distinct
    // from the existing solid magma_slime_crystal item) and molten quartz (hepatizon's alloy input).
    // Neither carries a 1.12 counterpart either, so both take the same -300 treatment: magma cream's
    // 600 (TinkerFluids#magma) becomes 300, and quartz's 937 (TinkerFluids#moltenQuartz) becomes 637.
    // Tints are a maintainer pick (no material JSON of their own to source a colour from): magma
    // cream reuses the existing magmaslime material's own #ff960d, and quartz takes a pale quartz
    // tone.
    public static final MoltenMetal MAGMA_CREAM = register("magma_cream", 0xFF960D, 300);
    public static final MoltenMetal QUARTZ = register("quartz", 0xE8D5C4, 637);

    // #840 -- Track B's molten fluids (M6 epic #824, closing the loop #839/#864 left open: ore/ingot/
    // nugget/raw items existed with no molten form). Ore-metal fluids reuse each TrackBOre's own
    // ore-block tint (dev.gkissel.forgeweave.trackb.TrackBOre) rather than picking a second color, the
    // same "one color per material" precedent QUARTZ/MAGMA_CREAM's own picks already set; temperatures
    // are this issue's own design decision (deliverable 2), placed on the existing lava(1000)/blazing
    // blood(1500) scale by tier -- the one stone-tier ore lands below lava, the one diamond-tier ore
    // near it, and the ten netherite-tier ores spread across 1080-1280.
    private static final Map<String, Integer> TRACK_B_ORE_TEMPERATURES = Map.ofEntries(
            Map.entry("fulmenite", 980),
            Map.entry("duskspar", 1080), Map.entry("voltcinder", 1100), Map.entry("murkiron", 1120),
            Map.entry("hardcinder", 1140), Map.entry("nightshale", 1160), Map.entry("warspar", 1180),
            Map.entry("hollowstone", 1200), Map.entry("resonite", 1220), Map.entry("starfall_stone", 1240),
            Map.entry("voidglass", 1260));

    private static final Map<String, MoltenMetal> TRACK_B_ORE_FLUIDS = new LinkedHashMap<>();
    private static final Map<String, MoltenMetal> TRACK_B_ALLOY_FLUIDS = new LinkedHashMap<>();

    static {
        for (TrackBOre ore : TrackBOre.ALL) {
            TRACK_B_ORE_FLUIDS.put(ore.id(), register(ore.id(), ore.color(), TRACK_B_ORE_TEMPERATURES.get(ore.id())));
        }
        // The 18 alloy tool materials (research doc §7.3 "Alloy" table): see TrackBAlloy's own javadoc
        // for the color/temperature design rationale.
        for (TrackBAlloy alloy : TrackBAlloy.ALL) {
            TRACK_B_ALLOY_FLUIDS.put(alloy.id(), register(alloy.id(), alloy.color(), alloy.temperature()));
        }
    }

    /** A Track B ore-metal fluid by material id (e.g. {@code "cinderstone"}), or {@code null} if unknown. */
    public static MoltenMetal trackBOreFluid(String id) {
        return TRACK_B_ORE_FLUIDS.get(id);
    }

    /** A Track B alloy fluid by material id, or {@code null} if unknown. */
    public static MoltenMetal trackBAlloyFluid(String id) {
        return TRACK_B_ALLOY_FLUIDS.get(id);
    }

    // #873 -- the JC3 reversal (M6 epic #824, session 2 2026-08-31): every Track A / recovery compat
    // metal gets full smeltery integration instead of Part-Builder-only. Registered unconditionally in
    // Java (the same NeoForge platform constraint every fluid in this file lives under) and hidden from
    // creative/JEI when the backing material's own provider is absent -- see
    // dev.gkissel.forgeweave.material.CompatMaterialAvailability, consulted by ForgeweaveCreativeTab
    // rather than duplicated here. Colors are each material's own existing `color` field (its Part
    // Builder tint); temperatures are this issue's own design pick (deliverable 1), banded by the
    // material's harvest tier on the established 700-1330 sub-lava-to-near-blazing-blood scale (stone
    // ~720-810, iron ~900-1030, diamond ~1040-1140, netherite ~1150-1330) with a small per-tier spread
    // so same-tier metals do not all melt at one identical temperature -- the same spread technique
    // TRACK_B_ORE_TEMPERATURES above already uses. Excluded (gems/crystals/organics upstream never
    // treated as meltable, or a non-metal synthetic -- listed in the PR): black_quartz, certus_quartz,
    // diamatine_crystal, dragonyst, emeradic_crystal, enori_crystal, fluix, fluorite, hdpe,
    // palis_crystal, psigem, restonia_crystal, sky_stone, void_crystal.
    public static final MoltenMetal ALUMINIUM = register("aluminium", 0xC6C7C8, 720);
    public static final MoltenMetal BRONZE = register("bronze", 0xE3BD68, 900);
    public static final MoltenMetal CONDUCTIVE_ALLOY = register("conductive_alloy", 0xC4732E, 916);
    public static final MoltenMetal CONSTANTAN = register("constantan", 0xA8794A, 932);
    public static final MoltenMetal DARK_STEEL = register("dark_steel", 0x272727, 1160);
    public static final MoltenMetal DRACONIUM_AWAKENED = register("draconium_awakened", 0xB813B2, 1172);
    public static final MoltenMetal DRACONIUM = register("draconium", 0x00BA99, 1184);
    public static final MoltenMetal ELECTRUM = register("electrum", 0xE8DB49, 738);
    public static final MoltenMetal END_STEEL = register("end_steel", 0xB29FE0, 1196);
    public static final MoltenMetal ENERGETIC_ALLOY = register("energetic_alloy", 0xD8A23C, 948);
    public static final MoltenMetal IESNIUM = register("iesnium", 0x5C3A21, 1208);
    public static final MoltenMetal INVAR = register("invar", 0xD1CDB9, 964);
    public static final MoltenMetal IRIDIUM = register("iridium", 0xEBEBFF, 1220);
    public static final MoltenMetal LEAD = register("lead", 0x4D4968, 756);
    public static final MoltenMetal NICKEL = register("nickel", 0xC7C2AC, 980);
    public static final MoltenMetal OSMIUM = register("osmium", 0x5764DB, 996);
    public static final MoltenMetal PLATINUM = register("platinum", 0xE5E5E5, 1040);
    public static final MoltenMetal PSIMETAL = register("psimetal", 0x6FCEDF, 1012);
    public static final MoltenMetal EBONY_PSIMETAL = register("ebony_psimetal", 0x221E26, 1054);
    public static final MoltenMetal IVORY_PSIMETAL = register("ivory_psimetal", 0xF5F0E1, 1068);
    public static final MoltenMetal PULSATING_ALLOY = register("pulsating_alloy", 0x7368C7, 1028);
    public static final MoltenMetal REDSTONE_ALLOY = register("redstone_alloy", 0xB0413E, 774);
    public static final MoltenMetal REFINED_GLOWSTONE = register("refined_glowstone", 0xF9E75B, 1082);
    public static final MoltenMetal REFINED_OBSIDIAN = register("refined_obsidian", 0x2E1743, 1232);
    public static final MoltenMetal SILVER = register("silver", 0xD1ECF6, 792);
    public static final MoltenMetal SOULARIUM = register("soularium", 0xD9C98A, 1096);
    public static final MoltenMetal TIN = register("tin", 0xD4D4D4, 810);
    public static final MoltenMetal TITANIUM = register("titanium", 0x8A8F92, 1110);
    public static final MoltenMetal TUNGSTEN = register("tungsten", 0x545454, 1124);
    public static final MoltenMetal URANIUM = register("uranium", 0xA8B84B, 1138);
    public static final MoltenMetal VIBRANT_ALLOY = register("vibrant_alloy", 0x4FD8B0, 1152);
    public static final MoltenMetal PINK_SLIME = register("pink_slime", 0xF49AC1, 1166);
    public static final MoltenMetal GRAPHITE = register("graphite", 0x36393B, 828);
    public static final MoltenMetal DARK_MATTER = register("dark_matter", 0x1A0A26, 1244);
    public static final MoltenMetal RED_MATTER = register("red_matter", 0xBD2645, 1256);
    public static final MoltenMetal COSMIC_NEUTRONIUM = register("cosmic_neutronium", 0x3D1A5C, 1268);
    public static final MoltenMetal CRYSTAL_MATRIX = register("crystal_matrix", 0x7FD9F5, 1280);
    public static final MoltenMetal INFINITY = register("infinity", 0xF5E9C8, 1292);
    public static final MoltenMetal CHAOTIC = register("chaotic", 0x6A1B9A, 1304);
    public static final MoltenMetal WYVERN = register("wyvern", 0x1F7A4D, 1316);
    public static final MoltenMetal QUARTZ_ENRICHED_IRON = register("quartz_enriched_iron", 0xC9A96A, 846);
    public static final MoltenMetal SILICON = register("silicon", 0x4A4A4A, 864);
    public static final MoltenMetal ENERGISED_STEEL = register("energised_steel", 0xFACC50, 1180);
    public static final MoltenMetal BLUTONIUM = register("blutonium", 0x3A5FE0, 1194);
    public static final MoltenMetal CYANITE = register("cyanite", 0x1B1F3B, 1208);
    public static final MoltenMetal LUDICRITE = register("ludicrite", 0xE066CC, 1328);
    public static final MoltenMetal URANINITE = register("uraninite", 0x7FA83B, 1222);

    private static final Map<String, MoltenMetal> COMPAT_METAL_FLUIDS = Map.ofEntries(
            Map.entry("aluminium", ALUMINIUM), Map.entry("bronze", BRONZE),
            Map.entry("conductive_alloy", CONDUCTIVE_ALLOY), Map.entry("constantan", CONSTANTAN),
            Map.entry("dark_steel", DARK_STEEL), Map.entry("draconium_awakened", DRACONIUM_AWAKENED),
            Map.entry("draconium", DRACONIUM), Map.entry("electrum", ELECTRUM),
            Map.entry("end_steel", END_STEEL), Map.entry("energetic_alloy", ENERGETIC_ALLOY),
            Map.entry("iesnium", IESNIUM), Map.entry("invar", INVAR), Map.entry("iridium", IRIDIUM),
            Map.entry("lead", LEAD), Map.entry("nickel", NICKEL), Map.entry("osmium", OSMIUM),
            Map.entry("platinum", PLATINUM), Map.entry("psimetal", PSIMETAL),
            Map.entry("ebony_psimetal", EBONY_PSIMETAL), Map.entry("ivory_psimetal", IVORY_PSIMETAL),
            Map.entry("pulsating_alloy", PULSATING_ALLOY), Map.entry("redstone_alloy", REDSTONE_ALLOY),
            Map.entry("refined_glowstone", REFINED_GLOWSTONE), Map.entry("refined_obsidian", REFINED_OBSIDIAN),
            Map.entry("silver", SILVER), Map.entry("soularium", SOULARIUM), Map.entry("tin", TIN),
            Map.entry("titanium", TITANIUM), Map.entry("tungsten", TUNGSTEN), Map.entry("uranium", URANIUM),
            Map.entry("vibrant_alloy", VIBRANT_ALLOY), Map.entry("pink_slime", PINK_SLIME),
            Map.entry("graphite", GRAPHITE), Map.entry("dark_matter", DARK_MATTER),
            Map.entry("red_matter", RED_MATTER), Map.entry("cosmic_neutronium", COSMIC_NEUTRONIUM),
            Map.entry("crystal_matrix", CRYSTAL_MATRIX), Map.entry("infinity", INFINITY),
            Map.entry("chaotic", CHAOTIC), Map.entry("wyvern", WYVERN),
            Map.entry("quartz_enriched_iron", QUARTZ_ENRICHED_IRON), Map.entry("silicon", SILICON),
            Map.entry("energised_steel", ENERGISED_STEEL), Map.entry("blutonium", BLUTONIUM),
            Map.entry("cyanite", CYANITE), Map.entry("ludicrite", LUDICRITE), Map.entry("uraninite", URANINITE));

    /** A compat metal's molten fluid by material id (e.g. {@code "bronze"}), or {@code null} if unknown. */
    public static MoltenMetal compatMetalFluid(String id) {
        return COMPAT_METAL_FLUIDS.get(id);
    }

    // The 6 smeltery-only catalysts (research doc §7.3 "Smeltery-only ingredients"): no tool stats,
    // no ingot/nugget/block item of their own -- deliverable 5's "fluids/items with no Material entry
    // at all" branch, picked over a stats-less Material JSON because nothing ever needs to carry one
    // of these as a solid item outside the smeltery (see the melting_recipe rows this issue ships,
    // which source each one straight from a common vanilla item rather than a Forgeweave ore/ingot).
    // Colors/temperatures are this issue's own pick, themed to each id; none needs a TrackBOre-style
    // roster class since nothing else in the codebase has to walk this list of seven by material id.
    public static final MoltenMetal FLAREALLOY = register("flarealloy", 0xFF7A1A, 900);
    public static final MoltenMetal DEEPALLOY = register("deepalloy", 0x123A3A, 950);
    public static final MoltenMetal SPARKALLOY = register("sparkalloy", 0xEAE92B, 920);
    public static final MoltenMetal REDCINDER = register("redcinder", 0xB22621, 880);
    public static final MoltenMetal PEARLCINDER = register("pearlcinder", 0xE8C9D6, 860);
    public static final MoltenMetal AMBERCINDER = register("ambercinder", 0xC9862A, 870);
    // #910 retired the seventh catalyst, twinalloy (a 910-degree fuel melted from amethyst shards):
    // brimspar below already fills the same role -- a mined fluid that is both a fuel and an alloy
    // input -- so the two merged, and every alloy recipe that took molten_twinalloy now takes
    // molten_brimspar at the same amount (scripts/generate_track_b_recipes.py). The ladder loses its
    // sub-lava rung with it: nothing burns below lava any more, which is also the shape of the TAIGA
    // ladder this rung was mapped from (inspiration only, CLAUDE.md).

    // #903 -- the fuel ladder's two mined rungs, both fuel-only like PYREALLOY below (no tool stats,
    // no ingot/nugget/block, no Material JSON). Their 200-degree steps continue the ladder the
    // maintainer confirmed on #897 and extended on #903/#910: lava 1300 -> blazing blood 1500 ->
    // molten magma 1700 -> molten brimspar 1900 -> pyrealloy 2100.
    //
    // Molten magma is what a vanilla magma block melts into (melting_recipe/magma_block.json). Unlike
    // the two below it is also an alloy *input*: pyrealloy is alloyed from it plus flarealloy (#903
    // re-bases that recipe off lava). Tint is the magma block's own ember red.
    public static final MoltenMetal MOLTEN_MAGMA = register("magma", 0x92002C, 1700);
    // Molten brimspar is what the Nether's brimspar crystals melt into
    // (melting_recipe/brimspar_crystal.json, dev.gkissel.forgeweave.block.UnstableOreBlock). It burns
    // as smeltery_fuel/brimspar.json and, since #910 folded twinalloy into it, is also the catalyst
    // input quakestone's alternate and both glowveil recipes take (32 mB, twinalloy's own old amount).
    // Tint is a sulfurous brimstone yellow, the crystal's own flavor color (the same hex
    // UnstableOreBlock#BRIMSPAR_CRYSTAL_COLOR feeds the art script).
    public static final MoltenMetal BRIMSPAR = register("brimspar", 0x0FBD59, 1900);

    // #897 -- the smeltery fuel ladder's top rung. Fuel-only like the six catalysts above (no tool
    // stats, no ingot/nugget/block, no Material JSON), but unlike them it is never an alloy *input*:
    // nothing consumes it except smeltery_fuel/pyrealloy.json. Its 2100 is the ladder's own design
    // number (see MOLTEN_MAGMA above for the full five-rung ladder), sitting far enough above the rung
    // below it to be worth the alloy chain and well short of the 3100 the TAIGA fluid that inspired
    // the rung ran at (inspiration only, see CLAUDE.md -- no code, numbers or assets taken). Tint is a
    // white-hot step past flarealloy's own ember orange, the catalyst it is alloyed from. #903
    // re-bases its alloy_recipe onto molten magma in place of lava, TAIGA's own magma+catalyst shape.
    //
    // #910 also makes it the ladder's one *long-burn* fuel: smeltery_fuel/pyrealloy.json drains
    // 100 mB per cycle but burns for 500 melt ticks, against every other fuel's 50 mB / 100 ticks --
    // twice the fuel for five times the burn, i.e. 2.5x the work per mB, the reward for finishing the
    // alloy chain rather than only a hotter number.
    public static final MoltenMetal PYREALLOY = register("pyrealloy", 0xFF58C2, 2100);

    private static MoltenMetal register(String metalId, int color, int temperature) {
        return register("molten_" + metalId, color, temperature, () -> moltenFluidType(temperature),
                STILL_TEXTURE, FLOWING_TEXTURE);
    }

    private static MoltenMetal register(String name, int color, int temperature,
            Supplier<FluidType> typeFactory, ResourceLocation stillTexture, ResourceLocation flowingTexture) {
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, typeFactory);

        // Source, Flowing and the LiquidBlock each need a supplier pointing at one of the other two,
        // none of which exist as Java values yet at this point in the method (BaseFlowingFluid
        // .Properties needs the block below; LiquidBlock needs the still fluid above). These
        // one-element arrays give the properties supplier a slot to read that gets filled in a few
        // lines later -- every lambda here only actually runs at NeoForge's RegisterEvent, well after
        // this method has returned and every slot is filled.
        @SuppressWarnings("unchecked")
        DeferredHolder<Fluid, FlowingFluid>[] stillRef = new DeferredHolder[1];
        @SuppressWarnings("unchecked")
        DeferredHolder<Fluid, FlowingFluid>[] flowingRef = new DeferredHolder[1];
        @SuppressWarnings("unchecked")
        DeferredBlock<LiquidBlock>[] blockRef = new DeferredBlock[1];

        // #286 -- the fluid's bucket. The BucketItem factory reads stillRef[0] at the item registry's
        // own RegisterEvent, the same deferred read every other lambda in this method does, so it is
        // safe to register here before stillRef is filled in below.
        DeferredItem<BucketItem> bucket = BUCKETS.registerItem(name + "_bucket",
                props -> new BucketItem(stillRef[0].get(), props),
                // Vanilla's own bucket properties (see Items#LAVA_BUCKET): one per slot, and the
                // empty bucket comes back when the filled one is used up in a recipe.
                new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1));

        Supplier<BaseFlowingFluid.Properties> properties = () -> new BaseFlowingFluid.Properties(
                type, () -> stillRef[0].get(), () -> flowingRef[0].get())
                .block(() -> blockRef[0].get())
                // The pickup half of the round trip: LiquidBlock#pickupBlock hands out
                // `new ItemStack(fluid.getBucket())`, which is Items.AIR without this.
                .bucket(bucket);

        // #285: the block's light level derives from the fluid's own FluidType#getLightLevel()
        // instead of a hardcoded 10, so BLOOD (no lightLevel() call on its FluidType.Properties,
        // default 0) renders non-glowing the way its own javadoc above documents. Read via a throwaway
        // FluidType built from the same (pure, stateless) typeFactory rather than through the `type`
        // DeferredHolder above: BlockBehaviour precomputes each BlockState's light emission eagerly at
        // Block construction time (unlike the FluidState-based accessors this class otherwise defers
        // through stillRef/flowingRef), which lands before the fluid_type registry's own RegisterEvent
        // has necessarily bound `type` -- calling type.get() here throws.
        int lightLevel = typeFactory.get().getLightLevel();

        stillRef[0] = FLUIDS.register(name, () -> new BaseFlowingFluid.Source(properties.get()));
        flowingRef[0] = FLUIDS.register("flowing_" + name, () -> new BaseFlowingFluid.Flowing(properties.get()));
        blockRef[0] = BLOCKS.register(name, () -> new LiquidBlock(stillRef[0].get(), BlockBehaviour.Properties.of()
                .mapColor(MapColor.METAL)
                .replaceable()
                .noCollission()
                .strength(100f)
                .noLootTable()
                .liquid()
                .pushReaction(PushReaction.DESTROY)
                .lightLevel(state -> lightLevel)));

        MoltenMetal fluid = new MoltenMetal(name, type, stillRef[0], flowingRef[0], blockRef[0], bucket,
                color, temperature, stillTexture, flowingTexture);
        ALL.add(fluid);
        return fluid;
    }

    // #285: forge/upstream's lava motionScale, ported 1:1 (TinkerFluids#hot's "from forge lava type"
    // comment; both upstream clones' molten fluids use it -- 1.12's BlockMolten via Material.LAVA,
    // 1.20's hot() via this exact constant).
    private static final double LAVA_MOTION_SCALE = 0.0023333333333333335D;

    /**
     * Every molten fluid's shared lava-like entity hazard behavior (#285): no swimming, no drowning
     * (burns/damages like lava instead), lava's mob pathfinding avoidance, and lava's bucket sounds --
     * 1.12's {@code BlockMolten} extends {@code BlockTinkerFluid} with {@code Material.LAVA}; 1.20's
     * {@code TinkerFluids#hot} builds the modern equivalent property set this mirrors. Density and
     * viscosity are left to the caller since {@link #moltenFluidType} and {@link #slimeFluidType}
     * differ there.
     */
    private static FluidType.Properties lavaLikeProperties(int temperature) {
        return FluidType.Properties.create()
                .lightLevel(10)
                .temperature(temperature)
                .canSwim(false)
                .canDrown(false)
                .motionScale(LAVA_MOTION_SCALE)
                .pathType(PathType.LAVA)
                .adjacentPathType(null)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL_LAVA)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY_LAVA);
    }

    /** Package-visible so the temperature wiring can be exercised directly without a live registry (see {@code ForgeweaveFluidsTest}). Color is client-only ({@code ForgeweaveFluidClientExtensions}) and plays no part in {@link FluidType} itself. */
    static FluidType moltenFluidType(int temperature) {
        return new FluidType(lavaLikeProperties(temperature)
                .density(2000)
                .viscosity(10000));
    }

    /**
     * #625: the blue and purple slime fluids' {@link FluidType}. Upstream's {@code BlockLiquidSlime}
     * takes {@code Material.WATER}, so unlike every molten fluid above these are swimmable, do not
     * burn, path like water and use the water bucket sounds -- vanilla's own defaults, which is why
     * this sets nothing but upstream's temperature, viscosity and density.
     */
    private static FluidType waterLikeSlimeFluidType(int temperature, int viscosityAndDensity) {
        return new FluidType(FluidType.Properties.create()
                .temperature(temperature)
                .viscosity(viscosityAndDensity)
                .density(viscosityAndDensity));
    }

    /** #285: molten slime's own {@link FluidType} -- same lava hazard behavior as every other molten fluid, just de-tuned density/viscosity (see {@link #SLIME}'s field comment). */
    private static FluidType slimeFluidType(int temperature) {
        return new FluidType(lavaLikeProperties(temperature)
                .density(1600)
                .viscosity(1600));
    }

    private ForgeweaveFluids() {}
}
