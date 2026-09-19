package dev.gkissel.forgeweave.compat.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;

import dev.gkissel.forgeweave.item.AmmoToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Issue #1046: walks {@link ToolAssemblyRecipes#BUILT_IN} -- the shipped half of the same table the
 * Tool Station itself assembles from, so a new tool or weapon issue inherits this check with no
 * second roster to keep in sync -- and confirms every Forgeweave weapon or tool has a
 * {@code scripts/generate_combat_movesets.py}-written Better Combat and Epic Fight file, every
 * armor piece has an Epic Fight armor file, the two ammo items ({@code shuriken}, {@code arrow}) are
 * deliberately excluded rather than merely forgotten, and no file exists for an id the registry
 * doesn't know. Neither mod is on this project's test classpath (both are data-only, no
 * {@code compat/<mod>/} Java seam -- see the generator script's own javadoc for why), so this is the
 * same shape of coverage {@code GeneratedProcessingRecipeTest} gives the M8-11 compat recipes: what a
 * test that never sees the real mod can pin.
 */
class GeneratedCombatMovesetTest {

    private static final Path DATA_ROOT = projectRoot().resolve("src/main/resources/data/forgeweave");
    private static final Path BC_DIR = DATA_ROOT.resolve("weapon_attributes");
    private static final Path EF_WEAPON_DIR = DATA_ROOT.resolve("capabilities/weapons");
    private static final Path EF_ARMOR_DIR = DATA_ROOT.resolve("capabilities/armors");

    /** The 33 confirmed Better Combat presets -- see the generator script's own copy and citation. */
    private static final Set<String> BC_PRESETS = Set.of(
            "anchor", "axe", "battlestaff", "bow_two_handed_heavy", "bow_two_handed_light", "claw",
            "claymore", "coral_blade", "crossbow_two_handed_heavy", "crossbow_two_handed_light", "cutlass",
            "dagger", "double_axe", "fist", "glaive", "halberd", "hammer", "heavy_axe", "katana", "lance",
            "mace", "pickaxe", "rapier", "scythe", "sickle", "soul_knife", "spear", "staff", "sword",
            "trident", "twin_blade", "vanilla_mace", "wand");

    /** The 17 confirmed Epic Fight built-in weapon types -- see the generator script's own copy and citation. */
    private static final Set<String> EF_TYPES = Set.of(
            "axe", "sword", "greatsword", "longsword", "uchigatana", "dagger", "trident", "shield",
            "pickaxe", "shovel", "hoe", "bokken", "spear", "tachi", "fist", "bow", "crossbow");

    /** Deliberately excluded: neither is ever swung in melee (see the generator script's own javadoc). */
    private static final Set<String> EXPECTED_AMMO_IDS = Set.of("shuriken", "arrow");

    /**
     * Vanilla parity (maintainer decision, 2026-09-18): Epic Fight ships no capability for vanilla's
     * mace, the war mace delegates to that item, so it must have no Epic Fight weapons file either.
     */
    private static final Set<String> NO_EPIC_FIGHT_FILE = Set.of("warmace");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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

    private static Set<String> idsInDir(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            return Set.of();
        }
        try (Stream<Path> walk = Files.list(dir)) {
            return walk.filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .collect(Collectors.toCollection(TreeSet::new));
        }
    }

    private static JsonObject parse(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    @Test
    void everyRegisteredToolHasTheRightGeneratedFiles() throws IOException {
        Set<String> weaponIds = new TreeSet<>();
        Set<String> armorIds = new TreeSet<>();
        Set<String> ammoIds = new HashSet<>();

        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.BUILT_IN) {
            String id = entry.constants().id();
            if (entry.constants().category() == ToolConstants.Category.ARMOR) {
                armorIds.add(id);
                continue;
            }
            Item item = entry.tool().get();
            if (item instanceof AmmoToolItem) {
                ammoIds.add(id);
                continue;
            }
            weaponIds.add(id);
        }

        assertEquals(EXPECTED_AMMO_IDS, ammoIds,
                "the set of AmmoToolItem entries changed -- decide deliberately whether the new one needs "
                        + "a combat moveset, then update this test and EXPECTED_AMMO_IDS together");

        // Every weapon/tool: a Better Combat weapon_attributes file and an Epic Fight weapons file.
        for (String id : weaponIds) {
            Path bcFile = BC_DIR.resolve(id + ".json");
            assertTrue(Files.isRegularFile(bcFile), "missing Better Combat weapon_attributes file for " + id);
            JsonObject bc = parse(bcFile);
            assertTrue(bc.has("parent"), id + ": weapon_attributes file has no \"parent\"");
            String bcParent = bc.get("parent").getAsString();
            assertTrue(bcParent.startsWith("bettercombat:"), id + ": parent " + bcParent + " is not a bettercombat: id");
            String bcPreset = bcParent.substring("bettercombat:".length());
            assertTrue(BC_PRESETS.contains(bcPreset), id + ": " + bcPreset + " is not a confirmed Better Combat preset");

            Path efFile = EF_WEAPON_DIR.resolve(id + ".json");
            if (NO_EPIC_FIGHT_FILE.contains(id)) {
                assertFalse(Files.isRegularFile(efFile), id + ": must fight like vanilla's mace under Epic Fight, with no capability file");
                continue;
            }
            assertTrue(Files.isRegularFile(efFile), "missing Epic Fight capabilities/weapons file for " + id);
            JsonObject ef = parse(efFile);
            assertTrue(ef.has("type"), id + ": capabilities/weapons file has no \"type\"");
            String efType = ef.get("type").getAsString();
            assertTrue(efType.startsWith("epicfight:"), id + ": type " + efType + " is not an epicfight: id");
            String efTypeId = efType.substring("epicfight:".length());
            assertTrue(EF_TYPES.contains(efTypeId), id + ": " + efTypeId + " is not a confirmed Epic Fight weapon type");
        }

        // Every armor piece: an Epic Fight armors file (weight + stun_armor), no weapons file, and
        // Better Combat gets nothing at all -- it has no armor schema (see the generator's javadoc).
        for (String id : armorIds) {
            assertFalse(Files.isRegularFile(BC_DIR.resolve(id + ".json")),
                    id + ": Better Combat has no armor schema, this file should not exist");
            assertFalse(Files.isRegularFile(EF_WEAPON_DIR.resolve(id + ".json")),
                    id + ": armor should not have an Epic Fight weapons file");

            Path armorFile = EF_ARMOR_DIR.resolve(id + ".json");
            assertTrue(Files.isRegularFile(armorFile), "missing Epic Fight capabilities/armors file for " + id);
            JsonObject armor = parse(armorFile).getAsJsonObject("attributes");
            assertTrue(armor.has("weight") && armor.get("weight").getAsDouble() > 0, id + ": weight must be positive");
            assertTrue(armor.has("stun_armor") && armor.get("stun_armor").getAsDouble() > 0, id + ": stun_armor must be positive");

            if (ToolConstants.isHeavy(id)) {
                String lightId = id.substring(ToolConstants.HEAVY_PREFIX.length());
                double heavyWeight = armor.get("weight").getAsDouble();
                double lightWeight = parse(EF_ARMOR_DIR.resolve(lightId + ".json"))
                        .getAsJsonObject("attributes").get("weight").getAsDouble();
                assertTrue(heavyWeight > lightWeight,
                        id + ": the heavy set must be heavier than " + lightId + " (" + heavyWeight + " vs " + lightWeight + ")");
            }
        }

        // Ammo gets nothing anywhere: excluded deliberately, not forgotten.
        for (String id : ammoIds) {
            assertFalse(Files.isRegularFile(BC_DIR.resolve(id + ".json")), id + ": ammo should have no Better Combat file");
            assertFalse(Files.isRegularFile(EF_WEAPON_DIR.resolve(id + ".json")), id + ": ammo should have no Epic Fight weapons file");
        }

        // No orphan file for an id the registry doesn't know (a removed tool, a typo'd filename).
        assertEquals(weaponIds, idsInDir(BC_DIR), "weapon_attributes directory has files with no matching registered tool");
        Set<String> epicFightIds = new java.util.TreeSet<>(weaponIds);
        epicFightIds.removeAll(NO_EPIC_FIGHT_FILE);
        assertEquals(epicFightIds, idsInDir(EF_WEAPON_DIR), "capabilities/weapons directory has files with no matching registered tool");
        assertEquals(armorIds, idsInDir(EF_ARMOR_DIR), "capabilities/armors directory has files with no matching registered armor piece");
    }
}
