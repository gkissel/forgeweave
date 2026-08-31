package dev.gkissel.forgeweave.trait;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.tool.AoeHarvest;

/**
 * Breaking one block matching {@code blockPredicate} takes the whole run of matching blocks stacked
 * directly above it in one swing, instead of the one-at-a-time collapse a gravity-affected tower
 * shows a player who mines its base by hand -- ADR-0004's M6 utility/economy library batch (issue
 * #829), {@code cascading}'s own instance gates on {@link net.minecraft.world.level.block.FallingBlock}
 * (sand, gravel, concrete powder, anvils, pointed dripstone, scaffolding, ...), a plain
 * {@code instanceof} against vanilla's own gravity-block marker rather than a new block tag.
 *
 * <p>The actual removal rides {@link AoeHarvest#breakEach}, the same durability/drops/Broken-tool
 * accounting the large tools' own extra blocks already get, per the issue's explicit instruction to
 * reuse that machinery rather than write a third break loop. Only fires for a real
 * {@link ServerPlayer} (the only breaker {@link AoeHarvest#breakEach} knows how to drive), same guard
 * {@link AoeHarvest}'s own extra-block break already applies.
 *
 * <p>{@link #cascading} guards against re-entrancy: {@link AoeHarvest#breakEach} breaks each column
 * block through the same {@code ServerPlayerGameMode#destroyBlock} path the original block went
 * through, which re-fires this very hook for every block it breaks. Without the guard, the second
 * block's own upward scan would find the third block still standing (the outer loop hasn't reached it
 * yet), start a nested cascade for it, and that nested call's own {@code finally} would clear {@link
 * AoeHarvest}'s shared re-entrancy flag out from under the outer call still using it -- so nested
 * calls collect nothing, and the one column collected up front is the one and only break.
 *
 * @param blockPredicate which blocks stack into one cascade
 */
public record CascadingBreak(Predicate<BlockState> blockPredicate) implements Trait {

    /** A runaway pillar never needs more than this many blocks -- {@link AoeHarvest#TREE_LIMIT}'s precedent. */
    private static final int CASCADE_LIMIT = 256;

    /** See the class javadoc. A plain field is enough: every path that touches it runs on the server thread. */
    private static boolean cascading;

    @Override
    public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
            LivingEntity breaker, boolean effective) {
        if (cascading || !blockPredicate.test(state) || !(breaker instanceof ServerPlayer player)) {
            return;
        }
        List<BlockPos> column = new ArrayList<>();
        BlockPos cursor = pos.above();
        while (column.size() < CASCADE_LIMIT && blockPredicate.test(level.getBlockState(cursor))) {
            column.add(cursor);
            cursor = cursor.above();
        }
        if (column.isEmpty()) {
            return;
        }
        cascading = true;
        try {
            AoeHarvest.breakEach(stack, player, column);
        } finally {
            cascading = false;
        }
    }
}
