package dev.gkissel.forgeweave.item;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Upstream 1.12's {@code library/SlimeBounceHandler} (NOTICE.md), shared by both things that throw a
 * player around: the Slime Boots' rebound (parity audit T21, issue #452) and the Slimesling's fling
 * (parity audit T22, issue #453).
 *
 * <p>Two arms, exactly upstream's. An upward {@code bounce} impulse is re-applied on the tick it was
 * scheduled for -- vanilla's own movement code runs after the fall check and would otherwise eat the
 * boots' rebound. And while the player is airborne, their horizontal momentum is divided by vanilla's
 * air drag every tick, so a bounce or a fling carries instead of bleeding away over the arc. The
 * handler detaches five ticks after the player is back on the ground.
 *
 * <p>Upstream registers one handler instance per entity on the event bus and unregisters it on
 * landing; NeoForge's listener list is not built for that churn, so this is one static listener over
 * a map of the players currently being carried -- same state, same arithmetic, same lifetime. Weak
 * keys because a player who logs out mid-bounce never ticks again and would otherwise be pinned;
 * every access below is a single-key get/put/remove, which the synchronized wrapper covers (the
 * client and the integrated server tick their own {@code Player} objects on different threads).
 */
public final class SlimeBounceHandler {

    /** Vanilla's horizontal air drag, upstream's {@code 0.91d + 0.025d}: dividing by it cancels the tick's decay. */
    private static final double AIR_DRAG = 0.91D + 0.025D;

    /** How long after landing the handler stays attached, in ticks (upstream's {@code > 5}). */
    private static final int LINGER_TICKS = 5;

    /**
     * "No impulse scheduled". Upstream uses 0 for this because 1.12's {@code ticksExisted} is never 0
     * by the time anything attaches a handler; a tick count genuinely can be 0, so this is -1 instead.
     */
    private static final int NO_BOUNCE = -1;

    private static final Map<Player, SlimeBounceHandler> BOUNCING =
            Collections.synchronizedMap(new WeakHashMap<>());

    private int timer;
    private boolean wasInAir;
    private double bounce;
    private int bounceTick;
    private double lastMovX;
    private double lastMovZ;

    private SlimeBounceHandler(Player player, double bounce) {
        this.bounce = bounce;
        this.bounceTick = bounce != 0 ? player.tickCount : NO_BOUNCE;
    }

    /** The Slimesling's arm: carry the momentum, no impulse of our own to schedule. */
    public static void addBounceHandler(Player player) {
        addBounceHandler(player, 0);
    }

    /**
     * Upstream's {@code SlimeBounceHandler#addBounceHandler}: real players only, never fake ones. An
     * already-running carry is left in place unless there is a fresh impulse to schedule onto it.
     */
    public static void addBounceHandler(Player player, double bounce) {
        if (player instanceof FakePlayer) {
            return;
        }
        SlimeBounceHandler existing = BOUNCING.get(player);
        if (existing == null) {
            BOUNCING.put(player, new SlimeBounceHandler(player, bounce));
        } else if (bounce != 0) {
            existing.bounce = bounce;
            existing.bounceTick = player.tickCount;
        }
    }

    /** Whether {@code player}'s momentum is currently being carried -- the tests' window onto the map. */
    public static boolean isBouncing(Player player) {
        return BOUNCING.containsKey(player);
    }

    /** Upstream's {@code SlimeBounceHandler#playerTickPost}. */
    public static void onPlayerTickPost(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        SlimeBounceHandler handler = BOUNCING.get(player);
        if (handler == null) {
            return;
        }
        if (player.isRemoved()) {
            BOUNCING.remove(player);
            return;
        }
        if (player.isFallFlying()) {
            return;
        }
        if (handler.tick(player)) {
            BOUNCING.remove(player);
        }
    }

    /** @return true once the player has been back on the ground long enough to stop tracking. */
    private boolean tick(Player player) {
        if (bounceTick != NO_BOUNCE && player.tickCount == bounceTick) {
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, bounce, motion.z);
            bounceTick = NO_BOUNCE;
        }

        if (!player.onGround()) {
            Vec3 motion = player.getDeltaMovement();
            if (motion.x != lastMovX || motion.z != lastMovZ) {
                player.setDeltaMovement(motion.x / AIR_DRAG, motion.y, motion.z / AIR_DRAG);
                player.hasImpulse = true;
                lastMovX = player.getDeltaMovement().x;
                lastMovZ = player.getDeltaMovement().z;
            }
        }

        if (wasInAir && player.onGround()) {
            if (timer == 0) {
                timer = player.tickCount;
            } else if (player.tickCount - timer > LINGER_TICKS) {
                return true;
            }
        } else {
            timer = 0;
            wasInAir = true;
        }
        return false;
    }
}
