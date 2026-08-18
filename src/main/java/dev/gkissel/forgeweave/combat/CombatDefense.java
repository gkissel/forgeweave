package dev.gkissel.forgeweave.combat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
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
 * states.
 *
 * <p>Issue #460 split the two questions those states actually ask, which upstream keeps apart and
 * Forgeweave had conflated into a single flag:
 *
 * <ul>
 *   <li>{@link #using} -- <em>this</em> tool is the one the defender holds the use button on. That is
 *       what a tool's own use-triggered innate needs ({@code BattleSign#shouldBlockDamage} demands
 *       {@code getActiveItemStack().getItem() == this}, and the broadsword's parry window is open only
 *       while its own use runs).
 *   <li>{@link #blocking} -- the defender <em>is blocking</em>, upstream's
 *       {@code EntityLivingBase#isActiveItemStackBlocking}: the active item, whichever it is, has the
 *       BLOCK use animation. A raised vanilla shield counts; a charging longsword (BOW animation)
 *       does not. That is the gate {@code TraitEvents#playerBlockOrHurtEvent} puts on
 *       {@code ITrait#onBlock}, and it is about the <em>player</em>, not about the tool the trait
 *       rides -- so a shield in one hand makes a Forgeweave tool in the other block.
 * </ul>
 *
 * @param level the server level the blow lands in -- seams are server side only ({@link CombatSeams})
 * @param tool the Forgeweave tool the defender is using or holding, never Broken
 * @param defender who is being hit, and who is holding {@code tool}
 * @param attacker whoever caused the damage, or {@code null} when the source names no living entity
 * @param source the damage source, for seams that need its type tags (the parry only stops melee
 *     blows; the reflect only stops projectiles)
 * @param using whether {@code tool} itself is the defender's active item stack
 * @param blocking whether the defender was blocking when the blow landed (upstream's {@code onBlock}
 *     state) rather than merely holding {@code tool} ({@code onPlayerHurt})
 */
public record CombatDefense(
        ServerLevel level,
        ItemStack tool,
        LivingEntity defender,
        @Nullable LivingEntity attacker,
        DamageSource source,
        boolean using,
        boolean blocking) {

    /**
     * Which hand {@code tool} is in, for the seams that spend its durability -- since issue #460 the
     * defensive pass runs for the off hand too, and {@code hurtAndBreak} names the slot whose break
     * the client gets told about.
     */
    public EquipmentSlot slot() {
        return defender.getOffhandItem() == tool ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND;
    }
}
