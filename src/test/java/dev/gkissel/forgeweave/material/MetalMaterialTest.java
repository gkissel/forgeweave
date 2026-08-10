package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Pins the seven metal materials' (docs/SCOPE.md M2 issue #103) shipped JSON stats against upstream
 * 1.12's {@code TinkerMaterials#registerToolMaterialStats}/{@code #setupMaterials} constants (cited
 * per-material below and in NOTICE.md); iron/copper/cobalt/ardite/manyullyn are 1:1 ports, rose gold
 * and netherite are this PR's own invented numbers (no upstream counterpart -- NOTICE.md).
 *
 * <p>{@code MaterialTest} covers the schema itself; this covers only that these seven materials parse
 * to the exact numbers the PR claims. Real-assembly stat derivation
 * ({@code ToolStats#compute}, traits, repair) is covered on a live Tool Station by
 * {@code gametest.MetalMaterialGameTests}.
 */
class MetalMaterialTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static Material shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = MetalMaterialTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped material JSON: " + path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Material.CODEC.parse(ops, json).getOrThrow();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    @ParameterizedTest
    @ValueSource(strings = { "iron", "copper", "cobalt", "ardite", "manyullyn", "rose_gold", "netherite" })
    void shippedMetalsParse(String name) {
        shipped(name);
    }

    // TinkerMaterials.registerToolMaterialStats: new HeadMaterialStats(204, 6.00f, 4.00f, DIAMOND),
    // new HandleMaterialStats(0.85f, 60), new ExtraMaterialStats(50).
    @Test
    void ironMatchesUpstreamsExactStats() {
        Material iron = shipped("iron");

        assertEquals(new Material.Head(204, 6.0f, 4.0f), iron.head());
        assertEquals(new Material.Handle(0.85f, 60), iron.handle());
        assertEquals(50, iron.extraDurability());
        assertEquals(List.of(id("magnetic")), iron.traits().general());
        assertEquals(List.of(id("magnetic2")), iron.traits().head());
    }

    // new HeadMaterialStats(210, 5.30f, 3.00f, IRON), new HandleMaterialStats(1.05f, 30), new
    // ExtraMaterialStats(100).
    @Test
    void copperMatchesUpstreamsExactStats() {
        Material copper = shipped("copper");

        assertEquals(new Material.Head(210, 5.3f, 3.0f), copper.head());
        assertEquals(new Material.Handle(1.05f, 30), copper.handle());
        assertEquals(100, copper.extraDurability());
        assertEquals(List.of(id("established")), copper.traits().general());
    }

    // new HeadMaterialStats(780, 12.00f, 4.10f, COBALT), new HandleMaterialStats(0.90f, 100), new
    // ExtraMaterialStats(300).
    @Test
    void cobaltMatchesUpstreamsExactStats() {
        Material cobalt = shipped("cobalt");

        assertEquals(new Material.Head(780, 12.0f, 4.1f), cobalt.head());
        assertEquals(new Material.Handle(0.9f, 100), cobalt.handle());
        assertEquals(300, cobalt.extraDurability());
        assertEquals(List.of(id("lightweight")), cobalt.traits().general());
        assertEquals(List.of(id("momentum")), cobalt.traits().head());
    }

    // new HeadMaterialStats(990, 3.50f, 3.60f, COBALT), new HandleMaterialStats(1.40f, -200), new
    // ExtraMaterialStats(450).
    @Test
    void arditeMatchesUpstreamsExactStats() {
        Material ardite = shipped("ardite");

        assertEquals(new Material.Head(990, 3.5f, 3.6f), ardite.head());
        assertEquals(new Material.Handle(1.4f, -200), ardite.handle());
        assertEquals(450, ardite.extraDurability());
        assertEquals(List.of(id("petramor")), ardite.traits().general());
        assertEquals(List.of(id("stonebound")), ardite.traits().head());
    }

    // new HeadMaterialStats(820, 7.02f, 8.72f, COBALT), new HandleMaterialStats(0.50f, 250), new
    // ExtraMaterialStats(50).
    @Test
    void manyullynMatchesUpstreamsExactStats() {
        Material manyullyn = shipped("manyullyn");

        assertEquals(new Material.Head(820, 7.02f, 8.72f), manyullyn.head());
        assertEquals(new Material.Handle(0.5f, 250), manyullyn.handle());
        assertEquals(50, manyullyn.extraDurability());
        assertEquals(List.of(id("coldblooded")), manyullyn.traits().general());
        assertEquals(List.of(id("insatiable")), manyullyn.traits().head());
    }

    /** No upstream counterpart (issue #103): rose gold's numbers are this PR's own -- NOTICE.md. */
    @Test
    void roseGoldMatchesItsRecordedInventedStats() {
        Material roseGold = shipped("rose_gold");

        assertEquals(new Material.Head(90, 10.0f, 2.0f), roseGold.head());
        assertEquals(new Material.Handle(0.65f, -40), roseGold.handle());
        assertEquals(15, roseGold.extraDurability());
        assertEquals(List.of(id("quick")), roseGold.traits().general());
    }

    /** No upstream counterpart (issue #103): netherite's numbers are this PR's own -- NOTICE.md. */
    @Test
    void netheriteMatchesItsRecordedInventedStats() {
        Material netherite = shipped("netherite");

        assertEquals(new Material.Head(1050, 8.0f, 7.0f), netherite.head());
        assertEquals(new Material.Handle(1.0f, 150), netherite.handle());
        assertEquals(350, netherite.extraDurability());
        assertEquals(List.of(id("fireproof"), id("reinforced_core")), netherite.traits().general());
    }

    /**
     * Manyullyn assembled entirely from itself, through the same pure function the Tool Station uses
     * ({@code ToolStats#compute}): durability = round((820 + 50) * 0.5) + 250 = 685.
     */
    @Test
    void manyullynComputesUpstreamsExactAssembledDurability() {
        Material manyullyn = shipped("manyullyn");

        ToolStats.Stats stats = ToolStats.compute(manyullyn, manyullyn, manyullyn);

        assertEquals(685, stats.durability());
        assertEquals(7.02f, stats.miningSpeed());
        assertEquals(8.72f, stats.attackDamage());
    }

    /**
     * Iron and copper reuse vanilla's own tool-tier ceilings; cobalt/ardite/manyullyn/netherite have
     * no vanilla tag for upstream's higher {@code COBALT} harvest level and so share the ladder's top
     * rung instead (NOTICE.md deviation).
     */
    @ParameterizedTest
    @CsvSource({
            "iron,minecraft:incorrect_for_diamond_tool",
            "copper,minecraft:incorrect_for_iron_tool",
            "cobalt,minecraft:incorrect_for_netherite_tool",
            "ardite,minecraft:incorrect_for_netherite_tool",
            "manyullyn,minecraft:incorrect_for_netherite_tool",
            "netherite,minecraft:incorrect_for_netherite_tool",
            "rose_gold,minecraft:incorrect_for_stone_tool"
    })
    void metalsUseTheExpectedToolTierTag(String name, String tag) {
        Material material = shipped(name);

        assertEquals(tag, material.incorrectForTool().location().toString());
    }
}
