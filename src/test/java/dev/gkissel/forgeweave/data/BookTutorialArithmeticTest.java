package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * The numbers the guide book's tutorial pages do sums with, checked against the shipped recipe
 * JSON. A recipe change that leaves the book's arithmetic behind fails here instead of in a
 * playtest that follows the page without JEI.
 */
class BookTutorialArithmeticTest {

    private static final String LANG = "src/generated/resources/assets/forgeweave/lang/en_us.json";
    private static final String RECIPES = "src/generated/resources/data/forgeweave/recipe/";
    private static final String ALLOYS = "src/main/resources/data/forgeweave/forgeweave/alloy_recipe/";
    private static final String SEARED_BRICK = "forgeweave:seared_brick";

    /** One plain seared block under the 1x1 interior, plus 8 wall positions less the core, tank and drain. */
    private static final int PLAIN_BLOCKS = 1 + 8 - 3;

    /**
     * Playtest checklist 1.0.0, line 96, "Grout and Seared Bricks": does the 56-brick count add up?
     * It does. The page prices each smeltery block in bricks, and every price matches the brick
     * count in its shipped crafting recipe: 4 for a block of seared bricks, 8 for a tank (plus a
     * glass block), 8 for a core, 6 for a drain, 3 for a faucet, 5 for three channels and 7 for a
     * casting table or basin. The smallest smeltery is 6 plain blocks plus a core, a tank and a
     * drain, {@code 6 * 4 + 8 + 8 + 6 = 46}; a faucet and a casting table bring it to 56. Grout
     * crafts 2 at a time and bakes 1 into 1 in 200 ticks, so 56 bricks are 28 crafts and 10
     * seconds a brick, also as the page says.
     */
    @Test
    void theGroutPagesBrickCountMatchesTheShippedRecipes() throws IOException {
        String text = lang().get("book.forgeweave.smeltery.grout.text").getAsString();
        Map<String, Integer> pagePrices = new LinkedHashMap<>();
        pagePrices.put("seared_bricks", 4);
        pagePrices.put("seared_tank", 8);
        pagePrices.put("standard_core", 8);
        pagePrices.put("seared_drain", 6);
        pagePrices.put("faucet", 3);
        pagePrices.put("seared_channel", 5);
        pagePrices.put("casting_table", 7);
        pagePrices.put("casting_basin", 7);

        pagePrices.forEach((recipe, price) -> assertEquals(price, bricksIn(recipe),
                "the grout page prices " + recipe + " at " + price + " bricks"));
        assertEquals(3, resultCount(recipe("seared_channel")),
                "the page says five bricks make three channels");
        assertTrue(recipe("seared_tank").getAsJsonObject("key").toString().contains("c:glass_blocks"),
                "the page says the tank takes a glass block");

        int smallest = PLAIN_BLOCKS * bricksIn("seared_bricks") + bricksIn("standard_core")
                + bricksIn("seared_tank") + bricksIn("seared_drain");
        int working = smallest + bricksIn("faucet") + bricksIn("casting_table");
        assertEquals(46, smallest, "the smallest smeltery's brick cost");
        assertEquals(56, working, "the working first smeltery's brick cost");
        assertTrue(text.contains("That is " + smallest + " bricks"), "the page's own sum: " + text);
        assertTrue(text.contains("Call it " + working + " seared bricks"), "the page's own total: " + text);

        int groutPerCraft = resultCount(recipe("grout"));
        JsonObject smelting = recipe("seared_brick_from_smelting");
        assertEquals(1, resultCount(smelting), "one grout bakes into one brick");
        // 26.2 writes cookingtime only when it differs from smelting's default of 200.
        int cookingTime = smelting.has("cookingtime") ? smelting.get("cookingtime").getAsInt() : 200;
        assertEquals(200, cookingTime, "the page says ten seconds a brick");
        assertTrue(text.contains(working / groutPerCraft + " grout crafts"),
                "56 bricks at " + groutPerCraft + " grout a craft: " + text);
    }

