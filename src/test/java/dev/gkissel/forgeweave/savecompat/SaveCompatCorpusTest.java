package dev.gkissel.forgeweave.savecompat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The save-compatibility fixture corpus (docs/SCOPE.md, "Save compatibility"): every serialized
 * format a release shipped is committed here as a snapshot, and every later PR has to decode all of
 * them. The point is that these bytes are <b>never regenerated</b> -- a format change that would
 * strand an existing world shows up as a failure here rather than as a player's lost tool.
 *
 * <h2>Fixture format</h2>
 *
 * <p>One SNBT file per snapshot under {@code src/test/resources/fixtures/save_compat/}, text rather
 * than binary NBT so a reviewer can read the diff:
 *
 * <pre>
 * {
 *     what: "M2 (#100): a casting table half way through a pour",  // prose, for the reader
 *     decoder: "item_stack",           // or "block_entity"
 *     block: "forgeweave:casting_table",  // block_entity only: whose default state to load against
 *     nbt: { ... },                    // the committed snapshot -- exactly what the game wrote
 *     expect: { ... }                  // hand-written; must appear in the re-encoded result
 * }
 * </pre>
 *
 * <p>{@code expect} is a <em>subtree</em> of what re-encoding the decoded object must produce, so a
 * fixture asserts the fields that matter without pinning the ones that legitimately move. Writing it
 * by hand (rather than deriving it from {@code nbt}) is what keeps the assertion honest.
 *
 * <h2>Adding a release's formats</h2>
 *
 * <ol>
 *   <li>Build the real object the release serializes -- an assembled {@link ItemStack}, or a block
 *       entity fed through {@code loadWithComponents} -- in a scratch test.
 *   <li>Print its saved tag: {@code new SnbtPrinterTagVisitor().visit(stack.save(registries))} (not
 *       {@code NbtUtils.structureToSnbt}, which injects structure-template keys).
 *   <li>Paste it as {@code nbt} into a new {@code <milestone>_<subject>.snbt} file here, add
 *       {@code what}/{@code decoder}/{@code expect}, and delete the scratch test.
 * </ol>
 *
 * <p>No test code changes: {@link #corpus()} walks the directory.
 *
 * <h2>What the corpus is required to cover</h2>
 *
 * <p>Issue #167 audited it against every format M3 shipped and made that the standing rule: a PR that
 * adds a persistent data component, or changes a block entity's saved tag, adds a fixture in the same
 * PR. The audit's own additions were the four tool components nothing had reached
 * ({@code broken}, {@code mending_moss_xp}, and the two trait-stack counters), the faucet -- the only
 * M3 block entity whose tag shape actually moved (#200, #207) -- the Tool Station's inventory across
 * #152's four-to-six slot growth, the post-#196 {@code parts} list in both its four-part and its
 * absent-and-reconstructed forms, and the three M3 tool formats decoding together on one stack rather
 * than one per file.
 */
class SaveCompatCorpusTest {

    private static final String CORPUS = "/fixtures/save_compat";
    private static final String CORRUPT = "/fixtures/corrupt/tool_modifier_level_zero.snbt";

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    static Stream<Path> corpus() throws Exception {
        URL dir = SaveCompatCorpusTest.class.getResource(CORPUS);
        assertNotNull(dir, "the corpus directory is missing from the test resources");
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            List<Path> snbt = files.filter(path -> path.toString().endsWith(".snbt")).sorted().toList();
            assertTrue(snbt.size() >= 15,
                    "the M2 four, #101's tank, #154's embossment, #160's ramp, #167's audit five, "
                            + "M3.2's two component states and #248's seven-slot station; "
                            + "something dropped fixtures");
            return snbt.stream();
        }
    }

    /**
     * Every committed snapshot still decodes, and re-encoding what came out still contains the
     * fields the fixture pinned.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("corpus")
    void everyFixtureStillDecodes(Path file) throws Exception {
        CompoundTag fixture = read(file);

        CompoundTag reEncoded = decodeAndReEncode(fixture);

        assertContains(fixture.getCompound("expect"), reEncoded, file.getFileName().toString());
    }

    /**
     * ADR-0004 decision 2, pinned against the corpus rather than against freshly encoded objects: a
     * saved modifier is an {@code id} and a {@code level} and nothing else. Anything richer would
     * stop decoding when M6 replaces the Java modifier behaviors with datapack definitions.
     */
    @Test
    void savedModifiersAreNothingButIdAndLevel() throws Exception {
        CompoundTag tool = read(Path.of(
                SaveCompatCorpusTest.class.getResource(CORPUS + "/m2_tool_pickaxe_modifiers.snbt").toURI()));
        ListTag modifiers = tool.getCompound("nbt").getCompound("components")
                .getList("forgeweave:modifiers", Tag.TAG_COMPOUND);

        assertEquals(3, modifiers.size(), "the fixture is meant to carry a multi-entry modifier list");
        for (int i = 0; i < modifiers.size(); i++) {
            assertEquals(Set.of("id", "level"), modifiers.getCompound(i).getAllKeys(),
                    "ADR-0004: a serialized modifier is id + level, never a class reference or a cached stat");
        }
    }

    /**
     * The same rule for embossing's <em>generated</em> ids (issue #154). Worth pinning separately
     * from the fixture above: {@code embossment.<material>} is built at application time from a
     * datapack material rather than picked from a fixed table, so it is the id most likely to grow a
     * companion field ("which traits did it add?") the day someone finds that convenient -- and the
     * day it does, every embossed tool in every existing world stops decoding.
     */
    @Test
    void aGeneratedEmbossmentIdIsStillNothingButIdAndLevel() throws Exception {
        CompoundTag tool = read(Path.of(
                SaveCompatCorpusTest.class.getResource(CORPUS + "/m3_tool_pickaxe_embossment.snbt").toURI()));
        ListTag modifiers = tool.getCompound("nbt").getCompound("components")
                .getList("forgeweave:modifiers", Tag.TAG_COMPOUND);

        CompoundTag embossment = null;
        for (int i = 0; i < modifiers.size(); i++) {
            if (modifiers.getCompound(i).getString("id").contains("embossment.")) {
                embossment = modifiers.getCompound(i);
            }
        }
        assertNotNull(embossment, "the fixture is meant to carry an embossment entry");
        assertEquals(Set.of("id", "level"), embossment.getAllKeys(),
                "ADR-0004 applies to generated ids too: an embossment is id + level, nothing more");
        assertEquals(1, embossment.getInt("level"), "an embossment is always level 1 (upstream's SingleAspect)");
    }

    /**
     * The non-vacuity check: a fixture that is corrupt in one field has to fail, or a green corpus
     * run would mean nothing. Deliberately kept outside {@link #CORPUS} so the walk above never
     * sees it.
     */
    @Test
    void aCorruptFixtureFailsToDecode() throws Exception {
        CompoundTag corrupt = read(Path.of(SaveCompatCorpusTest.class.getResource(CORRUPT).toURI()));

        assertThrows(IllegalStateException.class, () -> decodeAndReEncode(corrupt),
                "a level-0 modifier must not decode -- if this passes, the corpus walk proves nothing");
    }

    // ------------------------------------------------------------------ decoding

    /** Decodes a fixture's committed snapshot with the real save path, then writes it back out. */
    private static CompoundTag decodeAndReEncode(CompoundTag fixture) {
        CompoundTag nbt = fixture.getCompound("nbt");
        String decoder = fixture.getString("decoder");
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        return switch (decoder) {
            case "item_stack" -> {
                // getOrThrow rather than ItemStack#parse: a corrupt stack must blow up, not become EMPTY.
                ItemStack stack = ItemStack.CODEC.parse(ops, nbt).getOrThrow(IllegalStateException::new);
                yield (CompoundTag) ItemStack.CODEC.encodeStart(ops, stack).getOrThrow(IllegalStateException::new);
            }
            case "block_entity" -> {
                Block block = BuiltInRegistries.BLOCK.get(
                        ResourceLocation.parse(fixture.getString("block")));
                BlockEntity be = BlockEntity.loadStatic(BlockPos.ZERO, block.defaultBlockState(), nbt, registries);
                if (be == null) {
                    throw new IllegalStateException("block entity did not load: " + fixture.getString("what"));
                }
                yield be.saveWithId(registries);
            }
            default -> throw new IllegalStateException("unknown fixture decoder: " + decoder);
        };
    }

    private static CompoundTag read(Path file) throws IOException {
        try {
            return TagParser.parseTag(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("fixture is not valid SNBT: " + file, e);
        }
    }

    /** Asserts every key in {@code expected} is present in {@code actual} with the same value, recursively. */
    private static void assertContains(CompoundTag expected, CompoundTag actual, String where) {
        for (String key : expected.getAllKeys()) {
            Tag want = expected.get(key);
            Tag got = actual.get(key);
            if (got == null) {
                fail(where + ": expected key '" + key + "' is missing from the re-encoded " + actual);
            }
            if (want instanceof CompoundTag wantCompound && got instanceof CompoundTag gotCompound) {
                assertContains(wantCompound, gotCompound, where + "." + key);
            } else {
                assertEquals(want, got, where + ": '" + key + "' changed shape");
            }
        }
    }
}
