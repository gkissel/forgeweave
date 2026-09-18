package dev.gkissel.forgeweave.modifier;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffect;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.Application;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.AoeExpansion;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.Attribute;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.AttributeBonus;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.Behavior;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.BonusDamageVs;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.BonusExperience;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.BonusSlots;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.DurabilityNegation;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.EffectOnHit;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.FireResistant;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.GrantEnchantment;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.IgniteOnHit;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.KnockbackOnHit;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.LifestealOnHit;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.ProtectionBonus;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.SlotCost;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.Stat;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.StatBonus;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.ThornsCounter;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.ToolGate;
import dev.gkissel.forgeweave.modifier.ModifierLibrary.ToolTier;

/**
 * The behavior-type registry {@link ModifierDefinition}'s codec dispatches on: one {@link MapCodec}
 * per {@link ModifierLibrary} class, keyed by the {@code forgeweave:<name>} id a definition's
 * {@code behavior} field names. The parameter sets <em>are</em> the schemas -- each codec below
 * reads the same numbers {@link ForgeweaveModifiers} hardcodes in Java, under snake_case field
 * names. Issue #973, ADR-0004 item 3's modifier half, the sibling of {@code TraitBehaviors}.
 *
 * <p>Every behavior also accepts the three optional {@link Application} fields
 * ({@code units_per_level}, {@code slots}, {@code tools}), which is how {@code TraitBehaviors.Gate}
 * carries the shared seam gate on the trait side.
 *
 * <p>ponytail: a plain map, not a Minecraft registry, for {@code TraitBehaviors}' reason -- behavior
 * classes are Java and only a mod update adds one, so there is nothing a registry event could
 * contribute, and {@link #ids} is the whole discovery surface a pack author needs.
 *
 * <h2>What stayed in Java</h2>
 *
 * <p>Deliberately absent, the same way {@code escalating} is absent from the trait library: the
 * modifiers whose behavior is not a function of level and a parameter set. Haste's and sharpness's
 * diminishing-returns curves ({@code applyHarvestBoost}, {@code ModSharpness}) are loops over
 * thresholds, not a bonus with a coefficient. Searing, blasting, veinmine, magnetic pull, mending
 * moss, soulbound, glowing, luck's self-growth and beheading live in event handlers that see a drop
 * list, an XP orb or a death, which no {@link Modifier} hook carries. Embossment and fortification
 * are generated per material. The compat-owned ones (socketed, surgebound, the Draconic and Create
 * bridges) name another mod's types or read config values, so data cannot own their numbers.
 * Nothing about that list changed here, and a pack can still retune every one of them through its
 * {@link ModifierRecipe}.
 */
public final class ModifierBehaviors {

    private static final Map<ResourceLocation, MapCodec<ModifierDefinition>> TYPES = new LinkedHashMap<>();

    /** {@code ExtraCodecs} has the int one but not the float one. */
    private static final Codec<Float> NON_NEGATIVE_FLOAT = Codec.floatRange(0.0F, Float.MAX_VALUE);
    private static final Codec<Float> CHANCE = Codec.floatRange(0.0F, 1.0F);

