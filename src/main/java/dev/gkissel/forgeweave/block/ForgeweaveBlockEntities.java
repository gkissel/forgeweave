package dev.gkissel.forgeweave.block;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

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
                    .of(ToolStationBlockEntity::new, ForgeweaveBlocks.TOOL_STATION.get())
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

    private ForgeweaveBlockEntities() {}
}
