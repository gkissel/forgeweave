package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.tool.ModifierArt;

/**
 * Parity audit T25 (issue #456): the constants behind the glowing modifier, pinned against the 1.12
 * clone the same way {@code ModifierBatch2Test} pins its batch. The behavior itself needs a world --
 * light levels and a block placement -- so it lives in {@code gametest.GlowingGameTests}.
 *
 * <p>Verified against the clone at {@code c01173c0408352c50a2e8c5017552323ce42f5b4}:
 * {@code ModGlowing} extends {@code ModifierTrait("glowing", 0xffffaa)} with {@code maxLevel} 0,
 * which wires {@code DataAspect + freeModifier} -- one application, one slot -- and
 * {@code TinkerModifiers:163} binds it to {@code ItemCombination(1, glowstoneDust, ENDER_EYE,
 * glowstoneDust)}.
 */
class GlowingTest {

    private static final ResourceLocation GLOWING = ResourceLocation.fromNamespaceAndPath("forgeweave", "glowing");

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static ModifierRecipe shippedRecipe(String path) {
        JsonElement json;
        try (InputStream in = GlowingTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
        return ModifierRecipe.CODEC.parse(ops, json).getOrThrow();
    }

    /**
     * Issue #780: upstream's real three-item combination, restored now that issue #776 gave
     * {@link ModifierRecipe} a multi-item {@code require_all_reagents} form -- two glowstone dust and
     * one ender eye, all three required at once. {@code beheading.json} still ships its own
     * single-item reduction of a genuine upstream combo (see {@code ForgeweaveModifiers.BEHEADING}'s
     * javadoc for why it isn't converted the same way).
     */
    @Test
    void theShippedRecipeIsTwoGlowstoneDustAndAnEnderEyeAppliedOnce() {
        ModifierRecipe recipe = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/glowing.json");

        assertEquals(GLOWING, recipe.modifier());
        assertEquals(1, recipe.cost());
        assertEquals(1, recipe.maxLevel(), "upstream's DataAspect is one application, never a second");
        assertTrue(recipe.requireAllReagents(), "dust and eye are both required, not alternatives");
        assertTrue(recipe.matches(new ItemStack(Items.ENDER_EYE)));
        assertTrue(recipe.matches(new ItemStack(Items.GLOWSTONE_DUST)));
        assertFalse(recipe.isSatisfiedBy(List.of(new ItemStack(Items.ENDER_EYE))),
                "an ender eye alone is not the combo");
        assertFalse(recipe.isSatisfiedBy(List.of(new ItemStack(Items.GLOWSTONE_DUST, 2))),
                "glowstone dust alone is not the combo either");
        assertTrue(recipe.isSatisfiedBy(List.of(
                new ItemStack(Items.GLOWSTONE_DUST), new ItemStack(Items.ENDER_EYE), new ItemStack(Items.GLOWSTONE_DUST))),
                "two dust plus the eye together satisfy the combo");
    }

    /** {@code DataAspect + freeModifier}: registered, one slot, and no stat hook of its own. */
    @Test
    void glowingIsRegisteredAndCostsExactlyOneSlot() {
        Modifier glowing = ForgeweaveModifiers.get(GLOWING);

        assertNotNull(glowing, "glowing must be registered");
        assertEquals(1, glowing.unitsPerLevel());
        assertEquals(1, glowing.occupiedSlots(1));
        assertEquals(0, glowing.occupiedSlots(0));
        assertEquals(1.0F, glowing.attackSpeedMultiplier(1));
        assertEquals(5.0F, glowing.miningSpeed(1, 5.0F));
        assertTrue(glowing.appliesToLaunchers(), "upstream ModGlowing carries no category aspect");
    }

    /** Upstream's {@code super("glowing", 0xffffaa)}. */
    @Test
    void glowingUsesUpstreamsColour() {
        assertEquals(TextColor.fromRgb(0xFFFFAA), ForgeweaveModifiers.color(GLOWING));
    }

    /** Upstream ships {@code items/<tool>/mod_glowing.png}, so the overlay is derived, not skipped. */
    @Test
    void glowingDrawsAnOverlay() {
        assertTrue(ModifierArt.OVERLAY_MODIFIERS.contains(GLOWING));
        assertEquals("derived/tools/mods/broadsword_glowing", ModifierArt.overlay("broadsword", GLOWING));
    }
}
