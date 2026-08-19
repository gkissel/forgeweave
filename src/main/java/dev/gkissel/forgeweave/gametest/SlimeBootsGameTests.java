package dev.gkissel.forgeweave.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.SlimeBounceHandler;

/**
 * Parity audit T21, issue #452: upstream 1.12's {@code ItemSlimeBoots#onFall} and
 * {@code SlimeBounceHandler}.
 *
 * <p>Only the server half of the bounce is observable here. Upstream splits the handler by side --
 * the client sets the rebound velocity because player movement is client-authoritative, the server
 * cancels the fall damage -- and a GameTest is the server. So these tests drive the real
 * {@code causeFallDamage} path (which is what fires {@code LivingFallEvent}) and check what the
 * server side is responsible for: no damage on a bounced landing, a fifth of it on a crouched one,
 * and the bounce handler tracking then releasing the wearer.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SlimeBootsGameTests {

    /** Well past vanilla's three-block free fall, so a barefoot control takes real damage. */
    private static final float LONG_FALL = 10.0F;

    @GameTest(template = "empty")
    public static void aBootedLandingCostsNoFallDamage(GameTestHelper helper) {
        Player player = booted(helper);
        float before = player.getHealth();

        player.causeFallDamage(LONG_FALL, 1.0F, player.damageSources().fall());

        helper.assertTrue(player.getHealth() == before,
                "slime boots must cancel the fall damage entirely, took " + (before - player.getHealth()));
        helper.assertTrue(player.fallDistance == 0.0F,
                "a bounced landing must reset fallDistance, got " + player.fallDistance);
        helper.succeed();
    }

    /** The control: the same fall without the boots has to actually hurt, or the test above proves nothing. */
    @GameTest(template = "empty")
    public static void thatSameFallHurtsWithoutTheBoots(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        float before = player.getHealth();

        player.causeFallDamage(LONG_FALL, 1.0F, player.damageSources().fall());

        helper.assertTrue(player.getHealth() < before, "a barefoot ten-block fall must hurt");
        helper.succeed();
    }

    /** Upstream's {@code else if(!isClient && entity.isSneaking())}: a fifth of the damage, no bounce. */
    @GameTest(template = "empty")
    public static void crouchingThroughTheLandingTakesAFifthOfTheDamage(GameTestHelper helper) {
        Player barefoot = helper.makeMockPlayer(GameType.SURVIVAL);
        float barefootBefore = barefoot.getHealth();
        barefoot.causeFallDamage(LONG_FALL, 1.0F, barefoot.damageSources().fall());
        float full = barefootBefore - barefoot.getHealth();

        Player player = booted(helper);
        player.setShiftKeyDown(true);
        float before = player.getHealth();
        player.causeFallDamage(LONG_FALL, 1.0F, player.damageSources().fall());
        float crouched = before - player.getHealth();

        helper.assertTrue(crouched > 0.0F, "a crouched landing must still hurt, took nothing");
        helper.assertTrue(crouched < full,
                "a crouched landing must hurt less than a barefoot one, took " + crouched + " of " + full);
        helper.assertFalse(SlimeBounceHandler.isBouncing(player), "a crouched landing must not bounce");
        helper.succeed();
    }

    /** Upstream's {@code event.getDistance() > 2}: shorter drops are left to vanilla entirely. */
    @GameTest(template = "empty")
    public static void aDropOfTwoBlocksOrLessDoesNotBounce(GameTestHelper helper) {
        Player player = booted(helper);

        player.causeFallDamage(2.0F, 1.0F, player.damageSources().fall());

        helper.assertFalse(SlimeBounceHandler.isBouncing(player), "a two-block drop must not start a bounce");
        helper.succeed();
    }

    /**
     * Upstream's {@code SlimeBounceHandler#playerTickPost} lifecycle: the wearer stays tracked while
     * airborne and is released once they have been back on the ground for more than five ticks.
     */
    @GameTest(template = "empty")
    public static void theBounceHandlerHoldsTheWearerUntilTheyAreBackOnTheGround(GameTestHelper helper) {
        Player player = booted(helper);
        player.causeFallDamage(LONG_FALL, 1.0F, player.damageSources().fall());
        helper.assertTrue(SlimeBounceHandler.isBouncing(player), "a bounced landing must register the bounce handler");

        player.setOnGround(false);
        tick(player, 10);
        helper.assertTrue(SlimeBounceHandler.isBouncing(player), "the handler must keep hold of an airborne wearer");

        player.setOnGround(true);
        tick(player, 10);
        helper.assertFalse(SlimeBounceHandler.isBouncing(player),
                "the handler must let go once the wearer has been grounded for more than five ticks");
        helper.succeed();
    }

    /** Upstream returns an empty attribute multimap on purpose -- the boots grant no armour at all. */
    @GameTest(template = "empty")
    public static void theBootsGrantNoArmourAndAreWornOnTheFeet(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack boots = new ItemStack(ForgeweaveItems.SLIME_BOOTS.get());

        helper.assertTrue(boots.getAttributeModifiers().modifiers().isEmpty(),
                "the slime boots must carry no attribute modifiers, got " + boots.getAttributeModifiers().modifiers());
        helper.assertTrue(player.getEquipmentSlotForItem(boots) == EquipmentSlot.FEET,
                "the slime boots must be worn on the feet, got " + player.getEquipmentSlotForItem(boots));
        helper.assertTrue(boots.getMaxStackSize() == 1, "the slime boots must not stack");
        helper.succeed();
    }

    private static Player booted(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.FEET, new ItemStack(ForgeweaveItems.SLIME_BOOTS.get()));
        return player;
    }

    private static void tick(Player player, int ticks) {
        for (int i = 0; i < ticks; i++) {
            player.tickCount++;
            SlimeBounceHandler.onPlayerTickPost(new PlayerTickEvent.Post(player));
        }
    }

    private SlimeBootsGameTests() {}
}
