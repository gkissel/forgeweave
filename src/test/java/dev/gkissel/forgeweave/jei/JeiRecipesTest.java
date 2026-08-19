package dev.gkissel.forgeweave.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.modifier.EmbossingRecipe;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;

/**
 * Pins the pure recipe-building logic behind the JEI plugin's three categories: given a set of
 * materials, does it enumerate the right display recipes? Deliberately does not touch any JEI class
 * (JEI is compileOnly/optional -- see build.gradle), so it needs no more than the same Minecraft
 * bootstrap the rest of the test suite already uses.
 */
class JeiRecipesTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** A material with every stat block; {@link #statlessMaterial} is the counterpart with none. */
    private static Material material(List<Material.CraftingItem> craftingItems, Ingredient repairItem) {
        return new Material(
                Optional.of(new Material.Head(100, 1.0f, 1.0f)),
                Optional.of(new Material.Handle(1.0f, 10)),
                Optional.of(5),
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_wooden_tool")),
                Material.Traits.general(ResourceLocation.fromNamespaceAndPath("forgeweave", "ecological")),
                craftingItems,
                repairItem,
                TextColor.fromRgb(0xFFFFFF),
                Optional.of(new Material.Bow(1.0f, 1.0f, 0.0f)),
                Optional.of(new Material.Bowstring(1.0f)));
    }

    /**
     * A material carrying only the bowstring block -- the shape issue #392's {@code string} and
     * {@code vine} actually have. Every material before them had every block, so nothing could
     * tell whether the enumerator asked.
     */
    private static Material bowstringOnlyMaterial() {
        return new Material(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(craftingItem(Items.STRING, 2)),
                Ingredient.of(Items.STRING),
                TextColor.fromRgb(0xEEEEEE),
                Optional.empty(),
                Optional.of(new Material.Bowstring(1.0f)));
    }

    /** A metal: the crafting items are still listed (the config can turn them back on), but cast-only. */
    private static Material castOnlyMaterial() {
        return new Material(
                Optional.of(new Material.Head(100, 1.0f, 1.0f)),
                Optional.of(new Material.Handle(1.0f, 10)),
                Optional.of(5),
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_diamond_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(craftingItem(Items.IRON_INGOT, 2)),
                Ingredient.of(Items.IRON_INGOT),
                TextColor.fromRgb(0xCACACA),
                Optional.of(new Material.Bow(1.0f, 1.0f, 0.0f)),
                Optional.of(new Material.Bowstring(1.0f)),
                true);
    }

    private static Material.CraftingItem craftingItem(Item item, int value) {
        return new Material.CraftingItem(Ingredient.of(item), value);
    }

