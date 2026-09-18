package dev.gkissel.forgeweave.modifier;

import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.enchantment.Enchantment;

import dev.gkissel.forgeweave.combat.BonusDamageVsSeam;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.IgniteOnHitSeam;
import dev.gkissel.forgeweave.combat.KnockbackOnHitSeam;
import dev.gkissel.forgeweave.combat.LifestealOnHitSeam;
import dev.gkissel.forgeweave.combat.PotionEffectOnHitSeam;
import dev.gkissel.forgeweave.combat.Protection;
import dev.gkissel.forgeweave.combat.ThornsCounterSeam;

/**
 * The parameterized behavior library a {@link ModifierDefinition} picks from -- ADR-0004 item 3's
 * modifier half, closed out by issue #973. Every record here is a {@link Modifier} whose numbers
 * come from a datapack rather than from a constant in {@link ForgeweaveModifiers}, and every one was
 * extracted from a shipped Java modifier: nothing new was invented, and the behaviors no parameter
 * set can express stay Java (see {@link ModifierBehaviors}' class javadoc for that list).
 *
 * <p>Two conventions run through the whole library, so a pack author learns them once:
 *
 * <ul>
 *   <li>A field named {@code *_per_level} is per <em>displayed</em> level, i.e. multiplied by
 *       {@code units / units_per_level} ({@link Behavior#scaled}) -- smite's +7 damage per level
 *       over 24 units reads as {@code "damage_per_level": 7} with {@code "units_per_level": 24}.
 *       A field named {@code *_per_unit} is per raw application unit, which is what knockback and
 *       fiery scale on upstream.
 *   <li>{@link Application} carries the three things every modifier answers regardless of what it
 *       does -- how many units make a level, how many slots it occupies, and which tools take it --
 *       as optional fields on every behavior, the way {@code TraitBehaviors.Gate} carries the shared
 *       seam gate for traits.
 * </ul>
 */
public final class ModifierLibrary {

    /**
     * How many of the tool's modifier slots an application occupies -- the three shapes the shipped
     * roster already has. {@link #PER_LEVEL} is {@link Modifier#occupiedSlots}'s own default (one
     * slot per displayed level, upstream's {@code MultiAspect}); {@link #FIRST_LEVEL_ONLY} is
     * upstream's {@code FreeFirstModifierAspect} (luck, blasting, extra_slot); {@link #NONE} is a
     * slot-free utility ({@link Modifier#utility}, goggles and netherite).
     */
    public enum SlotCost {
        PER_LEVEL,
        FIRST_LEVEL_ONLY,
        NONE
    }

    /**
     * Which tools an application is allowed on -- one name per combination the shipped roster uses,
     * rather than a cross product of the individual {@link Modifier} gates. {@link #ARMOR_OR_HELD}
     * is the five protections' pair ({@code armorOnly} plus {@code alsoHeld}, issue #729);
     * {@link #CHESTPLATE} and {@link #HELMET} are narrower than {@link #ARMOR} the way elytra
     * flight and goggles are; {@link #NOT_LAUNCHER} is luck's refusal on a bow.
     */
    public enum ToolGate {
        ANY,
        HARVEST,
        ARMOR,
        ARMOR_OR_HELD,
        CHESTPLATE,
        HELMET,
        PROJECTILE,
        NOT_LAUNCHER
    }

    /**
     * The optional fields every behavior accepts.
     *
     * @param unitsPerLevel {@link Modifier#unitsPerLevel}, 1 for a modifier that is simply on or off
     * @param slots how many slots an application costs
     * @param tools which tools take it
     */
    public record Application(int unitsPerLevel, SlotCost slots, ToolGate tools) {
        public static final Application DEFAULT = new Application(1, SlotCost.PER_LEVEL, ToolGate.ANY);
    }

    /**
     * What every behavior record in this file implements: the {@link Modifier} hooks {@link
     * Application} answers, in one place, so a behavior only writes the hook it is actually about.
     */
    public interface Behavior extends Modifier {

