package dev.gkissel.forgeweave.tool;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.item.ToolItem;

/**
 * The scythe's right-click harvest (docs/SCOPE.md M3 issue #157), ported from upstream 1.12's
 * {@code tools/tools/Kama.java} -- {@code #onItemRightClick}, {@code #canHarvestCrop} and
 * {@code #doHarvestCrop} -- which the scythe inherits and widens to the same 3x3x3 its area mining
 * covers (NOTICE.md).
 *
 * <h2>What "harvest" means here, exactly as upstream defines it</h2>
 *
 * <ul>
 *   <li>Only <b>mature</b> crops and nether wart, and sugar cane above its own base -- an immature
 *       crop is left alone rather than broken, which is the whole point of harvesting with a tool
 *       instead of punching the field.
 *   <li>One seed from the drops goes back into the ground and the rest drop: upstream pulls the
 *       first {@code IPlantable} out of the drop list and re-plants it. 1.21's equivalent of "the
 *       thing this block is grown from" is {@code Block#getCloneItemStack}, which is what
 *       {@link #replant} matches against.
 *   <li>Anything harvestable that cannot be re-planted (sugar cane) is simply broken and dropped.
 *   <li>Each harvested block costs the tool 1 durability, upstream's own
 *       {@code ToolHelper.damageTool(stack, 1, player)}.
 * </ul>
 *
 * <p>ponytail: no shearing of blocks or entities (upstream's kama also shears sheep and leaves).
 * Leaves already come off through the scythe's 3x3x3 area mining and its {@code mineable/hoe} tag;
 * shears-on-entities is its own behavior with its own art and sounds, and no issue asks for it.
 */
public final class CropHarvest {

    /**
     * Harvests every mature crop in the 3x3x3 around the clicked block, origin included. Runs the
     * same scan on both sides so the client's arm swings exactly when the server harvests, but only
     * mutates the world server side (upstream's {@code harvestCrop}/{@code doHarvestCrop} split).
     */
    public static InteractionResult harvestAround(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        ItemStack tool = context.getItemInHand();
        if (player == null) {
            return InteractionResult.PASS;
        }
        BlockPos origin = context.getClickedPos();
        boolean harvested = false;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (!ToolItem.isBroken(tool)) {
                        harvested |= harvest(tool, level, player, origin.offset(x, y, z));
                    }
                }
            }
        }
        if (!harvested) {
            return InteractionResult.PASS;
        }
        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                player.getSoundSource(), 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /**
     * The same harvest over the clicked block alone -- the kama's (issue #156, upstream's own
     * {@code Kama#onItemRightClick}, which the scythe's {@link #harvestAround} is the 3x3x3 form of).
     * Shares every rule with it: what counts as harvestable, the replant-or-break, and one durability
     * per crop taken.
     */
    public static InteractionResult harvestAt(UseOnContext context) {
        Player player = context.getPlayer();
        Level level = context.getLevel();
        if (player == null || !harvest(context.getItemInHand(), level, player, context.getClickedPos())) {
            return InteractionResult.PASS;
        }
        level.playSound(player, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP,
                player.getSoundSource(), 1.0F, 1.0F);
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Whether {@code pos} held something this harvests -- true on both sides, mutating on neither client. */
    private static boolean harvest(ItemStack tool, Level level, Player player, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!isHarvestable(level, pos, state)) {
            return false;
        }
        if (level instanceof ServerLevel serverLevel) {
            List<ItemStack> drops = Block.getDrops(state, serverLevel, pos, null, player, tool);
            boolean replanted = replant(serverLevel, pos, state, drops);
            if (!replanted) {
                serverLevel.destroyBlock(pos, false);
            }
            for (ItemStack drop : drops) {
                Block.popResource(serverLevel, pos, drop);
            }
            // Upstream's `crop != state` guard: a mid-stalk sugar cane segment replants to the exact
            // state it already had (its AGE growth-timer resets to the same 0 it started at), so
            // nothing actually changed and the swing is free. A replanted wheat/nether wart always
            // moves to a different age, so this never skips their charge.
            if (!replanted || !state.equals(state.getBlock().defaultBlockState())) {
                tool.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            }
            // M7-3 (issue #920, docs/SCOPE.md D-M7-10): upstream's onScythe grant, ModToolLeveling
            // (Tinkers' Tool Leveling, MIT, NOTICE.md) -- per harvested block, so both the kama's
            // single-block harvest and the scythe's 3x3x3 loop grant once each, same as upstream's
            // per-block TinkerToolEvent.OnScytheHarvest.
            if (player instanceof ServerPlayer serverPlayer) {
                ToolLeveling.addXp(tool, 1, serverPlayer);
            }
        }
        return true;
    }

    /** Upstream's {@code Kama#canHarvestCrop}, plus its "only cane above cane" rule. */
    private static boolean isHarvestable(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (state.getBlock() instanceof NetherWartBlock) {
            return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE;
        }
        // Cane's own base stays planted, so the stalk regrows rather than having to be replanted.
        return state.getBlock() instanceof SugarCaneBlock
                && level.getBlockState(pos.below()).getBlock() instanceof SugarCaneBlock;
    }

    /**
     * Puts one seed back in the ground and takes it out of {@code drops}, upstream's
     * {@code doHarvestCrop}. Returns false when this block has no seed among its drops, which is the
     * signal to break it instead.
     */
    private static boolean replant(ServerLevel level, BlockPos pos, BlockState state, List<ItemStack> drops) {
        ItemStack seed = state.getBlock().getCloneItemStack(level, pos, state);
        if (seed.isEmpty()) {
            return false;
        }
        for (ItemStack drop : drops) {
            if (drop.is(seed.getItem())) {
                drop.shrink(1);
                drops.removeIf(ItemStack::isEmpty);
                level.setBlockAndUpdate(pos, state.getBlock().defaultBlockState());
                return true;
            }
        }
        return false;
    }

    private CropHarvest() {}
}
