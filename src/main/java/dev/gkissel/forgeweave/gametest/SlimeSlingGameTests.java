package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.SlimeBounceHandler;
import dev.gkissel.forgeweave.item.SlimeSlingItem;

/**
 * Parity audit T22 (issue #453): the Slimesling's fling, upstream 1.12's
 * {@code gadgets/item/ItemSlimeSling#onPlayerStoppedUsing} -- flung along the inverted look vector
 * at a third of the force vertically, but only from the ground and only when the player was aiming
 * at a block. {@link SlimeBounceHandler}'s momentum carry rides along.
 *
 * <p>{@code SlimeSlingTest} covers the charge curve itself; the release path needs a real level for
 * its block ray trace, which is why it lives here.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SlimeSlingGameTests {

    private static final float EPSILON = 1.0E-4F;

    /** A full charge: upstream's curve is clamped at 6 well before this. */
    private static final int FULL_CHARGE_TICKS = 40;

    /**
     * Puts {@code player} on a stone block inside this test's own structure, pitched {@code xRot}
     * degrees (90 = straight down at that block), then charges the sling for {@code ticks} and lets
     * go.
     */
    private static ItemStack fling(GameTestHelper helper, Player player, float xRot, boolean onGround, int ticks) {
        helper.setBlock(new BlockPos(0, 0, 0), Blocks.STONE);
        return fling(helper, player, 1.0, xRot, onGround, ticks);
    }

    /**
     * The same, from {@code standY} blocks above the structure floor -- the open-air variant, for the
     * one case that needs nothing at all within reach. The test framework boxes each structure in
     * barrier blocks, so "aim at empty space" has to happen well above that box.
     */
    private static ItemStack fling(GameTestHelper helper, Player player, double standY, float xRot,
            boolean onGround, int ticks) {
        Vec3 stand = helper.absoluteVec(new Vec3(0.5, standY, 0.5));
        player.moveTo(stand.x, stand.y, stand.z, 0.0F, xRot);
        player.setOnGround(onGround);
        player.setDeltaMovement(Vec3.ZERO);
        player.hurtMarked = false;

        ItemStack sling = new ItemStack(ForgeweaveItems.SLIME_SLING.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, sling);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        sling.getItem().releaseUsing(sling, helper.getLevel(), player, sling.getUseDuration(player) - ticks);
        player.stopUsingItem();
        return sling;
    }

    /**
     * Aimed straight down at the block underfoot, a full charge throws the player straight up at
     * {@code force / 3} -- upstream's {@code addVelocity(x * -f, y * -f / 3f, z * -f)} over the
     * inverted look vector.
     */
    @GameTest(template = "empty")
    public static void fullChargeFlingsThePlayerAwayFromTheBlockAimedAt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        fling(helper, player, 90.0F, true, FULL_CHARGE_TICKS);

        Vec3 motion = player.getDeltaMovement();
        helper.assertTrue(Math.abs(motion.y - SlimeSlingItem.MAX_FORCE * SlimeSlingItem.VERTICAL_SCALE) < EPSILON,
                "looking down, a full charge throws the player up at VERTICAL_SCALE of the force, got " + motion);
        helper.assertTrue(Math.abs(motion.x) < EPSILON && Math.abs(motion.z) < EPSILON,
                "straight down means no horizontal push, got " + motion);
        helper.assertTrue(player.hurtMarked, "the velocity change must be flagged for the client resync");
        helper.assertTrue(SlimeBounceHandler.isBouncing(player), "the flung player's momentum must be carried");
        helper.succeed();
    }

    /**
     * Issue #902 (playtest: "vertical launch hits a strange cap"): pins the vertical component to
     * upstream's literal value rather than to {@link SlimeSlingItem#VERTICAL_SCALE} symbolically, so a
     * future change to that constant (like #698's since-superseded tuning) fails this test instead of
     * silently drifting from the {@code y * -f / 3f} upstream ships -- full charge is
     * {@link SlimeSlingItem#MAX_FORCE} (6.0) over 3, i.e. 2.0 blocks/tick straight up.
     */
    @GameTest(template = "empty")
    public static void fullChargeStraightUpPinsUpstreamsVerticalVelocity(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        fling(helper, player, 90.0F, true, FULL_CHARGE_TICKS);

        Vec3 motion = player.getDeltaMovement();
        helper.assertTrue(Math.abs(motion.y - 2.0F) < EPSILON,
                "a full-charge straight-up launch must match upstream's y * -f / 3f = 6.0 / 3 = 2.0, got "
                        + motion);
        helper.succeed();
    }

    /** Upstream charges the fling off the vanilla bow curve: half the charge is nowhere near half the cap. */
    @GameTest(template = "empty")
    public static void aShorterChargeFlingsThePlayerLessFar(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        fling(helper, player, 90.0F, true, 10);

        Vec3 motion = player.getDeltaMovement();
        helper.assertTrue(Math.abs(motion.y - SlimeSlingItem.launchForce(10) * SlimeSlingItem.VERTICAL_SCALE) < EPSILON,
                "half a second of charge is upstream's curve at 10 ticks, got " + motion);
        helper.succeed();
    }

    /** Upstream's {@code if(!player.onGround) return;} -- no double jumping off the sling. */
    @GameTest(template = "empty")
    public static void aSlingDoesNothingInMidair(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        fling(helper, player, 90.0F, false, FULL_CHARGE_TICKS);

        helper.assertTrue(player.getDeltaMovement().equals(Vec3.ZERO),
                "a sling released in midair must not move the player, got " + player.getDeltaMovement());
        helper.assertFalse(player.hurtMarked, "nothing happened, so nothing to resync");
        helper.assertFalse(SlimeBounceHandler.isBouncing(player), "nothing happened, so no momentum to carry");
        helper.succeed();
    }

    /** Upstream's {@code mop.typeOfHit == BLOCK} check: aimed at open sky, the sling does nothing. */
    @GameTest(template = "empty")
    public static void aSlingAimedAtNothingDoesNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        fling(helper, player, 12.0, -90.0F, true, FULL_CHARGE_TICKS);

        helper.assertTrue(player.getDeltaMovement().equals(Vec3.ZERO),
                "no block in reach means no fling, got " + player.getDeltaMovement());
        helper.assertFalse(SlimeBounceHandler.isBouncing(player), "nothing happened, so no momentum to carry");
        helper.succeed();
    }

    /**
     * {@link SlimeBounceHandler}: while the flung player is airborne, each tick divides the
     * horizontal motion by vanilla's air drag, so the arc carries instead of bleeding out. On the
     * ground it leaves the motion alone.
     */
    @GameTest(template = "empty")
    public static void theCarriedMomentumSurvivesTheAirDrag(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        fling(helper, player, 90.0F, true, FULL_CHARGE_TICKS);

        player.setOnGround(false);
        player.setDeltaMovement(new Vec3(0.5, 0.0, 0.25));
        SlimeBounceHandler.onPlayerTickPost(new PlayerTickEvent.Post(player));

        Vec3 carried = player.getDeltaMovement();
        helper.assertTrue(Math.abs(carried.x - 0.5 / 0.935) < EPSILON && Math.abs(carried.z - 0.25 / 0.935) < EPSILON,
                "an airborne tick must undo the 0.935 air drag, got " + carried);

        player.setOnGround(true);
        player.setDeltaMovement(new Vec3(0.5, 0.0, 0.25));
        SlimeBounceHandler.onPlayerTickPost(new PlayerTickEvent.Post(player));
        helper.assertTrue(player.getDeltaMovement().equals(new Vec3(0.5, 0.0, 0.25)),
                "a grounded tick must leave the motion alone, got " + player.getDeltaMovement());
        helper.succeed();
    }
}
