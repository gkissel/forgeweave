package dev.gkissel.forgeweave.ponder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import net.createmod.ponder.api.registration.MultiSceneBuilder;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.createmod.ponder.api.registration.StoryBoardEntry;
import net.createmod.ponder.api.scene.PonderStoryBoard;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.Forgeweave;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins issue #664's Ponder wiring without a client: the plugin registers the smeltery assembly
 * scene on the standard core's item (the hold-W affordance lives on the component an entry is
 * registered against), pointing at a schematic that actually ships in the jar. Scene playback is
 * client-only and cannot run here; {@code PonderSchematicGameTests} proves the schematic's
 * structure is a smeltery the real scan accepts, and this test proves the registration reaches
 * Ponder with the right coordinates.
 */
class PonderSceneWiringTest {

    private record RegisteredScene(ResourceLocation component, ResourceLocation schematic, PonderStoryBoard board) {}

    /** Ponder's real helper drags in client classes; the interface itself is client-free. */
    private static final class RecordingHelper implements PonderSceneRegistrationHelper<ResourceLocation> {
        private final List<RegisteredScene> scenes = new ArrayList<>();

        @Override
        public <S> PonderSceneRegistrationHelper<S> withKeyFunction(Function<S, ResourceLocation> keyGen) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoryBoardEntry addStoryBoard(ResourceLocation component, ResourceLocation schematicLocation,
                PonderStoryBoard storyBoard, ResourceLocation... tags) {
            scenes.add(new RegisteredScene(component, schematicLocation, storyBoard));
            return null; // the plugin never chains on the returned entry
        }

        @Override
        public StoryBoardEntry addStoryBoard(ResourceLocation component, String schematicPath,
                PonderStoryBoard storyBoard, ResourceLocation... tags) {
            return addStoryBoard(component, asLocation(schematicPath), storyBoard, tags);
        }

        @Override
        public MultiSceneBuilder forComponents(ResourceLocation... components) {
            throw new UnsupportedOperationException();
        }

        @Override
        public MultiSceneBuilder forComponents(Iterable<? extends ResourceLocation> components) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ResourceLocation asLocation(String path) {
            return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
        }
    }

    @Test
    void pluginBelongsToForgeweave() {
        assertEquals(Forgeweave.MODID, new ForgeweavePonderPlugin().getModId());
    }

