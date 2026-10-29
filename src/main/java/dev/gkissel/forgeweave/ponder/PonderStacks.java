package dev.gkissel.forgeweave.ponder;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.data.loading.DatagenModLoader;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * The item stacks the storyboards show. Ponder's lang datagen compiles every storyboard during
 * runData to pull out its text, and on 26.1 a data run never binds item components, so building a
 * stack there throws. The stacks are only ever drawn, so a data run gets empty ones.
 */
final class PonderStacks {

    static ItemStack of(ItemLike item) {
        return DatagenModLoader.isRunningDataGen() ? ItemStack.EMPTY : new ItemStack(item);
    }

    /** A part stack made of {@code material}. */
    static ItemStack of(ItemLike item, Identifier material) {
        ItemStack stack = of(item);
        if (!stack.isEmpty()) {
            stack.set(ForgeweaveDataComponents.MATERIAL.get(), material);
        }
        return stack;
    }

    private PonderStacks() {}
}
