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
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagLoader;
import net.minecraft.util.ProblemReporter;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import com.mojang.serialization.Dynamic;

import dev.gkissel.forgeweave.TestRegistries;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.trait.TraitFamilies;

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
 *     decoder: "item_stack",           // or "block_entity", or "material"
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
 *
 * <h2>What the corpus deliberately does not cover: entity NBT</h2>
 *
 * <p>M7's damage ledger (docs/SCOPE.md D-M7-4, issue #919) is a NeoForge data attachment on
 * {@code LivingEntity}: it serializes into <em>entity</em> NBT, not into an item stack, a block
 * entity or a material, so none of the three decoders above has a shape for it. Issue #925 asked
 * whether the corpus should grow an entity case; the answer is <b>no</b>, and this paragraph is
 * where that is recorded so the next milestone does not rediscover the question.
 *
 * <p>The reasons: an entity fixture would need a live {@code ServerLevel} to load against, which is
 * exactly what these plain JUnit tests avoid; the corpus's value is in the committed bytes, and an
 * attachment's bytes are the codec's own map with nothing Forgeweave-shaped in between; and the
 * round trip that actually matters -- a mob saved, unloaded, reloaded and only then killed, still
 * paying the tool that hurt it -- is behavior, not a format, so it is pinned as a GameTest instead
 * ({@code ToolXpGameTests#theDamageLedgerSurvivesAnEntitySaveAndReload}). The item-side halves of M7
 * are here, in {@code m7_tool_level.snbt} and {@code m7_armor_level.snbt}, and
 * {@code m7_tool_level.snbt} carries the {@code forgeweave:tool_id} the ledger keys by, so the
 * component the corpus <em>can</em> hold is held. The standing rule is unchanged for everything
 * else: a new persistent data component or a changed block-entity tag still adds a fixture in the
 * same PR. A future attachment whose shape is richer than a codec-generated map is worth revisiting
 * this for.
 *
 * <h2>What the corpus cannot cover: a tool from another mod</h2>
 *
 * <p>Issue #1066 let another mod register a tool, and a registered tool's stack is the one shape the
 * corpus has no way to hold: {@code ItemStack.CODEC} needs the item in the registry, and the item
 * belongs to a mod these plain JUnit tests do not load. The round trip is a GameTest instead
 * ({@code RegisteredToolGameTests#aRegisteredToolAssemblesAndSurvivesASaveRoundTrip}), where the
 * test addon is loaded and the stack comes out of a real Tool Station.
 *
 * <p>Nothing was added here for that issue because nothing it changed is serialized. It widened
 * {@code ToolConstants.PartSlot#partId} from a path to a full {@code Identifier}, and that
 * field lives in a static table, not in a component: what a tool stores is still the material per
 * slot, positionally. The whole corpus passing unchanged is the evidence for that, which is the
 * other reason no fixture belongs here -- a new one would only restate it.
 */
class SaveCompatCorpusTest {

    private static final String CORPUS = "/fixtures/save_compat";
    private static final String CORRUPT = "/fixtures/corrupt/tool_modifier_level_zero.snbt";

    /**
     * The data version a 1.21.1 world stamps on everything it saves. The corpus was written by
     * 1.21.1 builds, so on 26.x the game runs every vanilla part of these bytes through the data
     * fixer from this version before decoding them, which is exactly what loading a 1.21.1 world does.
     */
    private static final int DATA_VERSION_1_21_1 = 3955;

    /** The built-in registries plus the vanilla data-driven ones loaded below. */
    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // T80: a tool can carry a real vanilla enchantment, whose Holder<Enchantment> codec needs the
        // data-driven ENCHANTMENT registry. TestRegistries.vanilla() loads it from the vanilla data
        // pack the way a server does -- not through RegistrySetBuilder/VanillaRegistries, whose holders
        // belong to a shared "UniversalOwner" and so fail canSerializeIn when re-encoded.
        registries = TestRegistries.vanilla();
    }

    static Stream<Path> corpus() throws Exception {
        URL dir = SaveCompatCorpusTest.class.getResource(CORPUS);
        assertNotNull(dir, "the corpus directory is missing from the test resources");
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            List<Path> snbt = files.filter(path -> path.toString().endsWith(".snbt")).sorted().toList();
            assertTrue(snbt.size() >= 72,
                    "the M2 four, #101's tank, #154's embossment, #160's ramp, #167's audit five, "
                            + "M3.2's two component states, #248's seven-slot station, #237's "
                            + "audit two (new-roster tool, new-fluid smeltery), #305's Pattern "
                            + "Chest inventory and every format each later milestone added, up to "
                            + "M7's leveled tool and leveled chestplate (#925) and all of M8's: the "
                            + "energized tank (#972), the pack-defined modifier (#973), the "
                            + "socketed and affixed tools (#969, #970), the module-carrying tool "
                            + "(#993), phase 2's modules and rayward armor (#994), surgebound "
                            + "(#996), the ritual-bound tool (#997) and the augmented tool (#999). "
                            + "#975 audited the set against every M8 child and raised this floor to "
                            + "the real count; something dropped fixtures");
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

        assertContains(expected(fixture), reEncoded, file.getFileName().toString());
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
        ListTag modifiers = tool.getCompoundOrEmpty("nbt").getCompoundOrEmpty("components")
                .getListOrEmpty("forgeweave:modifiers");

        assertEquals(3, modifiers.size(), "the fixture is meant to carry a multi-entry modifier list");
        for (int i = 0; i < modifiers.size(); i++) {
            assertEquals(Set.of("id", "level"), modifiers.getCompoundOrEmpty(i).keySet(),
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
        ListTag modifiers = tool.getCompoundOrEmpty("nbt").getCompoundOrEmpty("components")
                .getListOrEmpty("forgeweave:modifiers");

        CompoundTag embossment = null;
        for (int i = 0; i < modifiers.size(); i++) {
            if (modifiers.getCompoundOrEmpty(i).getStringOr("id", "").contains("embossment.")) {
                embossment = modifiers.getCompoundOrEmpty(i);
            }
        }
        assertNotNull(embossment, "the fixture is meant to carry an embossment entry");
        assertEquals(Set.of("id", "level"), embossment.keySet(),
                "ADR-0004 applies to generated ids too: an embossment is id + level, nothing more");
        assertEquals(1, embossment.getIntOr("level", 0), "an embossment is always level 1 (upstream's SingleAspect)");
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

    /**
     * Playtest checklist 1.0.0, line 19: a world saved at 0.6.0-beta.3 or earlier keeps its trait
     * names and effects, and its retired ids point at their replacement (issue #1103). The alias map
     * is the whole of that promise, so its size is pinned exactly: a retired id dropped from it
     * strands every saved tool still carrying it. The checklist says 123; the shipped map holds 124,
     * and the shipped map is what is asserted.
     */
    @Test
    void exactlyTheShippedHundredAndTwentyFourRetiredTraitIdsAreAliased() {
        assertEquals(124, TraitFamilies.aliases().size(), "#1103's retired trait ids");
    }

    /**
     * Playtest checklist 1.0.0, line 19, from the save side: the two {@code m1103_beta3_*} fixtures
     * were printed by the real assembly code at tag {@code mc1.21.1-v0.6.0-beta.3}, a pickaxe and a
     * chestplate whose stored trait lists name ids #1103 later retired. The corpus walk above proves
     * they decode with those ids kept verbatim; this proves each fixture really carries retired ids,
     * so the walk is not passing on a stack with nothing to remap, and that every stored id is either
     * still live or has an alias.
     */
    @Test
    void theBeta3FixturesCarryRetiredTraitIdsThatAllResolve() throws Exception {
        for (String name : List.of("m1103_beta3_pickaxe.snbt", "m1103_beta3_chestplate.snbt")) {
            CompoundTag fixture = read(Path.of(SaveCompatCorpusTest.class.getResource(CORPUS + "/" + name).toURI()));
            ListTag traits = fixture.getCompoundOrEmpty("nbt").getCompoundOrEmpty("components")
                    .getListOrEmpty("forgeweave:traits");
            List<Identifier> retired = traits.stream()
                    .map(tag -> Identifier.parse(tag.asString().orElseThrow()))
                    .filter(TraitFamilies.aliases()::containsKey)
                    .toList();
            assertTrue(!retired.isEmpty(), name + " must carry at least one retired trait id, carries " + traits);
            for (Identifier id : retired) {
                assertTrue(!TraitFamilies.aliases().containsKey(TraitFamilies.canonical(id)),
                        name + ": " + id + " must resolve straight to a live id");
            }
        }
    }

    // ------------------------------------------------------------------ decoding

    /** Decodes a fixture's committed snapshot with the real save path, then writes it back out. */
    private static CompoundTag decodeAndReEncode(CompoundTag fixture) {
        CompoundTag nbt = fixture.getCompoundOrEmpty("nbt");
        String decoder = fixture.getStringOr("decoder", "");
        // Lenient on missing tags (TestRegistries): no datapack binds c:ingots/iron here.
        RegistryOps<Tag> ops = TestRegistries.ops(NbtOps.INSTANCE, registries);
        return switch (decoder) {
            case "item_stack" -> {
                // 26.x: what a 1.21.1 world's stack goes through on load before ItemStack.CODEC sees it.
                nbt = upgradeItemStack(nbt);
                // getOrThrow rather than ItemStack#parse: a corrupt stack must blow up, not become EMPTY.
                ItemStack stack = ItemStack.CODEC.parse(ops, nbt).getOrThrow(IllegalStateException::new);
                yield (CompoundTag) ItemStack.CODEC.encodeStart(ops, stack).getOrThrow(IllegalStateException::new);
            }
            case "block_entity" -> {
                Block block = BuiltInRegistries.BLOCK.getValue(
                        Identifier.parse(fixture.getStringOr("block", "")));
                yield loadAndSaveBlockEntity(block, nbt, fixture.getStringOr("what", ""));
            }
            // Issue #392: a datapack material is a serialized format like any other -- the same codec
            // is the registry-sync codec, so a shape change strands a connecting client the way a
            // component change strands a world. Snapshotted in NBT (what the sync packet carries)
            // rather than JSON, which is what the rest of the corpus is in.
            case "material" -> {
                nbt = upgradeMaterialIngredients(nbt);
                Material material = Material.CODEC.parse(ops, nbt).getOrThrow(IllegalStateException::new);
                yield (CompoundTag) Material.CODEC.encodeStart(ops, material).getOrThrow(IllegalStateException::new);
            }
            default -> throw new IllegalStateException("unknown fixture decoder: " + decoder);
        };
    }

    /**
     * 26.x: a block entity's load and save go through ValueInput/ValueOutput, which report a field
     * that fails to decode to a ProblemReporter and carry on instead of throwing. The real chunk load
     * ({@link BlockEntity#loadStatic}) only logs those, so this does the same steps with a collector
     * and turns any reported problem into a failure: a field the game would drop is lost data, which
     * is what the corpus exists to catch.
     */
    private static CompoundTag loadAndSaveBlockEntity(Block block, CompoundTag nbt, String what) {
        BlockEntityType<?> type = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(
                Identifier.tryParse(nbt.getStringOr("id", "")));
        if (type == null) {
            throw new IllegalStateException("block entity has no known type: " + what);
        }
        BlockEntity be = type.create(BlockPos.ZERO, block.defaultBlockState());
        if (be == null) {
            throw new IllegalStateException("block entity did not load: " + what);
        }
        ProblemReporter.Collector loadProblems = new ProblemReporter.Collector();
        be.loadWithComponents(TagValueInput.create(loadProblems, registries, nbt));
        if (!loadProblems.isEmpty()) {
            throw new IllegalStateException("block entity dropped data on load (" + what + "): " + loadProblems.getReport());
        }
        ProblemReporter.Collector saveProblems = new ProblemReporter.Collector();
        TagValueOutput out = TagValueOutput.createWithContext(saveProblems, registries);
        be.saveWithId(out);
        if (!saveProblems.isEmpty()) {
            throw new IllegalStateException("block entity failed to save (" + what + "): " + saveProblems.getReport());
        }
        return out.buildResult();
    }

    /**
     * 26.x (1.21.2) changed vanilla's ingredient format: {@code {item: "x"}} is now {@code "x"},
     * {@code {tag: "x"}} is {@code "#x"}, a list stays a list of those. A material snapshot is the
     * registry-sync and datapack shape, not world data, and has no data fixer; the shipped material
     * JSON got exactly this rewrite. So the snapshot's two ingredient fields get it too, and only
     * those two and only those shapes: anything else in the bytes is decoded as written, so a
     * Forgeweave-owned field that stopped decoding still fails here.
     */
    private static CompoundTag upgradeMaterialIngredients(CompoundTag material) {
        CompoundTag upgraded = material.copy();
        Tag repair = upgraded.get("repair_item");
        if (repair != null) {
            upgraded.put("repair_item", upgradeIngredient(repair));
        }
        for (Tag entry : upgraded.getListOrEmpty("crafting_items")) {
            if (entry instanceof CompoundTag craftingItem && craftingItem.get("ingredient") != null) {
                craftingItem.put("ingredient", upgradeIngredient(craftingItem.get("ingredient")));
            }
        }
        return upgraded;
    }

    private static Tag upgradeIngredient(Tag ingredient) {
        if (ingredient instanceof ListTag list) {
            ListTag upgraded = new ListTag();
            list.forEach(element -> upgraded.add(upgradeIngredient(element)));
            return upgraded;
        }
        if (ingredient instanceof CompoundTag value && value.size() == 1) {
            if (value.get("item") instanceof StringTag(String item)) {
                return StringTag.valueOf(item);
            }
            if (value.get("tag") instanceof StringTag(String tag)) {
                return StringTag.valueOf("#" + tag);
            }
        }
        return ingredient;
    }

    /** Runs a 1.21.1 item stack tag through the vanilla data fixer, as loading a 1.21.1 world does. */
    private static CompoundTag upgradeItemStack(CompoundTag stack) {
        return (CompoundTag) DataFixers.getDataFixer().update(References.ITEM_STACK,
                new Dynamic<>(NbtOps.INSTANCE, stack), DATA_VERSION_1_21_1,
                SharedConstants.getCurrentVersion().dataVersion().version()).getValue();
    }

    /**
     * A fixture's hand-written {@code expect}. It was written against what 1.21.1 re-encoded, so for
     * an item stack it gets the same data-fixer pass as the bytes: a vanilla component whose shape
     * 26.x changed (1.21.5 unwrapped {@code minecraft:enchantments}' {@code levels}) is compared in
     * its new shape, while every Forgeweave component, which the fixer never touches, is compared
     * as written. The stack's id and count are copied in so the fixer sees a whole stack; both are
     * re-encoded unchanged, so they add nothing the decode could fail on by accident.
     */
    private static CompoundTag expected(CompoundTag fixture) {
        CompoundTag expect = fixture.getCompoundOrEmpty("expect");
        if (!fixture.getStringOr("decoder", "").equals("item_stack")) {
            return expect;
        }
        CompoundTag nbt = fixture.getCompoundOrEmpty("nbt");
        CompoundTag whole = expect.copy();
        for (String key : List.of("id", "count")) {
            if (!whole.contains(key) && nbt.contains(key)) {
                whole.put(key, nbt.get(key).copy());
            }
        }
        return upgradeItemStack(whole);
    }

    private static CompoundTag read(Path file) throws IOException {
        try {
            return TagParser.parseCompoundFully(Files.readString(file, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("fixture is not valid SNBT: " + file, e);
        }
    }

    /** Asserts every key in {@code expected} is present in {@code actual} with the same value, recursively. */
    private static void assertContains(CompoundTag expected, CompoundTag actual, String where) {
        for (String key : expected.keySet()) {
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
