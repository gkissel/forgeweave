package dev.gkissel.forgeweave.emi;

import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.registries.DeferredHolder;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import dev.emi.emi.api.stack.Comparison;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.stack.EmiStackInteraction;
import dev.emi.emi.api.widget.Bounds;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.client.SmelteryScreen;
import dev.gkissel.forgeweave.client.StationExtraAreas;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.jei.PartCraftingRecipe;
import dev.gkissel.forgeweave.jei.RecipeViewerContent;
import dev.gkissel.forgeweave.jei.SubtypeKeys;
import dev.gkissel.forgeweave.menu.CraftingStationMenu;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.ModifierWorktableMenu;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.recipe.RetexturedShapedRecipe;

/**
 * Forgeweave's native EMI plugin (issue #971): all 14 recipe categories the JEI plugin has, with
 * their workstations, the part/tool/texture stack comparisons, the station screens' exclusion
 * areas, the smeltery tank's hovered-fluid lookup, the fill buttons, the Mending Moss info page and
 * the retextured stations' every-variant crafting output.
 *
 * <p>It exists because EMI's JEI bridge ({@code dev.emi.emi.jemi.JemiPlugin}) is itself a JEI
 * plugin, so it only runs when JEI is installed: with EMI alone, the 1.0.0 playtest found no
 * Forgeweave category at all. Every recipe list comes from {@link RecipeViewerContent}, the same
 * builder the JEI plugin reads, and every layout copies its JEI category's numbers.
 *
 * <h2>With JEI installed too</h2>
 *
 * <p>No category shows up twice. Read off EMI 1.1.24's own source: the bridge collects the mod id of
 * every {@code @EmiEntrypoint} class ({@code EmiAgnosNeoForge#getPluginsAgnos}, the id of the mod file
 * the class sits in) and skips any JEI category whose id has one of those namespaces, logging
 * "Skipping recipe category ... because mod is already handled". All 14 of Forgeweave's are {@code
 * forgeweave:*}, so the bridge leaves every one of them to this plugin.
 *
 * <p>That skip only covers Forgeweave-namespaced categories, though. The bridge still forwards what
 * the JEI plugin hangs off JEI's own categories: the info page ({@code jei:information}), the
 * catalysts on vanilla crafting and smelting, and the retextured crafting extension (the bridge
 * rebuilds every crafting recipe through JEI's crafting category). This plugin leaves those three to
 * the bridge when JEI is loaded, or EMI would list them twice. Stack comparisons, exclusion areas and the tank lookup are
 * registered either way, since EMI merges duplicates of those harmlessly.
 */
@EmiEntrypoint
public final class ForgeweaveEmiPlugin implements EmiPlugin {

    /**
     * Runs every time EMI reloads, which is after a world is joined and the server's config and
     * datapack registries have synced, so {@link RecipeViewerContent} sees what that session has.
     * Off means {@link RecipeViewerContent#forEmi} is empty and nothing at all is registered.
     */
    @Override
    public void register(EmiRegistry registry) {
        ClientLevel level = Minecraft.getInstance().level;
        RecipeViewerContent.forEmi(level == null ? null : level.registryAccess())
                .ifPresent(content -> register(registry, content, ModList.get().isLoaded("jei")));
    }

