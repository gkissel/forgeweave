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
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.compat.draconic.ForgeweaveDraconicCompat;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * The Draconic Evolution fusion recipes (issue #915, docs/SCOPE.md M8), both layers of them, under
 * {@code data/forgeweave/recipe/compat/draconic/}. Every row carries a {@code neoforge:conditions}
 * {@code mod_loaded} gate, so a Forgeweave-only datapack drops the lot and the mod boots with no
 * Draconic Evolution installed at all -- which is what {@code runGameTestServer} proves on every
 * build.
 *
 * <p><b>Layer 1</b> is plain {@code draconicevolution:fusion_crafting} rows, i.e. Draconic
 * Evolution's own recipe type with no Forgeweave code behind it. Both of them promote a smeltery
 * core, and both exist because those two tiers have exactly one route each today (issue #845): the
 * End Core needs 1000 mB of molten dragon breath poured over a Nether Core, and the Deep Core needs
 * 2000 mB of deep blood, which only melting a Warden produces. Neither is craftable at all
 * ({@code ForgeweaveBlocks.END_CORE}'s comment). A player who has already built a fusion multiblock
 * has demonstrably cleared harder content than either gate, so an alternative route at the matching
 * Draconic tier costs nothing in progression terms and removes a hard blocker from a modpack that
 * happens to ship both mods. The recipes still ask for the thematic item of the tier they unlock --
 * dragon breath for the End Core, an echo shard for the Deep Core -- so the route reads as the same
 * step paid for with Draconic materials rather than as a way around the content.
 *
 * <p>No recipe is emitted for the four Draconic-tier materials themselves (draconium, awakened
 * draconium, wyvern, chaotic -- the Track A preset of issues #833-#837). Every one of them is
 * already reachable: Forgeweave melts Draconic Evolution's own ingots and cores and casts parts from
 * the resulting metal, and the cores that gate the top two are Draconic Evolution's own fusion
 * recipes, which it ships itself. Adding Forgeweave rows there would duplicate DE's ladder, not
 * complete it.
 *
 * <p><b>Layer 2</b> is the {@code forgeweave:draconic_fusion_upgrade} ladder --
 * {@link ForgeweaveDraconicCompat#UPGRADE_LINES}, eight modifier lines by four Draconic tech levels
 * -- whose recipe class is {@code compat.draconic.FusionUpgradeRecipe}.
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
        written.add(save(output, "end_core", fusion(
                itemId(ForgeweaveBlocks.NETHER_CORE.get()),
                itemId(ForgeweaveBlocks.END_CORE.get()),
                "wyvern", 1_000_000L,
                List.of("draconicevolution:wyvern_core", "minecraft:dragon_breath",
                        "#c:ingots/draconium", "#c:ingots/draconium"))));
        written.add(save(output, "deep_core", fusion(
                itemId(ForgeweaveBlocks.END_CORE.get()),
                itemId(ForgeweaveBlocks.DEEP_CORE.get()),
                "draconic", 32_000_000L,
                List.of("draconicevolution:awakened_core", "minecraft:echo_shard",
                        "#c:ingots/draconium_awakened", "#c:ingots/draconium_awakened"))));

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
