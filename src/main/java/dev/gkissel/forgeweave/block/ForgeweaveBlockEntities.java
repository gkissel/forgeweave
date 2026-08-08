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

    private ForgeweaveBlockEntities() {}
}
