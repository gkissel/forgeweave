package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import dev.gkissel.forgeweave.sound.ForgeweaveSounds;

/**
 * The Slimesling (parity audit T22, issue #453) -- upstream 1.12's
 * {@code gadgets/item/ItemSlimeSling} (NOTICE.md). Charge it like a bow while standing on the
 * ground, aim at a block, and release: the player is flung along the <em>inverted</em> look vector,
 * at {@link #VERTICAL_SCALE} of that force vertically and {@link #HORIZONTAL_SCALE} horizontally, and {@link SlimeBounceHandler} keeps the momentum through the
 * flight.
 *
 * <p>Upstream ships one sling per slime colour ({@code SlimeType} metadata subtypes, all named
 * "Slimesling" bar the blood one) and never reads the colour outside naming, so every colour of
 * {@code ForgeweaveItems#slimeSlings()} (#649) is this same item; only the recipe, name and tinted
 * sprite differ.
 */
public class SlimeSlingItem extends Item {

    /** Upstream's {@code getMaxItemUseDuration}: the vanilla bow's "hold as long as you like". */
    private static final int USE_DURATION = 72000;

    /** Upstream's {@code if(f > 6f) f = 6f;} -- roughly 1.35s of charge reaches it. */
    public static final float MAX_FORCE = 6.0F;

    /**
     * Upstream's {@code x * -f, y * -f / 3f, z * -f} -- horizontal at the full force, vertical at a
     * third of it. Shared by every coloured sling.
     *
     * <p>Issue #698 (beta.1 checklist §16) had replaced these with a maintainer-tuned -15 % horizontal
     * / +60 % vertical pair (0.85 and 1.6/3). Issue #902 (playtest: "vertical launch hits a strange
     * cap") supersedes that decision and restores upstream's 1:1 values. Per CLAUDE.md's 1.12-parity
     * directive, non-1:1 constants are a deviation that needs an active maintainer decision behind
     * them, and #902 found nothing in #698's own tuning math that explains a vertical-specific cap
     * (its retune, if anything, raised the vertical ceiling from {@code MAX_FORCE / 3 = 2.0} to
     * {@code MAX_FORCE * 1.6 / 3 = 3.2}). The more concrete candidate this class's own
     * {@link #releaseUsing} Javadoc already documents: {@code releaseUsing} relies on vanilla's
     * {@code hurtMarked} / {@code ClientboundSetEntityMotionPacket} path to push the server's velocity
     * to the client, and that packet clamps every axis to +-3.9 blocks/tick (Mojang's short-encoded
     * velocity format). Upstream never had that ceiling because {@code EntityMovementChangePacket}
     * (NOTICE.md) sent raw, unclamped doubles instead -- which is exactly why it existed. At the
     * now-restored 1:1 scale a full-charge horizontal launch (6.0) still exceeds 3.9; a second, separate
     * commit on this same PR removes the packet ceiling itself by no longer setting
     * {@code entity.hurtMarked} in {@link #releaseUsing} -- see that method's Javadoc.
     */
    public static final float HORIZONTAL_SCALE = 1.0F;
    public static final float VERTICAL_SCALE = 1.0F / 3.0F;

    public SlimeSlingItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /**
     * The vanilla bow's charge curve, scaled by four and clamped -- upstream's own "copy chargeup
     * code from bow \o/". {@code useTicks} is how long the sling was held.
     */
    public static float launchForce(int useTicks) {
        float f = useTicks / 20.0F;
        f = (f * f + f * 2.0F) / 3.0F;
        f *= 4.0F;
        return Math.min(f, MAX_FORCE);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    /**
     * Upstream's {@code onPlayerStoppedUsing}: nothing happens unless the player is on the ground and
     * looking at a block within reach; otherwise they are flung away from what they aimed at.
     *
     * <p>Runs on both sides, exactly as upstream's does -- vanilla calls {@code stopUsingItem} on the
     * client (releasing the use key locally predicts the fling for the local player) and independently
     * on the server (the {@code RELEASE_USE_ITEM} action packet), so {@code player.push} already runs once
     * per side against that side's own {@code Player} instance. Issue #902 ("vertical launch hits a
     * strange cap") found that this class used to also set {@code entity.hurtMarked = true}, which made
     * vanilla additionally broadcast a {@code ClientboundSetEntityMotionPacket} -- and that packet
     * clamps every axis to a hard +-3.9 blocks/tick (Mojang's short-encoded velocity wire format) before
     * overwriting the client's own already-correct, already-applied velocity with the clamped copy.
     * Upstream 1.12 never had that ceiling: its {@code EntityMovementChangePacket} (NOTICE.md) sent raw
     * doubles with no clamp, and only to the launching {@code EntityPlayerMP}, not broadcast to every
     * observer. We don't need a custom packet to match that -- the client-side push above is already
     * the source of truth for the local player, so simply not setting {@code hurtMarked} means nothing
     * ever ships a clamped correction to overwrite it. {@link SlimeBounceHandler#addBounceHandler} still
     * registers per side exactly as before, unaffected by this.
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player) || !player.onGround()) {
            return;
        }
        BlockHitResult target = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (target.getType() != HitResult.Type.BLOCK) {
            return;
        }

        float force = launchForce(USE_DURATION - timeLeft);
        Vec3 look = player.getLookAngle().normalize();
        player.push(look.x * -force * HORIZONTAL_SCALE, look.y * -force * VERTICAL_SCALE,
                look.z * -force * HORIZONTAL_SCALE);
        player.playSound(ForgeweaveSounds.SLIME_SLING.get(), 1.0F, 1.0F);
        SlimeBounceHandler.addBounceHandler(player);
    }

    /** Upstream's {@code item.tconstruct.slimesling.tooltip}, both lines. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.forgeweave.slime_sling").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.forgeweave.slime_sling.boots").withStyle(ChatFormatting.GRAY));
    }
}
