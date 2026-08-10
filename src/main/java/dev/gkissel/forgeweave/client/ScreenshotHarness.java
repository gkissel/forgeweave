package dev.gkissel.forgeweave.client;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.Supplier;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.StationMenuHost;

/**
 * Dev-only screenshot harness (docs/SCOPE.md issue #112): boots straight from the title screen into
 * a flat creative world, places each M1 station block in turn, opens its GUI the same way a player
 * does -- {@link StationMenuHost#open}, the exact call every station's right-click handler already
 * makes -- and grabs a real framebuffer PNG of each screen into {@code build/screenshots/}.
 *
 * <p>Exists because three GUI defects (issues #75, #85, #89) shipped in M1 past review that only
 * ever looked at offline PNG compositing, never the running client. This is not a CI gate -- see
 * {@code scripts/screenshots.sh} and docs/releasing.md's release-checklist step -- it is a tool a
 * reviewer runs by hand before a release.
 *
 * <h2>Why this is inert by default</h2>
 *
 * <p>Every entry point below is gated on {@link #ENABLED}, read once from the
 * {@code forgeweave.screenshot_harness} system property. A player's client never sets that property,
 * so this class does nothing but sit registered on the event bus -- same shape as the rest of the
 * mod's {@code @EventBusSubscriber} classes, just permanently a no-op off the flag.
 *
 * <h2>Extending for M2</h2>
 *
 * <p>The smeltery and casting screens land in M2 behind their own blocks. If their block entities
 * implement {@link StationMenuHost} the way every M1 station does, registering them here is one more
 * line in {@link #SCREENS}; nothing else in this class is screen-specific.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ScreenshotHarness {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String LOG_PREFIX = "[screenshot harness] ";

    /** See class javadoc; read once, so a player's client never pays even a system-property lookup per tick. */
    private static final boolean ENABLED = Boolean.getBoolean("forgeweave.screenshot_harness");
    /** Set by the {@code screenshotHarness} Gradle run; falls back to {@code <run dir>/screenshots}. */
    private static final String OUTPUT_DIR_PROPERTY = "forgeweave.screenshot_output_dir";

    private static final String LEVEL_NAME = "forgeweave_screenshot_harness";
    /** Ticks to let the fresh world finish loading chunks and lighting before anything is placed. */
    private static final int WORLD_SETTLE_TICKS = 40;
    /** Ticks to let a just-opened screen render before capture -- the issue's own "wait N ticks for render". */
    private static final int SCREEN_SETTLE_TICKS = 15;
    /** Blocks apart along +X so no station's GUI-open range ever spans a neighbor (issue #78 tabs). */
    private static final int SCREEN_SPACING = 4;

    /** One entry per station screen; see "Extending for M2" above. */
    private static final List<HarnessScreen> SCREENS = List.of(
            new HarnessScreen("part_builder", ForgeweaveBlocks.PART_BUILDER),
            new HarnessScreen("tool_station", ForgeweaveBlocks.TOOL_STATION),
            new HarnessScreen("crafting_station", ForgeweaveBlocks.CRAFTING_STATION),
            new HarnessScreen("stencil_table", ForgeweaveBlocks.STENCIL_TABLE));

    private enum Stage { AWAIT_TITLE, AWAIT_WORLD, SETTLE_WORLD, OPEN_SCREEN, SETTLE_SCREEN, DONE }

    private static Stage stage = Stage.AWAIT_TITLE;
    private static int stageTicks;
    private static int screenIndex;
    private static BlockPos origin;

    private ScreenshotHarness() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (!ENABLED) {
            return;
        }
        stageTicks++;
        Minecraft mc = Minecraft.getInstance();
        switch (stage) {
            case AWAIT_TITLE -> awaitTitle(mc);
            case AWAIT_WORLD -> awaitWorld(mc);
            case SETTLE_WORLD -> settleWorld();
            case OPEN_SCREEN -> openScreen(mc);
            case SETTLE_SCREEN -> settleScreen(mc);
            case DONE -> {}
        }
    }

    private static void awaitTitle(Minecraft mc) {
        if (!(mc.screen instanceof TitleScreen)) {
            return;
        }
        LOGGER.info("{}creating flat world '{}'", LOG_PREFIX, LEVEL_NAME);
        LevelSettings levelSettings = new LevelSettings(
                LEVEL_NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
        // Fixed seed, no structures, no bonus chest: a deterministic flat world is all the harness needs.
        WorldOptions worldOptions = new WorldOptions(0L, false, false);
        mc.createWorldOpenFlows().createFreshLevel(
                LEVEL_NAME, levelSettings, worldOptions, ScreenshotHarness::flatWorldDimensions, mc.screen);
        advance(Stage.AWAIT_WORLD);
    }

    private static WorldDimensions flatWorldDimensions(RegistryAccess registryAccess) {
        return registryAccess.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions();
    }

    private static void awaitWorld(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        if (mc.level == null || mc.player == null || server == null || !server.isReady()) {
            return; // World creation/login is asynchronous; keep polling every tick until it resolves.
        }
        origin = mc.player.blockPosition();
        LOGGER.info("{}world ready, settling before placing stations at {}", LOG_PREFIX, origin);
        advance(Stage.SETTLE_WORLD);
    }

    private static void settleWorld() {
        if (stageTicks >= WORLD_SETTLE_TICKS) {
            advance(Stage.OPEN_SCREEN);
        }
    }

    private static void openScreen(Minecraft mc) {
        if (screenIndex >= SCREENS.size()) {
            LOGGER.info("{}all {} screens captured, exiting", LOG_PREFIX, SCREENS.size());
            mc.stop();
            advance(Stage.DONE);
            return;
        }
        HarnessScreen screen = SCREENS.get(screenIndex);
        var server = mc.getSingleplayerServer();
        BlockPos pos = origin.offset((screenIndex + 1) * SCREEN_SPACING, 0, 0);
        LOGGER.info("{}placing and opening {}", LOG_PREFIX, screen.fileName());
        // Placing blocks and opening menus are server-side operations; singleplayer still runs its
        // integrated server on its own thread, so this must be scheduled rather than called directly.
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = serverPlayer.serverLevel();
            level.setBlockAndUpdate(pos, screen.block().get().defaultBlockState());
            // Within the menu's stillValid distance (vanilla's 8-block cap) so it doesn't auto-close.
            serverPlayer.teleportTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 1.5);
            if (level.getBlockEntity(pos) instanceof StationMenuHost host) {
                host.open(serverPlayer);
            } else {
                LOGGER.warn("{}{}'s block entity is not a StationMenuHost, skipping", LOG_PREFIX, screen.fileName());
            }
        });
        advance(Stage.SETTLE_SCREEN);
    }

    private static void settleScreen(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        capture(mc, SCREENS.get(screenIndex).fileName());
        if (mc.screen != null) {
            mc.screen.onClose(); // Same path Escape takes: closes the menu and notifies the server.
        }
        screenIndex++;
        advance(Stage.OPEN_SCREEN);
    }

    private static void capture(Minecraft mc, String fileName) {
        NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        try {
            File dir = outputDir(mc);
            dir.mkdirs();
            File file = new File(dir, fileName + ".png");
            image.writeToFile(file);
            LOGGER.info("{}wrote {}", LOG_PREFIX, file.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("{}failed to write {}", LOG_PREFIX, fileName, e);
        } finally {
            image.close();
        }
    }

    private static File outputDir(Minecraft mc) {
        String configured = System.getProperty(OUTPUT_DIR_PROPERTY);
        return configured != null ? new File(configured) : new File(mc.gameDirectory, "screenshots");
    }

    private static void advance(Stage next) {
        stage = next;
        stageTicks = 0;
    }

    /** One harness entry: the PNG's base filename, and the station block to place and open. */
    private record HarnessScreen(String fileName, Supplier<? extends Block> block) {}
}
