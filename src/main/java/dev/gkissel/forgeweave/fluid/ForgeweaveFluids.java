package dev.gkissel.forgeweave.fluid;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import dev.gkissel.forgeweave.Forgeweave;

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
 * <p>No bucket item is registered: M2's smeltery moves fluid through tanks/faucets/casting
 * (docs/SCOPE.md M2 in-scope systems), not buckets, and adding one is easy follow-up work if a
 * later issue needs it.
 */
public final class ForgeweaveFluids {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, Forgeweave.MODID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, Forgeweave.MODID);
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Forgeweave.MODID);

    private static final ResourceLocation STILL_TEXTURE = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/molten_metal");
    private static final ResourceLocation FLOWING_TEXTURE = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/molten_metal_flow");

    // #233 -- the two non-metal smeltery fluids' own texture pairs, ported the same way the shared
    // molten metal pair was: upstream's FluidColored.ICON_LiquidStill/Flowing (blood) and
    // ICON_StoneStill/Flowing (molten clay), each greyscale-ish base tinted per fluid (NOTICE.md).
    private static final ResourceLocation LIQUID_STILL = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid");
    private static final ResourceLocation LIQUID_FLOWING = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_flow");
    private static final ResourceLocation STONE_STILL = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_stone");
    private static final ResourceLocation STONE_FLOWING = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/block/liquid_stone_flow");

    /** A registered smeltery fluid: its client tint and textures, and the smeltery-fuel-gating temperature that governs it (upstream's {@code FluidType#getTemperature}). */
    public record MoltenMetal(DeferredHolder<FluidType, FluidType> fluidType, DeferredHolder<Fluid, FlowingFluid> still,
            DeferredHolder<Fluid, FlowingFluid> flowing, DeferredBlock<LiquidBlock> block, int color, int temperature,
            ResourceLocation stillTexture, ResourceLocation flowingTexture) {}

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
    // Not a metal, but it rides the same shared tinted texture upstream's own FluidColored does.
    public static final MoltenMetal OBSIDIAN = register("obsidian", 0x2C0D59, 1000);

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
    public static final MoltenMetal SLIME = register("slime", 0x82c873, 310);
    public static final MoltenMetal SEARED_STONE = register("seared_stone", 0x777777, 800);
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

        Supplier<BaseFlowingFluid.Properties> properties = () -> new BaseFlowingFluid.Properties(
                type, () -> stillRef[0].get(), () -> flowingRef[0].get())
                .block(() -> blockRef[0].get());

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
                .lightLevel(state -> 10)));

        return new MoltenMetal(type, stillRef[0], flowingRef[0], blockRef[0], color, temperature,
                stillTexture, flowingTexture);
    }

    /** Package-visible so the temperature wiring can be exercised directly without a live registry (see {@code ForgeweaveFluidsTest}). Color is client-only ({@code ForgeweaveFluidClientExtensions}) and plays no part in {@link FluidType} itself. */
    static FluidType moltenFluidType(int temperature) {
        return new FluidType(FluidType.Properties.create()
                .lightLevel(10)
                .density(2000)
                .viscosity(10000)
                .temperature(temperature));
    }

    private ForgeweaveFluids() {}
}
