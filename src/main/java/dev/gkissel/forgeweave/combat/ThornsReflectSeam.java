package dev.gkissel.forgeweave.combat;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ToolItem;

/**
 * Being hit reflects the tool's own attack damage back at the attacker, past armor -- spiky, cactus's
 * trait (issue #229), ported from upstream 1.12's {@code TraitSpiky#dealSpikyDamage}: the tool's
 * actual damage ({@code ToolHelper#getActualDamage}), <b>halved when the tool is merely held</b>
 * ({@code onPlayerHurt}) and <b>full while blocking</b> ({@code onBlock}), dealt as an
 * armor-bypassing secondary hit that neither consumes nor grants an invulnerability window
 * (upstream saves and restores {@code hurtResistantTime} around it).
 *
 * <p>Upstream marks its reflect as thorns damage and refuses to reflect thorns damage, which is what
 * stops two spiky tools feeding each other forever. The reflect here rides
 * {@code ForgeweaveInnates#armorBypassing}, whose damage type sits in
 * {@code minecraft:bypasses_armor} -- so the same guard is "never reflect an armor-bypassing hit",
 * which covers the mutual-reflect loop (the reflect itself bypasses armor) and, incidentally,
 * declines to reflect magic/bleed ticks the way upstream's thorns flag declined its own.
 *
 * @param heldFraction the fraction of the tool's damage reflected while merely held; blocking
 *     always reflects the full amount (upstream halves exactly once, on the not-blocking path)
 */
public record ThornsReflectSeam(float heldFraction) implements CombatSeam {

    @Override
    public float incomingHit(CombatDefense defense, float originalDamage, float damage) {
        LivingEntity attacker = defense.attacker();
        if (attacker == null || attacker == defense.defender() || !attacker.isAlive()) {
            return damage;
        }
        if (defense.source().is(DamageTypeTags.BYPASSES_ARMOR)) {
            return damage; // upstream's isThornsDamage guard -- see the class javadoc.
        }
        float reflected = toolDamage(defense.tool()) * (defense.blocking() ? 1.0F : heldFraction);
        if (reflected <= 0.0F) {
            return damage;
        }
        int invulnerableTime = attacker.invulnerableTime;
        attacker.invulnerableTime = 0;
        attacker.hurt(ForgeweaveInnates.armorBypassing(defense.level(), defense.defender()), reflected);
        attacker.invulnerableTime = invulnerableTime;
        return damage;
    }

    /** Upstream's {@code ToolHelper.getActualDamage}: the tool's own attack damage stat. */
    private static float toolDamage(ItemStack tool) {
        return tool.getItem() instanceof ToolItem item ? item.attackDamage(tool) : 0.0F;
    }
}
