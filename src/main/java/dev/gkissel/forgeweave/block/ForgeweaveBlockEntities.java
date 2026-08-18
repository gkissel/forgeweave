package dev.gkissel.forgeweave.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.casting.CastingRecipe;

/** Block entity types for Forgeweave's blocks. */
public final class ForgeweaveBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Forgeweave.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PartBuilderBlockEntity>> PART_BUILDER =
            BLOCK_ENTITIES.register("part_builder", () -> BlockEntityType.Builder
                    .of(PartBuilderBlockEntity::new, ForgeweaveBlocks.PART_BUILDER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ToolStationBlockEntity>> TOOL_STATION =
            BLOCK_ENTITIES.register("tool_station", () -> BlockEntityType.Builder
                    // One type for both blocks (issue #152): a Tool Forge is a Tool Station with a
                    // different material and two behaviour tweaks, and ToolStationBlockEntity reads
                    // which one it is off its own BlockState -- so there is nothing for a second
                    // block entity class (or type) to hold.
                    .of(ToolStationBlockEntity::new, ForgeweaveBlocks.TOOL_STATION.get(), ForgeweaveBlocks.TOOL_FORGE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CraftingStationBlockEntity>> CRAFTING_STATION =
            BLOCK_ENTITIES.register("crafting_station", () -> BlockEntityType.Builder
                    .of(CraftingStationBlockEntity::new, ForgeweaveBlocks.CRAFTING_STATION.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StencilTableBlockEntity>> STENCIL_TABLE =
            BLOCK_ENTITIES.register("stencil_table", () -> BlockEntityType.Builder
                    .of(StencilTableBlockEntity::new, ForgeweaveBlocks.STENCIL_TABLE.get())
                    .build(null));

    // docs/SCOPE.md M1 issue #66. Two BlockEntityType registrations sharing one ChestBlockEntity
    // class (see ChestKind's javadoc for why): each factory bakes in which ChestKind it builds.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChestBlockEntity>> PATTERN_CHEST =
            BLOCK_ENTITIES.register("pattern_chest", () -> BlockEntityType.Builder
                    .of((pos, state) -> new ChestBlockEntity(pos, state, ChestKind.PATTERN), ForgeweaveBlocks.PATTERN_CHEST.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ChestBlockEntity>> PART_CHEST =
            BLOCK_ENTITIES.register("part_chest", () -> BlockEntityType.Builder
                    .of((pos, state) -> new ChestBlockEntity(pos, state, ChestKind.PART), ForgeweaveBlocks.PART_CHEST.get())
                    .build(null));

    // The smeltery multiblock (docs/SCOPE.md M2 issue #95). The two cores get one type each so a
    // SmelteryControllerBlockEntity always knows which SmelteryCore tier it is (same pattern as the
    // chests above); the three tank blocks share one type because they behave identically.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelteryControllerBlockEntity>> STANDARD_CORE =
            BLOCK_ENTITIES.register("standard_core", () -> BlockEntityType.Builder
                    .of((pos, state) -> new SmelteryControllerBlockEntity(pos, state, SmelteryCore.STANDARD),
                            ForgeweaveBlocks.STANDARD_CORE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SmelteryControllerBlockEntity>> NETHER_CORE =
            BLOCK_ENTITIES.register("nether_core", () -> BlockEntityType.Builder
                    .of((pos, state) -> new SmelteryControllerBlockEntity(pos, state, SmelteryCore.NETHER),
                            ForgeweaveBlocks.NETHER_CORE.get())
                    .build(null));

    // #442 -- the seared furnace controller (upstream's TileSearedFurnace).
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SearedFurnaceBlockEntity>> SEARED_FURNACE =
            BLOCK_ENTITIES.register("seared_furnace_controller", () -> BlockEntityType.Builder
                    .of(SearedFurnaceBlockEntity::new, ForgeweaveBlocks.SEARED_FURNACE_CONTROLLER.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SearedTankBlockEntity>> SEARED_TANK =
            BLOCK_ENTITIES.register("seared_tank", () -> BlockEntityType.Builder
                    .of(SearedTankBlockEntity::new, ForgeweaveBlocks.SEARED_TANK.get(),
                            ForgeweaveBlocks.SEARED_GAUGE.get(), ForgeweaveBlocks.SEARED_WINDOW.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SearedDrainBlockEntity>> SEARED_DRAIN =
            BLOCK_ENTITIES.register("seared_drain", () -> BlockEntityType.Builder
                    .of(SearedDrainBlockEntity::new, ForgeweaveBlocks.SEARED_DRAIN.get())
                    .build(null));

    // #277 -- the duct's filter slot and the chute's item port (docs/SCOPE.md M3.4). Both share the
    // drain's core-linking base (SmelteryIoBlockEntity) but hand out different capabilities, so each
    // gets its own type.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SearedDuctBlockEntity>> SEARED_DUCT =
            BLOCK_ENTITIES.register("seared_duct", () -> BlockEntityType.Builder
                    .of(SearedDuctBlockEntity::new, ForgeweaveBlocks.SEARED_DUCT.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SearedChuteBlockEntity>> SEARED_CHUTE =
            BLOCK_ENTITIES.register("seared_chute", () -> BlockEntityType.Builder
                    .of(SearedChuteBlockEntity::new, ForgeweaveBlocks.SEARED_CHUTE.get())
                    .build(null));

    // #441 (parity audit T9) -- the channel's fluid buffer and flow flags.
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SearedChannelBlockEntity>> SEARED_CHANNEL =
            BLOCK_ENTITIES.register("seared_channel", () -> BlockEntityType.Builder
                    .of(SearedChannelBlockEntity::new, ForgeweaveBlocks.SEARED_CHANNEL.get())
                    .build(null));

    // #100 -- casting (docs/SCOPE.md M2 issue #100).
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CastingBlockEntity>> CASTING_TABLE =
            BLOCK_ENTITIES.register("casting_table", () -> BlockEntityType.Builder
                    .of((pos, state) -> new CastingBlockEntity(pos, state, CastingRecipe.Station.TABLE),
                            ForgeweaveBlocks.CASTING_TABLE.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CastingBlockEntity>> CASTING_BASIN =
            BLOCK_ENTITIES.register("casting_basin", () -> BlockEntityType.Builder
                    .of((pos, state) -> new CastingBlockEntity(pos, state, CastingRecipe.Station.BASIN),
                            ForgeweaveBlocks.CASTING_BASIN.get())
                    .build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FaucetBlockEntity>> FAUCET =
            BLOCK_ENTITIES.register("faucet", () -> BlockEntityType.Builder
                    .of(FaucetBlockEntity::new, ForgeweaveBlocks.FAUCET.get())
                    .build(null));

    private ForgeweaveBlockEntities() {}
}
