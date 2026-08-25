package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.tool.ModifierArt;

/**
 * Parity audit T24 (issue #455): the Blasting modifier's pure half -- its shipped recipe, its
 * aspects, and every number upstream {@code tools/modifiers/ModBlasting.java} computes from the
 * level. What it does to a real block break is {@code gametest.BlastingGameTests}.
 *
 * <p>All upstream references pinned at {@code c01173c}:
 * {@code ModBlasting} (the whole modifier), {@code tools/TinkerModifiers.java:147-148} (the
 * registration and its {@code RecipeMatch.ItemCombination(1, tnt, tnt, tnt)}),
 * {@code library/modifiers/ModifierAspect.java} ({@code harvestOnly}, {@code FreeFirstModifierAspect})
 * and {@code library/utils/ToolHelper.java:189-201} ({@code isToolEffective2}'s blasting widening).
 */
class BlastingTest {

    private static RegistryOps<JsonElement> ops;

    private static final ResourceLocation BLASTING =
            ResourceLocation.fromNamespaceAndPath("forgeweave", "blasting");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static ModifierRecipe shippedRecipe() {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/blasting.json";
        JsonElement json;
        try (InputStream in = BlastingTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
        return ModifierRecipe.CODEC.parse(ops, json).getOrThrow();
    }

    /** {@code TinkerModifiers:148}: one application costs three TNT, and {@code maxLevel} is 3. */
    @Test
    void theShippedRecipeIsThreeTntPerLevelUpToThree() {
        ModifierRecipe recipe = shippedRecipe();
        assertEquals(BLASTING, recipe.modifier());
        assertEquals(3, recipe.cost(), "upstream's ItemCombination(1, tnt, tnt, tnt): three TNT buy one level");
        assertEquals(3, recipe.maxLevel(), "ModBlasting's super(..., 3, 0)");
        assertTrue(recipe.reagent().test(new ItemStack(Items.TNT)));
        assertFalse(recipe.reagent().test(new ItemStack(Items.GUNPOWDER)));
        assertEquals(ForgeweaveModifiers.BLASTING, ForgeweaveModifiers.get(BLASTING));
        // countPerLevel is 0 upstream, i.e. one application unit is one displayed level.
        assertEquals(1, ForgeweaveModifiers.BLASTING.unitsPerLevel());
    }

    /**
     * Upstream's constructor swaps {@code freeModifier} for {@code FreeFirstModifierAspect(this, 1)}:
     * the first level takes a modifier slot and the second and third ride inside it -- unlike the
     * one-slot-per-level default every leveled modifier without that swap charges.
     */
    @Test
    void everyLevelRidesInsideOneModifierSlot() {
        assertEquals(0, ForgeweaveModifiers.BLASTING.occupiedSlots(0));
        assertEquals(1, ForgeweaveModifiers.BLASTING.occupiedSlots(1));
        assertEquals(1, ForgeweaveModifiers.BLASTING.occupiedSlots(2));
        assertEquals(1, ForgeweaveModifiers.BLASTING.occupiedSlots(3));
    }

    /** {@code ModifierAspect.harvestOnly}, and the only shipped modifier that carries it. */
    @Test
    void blastingAndVeinmineAreTheOnlyHarvestOnlyModifiers() {
        assertTrue(ForgeweaveModifiers.BLASTING.harvestOnly());
        for (ResourceLocation id : ForgeweaveModifiers.ids()) {
            if (!id.equals(BLASTING) && !id.getPath().equals("veinmine")) { // #719
                assertFalse(ForgeweaveModifiers.get(id).harvestOnly(), id + " unexpectedly harvest-only");
            }
        }
    }

    /** {@code getBlockDestroyChange}: {@code level * (1f / maxLevel)}. */
    @Test
    void theDestroyChanceIsAThirdPerLevel() {
        assertEquals(0.0F, ForgeweaveModifiers.blastingDestroyChance(0), 1.0e-6F);
        assertEquals(1.0F / 3.0F, ForgeweaveModifiers.blastingDestroyChance(1), 1.0e-6F);
        assertEquals(2.0F / 3.0F, ForgeweaveModifiers.blastingDestroyChance(2), 1.0e-6F);
        assertEquals(1.0F, ForgeweaveModifiers.blastingDestroyChance(3), 1.0e-6F);
    }

    /**
     * {@code ModBlasting#miningSpeed}, level by level, against a tool of speed 6 on a hardness-30
     * block (obsidian) whose vanilla speed would be 2 -- the case the modifier exists for.
     *
     * <p>{@code speed = 6 * 30 = 180}, divided by 10 / 5 / 1.1, then blended with the original at
     * {@code level / 3}: 62 at level 1, 25.33 at level 2, 55.14 at level 3. The level-2 dip is
     * upstream's own curve (a /5 divisor against a 2/3 weight), not a transcription slip.
     */
    @Test
    void theBreakSpeedFollowsUpstreamsHardnessBlend() {
        assertEquals(180.0F / 10.0F / 3.0F + 2.0F * 2.0F / 3.0F,
                ForgeweaveModifiers.blastingBreakSpeed(1, 6.0F, 30.0F, 2.0F), 1.0e-4F);
        assertEquals(180.0F / 5.0F * 2.0F / 3.0F + 2.0F / 3.0F,
                ForgeweaveModifiers.blastingBreakSpeed(2, 6.0F, 30.0F, 2.0F), 1.0e-4F);
        assertEquals(180.0F / 1.1F,
                ForgeweaveModifiers.blastingBreakSpeed(3, 6.0F, 30.0F, 2.0F), 1.0e-4F);
    }

    /** At full level the original speed is weighted out entirely -- {@code weight2} is 0. */
    @Test
    void atFullLevelTheOriginalSpeedNoLongerCounts() {
        assertEquals(ForgeweaveModifiers.blastingBreakSpeed(3, 6.0F, 30.0F, 2.0F),
                ForgeweaveModifiers.blastingBreakSpeed(3, 6.0F, 30.0F, 900.0F), 1.0e-4F);
    }

    /** {@code ModBlasting#getExtraInfo}: the destroy chance as {@code Util.dfPercent}. */
    @Test
    void theExtraInfoLineReportsTheBlastPower() {
        assertEquals(List.of(Component.translatable("modifier.forgeweave.blasting.extra", "33%")),
                ForgeweaveModifiers.extraInfo(new ItemStack(Items.IRON_PICKAXE), new ModifierEntry(BLASTING, 1)));
        assertEquals(List.of(Component.translatable("modifier.forgeweave.blasting.extra", "100%")),
                ForgeweaveModifiers.extraInfo(new ItemStack(Items.IRON_PICKAXE), new ModifierEntry(BLASTING, 3)));
        assertTrue(ForgeweaveModifiers.extraInfoIds().contains(BLASTING));
    }

    /** {@code ModBlasting}'s {@code super("blasting", 0xffaa23, ...)}. */
    @Test
    void theModifierKeepsUpstreamsColour() {
        assertEquals(TextColor.fromRgb(0xFFAA23), ForgeweaveModifiers.color(BLASTING));
    }

    /**
     * {@code ModBlasting#canApplyTogether}: refuses luck, silktouch, squeaky and autosmelt by id, and
     * Silk Touch / Looting / Fortune as enchantments already on the stack.
     */
    @Test
    void blastingRefusesEveryDropAlteringNeighbour() {
        for (String other : List.of("luck", "silky")) {
            assertTrue(refusedAgainstModifier(other).isPresent(), "blasting must refuse " + other);
        }
        for (String trait : List.of("squeaky", "autosmelt")) {
            assertTrue(refusedAgainstTrait(trait).isPresent(), "blasting must refuse the " + trait + " trait");
        }
        // Symmetric, as upstream's two-way canApplyTogether check is.
        assertTrue(ModifierCompatibility.refusal(withModifier("blasting"), id("luck"), Component.empty()).isPresent());
        // Something with no stake in the drops is fine.
        assertTrue(refusedAgainstModifier("haste").isEmpty());
    }

    /**
     * The enchantment half of the same {@code canApplyTogether} -- Silk Touch, Looting and Fortune.
     * Asserted through the shipped table rather than a live stack, because 1.21 enchantments are a
     * datapack registry no unit test has: {@code gametest.BlastingGameTests} exercises the stack path.
     */
    @Test
    void blastingRefusesTheThreeDropEnchantments() {
        assertEquals(Set.of(Enchantments.SILK_TOUCH, Enchantments.LOOTING, Enchantments.FORTUNE),
                ModifierCompatibility.excludedEnchantments(BLASTING));
    }

    /**
     * Upstream ships {@code items/<tool>/mod_blasting.png} in exactly its nine
     * {@code Category.HARVEST} folders. Every Forgeweave harvest tool resolves an overlay (the vein
     * hammer via the hammer donor issue #198 already established); nothing else does.
     */
    @Test
    void onlyHarvestToolsResolveABlastingOverlay() {
        for (String tool : List.of("pickaxe", "shovel", "hatchet", "mattock", "kama", "hammer",
                "excavator", "lumberaxe", "scythe", "vein_hammer")) {
            assertEquals("derived/tools/mods/" + tool + "_blasting", ModifierArt.overlay(tool, BLASTING),
                    tool + " must draw the blasting overlay");
        }
        for (String tool : List.of("broadsword", "cleaver", "warmace", "shortbow", "crossbow")) {
            assertEquals(null, ModifierArt.overlay(tool, BLASTING), tool + " must draw no blasting overlay");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    private static ItemStack withModifier(String modifier) {
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(id(modifier), 1)));
        return tool;
    }

    private static java.util.Optional<Component> refusedAgainstModifier(String other) {
        return ModifierCompatibility.refusal(withModifier(other), BLASTING, Component.empty());
    }

    private static java.util.Optional<Component> refusedAgainstTrait(String trait) {
        ItemStack tool = new ItemStack(Items.IRON_PICKAXE);
        tool.set(ForgeweaveDataComponents.TRAITS.get(), List.of(id(trait)));
        return ModifierCompatibility.refusal(tool, BLASTING, Component.empty());
    }
}
