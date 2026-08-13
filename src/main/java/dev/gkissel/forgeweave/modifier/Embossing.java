package dev.gkissel.forgeweave.modifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;

/**
 * Embossing: spending a spare tool part to give a finished tool that part material's traits, without
 * changing a single one of its stats. Upstream 1.12's {@code tools/modifiers/ModExtraTrait}, whose
 * whole {@code applyEffect} is {@code traits.forEach(trait -> ToolBuilder.addTrait(...))} -- no stat
 * block is touched, which is the entire point of the mechanic and what makes a bone handle worth
 * embossing onto a manyullyn tool.
 *
 * <h2>The three rules that come from upstream</h2>
 *
 * <ul>
 *   <li><b>One per tool.</b> Upstream's {@code ExtraTraitAspect#canApply} walks the tool's modifier
 *       list and throws the moment any identifier starts with {@code "extratrait"}; {@link #on} is
 *       the same scan over {@link ModifierEntry} ids.
 *   <li><b>A generated per-material identifier.</b> Upstream builds one {@code ModExtraTrait} per
 *       material with {@code generateIdentifier}. Forgeweave's is {@code embossment.<material>} in
 *       the material's own namespace -- {@code forgeweave:iron} becomes
 *       {@code forgeweave:embossment.iron} -- which keeps a modifier entry inside ADR-0004's strict
 *       {@code id + level} rule while still naming the donor material, and stays unambiguous for a
 *       material some other mod's datapack contributed.
 *   <li><b>No modifier slot.</b> Upstream gives {@code ModExtraTrait} a {@code SingleAspect} and a
 *       {@code DataAspect} but deliberately no {@code FreeModifierAspect}, so an embossment costs
 *       none of the three slots -- see {@code ForgeweaveModifiers#freeSlots}, which skips these
 *       entries for exactly that reason.
 * </ul>
 *
 * <h2>Where the traits actually live</h2>
 *
 * <p>In the tool's {@code forgeweave:traits} component, appended, exactly as upstream appends them
 * to the tool's own trait NBT. Nothing re-derives them from the modifier id at read time, so every
 * trait hook that already reads the stack ({@code ForgeweaveTraits#of}) works on an embossed tool
 * with no changes -- and a save keeps decoding even if the donor material's datapack later changes
 * or disappears.
 *
 * <p>Which traits: the ones the donor material grants <em>through the donor part's kind</em>
 * ({@code Material.Traits#forPart}), which is upstream's own filter
 * ({@code PartMaterialType#getApplicableTraitsForMaterial}). Note the deliberate consequence of the
 * per-material id above: an iron pickaxe head and an iron tool rod both serialize
 * {@code forgeweave:embossment.iron} while granting different trait sets (iron scopes
 * {@code magnetic2} to heads). That is not a decode ambiguity -- the traits are serialized in their
 * own component, not re-derived from the id -- and the id is a label for the material the player
 * spent, which is what the tooltip says too.
 *
 * <p>ponytail: no cached "which traits did this embossment add" field. The trait list already says
 * so, and a second copy would be a second thing to keep right.
 */
public final class Embossing {

    /**
     * Upstream's {@code ModExtraTrait#EXTRA_TRAIT_IDENTIFIER} prefix, in Forgeweave vocabulary. The
     * trailing dot is what makes {@link #materialOf} an exact split rather than a guess.
     */
    private static final String PREFIX = "embossment.";

    /**
     * What the station should show, and why it can't when it can't. Exactly one of the two is
     * meaningful, same contract as {@code ModifierApplication.Outcome} -- but with no per-slot
     * counts, because an embossment always spends exactly one of every slot it matched.
     */
    public record Outcome(ItemStack output, @Nullable Component rejection) {}

    // ------------------------------------------------------------------ ids

    /** The modifier id an embossment of {@code materialId} serializes as. */
    public static ResourceLocation idFor(ResourceLocation materialId) {
        return ResourceLocation.fromNamespaceAndPath(materialId.getNamespace(), PREFIX + materialId.getPath());
    }

    /** Whether {@code id} is one of {@link #idFor}'s generated embossment ids. */
    public static boolean isEmbossment(ResourceLocation id) {
        return id.getPath().startsWith(PREFIX);
    }

    /** The material an embossment id names, or {@code null} if the id isn't an embossment at all. */
    @Nullable
    public static ResourceLocation materialOf(ResourceLocation modifierId) {
        return isEmbossment(modifierId)
                ? ResourceLocation.fromNamespaceAndPath(
                        modifierId.getNamespace(), modifierId.getPath().substring(PREFIX.length()))
                : null;
    }

