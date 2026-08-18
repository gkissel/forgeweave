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

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Fortification: spending a sharpening kit and a flint to set a finished tool's mining tier to the
 * kit material's. Upstream 1.12's {@code tools/modifiers/ModFortify}, whose whole {@code applyEffect}
 * is {@code tag.setInteger(Tags.HARVESTLEVEL, material.getStats(HEAD).harvestLevel)} followed by a
 * sweep that drops every <em>other</em> {@code fortify*} entry off the modifier list.
 *
 * <h2>The four rules that come from upstream</h2>
 *
 * <ul>
 *   <li><b>A generated per-material identifier.</b> {@code TinkerModifiers#registerFortifyModifiers}
 *       builds one {@code ModFortify} per head-stat material, each identified
 *       {@code "fortify" + material.getIdentifier()}. Forgeweave's is
 *       {@code fortification.<material>} in the material's own namespace -- {@code forgeweave:cobalt}
 *       becomes {@code forgeweave:fortification.cobalt} -- the same construction {@link Embossing}
 *       already uses, and for the same two reasons: it keeps a modifier entry inside ADR-0004's
 *       strict {@code id + level} rule while still naming the material, and it stays unambiguous for
 *       a material some other mod's datapack contributed.
 *   <li><b>The kit plus a flint, one of each.</b> {@code ModFortify}'s constructor is
 *       {@code addRecipeMatch(new RecipeMatch.ItemCombination(1, kit, flint))} -- one sharpening kit
 *       of that material and one flint, consumed together. The flint half is data
 *       ({@link ModifierRecipe}, keyed on {@link #RECIPE_ID}); the kit half is structural, because
 *       <em>which</em> kit it is picks the modifier id and the tier, exactly as embossing's donor
 *       part is left out of {@code EmbossingRecipe}.
 *   <li><b>No modifier slot.</b> {@code ModFortify} takes
 *       {@code SingleAspect + DataAspect + harvestOnly} and deliberately no {@code FreeModifierAspect},
 *       so a fortification costs none of the tool's three slots -- see {@link #BEHAVIOR}'s
 *       {@code occupiedSlots}.
 *   <li><b>One at a time, replacing rather than stacking.</b> {@code SingleAspect#canApply} throws if
 *       the tool already carries <em>this</em> fortify ({@link #resolve}'s
 *       {@code already_fortified} rejection), and {@code applyEffect}'s loop removes every other
 *       {@code fortify*} entry it finds ({@link #fortified}'s filter). So a cobalt fortification
 *       replaces an iron one outright, and the tier is <b>set</b>, not raised -- refortifying a
 *       cobalt-fortified tool with iron really does lower it back, which is upstream's own behaviour.
 * </ul>
 *
 * <h2>Where the tier actually lives</h2>
 *
 * <p>Forgeweave carries tier as vanilla {@code incorrect_for_*_tool} block tags rather than upstream's
 * numeric harvest level (CONTEXT.md), on the tool's own deny-drops {@code Tool.Rule} --
 * {@code ForgeweaveModifiers#TIER_TAGS} is the ladder and {@code tierIndexOf}/{@code tierTag} map
 * between a tag and its index. A fortification therefore has to rewrite that rule, which is precisely
 * what {@link Modifier#toolTierIndex} already does for diamond and emerald, so this rides that hook
 * rather than reaching into the stack itself. Riding it is also what makes mining checks, the
 * tooltip and part exchange agree for free, since all three already read the rule rather than the
 * head material: vanilla's rule engine gates {@code isCorrectToolForDrops},
 * {@code ToolTooltip#effectiveTierTag} prefers the live rule over the head's starting tag, and
 * {@code ModifierApplication#rebake} re-runs every modifier's bump after a part swap rebuilds the
 * tool component from the new materials.
 *
 * <p><b>The level field carries the tier.</b> {@link Modifier} hooks are pure functions of the
 * entry's {@code level} and nothing else -- that is ADR-0004's rule, and it is what stops
 * {@code toolTierIndex} from needing the material registry access {@code ModifierApplication}
 * deliberately does not have. So a fortification entry stores {@code tierIndex + 1} as its level and
 * {@link #BEHAVIOR} reads the tier straight back out of it, the same way {@code ModifierEntry}'s
 * level already means "raw application units" for haste rather than a displayed level. The {@code +1}
 * is only so that level 0 keeps meaning "not present" for every caller that tests it that way. The id
 * still names the material, because that is what the tooltip says and what
 * {@code SingleAspect}'s "already has this one" test compares.
 *
 * <p>ponytail: no stored copy of the material's tag. The level already says which rung, the id
 * already says which material, and a third copy would be a third thing to keep right across a
 * datapack edit.
 */
public final class Fortification {

    /**
     * Upstream's {@code "fortify"} identifier prefix, in Forgeweave vocabulary. The trailing dot is
     * what makes {@link #materialOf} an exact split rather than a guess, and what keeps
     * {@link #RECIPE_ID} (no dot) from ever reading as one of these.
     */
    private static final String PREFIX = "fortification.";

    /**
     * The {@link ModifierRecipe} holding the flint half of the cost. Not a modifier that is ever
     * stored on a tool -- every stored fortification id is a per-material {@link #idFor} -- but a
     * real recipe all the same, so the cost stays datapack-tunable (ADR-0004 decision 1) and so JEI
     * lists fortification in the modifier-application category alongside every other modifier with
     * no category of its own. {@code ModifierApplication#recipeFor} skips it for exactly that reason:
     * the generic apply path must never try to put {@code forgeweave:fortification} on a tool.
     */
    public static final ResourceLocation RECIPE_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "fortification");

    /**
     * What the station should show, and why it can't when it can't. Exactly one of the two is
     * meaningful -- same contract as {@link Embossing.Outcome}, and with no per-slot counts for the
     * same reason: a fortification always spends exactly one of every slot it matched
     * ({@code RecipeMatch.ItemCombination(1, kit, flint)}).
     */
    public record Outcome(ItemStack output, @Nullable Component rejection) {}

    /**
     * The one shared behaviour every generated fortification id resolves to
     * ({@code ForgeweaveModifiers#get}), which is all a per-material family can have: the ids are
     * generated, so they cannot be static registry entries, and the behaviour must be a pure function
     * of the entry's level anyway. Both halves are upstream's:
     *
     * <ul>
     *   <li>{@code toolTierIndex} <b>sets</b> the ladder index to the one the level carries, ignoring
     *       whatever is there -- {@code ModFortify#applyEffect}'s
     *       {@code tag.setInteger(Tags.HARVESTLEVEL, stats.harvestLevel)} is an assignment, not a
     *       {@code max}, unlike diamond's and emerald's capped {@code +1}.
     *   <li>{@code occupiedSlots} is 0: {@code ModFortify} carries no {@code FreeModifierAspect}.
     * </ul>
     */
    static final Modifier BEHAVIOR = new Modifier() {
        @Override
        public int toolTierIndex(int level, int tierIndex) {
            return level > 0 ? level - 1 : tierIndex;
        }

        @Override
        public int occupiedSlots(int level) {
            return 0;
        }
    };

    // ------------------------------------------------------------------ ids

    /** The modifier id a fortification with {@code materialId} serializes as. */
    public static ResourceLocation idFor(ResourceLocation materialId) {
        return ResourceLocation.fromNamespaceAndPath(materialId.getNamespace(), PREFIX + materialId.getPath());
    }

    /** Whether {@code id} is one of {@link #idFor}'s generated fortification ids. */
    public static boolean isFortification(ResourceLocation id) {
        return id.getPath().startsWith(PREFIX);
    }

    /** The material a fortification id names, or {@code null} if the id isn't a fortification at all. */
    @Nullable
    public static ResourceLocation materialOf(ResourceLocation modifierId) {
        return isFortification(modifierId)
                ? ResourceLocation.fromNamespaceAndPath(
                        modifierId.getNamespace(), modifierId.getPath().substring(PREFIX.length()))
                : null;
    }

    /** The fortification already on {@code tool}, or {@code null} -- upstream's {@code applyEffect} scan. */
    @Nullable
    public static ModifierEntry on(ItemStack tool) {
        for (ModifierEntry entry : ForgeweaveModifiers.of(tool)) {
            if (isFortification(entry.id())) {
                return entry;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ station flow

    /** Whether {@code stack} is something a fortification consumes: the sharpening kit, or the flint. */
    public static boolean isReagent(HolderLookup.Provider registries, ItemStack stack) {
        if (isKit(stack)) {
            return true;
        }
        return recipe(registries)
                .filter(fortification -> reagents(fortification).stream().anyMatch(item -> item.test(stack)))
                .isPresent();
    }

    /**
     * Resolves what the station's free reagent slots do to {@code tool}: the fortified tool, the
     * reason there isn't one, or empty when this isn't a fortification attempt at all (no kit loaded,
     * or the flint isn't in yet) -- in which case the station stays silent and lets the repair,
     * exchange, embossing and modifier recipes have their turn.
     *
     * @param freeSlots every input slot except the tool's, in slot order
     */
    public static Optional<Outcome> resolve(HolderLookup.Provider registries, ItemStack tool,
            List<ItemStack> freeSlots) {
        if (tool.get(ForgeweaveDataComponents.TOOL_STATS.get()) == null) {
            return Optional.empty(); // not an assembled tool; nothing to fortify.
        }
        Optional<ModifierRecipe> recipe = recipe(registries);
        if (recipe.isEmpty()) {
            return Optional.empty();
        }

        ItemStack kit = ItemStack.EMPTY;
        List<ItemStack> rest = new ArrayList<>(freeSlots.size());
        for (ItemStack stack : freeSlots) {
            if (stack.isEmpty()) {
                continue;
            }
            if (kit.isEmpty() && isKit(stack)) {
                kit = stack;
            } else {
                rest.add(stack);
            }
        }
        if (kit.isEmpty() || !matchesAll(reagents(recipe.get()), rest)) {
            // Either no kit to fortify from, or the flint isn't loaded yet. Both are ordinary
            // mid-loading states, not errors, so there is nothing to tell the player.
            return Optional.empty();
        }

        if (!ForgeweaveConfig.enabled(ForgeweaveConfig.MODIFIERS)) {
            // Content-family toggles ticket (maintainer decision): `modifiers=false` gates
            // everything that alters a tool at the station beyond repair and part exchange, which
            // fortification is. Refused here rather than at the top of the method for the same reason
            // ModifierApplication#resolve refuses where it does -- only once the loadout is
            // actually a fortification one (a sharpening kit plus its flint), so a station holding items meant for another recipe stays silent. The
            // fortification already on a tool is untouched: nothing below this line runs.
            return Optional.of(rejected("gui.forgeweave.modifier.modifiers_disabled"));
        }

        boolean isHarvestTool = ToolAssemblyRecipes.entryFor(tool)
                .map(entry -> entry.constants().category() == ToolConstants.Category.HARVEST)
                .orElse(false);
        if (!isHarvestTool) {
            // ModFortify's aspects include harvestOnly (CategoryAspect(HARVEST)); upstream's nine
            // Category.HARVEST tools are pickaxe, shovel, hatchet, mattock, kama, hammer, excavator,
            // lumberaxe and scythe (Pickaxe/Shovel/Hatchet/Mattock/Kama.java each call
            // addCategory(Category.HARVEST); Hammer/Excavator/Scythe inherit it from the
            // Pickaxe/Shovel/Kama they extend). {@link ToolConstants.Category#HARVEST} is
            // Forgeweave's own collapse of that same split (see its javadoc) -- already the source
            // {@link dev.gkissel.forgeweave.menu.ContentFamilies} gates the harvest-tools content
            // family from, so this rides the one table rather than re-deriving harvest-ness from the
            // tool's AOE shape or its mineable tag.
            //
            // M3.5 #396 only refused bows; every other non-harvest tool (every sword shape, the
            // warmace, battlesign, frying pan, battleaxe, cleaver) fell through and could be fortified,
            // which upstream's harvestOnly aspect never allows. Refused with a reason, like every other
            // declined application here.
            return Optional.of(rejected("gui.forgeweave.fortification.not_harvest"));
        }
        ResourceLocation materialId = kit.get(ForgeweaveDataComponents.MATERIAL.get());
        if (materialId == null) {
            return Optional.empty(); // a kit with no material can't name a tier.
        }
        ModifierEntry existing = on(tool);
        if (existing != null && existing.id().equals(idFor(materialId))) {
            // Upstream SingleAspect: re-applying the same fortify is the error. A *different*
            // material is not -- it replaces, which is what fortified() below does.
            return Optional.of(rejected("gui.forgeweave.fortification.already_fortified",
                    MaterialDisplay.name(registries, materialId)));
        }
        Optional<Material> material = registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, materialId)))
                .map(holder -> holder.value());
        if (material.isEmpty()) {
            return Optional.empty(); // a kit of a material this pack no longer defines.
        }
        int tierIndex = ForgeweaveModifiers.tierIndexOf(material.get().incorrectForTool());
        if (tierIndex < 0) {
            // A material whose tag is off the vanilla ladder (gold, or a modded tier). Upstream
            // always has a number to copy; here there is no rung to pin the tool to, and silently
            // doing nothing would look like a bug, so the station says so.
            return Optional.of(rejected("gui.forgeweave.fortification.no_tier",
                    MaterialDisplay.name(registries, materialId)));
        }
        return Optional.of(new Outcome(fortified(tool, materialId, tierIndex), null));
    }

    /**
     * The tool with every previous fortification dropped, this one's entry appended, and its
     * deny-drops rule rewritten to the new tier -- upstream {@code ModFortify#applyEffect}'s two
     * halves in the same order. Every other component rides along on the copy untouched: a
     * fortification changes the tier and nothing else.
     *
     * <p>The rule rewrite goes through {@code ModifierApplication#rebake} rather than being written
     * here, so the one piece of code that knows how to find and replace a deny-drops rule stays the
     * one piece of code that does it -- and so this produces byte-identical output to what a later
     * part exchange re-derives.
     */
    private static ItemStack fortified(ItemStack tool, ResourceLocation materialId, int tierIndex) {
        List<ModifierEntry> entries = new ArrayList<>();
        for (ModifierEntry entry : ForgeweaveModifiers.of(tool)) {
            if (!isFortification(entry.id())) {
                entries.add(entry); // upstream's loop drops every other fortify* entry.
            }
        }
        entries.add(new ModifierEntry(idFor(materialId), tierIndex + 1));

        ItemStack result = tool.copy();
        result.set(ForgeweaveDataComponents.MODIFIERS.get(), List.copyOf(entries));
        ModifierApplication.rebake(result);
        return result;
    }

    /** A sharpening kit carrying a material -- the one part that is a fortification's donor. */
    private static boolean isKit(ItemStack stack) {
        return stack.is(ForgeweaveItems.PART_SHARPENING_KIT.get())
                && stack.get(ForgeweaveDataComponents.MATERIAL.get()) != null;
    }

    /**
     * Whether {@code stacks} is exactly one slot per reagent, in any order. Same greedy first-match
     * as {@code Embossing#matchesAll} and exact for the same reason -- the shipped cost is a single
     * ingredient.
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

    /** The flint half of the cost, as plain ingredients -- unit counts mean nothing here (one of each). */
    private static List<Ingredient> reagents(ModifierRecipe recipe) {
        return recipe.reagents().stream().map(ModifierRecipe.Reagent::ingredient).toList();
    }

    /** The datapack's fortification recipe, or empty if no pack defines one. */
    private static Optional<ModifierRecipe> recipe(HolderLookup.Provider registries) {
        return registries.lookup(ModifierRecipe.REGISTRY)
                .flatMap(lookup -> lookup.listElements()
                        .map(holder -> holder.value())
                        .filter(entry -> entry.modifier().equals(RECIPE_ID))
                        .findFirst());
    }

    private static Outcome rejected(String key, Object... args) {
        return new Outcome(ItemStack.EMPTY, Component.translatable(key, args));
    }

    private Fortification() {}
}
