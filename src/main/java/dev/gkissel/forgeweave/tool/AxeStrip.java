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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

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
 * <p>Unlike {@link ShovelPath} this covers the clicked block alone, not the tool's AoE area. 1.20's
 * {@code BlockTransformModule} does spread over the AoE, but through a transform-specific iterator;
 * {@link AoeHarvest#extraBlocks} is a <em>mining</em> filter and rejects anything the tool is not
 * correct for -- which would silently exclude every copper block from a scrape, since copper is
 * {@code mineable/pickaxe}. Issue #617 tracks the AoE half.
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
     * Strips, scrapes or wipes the clicked block -- the first of the three that applies -- for one
     * durability, or {@link InteractionResult#PASS} if none does. The caller has already refused a
     * Broken tool.
     */
    public static InteractionResult transformAt(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        if (wantsToRaiseAShield(context, player)) {
            return InteractionResult.PASS;
        }
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

    private AxeStrip() {}
}
