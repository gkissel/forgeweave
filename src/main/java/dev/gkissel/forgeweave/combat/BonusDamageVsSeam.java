package dev.gkissel.forgeweave.combat;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

/**
 * Flat bonus damage against any target whose entity type carries {@code targetTag} -- smite and bane
 * of arthropods (issue #162, upstream {@code ModAntiMonsterType}) are both this exact shape, differing
 * only in the tag and the magnitude. ADR-0004's M6 commitment names this behavior
 * ({@code bonus_damage_vs}) directly as a parameterized-library candidate, so {@code bonusDamage}
 * arrives pre-resolved through the constructor rather than this class reading a level of its own: the
 * {@link dev.gkissel.forgeweave.modifier.Modifier} entry this instance came from has already turned
 * its raw application units into a damage number by the time a hit reaches this seam
 * ({@code ForgeweaveModifiers#collectCombatSeams}).
 *
 * <p>Uses 1.21's own {@code sensitive_to_smite}/{@code sensitive_to_bane_of_arthropods} entity-type
 * tags (the tags vanilla's own Smite/Bane of Arthropods enchantments key off) rather than porting
 * upstream's {@code EnumCreatureAttribute}, which 1.21 has no equivalent of -- see NOTICE.md.
 */
public final class BonusDamageVsSeam implements CombatSeam {
    private final TagKey<EntityType<?>> targetTag;
    private final float bonusDamage;

    public BonusDamageVsSeam(TagKey<EntityType<?>> targetTag, float bonusDamage) {
        this.targetTag = targetTag;
        this.bonusDamage = bonusDamage;
    }

    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return hit.target().getType().is(targetTag) ? damage + bonusDamage : damage;
    }
}
