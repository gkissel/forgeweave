package dev.gkissel.forgeweave.data;

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
import dev.gkissel.forgeweave.compat.mekanism.ForgeweaveMekanismCompat;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The one Mekanism recipe Forgeweave ships (issue #993, docs/SCOPE.md M8, D-M8-13): an
 * {@code atomic_matter_alloy} ingot out of Mekanism's Antiprotonic Nucleosynthesizer, from its own
 * atomic alloy plus antimatter. Written to
 * {@code data/forgeweave/recipe/compat/mekanism/atomic_matter_alloy_ingot.json} with a
 * {@code neoforge:conditions} {@code mod_loaded} gate, so a Forgeweave-only datapack drops it and the
 * metal becomes unobtainable rather than the recipe becoming a broken row. That is the whole of "only
 * makeable in the nucleosynthesizer": there is no alloy table row, no Part Builder route and no
 * crafting recipe for the ingot anywhere else in the tree.
 *
 * <p>A plain {@code DataProvider} rather than a {@code RecipeProvider}, for the reason
 * {@link ForgeweaveDraconicRecipeProvider} gives: the serializer belongs to Mekanism, which is
 * {@code compileOnly}, so there is no {@code Recipe} object {@code runData} could build.
 *
 * <h2>Verified against the published jar (Mekanism 1.21.1-10.7.19.85)</h2>
 *
 * <p>The field names are read off Mekanism's own shipped rows, not from memory.
 * {@code data/mekanism/recipe/nucleosynthesizing/heart_of_the_sea.json} is
 * {@code {"type":"mekanism:nucleosynthesizing","chemical_input":{"amount":5,"chemical":"mekanism:antimatter"},}
 * {@code "duration":1250,"item_input":{"count":1,"tag":"c:nether_stars"},}
 * {@code "output":{"count":1,"id":"minecraft:heart_of_the_sea"},"per_tick_usage":false}} -- so
 * {@code item_input} takes {@code count} plus either {@code item} or {@code tag},
 * {@code chemical_input} takes {@code amount} plus {@code chemical}, and {@code per_tick_usage} says
 * whether the chemical amount is per tick or for the whole craft. Mekanism's own hardest row is 5 mB
 * of antimatter over 1250 ticks; this one is deliberately above it, and both numbers are config.
 */
public class ForgeweaveMekanismRecipeProvider implements DataProvider {

    private static final String NUCLEOSYNTHESIZING_TYPE = ForgeweaveMekanismCompat.MODID + ":nucleosynthesizing";

    private final PackOutput.PathProvider recipes;

    public ForgeweaveMekanismRecipeProvider(PackOutput output) {
        this.recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe");
    }

    @Override
    public String getName() {
        return "Forgeweave Mekanism nucleosynthesizing recipes";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        return save(output, "atomic_matter_alloy_ingot", nucleosynthesizing());
    }

    /** The row itself, built from the configured amounts. */
    public static JsonObject nucleosynthesizing() {
        JsonObject json = new JsonObject();
        json.addProperty("type", NUCLEOSYNTHESIZING_TYPE);

        JsonObject itemInput = new JsonObject();
        itemInput.addProperty("count", ForgeweaveMekanismCompat.nucleosynthesizingAlloyCount());
        itemInput.addProperty("item", ForgeweaveMekanismCompat.ATOMIC_ALLOY_ITEM);
        json.add("item_input", itemInput);

        JsonObject chemicalInput = new JsonObject();
        chemicalInput.addProperty("amount", ForgeweaveMekanismCompat.nucleosynthesizingAntimatterAmount());
        chemicalInput.addProperty("chemical", ForgeweaveMekanismCompat.ANTIMATTER_CHEMICAL);
        json.add("chemical_input", chemicalInput);

        json.addProperty("duration", ForgeweaveMekanismCompat.nucleosynthesizingDuration());
        json.addProperty("per_tick_usage", false);

        JsonObject result = new JsonObject();
        result.addProperty("count", 1);
        result.addProperty("id", itemId(ForgeweaveItems.trackBAlloyIngot("atomic_matter_alloy").get()));
        json.add("output", result);

        json.add("neoforge:conditions", conditions());
        return json;
    }

    private static JsonArray conditions() {
        JsonObject modLoaded = new JsonObject();
        modLoaded.addProperty("type", "neoforge:mod_loaded");
        modLoaded.addProperty("modid", ForgeweaveMekanismCompat.MODID);
        JsonArray array = new JsonArray();
        array.add(modLoaded);
        return array;
    }

    private static String itemId(ItemLike item) {
        return BuiltInRegistries.ITEM.getKey(item.asItem()).toString();
    }

    private CompletableFuture<?> save(CachedOutput output, String name, JsonObject json) {
        return DataProvider.saveStable(output, json,
                recipes.json(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "compat/mekanism/" + name)));
    }
}
