package dev.gkissel.forgeweave.trait;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.ChainArc;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.ConditionalSeam;
import dev.gkissel.forgeweave.combat.CritMultiplierBonus;
import dev.gkissel.forgeweave.combat.DamageScalesWith;
import dev.gkissel.forgeweave.combat.EffectOnHit;
import dev.gkissel.forgeweave.combat.EffectOnSelfOnHit;
import dev.gkissel.forgeweave.combat.FlatBonusDamage;
import dev.gkissel.forgeweave.combat.HitCondition;
import dev.gkissel.forgeweave.combat.Lifesteal;
import dev.gkissel.forgeweave.combat.LightningOnHit;
import dev.gkissel.forgeweave.combat.ReduceTargetHealing;
import dev.gkissel.forgeweave.combat.ShortenInvulnerability;
import dev.gkissel.forgeweave.combat.StripEffects;

/**
 * The behaviour-type registry {@link TraitDefinition}'s codec dispatches on: one {@link MapCodec}
 * per parameterized behaviour class the M6 library batches landed (#827 damage-scaling, #828 on-hit
 * effects, #829 utility/economy, #830 energy), keyed by the {@code forgeweave:<name>} id a
 * definition's {@code behavior} field names. The parameter sets <em>are</em> the schemas: each
 * codec below reads the same constructor arguments {@code ForgeweaveTraits} hands the class in
 * Java, under snake_case field names.
 *
 * <p>Combat-seam behaviours ({@link #seam}) all take the same optional gate: {@code condition}
 * (a {@link HitCondition} name, default {@code any}) and {@code chance} (0..1, default 1), which is
 * how {@link ConditionalSeam} composes with every seam in Java too. So {@code charged_bonus_damage}
 * is {@code bonus_damage_vs} with {@code "condition": "full_charge"}, {@code strip_effects}'
 * {@code chargedOnly} is the same field, and {@code chain_arc}'s chance is {@code "chance"} --
 * no per-behaviour variants of the gate.
 *
 * <p>ponytail: a plain map, not a Minecraft registry. Behaviour classes are Java and only a mod
 * update adds one, so there is nothing for a registry event to contribute; {@link #ids} is the
 * whole discovery surface a pack author needs. {@code escalating} ({@code DamageRamp.ESCALATING},
 * a stateful singleton sharing the katana's component) is deliberately absent -- it has no
 * parameters to expose.
 */
public final class TraitBehaviors {

    private static final Map<ResourceLocation, MapCodec<TraitDefinition>> TYPES = new LinkedHashMap<>();

    /** {@code ExtraCodecs} has the int one but not the float one. */
    private static final Codec<Float> NON_NEGATIVE_FLOAT = Codec.floatRange(0.0F, Float.MAX_VALUE);
    /** {@code "#minecraft:is_fire"}, the way every other tag field in this file reads. */
    private static final Codec<TagKey<DamageType>> DAMAGE_TYPE_TAG = TagKey.codec(Registries.DAMAGE_TYPE);

    /**
     * The optional {@link ConditionalSeam} gate every combat-seam behaviour accepts.
     *
     * @param condition when the seam applies; {@link HitCondition#ANY} for always
     * @param chance 0..1 roll per hook call, 1 for always
     */
    public record Gate(HitCondition condition, float chance) {
        public static final Gate NONE = new Gate(HitCondition.ANY, 1.0F);

