package dev.gkissel.forgeweave.compat.draconic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * The stacks a fusion upgrade row shows rather than the ones it matches (issue #952).
 *
 * <p>{@link FusionUpgradeRecipe}'s catalyst is the whole {@code #forgeweave:fusion_upgradable} tag,
 * because any assembled tool made of the tier's metal may sit in the crafting core. Drawn as-is that
 * reads as a row of bare iron tools -- a tag ingredient's display stacks are plain items with no
 * parts, no materials and no traits -- which tells a player nothing about what the row wants. So the
 * ingredient keeps its matching and hands the display side these instead: real assembled tools of
 * the tier's own fusion metal, built through {@link ToolAssemblyRecipes#assemble} exactly the way
 * the Tool Station builds one, so what JEI cycles is what a player would actually put in the core.
 *
 * <p>Nothing here names a Draconic Evolution type, the same rule {@link ForgeweaveDraconicCompat}
 * follows.
 */
final class FusionDisplay {

    /**
     * Assembled tools of {@code material}, one per tool that material can be built into, or empty
     * when the materials are not loaded (a dedicated server, or a client still at the title screen).
     * The caller falls back to the tag's own stacks then.
     *
     * <p>Not cached: JEI asks once per recipe layout, and a cache would have to be invalidated on
     * every datapack reload to stay honest about a pack that retuned the metal.
     */
    static List<ItemStack> catalysts(String material) {
        // ponytail: display only, so the client is the only side that needs an answer -- JEI and
        // Draconic Evolution's fusion category both draw on it. Client is a class of its own so a
        // dedicated server never resolves a net.minecraft.client type, the dist idiom Forgeweave's
        // other client-only wiring uses.
        return FMLEnvironment.dist == Dist.CLIENT ? catalysts(Client.registries(), material) : List.of();
    }

    /** As {@link #catalysts(String)}, for a caller that was handed registries of its own. */
    static List<ItemStack> catalysts(@Nullable HolderLookup.Provider registries, String material) {
        if (registries == null) {
            return List.of();
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, material);
        Optional<Material> stats = registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, id)))
                .map(holder -> holder.value());
        if (stats.isEmpty()) {
            return List.of();
        }

        List<ItemStack> stacks = new ArrayList<>();
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            if (!buildable(stats.get(), entry)) {
                continue;
            }
            ToolAssemblyRecipes.assemble(registries, entry, Collections.nCopies(entry.slotCount(), id))
                    .ifPresent(stacks::add);
        }
        return List.copyOf(stacks);
    }

    /**
     * Whether every one of this tool's parts can be made of {@code material} -- the same
     * {@code hasStatsFor} question {@code jei.AssemblyRecipes} asks before offering a material for a
     * slot. A fusion metal carries no bowstring, shaft or fletching stats, so bows and arrows drop
     * out here rather than assembling into something with holes in it.
     */
    private static boolean buildable(Material material, ToolAssemblyRecipes.Entry entry) {
        for (PartItem part : entry.parts()) {
            if (!material.hasStatsFor(part.kind())) {
                return false;
            }
        }
        return true;
    }

    /** The one client-side lookup {@link #catalysts(String)} needs, and the only reason it exists. */
    private static final class Client {

        @Nullable
        static HolderLookup.Provider registries() {
            ClientLevel level = Minecraft.getInstance().level;
            return level == null ? null : level.registryAccess();
        }

        private Client() {}
    }

    private FusionDisplay() {}
}
