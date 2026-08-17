package dev.gkissel.forgeweave.jei;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.subtypes.IIngredientSubtypeInterpreter;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.ISubtypeRegistration;

import net.neoforged.neoforge.registries.DeferredHolder;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.client.ChestScreen;
import dev.gkissel.forgeweave.client.CraftingStationScreen;
import dev.gkissel.forgeweave.client.PartBuilderScreen;
import dev.gkissel.forgeweave.client.SmelteryScreen;
import dev.gkissel.forgeweave.client.StencilTableScreen;
import dev.gkissel.forgeweave.client.ToolStationScreen;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ContentFamilies;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.EmbossingRecipe;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * JEI integration (docs/SCOPE.md M1 issue #11): three display-only recipe categories -- part
 * crafting, tool assembly, and repair -- built from the same rules {@code menu.PartBuilderRecipes}
 * and {@code menu.ToolAssemblyRecipes} apply live at the stations. {@code PartBuilderRecipes} makes
 * its cost constants and value math ({@code computeCost}, issue #45) {@code public} specifically so
 * this package can reuse them instead of re-deriving; the pattern/part/tool wiring those classes keep
 * package-private is re-declared here instead, since exposing it would widen the menu package's API
 * for no reuse benefit. This package still only ever depends on the mod, never the reverse.
 *
 * <p>Materials are a datapack registry (ADR-0002), not a static Java list, so they are only known
 * once a world is joined and the server has synced them. JEI calls {@link #registerRecipes} again
 * every time the client connects to a world or server -- its whole runtime is rebuilt per session,
 * per {@link IModPlugin#onRuntimeUnavailable}'s javadoc ("after a user quits or logs out of a
 * world") -- so reading {@code Minecraft.getInstance().level}'s registry access here always
 * reflects whatever materials that session's server actually synced, including modpack-added ones.
 *
 * <p>Plugin structure (splitting registration into {@code registerCategories}/{@code
 * registerRecipes}, pairing each category with its station item as a recipe catalyst) follows
 * Tinker's JEI's {@code TConstructModule.java} (docs/SCOPE.md M1 source policy names Tinker's JEI as
 * the derivation-eligible reference for this plugin) -- NOTICE.md row. Upstream repo is
 * {@code PssbleTrngle/TinkersJEI} (MIT), a different repository from the TinkersConstruct clones
 * this project otherwise reads from; only the plugin *shape* carries over; the 1.12 Forge
 * {@code IModPlugin}/{@code IRecipeCategory} API it implements against is unrelated to and
 * incompatible with JEI's current NeoForge 1.21 API used here.
 */
@JeiPlugin
public final class ForgeweaveJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    /**
     * Issue #307: without this, every material variant of a part or tool -- and every wood variant
     * of a retextured station (issue #43) -- collapses into a single JEI entry, since a bare
     * {@code ItemStack} carries no material/texture info in its {@code Item} identity; it is all in
     * data components. The three interpreters below (a fourth, upstream's pattern/cast interpreter,
     * has no Forgeweave equivalent -- see {@link SubtypeKeys}'s javadoc) each wrap one
     * {@link SubtypeKeys} method, mapping its {@code null} ("no component set") to this API's
     * {@code NONE} sentinel.
     *
     * <p>Registration itself walks the live item roster rather than a hand list, so a newly added
     * part or tool is covered automatically: every {@link PartItem} straight off the item registry
     * (same idiom as {@code client.ForgeweaveItemColors#tintedPartItems}), every assemblable tool off
     * {@link ToolAssemblyRecipes#ENTRIES} (ditto {@code #tintedToolItems}). The texture-bearing
     * station items are a fixed five (issue #43/#44/#40/#152's retextured-table items), so those are
     * named directly.
     */
    @Override
    public void registerItemSubtypes(ISubtypeRegistration registration) {
        IIngredientSubtypeInterpreter<ItemStack> partInterpreter = (stack, context) -> orNone(SubtypeKeys.part(stack));
        IIngredientSubtypeInterpreter<ItemStack> toolInterpreter = (stack, context) -> orNone(SubtypeKeys.tool(stack));
        IIngredientSubtypeInterpreter<ItemStack> textureInterpreter = (stack, context) -> orNone(SubtypeKeys.texture(stack));

        ForgeweaveItems.ITEMS.getEntries().stream()
                .<Item>map(DeferredHolder::get)
                .filter(item -> item instanceof PartItem)
                .forEach(item -> registration.registerSubtypeInterpreter(item, partInterpreter));

        ToolAssemblyRecipes.ENTRIES.stream()
                .map(entry -> entry.tool().get())
                .forEach(tool -> registration.registerSubtypeInterpreter(tool, toolInterpreter));

        registration.registerSubtypeInterpreter(ForgeweaveItems.PART_BUILDER.get(), textureInterpreter);
        registration.registerSubtypeInterpreter(ForgeweaveItems.TOOL_STATION.get(), textureInterpreter);
        registration.registerSubtypeInterpreter(ForgeweaveItems.TOOL_FORGE.get(), textureInterpreter);
        registration.registerSubtypeInterpreter(ForgeweaveItems.CRAFTING_STATION.get(), textureInterpreter);
        registration.registerSubtypeInterpreter(ForgeweaveItems.STENCIL_TABLE.get(), textureInterpreter);
    }

    private static String orNone(String key) {
        return key == null ? IIngredientSubtypeInterpreter.NONE : key;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new PartCraftingCategory(helper),
                new AssemblyCategory(helper, AssemblyCategory.TYPE,
                        Component.translatable("jei.category.forgeweave.tool_assembly"),
                        new ItemStack(ForgeweaveItems.TOOL_STATION.get())),
                // #165: the Tool Forge tier gets its own category so its catalyst list can say
                // "Tool Forge only" instead of the Tool Station -- JEI has no way to vary one
                // category's catalysts per recipe.
                new AssemblyCategory(helper, AssemblyCategory.LARGE_TYPE,
                        Component.translatable("jei.category.forgeweave.large_tool_assembly"),
                        new ItemStack(ForgeweaveItems.TOOL_FORGE.get())),
                new RepairCategory(helper),
                // #109 -- smeltery/casting/modifier JEI categories (docs/SCOPE.md M2 issue #109).
                new MeltingCategory(helper),
                new AlloyingCategory(helper),
                new CastingTableCategory(helper),
                new CastingBasinCategory(helper),
                new ModifierApplicationCategory(helper),
                // #165: embossing (issue #154's mechanic), the repair tab's fourth recipe.
                new EmbossingCategory(helper));
    }

    /**
     * <b>Content-family toggles ticket.</b> Every list below is filtered against the {@code content}
     * config section before it is handed to JEI, so a family the server has switched off has no
     * entries to find. Two things about that are worth stating plainly:
     *
     * <ul>
     *   <li>The values read here are the <em>server's</em>. {@link ForgeweaveConfig} is a
     *       {@code SERVER}-type spec, which NeoForge syncs during login, and this method runs once
     *       the session is up (see the class javadoc) -- so the filter agrees with what the station
     *       will actually do, including on a dedicated server whose values differ from the local
     *       file's.
     *   <li><b>Known limit:</b> the toggles are hot-reloadable everywhere else, but JEI builds its
     *       recipe lists once per session. Flipping a family <em>while</em> a world is open leaves
     *       JEI showing the previous roster until the next join. Nothing is wrong when that happens
     *       -- the station, part builder, casting and smeltery all follow the new value immediately,
     *       so a stale JEI entry simply refuses when clicked through. The pinned JEI
     *       ({@code jei_version} in gradle.properties) exposes no supported mid-session
     *       re-registration hook, and standing up a config listener that rebuilt the runtime would
     *       be a lot of machinery for a case only a pack author editing live ever hits.
     * </ul>
     *
     * <p>Repair and part exchange are deliberately <em>not</em> filtered: both act on a tool that
     * already exists without altering what it is, which the ticket's "items already in the world
     * keep working" rule keeps available whatever a toggle says. Embossing and fortification are
     * the other side of that line -- they change the tool -- and go with {@code modifiers}.
     */
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Map<ResourceLocation, Material> materials = currentMaterials();
        registration.addRecipes(PartCraftingCategory.TYPE, PartCraftingRecipes.build(materials).stream()
                .filter(recipe -> ContentFamilies.itemEnabled(recipe.result()))
                .toList());

        // Split by AssemblyRecipes#isLarge (issue #165) so each half lands in the category whose
        // catalyst list matches where it can actually be built -- see AssemblyCategory's class javadoc.
        List<AssemblyRecipe> assembly = AssemblyRecipes.build(materials).stream()
                .filter(recipe -> ContentFamilies.itemEnabled(recipe.tool()))
                .toList();
        registration.addRecipes(AssemblyCategory.TYPE,
                assembly.stream().filter(recipe -> !AssemblyRecipes.isLarge(recipe)).toList());
        registration.addRecipes(AssemblyCategory.LARGE_TYPE,
                assembly.stream().filter(AssemblyRecipes::isLarge).toList());

        registration.addRecipes(RepairCategory.TYPE, RepairRecipes.build(materials));

        // #109 -- smeltery/casting/modifier JEI categories (docs/SCOPE.md M2 issue #109). Same
        // per-session registry read as currentMaterials() above: melting, alloying, casting and
        // modifier recipes are also NeoForge datapack registries with network codecs
        // (Forgeweave#registerDataPackRegistries), so the client's synced copy is exactly what a
        // joined session actually has -- including whatever #104's nether-ore melting recipes land
        // mid-milestone, with no special-casing needed here.
        //
        // The smeltery family gates all four of the smeltery categories at once (melting, alloying
        // and both casting stations), and the modifier family gates modifier application; the
        // casting lists are additionally filtered per recipe, since a pour that shapes a part only
        // an off tool family takes goes with that family rather than with the smeltery.
        boolean smeltery = ForgeweaveConfig.enabled(ForgeweaveConfig.SMELTERY);
        Map<ResourceLocation, CastingRecipe> castingRecipes = smeltery ? currentCastingRecipes() : Map.of();
        registration.addRecipes(MeltingCategory.TYPE,
                smeltery ? MeltingRecipes.build(currentMeltingRecipes()) : List.of());
        registration.addRecipes(AlloyingCategory.TYPE,
                smeltery ? AlloyingRecipes.build(currentAlloyRecipes()) : List.of());
        registration.addRecipes(CastingTableCategory.TYPE, castingEnabled(CastingRecipes.table(castingRecipes)));
        registration.addRecipes(CastingBasinCategory.TYPE, castingEnabled(CastingRecipes.basin(castingRecipes)));
        registration.addRecipes(ModifierApplicationCategory.TYPE, ForgeweaveConfig.enabled(ForgeweaveConfig.MODIFIERS)
                ? ModifierApplicationRecipes.build(currentModifierRecipes())
                : List.of());
        // #165: embossing's own datapack registry, same read shape as the other four above --
        // and gated by the same modifiers key, since embossing is one of the things `modifiers`
        // covers (maintainer decision: everything that alters a tool at the station beyond repair
        // and part exchange). Fortification needs no line of its own: its recipe is a
        // ModifierRecipe, so it rides the ModifierApplicationCategory gate above.
        registration.addRecipes(EmbossingCategory.TYPE, ForgeweaveConfig.enabled(ForgeweaveConfig.MODIFIERS)
                ? EmbossingRecipes.build(currentEmbossingRecipes(), materials)
                : List.of());
    }

    /** The casting recipes whose cast and result both belong to families that are currently on. */
    private static List<CastingRecipe> castingEnabled(List<CastingRecipe> recipes) {
        return recipes.stream()
                .filter(recipe -> ContentFamilies.itemEnabled(recipe.result())
                        && recipe.cast().map(cast -> java.util.Arrays.stream(cast.getItems())
                                .anyMatch(ContentFamilies::itemEnabled)).orElse(true))
                .toList();
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ForgeweaveItems.PART_BUILDER.get(), PartCraftingCategory.TYPE);
        // #109/#165: the repair tab is also the modify tab and the emboss tab (menu.ToolStationMenu's
        // class javadoc), so the Tool Station is a catalyst for all three alongside plain assembly.
        registration.addRecipeCatalyst(ForgeweaveItems.TOOL_STATION.get(),
                AssemblyCategory.TYPE, RepairCategory.TYPE, ModifierApplicationCategory.TYPE, EmbossingCategory.TYPE);
        // #152: the Tool Forge does everything the Tool Station does, so it catalyses the same four,
        // plus #165's large-tool-only category the Tool Station never appears under.
        registration.addRecipeCatalyst(ForgeweaveItems.TOOL_FORGE.get(),
                AssemblyCategory.TYPE, AssemblyCategory.LARGE_TYPE, RepairCategory.TYPE,
                ModifierApplicationCategory.TYPE, EmbossingCategory.TYPE);
        // A shard always pays a part's cost exactly (SHARD_VALUE divides both HEAD_COST and
        // SMALL_PART_COST with no remainder), so it's as legitimate a "what can this craft" lookup
        // target as the station itself (issue #45's Part Crafting rework).
        registration.addRecipeCatalyst(ForgeweaveItems.SHARD.get(), PartCraftingCategory.TYPE);
        registration.addRecipeCatalyst(ForgeweaveItems.CRAFTING_STATION.get(), RecipeTypes.CRAFTING);

        // #109 -- smeltery/casting JEI categories (docs/SCOPE.md M2 issue #109): both core tiers
        // melt and alloy, so both are catalysts for both categories.
        registration.addRecipeCatalyst(ForgeweaveItems.STANDARD_CORE.get(), MeltingCategory.TYPE, AlloyingCategory.TYPE);
        registration.addRecipeCatalyst(ForgeweaveItems.NETHER_CORE.get(), MeltingCategory.TYPE, AlloyingCategory.TYPE);
        registration.addRecipeCatalyst(ForgeweaveItems.CASTING_TABLE.get(), CastingTableCategory.TYPE);
        registration.addRecipeCatalyst(ForgeweaveItems.CASTING_BASIN.get(), CastingBasinCategory.TYPE);
    }

    /**
     * Keeps JEI's overlay off the stations' side chrome (docs/SCOPE.md issue #68 fix 4). All four
     * station screens draw tabs, information panels and/or side-inventory panels outside the
     * rectangle {@code AbstractContainerScreen} advertises, which is all JEI would otherwise see.
     *
     * <p>Also wires up the smeltery tank's ingredient-under-mouse handler (issue #308): unlike the
     * station handler above, this one does not extend JEI's visible rectangle -- it answers "what
     * fluid is under the cursor" so R/U recipe lookup works on tank contents, upstream 1.12's {@code
     * TinkerGuiTankHandler}.
     */
    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(CraftingStationScreen.class, new StationGuiHandler<>());
        registration.addGuiContainerHandler(PartBuilderScreen.class, new StationGuiHandler<>());
        registration.addGuiContainerHandler(ToolStationScreen.class, new StationGuiHandler<>());
        registration.addGuiContainerHandler(StencilTableScreen.class, new StationGuiHandler<>());
        // Issue #78: the chests have no chrome of their own, but they do get the station-group tab row.
        registration.addGuiContainerHandler(ChestScreen.class, new StationGuiHandler<>());
        registration.addGuiContainerHandler(SmelteryScreen.class, new SmelteryTankGuiHandler());
    }

    /**
     * Recipe-click [+] transfer (docs/SCOPE.md M1 issue #40, extended to Tool Assembly and Tool
     * Repair by that issue's follow-ups): vanilla crafting recipes into the Crafting Station, Part
     * Crafting recipes into the Part Builder, and Tool Assembly/Repair recipes into the Tool Station
     * -- the latter two also select the recipe's tool tab first ({@link AssemblyTransferHandler},
     * {@link RepairTransferHandler}), since the tab decides where the input slots sit and what each
     * one accepts.
     */
    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(new CraftingStationTransferInfo());

        // Part Builder's pattern (slot 0) and material (slot 1) slots are contiguous and in the same
        // order PartCraftingCategory lays its two input slots out in, and its player-inventory slots
        // are the next contiguous 36 (PartBuilderMenu#layoutPlayerInventorySlots) -- exactly what the
        // basic transfer handler needs, so no custom IRecipeTransferInfo is needed here.
        registration.addRecipeTransferHandler(PartBuilderMenu.class, ForgeweaveMenus.PART_BUILDER.get(), PartCraftingCategory.TYPE, 0, 2, 4, 36);

        registration.addRecipeTransferHandler(
                new AssemblyTransferHandler(registration.getTransferHelper(), AssemblyCategory.TYPE), AssemblyCategory.TYPE);
        registration.addRecipeTransferHandler(
                new AssemblyTransferHandler(registration.getTransferHelper(), AssemblyCategory.LARGE_TYPE), AssemblyCategory.LARGE_TYPE);
        registration.addRecipeTransferHandler(new RepairTransferHandler(registration.getTransferHelper()), RepairCategory.TYPE);

        // #109 -- modifier application transfer (docs/SCOPE.md M2 issue #109): melting and alloying
        // happen automatically inside the smeltery tank and casting has no menu at all (both
        // in-world mechanics with nothing to transfer into), so only this fifth category gets a [+]
        // button.
        registration.addRecipeTransferHandler(
                new ModifierApplicationTransferHandler(registration.getTransferHelper()), ModifierApplicationCategory.TYPE);
        // #165: embossing's own [+] button, the repair tab's fourth and last recipe kind.
        registration.addRecipeTransferHandler(
                new EmbossingTransferHandler(registration.getTransferHelper()), EmbossingCategory.TYPE);
    }

    /** Empty (not an error) at the title screen, before any world is joined and materials are synced. */
    private static Map<ResourceLocation, Material> currentMaterials() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Map.of();
        }

        Registry<Material> registry = level.registryAccess().registryOrThrow(Material.REGISTRY);
        Map<ResourceLocation, Material> materials = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<Material>, Material> entry : registry.entrySet()) {
            materials.put(entry.getKey().location(), entry.getValue());
        }
        return materials;
    }

    // #109 -- same per-session synced-registry read as currentMaterials() above, one per datapack
    // registry this plugin's M2 categories need (docs/SCOPE.md M2 issue #109).

    private static Map<ResourceLocation, MeltingRecipe> currentMeltingRecipes() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Map.of();
        }

        Registry<MeltingRecipe> registry = level.registryAccess().registryOrThrow(MeltingRecipe.REGISTRY);
        Map<ResourceLocation, MeltingRecipe> recipes = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<MeltingRecipe>, MeltingRecipe> entry : registry.entrySet()) {
            recipes.put(entry.getKey().location(), entry.getValue());
        }
        return recipes;
    }

    private static Map<ResourceLocation, AlloyRecipe> currentAlloyRecipes() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Map.of();
        }

        Registry<AlloyRecipe> registry = level.registryAccess().registryOrThrow(AlloyRecipe.REGISTRY);
        Map<ResourceLocation, AlloyRecipe> recipes = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<AlloyRecipe>, AlloyRecipe> entry : registry.entrySet()) {
            recipes.put(entry.getKey().location(), entry.getValue());
        }
        return recipes;
    }

    private static Map<ResourceLocation, CastingRecipe> currentCastingRecipes() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Map.of();
        }

        Registry<CastingRecipe> registry = level.registryAccess().registryOrThrow(CastingRecipe.REGISTRY);
        Map<ResourceLocation, CastingRecipe> recipes = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<CastingRecipe>, CastingRecipe> entry : registry.entrySet()) {
            recipes.put(entry.getKey().location(), entry.getValue());
        }
        return recipes;
    }

    private static Map<ResourceLocation, ModifierRecipe> currentModifierRecipes() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Map.of();
        }

        Registry<ModifierRecipe> registry = level.registryAccess().registryOrThrow(ModifierRecipe.REGISTRY);
        Map<ResourceLocation, ModifierRecipe> recipes = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<ModifierRecipe>, ModifierRecipe> entry : registry.entrySet()) {
            recipes.put(entry.getKey().location(), entry.getValue());
        }
        return recipes;
    }

    private static Map<ResourceLocation, EmbossingRecipe> currentEmbossingRecipes() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return Map.of();
        }

        Registry<EmbossingRecipe> registry = level.registryAccess().registryOrThrow(EmbossingRecipe.REGISTRY);
        Map<ResourceLocation, EmbossingRecipe> recipes = new LinkedHashMap<>();
        for (Map.Entry<ResourceKey<EmbossingRecipe>, EmbossingRecipe> entry : registry.entrySet()) {
            recipes.put(entry.getKey().location(), entry.getValue());
        }
        return recipes;
    }
}
