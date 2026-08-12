package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The kama's right-click harvest+replant (docs/SCOPE.md M3 issue #156, upstream {@code
 * tools/tools/Kama.java#harvestCrop}/{@code #doHarvestCrop}, NOTICE.md). Upstream is generic over
 * every {@code IPlantable} drop; this ports the common case those drops actually cover in
 * Forgeweave's scope -- age-gated {@link CropBlock}s (wheat, carrots, potatoes, beetroot) and
 * {@link NetherWartBlock} replant themselves in place, sugarcane (no maturity state -- {@link
 * SugarCaneBlock}) breaks a segment above the bottom one, upstream's own "do not harvest bottom row
 * reeds" rule, since the bottom segment left standing is what keeps the plant growing back.
 *
 * <p>ponytail: one shared helper rather than a per-crop-type strategy interface -- three cases cover
 * every vanilla crop the clone's own {@code canHarvestCrop} recognizes ({@code BlockCrops}, {@code
 * BlockNetherWart}, {@code BlockReed}); add a case if a future crop shape doesn't fit one of these
 * three block classes.
 */
final class CropHarvest {

    /**
     * Whether the block at {@code pos} is a mature crop the kama can harvest -- a pure read (no
     * world mutation), so {@code KamaItem#useOn} can call it on both sides for the sided-success
     * decision the same way {@code HoeItem#useOn}'s predicate does for tilling.
     */
    static boolean canHarvest(BlockGetter level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        if (state.getBlock() instanceof SugarCaneBlock) {
            return level.getBlockState(pos.below()).getBlock() instanceof SugarCaneBlock;
        }
        return false;
    }

    /**
     * Harvests and replants the mature crop at {@code pos}, or does nothing if {@link #canHarvest}
     * says it isn't one. Returns whether anything happened, so the caller knows whether to damage
     * the tool and play the harvest sound (upstream's {@code harvestedSomething}).
     */
    static boolean harvestAndReplant(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!canHarvest(level, pos, state)) {
            return false;
        }
        if (state.getBlock() instanceof CropBlock crop) {
            return harvestCrop(level, pos, state, crop);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return harvestNetherWart(level, pos, state);
        }
        return harvestSugarCane(level, pos, state);
    }

    private static boolean harvestCrop(ServerLevel level, BlockPos pos, BlockState state, CropBlock crop) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null);
        // The seed that plants this crop, upstream's own "first plantable drop" rule -- a BlockItem
        // for this exact block covers wheat seeds (a distinct item) and carrot/potato (which drop and
        // plant the same item), the two shapes vanilla actually ships.
        for (ItemStack drop : drops) {
            if (drop.getItem() instanceof BlockItem seedItem && seedItem.getBlock() == crop) {
                drop.shrink(1);
                break;
            }
        }
        level.setBlockAndUpdate(pos, crop.defaultBlockState());
        spawnDrops(level, pos, drops);
        return true;
    }

    private static boolean harvestNetherWart(ServerLevel level, BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null);
        level.setBlockAndUpdate(pos, state.setValue(NetherWartBlock.AGE, 0));
        spawnDrops(level, pos, drops);
        return true;
    }

    /** Upstream: only a reed with another reed below it is harvested, so the bottom one regrows it. */
    private static boolean harvestSugarCane(ServerLevel level, BlockPos pos, BlockState state) {
        List<ItemStack> drops = Block.getDrops(state, level, pos, null);
        level.removeBlock(pos, false);
        spawnDrops(level, pos, drops);
        return true;
    }

    private static void spawnDrops(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        for (ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                Block.popResource(level, pos, drop);
            }
        }
    }

    private CropHarvest() {}
}
