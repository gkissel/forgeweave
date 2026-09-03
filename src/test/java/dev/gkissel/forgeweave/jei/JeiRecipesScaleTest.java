package dev.gkissel.forgeweave.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.recipe.CoreTransformRecipe;
import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * Issue #846 (M6 UI/schema hardening), pressure point 5: one JEI subtype per material per part is
 * fixed cost at registration (it walks the ~36 registered part items and the tool roster, never the
 * material list -- see {@code ForgeweaveJeiPlugin#registerItemSubtypes}), but the three display-recipe
 * lists ({@code PartCraftingRecipes}, {@code AssemblyRecipes}, {@code RepairRecipes}) are each built
 * once per session directly off the material map, the same "one entry per material x eligible part/
 * tool" shape the creative tab and Part Builder measurements cover. This pins the real roster's
 * counts and confirms building all three together stays fast -- the epic's own guess was "the
 * likeliest of the five to be a non-issue."
 */
class JeiRecipesScaleTest {

    private static final long BUDGET_MILLIS = 250;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Map<ResourceLocation, Material> shippedMaterials() throws Exception {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Path materialDir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/material");
        Map<ResourceLocation, Material> materials = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(materialDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                Material material = Material.CODEC.parse(ops, json).getOrThrow();
                materials.put(ResourceLocation.fromNamespaceAndPath("forgeweave", name), material);
            }
        }
        return materials;
    }

    /**
     * Issue #890: the same shipped-datapack-directory read {@link #shippedMaterials} uses, generalised
     * to any of this project's registry codecs. Reading straight off {@code src/main/resources} (never
     * {@code src/gametest/resources}) is what keeps GameTest-only fixtures -- {@code
     * smeltery_fuel/gametest_super_fuel.json}, excluded from the published jar by {@code build.gradle}'s
     * {@code jar} task -- out of these counts without this test needing any id-based filter of its own.
     */
    private static <T> Map<ResourceLocation, T> shippedRegistryEntries(String registryPath, com.mojang.serialization.Codec<T> codec) throws Exception {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Path dir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/" + registryPath);
        Map<ResourceLocation, T> entries = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                entries.put(ResourceLocation.fromNamespaceAndPath("forgeweave", name), codec.parse(ops, json).getOrThrow());
            }
        }
        return entries;
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    @Test
    void theThreeCategoriesBuildQuicklyAtTheFullRoster() throws Exception {
        Map<ResourceLocation, Material> shipped = shippedMaterials();
        assertTrue(shipped.size() >= 100, "non-vacuity: expected the real M6 roster, saw only " + shipped.size());

        long start = System.nanoTime();
        List<PartCraftingRecipe> partCrafting = PartCraftingRecipes.build(shipped);
        List<AssemblyRecipe> assembly = AssemblyRecipes.build(shipped);
        List<RepairRecipe> repair = RepairRecipes.build(shipped);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("[#846] JEI categories at " + shipped.size() + " materials: " + partCrafting.size()
                + " part-crafting, " + assembly.size() + " assembly, " + repair.size() + " repair recipes, "
                + "built in " + elapsedMillis + "ms");

        assertTrue(!partCrafting.isEmpty() && !assembly.isEmpty() && !repair.isEmpty(),
                "expected every category to enumerate at least one recipe from the real roster");
        assertTrue(elapsedMillis < BUDGET_MILLIS,
                "building all three JEI recipe lists for " + shipped.size() + " materials took "
                        + elapsedMillis + "ms, over the " + BUDGET_MILLIS + "ms budget");
    }

    /**
     * Issue #890: pins the real shipped counts for the two new categories the same way the test above
     * pins the original three -- lava and blazing blood ({@code smeltery_fuel}), end_core and
     * deep_core ({@code core_transform_recipe}, #845). Both registries are small and fixed by
     * design (docs/SCOPE.md M2/#845 never project either one growing the way the material roster did),
     * so this is a non-vacuity/regression pin rather than a scale budget -- if a pack or a future
     * milestone adds another fuel or transform, updating the expected count here is the intended
     * maintenance, not a failure of the test.
     *
     * <p>#894 added twinalloy, bringing the fuel count from 2 to 3; #897 added pyrealloy -- the fuel
     * ladder's 2100-degree top rung -- bringing it to 4; #903 added the ladder's two mined rungs,
     * molten magma (melted vanilla magma blocks, 1700) and molten brimspar (melted Nether crystals,
     * 1900), bringing it to 6. #910 merged twinalloy into brimspar and deleted its row, leaving 5.
     *
     * <p>The burn-rate assertions are #910's other half: every fuel drains 50 mB per 100-melt-tick
     * cycle (the clone's own lava numbers, {@code SmelteryFuelTest}) except pyrealloy, which drains
     * 100 mB and burns 500 ticks on it -- 2.5x the work per mB, the ladder's only long burn and the
     * reward for finishing the alloy chain. Pinned here, off the shipped JSON, because the shape of
     * the claim is "exactly one fuel is special"; {@code SmelteryFuelGameTests} proves the smeltery
     * actually honours those two numbers.
     */
    @Test
    void theTwoNewCategoriesEnumerateTheRealShippedRegistryContents() throws Exception {
        Map<ResourceLocation, SmelteryFuel> fuels = shippedRegistryEntries("smeltery_fuel", SmelteryFuel.CODEC);
        Map<ResourceLocation, CoreTransformRecipe> transforms =
                shippedRegistryEntries("core_transform_recipe", CoreTransformRecipe.CODEC);

        assertEquals(5, fuels.size(),
                "lava + blazing_blood + magma + brimspar + pyrealloy -- gametest_super_fuel ships outside src/main/resources");
        assertEquals(2, transforms.size(), "#845's end_core + deep_core rows");

        fuels.forEach((id, fuel) -> {
            boolean longBurn = id.getPath().equals("pyrealloy");
            assertEquals(longBurn ? 100 : 50, fuel.amount(), id + "'s mB drained per burn cycle");
            assertEquals(longBurn ? 500 : 100, fuel.duration(), id + "'s melt ticks per burn cycle");
        });

        List<SmelteryFuelDisplay> fuelDisplays = SmelteryFuelRecipes.build(fuels);
        List<CoreTransformRecipe> transformDisplays = CoreTransformRecipes.build(transforms);

        assertEquals(5, fuelDisplays.size());
        assertEquals(2, transformDisplays.size());
    }

    /**
     * Issue #931: pins the real shipped {@code entity_melting_recipe} count the same way the test
     * above pins the other two smeltery registries -- blaze, emerald_mobs (villager + three illager
     * types, one file), iron_golem, large_overworld_mobs, small_overworld_mobs, snow_golem and warden,
     * seven files. {@link EntityMeltingRecipes#build} appends one more synthetic row for {@link
     * EntityMeltingRecipe#defaultResult}, so the JEI category shows eight rows for seven registry
     * entries.
     */
    @Test
    void theEntityMeltingCategoryEnumeratesTheRealShippedRegistryContentsPlusTheDefaultRow() throws Exception {
        Map<ResourceLocation, EntityMeltingRecipe> recipes =
                shippedRegistryEntries("entity_melting_recipe", EntityMeltingRecipe.CODEC);

        assertEquals(7, recipes.size(),
                "blaze + emerald_mobs + iron_golem + large_overworld_mobs + small_overworld_mobs + snow_golem + warden");

        List<EntityMeltingDisplay> displays = EntityMeltingRecipes.build(recipes);
        assertEquals(8, displays.size(), "one row per recipe, plus EntityMeltingRecipe#defaultResult's own row");
        assertEquals(1, displays.stream().filter(EntityMeltingDisplay::defaultRow).count(),
                "exactly one row must be the default-rule row");
    }
}
