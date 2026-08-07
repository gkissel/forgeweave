package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * docs/SCOPE.md M1 issue #13: the {@code allowVanillaEnchanting} config flag. Covers both flag
 * states against a real assembled tool (see {@link ToolAssembly}), through {@link ItemStack#isEnchantable()}
 * -- the same method {@code EnchantmentMenu} consults to decide whether to offer the item any
 * enchantments at all.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EnchantingGameTests {

    /** CONTEXT.md invariant: off (the default) by default. */
    @GameTest(template = "empty")
    public static void toolRejectedWhenFlagOff(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        ItemStack pickaxe = assembledPickaxe(helper);

        helper.assertFalse(pickaxe.isEnchantable(),
                "a Forgeweave tool should be rejected by the enchanting table while allowVanillaEnchanting is off");

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void toolAcceptedWhenFlagOn(GameTestHelper helper) {
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        try {
            ItemStack pickaxe = assembledPickaxe(helper);

            helper.assertTrue(pickaxe.isEnchantable(),
                    "a Forgeweave tool should be accepted by the enchanting table while allowVanillaEnchanting is on");

            helper.succeed();
        } finally {
            // Restore the CONTEXT.md default so later tests don't inherit this test's flag state.
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }
    }

    private static ItemStack assembledPickaxe(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        return ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
    }
}
