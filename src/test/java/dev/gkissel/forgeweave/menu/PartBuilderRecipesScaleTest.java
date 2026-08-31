package dev.gkissel.forgeweave.menu;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.material.Material;

/**
 * Issue #846 (M6 UI/schema hardening), pressure point 4: {@code PartBuilderRecipes#materialValue}
 * walks every registry material and every one of its {@code crafting_items}, testing each {@link
 * net.minecraft.world.item.crafting.Ingredient} against the input stack, on every material-slot
 * change. At the real 128-material roster (~300 crafting items total, prep doc &sect;5) that is a
 * few hundred ingredient tests per slot change -- the epic guessed "probably still nothing, but it has
 * never been profiled." This measures it against the real shipped roster with an input that matches
 * nothing, the worst case that forces a full scan with no early exit.
 */
class PartBuilderRecipesScaleTest {

    /**
     * A slot change happens on every inventory click; this budget is generous by roughly two orders
     * of magnitude over the expected sub-millisecond cost, so it only trips on a genuine regression
     * (e.g. an accidental quadratic scan), not on ordinary JIT/GC noise.
     */
    private static final long BUDGET_MILLIS_PER_CALL = 5;
    private static final int ITERATIONS = 500;

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

    private static HolderLookup.Provider registries(Map<ResourceLocation, Material> shipped) {
        MappedRegistry<Material> materials = new MappedRegistry<>(Material.REGISTRY, Lifecycle.stable());
        for (Map.Entry<ResourceLocation, Material> entry : shipped.entrySet()) {
            materials.register(ResourceKey.create(Material.REGISTRY, entry.getKey()), entry.getValue(),
                    RegistrationInfo.BUILT_IN);
        }
        materials.freeze();
        return new RegistryAccess.ImmutableRegistryAccess(List.of(materials)).freeze();
    }

    @Test
    void materialValueScanStaysFastAtTheFullRoster() throws Exception {
        Map<ResourceLocation, Material> shipped = shippedMaterials();
        assertTrue(shipped.size() >= 100, "non-vacuity: expected the real M6 roster, saw only " + shipped.size());
        long craftingItems = shipped.values().stream().mapToLong(m -> m.craftingItems().size()).sum();

        HolderLookup.Provider registries = registries(shipped);
        // Vanilla dirt matches no material's crafting_items, forcing materialValue to scan every
        // material and every one of its crafting items with no early exit -- the worst case.
        ItemStack noMatch = new ItemStack(Items.DIRT);

        // One warm-up pass so the measured loop isn't paying JIT warm-up cost.
        for (int i = 0; i < 50; i++) {
            Optional<PartBuilderRecipes.MaterialMatch> ignored = PartBuilderRecipes.materialValue(registries, noMatch);
        }

        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            PartBuilderRecipes.materialValue(registries, noMatch);
        }
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;
        double perCall = elapsedMillis / (double) ITERATIONS;

        System.out.println("[#846] Part Builder materialValue: " + shipped.size() + " materials, "
                + craftingItems + " total crafting_items, " + ITERATIONS + " full-scan calls in "
                + elapsedMillis + "ms (" + perCall + "ms/call)");

        assertTrue(perCall < BUDGET_MILLIS_PER_CALL,
                "materialValue averaged " + perCall + "ms/call over " + ITERATIONS
                        + " calls against the full roster, over the " + BUDGET_MILLIS_PER_CALL + "ms budget");
    }
}
