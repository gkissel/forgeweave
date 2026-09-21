package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
