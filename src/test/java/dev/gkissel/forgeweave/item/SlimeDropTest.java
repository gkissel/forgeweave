package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.block.SlimeColour;

/**
 * Issue #649 (parity audit T57): pins the five slime drops to upstream 1.12's
 * {@code TinkerCommons:373-377}, which registers them through Mantle's
 * {@code ItemEdible#addFood(meta, food, saturation, name, effects...)} behind its Gadgets pulse.
 * Nutrition, saturation and each drop's single potion effect come straight off those five lines;
 * the always-edible flag comes from {@code addFood}'s own {@code alwaysEdible = effects.length > 0},
 * which every one of the five trips.
 *
 * <p>Unlike the slime balls, green gets an item of its own -- vanilla has no slime drop for it to
 * defer to.
 */
class SlimeDropTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Upstream registers exactly five, green included ({@code slimedropGreen} at meta 30). */
    @Test
    void thereAreFiveSlimeDropsGreenIncluded() {
        assertEquals(List.of(SlimeColour.GREEN, SlimeColour.BLUE, SlimeColour.PURPLE,
                        SlimeColour.BLOOD, SlimeColour.MAGMA),
                ForgeweaveItems.slimeDrops().stream().map(ForgeweaveItems.SlimeDrop::colour).toList());
    }

    @Test
    void everySlimeDropCarriesUpstreamsFoodValuesAndEffect() {
        // addFood(30, 1, 1f, "slimedrop_green", SPEED 90s x3)
        assertFood(SlimeColour.GREEN, 1, 1f, List.of(effect(MobEffects.MOVEMENT_SPEED, 20 * 90, 2)));
        // addFood(31, 3, 1f, "slimedrop_blue", JUMP_BOOST 90s x3)
        assertFood(SlimeColour.BLUE, 3, 1f, List.of(effect(MobEffects.JUMP, 20 * 90, 2)));
        // addFood(32, 3, 2f, "slimedrop_purple", LUCK 90s)
        assertFood(SlimeColour.PURPLE, 3, 2f, List.of(effect(MobEffects.LUCK, 20 * 90, 0)));
        // addFood(33, 3, 1.5f, "slimedrop_blood", HEALTH_BOOST 90s)
        assertFood(SlimeColour.BLOOD, 3, 1.5f, List.of(effect(MobEffects.HEALTH_BOOST, 20 * 90, 0)));
        // addFood(34, 6, 1f, "slimedrop_magma", FIRE_RESISTANCE 90s)
        assertFood(SlimeColour.MAGMA, 6, 1f, List.of(effect(MobEffects.FIRE_RESISTANCE, 20 * 90, 0)));
    }

    private record Effect(Holder<MobEffect> effect, int duration, int amplifier) {}

    private static Effect effect(Holder<MobEffect> effect, int duration, int amplifier) {
        return new Effect(effect, duration, amplifier);
    }

    /** Same expectation shape as {@code ColouredSlimeBallTest}: 1.21 saturation = n * modifier * 2. */
    private static void assertFood(SlimeColour colour, int nutrition, float saturationModifier, List<Effect> expected) {
        FoodProperties food = new ItemStack(ForgeweaveItems.slimeDrop(colour).get()).getFoodProperties(null);
        assertEquals(nutrition, food.nutrition(), colour + " nutrition");
        assertEquals(nutrition * saturationModifier * 2F, food.saturation(), 1e-4, colour + " saturation");
        assertTrue(food.canAlwaysEat(), colour + " should be always edible (upstream's addFood does)");
        assertEquals(expected, food.effects().stream()
                        .map(possible -> new Effect(possible.effect().getEffect(),
                                possible.effect().getDuration(), possible.effect().getAmplifier()))
                        .toList(),
                colour + " effects");
        assertTrue(food.effects().stream().allMatch(possible -> possible.probability() == 1.0F),
                colour + " effect is certain upstream (ItemEdible#onFoodEaten applies it unconditionally)");
    }
}
