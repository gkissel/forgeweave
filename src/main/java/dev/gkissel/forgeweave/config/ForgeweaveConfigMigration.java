package dev.gkissel.forgeweave.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Carries a pre-{@link ForgeweaveConfig#GENERAL_SPEC} config file into the folder that replaced it
 * (D-M8-8, issue #968).
 *
 * <p>Forgeweave shipped one flat {@code config/forgeweave-server.toml} up to that point. The folder
 * split could have left it alone and started the four new files at their defaults, and that is the
 * wrong answer: silently resetting a pack's tuned config is the config equivalent of eating a save.
 * So the old file is read once, every value it holds is written into whichever of the four new files
 * now owns that option, and the old file is renamed to {@code forgeweave-server.toml.migrated} --
 * renamed rather than deleted, so a pack operator can still read what they had.
 *
 * <p>Runs from {@code Forgeweave}'s constructor, before the four {@code registerConfig} calls and so
 * long before NeoForge opens any of the files. Writing only the values means NeoForge's own
 * correction pass then fills in the defaults and every comment, exactly as it does for a file it
 * created itself, so this class never has to know what an option's comment or default is.
 *
 * <h2>Which file claims which option</h2>
 *
 * <p>Asked of the specs themselves rather than of a table here: {@link ModConfigSpec#getSpec()} is
 * the tree of every path a spec defines, so walking it says exactly which options a file owns and
 * stays right as options are added. Paths are unchanged by the split inside {@code content},
 * {@code compat} and {@code worldgen}; the one move is the eleven options that used to sit at the
 * top level and now sit under {@code general}, which is the single {@link #SHIFTED_SECTION} case
 * below.
 *
 * <p>A failure here is logged and swallowed. A corrupt or unreadable old file must not stop the game
 * from booting: the cost of giving up is a pack operator retyping their values, and the cost of
 * throwing is a mod that will not load.
 */
public final class ForgeweaveConfigMigration {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** The config subfolder the four files live in, and the prefix their registered names carry. */
    public static final String FOLDER = "forgeweave";

    /** The flat file this replaces. */
    public static final String LEGACY_FILE = "forgeweave-server.toml";

    /** What {@link #LEGACY_FILE} is renamed to once its values have been carried across. */
    public static final String MIGRATED_FILE = LEGACY_FILE + ".migrated";

    /**
     * The one section whose options were at the old file's top level. A new path of
     * {@code general.oreToIngotRatio} reads {@code oreToIngotRatio} out of the old file.
     */
    private static final String SHIFTED_SECTION = "general";

    /** The four files and the spec each one holds; {@code Forgeweave} registers exactly these. */
    public static final Map<String, ModConfigSpec> FILES = Map.of(
            fileName(SHIFTED_SECTION), ForgeweaveConfig.GENERAL_SPEC,
            fileName("content"), ForgeweaveConfig.CONTENT_SPEC,
            fileName("compat"), ForgeweaveConfig.COMPAT_SPEC,
            fileName("worldgen"), ForgeweaveConfig.WORLDGEN_SPEC);

    /** The name a section's file is registered under, relative to the config directory. */
    public static String fileName(String section) {
        return FOLDER + "/" + section + "-server.toml";
    }

    /**
     * Splits {@code configDir/forgeweave-server.toml} across the four new files, if it is still
     * there. Does nothing when the old file is absent, which is every install after the first boot
     * on this version and every fresh install.
     */
    public static void run(Path configDir) {
        Path legacy = configDir.resolve(LEGACY_FILE);
        if (!Files.isRegularFile(legacy)) {
            return;
        }
        try {
            CommentedConfig old = TomlFormat.instance().createParser()
                    .parse(legacy, FileNotFoundAction.THROW_ERROR);
            for (Map.Entry<String, ModConfigSpec> file : FILES.entrySet()) {
                write(configDir.resolve(file.getKey()), old, file.getValue());
            }
            Files.move(legacy, configDir.resolve(MIGRATED_FILE), StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Split {} into config/{}/ and renamed it to {}", LEGACY_FILE, FOLDER, MIGRATED_FILE);
        } catch (RuntimeException | IOException failure) {
            LOGGER.error("Could not split {} into config/{}/; the new files start at their defaults and {} is "
                    + "left in place so its values can be copied over by hand", LEGACY_FILE, FOLDER, LEGACY_FILE,
                    failure);
        }
    }

    /**
     * Writes {@code target} with whichever of {@code old}'s values {@code spec} claims. Skips a
     * target that already exists, so a half-finished migration or a hand-written file is never
     * overwritten, and skips writing at all when the old file held nothing this spec owns.
     */
    private static void write(Path target, UnmodifiableConfig old, ModConfigSpec spec) throws IOException {
        if (Files.exists(target)) {
            return;
        }
        CommentedConfig carried = TomlFormat.newConfig();
        copyClaimed(old, spec.getSpec(), carried, new ArrayList<>());
        if (carried.isEmpty()) {
            return;
        }
        Files.createDirectories(target.getParent());
        TomlFormat.instance().createWriter().write(carried, target, WritingMode.REPLACE);
    }

    /**
     * Walks {@code specLevel}'s paths and copies each one's old value into {@code out}. A branch in
     * the spec tree is another {@link UnmodifiableConfig}; anything else is a leaf, i.e. one option.
     */
    private static void copyClaimed(UnmodifiableConfig old, UnmodifiableConfig specLevel, Config out,
            List<String> path) {
        for (UnmodifiableConfig.Entry entry : specLevel.entrySet()) {
            path.add(entry.getKey());
            if (entry.getRawValue() instanceof UnmodifiableConfig branch) {
                copyClaimed(old, branch, out, path);
            } else {
                List<String> was = legacyPath(path);
                if (old.contains(was)) {
                    out.set(path, old.getRaw(was));
                }
            }
            path.remove(path.size() - 1);
        }
    }

    /** Where {@code path} lived in the flat file: under {@code general}, one level up; else itself. */
    private static List<String> legacyPath(List<String> path) {
        return path.size() > 1 && SHIFTED_SECTION.equals(path.get(0))
                ? path.subList(1, path.size())
                : path;
    }

    private ForgeweaveConfigMigration() {}
}
