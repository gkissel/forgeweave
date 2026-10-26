package dev.gkissel.forgeweave.emi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;

import dev.gkissel.forgeweave.jei.MeltingDisplay;
import dev.gkissel.forgeweave.jei.RecipeViewerContent;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;
import dev.gkissel.forgeweave.recipe.CoreTransformRecipe;

/**
 * Issue #971: the EMI plugin constructs and registers against a stub {@link EmiRegistry}. EMI is on
 * this classpath (build.gradle's testRuntimeOnly row) and JEI is not, which is also the point: a
 * stray JEI type anywhere on the registration path would throw {@code NoClassDefFoundError} here,
 * the same way it would for a player running EMI alone.
 */
class ForgeweaveEmiPluginTest {

    /** The 14 JEI recipe-type ids; the EMI categories reuse them one for one. */
    private static final List<String> JEI_TYPES = List.of("part_crafting", "tool_assembly", "large_tool_assembly",
            "tool_repair", "modifier_worktable", "melting", "alloying", "casting_table", "casting_basin",
            "modifier_application", "embossing", "smeltery_fuel", "core_transform", "entity_melting");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Records what the plugin hands EMI; every other registry call is a no-op. */
    private static final class Recorder {
        final List<EmiRecipeCategory> categories = new ArrayList<>();
        final List<EmiRecipeCategory> workstationCategories = new ArrayList<>();
        final List<EmiRecipe> recipes = new ArrayList<>();
        int handlers;
        int comparisons;

        EmiRegistry registry() {
            return (EmiRegistry) Proxy.newProxyInstance(EmiRegistry.class.getClassLoader(), new Class<?>[] {EmiRegistry.class},
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "addCategory" -> categories.add((EmiRecipeCategory) args[0]);
                            case "addWorkstation" -> workstationCategories.add((EmiRecipeCategory) args[0]);
                            case "addRecipe" -> recipes.add((EmiRecipe) args[0]);
                            case "addRecipeHandler" -> handlers++;
                            case "setDefaultComparison" -> comparisons++;
                            // An empty one: the retextured-station recipes are read off it.
                            case "getRecipeManager" -> {
                                return new RecipeManager(RegistryAccess.EMPTY);
                            }
                            default -> { }
                        }
                        return method.getReturnType() == boolean.class ? Boolean.FALSE : null;
                    });
        }
    }

    /**
     * Three hand-built rows. The Mending Moss flag stays off: EMI's {@link EmiInfoRecipe} wraps its
     * text against the client's font as it is constructed, and a unit test has no client. That page
     * is on the manual checklist instead.
     */
    private static RecipeViewerContent sampleContent() {
        RecipeViewerContent empty = RecipeViewerContent.EMPTY;
        return new RecipeViewerContent(empty.partCrafting(), empty.toolAssembly(), empty.largeToolAssembly(),
                empty.repair(), empty.worktable(),
                List.of(new MeltingDisplay(List.of(new ItemStack(Items.IRON_ORE)), Fluids.LAVA, 144, 800, true, List.of())),
                List.of(new AlloyRecipe(List.of(new FluidStack(Fluids.WATER, 10), new FluidStack(Fluids.LAVA, 10)),
                        new FluidStack(Fluids.LAVA, 20), 0)),
                empty.castingTable(), empty.castingBasin(), empty.modifierApplication(), empty.embossing(),
                empty.smelteryFuel(),
                List.of(new CoreTransformRecipe(Fluids.LAVA, Blocks.STONE, Blocks.NETHERRACK, 1000)),
                empty.entityMelting(), false);
    }

    @Test
    void registersAllFourteenCategoriesUnderTheJeiIds() {
        Recorder recorder = new Recorder();
        ForgeweaveEmiPlugin.register(recorder.registry(), sampleContent(), false);

        assertEquals(JEI_TYPES.stream().map(path -> ResourceLocation.fromNamespaceAndPath("forgeweave", path)).toList(),
                recorder.categories.stream().map(EmiRecipeCategory::getId).toList());
        assertTrue(recorder.workstationCategories.containsAll(recorder.categories),
                "every category has at least one workstation, as every JEI category has a catalyst");
        assertEquals(4, recorder.handlers, "Crafting Station, Part Builder, Tool Station and Modifier Worktable");
        assertTrue(recorder.comparisons > 0, "part, tool and texture comparisons are registered");
    }

    @Test
    void eachRowBecomesOneRecipeInItsOwnCategory() {
        Recorder recorder = new Recorder();
        ForgeweaveEmiPlugin.register(recorder.registry(), sampleContent(), false);

        List<EmiRecipe> rows = recorder.recipes;
        assertEquals(List.of(ForgeweaveEmiCategories.MELTING, ForgeweaveEmiCategories.ALLOYING,
                ForgeweaveEmiCategories.CORE_TRANSFORM), rows.stream().map(EmiRecipe::getCategory).toList());
        assertEquals(List.of("/melting/0", "/alloying/0", "/core_transform/0"),
                rows.stream().map(recipe -> recipe.getId().getPath()).toList());
        assertEquals(2, rows.get(1).getInputs().size(), "both alloy inputs are inputs");
    }

    /**
     * With JEI installed EMI's bridge already forwards the JEI plugin's catalysts on vanilla crafting
     * and smelting (and its info page, which {@link #sampleContent} cannot build here); registering
     * them again would list each twice.
     */
    @Test
    void leavesTheVanillaCatalystsToTheJeiBridge() {
        Recorder alone = new Recorder();
        ForgeweaveEmiPlugin.register(alone.registry(), sampleContent(), false);
        Recorder bridged = new Recorder();
        ForgeweaveEmiPlugin.register(bridged.registry(), sampleContent(), true);

        // EMI's VanillaEmiRecipeCategories fields are only filled in once EMI's own plugin class
        // loads, which a unit test never does, so this counts rather than naming the two categories:
        // the Crafting Station on crafting and the seared furnace controller on smelting.
        assertEquals(2, alone.workstationCategories.size() - bridged.workstationCategories.size());
        assertEquals(14, bridged.categories.size(), "the bridge skips Forgeweave's own categories, so these stay");
    }
}