        Application application();

        /** {@code amountPerLevel} scaled to {@code units}, the {@code *_per_level} convention above. */
        default float scaled(float amountPerLevel, int units) {
            return amountPerLevel * units / application().unitsPerLevel();
        }

        @Override
        default int unitsPerLevel() {
            return application().unitsPerLevel();
        }

        @Override
        default int occupiedSlots(int level) {
            return switch (application().slots()) {
                case PER_LEVEL -> Modifier.super.occupiedSlots(level);
                case FIRST_LEVEL_ONLY -> level > 0 ? 1 : 0;
                case NONE -> 0;
            };
        }

        @Override
        default boolean utility() {
            return application().slots() == SlotCost.NONE;
        }

        @Override
        default boolean harvestOnly() {
            return application().tools() == ToolGate.HARVEST;
        }

        @Override
        default boolean armorOnly() {
            return switch (application().tools()) {
                case ARMOR, ARMOR_OR_HELD, CHESTPLATE, HELMET -> true;
                default -> false;
            };
        }

        @Override
        default boolean alsoHeld() {
            return application().tools() == ToolGate.ARMOR_OR_HELD;
        }

        @Override
        default boolean chestplateOnly() {
            return application().tools() == ToolGate.CHESTPLATE;
        }

        @Override
        default boolean helmetOnly() {
            return application().tools() == ToolGate.HELMET;
        }

        @Override
        default boolean projectileOnly() {
            return application().tools() == ToolGate.PROJECTILE;
        }

        @Override
        default boolean appliesToLaunchers() {
            return application().tools() != ToolGate.NOT_LAUNCHER;
        }
    }

    // ---------------------------------------------------------------- stat and attribute behaviors

    /** Which of the tool's own stats a {@link StatBonus} moves. */
    public enum Stat {
        DURABILITY,
        ATTACK_DAMAGE,
        MINING_SPEED
    }

    /**
     * {@code stat_bonus}: diamond's flat {@code +500} durability, emerald's {@code +50%} of the
     * untouched base, netherite's three base fractions and silky's {@code -3} floored at 1, as one
     * record. The three parts add up -- {@code flat} once the modifier is present at all,
     * {@code per_level} scaled by displayed level, {@code fraction_of_base} against the tool's
     * untouched materials-derived stat -- and {@code minimum} floors the result the way silky's
     * penalty is floored.
     */
    public record StatBonus(Application application, Stat stat, float flat, float perLevel,
            float fractionOfBase, Optional<Float> minimum) implements Behavior {

        private float apply(int level, float current, float base) {
            if (level <= 0) {
                return current;
            }
            float moved = current + flat + scaled(perLevel, level) + fractionOfBase * base;
            return minimum.map(floor -> Math.max(floor, moved)).orElse(moved);
        }

        @Override
        public int durability(int level, int durability, int baseDurability) {
            return stat == Stat.DURABILITY ? (int) apply(level, durability, baseDurability) : durability;
        }

        @Override
        public float attackDamage(int level, float attackDamage, float baseAttackDamage) {
            return stat == Stat.ATTACK_DAMAGE ? apply(level, attackDamage, baseAttackDamage) : attackDamage;
        }

        @Override
        public float miningSpeed(int level, float miningSpeed, float baseMiningSpeed) {
            return stat == Stat.MINING_SPEED ? apply(level, miningSpeed, baseMiningSpeed) : miningSpeed;
        }
    }

    /** Which wearer or holder attribute an {@link AttributeBonus} adds to. */
    public enum Attribute {
        KNOCKBACK_RESISTANCE,
        ARMOR_TOUGHNESS,
        SUBMERGED_MINING_SPEED,
        BLOCK_INTERACTION_RANGE
    }

