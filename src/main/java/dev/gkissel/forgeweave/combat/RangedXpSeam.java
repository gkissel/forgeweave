package dev.gkissel.forgeweave.combat;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.tool.ToolLeveling;

/**
 * Ranged XP on projectile impact (docs/SCOPE.md M7, D-M7-6; issue #920), ported from Tinkers' Tool
 * Leveling's {@code ModToolLeveling#afterHit(EntityProjectileBase, ...)} (MIT): a bow-family launcher
 * earns {@code ceil(5 * drawTime / (20 * drawSpeed))} XP for every entity it actually hits, where
 * {@code drawTime} is {@link BowItem#drawTime()} and {@code drawSpeed} the tool's own assembled draw
 * speed ({@link BowItem#drawSpeed}, upstream's {@code ProjectileLauncherNBT.drawSpeed}).
 *
 * <p>Hangs off {@link CombatSeam#onHit} rather than the arrow entity's own hit hook (D-M7-6): that is
 * the moment {@link CombatSeams} already resolves a projectile's live launcher stack to (issue #416),
 * and it only fires once damage was actually dealt to a living target -- an arrow that lands in a
 * block never posts the {@code LivingDamageEvent.Post} this rides, so a miss grants nothing without
 * any extra gate here. Crossbows have no upstream counterpart and take the same formula with their
 * own {@link BowItem#drawTime()} (45); the shuriken has no draw and does not extend {@link BowItem},
 * so it never matches {@link #collect} -- see the PR body for its own treatment.
 */
public final class RangedXpSeam implements CombatSeam {
    public static final RangedXpSeam INSTANCE = new RangedXpSeam();

    /** Upstream's flat "5 XP per second of draw time" rate. */
    private static final float XP_PER_SECOND_DRAWN = 5.0F;
    /** Ticks per second, the same constant upstream's {@code 20d *} folds in. */
    private static final float TICKS_PER_SECOND = 20.0F;

    private RangedXpSeam() {}

    /** Registered once in {@code Forgeweave}: applies to every bow-family launcher. */
    public static void collect(ItemStack weapon, Consumer<CombatSeam> out) {
        if (weapon.getItem() instanceof BowItem) {
            out.accept(INSTANCE);
        }
    }

    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (!hit.isProjectile() || !(hit.attacker() instanceof ServerPlayer player)
                || !(hit.weapon().getItem() instanceof BowItem bow)) {
            return;
        }
        float drawSpeed = bow.drawSpeed(hit.weapon());
        float drawTimeSeconds = bow.drawTime() / (TICKS_PER_SECOND * drawSpeed);
        int xp = Mth.ceil(XP_PER_SECOND_DRAWN * drawTimeSeconds);
        ToolLeveling.addXp(hit.weapon(), xp, player);
    }
}
