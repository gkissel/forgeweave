package dev.gkissel.forgeweave.combat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.item.BowItem;

/**
 * What right-clicking a tool does, for the innates whose trigger is the use button rather than a
 * blow: the longsword's charged leap, the broadsword's parry window, the battlesign's blocking
 * stance (issue #155).
 *
 * <p>{@link CombatSeam} deliberately stays about damage -- an innate that fires on a right click has
 * no damage instance to hang off, and NeoForge routes item use through {@code Item}'s own
 * {@code use}/{@code getUseAnimation}/{@code getUseDuration}/{@code releaseUsing} rather than through
 * an event. So {@code ToolItem} takes one of these as a constructor parameter and forwards those four
 * methods to it; no tool subclass and no extra event handler is involved, which is the property
 * ADR-0005 decision 3 is actually protecting.
 *
 * <p>Shaped for ADR-0004's M6 extraction the same way {@link CombatSeam} is: an implementation takes
 * its magnitudes as constructor fields and reads everything else from its arguments, so the whole
 * behavior is a set of numbers plus a name.
 */
public interface ToolUseAction {

    /** The animation vanilla plays while the tool is held down; also what {@code isBlocking} keys off. */
    UseAnim animation();

    /** How long the use can be held, in ticks -- vanilla's {@code Item#getUseDuration}. */
    int durationTicks();

    /**
     * Called the instant the button goes down, on both sides.
     *
     * <p>{@code null} -- the default -- means the action is a held one: the caller starts vanilla's
     * use and {@link #onRelease} follows. An <em>instant</em> action (the rapier's lunge, issue #300)
     * does its work here and answers the click itself, which is also how it declines one: a
     * {@code pass} leaves the click for the offhand.
     */
    @Nullable
    default InteractionResultHolder<ItemStack> onUse(ItemStack stack, Level level, Player player,
            InteractionHand hand) {
        return null;
    }

    /**
     * Called when the player lets go, server side only.
     *
     * @param heldTicks how long the use was held, {@code durationTicks() - timeLeft}
     */
    default void onRelease(ItemStack stack, ServerLevel level, LivingEntity user, int heldTicks) {}

    /**
     * The fraction of walking speed a user of this action keeps while it is held: upstream 1.12's
     * {@code ToolCore#preventSlowDown}, which every held weapon calls from its own {@code onUpdate}
     * (M3.5 issue #400, generalized past bows by parity audit T37/issue #468) --
     * {@code LongSword#onUpdate} passes {@code 0.9f}, {@code FryPan#onUpdate} {@code 0.7f}.
     *
     * <p>The default is vanilla's own slowdown, i.e. no override: what a held action gets unless it
     * says otherwise (the battlesign's block stance, upstream's {@code BattleSign}, never calls
     * {@code preventSlowDown} either). {@code BowDrawMovement} is the client hook that applies it, for
     * both this interface's implementations and {@link BowItem}'s own equivalent method.
     */
    default float drawMovementSpeed() {
        return BowItem.VANILLA_DRAW_MOVEMENT_SPEED;
    }
}