    /**
     * {@code attribute_bonus}: the four attribute hooks netherite ({@code +1} toughness,
     * {@code +0.05} knockback resistance), knockback resistance ({@code +0.1} per level),
     * aquadynamic ({@code +0.8} submerged mining speed) and far reach ({@code +1} range per level)
     * already share.
     */
    public record AttributeBonus(Application application, Attribute attribute, float flat, float perLevel)
            implements Behavior {

        private float amount(int level, Attribute wanted) {
            return attribute == wanted && level > 0 ? flat + scaled(perLevel, level) : 0.0F;
        }

        @Override
        public float knockbackResistanceBonus(int level) {
            return amount(level, Attribute.KNOCKBACK_RESISTANCE);
        }

        @Override
        public float armorToughnessBonus(int level) {
            return amount(level, Attribute.ARMOR_TOUGHNESS);
        }

        @Override
        public float submergedMiningSpeedBonus(int level) {
            return amount(level, Attribute.SUBMERGED_MINING_SPEED);
        }

        @Override
        public float blockInteractionRangeBonus(int level) {
            return amount(level, Attribute.BLOCK_INTERACTION_RANGE);
        }
    }

    /**
     * {@code tool_tier}: diamond's and emerald's capped one-rung bump and netherite's "at least
     * netherite" floor, as one formula -- {@code min(max(tier + bump, at_least), cap)}. Applied only
     * the moment the modifier is first added, like every {@link Modifier#toolTierIndex} (see that
     * hook's javadoc), so a bump never compounds.
     */
    public record ToolTier(Application application, int bump, int atLeast, Optional<Integer> cap)
            implements Behavior {

        @Override
        public int toolTierIndex(int level, int tierIndex) {
            if (level <= 0) {
                return tierIndex;
            }
            int bumped = Math.max(tierIndex + bump, atLeast);
            return cap.map(ceiling -> Math.min(bumped, ceiling)).orElse(bumped);
        }
    }

    /** {@code durability_negation}: reinforced's flat chance per level, capped at a certain 1. */
    public record DurabilityNegation(Application application, float chancePerLevel) implements Behavior {

        @Override
        public float durabilityNegationChance(int level) {
            return Math.min(1.0F, scaled(chancePerLevel, level));
        }
    }

    /**
     * {@code bonus_slots}: extra_slot's grant. Note the trap {@link Modifier#bonusSlots} documents --
     * an application occupies a slot of its own unless {@code slots} says otherwise, so extra_slot's
     * net {@code +1} per level is {@code flat: 1, per_level: 1} with {@code slots: first_level_only}.
     */
    public record BonusSlots(Application application, int flat, int perLevel) implements Behavior {

        @Override
        public int bonusSlots(int level) {
            return level <= 0 ? 0 : flat + (int) scaled(perLevel, level);
        }
    }

    /** {@code bonus_experience}: resonant's extra fraction of a mined block's dropped experience. */
    public record BonusExperience(Application application, float fractionPerLevel) implements Behavior {

        @Override
        public float bonusExperienceFraction(int level) {
            return scaled(fractionPerLevel, level);
        }
    }

    /**
     * {@code grant_enchantment}: wind burst's vanilla grant (issue #223), which is silky's Silk
     * Touch idiom generalized to carry a level. {@code level} pins the granted level; leaving it out
     * grants the applied display level, and {@code max_level} clamps it the way wind burst clamps at
     * vanilla's own cap of 3.
     */
    public record GrantEnchantment(Application application, ResourceKey<Enchantment> enchantment,
            Optional<Integer> level, Optional<Integer> maxLevel) implements Behavior {

        @Override
        public Optional<EnchantmentGrant> grantedEnchantment(int units) {
            if (units <= 0) {
                return Optional.empty();
            }
            int granted = level.orElseGet(() -> Math.max(1, units / application().unitsPerLevel()));
            return Optional.of(new EnchantmentGrant(enchantment,
                    maxLevel.map(cap -> Math.min(granted, cap)).orElse(granted)));
        }
    }

    /** {@code fire_resistant}: netherite's dropped-item survival, vanilla's own component. */
    public record FireResistant(Application application) implements Behavior {

        @Override
        public boolean fireResistant(int level) {
            return level > 0;
        }
    }

