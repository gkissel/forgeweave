package dev.gkissel.forgeweave.client;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.StationMenuHost;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

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
 * <p>Before the GUI screens, it also captures one plain world-view PNG with no menu open --
 * {@code seared_tank_world.png}, a seared tank/gauge/window row at three different fill levels, the
 * release-checklist artifact for in-world block entity rendering (issue #145) the same way
 * {@link #SCREENS} is for GUIs.
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
    /** Ticks between closing one screen and opening the next; see {@link #openScreen}. */
    private static final int SCREEN_GAP_TICKS = 10;
    /**
     * Blocks apart along +X so no station's GUI-open range ever spans a neighbor (issue #78 tabs),
     * and -- since #146 added a second smeltery -- so two 5x5-footprint multiblocks never share a
     * wall.
     */
    private static final int SCREEN_SPACING = 8;
    /** How far in +Z the #145 tank/gauge/window row sits from the player's spawn point. */
    private static final int TANK_SCENE_DISTANCE = 6;
    /** Blocks apart along +X, wide enough that neighboring tanks don't visually overlap in frame. */
    private static final int TANK_SCENE_SPACING = 2;
    /**
     * Extra distance the camera backs off beyond the row itself, on top of {@link
     * #TANK_SCENE_DISTANCE}: at 4 blocks back with blocks only 2 apart, the flanking tank/window sit
     * at a steep enough angle from the gauge-centered lookAt that perspective could plausibly read as
     * a shorter/taller fill than they actually are. Backing off flattens that angle.
     */
    private static final int TANK_SCENE_CAMERA_PULLBACK = 6;

    /** One entry per station screen; see "Extending for M2" above. */
    private static final List<HarnessScreen> SCREENS = List.of(
            new HarnessScreen("part_builder", ForgeweaveBlocks.PART_BUILDER),
            new HarnessScreen("tool_station", ForgeweaveBlocks.TOOL_STATION),
            new HarnessScreen("crafting_station", ForgeweaveBlocks.CRAFTING_STATION),
            new HarnessScreen("stencil_table", ForgeweaveBlocks.STENCIL_TABLE),
            // #101: the smeltery is a multiblock, so unlike every M1 station it needs a structure
            // around the placed block before its GUI will open at all -- see buildSmeltery.
            new HarnessScreen("smeltery", ForgeweaveBlocks.STANDARD_CORE, ScreenshotHarness::buildSmeltery),
            // #146: the state the melt-grid defect was reported in -- a minimum-size smeltery (two
            // melt slots, one row) with nothing in the grid, which is what a playtest smeltery looks
            // like once everything in it has melted away.
            new HarnessScreen("smeltery_empty", ForgeweaveBlocks.STANDARD_CORE, ScreenshotHarness::buildEmptySmeltery));

    private enum Stage {
        AWAIT_TITLE, AWAIT_WORLD, SETTLE_WORLD, PLACE_TANK_SCENE, SETTLE_TANK_SCENE, OPEN_SCREEN, SETTLE_SCREEN, DONE
    }

    private static Stage stage = Stage.AWAIT_TITLE;
    private static int stageTicks;
    private static int screenIndex;
    private static BlockPos origin;
    /** Set by {@link #placeTankScene}, checked by {@link #settleTankScene} before capture. */
    private static BlockPos[] tankScenePositions;

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
            case PLACE_TANK_SCENE -> placeTankScene(mc);
            case SETTLE_TANK_SCENE -> settleTankScene(mc);
            case OPEN_SCREEN -> openScreen(mc);
            case SETTLE_SCREEN -> settleScreen(mc);
            case DONE -> {}
        }
    }

    private static void awaitTitle(Minecraft mc) {
        if (!(mc.screen instanceof TitleScreen)) {
            return;
        }
        // A harness run is unattended, so its window is routinely not the focused one, and vanilla
        // pauses a singleplayer client the moment an unfocused window has no screen open -- which is
        // exactly the gap between closing one station's GUI and the next one's open packet arriving.
        // The pause screen then owns the screen slot and the next capture is a picture of the pause
        // menu. Seen once while capturing #146's second smeltery.
        mc.options.pauseOnLostFocus = false;
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
            advance(Stage.PLACE_TANK_SCENE);
        }
    }

    /**
     * Places a seared tank, gauge and window in a row a few blocks in front of the player at eye
     * level, each filled to a different level, and points the camera at them -- #145's release-
     * checklist scene. No GUI opens; {@link #settleTankScene} takes a plain world-view screenshot.
     */
    private static void placeTankScene(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}placing seared tank/gauge/window world scene near {}", LOG_PREFIX, origin);
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = serverPlayer.serverLevel();
            // The player's actual position right now, not a value captured earlier -- ties the scene
            // to where the camera really is instead of an assumption about the flat world's layout.
            BlockPos feet = serverPlayer.blockPosition();
            // One block above the player's feet -- close enough to eye level for a level shot, and the
            // camera below sits at the same height so it looks straight at the row instead of up at it.
            BlockPos tankPos = feet.offset(0, 1, TANK_SCENE_DISTANCE);
            BlockPos gaugePos = tankPos.offset(TANK_SCENE_SPACING, 0, 0);
            BlockPos windowPos = tankPos.offset(2 * TANK_SCENE_SPACING, 0, 0);
            tankScenePositions = new BlockPos[] {tankPos, gaugePos, windowPos};

            level.setBlockAndUpdate(tankPos, ForgeweaveBlocks.SEARED_TANK.get().defaultBlockState());
            level.setBlockAndUpdate(gaugePos, ForgeweaveBlocks.SEARED_GAUGE.get().defaultBlockState());
            level.setBlockAndUpdate(windowPos, ForgeweaveBlocks.SEARED_WINDOW.get().defaultBlockState());
            // Three different fill fractions in one frame: proves the height math, not just that
            // something renders. Filled directly on the block entity's tank, same as buildSmeltery --
            // no bucket needed for a dev-only scene. Fractions are of each BE's own getCapacity(),
            // not a compile-time constant -- SEARED_TANK/GAUGE/WINDOW all share SearedTankBlockEntity
            // (one BlockEntityType, see its class javadoc) so they're the same four-bucket capacity
            // in practice, but computing it live is what actually proves that instead of assuming it.
            // setBlockAndUpdate() re-placing the *same* block type at a position Minecraft doesn't
            // recreate the block entity, so an already-loaded save (harness re-run without wiping
            // run/saves/) would otherwise keep stacking fluid from the previous run on top of this
            // run's fill -- setFluid(EMPTY) first makes the scene idempotent regardless.
            fillTankFraction(level, tankPos, 2.0 / 3.0);
            fillTankFraction(level, gaugePos, 1.0);
            fillTankFraction(level, windowPos, 1.0 / 4.0);
            LOGGER.info("{}placed tank={} gauge={} window={}", LOG_PREFIX, tankPos, gaugePos, windowPos);

            BlockPos cameraPos = tankPos.offset(TANK_SCENE_SPACING, -1,
                    -(TANK_SCENE_DISTANCE + TANK_SCENE_CAMERA_PULLBACK));
            serverPlayer.teleportTo(cameraPos.getX() + 0.5, cameraPos.getY(), cameraPos.getZ() + 0.5);
            serverPlayer.lookAt(EntityAnchorArgument.Anchor.EYES,
                    new Vec3(gaugePos.getX() + 0.5, gaugePos.getY() + 0.5, gaugePos.getZ() + 0.5));
        });
        advance(Stage.SETTLE_TANK_SCENE);
    }

    /** Fills {@code pos}'s tank to {@code fraction} of its own real capacity, from empty every time. */
    private static void fillTankFraction(ServerLevel level, BlockPos pos, double fraction) {
        if (!(level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank)) {
            return;
        }
        FluidTank fluidTank = tank.tank();
        fluidTank.setFluid(FluidStack.EMPTY); // idempotent: wipe whatever a prior harness run left.
        int amount = (int) Math.round(fluidTank.getCapacity() * fraction);
        fluidTank.fill(new FluidStack(Fluids.LAVA, amount), IFluidHandler.FluidAction.EXECUTE);
    }

    /**
     * Fails loudly in the log, rather than silently, when the client's own world doesn't have a
     * tank block where the scene placed one -- distinguishes "placed but invisible" (a rendering
     * bug) from "never placed where the camera looks" (a placement/sync/chunk-load bug). Also logs
     * the client's own amount/capacity per position so a wrong fill fraction is self-diagnosing
     * straight from the log, without needing another capture.
     */
    private static void verifyTankScenePlaced(Minecraft mc) {
        if (mc.level == null || tankScenePositions == null) {
            return;
        }
        // Logged unconditionally so a run that only shows one position's check line proves the loop
        // itself never reached the others, rather than leaving that ambiguous.
        LOGGER.info("{}#145 scene check: verifying {} positions", LOG_PREFIX, tankScenePositions.length);
        for (BlockPos pos : tankScenePositions) {
            // A single position throwing (e.g. an unloaded chunk) must not stop the rest from
            // checking -- that's exactly the kind of silent early-exit this check exists to rule out.
            try {
                var block = mc.level.getBlockState(pos).getBlock();
                if (block == Blocks.AIR) {
                    LOGGER.error("{}#145 scene check FAILED: client sees air at {}, expected a seared tank "
                            + "block -- placement or client sync did not land before capture", LOG_PREFIX, pos);
                } else if (mc.level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank) {
                    LOGGER.info("{}#145 scene check: client sees {} at {}, fluid {}/{} mB", LOG_PREFIX, block, pos,
                            tank.tank().getFluidAmount(), tank.tank().getCapacity());
                } else {
                    LOGGER.error("{}#145 scene check FAILED: client sees {} at {} but no SearedTankBlockEntity",
                            LOG_PREFIX, block, pos);
                }
            } catch (RuntimeException e) {
                LOGGER.error("{}#145 scene check FAILED: exception checking {}", LOG_PREFIX, pos, e);
            }
        }
    }

    private static void settleTankScene(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        verifyTankScenePlaced(mc);
        capture(mc, "seared_tank_world");
        advance(Stage.OPEN_SCREEN);
    }

    private static void openScreen(Minecraft mc) {
        // Let the previous screen's container-close packet reach the server before asking it to open
        // the next one. Vanilla's handleContainerClose closes whatever container is open regardless
        // of the id the packet names, so a close that lands after the next station's menu opened
        // takes that menu down with it -- which is what an empty capture in this harness looks like.
        if (stageTicks < SCREEN_GAP_TICKS) {
            return;
        }
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
            screen.prepare().accept(level, pos);
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

    /**
     * Builds the minimum 3x3x3 smeltery around a core the harness has just placed, and points the
     * core at the camera (issue #101).
     *
     * <p>A core's {@code FACING} points <em>out</em> of its structure, so facing it south puts the
     * interior on the north side and leaves the harness's own camera spot -- one block south of the
     * placed block -- outside the walls and looking at the core's front face. That also keeps the
     * player inside {@code SmelteryMenu}'s reach check, which is the "stillValid distance" concern
     * flagged when this harness landed: the check is against the <em>core's</em> position, not the
     * multiblock's, so an adjacent camera is well within it.
     *
     * <p>Seared shell over a seared floor with one seared tank in a wall (the scan requires at least
     * one) -- the same fixture {@code SmelteryGameTests} builds, sized by the shared builder below.
     */
    private static void buildSmeltery(ServerLevel level, BlockPos corePos) {
        // A 3x3x2 interior rather than the 1x1x2 minimum: 18 melting slots is six rows of the melt
        // grid, so the capture shows a full grid and its scroll window instead of a single slot.
        SmelteryControllerBlockEntity controller = buildSmeltery(level, corePos, 1, 3);
        if (controller == null) {
            return;
        }

        // Two metals, so the capture shows what this screen is actually for: the stacked fluid
        // column, its per-fluid bands and the bottom (drain) fluid. An empty tank would prove only
        // that the background blits -- and a screenshot that cannot fail is the review gap issues
        // #75/#85/#89 slipped through.
        controller.tank().fill(new FluidStack(ForgeweaveFluids.IRON.still().get(), 4 * 1152),
                IFluidHandler.FluidAction.EXECUTE);
        controller.tank().fill(new FluidStack(ForgeweaveFluids.COPPER.still().get(), 2 * 576),
                IFluidHandler.FluidAction.EXECUTE);
        // Items mid-melt, so the grid shows occupied slots with heat bars rather than an empty frame.
        for (int i = 0; i < 5; i++) {
            controller.insertForMelting(new ItemStack(Items.IRON_INGOT));
        }
        controller.insertForMelting(new ItemStack(Items.COPPER_INGOT));
    }

    /**
     * Issue #146's capture: the smallest smeltery there is (a 1x1x2 interior, so two melt slots)
     * with an empty grid -- the state the melt grid rendered as a floating two-slot fragment in,
     * because the grid was sized from the slot count while the panel art's notch it sits in is a
     * fixed-size hole. Nothing is put in the tank or the grid: an empty smeltery is precisely what a
     * playtester sees once the last thing in it has melted.
     */
    private static void buildEmptySmeltery(ServerLevel level, BlockPos corePos) {
        buildSmeltery(level, corePos, 0, 1);
    }

    /**
     * The shared structure builder: seared shell around an interior {@code 2 * halfWidth + 1} blocks
     * wide, {@code depth} blocks deep and always two tall, with a lava-filled tank in a wall.
     *
     * @return the formed core, or {@code null} if the block entity did not come back
     */
    @Nullable
    private static SmelteryControllerBlockEntity buildSmeltery(ServerLevel level, BlockPos corePos,
            int halfWidth, int depth) {
        BlockState brick = ForgeweaveBlocks.SEARED_BRICKS.get().defaultBlockState();
        BlockPos interiorMin = corePos.offset(-halfWidth, 0, -depth);
        BlockPos interiorMax = corePos.offset(halfWidth, 1, -1);

        for (int x = interiorMin.getX(); x <= interiorMax.getX(); x++) {
            for (int z = interiorMin.getZ(); z <= interiorMax.getZ(); z++) {
                level.setBlockAndUpdate(new BlockPos(x, interiorMin.getY() - 1, z), brick); // floor
                for (int y = interiorMin.getY(); y <= interiorMax.getY(); y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
            }
        }
        for (int y = interiorMin.getY(); y <= interiorMax.getY(); y++) {
            for (int x = interiorMin.getX(); x <= interiorMax.getX(); x++) {
                level.setBlockAndUpdate(new BlockPos(x, y, interiorMin.getZ() - 1), brick); // north wall
                level.setBlockAndUpdate(new BlockPos(x, y, interiorMax.getZ() + 1), brick); // south wall
            }
            for (int z = interiorMin.getZ(); z <= interiorMax.getZ(); z++) {
                level.setBlockAndUpdate(new BlockPos(interiorMin.getX() - 1, y, z), brick); // west wall
                level.setBlockAndUpdate(new BlockPos(interiorMax.getX() + 1, y, z), brick); // east wall
            }
        }

        // A smeltery needs at least one tank in its walls, and #96's heat model burns the lava in it.
        BlockPos tankPos = new BlockPos(interiorMin.getX(), interiorMin.getY(), interiorMin.getZ() - 1);
        level.setBlockAndUpdate(tankPos, ForgeweaveBlocks.SEARED_TANK.get().defaultBlockState());
        if (level.getBlockEntity(tankPos) instanceof SearedTankBlockEntity tank) {
            tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY),
                    IFluidHandler.FluidAction.EXECUTE);
        }

        // Re-placing the core with the right facing does not re-trigger its own scan (the block skips
        // that when the old state is already itself), so ask for one directly.
        level.setBlockAndUpdate(corePos, ForgeweaveBlocks.STANDARD_CORE.get().defaultBlockState()
                .setValue(SmelteryControllerBlock.FACING, Direction.SOUTH));
        if (!(level.getBlockEntity(corePos) instanceof SmelteryControllerBlockEntity controller)) {
            return null;
        }
        controller.updateStructure();
        LOGGER.info("{}smeltery structure: {}", LOG_PREFIX, controller.lastResult().getString());
        return controller;
    }

    /**
     * One harness entry: the PNG's base filename, the station block to place and open, and anything
     * that has to exist around it first ({@link #buildSmeltery}; a no-op for the M1 stations, which
     * are all single blocks).
     */
    private record HarnessScreen(String fileName, Supplier<? extends Block> block,
            BiConsumer<ServerLevel, BlockPos> prepare) {
        HarnessScreen(String fileName, Supplier<? extends Block> block) {
            this(fileName, block, (level, pos) -> {});
        }
    }
}
