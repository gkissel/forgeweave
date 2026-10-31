package dev.gkissel.forgeweave.item;

import dev.gkissel.forgeweave.api.trait.Trait;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.EquipmentAsset;
import net.minecraft.world.item.equipment.EquipmentAssets;
import net.minecraft.world.item.equipment.Equippable;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.apotheosis.ApotheosisAffixes; // #970
import dev.gkissel.forgeweave.compat.apotheosis.ApotheosisSockets;
import dev.gkissel.forgeweave.compat.mekanism.modules.MekanismGearModules; // #994
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
 * <p>An {@code equippable} item rather than a {@link ToolItem} (1.21.2 retired {@code ArmorItem}; the
 * component is what armour is now): the slot, the right-click equip and the armor-damage path
 * ({@code LivingEntity#doHurtEquipment} hurts any {@code equippable} with {@code damage_on_hurt}) are
 * vanilla's, and so is Elytra's exclusion -- the chestplate occupies {@code CHEST}, which is all
 * vanilla ever does about it (D7). What is Forgeweave's is ported from the 1.20 clone's
 * {@code ModifiableArmorItem}: attributes come from the stack's stats and vanish while Broken
 * ({@code #getAttributeModifiers}), durability damage clamps at Broken instead of destroying the
 * piece ({@code #damageItem}), and the anvil never repairs it (no {@code repairable} component).
 *
 * <h2>Worn render (issue #679, D18; runtime tint #726)</h2>
 *
 * <p>The 1.20 clone's {@code ArmorModelProvider} plate model is two texture layers -- {@code
 * plating_} over {@code maille_}, each palette-baked per material by {@code
 * MaterialArmorTextureSupplier} -- drawn by its own {@code MultilayerArmorModel}. Vanilla's
 * {@code HumanoidArmorLayer} already draws one pass per equipment-asset layer, so {@link #PLATE_ASSET}'s
 * client file ({@code equipment/plate.json}) declares exactly {@link #PLATE_LAYERS} (maille first, plating on top: with the depth
 * test at {@code LEQUAL} the later pass wins where they overlap), each pass reading the clone's gray
 * base straight off the layer id. The colour is applied at render time: {@code
 * ForgeweaveItemClientExtensions} answers {@code getArmorLayerTintColor} with the {@link
 * #layerMaterial part's} {@code Material.color} -- the flat tint every tool layer gets (ADR-0002) --
 * so a datapack material needs no PNG of its own. No custom model class, no data-driven texture
 * manager, no generated per-material files. The material's stats are dead weight: defense,
 * toughness, enchantability and repair are all overridden below.
 */
public class ArmorPieceItem extends Item {

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /** {@code textures/entity/equipment/humanoid{,_leggings}/derived/maille.png}, the clone's {@code maille_{armor,leggings}.png}. */
    public static final Identifier MAILLE_LAYER = id("derived/maille");
    /** {@code textures/entity/equipment/humanoid{,_leggings}/derived/plating.png}, the clone's {@code plating_{armor,leggings}.png}. */
    public static final Identifier PLATING_LAYER = id("derived/plating");
    /** The worn passes in draw order: maille first, plating on top (see the class javadoc). */
    public static final List<Identifier> PLATE_LAYERS = List.of(MAILLE_LAYER, PLATING_LAYER);
    /** Every piece's worn-texture asset, {@code assets/forgeweave/equipment/plate.json}. */
    public static final ResourceKey<EquipmentAsset> PLATE_ASSET = ResourceKey.create(EquipmentAssets.ROOT_ID, id("plate"));

    private final ArmorType type;

    /** #735: a heavy piece (plating + maille + large plate) -- each one worn takes {@link ToolConstants#HEAVY_ARMOR_SPEED} off movement speed. */
    private final boolean heavy;

    public ArmorPieceItem(ArmorType type, boolean heavy, Properties properties) {
        // `enchantable` is what lets the 26.1 table take the piece at all (see ToolItem's constructor).
        super(properties.enchantable(Material.DEFAULT_ENCHANTABILITY).component(DataComponents.EQUIPPABLE, Equippable.builder(type.getSlot())
                .setEquipSound(SoundEvents.ARMOR_EQUIP_IRON).setAsset(PLATE_ASSET).build()));
        this.type = type;
        this.heavy = heavy;
    }

    /** What {@code ArmorItem#getType} answered: which of the four pieces this is. */
    public ArmorType getType() {
        return type;
    }

    /** What {@code ArmorItem#getEquipmentSlot} answered: the slot this piece is worn in. */
    public EquipmentSlot getEquipmentSlot() {
        return type.getSlot();
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
    public static Identifier layerMaterial(ItemStack stack, int layerIdx) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null || materials.parts().size() < 2) {
            return null;
        }
        return materials.parts().get(layerIdx == 0 ? 1 : 0);
    }

    /**
     * The material a worn pass tints with, by the pass's texture id rather than its index -- what a
     * 26.1 equipment layer knows about itself. {@code null} for any other texture.
     */
    @Nullable
    public static Identifier layerMaterial(ItemStack stack, Identifier layerTexture) {
        int idx = PLATE_LAYERS.indexOf(layerTexture);
        return idx < 0 ? null : layerMaterial(stack, idx);
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
        Identifier id = Identifier.fromNamespaceAndPath(Forgeweave.MODID, "armor." + type.getName());
        // #969: an Apotheosis gem's armour, toughness or knockback-resistance attribute rides the
        // same three lines the plating and the modifiers already share, rather than adding attribute
        // modifiers of its own. 0 without Apotheosis or without sockets.
        float armor = stats.armor() + ApotheosisSockets.armorBonus(stack);
        if (armor > 0) {
            builder.add(Attributes.ARMOR, new AttributeModifier(id, armor, AttributeModifier.Operation.ADD_VALUE), slot);
        }
        // #736: netherite's +1 toughness rides the same attribute line as the plating's own.
        float toughness = stats.toughness() + ForgeweaveModifiers.armorToughnessBonus(stack)
                + ApotheosisSockets.armorToughnessBonus(stack);
        if (toughness > 0) {
            builder.add(Attributes.ARMOR_TOUGHNESS,
                    new AttributeModifier(id, toughness, AttributeModifier.Operation.ADD_VALUE), slot);
        }
        // M4-6 (#681): the knockback resistance modifier's +0.1/level rides the same attribute line.
        float knockbackResistance = stats.knockbackResistance() + ForgeweaveModifiers.knockbackResistanceBonus(stack)
                + ApotheosisSockets.knockbackResistanceBonus(stack);
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
    public void inventoryTick(ItemStack stack, ServerLevel serverLevel, Entity entity, @Nullable EquipmentSlot slot) {
        // 26.1.2: a tool or armor piece assembled on 1.21.1 has no minecraft:enchantable of its own;
        // give it the one its material set calls for (see Enchantability).
        Enchantability.backfill(stack);
        if (entity instanceof LivingEntity holder && !ToolItem.isBroken(stack)) {
            ForgeweaveTraits.inventoryTick(stack, serverLevel, holder);
        }
        syncGlider(stack);
        // #994: the same seam ToolItem#inventoryTick gives a Mekanism module, for the worn half --
        // the free ICustomModule tick hooks (the gravitational modulating unit's own is one), and the
        // jetpack, which needs both sides. Does nothing without Mekanism, without an
        // atomic_matter_alloy part, or with the toggle off.
        if (entity instanceof Player player && !ToolItem.isBroken(stack)) {
            // 26.1 ticks items on the server only, so the client half of the pair no longer runs.
            MekanismGearModules.tickModules(stack, player, true);
            if (stack == player.getItemBySlot(EquipmentSlot.CHEST)) {
                MekanismGearModules.jetpackTick(stack, player, true);
            }
        }
    }

    /**
     * The same seam as a tool ({@link ToolItem#damageKeepingItem}): reinforced, durability traits
     * (#728: overslime pays the loss first, same chain as a tool's), then the Broken clamp.
     */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<ItemStack> onBroken) {
        if (entity != null && amount > 0 && !ToolItem.isBroken(stack)) {
            float chance = durabilityNegationChance(entity);
            if (chance > 0.0F && entity.getRandom().nextFloat() < chance) {
                return 0;
            }
        }
        return ToolItem.damageKeepingItem(stack, amount, entity);
    }

    /** Sums durability protection from worn, unbroken Forgeweave armor. */
    public static float durabilityNegationChance(LivingEntity entity) {
        float chance = 0.0F;
        for (EquipmentSlot slot : EquipmentSlotGroup.ARMOR) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack piece = entity.getItemBySlot(slot);
            if (piece.getItem() instanceof ArmorPieceItem && !ToolItem.isBroken(piece)) {
                for (Trait trait : ForgeweaveTraits.of(piece)) {
                    chance += trait.armorDurabilityNegationChance();
                }
            }
        }
        return Math.min(1.0F, chance);
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

    /**
     * Issue #737's elytra flight modifier. 1.21.1 asked NeoForge's {@code canElytraFly} item hook;
     * since 1.21.2 gliding is the {@code minecraft:glider} component ({@code LivingEntity#canGlideUsing}),
     * so {@link #syncGlider} keeps that component on the stack exactly while this answers true. Never
     * while Broken, the same gate every other worn behavior on this class uses.
     *
     * <p>Never once the chestplate also carries creative flight, which replaces the glide. Otherwise
     * a jump in mid-air meant to start flying opens the wings instead.
     */
    public boolean canElytraFly(ItemStack stack, LivingEntity entity) {
        // #994: a powered Mekanism elytra unit is a second, independent grant of the same glide.
        // Ored, not anded: two grants are no better than one, and neither takes the other away.
        return (ForgeweaveModifiers.grantsElytraFlight(stack) || MekanismGearModules.grantsElytraFlight(stack))
                && !ForgeweaveModifiers.grantsCreativeFlight(stack)
                && !ToolItem.isBroken(stack);
    }

    /**
     * Puts {@code minecraft:glider} on the stack while {@link #canElytraFly} holds and takes it off
     * otherwise. Run from {@link #inventoryTick}, which 26.1 calls for worn equipment every server
     * tick; the component then syncs to the client like any other stack change.
     */
    private void syncGlider(ItemStack stack) {
        boolean glides = canElytraFly(stack, null);
        if (glides != stack.has(DataComponents.GLIDER)) {
            if (glides) {
                stack.set(DataComponents.GLIDER, Unit.INSTANCE);
            } else {
                stack.remove(DataComponents.GLIDER);
            }
        }
    }

    /**
     * Vanilla {@code ElytraItem#elytraFlightTick}'s own cadence, mirrored exactly rather than
     * reinvented: a game event every 10 ticks, one point of durability every 20th. Since 1.21.2
     * vanilla's own {@code LivingEntity#updateFallFlying} runs exactly this cadence for any glider, so
     * nothing calls this in play any more; it stays as the same arithmetic for the tests that pin it.
     * The durability hit goes through {@link #damageItem} like any other loss on this class
     * (reinforced, durability traits, the Broken clamp), so a piece never breaks outright mid-glide.
     */
    public boolean elytraFlightTick(ItemStack stack, LivingEntity entity, int flightTicks) {
        if (!entity.level().isClientSide()) {
            int nextFlightTick = flightTicks + 1;
            if (nextFlightTick % 10 == 0) {
                if (nextFlightTick % 20 == 0) {
                    stack.hurtAndBreak(1, entity, EquipmentSlot.CHEST);
                }
                entity.gameEvent(GameEvent.ELYTRA_GLIDE);
            }
        }
        return true;
    }

    // Repair is the station's, off the plating material's repair_item (D19) -- never the anvil's iron
    // ingot. 1.21.1 refused it in isValidRepairItem; since 1.21.2 the anvil only repairs with a
    // repairable component, which these pieces never carry.

    // Vanilla enchanting (D20): the same allowVanillaEnchanting gate as ToolItem, enchantability off
    // the plating material's component. #970 adds the Apotheosis toggle at the same two doors and for
    // the same reasons -- see ToolItem#isEnchantable and #isBookEnchantable.

    public boolean isEnchantable(ItemStack stack) {
        return ToolItem.vanillaEnchantingAllowed() && stack.isEnchantable();
    }

    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return ToolItem.vanillaEnchantingAllowed();
    }

    public int getEnchantmentValue(ItemStack stack) {
        if (!ForgeweaveConfig.read(ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING)) {
            return 0;
        }
        return stack.getOrDefault(ForgeweaveDataComponents.ENCHANTABILITY.get(), Material.DEFAULT_ENCHANTABILITY);
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return ForgeweaveConfig.read(ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING) && super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        List<Component> tooltip = new ArrayList<>();
        ToolTooltip.appendArmor(stack, context.registries(), ToolTooltip.detailed(flag), tooltip);
        if (stats(stack) != null) {
            ToolTooltip.appendShiftHint(ToolTooltip.shiftHint(flag), tooltip);
        }
        tooltip.forEach(builder);
    }
}
