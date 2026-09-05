package dev.gkissel.forgeweave.compat.draconic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.brandon3055.brandonscore.api.TechLevel;

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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #915's unit half: the fusion upgrade recipe's codec and the rule that decides whether a
 * given tool in the crafting core has anything to gain. Everything that needs a running fusion
 * multiblock -- the charge/craft state machine, the injectors, the RF -- is Draconic Evolution's own
 * code and is not exercised here; what this pins is the half Forgeweave wrote.
 *
 * <p>Runs with Draconic Evolution and BrandonsCore on the test classpath only (build.gradle's
 * {@code testCompileOnly}/{@code testRuntimeOnly} rows). No dev or gametest run ever loads them, so
 * this is the only place the compat layer is executed at all.
 */
class FusionUpgradeRecipeTest {

    private static RegistryOps<JsonElement> ops;

    private static final ResourceLocation HASTE = ResourceLocation.fromNamespaceAndPath("forgeweave", "haste");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /**
     * The four tier markers in ladder order. The ids read one behind the level they stand on:
     * issue #965 added {@code evolving} underneath rather than renumbering the three shipped
     * {@code evolved} ids, so a tool already in a save keeps the tier it was built at.
     */
    private static final List<String> TIER_TRAITS = List.of("evolving", "evolved", "evolved2", "evolved3");

    /**
     * A pickaxe carrying the tier marker for {@code level} -- what a tool built out of one of the
     * four fusion metals looks like by the time it reaches a crafting core (issues #946, #965).
     * Level 0 gives a plain assembled pickaxe with no trait at all, i.e. the "iron tool" case.
     */
    private static ItemStack evolvedTool(int level) {
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        if (level > 0) {
            pickaxe.set(ForgeweaveDataComponents.TRAITS.get(), List.of(
                    ResourceLocation.fromNamespaceAndPath("forgeweave", TIER_TRAITS.get(level - 1))));
        }
        return pickaxe;
    }

    private static FusionUpgradeRecipe decode(JsonObject json) {
        return FusionUpgradeRecipe.Serializer.INSTANCE.codec()
                .codec().parse(ops, json).getOrThrow();
    }

    /** The recipe fixture, spelled as the shipped JSON spells it minus the tag-only catalyst. */
    private static JsonObject fixture(int level, String techLevel, long energy) {
        JsonObject json = new JsonObject();
        JsonObject catalyst = new JsonObject();
        catalyst.addProperty("item", "forgeweave:pickaxe");
        json.add("catalyst", catalyst);
        json.addProperty("modifier", HASTE.toString());
        json.addProperty("level", level);
        json.add("ingredients", JsonParser.parseString("""
                [
                  {"ingredient": {"item": "minecraft:redstone_block"}, "consume": true},
                  {"ingredient": {"item": "minecraft:diamond"}, "consume": false}
                ]
                """));
        json.addProperty("totalEnergy", energy);
        json.addProperty("techLevel", techLevel);
        return json;
    }

    @Test
    void codecRoundTripsEveryField() {
        JsonObject json = fixture(100, "wyvern", 8_000_000L);
        FusionUpgradeRecipe recipe = decode(json);

        assertEquals(HASTE, recipe.modifier());
        assertEquals(100, recipe.level());
        assertEquals(8_000_000L, recipe.totalEnergy());
        assertEquals(TechLevel.WYVERN, recipe.techLevel());
        assertEquals(TechLevel.WYVERN, recipe.getRecipeTier(), "getRecipeTier is what DE reads the tier off");
        assertEquals(8_000_000L, recipe.getEnergyCost());
        assertEquals(2, recipe.ingredients().size());
        assertTrue(recipe.ingredients().get(0).consume(), "the first entry names consume: true");
        assertTrue(recipe.ingredients().get(0).ingredient().test(new ItemStack(Items.REDSTONE_BLOCK)));
        assertTrue(recipe.ingredients().get(1).ingredient().test(new ItemStack(Items.DIAMOND)));
        assertTrue(recipe.getCatalyst().test(new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get())));
        assertEquals(2, recipe.fusionIngredients().size(),
                "fusionIngredients is the list DE hands the injectors, and does not include the catalyst");

