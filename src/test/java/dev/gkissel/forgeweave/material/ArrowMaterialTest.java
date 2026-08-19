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
 * The SHAFT and FLETCHING material stat blocks (issue #626, parity audit T17): their codec shape,
 * the values ported from upstream 1.12's {@code TinkerMaterials#registerProjectileMaterialStats},
 * the six arrow-only materials from {@code TinkerMaterials:360-390}, bone's SHAFT-scoped
 * {@code splitting} trait ({@code TinkerMaterials:272}), and the stat lines the two blocks
 * contribute to the part tooltip and the Part Builder's info panel.
 */
class ArrowMaterialTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static Material shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = ArrowMaterialTest.class.getResourceAsStream(path)) {
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

    private static Material arrowMaterial(Optional<Material.Shaft> shaft, Optional<Material.Fletching> fletching) {
        return new Material(
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(id("hovering")), List.of()),
                List.of(new Material.CraftingItem(Ingredient.of(Items.BLAZE_ROD), 144)),
                Ingredient.of(Items.BLAZE_ROD),
                TextColor.parseColor("#FFC100").getOrThrow(),
                Optional.empty(),
                Optional.empty(),
                false,
                Material.DEFAULT_ENCHANTABILITY,
                shaft,
                fletching);
    }

    @Test
    void shaftAndFletchingRoundTripThroughTheCodec() {
        Material material = arrowMaterial(Optional.of(new Material.Shaft(0.9f, 5)),
                Optional.of(new Material.Fletching(0.5f, 1.5f)));

        JsonElement encoded = Material.CODEC.encodeStart(ops, material).getOrThrow();
        Material decoded = Material.CODEC.parse(ops, encoded).getOrThrow();

        assertEquals(Optional.of(new Material.Shaft(0.9f, 5)), decoded.shaft());
        assertEquals(Optional.of(new Material.Fletching(0.5f, 1.5f)), decoded.fletching());
    }

    /**
     * Both blocks are optional and DFU omits an absent optional on encode, so a material with no
     * arrow stats costs the registry-sync payload nothing (the budget {@code MaterialSyncSizeTest}
     * guards).
     */
    @Test
    void absentBlocksAreOmittedFromTheEncodedMaterial() {
        JsonObject encoded = Material.CODEC
                .encodeStart(ops, arrowMaterial(Optional.empty(), Optional.empty())).getOrThrow().getAsJsonObject();

        assertFalse(encoded.has("shaft"), "absent shaft block must not be encoded");
        assertFalse(encoded.has("fletching"), "absent fletching block must not be encoded");
    }

    /**
     * The whole SHAFT table, verbatim from {@code TinkerMaterials#registerProjectileMaterialStats}
     * ({@code TinkerMaterials.java:589-594}) at the pinned commit: {@code new
     * ArrowShaftMaterialStats(modifier, bonusAmmo)}.
     */
    @ParameterizedTest
    @CsvSource({
            "wood,1.0,0",
            "bone,0.9,5",
            "blaze,0.8,3",
            "reed,1.5,20",
            "ice,0.95,0",
            "endrod,0.7,1",
    })
    void shippedMaterialsMatchUpstreamShaftStats(String name, float modifier, int bonusAmmo) {
        assertEquals(Optional.of(new Material.Shaft(modifier, bonusAmmo)), shipped(name).shaft());
    }

    /**
     * The whole FLETCHING table ({@code TinkerMaterials.java:597-598}): {@code new
     * FletchingMaterialStats(accuracy, modifier)}. The slimeleaf trio stays deferred with T57's
     * world content, exactly as issue #626 scopes it.
     */
    @ParameterizedTest
    @CsvSource({
            "feather,1.0,1.0",
            "leaf,0.5,1.5",
    })
    void shippedMaterialsMatchUpstreamFletchingStats(String name, float accuracy, float modifier) {
        assertEquals(Optional.of(new Material.Fletching(accuracy, modifier)), shipped(name).fletching());
    }

    /**
     * Upstream's arrow-only materials get nothing but their one block ({@code mat(...)} plus a
     * single {@code addMaterialStats} call each), so none of them may turn up as a head, handle,
     * binding or bow part.
     */
    @ParameterizedTest
    @ValueSource(strings = { "blaze", "reed", "ice", "endrod", "feather", "leaf" })
    void arrowOnlyMaterialsCarryNoToolStatBlocks(String name) {
        Material material = shipped(name);

        assertTrue(material.head().isEmpty(), name + " must have no head stats");
        assertTrue(material.handle().isEmpty(), name + " must have no handle stats");
        assertTrue(material.extraDurability().isEmpty(), name + " must have no extra durability");
        assertTrue(material.bow().isEmpty(), name + " must have no bow stats");
        assertTrue(material.bowstring().isEmpty(), name + " must have no bowstring stats");
    }

    /** Colors verbatim from {@code TinkerMaterials.java:164-171}'s {@code mat(name, color)} calls. */
    @ParameterizedTest
    @CsvSource({
            "blaze,#FFC100",
            "reed,#AADB74",
            "ice,#97D7E0",
            "endrod,#E8FFD6",
            "feather,#EEEEEE",
            "leaf,#1D730C",
    })
    void arrowMaterialsCarryUpstreamsColor(String name, String color) {
        assertEquals(TextColor.parseColor(color).getOrThrow(), shipped(name).color());
    }

    /**
     * The five ammo traits, one per material exactly as {@code TinkerMaterials:367-381} assigns
     * them: blaze hovering, reed breakable, ice freezing, endrod endspeed. Feather and leaf carry
     * none.
     */
    @ParameterizedTest
    @CsvSource({
            "blaze,hovering",
            "reed,breakable",
            "ice,freezing",
            "endrod,endspeed",
    })
    void arrowMaterialsCarryTheirAmmoTrait(String name, String trait) {
        assertEquals(List.of(id(trait)), shipped(name).traits().general());
    }

    @Test
    void hasStatsForAnswersShaftAndFletching() {
        Material blaze = shipped("blaze");
        Material feather = shipped("feather");
        Material wood = shipped("wood");

        assertTrue(blaze.hasStatsFor(PartItem.Kind.SHAFT));
        assertFalse(blaze.hasStatsFor(PartItem.Kind.HEAD));
        assertFalse(blaze.hasStatsFor(PartItem.Kind.FLETCHING));

        assertTrue(feather.hasStatsFor(PartItem.Kind.FLETCHING));
        assertFalse(feather.hasStatsFor(PartItem.Kind.SHAFT));

        assertTrue(wood.hasStatsFor(PartItem.Kind.SHAFT));
        assertFalse(wood.hasStatsFor(PartItem.Kind.FLETCHING));
    }

    /**
     * Bone: {@code bone.addTrait(splitting, SHAFT)} ({@code TinkerMaterials:272}). A part-scoped
     * list <b>replaces</b> the general one for that part (upstream's {@code getAllTraitsForStats}
     * falls back to the default list only when the stat has no list of its own), so a bone shaft
     * grants splitting alone while a bone handle keeps fractured and a bone head keeps splintering.
     */
    @Test
    void boneShaftTraitReplacesTheGeneralList() {
        Material.Traits traits = shipped("bone").traits();

        assertEquals(List.of(id("splitting")), traits.forPart(PartItem.Kind.SHAFT));
        assertEquals(List.of(id("splintering")), traits.forPart(PartItem.Kind.HEAD));
        assertEquals(List.of(id("fractured")), traits.forPart(PartItem.Kind.HANDLE));
        assertTrue(traits.all().contains(id("splitting")));
    }

    /** A material with no shaft-scoped list falls back to general for its shaft, like every scope. */
    @Test
    void shaftScopeFallsBackToGeneralTraits() {
        assertEquals(List.of(id("hovering")), shipped("blaze").traits().forPart(PartItem.Kind.SHAFT));
    }

    /**
     * Upstream {@code ArrowShaftMaterialStats#getLocalizedInfo}: modifier first, then bonus ammo.
     */
    @Test
    void shaftStatsRenderTwoLines() {
        assertEquals(List.of("gui.forgeweave.stat.shaft_modifier", "gui.forgeweave.stat.bonus_ammo"),
                StationText.shaftStats(shipped("bone")).stream().map(Component::getString).toList());
    }

    /**
     * Upstream {@code FletchingMaterialStats#getLocalizedInfo}: modifier first, then accuracy --
     * and the accuracy is a whole percent ({@code formatNumberPercent}), so leaf's 0.5 reads 50%.
     */
    @Test
    void fletchingStatsRenderTwoLinesWithPercentAccuracy() {
        List<Component> lines = StationText.fletchingStats(shipped("leaf"));

        assertEquals(List.of("gui.forgeweave.stat.fletching_modifier", "gui.forgeweave.stat.accuracy"),
                lines.stream().map(Component::getString).toList());
        assertEquals("50%", statValue(lines.get(1)));
        assertEquals("100%", statValue(StationText.fletchingStats(shipped("feather")).get(1)));
    }

    /** The formatted number a stat row carries as its single translation argument. */
    private static String statValue(Component line) {
        Object[] args = ((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getArgs();
        return ((Component) args[0]).getString();
    }

    /** The info panel groups the new blocks too, after the existing five. */
    @Test
    void materialStatsGroupsTheArrowBlocks() {
        List<String> lines = StationText.materialStats(shipped("blaze")).stream()
                .map(line -> line == null ? null : line.getString()).toList();

        assertEquals(List.of("tooltip.forgeweave.stat_type.shaft", "gui.forgeweave.stat.shaft_modifier",
                "gui.forgeweave.stat.bonus_ammo"), lines,
                "a shaft-only material heads exactly one group and gets no trailing spacer");
    }
}
