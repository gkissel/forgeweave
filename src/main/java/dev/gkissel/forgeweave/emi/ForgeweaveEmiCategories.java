package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;

import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.render.EmiRenderable;
import dev.emi.emi.api.render.EmiTexture;
import dev.emi.emi.api.stack.EmiStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;

/**
 * The 14 EMI recipe categories (issue #971), one per JEI {@code RecipeType}, under the very same ids:
 * {@code forgeweave:part_crafting} in EMI is {@code forgeweave:part_crafting} in JEI. Icons are the
 * JEI categories' own.
 *
 * <p>Each category's name is the JEI category's existing {@code jei.category.forgeweave.<id>} lang
 * key rather than a parallel {@code emi.category.*} family: the two viewers show the same category
 * under the same title, so one key per title is the whole requirement, and EMI only falls back to
 * its own key family when {@link EmiRecipeCategory#getName} is not overridden.
 */
final class ForgeweaveEmiCategories {
    /** Upstream's entity-melting tab icon, the 16x16 crop {@code EntityMeltingCategory} uses. */
    private static final int ENTITY_MELTING_ICON_U = 174;
    private static final int ENTITY_MELTING_ICON_V = 41;
    private static final int ICON_SIZE = 16;

    static final EmiRecipeCategory PART_CRAFTING = category("part_crafting", EmiStack.of(ForgeweaveItems.PART_BUILDER.get()));
    static final EmiRecipeCategory TOOL_ASSEMBLY = category("tool_assembly", EmiStack.of(ForgeweaveItems.TOOL_STATION.get()));
    static final EmiRecipeCategory LARGE_TOOL_ASSEMBLY = category("large_tool_assembly", EmiStack.of(ForgeweaveItems.TOOL_FORGE.get()));
    static final EmiRecipeCategory TOOL_REPAIR = category("tool_repair", EmiStack.of(ForgeweaveItems.TOOL_STATION.get()));
    static final EmiRecipeCategory MODIFIER_WORKTABLE = category("modifier_worktable", EmiStack.of(ForgeweaveItems.MODIFIER_WORKTABLE.get()));
    static final EmiRecipeCategory MELTING = category("melting", EmiStack.of(ForgeweaveItems.STANDARD_CORE.get()));
    static final EmiRecipeCategory ALLOYING = category("alloying", EmiStack.of(ForgeweaveItems.STANDARD_CORE.get()));
    static final EmiRecipeCategory CASTING_TABLE = category("casting_table", EmiStack.of(ForgeweaveItems.CASTING_TABLE.get()));
    static final EmiRecipeCategory CASTING_BASIN = category("casting_basin", EmiStack.of(ForgeweaveItems.CASTING_BASIN.get()));
    static final EmiRecipeCategory MODIFIER_APPLICATION = category("modifier_application", EmiStack.of(ForgeweaveItems.TOOL_STATION.get()));
    static final EmiRecipeCategory EMBOSSING = category("embossing", EmiStack.of(ForgeweaveItems.TOOL_STATION.get()));
    static final EmiRecipeCategory SMELTERY_FUEL = category("smeltery_fuel", EmiStack.of(Items.LAVA_BUCKET));
    static final EmiRecipeCategory CORE_TRANSFORM = category("core_transform", EmiStack.of(ForgeweaveItems.FAUCET.get()));
    static final EmiRecipeCategory ENTITY_MELTING = category("entity_melting", new EmiTexture(
            JeiCategoryGeometry.ENTITY_MELTING.background(), ENTITY_MELTING_ICON_U, ENTITY_MELTING_ICON_V, ICON_SIZE, ICON_SIZE));

    /** All 14, in the order the JEI plugin registers its categories. */
    static final List<EmiRecipeCategory> ALL = List.of(PART_CRAFTING, TOOL_ASSEMBLY, LARGE_TOOL_ASSEMBLY, TOOL_REPAIR,
            MODIFIER_WORKTABLE, MELTING, ALLOYING, CASTING_TABLE, CASTING_BASIN, MODIFIER_APPLICATION, EMBOSSING,
            SMELTERY_FUEL, CORE_TRANSFORM, ENTITY_MELTING);

    private ForgeweaveEmiCategories() {}

    /**
     * {@code forgeweave:/<category>/<index>}: EMI wants a unique id per recipe, and the leading slash
     * is its convention for one that names no real {@code RecipeManager} entry, which is all of these
     * (every Forgeweave station recipe is a datapack registry, not a {@code RecipeType}). The index is
     * the row's place in its {@code RecipeViewerContent} list, which is registry order, so it holds
     * steady across sessions for the same set of datapacks.
     */
    static ResourceLocation recipeId(EmiRecipeCategory category, int index) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "/" + category.getId().getPath() + "/" + index);
    }

    private static EmiRecipeCategory category(String path, EmiRenderable icon) {
        Component name = Component.translatable("jei.category.forgeweave." + path);
        return new EmiRecipeCategory(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path), icon) {
            @Override
            public Component getName() {
                return name;
            }
        };
    }
}
