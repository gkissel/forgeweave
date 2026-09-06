package dev.gkissel.forgeweave.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * A plain seared block (the twelve wall/floor styles and seared glass) that follows its core's tier
 * -- see {@link SearedTier}. No block entity; the tier is a blockstate property.
 */
public class TieredSearedBlock extends Block {
    public TieredSearedBlock(Properties properties) {
        super(properties);
        registerDefaultState(SearedTier.standard(stateDefinition.any()));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SearedTier.TIER);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SearedTier.flip(state, level, pos);
    }
}
