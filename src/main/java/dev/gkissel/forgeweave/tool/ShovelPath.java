package dev.gkissel.forgeweave.tool;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.ItemAbilities;

import dev.gkissel.forgeweave.item.ToolItem;

/**
 * Grass paths for the shovel and the excavator (parity audit 2026-08-18 T33, issue #464), upstream
 * 1.12 {@code tools/tools/Shovel.java#onItemUse} (NOTICE.md) -- the excavator inherits it by
 * extending {@code Shovel}, which is why the shape the area comes from is a parameter here.
 *
 * <p>Upstream's method is three steps, kept in the same order:
 *
 * <ol>
 *   <li>{@code Items.DIAMOND_SHOVEL.onItemUse(...)} on the block clicked -- i.e. "do whatever
 *       vanilla's own shovel would do here". 1.21 spells that {@code BlockState#getToolModifiedState}
 *       with {@link ItemAbilities#SHOVEL_FLATTEN} (and its sibling {@code SHOVEL_DOUSE}, which 1.12
 *       had no counterpart for -- campfires did not exist), so {@link #flattenOne} is vanilla's own
 *       {@code ShovelItem#useOn} body with this tool's durability accounting in place of vanilla's.
 *       Going through the ability rather than a Forgeweave-side block table means a modded flattenable
 *       block works with a Forgeweave shovel exactly as it does with a vanilla one -- the same reason
 *       {@code MattockItem} tills through {@code HOE_TILL}.
 *   <li>"only do the AOE path if the selected block is grass or grass path" -- upstream reads that
 *       block <em>after</em> the first flatten, so a grass block that just became a path still
 *       qualifies. Read at the same point here, against 1.21's names for the same two blocks.
 *   <li>the same flatten again over {@code getAOEBlocks}, stopping the moment the tool breaks. That
 *       list is {@link AoeHarvest#extraBlocks}: upstream's {@code getAOEBlocks} is the identical
 *       {@code calcAOEBlocks} call the tool's mining path already uses (1x1x1 for the shovel, so
 *       nothing extra without an expander; 3x3x1 for the excavator), which is exactly what
 *       {@link AoeHarvest.Shape#SINGLE} and {@link AoeHarvest.Shape#PLANE_3X3} already are, expanders
 *       included. Its one addition over upstream's own path list is the hardness-ratio filter
 *       {@code calcAOEBlocks} leaves to break time, which is inert here: everything flattenable is
 *       softer than the path it turns into.
 * </ol>
 *
 * <p>Upstream returns the first {@code SUCCESS} it sees, falling back to a later block's result only
 * while it has none, and fires its {@code OnShovelMakePath} event per flattened block. Forgeweave has
 * no event bus of its own for tool hooks (ADR-0005), so that half has no counterpart.
 */
public final class ShovelPath {

    /**
     * Flattens the block clicked and, if it was grass or a path, everything {@code shape} takes along.
     * The caller has already refused a Broken tool; a tool that breaks mid-area stops the area.
     */
    public static InteractionResult flattenAt(UseOnContext context, AoeHarvest.Shape shape) {
        Level level = context.getLevel();
        BlockPos origin = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        InteractionResult result = flattenOne(context);

        Block flattened = level.getBlockState(origin).getBlock();
        Player player = context.getPlayer();
        if (player == null || (flattened != Blocks.GRASS_BLOCK && flattened != Blocks.DIRT_PATH)) {
            return result;
        }
        for (BlockPos pos : AoeHarvest.extraBlocks(stack, level, player, origin, level.getBlockState(origin), shape)) {
            if (ToolItem.isBroken(stack)) {
                break;
            }
            InteractionResult extra = flattenOne(contextAt(context, pos));
            if (!result.consumesAction()) {
                result = extra;
            }
        }
        return result;
    }

    /**
     * Vanilla {@code ShovelItem#useOn}, verbatim but for the tool it damages: flatten if the block
     * above is clear, else douse a campfire, else pass -- and never through the block's own bottom
     * face.
     */
    private static InteractionResult flattenOne(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (context.getClickedFace() == Direction.DOWN) {
            return InteractionResult.PASS;
        }
        Player player = context.getPlayer();
        BlockState flattened = state.getToolModifiedState(context, ItemAbilities.SHOVEL_FLATTEN, false);
        BlockState result = null;
        if (flattened != null && level.getBlockState(pos.above()).isAir()) {
            level.playSound(player, pos, SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS, 1.0F, 1.0F);
            result = flattened;
        } else if ((result = state.getToolModifiedState(context, ItemAbilities.SHOVEL_DOUSE, false)) != null) {
            if (!level.isClientSide()) {
                level.levelEvent(null, 1009, pos, 0);
            }
        }
        if (result == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            level.setBlock(pos, result, 11);
            level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, result));
            if (player != null) {
                context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** The same click, moved to another block of the area: same hand, same stack, same face. */
    private static UseOnContext contextAt(UseOnContext origin, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), origin.getClickedFace(), pos, false);
        return new UseOnContext(origin.getLevel(), origin.getPlayer(), origin.getHand(), origin.getItemInHand(), hit);
    }

    private ShovelPath() {}
}
