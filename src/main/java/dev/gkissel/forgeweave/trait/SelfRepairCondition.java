package dev.gkissel.forgeweave.trait;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * When {@link SelfRepairWhen} may heal the tool carrying it. Same shape as {@code
 * dev.gkissel.forgeweave.combat.HitCondition}'s parameter enum: "add a constant when a shipped
 * instance needs it, not before" -- issue #829's M6 utility/economy library batch ships {@link
 * #ALWAYS} ({@code ecological}'s own unconditional behavior, generalized), {@link #SUNLIT} and
 * {@link #NIGHT}.
 */
@FunctionalInterface
public interface SelfRepairCondition {

    /** Whether {@code holder}'s tool may heal one durability point right now. */
    boolean active(ServerLevel level, LivingEntity holder);

    /** Unconditional -- {@code ecological}'s own upstream behavior (issue #102), ported whole. */
    SelfRepairCondition ALWAYS = (level, holder) -> true;

    /**
     * Direct sunlight: daytime, no rain, and open sky overhead -- {@code sunmend}'s gate. Own
     * formula, not a port: vanilla's closest equivalent ({@code Mob#isSunBurnTick}) is a per-tick
     * random mob-burn roll with its own unrelated purpose, not a "is this entity in the sun" query a
     * self-repair trait could reuse as-is.
     */
    SelfRepairCondition SUNLIT =
            (level, holder) -> level.isDay() && !level.isRaining() && level.canSeeSky(holder.blockPosition());

    /** Night -- {@code duskmend}'s gate, the mirror of {@link #SUNLIT}. */
    SelfRepairCondition NIGHT = (level, holder) -> level.isNight();
}
