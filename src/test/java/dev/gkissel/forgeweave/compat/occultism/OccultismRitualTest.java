package dev.gkissel.forgeweave.compat.occultism;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #997's ritual half: what a spirit binding decides, and whether the four shipped
 * {@code occultism:ritual} rows spell that decision out the way Occultism's own codec reads.
 *
 * <p>The analogue of {@code FusionUpgradeRecipeTest}, adjusted for what Occultism actually exposes.
 * There is no Forgeweave serializer to round-trip: the rows are {@code occultism:ritual},
 * Occultism's own recipe type, and the Forgeweave-owned half is a {@code RitualFactory} in
 * {@code occultism:ritual_factories} ({@link SpiritBindingRitual}'s javadoc explains why). So the
 * row half of this test asserts the generated JSON's field shape directly.
 *
 * <p>Names no Occultism type, unlike {@code FusionUpgradeRecipeTest}, which does name Draconic ones.
 * Occultism will not load on this repo's unit-test classpath at all -- it needs four further mods,
 * see build.gradle's comment on the dependency -- so the field names below are checked against
 * Occultism's codecs by reading them rather than by executing them:
 * {@code RitualRecipe.RitualRequirementSettings.CODEC} for {@code pentacle_id} /
 * {@code activation_item} / {@code ingredients} / {@code duration}, and {@code RitualRecipe.CODEC}
 * for {@code ritual_type} / {@code ritual_dummy} / {@code result}. That is the one thing here a
 * reviewer should take on the PR's word rather than on the build's.
 */
class OccultismRitualTest {

    private static RegistryAccess.Frozen registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    static List<ForgeweaveOccultismCompat.Ritual> rituals() {
        return ForgeweaveOccultismCompat.RITUALS;
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static JsonObject row(ForgeweaveOccultismCompat.Ritual ritual) throws IOException {
        Path file = projectRoot().resolve("src/generated/resources/data/forgeweave/recipe/compat/occultism/ritual")
                .resolve(ritual.name() + ".json");
        assertTrue(Files.isRegularFile(file), "expected a generated ritual row at " + file);
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static ItemStack pickaxe() {
        return new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
    }

    private static Optional<ItemStack> bind(ForgeweaveOccultismCompat.Ritual ritual, ItemStack tool) {
        return ForgeweaveOccultismCompat.bind(registries, tool,
                ResourceLocation.parse(ritual.modifier()), ritual.level());
    }

    // ----------------------------------------------------------------- the rows

    @ParameterizedTest
    @MethodSource("rituals")
    void everyRowNamesTheFactoryItsModifierRegistersUnder(ForgeweaveOccultismCompat.Ritual ritual)
            throws IOException {
        JsonObject json = row(ritual);

        assertEquals("occultism:ritual", json.get("type").getAsString());
        assertEquals("forgeweave:" + ritual.factoryName(), json.get("ritual_type").getAsString(),
                "a row whose ritual_type does not match a registered factory silently does nothing");
        assertEquals(ritual.pentacle(), json.get("pentacle_id").getAsString());
        assertEquals("forgeweave:ritual_bindable",
                json.getAsJsonObject("activation_item").get("tag").getAsString(),
                "the activation item is the tool the player lays on the golden bowl");
        assertTrue(json.get("duration").getAsInt() > 0);
    }

    @ParameterizedTest
    @MethodSource("rituals")
    void everyRowAsksForOneBowlPerIngredient(ForgeweaveOccultismCompat.Ritual ritual) throws IOException {
        JsonArray ingredients = row(ritual).getAsJsonArray("ingredients");

        assertEquals(ritual.ingredients().size(), ingredients.size(),
                "one sacrificial bowl per ingredient -- a dropped entry is a cheaper ritual");
        assertEquals(ForgeweaveOccultismCompat.SPIRIT_GEM,
                ingredients.get(0).getAsJsonObject().get("item").getAsString(),
                "the spirit itself comes first; it is what is being bound");
        assertEquals(ritual.essence(), ingredients.size() - 1,
                "and the rest is otherworld essence, one per rank of the ritual's pentacle");
    }

    /**
     * Both stacks Occultism's JEI category draws off the recipe carry the modifier the ritual grants,
     * which is the whole of the JEI coverage this integration needs -- see
     * {@code ForgeweaveOccultismRecipeProvider}'s javadoc.
     */
    @ParameterizedTest
    @MethodSource("rituals")
    void bothDisplayStacksCarryTheModifierTheRitualGrants(ForgeweaveOccultismCompat.Ritual ritual)
            throws IOException {
        JsonObject json = row(ritual);
        for (String field : List.of("ritual_dummy", "result")) {
            JsonArray entries = json.getAsJsonObject(field).getAsJsonObject("components")
                    .getAsJsonArray("forgeweave:modifiers");
            assertEquals(1, entries.size(), field + " should show exactly the one modifier the ritual grants");
            JsonObject entry = entries.get(0).getAsJsonObject();
            assertEquals(ritual.modifier(), entry.get("id").getAsString());
            assertEquals(ritual.level(), entry.get("level").getAsInt());
        }
    }

    /** No two rituals share a factory name, a modifier or a pentacle. */
    @Test
    void theRosterHasNoDuplicates() {
        Set<String> factories = new HashSet<>();
        Set<String> modifiers = new HashSet<>();
        Set<String> pentacles = new HashSet<>();
        for (ForgeweaveOccultismCompat.Ritual ritual : ForgeweaveOccultismCompat.RITUALS) {
            assertTrue(factories.add(ritual.factoryName()), "duplicate factory " + ritual.factoryName());
            assertTrue(modifiers.add(ritual.modifier()), "duplicate modifier " + ritual.modifier());
            assertTrue(pentacles.add(ritual.pentacle()), "two rituals in one pentacle: " + ritual.pentacle());
        }
        assertEquals(4, ForgeweaveOccultismCompat.RITUALS.size(),
                "four rituals, one per Occultism spirit rank; a fifth needs a rank to sit in");
    }

    // ----------------------------------------------------------------- the binding

    @ParameterizedTest
    @MethodSource("rituals")
    void everyRitualBindsItsModifierAtItsLevel(ForgeweaveOccultismCompat.Ritual ritual) {
        ItemStack tool = pickaxe();
        Optional<ItemStack> bound = bind(ritual, tool);

        assertTrue(bound.isPresent(), ritual.name() + " should bind onto a freshly assembled pickaxe;"
                + " every level in the roster is picked to fit a fresh tool's slot budget");
        ModifierEntry entry = ForgeweaveModifiers.entry(bound.get(), ResourceLocation.parse(ritual.modifier()));
        assertNotNull(entry, ritual.name() + " wrote no modifier entry at all");
        assertEquals(ritual.level(), entry.level(), ritual.name() + " must land at its roster level");
        assertTrue(ForgeweaveModifiers.of(tool).isEmpty(), "and must not have mutated the tool it was given");
    }

    /**
     * The half that separates a binding from a fusion upgrade: the entry occupies its slots. Without
     * it a ritual would be strictly better than the Tool Station route it shortcuts.
     */
    @ParameterizedTest
    @MethodSource("rituals")
    void bindingSpendsTheSlotsItsEntryOccupies(ForgeweaveOccultismCompat.Ritual ritual) {
        ItemStack tool = pickaxe();
        int before = ForgeweaveModifiers.freeSlots(tool);
        ItemStack bound = bind(ritual, tool).orElseThrow();

        int spent = before - ForgeweaveModifiers.freeSlots(bound);
        ResourceLocation modifier = ResourceLocation.parse(ritual.modifier());
        assertEquals(ForgeweaveModifiers.occupiedSlots(modifier, ritual.level()), spent,
                ritual.name() + " must spend exactly what its entry occupies -- no free upgrade, and no"
                        + " double charge either");
        assertTrue(spent <= before, "and never more slots than the tool had");
    }

    @Test
    void aSecondBindingOfTheSameRitualIsRefused() {
        ForgeweaveOccultismCompat.Ritual ritual = ForgeweaveOccultismCompat.RITUALS.getFirst();
        ItemStack bound = bind(ritual, pickaxe()).orElseThrow();

        assertTrue(bind(ritual, bound).isEmpty(),
                "a tool already at the ritual's level has nothing to gain, so the pentacle must not light"
                        + " up and burn the ingredients for nothing");
    }

    @Test
    void aStackThatIsNotAnAssembledToolIsRefused() {
        ForgeweaveOccultismCompat.Ritual ritual = ForgeweaveOccultismCompat.RITUALS.getFirst();

        assertTrue(bind(ritual, new ItemStack(Items.DIAMOND_PICKAXE)).isEmpty(),
                "a vanilla pickaxe carries no Forgeweave modifier list, so there is nothing to bind into");
        assertTrue(bind(ritual, ItemStack.EMPTY).isEmpty());
    }

    /** An unregistered modifier id is refused rather than written as an inert entry. */
    @Test
    void anUnknownModifierIsRefused() {
        assertTrue(ForgeweaveOccultismCompat.bind(registries, pickaxe(),
                ResourceLocation.fromNamespaceAndPath("forgeweave", "no_such_modifier"), 1).isEmpty());
    }

    /** Every level in the roster is at or under the modifier's own shipped Tool Station cap. */
    @ParameterizedTest
    @MethodSource("rituals")
    void noRitualGrantsMoreThanTheToolStationCould(ForgeweaveOccultismCompat.Ritual ritual) throws IOException {
        Path recipe = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/modifier_recipe")
                .resolve(ritual.name() + ".json");
        assertTrue(Files.isRegularFile(recipe), ritual.name() + " must have a shipped Tool Station recipe;"
                + " a ritual is a shortcut past the reagent grind, not a modifier of its own");
        int maxLevel = JsonParser.parseString(Files.readString(recipe, StandardCharsets.UTF_8))
                .getAsJsonObject().get("max_level").getAsInt();
        assertTrue(ritual.level() <= maxLevel, ritual.name() + " grants level " + ritual.level()
                + " but its own recipe caps at " + maxLevel);
    }
}
