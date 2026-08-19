package dev.gkissel.forgeweave.item;

import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The slime boots (issue #452, parity audit T21): upstream 1.12's {@code gadgets/item/ItemSlimeBoots}
 * plus {@code library/SlimeBounceHandler} (the latter in {@link SlimeBounceHandler}; NOTICE.md).
 * Landing in them from more than two blocks costs no fall damage and throws the wearer back up;
 * crouching through the landing cancels the bounce and takes a fifth of the damage instead.
 *
 * <p>Upstream's boots carry no armour, no toughness and no enchantability -- its
 * {@code getAttributeModifiers} returns an empty multimap on purpose ("all our armor values are 0,
 * causing a weird tooltip"), which {@link #getDefaultAttributeModifiers()} reproduces: vanilla's
 * {@link ArmorItem} would otherwise contribute a zero-valued Armor modifier, and 1.21's tooltip
 * prints the "When on Feet" header for it with nothing underneath.
 *
 * <h2>Which side does what</h2>
 *
 * <p>Exactly upstream's split. {@link #onFall} runs on both: the client sets the rebound velocity
 * (player movement is client-authoritative, so this is the side that can), the server cancels the
 * fall damage. The rebound itself is handed to {@link SlimeBounceHandler}, upstream's own shared
 * class -- vanilla's own movement code runs after the fall check and would eat the rebound, so the
 * velocity is re-applied on the tick it was scheduled for, and the wearer keeps most of their
 * horizontal momentum for as long as they stay in the air.
 */
public class SlimeBootsItem extends ArmorItem {

    /** Upstream's {@code ItemSlimeBoots.SLIME_MATERIAL}: no defense, no enchantability, no toughness. */
    public static final DeferredRegister<ArmorMaterial> ARMOR_MATERIALS =
            DeferredRegister.create(Registries.ARMOR_MATERIAL, Forgeweave.MODID);

    /**
     * The one armour layer, {@code derived/slime} rather than plain {@code slime} because vanilla
     * builds the texture path from this name and derived art belongs in a {@code derived} folder
     * (CLAUDE.md) -- here {@code textures/models/armor/derived/slime_layer_1.png}.
     */
    public static final DeferredHolder<ArmorMaterial, ArmorMaterial> SLIME_MATERIAL = ARMOR_MATERIALS.register(
            "slime",
            () -> new ArmorMaterial(Map.of(), 0,
                    BuiltInRegistries.SOUND_EVENT.wrapAsHolder(SoundEvents.SLIME_BLOCK_PLACE), () -> Ingredient.EMPTY,
                    List.of(new ArmorMaterial.Layer(
                            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "derived/slime"))),
                    0.0F, 0.0F));

    /** Upstream's {@code event.getDistance() > 2}: shorter drops are left to vanilla. */
    private static final float MIN_BOUNCE_DISTANCE = 2.0F;
    /** Upstream's {@code motionY *= -0.9}: each rebound keeps nine tenths of the impact speed. */
    private static final double BOUNCE_RETENTION = -0.9;
    /** Upstream's {@code 0.91 + 0.04}: undoes most of one tick of air drag on the landing tick. */
    private static final double LANDING_DRAG_RELIEF = 0.95;
    /** Upstream's {@code event.setDamageMultiplier(0.2f)} for a crouched landing. */
    private static final float CROUCHED_DAMAGE_MULTIPLIER = 0.2F;
    public SlimeBootsItem(Properties properties) {
        super(SLIME_MATERIAL, Type.BOOTS, properties.stacksTo(1));
    }

    /** See the class javadoc: upstream returns an empty multimap so the tooltip stays clean. */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ItemAttributeModifiers.EMPTY;
    }

    /** Upstream's {@code item.tconstruct.slime_boots.tooltip}. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("tooltip.forgeweave.slime_boots").withStyle(ChatFormatting.GRAY));
    }

    /** Upstream's {@code ItemSlimeBoots#onFall}. */
    public static void onFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity.getItemBySlot(EquipmentSlot.FEET).getItem() instanceof SlimeBootsItem)) {
            return;
        }

        boolean client = entity.level().isClientSide;
        if (!entity.isShiftKeyDown() && event.getDistance() > MIN_BOUNCE_DISTANCE) {
            event.setDamageMultiplier(0.0F);
            entity.resetFallDistance();
            if (client) {
                Vec3 movement = entity.getDeltaMovement();
                entity.setDeltaMovement(movement.x / LANDING_DRAG_RELIEF, movement.y * BOUNCE_RETENTION,
                        movement.z / LANDING_DRAG_RELIEF);
                entity.hasImpulse = true;
                entity.setOnGround(false);
            } else {
                // Upstream: "we don't care about previous cancels, since we just bounceeeee".
                event.setCanceled(true);
            }
            entity.playSound(SoundEvents.SLIME_SQUISH, 1.0F, 1.0F);
            if (entity instanceof Player player) {
                SlimeBounceHandler.addBounceHandler(player, entity.getDeltaMovement().y);
            }
        } else if (!client && entity.isShiftKeyDown()) {
            event.setDamageMultiplier(CROUCHED_DAMAGE_MULTIPLIER);
        }
    }
}
