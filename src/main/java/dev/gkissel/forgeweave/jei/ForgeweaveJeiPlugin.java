package dev.gkissel.forgeweave.jei;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;

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

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        registration.addRecipeCategories(
                new PartCraftingCategory(helper),
                new AssemblyCategory(helper),
                new RepairCategory(helper));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Map<ResourceLocation, Material> materials = currentMaterials();
        registration.addRecipes(PartCraftingCategory.TYPE, PartCraftingRecipes.build(materials));
        registration.addRecipes(AssemblyCategory.TYPE, AssemblyRecipes.build(materials));
        registration.addRecipes(RepairCategory.TYPE, RepairRecipes.build(materials));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(ForgeweaveItems.PART_BUILDER.get(), PartCraftingCategory.TYPE);
        registration.addRecipeCatalyst(ForgeweaveItems.TOOL_STATION.get(), AssemblyCategory.TYPE, RepairCategory.TYPE);
        // A shard always pays a part's cost exactly (SHARD_VALUE divides both HEAD_COST and
        // SMALL_PART_COST with no remainder), so it's as legitimate a "what can this craft" lookup
        // target as the station itself (issue #45's Part Crafting rework).
        registration.addRecipeCatalyst(ForgeweaveItems.SHARD.get(), PartCraftingCategory.TYPE);
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
}