    /** wood: stick=shard, planks=ingot, log=4 ingots -- the real shipped wood.json values (issue #45, T58 units). */
    private static Map<ResourceLocation, Material> twoMaterials() {
        Map<ResourceLocation, Material> materials = new LinkedHashMap<>();
        materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", "wood"),
                material(List.of(
                        craftingItem(Items.STICK, PartBuilderRecipes.SHARD_VALUE),
                        craftingItem(Items.OAK_PLANKS, PartBuilderRecipes.INGOT_VALUE),
                        craftingItem(Items.OAK_LOG, 4 * PartBuilderRecipes.INGOT_VALUE)),
                        Ingredient.of(Items.OAK_PLANKS)));
        materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", "stone"),
                material(List.of(craftingItem(Items.COBBLESTONE, PartBuilderRecipes.INGOT_VALUE)), Ingredient.of(Items.COBBLESTONE)));
        return materials;
    }

    @Test
    void partCraftingEnumeratesEveryPartTypeTimesEveryMaterial() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());

        // 5 M1 part types + 17 M3 part types (docs/SCOPE.md issue #151) + the war mace head
        // (issue #161) + the curved blade (issue #159) + the katana blade (issue #160) + the
        // sharpening kit (issue #271, the one part no tool is built from) + the bow limb and bow
        // string (issue #393) + the shard (issue #605, upstream's other no-tool part) x 2
        // materials, both of which carry every stat block.
        assertEquals(29 * 2, recipes.size(), "29 part types x 2 materials");
        assertTrue(recipes.stream().allMatch(r -> r.result().has(ForgeweaveDataComponents.MATERIAL.get())));
    }

    /**
     * M3.5 issue #401: the three bows reach JEI's assembly categories off the same
     * {@link ToolAssemblyRecipes#ENTRIES} table every other tool does, with their own slot order --
     * so this pins that the registry-driven path really does produce a bow recipe, and produces it
     * with upstream's part composition ({@code ShortBow}/{@code LongBow}/{@code CrossBow}'s
     * {@code PartMaterialType} lists) rather than a melee tool's.
     */
    @Test
    void assemblyListsTheThreeBowsWithUpstreamsPartOrder() {
        List<AssemblyRecipe> recipes = AssemblyRecipes.build(twoMaterials());

        assertEquals(List.of("bow_limb", "bow_limb", "bow_string"),
                slotPartIds(recipes, ForgeweaveItems.TOOL_SHORTBOW.get()));
        assertEquals(List.of("bow_limb", "bow_limb", "large_plate", "bow_string"),
                slotPartIds(recipes, ForgeweaveItems.TOOL_LONGBOW.get()));
        assertEquals(List.of("tough_tool_rod", "bow_limb", "tough_binding", "bow_string"),
                slotPartIds(recipes, ForgeweaveItems.TOOL_CROSSBOW.get()));
    }

    /**
     * M3.5 issue #401: which JEI category a bow lands in -- {@code AssemblyCategory.TYPE} (Tool
     * Station catalyst) or {@code LARGE_TYPE} (Tool Forge catalyst) -- is {@link
     * AssemblyRecipes#isLarge}, i.e. {@code #forgeweave:large_tools}. Item tags aren't bound outside
     * a running server, so the answers themselves are pinned by {@code
     * gametest.ToolForgeGameTests#theTwoForgeTierBowsAreTaggedLarge}; what this test can guard is
     * that each bow resolves to an entry at all, which is the step that silently returns
     * {@code false} (and so mis-files a Tool Forge bow under the Tool Station) when it fails.
     */
    @Test
    void everyBowResolvesToAnAssemblyTableEntryForTheCategorySplit() {
        for (Item bow : List.of(ForgeweaveItems.TOOL_SHORTBOW.get(), ForgeweaveItems.TOOL_LONGBOW.get(),
                ForgeweaveItems.TOOL_CROSSBOW.get())) {
            assertTrue(ToolAssemblyRecipes.entryFor(new ItemStack(bow)).isPresent(),
                    bow + " has no assembly entry, so AssemblyRecipes#isLarge would answer false for it");
        }
    }

    /**
     * Issue #393/#405: a bow's slots cycle only the materials that slot has a stat block for, so JEI
     * never advertises a string bow limb or a wooden bow string. The string-shaped material carries
     * the BOWSTRING block alone, so it appears in the shortbow's third slot and in neither limb.
     */
    @Test
    void bowAssemblySlotsOnlyCycleMaterialsWithThatSlotsStatBlock() {
        Map<ResourceLocation, Material> materials = new LinkedHashMap<>(twoMaterials());
        materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", "string"), bowstringOnlyMaterial());

        AssemblyRecipe shortbow = recipeFor(AssemblyRecipes.build(materials),
                ForgeweaveItems.TOOL_SHORTBOW.get());

        assertEquals(List.of("wood", "stone"), slotMaterialPaths(shortbow, 0));
        assertEquals(List.of("wood", "stone"), slotMaterialPaths(shortbow, 1));
        assertEquals(List.of("wood", "stone", "string"), slotMaterialPaths(shortbow, 2));
    }

    private static AssemblyRecipe recipeFor(List<AssemblyRecipe> recipes, Item tool) {
        return recipes.stream().filter(recipe -> recipe.result().is(tool)).findFirst().orElseThrow();
    }

    /** The registered path of the part item each slot of {@code tool}'s recipe offers. */
    private static List<String> slotPartIds(List<AssemblyRecipe> recipes, Item tool) {
        return recipeFor(recipes, tool).parts().stream()
                .map(slot -> BuiltInRegistries.ITEM.getKey(slot.getFirst().getItem()).getPath())
                .toList();
    }

    private static List<String> slotMaterialPaths(AssemblyRecipe recipe, int slot) {
        return recipe.parts().get(slot).stream()
                .map(stack -> stack.get(ForgeweaveDataComponents.MATERIAL.get()).getPath())
                .toList();
    }

    /**
     * Issue #393: JEI must apply the same per-kind stat gate the station does
     * ({@code PartBuilderRecipes#resolve}, issue #392), or it advertises crafts the Part Builder
     * silently refuses -- a string pickaxe head, a wooden bow string.
     */
    @Test
    void partCraftingSkipsPartsAMaterialHasNoStatBlockFor() {
        Map<ResourceLocation, Material> materials = new LinkedHashMap<>();
        materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", "string"), bowstringOnlyMaterial());

        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(materials);

        // Issue #605: the shard is the one part every material can stamp regardless of stat blocks
        // -- upstream's Shard#canUseMaterial is unconditionally true, mirrored by
        // Material#hasStatsFor answering true for PartItem.Kind.NONE.
        assertEquals(2, recipes.size(), "a bowstring-only material can craft the bow string and the shard");
        assertTrue(recipes.stream().anyMatch(r -> r.result().is(ForgeweaveItems.PART_BOW_STRING.get())),
                "the bow string is one of them");
        assertTrue(recipes.stream().anyMatch(r -> r.result().is(ForgeweaveItems.SHARD.get())),
                "the shard is the other");
    }

    @Test
    void partCraftingCyclesEveryCraftingItemPlusTheShardForAHead() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());
        PartCraftingRecipe woodPickaxeHead = recipes.stream()
                .filter(r -> r.pattern().is(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get())
                        && r.result().get(ForgeweaveDataComponents.MATERIAL.get()).getPath().equals("wood"))
                .findFirst().orElseThrow();

        // stick, planks, log, shard -- 4 cycling options for a 2-ingot head.
        assertEquals(4, woodPickaxeHead.materialInputs().size());
        assertEquals(4, woodPickaxeHead.changeOutputs().size());

        assertEquals(4, countByItem(woodPickaxeHead, Items.STICK), "shard-value stick needs 4 to cover a head");
        assertNull(changeFor(woodPickaxeHead, Items.STICK), "exact payment leaves no change");

        assertEquals(2, countByItem(woodPickaxeHead, Items.OAK_PLANKS), "ingot-value planks need 2 to cover a head");
        assertNull(changeFor(woodPickaxeHead, Items.OAK_PLANKS), "exact payment leaves no change");

        assertEquals(1, countByItem(woodPickaxeHead, Items.OAK_LOG), "4-ingot log overpays a head with just 1");
        ItemStack logChange = changeFor(woodPickaxeHead, Items.OAK_LOG);
        assertEquals(4, logChange.getCount(), "log overpays a 2-ingot head by 2 ingots = 4 shards");
        assertTrue(logChange.is(ForgeweaveItems.SHARD.get()));
    }

    @Test
    void partCraftingShardOptionAlwaysPaysExactly() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());

        for (PartCraftingRecipe recipe : recipes) {
            int shardIndex = indexOfItem(recipe, ForgeweaveItems.SHARD.get());
            assertTrue(shardIndex >= 0, "the shard is always a valid crafting option");
            assertNull(recipe.changeOutputs().get(shardIndex), "the shard's value divides every part cost evenly");
        }
    }

    @Test
    void partCraftingCostsMatchPartBuilderRecipesConstants() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());

        PartCraftingRecipe stoneBinding = recipes.stream()
                .filter(r -> r.pattern().is(ForgeweaveItems.PATTERN_TOOL_BINDING.get())
                        && r.result().get(ForgeweaveDataComponents.MATERIAL.get()).getPath().equals("stone"))
                .findFirst().orElseThrow();

        PartBuilderRecipes.CostResult expected = PartBuilderRecipes.computeCost(PartBuilderRecipes.SMALL_PART_COST, PartBuilderRecipes.INGOT_VALUE);
        assertEquals(expected.itemsNeeded(), countByItem(stoneBinding, Items.COBBLESTONE));
    }

    private static int countByItem(PartCraftingRecipe recipe, Item item) {
        int index = indexOfItem(recipe, item);
        return index < 0 ? -1 : recipe.materialInputs().get(index).getCount();
    }

    private static ItemStack changeFor(PartCraftingRecipe recipe, Item item) {
        return recipe.changeOutputs().get(indexOfItem(recipe, item));
    }

    private static int indexOfItem(PartCraftingRecipe recipe, Item item) {
        List<ItemStack> inputs = recipe.materialInputs();
        for (int i = 0; i < inputs.size(); i++) {
            if (inputs.get(i).is(item)) {
                return i;
            }
        }
        return -1;
    }

    @Test
    void assemblyHasOneRecipePerToolTypeWithEveryMaterialCycling() {
        Map<ResourceLocation, Material> materials = twoMaterials();
        List<AssemblyRecipe> recipes = AssemblyRecipes.build(materials);

        // One per assemblable tool: M1's three plus M3's six station weapons (issue #155).
        assertEquals(ToolAssemblyRecipes.ENTRIES.size(), recipes.size());
        for (AssemblyRecipe recipe : recipes) {
            ToolAssemblyRecipes.Entry entry = ToolAssemblyRecipes.entryFor(recipe.result()).orElseThrow();
            assertEquals(entry.slotCount(), recipe.parts().size(),
                    "one JEI slot per part, so a two-part weapon shows two");
            for (List<ItemStack> slot : recipe.parts()) {
                assertEquals(materials.size(), slot.size());
            }
        }
    }

    @Test
    void repairHasOneRecipePerMaterialCoveringEveryToolType() {
        Map<ResourceLocation, Material> materials = twoMaterials();
        List<RepairRecipe> recipes = RepairRecipes.build(materials);

        assertEquals(materials.size(), recipes.size());
        for (RepairRecipe recipe : recipes) {
            assertEquals(ToolAssemblyRecipes.ENTRIES.size(), recipe.tools().size(),
                    "every assemblable tool repairs the same way");
            assertEquals(recipe.tools(), recipe.repairedTools());
        }
    }

    /**
     * Parity audit T30 (issue #461): repair takes every one of the material's crafting items, so the
     * JEI row cycles all of them rather than advertising the single {@code repair_item}. Wood's plank
     * is both a crafting item and the repair item and must still appear exactly once.
     */
    @Test
    void repairListsEveryCraftingItemOfTheMaterial() {
        List<RepairRecipe> recipes = RepairRecipes.build(twoMaterials());

        assertEquals(List.of(Items.STICK, Items.OAK_PLANKS, Items.OAK_LOG),
                recipes.get(0).repairItems().stream().map(ItemStack::getItem).toList());
        assertEquals(List.of(Items.COBBLESTONE),
                recipes.get(1).repairItems().stream().map(ItemStack::getItem).toList());
    }

    /**
     * Issue #435 (parity audit T3): a cast-only material has no Part Builder path at all with
     * {@code craftCastableMaterials} at upstream's {@code false} default, so JEI must not advertise
     * one -- the same gate {@code PartBuilderRecipes#materialValue} applies at the station. Its
     * repair and assembly rows are untouched: cast-only is about how parts are <em>made</em>.
     */
    @Test
    void partCraftingSkipsCastOnlyMaterials() {
        Map<ResourceLocation, Material> materials = new LinkedHashMap<>();
        materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", "iron"), castOnlyMaterial());

        assertTrue(PartCraftingRecipes.build(materials).isEmpty(),
                "a cast-only material stamps no part in the Part Builder, so JEI shows none");
        assertEquals(1, RepairRecipes.build(materials).size(),
                "cast-only gates the Part Builder, not repair");
    }

    @Test
    void emptyMaterialsProduceNoRecipes() {
        Map<ResourceLocation, Material> none = Map.of();

        assertTrue(PartCraftingRecipes.build(none).isEmpty());
        assertTrue(AssemblyRecipes.build(none).isEmpty());
        assertTrue(RepairRecipes.build(none).isEmpty());
    }

    // ------------------------------------------------------------------ #109: melting, alloying, casting, modifiers

    @Test
    void meltingBuildsOneDisplayPerNonCollidingRecipe() {
        Map<ResourceLocation, MeltingRecipe> recipes = new LinkedHashMap<>();
        recipes.put(id("iron_ingot"), new MeltingRecipe(Ingredient.of(Items.IRON_INGOT), Fluids.LAVA, 144, 534, false));

        List<MeltingDisplay> displays = MeltingRecipes.build(recipes);

        assertEquals(1, displays.size());
        MeltingDisplay display = displays.get(0);
        assertEquals(144, display.amount());
        assertEquals(534, display.temperature());
        assertTrue(!display.ore());
        assertEquals(1, display.inputs().size());
        assertTrue(display.inputs().get(0).is(Items.IRON_INGOT));
    }

    /**
     * The scenario #96's PR #124 warns about: an item can match both a broad "default" recipe and a
     * more specific override, and a naive JEI view that enumerates each recipe's {@code Ingredient}
     * independently would show the item under both -- copper ore reading 144 <em>and</em> 504. This
     * pins the fix at the JEI layer: {@link MeltingRecipes#build} must never let the same item appear
     * in two different displayed recipes, and whichever recipe it does show under must be the one
     * {@link MeltingRecipe#resolve} -- the shared "most specific wins" resolver {@code
     * recipe.MeltingRecipe#find} also uses at melt time -- actually picks.
     *
     * <p>Uses two item-list {@code Ingredient}s rather than a real {@code c:} tag for the broad
     * recipe: {@code recipe.MeltingRecipeTest} already documents that tag contents aren't bound
     * without a running server in a plain unit test, so a tag-based {@code Ingredient} would never
     * match anything here and the collision this test exists to catch couldn't happen at all. The
     * tie-break rule itself (item beats tag) is pinned separately by {@code
     * recipe.MeltingRecipeTest#itemInputsAreMoreSpecificThanTagInputs}; this test's job is only to
     * prove the JEI view correctly reflects whichever recipe {@link MeltingRecipe#resolve} decides.
     */
    @Test
    void meltingViewDedupesAnItemMatchedByTwoOverlappingRecipes() {
        Map<ResourceLocation, MeltingRecipe> recipes = new LinkedHashMap<>();
        MeltingRecipe broadDefault = new MeltingRecipe(
                Ingredient.of(Items.COPPER_INGOT, Items.GOLD_INGOT), Fluids.LAVA, 144, 500, true);
        MeltingRecipe specificOverride = new MeltingRecipe(Ingredient.of(Items.COPPER_INGOT), Fluids.LAVA, 504, 500, true);
        // Ids picked so specificOverride wins resolve()'s key tie-break (both inputs are item lists
        // here, so isTagInput() ties -- see the class javadoc above for why a real tag can't be used).
        recipes.put(id("aaa_specific_override"), specificOverride);
        recipes.put(id("zzz_broad_default"), broadDefault);

        List<MeltingDisplay> displays = MeltingRecipes.build(recipes);

        MeltingRecipe winner = MeltingRecipe.resolve(recipes, new ItemStack(Items.COPPER_INGOT)).orElseThrow();
        List<MeltingDisplay> showingCopper = displays.stream()
                .filter(display -> display.inputs().stream().anyMatch(stack -> stack.is(Items.COPPER_INGOT)))
                .toList();
        assertEquals(1, showingCopper.size(), "copper ingot must show under exactly one recipe -- never both amounts");
        assertEquals(winner.amount(), showingCopper.get(0).amount(), "the one display showing it must be resolve()'s winner");

        // Gold ingot only ever matched the broad recipe, so a real collision on the shared item must
        // not suppress an unrelated item that recipe also covers.
        assertTrue(displays.stream().anyMatch(display -> display.amount() == 144
                && display.inputs().stream().anyMatch(stack -> stack.is(Items.GOLD_INGOT))));
    }

    @Test
    void meltingViewOmitsARecipeThatLosesEveryCandidateItem() {
        Map<ResourceLocation, MeltingRecipe> recipes = new LinkedHashMap<>();
        // Both recipes claim only copper ingot; "loses_entirely" always loses the tie-break to
        // "always_wins" since a plain equal-specificity tie still needs a deterministic pick.
        recipes.put(id("loses_entirely"), new MeltingRecipe(Ingredient.of(Items.COPPER_INGOT), Fluids.LAVA, 100, 500, false));
        recipes.put(id("always_wins"), new MeltingRecipe(Ingredient.of(Items.COPPER_INGOT), Fluids.WATER, 200, 500, false));

        List<MeltingDisplay> displays = MeltingRecipes.build(recipes);

        assertEquals(1, displays.size(), "the recipe that loses every one of its candidate items shows no display at all");
    }

    @Test
    void alloyingCarriesTheRecipesRatioAndResultUnchanged() {
        Map<ResourceLocation, AlloyRecipe> recipes = new LinkedHashMap<>();
        AlloyRecipe manyullyn = new AlloyRecipe(
                List.of(new FluidStack(Fluids.LAVA, 2), new FluidStack(Fluids.WATER, 2)),
                new FluidStack(Fluids.FLOWING_LAVA, 2), 0);
        recipes.put(id("manyullyn"), manyullyn);

        List<AlloyRecipe> displays = AlloyingRecipes.build(recipes);

        assertEquals(1, displays.size());
        assertEquals(manyullyn, displays.get(0), "no display wrapper is needed -- the domain record already is the display shape");
    }

    @Test
    void castingSplitsOneRegistryIntoTableAndBasinByStation() {
        Map<ResourceLocation, CastingRecipe> recipes = new LinkedHashMap<>();
        CastingRecipe tableRecipe = new CastingRecipe(CastingRecipe.Station.TABLE, Optional.of(Ingredient.of(Items.STICK)),
                Optional.of(Fluids.LAVA), 144, new ItemStack(Items.IRON_INGOT), Optional.empty(), false, false);
        CastingRecipe basinRecipe = new CastingRecipe(CastingRecipe.Station.BASIN, Optional.empty(),
                Optional.of(Fluids.LAVA), 1296, new ItemStack(Items.IRON_BLOCK), Optional.empty(), false, false);
        recipes.put(id("table_one"), tableRecipe);
        recipes.put(id("basin_one"), basinRecipe);

        assertEquals(List.of(tableRecipe), CastingRecipes.table(recipes));
        assertEquals(List.of(basinRecipe), CastingRecipes.basin(recipes));
    }

    /**
     * Modifier application: reagent + resulting modifier + level cap (docs/SCOPE.md M2 issue #109).
     * Two recipes may legitimately target the same modifier id with different reagents -- extra_slot's
     * shipped crafted-item and netherite-ingot recipes -- and both must show up as their own row
     * rather than being collapsed, unlike melting's item-level collision.
     */
    @Test
    void modifierApplicationShowsBothRecipesThatTargetTheSameModifier() {
        Map<ResourceLocation, ModifierRecipe> recipes = new LinkedHashMap<>();
        ModifierRecipe crafted = new ModifierRecipe(
                ResourceLocation.fromNamespaceAndPath("forgeweave", "extra_slot"),
                List.of(new ModifierRecipe.Reagent(Ingredient.of(ForgeweaveItems.EXTRA_MODIFIER.get()), 1)),
                1, 5, List.of());
        ModifierRecipe netherite = new ModifierRecipe(
                ResourceLocation.fromNamespaceAndPath("forgeweave", "extra_slot"),
                List.of(new ModifierRecipe.Reagent(Ingredient.of(Items.NETHERITE_INGOT), 1)),
                1, 5, List.of());
        recipes.put(id("extra_slot"), crafted);
        recipes.put(id("extra_slot_netherite"), netherite);

        List<ModifierRecipe> displays = ModifierApplicationRecipes.build(recipes);

        assertEquals(2, displays.size(), "both extra_slot recipes must show, not just one");
        assertTrue(displays.stream().anyMatch(recipe -> recipe.reagent().test(new ItemStack(ForgeweaveItems.EXTRA_MODIFIER.get()))));
        assertTrue(displays.stream().anyMatch(recipe -> recipe.reagent().test(new ItemStack(Items.NETHERITE_INGOT))));
    }

    @Test
    void modifierApplicationLevelCapFollowsTheRecipesOwnSchedule() {
        ModifierRecipe uniform = new ModifierRecipe(
                ResourceLocation.fromNamespaceAndPath("forgeweave", "haste"),
                List.of(new ModifierRecipe.Reagent(Ingredient.of(Items.REDSTONE), 1)), 1, 250, List.of());

        // Haste has no per-level override, so its display level cap comes from ForgeweaveModifiers'
        // uniform unitsPerLevel (50 redstone per level): 250 units is level 5.
        assertEquals(5, uniform.levelsReached(uniform.maxLevel()));
    }

    // ------------------------------------------------------------------ #165: embossing, large-tool split

    /**
     * Both {@link #twoMaterials()} materials grant traits through every part kind ({@code
     * Traits.general}), so both must show a display -- one donor stack per material, all carrying
     * the shared reagent set straight off the datapack recipe.
     */
    @Test
    void embossingBuildsOneDisplayPerMaterialThatGrantsTraits() {
        Map<ResourceLocation, EmbossingRecipe> recipes = Map.of(id("embossment"),
                new EmbossingRecipe(List.of(Ingredient.of(Items.SLIME_BLOCK), Ingredient.of(Items.MAGMA_BLOCK))));
        Map<ResourceLocation, Material> materials = twoMaterials();

        List<EmbossingDisplay> displays = EmbossingRecipes.build(recipes, materials);

        assertEquals(materials.size(), displays.size());
        for (EmbossingDisplay display : displays) {
            assertTrue(materials.containsKey(display.material()));
            assertTrue(!display.donorParts().isEmpty(), "every donor stack must carry a real part");
            assertTrue(display.donorParts().stream()
                    .allMatch(stack -> display.material().equals(stack.get(ForgeweaveDataComponents.MATERIAL.get()))));
            assertEquals(2, display.reagents().size(), "the reagent set comes straight off the recipe");
        }
    }

    /** A material with no traits at all grants nothing to emboss with, so it shows no display. */
    @Test
    void embossingSkipsAMaterialThatGrantsNoTraits() {
        Map<ResourceLocation, EmbossingRecipe> recipes =
                Map.of(id("embossment"), new EmbossingRecipe(List.of(Ingredient.of(Items.SLIME_BLOCK))));
        Material traitless = new Material(
                new Material.Head(100, 1.0f, 1.0f), new Material.Handle(1.0f, 10), 5,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_wooden_tool")),
                new Material.Traits(List.of(), List.of()), List.of(craftingItem(Items.STICK, 1)),
                Ingredient.of(Items.STICK), TextColor.fromRgb(0xFFFFFF));
        Map<ResourceLocation, Material> materials = new LinkedHashMap<>();
        materials.put(id("traitless"), traitless);

        assertTrue(EmbossingRecipes.build(recipes, materials).isEmpty());
    }

    /** No datapack {@code embossing_recipe} at all (e.g. the title screen) -- nothing to display. */
    @Test
    void embossingWithNoRegistryRecipeProducesNoDisplays() {
        assertTrue(EmbossingRecipes.build(Map.of(), twoMaterials()).isEmpty());
    }

    /**
     * The Tool Forge-only split (docs/SCOPE.md M3 issue #165) reads a real item tag, which isn't
     * bound outside a running server (see this class's own javadoc on {@code MeltingRecipeTest}), so
     * {@code AssemblyRecipes#isLarge} answering {@code false} for every tool here is expected --
     * it just proves the helper resolves each recipe's entry and never throws, rather than the tag
     * membership itself. The real per-tool true/false answer is pinned by {@code
     * gametest.ToolForgeGameTests#exactlySevenToolsAreForgeOnly}, against the shipped tag.
     */
    @Test
    void isLargeResolvesEveryRecipesEntryWithoutThrowing() {
        List<AssemblyRecipe> recipes = AssemblyRecipes.build(twoMaterials());

        assertEquals(ToolAssemblyRecipes.ENTRIES.size(), recipes.size());
        for (AssemblyRecipe recipe : recipes) {
            AssemblyRecipes.isLarge(recipe); // must not throw for any tool in the roster
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }
}
