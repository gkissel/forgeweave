package dev.gkissel.forgeweave.combat;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * A combat innate whose damage bonus builds up over consecutive landed hits and falls away once the
 * wielder stops landing them -- the katana's ramp (docs/SCOPE.md M3, issue #160). Attached like every
 * other combat behavior, as a {@link CombatSeam} handed out by a {@link CombatSeams.Provider}
 * registered in {@code Forgeweave} (ADR-0005 decision 3).
 *
 * <h2>Magnitudes (maintainer decision on issue #160, 2026-08-12)</h2>
 *
 * <p>{@code +10%} damage per landed melee hit, capped at {@code +75%}, reset after {@code 5} seconds
 * without landing one. All three are constructor parameters rather than constants in the body, which
 * is what ADR-0004's M6 commitment asks of a seam: the numbers have to be able to arrive from
 * datapack JSON later without the behavior being rewritten. {@link #KATANA} is the one instance the
 * decision above names.
 *
 * <h2>Serialized state ({@link State})</h2>
 *
 * <p>The ramp is a <b>timer since the last landed hit</b>, not a countdown, and it is stored that
 * way: a stack count plus the game time of the hit that produced it
 * ({@link ForgeweaveDataComponents#KATANA_RAMP}). Two consequences, both deliberate:
 *
 * <ul>
 *   <li><b>Nothing ticks.</b> Expiry is derived on read ({@link #liveStacks}), so a katana lying in a
 *       chest, in an unloaded chunk, or in another player's inventory needs no per-tick upkeep -- and
 *       it cannot "hold" its ramp across a logout the way a stored countdown would. Contrast
 *       {@code trait.TraitStacks}, whose {@code ticks_remaining} is decremented from
 *       {@code Trait#inventoryTick} and therefore only decays while carried; that shape is wrong for
 *       an in-combat ramp, which must lapse in real time whether or not the tool is being held.
 *   <li><b>The format is one int and one long, forever.</b> This component ships from the first beta
 *       and docs/SCOPE.md makes the save-compat corpus CI-gating, so the shape is pinned by
 *       {@code fixtures/save_compat/m3_tool_katana_ramp.snbt}. Deriving the bonus from the stack
 *       count at read time (rather than storing a bonus, an expiry tick, or a tuned magnitude) is
 *       what lets the numbers above change -- or move to a datapack -- without stranding a save.
 * </ul>
 *
 * <p>A lapsed component is left on the stack rather than cleared: it already reads as zero stacks
 * everywhere, and clearing it would mean an extra write on a path that has nothing else to do.
 *
 * <p>ponytail: no separate "in combat" flag, no attacker-scoped bookkeeping, no tick subscription.
 * The last-hit timestamp answers "is the ramp still live?" on its own.
 *
 * <h2>{@code requireFullCharge} (issue #827)</h2>
 *
 * <p>M6's damage-scaling library batch asks for a second ramp instance that only counts
 * <em>fully-charged</em> landed hits ({@link CombatHit#isFullCharge}) rather than every one --
 * "reuse its component and decay machinery rather than adding a second stateful ramp" (issue #827).
 * {@link #ESCALATING} is that instance: same {@link ForgeweaveDataComponents#KATANA_RAMP}
 * component and the same read-a-timer {@link #liveStacks}/{@link #bonus} machinery as {@link
 * #KATANA}, just gated so a hit that was not fully charged neither builds nor refreshes the stack
 * in {@link #onHit} (the bonus itself still applies to every hit, same as the katana's, once the
 * ramp is live). Nothing about the serialized {@link State} shape changes, so no new save-compat
 * fixture is needed -- the existing katana one already pins it.
 */
public record DamageRamp(float stepPerStack, float maxBonus, int resetTicks, boolean requireFullCharge)
        implements CombatSeam {

    /**
     * One tool's live ramp. Save-compat promised from the first beta -- see the class javadoc before
     * changing a field name or type.
     *
     * @param stacks landed hits currently counted, never above {@link DamageRamp#maxStacks()}
     * @param lastHit the game time of the most recent landed hit, which is what {@link
     *     DamageRamp#resetTicks()} is measured from
     */
    public record State(int stacks, long lastHit) {
        public static final Codec<State> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("stacks").forGetter(State::stacks),
                Codec.LONG.fieldOf("last_hit").forGetter(State::lastHit))
                .apply(instance, State::new));

        public static final StreamCodec<ByteBuf, State> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, State::stacks,
                ByteBufCodecs.VAR_LONG, State::lastHit,
                State::new);
    }

    /** The katana's ramp, with the issue #160 decision comment's three numbers. */
    public static final DamageRamp KATANA = new DamageRamp(0.10F, 0.75F, 5 * 20, false);

    /**
     * M6's "consecutive-charge ramp" library instance (issue #827): {@code +15%} damage per
     * consecutive fully-charged landed hit, capped at {@code +60%}, reset after {@code 4} seconds
     * without landing one. Proposed magnitudes -- maintainer decision point, same as issue #160's
     * for {@link #KATANA} and #103/M3's combat innates; not yet confirmed on the issue thread.
     */
    public static final DamageRamp ESCALATING = new DamageRamp(0.15F, 0.60F, 4 * 20, true);

    /**
     * Where counting stops. The <em>bonus</em> is what the decision caps, so at +10% a step the cap
     * lands mid-stack (7.5); counting one further and clamping the bonus keeps the last partial step
     * reachable instead of rounding it away, and keeps the stored count bounded either way.
     */
    public int maxStacks() {
        return Mth.ceil(maxBonus / stepPerStack);
    }

    /** The bonus fraction {@code weapon} currently carries, {@code 0} once the ramp has lapsed. */
    public float bonus(ItemStack weapon, long gameTime) {
        return Math.min(maxBonus, liveStacks(weapon, gameTime) * stepPerStack);
    }

    /**
     * {@code weapon}'s stack count if the ramp is still live, otherwise zero. A negative elapsed time
     * counts as lapsed too: a world whose clock went backwards (a restored backup) must not leave a
     * katana ramped forever.
     */
    public int liveStacks(ItemStack weapon, long gameTime) {
        State state = weapon.get(ForgeweaveDataComponents.KATANA_RAMP.get());
        if (state == null) {
            return 0;
        }
        long elapsed = gameTime - state.lastHit();
        return elapsed < 0 || elapsed >= resetTicks ? 0 : state.stacks();
    }

    /**
     * The ramp applies to the blow <em>after</em> the ones that built it: this reads the stacks the
     * previous hits left, and {@link #onHit} adds this blow's own only once it has actually landed.
     * Scaled off {@code originalDamage} rather than the running {@code damage} so the bonus is the
     * stated percentage of the tool's own hit and stays order-independent among seams ({@link
     * CombatSeam#preHit}).
     */
    @Override
    public float preHit(CombatHit hit, float originalDamage, float damage) {
        return damage + originalDamage * bonus(hit.weapon(), hit.level().getGameTime());
    }

    /**
     * A landed hit: one more stack (up to the cap) and the timer restarts from now -- unless {@link
     * #requireFullCharge} and this blow was not a fully-charged swing, in which case it neither
     * builds nor refreshes the ramp (issue #827's {@link #ESCALATING}). The bonus a live ramp grants
     * still applies to every hit either way; only what counts toward building it differs.
     */
    @Override
    public void onHit(CombatHit hit, float damageDealt) {
        if (requireFullCharge && !hit.isFullCharge()) {
            return;
        }
        long now = hit.level().getGameTime();
        int next = Math.min(maxStacks(), liveStacks(hit.weapon(), now) + 1);
        hit.weapon().set(ForgeweaveDataComponents.KATANA_RAMP.get(), new State(next, now));
    }
}
