package dev.gkissel.forgeweave.client;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

import dev.gkissel.forgeweave.item.BowItem;

/**
 * The crossbow's cranking arm pose, paced by the crossbow's own draw (issue #601, playtest
 * 0.3.5-alpha.3 obs1). {@link ForgeweaveItemClientExtensions} is what hands this pose out; this is
 * what the pose <em>does</em>.
 *
 * <h2>Why not vanilla's {@code CROSSBOW_CHARGE}</h2>
 *
 * <p>Issue #425 gave the crossbow vanilla's own {@code ArmPose.CROSSBOW_CHARGE}, whose animation is
 * {@code AnimationUtils#animateCrossbowCharge}: it lerps the winding arm over
 * {@code net.minecraft.world.item.CrossbowItem.getChargeDuration}, a flat 25 ticks (1.25s times 20,
 * before quick-charge enchantments). A Forgeweave crossbow draws in {@link BowItem#drawTime()} = 45
 * ticks scaled by the assembled tool's {@code drawSpeed}, so the arms finished winding at tick 25
 * and then froze for the remaining twenty while the crank -- and the model's own {@code pull}
 * overrides -- carried on. That was #425's one documented cosmetic deviation; this closes it.
 *
 * <h2>How</h2>
 *
 * <p>{@code AnimationUtils#animateCrossbowCharge} is a static call inside
 * {@code HumanoidModel#poseRightArm}'s {@code switch}, and nothing fires between
 * {@code HumanoidModel#setupAnim} and the model being drawn ({@code RenderLivingEvent.Pre} is ahead
 * of {@code setupAnim}, {@code Post} is behind the draw), so there is no event at which the arms
 * could be corrected after the fact. What there <em>is</em> is the {@code default:} arm of that same
 * switch, which calls {@code ArmPose#applyTransform} -- the hook NeoForge added for exactly this by
 * making {@code HumanoidModel.ArmPose} an extensible enum. So this registers one extra constant
 * ({@code META-INF/enumextensions.json}, {@code FORGEWEAVE_CROSSBOW_CHARGE}) carrying
 * {@link #animate} as its {@link IArmPoseTransformer}, and {@link #animate} is vanilla's own crank
 * transcribed constant for constant with only its <em>fraction</em> replaced.
 *
 * <p>No mixin, and nothing here loads on a dedicated server: {@code HumanoidModel.ArmPose} is a
 * client class, so the enum is only ever extended where it is loaded, and {@link #PROXY} resolves
 * its value lazily.
 */
public final class CrossbowChargeArmPose {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The extra {@code HumanoidModel.ArmPose} constant, two-handed like the {@code CROSSBOW_CHARGE}
     * it replaces so {@code HumanoidModel#setupAnim} still sequences it after the idle arm.
     *
     * <p>Public and non-final-looking on purpose: {@code META-INF/enumextensions.json} names this
     * field, and FML reads it reflectively when it extends the enum.
     */
    public static final EnumProxy<HumanoidModel.ArmPose> PROXY =
            new EnumProxy<>(HumanoidModel.ArmPose.class, true, (IArmPoseTransformer) CrossbowChargeArmPose::apply);

    private static HumanoidModel.ArmPose resolved;

    /**
     * The pose constant, resolved once.
     *
     * <p>The fallback is not decoration: if the enum extension ever fails to apply, an unresolvable
     * proxy would otherwise throw once per rendered frame. Vanilla's {@code CROSSBOW_CHARGE} is the
     * same pose at the wrong speed -- the exact behaviour this ticket found, which is a far better
     * failure than a crash -- so it is what a broken extension degrades to, loudly.
     */
    static synchronized HumanoidModel.ArmPose pose() {
        if (resolved == null) {
            try {
                resolved = PROXY.getValue();
            } catch (RuntimeException | LinkageError failure) {
                LOGGER.error("Forgeweave's crossbow arm pose was not registered; the cranking animation "
                        + "falls back to vanilla's 25-tick pacing", failure);
                resolved = HumanoidModel.ArmPose.CROSSBOW_CHARGE;
            }
        }
        return resolved;
    }

    /** {@code IArmPoseTransformer#applyTransform}: read the crank's progress, then pose the arms. */
    private static void apply(HumanoidModel<?> model, LivingEntity holder, HumanoidArm arm) {
        animate(model.rightArm, model.leftArm, arm, chargeProgress(holder.getUseItem(), holder.getTicksUsingItem()));
    }

    /**
     * How far along the crank is: {@link BowItem#drawbackProgress(ItemStack, int)}, the same
     * {@code BowCore#getDrawbackProgress} the model's {@code pull} property reads, so the arms and
     * the limbs move together and both respond to the assembled tool's {@code drawSpeed} (and so to
     * haste and lightweight, which vanilla's fixed 25 ticks could not see either).
     *
     * @param using the stack being used, {@code LivingEntity#getUseItem}
     * @param ticksUsingItem elapsed use ticks, {@code LivingEntity#getTicksUsingItem}
     * @return the crank fraction in {@code [0, 1]}, or 0 for a stack that is not one of our bows
     */
    static float chargeProgress(ItemStack using, int ticksUsingItem) {
        return using.getItem() instanceof BowItem bow ? bow.drawbackProgress(using, ticksUsingItem) : 0.0F;
    }

    /**
     * {@code AnimationUtils#animateCrossbowCharge}, transcribed: the arm on {@code arm}'s side holds
     * the crossbow steady across the body while the other winds out from {@code 0.4} to {@code 0.85}
     * yaw and down to a right angle. Every constant is vanilla's; only {@code progress} -- which
     * vanilla recomputes from its own 25-tick charge duration -- is supplied.
     *
     * @param arm the hand the crossbow is in, so the steady arm and the winding one swap for a
     *     left-handed crank exactly as vanilla's {@code rightHanded} flag makes them
     */
    static void animate(ModelPart rightArm, ModelPart leftArm, HumanoidArm arm, float progress) {
        boolean rightHanded = arm == HumanoidArm.RIGHT;
        ModelPart steady = rightHanded ? rightArm : leftArm;
        ModelPart winding = rightHanded ? leftArm : rightArm;
        steady.yRot = rightHanded ? -0.8F : 0.8F;
        steady.xRot = -0.97079635F;
        winding.yRot = Mth.lerp(progress, 0.4F, 0.85F) * (rightHanded ? 1 : -1);
        winding.xRot = Mth.lerp(progress, -0.97079635F, -Mth.HALF_PI);
    }

    private CrossbowChargeArmPose() {}
}
