package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.material.Material;

/**
 * Issue #846 (M6 UI/schema hardening): measures {@link ForgeweaveCreativeTab#addPartItems} against
 * the <em>real</em> shipped material roster (128 at time of writing, the M6 epic's own final tally)
 * rather than a synthetic count, so the pressure point the epic (#824) flagged -- "~36 part items x
 * every material with matching stats, potentially several thousand stacks" -- gets a real number
 * instead of a guess.
 *
 * <p>Loads every {@code data/forgeweave/forgeweave/material/*.json} straight through {@link
 * Material#CODEC} (the same non-vacuity approach {@code MaterialSyncSizeTest} uses), which -- like
 * that test -- does not evaluate {@code neoforge:conditions}, so this measures the worst case where
 * every Track A compat metal's provider mod is installed. That is the right upper bound for "does the
 * tab hold up," since a kitchen-sink modpack is exactly the scenario the pressure point worries about.
 */
class ForgeweaveCreativeTabScaleTest {

    /**
     * Generous on purpose: the concern is an accidental quadratic blowup, not shaving milliseconds
     * off a one-time tab build. Enumerating a few thousand {@link ItemStack}s is expected to take low
     * single-digit milliseconds; 250ms would mean something is very wrong.
     */
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
    void partsTabHoldsTheFullRosterWithinBudget() throws Exception {
        Map<ResourceLocation, Material> shipped = shippedMaterials();
        assertTrue(shipped.size() >= 100, "non-vacuity: expected the real M6 roster, saw only " + shipped.size());

        CreativeModeTab.ItemDisplayParameters parameters = parameters(shipped);
        List<ItemStack> displayed = new ArrayList<>();

        long start = System.nanoTime();
        ForgeweaveCreativeTab.addPartItems(parameters, (stack, visibility) -> displayed.add(stack), true);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        int parts = ForgeweaveCreativeTab.PART_ITEMS.size();
        long partStacks = displayed.stream().filter(stack -> stack.getItem() instanceof PartItem).count();
        long patterns = displayed.size() - partStacks;

        System.out.println("[#846] creative tab: " + shipped.size() + " materials x " + parts
                + " part items -> " + partStacks + " part-material stacks (+ " + patterns
                + " fixed pattern items), built in " + elapsedMillis + "ms");

        assertTrue(partStacks > 0, "expected at least some part stacks");
        assertTrue(partStacks <= (long) parts * shipped.size(),
                "hasStatsFor filtering should only ever shrink the parts x materials product");
        assertTrue(elapsedMillis < BUDGET_MILLIS,
                "building the parts tab for " + shipped.size() + " materials took " + elapsedMillis
                        + "ms, over the " + BUDGET_MILLIS + "ms budget");
    }

    /** A registry access wrapping the real shipped materials, the shape {@code addPartItems} reads. */
    private static CreativeModeTab.ItemDisplayParameters parameters(Map<ResourceLocation, Material> shipped) {
        MappedRegistry<Material> materials = new MappedRegistry<>(Material.REGISTRY, Lifecycle.stable());
        for (Map.Entry<ResourceLocation, Material> entry : shipped.entrySet()) {
            materials.register(ResourceKey.create(Material.REGISTRY, entry.getKey()), entry.getValue(),
                    RegistrationInfo.BUILT_IN);
        }
        materials.freeze();
        RegistryAccess.Frozen registryAccess = new RegistryAccess.ImmutableRegistryAccess(List.of(materials)).freeze();
        return new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, true, registryAccess);
    }
}
