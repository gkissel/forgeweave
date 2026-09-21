package dev.gkissel.forgeweave.trait;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Where and when a {@link ConditionalMiningSpeed} pays out -- the world's own state around whoever
 * holds the tool, rather than anything about the block or the blow.
 *
 * <p>Same rule as {@link dev.gkissel.forgeweave.combat.HitCondition} and
 * {@link SelfRepairCondition}: one constant per shipped consumer, add one when a trait needs it.
 *
 * <p>ponytail: {@link SelfRepairCondition} already holds these two predicates as lambdas for
 * {@code sunmend} and {@code duskmend}. It is a {@code @FunctionalInterface} named for self-repair
 * and it takes a {@code ServerLevel}, which a break-speed read cannot hand it (the break-speed event
 * also fires on the client, for its own prediction), so folding the two together is a refactor for
 * when a third consumer turns up rather than now.
 */
public enum WorldCondition {
    /** Direct sunlight: daytime, no rain, open sky overhead -- {@link SelfRepairCondition#SUNLIT}'s test. */
    SUNLIT,
    /** It is raining or snowing where the holder stands. */
    RAINING,
    /** The Nether, read off the dimension's own no-sky/ultrawarm flags rather than its id. */
    NETHER;

    /** Whether the condition holds for {@code holder} right now. */
    public boolean active(Level level, LivingEntity holder) {
        return switch (this) {
            case SUNLIT -> level.isDay() && !level.isRaining() && level.canSeeSky(holder.blockPosition());
            case RAINING -> level.isRainingAt(holder.blockPosition().above());
            case NETHER -> level.dimensionType().ultraWarm();
        };
    }
}
