package dev.gkissel.forgeweave.item;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.tool.ArmorStats;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * One part-built plate armor piece (issue #678, M4-3; SCOPE.md D3/D7/D14/D19/D20): plating +
 * maille, assembled at the Tool Station or Forge like any tool ({@code ToolAssemblyRecipes}), and
 * carrying the same {@code TOOL_MATERIALS}/{@code TRAITS}/{@code MODIFIERS}/{@code ENCHANTABILITY}
 * components -- plus {@link ForgeweaveDataComponents#ARMOR_STATS} in place of {@code TOOL_STATS}.
 *
 * <p>A vanilla {@link ArmorItem} rather than a {@link ToolItem}: the slot, the right-click equip and
 * the armor-damage path ({@code LivingEntity#doHurtEquipment} only hurts {@code ArmorItem}s) are
 * vanilla's, and so is Elytra's exclusion -- the chestplate occupies {@code CHEST}, which is all
 * vanilla ever does about it (D7). What is Forgeweave's is ported from the 1.20 clone's
 * {@code ModifiableArmorItem}: attributes come from the stack's stats and vanish while Broken
 * ({@code #getAttributeModifiers}), durability damage clamps at Broken instead of destroying the
 * piece ({@code #damageItem}), and the anvil never repairs it ({@code #isValidRepairItem}).
 *
 * <p>ponytail: the {@link ArmorMaterials#IRON} holder is a placeholder for the render layer only --
 * #679 lands the two-layer tinted model. Nothing else reads it: defense, toughness, enchantability
 * and repair are all overridden below.
 */
public class ArmorPieceItem extends ArmorItem {

    public ArmorPieceItem(Type type, Properties properties) {
        super(ArmorMaterials.IRON, type, properties);
    }

    /** The plating material prefixes the name, as a tool's head does ({@link ToolItem#getName}). */
    @Override
    public Component getName(ItemStack stack) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        return MaterialDisplay.prefixed(materials == null ? List.of() : List.of(materials.head()), super.getName(stack));
    }

    @Nullable
    public static ArmorStats stats(ItemStack stack) {
        return stack.get(ForgeweaveDataComponents.ARMOR_STATS.get());
    }

    /**
     * Upstream {@code ModifiableArmorItem#getAttributeModifiers}: armor, toughness and knockback
     * resistance straight off the stats, each only when positive, and nothing at all while Broken
     * -- a Broken piece stays equipped and protects nothing (D19).
     */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        ArmorStats stats = stats(stack);
        if (stats == null || ToolItem.isBroken(stack)) {
            return ItemAttributeModifiers.EMPTY;
        }
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        EquipmentSlotGroup slot = EquipmentSlotGroup.bySlot(type.getSlot());
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "armor." + type.getName());
        if (stats.armor() > 0) {
            builder.add(Attributes.ARMOR, new AttributeModifier(id, stats.armor(), AttributeModifier.Operation.ADD_VALUE), slot);
        }
        if (stats.toughness() > 0) {
            builder.add(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(id, stats.toughness(), AttributeModifier.Operation.ADD_VALUE), slot);
        }
        if (stats.knockbackResistance() > 0) {
            builder.add(Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(id, stats.knockbackResistance(), AttributeModifier.Operation.ADD_VALUE), slot);
        }
        return builder.build();
    }

    /** Same clamp as a tool: never destroyed, Broken at {@code max - 1} ({@link ToolItem#damageItem}). */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        return amount <= 0 ? amount : ToolItem.applyDamageKeepingItem(stack, amount, entity);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !ToolItem.isBroken(stack) && super.isBarVisible(stack);
    }

    /** Repair is the station's, off the plating material's {@code repair_item} (D19) -- never the anvil's iron ingot. */
    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return false;
    }

    // Vanilla enchanting (D20): the same allowVanillaEnchanting gate as ToolItem, enchantability off
    // the plating material's component.

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.get() && super.isEnchantable(stack);
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.get() && super.isBookEnchantable(stack, book);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        if (!ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.get()) {
            return 0;
        }
        return stack.getOrDefault(ForgeweaveDataComponents.ENCHANTABILITY.get(), Material.DEFAULT_ENCHANTABILITY);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.get() && super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (stats(stack) == null) {
            return;
        }
        if (ToolItem.isBroken(stack)) {
            tooltip.add(Component.translatable("tooltip.forgeweave.durability")
                    .append(": ")
                    .append(Component.translatable("tooltip.forgeweave.broken")
                            .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)));
        }
        tooltip.addAll(StationText.armorStats(stack));
    }
}