    /** {@code aoe_expansion}: Width++ and Height++ (issue #438), which only name an axis. */
    public record AoeExpansion(Application application, Modifier.AoeAxis axis) implements Behavior {

        @Override
        public Optional<Modifier.AoeAxis> aoeExpansion(int level) {
            return level > 0 ? Optional.of(axis) : Optional.empty();
        }
    }

    // ---------------------------------------------------------------- combat seams
    // Each hands back a fresh, already-parameterized seam per hit, which is Modifier#combatSeam's
    // own contract. The seam classes are the ones the #162/#163 batches and M4 already use, so a
    // pack-defined knockback and the shipped one run the exact same code.

    /** {@code knockback_on_hit}: knockback's extra magnitude, linear in raw units upstream. */
    public record KnockbackOnHit(Application application, float perUnit) implements Behavior {

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new KnockbackOnHitSeam(perUnit * level));
        }
    }

    /**
     * {@code effect_on_hit}: shulking's Levitation ({@code units / 2 + 10} ticks) and webbed's
     * Slowness II ({@code 20} ticks per level), whose only difference is these parameters.
     */
    public record EffectOnHit(Application application, Holder<MobEffect> effect, int amplifier,
            float durationPerUnit, int durationOffset) implements Behavior {

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            int duration = durationOffset + (int) (durationPerUnit * level);
            return duration <= 0 ? Optional.empty()
                    : Optional.of(new PotionEffectOnHitSeam(effect, amplifier, duration));
        }
    }

    /** {@code bonus_damage_vs}: smite and bane of arthropods, keyed on a vanilla sensitivity tag. */
    public record BonusDamageVs(Application application, TagKey<EntityType<?>> entities, float damagePerLevel)
            implements Behavior {

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new BonusDamageVsSeam(entities, scaled(damagePerLevel, level)));
        }
    }

    /** {@code ignite_on_hit}: fiery's fire duration and small true-damage instance. */
    public record IgniteOnHit(Application application, float secondsPerUnit, int secondsOffset,
            float damagePerUnit) implements Behavior {

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new IgniteOnHitSeam(secondsOffset + (int) (secondsPerUnit * level),
                    damagePerUnit * level));
        }
    }

    /** {@code lifesteal_on_hit}: necrotic's fraction of the damage dealt, healed back. */
    public record LifestealOnHit(Application application, float fractionPerLevel) implements Behavior {

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new LifestealOnHitSeam(scaled(fractionPerLevel, level)));
        }
    }

    /** {@code thorns_counter}: the thorns modifier's chance to hit an attacker back. */
    public record ThornsCounter(Application application, float chancePerLevel, float constantDamage,
            float randomDamage) implements Behavior {

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new ThornsCounterSeam(scaled(chancePerLevel, level), constantDamage, randomDamage));
        }
    }

    /**
     * {@code protection}: the five protection modifiers, whose only differences are the damage-type
     * tag they answer to, how much each level is worth, and melee protection's direct-only gate.
     * Leaving {@code damage_type} out protects against every source vanilla's own Protection
     * enchantment may touch ({@link Protection#CAN_PROTECT}), which is the clone's plain
     * {@code protection}.
     */
    public record ProtectionBonus(Application application, float perLevel, Optional<TagKey<DamageType>> damageType,
            boolean directOnly) implements Behavior {

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            Predicate<DamageSource> sources = damageType
                    .<Predicate<DamageSource>>map(tag -> Protection.CAN_PROTECT
                            .and(source -> source.is(tag)))
                    .orElse(Protection.CAN_PROTECT);
            if (directOnly) {
                sources = sources.and(DamageSource::isDirect);
            }
            // Protection#level is an int, so a fractional effective level is expressed as
            // "per unit" x units, the way ForgeweaveModifiers#protection does it.
            return Optional.of(new Protection(perLevel / application().unitsPerLevel(), level, sources,
                    Protection.ANY_ATTACKER));
        }
    }

    private ModifierLibrary() {}
}
