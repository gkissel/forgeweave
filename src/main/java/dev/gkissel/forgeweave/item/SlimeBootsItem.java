package dev.gkissel.forgeweave.item;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

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

import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * The slime boots (issue #452, parity audit T21): upstream 1.12's {@code gadgets/item/ItemSlimeBoots}
 * plus {@code library/SlimeBounceHandler}, both ported here (NOTICE.md). Landing in them from more
 * than two blocks costs no fall damage and throws the wearer back up; crouching through the landing
 * cancels the bounce and takes a fifth of the damage instead.
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
 * fall damage. {@link #onPlayerTick} is the bounce handler -- vanilla's own movement code runs after
 * the fall check and would eat the rebound, so the velocity is re-applied on the tick it was
 * scheduled for, and the wearer keeps most of their horizontal momentum for as long as they stay in
 * the air.
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
    /** Upstream's {@code 0.91 + 0.025}: and slightly less of it on every airborne tick after. */
    private static final double AIRBORNE_DRAG_RELIEF = 0.935;
    /** Upstream's {@code event.setDamageMultiplier(0.2f)} for a crouched landing. */
    private static final float CROUCHED_DAMAGE_MULTIPLIER = 0.2F;
    /** Upstream's {@code entityLiving.ticksExisted - timer > 5}: how long back on the ground ends it. */
    private static final int GROUNDED_TICKS_BEFORE_DONE = 5;

    /**
     * Upstream keeps one handler object per bouncing entity in a static {@code IdentityHashMap} and
     * registers each on the event bus; this is the same map with one listener instead of many. Weak
     * keys because a player who logs out mid-bounce never ticks again and would otherwise be pinned;
     * every access below is a single-key get/put/remove, which the synchronized wrapper covers (the
     * client and the integrated server tick their own {@code Player} objects on different threads).
     */
    private static final Map<Player, Bounce> BOUNCING = Collections.synchronizedMap(new WeakHashMap<>());

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
            addBounceHandler(entity, entity.getDeltaMovement().y);
        } else if (!client && entity.isShiftKeyDown()) {
            event.setDamageMultiplier(CROUCHED_DAMAGE_MULTIPLIER);
        }
    }

    /** Upstream's {@code SlimeBounceHandler#addBounceHandler}: real players only, never fake ones. */
    private static void addBounceHandler(LivingEntity entity, double bounce) {
        if (!(entity instanceof Player player) || player instanceof FakePlayer) {
            return;
        }
        Bounce existing = BOUNCING.get(player);
        if (existing == null) {
            BOUNCING.put(player, new Bounce(player, bounce));
        } else if (bounce != 0) {
            existing.bounce = bounce;
            existing.bounceTick = player.tickCount;
        }
    }

    /** Upstream's {@code SlimeBounceHandler#playerTickPost}. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Bounce bounce = BOUNCING.get(player);
        if (bounce == null || player.isFallFlying()) {
            return;
        }
        if (bounce.tick(player)) {
            BOUNCING.remove(player);
        }
    }

    /** Test seam: whether the wearer is still being carried by a bounce. */
    public static boolean isBouncing(Player player) {
        return BOUNCING.containsKey(player);
    }

    /** One entity's in-flight bounce -- upstream's {@code SlimeBounceHandler} instance state. */
    private static final class Bounce {
        private int timer;
        private boolean wasInAir;
        private double bounce;
        private int bounceTick;
        private double lastMovementX;
        private double lastMovementZ;

        private Bounce(Player player, double bounce) {
            this.bounce = bounce;
            this.bounceTick = bounce != 0 ? player.tickCount : 0;
        }

        /** @return true once the wearer has been back on the ground long enough to stop tracking. */
        private boolean tick(Player player) {
            if (player.tickCount == bounceTick) {
                Vec3 movement = player.getDeltaMovement();
                player.setDeltaMovement(movement.x, bounce, movement.z);
                bounceTick = 0;
            }

            if (!player.onGround() && player.tickCount != bounceTick) {
                Vec3 movement = player.getDeltaMovement();
                if (lastMovementX != movement.x || lastMovementZ != movement.z) {
                    player.setDeltaMovement(movement.x / AIRBORNE_DRAG_RELIEF, movement.y,
                            movement.z / AIRBORNE_DRAG_RELIEF);
                    player.hasImpulse = true;
                    lastMovementX = player.getDeltaMovement().x;
                    lastMovementZ = player.getDeltaMovement().z;
                }
            }

            if (wasInAir && player.onGround()) {
                if (timer == 0) {
                    timer = player.tickCount;
                } else if (player.tickCount - timer > GROUNDED_TICKS_BEFORE_DONE) {
                    return true;
                }
            } else {
                timer = 0;
                wasInAir = true;
            }
            return false;
        }
    }
}
