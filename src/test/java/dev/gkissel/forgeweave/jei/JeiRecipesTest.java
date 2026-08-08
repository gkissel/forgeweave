package dev.gkissel.forgeweave.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

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

    private static Material material(List<Material.CraftingItem> craftingItems, Ingredient repairItem) {
        return new Material(
                new Material.Head(100, 1.0f, 1.0f),
                new Material.Handle(1.0f, 10),
                5,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_wooden_tool")),
                ResourceLocation.fromNamespaceAndPath("forgeweave", "ecological"),
                craftingItems,
                repairItem,
                TextColor.fromRgb(0xFFFFFF));
    }

    private static Material.CraftingItem craftingItem(Item item, int value) {
        return new Material.CraftingItem(Ingredient.of(item), value);
    }

    /** wood: stick=1, planks=2, log=8 -- the real shipped wood.json values (issue #45). */
    private static Map<ResourceLocation, Material> twoMaterials() {
        Map<ResourceLocation, Material> materials = new LinkedHashMap<>();
        materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", "wood"),
                material(List.of(
                        craftingItem(Items.STICK, 1),
                        craftingItem(Items.OAK_PLANKS, 2),
                        craftingItem(Items.OAK_LOG, 8)),
                        Ingredient.of(Items.OAK_PLANKS)));
        materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", "stone"),
                material(List.of(craftingItem(Items.COBBLESTONE, 2)), Ingredient.of(Items.COBBLESTONE)));
        return materials;
    }

    @Test
    void partCraftingEnumeratesEveryPartTypeTimesEveryMaterial() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());

        assertEquals(5 * 2, recipes.size(), "5 part types x 2 materials");
        assertTrue(recipes.stream().allMatch(r -> r.result().has(ForgeweaveDataComponents.MATERIAL.get())));
    }

    @Test
    void partCraftingCyclesEveryCraftingItemPlusTheShardForAHead() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());
        PartCraftingRecipe woodPickaxeHead = recipes.stream()
                .filter(r -> r.pattern().is(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get())
                        && r.result().get(ForgeweaveDataComponents.MATERIAL.get()).getPath().equals("wood"))
                .findFirst().orElseThrow();

        // stick, planks, log, shard -- 4 cycling options for a HEAD_COST=4 part.
        assertEquals(4, woodPickaxeHead.materialInputs().size());
        assertEquals(4, woodPickaxeHead.changeOutputs().size());

        assertEquals(4, countByItem(woodPickaxeHead, Items.STICK), "1-value stick needs 4 to cover a head");
        assertNull(changeFor(woodPickaxeHead, Items.STICK), "exact payment leaves no change");

        assertEquals(2, countByItem(woodPickaxeHead, Items.OAK_PLANKS), "2-value planks need 2 to cover a head");
        assertNull(changeFor(woodPickaxeHead, Items.OAK_PLANKS), "exact payment leaves no change");

        assertEquals(1, countByItem(woodPickaxeHead, Items.OAK_LOG), "8-value log overpays a head with just 1");
        ItemStack logChange = changeFor(woodPickaxeHead, Items.OAK_LOG);
        assertEquals(4, logChange.getCount(), "log overpays a HEAD_COST=4 part by 4 shard-units");
        assertTrue(logChange.is(ForgeweaveItems.SHARD.get()));
    }

    @Test
    void partCraftingShardOptionAlwaysPaysExactly() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());

        for (PartCraftingRecipe recipe : recipes) {
            int shardIndex = indexOfItem(recipe, ForgeweaveItems.SHARD.get());
            assertTrue(shardIndex >= 0, "the shard is always a valid crafting option");
            assertNull(recipe.changeOutputs().get(shardIndex), "the shard's 1-unit value divides every part cost evenly");
        }
    }

    @Test
    void partCraftingCostsMatchPartBuilderRecipesConstants() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());

        PartCraftingRecipe stoneBinding = recipes.stream()
                .filter(r -> r.pattern().is(ForgeweaveItems.PATTERN_TOOL_BINDING.get())
                        && r.result().get(ForgeweaveDataComponents.MATERIAL.get()).getPath().equals("stone"))
                .findFirst().orElseThrow();

        PartBuilderRecipes.CostResult expected = PartBuilderRecipes.computeCost(PartBuilderRecipes.SMALL_PART_COST, 2);
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

        assertEquals(3, recipes.size(), "pickaxe, shovel, hatchet");
        for (AssemblyRecipe recipe : recipes) {
            assertEquals(materials.size(), recipe.heads().size());
            assertEquals(materials.size(), recipe.bindings().size());
            assertEquals(materials.size(), recipe.handles().size());
        }
    }

    @Test
    void repairHasOneRecipePerMaterialCoveringEveryToolType() {
        Map<ResourceLocation, Material> materials = twoMaterials();
        List<RepairRecipe> recipes = RepairRecipes.build(materials);

        assertEquals(materials.size(), recipes.size());
        for (RepairRecipe recipe : recipes) {
            assertEquals(3, recipe.tools().size(), "pickaxe, shovel, hatchet all repair the same way");
            assertEquals(recipe.tools(), recipe.repairedTools());
        }
    }

    @Test
    void emptyMaterialsProduceNoRecipes() {
        Map<ResourceLocation, Material> none = Map.of();

        assertTrue(PartCraftingRecipes.build(none).isEmpty());
        assertTrue(AssemblyRecipes.build(none).isEmpty());
        assertTrue(RepairRecipes.build(none).isEmpty());
    }
}
