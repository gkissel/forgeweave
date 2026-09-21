package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.recipe.AlloyRecipe;

/**
 * Issue #1113: climbing an alloy chain has to pay. Review 04-impact.md &sect;1c found eleven alloys a
 * shallower material already matched on all three head stats, {@code osgloglas} among them losing to
 * one of its own three ingredients, and 06-progression.md &sect;3 found the mod's own rungs above
 * netherite sitting below plain netherite. This is the guard, over the real shipped data, so a later
 * stat edit cannot quietly flatten the curve again.
 *
 * <p>Three rules, all read off {@code material/*.json} and {@code alloy_recipe/*.json}:
 *
 * <ol>
 *   <li><b>Payoff.</b> Every alloy recipe's result beats the per-stat best of its own head-bearing
 *       inputs by at least {@link #PAYOFF} on at least two of the three head stats, and no input
 *       matches or beats it on all three. "Per-stat best" rather than "best input" is the strict
 *       reading: whichever ingredient a player would otherwise have carved, the alloy is meaningfully
 *       better than it on two axes.
 *   <li><b>The rungs above netherite.</b> Every Forgeweave-own material on the hardcinder, warspar or
 *       resonite rung clears plain netherite on all three head stats.
 *   <li><b>The deep alloys are the top.</b> No other own-or-vanilla material matches or beats any of
 *       the four depth-3/4 alloys on all three head stats.
 * </ol>
 *
 * <p>Compat materials are outside rules 2 and 3: another mod sets their acquisition cost, and the
 * issue's own wording leaves the compat endgame free to sit above Forgeweave's own.
 */
class AlloyPayoffTest {

    /** The margin an alloying step buys, on at least two of the three head stats (review 04-impact.md &sect;5b). */
    private static final double PAYOFF = 0.12;

    /** Plain netherite's head, the wall the rungs above it have to clear. */
    private static final double[] NETHERITE = {1050, 8.0, 7.0};

    /** The depth-3 and depth-4 alloys, which the issue makes the best own materials in the game. */
    private static final Set<String> DEEP_ALLOYS = Set.of("stormalloy", "sunsteel", "hollowsteel", "truesteel");

    private static final List<String> FORGEWEAVE_RUNGS = List.of("hardcinder", "warspar", "resonite");

    /**
     * Alloys whose result deliberately does not clear {@link #PAYOFF} against one of its inputs,
     * each because the input is an upstream specialist whose spike the balanced product is not meant
     * to match. Every entry is a parity number, not a gap:
     *
     * <ul>
     *   <li>{@code rose_gold} 90/10.0/2.0 -- the day-one mining spike, 10.0 speed on 90 durability.
     *       Issue #1113 names it as a keeper.
     *   <li>{@code manyullyn} 820/7.02/8.72 -- upstream 1.12's own numbers
     *       ({@code TinkerMaterials}), and cobalt's 12.0 mining speed is the 1.12 spike it trades away
     *       for damage.
     *   <li>{@code queens_slime} 1650/6.0/2.0 and {@code hepatizon} 975/8.0/2.5 -- upstream 1.20's
     *       own numbers ({@code MaterialStatsDataProvider}), both pure durability/speed picks built on
     *       cobalt.
     * </ul>
     */
    private static final Set<String> PARITY_SPECIALISTS = Set.of("rose_gold", "manyullyn", "queens_slime", "hepatizon");

    private static RegistryOps<JsonElement> ops;
    private static Map<String, Material> materials;
    private static Map<String, JsonElement> raw;
    private static List<AlloyRecipe> alloys;

    @BeforeAll
    static void loadShippedData() throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        materials = new HashMap<>();
        raw = new HashMap<>();
        for (Path file : shippedFiles("material")) {
            String name = file.getFileName().toString().replace(".json", "");
            JsonElement json = parse(file);
            raw.put(name, json);
            materials.put(name, Material.CODEC.parse(ops, json).getOrThrow());
        }
        List<AlloyRecipe> parsed = new ArrayList<>();
        for (Path file : shippedFiles("alloy_recipe")) {
            parsed.add(AlloyRecipe.CODEC.parse(ops, parse(file)).getOrThrow());
        }
        alloys = List.copyOf(parsed);
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

