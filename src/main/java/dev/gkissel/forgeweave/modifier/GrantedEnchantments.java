package dev.gkissel.forgeweave.modifier;

import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

/**
 * Enchantments a tool gets from its modifiers and traits, stored apart from the ones a table or an
 * anvil put on it. Vanilla's {@code ItemStack#isEnchantable} and Apothic Enchanting's
 * {@code ApothEnchantmentMenu#isEnchantableEnough} both refuse an item with anything in
 * {@code minecraft:enchantments}, so storing Luck's Fortune there kept every lapis tool off the table.
 * {@code ToolItem#getEnchantmentLevel} and {@code #getAllEnchantments} merge these back in, which is
 * where NeoForge's patched {@code EnchantmentHelper} reads a stack's levels.
 */
public final class GrantedEnchantments {

    private GrantedEnchantments() {
    }

    public static ItemEnchantments of(ItemStack stack) {
        return stack.getOrDefault(ForgeweaveDataComponents.GRANTED_ENCHANTMENTS.get(), ItemEnchantments.EMPTY);
    }

    /** Takes the higher of {@code level} and what is already granted, so a grant never walks back. */
    public static void upgrade(ItemStack stack, Holder<Enchantment> enchantment, int level) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(of(stack));
        mutable.upgrade(enchantment, level);
        stack.set(ForgeweaveDataComponents.GRANTED_ENCHANTMENTS.get(), mutable.toImmutable());
    }

    public static void remove(ItemStack stack, Holder<Enchantment> enchantment) {
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(of(stack));
        mutable.set(enchantment, 0);
        ItemEnchantments left = mutable.toImmutable();
        if (left.isEmpty()) {
            stack.remove(ForgeweaveDataComponents.GRANTED_ENCHANTMENTS.get());
        } else {
            stack.set(ForgeweaveDataComponents.GRANTED_ENCHANTMENTS.get(), left);
        }
    }

    /** Stored and granted enchantments together, at the higher level of the two. */
    public static ItemEnchantments withStored(ItemStack stack) {
        ItemEnchantments.Mutable merged = new ItemEnchantments.Mutable(
                stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY));
        ItemEnchantments granted = of(stack);
        for (Holder<Enchantment> enchantment : granted.keySet()) {
            merged.upgrade(enchantment, granted.getLevel(enchantment));
        }
        return merged.toImmutable();
    }

    /** What the tool's modifiers grant through {@link Modifier#grantedEnchantment}, Silky's included. */
    public static void grantFromModifiers(HolderLookup.Provider registries, ItemStack stack) {
        HolderGetter<Enchantment> enchantments = registries.lookupOrThrow(Registries.ENCHANTMENT);
        if (ForgeweaveModifiers.grantsSilkTouch(stack)) {
            upgrade(stack, enchantments.getOrThrow(Enchantments.SILK_TOUCH), 1);
        }
        for (ModifierEntry entry : ForgeweaveModifiers.of(stack)) {
            Modifier modifier = ForgeweaveModifiers.get(entry.id());
            if (modifier != null) {
                modifier.grantedEnchantment(entry.level())
                        .ifPresent(grant -> upgrade(stack, enchantments.getOrThrow(grant.enchantment()), grant.level()));
            }
        }
    }

    /**
     * Moves a tool built before this component existed. A stored enchantment moves only when the
     * tool's own modifiers and traits account for that level; anything higher came from elsewhere and
     * stays. Runs once per tool: the component being present, even empty, is the marker.
     */
    public static void migrate(HolderLookup.Provider registries, ItemStack stack) {
        if (stack.has(ForgeweaveDataComponents.GRANTED_ENCHANTMENTS.get()) || !stack.isEnchanted()) {
            return;
        }
        stack.set(ForgeweaveDataComponents.GRANTED_ENCHANTMENTS.get(), ItemEnchantments.EMPTY);
        grantFromModifiers(registries, stack);
        if (ForgeweaveTraits.grantsSilkTouch(stack)) {
            upgrade(stack, registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        }
        ModifierApplication.grantEnchantments(registries, stack);
        ItemEnchantments.Mutable stored = new ItemEnchantments.Mutable(stack.get(DataComponents.ENCHANTMENTS));
        ItemEnchantments granted = of(stack);
        for (Holder<Enchantment> enchantment : granted.keySet()) {
            if (stored.getLevel(enchantment) <= granted.getLevel(enchantment)) {
                stored.set(enchantment, 0);
            }
        }
        stack.set(DataComponents.ENCHANTMENTS, stored.toImmutable());
    }
}
