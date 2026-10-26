package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import dev.gkissel.forgeweave.api.combat.CombatSeam;
import dev.gkissel.forgeweave.api.trait.Trait;

/**
 * {@code damage_floor} is a drawback, and a shipped {@code trait_definition} may only name it on
 * purpose (issue #1091).
 *
 * <p>{@link DamageFloor} raises a blow back toward its original damage: on a worn piece it can only
 * ever undo a reduction another trait made, and on a held tool it does nothing at all. Three
 * material traits shipped in 0.6.0-beta.2 with it read backwards --
 * {@code empowered_emeradic_bulwark}, {@code naga_ward} and {@code compressed_iron_heft}, all three
 * named and described as protection -- because nothing stopped them. This test is what stops the
 * fourth: every definition naming {@code damage_floor} has to be listed in {@link #DRAWBACKS_ON_PURPOSE}
 * below, with a line saying what the trait costs its wearer and why that is the design.
 *
 * <p>The list is empty today: {@code bloodtoll}, the one honest user of the behaviour, went away
 * with issue #1103's unreachable-id sweep, so nothing in the tree names {@code damage_floor}.
 *
 * <p>{@link #noTwoTraitsShareABehaviourAndItsParameters} lives here too. It is what replaced
 * #876's "no two materials may name the same trait id" rule (issue #1103): materials share traits
 * now, and what is forbidden instead is two <em>traits</em> that do the same thing under two names.
 */
class TraitDefinitionAuditTest {

    private static final String DAMAGE_FLOOR = "forgeweave:damage_floor";

    /**
     * Trait definitions that carry {@code damage_floor} deliberately, as a cost the material's own
     * name and lang description own up to. Add a trait here only with a comment saying what the
     * drawback buys.
     */
    private static final Set<String> DRAWBACKS_ON_PURPOSE = Set.of();

