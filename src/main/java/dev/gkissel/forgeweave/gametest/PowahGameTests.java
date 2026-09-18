package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #996 (D-M8-17, M8-12): Powah's four remaining Track A crystal presets -- {@code
 * blazing_crystal}/{@code niotic_crystal}/{@code spirited_crystal}/{@code nitro_crystal}, the ones
 * {@link PresetBatch5GameTests} left unshippable -- and the {@code surgebound} modifier built on the
 * five-material ladder (energized steel, then the four crystals). Powah is not a build/test
 * dependency (see {@code build.gradle}; no Powah Java type is needed at all -- every material and
 * recipe here is existence-gated JSON keyed on a concrete Powah item id, the same shape {@code
 * RecoveryBatchGameTests} already proves for {@code energised_steel}), so every {@code
 * neoforge:conditions} check here fails in this GameTest server exactly as it would in a
 * Forgeweave-only install. The positive existence path is generic infrastructure already covered by
 * {@code ConditionalMaterialGameTests}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class PowahGameTests {

    /** The four crystal materials issue #996 unblocks -- see {@link PresetBatch5GameTests}'s javadoc. */
    private static final String[] CRYSTAL_MATERIALS = {
            "blazing_crystal", "niotic_crystal", "spirited_crystal", "nitro_crystal",
    };

    /** The five surgebound application recipes, one per crystal-ladder level (I-V). */
    private static final String[] SURGEBOUND_RECIPES = {
            "surgebound_1_energized_steel", "surgebound_2_blazing_crystal", "surgebound_3_niotic_crystal",
            "surgebound_4_spirited_crystal", "surgebound_5_nitro_crystal",
    };

    @GameTest(template = "empty")
    public static void unsuppliedCrystalMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : CRYSTAL_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without Powah, found it registered");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unsuppliedSurgeboundRecipesDoNotExistAtAll(GameTestHelper helper) {
        Registry<ModifierRecipe> recipes = helper.getLevel().registryAccess().registryOrThrow(ModifierRecipe.REGISTRY);
        for (String name : SURGEBOUND_RECIPES) {
            helper.assertTrue(recipes.get(id(name)) == null,
                    "expected the " + name + " modifier recipe to be absent without Powah, found it registered");
        }
        helper.succeed();
    }

    /**
     * The order rule and the slot cost, exercised through the real {@link ModifierApplication#apply}
     * pipeline rather than the hook directly (see {@code SurgeboundTest} for the pure-function
     * coverage): a hand-built recipe per level, a vanilla stand-in reagent (Powah's own crystals are
     * not on this classpath -- see the class javadoc), applied to a freshly assembled pickaxe.
     */
    @GameTest(template = "empty")
    public static void surgeboundAppliesInOrderAndRefusesOutOfOrder(GameTestHelper helper) {
        ItemStack tool = pickaxe();
        ModifierRecipe levelTwo = recipe(2, Items.AMETHYST_SHARD);
        // Level II (blazing) attempted before level I (energized steel) is applied: refused.
        var skippedAhead = ModifierApplication.apply(levelTwo, tool, 1, 0);
        helper.assertTrue(skippedAhead.output().isEmpty(), "level II before level I should be refused");
        helper.assertTrue(Component
                        .translatable("gui.forgeweave.modifier.surgebound_out_of_order", 1, 2)
                        .equals(skippedAhead.rejection()),
                "the refusal should name the ordering problem, not some other rejection");

        // Level I in order: succeeds and spends exactly one slot.
        var levelOne = ModifierApplication.apply(recipe(1, Items.IRON_INGOT), tool, 1, 0);
        helper.assertTrue(!levelOne.output().isEmpty(), "level I from a bare tool should be accepted");
        ItemStack afterLevelOne = levelOne.output();
        helper.assertValueEqual(ForgeweaveModifiers.entry(afterLevelOne, ForgeweaveModifiers.SURGEBOUND_ID).level(), 1,
                "level after the first application");

        // Level II now in order: succeeds.
        var levelTwoInOrder = ModifierApplication.apply(levelTwo, afterLevelOne, 1, 0);
        helper.assertTrue(!levelTwoInOrder.output().isEmpty(), "level II right after level I should be accepted");
        helper.assertValueEqual(
                ForgeweaveModifiers.entry(levelTwoInOrder.output(), ForgeweaveModifiers.SURGEBOUND_ID).level(), 2,
                "level after the second application");
        helper.succeed();
    }

    /**
     * D-M7-3's rule applied to a compat toggle (issue #996): {@code powahModifiers} off makes a
     * tool's already-applied {@code surgebound} bonus inert without touching the stored component --
     * the level on the tool is unchanged before and after, only what it computes drops to nothing.
     */
    @GameTest(template = "empty")
    public static void powahModifiersOffMakesAnAlreadyAppliedSurgeboundInert(GameTestHelper helper) {
        ItemStack bare = energizedTool();
        int baseCapacity = ForgeweaveTraits.energyCapacity(bare);

        ItemStack tool = energizedToolAt(5);
        int onCapacity = ForgeweaveTraits.energyCapacity(tool);
        helper.assertTrue(onCapacity > baseCapacity, "level V should have raised the trait-derived base");

        ForgeweaveConfig.POWAH_MODIFIERS.set(false);
        try {
            helper.assertValueEqual(ForgeweaveTraits.energyCapacity(tool), baseCapacity,
                    "the bonus should be fully inert with the toggle off");
            helper.assertValueEqual(ForgeweaveModifiers.entry(tool, ForgeweaveModifiers.SURGEBOUND_ID).level(), 5,
                    "the stored level must survive the toggle -- D-M7-3's rule, not just its effect");
        } finally {
            ForgeweaveConfig.POWAH_MODIFIERS.set(true);
        }
        helper.assertValueEqual(ForgeweaveTraits.energyCapacity(tool), onCapacity, "back on, the bonus returns");
        helper.succeed();
    }

    private static ModifierRecipe recipe(int maxLevel, Item reagentItem) {
        return new ModifierRecipe(ForgeweaveModifiers.SURGEBOUND_ID,
                List.of(new ModifierRecipe.Reagent(Ingredient.of(reagentItem), 1)), 1, maxLevel, List.of());
    }

    private static ItemStack pickaxe() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), new ToolStats.Stats(250, 6.0F, 3.5F));
        return stack;
    }

    /** A pickaxe carrying {@code forgeweave:energized} (the trait's own fixed FE capacity), no modifiers yet. */
    private static ItemStack energizedTool() {
        ItemStack stack = pickaxe();
        stack.set(ForgeweaveDataComponents.TRAITS.get(),
                List.of(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "energized")));
        return stack;
    }

    /** {@link #energizedTool()}, plus surgebound at {@code level}. */
    private static ItemStack energizedToolAt(int level) {
        ItemStack stack = energizedTool();
        stack.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(ForgeweaveModifiers.SURGEBOUND_ID, level)));
        return stack;
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private PowahGameTests() {}
}
