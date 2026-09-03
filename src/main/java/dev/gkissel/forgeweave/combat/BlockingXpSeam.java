package dev.gkissel.forgeweave.combat;

import java.util.function.Consumer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.tool.ToolLeveling;

/**
 * Blocking XP (docs/SCOPE.md M7, D-M7-10; issue #920), ported from Tinkers' Tool Leveling's
 * {@code ModToolLeveling#onBlock} and its battlesign-specific {@code onLivingHurt} handler (MIT): the
 * tool a player is actively blocking with earns {@code max(1, round(originalDamage))} XP -- the
 * <em>incoming</em> damage, not what the block ultimately absorbed, which is upstream's own choice
 * and is kept here. Reads {@link CombatDefense#using()} (this tool is the active item) and
 * {@link CombatDefense#blocking()} (that active item has the BLOCK use animation) together, matching
 * upstream's {@code player.getActiveItemStack() == tool} gate.
 *
 * <p>No separate code for the battlesign's projectile reflect: {@link ForgeweaveInnates.Deflect}
 * already zeroes a blocked projectile's damage earlier in the same {@link CombatSeam#incomingHit}
 * chain, but this seam still runs and still grants off {@code originalDamage}, which {@link
 * CombatSeams} fixes for the whole chain before any seam adjusts it -- so upstream's separate
 * {@code max(1, round(amount))} battlesign case falls out of the one general rule rather than needing
 * its own hook.
 */
public final class BlockingXpSeam implements CombatSeam {
    public static final BlockingXpSeam INSTANCE = new BlockingXpSeam();

    private BlockingXpSeam() {}

    /** Registered once in {@code Forgeweave}: applies to every Forgeweave tool. */
    public static void collect(ItemStack weapon, Consumer<CombatSeam> out) {
        out.accept(INSTANCE);
    }

    @Override
    public float incomingHit(CombatDefense defense, float originalDamage, float damage) {
        if (defense.using() && defense.blocking() && defense.defender() instanceof ServerPlayer player) {
            ToolLeveling.addXp(defense.tool(), Math.max(1, Math.round(originalDamage)), player);
        }
        return damage;
    }
}
