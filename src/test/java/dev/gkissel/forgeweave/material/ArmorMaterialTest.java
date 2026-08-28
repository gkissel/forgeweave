package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.item.PartItem;

/**
 * The PLATING and MAILLE material blocks (issue #676, M4-1; SCOPE.md D10/D11/D14/D17): codec shape,
 * the per-piece values derived from the 1.20 clone's {@code tconstruct:plating_*} stat rows, the
 * three interpolated metals, and the ARMOR-scoped trait list.
 */
class ArmorMaterialTest {

    /** D10: the 15 clone-derived plating materials plus ardite, netherite and nahuatl. */
    private static final Set<String> PLATING = Set.of("iron", "copper", "cobalt", "manyullyn", "knightslime",
            "pig_iron", "steel", "bronze", "lead", "silver", "electrum", "amethyst_bronze", "rose_gold",
            "obsidian", "ancient", "ardite", "netherite", "nahuatl");

    /** D11: every plating material plus the five maille-only ones. */
    private static final Set<String> MAILLE = Stream.concat(PLATING.stream(),
            Stream.of("vine", "chorus", "bone", "cactus", "slimevine_blue")).collect(java.util.stream.Collectors.toSet());

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static Material shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = ArmorMaterialTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped material JSON: " + path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Material.CODEC.parse(ops, json).getOrThrow();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    static Stream<String> shippedMaterials() throws Exception {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null && !Files.exists(dir.resolve("settings.gradle"))) {
            dir = dir.getParent();
        }
        assertTrue(dir != null, "could not locate project root (no settings.gradle found)");
        try (Stream<Path> files = Files.list(dir.resolve("src/main/resources/data/forgeweave/forgeweave/material"))) {
            return files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5)).sorted().toList().stream();
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    private static Material armorMaterial(Optional<Material.Plating> plating, boolean maille, Material.Traits traits) {
        return new Material(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                traits,
                List.of(new Material.CraftingItem(Ingredient.of(Items.IRON_INGOT), 144)),
                Ingredient.of(Items.IRON_INGOT),
                TextColor.parseColor("#D8D8D8").getOrThrow(),
                Optional.empty(),
                Optional.empty(),
                false,
                Material.DEFAULT_ENCHANTABILITY,
                Optional.empty(),
                Optional.empty(),
                plating,
                maille);
    }

    @Test
    void platingMailleAndArmorTraitsRoundTripThroughTheCodec() {
        Material.Plating plating = new Material.Plating(
                new Material.PlatingPiece(330, 2.0f, 1.0f, 0.05f),
                new Material.PlatingPiece(480, 7.0f, 1.0f, 0.05f),
                new Material.PlatingPiece(450, 5.0f, 1.0f, 0.05f),
                new Material.PlatingPiece(390, 2.0f, 1.0f, 0.05f));
        Material.Traits traits = new Material.Traits(List.of(id("lightweight")), List.of(), List.of(), List.of(),
                List.of(id("melee_protection")));
        Material material = armorMaterial(Optional.of(plating), true, traits);

        JsonElement encoded = Material.CODEC.encodeStart(ops, material).getOrThrow();
        Material decoded = Material.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(Optional.of(plating), decoded.plating());
        assertTrue(decoded.maille());
        assertEquals(List.of(id("melee_protection")), decoded.traits().armor());
    }

    /** {@code toughness} and {@code knockback_resistance} default to 0, so iron's rows stay terse. */
    @Test
    void toughnessAndKnockbackResistanceDefaultToZero() {
        JsonObject piece = JsonParser.parseString("{\"durability\": 240, \"armor\": 5.0}").getAsJsonObject();

        Material.PlatingPiece decoded = Material.PlatingPiece.CODEC.parse(JsonOps.INSTANCE, piece).getOrThrow();

        assertEquals(new Material.PlatingPiece(240, 5.0f, 0.0f, 0.0f), decoded);
    }

    /** Absent blocks cost the registry-sync payload nothing (the budget {@code MaterialSyncSizeTest} guards). */
    @Test
    void absentBlocksAreOmittedFromTheEncodedMaterial() {
        JsonObject encoded = Material.CODEC
                .encodeStart(ops, armorMaterial(Optional.empty(), false, Material.Traits.general(id("lightweight"))))
                .getOrThrow().getAsJsonObject();

        assertFalse(encoded.has("plating"), "absent plating block must not be encoded");
        assertFalse(encoded.has("maille"), "a non-maille material must not encode the marker");
        assertFalse(encoded.getAsJsonObject("traits").has("armor"), "an empty armor scope must not be encoded");
    }

    /** D10/D11 for every shipped material: exactly the listed ones answer PLATING / MAILLE. */
    @ParameterizedTest
    @MethodSource("shippedMaterials")
    void hasStatsForPlatingAndMailleMatchesTheRoster(String name) {
        Material material = shipped(name);

        assertEquals(PLATING.contains(name), material.hasStatsFor(PartItem.Kind.PLATING), name + " plating");
        assertEquals(MAILLE.contains(name), material.hasStatsFor(PartItem.Kind.MAILLE), name + " maille");
    }

    /**
     * Spot checks against the clone's generated stats at the pinned commit
     * ({@code tinkering/materials/stats/<m>.json}, {@code tconstruct:plating_<piece>}), plus the three
     * interpolated metals whose numbers the PR body proposes.
     */
    @ParameterizedTest
    @CsvSource({
            "iron,165,2.0,0.0,0.0,240,5.0,0.0,0.0,225,4.0,0.0,0.0,195,2.0,0.0,0.0",
            "cobalt,330,2.0,1.0,0.05,480,7.0,1.0,0.05,450,5.0,1.0,0.05,390,2.0,1.0,0.05",
            "manyullyn,385,2.0,3.0,0.05,560,7.0,3.0,0.05,525,5.0,3.0,0.05,455,2.0,3.0,0.05",
            "obsidian,121,2.0,0.0,0.15,176,5.0,0.0,0.15,165,4.0,0.0,0.15,143,2.0,0.0,0.15",
            "ardite,363,2.0,1.0,0.05,528,7.0,1.0,0.05,495,5.0,1.0,0.05,429,2.0,1.0,0.05",
            "netherite,407,3.0,3.0,0.1,592,8.0,3.0,0.1,555,6.0,3.0,0.1,481,3.0,3.0,0.1",
            "nahuatl,151,2.0,0.0,0.1,220,5.0,0.0,0.1,206,4.0,0.0,0.1,179,2.0,0.0,0.1",
    })
    void shippedPlatingMatchesTheCloneOrTheProposedInterpolation(String name,
            int hd, float ha, float ht, float hk,
            int cd, float ca, float ct, float ck,
            int ld, float la, float lt, float lk,
            int bd, float ba, float bt, float bk) {
        Material.Plating plating = shipped(name).plating().orElseThrow();

        assertEquals(new Material.PlatingPiece(hd, ha, ht, hk), plating.helmet(), name + " helmet");
        assertEquals(new Material.PlatingPiece(cd, ca, ct, ck), plating.chestplate(), name + " chestplate");
        assertEquals(new Material.PlatingPiece(ld, la, lt, lk), plating.leggings(), name + " leggings");
        assertEquals(new Material.PlatingPiece(bd, ba, bt, bk), plating.boots(), name + " boots");
    }

    /**
     * D17: the ARMOR-scoped list replaces the general one on plating and maille parts only, and
     * materials without a clone ARMOR row fall back to their general traits.
     */
    @ParameterizedTest
    @CsvSource({
            "iron,projectile_protection", "copper,depth_protection", "obsidian,blast_protection",
            "cobalt,melee_protection", "manyullyn,warded", "amethyst_bronze,crystalstrike",
            "silver,consecrated", "knightslime,overshield|overslime", "bone,piercing_guard", "cactus,thorns",
            "chorus,enderclearance|overslime_friend", "slimevine_blue,skyfall|overslime_friend",
    })
    void armorScopedTraitsApplyToPlatingAndMailleOnly(String name, String traits) {
        Material material = shipped(name);
        // #728: knightslime carries the clone's overshield + overslime pair, the vines overslime_friend.
        List<ResourceLocation> expected = Arrays.stream(traits.split("\\|")).map(ArmorMaterialTest::id).toList();

        assertEquals(expected, material.traits().forPart(PartItem.Kind.PLATING), name + " plating");
        assertEquals(expected, material.traits().forPart(PartItem.Kind.MAILLE), name + " maille");
        assertEquals(material.traits().general(), material.traits().forPart(PartItem.Kind.HANDLE),
                name + " handle keeps the general list");
        assertTrue(material.traits().all().containsAll(expected), name + " all()");
    }

    @Test
    void materialsWithoutAnArmorRowFallBackToGeneral() {
        Material steel = shipped("steel");

        assertTrue(steel.traits().armor().isEmpty());
        assertEquals(steel.traits().general(), steel.traits().forPart(PartItem.Kind.PLATING));
        assertEquals(steel.traits().general(), steel.traits().forPart(PartItem.Kind.MAILLE));
    }
}
