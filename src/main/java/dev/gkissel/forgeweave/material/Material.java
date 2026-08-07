package dev.gkissel.forgeweave.material;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.gkissel.forgeweave.Forgeweave;

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
        ResourceLocation trait,
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

    public static final Codec<Material> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Head.CODEC.fieldOf("head").forGetter(Material::head),
            Handle.CODEC.fieldOf("handle").forGetter(Material::handle),
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("extra_durability").forGetter(Material::extraDurability),
            // Vanilla tool tier, expressed as the block tag the tool cannot mine (CONTEXT.md: no numeric harvest levels).
            TagKey.codec(Registries.BLOCK).fieldOf("incorrect_for_tool").forGetter(Material::incorrectForTool),
            // Trait behavior is Java (ADR-0002); data only names which trait this material grants.
            ResourceLocation.CODEC.fieldOf("trait").forGetter(Material::trait),
            Ingredient.CODEC.fieldOf("repair_item").forGetter(Material::repairItem),
            TextColor.CODEC.fieldOf("color").forGetter(Material::color))
            .apply(instance, Material::new));
}
