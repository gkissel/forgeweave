package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.mekanism.modules.MekanismGearModules;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.tool.ArmorStats;

/**
 * Issue #994 (M8-10): the {@code rayward} modifier at the Tool Station, and the two toggles it does
 * and does not follow.
 *
 * <p>The shipped recipe spends {@code c:ingots/lead}, which no mod on this classpath fills, so the
 * application runs through a hand-built recipe with a vanilla stand-in reagent -- the same shape
 * {@code PowahGameTests} uses for the same reason -- while the shipped file itself is checked where
 * it can be: it is in the registry, it names the lead tag, and it caps at four levels.
 *
 * <p>The effect this modifier exists for, radiation shielding, is a capability only Mekanism reads,
 * and Mekanism is never on a GameTest classpath (JC-B). What is covered here is everything on
 * Forgeweave's own side of that number: the level applied, the slots it costs, the fraction the level
 * computes to, and the two toggles.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class RaywardGameTests {

    @GameTest(template = "empty")
    public static void theShippedRecipeIsRegisteredAndSpendsLeadAtUpToFourLevels(GameTestHelper helper) {
        Registry<ModifierRecipe> recipes =
                helper.getLevel().registryAccess().registryOrThrow(ModifierRecipe.REGISTRY);
        ModifierRecipe recipe = recipes.get(id("rayward_lead"));
        helper.assertTrue(recipe != null, "the rayward recipe should ship unconditionally, Mekanism or not");
        helper.assertValueEqual(recipe.modifier(), ForgeweaveModifiers.RAYWARD_ID, "recipe modifier");
        helper.assertValueEqual(recipe.maxLevel(), ForgeweaveModifiers.RAYWARD_MAX_LEVEL, "recipe max level");
        // The reagent is the c:ingots/lead tag, which nothing on this classpath fills, so the
        // ingredient resolves to no items at all here. That is the assertion: a lead-less install
        // ships the recipe and simply cannot complete it. RaywardTest pins the tag by name.
        helper.assertTrue(recipe.reagent().isEmpty(),
                "with nothing filling c:ingots/lead the reagent should match no item on this server");
        helper.succeed();
    }

    /**
     * Four applications, one level each, through the real {@link ModifierApplication#apply} pipeline:
     * the level climbs one at a time, each level costs exactly one more modifier slot, and the
     * shielding the level computes to reaches exactly 100% on the fourth and no further.
     */
    @GameTest(template = "empty")
    public static void raywardCostsOneSlotAndOneIngotPerLevelUpToFullShielding(GameTestHelper helper) {
        ItemStack piece = chestplate();
        ModifierRecipe recipe = recipe(ForgeweaveModifiers.RAYWARD_MAX_LEVEL, Items.IRON_INGOT);
        for (int level = 1; level <= ForgeweaveModifiers.RAYWARD_MAX_LEVEL; level++) {
            var applied = ModifierApplication.apply(recipe, piece, 1, 0);
            helper.assertTrue(!applied.output().isEmpty(), "level " + level + " should be accepted");
            piece = applied.output();
            helper.assertValueEqual(ForgeweaveModifiers.entry(piece, ForgeweaveModifiers.RAYWARD_ID).level(), level,
                    "level after application " + level);
            helper.assertValueEqual(ForgeweaveModifiers.RAYWARD.occupiedSlots(level), level,
                    "slots occupied at level " + level);
        }
        helper.assertValueEqual(ForgeweaveModifiers.radiationShielding(piece), 1.0D,
                "four levels should shield completely");

        // A fifth is refused: the recipe's own cap, not a clamp hiding a spent slot.
        helper.assertTrue(ModifierApplication.apply(recipe, piece, 1, 0).output().isEmpty(),
                "a fifth level should be refused by the recipe's max_level");
        helper.succeed();
    }

    /** One level is a quarter, two are a half, and nothing stacks past complete. */
    @GameTest(template = "empty")
    public static void eachLevelIsWorthItsOwnQuarter(GameTestHelper helper) {
        for (int level = 0; level <= ForgeweaveModifiers.RAYWARD_MAX_LEVEL; level++) {
            ItemStack piece = chestplateAt(level);
            double expected = level * ForgeweaveConfig.radiationShieldingPerLevel();
            helper.assertTrue(Math.abs(ForgeweaveModifiers.radiationShielding(piece) - expected) < 1.0e-9D,
                    "level " + level + " should shield " + expected);
        }
        helper.succeed();
    }

    /**
     * The toggle decision this issue had to make explicitly. {@code mekanismModules} covers the module
     * container and the ore chains; rayward is a Forgeweave modifier and rides {@code modifiers} like
     * every other one. So with {@code mekanismModules} off the module bridge goes inert and rayward's
     * own number is untouched, and the stored component survives either way -- D-M7-3's rule.
     */
    @GameTest(template = "empty")
    public static void mekanismModulesOffLeavesRaywardAloneAndKeepsItsComponent(GameTestHelper helper) {
        ItemStack piece = chestplateAt(ForgeweaveModifiers.RAYWARD_MAX_LEVEL);
        ForgeweaveConfig.MEKANISM_MODULES.set(false);
        try {
            helper.assertTrue(!MekanismGearModules.modulesEnabled(), "the module toggle should be off");
            helper.assertValueEqual(MekanismGearModules.radiationShielding(piece), 0.0D,
                    "the module bridge itself registers nothing and answers nothing with the toggle off");
            helper.assertValueEqual(ForgeweaveModifiers.radiationShielding(piece), 1.0D,
                    "rayward does not ride mekanismModules -- it still shields completely");
            helper.assertValueEqual(ForgeweaveModifiers.entry(piece, ForgeweaveModifiers.RAYWARD_ID).level(),
                    ForgeweaveModifiers.RAYWARD_MAX_LEVEL, "the stored level survives the toggle untouched");
        } finally {
            ForgeweaveConfig.MEKANISM_MODULES.set(true);
        }
        helper.assertValueEqual(ForgeweaveModifiers.radiationShielding(piece), 1.0D,
                "back on, nothing has changed on either side");
        helper.succeed();
    }

    private static ModifierRecipe recipe(int maxLevel, Item reagentItem) {
        return new ModifierRecipe(ForgeweaveModifiers.RAYWARD_ID,
                List.of(new ModifierRecipe.Reagent(Ingredient.of(reagentItem), 1)), 1, maxLevel, List.of());
    }

    /** A plain iron chestplate with stats, no atomic_matter_alloy part and nothing from Mekanism on it. */
    private static ItemStack chestplate() {
        ItemStack stack = new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get());
        stack.set(ForgeweaveDataComponents.ARMOR_STATS.get(), new ArmorStats(5.0F, 0.0F, 0.0F, 240));
        return stack;
    }

    private static ItemStack chestplateAt(int level) {
        ItemStack stack = chestplate();
        if (level > 0) {
            stack.set(ForgeweaveDataComponents.MODIFIERS.get(),
                    List.of(new ModifierEntry(ForgeweaveModifiers.RAYWARD_ID, level)));
        }
        return stack;
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private RaywardGameTests() {}
}
