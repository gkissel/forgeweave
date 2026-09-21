package dev.gkissel.forgeweave.trait;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.api.combat.CombatDefense;
import dev.gkissel.forgeweave.api.combat.DefendedBlow;
import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.combat.Protection;
import dev.gkissel.forgeweave.combat.SecondaryDamage;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * A worn piece stores part of every blow it takes and, once the store is full, throws it back out as
 * a shockwave (issue #1114's {@code stored_retaliation}).
 *
 * <p>This is the earth line's armour side. It does not reduce anything, which is what keeps it clear
 * of {@link StackingResistance} and the {@code *_protection} traits: you take the blow in full, and
 * then the crowd that dealt it takes the sum back. Being surrounded is what charges it, so it rewards
 * exactly the fight a protection trait would rather you avoided.
 *
 * <p>Sized by the piece, not by the set, but the arithmetic runs the other way from a percentage:
 * {@code CombatSeams#armorPass} calls {@link Trait#onDefend} once per worn piece, so a full set banks
 * four times as fast and erupts four times as often at the same numbers. {@code backlash} stores a
 * quarter of each blow and erupts at 20 -- one eruption every five blows on one piece, a bit more
 * than one per blow for a set.
 *
 * @param storedFraction how much of each blow goes into the store
 * @param threshold the store the eruption waits for, in damage
 * @param radius how far the eruption reaches
 * @param releaseFraction how much of the store each entity in range takes
 */
public record StoredRetaliation(float storedFraction, int threshold, double radius, float releaseFraction)
        implements Trait {

    /**
     * How long a part-filled store survives without another blow. Long, unlike
     * {@link StackingResistance}'s six seconds: the store is a running total of a fight rather than a
     * standing buff, and a wearer who breaks off for half a minute should not have to start the
     * tally again.
     */
    private static final int DECAY_TICKS = 600;

    /** How hard the eruption shoves, in vanilla's own knockback units. */
    private static final double KNOCKBACK = 0.6;

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (blow.damage() <= 0.0F || !Protection.CAN_PROTECT.test(defense.source())) {
            return;
        }
        ItemStack piece = defense.tool();
        int stored = ForgeweaveTraits.stackLevel(piece, ForgeweaveDataComponents.STORED_BLOW.get())
                + Math.round(blow.damage() * storedFraction);
        if (stored < threshold) {
            piece.set(ForgeweaveDataComponents.STORED_BLOW.get(), new TraitStacks(stored, DECAY_TICKS));
            return;
        }
        piece.remove(ForgeweaveDataComponents.STORED_BLOW.get());
        erupt(defense.level(), defense.defender(), stored * releaseFraction);
    }

    private void erupt(ServerLevel level, LivingEntity wearer, float damage) {
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class,
                wearer.getBoundingBox().inflate(radius))) {
            if (nearby == wearer) {
                continue;
            }
            SecondaryDamage.deal(nearby, level.damageSources().magic(), damage);
            nearby.knockback(KNOCKBACK, wearer.getX() - nearby.getX(), wearer.getZ() - nearby.getZ());
        }
        TraitFeedback.fire(this, TraitFeedback.Kind.WARD, level, wearer);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        ForgeweaveTraits.decayStack(stack, ForgeweaveDataComponents.STORED_BLOW.get());
    }
}
