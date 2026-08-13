package dev.gkissel.forgeweave.combat;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * A status effect that does nothing by itself -- a timed, saved, synced <em>mark</em> some other
 * code reads: splintering's stack counter ({@link StackingHitBonus}) and enderference's
 * teleport-block flag ({@code ForgeweaveTraits#onEnderTeleport}) both are one of these, the same
 * marker-potion idiom upstream 1.12's {@code TinkerPotion} serves for {@code TraitSplintering} and
 * {@code TraitEnderference}. Exists only because {@link MobEffect}'s constructor is protected.
 */
public class MarkerEffect extends MobEffect {

    public MarkerEffect(MobEffectCategory category, int color) {
        super(category, color);
    }
}
