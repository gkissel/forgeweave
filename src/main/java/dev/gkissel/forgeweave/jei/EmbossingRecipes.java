package dev.gkissel.forgeweave.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.EmbossingRecipe;

/**
 * Builds one {@link EmbossingDisplay} per material that grants traits through at least one donor
 * part kind (docs/SCOPE.md M3 issue #165's embossing category; the mechanic itself is {@code
 * modifier.Embossing}). Only the registry's first entry is read, mirroring {@code
 * Embossing#recipe}'s own "first entry wins" tie-break -- a modpack-added second {@code
 * embossing_recipe} is never actually reachable at the station, so showing it here would mislead.
 */
final class EmbossingRecipes {
    static List<EmbossingDisplay> build(Map<ResourceLocation, EmbossingRecipe> recipes,
            Map<ResourceLocation, Material> materials) {
        if (recipes.isEmpty()) {
            return List.of();
        }
        EmbossingRecipe recipe = recipes.values().iterator().next();

        // Every distinct part type any assemblable tool uses -- off ToolAssemblyRecipes' own table
        // (issue #79's "expose the table, don't hand-copy it" pattern), rather than a second list of
        // part items that could drift from the real roster.
        List<PartItem> donorPartTypes = ToolAssemblyRecipes.ENTRIES.stream()
                .flatMap(entry -> entry.parts().stream())
                .distinct()
                .toList();

        List<EmbossingDisplay> displays = new ArrayList<>();
        for (Map.Entry<ResourceLocation, Material> material : materials.entrySet()) {
            List<ItemStack> donorParts = donorPartsFor(donorPartTypes, material.getKey(), material.getValue());
            if (donorParts.isEmpty()) {
                continue; // this material grants no traits through any part kind -- nothing to emboss with
            }
            displays.add(new EmbossingDisplay(donorParts, recipe.reagents(), material.getKey()));
        }
        return displays;
    }

    /** One representative stack per part kind {@code material} actually grants traits through. */
    private static List<ItemStack> donorPartsFor(List<PartItem> partTypes, ResourceLocation materialId,
            Material material) {
        List<ItemStack> stacks = new ArrayList<>();
        for (PartItem part : partTypes) {
            if (material.traits().forPart(part.kind()).isEmpty()) {
                continue; // Embossing#resolve rejects a donor whose material grants no traits for its kind
            }
            ItemStack stack = new ItemStack(part);
            stack.set(ForgeweaveDataComponents.MATERIAL.get(), materialId);
            stacks.add(stack);
        }
        return stacks;
    }

    private EmbossingRecipes() {}
}
