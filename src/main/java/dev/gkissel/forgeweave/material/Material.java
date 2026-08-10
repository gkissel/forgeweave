package dev.gkissel.forgeweave.material;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.PartItem;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * A material a {@code Part} can be made from, defined entirely in datapack JSON under
 * {@code data/<namespace>/forgeweave/material/<name>.json} (ADR-0002).
 *
 * <p>Registered as a NeoForge datapack registry with a network codec, so the server loads it on
 * {@code /reload} and syncs it to connecting clients for free. Look entries up through
 * {@code registryAccess().registryOrThrow(Material.REGISTRY)} on either side.
 */
public record Material(
        Head head,
        Handle handle,
        int extraDurability,
        TagKey<Block> incorrectForTool,
        Traits traits,
        List<CraftingItem> craftingItems,
        Ingredient repairItem,
        TextColor color) {

    public static final ResourceKey<Registry<Material>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "material"));

    /** Stats a head part contributes: the tool's durability pool, mining speed and attack damage. */
    public record Head(int durability, float miningSpeed, float attackDamage) {
        public static final Codec<Head> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("durability").forGetter(Head::durability),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("mining_speed").forGetter(Head::miningSpeed),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("attack_damage").forGetter(Head::attackDamage))
                .apply(instance, Head::new));
    }

    /**
     * Stats a handle part contributes: a multiplier on the head's durability pool plus a flat
     * bonus, which is negative for materials that trade durability for other stats.
     */
    public record Handle(float durabilityModifier, int durability) {
        public static final Codec<Handle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("durability_modifier").forGetter(Handle::durabilityModifier),
                Codec.INT.fieldOf("durability").forGetter(Handle::durability))
                .apply(instance, Handle::new));
    }

    /**
     * A raw item usable as Part Builder crafting input, and how much of a part's cost one of it
     * pays off (upstream 1.12's `Material#addItem`/`addItemIngot`, {@code TinkerMaterials}).
     * {@code value} is expressed in shard-units -- see {@code PartBuilderRecipes}'s class javadoc
     * for the shard-unit normalization this whole schema is denominated in.
     */
    public record CraftingItem(Ingredient ingredient, int value) {
        public static final Codec<CraftingItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(CraftingItem::ingredient),
                ExtraCodecs.POSITIVE_INT.fieldOf("value").forGetter(CraftingItem::value))
                .apply(instance, CraftingItem::new));
    }

    /**
     * Which traits this material grants, scoped by the kind of part granting them -- upstream 1.12's
     * {@code Material#addTrait(trait, HEAD)} / {@code addTrait(trait)} split, keyed by the same
     * part-stat block {@link PartItem.Kind} names ({@code library/materials/Material.java}, pinned
     * commit in NOTICE.md).
     *
     * <p>{@code general} applies to every part; a part-scoped list <b>replaces</b> it for that part
     * rather than adding to it, exactly as upstream's {@code getAllTraitsForStats} falls back to the
     * default list only when the stat has no list of its own. That is why upstream re-states a
     * general trait under {@code HEAD} when it wants both (prismarine's {@code aquadynamic}), and why
     * a stronger head variant simply replaces the general one (iron: {@code magnetic2} on the head,
     * {@code magnetic} everywhere else).
     *
     * <p>Only {@code head} exists as a scope so far, because that is the only one Forgeweave's
     * materials use; ponytail: the other two {@link PartItem.Kind}s get a field when a material needs
     * one, and {@link #forPart} is the single place that has to learn about it.
     */
    public record Traits(List<ResourceLocation> general, List<ResourceLocation> head) {

        public static final Codec<Traits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("general", List.of()).forGetter(Traits::general),
                ResourceLocation.CODEC.listOf().optionalFieldOf("head", List.of()).forGetter(Traits::head))
                .apply(instance, Traits::new));

        /** The common case: traits every part of the material grants. */
        public static Traits general(ResourceLocation... ids) {
            return new Traits(List.of(ids), List.of());
        }

        /** The trait ids a part of this {@code kind} grants (see the class javadoc on scoping). */
        public List<ResourceLocation> forPart(PartItem.Kind kind) {
            if (kind == PartItem.Kind.HEAD && !head.isEmpty()) {
                return head;
            }
            return general;
        }

        /** Every trait id this material can grant through any part, de-duplicated. */
        public List<ResourceLocation> all() {
            return Stream.concat(general.stream(), head.stream()).distinct().toList();
        }
    }

    /**
     * Accepts both the pre-#94 {@code "trait": "<id>"} (one trait, every part) and the current
     * {@code "traits": {...}} object, and always writes the latter -- datapack materials are a public
     * surface (ADR-0002), so a pack written against the old shape keeps loading. A material naming
     * both fields is read as the old shape; naming neither still fails, as it always has.
     *
     * <p>The {@code Traits} lists are {@code optionalFieldOf} with an empty default, which DFU omits
     * on encode, so the registry-sync payload of a material with no head-scoped traits is exactly the
     * one list it actually has (docs/SCOPE.md's material sync-packet budget).
     */
    private static final MapCodec<Traits> TRAITS_CODEC = Codec.mapEither(
            ResourceLocation.CODEC.fieldOf("trait"),
            Traits.CODEC.fieldOf("traits"))
            .xmap(either -> either.map(Traits::general, Function.identity()), Either::right);

    public static final Codec<Material> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Head.CODEC.fieldOf("head").forGetter(Material::head),
            Handle.CODEC.fieldOf("handle").forGetter(Material::handle),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("extra_durability").forGetter(Material::extraDurability),
            // Vanilla tool tier, expressed as the block tag the tool cannot mine (CONTEXT.md: no numeric harvest levels).
            TagKey.codec(Registries.BLOCK).fieldOf("incorrect_for_tool").forGetter(Material::incorrectForTool),
            // Trait behavior is Java (ADR-0002); data only names which traits this material grants.
            TRAITS_CODEC.forGetter(Material::traits),
            // Part Builder crafting inputs and their shard-unit values (issue #45); repair_item below
            // is a separate, single-ingredient concept used only for Tool Station repair.
            CraftingItem.CODEC.listOf().fieldOf("crafting_items").forGetter(Material::craftingItems),
            Ingredient.CODEC.fieldOf("repair_item").forGetter(Material::repairItem),
            TextColor.CODEC.fieldOf("color").forGetter(Material::color))
            .apply(instance, Material::new));
}
