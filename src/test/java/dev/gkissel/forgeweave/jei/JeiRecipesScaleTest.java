package dev.gkissel.forgeweave.jei;

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
}
