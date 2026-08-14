package dev.gkissel.forgeweave.recipe;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * What one entity standing in a smeltery melts into, defined entirely in datapack JSON under
 * {@code data/<namespace>/forgeweave/entity_melting_recipe/<name>.json} (docs/SCOPE.md M3.4 issue
 * #270). Ported from upstream 1.12's {@code TinkerRegistry#registerEntityMelting} +
 * {@code TinkerRegistry#getMeltingForEntity} (NOTICE.md), which is a hardcoded Java map keyed by
 * entity class there and datapack JSON here -- the same move {@link MeltingRecipe} makes for item
 * melting, and the same one the 1.20 clone made for itself when it turned that map into its
 * {@code EntityMeltingRecipe} recipe type.
 *
 * <pre>
 * {
 *   "entities": ["minecraft:villager", "minecraft:evoker"],
 *   "fluid": "forgeweave:molten_emerald",
 *   "amount": 6
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code entities} -- a list of entity ids, even when there is only one. Upstream 1.12 registers
 *       one class at a time and simply repeats the same {@code FluidStack} for the four illager-family
 *       mobs that share an output; a list lets those four be one file instead of four identical ones.
 *   <li>{@code amount} -- the output in mB, and unlike {@link MeltingRecipe#amount()} it is <b>not</b>
 *       scaled by the core tier. Upstream's own comment on {@code registerEntityMelting} calls it
 *       "the fluidstack ... returned for 1 heart damage", and the smeltery deals exactly
 *       {@link #DAMAGE} once per {@link #INTERVAL_TICKS} regardless of what tier is melting -- a
 *       Nether Core does not make a villager worth more emerald.
 * </ul>
 *
 * <p>Deliberately narrower than the 1.20 clone's version of this recipe, which additionally carries a
 * per-recipe {@code damage} field and an entity <em>tag</em> ingredient. The 1.12 generation this
 * repo targets has neither: every entity takes the same flat 2 damage, and there is no tag layer at
 * all. ponytail: both are a codec field away if a datapack ever wants them.
 */
public record EntityMeltingRecipe(List<EntityType<?>> entities, Fluid fluid, int amount) {

    /**
     * Damage the smeltery deals per interaction, upstream {@code TileSmeltery#interactWithEntitiesInside}'s
     * {@code entity.attackEntityFrom(smelteryDamage, 2f)} -- one heart, and the unit its recipe
     * amounts are quoted in.
     */
    public static final float DAMAGE = 2f;

    /**
     * How often the smeltery interacts with what is standing in it: upstream runs
     * {@code interactWithEntitiesInside} on {@code tick == 0} of a {@code tick = (tick + 1) % 20}
     * counter, i.e. once a second. Same number as the dropped-item sweep it shares a pass with
     * ({@code SmelteryControllerBlockEntity.ITEM_PICKUP_INTERVAL_TICKS}), because upstream is one
     * method doing both.
     */
    public static final int INTERVAL_TICKS = 20;

    /**
     * What a living entity with no recipe of its own yields, upstream's
     * {@code fluid = new FluidStack(TinkerFluids.blood, 20)} fallback in the same method. 20 mB per
     * heart, so an unarmoured 10-heart mob is worth 200 mB of blood.
     */
    public static final int DEFAULT_AMOUNT = 20;

    public static final ResourceKey<Registry<EntityMeltingRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "entity_melting_recipe"));

    public static final Codec<EntityMeltingRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            // Always a list, even of one. ponytail: the bare-id shorthand is a Codec#withAlternative
            // away, but a single spelling is one less thing for a datapack to get wrong.
            BuiltInRegistries.ENTITY_TYPE.byNameCodec().listOf()
                    .fieldOf("entities").forGetter(EntityMeltingRecipe::entities),
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(EntityMeltingRecipe::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(EntityMeltingRecipe::amount))
            .apply(instance, EntityMeltingRecipe::new));

    /** What this recipe pours per interaction. */
    public FluidStack result() {
        return new FluidStack(fluid, amount);
    }

    /**
     * The default blood every living entity with no recipe of its own melts into -- upstream's own
     * fallback, kept in Java rather than as a datapack row because it is the branch taken when the
     * datapack has nothing to say.
     */
    public static FluidStack defaultResult() {
        return new FluidStack(ForgeweaveFluids.BLOOD.still().get(), DEFAULT_AMOUNT);
    }

    /**
     * The recipe for {@code type}, or empty if nothing melts it (in which case the caller falls back
     * to {@link #defaultResult()} for anything living).
     *
     * <p>Ties break on registry id so two overlapping recipes always pick the same winner; unlike
     * {@link MeltingRecipe#find} there is no specific-beats-general axis to rank on, because an
     * entity list names exact types and never a tag.
     *
     * <p>ponytail: a linear scan of a registry holding under a dozen entries, run once a second per
     * formed smeltery with something standing in it. Index it by entity type if that ever shows up in
     * a profile.
     */
    public static Optional<EntityMeltingRecipe> find(RegistryAccess registries, EntityType<?> type) {
        Registry<EntityMeltingRecipe> recipes = registries.registryOrThrow(REGISTRY);
        return recipes.entrySet().stream()
                .filter(entry -> entry.getValue().entities.contains(type))
                .min(Comparator.comparing(entry -> entry.getKey().location()))
                .map(java.util.Map.Entry::getValue);
    }
}
