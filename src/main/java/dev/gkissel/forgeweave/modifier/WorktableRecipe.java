package dev.gkissel.forgeweave.modifier;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.api.modifier.Modifier;

/**
 * What the Modifier Worktable can do to the modifiers already on a tool, defined in datapack JSON
 * under {@code data/<namespace>/forgeweave/worktable_recipe/<name>.json} (issue #1057).
 *
 * <pre>
 * {
 *   "kind": "remove",
 *   "input": {"item": "minecraft:wet_sponge"},
 *   "leftovers": [{"id": "minecraft:sponge"}]
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code kind} -- which of the table's two functions this recipe drives, {@link Kind}.
 *   <li>{@code input} -- what the player puts in one of the table's two input slots. Upstream 1.20
 *       ships a wet sponge for removal and a compass for sorting; a pack adds a reagent by adding a
 *       file here, which is the whole reason this is data rather than a pair of constants.
 *   <li>{@code leftovers} -- what the spent input turns back into, upstream's own field name. The wet
 *       sponge comes back dry. Optional, default empty.
 * </ul>
 *
 * <p>A datapack registry rather than a {@code RecipeType}, following {@link ModifierRecipe} (ADR-0004
 * decision 1) and every other Forgeweave station recipe: {@code /reload} picks up an edit and the
 * table syncs to connecting clients, which is what lets the worktable screen work out its own button
 * list without a packet of its own.
 */
public record WorktableRecipe(Kind kind, Ingredient input, List<ItemStack> leftovers) {

    public static final ResourceKey<Registry<WorktableRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "worktable_recipe"));

    /** The two functions the table has. Nothing else from upstream 1.20's seven is in scope (#1057). */
    public enum Kind implements StringRepresentable {
        /** Takes one level off a chosen modifier and hands its slots back. The input is spent. */
        REMOVE,
        /** Swaps a chosen modifier with its neighbour in the tool's list. The input is never spent. */
        SORT;

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final Codec<WorktableRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Kind.CODEC.fieldOf("kind").forGetter(WorktableRecipe::kind),
            Ingredient.CODEC.fieldOf("input").forGetter(WorktableRecipe::input),
            ItemStack.CODEC.listOf().optionalFieldOf("leftovers", List.of()).forGetter(WorktableRecipe::leftovers))
            .apply(instance, WorktableRecipe::new));

    /** Whether taking the result spends one of the input item. Sorting never does, upstream's rule too. */
    public boolean consumesInput() {
        return kind == Kind.REMOVE;
    }
}
