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

    /** A registered molten metal: its client tint, and the smeltery-fuel-gating temperature that governs it (upstream's {@code FluidType#getTemperature}). */
    public record MoltenMetal(DeferredHolder<FluidType, FluidType> fluidType, DeferredHolder<Fluid, FlowingFluid> still,
            DeferredHolder<Fluid, FlowingFluid> flowing, DeferredBlock<LiquidBlock> block, int color, int temperature) {}

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

    private static MoltenMetal register(String metalId, int color, int temperature) {
        String name = "molten_" + metalId;
        DeferredHolder<FluidType, FluidType> type = FLUID_TYPES.register(name, () -> moltenFluidType(temperature));

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

        return new MoltenMetal(type, stillRef[0], flowingRef[0], blockRef[0], color, temperature);
    }

    /** Package-visible so the temperature wiring can be exercised directly without a live registry (see {@code ForgeweaveFluidsTest}). Color is client-only ({@code ForgeweaveFluidClientExtensions}) and plays no part in {@link FluidType} itself. */
    static FluidType moltenFluidType(int temperature) {
        return new FluidType(FluidType.Properties.create()
                .lightLevel(10)
                .density(2000)
                .viscosity(10000)
                .temperature(temperature));
    }

    public static ResourceLocation stillTexture() {
        return STILL_TEXTURE;
    }

    public static ResourceLocation flowingTexture() {
        return FLOWING_TEXTURE;
    }

    private ForgeweaveFluids() {}
}
