package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.api.combat.CombatSeam;
import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.item.PartItem;

/**
 * A trait a material grants has to have at least one hook that can run on a part kind that material
 * can actually build (issue #1092).
 *
 * <p>That is the class of bug #1091 found from the other end: {@code damage_floor} on a material
 * that makes no armour is inert, an armour attribute on a bowstring-only material is inert, and
 * nothing in the build said so. The check is mechanical, off the two registries rather than a
 * hand-written list: which hooks a trait overrides comes from reflection over {@link Trait} (and,
 * for a trait whose whole behaviour is a combat seam, over {@link CombatSeam}), and which part kinds
 * a material has stats for comes from its shipped JSON.
 *
 * <h2>The hook-to-side map</h2>
 *
 * <p>{@link #TRAIT_HOOK_SIDES} and {@link #SEAM_HOOK_SIDES} are the judgement in this file: for each
 * hook, whether the call site that drives it holds a tool, wears a piece, or does both. They were
 * read off the call sites once (see {@code docs/research/trait-audit.md} for the table and the
 * reasoning) and a new hook has to be added to one of them or the test fails, which is the point --
 * a hook nobody classified is a hook whose reachability nobody checked.
 *
 * <h2>What it cannot decide</h2>
 *
 * <p>Four things, listed in the audit document and deliberately not guessed at here:
 *
 * <ul>
 *   <li>A trait that overrides nothing at all (a marker such as {@code evolving}) has no hook to
 *       reach, so it is exempt rather than a failure.
 *   <li>A hook that is reachable from a side still says nothing about whether the trait's own
 *       <em>condition</em> can ever be met there. {@code dusksnare} needs a night sky; no registry
 *       knows that.
 *   <li>A seam built at runtime from per-stack state ({@code combatSeams} on a trait that reads the
 *       stack) is read from the instance the registry holds, which is the shipped parameterisation.
 *   <li>Traits reached through a KubeJS script or a partner mod's {@code TraitRegistry} are not in
 *       either registry at test time and are not covered.
 * </ul>
 */
class TraitReachabilityTest {

    /** Which half of the game a hook can run on. */
    private enum Side {
        TOOL,
        ARMOR
    }

    private static final Set<Side> BOTH = EnumSet.allOf(Side.class);
    private static final Set<Side> TOOL = EnumSet.of(Side.TOOL);
    private static final Set<Side> ARMOR = EnumSet.of(Side.ARMOR);

    /**
     * Every {@link Trait} hook and the side its call site runs on. A tool-side hook is driven by
     * {@code ToolItem}, {@code BowItem}, {@code ToolAssemblyRecipes} or the attacking half of
     * {@code CombatSeams}; an armour-side hook by {@code ArmorPieceItem} or one of
     * {@code ForgeweaveTraits}' worn-piece walks.
     */
    private static final Map<String, Set<Side>> TRAIT_HOOK_SIDES = Map.ofEntries(
            // ToolStats/ToolAssemblyRecipes, at assembly, off the head material only.
            Map.entry("headDurability", TOOL),
            // ToolItem#inventoryTick and ArmorPieceItem#inventoryTick both call it.
            Map.entry("inventoryTick", BOTH),
            // ToolAssemblyRecipes' repair loop, which armour pieces share.
            Map.entry("repairBonus", BOTH),
            Map.entry("attackDamageBonus", TOOL),
            Map.entry("bonusDamageAgainst", TOOL),
            Map.entry("miningSpeed", TOOL),
            Map.entry("afterBlockBreak", TOOL),
            Map.entry("attackSpeedBonus", TOOL),
            Map.entry("drawSpeedBonus", TOOL),
            Map.entry("afterHit", TOOL),
            Map.entry("attackDurabilityBonus", TOOL),
            // Read inside ForgeweaveTraits#inventoryTick, which runs for a worn piece too.
            Map.entry("magneticLevel", BOTH),
            Map.entry("killExperience", TOOL),
            Map.entry("blockBreakExperience", TOOL),
            // ForgeweaveModifiers#freeSlots takes any stack with a modifier list.
            Map.entry("bonusSlots", BOTH),
            Map.entry("onCombatHit", TOOL),
            Map.entry("movementSpeedBonus", TOOL),
            // ModifierApplication#retuneStats re-adds it for armour pieces too (#721).
            Map.entry("maxDurabilityBonus", BOTH),
            Map.entry("energyCapacity", BOTH),
            // ToolItem#damageKeepingItem, which ArmorPieceItem#damageItem delegates to (#721).
            Map.entry("durabilityDamage", BOTH),
            Map.entry("breakSpeed", TOOL),
            Map.entry("grantsSilkTouch", TOOL),
            Map.entry("zeroesAttackDamage", TOOL),
            Map.entry("autoSmelt", TOOL),
            // Depends on the seams it hands out; resolved through SEAM_HOOK_SIDES instead.
            Map.entry("combatSeams", Set.of()),
            // ToolItem#getDefaultAttributeModifiers, main hand.
            Map.entry("knockbackResistance", TOOL),
            // CombatSeams#armorPass walks held tools first (#729), then the four worn slots.
            Map.entry("onDefend", BOTH),
            Map.entry("armorAttributes", ARMOR),
            Map.entry("useOnBlock", TOOL),
            Map.entry("dropDestroyChance", TOOL),
            Map.entry("healingMultiplier", ARMOR),
            Map.entry("visibilityMultiplier", ARMOR),
            Map.entry("stateLines", BOTH));

