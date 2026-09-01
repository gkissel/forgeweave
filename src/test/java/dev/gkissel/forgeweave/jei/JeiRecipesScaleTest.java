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
     * deep_core ({@code core_transform_recipe}, #845). Both registries are small and fixed by design
     * (docs/SCOPE.md M2/#845 never project either one growing the way the material roster did), so this
     * is a non-vacuity/regression pin rather than a scale budget -- if a pack or a future milestone adds
     * a third fuel or transform, updating the expected count here is the intended maintenance, not a
     * failure of the test.
     */
    @Test
    void theTwoNewCategoriesEnumerateTheRealShippedRegistryContents() throws Exception {
        Map<ResourceLocation, SmelteryFuel> fuels = shippedRegistryEntries("smeltery_fuel", SmelteryFuel.CODEC);
        Map<ResourceLocation, CoreTransformRecipe> transforms =
                shippedRegistryEntries("core_transform_recipe", CoreTransformRecipe.CODEC);

        assertEquals(2, fuels.size(), "lava + blazing_blood -- gametest_super_fuel ships outside src/main/resources");
        assertEquals(2, transforms.size(), "#845's end_core + deep_core rows");

        List<SmelteryFuelDisplay> fuelDisplays = SmelteryFuelRecipes.build(fuels);
        List<CoreTransformRecipe> transformDisplays = CoreTransformRecipes.build(transforms);

        assertEquals(2, fuelDisplays.size());
        assertEquals(2, transformDisplays.size());
    }
}
