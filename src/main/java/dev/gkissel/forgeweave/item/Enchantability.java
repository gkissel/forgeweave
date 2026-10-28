package dev.gkissel.forgeweave.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantable;

import dev.gkissel.forgeweave.material.Material;

import org.jspecify.annotations.Nullable;

/**
 * Keeps Forgeweave's own enchantability component and vanilla's {@code minecraft:enchantable} in step.
 * Since 1.21.5 the enchanting table reads only the vanilla one, and no per-stack hook is left to
 * override, so every write goes through {@link #set} and a stack assembled before the port gets the
 * vanilla component from {@link #backfill} the first time it ticks in an inventory.
 */
public final class Enchantability {

    /**
     * Writes both components. {@code null} clears Forgeweave's and puts vanilla's back to
     * {@link Material#DEFAULT_ENCHANTABILITY}, the value an unset stack enchanted at on 1.21.1.
     */
    public static void set(ItemStack stack, @Nullable Integer value) {
        stack.set(ForgeweaveDataComponents.ENCHANTABILITY.get(), value);
        stack.set(DataComponents.ENCHANTABLE, new Enchantable(value == null ? Material.DEFAULT_ENCHANTABILITY : value));
    }

    /**
     * A tool or armor piece assembled on 1.21.1 carries Forgeweave's component but only the item's
     * default {@code minecraft:enchantable} (14, from its properties); this sets the vanilla one from
     * Forgeweave's. A stack with no Forgeweave component (one from before issue #593) already
     * enchants at the default 14, as it did on 1.21.1. A vanilla value already on the stack is left
     * alone. Returns whether it wrote anything.
     */
    public static boolean backfill(ItemStack stack) {
        Integer value = stack.get(ForgeweaveDataComponents.ENCHANTABILITY.get());
        if (value == null || stack.hasNonDefault(DataComponents.ENCHANTABLE)) {
            return false;
        }
        stack.set(DataComponents.ENCHANTABLE, new Enchantable(value));
        return true;
    }

    private Enchantability() {}
}
