package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;

/**
 * The item {@code ForgeweaveTraits#DUSKSNARE} snares a mob into (issue #886), and the only way a
 * cage is ever obtained -- it is never crafted and never empty. Right-clicking a block lets the mob
 * back out at the clicked face with the NBT it was captured with, and <b>consumes</b> the cage:
 * one-shot rather than an emptied-and-reusable container, so there is no second, empty item state to
 * name, model or explain (an empty cage would do nothing anyway -- the snare makes filled ones, it
 * does not fill given ones).
 */
public class DuskCageItem extends Item {

    public DuskCageItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    /** Cages never stack anyway (their payloads differ), but the name has to name the captive. */
    @Override
    public Component getName(ItemStack stack) {
        CapturedMob captured = stack.get(ForgeweaveDataComponents.CAPTURED_MOB.get());
        return captured == null
                ? super.getName(stack)
                : Component.translatable("item.forgeweave.dusk_cage.filled", captured.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CapturedMob captured = stack.get(ForgeweaveDataComponents.CAPTURED_MOB.get());
        Component line = captured == null
                ? Component.translatable("tooltip.forgeweave.dusk_cage.empty")
                : Component.translatable("tooltip.forgeweave.dusk_cage", captured.name());
        tooltip.add(line.copy().withStyle(ChatFormatting.GRAY));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        ItemStack stack = context.getItemInHand();
        CapturedMob captured = stack.get(ForgeweaveDataComponents.CAPTURED_MOB.get());
        if (captured == null) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        Vec3 pos = Vec3.atBottomCenterOf(context.getClickedPos().relative(context.getClickedFace()));
        float yRot = context.getPlayer() == null ? 0.0F : context.getPlayer().getYRot();
        if (captured.release(level, pos, yRot).isEmpty()) {
            return InteractionResult.FAIL;
        }
        stack.shrink(1);
        return InteractionResult.CONSUME;
    }
}
