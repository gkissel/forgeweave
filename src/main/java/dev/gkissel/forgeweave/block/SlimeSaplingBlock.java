package dev.gkissel.forgeweave.block;

import java.util.List;

import com.mojang.serialization.MapCodec;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import dev.gkissel.forgeweave.worldgen.SlimeIslandShape;

/**
 * A slime sapling: upstream 1.12's {@code BlockSlimeSapling} (issue #488, parity audit T57,
 * NOTICE.md). One block per foliage colour, where upstream keeps both on one metadata block.
 *
 * <p>Everything vanilla's {@code SaplingBlock} does is upstream's too -- the two-step {@code STAGE},
 * the light-9 random-tick roll of one in seven, and bone meal advancing a step at 45% -- so this
 * copies that class rather than extending it, for the one thing it cannot express: vanilla saplings
 * grow a {@code TreeGrower}'s configured feature, and a slime tree is not one. Upstream's sapling
 * likewise builds its own {@code SlimeTreeGenerator} inline, with a green congealed-slime trunk, its
 * own foliage's leaves and <em>no</em> vines -- so a hand-planted tree gets upstream's leafy canopy
 * corners rather than the island generator's hanging vines. {@link SlimeIslandShape} already holds
 * that generator, ported for the island feature, so the growth here is one call into it.
 */
public class SlimeSaplingBlock extends BushBlock implements BonemealableBlock {
    public static final MapCodec<SlimeSaplingBlock> CODEC =
            simpleCodec(properties -> new SlimeSaplingBlock(properties, FoliageType.BLUE));

    /** Vanilla's {@code SaplingBlock.STAGE}, which upstream's {@code BlockSapling} carries too. */
    public static final IntegerProperty STAGE = BlockStateProperties.STAGE;

    /** Upstream's {@code tile.tconstruct.slime_sapling.tooltip} -- the placement rule, spelled out. */
    public static final String TOOLTIP_KEY = "tooltip.forgeweave.slime_sapling";

    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 12.0D, 14.0D);

    private final FoliageType foliage;

    public SlimeSaplingBlock(Properties properties, FoliageType foliage) {
        super(properties);
        this.foliage = foliage;
        registerDefaultState(stateDefinition.any().setValue(STAGE, 0));
    }

    /** The colour of the leaves this sapling grows. */
    public FoliageType foliage() {
        return foliage;
    }

    @Override
    protected MapCodec<? extends BushBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STAGE);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    /** Upstream {@code canPlaceBlockAt}: slime grass or slime dirt, and nothing else. */
    @Override
    protected boolean mayPlaceOn(BlockState state, BlockGetter level, BlockPos pos) {
        return ForgeweaveBlocks.isSlimeSoil(state.getBlock());
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getMaxLocalRawBrightness(pos.above()) >= 9 && random.nextInt(7) == 0) {
            advanceTree(level, pos, state, random);
        }
    }

    /** Vanilla's {@code SaplingBlock#advanceTree}: the first step arms the sapling, the second grows it. */
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        if (state.getValue(STAGE) == 0) {
            level.setBlock(pos, state.cycle(STAGE), Block.UPDATE_INVISIBLE);
            return;
        }
        SlimeIslandShape.growSaplingTree(level, random, pos, foliage);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(TOOLTIP_KEY).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return level.random.nextFloat() < 0.45D;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        advanceTree(level, pos, state, random);
    }
}
