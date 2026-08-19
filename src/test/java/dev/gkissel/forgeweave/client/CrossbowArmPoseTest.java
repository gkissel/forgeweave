package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;

import dev.gkissel.forgeweave.item.CrossbowItem;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #425: the crossbow's third-person arm pose. Vanilla's own crossbow poses are hardcoded to
 * {@code Items.CROSSBOW}, so a Forgeweave crossbow was cranked and carried with the plain
 * {@code ArmPose.ITEM} -- no cranking animation, and a loaded one indistinguishable from an empty
 * one to everybody but its holder. {@link ForgeweaveItemClientExtensions#crossbowArmPose} is the
 * replacement, and this pins its branches plus the use animation it deliberately leaves alone.
 */
class CrossbowArmPoseTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack crossbow(boolean loaded) {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_CROSSBOW.get());
        if (loaded) {
            CrossbowItem.setLoaded(stack, true);
        }
        return stack;
    }

    /**
     * Mid-crank: the cranking pose, which since issue #601 is {@link CrossbowChargeArmPose}'s rather
     * than vanilla's {@code CROSSBOW_CHARGE} -- same arms, paced by the crossbow's own draw. Outside
     * a loaded game the enum extension is not applied and {@code pose()} degrades to vanilla's, which
     * is exactly what this asserts is handed out either way.
     */
    @Test
    void crankingGetsTheChargePose() {
        assertEquals(CrossbowChargeArmPose.pose(),
                ForgeweaveItemClientExtensions.crossbowArmPose(crossbow(false), true, false));
        assertEquals(CrossbowChargeArmPose.pose(),
                ForgeweaveItemClientExtensions.crossbowArmPose(crossbow(true), true, false),
                "a crossbow being cranked again is still being cranked");
    }

    /** Crank stored, hands idle: shouldered, the pose that makes a loaded crossbow read as loaded. */
    @Test
    void aStoredCrankGetsTheHoldPose() {
        assertEquals(HumanoidModel.ArmPose.CROSSBOW_HOLD,
                ForgeweaveItemClientExtensions.crossbowArmPose(crossbow(true), false, false));
    }

    /**
     * Swinging beats the shouldered pose, exactly as vanilla's own {@code !player.swinging} guard
     * does: an arm mid-swing has somewhere else to be.
     */
    @Test
    void swingingBeatsTheHoldPose() {
        assertNull(ForgeweaveItemClientExtensions.crossbowArmPose(crossbow(true), false, true));
    }

    /** An empty crossbow just being carried is an ordinary held item; leave it to vanilla. */
    @Test
    void anEmptyCarriedCrossbowKeepsTheDefaultPose() {
        assertNull(ForgeweaveItemClientExtensions.crossbowArmPose(crossbow(false), false, false));
    }

    /**
     * The use animation stays {@code NONE} ({@code CrossBow#getItemUseAction}, 1.12 parity). This is
     * a guard, not a leftover: vanilla's {@code ItemInHandRenderer} switch has <em>no</em>
     * {@code CROSSBOW} case at all -- that first-person animation is reached only through its
     * {@code instanceof net.minecraft.world.item.CrossbowItem} special case -- so a Forgeweave
     * crossbow declaring {@code UseAnim.CROSSBOW} would fall out of the switch with no
     * {@code applyItemArmTransform} applied and render its first-person model detached from the
     * hand, losing issue #413's cranking poses. The third-person pose comes from
     * {@link ForgeweaveItemClientExtensions} instead, which costs first person nothing.
     */
    @Test
    void theUseAnimationStaysNone() {
        assertEquals(UseAnim.NONE, crossbow(false).getUseAnimation());
        assertEquals(UseAnim.NONE, crossbow(true).getUseAnimation());
    }
}