    /**
     * Playtest checklist 1.0.0, line 97: one "The Long Chains" chain checked against the recipes,
     * recipe by recipe. This checks both chains the line names. Every sentence of the shape
     * "Riftalloy is 144 murkiron, 144 nightshale and 144 voltcinder, out 216" must match one
     * shipped {@code alloy_recipe} for that alloy, input for input and amount for amount. All eight
     * steps of the truesteel and stormalloy chains have to be among the sentences read, so a
     * rewording that the pattern stops matching fails instead of passing on nothing.
     */
    @Test
    void theLongChainsPageMatchesTheShippedAlloyRecipes() throws IOException {
        String text = lang().get("book.forgeweave.alloys.chains.text").getAsString();
        Map<String, Map<Map<String, Integer>, Integer>> recipes = alloyRecipesByResult();
        Matcher sentence = Pattern.compile("([A-Z][a-z]+) is ((?:\\d+ [a-z]+(?:, | and )?)+), out (\\d+)")
                .matcher(text);
        Pattern input = Pattern.compile("(\\d+) ([a-z]+)");

        List<String> checked = new ArrayList<>();
        while (sentence.find()) {
            String alloy = sentence.group(1).toLowerCase();
            Map<String, Integer> stated = new HashMap<>();
            Matcher inputs = input.matcher(sentence.group(2));
            while (inputs.find()) {
                stated.put(inputs.group(2), Integer.parseInt(inputs.group(1)));
            }
            Map<Map<String, Integer>, Integer> shipped = recipes.getOrDefault(alloy, Map.of());
            assertTrue(shipped.containsKey(stated),
                    "the page says " + sentence.group() + ", but the shipped " + alloy + " recipes are " + shipped);
            assertEquals(Integer.parseInt(sentence.group(3)), shipped.get(stated),
                    "the output the page gives for " + alloy);
            checked.add(alloy);
        }
        assertTrue(checked.containsAll(List.of("riftalloy", "glowveil", "sunsteel", "truesteel",
                        "quakestone", "shardline", "faultsteel", "stormalloy")),
                "expected every step of both chains to be read off the page, read " + checked);
    }

    private static int bricksIn(String recipe) {
        JsonObject json = recipe(recipe);
        StringBuilder brickKeys = new StringBuilder();
        for (Map.Entry<String, JsonElement> key : json.getAsJsonObject("key").entrySet()) {
            JsonElement value = key.getValue();
            if (value.isJsonPrimitive() && value.getAsString().equals(SEARED_BRICK)) {
                brickKeys.append(key.getKey());
            }
        }
        int bricks = 0;
        for (JsonElement row : json.getAsJsonArray("pattern")) {
            for (char c : row.getAsString().toCharArray()) {
                if (brickKeys.indexOf(String.valueOf(c)) >= 0) {
                    bricks++;
                }
            }
        }
        return bricks;
    }

    /** A recipe's result count; 26.x datagen leaves out a count of 1, the codec's default. */
    private static int resultCount(JsonObject recipe) {
        JsonElement count = recipe.getAsJsonObject("result").get("count");
        return count == null ? 1 : count.getAsInt();
    }

    private static JsonObject recipe(String name) {
        return read(LocalizationAuditTest.projectRoot().resolve(RECIPES + name + ".json"));
    }

    /** Every shipped alloy recipe as inputs to output amount, keyed by the result's material name; an alloy can have more than one. */
    private static Map<String, Map<Map<String, Integer>, Integer>> alloyRecipesByResult() throws IOException {
        Map<String, Map<Map<String, Integer>, Integer>> byResult = new HashMap<>();
        for (JsonObject recipe : alloyRecipes()) {
            JsonObject result = recipe.getAsJsonObject("result");
            byResult.computeIfAbsent(material(result), k -> new HashMap<>())
                    .put(inputs(recipe), result.get("amount").getAsInt());
        }
        return byResult;
    }

    private static Map<String, Integer> inputs(JsonObject recipe) {
        Map<String, Integer> inputs = new HashMap<>();
        for (JsonElement input : recipe.getAsJsonArray("inputs")) {
            inputs.put(material(input.getAsJsonObject()), input.getAsJsonObject().get("amount").getAsInt());
        }
        return inputs;
    }

    private static List<JsonObject> alloyRecipes() throws IOException {
        try (Stream<Path> files = Files.list(LocalizationAuditTest.projectRoot().resolve(ALLOYS))) {
            return files.filter(path -> path.toString().endsWith(".json")).map(BookTutorialArithmeticTest::read).toList();
        }
    }

    /** {@code forgeweave:molten_murkiron} to {@code murkiron}, and a fluid without the prefix to its path. */
    private static String material(JsonObject fluidAmount) {
        String path = fluidAmount.get("fluid").getAsString().replaceFirst("^[a-z_]+:", "");
        return path.startsWith("molten_") ? path.substring("molten_".length()) : path;
    }

    private static JsonObject lang() {
        return read(LocalizationAuditTest.projectRoot().resolve(LANG));
    }

    private static JsonObject read(Path path) {
        try {
            return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