        static final MapCodec<Gate> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                enumCodec(HitCondition.class).optionalFieldOf("condition", HitCondition.ANY).forGetter(Gate::condition),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(Gate::chance))
                .apply(instance, Gate::new));

        CombatSeam wrap(CombatSeam seam) {
            return equals(NONE) ? seam : new ConditionalSeam(condition, chance, seam);
        }
    }

    /**
     * A definition whose whole behaviour is one combat seam, gated -- the datapack face of
     * {@code ForgeweaveTraits#seamTrait}. {@link #gated} is precomputed so a hit never allocates.
     */
    public record SeamTrait(Gate gate, CombatSeam seam, CombatSeam gated) implements Trait {
        public SeamTrait(Gate gate, CombatSeam seam) {
            this(gate, seam, gate.wrap(seam));
        }

        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(gated);
        }
    }

    /**
     * {@code cascading_break} with its predicate expressed as an optional block tag; absent means
     * vanilla's own gravity-block marker ({@link FallingBlock}), which is what the built-in {@code
     * cascading} uses. Kept beside the {@link CascadingBreak} it delegates to so the tag survives
     * a re-encode.
     */
    public record CascadingBreakDefinition(Optional<TagKey<Block>> blocks, CascadingBreak delegate) implements Trait {
        static CascadingBreakDefinition of(Optional<TagKey<Block>> blocks) {
            Predicate<BlockState> predicate = blocks
                    .<Predicate<BlockState>>map(tag -> state -> state.is(tag))
                    .orElse(state -> state.getBlock() instanceof FallingBlock);
            return new CascadingBreakDefinition(blocks, new CascadingBreak(predicate));
        }

        @Override
        public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
                LivingEntity breaker, boolean effective) {
            delegate.afterBlockBreak(stack, level, state, pos, breaker, effective);
        }
    }

    private static final Map<String, SelfRepairCondition> SELF_REPAIR_CONDITIONS = Map.of(
            "always", SelfRepairCondition.ALWAYS,
            "sunlit", SelfRepairCondition.SUNLIT,
            "night", SelfRepairCondition.NIGHT);

    private static final Codec<SelfRepairCondition> SELF_REPAIR_CONDITION_CODEC = Codec.STRING.comapFlatMap(
            name -> {
                SelfRepairCondition condition = SELF_REPAIR_CONDITIONS.get(name);
                return condition == null
                        ? DataResult.error(() -> "Unknown self-repair condition '" + name + "'; one of "
                                + SELF_REPAIR_CONDITIONS.keySet())
                        : DataResult.success(condition);
            },
            condition -> SELF_REPAIR_CONDITIONS.entrySet().stream()
                    .filter(entry -> entry.getValue() == condition)
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow());

    static {
        // #827 damage-scaling batch.
        seam("damage_scales_with", RecordCodecBuilder.<DamageScalesWith>mapCodec(instance -> instance.group(
                enumCodec(DamageScalesWith.Source.class).fieldOf("source").forGetter(DamageScalesWith::source),
                Codec.FLOAT.fieldOf("coefficient").forGetter(DamageScalesWith::coefficient),
                NON_NEGATIVE_FLOAT.fieldOf("cap").forGetter(DamageScalesWith::cap))
                .apply(instance, DamageScalesWith::new)));
        seam("bonus_damage_vs", Codec.FLOAT.fieldOf("amount").xmap(FlatBonusDamage::new, FlatBonusDamage::bonus));
        seam("crit_multiplier_bonus", Codec.FLOAT.fieldOf("extra").xmap(CritMultiplierBonus::new, CritMultiplierBonus::extra));

        // #828 on-hit effect batch.
        seam("effect_on_hit", RecordCodecBuilder.<EffectOnHit>mapCodec(instance -> instance.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(EffectOnHit::effect),
                ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(EffectOnHit::durationTicks),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("amplifier", 0).forGetter(EffectOnHit::amplifier),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("stacking_cap", 0).forGetter(EffectOnHit::stackingCap))
                .apply(instance, EffectOnHit::new)));
        seam("effect_on_self_on_hit", RecordCodecBuilder.<EffectOnSelfOnHit>mapCodec(instance -> instance.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(EffectOnSelfOnHit::effect),
                ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(EffectOnSelfOnHit::durationTicks),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("amplifier", 0).forGetter(EffectOnSelfOnHit::amplifier))
                .apply(instance, EffectOnSelfOnHit::new)));
        seam("strip_effects", ExtraCodecs.POSITIVE_INT.optionalFieldOf("count", 1).xmap(StripEffects::new, StripEffects::count));
        seam("reduce_target_healing", RecordCodecBuilder.<ReduceTargetHealing>mapCodec(instance -> instance.group(
                Codec.floatRange(0.0F, 1.0F).fieldOf("fraction").forGetter(ReduceTargetHealing::fraction),
                ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(ReduceTargetHealing::durationTicks))
                .apply(instance, ReduceTargetHealing::new)));
        seam("shorten_invulnerability",
                ExtraCodecs.POSITIVE_INT.fieldOf("ticks").xmap(ShortenInvulnerability::new, ShortenInvulnerability::ticks));
        seam("lifesteal", RecordCodecBuilder.<Lifesteal>mapCodec(instance -> instance.group(
                NON_NEGATIVE_FLOAT.fieldOf("fraction").forGetter(Lifesteal::fraction),
                NON_NEGATIVE_FLOAT.fieldOf("cap").forGetter(Lifesteal::cap))
                .apply(instance, Lifesteal::new)));
        seam("chain_arc", RecordCodecBuilder.<ChainArc>mapCodec(instance -> instance.group(
                Codec.DOUBLE.fieldOf("range").forGetter(ChainArc::range),
                NON_NEGATIVE_FLOAT.fieldOf("damage_fraction").forGetter(ChainArc::damageFraction),
                ExtraCodecs.POSITIVE_INT.fieldOf("max_targets").forGetter(ChainArc::maxTargets))
                .apply(instance, ChainArc::new)));
        seam("lightning_on_hit", MapCodec.unit(LightningOnHit::new));

        // #829 utility/economy batch.
        register("self_repair_when", RecordCodecBuilder.<SelfRepairWhen>mapCodec(instance -> instance.group(
                SELF_REPAIR_CONDITION_CODEC.optionalFieldOf("condition", SelfRepairCondition.ALWAYS)
                        .forGetter(SelfRepairWhen::condition),
                ExtraCodecs.POSITIVE_INT.fieldOf("ticks_per_point").forGetter(SelfRepairWhen::ticksPerPoint))
                .apply(instance, SelfRepairWhen::new)));
        register("cascading_break", TagKey.codec(Registries.BLOCK).optionalFieldOf("blocks")
                .xmap(CascadingBreakDefinition::of, CascadingBreakDefinition::blocks));
        register("fertilize_on_use", RecordCodecBuilder.<FertilizeOnUse>mapCodec(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("durability_cost").forGetter(FertilizeOnUse::durabilityCost),
                Codec.floatRange(0.0F, 1.0F).fieldOf("chance").forGetter(FertilizeOnUse::chance))
                .apply(instance, FertilizeOnUse::new)));
        register("extra_modifier_slots",
                ExtraCodecs.POSITIVE_INT.fieldOf("count").xmap(ExtraModifierSlots::new, ExtraModifierSlots::count));

        // #830 energy batch.
        register("energized", RecordCodecBuilder.<EnergyBuffer>mapCodec(instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("capacity").forGetter(EnergyBuffer::capacity),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("energy_per_durability_point")
                        .forGetter(EnergyBuffer::energyPerDurabilityPoint))
                .apply(instance, EnergyBuffer::new)));
        register("solar_recharge",
                ExtraCodecs.POSITIVE_INT.fieldOf("rate_per_tick").xmap(SolarRecharge::new, SolarRecharge::ratePerTick));
        seam("kinetic_charge",
                NON_NEGATIVE_FLOAT.fieldOf("fraction").xmap(KineticCharge::new, KineticCharge::fractionOfDamage));

        // #831 M6-7 armor library. Registered with register(), not seam(): these are Trait#onDefend
        // behaviours, and Gate's ConditionalSeam implements neither onDefend nor incomingHit -- a
        // gated defensive seam would silently never run. The two that carry a roll
        // (effect_on_attacker, evasion) take their own `chance` field instead.
        register("damage_floor", NON_NEGATIVE_FLOAT.fieldOf("minimum_hearts")
                .xmap(DamageFloor::new, DamageFloor::minimumHearts));
        register("effect_on_attacker", RecordCodecBuilder.<EffectOnAttacker>mapCodec(instance -> instance.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(EffectOnAttacker::effect),
                ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(EffectOnAttacker::durationTicks),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("amplifier", 0).forGetter(EffectOnAttacker::amplifier),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("chance", 1.0F).forGetter(EffectOnAttacker::chance))
                .apply(instance, EffectOnAttacker::new)));
        register("effect_on_hurt", RecordCodecBuilder.<EffectOnHurt>mapCodec(instance -> instance.group(
                MobEffect.CODEC.fieldOf("effect").forGetter(EffectOnHurt::effect),
                ExtraCodecs.POSITIVE_INT.fieldOf("duration").forGetter(EffectOnHurt::durationTicks),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("amplifier", 0).forGetter(EffectOnHurt::amplifier))
                .apply(instance, EffectOnHurt::new)));
        register("amplify_incoming_healing", NON_NEGATIVE_FLOAT.fieldOf("factor")
                .xmap(AmplifyIncomingHealing::new, AmplifyIncomingHealing::factor));
        register("convert_damage_to_healing",
                RecordCodecBuilder.<ConvertDamageToHealing>mapCodec(instance -> instance.group(
                        DAMAGE_TYPE_TAG.fieldOf("damage_type").forGetter(ConvertDamageToHealing::damageType),
                        Codec.floatRange(0.0F, 1.0F).fieldOf("fraction").forGetter(ConvertDamageToHealing::fraction))
                        .apply(instance, ConvertDamageToHealing::new)));
        register("stacking_resistance", RecordCodecBuilder.<StackingResistance>mapCodec(instance -> instance.group(
                NON_NEGATIVE_FLOAT.fieldOf("per_hit").forGetter(StackingResistance::perHit),
                ExtraCodecs.POSITIVE_INT.fieldOf("cap").forGetter(StackingResistance::cap),
                ExtraCodecs.POSITIVE_INT.fieldOf("decay").forGetter(StackingResistance::decayTicks))
                .apply(instance, StackingResistance::new)));
        register("death_save", RecordCodecBuilder.<DeathSave>mapCodec(instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("cooldown").forGetter(DeathSave::cooldownTicks),
                ExtraCodecs.POSITIVE_INT.fieldOf("cost").forGetter(DeathSave::durabilityCost))
                .apply(instance, DeathSave::new)));
        register("invulnerability_window",
                RecordCodecBuilder.<InvulnerabilityWindow>mapCodec(instance -> instance.group(
                        ExtraCodecs.POSITIVE_INT.fieldOf("ticks").forGetter(InvulnerabilityWindow::ticks),
                        enumCodec(DefenseCondition.class).optionalFieldOf("condition", DefenseCondition.ANY)
                                .forGetter(InvulnerabilityWindow::condition))
                        .apply(instance, InvulnerabilityWindow::new)));
        register("evasion", Codec.floatRange(0.0F, 1.0F).fieldOf("chance").xmap(Evasion::new, Evasion::chance));
        register("conceal_in_darkness", RecordCodecBuilder.<ConcealInDarkness>mapCodec(instance -> instance.group(
                Codec.intRange(0, 15).fieldOf("light_threshold").forGetter(ConcealInDarkness::lightThreshold),
                Codec.floatRange(0.0F, 1.0F).fieldOf("visibility").forGetter(ConcealInDarkness::visibility))
                .apply(instance, ConcealInDarkness::new)));
        register("movement_bonus", RecordCodecBuilder.<MovementBonus>mapCodec(instance -> instance.group(
                enumCodec(MovementBonus.Kind.class).fieldOf("kind").forGetter(MovementBonus::kind),
                Codec.FLOAT.fieldOf("magnitude").forGetter(MovementBonus::magnitude))
                .apply(instance, MovementBonus::new)));
        register("stat_scales_with_wear", RecordCodecBuilder.<StatScalesWithWear>mapCodec(instance -> instance.group(
                enumCodec(StatScalesWithWear.Stat.class).fieldOf("stat").forGetter(StatScalesWithWear::stat),
                Codec.FLOAT.fieldOf("coefficient").forGetter(StatScalesWithWear::coefficient))
                .apply(instance, StatScalesWithWear::new)));
        register("damage_type_immunity", DAMAGE_TYPE_TAG.fieldOf("damage_type")
                .xmap(DamageTypeImmunity::new, DamageTypeImmunity::damageType));
        register("vent_explosions", NON_NEGATIVE_FLOAT.fieldOf("knockback_factor")
                .xmap(VentExplosions::new, VentExplosions::knockbackFactor));
    }


    /** The {@code behavior} field: a known id or a loud error naming every id that would have worked. */
    private static final Codec<ResourceLocation> TYPE_CODEC = ResourceLocation.CODEC.validate(id -> TYPES.containsKey(id)
            ? DataResult.success(id)
            : DataResult.error(() -> "Unknown trait behavior '" + id + "'; known behaviors: " + TYPES.keySet()));

    /** See {@link TraitDefinition#CODEC}. */
    static final Codec<TraitDefinition> CODEC = TYPE_CODEC.dispatch("behavior", TraitDefinition::behavior, TYPES::get);

    /** Every behaviour id a definition may name, in registration order. */
    public static Set<ResourceLocation> ids() {
        return TYPES.keySet();
    }

    private static <T extends Trait> void register(String name, MapCodec<T> codec) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
        TYPES.put(id, codec.xmap(trait -> new TraitDefinition(id, trait), TraitBehaviors::trait));
    }

    private static <S extends CombatSeam> void seam(String name, MapCodec<S> seamCodec) {
        register(name, RecordCodecBuilder.<SeamTrait>mapCodec(instance -> instance.group(
                Gate.CODEC.forGetter(SeamTrait::gate),
                seamCodec.forGetter((SeamTrait trait) -> TraitBehaviors.<S>seam(trait)))
                .apply(instance, SeamTrait::new)));
    }

    /** Encode-side casts: a definition registered under a behaviour id always holds that behaviour's type. */
    @SuppressWarnings("unchecked")
    private static <T extends Trait> T trait(TraitDefinition definition) {
        return (T) definition.trait();
    }

    @SuppressWarnings("unchecked")
    private static <S extends CombatSeam> S seam(SeamTrait trait) {
        return (S) trait.seam();
    }

    /** Lower-case enum names in JSON ({@code "full_charge"}), the way {@code HitCondition} reads in a spec. */
    static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(name -> {
            try {
                return DataResult.success(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + " '" + name + "'; one of "
                        + Arrays.stream(type.getEnumConstants()).map(c -> c.name().toLowerCase(Locale.ROOT)).toList());
            }
        }, constant -> constant.name().toLowerCase(Locale.ROOT));
    }

    private TraitBehaviors() {}
}
