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
 * Issue #635 (parity audit T57): pins the five coloured slime balls to upstream 1.12's
 * {@code TinkerCommons:140-144}, which registers them through Mantle's
 * {@code ItemEdible#addFood(meta, food, saturation, name, effects...)}. Nutrition, saturation and
 * every potion effect's identity, duration and amplifier come straight off those five lines; the
 * always-edible flag comes from {@code addFood}'s own {@code alwaysEdible = effects.length > 0},
 * which every one of the five trips.
 */
class ColouredSlimeBallTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Upstream registers exactly five; green is vanilla's own slime ball and gets no item here. */
    @Test
    void thereAreFiveColouredSlimeBallsAndNoGreenOne() {
        assertEquals(List.of(SlimeColour.BLUE, SlimeColour.PURPLE, SlimeColour.BLOOD,
                        SlimeColour.MAGMA, SlimeColour.PINK),
                ForgeweaveItems.slimeBalls().stream().map(ForgeweaveItems.SlimeBall::colour).toList());
        assertEquals(net.minecraft.world.item.Items.SLIME_BALL, ForgeweaveItems.slimeBall(SlimeColour.GREEN));
    }

    @Test
    void everySlimeBallCarriesUpstreamsFoodValuesAndEffects() {
        // addFood(1, 1, 1f, "slimeball_blue", SLOWNESS 45s x3, JUMP_BOOST 60s x3)
        assertFood(SlimeColour.BLUE, 1, 1f, List.of(
                effect(MobEffects.MOVEMENT_SLOWDOWN, 20 * 45, 2),
                effect(MobEffects.JUMP, 20 * 60, 2)));
        // addFood(2, 1, 2f, "slimeball_purple", UNLUCK 45s, LUCK 60s)
        assertFood(SlimeColour.PURPLE, 1, 2f, List.of(
                effect(MobEffects.UNLUCK, 20 * 45, 0),
                effect(MobEffects.LUCK, 20 * 60, 0)));
        // addFood(3, 1, 1.5f, "slimeball_blood", POISON 45s x3, HEALTH_BOOST 60s)
        assertFood(SlimeColour.BLOOD, 1, 1.5f, List.of(
                effect(MobEffects.POISON, 20 * 45, 2),
                effect(MobEffects.HEALTH_BOOST, 20 * 60, 0)));
        // addFood(4, 2, 1f, "slimeball_magma", WEAKNESS 45s, WITHER 15s, FIRE_RESISTANCE 60s)
        assertFood(SlimeColour.MAGMA, 2, 1f, List.of(
                effect(MobEffects.WEAKNESS, 20 * 45, 0),
                effect(MobEffects.WITHER, 20 * 15, 0),
                effect(MobEffects.FIRE_RESISTANCE, 20 * 60, 0)));
        // addFood(5, 1, 1f, "slimeball_pink", NAUSEA 10s x3)
        assertFood(SlimeColour.PINK, 1, 1f, List.of(
                effect(MobEffects.CONFUSION, 20 * 10, 2)));
    }

    private record Effect(Holder<MobEffect> effect, int duration, int amplifier) {}

    private static Effect effect(Holder<MobEffect> effect, int duration, int amplifier) {
        return new Effect(effect, duration, amplifier);
    }

    /**
     * {@code saturationModifier} is upstream's number; 1.21's {@code FoodProperties#saturation} is
     * the absolute value the builder derives from it, and both generations use the same
     * {@code nutrition * modifier * 2} formula, so that is what this expects.
     */
    private static void assertFood(SlimeColour colour, int nutrition, float saturationModifier, List<Effect> expected) {
        FoodProperties food = new ItemStack(ForgeweaveItems.slimeBall(colour)).getFoodProperties(null);
        assertEquals(nutrition, food.nutrition(), colour + " nutrition");
        assertEquals(nutrition * saturationModifier * 2F, food.saturation(), 1e-4, colour + " saturation");
        assertTrue(food.canAlwaysEat(), colour + " should be always edible (upstream's addFood does)");
        assertEquals(expected, food.effects().stream()
                        .map(possible -> new Effect(possible.effect().getEffect(),
                                possible.effect().getDuration(), possible.effect().getAmplifier()))
                        .toList(),
                colour + " effects");
        assertTrue(food.effects().stream().allMatch(possible -> possible.probability() == 1.0F),
                colour + " effects are certain upstream (ItemEdible#onFoodEaten applies them unconditionally)");
    }
}
