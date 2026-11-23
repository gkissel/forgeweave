package dev.gkissel.forgeweave.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.api.combat.CombatHit;
import dev.gkissel.forgeweave.entity.ArrowEntity;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.ToolLeveling;

/**
 * Ranged XP on projectile impact (docs/SCOPE.md M7, D-M7-6; issue #920), ported from Tinkers' Tool
 * Leveling's {@code ModToolLeveling#afterHit(EntityProjectileBase, ...)} (MIT): a bow-family launcher
 * earns {@code ceil(5 * drawTime / (20 * drawSpeed))} XP for every entity it actually hits, where
 * {@code drawTime} is {@link BowItem#drawTime()} and {@code drawSpeed} the tool's own assembled draw
 * speed ({@link BowItem#drawSpeed}, upstream's {@code ProjectileLauncherNBT.drawSpeed}).
 *
 * <p>Called from {@link CombatSeams}' on-hit, beside the melee ledger: that is the moment the
 * pipeline already resolves a projectile's live launcher stack (issue #416), and it only fires once
 * damage was actually dealt to a living target, so a miss grants nothing without any extra gate.
 *
 * <p>Not a {@code CombatProviders} seam (issue #1233): a provider is chosen by the hit's weapon, and
 * a material arrow's weapon is the arrow, not the bow, so a seam keyed on {@link BowItem} never ran
 * for the ammo a Forgeweave bow fires first. The launcher is the weapon for a vanilla arrow and
 * {@link ArrowEntity#launcher()} for a material one, upstream's {@code getLaunchingStack}. Crossbows
 * have no upstream counterpart and take the same formula with their own {@link BowItem#drawTime()}
 * (45). The shuriken has no launcher and earns through {@code DamageXpLedger} instead.
 */
public final class RangedXpSeam {

    /** Upstream's flat "5 XP per second of draw time" rate. */
    private static final float XP_PER_SECOND_DRAWN = 5.0F;
    /** Ticks per second, the same constant upstream's {@code 20d *} folds in. */
    private static final float TICKS_PER_SECOND = 20.0F;

    private RangedXpSeam() {}

    public static void afterProjectileHit(CombatHit hit) {
        if (!hit.isProjectile() || !(hit.attacker() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack launcher = hit.weapon();
        if (!(launcher.getItem() instanceof BowItem) && hit.source().getDirectEntity() instanceof ArrowEntity arrow) {
            launcher = CombatSeams.liveLauncher(player, arrow.launcher());
        }
        if (!(launcher.getItem() instanceof BowItem bow) || ToolItem.isBroken(launcher)) {
            return;
        }
        float drawSpeed = bow.drawSpeed(launcher);
        float drawTimeSeconds = bow.drawTime() / (TICKS_PER_SECOND * drawSpeed);
        int xp = Mth.ceil(XP_PER_SECOND_DRAWN * drawTimeSeconds);
        ToolLeveling.addXp(launcher, xp, player);
    }
}
