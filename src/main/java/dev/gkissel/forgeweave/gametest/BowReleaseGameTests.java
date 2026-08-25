package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.CrossbowItem;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #670: every launcher drawn in a real client stuck at full draw and never fired. The other
 * bow GameTests drive the item directly ({@code Item#releaseUsing} with a synthetic {@code timeLeft}
 * on an unticked mock {@code Player}), which is the one path a real player never takes. These run
 * the real one: a {@link ServerPlayer} the level ticks, {@code ServerPlayerGameMode#useItem} for the
 * click, the level's own ticks for the draw, and {@code LivingEntity#releaseUsingItem} for the
 * release -- what {@code ServerboundPlayerActionPacket.RELEASE_USE_ITEM} calls.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class BowReleaseGameTests {

    private static final int DRAW_TICKS = 100;

    /**
     * A real {@link ServerPlayer} in the test's level, ticked the way a connected one is: the level
     * never calls {@code ServerPlayer#doTick} itself -- {@code ServerGamePacketListenerImpl#tick} does,
     * and the mock connection {@code makeMockServerPlayerInLevel} hands out is on no listener the
     * server ticks. Without it {@code useItemRemaining} never counts down and every draw is 0 ticks.
     */
    private static ServerPlayer tickedPlayer(GameTestHelper helper, GameType mode) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(mode);
        Vec3 stand = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        player.moveTo(stand.x, stand.y, stand.z, 0.0F, 0.0F);
        helper.onEachTick(() -> {
            if (!player.isRemoved()) {
                player.doTick();
            }
        });
        return player;
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper) {
        ServerPlayer player = tickedPlayer(helper, GameType.SURVIVAL);
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 5));
        return player;
    }

    /** Arrows that join the level near {@code player} between now and the end of the test. */
    private static List<AbstractArrow> captureArrows(GameTestHelper helper, ServerPlayer player) {
        List<AbstractArrow> captured = new ArrayList<>();
        AABB near = new AABB(player.position(), player.position()).inflate(6.0);
        Consumer<EntityJoinLevelEvent> listener = event -> {
            if (event.getEntity() instanceof AbstractArrow arrow && event.getLevel() == helper.getLevel()
                    && near.contains(arrow.position())) {
                captured.add(arrow);
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EntityJoinLevelEvent.class, listener);
        helper.onEachTick(() -> {
            if (helper.getTick() >= DRAW_TICKS + 5) {
                NeoForge.EVENT_BUS.unregister(listener);
            }
        });
        return captured;
    }

    /** The real click: what {@code ServerGamePacketListenerImpl#handleUseItem} does with it. */
    private static void click(GameTestHelper helper, ServerPlayer player, ItemStack bow) {
        InteractionResult result = player.gameMode.useItem(player, helper.getLevel(), bow, InteractionHand.MAIN_HAND);
        helper.assertTrue(result.consumesAction(), "the click must start a draw, got " + result);
        helper.assertTrue(player.isUsingItem(), "the server must be drawing after the click");
    }

    private static void assertFired(GameTestHelper helper, ServerPlayer player, List<AbstractArrow> arrows, String bow) {
        helper.assertTrue(!player.isUsingItem(), bow + ": the release must end the draw");
        helper.assertTrue(arrows.size() == 1, bow + ": one arrow per release, got " + arrows.size());
        helper.assertTrue(arrows.get(0).getDeltaMovement().length() > 1.0,
                bow + ": the arrow must actually fly, got " + arrows.get(0).getDeltaMovement());
        helper.assertTrue(player.getInventory().countItem(Items.ARROW) == 4,
                bow + ": one arrow spent, got " + player.getInventory().countItem(Items.ARROW));
        arrows.forEach(AbstractArrow::discard);
        player.discard();
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void shortbowFiresThroughTheRealUsePath(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        ItemStack bow = ToolAssembly.assemble(helper, player, new BlockPos(1, 1, 1),
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_SHORTBOW.get()), List.of("wood", "wood", "string"));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        List<AbstractArrow> arrows = captureArrows(helper, player);

        click(helper, player, bow);
        helper.runAfterDelay(DRAW_TICKS, () -> {
            player.releaseUsingItem();
            helper.runAfterDelay(2, () -> assertFired(helper, player, arrows, "shortbow"));
        });
    }

    @GameTest(template = "empty", timeoutTicks = 300)
    public static void longbowFiresThroughTheRealUsePath(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        ItemStack bow = ToolAssembly.assembleAtForge(helper, player, new BlockPos(1, 1, 1),
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_LONGBOW.get()), List.of("iron", "bone", "iron", "string"));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        List<AbstractArrow> arrows = captureArrows(helper, player);

        click(helper, player, bow);
        helper.runAfterDelay(DRAW_TICKS, () -> {
            player.releaseUsingItem();
            helper.runAfterDelay(2, () -> assertFired(helper, player, arrows, "longbow"));
        });
    }

    /** The crossbow's two phases, both through the real path: a ticked draw loads, the next click fires. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void crossbowLoadsAndFiresThroughTheRealUsePath(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        ItemStack bow = ToolAssembly.assembleAtForge(helper, player, new BlockPos(1, 1, 1),
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_CROSSBOW.get()), List.of("iron", "iron", "iron", "string"));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        List<AbstractArrow> arrows = captureArrows(helper, player);

        click(helper, player, bow);
        helper.runAfterDelay(DRAW_TICKS, () -> {
            player.releaseUsingItem();
            ItemStack held = player.getMainHandItem();
            helper.assertTrue(CrossbowItem.isLoaded(held), "a 100-tick draw (0.5 x 100 / 45 > 1) loads the crossbow");
            helper.assertTrue(arrows.isEmpty(), "loading fires nothing, got " + arrows);
            InteractionResult fire = player.gameMode.useItem(player, helper.getLevel(), held, InteractionHand.MAIN_HAND);
            helper.assertTrue(fire.consumesAction(), "a loaded crossbow fires on the click, got " + fire);
            helper.runAfterDelay(2, () -> assertFired(helper, player, arrows, "crossbow"));
        });
    }

    /**
     * Issue #693: the bolt flies where the player <em>aims</em>, not where the server last saw their
     * head. {@code ServerGamePacketListenerImpl#handleUseItem} writes the click packet's rotation into
     * {@code yRot}/{@code xRot} ({@code Entity#absRotateTo}) but not {@code yHeadRot}, which
     * {@code Player#serverAiStep} only re-syncs on the next tick -- so at fire time
     * {@code LivingEntity#getViewVector} (head) is a tick stale while {@code getYRot} (aim) is current.
     * Upstream fires from {@code rotationPitch}/{@code rotationYaw} ({@code BowCore#getProjectileEntity}
     * via {@code EntityArrow#shoot(entity, pitch, yaw, ...)}), and so must this. Reproduced here by
     * holding the head where the previous tick left it and turning the aim a quarter turn.
     */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void crossbowFiresWhereThePlayerAimsNotWhereTheirHeadWas(GameTestHelper helper) {
        ServerPlayer player = survivalPlayer(helper);
        ItemStack bow = ToolAssembly.assembleAtForge(helper, player, new BlockPos(1, 1, 1),
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_CROSSBOW.get()), List.of("iron", "iron", "iron", "string"));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        List<AbstractArrow> arrows = captureArrows(helper, player);

        click(helper, player, bow);
        helper.runAfterDelay(DRAW_TICKS, () -> {
            player.releaseUsingItem();
            ItemStack held = player.getMainHandItem();
            helper.assertTrue(CrossbowItem.isLoaded(held), "the crossbow must be loaded before the aimed shot");
            // The state handleUseItem leaves: aim updated from the packet, head not yet re-synced.
            player.absRotateTo(90.0F, 0.0F);
            player.setYHeadRot(0.0F);
            player.gameMode.useItem(player, helper.getLevel(), held, InteractionHand.MAIN_HAND);
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(arrows.size() == 1, "one bolt, got " + arrows.size());
                Vec3 flight = arrows.get(0).getDeltaMovement().normalize();
                Vec3 aim = Vec3.directionFromRotation(0.0F, 90.0F);
                helper.assertTrue(flight.dot(aim) > 0.95,
                        "the bolt must follow the aim " + aim + ", got " + flight + " (the stale head points +Z)");
                arrows.forEach(AbstractArrow::discard);
                player.discard();
                helper.succeed();
            });
        });
    }

    /** Vanilla's creative rule ({@code BowCore#getCreativeProjectileStack}): no ammo needed, through the real path. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void creativeShortbowFiresWithoutAmmoThroughTheRealUsePath(GameTestHelper helper) {
        ServerPlayer player = tickedPlayer(helper, GameType.CREATIVE);
        ItemStack bow = ToolAssembly.assemble(helper, player, new BlockPos(1, 1, 1),
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_SHORTBOW.get()), List.of("wood", "wood", "string"));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        List<AbstractArrow> arrows = captureArrows(helper, player);
        helper.assertTrue(BowItem.findAmmo(player).isEmpty(), "creative starts with no ammo");

        click(helper, player, bow);
        helper.runAfterDelay(DRAW_TICKS, () -> {
            player.releaseUsingItem();
            helper.runAfterDelay(2, () -> {
                helper.assertTrue(arrows.size() == 1, "creative: one conjured arrow, got " + arrows.size());
                helper.assertTrue(arrows.get(0).pickup == AbstractArrow.Pickup.CREATIVE_ONLY,
                        "creative arrows are CREATIVE_ONLY, got " + arrows.get(0).pickup);
                arrows.forEach(AbstractArrow::discard);
                player.discard();
                helper.succeed();
            });
        });
    }
}
