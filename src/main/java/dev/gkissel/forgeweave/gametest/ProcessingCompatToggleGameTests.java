package dev.gkissel.forgeweave.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.config.ForgeweaveConfigCondition;

/**
 * Issue #995 (D-M8-12, D-M8-13, D-M8-16): the four {@code compat} toggles this issue adds --
 * {@code createRecipes}, {@code immersiveEngineeringRecipes}, {@code enderIoRecipes} and
 * {@code powahHeatSources} -- switched off and back on. Sibling to {@code CompatToggleGameTests},
 * split into its own file rather than appended there so this PR's edits never collide with another
 * M8 child's own toggle additions to that shared file.
 *
 * <p>None of the four generated recipe types or the Powah data map is a Forgeweave-owned recipe type,
 * so unlike {@code CompatToggleGameTests}' other entries there is no in-game "does the row still
 * match" site to exercise here: Create, Immersive Engineering, EnderIO and Powah are all absent from
 * {@code runGameTestServer} (JC-B), so a {@code create:mixing} or {@code enderio:sag_milling} row
 * never resolves in this environment regardless of the toggle. What a loaded world adds over
 * {@code ForgeweaveConfigConditionTest} is the thing a plain JUnit run cannot reach at all --
 * {@code ModConfigSpec.ConfigValue#set} requires a loaded spec -- so this is what actually proves
 * {@link ForgeweaveConfigCondition#test} reads the live value rather than a cached default.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ProcessingCompatToggleGameTests {

    private static boolean evaluate(String toggle) {
        return new ForgeweaveConfigCondition(toggle).test(ICondition.IContext.TAGS_INVALID);
    }

    @GameTest(template = "empty")
    public static void createRecipesOffReadsFalseAndOnReadsTrueAgain(GameTestHelper helper) {
        helper.assertTrue(evaluate("createRecipes"), "on by default, or this test proves nothing");
        ForgeweaveConfig.CREATE_RECIPES.set(false);
        try {
            helper.assertFalse(evaluate("createRecipes"), "must read false while the toggle is off");
        } finally {
            ForgeweaveConfig.CREATE_RECIPES.set(true);
        }
        helper.assertTrue(evaluate("createRecipes"), "must read true again with no reload needed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void immersiveEngineeringRecipesOffReadsFalseAndOnReadsTrueAgain(GameTestHelper helper) {
        helper.assertTrue(evaluate("immersiveEngineeringRecipes"), "on by default, or this test proves nothing");
        ForgeweaveConfig.IMMERSIVE_ENGINEERING_RECIPES.set(false);
        try {
            helper.assertFalse(evaluate("immersiveEngineeringRecipes"), "must read false while the toggle is off");
        } finally {
            ForgeweaveConfig.IMMERSIVE_ENGINEERING_RECIPES.set(true);
        }
        helper.assertTrue(evaluate("immersiveEngineeringRecipes"), "must read true again with no reload needed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void enderIoRecipesOffReadsFalseAndOnReadsTrueAgain(GameTestHelper helper) {
        helper.assertTrue(evaluate("enderIoRecipes"), "on by default, or this test proves nothing");
        ForgeweaveConfig.ENDER_IO_RECIPES.set(false);
        try {
            helper.assertFalse(evaluate("enderIoRecipes"), "must read false while the toggle is off");
        } finally {
            ForgeweaveConfig.ENDER_IO_RECIPES.set(true);
        }
        helper.assertTrue(evaluate("enderIoRecipes"), "must read true again with no reload needed");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void powahHeatSourcesOffReadsFalseAndOnReadsTrueAgain(GameTestHelper helper) {
        helper.assertTrue(evaluate("powahHeatSources"), "on by default, or this test proves nothing");
        ForgeweaveConfig.POWAH_HEAT_SOURCES.set(false);
        try {
            helper.assertFalse(evaluate("powahHeatSources"), "must read false while the toggle is off");
        } finally {
            ForgeweaveConfig.POWAH_HEAT_SOURCES.set(true);
        }
        helper.assertTrue(evaluate("powahHeatSources"), "must read true again with no reload needed");
        helper.succeed();
    }
}
