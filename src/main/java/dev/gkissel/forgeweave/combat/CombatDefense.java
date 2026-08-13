package dev.gkissel.forgeweave.combat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * One blow <em>taken</em> while holding or actively using a Forgeweave tool, as
 * {@link CombatSeam#incomingHit} sees it -- the mirror image of {@link CombatHit}, which is always
 * about the tool that <em>struck</em>.
 *
 * <p>Two M3 innates are defensive and therefore cannot ride {@link CombatHit}: the broadsword's parry
 * window and the battlesign's projectile reflect (issue #155). Both are still combat behaviors, so
 * ADR-0005 decision 3 says they attach to the shared pipeline rather than to event handlers of their
 * own -- {@link CombatSeams} simply runs a second pass over the same
 * {@code LivingIncomingDamageEvent}, keyed on the defender's tool instead of the attacker's.
 *
 * <p>Issue #229 widened the pass to a tool that is merely <em>held</em>: upstream 1.12 runs
 * {@code ITrait#onPlayerHurt} for a held tool and {@code ITrait#onBlock} for a blocking one
 * (spiky's half-strength thorns, flammable's retaliation fire), so the one pass now covers both
 * states and {@link #blocking} says which this blow found.
 *
 * @param level the server level the blow lands in -- seams are server side only ({@link CombatSeams})
 * @param tool the Forgeweave tool the defender is using or holding, never Broken
 * @param defender who is being hit, and who is holding {@code tool}
 * @param attacker whoever caused the damage, or {@code null} when the source names no living entity
 * @param source the damage source, for seams that need its type tags (the parry only stops melee
 *     blows; the reflect only stops projectiles)
 * @param blocking whether the defender was <em>actively using</em> {@code tool} when the blow
 *     landed (upstream's {@code onBlock} state -- the broadsword's parry window, the battlesign's
 *     stance) rather than merely holding it in the main hand ({@code onPlayerHurt})
 */
public record CombatDefense(
        ServerLevel level,
        ItemStack tool,
        LivingEntity defender,
        @Nullable LivingEntity attacker,
        DamageSource source,
        boolean blocking) {}
