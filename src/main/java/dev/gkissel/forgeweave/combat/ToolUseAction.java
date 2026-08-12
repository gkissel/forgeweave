package dev.gkissel.forgeweave.combat;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

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
     * Called when the player lets go, server side only.
     *
     * @param heldTicks how long the use was held, {@code durationTicks() - timeLeft}
     */
    default void onRelease(ItemStack stack, ServerLevel level, LivingEntity user, int heldTicks) {}
}
