package dev.gkissel.forgeweave.combat;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Forgeweave's own status effects -- deliberately just the registry plumbing. The scimitar's bleed
 * (issue #159): see {@link LacerateEffect} for why a DoT is a status effect at all, and
 * {@link Lacerate} for the seam that applies it. Issue #229's combat traits added sharp's own bleed
 * ({@link BleedEffect}) and two inert marks ({@link MarkerEffect}): splintering's stack counter and
 * enderference's teleport block.
 */
public final class ForgeweaveMobEffects {
    public static final DeferredRegister<MobEffect> MOB_EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, Forgeweave.MODID);

    public static final DeferredHolder<MobEffect, LacerateEffect> LACERATE =
            MOB_EFFECTS.register("lacerate", LacerateEffect::new);

    /** Sharp's DoT (issue #229, upstream {@code TraitSharp.DoT}). */
    public static final DeferredHolder<MobEffect, BleedEffect> BLEED =
            MOB_EFFECTS.register("bleed", BleedEffect::new);

    /** Splintering's stack counter (issue #229, upstream {@code TraitSplintering.Splinter}); bone-white. */
    public static final DeferredHolder<MobEffect, MarkerEffect> SPLINTER =
            MOB_EFFECTS.register("splinter", () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0xE8E5D2));

    /**
     * Enderference's teleport block (issue #229, upstream {@code TraitEnderference.Enderference},
     * including its 0x21985f color). {@code ForgeweaveTraits#onEnderTeleport} is what reads it.
     */
    public static final DeferredHolder<MobEffect, MarkerEffect> ENDERFERENCE =
            MOB_EFFECTS.register("enderference", () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0x21985F));

    /**
     * Piercing guard's mark on whoever hit a bone-mailled wearer (issue #680, the 1.20 clone's
     * {@code TinkerEffects.pierce}: harmful, {@code 0xD1D37A}, -1 armor per level as an attribute
     * modifier -- vanilla scales the amount by {@code amplifier + 1}).
     */
    public static final DeferredHolder<MobEffect, MobEffect> PIERCE =
            MOB_EFFECTS.register("pierce", () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0xD1D37A)
                    .addAttributeModifier(Attributes.ARMOR,
                            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "effect.pierce"),
                            -1.0, AttributeModifier.Operation.ADD_VALUE));

    /**
     * {@code grievous}'s mark (issue #828, M6 on-hit effect library, {@code reduce_target_healing}):
     * an inert marker whose amplifier is read back as a 0-100 percent healing reduction by {@code
     * ForgeweaveTraits#onLivingHeal} -- see {@link dev.gkissel.forgeweave.combat.ReduceTargetHealing}
     * for why the amplifier carries the fraction instead of a new data component.
     */
    public static final DeferredHolder<MobEffect, MobEffect> REDUCED_HEALING =
            MOB_EFFECTS.register("reduced_healing", () -> new MarkerEffect(MobEffectCategory.HARMFUL, 0x7A1F3D));

    private ForgeweaveMobEffects() {}
}
