package dev.gkissel.forgeweave.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * D-M8-8 (issue #968): the one flat {@code forgeweave-server.toml} becoming four files under
 * {@code config/forgeweave/}, and the thing that would be easy to get quietly wrong -- a pack's
 * tuned values surviving the move. Silently resetting them is the config equivalent of eating a
 * save, so this pins that every option a populated old file held comes out the other side holding
 * the same value.
 *
 * <p>The fixture is built by walking the four specs rather than by listing options, so an option
 * added later is covered here without anyone remembering to add it -- which is the failure mode a
 * hand-written fixture has. Each option gets a value distinct from its own default
 * ({@link #distinct}), so "the value was carried" and "the value was reset to its default" cannot
 * both pass.
 */
class ForgeweaveConfigMigrationTest {

    @Test
    void aPopulatedFlatFileKeepsEveryValueAcrossTheSplit(@TempDir Path configDir) throws IOException {
        Map<String, Map<List<String>, Object>> expected = new LinkedHashMap<>();
        CommentedConfig flat = TomlFormat.newConfig();
        for (Map.Entry<String, ModConfigSpec> file : ForgeweaveConfigMigration.FILES.entrySet()) {
            Map<List<String>, Object> perFile = new LinkedHashMap<>();
            plant(file.getValue().getSpec(), flat, perFile, new ArrayList<>());
            expected.put(file.getKey(), perFile);
        }
        assertFalse(expected.values().stream().allMatch(Map::isEmpty), "the fixture must hold something");

        Path legacy = configDir.resolve(ForgeweaveConfigMigration.LEGACY_FILE);
        TomlFormat.instance().createWriter().write(flat, legacy, WritingMode.REPLACE);

        ForgeweaveConfigMigration.run(configDir);

        for (Map.Entry<String, Map<List<String>, Object>> file : expected.entrySet()) {
            Path split = configDir.resolve(file.getKey());
            assertTrue(Files.isRegularFile(split), file.getKey() + " must have been written");
            UnmodifiableConfig carried = TomlFormat.instance().createParser()
                    .parse(split, FileNotFoundAction.THROW_ERROR);
            for (Map.Entry<List<String>, Object> option : file.getValue().entrySet()) {
                assertEquals(option.getValue(), carried.getRaw(option.getKey()),
                        option.getKey() + " must survive the split into " + file.getKey());
            }
        }

        assertFalse(Files.exists(legacy), "the old flat file must not be left where NeoForge would read it");
        assertTrue(Files.isRegularFile(configDir.resolve(ForgeweaveConfigMigration.MIGRATED_FILE)),
                "it must be renamed rather than deleted, so a pack operator can still read it");
    }

    /** No old file means nothing to carry, and in particular nothing written and nothing thrown. */
    @Test
    void noFlatFileLeavesTheFolderAlone(@TempDir Path configDir) {
        ForgeweaveConfigMigration.run(configDir);
        assertFalse(Files.exists(configDir.resolve(ForgeweaveConfigMigration.FOLDER)),
                "a fresh install must be left to NeoForge's own defaults");
    }

    /** A file that is already there is a pack operator's, and is never overwritten. */
    @Test
    void anExistingSplitFileIsNotOverwritten(@TempDir Path configDir) throws IOException {
        Path legacy = configDir.resolve(ForgeweaveConfigMigration.LEGACY_FILE);
        Files.writeString(legacy, "[content]\nharvestTools = false\n");
        Path content = configDir.resolve(ForgeweaveConfigMigration.fileName("content"));
        Files.createDirectories(content.getParent());
        Files.writeString(content, "[content]\nharvestTools = true\n");

        ForgeweaveConfigMigration.run(configDir);

        assertTrue(TomlFormat.instance().createParser().parse(content, FileNotFoundAction.THROW_ERROR)
                        .<Boolean>getRaw(List.of("content", "harvestTools")),
                "the file that was already there must be left exactly as it was");
    }

    /** An unreadable old file is a logged giveup, never a crash, and never a rename. */
    @Test
    void aCorruptFlatFileIsLeftInPlace(@TempDir Path configDir) throws IOException {
        Path legacy = configDir.resolve(ForgeweaveConfigMigration.LEGACY_FILE);
        Files.writeString(legacy, "this is not = = toml [[[\n");

        ForgeweaveConfigMigration.run(configDir);

        assertTrue(Files.isRegularFile(legacy),
                "a file that could not be read must be left where its owner can still see it");
        assertFalse(Files.exists(configDir.resolve(ForgeweaveConfigMigration.MIGRATED_FILE)),
                "and must not be reported as migrated");
    }

    /**
     * Writes one distinct value per option of {@code specLevel} into {@code flat} at the path that
     * option had in the old flat file, and records what to expect at its new path.
     */
    private static void plant(UnmodifiableConfig specLevel, Config flat, Map<List<String>, Object> expected,
            List<String> path) {
        for (UnmodifiableConfig.Entry entry : specLevel.entrySet()) {
            path.add(entry.getKey());
            if (entry.getRawValue() instanceof UnmodifiableConfig branch) {
                plant(branch, flat, expected, path);
            } else {
                Object value = distinct(((ModConfigSpec.ValueSpec) entry.getRawValue()).getDefault());
                // The old file had no `general` table: those eleven options sat at its top level.
                List<String> was = "general".equals(path.get(0)) ? path.subList(1, path.size()) : path;
                flat.set(was, value);
                expected.put(List.copyOf(path), value);
            }
            path.remove(path.size() - 1);
        }
    }

    /**
     * A value that is not this option's default. Nothing validates it -- the migration copies raw
     * values and this test reads raw TOML back, so no range or list validator ever runs -- it only
     * has to differ from the default and survive a TOML round trip.
     */
    private static Object distinct(Object defaultValue) {
        if (defaultValue instanceof Boolean flag) {
            return !flag;
        }
        if (defaultValue instanceof Integer number) {
            return number + 1;
        }
        if (defaultValue instanceof Long number) {
            return number + 1L;
        }
        if (defaultValue instanceof Double number) {
            return number + 0.5D;
        }
        if (defaultValue instanceof List<?>) {
            return List.of("forgeweave:migration_probe");
        }
        if (defaultValue instanceof String text) {
            return text + "_probe";
        }
        throw new AssertionError("no distinct value known for a config default of type "
                + defaultValue.getClass() + "; teach this test about it");
    }
}