    /** The same map for {@link CombatSeam}, reached through {@link Trait#combatSeams}. */
    private static final Map<String, Set<Side>> SEAM_HOOK_SIDES = Map.of(
            "preHit", TOOL,
            "onHit", TOOL,
            "postKill", TOOL,
            "knockback", TOOL,
            // CombatSeams#defensePass walks the defender's two hands only.
            "incomingHit", TOOL,
            "onDefend", BOTH);

    /** Part kinds that belong to a tool or weapon rather than to an armour piece. */
    private static final Set<PartItem.Kind> TOOL_KINDS = EnumSet.of(PartItem.Kind.HEAD, PartItem.Kind.HANDLE,
            PartItem.Kind.EXTRA, PartItem.Kind.BOW, PartItem.Kind.BOWSTRING, PartItem.Kind.SHAFT,
            PartItem.Kind.FLETCHING, PartItem.Kind.PROJECTILE);

    /** Part kinds that belong to an armour piece. */
    private static final Set<PartItem.Kind> ARMOR_KINDS = EnumSet.of(PartItem.Kind.PLATING, PartItem.Kind.MAILLE);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Nobody may add a {@link Trait} or {@link CombatSeam} hook without saying which side it runs on. */
    @Test
    void everyHookIsClassified() {
        assertTrue(TRAIT_HOOK_SIDES.keySet().containsAll(hookNames(Trait.class)),
                "a Trait hook is missing from TraitReachabilityTest.TRAIT_HOOK_SIDES. Read its call sites, decide "
                        + "whether a held tool, a worn piece or both can drive it, add the row, and add the trait "
                        + "to the hook-to-side table in docs/research/trait-audit.md: "
                        + minus(hookNames(Trait.class), TRAIT_HOOK_SIDES.keySet()));
        assertTrue(SEAM_HOOK_SIDES.keySet().containsAll(hookNames(CombatSeam.class)),
                "a CombatSeam hook is missing from TraitReachabilityTest.SEAM_HOOK_SIDES: "
                        + minus(hookNames(CombatSeam.class), SEAM_HOOK_SIDES.keySet()));
    }

    /**
     * A material may only name a trait id something implements. An id nothing implements is dropped
     * by {@link ForgeweaveTraits#of} with a one-line warning in the server log, so the grant is
     * silently inert and the station shows an untranslated key where the trait's name should be.
     *
     * <p>Issue #1092 found three: {@code seared_stone} named {@code searing} and
     * {@code fire_protection} and {@code necrotic_bone} named {@code necrotic}, all three modifier
     * ids rather than trait ids, copied over from the 1.20 clone's own material trait rows in #843
     * without the matching {@code Trait}. They now exist.
     */
    @Test
    void everyMaterialTraitIdResolvesToAnImplementation() throws IOException {
        Set<ResourceLocation> implemented = allTraits().keySet();
        List<String> unimplemented = new ArrayList<>();
        for (MaterialFacts material : materials()) {
            for (ResourceLocation id : material.grantedTraits().keySet()) {
                if (!implemented.contains(id)) {
                    unimplemented.add(material.name() + " grants " + id);
                }
            }
        }
        assertTrue(unimplemented.isEmpty(), "a material names a trait id nothing implements, so the grant does "
                + "nothing and the station shows a raw lang key. Add the Trait, add a trait_definition, or drop "
                + "the grant (issue #1092):\n  " + String.join("\n  ", unimplemented));
    }

