package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.item.PartItem;

/**
 * The BOW and BOWSTRING material stat blocks (issue #392, M3.5): their codec shape, the values
 * ported from upstream 1.12's {@code TinkerMaterials#registerBowMaterialStats}, and the stat lines
 * they contribute to the part tooltip and the Part Builder's info panel.
 */
class BowMaterialTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static Material shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = BowMaterialTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped material JSON: " + path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Material.CODEC.parse(ops, json).getOrThrow();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static Material bowMaterial(Material.Bow bow, Optional<Material.Bowstring> bowstring) {
        return new Material(
                Optional.of(new Material.Head(200, 5.0f, 2.5f)),
                Optional.of(new Material.Handle(1.1f, 50)),
                Optional.of(65),
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(new Material.CraftingItem(Ingredient.of(Items.BONE), 2)),
                Ingredient.of(Items.BONE),
                TextColor.parseColor("#EDE6BF").getOrThrow(),
                Optional.of(bow),
                bowstring);
    }

    @Test
    void bowAndBowstringRoundTripThroughTheCodec() {
        Material material = bowMaterial(new Material.Bow(0.5f, 1.5f, 7.0f),
                Optional.of(new Material.Bowstring(1.0f)));

        JsonElement encoded = Material.CODEC.encodeStart(ops, material).getOrThrow();
        Material decoded = Material.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(Optional.of(new Material.Bow(0.5f, 1.5f, 7.0f)), decoded.bow());
        assertEquals(Optional.of(new Material.Bowstring(1.0f)), decoded.bowstring());
    }

    /** Upstream's paper limb is the one with negative bonus damage, so the codec must allow it. */
    @Test
    void bowAcceptsNegativeBonusDamage() {
        Material material = bowMaterial(new Material.Bow(1.5f, 0.4f, -2.0f), Optional.empty());
        JsonElement encoded = Material.CODEC.encodeStart(ops, material).getOrThrow();

        assertEquals(Optional.of(new Material.Bow(1.5f, 0.4f, -2.0f)),
                Material.CODEC.parse(ops, encoded).getOrThrow().bow());
    }

    /**
     * Both blocks are optional and DFU omits an absent optional on encode, so a material with no
     * bow stats costs the registry-sync payload nothing (the budget {@code MaterialSyncSizeTest}
     * guards).
     */
    @Test
    void absentBlocksAreOmittedFromTheEncodedMaterial() {
        Material material = new Material(
                Optional.of(new Material.Head(200, 5.0f, 2.5f)),
                Optional.of(new Material.Handle(1.1f, 50)),
                Optional.of(65),
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(),
                Ingredient.of(Items.BONE),
                TextColor.parseColor("#EDE6BF").getOrThrow(),
                Optional.empty(),
                Optional.empty());

        JsonObject encoded = Material.CODEC.encodeStart(ops, material).getOrThrow().getAsJsonObject();

        assertFalse(encoded.has("bow"), "absent bow block must not be encoded");
        assertFalse(encoded.has("bowstring"), "absent bowstring block must not be encoded");
    }

    /**
     * Spot check against {@code TinkerMaterials#registerBowMaterialStats} at the pinned 1.12 commit:
     * the wood baseline, upstream's shared "why would you make a bow out of this" block, the fast and
     * flimsy paper limb, and the two ends of the metal ladder.
     */
    @ParameterizedTest
    @CsvSource({
            "wood,1.0,1.0,0.0",
            "stone,0.2,0.4,-1.0",
            "flint,0.2,0.4,-1.0",
            "paper,1.5,0.4,-2.0",
            "iron,0.5,1.5,7.0",
            "steel,0.4,2.0,9.0",
            "knightslime,0.4,2.0,2.0",
            "manyullyn,0.65,1.2,4.0",
            "silver,1.2,0.8,2.0",
    })
    void shippedMaterialsMatchUpstreamBowStats(String name, float drawspeed, float range, float bonusDamage) {
        assertEquals(Optional.of(new Material.Bow(drawspeed, range, bonusDamage)), shipped(name).bow());
    }

    /** Every material upstream 1.12 gives a limb to, plus the six Forgeweave-only ones, carry a block. */
    @ParameterizedTest
    @ValueSource(strings = {
            "wood", "stone", "flint", "cactus", "bone", "obsidian", "prismarine", "endstone", "paper", "sponge",
            "slime", "blueslime", "knightslime", "magmaslime", "netherrack", "cobalt", "ardite", "manyullyn",
            "firewood", "iron", "pig_iron", "copper", "bronze", "lead", "silver", "electrum", "steel",
            "amethyst_bronze", "ancient", "chorus", "nahuatl", "netherite", "rose_gold",
    })
    void everyToolMaterialCarriesBowStats(String name) {
        assertTrue(shipped(name).bow().isPresent(), name + " is missing its bow stat block");
    }

    /**
     * Upstream's bowstring materials are stringy things with no tool stats at all ({@code
     * mat("string")} / {@code mat("vine")} get only {@code BowStringMaterialStats}), so they must not
     * be usable as a head, handle or binding.
     */
    @ParameterizedTest
    @ValueSource(strings = { "string", "vine" })
    void bowstringMaterialsCarryOnlyABowstringBlock(String name) {
        Material material = shipped(name);

        assertEquals(Optional.of(new Material.Bowstring(1.0f)), material.bowstring());
        assertTrue(material.head().isEmpty(), name + " must have no head stats");
        assertTrue(material.handle().isEmpty(), name + " must have no handle stats");
        assertTrue(material.extraDurability().isEmpty(), name + " must have no extra durability");
        assertTrue(material.bow().isEmpty(), name + " must have no bow stats");
    }

    @Test
    void hasStatsForAnswersPerPartKind() {
        Material string = shipped("string");
        Material wood = shipped("wood");

        assertTrue(string.hasStatsFor(PartItem.Kind.BOWSTRING));
        assertFalse(string.hasStatsFor(PartItem.Kind.HEAD));
        assertFalse(string.hasStatsFor(PartItem.Kind.HANDLE));
        assertFalse(string.hasStatsFor(PartItem.Kind.EXTRA));
        assertFalse(string.hasStatsFor(PartItem.Kind.BOW));

        assertTrue(wood.hasStatsFor(PartItem.Kind.HEAD));
        assertTrue(wood.hasStatsFor(PartItem.Kind.BOW));
        assertFalse(wood.hasStatsFor(PartItem.Kind.BOWSTRING));
    }

    /**
     * The three bow lines the part tooltip and the info panel both render. Upstream shows the draw
     * speed inverted ({@code BowMaterialStats#getLocalizedInfo} formats {@code 1f/drawspeed}), so
     * wood's stored 1.0 reads as 1 and steel's 0.4 reads as 2.5.
     */
    @Test
    void bowStatsRenderThreeLines() {
        List<String> keys = StationText.bowStats(shipped("steel")).stream().map(Component::getString).toList();

        assertEquals(List.of("gui.forgeweave.stat.drawspeed", "gui.forgeweave.stat.range",
                "gui.forgeweave.stat.bonus_damage"), keys);
    }

    /**
     * Upstream shows the draw speed inverted -- {@code BowMaterialStats#getLocalizedInfo} formats
     * {@code 1f/drawspeed} -- so steel's stored 0.4 reads as 2.5 (seconds-ish to draw) and wood's
     * 1.0 reads as 1.
     */
    @Test
    void theDrawSpeedLineShowsTheInverseOfTheStoredValue() {
        assertEquals("2.5", statValue(StationText.bowStats(shipped("steel")).get(0)));
        assertEquals("1", statValue(StationText.bowStats(shipped("wood")).get(0)));
    }

    @Test
    void bowstringStatsRenderOneLine() {
        assertEquals(List.of("gui.forgeweave.stat.bowstring_modifier"),
                StationText.bowstringStats(shipped("string")).stream().map(Component::getString).toList());
    }

    /** The formatted number a stat row carries as its single translation argument. */
    private static String statValue(Component line) {
        Object[] args = ((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getArgs();
        return ((Component) args[0]).getString();
    }

    /** A material with no block of a kind contributes no lines rather than an empty heading. */
    @Test
    void missingBlocksRenderNoLines() {
        Material string = shipped("string");

        assertTrue(StationText.headStats(string).isEmpty());
        assertTrue(StationText.handleStats(string).isEmpty());
        assertTrue(StationText.extraStats(string).isEmpty());
        assertTrue(StationText.bowStats(string).isEmpty());
    }

    /** The info panel groups every block the material actually has, and only those. */
    @Test
    void materialStatsGroupsOnlyThePresentBlocks() {
        List<Component> lines = StationText.materialStats(shipped("string"));

        assertEquals(List.of("tooltip.forgeweave.stat_type.bowstring", "gui.forgeweave.stat.bowstring_modifier"),
                lines.stream().map(Component::getString).toList(),
                "a bowstring-only material heads exactly one group and gets no trailing spacer");
    }
}