    /**
     * Everything this plugin registers, from one session's {@code content}. Package-private so
     * {@code ForgeweaveEmiPluginTest} can run it against a stub registry.
     *
     * @param jeiBridged whether EMI's JEI bridge is running too, and so already forwards the info
     *     page, the vanilla-category catalysts and the retextured crafting output
     */
    static void register(EmiRegistry registry, RecipeViewerContent content, boolean jeiBridged) {
        ForgeweaveEmiCategories.ALL.forEach(registry::addCategory);
        registerWorkstations(registry, jeiBridged);
        registerRecipes(registry, content);
        registerComparisons(registry);
        registerScreens(registry);
        registerFillHandlers(registry);
        if (!jeiBridged) {
            registerRetexturedCrafting(registry);
        }

        // Issue #752: Mending Moss is made at a bookshelf, by no datapack recipe, so without this page
        // it would have an empty recipe tab and no clue how it is made.
        if (content.mendingMossInfo() && !jeiBridged) {
            registry.addRecipe(new EmiInfoRecipe(List.of(EmiStack.of(ForgeweaveItems.MENDING_MOSS.get())),
                    List.of(Component.translatable("tooltip.forgeweave.mending_moss.source")),
                    ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "/info/mending_moss")));
        }
    }

    /**
     * The JEI plugin's {@code RetexturedCraftingExtension}, for EMI without JEI: each retextured
     * station recipe replaces EMI's own single-output version of itself, under the same id. With JEI
     * installed the bridge already rebuilds every crafting recipe through JEI's crafting category,
     * extension included, so this would only add a second copy.
     */
    private static void registerRetexturedCrafting(EmiRegistry registry) {
        List<RecipeHolder<CraftingRecipe>> retextured = registry.getRecipeManager().getAllRecipesFor(RecipeType.CRAFTING)
                .stream()
                .filter(holder -> holder.value() instanceof RetexturedShapedRecipe)
                .toList();
        Set<ResourceLocation> ids = retextured.stream().map(RecipeHolder::id).collect(Collectors.toSet());
        registry.removeRecipes(recipe -> ids.contains(recipe.getId()) && !(recipe instanceof RetexturedCraftingEmiRecipe));
        for (RecipeHolder<CraftingRecipe> holder : retextured) {
            registry.addRecipe(new RetexturedCraftingEmiRecipe(holder, (RetexturedShapedRecipe) holder.value()));
        }
    }

    /** The JEI plugin's {@code registerRecipeCatalysts}, category for category. */
    private static void registerWorkstations(EmiRegistry registry, boolean jeiBridged) {
        workstation(registry, ForgeweaveItems.PART_BUILDER.get(), ForgeweaveEmiCategories.PART_CRAFTING);
        // A shard always pays a part's cost exactly, so it is as good a "what can this craft" lookup
        // as the station itself (issue #45).
        workstation(registry, ForgeweaveItems.SHARD.get(), ForgeweaveEmiCategories.PART_CRAFTING);
        // The repair tab is also the modify and emboss tab (menu.ToolStationMenu's class javadoc).
        workstation(registry, ForgeweaveItems.TOOL_STATION.get(), ForgeweaveEmiCategories.TOOL_ASSEMBLY,
                ForgeweaveEmiCategories.TOOL_REPAIR, ForgeweaveEmiCategories.MODIFIER_APPLICATION,
                ForgeweaveEmiCategories.EMBOSSING);
        // #152: the Tool Forge does everything the Tool Station does, plus #165's large-tool category.
        workstation(registry, ForgeweaveItems.TOOL_FORGE.get(), ForgeweaveEmiCategories.TOOL_ASSEMBLY,
                ForgeweaveEmiCategories.LARGE_TOOL_ASSEMBLY, ForgeweaveEmiCategories.TOOL_REPAIR,
                ForgeweaveEmiCategories.MODIFIER_APPLICATION, ForgeweaveEmiCategories.EMBOSSING);
        workstation(registry, ForgeweaveItems.MODIFIER_WORKTABLE.get(), ForgeweaveEmiCategories.MODIFIER_WORKTABLE);
        // Every core tier melts, alloys, burns fuel and melts entities; only the Nether and End Core
        // are ever a core transform's from-block (#845's two shipped rows).
        for (Item core : List.of(ForgeweaveItems.STANDARD_CORE.get(), ForgeweaveItems.NETHER_CORE.get(),
                ForgeweaveItems.END_CORE.get(), ForgeweaveItems.DEEP_CORE.get())) {
            workstation(registry, core, ForgeweaveEmiCategories.MELTING, ForgeweaveEmiCategories.ALLOYING,
                    ForgeweaveEmiCategories.SMELTERY_FUEL, ForgeweaveEmiCategories.ENTITY_MELTING);
        }
        workstation(registry, ForgeweaveItems.NETHER_CORE.get(), ForgeweaveEmiCategories.CORE_TRANSFORM);
        workstation(registry, ForgeweaveItems.END_CORE.get(), ForgeweaveEmiCategories.CORE_TRANSFORM);
        // #972: the tank is a fuel source, so looking it up lands on the category that explains it.
        workstation(registry, ForgeweaveItems.ENERGIZED_TANK.get(), ForgeweaveEmiCategories.SMELTERY_FUEL);
        workstation(registry, ForgeweaveItems.CASTING_TABLE.get(), ForgeweaveEmiCategories.CASTING_TABLE);
        workstation(registry, ForgeweaveItems.CASTING_BASIN.get(), ForgeweaveEmiCategories.CASTING_BASIN);
        if (!jeiBridged) {
            workstation(registry, ForgeweaveItems.CRAFTING_STATION.get(), VanillaEmiRecipeCategories.CRAFTING);
            // #442: upstream's JEIPlugin makes the seared furnace controller a catalyst of vanilla smelting.
            workstation(registry, ForgeweaveItems.SEARED_FURNACE_CONTROLLER.get(), VanillaEmiRecipeCategories.SMELTING);
        }
    }

    private static void workstation(EmiRegistry registry, ItemLike item, EmiRecipeCategory... categories) {
        for (EmiRecipeCategory category : categories) {
            registry.addWorkstation(category, EmiStack.of(item));
        }
    }

    private static void registerRecipes(EmiRegistry registry, RecipeViewerContent content) {
        int part = 0;
        for (PartCraftingRecipe recipe : content.partCrafting()) {
            for (int option = 0; option < recipe.materialInputs().size(); option++) {
                registry.addRecipe(new PartCraftingEmiRecipe(
                        ForgeweaveEmiCategories.recipeId(ForgeweaveEmiCategories.PART_CRAFTING, part++), recipe, option));
            }
        }
        add(registry, ForgeweaveEmiCategories.TOOL_ASSEMBLY, content.toolAssembly(),
                (id, recipe) -> new AssemblyEmiRecipe(ForgeweaveEmiCategories.TOOL_ASSEMBLY, id, recipe));
        add(registry, ForgeweaveEmiCategories.LARGE_TOOL_ASSEMBLY, content.largeToolAssembly(),
                (id, recipe) -> new AssemblyEmiRecipe(ForgeweaveEmiCategories.LARGE_TOOL_ASSEMBLY, id, recipe));
        add(registry, ForgeweaveEmiCategories.TOOL_REPAIR, content.repair(), RepairEmiRecipe::new);
        add(registry, ForgeweaveEmiCategories.MODIFIER_WORKTABLE, content.worktable(), WorktableEmiRecipe::new);
        add(registry, ForgeweaveEmiCategories.MELTING, content.melting(), MeltingEmiRecipe::new);
        add(registry, ForgeweaveEmiCategories.ALLOYING, content.alloying(), AlloyingEmiRecipe::new);
        addCasting(registry, ForgeweaveEmiCategories.CASTING_TABLE, content.castingTable(), CastingEmiRecipe.BLOCK_V_TABLE);
        addCasting(registry, ForgeweaveEmiCategories.CASTING_BASIN, content.castingBasin(), CastingEmiRecipe.BLOCK_V_BASIN);
        add(registry, ForgeweaveEmiCategories.MODIFIER_APPLICATION, content.modifierApplication(),
                ModifierApplicationEmiRecipe::new);
        add(registry, ForgeweaveEmiCategories.EMBOSSING, content.embossing(), EmbossingEmiRecipe::new);
        add(registry, ForgeweaveEmiCategories.SMELTERY_FUEL, content.smelteryFuel(), SmelteryFuelEmiRecipe::new);
        add(registry, ForgeweaveEmiCategories.CORE_TRANSFORM, content.coreTransform(), CoreTransformEmiRecipe::new);
        add(registry, ForgeweaveEmiCategories.ENTITY_MELTING, content.entityMelting(), EntityMeltingEmiRecipe::new);
    }

    private static <T> void add(EmiRegistry registry, EmiRecipeCategory category, List<T> rows,
            BiFunction<ResourceLocation, T, EmiRecipe> recipe) {
        for (int i = 0; i < rows.size(); i++) {
            registry.addRecipe(recipe.apply(ForgeweaveEmiCategories.recipeId(category, i), rows.get(i)));
        }
    }

    /** One EMI row per poured fluid; see {@link CastingEmiRecipe}'s javadoc. */
    private static void addCasting(EmiRegistry registry, EmiRecipeCategory category, List<CastingRecipe> recipes,
            int blockV) {
        int index = 0;
        for (CastingRecipe recipe : recipes) {
            for (Fluid poured : recipe.displayFluids()) {
                registry.addRecipe(new CastingEmiRecipe(category,
                        ForgeweaveEmiCategories.recipeId(category, index++), recipe, poured, blockV));
            }
        }
    }

    /**
     * Issue #307's subtype interpreters, as EMI comparisons: without them every material variant of a
     * part or tool, and every wood variant of a retextured station, collapses into one EMI entry. The
     * same {@link SubtypeKeys} methods and the same item roster as the JEI plugin's
     * {@code registerItemSubtypes}.
     */
    private static void registerComparisons(EmiRegistry registry) {
        Comparison part = Comparison.compareData(stack -> SubtypeKeys.part(stack.getItemStack()));
        Comparison tool = Comparison.compareData(stack -> SubtypeKeys.tool(stack.getItemStack()));
        Comparison texture = Comparison.compareData(stack -> SubtypeKeys.texture(stack.getItemStack()));

        ForgeweaveItems.ITEMS.getEntries().stream()
                .<Item>map(DeferredHolder::get)
                .filter(item -> item instanceof PartItem)
                .forEach(item -> registry.setDefaultComparison(item, part));
        ToolAssemblyRecipes.ENTRIES.forEach(entry -> registry.setDefaultComparison(entry.tool().get(), tool));
        for (Item station : List.of(ForgeweaveItems.PART_BUILDER.get(), ForgeweaveItems.TOOL_STATION.get(),
                ForgeweaveItems.TOOL_FORGE.get(), ForgeweaveItems.CRAFTING_STATION.get(),
                ForgeweaveItems.STENCIL_TABLE.get())) {
            registry.setDefaultComparison(station, texture);
        }
    }

    /**
     * The JEI plugin's {@code registerGuiHandlers}. Every screen that draws outside its own box says so
     * through {@link StationExtraAreas} (docs/SCOPE.md issue #68 fix 4), so one generic area covers
     * all of them, the smeltery's melt grid included. The smeltery tank also answers "which fluid is
     * under the cursor", so EMI's recipe and usage keys work on its contents (issue #308).
     */
    private static void registerScreens(EmiRegistry registry) {
        registry.addGenericExclusionArea((screen, areas) -> {
            if (screen instanceof StationExtraAreas station) {
                station.extraGuiAreas().forEach(area ->
                        areas.accept(new Bounds(area.getX(), area.getY(), area.getWidth(), area.getHeight())));
            }
        });
        registry.addStackProvider(SmelteryScreen.class, (screen, x, y) -> screen.hoveredTankFluid(x, y)
                .map(hover -> new EmiStackInteraction(fluidStack(hover.fluid())))
                .orElse(EmiStackInteraction.EMPTY));
    }

    private static EmiStack fluidStack(FluidStack fluid) {
        return EmiStack.of(fluid.getFluid(), fluid.getComponentsPatch(), fluid.getAmount());
    }

    /** The JEI plugin's {@code registerRecipeTransferHandlers}, as EMI fill handlers. */
    private static void registerFillHandlers(EmiRegistry registry) {
        registry.addRecipeHandler(ForgeweaveMenus.CRAFTING_STATION.get(), new SlotRangeRecipeHandler<CraftingStationMenu>(
                recipe -> recipe.getCategory() == VanillaEmiRecipeCategories.CRAFTING
                        && (recipe.supportsRecipeTree() || recipe instanceof RetexturedCraftingEmiRecipe),
                IntStream.range(0, CraftingStationMenu.GRID_SLOTS).toArray(), CraftingStationMenu.CONTAINER_SLOTS, menu -> menu.slots.size()));
        registry.addRecipeHandler(ForgeweaveMenus.PART_BUILDER.get(), new SlotRangeRecipeHandler<PartBuilderMenu>(
                recipe -> recipe.getCategory() == ForgeweaveEmiCategories.PART_CRAFTING,
                new int[] {PartBuilderMenu.PATTERN_SLOT, PartBuilderMenu.MATERIAL_SLOT},
                PartBuilderMenu.CONTAINER_SLOTS, menu -> menu.slots.size()));
        registry.addRecipeHandler(ForgeweaveMenus.TOOL_STATION.get(), new ToolStationRecipeHandler());
        registry.addRecipeHandler(ForgeweaveMenus.MODIFIER_WORKTABLE.get(), new SlotRangeRecipeHandler<ModifierWorktableMenu>(
                recipe -> recipe.getCategory() == ForgeweaveEmiCategories.MODIFIER_WORKTABLE,
                new int[] {ModifierWorktableMenu.INPUT_START}, ModifierWorktableMenu.RESULT_SLOT + 1,
                ModifierWorktableMenu::playerInventoryEnd));
    }
}
