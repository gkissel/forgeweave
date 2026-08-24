package dev.gkissel.forgeweave.combat;

import java.util.function.Predicate;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The 1.20 clone's {@code ProtectionModule} (issue #680, M4-5; SCOPE.md D17): a worn piece adds
 * {@code perLevel * level} to the blow's {@link DefendedBlow#protection} when the damage source
 * (and, optionally, the attacker) matches. Shared by the ARMOR traits (level 1) and the
 * protection modifiers of #681 (their applied level) -- one class, so the trait and the modifier
 * of the same name can never drift apart.
 *
 * <p>What a protection value is worth is settled once, in {@link CombatSeams}: {@code 1} blocks
 * {@code 1/25} of the post-armor damage, summed across every worn piece and vanilla's own
 * Protection enchantments, capped at 20 (80%) -- upstream's {@code ProtectionModifierHook}
 * contract and {@code ArmorUtil#getDamageForEvent}.
 *
 * <p>The damage-type tags are the clone's {@code TinkerTags.DamageTypes} protection tags
 * ({@code DamageTypeTagProvider}), datagen'd by {@code ForgeweaveDamageTypeTagsProvider} with the
 * vanilla members only.
 *
 * @param perLevel protection per level (the clone's {@code eachLevel} constant)
 * @param level the level this instance protects at; a trait is level 1
 * @param sources which blows count -- always inside {@link #CAN_PROTECT}
 * @param attacker which attackers count; {@link #ANY_ATTACKER} unless the clone filters
 */
public record Protection(float perLevel, int level, Predicate<DamageSource> sources,
        Predicate<LivingEntity> attacker) implements CombatSeam {

    /**
     * Mantle's {@code DamageSourcePredicate.CAN_PROTECT}: the sources vanilla's own Protection
     * enchantment is allowed to touch -- neither {@code bypasses_invulnerability} (void, /kill) nor
     * {@code bypasses_enchantments} (starvation, {@code generic_kill}).
     */
    public static final Predicate<DamageSource> CAN_PROTECT = source ->
            !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS);

    public static final Predicate<LivingEntity> ANY_ATTACKER = attacker -> true;

    /** Upstream's {@code MobTypePredicate(MobType.UNDEAD)}, 1.21's {@code #minecraft:undead}. */
    public static final Predicate<LivingEntity> UNDEAD_ATTACKER = attacker -> attacker.getType().is(EntityTypeTags.UNDEAD);

    public static final TagKey<DamageType> MELEE_PROTECTION = tag("melee_protection");
    public static final TagKey<DamageType> PROJECTILE_PROTECTION = tag("projectile_protection");
    public static final TagKey<DamageType> FIRE_PROTECTION = tag("fire_protection");
    public static final TagKey<DamageType> BLAST_PROTECTION = tag("blast_protection");
    public static final TagKey<DamageType> MAGIC_PROTECTION = tag("magic_protection");

    /** Level-1 protection against every protectable source (the clone's plain {@code protection}). */
    public static Protection of(float perLevel) {
        return new Protection(perLevel, 1, CAN_PROTECT, ANY_ATTACKER);
    }

    /** Level-1 protection against the sources in {@code tag} (projectile, fire, blast, magic). */
    public static Protection against(TagKey<DamageType> tag, float perLevel) {
        return new Protection(perLevel, 1, CAN_PROTECT.and(source -> source.is(tag)), ANY_ATTACKER);
    }

    /**
     * Melee protection's extra gate: {@code DamageSourcePredicate.IS_INDIRECT.inverted()} -- the
     * clone refuses indirect blows "to guard against misuse of the melee damage types".
     */
    public Protection directOnly() {
        return new Protection(perLevel, level, sources.and(DamageSource::isDirect), attacker);
    }

    public Protection attacker(Predicate<LivingEntity> attacker) {
        return new Protection(perLevel, level, sources, attacker);
    }

    /** The same protection at another level -- what a modifier hands back for its applied level. */
    public Protection level(int level) {
        return new Protection(perLevel, level, sources, attacker);
    }

    /** {@code perLevel * level}, the clone's {@code eachLevel} formula. */
    public float value() {
        return perLevel * level;
    }

    @Override
    public void onDefend(CombatDefense defense, DefendedBlow blow) {
        if (!sources.test(defense.source())) {
            return;
        }
        // Upstream TinkerPredicate.matches(attacker, source.getEntity()): a filter only ever
        // matches a living attacker; ANY_ATTACKER is the no-filter case and passes a null one.
        if (attacker != ANY_ATTACKER && (defense.attacker() == null || !attacker.test(defense.attacker()))) {
            return;
        }
        blow.addProtection(value());
    }

    private static TagKey<DamageType> tag(String path) {
        return TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path));
    }
}
