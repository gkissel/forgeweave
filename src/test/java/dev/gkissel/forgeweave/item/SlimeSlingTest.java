package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;

/**
 * Parity audit T22 (issue #453). The charge-up arithmetic upstream 1.12's
 * {@code gadgets/item/ItemSlimeSling#onPlayerStoppedUsing} copies off the vanilla bow, its 6.0 cap,
 * and the item properties around it ({@code EnumAction.BOW}, a 72000-tick use, stack size 1). The
 * fling itself -- ground check, block-target check, inverted look vector, velocity resync -- needs a
 * real level and is pinned by {@code SlimeSlingGameTests}.
 */
class SlimeSlingTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Upstream, verbatim:
     *
     * <pre>
     *   int i = this.getMaxItemUseDuration(stack) - timeLeft;
     *   float f = i / 20.0F;
     *   f = (f * f + f * 2.0F) / 3.0F;
     *   f *= 4f;
     *   if(f &gt; 6f) f = 6f;
     * </pre>
     */
    @Test
    void theChargeCurveIsTheBowsCurveTimesFour() {
        assertEquals(0.0F, SlimeSlingItem.launchForce(0), 1.0E-6F);
        assertEquals(1.6666667F, SlimeSlingItem.launchForce(10), 1.0E-5F); // half a second of charge
        assertEquals(4.0F, SlimeSlingItem.launchForce(20), 1.0E-6F); // one second of charge
        assertEquals(5.4166667F, SlimeSlingItem.launchForce(25), 1.0E-5F);
    }

    /** Past ~1.35s the curve would pass 6; upstream clamps there and so does this. */
    @Test
    void theChargeIsCappedAtSix() {
        assertEquals(6.0F, SlimeSlingItem.launchForce(30), 1.0E-6F);
        assertEquals(6.0F, SlimeSlingItem.launchForce(72000), 1.0E-6F);
    }

    @Test
    void theSlingChargesLikeABowAndDoesNotStack() {
        ItemStack stack = new ItemStack(ForgeweaveItems.SLIME_SLING.get());

        assertSame(UseAnim.BOW, stack.getItem().getUseAnimation(stack));
        assertEquals(72000, stack.getItem().getUseDuration(stack, null));
        assertEquals(1, stack.getMaxStackSize());
    }

    /** Upstream's {@code item.tconstruct.slimesling.tooltip}, both of its lines. */
    @Test
    void theSlingCarriesItsGreyFlavourLine() {
        ItemStack stack = new ItemStack(ForgeweaveItems.SLIME_SLING.get());
        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        assertEquals(
                List.of(Component.translatable("tooltip.forgeweave.slime_sling").withStyle(ChatFormatting.GRAY),
                        Component.translatable("tooltip.forgeweave.slime_sling.boots")
                                .withStyle(ChatFormatting.GRAY)),
                tooltip);
    }
}
