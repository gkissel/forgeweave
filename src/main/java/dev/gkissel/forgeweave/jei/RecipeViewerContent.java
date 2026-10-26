package dev.gkissel.forgeweave.jei;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;

import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ContentFamilies;
import dev.gkissel.forgeweave.modifier.EmbossingRecipe;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.modifier.WorktableRecipe;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;
import dev.gkissel.forgeweave.recipe.CoreTransformRecipe;
import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * Every recipe list a recipe viewer shows, for all 14 Forgeweave categories, built once per session
 * from that session's synced registries (issue #971). {@link ForgeweaveJeiPlugin} and the EMI plugin
 * ({@code dev.gkissel.forgeweave.emi.ForgeweaveEmiPlugin}) both read this, so the two viewers can
 * never disagree about what a category holds.
 *
 * <p>Kept free of any JEI or EMI import, like {@link JeiCategoryGeometry}: both are compileOnly, and
 * EMI loads its plugin with no JEI installed (the whole point of #971), so nothing the EMI plugin
 * touches may pull a JEI type in. It also takes a plain {@link RegistryAccess} instead of reading
 * {@code Minecraft.getInstance().level} itself, which is what lets {@code EmiPluginToggleGameTests}
 * build one on a dedicated GameTest server.
 *
 * <p>Every list is filtered against the {@code content} config section first. See {@link
 * ForgeweaveJeiPlugin#registerRecipes} for why those reads are the server's values and for the one
 * known limit (a toggle flipped mid-session shows up at the next join).
 *
 * @param mendingMossInfo whether the Mending Moss ingredient-info line belongs in the viewer at all
 *     (issue #752), which is the {@code modifiers} family's call
 */
public record RecipeViewerContent(
        List<PartCraftingRecipe> partCrafting,
        List<AssemblyRecipe> toolAssembly,
        List<AssemblyRecipe> largeToolAssembly,
        List<RepairRecipe> repair,
        List<WorktableDisplay> worktable,
        List<MeltingDisplay> melting,
        List<AlloyRecipe> alloying,
        List<CastingRecipe> castingTable,
        List<CastingRecipe> castingBasin,
        List<ModifierRecipe> modifierApplication,
        List<EmbossingDisplay> embossing,
        List<SmelteryFuelDisplay> smelteryFuel,
        List<CoreTransformRecipe> coreTransform,
        List<EntityMeltingDisplay> entityMelting,
        boolean mendingMossInfo) {

    /** Nothing at all: what a session with no world yet has to show. */
    public static final RecipeViewerContent EMPTY = new RecipeViewerContent(List.of(), List.of(), List.of(),
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
            List.of(), List.of(), false);

    /**
     * The EMI plugin's whole input (issue #971): empty when the {@code emiPlugin} compat toggle is
     * off, so the plugin has nothing to register, and {@link #build} otherwise. The toggle is read
     * here, in the EMI-free half, for the reason {@code ForgeweaveConfig}'s compat javadoc gives:
     * a GameTest can reach this, and nothing that names an EMI type.
     */
    public static Optional<RecipeViewerContent> forEmi(RegistryAccess registries) {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.EMI_PLUGIN) ? Optional.of(build(registries)) : Optional.empty();
    }

    /**
     * Builds every list from {@code registries}, or {@link #EMPTY}'s lists when that is {@code null}
     * (the title screen, before any world is joined and the materials are synced).
     *
     * <p>Repair and part exchange are deliberately <em>not</em> filtered: both act on a tool that
     * already exists without altering what it is, which the content-family ticket's "items already
     * in the world keep working" rule keeps available whatever a toggle says. Embossing and
     * fortification are the other side of that line -- they change the tool -- and go with {@code
     * modifiers}.
     */
    public static RecipeViewerContent build(RegistryAccess registries) {
        Map<Identifier, Material> materials = entries(registries, Material.REGISTRY);

        // Split by AssemblyRecipes#isLarge (issue #165) so each half lands in the category whose
        // catalyst list matches where it can actually be built -- see AssemblyCategory's class
        // javadoc. Since issue #1006 the split carries armor too: the light set is with the small
        // tools, the heavy set with the large ones.
        List<AssemblyRecipe> assembly = AssemblyRecipes.build(materials).stream()
                .filter(recipe -> ContentFamilies.itemEnabled(recipe.tool()))
                .toList();

        // #109: the smeltery family gates all four smeltery categories at once (melting, alloying and
        // both casting stations), and the modifier family gates modifier application; the casting
        // lists are additionally filtered per recipe, since a pour that shapes a part only an off
        // tool family takes goes with that family rather than with the smeltery.
        boolean smeltery = ForgeweaveConfig.enabled(ForgeweaveConfig.SMELTERY);
        boolean modifiers = ForgeweaveConfig.enabled(ForgeweaveConfig.MODIFIERS);
        Map<Identifier, CastingRecipe> casting = smeltery ? entries(registries, CastingRecipe.REGISTRY) : Map.of();
        // #893: melting's fuel column reads the same smeltery_fuel registry #890's standalone
        // category does, so it is read once here and reused.
        Map<Identifier, SmelteryFuel> fuels = smeltery ? entries(registries, SmelteryFuel.REGISTRY) : Map.of();

        return new RecipeViewerContent(
                PartCraftingRecipes.build(materials).stream()
                        .filter(recipe -> ContentFamilies.itemEnabled(recipe.result()))
                        .toList(),
                assembly.stream().filter(recipe -> !AssemblyRecipes.isLarge(recipe)).toList(),
                assembly.stream().filter(AssemblyRecipes::isLarge).toList(),
                RepairRecipes.build(materials),
                // #1057: gated by the modifiers key -- taking a modifier off is one of the things it covers.
                modifiers ? worktable(registries) : List.of(),
                smeltery ? MeltingRecipes.build(entries(registries, MeltingRecipe.REGISTRY), fuels) : List.of(),
                smeltery ? AlloyingRecipes.build(entries(registries, AlloyRecipe.REGISTRY)) : List.of(),
                castingEnabled(CastingRecipes.table(casting)),
                castingEnabled(CastingRecipes.basin(casting)),
                modifiers ? ModifierApplicationRecipes.build(entries(registries, ModifierRecipe.REGISTRY)) : List.of(),
                // #165: embossing is one of the things `modifiers` covers (maintainer decision:
                // everything that alters a tool at the station beyond repair and part exchange).
                modifiers ? EmbossingRecipes.build(entries(registries, EmbossingRecipe.REGISTRY), materials) : List.of(),
                // #890: both smeltery-only mechanics (SmelteryFuel#find and CoreTransformRecipe#find
                // check ForgeweaveConfig#SMELTERY themselves). #972: the energized tank's own synthetic
                // row rides its own compat toggle on top -- a bridge that is off has nothing to advertise.
                smeltery ? SmelteryFuelRecipes.build(fuels, ForgeweaveConfig.enabled(ForgeweaveConfig.ENERGIZED_TANK)) : List.of(),
                smeltery ? CoreTransformRecipes.build(entries(registries, CoreTransformRecipe.REGISTRY)) : List.of(),
                // #931: EntityMeltingRecipe#find checks ForgeweaveConfig#SMELTERY itself too.
                smeltery ? EntityMeltingRecipes.build(entries(registries, EntityMeltingRecipe.REGISTRY),
                        fuels.values().stream().map(SmelteryFuel::fluid).distinct().toList()) : List.of(),
                // Issue #752: Mending Moss is not produced by any datapack recipe, so without this one
                // text line it would have an empty "Recipes" tab with no clue how it is made.
                modifiers);
    }

    /** Every recipe in all 14 lists, for a quick "did anything get built" check. */
    public int recipeCount() {
        return partCrafting.size() + toolAssembly.size() + largeToolAssembly.size() + repair.size() + worktable.size()
                + melting.size() + alloying.size() + castingTable.size() + castingBasin.size()
                + modifierApplication.size() + embossing.size() + smelteryFuel.size() + coreTransform.size()
                + entityMelting.size();
    }

    /** The casting recipes whose cast and result both belong to families that are currently on. */
    private static List<CastingRecipe> castingEnabled(List<CastingRecipe> recipes) {
        return recipes.stream()
                .filter(recipe -> ContentFamilies.itemEnabled(recipe.result())
                        && recipe.cast().map(cast -> Arrays.stream(cast.getItems())
                                .anyMatch(ContentFamilies::itemEnabled)).orElse(true))
                .toList();
    }

    /** #1057: the worktable's functions, in registry order, each paired with its own key. */
    private static List<WorktableDisplay> worktable(RegistryAccess registries) {
        return entries(registries, WorktableRecipe.REGISTRY).entrySet().stream()
                .map(entry -> new WorktableDisplay(entry.getKey(), entry.getValue()))
                .toList();
    }

    /**
     * One synced datapack registry, in registry order, keyed by id -- materials and every recipe
     * registry above are NeoForge datapack registries with network codecs
     * ({@code Forgeweave#registerDataPackRegistries}), so the client's copy is exactly what the
     * joined session has, modpack additions included. Empty (not an error) with no registries yet.
     */
    private static <T> Map<Identifier, T> entries(RegistryAccess registries, ResourceKey<Registry<T>> key) {
        if (registries == null) {
            return Map.of();
        }
        Map<Identifier, T> entries = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<T>, T> entry : registries.lookupOrThrow(key).entrySet()) {
            entries.put(entry.getKey().location(), entry.getValue());
        }
        return entries;
    }
}
