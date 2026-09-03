package dev.gkissel.forgeweave.data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.draconic.ForgeweaveDraconicCompat;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * The Draconic Evolution fusion recipes (issues #915 and #946, docs/SCOPE.md M8), both layers of
 * them, under {@code data/forgeweave/recipe/compat/draconic/}. Every row carries a
 * {@code neoforge:conditions} {@code mod_loaded} gate, so a Forgeweave-only datapack drops the lot
 * and the mod boots with no Draconic Evolution installed at all -- which is what
 * {@code runGameTestServer} proves on every build.
 *
 * <p><b>Layer 1</b> is plain {@code draconicevolution:fusion_crafting} rows, i.e. Draconic
 * Evolution's own recipe type with no Forgeweave code behind it. There are three, one per fusion
 * metal ({@link ForgeweaveDraconicCompat#FUSION_METALS}): a fusion craft is the only thing that
 * makes an emberweld, starweld or voidweld ingot, which is why those three metals have no alloy
 * table row and no Part Builder route. The weldheart every one of them puts in the crafting core is
 * a plain crafting-table recipe, written here too so the whole path lives in one file.
 *
 * <p>Issue #946 replaced what Layer 1 used to be. Until then it was two rows promoting a smeltery
 * core (Nether to End, End to Deep) on the grounds that those tiers each have a single route today;
 * the maintainer's 2026-09-03 directive removed them, because promoting Forgeweave's own blocks is
 * not what the parity target does with fusion crafting. The core tiers keep the pour-to-transform
 * route issue #845 gave them.
 *
 * <p>No recipe is emitted for the four Draconic-tier preset materials (draconium, awakened
 * draconium, wyvern, chaotic -- the Track A preset of issues #833-#837), which stay exactly as they
 * were and keep their ids. They are the raw tier the three fusion metals sit above: every one of
 * them is already reachable by melting Draconic Evolution's own ingots and cores, and the cores that
 * gate the top two are DE's own fusion recipes, which it ships itself. Adding Forgeweave rows there
 * would duplicate DE's ladder, not complete it.
 *
 * <p><b>Layer 2</b> is the {@code forgeweave:draconic_fusion_upgrade} ladder --
 * {@link ForgeweaveDraconicCompat#UPGRADE_LINES}, eight modifier lines by four Draconic tech levels
 * -- whose recipe class is {@code compat.draconic.FusionUpgradeRecipe}. Since #946 every rung also
 * asks the tool for the {@code evolved} trait at its tier, so the two layers are one path: make the
 * metal, build the tool, then upgrade it.
 *
 * <p>Written as JSON rather than through a {@code RecipeOutput} because both recipe types need types
 * this provider must not touch: Layer 1's serializer belongs to Draconic Evolution, and Layer 2's
 * only links when Draconic Evolution is present, while {@code runData} runs on a Forgeweave-only
 * classpath. The ids that can be checked at build time are: every Forgeweave item and block here is
 * a real registry object rather than a string, and every modifier id is verified against
 * {@link ForgeweaveModifiers#get} before it is written.
 */
public class ForgeweaveDraconicRecipeProvider implements DataProvider {

    private static final String FUSION_TYPE = "draconicevolution:fusion_crafting";

    private final PackOutput.PathProvider recipes;

    public ForgeweaveDraconicRecipeProvider(PackOutput output) {
        this.recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe");
    }

    @Override
    public String getName() {
        return "Forgeweave Draconic Evolution fusion recipes";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> written = new ArrayList<>();

        // Layer 1. Energies follow Draconic Evolution's own recipe datagen: 1,000,000 RF at wyvern is
        // what it charges for the Awakened Core, its other "promote a component one tier" craft, and
        // 32,000,000 at draconic is its draconic-tier equipment and capacitor cost.
        written.add(save(output, "weldheart", weldheart()));

        for (ForgeweaveDraconicCompat.FusionMetal metal : ForgeweaveDraconicCompat.FUSION_METALS) {
            written.add(save(output, metal.material() + "_ingot", fusion(
                    ForgeweaveDraconicCompat.CATALYST,
                    itemId(ForgeweaveItems.trackBAlloyIngot(metal.material()).get()),
                    metal.techLevel(), metal.energy(), metal.ingredients())));
        }

        // Layer 2.
        for (ForgeweaveDraconicCompat.Line line : ForgeweaveDraconicCompat.UPGRADE_LINES) {
            ResourceLocation modifier = ResourceLocation.parse(line.modifier());
            if (ForgeweaveModifiers.get(modifier) == null) {
                throw new IllegalStateException("no modifier registered as " + modifier
                        + " -- ForgeweaveDraconicCompat.UPGRADE_LINES names one that does not exist");
            }
            for (ForgeweaveDraconicCompat.Rung rung : line.rungs()) {
                JsonObject json = new JsonObject();
                json.addProperty("type",
                        Forgeweave.MODID + ":" + ForgeweaveDraconicCompat.UPGRADE_SERIALIZER_NAME);
                json.add("catalyst", ingredient("#" + ForgeweaveDraconicCompat.FUSION_UPGRADABLE.location()));
                json.addProperty("modifier", modifier.toString());
                json.addProperty("level", rung.level());
                String tier = ForgeweaveDraconicCompat.tierIngredient(rung.techLevel());
                json.add("ingredients", ingredients(List.of(tier, tier, line.reagent())));
                json.addProperty("totalEnergy", rung.energy());
                json.addProperty("techLevel", rung.techLevel());
                json.add("neoforge:conditions", conditions());
                written.add(save(output, modifier.getPath() + "_" + rung.techLevel(), json));
            }
        }

        return CompletableFuture.allOf(written.toArray(CompletableFuture[]::new));
    }

    /**
     * The weldheart's own crafting recipe (issue #946): four draconium ingots at the corners, four
     * eyes of ender on the edges, and one Forgeweave ingot cast in the middle -- the cast every
     * Forgeweave ingot already comes out of, sitting at the heart of the thing the three fusion
     * ingots come out of. A plain vanilla crafting table recipe, so the only thing gating it is the
     * draconium the corners ask for, and the {@code mod_loaded} condition every row here carries.
     */
    private static JsonObject weldheart() {
        JsonObject key = new JsonObject();
        key.add("D", ingredient("#c:ingots/draconium"));
        key.add("E", ingredient("minecraft:ender_eye"));
        key.add("C", ingredient(itemId(ForgeweaveItems.CAST_INGOT.get())));

        JsonArray pattern = new JsonArray();
        pattern.add("DED");
        pattern.add("ECE");
        pattern.add("DED");

        JsonObject result = new JsonObject();
        result.addProperty("id", ForgeweaveDraconicCompat.CATALYST);
        result.addProperty("count", 1);

        JsonObject json = new JsonObject();
        json.addProperty("type", "minecraft:crafting_shaped");
        json.addProperty("category", "misc");
        json.add("pattern", pattern);
        json.add("key", key);
        json.add("result", result);
        json.add("neoforge:conditions", conditions());
        return json;
    }

    /** One {@code draconicevolution:fusion_crafting} row producing a single {@code result} stack. */
    private static JsonObject fusion(String catalyst, String result, String techLevel, long energy,
            List<String> ingredients) {
        JsonObject json = new JsonObject();
        json.addProperty("type", FUSION_TYPE);
        json.add("catalyst", ingredient(catalyst));
        json.add("ingredients", ingredients(ingredients));
        JsonObject stack = new JsonObject();
        stack.addProperty("id", result);
        stack.addProperty("count", 1);
        json.add("result", stack);
        json.addProperty("techLevel", techLevel);
        json.addProperty("totalEnergy", energy);
        json.add("neoforge:conditions", conditions());
        return json;
    }

    /** {@code "#namespace:path"} reads as a tag ingredient, anything else as an item ingredient. */
    private static JsonObject ingredient(String id) {
        JsonObject json = new JsonObject();
        json.addProperty(id.startsWith("#") ? "tag" : "item", id.startsWith("#") ? id.substring(1) : id);
        return json;
    }

    /** Draconic Evolution's injector list: one entry per injector, each consumed by the craft. */
    private static JsonArray ingredients(List<String> ids) {
        JsonArray array = new JsonArray();
        for (String id : ids) {
            JsonObject entry = new JsonObject();
            entry.add("ingredient", ingredient(id));
            entry.addProperty("consume", true);
            array.add(entry);
        }
        return array;
    }

    private static JsonArray conditions() {
        JsonObject modLoaded = new JsonObject();
        modLoaded.addProperty("type", "neoforge:mod_loaded");
        modLoaded.addProperty("modid", ForgeweaveDraconicCompat.MODID);
        JsonArray array = new JsonArray();
        array.add(modLoaded);
        return array;
    }

    private static String itemId(ItemLike item) {
        return BuiltInRegistries.ITEM.getKey(item.asItem()).toString();
    }

    private CompletableFuture<?> save(CachedOutput output, String name, JsonObject json) {
        return DataProvider.saveStable(output, json,
                recipes.json(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "compat/draconic/" + name)));
    }
}