    @Test
    void smelteryAssemblySceneIsRegisteredOnTheStandardCoreItem() {
        RecordingHelper helper = new RecordingHelper();
        new ForgeweavePonderPlugin().registerScenes(helper);

        assertEquals(5, helper.scenes.size(),
                "#664's smeltery scene, #700's sizes and casting scenes (casting on faucet and channel), #682's armor scene");
        RegisteredScene scene = helper.scenes.get(0);
        assertEquals(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "standard_core"), scene.component(),
                "the scene is registered on the smeltery controller item");
        assertEquals(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "smeltery"), scene.schematic());
        assertNotNull(scene.board());
    }

    /**
     * M4-7 (issue #682, docs/SCOPE.md D21): the armor assembly scene, registered on the Tool Station
     * item -- the block the scene plays around -- so the hold-W affordance sits where a player who
     * has the parts and is wondering where they go will look.
     */
    @Test
    void armorAssemblySceneIsRegisteredOnTheToolStationItem() {
        RecordingHelper helper = new RecordingHelper();
        new ForgeweavePonderPlugin().registerScenes(helper);

        RegisteredScene scene = helper.scenes.stream()
                .filter(s -> s.schematic().getPath().equals("tool_station")).findFirst().orElseThrow();
        assertEquals(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "tool_station"), scene.component());
        assertEquals(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "tool_station"), scene.schematic());
        assertNotNull(scene.board());
    }

    /** The armor scene's schematic: a Tool Station alone on the base plate. */
    @Test
    void toolStationSchematicShipsAndContainsTheStation() throws IOException {
        CompoundTag root;
        try (InputStream in = Forgeweave.class.getResourceAsStream("/assets/forgeweave/ponder/tool_station.nbt")) {
            assertNotNull(in, "assets/forgeweave/ponder/tool_station.nbt is missing");
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            names.add(palette.getCompound(i).getString("Name"));
        }
        assertTrue(names.contains("forgeweave:tool_station"), "the station is part of the structure: " + names);
    }

    /**
     * Ponder resolves the schematic to {@code assets/forgeweave/ponder/smeltery.nbt} (gzipped
     * vanilla structure NBT, {@code PonderSceneRegistry.loadSchematic}); a missing file degrades to
     * an empty scene world at runtime with only a log line, so the jar-ships check has to live in a
     * test. Regenerate with {@code scripts/generate_ponder_schematics.py}.
     */
    @Test
    void smelterySchematicShipsAndContainsTheStructure() throws IOException {
        CompoundTag root;
        try (InputStream in = Forgeweave.class.getResourceAsStream("/assets/forgeweave/ponder/smeltery.nbt")) {
            assertNotNull(in, "assets/forgeweave/ponder/smeltery.nbt is missing");
            root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }

        ListTag size = root.getList("size", Tag.TAG_INT);
        assertEquals(5, size.getInt(0));
        assertEquals(4, size.getInt(1));
        assertEquals(5, size.getInt(2));

        ListTag palette = root.getList("palette", Tag.TAG_COMPOUND);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            names.add(palette.getCompound(i).getString("Name"));
        }
        assertTrue(names.contains("forgeweave:seared_bricks"), "walls and floor are seared bricks: " + names);
        assertTrue(names.contains("forgeweave:seared_tank"), "the fuel tank is part of the walls: " + names);

        int coreIndex = names.indexOf("forgeweave:standard_core");
        assertTrue(coreIndex >= 0, "the controller is part of the structure: " + names);
        CompoundTag coreProperties = palette.getCompound(coreIndex).getCompound("Properties");
        assertEquals("north", coreProperties.getString("facing"),
                "the core faces out of the north wall, toward the default camera (#700)");
        assertEquals("false", coreProperties.getString("active"),
                "the schematic stores the unformed state; the scene flips ACTIVE as its completion cue");
    }

    /** #700: the size-variants scene sits beside the assembly scene on the core; the casting scene on both blocks it teaches. */
    @Test
    void sizesAndCastingScenesAreRegisteredOnTheirBlocks() {
        RecordingHelper helper = new RecordingHelper();
        new ForgeweavePonderPlugin().registerScenes(helper);

        List<String> pairs = helper.scenes.stream()
                .map(s -> s.component().getPath() + "->" + s.schematic().getPath()).toList();
        assertTrue(pairs.contains("standard_core->smeltery_sizes"), pairs.toString());
        assertTrue(pairs.contains("faucet->casting"), pairs.toString());
        assertTrue(pairs.contains("seared_channel->casting"), pairs.toString());
    }

    /**
     * #700 (playtest 0.3.5-beta.1): Ponder's default camera ({@code PonderScene.SceneTransform}: yaw
     * 145, pitch -35, no mirroring) looks at the scene from the north-west, so a block's north and
     * west faces are the ones a player sees. The smeltery scene's core sat in the south wall facing
     * south -- scan-correct, but the camera saw its back. Every directional block in every schematic
     * is pinned here to a face the camera sees (the faucet's {@code facing} is its input, the drain
     * south of it).
     */
    @Test
    void directionalBlocksFaceTheDefaultCamera() throws IOException {
        assertFacings("smeltery", Map.of("forgeweave:standard_core", "north"));
        assertFacings("smeltery_sizes", Map.of("forgeweave:standard_core", "north"));
        assertFacings("casting", Map.of(
                "forgeweave:standard_core", "west",
                "forgeweave:seared_drain", "north",
                "forgeweave:faucet", "south"));
    }

    /** The casting scene's channel run: faucet -> fork -> west to the table's downspout, north to the basin's. */
    @Test
    void castingChannelsChainFromTheFaucetToTheTableAndBasin() throws IOException {
        ListTag palette = readSchematic("casting").getList("palette", Tag.TAG_COMPOUND);
        List<Map<String, String>> channels = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (entry.getString("Name").equals("forgeweave:seared_channel")) {
                CompoundTag props = entry.getCompound("Properties");
                Map<String, String> map = new TreeMap<>();
                for (String key : props.getAllKeys()) {
                    map.put(key, props.getString(key));
                }
                channels.add(map);
            }
        }
        assertTrue(channels.contains(Map.of("down", "false", "north", "out", "west", "out")), "the fork under the faucet: " + channels);
        assertTrue(channels.contains(Map.of("down", "false", "east", "in", "west", "out")), "the westward run: " + channels);
        assertTrue(channels.contains(Map.of("down", "true", "east", "in")), "the table's downspout: " + channels);
        assertTrue(channels.contains(Map.of("down", "true", "south", "in")), "the basin's downspout: " + channels);
    }

    private static void assertFacings(String schematic, Map<String, String> expected) throws IOException {
        ListTag palette = readSchematic(schematic).getList("palette", Tag.TAG_COMPOUND);
        Map<String, String> actual = new HashMap<>();
        for (int i = 0; i < palette.size(); i++) {
            CompoundTag entry = palette.getCompound(i);
            if (entry.getCompound("Properties").contains("facing")) {
                actual.put(entry.getString("Name"), entry.getCompound("Properties").getString("facing"));
            }
        }
        assertEquals(expected, actual, "directional blocks in " + schematic + ".nbt");
    }

    private static CompoundTag readSchematic(String name) throws IOException {
        try (InputStream in = Forgeweave.class.getResourceAsStream("/assets/forgeweave/ponder/" + name + ".nbt")) {
            assertNotNull(in, "assets/forgeweave/ponder/" + name + ".nbt is missing");
            return NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        }
    }
}