        JsonElement encoded = FusionUpgradeRecipe.Serializer.INSTANCE.codec()
                .codec().encodeStart(ops, recipe).getOrThrow();
        assertEquals(recipe, decode(encoded.getAsJsonObject()), "encode then decode is the identity");
    }

    @Test
    void consumeDefaultsToTrue() {
        JsonObject json = fixture(50, "draconium", 2_000_000L);
        json.add("ingredients", JsonParser.parseString("""
                [{"ingredient": {"item": "minecraft:redstone_block"}}]
                """));
        assertTrue(decode(json).ingredients().get(0).consume(),
                "an entry with no consume field is consumed, the same default DE's own rows write out");
    }

    @Test
    void everyShippedRowParses() {
        for (ForgeweaveDraconicCompat.Line line : ForgeweaveDraconicCompat.UPGRADE_LINES) {
            ResourceLocation modifier = ResourceLocation.parse(line.modifier());
            assertNotNull(ForgeweaveModifiers.get(modifier),
                    line.modifier() + " is in the fusion roster but is not a registered modifier");
            for (ForgeweaveDraconicCompat.Rung rung : line.rungs()) {
                String path = "/data/forgeweave/recipe/compat/draconic/"
                        + modifier.getPath() + "_" + rung.techLevel() + ".json";
                JsonObject json = read(path);
                assertEquals("forgeweave:draconic_fusion_upgrade", json.get("type").getAsString(), path);
                json.remove("type");
                json.remove("neoforge:conditions");
                FusionUpgradeRecipe recipe = decode(json);
                assertEquals(rung.level(), recipe.level(), path);
                assertEquals(rung.energy(), recipe.totalEnergy(), path);
                assertEquals(rung.techLevel(), recipe.techLevel().getSerializedName(), path);
            }
        }
    }

    @Test
    void upgradeRaisesTheModifierOnAnAssembledTool() {
        FusionUpgradeRecipe recipe = decode(fixture(100, "wyvern", 8_000_000L));
        ItemStack pickaxe = evolvedTool(2);

        Optional<ItemStack> upgraded = recipe.upgrade(null, pickaxe);

        assertTrue(upgraded.isPresent(), "an evolved pickaxe with no haste yet has haste to gain");
        List<ModifierEntry> entries = ForgeweaveModifiers.of(upgraded.get());
        assertEquals(List.of(new ModifierEntry(HASTE, 100)), entries);
        assertTrue(ForgeweaveModifiers.of(pickaxe).isEmpty(), "the catalyst stack itself is left alone");
    }

    /**
     * Issue #952: the class javadoc's "does not spend a modifier slot" is arithmetic now rather than
     * a comment. The entry the upgrade writes occupies a slot the way every entry does, so the tool
     * carries back as many through {@code granted_slots} and {@link ForgeweaveModifiers#freeSlots}
     * reads what it read before. {@code gametest.ModifierGameTests} spends the untouched slots at a
     * real Tool Station.
     */
    @Test
    void aFusionUpgradeSpendsNoModifierSlot() {
        ItemStack pickaxe = evolvedTool(4);
        int before = ForgeweaveModifiers.freeSlots(pickaxe);

        ItemStack wyvern = decode(fixture(100, "wyvern", 8_000_000L)).upgrade(null, pickaxe).orElseThrow();
        assertEquals(before, ForgeweaveModifiers.freeSlots(wyvern),
                "one rung must leave the slot budget where it found it");

        ItemStack chaotic = decode(fixture(250, "chaotic", 128_000_000L)).upgrade(null, wyvern).orElseThrow();
        assertEquals(before, ForgeweaveModifiers.freeSlots(chaotic),
                "and so must the next rung up, which rewrites the same entry rather than adding one");

        assertEquals(before, ForgeweaveModifiers.freeSlots(chaotic.copy()),
                "the grant rides the stack's components, so it survives the copy a repair or a part "
                        + "exchange rebuilds a tool from");
    }

    /**
     * Issue #952's display half, checked from the matching side: the catalyst still accepts exactly
     * what the tag accepts. All that changed is which stacks it offers a screen to draw
     * ({@code FusionDisplay}), and those need a loaded material registry this test has none of, so
     * the ingredient falls back to the tag's own stacks here.
     */
    @Test
    void theDisplayCatalystMatchesExactlyWhatTheTagDoes() {
        FusionUpgradeRecipe recipe = decode(fixture(100, "wyvern", 8_000_000L));
        Ingredient display = recipe.getCatalyst();

        assertTrue(display.test(new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get())),
                "the tag's own item must still match");
        assertFalse(display.test(new ItemStack(Items.STICK)), "and nothing else may start matching");
        assertEquals(recipe.catalyst().getItems()[0].getItem(), display.getItems()[0].getItem(),
                "with no materials loaded the display falls back to the tag's stacks");
    }

    @Test
    void upgradeRefusesAToolAlreadyAtOrAboveTheTiersLevel() {
        FusionUpgradeRecipe wyvern = decode(fixture(100, "wyvern", 8_000_000L));
        ItemStack pickaxe = wyvern.upgrade(null, evolvedTool(4)).orElseThrow();

        assertTrue(wyvern.upgrade(null, pickaxe).isEmpty(),
                "the same rung twice is nothing gained, so the craft must not start");
        FusionUpgradeRecipe draconium = decode(fixture(50, "draconium", 2_000_000L));
        assertTrue(draconium.upgrade(null, pickaxe).isEmpty(),
                "a lower rung never walks a tool back down");
        FusionUpgradeRecipe draconic = decode(fixture(175, "draconic", 32_000_000L));
        assertEquals(175, ForgeweaveModifiers.entry(draconic.upgrade(null, pickaxe).orElseThrow(), HASTE).level(),
                "the next rung up carries on from where the last one left the tool");
    }

    @Test
    void upgradeRefusesAnythingThatIsNotAnAssembledTool() {
        FusionUpgradeRecipe recipe = decode(fixture(100, "wyvern", 8_000_000L));
        assertTrue(recipe.upgrade(null, new ItemStack(Items.STICK)).isEmpty(),
                "the catalyst tag can only ever hold Forgeweave tools, but the recipe checks anyway");
    }

    @Test
    void upgradeRefusesAToolShapeTheModifierDoesNotAccept() {
        JsonObject json = fixture(3, "wyvern", 8_000_000L);
        json.addProperty("modifier", "forgeweave:veinmine");
        FusionUpgradeRecipe recipe = decode(json);

        assertTrue(recipe.upgrade(null, evolvedTool(2)).isPresent(),
                "vein mining is a harvest modifier and a pickaxe harvests");
        ItemStack sword = new ItemStack(ForgeweaveItems.TOOL_BROADSWORD.get());
        sword.set(ForgeweaveDataComponents.TRAITS.get(),
                List.of(ResourceLocation.fromNamespaceAndPath("forgeweave", "evolved")));
        assertTrue(recipe.upgrade(null, sword).isEmpty(),
                "vein mining is harvest-only, so the fusion path refuses a weapon the same way the "
                        + "Tool Station does");
    }

    /**
     * Issue #946's gate: fusion crafting only upgrades a tool already made of a fusion metal, and
     * only up to that metal's tier. Four rungs since issue #965 -- one per Draconic Evolution tech
     * level, where the draconium rung and the wyvern rung used to share level 1.
     */
    @Test
    void upgradeRefusesAToolThatIsNotEvolvedToTheRungsTier() {
        FusionUpgradeRecipe draconium = decode(fixture(50, "draconium", 2_000_000L));
        FusionUpgradeRecipe wyvern = decode(fixture(100, "wyvern", 8_000_000L));
        FusionUpgradeRecipe draconic = decode(fixture(175, "draconic", 32_000_000L));
        FusionUpgradeRecipe chaotic = decode(fixture(250, "chaotic", 128_000_000L));

        assertTrue(draconium.upgrade(null, evolvedTool(0)).isEmpty(),
                "a plain assembled tool carries no tier marker, so not even the entry rung takes it");
        assertTrue(draconium.upgrade(null, evolvedTool(1)).isPresent(),
                "a duskweld tool is evolving and the draconium rung asks for exactly that");
        assertTrue(wyvern.upgrade(null, evolvedTool(1)).isEmpty(),
                "the inert tier does not reach the wyvern rung");
        assertTrue(wyvern.upgrade(null, evolvedTool(2)).isPresent(),
                "an emberweld tool is evolved and clears the wyvern rung");
        assertTrue(draconic.upgrade(null, evolvedTool(2)).isEmpty(),
                "evolved does not reach the draconic rung");
        assertTrue(draconic.upgrade(null, evolvedTool(3)).isPresent(),
                "a starweld tool is evolved II and clears the draconic rung");
        assertTrue(chaotic.upgrade(null, evolvedTool(3)).isEmpty(),
                "evolved II does not reach the chaotic rung");
        assertTrue(chaotic.upgrade(null, evolvedTool(4)).isPresent(),
                "a voidweld tool is evolved III and clears every rung");
    }

    /**
     * The gate table read the other way round (issue #965): every tech level asks for its own rung,
     * and the roster's metals line up with it one for one.
     */
    @Test
    void everyTechLevelHasItsOwnRungAndItsOwnMetal() {
        assertEquals(List.of(1, 2, 3, 4), List.of(
                ForgeweaveDraconicCompat.requiredEvolved("draconium"),
                ForgeweaveDraconicCompat.requiredEvolved("wyvern"),
                ForgeweaveDraconicCompat.requiredEvolved("draconic"),
                ForgeweaveDraconicCompat.requiredEvolved("chaotic")));
        assertEquals(List.of("duskweld", "emberweld", "starweld", "voidweld"), List.of(
                ForgeweaveDraconicCompat.fusionMetal("draconium"),
                ForgeweaveDraconicCompat.fusionMetal("wyvern"),
                ForgeweaveDraconicCompat.fusionMetal("draconic"),
                ForgeweaveDraconicCompat.fusionMetal("chaotic")));
    }

    /**
     * The four {@code draconicevolution:fusion_crafting} rows that make the metals (issues #946 and
     * #965), plus the crafting recipe for the weldheart every one of them consumes.
     */
    @Test
    void everyFusionMetalRowMatchesItsRosterEntry() {
        for (ForgeweaveDraconicCompat.FusionMetal metal : ForgeweaveDraconicCompat.FUSION_METALS) {
            String path = "/data/forgeweave/recipe/compat/draconic/" + metal.material() + "_ingot.json";
            JsonObject json = read(path);

            assertEquals("draconicevolution:fusion_crafting", json.get("type").getAsString(), path);
            assertEquals(ForgeweaveDraconicCompat.CATALYST,
                    json.getAsJsonObject("catalyst").get("item").getAsString(),
                    metal.material() + " is fused onto the weldheart");
            assertEquals("forgeweave:" + metal.material() + "_ingot",
                    json.getAsJsonObject("result").get("id").getAsString(), path);
            assertEquals(metal.techLevel(), json.get("techLevel").getAsString(), path);
            assertEquals(metal.energy(), json.get("totalEnergy").getAsLong(), path);
            assertEquals(metal.ingredients().size(), json.getAsJsonArray("ingredients").size(), path);
            assertEquals("draconicevolution",
                    json.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("modid").getAsString(),
                    path + " must drop out of a Forgeweave-only datapack");
        }
    }

    @Test
    void theWeldheartHasACraftingRecipeOfItsOwn() {
        JsonObject json = read("/data/forgeweave/recipe/compat/draconic/weldheart.json");

        assertEquals("minecraft:crafting_shaped", json.get("type").getAsString());
        assertEquals(ForgeweaveDraconicCompat.CATALYST, json.getAsJsonObject("result").get("id").getAsString());
        assertEquals("c:ingots/draconium",
                json.getAsJsonObject("key").getAsJsonObject("D").get("tag").getAsString(),
                "the corners are what keeps this recipe out of a Draconic-free game's recipe book");
        assertEquals("draconicevolution",
                json.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("modid").getAsString());
    }

    /** The two smeltery-core promotion rows PR #939 shipped are gone (issue #946, work item 1). */
    @Test
    void noCorePromotionRowsAreShipped() {
        for (String core : List.of("end_core", "deep_core")) {
            assertNull(FusionUpgradeRecipeTest.class
                    .getResourceAsStream("/data/forgeweave/recipe/compat/draconic/" + core + ".json"),
                    core + " promotion was removed by the maintainer's 2026-09-03 directive");
        }
    }

    @Test
    void catalystIsATagOfForgeweaveToolsOnEveryShippedRow() {
        JsonObject json = read("/data/forgeweave/recipe/compat/draconic/haste_wyvern.json");
        assertEquals("forgeweave:fusion_upgradable",
                json.getAsJsonObject("catalyst").get("tag").getAsString());
        assertEquals("draconicevolution",
                json.getAsJsonArray("neoforge:conditions").get(0).getAsJsonObject().get("modid").getAsString(),
                "without the mod_loaded gate this row would fail to load on a Forgeweave-only install");
    }

    /**
     * Issues #953 and #965: the four Draconic core materials carry a tier marker at their own tier,
     * so a tool built out of a core is fusion upgradable without ever touching a fusion metal. This walks
     * the shipped material JSON rather than a hand-written trait list, so a core that quietly loses
     * its {@code evolved} entry fails here instead of in a save.
     */
    @Test
    void aToolMadeOfADraconicCoreClearsItsOwnTiersGate() {
        FusionUpgradeRecipe draconium = decode(fixture(50, "draconium", 2_000_000L));
        FusionUpgradeRecipe wyvern = decode(fixture(100, "wyvern", 8_000_000L));
        FusionUpgradeRecipe draconic = decode(fixture(175, "draconic", 32_000_000L));
        FusionUpgradeRecipe chaotic = decode(fixture(250, "chaotic", 128_000_000L));

        assertTrue(draconium.upgrade(null, coreTool("draconium_core")).isPresent(),
                "a draconium-core head is evolving and the entry rung asks for exactly that");
        assertTrue(wyvern.upgrade(null, coreTool("draconium_core")).isEmpty(),
                "the inert core does not reach the wyvern rung");
        assertTrue(wyvern.upgrade(null, coreTool("wyvern")).isPresent(),
                "a wyvern-core head is evolved and the wyvern rung asks for exactly that");
        assertTrue(draconic.upgrade(null, coreTool("wyvern")).isEmpty(),
                "a wyvern core does not reach the draconic rung");
        assertTrue(draconic.upgrade(null, coreTool("awakened")).isPresent(),
                "an awakened-core head is evolved II and clears the draconic rung");
        assertTrue(chaotic.upgrade(null, coreTool("chaotic")).isPresent(),
                "a chaotic-core head is evolved III and clears every rung");
    }

    /**
     * A pickaxe carrying whatever {@code traits.general} the shipped material JSON for {@code
     * material} lists -- the trait list an assembled tool made of that material ends up with.
     */
    private static ItemStack coreTool(String material) {
        JsonObject json = read("/data/forgeweave/forgeweave/material/" + material + ".json");
        List<ResourceLocation> traits = json.getAsJsonObject("traits").getAsJsonArray("general")
                .asList().stream().map(JsonElement::getAsString).map(ResourceLocation::parse).toList();
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        pickaxe.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        return pickaxe;
    }

    private static JsonObject read(String path) {
        try (InputStream in = FusionUpgradeRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing generated recipe: " + path + " -- run ./gradlew runData");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
