package dev.gkissel.forgeweave.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * A single-use clay cast (docs/SCOPE.md M3.4-12, issue #292): moulded from molten clay instead of
 * molten gold and eaten by the pour that uses it, upstream 1.12's {@code TinkerSmeltery.clayCast}.
 * The mid-game bridge to the gold casts, which survive their own casting cycle.
 *
 * <p>Plain {@link Item} behaviour -- the class exists only so the two halves of the clay system can
 * be recognised without a registry lookup: {@link dev.gkissel.forgeweave.casting.CastingRecipe}
 * filters every recipe that moulds one or casts through one while upstream's {@code enableClayCasts}
 * option ({@link dev.gkissel.forgeweave.config.ForgeweaveConfig#ENABLE_CLAY_CASTS}) is off, which is
 * where upstream instead skips their registration entirely.
 */
public class ClayCastItem extends Item {
    public ClayCastItem(Properties properties) {
        super(properties);
    }

    /** Whether {@code stack} is one of the clay casts (see {@link ForgeweaveItems#CLAY_CASTS}). */
    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof ClayCastItem;
    }
}
