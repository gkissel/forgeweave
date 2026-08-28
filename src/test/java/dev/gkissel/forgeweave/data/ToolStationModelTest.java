package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Guards issue #755: the Tool Station's block model must keep every non-top face on oak planks (or
 * the shared {@code table_side} trim), matching upstream 1.12's {@code models/block/toolstation.json}
 * ({@code bottom}/{@code legBottom} = {@code minecraft:blocks/planks_oak}, {@code side}/{@code leg} =
 * {@code tconstruct:blocks/table_side}). The playtest defect itself was the crafting recipe copying
 * the crafting table ingredient onto the TEXTURE component (see
 * {@code RetexturedTableGameTests#toolStationRecipeDoesNotCopyTheCraftingTableAsAWood}), but this
 * model JSON is the other half of the contract: if a texture slot here ever points somewhere other
 * than oak planks / the shared table trim, a freshly placed (untextured) station renders wrong
 * regardless of what the recipe does.
 *
 * <p>Plain filesystem scan of the shipped model JSON, same approach as {@link ChestModelTest}.
 */
class ToolStationModelTest {

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static JsonObject readJson(Path path) throws IOException {
        assertTrue(Files.isRegularFile(path), "expected model JSON at " + path);
        return JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everyNonTopFaceIsOakPlanksOrTheSharedTableTrim() throws IOException {
        JsonObject model = readJson(projectRoot()
                .resolve("src/main/resources/assets/forgeweave/models/block/tool_station.json"));
        JsonObject textures = model.getAsJsonObject("textures");

        for (String oakSlot : new String[] {"texture", "bottom", "legBottom"}) {
            assertEquals("minecraft:block/oak_planks", textures.get(oakSlot).getAsString(),
                    "tool_station.json's \"" + oakSlot + "\" slot must be oak planks (upstream toolstation.json), "
                            + "not the vanilla crafting table -- a station's own bottom/leg faces must never render "
                            + "with a different block's texture (issue #755)");
        }

        assertEquals("forgeweave:derived/block/part_builder_side", textures.get("side").getAsString(),
                "tool_station.json's \"side\" slot must be the shared table-trim texture "
                        + "(upstream's table_side.png is reused by every station table)");

        assertTrue(Files.isRegularFile(projectRoot().resolve("src/main/resources/assets/forgeweave/textures")
                        .resolve(textures.get("side").getAsString().replace("forgeweave:", "") + ".png")),
                "tool_station.json's \"side\" slot points at a texture file that does not exist");
    }
}
