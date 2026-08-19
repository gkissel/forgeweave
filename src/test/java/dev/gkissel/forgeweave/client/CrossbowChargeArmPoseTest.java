package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #601 (1): the crossbow's cranking arms track <em>its</em> draw, not vanilla's.
 *
 * <p>Vanilla's {@code AnimationUtils#animateCrossbowCharge} divides the elapsed use ticks by
 * {@code net.minecraft.world.item.CrossbowItem.getChargeDuration}, a flat 25 ticks. A Forgeweave
 * crossbow draws in {@code drawTime = 45} scaled by the assembled tool's {@code drawSpeed}, so the
 * vanilla pacing ran out at tick 25 and froze on the last frame while the crank went on for another
 * twenty ticks (playtest 0.3.5-alpha.3, obs1). {@link CrossbowChargeArmPose} is the replacement; the
 * two halves it splits into -- how far along the crank is, and what the arms do at that fraction --
 * are both pure and pinned here.
 */
class CrossbowChargeArmPoseTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Vanilla's own charge duration, the number this animation used to be paced by. */
    private static final int VANILLA_CHARGE_TICKS = 25;

    private static ModelPart arm() {
        return new ModelPart(List.of(), Map.of());
    }

    /**
     * The whole bug in one assertion: a quarter of the way through vanilla's 25 ticks and a quarter
     * of the way through the crossbow's own 45 are different fractions, and at vanilla's 25 the real
     * crank is only just past halfway.
     */
    @Test
    void progressFollowsTheCrossbowsOwnDrawTime() {
        ItemStack crossbow = new ItemStack(ForgeweaveItems.TOOL_CROSSBOW.get());
        int drawTime = ForgeweaveItems.TOOL_CROSSBOW.get().drawTime();
        assertTrue(drawTime > VANILLA_CHARGE_TICKS, "the premise: the crossbow is slower than vanilla's crossbow");

        assertEquals(0.0F, CrossbowChargeArmPose.chargeProgress(crossbow, 0), 1.0E-5F);
        assertEquals(VANILLA_CHARGE_TICKS / (float) drawTime,
                CrossbowChargeArmPose.chargeProgress(crossbow, VANILLA_CHARGE_TICKS), 1.0E-5F,
                "at vanilla's full-charge tick the real crank is nowhere near done");
        assertEquals(1.0F, CrossbowChargeArmPose.chargeProgress(crossbow, drawTime), 1.0E-5F);
        assertEquals(1.0F, CrossbowChargeArmPose.chargeProgress(crossbow, drawTime * 2), 1.0E-5F,
                "and it holds at the end rather than running past it");
    }

    /** Anything that is not one of our bows contributes no crank at all. */
    @Test
    void aNonBowStackHasNoCrank() {
        assertEquals(0.0F, CrossbowChargeArmPose.chargeProgress(new ItemStack(Items.STICK), 10), 1.0E-5F);
        assertEquals(0.0F, CrossbowChargeArmPose.chargeProgress(ItemStack.EMPTY, 10), 1.0E-5F);
    }

    /**
     * And the pose itself is {@code AnimationUtils#animateCrossbowCharge} transcribed constant for
     * constant -- only the fraction it lerps on is ours. Right-handed: the right arm is the one
     * holding the crossbow steady, the left is the one winding it.
     */
    @Test
    void theArmsAreVanillasOwnCrankTranscribed() {
        ModelPart right = arm();
        ModelPart left = arm();

        CrossbowChargeArmPose.animate(right, left, HumanoidArm.RIGHT, 0.0F);
        assertEquals(-0.8F, right.yRot, 1.0E-5F, "the steady arm points across the body");
        assertEquals(-0.97079635F, right.xRot, 1.0E-5F);
        assertEquals(0.4F, left.yRot, 1.0E-5F, "the winding arm starts near the steady one");
        assertEquals(-0.97079635F, left.xRot, 1.0E-5F);

        CrossbowChargeArmPose.animate(right, left, HumanoidArm.RIGHT, 1.0F);
        assertEquals(-0.8F, right.yRot, 1.0E-5F, "the steady arm does not move over the crank");
        assertEquals(-0.97079635F, right.xRot, 1.0E-5F);
        assertEquals(0.85F, left.yRot, 1.0E-5F, "the winding arm has swung all the way out");
        assertEquals(-Mth.HALF_PI, left.xRot, 1.0E-5F);

        CrossbowChargeArmPose.animate(right, left, HumanoidArm.RIGHT, 0.5F);
        assertEquals(Mth.lerp(0.5F, 0.4F, 0.85F), left.yRot, 1.0E-5F, "and it lerps in between");
    }

    /** Left-handed is the same crank mirrored, exactly as vanilla mirrors it. */
    @Test
    void theLeftHandedCrankIsMirrored() {
        ModelPart right = arm();
        ModelPart left = arm();

        CrossbowChargeArmPose.animate(right, left, HumanoidArm.LEFT, 1.0F);
        assertEquals(0.8F, left.yRot, 1.0E-5F, "the left arm is now the steady one");
        assertEquals(-0.97079635F, left.xRot, 1.0E-5F);
        assertEquals(-0.85F, right.yRot, 1.0E-5F, "and the right one winds, the other way about");
        assertEquals(-Mth.HALF_PI, right.xRot, 1.0E-5F);
    }
}
