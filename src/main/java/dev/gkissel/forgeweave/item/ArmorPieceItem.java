package dev.gkissel.forgeweave.item;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantment;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ArmorStats;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

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
 * <h2>Worn render (issue #679, D18; runtime tint #726)</h2>
 *
 * <p>The 1.20 clone's {@code ArmorModelProvider} plate model is two texture layers -- {@code
 * plating_} over {@code maille_}, each palette-baked per material by {@code
 * MaterialArmorTextureSupplier} -- drawn by its own {@code MultilayerArmorModel}. Vanilla's
 * {@code HumanoidArmorLayer} already draws one pass per {@link ArmorMaterial.Layer}, so
 * {@link #plateMaterial} declares exactly those two (maille first, plating on top: with the depth
 * test at {@code LEQUAL} the later pass wins where they overlap), each pass reading the clone's gray
 * base straight off the layer id. The colour is applied at render time: {@code
 * ForgeweaveItemClientExtensions} answers {@code getArmorLayerTintColor} with the {@link
 * #layerMaterial part's} {@code Material.color} -- the flat tint every tool layer gets (ADR-0002) --
 * so a datapack material needs no PNG of its own. No custom model class, no data-driven texture
 * manager, no generated per-material files. The material's stats are dead weight: defense,
 * toughness, enchantability and repair are all overridden below.
 */
public class ArmorPieceItem extends ArmorItem {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /** {@code textures/models/armor/derived/maille_layer_{1,2}.png}, the clone's {@code maille_{armor,leggings}.png}. */
    private static final ArmorMaterial.Layer MAILLE_LAYER = new ArmorMaterial.Layer(id("derived/maille"));
    /** {@code textures/models/armor/derived/plating_layer_{1,2}.png}, the clone's {@code plating_{armor,leggings}.png}. */
    private static final ArmorMaterial.Layer PLATING_LAYER = new ArmorMaterial.Layer(id("derived/plating"));

    /**
     * {@link ForgeweaveItems#PLATE_ARMOR_MATERIAL}'s value -- registered from there rather than here
     * because the armor-material registry event fires before the item one that first loads this
     * class, and a {@code DeferredRegister} entry added after its event is an error.
     */
    public static ArmorMaterial plateMaterial() {
        return new ArmorMaterial(Map.of(), 0, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.EMPTY,
                List.of(MAILLE_LAYER, PLATING_LAYER), 0.0F, 0.0F);
    }

    /** #735: a heavy piece (plating + maille + large plate) -- each one worn takes {@link ToolConstants#HEAVY_ARMOR_SPEED} off movement speed. */
    private final boolean heavy;

    public ArmorPieceItem(Type type, boolean heavy, Properties properties) {
        super(ForgeweaveItems.PLATE_ARMOR_MATERIAL, type, properties);
        this.heavy = heavy;
    }

    public boolean isHeavy() {
        return heavy;
    }

    /**
     * The material a worn pass tints with: {@link #plateMaterial}'s layer 0 is the maille (part
     * slot 1), layer 1 the plating (part slot 0, {@code ToolConstants#armor}). {@code null} for a
     * stack without materials (creative-tab dummy, corrupted save), which keeps the gray base.
     */
    @Nullable
    public static ResourceLocation layerMaterial(ItemStack stack, int layerIdx) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null || materials.parts().size() < 2) {
            return null;
        }
        return materials.parts().get(layerIdx == 0 ? 1 : 0);
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
        // #736: netherite's +1 toughness rides the same attribute line as the plating's own.
        float toughness = stats.toughness() + ForgeweaveModifiers.armorToughnessBonus(stack);
        if (toughness > 0) {
            builder.add(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(id, toughness, AttributeModifier.Operation.ADD_VALUE), slot);
        }
        // M4-6 (#681): the knockback resistance modifier's +0.1/level rides the same attribute line.
        float knockbackResistance = stats.knockbackResistance() + ForgeweaveModifiers.knockbackResistanceBonus(stack);
        if (knockbackResistance > 0) {
            builder.add(Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(id, knockbackResistance, AttributeModifier.Operation.ADD_VALUE), slot);
        }
        // #680: the ARMOR traits' attribute modifiers (skyfall, crystalstrike, projectile protection).
        if (heavy) {
            // #735: -5% per worn heavy piece, multiplicative -- ADD_MULTIPLIED_TOTAL compounds across
            // the four slots (0.95^4 for the set), the same operation vanilla's speed potions use.
            builder.add(Attributes.MOVEMENT_SPEED, new AttributeModifier(id, ToolConstants.HEAVY_ARMOR_SPEED,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL), slot);
        }
        ForgeweaveTraits.armorAttributes(stack, type.getSlot(), builder);
        return builder.build();
    }

    /** Same seam as {@link ToolItem#inventoryTick}: the piece's traits tick while it is carried or worn (#680). */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity holder && !ToolItem.isBroken(stack)) {
            ForgeweaveTraits.inventoryTick(stack, serverLevel, holder);
        }
    }

    /**
     * The same seam as a tool ({@link ToolItem#damageKeepingItem}): reinforced, durability traits
     * (#728: overslime pays the loss first, same chain as a tool's), then the Broken clamp.
     */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        return ToolItem.damageKeepingItem(stack, amount, entity);
    }

    // #728: while there is overslime, the bar is its light-blue gauge (the clone's
    // OverslimeModifier#showDurabilityBar/getDurabilityWidth/getDurabilityRGB); once it is spent the
    // durability bar takes over as usual.

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !ToolItem.isBroken(stack) && (ForgeweaveTraits.overslime(stack) > 0 || super.isBarVisible(stack));
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        int overslime = ForgeweaveTraits.overslime(stack);
        return overslime > 0 ? Math.round(13.0F * overslime / ForgeweaveTraits.overslimeCapacity(stack)) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return ForgeweaveTraits.overslime(stack) > 0 ? ForgeweaveTraits.OVERSLIME_BAR_COLOR : super.getBarColor(stack);
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
