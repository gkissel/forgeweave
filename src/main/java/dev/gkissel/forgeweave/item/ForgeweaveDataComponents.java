package dev.gkissel.forgeweave.item;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/** Data components carried by Forgeweave items. */
public final class ForgeweaveDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Forgeweave.MODID);

    /**
     * The id of the {@code dev.gkissel.forgeweave.material.Material} a part item was crafted from
     * (ADR-0002: materials are datapack registry entries, so parts reference them by id rather than
     * embedding their stats).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> MATERIAL =
            DATA_COMPONENTS.registerComponentType("material",
                    builder -> builder.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** The head/binding/handle materials an assembled tool ({@code ToolItem}) was built from. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolMaterials>> TOOL_MATERIALS =
            DATA_COMPONENTS.registerComponentType("tool_materials",
                    builder -> builder.persistent(ToolMaterials.CODEC).networkSynchronized(ToolMaterials.STREAM_CODEC));

    private ForgeweaveDataComponents() {}
}
