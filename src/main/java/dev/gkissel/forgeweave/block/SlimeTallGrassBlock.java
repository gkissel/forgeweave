package dev.gkissel.forgeweave.block;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Slimy tall grass and its fern variant: upstream 1.12's {@code BlockTallSlimeGrass} (NOTICE.md),
 * the plants the slime island generator scatters over its surface (issue #449, parity audit T18).
 *
 * <p>Upstream keeps both shapes and both foliage colours on one metadata block; modern Minecraft
 * gets one block per (shape, colour) pair, so the colour rides on {@link #foliage()} and the shape
 * on which texture the block's model names. Everything else is upstream's: it can only stand on
 * slime grass or slime dirt ({@code canPlaceBlockAt}), it is replaceable, it drops nothing but
 * itself to shears ({@code getItemDropped} returning null plus {@code IShearable}, loot-table-side
 * here), and it renders on the {@code XYZ} offset upstream's {@code getOffsetType} asks for -- all
 * of which except the placement rule are block properties in modern Minecraft, so they live in
 * {@code ForgeweaveBlocks} rather than here.
 */
public class SlimeTallGrassBlock extends BushBlock {
    // Block codecs only ever re-create a block from its properties (registry dumps); nothing decodes
    // one back into a registered instance, so the foliage the reconstructed block would carry is
    // immaterial. Same shortcut vanilla's own simpleCodec blocks take with their extra constructor
    // arguments.
    public static final MapCodec<SlimeTallGrassBlock> CODEC =
            simpleCodec(properties -> new SlimeTallGrassBlock(properties, FoliageType.BLUE));

    /** Vanilla's own short-grass hitbox; upstream 1.12 inherits {@code BlockBush}'s identical box. */
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);

    private final FoliageType foliage;

    public SlimeTallGrassBlock(Properties properties, FoliageType foliage) {
        super(properties);
        this.foliage = foliage;
    }

    /** The colour this plant is tinted with. */
    public FoliageType foliage() {
        return foliage;
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Vec3 offset = state.getOffset(level, pos);
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    /** Upstream {@code canPlaceBlockAt}: slime grass or slime dirt, and nothing else -- not even vanilla dirt. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return ForgeweaveBlocks.isSlimeSoil(state.getBlock());
    }
}
