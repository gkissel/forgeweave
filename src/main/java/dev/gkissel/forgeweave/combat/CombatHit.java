package dev.gkissel.forgeweave.combat;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * One blow struck with a Forgeweave tool, as every {@link CombatSeam} sees it. {@link CombatSeams}
 * builds exactly one of these per hit and hands the same instance to the pre-hit, on-hit and
 * post-kill calls of that hit, so a seam never has to re-derive who hit whom with what.
 *
 * @param level the server level the blow lands in -- seams are server side only ({@link CombatSeams})
 * @param weapon the Forgeweave tool the blow was struck with, never Broken (see {@link CombatSeams})
 * @param attacker whoever swung it, or {@code null} when the damage source names no living entity
 *     (a tool held by an arrow-shooting dispenser, a mob killed by its own thrown weapon, ...)
 * @param target what was hit
 * @param source the damage source of the blow, for seams that need its type tags (M3's smite, bane
 *     of arthropods and the armor-bypassing innates all key off it -- docs/SCOPE.md M3)
 */
public record CombatHit(
        ServerLevel level,
        ItemStack weapon,
        @Nullable LivingEntity attacker,
        LivingEntity target,
        DamageSource source) {}