    private static List<Path> shippedFiles(String registry) throws Exception {
        Path dir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave").resolve(registry);
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.toString().endsWith(".json")).sorted().toList();
        }
    }

    private static JsonElement parse(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
    }

    /** The material a molten fluid belongs to, i.e. {@link MaterialStage#moltenFluidId} run backwards. */
    private static String materialOf(Fluid fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        String path = id.getPath();
        return path.startsWith("molten_") ? path.substring("molten_".length()) : path;
    }

    /** A material's three head stats, or empty for the catalysts and fluids that are not a material. */
    private static Optional<double[]> head(String materialId) {
        Material material = materials.get(materialId);
        if (material == null || material.head().isEmpty()) {
            return Optional.empty();
        }
        Material.Head h = material.head().get();
        return Optional.of(new double[] {h.durability(), h.miningSpeed(), h.attackDamage()});
    }

    private static String show(double[] stats) {
        return String.format(Locale.ROOT, "%.0f/%.2f/%.2f", stats[0], stats[1], stats[2]);
    }

    @Test
    void everyAlloyingStepBuysTwelvePercentOnTwoHeadStats() {
        assertTrue(alloys.size() >= 30, "non-vacuity: expected the real alloy table, saw " + alloys.size());
        int checked = 0;
        for (AlloyRecipe recipe : alloys) {
            String resultId = materialOf(recipe.result().getFluid());
            Optional<double[]> result = head(resultId);
            if (result.isEmpty() || PARITY_SPECIALISTS.contains(resultId)) {
                continue;
            }
            double[] out = result.get();
            double[] best = null;
            for (FluidStack input : recipe.inputs()) {
                double[] in = head(materialOf(input.getFluid())).orElse(null);
                if (in == null) {
                    continue;
                }
                assertFalse(in[0] >= out[0] && in[1] >= out[1] && in[2] >= out[2],
                        resultId + " " + show(out) + " is matched or beaten on all three head stats by its own"
                        + " ingredient " + materialOf(input.getFluid()) + " " + show(in));
                best = best == null ? in.clone()
                        : new double[] {Math.max(best[0], in[0]), Math.max(best[1], in[1]), Math.max(best[2], in[2])};
            }
            if (best == null) {
                continue;
            }
            checked++;
            int paid = 0;
            for (int stat = 0; stat < 3; stat++) {
                if (out[stat] / best[stat] - 1.0 >= PAYOFF) {
                    paid++;
                }
            }
            assertTrue(paid >= 2, resultId + " " + show(out) + " buys " + paid + " of the two head stats it owes"
                    + " over the best of its inputs " + show(best) + " (needs +12% on two)");
        }
        assertTrue(checked >= 25, "non-vacuity: only " + checked + " alloys had a head-bearing input to check");
    }

    @Test
    void theModsOwnRungsAboveNetheriteClearPlainNetherite() {
        int checked = 0;
        for (Map.Entry<String, Material> entry : materials.entrySet()) {
            if (isCompat(entry.getKey()) || !FORGEWEAVE_RUNGS.contains(rungOf(entry.getValue()))) {
                continue;
            }
            double[] stats = head(entry.getKey()).orElse(null);
            if (stats == null || PARITY_SPECIALISTS.contains(entry.getKey())) {
                continue;
            }
            checked++;
            for (int stat = 0; stat < 3; stat++) {
                assertTrue(stats[stat] > NETHERITE[stat],
                        entry.getKey() + " " + show(stats) + " sits on the " + rungOf(entry.getValue())
                        + " rung and does not clear plain netherite " + show(NETHERITE));
            }
        }
        assertTrue(checked >= 15, "non-vacuity: only " + checked + " own materials above the netherite rung");
    }

    @Test
    void theFourDeepAlloysAreTheBestOwnMaterials() {
        for (String deep : DEEP_ALLOYS) {
            double[] top = head(deep).orElseThrow(() -> new AssertionError("no head on " + deep));
            for (Map.Entry<String, Material> entry : materials.entrySet()) {
                if (DEEP_ALLOYS.contains(entry.getKey()) || isCompat(entry.getKey())) {
                    continue;
                }
                double[] stats = head(entry.getKey()).orElse(null);
                if (stats == null) {
                    continue;
                }
                assertFalse(stats[0] >= top[0] && stats[1] >= top[1] && stats[2] >= top[2],
                        entry.getKey() + " " + show(stats) + " matches or beats the depth-3/4 alloy " + deep
                        + " " + show(top) + " on all three head stats");
            }
        }
    }

    /**
     * The seven byte-identical stat clusters review 06-progression.md &sect;3 counted, 25 materials in
     * all -- six Actually Additions crystals plus the spirit gem, their six empowered forms, three
     * deathworm chitins, three troll leathers, two psimetals, and the two Elementarium presets that
     * came out as copies of their own interpolation anchors. No two materials share a head/handle
     * profile now, and this fails if a new one lands on an existing profile.
     */
    @Test
    void noTwoMaterialsShareTheSameHeadAndHandleProfile() {
        Map<String, String> seen = new HashMap<>();
        for (Map.Entry<String, Material> entry : materials.entrySet()) {
            Material material = entry.getValue();
            if (material.head().isEmpty()) {
                continue;
            }
            Material.Head h = material.head().get();
            String profile = h.durability() + "/" + h.miningSpeed() + "/" + h.attackDamage()
                    + "/" + material.extraDurability().orElse(0)
                    + "/" + material.handle().map(Material.Handle::durabilityModifier).orElse(0.0F)
                    + "/" + material.handle().map(Material.Handle::durability).orElse(0);
            String other = seen.put(profile, entry.getKey());
            assertNull(other, entry.getKey() + " and " + other + " share one stat profile, " + profile);
        }
        assertTrue(seen.size() >= 190, "non-vacuity: only " + seen.size() + " distinct profiles");
    }

    /**
     * Whether another mod has to be installed for this material to exist -- read off the raw JSON
     * rather than the parsed record, the same two signals review 04-impact.md's own scan reads: a
     * {@code neoforge:conditions} gate, or an ingredient in a namespace that is neither Forgeweave's,
     * vanilla's, nor the {@code c:} convention's.
     */
    private static boolean isCompat(String materialId) {
        JsonObject json = raw.get(materialId).getAsJsonObject();
        if (json.has("neoforge:conditions")) {
            return true;
        }
        return ingredientNamespaces(json).anyMatch(namespace ->
                !namespace.equals("forgeweave") && !namespace.equals("minecraft") && !namespace.equals("c"));
    }

    private static Stream<String> ingredientNamespaces(JsonObject json) {
        List<String> found = new ArrayList<>();
        for (JsonElement entry : json.getAsJsonArray("crafting_items")) {
            JsonObject ingredient = entry.getAsJsonObject().getAsJsonObject("ingredient");
            for (String key : List.of("item", "tag")) {
                if (ingredient.has(key)) {
                    found.add(ingredient.get(key).getAsString().split(":")[0]);
                }
            }
        }
        return found.stream();
    }

    private static String rungOf(Material material) {
        return material.incorrectForTool().location().getPath()
                .replace("incorrect_for_", "").replace("_tool", "");
    }
}
