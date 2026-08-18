package dev.gkissel.forgeweave.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.block.SearedChannelBlock;

/**
 * The channel's item (issue #441, parity audit T9), ported from upstream 1.12's {@code ItemChannel}
 * (NOTICE.md).
 *
 * <p>It exists for one line of upstream's {@code onPlaceBlock}: a channel placed against the
 * <em>underside</em> of another channel opens that channel's downward output, so a vertical drop is
 * built by stacking rather than by clicking each joint afterwards. Every other placement case is a
 * property of the new block alone and lives in {@link SearedChannelBlock#getStateForPlacement}.
 */
public class SearedChannelBlockItem extends BlockItem {

    public SearedChannelBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        if (!super.placeBlock(context, state)) {
            return false;
        }
        if (context.getClickedFace() == Direction.DOWN) {
            Level level = context.getLevel();
            BlockPos above = context.getClickedPos().above();
            BlockState aboveState = level.getBlockState(above);
            if (aboveState.getBlock() instanceof SearedChannelBlock
                    && !aboveState.getValue(SearedChannelBlock.DOWN)) {
                level.setBlockAndUpdate(above, aboveState.setValue(SearedChannelBlock.DOWN, true));
            }
        }
        return true;
    }
}
