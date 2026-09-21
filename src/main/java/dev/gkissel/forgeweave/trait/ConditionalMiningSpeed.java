package dev.gkissel.forgeweave.trait;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.api.trait.Trait;

/**
 * Mines faster where the world suits the metal, and slower where it does not (issue #1114's
 * {@code conditional_mining_speed}).
 *
 * <p>The general form of the two upstream traits that already do this for one fixed condition each
 * -- {@code aquadynamic}'s water and {@code aridiculous}' biome temperature -- with the condition
 * lifted into a {@link WorldCondition} field and a penalty half the upstream pair does not have. A
 * material with a strong number and a real place it is weak reads as a choice; the same number
 * unconditional reads as a stat.
 *
 * <p>No proc feedback: the bonus is constant for as long as the condition holds, so there is no
 * discrete event to announce. It shows in how fast the block comes apart, and in the tooltip
 * sentence, which is where issue #1112's own guidance puts a standing bonus.
 *
 * @param condition where and when {@link #bonus} is paid
 * @param bonus fraction of the tool's own break speed added while the condition holds
 * @param penalty fraction taken off while it does not; {@code 0} for a bonus with no downside
 */
public record ConditionalMiningSpeed(WorldCondition condition, float bonus, float penalty) implements Trait {

    @Override
    public float breakSpeed(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed) {
        float fraction = condition.active(player.level(), player) ? bonus : -penalty;
        return speed + originalSpeed * fraction;
    }
}
