package dev.gkissel.forgeweave.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.FileNotFoundAction;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Issue #995 (D-M8-16, D-M8-13): a {@code neoforge:conditions} predicate reading one of the four
 * {@code compat} toggles {@link ForgeweaveConfig#CREATE_RECIPES}, {@link
 * ForgeweaveConfig#IMMERSIVE_ENGINEERING_RECIPES}, {@link ForgeweaveConfig#ENDER_IO_RECIPES} and
 * {@link ForgeweaveConfig#POWAH_HEAT_SOURCES} gate -- {@code {"type": "forgeweave:compat_toggle",
 * "toggle": "createRecipes"}}.
 *
 * <p>None of the four rows this issue generates is Forgeweave's own recipe type (they are {@code
 * create:mixing}, {@code immersiveengineering:arc_furnace}, {@code enderio:alloy_smelting} and a
 * Powah {@code data_maps} value), so none of them has a Forgeweave-owned lookup site to filter at the
 * way {@code ENABLE_CLAY_CASTS} filters {@link dev.gkissel.forgeweave.casting.CastingRecipe#matches}
 * or {@code DRACONIC_FUSION} filters {@code ForgeweaveDraconicCompat#acceptsFusionCatalyst}. This
 * condition is the smallest thing that reaches the same "off means the row does not resolve"
 * contract for a row Forgeweave does not own the matching code for -- one class, reused by all four
 * toggles, evaluated by NeoForge's own generic conditional-JSON loader (the same one every {@code
 * neoforge:mod_loaded} row in this codebase already goes through), not a bespoke parallel system.
 *
 * <h2>Why {@code test} does not simply call {@link ForgeweaveConfig#enabled}</h2>
 *
 * <p>Conditions run as part of the very first {@code ReloadableServerResources} build, which happens
 * while the server's {@code WorldStem} is created -- before {@code MinecraftServer.spin} constructs
 * the server object {@code ServerLifecycleHooks#handleServerAboutToStart} is called on, and it is
 * that call that loads {@code ModConfigSpec.Type.SERVER} configs (confirmed against NeoForge 21.1
 * source, PR #1028 review). So on every boot, {@link ForgeweaveConfig#loaded()} is still {@code
 * false} the first time this condition is asked anything, and {@link ForgeweaveConfig#enabled}'s
 * permissive-when-unloaded fallback would always answer {@code true} regardless of what a pack's
 * file actually says -- the toggle would only take effect after a manual {@code /reload} once the
 * spec catches up. {@link #readToggleFromDisk} is the fix: when the spec is not loaded yet, read
 * {@code compat.<toggle>} straight out of {@code config/forgeweave/compat-server.toml} with the same
 * night-config TOML parser {@link ForgeweaveConfigMigration} already uses, rather than trusting a
 * spec that has not opened the file yet. A missing file, a missing key, or a parse failure all mean
 * the declared default, {@code true} -- consistent with {@link ForgeweaveConfig#enabled}'s own
 * permissive-when-unknown contract, and safe because nothing here holds state (see below).
 *
 * <p><b>Known limit:</b> this only reads the global {@code config/forgeweave/compat-server.toml}. A
 * per-world {@code serverconfig/forgeweave/compat-server.toml} override -- the second path {@code
 * ConfigTracker.loadConfigs} also accepts -- is not visible yet at this point in startup either (the
 * save is not open), so a world-specific override of one of these four toggles is not honoured on
 * the very first datapack load, only after a subsequent {@code /reload}. No pack in this codebase
 * uses a per-world compat override today, and building a second loader for a case nothing exercises
 * is exactly the "conditional-recipe machinery" this class's own first paragraph already declines.
 *
 * <p>Off is inert, not destructive, the same as every other {@code compat} toggle: nothing here holds
 * state, so a row that stops resolving loses nothing, and it resolves again the moment the toggle
 * returns and the datapack reloads.
 */
public record ForgeweaveConfigCondition(String toggle) implements ICondition {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<String, BooleanSupplier> TOGGLES = Map.of(
            "createRecipes", () -> ForgeweaveConfig.enabled(ForgeweaveConfig.CREATE_RECIPES),
            "immersiveEngineeringRecipes", () -> ForgeweaveConfig.enabled(ForgeweaveConfig.IMMERSIVE_ENGINEERING_RECIPES),
            "enderIoRecipes", () -> ForgeweaveConfig.enabled(ForgeweaveConfig.ENDER_IO_RECIPES),
            "powahHeatSources", () -> ForgeweaveConfig.enabled(ForgeweaveConfig.POWAH_HEAT_SOURCES));

    public static final MapCodec<ForgeweaveConfigCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.STRING.fieldOf("toggle").forGetter(ForgeweaveConfigCondition::toggle))
            .apply(instance, ForgeweaveConfigCondition::new));

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Forgeweave.MODID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ForgeweaveConfigCondition>> COMPAT_TOGGLE =
            CONDITION_CODECS.register("compat_toggle", () -> CODEC);

    @Override
    public boolean test(IContext context) {
        if (!TOGGLES.containsKey(toggle)) {
            throw new IllegalArgumentException("unknown forgeweave compat toggle: " + toggle);
        }
        if (ForgeweaveConfig.loaded()) {
            return TOGGLES.get(toggle).getAsBoolean();
        }
        return readToggleFromDisk(FMLPaths.CONFIGDIR.get(), toggle);
    }

    /**
     * {@code compat.<toggle>} out of {@code <configDir>/forgeweave/compat-server.toml}, read directly
     * because the spec has not loaded that file yet (see this class's own javadoc). Package-private
     * and taking {@code configDir} as a parameter, rather than reading {@link FMLPaths#CONFIGDIR}
     * itself, purely so a test can point it at a temporary directory instead of the real one.
     */
    static boolean readToggleFromDisk(Path configDir, String toggle) {
        Path file = configDir.resolve(ForgeweaveConfigMigration.FOLDER).resolve("compat-server.toml");
        if (!Files.isRegularFile(file)) {
            return true;
        }
        try {
            CommentedConfig config = TomlFormat.instance().createParser().parse(file, FileNotFoundAction.THROW_ERROR);
            Object value = config.get(List.of("compat", toggle));
            return !(value instanceof Boolean bool) || bool;
        } catch (RuntimeException failure) {
            LOGGER.error("Could not read compat.{} from {} before the config spec loaded; treating it as true",
                    toggle, file, failure);
            return true;
        }
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "forgeweave:compat_toggle(\"" + toggle + "\")";
    }
}