    /**
     * The optional fields every behavior accepts. Absent means {@link Application#DEFAULT}: one unit
     * per level, one slot per level, and no restriction on which tool takes it.
     */
    static final MapCodec<Application> APPLICATION = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("units_per_level", 1).forGetter(Application::unitsPerLevel),
            enumCodec(SlotCost.class).optionalFieldOf("slots", SlotCost.PER_LEVEL).forGetter(Application::slots),
            enumCodec(ToolGate.class).optionalFieldOf("tools", ToolGate.ANY).forGetter(Application::tools))
            .apply(instance, Application::new));

    static {
        register("stat_bonus", RecordCodecBuilder.<StatBonus>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(StatBonus::application),
                enumCodec(Stat.class).fieldOf("stat").forGetter(StatBonus::stat),
                Codec.FLOAT.optionalFieldOf("flat", 0.0F).forGetter(StatBonus::flat),
                Codec.FLOAT.optionalFieldOf("per_level", 0.0F).forGetter(StatBonus::perLevel),
                Codec.FLOAT.optionalFieldOf("fraction_of_base", 0.0F).forGetter(StatBonus::fractionOfBase),
                Codec.FLOAT.optionalFieldOf("minimum").forGetter(StatBonus::minimum))
                .apply(instance, StatBonus::new)));
        register("attribute_bonus", RecordCodecBuilder.<AttributeBonus>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(AttributeBonus::application),
                enumCodec(Attribute.class).fieldOf("attribute").forGetter(AttributeBonus::attribute),
                Codec.FLOAT.optionalFieldOf("flat", 0.0F).forGetter(AttributeBonus::flat),
                Codec.FLOAT.optionalFieldOf("per_level", 0.0F).forGetter(AttributeBonus::perLevel))
                .apply(instance, AttributeBonus::new)));
        register("tool_tier", RecordCodecBuilder.<ToolTier>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(ToolTier::application),
                Codec.INT.optionalFieldOf("bump", 0).forGetter(ToolTier::bump),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("at_least", 0).forGetter(ToolTier::atLeast),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("cap").forGetter(ToolTier::cap))
                .apply(instance, ToolTier::new)));
        register("durability_negation", RecordCodecBuilder.<DurabilityNegation>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(DurabilityNegation::application),
                CHANCE.fieldOf("chance_per_level").forGetter(DurabilityNegation::chancePerLevel))
                .apply(instance, DurabilityNegation::new)));
        register("bonus_slots", RecordCodecBuilder.<BonusSlots>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(BonusSlots::application),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("flat", 0).forGetter(BonusSlots::flat),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("per_level", 0).forGetter(BonusSlots::perLevel))
                .apply(instance, BonusSlots::new)));
        register("bonus_experience", RecordCodecBuilder.<BonusExperience>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(BonusExperience::application),
                NON_NEGATIVE_FLOAT.fieldOf("fraction_per_level").forGetter(BonusExperience::fractionPerLevel))
                .apply(instance, BonusExperience::new)));
        register("grant_enchantment", RecordCodecBuilder.<GrantEnchantment>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(GrantEnchantment::application),
                ResourceKey.codec(Registries.ENCHANTMENT).fieldOf("enchantment")
                        .forGetter(GrantEnchantment::enchantment),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("level").forGetter(GrantEnchantment::level),
                ExtraCodecs.POSITIVE_INT.optionalFieldOf("max_level").forGetter(GrantEnchantment::maxLevel))
                .apply(instance, GrantEnchantment::new)));
        register("fire_resistant", RecordCodecBuilder.<FireResistant>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(FireResistant::application))
                .apply(instance, FireResistant::new)));
        register("aoe_expansion", RecordCodecBuilder.<AoeExpansion>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(AoeExpansion::application),
                enumCodec(Modifier.AoeAxis.class).fieldOf("axis").forGetter(AoeExpansion::axis))
                .apply(instance, AoeExpansion::new)));

        // Combat seams.
        register("knockback_on_hit", RecordCodecBuilder.<KnockbackOnHit>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(KnockbackOnHit::application),
                Codec.FLOAT.fieldOf("per_unit").forGetter(KnockbackOnHit::perUnit))
                .apply(instance, KnockbackOnHit::new)));
        register("effect_on_hit", RecordCodecBuilder.<EffectOnHit>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(EffectOnHit::application),
                MobEffect.CODEC.fieldOf("effect").forGetter(EffectOnHit::effect),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("amplifier", 0).forGetter(EffectOnHit::amplifier),
                NON_NEGATIVE_FLOAT.fieldOf("duration_per_unit").forGetter(EffectOnHit::durationPerUnit),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("duration_offset", 0)
                        .forGetter(EffectOnHit::durationOffset))
                .apply(instance, EffectOnHit::new)));
        register("bonus_damage_vs", RecordCodecBuilder.<BonusDamageVs>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(BonusDamageVs::application),
                TagKey.codec(Registries.ENTITY_TYPE).fieldOf("entities").forGetter(BonusDamageVs::entities),
                Codec.FLOAT.fieldOf("damage_per_level").forGetter(BonusDamageVs::damagePerLevel))
                .apply(instance, BonusDamageVs::new)));
        register("ignite_on_hit", RecordCodecBuilder.<IgniteOnHit>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(IgniteOnHit::application),
                NON_NEGATIVE_FLOAT.optionalFieldOf("seconds_per_unit", 0.0F).forGetter(IgniteOnHit::secondsPerUnit),
                ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("seconds_offset", 0)
                        .forGetter(IgniteOnHit::secondsOffset),
                NON_NEGATIVE_FLOAT.optionalFieldOf("damage_per_unit", 0.0F).forGetter(IgniteOnHit::damagePerUnit))
                .apply(instance, IgniteOnHit::new)));
        register("lifesteal_on_hit", RecordCodecBuilder.<LifestealOnHit>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(LifestealOnHit::application),
                NON_NEGATIVE_FLOAT.fieldOf("fraction_per_level").forGetter(LifestealOnHit::fractionPerLevel))
                .apply(instance, LifestealOnHit::new)));
        register("thorns_counter", RecordCodecBuilder.<ThornsCounter>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(ThornsCounter::application),
                CHANCE.fieldOf("chance_per_level").forGetter(ThornsCounter::chancePerLevel),
                NON_NEGATIVE_FLOAT.fieldOf("constant_damage").forGetter(ThornsCounter::constantDamage),
                NON_NEGATIVE_FLOAT.fieldOf("random_damage").forGetter(ThornsCounter::randomDamage))
                .apply(instance, ThornsCounter::new)));
        register("protection", RecordCodecBuilder.<ProtectionBonus>mapCodec(instance -> instance.group(
                APPLICATION.forGetter(ProtectionBonus::application),
                NON_NEGATIVE_FLOAT.fieldOf("per_level").forGetter(ProtectionBonus::perLevel),
                TagKey.codec(Registries.DAMAGE_TYPE).optionalFieldOf("damage_type")
                        .forGetter(ProtectionBonus::damageType),
                Codec.BOOL.optionalFieldOf("direct_only", false).forGetter(ProtectionBonus::directOnly))
                .apply(instance, ProtectionBonus::new)));
    }

    /** The {@code behavior} field: a known id or a loud error naming every id that would have worked. */
    private static final Codec<ResourceLocation> TYPE_CODEC = ResourceLocation.CODEC.validate(id -> TYPES.containsKey(id)
            ? DataResult.success(id)
            : DataResult.error(() -> "Unknown modifier behavior '" + id + "'; known behaviors: " + TYPES.keySet()));

    /** See {@link ModifierDefinition#CODEC}. */
    static final Codec<ModifierDefinition> CODEC =
            TYPE_CODEC.dispatch("behavior", ModifierDefinition::behavior, TYPES::get);

    /** Every behavior id a definition may name, in registration order. */
    public static Set<ResourceLocation> ids() {
        return TYPES.keySet();
    }

    private static <B extends Behavior> void register(String name, MapCodec<B> codec) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
        TYPES.put(id, codec.xmap(behavior -> new ModifierDefinition(id, behavior), ModifierBehaviors::behavior));
    }

    /** Encode-side cast: a definition registered under a behavior id always holds that behavior's type. */
    @SuppressWarnings("unchecked")
    private static <B extends Behavior> B behavior(ModifierDefinition definition) {
        return (B) definition.modifier();
    }

    /**
     * Lower-case enum names in JSON ({@code "first_level_only"}), listing the alternatives when one
     * is wrong.
     *
     * <p>ponytail: the same eight lines {@code TraitBehaviors#enumCodec} has, rather than making
     * that one public and pointing the modifier package at the trait package for a codec helper. If
     * a third registry wants it, that is the moment it earns a home of its own.
     */
    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(name -> {
            try {
                return DataResult.success(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException e) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + " '" + name + "'; one of "
                        + Arrays.stream(type.getEnumConstants()).map(c -> c.name().toLowerCase(Locale.ROOT)).toList());
            }
        }, constant -> constant.name().toLowerCase(Locale.ROOT));
    }

    private ModifierBehaviors() {}
}
