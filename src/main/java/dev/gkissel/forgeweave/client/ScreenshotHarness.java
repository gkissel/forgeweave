package dev.gkissel.forgeweave.client;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;

import org.slf4j.Logger;

import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;

import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;


import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.CastingBlockEntity;
import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.client.book.BookContent;
import dev.gkissel.forgeweave.client.book.BookScreen;
import dev.gkissel.forgeweave.block.FaucetBlock;
import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.StationMenuHost;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.config.ForgeweaveClientConfig;
import dev.gkissel.forgeweave.config.HeldBowPose;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.CrossbowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.SmelteryMenu;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationTabs;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolArt;
import dev.gkissel.forgeweave.tool.ToolConstants;

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
 * <p>Before the GUI screens, it also captures plain world-view PNGs with no menu open --
 * {@code seared_tank_world.png}, a seared tank/gauge/window row at three different fill levels
 * (issue #145), {@code tool_forge_world.png}, a Tool Forge beside a Tool Station (issue #152), and
 * {@code smeltery_world.png}, a formed smeltery holding two molten metals seen from over its rim
 * (issue #179), and {@code casting_world.png}, casting tables and basins with casts, fluid, a live
 * faucet pour and a finished one (issues #182 and #200), and {@code m3_2_materials_1..3.png}, item
 * frames holding a tinted pickaxe head and an assembled pickaxe for every M3.2 material (issue
 * #236). Those are the release-checklist artifacts for in-world block rendering the same way
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
    /** How far in -Z the #179 smeltery scene's core sits from the player's spawn point, clear of every other scene. */
    private static final int SMELTERY_SCENE_DISTANCE = 12;
    /**
     * How far above the core the #179 camera sits, and how far back from it. Both are dictated by the
     * near wall: the sightline to the middle of the pool has to clear the top of the two-block wall
     * between the camera and it, so a low camera sees seared brick and nothing else however well the
     * pool renders. These clear it by about a block.
     */
    private static final int SMELTERY_SCENE_CAMERA_HEIGHT = 10;
    private static final int SMELTERY_SCENE_CAMERA_PULLBACK = 4;
    /** How far in -X the #157 tool scene sits from the harness origin, clear of every other scene. */
    private static final int TOOL_SCENE_OFFSET = 14;
    /** How far in -Z the #157 tool scene's props sit from the camera. */
    private static final int TOOL_SCENE_DISTANCE = 5;
    /**
     * Longer than {@link #SCREEN_SETTLE_TICKS} for this scene alone: every capture here waits on a
     * server-side inventory change reaching the client and being drawn, where the other scenes only
     * wait on blocks that were placed once.
     */
    private static final int TOOL_SETTLE_TICKS = 30;
    /** How far in +X the throwaway Tool Forge that builds the tools sits, out of the camera's view. */
    private static final int TOOL_SCENE_FORGE_OFFSET = 6;
    /** Iron head over wooden parts: four tinted layers that read as four different materials. */
    private static final String TOOL_SCENE_HEAD_MATERIAL = "iron";
    private static final String TOOL_SCENE_PART_MATERIAL = "wood";
    /** The #152 table row's own distance/spacing/pullback; tables are floor-standing, so no +Y. */
    private static final int TABLE_SCENE_DISTANCE = 4;
    private static final int TABLE_SCENE_SPACING = 2;
    private static final int TABLE_SCENE_CAMERA_PULLBACK = 2;
    /** One entry per station screen; see "Extending for M2" above. */
    private static final List<HarnessScreen> SCREENS = List.of(
            new HarnessScreen("part_builder", ForgeweaveBlocks.PART_BUILDER),
            // #758: a Pattern Chest (plus the Stencil Table and Crafting Station upstream's
            // partCrafter check also wants) beside the Part Builder, with a pattern already in the
            // chest -- the capture a reviewer checks the pattern-button sidebar against.
            new HarnessScreen("part_builder_pattern_chest", ForgeweaveBlocks.PART_BUILDER,
                    ScreenshotHarness::preparePatternChestScene),
            new HarnessScreen("tool_station", ForgeweaveBlocks.TOOL_STATION),
            // #152: the same screen class in its metal style. Captured next to tool_station.png on
            // purpose -- the two PNGs side by side are how a reviewer checks that only the styling
            // differs and the layout is still upstream's.
            new HarnessScreen("tool_forge", ForgeweaveBlocks.TOOL_FORGE),
            // #733: the selection grid's second page (the Tool Forge's roster is the one that
            // overflows a page) and the placed-parts preview on a build tab.
            new HarnessScreen("tool_forge_selection_page2", ForgeweaveBlocks.TOOL_FORGE,
                    (level, pos) -> {}, ScreenshotHarness::selectLastTab),
            new HarnessScreen("tool_station_preview", ForgeweaveBlocks.TOOL_STATION,
                    ScreenshotHarness::loadPickaxeParts, ScreenshotHarness::selectPickaxeTab),
            new HarnessScreen("crafting_station", ForgeweaveBlocks.CRAFTING_STATION),
            new HarnessScreen("stencil_table", ForgeweaveBlocks.STENCIL_TABLE),
            // #101: the smeltery is a multiblock, so unlike every M1 station it needs a structure
            // around the placed block before its GUI will open at all -- see buildSmeltery.
            new HarnessScreen("smeltery", ForgeweaveBlocks.STANDARD_CORE, ScreenshotHarness::buildSmeltery),
            // #146: the state the melt-grid defect was reported in -- a minimum-size smeltery (two
            // melt slots, so one row) with nothing in the grid, which is what a playtest smeltery
            // looks like once everything in it has melted away. #408: also the smallest the grid
            // ever draws, so this is the capture that shows the notch left empty behind it.
            new HarnessScreen("smeltery_empty", ForgeweaveBlocks.STANDARD_CORE, ScreenshotHarness::buildEmptySmeltery),
            // #408: the other end of the melt grid's sizing -- a smeltery with more rows than the
            // grid can show, so the capture is the one that proves the cap and the slider.
            new HarnessScreen("smeltery_large", ForgeweaveBlocks.STANDARD_CORE, ScreenshotHarness::buildLargeSmeltery));

    /**
     * The #182 casting row sits well out in -X, the one direction nothing else in this harness uses
     * (the GUI stations march off in +X, the two world scenes sit just in front of spawn): unlike
     * those, its camera has to look <em>into</em> open-topped blocks, and a stray tank or table from
     * an earlier scene standing in that line of sight is exactly what makes a capture unreadable.
     */
    private static final int CASTING_SCENE_OFFSET_X = 26;
    private static final int CASTING_SCENE_SPACING = 2;
    /** Wider than {@link #CASTING_SCENE_SPACING}: a faucet group is a table, a faucet and a tank. */
    private static final int CASTING_SCENE_FAUCET_SPACING = 3;
    /**
     * Long enough that the ingot-cast pour (one 24-tick transaction) is over and its faucet idle,
     * short enough that the shovel-head pour (two) is still running -- see {@link #placeCastingScene}.
     * The other scenes' {@link #SCREEN_SETTLE_TICKS} is far too short to catch a faucet stopping.
     */
    private static final int CASTING_SCENE_SETTLE_TICKS = 34;
    /** Where the camera stands relative to the row's middle: south of it and up, looking down in. */
    private static final int CASTING_SCENE_CAMERA_BACK = 10;
    private static final int CASTING_SCENE_CAMERA_HEIGHT = 3;
    /** Molten iron poured into the mid-pour table, out of the 144 mB an ingot cast wants. */
    private static final int CASTING_SCENE_PARTIAL_POUR = 72;
    /** {@code shovel_head_iron.json}: what the still-pouring table's cast wants, and #204's denominator. */
    private static final int CASTING_SCENE_PART_CAST_AMOUNT = 288;

    /**
     * Issue #155's six Tool Station weapons, one third-person held capture each. Listed rather than
     * walked off {@code ToolAssemblyRecipes.ENTRIES} on purpose: this is the release-checklist
     * artifact for <em>these</em> tools' art, and a later tool issue adding a row should decide for
     * itself whether its tool needs a frame here.
     */
    static final List<Supplier<? extends ToolItem>> WEAPONS = List.of(
            ForgeweaveItems.TOOL_BROADSWORD, ForgeweaveItems.TOOL_LONGSWORD, ForgeweaveItems.TOOL_RAPIER,
            ForgeweaveItems.TOOL_BATTLESIGN, ForgeweaveItems.TOOL_FRYING_PAN, ForgeweaveItems.TOOL_DAGGER,
            // #161: the warmace poses the same way -- it is a held weapon like the six above, and
            // assembleForDisplay builds it through the same station call, Tool Forge tier or not.
            ForgeweaveItems.TOOL_WARMACE,
            // Issue #156's two station tools, on the same held-render stage: their art is the
            // per-layer head/binding/handle composite the weapons' is, so the same capture answers
            // the same release-checklist question for them.
            ForgeweaveItems.TOOL_MATTOCK, ForgeweaveItems.TOOL_KAMA,
            // #159. The battleaxe is the first four-layer capture (handle, both heads, binding), so
            // this frame is also where a missing or misnamed second-head layer would show up.
            ForgeweaveItems.TOOL_BATTLEAXE, ForgeweaveItems.TOOL_SCIMITAR,
            // #160. The katana's art is freshly authored rather than derived, so its frame is the
            // one that catches a layer drawn at the wrong contrast or a guard that reads as heavier
            // than the blade -- both of which the issue's first pass actually shipped.
            ForgeweaveItems.TOOL_KATANA,
            // #158. The cleaver is the second four-layer capture and the first Tool Forge-tier tool
            // in this list; assembleForDisplay builds it through the same station call regardless.
            ForgeweaveItems.TOOL_CLEAVER,
            // #157's five. Each is four layers, and the hammer is the only tool with three of them
            // in the same role -- so this row is where a head2/head3 layer that went missing or got
            // tinted from the wrong part slot shows up.
            ForgeweaveItems.TOOL_HAMMER, ForgeweaveItems.TOOL_EXCAVATOR, ForgeweaveItems.TOOL_LUMBERAXE,
            ForgeweaveItems.TOOL_SCYTHE, ForgeweaveItems.TOOL_VEIN_HAMMER,
            // M3.5 #400: the three bows' *undrawn* poses. They belong on this list for the same
            // reason every other tool does -- these are the frames that show the assembled layer
            // stack held in a hand -- and their drawn ones are {@link #BOW_POSES} below, which is a
            // separate scene because getting a bow to a given pull stage takes ticking, not just
            // putting it in a hand.
            ForgeweaveItems.TOOL_SHORTBOW, ForgeweaveItems.TOOL_LONGBOW, ForgeweaveItems.TOOL_CROSSBOW,
            // #448: the shuriken -- the first four-blade capture, where a quadrant layer tinted from
            // the wrong slot or a missing head2..head4 texture shows up.
            ForgeweaveItems.TOOL_SHURIKEN);

    /**
     * Issue #400's release-checklist frames, {@code bow_<bow>_draw<stage>_firstperson.png} plus
     * {@code bow_crossbow_loaded_firstperson.png}: every draw state a bow's model can resolve to.
     *
     * <p>One frame per state and no fewer, because each is a different <em>model</em> -- the item
     * property overrides pick a whole sibling model per stage ({@code ForgeweaveItemModelProvider#
     * drawStageOverrides}), and a stage whose textures were misnamed, whose layer order drifted, or
     * whose predicate threshold is wrong renders as a missing-texture checker or as the wrong stage
     * in exactly one of these and in none of the others.
     *
     * <p>First person for every state, because a draw state <em>is</em> a model and first person is
     * where the model is legible; the third-person capture of each undrawn bow is already on the
     * list above. Two of them get a third-person frame as well ({@code thirdPerson}, issue #425):
     * the crossbow mid-crank and the crossbow carrying a crank are arm <em>poses</em>
     * ({@link ForgeweaveItemClientExtensions}), and an arm pose is invisible from inside the arm.
     * Only the crossbow's two, because no other pose on this list changes what the arms do.
     */
    static final List<BowPose> BOW_POSES = List.of(
            new BowPose(ForgeweaveItems.TOOL_SHORTBOW, 1, false),
            new BowPose(ForgeweaveItems.TOOL_SHORTBOW, 2, false),
            new BowPose(ForgeweaveItems.TOOL_SHORTBOW, 3, false),
            new BowPose(ForgeweaveItems.TOOL_LONGBOW, 1, false),
            new BowPose(ForgeweaveItems.TOOL_LONGBOW, 2, false),
            new BowPose(ForgeweaveItems.TOOL_LONGBOW, 3, false),
            new BowPose(ForgeweaveItems.TOOL_CROSSBOW, 1, false),
            // Third person as well (issue #601): this is the frame the crank's *pacing* shows up in.
            // Stage 2 is reached around tick 23 of the crossbow's 45, so a correctly paced winding
            // arm is about halfway out here; the vanilla 25-tick pacing this replaced had it at 92%
            // and about to freeze. Stage 3 alone cannot tell the two apart -- both are fully wound.
            new BowPose(ForgeweaveItems.TOOL_CROSSBOW, 2, false, true),
            new BowPose(ForgeweaveItems.TOOL_CROSSBOW, 3, false, true),
            // The one state that is not a draw: the crossbow's stored crank, which resolves to the
            // full-draw art under a first-person pose of its own.
            new BowPose(ForgeweaveItems.TOOL_CROSSBOW, 0, true, true));

    /**
     * Issue #723: {@link #BOW_POSES} again under {@code heldBowPose = modern}, as {@code
     * bow_<bow>_<state>_modern[_firstperson].png}, plus each bow's undrawn pose from both cameras
     * ({@code drawStage} 0, {@code bow_<bow>_idle_modern*.png}) -- the classic undrawn frames are
     * {@link #WEAPONS}', which runs before the config flips. The config is flipped on the live
     * spec between the two passes ({@link #holdBowPose}) and restored after, which is exactly what
     * a player toggling it in the mod-list screen does; no reload is involved because the switch
     * is an item property ({@code ForgeweaveItemProperties#modernPose}).
     */
    static final List<BowPose> MODERN_BOW_POSES = Stream.concat(
            Stream.of(ForgeweaveItems.TOOL_SHORTBOW, ForgeweaveItems.TOOL_LONGBOW, ForgeweaveItems.TOOL_CROSSBOW)
                    .map(bow -> new BowPose(bow, 0, false, true)),
            BOW_POSES.stream()).toList();

    /** Whether {@link #holdBowPose} is on its second, {@link #MODERN_BOW_POSES} pass (#723). */
    private static boolean modernBowPass;

    private static List<BowPose> bowPoses() {
        return modernBowPass ? MODERN_BOW_POSES : BOW_POSES;
    }

    private static String bowPoseFileName() {
        return bowPoses().get(bowPoseIndex).fileName() + (modernBowPass ? "_modern" : "");
    }

    /**
     * One {@link #BOW_POSES} frame: which bow, which pull stage (1 to {@link ToolArt#DRAW_STAGES},
     * or 0 for a state that is not a draw), whether the crossbow's {@code loaded} flag is set, and
     * whether the frame is captured from behind as well as from inside the head.
     */
    record BowPose(Supplier<? extends BowItem> bow, int drawStage, boolean loaded, boolean thirdPerson) {
        BowPose(Supplier<? extends BowItem> bow, int drawStage, boolean loaded) {
            this(bow, drawStage, loaded, false);
        }

        String fileName() {
            String name = BuiltInRegistries.ITEM.getKey(bow.get()).getPath();
            return "bow_" + name + (loaded ? "_loaded" : drawStage == 0 ? "_idle" : "_draw" + drawStage);
        }
    }

    /**
     * Ceiling on how long {@link #settleBowPose} will keep drawing before it gives up and captures
     * whatever the bow reached. Generous: the slowest bow is the crossbow at {@code drawTime = 45}
     * on wooden limbs, and this has to cover a full draw plus the settle wait in front of it.
     */
    private static final int BOW_DRAW_TIMEOUT_TICKS = 200;

    /** How far in -Z of spawn the weapon poses stand, clear of the block scenes above. */
    private static final int WEAPON_SCENE_DISTANCE = 6;

    /**
     * Issue #257's release-checklist frames, {@code weapon_broadsword_modified(_firstperson).png}:
     * one extra pose after {@link #WEAPONS}, the broadsword again but carrying three
     * overlay-bearing modifiers, so the pair of frames next to the unmodified {@code
     * weapon_broadsword*.png} shows exactly what applying modifiers adds. Three distinct arts on
     * one blade -- diamond's edge glint, fiery's flames, silky's jewel -- because one overlay
     * proves only that <em>something</em> renders, while three prove they stack in application
     * order without hiding each other ({@code ModifierOverlayModels}'s whole job). Fiery's level is
     * one full display level (25 units, {@code ModFiery}'s own {@code countPerLevel}); the overlay
     * ignores level, but the component should still look like a stack a station actually built.
     */
    private static final List<ModifierEntry> MODIFIED_WEAPON_MODIFIERS = List.of(
            new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "diamond"), 1),
            new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "fiery"), 25),
            new ModifierEntry(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "silky"), 1));

    /**
     * Issue #236's M3.2 material roster, in milestone order (vanilla-sourced batch, slime family,
     * metals, modern shapes). Listed rather than walked off the datapack on purpose, like {@link
     * #WEAPONS}: this is the release-checklist artifact for <em>these</em> materials' tints, and a
     * later material should decide for itself whether it belongs on this wall.
     */
    private static final List<String> M3_2_MATERIALS = List.of(
            "cactus", "obsidian", "prismarine", "endstone", "paper", "sponge", "netherrack", "firewood",
            "slime", "blueslime", "magmaslime", "knightslime", "pig_iron", "steel", "bronze", "lead",
            "silver", "electrum", "amethyst_bronze", "nahuatl", "chorus", "ancient");

    /**
     * How many materials share one #236 frame: 22 tints in a single 854x480 frame would leave each
     * item around 30 px -- too small to judge a tint against its neighbor -- so the wall is split
     * into groups of eight, captured as {@code m3_2_materials_1..3}.
     */
    private static final int MATERIAL_SCENE_COLUMNS = 8;
    /**
     * The #236 walls sit far out in +Z, past the #145 tank row -- the one line from spawn no other
     * scene builds in. The wall itself occludes everything behind it, so the frames read against
     * plain stone whatever the earlier scenes left standing.
     */
    private static final int MATERIAL_SCENE_DISTANCE = 18;
    /** Blocks apart along +X between one group's wall and the next, so no capture shows two walls. */
    private static final int MATERIAL_SCENE_GROUP_SPACING = 12;
    /** Camera distance south of a wall: close enough that each framed item is ~65 px across. */
    private static final int MATERIAL_SCENE_CAMERA_PULLBACK = 5;

    /** Ceiling on the attack-cooldown wait before a first-person weapon frame; see {@link #settleWeaponFirstPerson}. */
    private static final int WEAPON_SWING_RESET_TICKS = 60;

    private enum Stage {
        AWAIT_TITLE, AWAIT_WORLD, SETTLE_WORLD, PLACE_TANK_SCENE, SETTLE_TANK_SCENE,
        PLACE_TABLE_SCENE, SETTLE_TABLE_SCENE, PLACE_SMELTERY_SCENE, SETTLE_SMELTERY_SCENE,
        PLACE_CASTING_SCENE, SETTLE_CASTING_SCENE, PLACE_MATERIAL_SCENE, SETTLE_MATERIAL_SCENE,
        PLACE_PART_TINT_SCENE, SETTLE_PART_TINT_SCENE,
        HOLD_WEAPON, SETTLE_WEAPON, SETTLE_WEAPON_FIRST_PERSON,
        SETTLE_WEAPON_OFFHAND_FIRST_PERSON, SETTLE_WEAPON_OFFHAND, WEAR_ARMOR, SETTLE_ARMOR,
        SETTLE_ARMOR_FIRST_PERSON,
        HOLD_BOW_POSE, SETTLE_BOW_POSE, SETTLE_BOW_POSE_THIRD_PERSON,
        FIRE_BOW_SETUP, FIRE_BOW_DRAW, FIRE_BOW_CHECK,
        OPEN_SCREEN, SETTLE_SCREEN, OPEN_BOOK, SETTLE_BOOK, OPEN_PONDER, SETTLE_PONDER, DONE
    }

    /** Ceiling on waiting for a Ponder scene's finished frame; the longest scene runs about 900 ticks. */
    private static final int PONDER_SCENE_TIMEOUT_TICKS = 1500;
    /** Which of {@link PonderHarnessCaptures#CAPTURES} is playing (#700). */
    private static int ponderCaptureIndex;

    private static Stage stage = Stage.AWAIT_TITLE;
    private static int stageTicks;
    private static int screenIndex;
    /** Which of {@link #BOOK_SCENES} is being captured (#430). */
    private static int bookSceneIndex;
    private static BlockPos origin;
    /** Set by {@link #placeTankScene}, checked by {@link #settleTankScene} before capture. */
    private static BlockPos[] tankScenePositions;
    /** Which of {@link #WEAPONS} is being posed. */
    private static int weaponIndex;
    /** Which of {@link #BOW_POSES} is being posed (#400). */
    private static int bowPoseIndex;
    /** Which of {@link #ARMOR_SCENES} is being worn (#682). */
    private static int armorSceneIndex;
    /** Set by {@link #placeTableScene}, checked by {@link #settleTableScene} before capture (#152). */
    private static BlockPos[] tableScenePositions;
    /** Set by {@link #placeSmelteryScene}, checked by {@link #settleSmelteryScene} before capture (#179). */
    private static BlockPos smelteryScenePos;
    /** Set by {@link #placeCastingScene}, checked by {@link #settleCastingScene} before capture (#182). */
    private static BlockPos[] castingScenePositions;
    /** #200: the casting row's faucet whose pour has already ended -- it must be drawing nothing. */
    private static BlockPos castingSceneStoppedFaucetPos;
    /** #200: the one still mid-pour, so "nothing renders" cannot pass for "the stream stopped". */
    private static BlockPos castingSceneFlowingFaucetPos;
    /** Which {@value #MATERIAL_SCENE_COLUMNS}-material group of {@link #M3_2_MATERIALS} is up (#236). */
    private static int materialSceneIndex;
    /** Set by {@link #placeMaterialScene}, checked by {@link #settleMaterialScene} before capture. */
    private static AABB materialSceneBounds;
    /** Set by {@link #placePartTintScene}, checked by {@link #settlePartTintScene} before capture (#256). */
    private static AABB partTintSceneBounds;

    /**
     * A guide-book capture: the file name and either the spread to open ({@code -1} = the closed
     * cover) or a {@code section.page} bookmark resolved through the screen's ordinary
     * bookmark-opening path -- for the pages whose spread number shifts with the registry-generated
     * content in front of them.
     */
    private record BookScene(String fileName, int spread, @Nullable String bookmark) {
        BookScene(String fileName, int spread) {
            this(fileName, spread, null);
        }
    }

    private static final List<BookScene> BOOK_SCENES = List.of(
            new BookScene("book_cover", -1),
            new BookScene("book_index", 0),
            new BookScene("book_section", 1),
            // Issue #651: the smeltery multiblock's 3D structure page, the book's one
            // StructureElement render.
            new BookScene("book_structure", -1, "smeltery.multiblock"),
            // #682 (M4-7): the armor section's opening text page and one piece page, the latter
            // being the first tool-kind page whose diagram assembles an ArmorPieceItem.
            new BookScene("book_armor", -1, "armor.intro"),
            new BookScene("book_armor_chestplate", -1, "armor.chestplate"),
            // #760: two modifier pages whose diagrams used to always paint a pickaxe regardless of
            // what the modifier applies to. Fire protection is armor-only (an armor piece is now
            // correct); fins is projectile-only (an ammo item is now correct) -- neither answer is a
            // pickaxe, so either capture alone would have caught the old bug.
            new BookScene("book_modifier_fire_protection", -1, "modifiers.fire_protection"),
            new BookScene("book_modifier_fins", -1, "modifiers.fins"));

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
            case PLACE_TABLE_SCENE -> placeTableScene(mc);
            case SETTLE_TABLE_SCENE -> settleTableScene(mc);
            case PLACE_SMELTERY_SCENE -> placeSmelteryScene(mc);
            case SETTLE_SMELTERY_SCENE -> settleSmelteryScene(mc);
            case PLACE_CASTING_SCENE -> placeCastingScene(mc);
            case SETTLE_CASTING_SCENE -> settleCastingScene(mc);
            case PLACE_MATERIAL_SCENE -> placeMaterialScene(mc);
            case SETTLE_MATERIAL_SCENE -> settleMaterialScene(mc);
            case PLACE_PART_TINT_SCENE -> placePartTintScene(mc);
            case SETTLE_PART_TINT_SCENE -> settlePartTintScene(mc);
            case HOLD_WEAPON -> holdWeapon(mc);
            case SETTLE_WEAPON -> settleWeapon(mc);
            case SETTLE_WEAPON_FIRST_PERSON -> settleWeaponFirstPerson(mc);
            case SETTLE_WEAPON_OFFHAND_FIRST_PERSON -> settleWeaponOffhandFirstPerson(mc);
            case SETTLE_WEAPON_OFFHAND -> settleWeaponOffhand(mc);
            case WEAR_ARMOR -> wearArmor(mc);
            case SETTLE_ARMOR -> settleArmor(mc);
            case SETTLE_ARMOR_FIRST_PERSON -> settleArmorFirstPerson(mc);
            case HOLD_BOW_POSE -> holdBowPose(mc);
            case SETTLE_BOW_POSE -> settleBowPose(mc);
            case SETTLE_BOW_POSE_THIRD_PERSON -> settleBowPoseThirdPerson(mc);
            case FIRE_BOW_SETUP -> fireBowSetup(mc);
            case FIRE_BOW_DRAW -> fireBowDraw(mc);
            case FIRE_BOW_CHECK -> fireBowCheck(mc);
            case OPEN_SCREEN -> openScreen(mc);
            case SETTLE_SCREEN -> settleScreen(mc);
            case OPEN_BOOK -> openBook(mc);
            case SETTLE_BOOK -> settleBook(mc);
            case OPEN_PONDER -> openPonder(mc);
            case SETTLE_PONDER -> settlePonder(mc);
            case DONE -> {}
        }
    }

    private static void awaitTitle(Minecraft mc) {
        if (mc.screen instanceof AccessibilityOnboardingScreen) {
            // 1.21 shows accessibility onboarding in front of the title screen on a run dir's
            // first-ever launch (fresh worktree, CI), and the harness would wait on it forever.
            // Opting out through the real option -- not a synthetic click -- both dismisses it and
            // persists to options.txt so the next run in this dir goes straight to the title.
            mc.options.onboardAccessibility = false;
            mc.options.save();
            mc.setScreen(new TitleScreen());
            return;
        }
        if (!(mc.screen instanceof TitleScreen)) {
            return;
        }
        // A harness run is unattended, so its window is routinely not the focused one, and vanilla
        // pauses a singleplayer client the moment an unfocused window has no screen open -- which is
        // exactly the gap between closing one station's GUI and the next one's open packet arriving.
        // The pause screen then owns the screen slot and the next capture is a picture of the pause
        // menu. Seen once while capturing #146's second smeltery.
        mc.options.pauseOnLostFocus = false;
        // #158: a fresh run dir is also a fresh "first world", so vanilla's tutorial toast ("Move with
        // W, A, S and D") floats over the top-right of every world-view capture for its first minute.
        mc.options.tutorialStep = TutorialSteps.NONE;
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
        advance(Stage.PLACE_TABLE_SCENE);
    }

    /**
     * Issue #152's in-world scene: a Tool Forge beside a Tool Station, a couple of blocks in front of
     * the player, captured with no GUI open as {@code tool_forge_world.png}.
     *
     * <p>Both blocks in one frame for the same reason the {@link #placeTankScene} row holds three
     * fill levels: a lone forge proves only that <em>something</em> renders. The pair proves the
     * forge wears its own top art and its iron legs and underside, against a table whose geometry is
     * known-good -- the two share a model, so a geometry regression would show on both and a texture
     * regression on only one.
     *
     * <p>Deliberately <em>not</em> a third, retextured forge: a table whose texture is set in the
     * same tick the block is placed renders its default wood/metal in a first capture and the right
     * one in a second, because the client applies the block-entity payload after the chunk section
     * has already baked its {@code ModelData} and nothing schedules a rebuild (issue #79's
     * {@code getUpdateTag} fix covers the chunk-load path, not this one). That race predates #152 and
     * belongs to every retextured table, so this scene stays on the deterministic default appearance
     * rather than shipping an artifact that is wrong every time it is captured fresh.
     */
    private static void placeTableScene(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}placing Tool Forge / Tool Station world scene near {}", LOG_PREFIX, origin);
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = serverPlayer.serverLevel();
            BlockPos feet = serverPlayer.blockPosition();
            BlockPos stationPos = feet.offset(0, 0, TABLE_SCENE_DISTANCE);
            BlockPos forgePos = stationPos.offset(TABLE_SCENE_SPACING, 0, 0);
            tableScenePositions = new BlockPos[] {stationPos, forgePos};

            level.setBlockAndUpdate(stationPos, ForgeweaveBlocks.TOOL_STATION.get().defaultBlockState());
            level.setBlockAndUpdate(forgePos, ForgeweaveBlocks.TOOL_FORGE.get().defaultBlockState());
            LOGGER.info("{}placed station={} forge={}", LOG_PREFIX, stationPos, forgePos);

            // Between the two, so neither is seen edge-on, and far enough back that both fit.
            BlockPos cameraPos = stationPos.offset(TABLE_SCENE_SPACING / 2, 0,
                    -(TABLE_SCENE_DISTANCE + TABLE_SCENE_CAMERA_PULLBACK));
            serverPlayer.teleportTo(cameraPos.getX() + 0.5, cameraPos.getY(), cameraPos.getZ() + 0.5);
            serverPlayer.lookAt(EntityAnchorArgument.Anchor.EYES,
                    new Vec3(forgePos.getX(), forgePos.getY() + 0.5, forgePos.getZ() + 0.5));
        });
        advance(Stage.SETTLE_TABLE_SCENE);
    }

    private static void settleTableScene(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        // Same self-diagnosing check the tank scene does: "placed but invisible" and "never placed"
        // look identical in a PNG and need very different fixes.
        if (mc.level != null && tableScenePositions != null) {
            for (BlockPos pos : tableScenePositions) {
                var block = mc.level.getBlockState(pos).getBlock();
                if (block == Blocks.AIR) {
                    LOGGER.error("{}#152 scene check FAILED: client sees air at {}", LOG_PREFIX, pos);
                } else if (mc.level.getBlockEntity(pos) instanceof ToolStationBlockEntity table) {
                    LOGGER.info("{}#152 scene check: client sees {} at {}, texture {}, forge={}",
                            LOG_PREFIX, block, pos, table.getTexture(), table.isForge());
                } else {
                    LOGGER.error("{}#152 scene check FAILED: client sees {} at {} but no table block entity",
                            LOG_PREFIX, block, pos);
                }
            }
        }
        capture(mc, "tool_forge_world");
        advance(Stage.PLACE_SMELTERY_SCENE);
    }

    /**
     * Issue #179's scene: a formed, partly filled smeltery seen from outside as {@code
     * smeltery_world.png}, the artifact for "the molten contents render in the world at all".
     *
     * <p>Two metals rather than one, filled to a little over half the interior's capacity, because
     * the defect this captures has three separable failure modes and a single full fluid would only
     * rule out the first: nothing renders, the layers are not stacked in tank order, and the pool's
     * height is not proportional to what the smeltery holds.
     *
     * <p>A smeltery has no ceiling and opaque walls, so the camera goes <em>above</em> the rim and
     * looks down into the interior -- from a player's eye level all that is visible is seared brick.
     */
    private static void placeSmelteryScene(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}placing smeltery world scene near {}", LOG_PREFIX, origin);
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = serverPlayer.serverLevel();
            // Off the spawn point rather than off wherever the previous scene left the camera: the
            // other scenes and the GUI captures all sit in +X/+Z from spawn, and a 5x5-footprint
            // multiblock dropped on top of one of them would rewrite blocks another artifact shows.
            BlockPos corePos = origin.offset(0, 0, -SMELTERY_SCENE_DISTANCE);
            smelteryScenePos = corePos;

            // The same 3x3x2 interior the smeltery GUI capture uses, so both artifacts describe the
            // same structure; buildSmeltery puts the interior on the north (-Z) side of the core.
            SmelteryControllerBlockEntity controller = buildSmeltery(level, corePos, 1, 3);
            if (controller == null) {
                LOGGER.error("{}#179 scene FAILED: no controller block entity at {}", LOG_PREFIX, corePos);
                return;
            }
            // A little over half full, in two metals: the lower (iron) band is much the thicker one,
            // so a stack rendered in the wrong order is obvious rather than subtle.
            controller.tank().fill(new FluidStack(ForgeweaveFluids.IRON.still().get(), 4 * 1152),
                    IFluidHandler.FluidAction.EXECUTE);
            controller.tank().fill(new FluidStack(ForgeweaveFluids.COPPER.still().get(), 2 * 576),
                    IFluidHandler.FluidAction.EXECUTE);
            LOGGER.info("{}smeltery scene core={} holding {}/{} mB in {} fluids", LOG_PREFIX, corePos,
                    controller.tank().getFluidAmount(), controller.tank().getCapacity(),
                    controller.tank().fluids().size());

            // Above the rim and back from it, looking down at the middle of the pool. Flight first:
            // this is the one scene whose camera is off the ground, and a teleported player who is
            // not flying has fallen back to it long before the capture.
            serverPlayer.getAbilities().flying = true;
            serverPlayer.onUpdateAbilities();
            Vec3 poolCenter = new Vec3(corePos.getX() + 0.5, corePos.getY() + 0.5, corePos.getZ() - 1.5);
            serverPlayer.teleportTo(corePos.getX() + 0.5, corePos.getY() + SMELTERY_SCENE_CAMERA_HEIGHT,
                    corePos.getZ() + SMELTERY_SCENE_CAMERA_PULLBACK + 0.5);
            serverPlayer.lookAt(EntityAnchorArgument.Anchor.EYES, poolCenter);
        });
        advance(Stage.SETTLE_SMELTERY_SCENE);
    }

    private static void settleSmelteryScene(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        // Same self-diagnosing check the other scenes do, against the *client's* copy: an unformed or
        // empty client-side controller means the sync is at fault, not the renderer.
        if (mc.level != null && smelteryScenePos != null) {
            if (mc.level.getBlockEntity(smelteryScenePos) instanceof SmelteryControllerBlockEntity controller) {
                LOGGER.info("{}#179 scene check: client sees structure={} holding {}/{} mB in {} fluids",
                        LOG_PREFIX, controller.structure(), controller.tank().getFluidAmount(),
                        controller.tank().getCapacity(), controller.tank().fluids().size());
                if (controller.structure() == null || controller.tank().fluids().isEmpty()) {
                    LOGGER.error("{}#179 scene check FAILED: client-side smeltery is unformed or empty, so the "
                            + "capture cannot show a pool no matter what the renderer does", LOG_PREFIX);
                }
            } else {
                LOGGER.error("{}#179 scene check FAILED: client sees no smeltery controller at {}",
                        LOG_PREFIX, smelteryScenePos);
            }
        }
        capture(mc, "smeltery_world");
        advance(Stage.PLACE_CASTING_SCENE);
    }

    /**
     * Issue #182's in-world scene, captured as {@code casting_world.png}, extended by #200 to cover
     * the two defects that survived it. Left to right along +X:
     *
     * <ol>
     *   <li>a casting table with an ingot cast lying on it and nothing else -- the plain "the cast is
     *       invisible" report;
     *   <li>a casting table holding the same cast half covered in molten iron -- #200's second
     *       defect, where the pool used to swallow the cast instead of stopping under it;
     *   <li>an empty casting basin -- the transparency report, which is only visible with nothing in
     *       the way of the interior;
     *   <li>a table under a faucet whose pour has <em>already finished</em> -- #200's first defect.
     *       This one must show a full table and <b>no stream at all</b>;
     *   <li>a table under a faucet still mid-pour, so the capture also proves the stream renderer has
     *       not simply been switched off to make the fourth one pass.
     * </ol>
     *
     * <p>The two pours start here and are captured {@value #CASTING_SCENE_SETTLE_TICKS} ticks later,
     * which is what puts one either side of the line. A faucet buffers {@value
     * FaucetBlockEntity#TRANSACTION_AMOUNT} mB and moves {@value FaucetBlockEntity#LIQUID_TRANSFER}
     * mB a tick, so one transaction runs 24 ticks: the ingot cast (144 mB) is a single transaction
     * and its faucet has been idle for ten ticks by the time the shutter opens, while the shovel-head
     * cast (288 mB) is two and its faucet is halfway through the second.
     *
     * <p>Both pours target tables rather than a basin on purpose -- issue #183's basin intake is
     * being worked separately, and an artifact that has to prove a rendering fix must not be able to
     * fail for a reason that has nothing to do with rendering.
     */
    private static void placeCastingScene(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}placing casting table/basin/faucet world scene near {}", LOG_PREFIX, origin);
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = serverPlayer.serverLevel();
            BlockPos castTable = origin.offset(-CASTING_SCENE_OFFSET_X, 0, 0);
            BlockPos pouringTable = castTable.offset(CASTING_SCENE_SPACING, 0, 0);
            BlockPos emptyBasin = castTable.offset(2 * CASTING_SCENE_SPACING, 0, 0);
            // The faucet groups each need a source tank beside them, so they stand further apart.
            BlockPos stoppedTable = castTable.offset(3 * CASTING_SCENE_SPACING, 0, 0);
            BlockPos flowingTable = stoppedTable.offset(CASTING_SCENE_FAUCET_SPACING, 0, 0);
            castingScenePositions = new BlockPos[] {castTable, pouringTable, emptyBasin, stoppedTable, flowingTable};

            level.setBlockAndUpdate(castTable, ForgeweaveBlocks.CASTING_TABLE.get().defaultBlockState());
            level.setBlockAndUpdate(pouringTable, ForgeweaveBlocks.CASTING_TABLE.get().defaultBlockState());
            level.setBlockAndUpdate(emptyBasin, ForgeweaveBlocks.CASTING_BASIN.get().defaultBlockState());

            putCast(serverPlayer, level, castTable, ForgeweaveItems.CAST_INGOT.get());
            putCast(serverPlayer, level, pouringTable, ForgeweaveItems.CAST_INGOT.get());
            // Through the block's own fluid handler, so the scene can only ever hold fluid the real
            // casting rules would have let a faucet put there.
            IFluidHandler pouringTableTank = level.getCapability(
                    Capabilities.FluidHandler.BLOCK, pouringTable, Direction.UP);
            if (pouringTableTank != null) {
                pouringTableTank.fill(new FluidStack(ForgeweaveFluids.IRON.still().get(), CASTING_SCENE_PARTIAL_POUR),
                        IFluidHandler.FluidAction.EXECUTE);
            }

            // An ingot cast wants one transaction, so this faucet is done and idle by capture time.
            castingSceneStoppedFaucetPos = pourInto(level, serverPlayer, stoppedTable,
                    ForgeweaveItems.CAST_INGOT.get());
            // A shovel-head cast wants two, so this one is still running.
            castingSceneFlowingFaucetPos = pourInto(level, serverPlayer, flowingTable,
                    ForgeweaveItems.CAST_SHOVEL_HEAD.get());

            // South of the row and up, looking north: +X reads left to right in the frame that way,
            // and looking down is the only way an open-topped block shows what is lying in it.
            Vec3 rowCentre = new Vec3((castTable.getX() + flowingTable.getX()) / 2.0 + 0.5,
                    castTable.getY() + 0.5, castTable.getZ() + 0.5);
            serverPlayer.teleportTo(rowCentre.x, castTable.getY() + CASTING_SCENE_CAMERA_HEIGHT,
                    rowCentre.z + CASTING_SCENE_CAMERA_BACK);
            serverPlayer.lookAt(EntityAnchorArgument.Anchor.EYES, rowCentre);
        });
        advance(Stage.SETTLE_CASTING_SCENE);
    }

    /**
     * Puts one cast on a casting block through {@link CastingBlockEntity#interact} -- the player's
     * own path, held stack and all, rather than a back door into the block entity that would only
     * exist for this harness.
     */
    private static void putCast(ServerPlayer serverPlayer, ServerLevel level, BlockPos pos, Item cast) {
        if (!(level.getBlockEntity(pos) instanceof CastingBlockEntity casting)) {
            return;
        }
        ItemStack held = serverPlayer.getItemInHand(InteractionHand.MAIN_HAND);
        serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(cast));
        casting.interact(serverPlayer, InteractionHand.MAIN_HAND);
        serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, held);
    }

    /**
     * Builds one faucet group at {@code tablePos}: a casting table holding {@code cast}, a faucet
     * above it drawing from the east, a full seared tank of molten iron east of that, and the pour
     * already started. The source tank sits on the far side of the row rather than between blocks so
     * it never stands in the camera's line into the table.
     *
     * @return the faucet's position, for {@link #settleCastingScene}'s client-side check
     */
    private static BlockPos pourInto(ServerLevel level, ServerPlayer serverPlayer, BlockPos tablePos, Item cast) {
        level.setBlockAndUpdate(tablePos, ForgeweaveBlocks.CASTING_TABLE.get().defaultBlockState());
        putCast(serverPlayer, level, tablePos, cast);

        BlockPos faucetPos = tablePos.above();
        BlockPos sourceTankPos = faucetPos.east();
        level.setBlockAndUpdate(sourceTankPos, ForgeweaveBlocks.SEARED_TANK.get().defaultBlockState());
        if (level.getBlockEntity(sourceTankPos) instanceof SearedTankBlockEntity tank) {
            tank.tank().setFluid(FluidStack.EMPTY); // Idempotent across harness re-runs, as #145's scene is.
            tank.tank().fill(new FluidStack(ForgeweaveFluids.IRON.still().get(), SearedTankBlockEntity.CAPACITY),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        // Drawing sideways rather than from a tank straight above: the horizontal model is the one
        // with a trough to fill and a rotation to get wrong, so it is the one worth photographing.
        level.setBlockAndUpdate(faucetPos, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.EAST));
        if (level.getBlockEntity(faucetPos) instanceof FaucetBlockEntity faucet) {
            LOGGER.info("{}#200 faucet at {} over {} activate -> pouring={}",
                    LOG_PREFIX, faucetPos, cast, faucet.activate());
        }
        return faucetPos;
    }

    private static void settleCastingScene(Minecraft mc) {
        if (stageTicks < CASTING_SCENE_SETTLE_TICKS) {
            return;
        }
        // Same self-diagnosing check the other two world scenes do: a PNG cannot tell "rendered
        // nothing" apart from "there was nothing to render", and #182 is exactly that distinction.
        if (mc.level != null && castingScenePositions != null) {
            for (BlockPos pos : castingScenePositions) {
                var block = mc.level.getBlockState(pos).getBlock();
                if (mc.level.getBlockEntity(pos) instanceof CastingBlockEntity casting) {
                    LOGGER.info("{}#182 scene check: client sees {} at {}, input={} output={} fluid {}/{} mB",
                            LOG_PREFIX, block, pos, casting.input(), casting.output(),
                            casting.tank().getFluidAmount(), casting.tank().getCapacity());
                } else {
                    LOGGER.error("{}#182 scene check FAILED: client sees {} at {} but no casting block entity",
                            LOG_PREFIX, block, pos);
                }
            }
        }
        // #200 is a client-state bug, so what the *client* believes about each faucet is the whole
        // point: the stopped one reading pouring=true here means the fix did not land, and no amount
        // of squinting at the PNG would tell a reviewer that apart from a renderer that ignores it.
        checkFaucet(mc, castingSceneStoppedFaucetPos, false);
        checkFaucet(mc, castingSceneFlowingFaucetPos, true);
        // #204, same reasoning: the fifth table is still being poured into, so what the client has to
        // hold is a real *fraction* -- the capacity is the recipe's 288 mB however little has arrived
        // so far. A table reading N/N mid-pour draws itself 100% full, and a PNG of a one-pixel-deep
        // pool cannot tell a reviewer 204/288 from 204/204.
        checkPartCastFill(mc, castingScenePositions == null ? null : castingScenePositions[4]);
        capture(mc, "casting_world");
        advance(Stage.PLACE_MATERIAL_SCENE);
    }

    /**
     * Issue #236's release-checklist scenes, {@code m3_2_materials_1..3}: every M3.2 material's tint
     * in real rendering, {@value #MATERIAL_SCENE_COLUMNS} materials per frame. Each material gets a
     * column of two item frames on a stone wall facing the camera -- a pickaxe head part on top and
     * an assembled pickaxe (that material's head over wooden binding/handle) below it -- so one
     * capture shows the same tint through both render paths a player sees it in: the flat part
     * sprite and the tool's per-layer composite.
     *
     * <p>Item frames rather than dropped items because a dropped item spins: a frame holds every
     * item flat, face-on and at a fixed size, which is what comparing 22 tints side by side needs.
     * The stacks are built the same way the stations build them -- the part carries the real {@link
     * ForgeweaveDataComponents#MATERIAL} component and the tool comes out of {@link
     * ToolAssemblyRecipes#assemble}, the very call the Tool Station's menu makes -- so the tint on
     * the wall is the tint a player gets, not staged art.
     */
    private static void placeMaterialScene(Minecraft mc) {
        int start = materialSceneIndex * MATERIAL_SCENE_COLUMNS;
        if (start >= M3_2_MATERIALS.size()) {
            advance(Stage.PLACE_PART_TINT_SCENE);
            return;
        }
        List<String> group = M3_2_MATERIALS.subList(start,
                Math.min(start + MATERIAL_SCENE_COLUMNS, M3_2_MATERIALS.size()));
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}placing #236 material wall {} ({} materials: {})", LOG_PREFIX,
                materialSceneIndex + 1, group.size(), group);
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = serverPlayer.serverLevel();
            BlockPos wallBase = origin.offset(
                    materialSceneIndex * MATERIAL_SCENE_GROUP_SPACING, 0, MATERIAL_SCENE_DISTANCE);
            // The frames hang one block south of the wall; the box covers both frame rows.
            materialSceneBounds = new AABB(
                    wallBase.getX(), wallBase.getY() + 1, wallBase.getZ() + 1,
                    wallBase.getX() + group.size(), wallBase.getY() + 3, wallBase.getZ() + 2);
            // Idempotent across harness re-runs on a kept save, as #145's setFluid(EMPTY) is:
            // without this a re-run would hang a second frame over each of the first run's.
            level.getEntitiesOfClass(ItemFrame.class, materialSceneBounds).forEach(ItemFrame::discard);

            for (int i = 0; i < group.size(); i++) {
                String materialName = group.get(i);
                BlockPos topWall = wallBase.offset(i, 2, 0);
                BlockPos bottomWall = wallBase.offset(i, 1, 0);
                level.setBlockAndUpdate(topWall, Blocks.SMOOTH_STONE.defaultBlockState());
                level.setBlockAndUpdate(bottomWall, Blocks.SMOOTH_STONE.defaultBlockState());

                ItemStack part = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
                part.set(ForgeweaveDataComponents.MATERIAL.get(), material(materialName));
                hangFrame(level, topWall.south(), part);

                ItemStack tool = assembleForDisplay(serverPlayer,
                        ForgeweaveItems.TOOL_PICKAXE.get(), materialName);
                if (tool.isEmpty()) {
                    LOGGER.error("{}#236 scene FAILED: could not assemble a {} pickaxe",
                            LOG_PREFIX, materialName);
                }
                hangFrame(level, bottomWall.south(), tool);
            }

            // South of the wall at ground level, looking at the seam between the two frame rows.
            double centerX = wallBase.getX() + group.size() / 2.0;
            BlockPos cameraPos = wallBase.offset(0, 0, 1 + MATERIAL_SCENE_CAMERA_PULLBACK);
            serverPlayer.teleportTo(centerX, cameraPos.getY(), cameraPos.getZ() + 0.5);
            serverPlayer.lookAt(EntityAnchorArgument.Anchor.EYES,
                    new Vec3(centerX, wallBase.getY() + 2.0, wallBase.getZ() + 1.0));
        });
        advance(Stage.SETTLE_MATERIAL_SCENE);
    }

    /** Hangs one item frame at {@code pos}, facing the camera, holding {@code stack}. */
    private static void hangFrame(ServerLevel level, BlockPos pos, ItemStack stack) {
        ItemFrame frame = new ItemFrame(level, pos, Direction.SOUTH);
        frame.setItem(stack);
        level.addFreshEntity(frame);
    }

    private static void settleMaterialScene(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        int start = materialSceneIndex * MATERIAL_SCENE_COLUMNS;
        List<String> group = M3_2_MATERIALS.subList(start,
                Math.min(start + MATERIAL_SCENE_COLUMNS, M3_2_MATERIALS.size()));
        // Same self-diagnosing check every world scene logs before its capture: a PNG cannot tell
        // "rendered nothing" from "nothing to render", and 2xN tiny sprites are especially easy to
        // miscount by eye. Counts the client's own frame entities and logs each one's item and
        // material, west to east, so the log doubles as the column legend for the PNG.
        if (mc.level != null && materialSceneBounds != null) {
            List<ItemFrame> frames = mc.level.getEntitiesOfClass(ItemFrame.class, materialSceneBounds).stream()
                    .sorted(java.util.Comparator.comparingDouble((ItemFrame f) -> f.getX())
                            .thenComparing(f -> -f.getY()))
                    .toList();
            int expected = 2 * group.size();
            if (frames.size() != expected) {
                LOGGER.error("{}#236 scene check FAILED: client sees {} item frames in {}, expected {}",
                        LOG_PREFIX, frames.size(), materialSceneBounds, expected);
            }
            for (ItemFrame frame : frames) {
                ItemStack item = frame.getItem();
                Object materialId = item.get(ForgeweaveDataComponents.MATERIAL.get());
                Object toolMaterials = item.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
                if (item.isEmpty() || (materialId == null && toolMaterials == null)) {
                    LOGGER.error("{}#236 scene check FAILED: frame at {} holds {} with no material "
                            + "component -- it renders untinted", LOG_PREFIX, frame.blockPosition(), item);
                } else {
                    LOGGER.info("{}#236 scene check: frame at {} holds {} material={}", LOG_PREFIX,
                            frame.blockPosition(), item, materialId != null ? materialId : toolMaterials);
                }
            }
        }
        capture(mc, "m3_2_materials_" + (materialSceneIndex + 1));
        materialSceneIndex++;
        advance(Stage.PLACE_MATERIAL_SCENE);
    }

    /**
     * Issue #256's release-checklist scene, {@code part_tints}: the two head parts that shipped
     * rendering white (the scimitar's curved blade and the katana's blade -- both missing from the
     * old hand-maintained part-tint list) framed next to the tools assembled from them, everything
     * in slime green -- the one tint that cannot be mistaken for an untinted white sprite. Same
     * wall-of-item-frames staging as the #236 material walls, one wall west of them.
     */
    private static void placePartTintScene(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}placing #256 part-tint wall (curved blade, scimitar, katana blade, katana)", LOG_PREFIX);
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            ServerLevel level = serverPlayer.serverLevel();
            BlockPos wallBase = origin.offset(-MATERIAL_SCENE_GROUP_SPACING, 0, MATERIAL_SCENE_DISTANCE);

            ItemStack curvedBlade = new ItemStack(ForgeweaveItems.PART_CURVED_BLADE.get());
            curvedBlade.set(ForgeweaveDataComponents.MATERIAL.get(), material("slime"));
            ItemStack katanaBlade = new ItemStack(ForgeweaveItems.PART_KATANA_BLADE.get());
            katanaBlade.set(ForgeweaveDataComponents.MATERIAL.get(), material("slime"));
            List<ItemStack> stacks = List.of(
                    curvedBlade,
                    assembleForDisplay(serverPlayer, ForgeweaveItems.TOOL_SCIMITAR.get(), "slime"),
                    katanaBlade,
                    assembleForDisplay(serverPlayer, ForgeweaveItems.TOOL_KATANA.get(), "slime"));

            partTintSceneBounds = new AABB(
                    wallBase.getX(), wallBase.getY() + 1, wallBase.getZ() + 1,
                    wallBase.getX() + stacks.size(), wallBase.getY() + 2, wallBase.getZ() + 2);
            // Idempotent across harness re-runs on a kept save, as the #236 walls are.
            level.getEntitiesOfClass(ItemFrame.class, partTintSceneBounds).forEach(ItemFrame::discard);

            for (int i = 0; i < stacks.size(); i++) {
                if (stacks.get(i).isEmpty()) {
                    LOGGER.error("{}#256 scene FAILED: stack {} is empty", LOG_PREFIX, i);
                }
                BlockPos wall = wallBase.offset(i, 1, 0);
                level.setBlockAndUpdate(wall, Blocks.SMOOTH_STONE.defaultBlockState());
                hangFrame(level, wall.south(), stacks.get(i));
            }

            double centerX = wallBase.getX() + stacks.size() / 2.0;
            BlockPos cameraPos = wallBase.offset(0, 0, 1 + MATERIAL_SCENE_CAMERA_PULLBACK);
            serverPlayer.teleportTo(centerX, cameraPos.getY(), cameraPos.getZ() + 0.5);
            serverPlayer.lookAt(EntityAnchorArgument.Anchor.EYES,
                    new Vec3(centerX, wallBase.getY() + 1.5, wallBase.getZ() + 1.0));
        });
        advance(Stage.SETTLE_PART_TINT_SCENE);
    }

    private static void settlePartTintScene(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        // Self-diagnosing like the #236 walls: an item that lost its MATERIAL/TOOL_MATERIALS
        // component would render untinted white -- exactly the defect this scene exists to catch --
        // and a PNG alone cannot tell that from the tint bug itself.
        if (mc.level != null && partTintSceneBounds != null) {
            List<ItemFrame> frames = mc.level.getEntitiesOfClass(ItemFrame.class, partTintSceneBounds);
            if (frames.size() != 4) {
                LOGGER.error("{}#256 scene check FAILED: client sees {} item frames in {}, expected 4",
                        LOG_PREFIX, frames.size(), partTintSceneBounds);
            }
            for (ItemFrame frame : frames) {
                ItemStack item = frame.getItem();
                Object materialId = item.get(ForgeweaveDataComponents.MATERIAL.get());
                Object toolMaterials = item.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
                if (item.isEmpty() || (materialId == null && toolMaterials == null)) {
                    LOGGER.error("{}#256 scene check FAILED: frame at {} holds {} with no material "
                            + "component -- it renders untinted", LOG_PREFIX, frame.blockPosition(), item);
                } else {
                    LOGGER.info("{}#256 scene check: frame at {} holds {} material={}", LOG_PREFIX,
                            frame.blockPosition(), item, materialId != null ? materialId : toolMaterials);
                }
            }
        }
        capture(mc, "part_tints");
        advance(Stage.HOLD_WEAPON);
    }

    /**
     * Issue #155's in-world scene: one third-person capture per M3 station weapon, the assembled
     * tool held in the player's main hand, as {@code weapon_<tool>.png}.
     *
     * <p>A held-item render is the only thing that exercises a tool's layered model the way a player
     * actually sees it -- the inventory sprite, the Tool Station's preview and the JEI icon all draw
     * the layer PNGs by other paths, and a two-layer weapon (battlesign, frying pan, dagger) is
     * exactly where a wrong layer count would surface. Third person for the whole silhouette and
     * because it is what other players (and a dedicated server's clients) see; {@link
     * #settleWeaponFirstPerson} takes the companion first-person frame, which since issue #217 is
     * the one that actually shows the blade face.
     *
     * <p>The tools are built with {@code ToolAssemblyRecipes#assemble}, which is the same call the
     * station's own menu makes, so the captured stack carries the real components (materials, stats,
     * the vanilla tool component) and the per-layer material tint is real rather than staged.
     */
    private static void holdWeapon(Minecraft mc) {
        if (stageTicks < SCREEN_GAP_TICKS) {
            return;
        }
        // The two indices past the list are the #257 modified-broadsword and #284 broken-broadsword
        // poses; see currentWeaponFileName.
        if (weaponIndex > WEAPONS.size() + 1) {
            advance(Stage.WEAR_ARMOR);
            return;
        }
        ToolItem weapon = currentWeapon();
        boolean modified = weaponIndex == WEAPONS.size();
        boolean broken = weaponIndex == WEAPONS.size() + 1;
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}holding {} for its third-person capture", LOG_PREFIX, currentWeaponFileName());
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            ItemStack stack = assembleForDisplay(serverPlayer, weapon);
            if (stack.isEmpty()) {
                LOGGER.error("{}#155 scene check FAILED: could not assemble {}", LOG_PREFIX, weaponName(weapon));
            }
            if (modified) {
                stack.set(ForgeweaveDataComponents.MODIFIERS.get(), MODIFIED_WEAPON_MODIFIERS);
            }
            if (broken) {
                // #284's frame: the model must swap the blade layer for its broken art. Set straight
                // on the stack rather than mined down to zero durability -- the component is what the
                // model's forgeweave:broken predicate reads either way.
                stack.set(ForgeweaveDataComponents.BROKEN.get(), true);
            }
            serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
            // Away from the blocks the earlier scenes left standing, so the silhouette reads against
            // flat ground rather than against a Tool Forge.
            BlockPos stand = origin.offset(0, 0, -WEAPON_SCENE_DISTANCE);
            serverPlayer.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            serverPlayer.setYRot(180.0F);
            serverPlayer.setXRot(0.0F);
        });
        advance(Stage.SETTLE_WEAPON_FIRST_PERSON);
    }

    private static void settleWeapon(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        ToolItem weapon = currentWeapon();
        // Self-diagnosing, like the block scenes: an empty hand and a wrongly-rendered tool look the
        // same in a PNG and need very different fixes. The #257 pose additionally logs the held
        // stack's modifier list -- an unmodified broadsword in that frame renders overlay-free and
        // would otherwise pass for a rendering bug.
        if (mc.player != null) {
            ItemStack held = mc.player.getMainHandItem();
            if (held.isEmpty() || !held.is(weapon)) {
                LOGGER.error("{}#155 scene check FAILED: client sees {} in hand, expected {}",
                        LOG_PREFIX, held, weaponName(weapon));
            } else {
                LOGGER.info("{}#155 scene check: client holds {} with materials {} modifiers {}", LOG_PREFIX,
                        weaponName(weapon), held.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()),
                        held.get(ForgeweaveDataComponents.MODIFIERS.get()));
            }
        }
        capture(mc, "weapon_" + currentWeaponFileName());
        if (weaponIndex < WEAPONS.size() && OFFHAND_WEAPONS.contains(WEAPONS.get(weaponIndex))) {
            // #699: same stack, other hand. Moved rather than re-assembled so the off-hand frames
            // show exactly the tool the main-hand frames did.
            var server = mc.getSingleplayerServer();
            mc.options.setCameraType(CameraType.FIRST_PERSON);
            server.execute(() -> {
                ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
                serverPlayer.setItemInHand(InteractionHand.OFF_HAND,
                        serverPlayer.getItemInHand(InteractionHand.MAIN_HAND));
                serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            });
            advance(Stage.SETTLE_WEAPON_OFFHAND_FIRST_PERSON);
            return;
        }
        weaponIndex++;
        advance(Stage.HOLD_WEAPON);
    }

    /**
     * Issue #699's tools, the five whose {@code display} block is upstream 1.12's own rather than
     * {@code item/handheld}'s and therefore the five whose {@code *_lefthand} entries are transcribed
     * by hand ({@code ForgeweaveItemModelProvider#TOOL_DISPLAY_OVERRIDES}). Each gets two extra
     * frames with the tool in the off-hand, {@code weapon_<tool>_offhand_firstperson.png} and
     * {@code weapon_<tool>_offhand.png} -- the beta.1 playtest caught all five posing wrong there and
     * nowhere else.
     */
    static final Set<Supplier<? extends ToolItem>> OFFHAND_WEAPONS = Set.of(
            ForgeweaveItems.TOOL_CLEAVER, ForgeweaveItems.TOOL_RAPIER, ForgeweaveItems.TOOL_BATTLESIGN,
            ForgeweaveItems.TOOL_SHORTBOW, ForgeweaveItems.TOOL_LONGBOW, ForgeweaveItems.TOOL_CROSSBOW);

    private static void settleWeaponOffhandFirstPerson(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        if (mc.player != null && !mc.player.getOffhandItem().is(currentWeapon())) {
            LOGGER.error("{}#699 scene check FAILED: client sees {} in off-hand, expected {}",
                    LOG_PREFIX, mc.player.getOffhandItem(), currentWeaponFileName());
        }
        capture(mc, "weapon_" + currentWeaponFileName() + "_offhand_firstperson");
        // Front rather than back: the off-hand pose is a mirror, and a mirror read edge-on from
        // behind is the same sliver its main-hand sibling is (see settleWeaponFirstPerson).
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        advance(Stage.SETTLE_WEAPON_OFFHAND);
    }

    private static void settleWeaponOffhand(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        capture(mc, "weapon_" + currentWeaponFileName() + "_offhand");
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getPlayerList().getPlayers().get(0)
                .setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY));
        weaponIndex++;
        advance(Stage.HOLD_WEAPON);
    }

    /**
     * Issue #679's in-world scene, {@code armor_<scene>.png}: a plate set worn by the player,
     * captured from the front in third person -- the one render path that exercises
     * {@code ArmorPieceItem#plateMaterial}'s two worn layers and their runtime material tint (#726),
     * which no inventory sprite does -- then again in first person
     * ({@code armor_<scene>_firstperson.png}, the release checklist's second angle, #682). The
     * pieces are assembled through {@link #assembleForDisplay}, the station's own call, so the worn
     * stack carries real materials. One pass per {@link #ARMOR_SCENES} row.
     */
    private static void wearArmor(Minecraft mc) {
        if (stageTicks < SCREEN_GAP_TICKS) {
            return;
        }
        if (armorSceneIndex >= ARMOR_SCENES.size()) {
            advance(Stage.FIRE_BOW_SETUP);
            return;
        }
        ArmorScene scene = ARMOR_SCENES.get(armorSceneIndex);
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}wearing {} for its third-person capture", LOG_PREFIX, scene.fileName());
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            for (Supplier<ArmorPieceItem> piece : scene.pieces()) {
                ItemStack stack = assembleForDisplay(serverPlayer, piece.get(), scene.material());
                if (stack.isEmpty()) {
                    LOGGER.error("{}#679 scene check FAILED: could not assemble {} {}", LOG_PREFIX,
                            scene.material(), weaponName(piece.get()));
                }
                serverPlayer.setItemSlot(piece.get().getEquipmentSlot(), stack);
            }
            BlockPos stand = origin.offset(0, 0, -WEAPON_SCENE_DISTANCE);
            serverPlayer.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            serverPlayer.setYRot(180.0F);
            serverPlayer.setXRot(0.0F);
        });
        mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
        advance(Stage.SETTLE_ARMOR);
    }

    private static void settleArmor(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        ArmorScene scene = ARMOR_SCENES.get(armorSceneIndex);
        if (mc.player != null) {
            for (Supplier<ArmorPieceItem> piece : scene.pieces()) {
                ItemStack worn = mc.player.getItemBySlot(piece.get().getEquipmentSlot());
                if (!worn.is(piece.get())) {
                    LOGGER.error("{}#679 scene check FAILED: client wears {} where {} was expected", LOG_PREFIX, worn,
                            weaponName(piece.get()));
                } else {
                    LOGGER.info("{}#679 scene check: client wears {} with materials {}", LOG_PREFIX,
                            weaponName(piece.get()), worn.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()));
                }
            }
        }
        capture(mc, scene.fileName());
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        advance(Stage.SETTLE_ARMOR_FIRST_PERSON);
    }

    private static void settleArmorFirstPerson(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        ArmorScene scene = ARMOR_SCENES.get(armorSceneIndex);
        capture(mc, scene.fileName() + "_firstperson");
        // Off again, so the next set (and the bow frames after) start from a bare player.
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            for (Supplier<ArmorPieceItem> piece : ARMOR_SET) {
                serverPlayer.setItemSlot(piece.get().getEquipmentSlot(), ItemStack.EMPTY);
            }
        });
        armorSceneIndex++;
        advance(Stage.WEAR_ARMOR);
    }

    /** The four plate pieces, in vanilla's slot order. */
    private static final List<Supplier<ArmorPieceItem>> ARMOR_SET = List.of(
            ForgeweaveItems.ARMOR_HELMET, ForgeweaveItems.ARMOR_CHESTPLATE,
            ForgeweaveItems.ARMOR_LEGGINGS, ForgeweaveItems.ARMOR_BOOTS);

    /** One worn capture pair: the file stem, the plating-and-maille material, and the pieces put on. */
    private record ArmorScene(String fileName, String material, List<Supplier<ArmorPieceItem>> pieces) {}

    /**
     * The release checklist's worn sets (docs/SCOPE.md M4 gates, #682): the iron set, the cobalt set
     * -- a second metal tint over the same grayscale bases -- and one obsidian-plated piece alone,
     * the non-metal plating the cast bootstrap starts from, worn over a bare body so a plating layer
     * bleeding outside its piece would show.
     */
    private static final List<ArmorScene> ARMOR_SCENES = List.of(
            new ArmorScene("armor_iron", "iron", ARMOR_SET),
            new ArmorScene("armor_cobalt", "cobalt", ARMOR_SET),
            new ArmorScene("armor_obsidian_chestplate", "obsidian", List.of(ForgeweaveItems.ARMOR_CHESTPLATE)),
            // #735: the heavy set, iron -- the plate set's layers until M9's own art.
            new ArmorScene("armor_heavy_iron", "iron", List.of(
                    ForgeweaveItems.ARMOR_HEAVY_HELMET, ForgeweaveItems.ARMOR_HEAVY_CHESTPLATE,
                    ForgeweaveItems.ARMOR_HEAVY_LEGGINGS, ForgeweaveItems.ARMOR_HEAVY_BOOTS)));

    /**
     * {@link #WEAPONS} by index, or the broadsword for the two extra poses past the end: #257's
     * modified one and #284's broken one.
     */
    private static ToolItem currentWeapon() {
        return weaponIndex < WEAPONS.size()
                ? WEAPONS.get(weaponIndex).get()
                : ForgeweaveItems.TOOL_BROADSWORD.get();
    }

    private static String currentWeaponFileName() {
        if (weaponIndex < WEAPONS.size()) {
            return weaponName(currentWeapon());
        }
        return weaponIndex == WEAPONS.size() ? "broadsword_modified" : "broadsword_broken";
    }

    /**
     * Issue #217's second frame per weapon, {@code weapon_<tool>_firstperson.png}.
     *
     * <p>The third-person capture above is the "what other players see" artifact, but it cannot be
     * the one a reviewer judges the art from: {@code item/handheld}'s third-person pose lays a flat
     * item in the player's own sagittal plane, and {@code CameraType.THIRD_PERSON_BACK} looks
     * straight down that plane, so the blade is edge-on -- a one-pixel-thick sliver. That is vanilla
     * behaviour, not a Forgeweave bug: a vanilla iron sword held in the same frame renders as the
     * same sliver (issue #217 verification). First person is where {@code item/handheld} actually
     * shows a blade, so every weapon now gets both frames.
     */
    private static void settleWeaponFirstPerson(Minecraft mc) {
        // Putting a new tool in hand resets the attack cooldown, and ItemInHandRenderer ties the
        // first-person hand's height to getAttackStrengthScale -- so until the swing has refilled
        // the tool is still on its way up from off-screen and the frame catches a stump of handle.
        // A slow tool (the hammer) takes longer to refill than SCREEN_SETTLE_TICKS, hence the wait,
        // capped so a future tool with an absurd attack speed cannot hang the harness.
        boolean swingReady = mc.player == null || mc.player.getAttackStrengthScale(1.0F) >= 1.0F;
        if (stageTicks < SCREEN_SETTLE_TICKS || (!swingReady && stageTicks < WEAPON_SWING_RESET_TICKS)) {
            return;
        }
        capture(mc, "weapon_" + currentWeaponFileName() + "_firstperson");
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        advance(Stage.SETTLE_WEAPON);
    }

    /**
     * Issue #400's in-world scene: one first-person capture per bow draw state, {@code
     * bow_<bow>_draw<stage>_firstperson.png} and {@code bow_crossbow_loaded_firstperson.png}.
     *
     * <p>The bow goes in the player's hand server-side, exactly as {@link #holdWeapon} does it, and
     * the crossbow's loaded pose additionally gets its {@code CROSSBOW_LOADED} component set
     * straight on the stack -- the same shortcut #284's broken pose takes, and for the same reason:
     * the item property reads the component either way, and cranking a real crossbow through the
     * server would take 45 ticks of a use the harness cannot drive from here.
     *
     * <p>{@link #settleBowPose} then does the drawing, client-side.
     *
     * <p>T52 (issue #483) puts arrows in the player's inventory here, because the nocked arrow a bow
     * draws is the ammo {@code BowItem#ammoToRender} finds: with an empty quiver every one of these
     * frames would show a bow with nothing on the string, which is exactly the state the ticket was
     * filed about. Not the hotbar and not a hand -- the offhand would win the ammo lookup and put a
     * second arrow in the frame. The undrawn bows on {@link #WEAPONS} are captured before this stage
     * runs and so keep their bare-bow frames, which is what they are for.
     */
    private static void holdBowPose(Minecraft mc) {
        if (stageTicks < SCREEN_GAP_TICKS) {
            return;
        }
        if (bowPoseIndex >= bowPoses().size()) {
            if (!modernBowPass) {
                // #723: same scene again under the other setting.
                modernBowPass = true;
                bowPoseIndex = 0;
                ForgeweaveClientConfig.HELD_BOW_POSE.set(HeldBowPose.MODERN);
                return;
            }
            ForgeweaveClientConfig.HELD_BOW_POSE.set(HeldBowPose.DEFAULT);
            advance(Stage.OPEN_SCREEN);
            return;
        }
        BowPose pose = bowPoses().get(bowPoseIndex);
        BowItem bow = pose.bow().get();
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}holding {} for its first-person capture", LOG_PREFIX, bowPoseFileName());
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            ItemStack stack = assembleForDisplay(serverPlayer, bow);
            if (stack.isEmpty()) {
                LOGGER.error("{}#400 scene check FAILED: could not assemble {}", LOG_PREFIX, pose.fileName());
            }
            if (pose.loaded()) {
                CrossbowItem.setLoaded(stack, true);
            }
            serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, stack);
            serverPlayer.getInventory().setItem(9, new ItemStack(Items.ARROW, 16));
            BlockPos stand = origin.offset(0, 0, -WEAPON_SCENE_DISTANCE);
            serverPlayer.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            serverPlayer.setYRot(180.0F);
            serverPlayer.setXRot(0.0F);
        });
        advance(Stage.SETTLE_BOW_POSE);
    }

    /**
     * Draws the held bow until it is at exactly the stage this frame is for, then captures.
     *
     * <p>The draw goes through the client's own use loop (use key held, {@code MultiPlayerGameMode#
     * useItem}), the way {@link #fireBowDraw} does it and a real right-click does: the two item
     * properties the model branches on read the client-side holder, whose {@code isUsingItem()} is a
     * server-synced flag that only the real use packet flips (issue #673). Capturing on the stage
     * the model itself would resolve, rather than after a fixed number of ticks, is what makes the
     * frame independent of the assembled bow's draw speed: a change to the limb materials or to
     * {@code drawTime} moves the tick count, never the frame.
     */
    private static void settleBowPose(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS || mc.player == null) {
            return;
        }
        BowPose pose = bowPoses().get(bowPoseIndex);
        BowItem bow = pose.bow().get();
        ItemStack held = mc.player.getMainHandItem();
        if (held.isEmpty() || !held.is(bow)) {
            LOGGER.error("{}#400 scene check FAILED: client sees {} in hand, expected {}",
                    LOG_PREFIX, held, bowPoseFileName());
            capture(mc, bowPoseFileName() + "_firstperson");
            nextBowPose(mc);
            return;
        }
        if (pose.drawStage() > 0) {
            String tool = BuiltInRegistries.ITEM.getKey(bow).getPath();
            float pull = ForgeweaveItemProperties.pull(held, mc.player);
            int reached = ToolArt.drawStage(tool, pull);
            switch (drawStep(stageTicks, mc.player.isUsingItem(), reached, pose.drawStage())) {
                case PRESS -> {
                    // Issue #673: the same real use path as fireBowDraw. A client-only startUsingItem
                    // never flips isUsingItem() -- that flag is server-synced -- and the use key has
                    // to read as held or handleKeybinds releases the draw next tick.
                    KeyMapping.set(mc.options.keyUse.getKey(), true);
                    mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                    return;
                }
                case WAIT -> {
                    return;
                }
                case TIMED_OUT -> LOGGER.error("{}#400 scene check FAILED: {} timed out after {} ticks"
                        + " (using={}, pull {}, stage {}, wanted {})", LOG_PREFIX, pose.fileName(), stageTicks,
                        mc.player.isUsingItem(), pull, reached, pose.drawStage());
                case CAPTURE -> LOGGER.info("{}#400 scene check: {} is at pull {} (stage {}, wanted {})",
                        LOG_PREFIX, pose.fileName(), pull, reached, pose.drawStage());
            }
            if (reached != pose.drawStage()) {
                LOGGER.error("{}#400 scene check FAILED: {} settled at stage {} rather than {} -- the"
                        + " capture shows the wrong draw art no matter what the renderer does",
                        LOG_PREFIX, pose.fileName(), reached, pose.drawStage());
            }
        } else {
            LOGGER.info("{}#400 scene check: {} loaded={}", LOG_PREFIX, pose.fileName(),
                    ForgeweaveItemProperties.loaded(held));
        }
        capture(mc, bowPoseFileName() + "_firstperson");
        if (pose.thirdPerson()) {
            mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
            advance(Stage.SETTLE_BOW_POSE_THIRD_PERSON);
            return;
        }
        nextBowPose(mc);
    }

    /**
     * Issue #425's frames, {@code bow_crossbow_draw{2,3}.png} and {@code bow_crossbow_loaded.png}:
     * the same poses again from behind, which is the only place the arms are visible.
     *
     * <p>The use started by {@link #settleBowPose} is deliberately still running -- {@link
     * #nextBowPose} is what ends it -- because the cranking pose is a pose the holder has to still be
     * <em>in</em>: {@code PlayerRenderer#getArmPose} reads the client entity's use flags, synced
     * from the server once the use packet landed.
     */
    private static void settleBowPoseThirdPerson(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        capture(mc, bowPoseFileName());
        nextBowPose(mc);
    }

    /** What {@link #settleBowPose} does on a given tick of a drawn pose; a seam for the unit test. */
    enum DrawStep { PRESS, WAIT, CAPTURE, TIMED_OUT }

    static DrawStep drawStep(int stageTicks, boolean using, int reached, int wanted) {
        if (stageTicks == SCREEN_SETTLE_TICKS) {
            return DrawStep.PRESS;
        }
        if (stageTicks >= BOW_DRAW_TIMEOUT_TICKS) {
            return DrawStep.TIMED_OUT;
        }
        return using && reached >= wanted ? DrawStep.CAPTURE : DrawStep.WAIT;
    }

    private static void nextBowPose(Minecraft mc) {
        // Stop the use on the server before the key goes up, so handleKeybinds has nothing to release
        // and the pose does not end in a creative arrow. ponytail: if the release packet wins the
        // race the bow fires once; harmless, the next pose re-stages the hand anyway.
        var server = mc.getSingleplayerServer();
        server.execute(() -> server.getPlayerList().getPlayers().get(0).stopUsingItem());
        if (mc.player != null) {
            mc.player.stopUsingItem();
        }
        KeyMapping.set(mc.options.keyUse.getKey(), false);
        bowPoseIndex++;
        advance(Stage.HOLD_BOW_POSE);
    }

    /** The tool the Tool Station would build from an iron head and wooden everything else. */
    private static ItemStack assembleForDisplay(ServerPlayer player, Item weapon) {
        return assembleForDisplay(player, weapon, "iron");
    }

    /** As above with the head material chosen by the caller -- #236's wall builds one per material. */
    private static ItemStack assembleForDisplay(ServerPlayer player, Item weapon, String headMaterial) {
        return ToolAssemblyRecipes.entryFor(new ItemStack(weapon))
                .flatMap(entry -> ToolAssemblyRecipes.assemble(player.registryAccess(), entry,
                        entry.constants().parts().stream()
                                .map(slot -> switch (slot.role()) {
                                    case HEAD -> material(headMaterial);
                                    // M3.5 #400: a bow's string slot refuses every material but a
                                    // stringy one (issue #392's BOWSTRING stat gate), so "wood
                                    // everything else" cannot assemble one.
                                    case BOWSTRING -> material("string");
                                    // #653: the arrow's fletching slot likewise takes no wood.
                                    case FLETCHING -> material("feather");
                                    // #679: the plate set's plating and maille are the head material too.
                                    case PLATING, MAILLE -> material(headMaterial);
                                    default -> material("wood"); // a SHAFT slot: wood has shaft stats
                                })
                                .toList()))
                .orElse(ItemStack.EMPTY);
    }

    private static ResourceLocation material(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private static String weaponName(Item weapon) {
        return BuiltInRegistries.ITEM.getKey(weapon).getPath();
    }

    /** Logs what the client thinks a scene faucet is doing, and shouts if it is not {@code expectPouring}. */
    private static void checkFaucet(Minecraft mc, @Nullable BlockPos pos, boolean expectPouring) {
        if (mc.level == null || pos == null) {
            return;
        }
        if (!(mc.level.getBlockEntity(pos) instanceof FaucetBlockEntity faucet)) {
            LOGGER.error("{}#200 scene check FAILED: no faucet block entity at {}", LOG_PREFIX, pos);
            return;
        }
        LOGGER.info("{}#200 scene check: client sees faucet at {} pouring={} buffered={} (expected pouring={})",
                LOG_PREFIX, pos, faucet.isPouring(), faucet.buffered(), expectPouring);
        if (faucet.isPouring() != expectPouring) {
            LOGGER.error("{}#200 scene check FAILED: faucet at {} is pouring={} on the client, expected {} -- "
                    + "the capture cannot show the right thing no matter what the renderer does",
                    LOG_PREFIX, pos, faucet.isPouring(), expectPouring);
        }
    }

    /** #204: the mid-pour part-cast table must read a fraction of its recipe's amount, never N/N. */
    private static void checkPartCastFill(Minecraft mc, @Nullable BlockPos pos) {
        if (mc.level == null || pos == null) {
            return;
        }
        if (!(mc.level.getBlockEntity(pos) instanceof CastingBlockEntity casting)) {
            return; // Already reported by the loop above.
        }
        int amount = casting.tank().getFluidAmount();
        int capacity = casting.tank().getCapacity();
        LOGGER.info("{}#204 scene check: client sees the part-cast table at {} at {}/{} mB (expected a fraction of {})",
                LOG_PREFIX, pos, amount, capacity, CASTING_SCENE_PART_CAST_AMOUNT);
        if (capacity != CASTING_SCENE_PART_CAST_AMOUNT || amount <= 0 || amount >= capacity) {
            LOGGER.error("{}#204 scene check FAILED: the part-cast table at {} reads {}/{} mB on the client, "
                    + "expected a partial pour out of {} -- it will render as a full table",
                    LOG_PREFIX, pos, amount, capacity, CASTING_SCENE_PART_CAST_AMOUNT);
        }
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
            advance(Stage.OPEN_BOOK);
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
                screen.afterOpen().accept(serverPlayer);
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

    /**
     * The #430 guide-book scenes: the closed cover, the opened index leaf, the first full spread
     * -- the three chrome states the 1.12 parity review compares against Mantle's book -- and the
     * #651 smeltery structure spread, opened through its bookmark. The
     * book is a plain screen with no menu behind it, so unlike {@link #openScreen} there is no
     * server-side work: the screen is set directly, and {@link BookScreen#openSpread} turns it to
     * the scene's spread.
     */
    private static void openBook(Minecraft mc) {
        if (stageTicks < SCREEN_GAP_TICKS) {
            return;
        }
        if (bookSceneIndex >= BOOK_SCENES.size()) {
            advance(Stage.OPEN_PONDER);
            return;
        }
        BookScene scene = BOOK_SCENES.get(bookSceneIndex);
        LOGGER.info("{}opening the guide book for {}", LOG_PREFIX, scene.fileName());
        BookScreen screen = scene.bookmark() != null
                ? new BookScreen(BookContent.sections(mc.level.registryAccess()), scene.bookmark(), null)
                : new BookScreen(BookContent.sections(mc.level.registryAccess()));
        mc.setScreen(screen);
        if (scene.bookmark() == null && scene.spread() >= 0) {
            screen.openSpread(scene.spread());
        }
        advance(Stage.SETTLE_BOOK);
    }

    private static void settleBook(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS) {
            return;
        }
        capture(mc, BOOK_SCENES.get(bookSceneIndex).fileName());
        if (mc.screen != null) {
            mc.screen.onClose();
        }
        bookSceneIndex++;
        advance(Stage.OPEN_BOOK);
    }

    /**
     * #700: every Ponder scene, opened through Ponder's own UI and captured on its finished frame --
     * the one frame that shows the whole structure, every directional block included, from the
     * default camera. See {@link PonderHarnessCaptures}.
     */
    private static void openPonder(Minecraft mc) {
        if (stageTicks < SCREEN_GAP_TICKS) {
            return;
        }
        if (ponderCaptureIndex >= PonderHarnessCaptures.CAPTURES.size()) {
            LOGGER.info("{}all {} screens, {} book scenes and {} ponder scenes captured, exiting", LOG_PREFIX,
                    SCREENS.size(), BOOK_SCENES.size(), PonderHarnessCaptures.CAPTURES.size());
            mc.stop();
            advance(Stage.DONE);
            return;
        }
        PonderHarnessCaptures.Capture capture = PonderHarnessCaptures.CAPTURES.get(ponderCaptureIndex);
        LOGGER.info("{}opening ponder scene {}", LOG_PREFIX, capture.fileName());
        PonderHarnessCaptures.open(capture);
        advance(Stage.SETTLE_PONDER);
    }

    private static void settlePonder(Minecraft mc) {
        if (!PonderHarnessCaptures.finished(mc) && stageTicks < PONDER_SCENE_TIMEOUT_TICKS) {
            return;
        }
        PonderHarnessCaptures.Capture capture = PonderHarnessCaptures.CAPTURES.get(ponderCaptureIndex);
        if (!PonderHarnessCaptures.finished(mc)) {
            LOGGER.error("{}ponder scene {} never reached its finished frame; capturing it as is", LOG_PREFIX,
                    capture.fileName());
        }
        capture(mc, capture.fileName());
        if (mc.screen != null) {
            mc.screen.onClose();
        }
        ponderCaptureIndex++;
        advance(Stage.OPEN_PONDER);
    }

    /**
     * The window size {@code build.gradle}'s {@code runScreenshotHarness} asks for. Every
     * first-person pose is framed against it: a tiling compositor that re-tiles the window into a
     * portrait column (Hyprland did, #712) pushes anything held at the right edge of the frame --
     * the undrawn bows, laid across the hand at vanilla's own {@code bow.json} offsets -- clean off
     * the picture, and the empty frame reads as a model bug. Mirror of the gradle arguments.
     */
    static final int EXPECTED_FRAME_WIDTH = 854;
    static final int EXPECTED_FRAME_HEIGHT = 480;

    /** How far the captured aspect ratio may drift from {@link #EXPECTED_FRAME_WIDTH}:{@link #EXPECTED_FRAME_HEIGHT} (window decorations, HiDPI rounding) before the frame is not the one the poses were tuned against. */
    static final double FRAME_ASPECT_TOLERANCE = 0.1;

    /** #712: whether a {@code width x height} frame is the shape the harness asked for. */
    static boolean isExpectedFrameShape(int width, int height) {
        double expected = (double) EXPECTED_FRAME_WIDTH / EXPECTED_FRAME_HEIGHT;
        return height > 0 && Math.abs((double) width / height - expected) <= expected * FRAME_ASPECT_TOLERANCE;
    }

    private static void capture(Minecraft mc, String fileName) {
        NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        try {
            if (!isExpectedFrameShape(image.getWidth(), image.getHeight())) {
                LOGGER.error("{}#712 scene check FAILED: {} captured at {}x{}, expected the {}x{} window from build.gradle"
                        + " -- the window manager resized the client; first-person poses at the frame edge are cut off",
                        LOG_PREFIX, fileName, image.getWidth(), image.getHeight(), EXPECTED_FRAME_WIDTH, EXPECTED_FRAME_HEIGHT);
            }
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

    // ---------------------------------------------------------------- issue #670: the real client use loop

    /** The launchers issue #670 reported stuck; each is drawn and released through the real client path. */
    private static final List<Supplier<? extends BowItem>> FIRE_BOWS = List.of(
            ForgeweaveItems.TOOL_SHORTBOW, ForgeweaveItems.TOOL_LONGBOW, ForgeweaveItems.TOOL_CROSSBOW);
    /** Long enough for every one of {@link #FIRE_BOWS} at iron/wood stats (crossbow: 0.5 x 100 / 45 > 1). */
    private static final int FIRE_BOW_DRAW_TICKS = 100;
    private static int fireBowIndex;

    /**
     * Survival, a vanilla arrow in the main inventory, the assembled bow in hand -- the playtest
     * setup of issue #670, staged on the server exactly as {@link #holdBowPose} stages the poses.
     */
    private static void fireBowSetup(Minecraft mc) {
        if (stageTicks < SCREEN_GAP_TICKS) {
            return;
        }
        if (fireBowIndex >= FIRE_BOWS.size()) {
            var server = mc.getSingleplayerServer();
            server.execute(() -> server.getPlayerList().getPlayers().get(0).setGameMode(GameType.CREATIVE));
            advance(Stage.HOLD_BOW_POSE);
            return;
        }
        BowItem bow = FIRE_BOWS.get(fireBowIndex).get();
        var server = mc.getSingleplayerServer();
        LOGGER.info("{}#670 fire check: staging {}", LOG_PREFIX, BuiltInRegistries.ITEM.getKey(bow));
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            serverPlayer.setGameMode(GameType.SURVIVAL);
            serverPlayer.setItemInHand(InteractionHand.MAIN_HAND, assembleForDisplay(serverPlayer, bow));
            serverPlayer.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            serverPlayer.getInventory().setItem(9, new ItemStack(Items.ARROW, 16));
            BlockPos stand = origin.offset(0, 0, -WEAPON_SCENE_DISTANCE);
            serverPlayer.teleportTo(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5);
            serverPlayer.setYRot(180.0F);
            serverPlayer.setXRot(-20.0F);
        });
        advance(Stage.FIRE_BOW_DRAW);
    }

    /**
     * The client's own use loop, what {@code Minecraft#startUseItem} and {@code #handleKeybinds} call
     * on a right-click press and release: {@code MultiPlayerGameMode#useItem} (sends the use packet
     * and predicts locally) and {@code #releaseUsingItem} (sends {@code RELEASE_USE_ITEM}).
     */
    private static void fireBowDraw(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        boolean crossbow = FIRE_BOWS.get(fireBowIndex).get() instanceof CrossbowItem;
        // The use key must read as held for the whole draw: Minecraft#handleKeybinds releases the
        // use the first tick it sees isUsingItem() with the key up, so a bare useItem() call is a
        // 0-tick draw (what this stage's first version measured).
        if (stageTicks == SCREEN_GAP_TICKS) {
            KeyMapping.set(mc.options.keyUse.getKey(), true);
            var result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            LOGGER.info("{}#670 fire check: client useItem -> {}, client using={}", LOG_PREFIX, result,
                    mc.player.isUsingItem());
        } else if (stageTicks == SCREEN_GAP_TICKS + FIRE_BOW_DRAW_TICKS) {
            LOGGER.info("{}#670 fire check: before release client using={} ticksUsing={} pull={}", LOG_PREFIX,
                    mc.player.isUsingItem(), mc.player.getTicksUsingItem(),
                    ForgeweaveItemProperties.pull(mc.player.getMainHandItem(), mc.player));
            KeyMapping.set(mc.options.keyUse.getKey(), false); // handleKeybinds sends RELEASE_USE_ITEM
        } else if (crossbow && stageTicks == SCREEN_GAP_TICKS + FIRE_BOW_DRAW_TICKS + 10) {
            var result = mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
            LOGGER.info("{}#670 fire check: loaded crossbow useItem -> {}", LOG_PREFIX, result);
        } else if (stageTicks == SCREEN_GAP_TICKS + FIRE_BOW_DRAW_TICKS + 20) {
            advance(Stage.FIRE_BOW_CHECK);
        }
    }

    /** What actually happened: arrows in the world, ammo spent, and whether either side still thinks it is drawing. */
    private static void fireBowCheck(Minecraft mc) {
        if (stageTicks < SCREEN_SETTLE_TICKS || mc.player == null) {
            return;
        }
        boolean clientUsing = mc.player.isUsingItem();
        String name = BuiltInRegistries.ITEM.getKey(FIRE_BOWS.get(fireBowIndex).get()).toString();
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer serverPlayer = server.getPlayerList().getPlayers().get(0);
            List<AbstractArrow> arrows = serverPlayer.serverLevel().getEntitiesOfClass(AbstractArrow.class,
                    serverPlayer.getBoundingBox().inflate(64.0));
            int ammo = serverPlayer.getInventory().countItem(Items.ARROW);
            String verdict = arrows.size() == 1 && !clientUsing && !serverPlayer.isUsingItem() ? "OK" : "FAILED";
            LOGGER.info("{}#670 fire check {}: {} -> arrows={} ammoLeft={} serverUsing={} clientUsing={} held={}",
                    LOG_PREFIX, verdict, name, arrows.size(), ammo, serverPlayer.isUsingItem(), clientUsing,
                    serverPlayer.getMainHandItem());
            arrows.forEach(AbstractArrow::discard);
        });
        fireBowIndex++;
        advance(Stage.FIRE_BOW_SETUP);
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
     * Issue #408's capture: a 5x3x2 interior, so 30 melt slots -- ten rows in a grid capped at
     * {@link SmelteryMenu#MELT_MAX_ROWS}, which is the only state that draws the slider. Items in the
     * first row so the capture also shows that the heat bars still line up at the grid's full size.
     */
    private static void buildLargeSmeltery(ServerLevel level, BlockPos corePos) {
        SmelteryControllerBlockEntity controller = buildSmeltery(level, corePos, 2, 3);
        if (controller == null) {
            return;
        }
        for (int i = 0; i < 4; i++) {
            controller.insertForMelting(new ItemStack(Items.IRON_INGOT));
        }
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
            BiConsumer<ServerLevel, BlockPos> prepare, Consumer<ServerPlayer> afterOpen) {
        HarnessScreen(String fileName, Supplier<? extends Block> block) {
            this(fileName, block, (level, pos) -> {});
        }

        HarnessScreen(String fileName, Supplier<? extends Block> block, BiConsumer<ServerLevel, BlockPos> prepare) {
            this(fileName, block, prepare, player -> {});
        }
    }

    /**
     * #758: the full {@code partCrafter} workshop upstream's {@code ContainerPartBuilder} wants
     * before it turns the Pattern Chest's slots into a button sidebar -- a Crafting Station and a
     * Stencil Table alongside the chest, one pattern already sitting in it so the capture shows a
     * real button rather than an empty row.
     */
    private static void preparePatternChestScene(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos.east(), ForgeweaveBlocks.PATTERN_CHEST.get().defaultBlockState());
        level.setBlockAndUpdate(pos.south(), ForgeweaveBlocks.STENCIL_TABLE.get().defaultBlockState());
        level.setBlockAndUpdate(pos.north(), ForgeweaveBlocks.CRAFTING_STATION.get().defaultBlockState());
        if (level.getBlockEntity(pos.east()) instanceof ChestBlockEntity chest) {
            chest.container().setItem(0, new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        }
    }

    /**
     * #733's preview scene: a pickaxe tab with an iron head and a wooden handle in their slots and
     * the binding still missing, so the capture shows the big preview tinted from the placed parts
     * with the missing layer grey. The parts go straight into the station's container before the
     * menu opens; the tab is selected through the menu's own button path once it has, which is
     * what a click on the sidebar does.
     */
    private static void loadPickaxeParts(ServerLevel level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof ToolStationBlockEntity station)) {
            return;
        }
        ToolStationTabs.Tab tab = ToolStationTabs.get(ToolStationTabs.indexOfTool(ForgeweaveItems.TOOL_PICKAXE.get()));
        ItemStack head = new ItemStack(tab.part(ToolStationMenu.HEAD_SLOT));
        head.set(ForgeweaveDataComponents.MATERIAL.get(), material("iron"));
        ItemStack handle = new ItemStack(tab.part(ToolStationMenu.HANDLE_SLOT));
        handle.set(ForgeweaveDataComponents.MATERIAL.get(), material("wood"));
        station.container().setItem(ToolStationMenu.HEAD_SLOT, head);
        station.container().setItem(ToolStationMenu.HANDLE_SLOT, handle);
    }

    /** Selects the roster's last tab, so the screen opens on the grid's last page (#733). */
    private static void selectLastTab(ServerPlayer player) {
        if (player.containerMenu instanceof ToolStationMenu menu) {
            menu.clickMenuButton(player, menu.visibleTabs().getLast());
        }
    }

    private static void selectPickaxeTab(ServerPlayer player) {
        if (player.containerMenu instanceof ToolStationMenu menu) {
            menu.clickMenuButton(player, ToolStationTabs.indexOfTool(ForgeweaveItems.TOOL_PICKAXE.get()));
        }
    }
}
