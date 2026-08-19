package dev.gkissel.forgeweave.tool;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import dev.gkissel.forgeweave.item.ToolItem;

/**
 * Log stripping, copper scraping and wax removal for the axe family (issue #575, follow-up to the
 * parity audit's T33 / issue #464).
 *
 * <p><strong>This is a deliberate deviation from 1.12</strong>, the only one in this file: all three
 * mechanics postdate that generation entirely -- stripping arrived in 1.13, copper in 1.17 -- so
 * there is no upstream 1.12 behavior to port and no 1.12 parity question to answer. What decides it
 * instead is how upstream itself adapted the axe once the mechanics existed: 1.20's
 * {@code ModifierProvider} builds a {@code stripping} trait out of three
 * {@code ToolActionTransformModule}s -- {@code AXE_STRIP} with {@code SoundEvents.AXE_STRIP},
 * {@code AXE_SCRAPE} with {@code AXE_SCRAPE} plus {@code LevelEvent.PARTICLES_SCRAPE}, and
 * {@code AXE_WAX_OFF} with {@code AXE_WAX_OFF} plus {@code PARTICLES_WAX_OFF} -- and hands it to its
 * {@code HAND_AXE} and {@code BROAD_AXE} definitions. Forgeweave's axe family is those two tools plus
 * the battleaxe, so all three carry it here; the mattock does not, matching 1.20's mattock definition,
 * which carries {@code tilling} and no other interaction trait.
 *
 * <p>The transform itself is vanilla 1.21's own {@code AxeItem#useOn}/{@code evaluateNewBlockState}
 * verbatim but for the tool it damages -- the same relationship {@code ShovelPath#flattenOne} has to
 * {@code ShovelItem#useOn}. Going through {@link BlockState#getToolModifiedState}
 * rather than a Forgeweave-side block table means a modded strippable block works with a Forgeweave
 * hatchet exactly as it does with a vanilla axe. Vanilla's shield check comes along with it: an axe in
 * the main hand and a shield in the off hand, not sneaking, means the player wants to raise the
 * shield, so nothing is transformed.
 *
 * <p>Like {@link ShovelPath}, this also spreads over the tool's AoE area (issue #617): 1.20's {@code
 * BlockTransformModule#afterBlockUse} does the same, through a transform-specific iterator --
 * {@code AOEMatchType.TRANSFORM} rather than the mining one {@link AoeHarvest#extraBlocks} answers,
 * which would silently exclude every copper block from an area scrape, copper being {@code
 * mineable/pickaxe} rather than {@code mineable/axe}. {@link AoeHarvest#extraTransformBlocks} is that
 * split ported: the hatchet and battleaxe are {@link AoeHarvest.Shape#SINGLE} and {@link
 * AoeHarvest.Shape#NONE}, so neither takes anything extra without an expander; the lumber axe is
 * {@link AoeHarvest.Shape#TREE_FELL}, so a strip on a tree's trunk strips the whole tree -- upstream's
 * own broad-axe behavior, and what makes this whole issue worth doing.
 */
public final class AxeStrip {

    /** Upstream 1.20's three {@code stripping} modules, in vanilla {@code AxeItem}'s own order. */
    private record Transform(ItemAbility ability, SoundEvent sound, int particles) {}

    private static final int NO_PARTICLES = -1;

    private static final List<Transform> TRANSFORMS = List.of(
            new Transform(ItemAbilities.AXE_STRIP, SoundEvents.AXE_STRIP, NO_PARTICLES),
            new Transform(ItemAbilities.AXE_SCRAPE, SoundEvents.AXE_SCRAPE, LevelEvent.PARTICLES_SCRAPE),
            new Transform(ItemAbilities.AXE_WAX_OFF, SoundEvents.AXE_WAX_OFF, LevelEvent.PARTICLES_WAX_OFF));

    /**
     * Transforms the block clicked and, following {@code aoeShape}, everything else the tool's AoE
     * takes along (issue #617). The caller has already refused a Broken tool; a tool that breaks
     * mid-area stops the area, matching {@link ShovelPath#flattenAt}.
     */
    public static InteractionResult transformAt(UseOnContext context, AoeHarvest.Shape aoeShape) {
        Level level = context.getLevel();
        BlockPos origin = context.getClickedPos();
        Player player = context.getPlayer();
        if (wantsToRaiseAShield(context, player)) {
            return InteractionResult.PASS;
        }
        InteractionResult result = transformOne(context);
        if (player == null) {
            return result;
        }
        ItemStack stack = context.getItemInHand();
        BlockState originState = level.getBlockState(origin);
        for (BlockPos pos : AoeHarvest.extraTransformBlocks(stack, level, player, origin, originState, aoeShape)) {
            if (ToolItem.isBroken(stack)) {
                break;
            }
            InteractionResult extra = transformOne(contextAt(context, pos));
            if (!result.consumesAction()) {
                result = extra;
            }
        }
        return result;
    }

    /**
     * Strips, scrapes or wipes one block -- the first of the three transforms that applies -- for one
     * durability, or {@link InteractionResult#PASS} if none does. Issue #575's original body, now
     * {@link #transformAt}'s per-block step.
     */
    private static InteractionResult transformOne(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        BlockState state = level.getBlockState(pos);
        for (Transform transform : TRANSFORMS) {
            BlockState transformed = state.getToolModifiedState(context, transform.ability(), false);
            if (transformed == null) {
                continue;
            }
            level.playSound(player, pos, transform.sound(), SoundSource.BLOCKS, 1.0F, 1.0F);
            if (transform.particles() != NO_PARTICLES) {
                level.levelEvent(player, transform.particles(), pos, 0);
            }
            if (!level.isClientSide) {
                level.setBlock(pos, transformed, 11);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, transformed));
                if (player != null) {
                    context.getItemInHand().hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }

    /** Vanilla {@code AxeItem#playerHasShieldUseIntent}, with the null player a GameTest can hand us. */
    private static boolean wantsToRaiseAShield(UseOnContext context, Player player) {
        return player != null && context.getHand() == InteractionHand.MAIN_HAND
                && player.getOffhandItem().is(Items.SHIELD) && !player.isSecondaryUseActive();
    }

    /** The same click, moved to another block of the area: same hand, same stack, same face. */
    private static UseOnContext contextAt(UseOnContext origin, BlockPos pos) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), origin.getClickedFace(), pos, false);
        return new UseOnContext(origin.getLevel(), origin.getPlayer(), origin.getHand(), origin.getItemInHand(), hit);
    }

    private AxeStrip() {}
}
