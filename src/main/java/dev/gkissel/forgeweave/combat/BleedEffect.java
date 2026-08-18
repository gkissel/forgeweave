package dev.gkissel.forgeweave.combat;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.particle.ForgeweaveParticles;

/**
 * Sharp's armor-ignoring bleed (issue #229), ported from upstream 1.12's {@code TraitSharp.DoT}:
 * {@code (level + 1) / 3} damage every 15 ticks for a 121-tick application, non-stacking (sharp
 * always applies at level 0, so each tick is 1/3 damage -- eight ticks, ~2.67 total over 6 seconds).
 * The seam that applies it is a {@link Lacerate} with {@code maxStacks} 1; see
 * {@link LacerateEffect}'s javadoc for why a DoT is a vanilla status effect at all.
 *
 * <p>Three upstream mechanics mirrored exactly:
 *
 * <ul>
 *   <li><b>Armor-ignoring</b>: upstream's {@code attackEntitySecondary(source, damage, target, true,
 *       true)} bypasses armor; a magic-typed source is 1.21's equivalent, the same one
 *       {@link LacerateEffect} uses -- and, like there, it names no weapon, so it can never chain
 *       back into {@link CombatSeams}.
 *   <li><b>No knockback</b> (issue #436): upstream's {@code noKnockback} flag on that same
 *       {@code attackEntitySecondary} call is a transient +1 {@code KNOCKBACK_RESISTANCE} attribute
 *       modifier, added right before the hit and removed right after -- present unchanged through
 *       1.20's {@code ToolAttackUtil#disableKnockback}/{@code #enableKnockback}. Not a
 *       {@code no_knockback} damage-type tag (the parity audit's proposed mechanism): a tag on
 *       {@code indirect_magic} would silence knockback for every unrelated use of that vanilla type,
 *       and neither upstream generation reaches for one here.
 *   <li><b>Attacker credit</b> (issue #297 parity fix): upstream's {@code dealDamage} builds the tick's
 *       {@code DamageSource} from {@code target.getLastAttackedEntity()}, set by {@code TraitSharp
 *       #afterHit} when the bleed was applied ({@link Lacerate#onHit}'s {@code setLastHurtByMob}).
 *       This does the same off {@link LivingEntity#getLastHurtByMob}: an {@code indirect_magic}
 *       source (vanilla's armor-ignoring-with-a-credited-entity type, a thrown Harming potion's)
 *       crediting the attacker as its <em>causing</em> entity only, plain {@code magic()} otherwise
 *       -- so a kill lands on the wielder instead of no one. Never the attacker as the source's
 *       <em>direct</em> entity (issue #422): {@code DamageSource#getWeaponItem} is the direct entity's
 *       held item, so {@code indirectMagic(attacker, attacker)} named the wielder's tool as the
 *       tick's weapon and every tick re-ran the tool's seams -- a hellish wielder bled for 1 + 4.
 *   <li><b>No invulnerability-window games</b>: upstream saves {@code hurtResistantTime}, deals the
 *       tick ignoring it, and restores it -- so a bleed tick neither gets swallowed by the window a
 *       real blow just opened nor grants a window that would shield the target from real blows. The
 *       zero-then-restore below is the same maneuver on 1.21's {@code invulnerableTime}.
 * </ul>
 */
public class BleedEffect extends MobEffect {

    /** Upstream {@code TraitSharp.dealDamage} at level 0: {@code (0 + 1) / 3}. */
    public static final float DAMAGE_PER_TICK = 1.0F / 3.0F;
    /** Upstream {@code TraitSharp#afterHit}: {@code DOT.apply(target, 121)}. */
    public static final int DURATION_TICKS = 121;

    /** Upstream {@code DoT#isReady}: every 15 ticks. */
    private static final int TICKS_PER_DAMAGE = 15;
    /** A brighter arterial red than lacerate's, which is what the HUD swirl particles are drawn in. */
    private static final int COLOR = 0xC41E1E;
    /**
     * Id for the transient anti-knockback modifier (class javadoc), the same idiom as
     * {@code ToolItem}'s trait-driven attribute modifiers.
     */
    private static final ResourceLocation ANTI_KNOCKBACK_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "bleed_anti_knockback");
    /** Upstream's {@code ANTI_KNOCKBACK_MOD}/{@code ANTI_KNOCKBACK_MODIFIER}: a flat +1 (full immunity). */
    private static final AttributeModifier ANTI_KNOCKBACK_MODIFIER =
            new AttributeModifier(ANTI_KNOCKBACK_ID, 1.0, AttributeModifier.Operation.ADD_VALUE);

    public BleedEffect() {
        super(MobEffectCategory.HARMFUL, COLOR);
    }

    /**
     * Vanilla hands this the duration still to run and then decrements it, so a 121-tick application
     * sees 121, 120, ..., 1 -- {@code % 15 == 0} lands the eight ticks at 15-tick spacing with the
     * first a moment after the blow, matching upstream's count-up {@code tick % 15 == 0}.
     */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return duration % TICKS_PER_DAMAGE == 0;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        LivingEntity attacker = entity.getLastHurtByMob();
        DamageSource source = attacker != null
                ? new DamageSource(indirectMagic(entity), null, attacker)
                : entity.damageSources().magic();
        int invulnerableTime = entity.invulnerableTime;
        entity.invulnerableTime = 0;
        AttributeInstance knockbackResistance = disableKnockback(entity);
        entity.hurt(source, DAMAGE_PER_TICK);
        // #482 -- upstream TraitSharp#dealDamage puts one blood heart over the target on every tick,
        // ungated (it spawns the particle after attackEntitySecondary without reading its result).
        if (entity.level() instanceof ServerLevel level) {
            ForgeweaveParticles.spawnHearts(ForgeweaveParticles.HEART_BLOOD.get(), level, entity, 1);
        }
        enableKnockback(knockbackResistance);
        entity.invulnerableTime = invulnerableTime;
        return true;
    }

    /**
     * Adds the transient anti-knockback modifier (class javadoc) unless it is already present, mirroring
     * upstream's {@code disableKnockback}/{@code Optional.filter(!hasModifier(...))} guard so a bleed
     * tick landing mid some other anti-knockback window never double-adds or later double-removes it.
     *
     * @return the attribute this call added the modifier to, or {@code null} if none was added
     */
    private static AttributeInstance disableKnockback(LivingEntity entity) {
        AttributeInstance knockbackResistance = entity.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (knockbackResistance != null && !knockbackResistance.hasModifier(ANTI_KNOCKBACK_ID)) {
            knockbackResistance.addTransientModifier(ANTI_KNOCKBACK_MODIFIER);
            return knockbackResistance;
        }
        return null;
    }

    private static void enableKnockback(AttributeInstance knockbackResistance) {
        if (knockbackResistance != null) {
            knockbackResistance.removeModifier(ANTI_KNOCKBACK_ID);
        }
    }

    private static Holder<DamageType> indirectMagic(LivingEntity entity) {
        return entity.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.INDIRECT_MAGIC);
    }
}
