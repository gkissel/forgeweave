package dev.gkissel.forgeweave.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Congealed slime: upstream 1.12's {@code BlockSlimeCongealed} (NOTICE.md), the block every slime
 * tree's trunk is built from (issue #449, parity audit T18).
 *
 * <p>It looks like a full cube but only collides to ten sixteenths of one -- upstream's
 * {@code AABB = (0, 0, 0) to (1, 0.625, 1)} -- so anything standing on it sinks in to the ankles.
 * That is the one behaviour that needs a class of its own; its hardness, its halved friction and its
 * slime footstep sound are all block properties, set where it is registered.
 */
public class CongealedSlimeBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 10.0D, 16.0D);

    public CongealedSlimeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }
}
