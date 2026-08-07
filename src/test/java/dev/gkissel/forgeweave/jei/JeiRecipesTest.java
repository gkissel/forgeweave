package dev.gkissel.forgeweave.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;

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

    private static Material material(Ingredient repairItem) {
        return new Material(
                new Material.Head(100, 1.0f, 1.0f),
                new Material.Handle(1.0f, 10),
                5,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_wooden_tool")),
                ResourceLocation.fromNamespaceAndPath("forgeweave", "ecological"),
                repairItem,
                TextColor.fromRgb(0xFFFFFF));
    }

    private static Map<ResourceLocation, Material> twoMaterials() {
        Map<ResourceLocation, Material> materials = new LinkedHashMap<>();
        materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", "wood"), material(Ingredient.of(Items.OAK_PLANKS)));
        materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", "stone"), material(Ingredient.of(Items.COBBLESTONE)));
        return materials;
    }

    @Test
    void partCraftingEnumeratesEveryPartTypeTimesEveryMaterial() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());

        assertEquals(5 * 2, recipes.size(), "5 part types x 2 materials");
        assertTrue(recipes.stream().allMatch(r -> r.result().has(ForgeweaveDataComponents.MATERIAL.get())));
    }

    @Test
    void partCraftingChargesTwoForAHeadAndOneForASmallPart() {
        List<PartCraftingRecipe> recipes = PartCraftingRecipes.build(twoMaterials());

        assertTrue(recipes.stream()
                .anyMatch(r -> r.pattern().is(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()) && r.material().getCount() == 2));
        assertTrue(recipes.stream()
                .anyMatch(r -> r.pattern().is(ForgeweaveItems.PATTERN_TOOL_HANDLE.get()) && r.material().getCount() == 1));
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
