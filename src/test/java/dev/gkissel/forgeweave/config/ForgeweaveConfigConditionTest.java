package dev.gkissel.forgeweave.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.conditions.ICondition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issue #995: {@link ForgeweaveConfigCondition}'s own logic, independent of a loaded {@code
 * ModConfigSpec} -- flipping {@link ForgeweaveConfig#CREATE_RECIPES} and its three siblings needs a
 * {@code Config} object {@code ModConfigSpec.ConfigValue#set} refuses to work without (a plain JUnit
 * run never loads one, per {@code ConfigValue.set}'s own "Cannot set config value without assigned
 * Config object present"), so the actual on/off round trip for all four toggles is a GameTest instead
 * -- see {@code ProcessingCompatToggleGameTests}, which runs inside a real loaded world the same way
 * {@code CompatToggleGameTests} exercises every other {@code compat} toggle.
 *
 * <p>What stays here: every default reads true when nothing on disk says otherwise, an unrecognized
 * toggle key fails loudly instead of silently resolving either way, and -- the PR #1028 review fix --
 * {@link ForgeweaveConfigCondition#readToggleFromDisk} reads a toggle straight out of a
 * {@code compat-server.toml} on disk correctly with no spec loaded at all, which is the actual state
 * of the world the first time this condition is ever asked anything (see that method's own javadoc).
 * The config directory is passed in explicitly rather than read from {@link
 * net.neoforged.fml.loading.FMLPaths}, so these tests point it at {@code @TempDir} instead of the
 * real one.
 */
class ForgeweaveConfigConditionTest {

    private static boolean evaluate(String toggle) {
        return new ForgeweaveConfigCondition(toggle).test(ICondition.IContext.TAGS_INVALID);
    }

    private static void writeCompatToml(Path configDir, String key, boolean value) throws IOException {
        Path file = configDir.resolve(ForgeweaveConfigMigration.FOLDER).resolve("compat-server.toml");
        Files.createDirectories(file.getParent());
        CommentedConfig config = TomlFormat.newConfig();
        config.set(List.of("compat", key), value);
        TomlFormat.instance().createWriter().write(config, file, WritingMode.REPLACE);
    }

    @Test
    void everyToggleDefaultsToTrueWhenTheSpecIsNotLoaded() {
        assertTrue(evaluate("createRecipes"));
        assertTrue(evaluate("immersiveEngineeringRecipes"));
        assertTrue(evaluate("enderIoRecipes"));
        assertTrue(evaluate("powahHeatSources"));
        // Issue #998 (D-M8-19): elementariumMaterials, added after the four above.
        assertTrue(evaluate("elementariumMaterials"));
    }

    @Test
    void anUnknownToggleKeyThrowsRatherThanSilentlyResolvingEitherWay() {
        assertThrows(IllegalArgumentException.class, () -> evaluate("notARealToggle"));
    }

    @Test
    void readToggleFromDiskAnswersFalseWhenTheFileOnDiskSaysSo(@TempDir Path configDir) throws IOException {
        writeCompatToml(configDir, "createRecipes", false);
        assertFalse(ForgeweaveConfigCondition.readToggleFromDisk(configDir, "createRecipes"));
    }

    @Test
    void readToggleFromDiskAnswersTrueWhenTheFileOnDiskSaysSo(@TempDir Path configDir) throws IOException {
        writeCompatToml(configDir, "createRecipes", true);
        assertTrue(ForgeweaveConfigCondition.readToggleFromDisk(configDir, "createRecipes"));
    }

    @Test
    void readToggleFromDiskDefaultsToTrueWhenTheFileIsMissing(@TempDir Path configDir) {
        assertTrue(ForgeweaveConfigCondition.readToggleFromDisk(configDir, "createRecipes"));
    }

    @Test
    void readToggleFromDiskDefaultsToTrueWhenTheKeyIsMissing(@TempDir Path configDir) throws IOException {
        // A real file, but naming a different toggle -- createRecipes itself is absent from it.
        writeCompatToml(configDir, "immersiveEngineeringRecipes", false);
        assertTrue(ForgeweaveConfigCondition.readToggleFromDisk(configDir, "createRecipes"));
    }

    @Test
    void readToggleFromDiskDefaultsToTrueOnAParseFailure(@TempDir Path configDir) throws IOException {
        Path file = configDir.resolve(ForgeweaveConfigMigration.FOLDER).resolve("compat-server.toml");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "this is not valid = = toml [[[");
        assertTrue(ForgeweaveConfigCondition.readToggleFromDisk(configDir, "createRecipes"));
    }

    /**
     * Issue #998 (D-M8-19): the exact scenario a first attempt at this toggle
     * ({@code ElementariumEnabledCondition}, since replaced) got wrong -- a pack sets {@code
     * elementariumMaterials = false} on disk, and this is asked before any {@code ModConfigSpec} has
     * loaded (every generated Elementarium material's own real-world case, since materials are a
     * datapack registry loaded before {@code ForgeweaveConfig.loaded()} is ever true).
     */
    @Test
    void readToggleFromDiskAnswersFalseForElementariumMaterialsWhenTheFileSaysSo(@TempDir Path configDir)
            throws IOException {
        writeCompatToml(configDir, "elementariumMaterials", false);
        assertFalse(ForgeweaveConfigCondition.readToggleFromDisk(configDir, "elementariumMaterials"));
    }

    /**
     * Playtest checklist 1.0.0, line 139: the checklist tells a tester to turn off "the twenty
     * toggles" of {@code compat-server.toml} and lists them by name. This pins that list, in the
     * checklist's own order, against every on/off option {@link ForgeweaveConfig#COMPAT_SPEC}
     * defines. The file's numeric tuning values (energy costs, buffer sizes and the like) are not
     * toggles and are left out. A toggle added to or renamed in the spec fails here until the
     * checklist line names it too.
     */
    @Test
    void theCompatSpecHasExactlyTheTwentyTogglesTheChecklistNames() {
        List<String> checklist = List.of("draconicFusion", "draconicModules", "overlays", "kubejsTraits",
                "createGoggles", "apotheosisSockets", "energizedTank", "powahModifiers", "occultismRituals",
                "apotheosisAffixes", "apotheosisEnchanting", "createRecipes", "immersiveEngineeringRecipes",
                "enderIoRecipes", "powahHeatSources", "modifierDefinitions", "allthemodiumTiers",
                "elementariumMaterials", "mekanismModules", "mysticalAgricultureAugments");
        List<String> spec = new ArrayList<>();
        collectOptions(ForgeweaveConfig.COMPAT_SPEC.getSpec(), "", spec);

        assertEquals(20, checklist.size());
        assertEquals(checklist.stream().map(key -> "compat." + key).sorted().toList(), spec.stream().sorted().toList(),
                "compat-server.toml's options must be exactly the twenty the checklist names, all under [compat]");
    }

    private static void collectOptions(UnmodifiableConfig level, String prefix, List<String> out) {
        for (UnmodifiableConfig.Entry entry : level.entrySet()) {
            String path = prefix + entry.getKey();
            if (entry.getRawValue() instanceof UnmodifiableConfig branch) {
                collectOptions(branch, path + ".", out);
            } else if (entry.getRawValue() instanceof ModConfigSpec.ValueSpec value
                    && value.getDefault() instanceof Boolean) {
                out.add(path);
            }
        }
    }
}