    @Test
    void onlyDeliberateDrawbacksUseDamageFloor() throws IOException {
        Set<String> definitions = new LinkedHashSet<>();
        Set<String> usingDamageFloor = new LinkedHashSet<>();
        for (Path dataDir : List.of(projectRoot().resolve("src/main/resources/data"),
                projectRoot().resolve("src/generated/resources/data"))) {
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dataDir)) {
                for (Path file : files.filter(TraitDefinitionAuditTest::isTraitDefinition).sorted().toList()) {
                    String id = file.getFileName().toString().replace(".json", "");
                    definitions.add(id);
                    if (DAMAGE_FLOOR.equals(behaviorOf(file))) {
                        usingDamageFloor.add(id);
                    }
                }
            }
        }

        assertFalse(definitions.isEmpty(), "expected to find shipped trait definitions to audit");
        assertEquals(DRAWBACKS_ON_PURPOSE, usingDamageFloor,
                "damage_floor is a drawback: it raises a blow back toward its original damage and does nothing at "
                        + "all on a held tool. A definition may only name it if it is listed in "
                        + "TraitDefinitionAuditTest.DRAWBACKS_ON_PURPOSE with a comment saying what the cost buys, "
                        + "and its lang description has to say the trait costs the wearer something. If the trait is "
                        + "meant to protect, reach for a defensive behaviour instead (stacking_resistance, "
                        + "damage_type_immunity, evasion, invulnerability_window, death_save) or, for a tool, "
                        + "knockback_resistance");
    }

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /**
     * No two registered traits do the same thing under different names -- the guard that took over
     * from {@code MaterialTest#noTwoMaterialsShareANonExemptTraitId} when issue #1103 withdrew
     * #876's uniqueness rule.
     *
     * <p>The old rule forbade the wrong thing. It banned two materials from <em>naming</em> one id,
     * which forced a renamed clone per material and produced exactly what it was meant to prevent:
     * 29 groups of byte-identical behaviour under 70 ids. This one bans the clone itself, by
     * behaviour and parameters rather than by name, over both sources at once -- the Java roster and
     * the shipped {@code trait_definition} files -- so it catches a Java trait cloning a pack one
     * too, which the old rule could not see at all.
     *
     * <p>How a trait reduces to a signature: the parameterised behaviour classes are all records, so
     * value equality is the signature. A combat-seam trait reduces to the seams it emits, which puts
     * {@code ForgeweaveTraits#seamTrait}'s anonymous wrapper and {@code TraitBehaviors.SeamTrait}'s
     * record on the same footing. Everything else is a bespoke anonymous {@code Trait} body, which
     * compares by identity and so is never a duplicate of anything -- those are the mod's real
     * content and this test deliberately says nothing about them.
     */
    @Test
    void noTwoTraitsShareABehaviourAndItsParameters() throws Exception {
        Map<Object, List<String>> bySignature = new LinkedHashMap<>();
        javaRoster().forEach((id, trait) ->
                bySignature.computeIfAbsent(signature(trait), key -> new ArrayList<>()).add(id + " (Java)"));

        for (Path dataDir : List.of(projectRoot().resolve("src/main/resources/data"),
                projectRoot().resolve("src/generated/resources/data"))) {
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dataDir)) {
                for (Path file : files.filter(TraitDefinitionAuditTest::isTraitDefinition).sorted().toList()) {
                    JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    TraitDefinition definition = TraitDefinition.CODEC.parse(ops, json).getOrThrow();
                    bySignature.computeIfAbsent(signature(definition.trait()), key -> new ArrayList<>())
                            .add(file.getFileName().toString().replace(".json", "") + " (pack)");
                }
            }
        }

        List<List<String>> clones = bySignature.values().stream().filter(ids -> ids.size() > 1).toList();
        assertTrue(bySignature.size() > 50, "expected the whole roster, reduced only " + bySignature.size()
                + " signatures");
        assertTrue(clones.isEmpty(), "these trait ids are the same behaviour with the same parameters under "
                + "different names, which is what issue #1103 merged away: " + clones + ". Make them one id, or "
                + "one leveled family in TraitFamilies with an alias from the retired name");
    }

    /** Every alias points at a live id, and never at another alias. */
    @Test
    void everyRetiredTraitIdPointsAtALiveOne() throws Exception {
        Set<String> live = new LinkedHashSet<>(javaRoster().keySet());
        for (Path dataDir : List.of(projectRoot().resolve("src/main/resources/data"),
                projectRoot().resolve("src/generated/resources/data"))) {
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dataDir)) {
                files.filter(TraitDefinitionAuditTest::isTraitDefinition)
                        .forEach(file -> live.add("forgeweave:"
                                + file.getFileName().toString().replace(".json", "")));
            }
        }

        List<String> broken = new ArrayList<>();
        TraitFamilies.aliases().forEach((retired, replacement) -> {
            if (live.contains(retired.toString())) {
                broken.add(retired + " is still registered, so it cannot also be an alias");
            }
            if (!live.contains(replacement.toString())) {
                broken.add(retired + " points at " + replacement + ", which nothing registers");
            }
            if (TraitFamilies.aliases().containsKey(replacement)) {
                broken.add(retired + " points at " + replacement + ", which is itself an alias");
            }
        });

        assertFalse(TraitFamilies.aliases().isEmpty(), "expected #1103's retired ids");
        assertTrue(broken.isEmpty(), "a saved tool carrying a retired trait id would lose the trait: " + broken);
    }

    /** {@code ForgeweaveTraits.REGISTRY}, by reflection, the way {@code TraitReachabilityTest} reads it. */
    @SuppressWarnings("unchecked")
    private static Map<String, Trait> javaRoster() throws Exception {
        var field = ForgeweaveTraits.class.getDeclaredField("REGISTRY");
        field.setAccessible(true);
        Map<String, Trait> roster = new LinkedHashMap<>();
        ((Map<ResourceLocation, Trait>) field.get(null))
                .forEach((id, trait) -> roster.put(id.toString(), trait));
        return roster;
    }

    private static Object signature(Trait trait) {
        if (trait instanceof TraitBehaviors.SeamTrait seamTrait) {
            return List.of(seamTrait.gated());
        }
        List<CombatSeam> seams = new ArrayList<>();
        trait.combatSeams(seams::add);
        return seams.isEmpty() ? trait : List.copyOf(seams);
    }

    /**
     * A worn trait's magnitude is sized by what a full set totals, not by what one piece pays
     * (maintainer directive, 2026-09-21). {@code evasion} is the one behaviour where that is not
     * obvious: {@code CombatSeams#armorPass} calls {@code onDefend} once per worn piece, so four
     * pieces roll the dodge independently and a full set misses 1 - (1 - chance)^4 of the blows.
     * A 25% per-piece rung would make a full set dodge 68% of everything, which is why the shipped
     * rungs are 7% and 10% (a set of 25% and 34%).
     *
     * <p>The ceiling here is 40% for a full set. Raise a rung past it only with a maintainer
     * decision, and do the arithmetic on the set rather than on the piece.
     */
    @Test
    void noEvasionRungLetsAFullSetDodgeMoreThanFortyPercent() throws IOException {
        List<String> tooHigh = new ArrayList<>();
        for (Path dataDir : List.of(projectRoot().resolve("src/main/resources/data"),
                projectRoot().resolve("src/generated/resources/data"))) {
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dataDir)) {
                for (Path file : files.filter(TraitDefinitionAuditTest::isTraitDefinition).sorted().toList()) {
                    JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    if (!root.isJsonObject() || !"forgeweave:evasion".equals(behaviorOf(file))) {
                        continue;
                    }
                    double chance = root.getAsJsonObject().get("chance").getAsDouble();
                    double fullSet = 1.0 - Math.pow(1.0 - chance, 4);
                    if (fullSet > 0.40) {
                        tooHigh.add(file.getFileName() + " rolls " + chance + " a piece, so a full set dodges "
                                + Math.round(fullSet * 100) + "%");
                    }
                }
            }
        }
        assertTrue(tooHigh.isEmpty(), "evasion is rolled once per worn piece, so a full set compounds: "
                + tooHigh);
    }

    /**
     * Checklist lines 73, 75, 87 and 93: the set figure a worn trait's sentence quotes is what four
     * pieces of it reach, worked out again from the trait's own parameters rather than read back from
     * the {@code description_args} beside them.
     *
     * <p>The aggregation is docs/agents/description-style.md rule 5. Protection and movement add a
     * piece's value four times. Evasion and an effect on the attacker roll once per piece, so a set
     * answers {@code 1 - (1 - chance)^4} of the blows, quoted as a whole percent. Knockback
     * resistance pays each piece {@link ForgeweaveTraits#WORN_KNOCKBACK_RESISTANCE_SHARE} of the held
     * figure, so the one number the sentence gives is the set's as well as the held tool's.
     */
    @Test
    void everyWornSetFigureADescriptionQuotesIsWhatFourPiecesReach() throws IOException {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, TraitDefinition> entry : shippedDefinitions().entrySet()) {
            List<Double> expected = wornFigures(entry.getValue().trait());
            if (expected == null) {
                continue;
            }
            checked++;
            List<String> quoted = entry.getValue().rung().descriptionArgs();
            if (!sameFigures(quoted, expected)) {
                wrong.add(entry.getKey() + " quotes " + quoted + ", its own parameters give " + expected);
            }
        }
        assertTrue(checked >= 15, "expected the shipped worn traits, checked only " + checked);
        assertEquals(List.of(), wrong, "a worn trait's description_args have to be what its parameters add up to "
                + "on one piece and on a full set (docs/agents/description-style.md rule 5)");
    }

    /**
     * Checklist line 73 and docs/agents/description-style.md rule 5: movement adds per piece. Each
     * worn slot adds its own modifier, so a speed kind on {@code ADD_MULTIPLIED_TOTAL} would multiply
     * {@code (1 + m)^4} (swiftward's 8% a piece would be 36% for a set, not 32%), and
     * {@link #wornFigures} works the set out that way for any such kind. The speed kinds use
     * {@code ADD_MULTIPLIED_BASE}, which adds.
     */
    @Test
    void aMovementBonusSetReachesTheFigureItsDescriptionQuotes() throws IOException {
        List<String> wrong = new ArrayList<>();
        for (MovementBonus.Kind kind : MovementBonus.Kind.values()) {
            assertNotEquals(AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, kind.operation(),
                    kind + " would compound across worn slots instead of adding");
        }
        for (Map.Entry<String, TraitDefinition> entry : shippedDefinitions().entrySet()) {
            if (!(entry.getValue().trait() instanceof MovementBonus)) {
                continue;
            }
            List<Double> expected = wornFigures(entry.getValue().trait());
            List<String> quoted = entry.getValue().rung().descriptionArgs();
            if (!sameFigures(quoted, expected)) {
                wrong.add(entry.getKey() + " quotes " + quoted + ", a worn set really reaches " + expected);
            }
        }
        assertEquals(List.of(), wrong, "a movement bonus set must reach the figure its description quotes");
    }

    /**
     * Checklist line 77: a vein's extra block costs its own mining point plus
     * {@code durability_per_block}, and {@code max_blocks} does not count the first block. The
     * sentence quotes both as they play.
     */
    @Test
    void everyVeinBreakQuotesItsBlocksPastTheFirstAndTheirWholeCost() throws IOException {
        List<String> wrong = new ArrayList<>();
        int checked = 0;
        for (Map.Entry<String, TraitDefinition> entry : shippedDefinitions().entrySet()) {
            if (!(entry.getValue().trait() instanceof VeinBreak vein)) {
                continue;
            }
            checked++;
            List<Double> expected = List.of((double) vein.maxBlocks(), vein.durabilityPerBlock() + 1.0);
            List<String> quoted = entry.getValue().rung().descriptionArgs();
            if (!sameFigures(quoted, expected)) {
                wrong.add(entry.getKey() + " quotes " + quoted + ", it plays as " + expected);
            }
        }
        assertTrue(checked >= 2, "expected both veinseeker rungs, checked only " + checked);
        assertEquals(List.of(), wrong, "a vein break must quote its blocks past the first and each one's whole cost");
    }

    /**
     * Checklist line 149: the essence ladder's tool and armor rungs read their own name and numbers
     * through {@link TraitFamilies}, the path the tooltip takes, and leave no {@code %s} or bare
     * {@code %} behind. The three Mystical Agriculture materials name the rungs the checklist expects.
     */
    @Test
    void theEssenceLadderRendersEachRungsOwnNameAndNumbers() throws IOException {
        Map<ResourceLocation, TraitFamilies.Rung> rungs = new LinkedHashMap<>();
        shippedDefinitions().forEach((id, definition) -> rungs.put(forgeweave(id), definition.rung()));
        TraitFamilies.datapack(rungs);
        try {
            assertRendered("keen_edge", "Keen Edge", "Every hit lands 2 extra damage, whatever it hits.");
            assertRendered("keen_edge2", "Keen Edge II", "Every hit lands 3 extra damage, whatever it hits.");
            assertRendered("keen_edge4", "Keen Edge IV", "Every hit lands 5 extra damage, whatever it hits.");
            assertRendered("magic_protection", "Magic Protection",
                    "Turns magic aside: 4 protection on every piece worn, 16 across a full set.");
            assertRendered("magic_protection2", "Magic Protection II",
                    "Turns magic aside: 4.5 protection on every piece worn, 18 across a full set.");
            assertRendered("magic_protection3", "Magic Protection III",
                    "Turns magic aside: 5 protection on every piece worn, 20 across a full set.");
        } finally {
            TraitFamilies.datapack(Map.of());
        }

        assertMaterialTraits("inferium", "keen_edge", "magic_protection");
        assertMaterialTraits("imperium", "keen_edge2", "magic_protection2");
        assertMaterialTraits("insanium", "keen_edge4", "magic_protection3");
    }

    private static void assertRendered(String id, String name, String description) {
        String renderedName = ShippedLanguage.render(TraitFamilies.name(forgeweave(id)));
        String renderedDescription = ShippedLanguage.render(TraitFamilies.description(forgeweave(id)));
        assertEquals(name, renderedName, id + "'s name");
        assertEquals(description, renderedDescription, id + "'s description");
        assertFalse(renderedDescription.contains("%"), id + " left a format marker behind: " + renderedDescription);
    }

    private static void assertMaterialTraits(String material, String general, String armor) throws IOException {
        JsonObject traits = JsonParser.parseString(Files.readString(projectRoot().resolve(
                        "src/main/resources/data/forgeweave/forgeweave/material/" + material + ".json"),
                        StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonObject("traits");
        assertEquals("[\"forgeweave:" + general + "\"]", traits.get("general").toString(), material + " tool side");
        assertEquals("[\"forgeweave:" + armor + "\"]", traits.get("armor").toString(), material + " armor side");
    }

    /**
     * What one piece and a full set of {@code trait} come to, in the order its sentence quotes them,
     * or {@code null} for a behaviour that does not sum across worn pieces. Percentages are whole
     * numbers of percent; a compounded chance is rounded, the way its sentence says "about".
     *
     * <p>A movement bonus is summed the way the attribute system stacks four worn slots' modifiers:
     * {@code ADD_VALUE} and {@code ADD_MULTIPLIED_BASE} add, {@code ADD_MULTIPLIED_TOTAL} multiplies
     * {@code (1 + m)^4}.
     */
    private static List<Double> wornFigures(Trait trait) {
        if (trait instanceof TraitBehaviors.ProtectionDefinition protection) {
            return List.of((double) protection.amount(), 4.0 * protection.amount());
        }
        if (trait instanceof MovementBonus movement) {
            AttributeModifier.Operation operation = movement.kind().operation();
            double scale = operation == AttributeModifier.Operation.ADD_VALUE ? 1.0 : 100.0;
            double set = operation == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ? Math.round(100.0 * (Math.pow(1.0 + movement.magnitude(), 4) - 1.0))
                    : 4.0 * movement.magnitude() * scale;
            return List.of(movement.magnitude() * scale, set);
        }
        if (trait instanceof Evasion evasion) {
            return List.of(100.0 * evasion.chance(), (double) Math.round(100.0 * compounded(evasion.chance())));
        }
        if (trait instanceof EffectOnAttacker effect) {
            double seconds = effect.durationTicks() / 20.0;
            return effect.chance() >= 1.0F ? List.of(seconds) : List.of(100.0 * effect.chance(), seconds,
                    (double) Math.round(100.0 * compounded(effect.chance())));
        }
        if (trait instanceof KnockbackResistance knockback) {
            return List.of(100.0 * 4 * knockback.resistance() * ForgeweaveTraits.WORN_KNOCKBACK_RESISTANCE_SHARE);
        }
        return null;
    }

    /** What a full set of four independent rolls at {@code chance} answers. */
    private static double compounded(float chance) {
        return 1.0 - Math.pow(1.0 - chance, 4);
    }

    private static boolean sameFigures(List<String> quoted, List<Double> expected) {
        if (quoted.size() != expected.size()) {
            return false;
        }
        for (int i = 0; i < quoted.size(); i++) {
            if (Math.abs(Double.parseDouble(quoted.get(i).replace(",", "")) - expected.get(i)) > 0.005) {
                return false;
            }
        }
        return true;
    }

    /** Every shipped {@code trait_definition}, parsed, by file name. */
    private static Map<String, TraitDefinition> shippedDefinitions() throws IOException {
        Map<String, TraitDefinition> definitions = new LinkedHashMap<>();
        for (Path dataDir : List.of(projectRoot().resolve("src/main/resources/data"),
                projectRoot().resolve("src/generated/resources/data"))) {
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(dataDir)) {
                for (Path file : files.filter(TraitDefinitionAuditTest::isTraitDefinition).sorted().toList()) {
                    JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                    definitions.put(file.getFileName().toString().replace(".json", ""),
                            TraitDefinition.CODEC.parse(ops, json).getOrThrow());
                }
            }
        }
        return definitions;
    }

    private static ResourceLocation forgeweave(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    private static boolean isTraitDefinition(Path file) {
        String path = file.toString().replace('\\', '/');
        return path.contains("/forgeweave/trait_definition/") && path.endsWith(".json");
    }

    private static String behaviorOf(Path file) throws IOException {
        JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
        if (!root.isJsonObject()) {
            return null;
        }
        JsonElement behavior = root.getAsJsonObject().get("behavior");
        return behavior != null && behavior.isJsonPrimitive() ? behavior.getAsString() : null;
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
}
