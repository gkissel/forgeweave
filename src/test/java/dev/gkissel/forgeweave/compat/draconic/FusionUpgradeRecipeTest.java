package dev.gkissel.forgeweave.compat.draconic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());

        Optional<ItemStack> upgraded = recipe.upgrade(null, pickaxe);

        assertTrue(upgraded.isPresent(), "a bare assembled pickaxe has haste to gain");
        List<ModifierEntry> entries = ForgeweaveModifiers.of(upgraded.get());
        assertEquals(List.of(new ModifierEntry(HASTE, 100)), entries);
        assertTrue(ForgeweaveModifiers.of(pickaxe).isEmpty(), "the catalyst stack itself is left alone");
    }

    @Test
    void upgradeRefusesAToolAlreadyAtOrAboveTheTiersLevel() {
        FusionUpgradeRecipe wyvern = decode(fixture(100, "wyvern", 8_000_000L));
        ItemStack pickaxe = wyvern.upgrade(null, new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get())).orElseThrow();

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

        assertTrue(recipe.upgrade(null, new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get())).isPresent(),
                "vein mining is a harvest modifier and a pickaxe harvests");
        assertTrue(recipe.upgrade(null, new ItemStack(ForgeweaveItems.TOOL_BROADSWORD.get())).isEmpty(),
                "vein mining is harvest-only, so the fusion path refuses a weapon the same way the "
                        + "Tool Station does");
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

    private static JsonObject read(String path) {
        try (InputStream in = FusionUpgradeRecipeTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing generated recipe: " + path + " -- run ./gradlew runData");
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
