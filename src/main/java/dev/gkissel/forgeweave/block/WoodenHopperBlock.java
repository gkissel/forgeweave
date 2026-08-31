package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Wooden Hopper (docs/SCOPE.md M5, issue #822), ported from upstream 1.12's {@code
 * BlockWoodenHopper} (NOTICE.md). Everything about a vanilla hopper -- shape, redstone locking,
 * the vanilla {@code HopperMenu} GUI, facing/placement -- carries over unmodified from {@link
 * HopperBlock}; the only two overrides here exist to route block-entity creation and ticking to
 * {@link WoodenHopperBlockEntity} instead of vanilla's own {@link HopperBlockEntity}, which is
 * where the actual half-speed behavior lives.
 *
 * <p>Two intentional deviations from upstream 1.12's own {@code BlockWoodenHopper}, both called out
 * on issue #822: upstream disables redstone response entirely ("no redstone"), and ships a
 * hand-rolled, larger collision box ("Quitely stolen from RWTemas Diet Hoppers"). This port keeps
 * vanilla {@link HopperBlock}'s redstone locking and shape instead, since the wooden hopper is
 * meant to be a vanilla hopper in every respect except transfer speed.
 */
public class WoodenHopperBlock extends HopperBlock {
    // HopperBlock#codec() is declared as MapCodec<HopperBlock> rather than MapCodec<? extends
    // HopperBlock>, so an override can't narrow it to MapCodec<WoodenHopperBlock> (generics are
    // invariant) -- CODEC keeps HopperBlock's own type, matching the super method it overrides.
    public static final MapCodec<HopperBlock> CODEC = simpleCodec(WoodenHopperBlock::new);

    public WoodenHopperBlock(Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<HopperBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WoodenHopperBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return level.isClientSide ? null
                : createTickerHelper(blockEntityType, ForgeweaveBlockEntities.WOODEN_HOPPER.get(), HopperBlockEntity::pushItemsTick);
    }
}