    /**
     * The guard the issue asked for: every trait a shipped material names has a hook that can run on
     * at least one part kind that material actually builds.
     */
    @Test
    void everyMaterialTraitCanFireOnSomePartTheMaterialBuilds() throws IOException {
        Map<ResourceLocation, Set<Side>> traitSides = traitSides();
        List<MaterialFacts> materials = materials();
        assertFalse(materials.isEmpty(), "expected to find shipped materials to audit");

        List<String> stranded = new ArrayList<>();
        for (MaterialFacts material : materials) {
            for (Map.Entry<ResourceLocation, Set<Side>> granted : material.grantedTraits().entrySet()) {
                Set<Side> reachable = traitSides.get(granted.getKey());
                if (reachable == null || reachable.isEmpty()) {
                    continue; // unimplemented id, or a marker trait with no hooks at all
                }
                Set<Side> overlap = EnumSet.copyOf(granted.getValue());
                overlap.retainAll(reachable);
                if (overlap.isEmpty()) {
                    stranded.add(material.name() + " grants " + granted.getKey() + " on " + granted.getValue()
                            + " parts, but that trait only has hooks on " + reachable);
                }
            }
        }

        assertTrue(stranded.isEmpty(), "a material grants a trait it can never run. Either move the trait to a "
                + "material that builds the right parts, give the material the missing part kind, or swap the trait "
                + "for one whose hooks reach the side it is on (issue #1092, #1093):\n  "
                + String.join("\n  ", stranded));
    }

    /**
     * Writes the mechanical half of {@code docs/research/trait-audit.md} to {@code build/trait-audit/}
     * so the document's hook, side and per-material columns stay derived from the code rather than
     * typed by hand. Asserts nothing on its own; the guard above is the test.
     */
    @Test
    void writesTheMechanicalAuditTables() throws IOException {
        Map<ResourceLocation, Set<Side>> traitSides = traitSides();
        Map<ResourceLocation, List<String>> hooks = traitHooks();
        Path out = projectRoot().resolve("build/trait-audit");
        Files.createDirectories(out);

        StringBuilder traits = new StringBuilder("| Trait | Hooks it overrides | Tool side | Armor side |\n");
        traits.append("| --- | --- | --- | --- |\n");
        for (ResourceLocation id : new TreeMap<>(hooks).keySet().stream().toList()) {
            Set<Side> sides = traitSides.getOrDefault(id, Set.of());
            traits.append("| `").append(id.getPath()).append("` | ")
                    .append(hooks.get(id).isEmpty() ? "none" : "`" + String.join("`, `", hooks.get(id)) + "`")
                    .append(" | ").append(mark(sides.contains(Side.TOOL)))
                    .append(" | ").append(mark(sides.contains(Side.ARMOR))).append(" |\n");
        }
        Files.writeString(out.resolve("traits.md"), traits.toString(), StandardCharsets.UTF_8);

        StringBuilder byMaterial = new StringBuilder("| Material | Builds | Trait | Granted on | Works |\n");
        byMaterial.append("| --- | --- | --- | --- | --- |\n");
        for (MaterialFacts material : materials()) {
            String builds = material.buildsTools() && material.buildsArmor() ? "tools and armor"
                    : material.buildsTools() ? "tools only"
                    : material.buildsArmor() ? "armor only" : "nothing";
            for (Map.Entry<ResourceLocation, Set<Side>> granted : material.grantedTraits().entrySet()) {
                Set<Side> reachable = EnumSet.noneOf(Side.class);
                reachable.addAll(traitSides.getOrDefault(granted.getKey(), Set.of()));
                reachable.retainAll(granted.getValue());
                byMaterial.append("| `").append(material.name()).append("` | ").append(builds)
                        .append(" | `").append(granted.getKey().getPath()).append("` | ")
                        .append(sideNames(granted.getValue())).append(" | ")
                        .append(reachable.isEmpty() ? "**nowhere**" : sideNames(reachable)).append(" |\n");
            }
        }
        Files.writeString(out.resolve("materials.md"), byMaterial.toString(), StandardCharsets.UTF_8);
    }

    private static String mark(boolean yes) {
        return yes ? "yes" : "no";
    }

    private static String sideNames(Set<Side> sides) {
        if (sides.isEmpty()) {
            return "nowhere";
        }
        return sides.stream().map(side -> side == Side.TOOL ? "tool" : "armor").sorted().reduce((a, b) -> a + ", " + b)
                .orElseThrow();
    }

