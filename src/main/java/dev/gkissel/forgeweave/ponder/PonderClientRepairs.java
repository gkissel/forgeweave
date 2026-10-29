package dev.gkissel.forgeweave.ponder;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

import net.neoforged.fml.config.ConfigTracker;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.RegisterPictureInPictureRenderersEvent;

import net.createmod.catnip.api.config.CatnipConfigRegistry;
import net.createmod.catnip.api.config.ConfigId;
import net.createmod.catnip.api.config.ConfigSide;
import net.createmod.catnip.api.config.definition.CatnipConfig;
import net.createmod.catnip.api.config.definition.ConfigValue;
import net.createmod.catnip.impl.neoforge.service.NeoForgeClientHooksHelper;
import net.createmod.ponder.impl.client.gui.PonderSceneRenderState;
import net.createmod.ponder.impl.client.gui.PonderSceneRenderer;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Repairs two things Ponder 1.0.52 and the Catnip library it embeds set up too late on 26.1, both
 * during client setup. Drop each part when Ponder does it itself.
 *
 * <p><b>The scene renderer.</b> Ponder draws a scene through a picture-in-picture renderer that
 * Catnip hands to {@link RegisterPictureInPictureRenderersEvent}. That event fires while the game
 * renderer is built, before client setup, so Ponder's renderer is never in Catnip's list by then
 * and every scene opens as an empty screen. {@link #registerSceneRenderer} registers it directly.
 *
 * <p><b>The client configs.</b> Without this, opening any Ponder scene crashes the game with
 * "Config value 'editingMode' accessed too early, not loaded yet". Two upstream bugs stack.
 * Ponder registers its client config during client setup, after FML 11 has already loaded every
 * client config, and FML never loads a late one. And even when a
 * config does load, Catnip copies each file value in through {@link ConfigValue#set}, which refuses
 * to run until the value already holds something, and nothing ever gives it a first value.
 *
 * <p>So, once every mod has finished client setup: give each still-empty value its default, then
 * open the config files FML skipped. Opening fires Catnip's own load listener, which now copies the
 * file values in, and it gives the spec a loaded config so a setting changed in Ponder's screen
 * saves instead of throwing.
 *
 * <p>Only reached behind {@code ModList.isLoaded("ponder")}, like {@link ForgeweavePonderPlugin}.
 */
public final class PonderClientRepairs {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final List<String> MOD_IDS = List.of("ponder", "catnip");

    private PonderClientRepairs() {}

    public static void onLoadComplete(FMLLoadCompleteEvent event) {
        for (String modId : MOD_IDS) {
            CatnipConfigRegistry.INSTANCE.get(new ConfigId(modId, ConfigSide.CLIENT)).ifPresent(PonderClientRepairs::seedDefaults);
            for (ModConfig config : ModConfigs.getModConfigs(modId)) {
                if (config.getType() == ModConfig.Type.CLIENT && config.getLoadedConfig() == null) {
                    open(config);
                }
            }
        }
    }

    /** Client only; skipped if Catnip already had Ponder's renderer when the event fired. */
    public static void registerSceneRenderer(RegisterPictureInPictureRenderersEvent event) {
        if (!NeoForgeClientHooksHelper.PIP_RENDERERS.containsKey(PonderSceneRenderState.class)) {
            event.register(PonderSceneRenderState.class, PonderSceneRenderer::new);
        }
    }

    private static void seedDefaults(CatnipConfig config) {
        for (ConfigValue<?> value : config.values()) {
            try {
                Field field = ConfigValue.class.getDeclaredField("value");
                field.setAccessible(true);
                if (field.get(value) == null) {
                    field.set(value, value.defaultValue);
                }
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.error("Could not give Catnip config value {} its default", value, e);
            }
        }
        config.nested().values().forEach(PonderClientRepairs::seedDefaults);
    }

    private static void open(ModConfig config) {
        try {
            Method openConfig = ConfigTracker.class.getDeclaredMethod("openConfig", ModConfig.class, Path.class, Path.class);
            openConfig.setAccessible(true);
            openConfig.invoke(null, config, FMLPaths.CONFIGDIR.get(), null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.error("Could not load the {} config {}; its defaults stay in use", config.getModId(), config.getFileName(), e);
        }
    }
}
