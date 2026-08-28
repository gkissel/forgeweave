package dev.gkissel.forgeweave.item;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A single-use clay cast (docs/SCOPE.md M3.4-12, issue #292): moulded from molten clay instead of
 * molten gold and eaten by the pour that uses it, upstream 1.12's {@code TinkerSmeltery.clayCast}.
 * The mid-game bridge to the gold casts, which survive their own casting cycle.
 *
 * <p>Its only behaviour beyond plain {@link Item} is the hover line added by issue #783's audit --
 * neither this nor {@link ForgeweaveItems#CAST_INGOT}'s reusable siblings carried one before, so the
 * item explained neither what it was for nor that it breaks after one pour -- and the class marker
 * so the two halves of the clay system can be recognised without a registry lookup:
 * {@link dev.gkissel.forgeweave.casting.CastingRecipe} filters every recipe that moulds one or casts
 * through one while upstream's {@code enableClayCasts} option
 * ({@link dev.gkissel.forgeweave.config.ForgeweaveConfig#ENABLE_CLAY_CASTS}) is off, which is where
 * upstream instead skips their registration entirely.
 */
public class ClayCastItem extends Item {
    public ClayCastItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.forgeweave.clay_cast").withStyle(ChatFormatting.GRAY));
    }

    /** Whether {@code stack} is one of the clay casts (see {@link ForgeweaveItems#CLAY_CASTS}). */
    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof ClayCastItem;
    }
}
