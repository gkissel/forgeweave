package dev.gkissel.forgeweave.item;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;

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

    /**
     * The stats {@code ToolStats#compute} derived from those materials at assembly time. Durability
     * is mirrored onto vanilla's max-damage component and mining speed into vanilla's {@code tool}
     * component; this keeps the whole block so attack damage has a home and so a tool's stats can be
     * read back without a registry lookup.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolStats.Stats>> TOOL_STATS =
            DATA_COMPONENTS.registerComponentType("tool_stats",
                    builder -> builder.persistent(ToolStats.Stats.CODEC).networkSynchronized(ToolStats.Stats.STREAM_CODEC));

    /**
     * The ids of the {@code Trait}s an assembled tool has, resolved from its three materials at
     * assembly time and de-duplicated ({@code ForgeweaveTraits#resolve}). Stored rather than looked
     * up per use because the seams traits hook into -- notably
     * {@code ToolItem#getDefaultAttributeModifiers}, which receives only an {@code ItemStack} -- have
     * no registry access to resolve materials with.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> TRAITS =
            DATA_COMPONENTS.registerComponentType("traits",
                    builder -> builder.persistent(ResourceLocation.CODEC.listOf())
                            .networkSynchronized(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list())));

    /**
     * Set once a tool runs out of durability. CONTEXT.md: a Broken tool is unusable but never
     * destroyed, and only a Tool Station repair clears this.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BROKEN =
            DATA_COMPONENTS.registerComponentType("broken",
                    builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** How many times a tool has been repaired; feeds the diminishing returns in {@code ToolRepair}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> REPAIR_COUNT =
            DATA_COMPONENTS.registerComponentType("repair_count",
                    builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * The registry id of the {@code Block} (a log or planks) a table station item was crafted from,
     * so the placed block can retain that wood's appearance (issue #43). Absent means "unspecified"
     * (e.g. a creative-tab/pick-block item) -- {@code WoodTexturedBlockEntity} falls back to oak.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> TEXTURE =
            DATA_COMPONENTS.registerComponentType("texture",
                    builder -> builder.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));

    private ForgeweaveDataComponents() {}
}
