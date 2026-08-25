package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.ItemLike;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.SlimeColour;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #731 (playtest beta.1 s29): eating a coloured slime food applies <em>exactly</em> the
 * effects upstream 1.12 lists for it, nothing more. The balls are {@code TinkerCommons:293-297}
 * (each a positive effect paired with a shorter negative one -- upstream's own design, kept), the
 * drops {@code TinkerCommons:373-377} (one positive effect each, no side effect). Each is eaten
 * through the real {@code Item#finishUsingItem} path and the player's active effects read back, so
 * a stray effect in either list fails here.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SlimeFoodGameTests {

    @GameTest(template = "empty")
    public static void eatingEachSlimeBallAppliesExactlyUpstreamsEffects(GameTestHelper helper) {
        assertEats(helper, ForgeweaveItems.slimeBall(SlimeColour.BLUE), Set.of(MobEffects.MOVEMENT_SLOWDOWN, MobEffects.JUMP));
        assertEats(helper, ForgeweaveItems.slimeBall(SlimeColour.PURPLE), Set.of(MobEffects.UNLUCK, MobEffects.LUCK));
        assertEats(helper, ForgeweaveItems.slimeBall(SlimeColour.BLOOD), Set.of(MobEffects.POISON, MobEffects.HEALTH_BOOST));
        assertEats(helper, ForgeweaveItems.slimeBall(SlimeColour.MAGMA), Set.of(MobEffects.WEAKNESS, MobEffects.WITHER, MobEffects.FIRE_RESISTANCE));
        assertEats(helper, ForgeweaveItems.slimeBall(SlimeColour.PINK), Set.of(MobEffects.CONFUSION));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eatingEachSlimeDropAppliesOnlyItsSinglePositiveEffect(GameTestHelper helper) {
        assertEats(helper, ForgeweaveItems.slimeDrop(SlimeColour.GREEN), Set.of(MobEffects.MOVEMENT_SPEED));
        assertEats(helper, ForgeweaveItems.slimeDrop(SlimeColour.BLUE), Set.of(MobEffects.JUMP));
        assertEats(helper, ForgeweaveItems.slimeDrop(SlimeColour.PURPLE), Set.of(MobEffects.LUCK));
        assertEats(helper, ForgeweaveItems.slimeDrop(SlimeColour.BLOOD), Set.of(MobEffects.HEALTH_BOOST));
        assertEats(helper, ForgeweaveItems.slimeDrop(SlimeColour.MAGMA), Set.of(MobEffects.FIRE_RESISTANCE));
        helper.succeed();
    }

    /** A fresh player per food, so one bite's effects never leak into the next assertion. */
    private static void assertEats(GameTestHelper helper, ItemLike food, Set<Holder<MobEffect>> expected) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack stack = new ItemStack(food);
        stack.getItem().finishUsingItem(stack, helper.getLevel(), player);
        Set<Holder<MobEffect>> actual = player.getActiveEffects().stream()
                .map(instance -> instance.getEffect()).collect(Collectors.toSet());
        helper.assertTrue(actual.equals(expected), food.asItem() + " should apply exactly "
                + names(expected) + " when eaten, applied " + names(actual));
        player.discard();
    }

    private static List<String> names(Set<Holder<MobEffect>> effects) {
        return effects.stream().map(Holder::getRegisteredName).sorted().toList();
    }
}
