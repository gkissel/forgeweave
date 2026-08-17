package dev.gkissel.forgeweave.tool;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/**
 * The {@code Material} ids (ADR-0002) an assembled tool was built from, stored as a single data
 * component ({@code ForgeweaveDataComponents.TOOL_MATERIALS}) rather than one per part so a tool's
 * material set reads and writes atomically.
 *
 * <h2>Two views of the same set</h2>
 *
 * <p>{@link #parts()} is the authoritative one: one id per Tool Station input slot, in the tool's own
 * part order ({@code ToolAssemblyRecipes.Entry}). It is a list because M3's roster is not three parts
 * per tool -- battlesign, frying pan and dagger have two, and the Tool Forge tier plus the
 * station-tier battleaxe have four, several of them with more than one HEAD part.
 *
 * <p>{@link #head()}/{@link #binding()}/{@link #handle()} are the primary pick for each
 * {@link ToolConstants.Role}: the first HEAD part, the EXTRA part if the tool has one, the first
 * HANDLE part. Repair keys off the head material, the item model's three tint layers are exactly
 * these three, and the M1 tools' serialized shape was precisely this triple -- so keeping them as
 * real fields is both what those callers want and what keeps <b>every save written before M3
 * decoding and re-encoding byte-identically</b> (the save-compat fixture corpus is CI-gating). A
 * pre-M3 tool has no {@code parts} list at all; {@link #CODEC} reconstructs it from the triple in the
 * order M1's station used, which is the order that tool was built in.
 *
 * <p>ponytail: the triple is derived data and could be recomputed from {@code parts} plus the tool's
 * entry, but only a caller holding the whole {@code ItemStack} can do that lookup, and a data
 * component decodes without one. Storing it is what lets the component stay self-describing -- and
 * it is the shape already on disk, so it costs nothing new.
 *
 * <p>M3.5's bows (issue #394) have no HANDLE slot at all -- limb, limb, string -- so {@code handle}
 * became optional then, the same way {@code binding} already was for the binding-less tools. A bow's
 * {@code head} is its first limb ({@link ToolConstants.Role#LIMB} carries the HEAD block); a tool
 * written before then still has its {@code handle} field and encodes byte-identically.
 */
public record ToolMaterials(ResourceLocation head, Optional<ResourceLocation> binding, Optional<ResourceLocation> handle,
                            List<ResourceLocation> parts) {

    /** Decoding shape: {@code parts} absent means a pre-M3 tool, rebuilt from the triple. */
    private record Raw(ResourceLocation head, Optional<ResourceLocation> binding, Optional<ResourceLocation> handle,
                       Optional<List<ResourceLocation>> parts) {}

    private static final Codec<Raw> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("head").forGetter(Raw::head),
            ResourceLocation.CODEC.optionalFieldOf("binding").forGetter(Raw::binding),
            ResourceLocation.CODEC.optionalFieldOf("handle").forGetter(Raw::handle),
            ResourceLocation.CODEC.listOf().optionalFieldOf("parts").forGetter(Raw::parts))
            .apply(instance, Raw::new));

    public static final Codec<ToolMaterials> CODEC = RAW_CODEC.xmap(
            raw -> new ToolMaterials(raw.head(), raw.binding(), raw.handle(),
                    raw.parts().orElseGet(() -> legacyOrder(raw.head(), raw.binding(), raw.handle()))),
            materials -> new Raw(materials.head(), materials.binding(), materials.handle(),
                    Optional.of(materials.parts())));

    public static final StreamCodec<RegistryFriendlyByteBuf, ToolMaterials> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ToolMaterials::head,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ToolMaterials::binding,
            ByteBufCodecs.optional(ResourceLocation.STREAM_CODEC), ToolMaterials::handle,
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list()), ToolMaterials::parts,
            ToolMaterials::new);

    /** M1's slot order, which is what a pre-M3 tool's three fields were written in. */
    private static List<ResourceLocation> legacyOrder(ResourceLocation head, Optional<ResourceLocation> binding,
            Optional<ResourceLocation> handle) {
        List<ResourceLocation> ids = new ArrayList<>(3);
        ids.add(head);
        binding.ifPresent(ids::add);
        handle.ifPresent(ids::add);
        return List.copyOf(ids);
    }

    /**
     * The slot-ordered ids folded into both views. {@code roles} is the tool's own
     * {@link ToolConstants.Entry#parts()} role list, index-for-index with {@code materialIds}.
     */
    public static ToolMaterials of(List<ToolConstants.PartSlot> slots, List<ResourceLocation> materialIds) {
        if (slots.size() != materialIds.size()) {
            throw new IllegalArgumentException("part slots and material ids disagree");
        }
        ResourceLocation head = null;
        ResourceLocation binding = null;
        ResourceLocation handle = null;
        for (int i = 0; i < slots.size(); i++) {
            switch (slots.get(i).role()) {
                case HEAD, LIMB -> head = head == null ? materialIds.get(i) : head;
                case EXTRA -> binding = binding == null ? materialIds.get(i) : binding;
                case HANDLE -> handle = handle == null ? materialIds.get(i) : handle;
                case BOWSTRING -> { } // no primary pick: nothing keys off the string material
            }
        }
        if (head == null) {
            throw new IllegalArgumentException("a tool needs at least one head (or limb) part");
        }
        return new ToolMaterials(head, Optional.ofNullable(binding), Optional.ofNullable(handle), List.copyOf(materialIds));
    }

    /**
     * Every material this tool is made of, in slot order -- what the tooltip lists and what a trait's
     * granting-material lookup searches.
     */
    public List<ResourceLocation> all() {
        return parts;
    }
}
