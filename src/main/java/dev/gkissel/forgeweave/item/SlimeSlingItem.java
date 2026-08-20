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
 * at a third of that force vertically, and {@link SlimeBounceHandler} keeps the momentum through the
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
     * <p>Runs on both sides, exactly as upstream's does -- the client moves its own player right away
     * (player movement is client-authoritative), and the server's {@code hurtMarked} makes vanilla
     * send that player a {@code ClientboundSetEntityMotionPacket} to reconcile. That flag is the
     * modern stand-in for upstream's hand-rolled {@code EntityMovementChangePacket}, which existed
     * only because 1.12 had no such hook on the player's own tracker.
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
        player.push(look.x * -force, look.y * -force / 3.0F, look.z * -force);
        player.hurtMarked = true;
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