    // ------------------------------------------------------------------ the trait registries

    /** Every shipped trait id and the sides its hooks can run on. */
    private static Map<ResourceLocation, Set<Side>> traitSides() throws IOException {
        Map<ResourceLocation, Set<Side>> sides = new LinkedHashMap<>();
        allTraits().forEach((id, trait) -> sides.put(id, sidesOf(trait)));
        return sides;
    }

    /** Every shipped trait id and the {@link Trait} hooks its implementation overrides. */
    private static Map<ResourceLocation, List<String>> traitHooks() throws IOException {
        Map<ResourceLocation, List<String>> hooks = new LinkedHashMap<>();
        allTraits().forEach((id, trait) -> hooks.put(id, overridden(trait, Trait.class)));
        return hooks;
    }

    /** The Java roster plus every shipped {@code trait_definition}, keyed by id. */
    private static Map<ResourceLocation, Trait> allTraits() throws IOException {
        Map<ResourceLocation, Trait> all = new LinkedHashMap<>(javaTraits());
        all.putAll(datapackTraits());
        return all;
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, Trait> javaTraits() {
        try {
            var field = ForgeweaveTraits.class.getDeclaredField("REGISTRY");
            field.setAccessible(true);
            return (Map<ResourceLocation, Trait>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("ForgeweaveTraits.REGISTRY moved; the audit reads it to enumerate the "
                    + "built-in traits (issue #1092)", e);
        }
    }

    private static Map<ResourceLocation, Trait> datapackTraits() throws IOException {
        Map<ResourceLocation, Trait> traits = new LinkedHashMap<>();
        for (Path file : shippedFiles("/forgeweave/trait_definition/")) {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            TraitDefinition definition = TraitDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(message -> new AssertionError(file + ": " + message));
            traits.put(ResourceLocation.fromNamespaceAndPath("forgeweave",
                    file.getFileName().toString().replace(".json", "")), definition.trait());
        }
        return traits;
    }

    /** Which sides {@code trait} has a hook on, following {@link Trait#combatSeams} into its seams. */
    private static Set<Side> sidesOf(Trait trait) {
        Set<Side> sides = EnumSet.noneOf(Side.class);
        for (String hook : overridden(trait, Trait.class)) {
            sides.addAll(TRAIT_HOOK_SIDES.getOrDefault(hook, Set.of()));
            if (hook.equals("combatSeams")) {
                List<CombatSeam> seams = new ArrayList<>();
                trait.combatSeams(seams::add);
                for (CombatSeam seam : seams) {
                    for (String seamHook : overridden(seam, CombatSeam.class)) {
                        sides.addAll(SEAM_HOOK_SIDES.getOrDefault(seamHook, Set.of()));
                    }
                }
            }
        }
        return sides;
    }

    /** The {@code iface} hooks {@code implementation} overrides, in declaration order. */
    private static List<String> overridden(Object implementation, Class<?> iface) {
        List<String> names = new ArrayList<>();
        for (Method hook : iface.getDeclaredMethods()) {
            if (!hook.isDefault()) {
                continue;
            }
            try {
                Method actual = implementation.getClass().getMethod(hook.getName(), hook.getParameterTypes());
                if (!actual.getDeclaringClass().equals(iface)) {
                    names.add(hook.getName());
                }
            } catch (NoSuchMethodException e) {
                // A non-public implementation class: getMethod cannot see it, so fall back to the
                // declared methods of the class and its supertypes.
                if (declaresDeeply(implementation.getClass(), hook)) {
                    names.add(hook.getName());
                }
            }
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private static boolean declaresDeeply(Class<?> type, Method hook) {
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            try {
                current.getDeclaredMethod(hook.getName(), hook.getParameterTypes());
                return true;
            } catch (NoSuchMethodException ignored) {
                // keep walking
            }
        }
        return false;
    }

    private static Set<String> hookNames(Class<?> iface) {
        Set<String> names = new LinkedHashSet<>();
        for (Method method : iface.getDeclaredMethods()) {
            if (method.isDefault()) {
                names.add(method.getName());
            }
        }
        return names;
    }

    private static String minus(Set<String> all, Set<String> known) {
        Set<String> missing = new LinkedHashSet<>(all);
        missing.removeAll(known);
        return missing.toString();
    }

    // ------------------------------------------------------------------ the material registry

    /**
     * One shipped material: which part kinds it has stats for, and which trait it grants on which
     * side. Read straight off the JSON rather than through {@code Material.CODEC}, which would
     * need item and tag registries a unit test has no server for.
     */
    private record MaterialFacts(String name, boolean buildsTools, boolean buildsArmor,
            Map<ResourceLocation, Set<Side>> grantedTraits) {}

    private static List<MaterialFacts> materials() throws IOException {
        List<MaterialFacts> materials = new ArrayList<>();
        for (Path file : shippedFiles("/forgeweave/material/")) {
            JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
            if (!json.isJsonObject()) {
                continue;
            }
            materials.add(factsOf(file.getFileName().toString().replace(".json", ""), json.getAsJsonObject()));
        }
        return materials;
    }

    private static MaterialFacts factsOf(String name, JsonObject json) {
        Set<PartItem.Kind> built = EnumSet.noneOf(PartItem.Kind.class);
        if (json.has("head")) {
            built.add(PartItem.Kind.HEAD);
            built.add(PartItem.Kind.PROJECTILE); // auto-added upstream, see Material#hasStatsFor
        }
        if (json.has("handle")) {
            built.add(PartItem.Kind.HANDLE);
        }
        if (json.has("extra_durability")) {
            built.add(PartItem.Kind.EXTRA);
        }
        if (json.has("bow")) {
            built.add(PartItem.Kind.BOW);
        }
        if (json.has("bowstring")) {
            built.add(PartItem.Kind.BOWSTRING);
        }
        if (json.has("shaft")) {
            built.add(PartItem.Kind.SHAFT);
        }
        if (json.has("fletching")) {
            built.add(PartItem.Kind.FLETCHING);
        }
        if (json.has("plating")) {
            built.add(PartItem.Kind.PLATING);
        }
        if (json.has("maille") && json.get("maille").getAsBoolean()) {
            built.add(PartItem.Kind.MAILLE);
        }

        Map<ResourceLocation, Set<Side>> granted = new LinkedHashMap<>();
        for (PartItem.Kind kind : built) {
            Side side = ARMOR_KINDS.contains(kind) ? Side.ARMOR : Side.TOOL;
            for (ResourceLocation id : traitsForPart(json, kind)) {
                granted.computeIfAbsent(id, key -> EnumSet.noneOf(Side.class)).add(side);
            }
        }
        return new MaterialFacts(name, built.stream().anyMatch(TOOL_KINDS::contains),
                built.stream().anyMatch(ARMOR_KINDS::contains), granted);
    }

    /** {@code Material.Traits#forPart}, read off the raw JSON: a scoped list replaces the general one. */
    private static List<ResourceLocation> traitsForPart(JsonObject material, PartItem.Kind kind) {
        if (material.has("trait")) { // the pre-#94 single-trait shape
            return List.of(ResourceLocation.parse(material.get("trait").getAsString()));
        }
        JsonElement traits = material.get("traits");
        if (traits == null || !traits.isJsonObject()) {
            return List.of();
        }
        JsonObject scopes = traits.getAsJsonObject();
        String scope = switch (kind) {
            case HEAD -> "head";
            case SHAFT -> "shaft";
            case PROJECTILE -> "projectile";
            case PLATING, MAILLE -> "armor";
            default -> null;
        };
        List<ResourceLocation> scoped = scope == null ? List.of() : idList(scopes, scope);
        return scoped.isEmpty() ? idList(scopes, "general") : scoped;
    }

    private static List<ResourceLocation> idList(JsonObject object, String field) {
        JsonElement array = object.get(field);
        if (array == null || !array.isJsonArray()) {
            return List.of();
        }
        List<ResourceLocation> ids = new ArrayList<>();
        array.getAsJsonArray().forEach(element -> ids.add(ResourceLocation.parse(element.getAsString())));
        return ids;
    }

    // ------------------------------------------------------------------ files

    private static List<Path> shippedFiles(String underDirectory) throws IOException {
        List<Path> files = new ArrayList<>();
        for (Path dataDir : List.of(projectRoot().resolve("src/main/resources/data"),
                projectRoot().resolve("src/generated/resources/data"))) {
            if (!Files.isDirectory(dataDir)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(dataDir)) {
                walk.filter(file -> file.toString().replace('\\', '/').contains(underDirectory))
                        .filter(file -> file.toString().endsWith(".json"))
                        .sorted()
                        .forEach(files::add);
            }
        }
        return files;
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
