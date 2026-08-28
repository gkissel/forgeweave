package dev.gkissel.forgeweave.modifier;

import java.util.Arrays;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.modifier.ModifierApplication.Outcome;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Refilling a piece's overslime at the Tool Station (issue #728) -- the 1.20 clone's
 * {@code OverslimeModifierRecipe}, whose {@code restore_amount} per slime item is here the
 * {@code units} of {@code modifier_recipe/overslime.json}'s reagents. That recipe names
 * {@link #ID} as its modifier, which is a marker like fortification's: nothing is ever stored under
 * it on the piece -- {@link ModifierApplication#resolve} hands the pooled slots to {@link #apply},
 * which only moves {@code forgeweave:overslime}. The clone's recipe also <em>adds</em> the overslime
 * modifier to a tool that lacks it; Forgeweave's overslime is a trait (knightslime's ARMOR row) and
 * armor only for now, so a piece without the trait refuses instead.
 */
public final class OverslimeRefill {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "overslime");

    /** The arithmetic's result: the new amount and the items spent per slot. */
    public record Fill(int amount, int[] used) {}

    private OverslimeRefill() {}

    /**
     * {@code OverslimeModifierRecipe#getValidatedResult} + {@code updateInputs}: as many items as the
     * missing amount needs, slot by slot, rounded up -- the overshoot is wasted, as the clone wastes
     * it -- and the amount clamps at the capacity.
     */
    public static Fill fill(int amount, int capacity, int[] available, int[] unitsPerItem) {
        int[] used = new int[available.length];
        for (int i = 0; i < available.length && amount < capacity; i++) {
            if (available[i] <= 0 || unitsPerItem[i] <= 0) {
                continue;
            }
            int missing = capacity - amount;
            used[i] = Math.min(available[i], (missing + unitsPerItem[i] - 1) / unitsPerItem[i]);
            amount = Math.min(capacity, amount + used[i] * unitsPerItem[i]);
        }
        return new Fill(amount, used);
    }

    /** The station outcome: the refilled copy, or the clone's at-capacity refusal / no-trait refusal. */
    public static Outcome apply(ItemStack tool, int[] available, int[] unitsPerItem) {
        int capacity = ForgeweaveTraits.overslimeCapacity(tool);
        if (capacity <= 0) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.overslime_unsupported"));
        }
        int current = ForgeweaveTraits.overslime(tool);
        if (current >= capacity) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.overslime_full"));
        }
        Fill fill = fill(current, capacity, available, unitsPerItem);
        ItemStack result = tool.copy();
        ForgeweaveTraits.setOverslime(result, fill.amount());
        return Outcome.applied(result, Arrays.stream(fill.used()).boxed().toList());
    }
}