    /** The embossment already on {@code tool}, or {@code null} -- upstream's {@code ExtraTraitAspect} scan. */
    @Nullable
    public static ModifierEntry on(ItemStack tool) {
        for (ModifierEntry entry : ForgeweaveModifiers.of(tool)) {
            if (isEmbossment(entry.id())) {
                return entry;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ station flow

    /** Whether {@code stack} is something an embossment consumes: the donor part, or one reagent. */
    public static boolean isReagent(HolderLookup.Provider registries, ItemStack stack) {
        if (isDonorPart(stack)) {
            return true;
        }
        return recipe(registries)
                .filter(embossing -> embossing.reagents().stream().anyMatch(reagent -> reagent.test(stack)))
                .isPresent();
    }

    /**
     * Resolves what the station's free reagent slots do to {@code tool}: the embossed tool, the
     * reason there isn't one, or empty when this isn't an embossing attempt at all (no donor part
     * loaded, or the reagent set isn't complete yet) -- in which case the station stays silent and
     * lets the repair and modifier recipes have their turn.
     *
     * @param freeSlots every input slot except the tool's, in slot order
     */
    public static Optional<Outcome> resolve(HolderLookup.Provider registries, ItemStack tool,
            List<ItemStack> freeSlots) {
        if (tool.get(ForgeweaveDataComponents.TOOL_STATS.get()) == null) {
            return Optional.empty(); // not an assembled tool; nothing to emboss.
        }
        Optional<EmbossingRecipe> recipe = recipe(registries);
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        ItemStack donor = ItemStack.EMPTY;
        List<ItemStack> rest = new ArrayList<>(freeSlots.size());
        for (ItemStack stack : freeSlots) {
            if (stack.isEmpty()) {
                continue;
            }
            if (donor.isEmpty() && isDonorPart(stack)) {
                donor = stack;
            } else {
                rest.add(stack);
            }
        }
        if (donor.isEmpty() || !matchesAll(recipe.get().reagents(), rest)) {
            // Either no part to emboss from, or the cost isn't all loaded yet. Both are ordinary
            // mid-loading states, not errors, so there is nothing to tell the player.
            return Optional.empty();
        }

        if (on(tool) != null) {
            return Optional.of(rejected("gui.forgeweave.embossment.already_embossed"));
        }
        ResourceLocation materialId = donor.get(ForgeweaveDataComponents.MATERIAL.get());
        Optional<Material> material = registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, materialId)))
                .map(holder -> holder.value());
        if (material.isEmpty()) {
            return Optional.empty(); // a part of a material this pack no longer defines.
        }
        List<ResourceLocation> traits = material.get().traits().forPart(((PartItem) donor.getItem()).kind());
        if (traits.isEmpty()) {
            // Upstream simply never registers a ModExtraTrait for a material/part pair with no
            // traits, so the combination isn't craftable there either -- but a silent dead slot is
            // worse than a sentence, so Forgeweave says why.
            return Optional.of(rejected("gui.forgeweave.embossment.no_traits",
                    MaterialDisplay.name(registries, materialId)));
        }
        return Optional.of(new Outcome(embossed(tool, materialId, traits), null));
    }

    /**
     * The tool with the embossment's modifier entry appended and the donor's traits merged into its
     * trait list. Every other component -- stats, materials, the vanilla {@code tool} component,
     * damage -- rides along on the copy untouched, which is the "without changing stats" half of the
     * mechanic and what the GameTest pins.
     */
    private static ItemStack embossed(ItemStack tool, ResourceLocation materialId, List<ResourceLocation> donorTraits) {
        ItemStack result = tool.copy();

        List<ModifierEntry> modifiers = new ArrayList<>(ForgeweaveModifiers.of(tool));
        modifiers.add(new ModifierEntry(idFor(materialId), 1));
        result.set(ForgeweaveDataComponents.MODIFIERS.get(), List.copyOf(modifiers));

        List<ResourceLocation> traits =
                new ArrayList<>(tool.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of()));
        for (ResourceLocation trait : donorTraits) {
            if (!traits.contains(trait)) {
                traits.add(trait); // same id from two sources still counts once, as assembly does.
            }
        }
        result.set(ForgeweaveDataComponents.TRAITS.get(), List.copyOf(traits));
        return result;
    }

    /** A buildable part carrying a material -- a shard ({@link PartItem.Kind#NONE}) is not one. */
    private static boolean isDonorPart(ItemStack stack) {
        return stack.getItem() instanceof PartItem part
                && part.kind() != PartItem.Kind.NONE
                && stack.get(ForgeweaveDataComponents.MATERIAL.get()) != null;
    }

    /**
     * Whether {@code stacks} is exactly one slot per reagent, in any order.
     *
     * <p>ponytail: greedy first-match, not a bipartite matching. The shipped cost is four disjoint
     * single-item ingredients, where greedy is exact; a pack that made two reagents overlap could
     * see a valid loadout refused, and the fix then is a real matching, not a bigger greedy.
     */
    private static boolean matchesAll(List<Ingredient> reagents, List<ItemStack> stacks) {
        if (stacks.size() != reagents.size()) {
            return false;
        }
        List<ItemStack> remaining = new ArrayList<>(stacks);
        for (Ingredient reagent : reagents) {
            int match = -1;
            for (int i = 0; i < remaining.size(); i++) {
                if (reagent.test(remaining.get(i))) {
                    match = i;
                    break;
                }
            }
            if (match < 0) {
                return false;
            }
            remaining.remove(match);
        }
        return true;
    }

    /**
     * The one embossing recipe a datapack defines. A list rather than a single-valued registry
     * because it is a registry like every other cost table here; the first entry wins, which is the
     * only entry Forgeweave ships.
     */
    private static Optional<EmbossingRecipe> recipe(HolderLookup.Provider registries) {
        return registries.lookup(EmbossingRecipe.REGISTRY)
                .flatMap(lookup -> lookup.listElements().map(holder -> holder.value()).findFirst());
    }

    private static Outcome rejected(String key, Object... args) {
        return new Outcome(ItemStack.EMPTY, Component.translatable(key, args));
    }

    private Embossing() {}
}
