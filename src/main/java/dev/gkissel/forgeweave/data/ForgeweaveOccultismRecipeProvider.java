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

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.occultism.ForgeweaveOccultismCompat;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * The Occultism rows (issue #997, docs/SCOPE.md M8, D-M8-18), all three kinds, under
 * {@code data/forgeweave/recipe/compat/occultism/}. Every row carries a {@code neoforge:conditions}
 * {@code mod_loaded} gate, so a Forgeweave-only datapack drops the lot and the mod boots with no
 * Occultism installed at all -- which is what {@code runGameTestServer} proves on every build. That
 * gate is load-bearing rather than tidy: all three are Occultism's own recipe types, so without the
 * condition an install without it would fail to load the file rather than skip it.
 *
 * <p><b>Crushing.</b> Two {@code occultism:crushing} rows per Track B ore, 22 in all: the ore block
 * tag and the ore's raw form, each grinding to the metal's own ingot. {@code min_tier} comes from
 * {@link ForgeweaveOccultismCompat#crusherTier}, so a foliot crusher cannot chew through resonite.
 * {@code ignore_crushing_multiplier} is set on the raw row and not on the ore row, which is
 * Occultism's own convention: a spirit's output multiplier is what makes crushing an ore block worth
 * doing, and leaving it on a raw-to-ingot row would be a duplication loop, since the smeltery already
 * turns an ingot back into a raw's worth of metal.
 *
 * <p>Fulmenite is the one ore with a single row rather than two: issue #929 gave it a crystal drop
 * instead of a raw item (the brimspar shape), so it has {@code c:gems/fulmenite} where the other ten
 * have {@code c:raw_materials/<id>}, and that crystal is what the second row grinds.
 *
 * <p><b>Miner.</b> One {@code occultism:miner} row per Track B ore, all eleven in Occultism's general
 * {@code #occultism:miners/ores} pool -- the one every miner spirit item matches -- weighted by
 * {@link ForgeweaveOccultismCompat#minerWeight}. The tier gate here is the weight rather than the
 * pool: an ore in a narrower pool would be unreachable to a foliot outright, and Track B is meant to
 * be climbed slowly, not locked behind a spirit rank twice over (the crushing rows already do that
 * half).
 *
 * <p><b>Rituals.</b> One {@code occultism:ritual} row per
 * {@link ForgeweaveOccultismCompat#RITUALS} entry, each naming the
 * {@code occultism:ritual_factories} entry {@code compat.occultism.SpiritBindingRitual} registers.
 * The two stacks Occultism's JEI category draws off the recipe -- {@code ritual_dummy}, the row's own
 * icon, and {@code result} -- are both a Forgeweave pickaxe carrying the modifier entry the ritual
 * grants, so the category shows the modifier by name in its tooltip. Neither is what the ritual
 * actually produces: the real output is the tool the player laid on the golden bowl, which is a
 * function of that tool and cannot be a literal in a recipe file. Occultism does the same thing with
 * its own {@code occultism:repair} rows and their {@code occultism:repair_icon}.
 *
 * <p>Written as JSON rather than through a {@code RecipeOutput} for {@code
 * ForgeweaveDraconicRecipeProvider}'s reason: all three serializers belong to Occultism, and
 * {@code runData} runs on a Forgeweave-only classpath. The ids that can be checked at build time
 * are: every Forgeweave item here is a real registry object rather than a string, every ore comes
 * off {@link TrackBOre#ALL}, and every modifier id is verified against {@link ForgeweaveModifiers#get}
 * before it is written.
 */
public class ForgeweaveOccultismRecipeProvider implements DataProvider {

    private static final String CRUSHING_TYPE = ForgeweaveOccultismCompat.MODID + ":crushing";
    private static final String MINER_TYPE = ForgeweaveOccultismCompat.MODID + ":miner";
    private static final String RITUAL_TYPE = ForgeweaveOccultismCompat.MODID + ":ritual";

    /**
     * The miner pool every Track B row joins -- Occultism's general ore tag, which each of its five
     * miner spirit items is a member of.
     */
    private static final String MINER_POOL = "#" + ForgeweaveOccultismCompat.MODID + ":miners/ores";

    /** Occultism's own default is 200 ticks; an ore block is worth a slower grind than a raw item. */
    private static final int ORE_CRUSHING_TICKS = 400;
    private static final int RAW_CRUSHING_TICKS = 200;

    /** How long a binding ritual runs, in seconds -- Occultism's own field, whose default is 30. */
    private static final int RITUAL_SECONDS = 120;

    private final PackOutput.PathProvider recipes;

    public ForgeweaveOccultismRecipeProvider(PackOutput output) {
        this.recipes = output.createPathProvider(PackOutput.Target.DATA_PACK, "recipe");
    }

    @Override
    public String getName() {
        return "Forgeweave Occultism crushing, miner and ritual recipes";
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        List<CompletableFuture<?>> written = new ArrayList<>();

        for (TrackBOre ore : TrackBOre.ALL) {
            int tier = ForgeweaveOccultismCompat.crusherTier(ore.tier());
            String ingot = "c:ingots/" + ore.id();

            written.add(save(output, "crushing/" + ore.id() + "_from_ore",
                    crushing("#c:ores/" + ore.id(), ingot, 2, tier, ORE_CRUSHING_TICKS, false)));
            // #929: fulmenite drops a crystal, not a raw item, so its second row grinds the crystal.
            String raw = ore.dropsCrystal() ? "#c:gems/" + ore.id() : "#c:raw_materials/" + ore.id();
            written.add(save(output, "crushing/" + ore.id() + "_from_raw",
                    crushing(raw, ingot, 1, tier, RAW_CRUSHING_TICKS, true)));

            written.add(save(output, "miner/" + ore.id(),
                    miner("#c:ores/" + ore.id(), ForgeweaveOccultismCompat.minerWeight(ore.tier()))));
        }

        for (ForgeweaveOccultismCompat.Ritual ritual : ForgeweaveOccultismCompat.RITUALS) {
            ResourceLocation modifier = ResourceLocation.parse(ritual.modifier());
            if (ForgeweaveModifiers.get(modifier) == null) {
                throw new IllegalStateException("no modifier registered as " + modifier
                        + " -- ForgeweaveOccultismCompat.RITUALS names one that does not exist");
            }
            written.add(save(output, "ritual/" + ritual.name(), ritual(ritual, modifier)));
        }

        return CompletableFuture.allOf(written.toArray(CompletableFuture[]::new));
    }

    /**
     * One {@code occultism:crushing} row. {@code result} is Occultism's registry-dispatched
     * {@code RecipeResult}, so the {@code type} field inside it is required rather than decorative --
     * {@code occultism:tag} resolves the tag through AlmostUnified where present and otherwise takes
     * the tag's first item, which is the Forgeweave ingot either way.
     */
    private static JsonObject crushing(String ingredient, String resultTag, int count, int minTier,
            int crushingTime, boolean ignoreMultiplier) {
        JsonObject result = new JsonObject();
        result.addProperty("type", ForgeweaveOccultismCompat.MODID + ":tag");
        result.addProperty("tag", resultTag);
        result.addProperty("count", count);

        JsonObject json = new JsonObject();
        json.addProperty("type", CRUSHING_TYPE);
        json.add("ingredient", ingredient(ingredient));
        json.add("result", result);
        json.addProperty("min_tier", minTier);
        json.addProperty("crushing_time", crushingTime);
        json.addProperty("ignore_crushing_multiplier", ignoreMultiplier);
        json.add("neoforge:conditions", conditions());
        return json;
    }

    /**
     * One {@code occultism:miner} row. {@code weight} sits inside the result rather than at the top
     * level, which is where Occultism's {@code WeightedRecipeResult} codec reads it.
     */
    private static JsonObject miner(String oreTag, int weight) {
        JsonObject result = new JsonObject();
        result.addProperty("type", ForgeweaveOccultismCompat.MODID + ":weighted_tag");
        result.addProperty("tag", oreTag.substring(1));
        result.addProperty("count", 1);
        result.addProperty("weight", weight);

        JsonObject json = new JsonObject();
        json.addProperty("type", MINER_TYPE);
        json.add("ingredient", ingredient(MINER_POOL));
        json.add("result", result);
        json.add("neoforge:conditions", conditions());
        return json;
    }

    /** One {@code occultism:ritual} row naming a {@code forgeweave:bind_*} ritual factory. */
    private static JsonObject ritual(ForgeweaveOccultismCompat.Ritual ritual, ResourceLocation modifier) {
        JsonArray ingredients = new JsonArray();
        for (String item : ritual.ingredients()) {
            ingredients.add(ingredient(item));
        }

        JsonObject json = new JsonObject();
        json.addProperty("type", RITUAL_TYPE);
        json.addProperty("ritual_type", Forgeweave.MODID + ":" + ritual.factoryName());
        json.addProperty("pentacle_id", ritual.pentacle());
        json.add("activation_item", ingredient("#" + ForgeweaveOccultismCompat.RITUAL_BINDABLE.location()));
        json.add("ingredients", ingredients);
        json.addProperty("duration", RITUAL_SECONDS);
        json.add("ritual_dummy", display(modifier, ritual.level()));
        json.add("result", display(modifier, ritual.level()));
        json.add("neoforge:conditions", conditions());
        return json;
    }

    /**
     * The stack Occultism's JEI ritual category draws: a Forgeweave pickaxe carrying the modifier
     * entry the ritual grants, so the slot's tooltip names the modifier. A representative rather
     * than the real output -- see the class javadoc.
     */
    private static JsonObject display(ResourceLocation modifier, int level) {
        JsonObject entry = new JsonObject();
        entry.addProperty("id", modifier.toString());
        entry.addProperty("level", level);
        JsonArray entries = new JsonArray();
        entries.add(entry);

        JsonObject components = new JsonObject();
        components.add(Forgeweave.MODID + ":modifiers", entries);

        JsonObject stack = new JsonObject();
        stack.addProperty("id", itemId());
        stack.addProperty("count", 1);
        stack.add("components", components);
        return stack;
    }

    /** {@code "#namespace:path"} reads as a tag ingredient, anything else as an item ingredient. */
    private static JsonObject ingredient(String id) {
        JsonObject json = new JsonObject();
        json.addProperty(id.startsWith("#") ? "tag" : "item", id.startsWith("#") ? id.substring(1) : id);
        return json;
    }

    private static JsonArray conditions() {
        JsonObject modLoaded = new JsonObject();
        modLoaded.addProperty("type", "neoforge:mod_loaded");
        modLoaded.addProperty("modid", ForgeweaveOccultismCompat.MODID);
        JsonArray array = new JsonArray();
        array.add(modLoaded);
        return array;
    }

    private static String itemId() {
        return BuiltInRegistries.ITEM.getKey(ForgeweaveItems.TOOL_PICKAXE.get()).toString();
    }

    private CompletableFuture<?> save(CachedOutput output, String name, JsonObject json) {
        return DataProvider.saveStable(output, json,
                recipes.json(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "compat/occultism/" + name)));
    }
}
