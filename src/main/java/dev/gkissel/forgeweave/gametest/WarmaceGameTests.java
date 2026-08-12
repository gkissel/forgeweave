package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * docs/SCOPE.md M3 issue #161's verification: the warmace assembles only at the Tool Forge, its
 * smash bonus applies after a fall and not from the ground, and a landed smash negates the
 * attacker's fall damage.
 *
 * <p>The smash tests call the item's own vanilla hooks rather than staging a swing, because that is
 * exactly where the behavior lives: {@code Player#attack} calls
 * {@code Item#getAttackDamageBonus} on every item unconditionally and {@code Item#postHurtEnemy}
 * on every hit that lands, and {@code WarmaceItem} routes both to vanilla's mace. Testing the hooks
 * therefore tests the same code the game runs, without needing a swing's cooldown and crit maths to
 * come out a particular way first. {@link #smashSetsVanillasFallDamageImmunity} does stage a real
 * hit through a real {@code ServerPlayer}, since vanilla's smash side effects only run for one.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class WarmaceGameTests {

    /** Vanilla's {@code MaceItem.SMASH_ATTACK_FALL_THRESHOLD}: a smash needs more than this. */
    private static final float SMASH_THRESHOLD = 1.5F;

    /**
     * Materials in {@link ToolConstants#WARMACE}'s own part order -- handle, head, binding -- which
     * is the order the station's slots take them in since issue #155.
     */
    private static final List<String> STONE_HEAD = List.of("wood", "stone", "wood");

    private static ToolAssemblyRecipes.Entry entry() {
        return ToolAssembly.entryFor(ForgeweaveItems.TOOL_WARMACE.get());
    }

    /** Assembles a warmace from a stone head and wood rod/binding at a Tool Forge. */
    private static ItemStack warmace(GameTestHelper helper, Player player, BlockPos pos) {
        return ToolAssembly.assembleAtForge(helper, player, pos, entry(), STONE_HEAD);
    }

    /**
     * Tool Forge tier (docs/SCOPE.md M3, issue #152's {@code large_tools} gate) and the stat
     * constants issue #153 landed: a stone head's 3.0 attack through
     * {@link ToolConstants#WARMACE}'s flat +4.0 and 1.0 damage potential is 7.0, at 0.8 attacks per
     * second. Neither of wood's or stone's traits touches an attack stat, so those two numbers are
     * the constants and nothing else.
     */
    @GameTest(template = "empty")
    public static void warmaceAssemblesOnlyAtTheToolForge(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack fromStation = ToolAssembly.assemble(helper, player, pos, entry(), STONE_HEAD);
        helper.assertTrue(fromStation.isEmpty(),
                "the Tool Station must refuse a warmace (a large tool), got " + fromStation);

        ItemStack warmace = warmace(helper, player, pos);
        helper.assertTrue(warmace.is(ForgeweaveItems.TOOL_WARMACE.get()),
                "expected the Tool Forge to assemble a warmace, got " + warmace);

        assertModifier(helper, warmace, Attributes.ATTACK_DAMAGE.getKey().location().toString(), 7.0);
        // Vanilla's ATTACK_SPEED base is 4 attacks/second and the item modifier is the offset.
        assertModifier(helper, warmace, Attributes.ATTACK_SPEED.getKey().location().toString(),
                ToolConstants.WARMACE.attackSpeed() - 4.0);

        helper.succeed();
    }

    /**
     * The smash bonus itself, straight off vanilla's curve: nothing from the ground or at exactly
     * the threshold, {@code 4 x fallDistance} below three blocks, {@code 12 + 2 x (d - 3)} up to
     * eight. A Broken warmace adds nothing at all, matching every other attack stat it loses.
     *
     * <p>These are vanilla's numbers, asserted here rather than in {@code WarmaceItem} precisely
     * because Forgeweave does not own them -- if a future Minecraft reshapes the curve this test is
     * what says so, and the answer will be to update the expectations, never the item.
     */
    @GameTest(template = "empty")
    public static void smashBonusAppliesAfterAFallAndNotFromTheGround(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack warmace = warmace(helper, player, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, warmace);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        assertSmashBonus(helper, player, target, source, 0.0F, 0.0F, "standing on the ground");
        assertSmashBonus(helper, player, target, source, SMASH_THRESHOLD, 0.0F, "at exactly the smash threshold");
        assertSmashBonus(helper, player, target, source, 2.0F, 8.0F, "after a two-block fall");
        assertSmashBonus(helper, player, target, source, 6.0F, 18.0F, "after a six-block fall");

        warmace.set(ForgeweaveDataComponents.BROKEN.get(), true);
        assertSmashBonus(helper, player, target, source, 6.0F, 0.0F, "with a Broken warmace");

        target.discard();
        helper.succeed();
    }

    /**
     * The fall-damage negation: a smash that lands resets the attacker's fall distance, which is
     * what vanilla's own {@code MaceItem#postHurtEnemy} does and therefore what keeps the attacker
     * from being hurt by the landing. A blow struck from the ground leaves the (already zero) fall
     * distance alone, and one struck below the threshold leaves it untouched rather than clearing
     * it -- the negation is the smash's, not every hit's.
     */
    @GameTest(template = "empty")
    public static void smashNegatesTheAttackersFallDamage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack warmace = warmace(helper, player, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, warmace);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        player.fallDistance = 6.0F;
        warmace.getItem().postHurtEnemy(warmace, target, player);
        helper.assertTrue(player.fallDistance == 0.0F,
                "a landed smash must negate the attacker's fall damage by resetting its fall distance, got "
                        + player.fallDistance);

        player.fallDistance = SMASH_THRESHOLD;
        warmace.getItem().postHurtEnemy(warmace, target, player);
        helper.assertTrue(player.fallDistance == SMASH_THRESHOLD,
                "a hit below the smash threshold must leave the fall distance alone, got " + player.fallDistance);

        target.discard();
        helper.succeed();
    }

    /**
     * The whole smash through a real hit: vanilla's {@code MaceItem#hurtEnemy} only runs its side
     * effects for a {@code ServerPlayer}, and the one this asserts on --
     * {@code setIgnoreFallDamageFromCurrentImpulse(true)} plus the recorded impulse position -- is
     * the wind-charge synergy the maintainer decision pins to "matches vanilla exactly": it is the
     * same flag a wind charge sets when it launches a player, which is why a warmace smash out of a
     * wind-charge launch lands unhurt.
     */
    @GameTest(template = "empty")
    public static void smashSetsVanillasFallDamageImmunity(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack warmace = warmace(helper, player, pos);
        player.setItemInHand(InteractionHand.MAIN_HAND, warmace);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        player.fallDistance = 6.0F;
        helper.assertFalse(player.isIgnoringFallDamageFromCurrentImpulse(),
                "the fixture must start without vanilla's impulse immunity, or this proves nothing");

        helper.assertTrue(warmace.getItem().hurtEnemy(warmace, target, player),
                "an assembled, unbroken warmace must accept the hit");
        helper.assertTrue(player.isIgnoringFallDamageFromCurrentImpulse(),
                "a smash must set vanilla's ignore-fall-damage-from-impulse flag");
        helper.assertTrue(player.currentImpulseImpactPos != null,
                "a smash must record vanilla's impulse impact position");

        target.discard();
        helper.succeed();
    }

    /** Sets {@code fallDistance} and checks what the warmace adds to a blow from there. */
    private static void assertSmashBonus(GameTestHelper helper, Player player, Pig target, DamageSource source,
            float fallDistance, float expected, String situation) {
        player.fallDistance = fallDistance;
        ItemStack weapon = player.getMainHandItem();
        float bonus = weapon.getItem().getAttackDamageBonus(target, 0.0F, source);
        helper.assertTrue(Math.abs(bonus - expected) < 0.001F,
                "expected a smash bonus of " + expected + " " + situation + ", got " + bonus);
    }

    /** The single {@code ADD_VALUE} modifier {@code tool} carries for the named vanilla attribute. */
    private static void assertModifier(GameTestHelper helper, ItemStack tool, String attribute, double expected) {
        Double found = null;
        for (ItemAttributeModifiers.Entry entry : tool.getAttributeModifiers().modifiers()) {
            if (entry.attribute().getRegisteredName().equals(attribute)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                found = entry.modifier().amount();
            }
        }
        helper.assertTrue(found != null, "the warmace carries no " + attribute + " modifier");
        helper.assertTrue(Math.abs(found - expected) < 0.001,
                "expected the warmace's " + attribute + " modifier to read " + expected + ", got " + found);
    }
}
