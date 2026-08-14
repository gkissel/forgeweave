package dev.gkissel.forgeweave.fluid;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
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
    // Not a metal -- #285: it rides molten clay's stone still/flowing texture pair (upstream's own
    // FluidColored ICON_StoneStill/Flowing), not the shared metal texture.
    public static final MoltenMetal OBSIDIAN = register("molten_obsidian", 0x2C0D59, 1000,
            () -> moltenFluidType(1000), STONE_STILL, STONE_FLOWING);

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

    // #235 M3.2: amethyst and amethyst bronze, from the 1.20 clone (the by-name modern-branch
    // additions, docs/SCOPE.md M3.2). The 1.20 branch measures its fluid temperatures 300 degrees
    // above the 1.12 scale this class uses (its obsidian is 1300 where 1.12's -- and OBSIDIAN
    // above -- is 1000), so TinkerFluids#moltenAmethyst 1250 and #moltenAmethystBronze 1120 land
    // here as 950 and 820. Tints are the clone's mantle/colors.json material colours (NOTICE.md);
    // like OBSIDIAN, amethyst is not a metal but rides the same shared tinted texture.
    public static final MoltenMetal AMETHYST = register("amethyst", 0xB38EF1, 950);
    public static final MoltenMetal AMETHYST_BRONZE = register("amethyst_bronze", 0xC687BD, 820);

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

        return new MoltenMetal(type, stillRef[0], flowingRef[0], blockRef[0], color, temperature,
                stillTexture, flowingTexture);
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

    /** #285: molten slime's own {@link FluidType} -- same lava hazard behavior as every other molten fluid, just de-tuned density/viscosity (see {@link #SLIME}'s field comment). */
    private static FluidType slimeFluidType(int temperature) {
        return new FluidType(lavaLikeProperties(temperature)
                .density(1600)
                .viscosity(1600));
    }

    private ForgeweaveFluids() {}
}
