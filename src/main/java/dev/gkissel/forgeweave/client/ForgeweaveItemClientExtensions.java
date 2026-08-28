package dev.gkissel.forgeweave.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.CrossbowItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * The crossbow's third-person arm pose (issue #425), and the worn plate armor's per-layer tint
 * (issue #726, see {@link #registerItemClientExtensions}).
 *
 * <p>The crossbow's third-person arm pose (issue #425). {@link ForgeweaveItemProperties} is the
 * first-person half of the same story -- what the <em>model</em> resolves to -- and this is what the
 * player's <em>arms</em> do while everybody else watches.
 *
 * <h2>Why an extension at all</h2>
 *
 * <p>{@code PlayerRenderer#getArmPose} reaches {@code ArmPose.CROSSBOW_HOLD} only through
 * {@code itemstack.getItem() instanceof net.minecraft.world.item.CrossbowItem && CrossbowItem
 * .isCharged(...)}, and a Forgeweave crossbow is neither -- it is a {@link
 * dev.gkissel.forgeweave.item.BowItem} storing a boolean of its own. So a cranked crossbow was
 * carried at the plain {@code ArmPose.ITEM}, exactly like a torch: no cranking animation, and
 * nothing about a loaded crossbow that anyone but its holder could see (playtest 0.3.5-alpha.1,
 * checklist 9.i). NeoForge's {@code IClientItemExtensions#getArmPose} is the hook vanilla leaves for
 * precisely this, and it is consulted right before that {@code ArmPose.ITEM} fallback, so returning
 * {@code null} keeps vanilla's answer.
 *
 * <p>Upstream's 1.20 branch does the same thing at the same hook ({@code
 * ModifiableCrossbowClientExtension#getArmPose}), which is the shape this follows.
 *
 * <h2>Why {@code getUseAnimation} is not touched</h2>
 *
 * <p>The cheaper-looking fix -- returning {@code UseAnim.CROSSBOW} from {@link
 * CrossbowItem#getUseAnimation} so vanilla hands out {@code CROSSBOW_CHARGE} itself -- would break
 * first person. {@code ItemInHandRenderer}'s {@code switch (stack.getUseAnimation())} has no
 * {@code CROSSBOW} case at all (vanilla's crossbow never reaches it; it is diverted by an
 * {@code instanceof} branch above), so the crossbow would fall through the switch without even the
 * {@code applyItemArmTransform} that {@code UseAnim.NONE} gets, and issue #413's cranking and loaded
 * poses would render detached from the hand. Upstream 1.20 pays for {@code UseAnim.CROSSBOW} by
 * reimplementing the entire first-person hand transform in {@code
 * ModifiableItemClientExtension#applyForgeHandTransform}; Forgeweave does not need to, because
 * {@code UseAnim.NONE} is <em>also</em> what 1.12 returns ({@code CrossBow#getItemUseAction}) and
 * this hook alone covers both poses. {@code CrossbowArmPoseTest} guards it.
 *
 * <h2>Whose crank pose</h2>
 *
 * <p>Not vanilla's {@code ArmPose.CROSSBOW_CHARGE}: its animation is paced by vanilla's own 25-tick
 * charge duration rather than the assembled crossbow's {@code drawTime}, so the arms finished
 * winding twenty ticks early and froze there (issue #601, playtest 0.3.5-alpha.3 obs1). The charge
 * branch hands out {@link CrossbowChargeArmPose} instead, which is the same pose over the right
 * clock. The shouldered {@code CROSSBOW_HOLD} is still vanilla's, because it animates nothing.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveItemClientExtensions {

    /**
     * {@code PlayerRenderer#getArmPose}'s two crossbow branches, transcribed for a stack that keeps
     * its crank in {@code CROSSBOW_LOADED} instead of in vanilla's {@code CHARGED_PROJECTILES}.
     *
     * @param stack         the held crossbow
     * @param usingThisHand whether the holder is mid-use on this very hand, vanilla's
     *                      {@code getUsedItemHand() == hand && getUseItemRemainingTicks() > 0}
     * @param swinging      the holder's {@code swinging}; vanilla suppresses the shouldered pose
     *                      mid-swing and so does this
     * @return the pose, or {@code null} to leave the item at whatever vanilla would have chosen
     */
    @Nullable
    static HumanoidModel.ArmPose crossbowArmPose(ItemStack stack, boolean usingThisHand, boolean swinging) {
        if (usingThisHand) {
            return CrossbowChargeArmPose.pose();
        }
        return !swinging && CrossbowItem.isLoaded(stack) ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
    }

    /**
     * Registered off {@link ToolAssemblyRecipes#ENTRIES} for the reason {@link ForgeweaveItemProperties} is.
     *
     * <p>The armor extension is #726's runtime tint: {@code HumanoidArmorLayer} asks {@code
     * getArmorLayerTintColor} once per {@link ArmorMaterial.Layer} pass, and each pass gets its own
     * part's {@code Material.color} over the shared gray base ({@link ArmorPieceItem#layerMaterial}),
     * the same colour {@link ForgeweaveItemColors} puts on the item sprite. A stack without
     * materials returns white, vanilla's untinted answer.
     */
    @SubscribeEvent
    static void registerItemClientExtensions(RegisterClientExtensionsEvent event) {
        IClientItemExtensions armor = new IClientItemExtensions() {
            @Override
            public int getArmorLayerTintColor(ItemStack stack, LivingEntity entity, ArmorMaterial.Layer layer, int layerIdx,
                    int fallbackColor) {
                return ForgeweaveItemColors.opaqueMaterialColor(ArmorPieceItem.layerMaterial(stack, layerIdx));
            }
        };
        IClientItemExtensions crossbow = new IClientItemExtensions() {
            @Nullable
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity holder, InteractionHand hand, ItemStack stack) {
                return crossbowArmPose(stack,
                        holder.getUsedItemHand() == hand && holder.getUseItemRemainingTicks() > 0,
                        holder.swinging);
            }
        };
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            if (entry.tool().get() instanceof CrossbowItem) {
                event.registerItem(crossbow, entry.tool().get());
            } else if (entry.tool().get() instanceof ArmorPieceItem) {
                event.registerItem(armor, entry.tool().get());
            }
        }
    }

    private ForgeweaveItemClientExtensions() {}
}
