package dev.gkissel.forgeweave.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

/**
 * The Nether Core: a {@link SmelteryControllerBlock} that also carries {@link #HOT}, the "v2" look
 * it wears while the smeltery burns fuel hotter than {@link #HOT_TEMPERATURE} (maintainer request,
 * 2026-09-06). A subclass rather than a per-core branch because a block's state definition is built
 * inside {@code Block}'s constructor, before the core field exists.
 */
public class NetherCoreBlock extends SmelteryControllerBlock {
    private static final MapCodec<NetherCoreBlock> CODEC = simpleCodec(NetherCoreBlock::new);

    public NetherCoreBlock(Properties properties) {
        super(properties, SmelteryCore.NETHER);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false).setValue(HOT, false));
    }

    @Override
    protected MapCodec<? extends SmelteryControllerBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HOT);
    }
}
