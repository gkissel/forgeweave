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
import net.minecraft.world.entity.EquipmentSlot;
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
 * <h2>Worn render (issue #679, D18)</h2>
 *
 * <p>The 1.20 clone's {@code ArmorModelProvider} plate model is two texture layers -- {@code
 * plating_} over {@code maille_}, each palette-baked per material by {@code
 * MaterialArmorTextureSupplier} -- drawn by its own {@code MultilayerArmorModel}. Vanilla's
 * {@code HumanoidArmorLayer} already draws one pass per {@link ArmorMaterial.Layer}, so
 * {@link #plateMaterial} declares exactly those two (maille first, plating on top: with the depth
 * test at {@code LEQUAL} the later pass wins where they overlap) and {@link #getArmorTexture} swaps
 * each pass's file for the per-material one {@code scripts/derive_armor_art.py} pre-tints from
 * {@code Material.color} -- the flat tint every tool layer gets (ADR-0002), baked at generation time
 * as D18 asks. No custom model class, no data-driven texture manager. The material's stats are dead
 * weight: defense, toughness, enchantability and repair are all overridden below.
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
    static ArmorMaterial plateMaterial() {
        return new ArmorMaterial(Map.of(), 0, SoundEvents.ARMOR_EQUIP_IRON, () -> Ingredient.EMPTY,
                List.of(MAILLE_LAYER, PLATING_LAYER), 0.0F, 0.0F);
    }

    public ArmorPieceItem(Type type, Properties properties) {
        super(ForgeweaveItems.PLATE_ARMOR_MATERIAL, type, properties);
    }

    /**
     * The worn pass's per-material file: {@code textures/models/armor/derived/<part>_<material>_layer_<N>.png},
     * the plating pass off the stack's plating (head) material and the maille pass off its maille.
     * A stack without materials (creative-tab dummy, corrupted save) keeps the gray base.
     */
    @Nullable
    @Override
    public ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer,
            boolean innerModel) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null || materials.parts().size() < 2) {
            return null;
        }
        // ToolConstants#armor: part slot 0 is the plating, slot 1 the maille.
        ResourceLocation material = materials.parts().get(PLATING_LAYER.equals(layer) ? 0 : 1);
        String part = PLATING_LAYER.equals(layer) ? "plating" : "maille";
        // ponytail: a datapack material with no generated file renders vanilla's missing texture,
        // loudly; the upgrade path is a runtime getArmorLayerTintColor tint over the gray base.
        return id("textures/models/armor/derived/" + part + "_" + material.getPath() + "_layer_" + (innerModel ? 2 : 1) + ".png");
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
        // M4-6 (#681): the knockback resistance modifier's +0.1/level rides the same attribute line.
        float knockbackResistance = stats.knockbackResistance() + ForgeweaveModifiers.knockbackResistanceBonus(stack);
        if (knockbackResistance > 0) {
            builder.add(Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(id, knockbackResistance, AttributeModifier.Operation.ADD_VALUE), slot);
        }
        // #680: the ARMOR traits' attribute modifiers (skyfall, crystalstrike, projectile protection).
        ForgeweaveTraits.armorAttributes(stack, type.getSlot(), builder);
        return builder.build();
    }

    /** Same seam as {@link ToolItem#inventoryTick}: the piece's traits tick while it is carried or worn (#680, overshield's recharge). */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity holder && !ToolItem.isBroken(stack)) {
            ForgeweaveTraits.inventoryTick(stack, serverLevel, holder);
        }
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
