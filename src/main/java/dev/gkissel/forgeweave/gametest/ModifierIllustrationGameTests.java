package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.book.ModifyPageContent;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Modifier;
import dev.gkissel.forgeweave.modifier.ModifierApplication;

/**
 * Issue #794's exhaustive regression guard, the GameTest half of {@code
 * client.BookModifyPageTest#everyRegisteredModifierIllustratesWithAnItemItAccepts}. That unit test
 * runs with no world ({@code registries} null), so it can't exercise the one registry-dependent gate
 * {@link ModifierApplication#acceptsToolShape} carries -- wind burst's {@code
 * #minecraft:enchantable/mace} restriction, which needs the real enchantment registry <em>and</em>
 * Forgeweave's own datapack tag addition ({@code ForgeweaveItemTagsProvider}'s {@code
 * tag(ItemTags.MACE_ENCHANTABLE).add(TOOL_WARMACE)}) to resolve. A GameTestServer loads both, so this
 * is the one place the whole gate runs end to end for every registered modifier at once -- the guard
 * the issue asked for: it must fail the moment {@link ModifyPageContent#representativeEntry} (the
 * guide book's picture) or {@link ModifyPageContent#compatibleEntries} (JEI's catalyst cycle) ever
 * hands back an item the modifier would actually refuse.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ModifierIllustrationGameTests {

    @GameTest(template = "empty")
    public static void everyRegisteredModifierIllustratesWithAnItemItAccepts(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        List<String> offenders = new ArrayList<>();
        for (ResourceLocation id : ForgeweaveModifiers.ids()) {
            Modifier modifier = ForgeweaveModifiers.get(id);
            if (modifier == null) {
                continue; // ForgeweaveModifiers.get already warned; nothing more this test can check.
            }
            ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(registries, modifier);
            ItemStack tool = new ItemStack(entry.tool().get());
            if (!ModifierApplication.acceptsToolShape(registries, modifier, tool)) {
                offenders.add(id + " -> " + BuiltInRegistries.ITEM.getKey(entry.tool().get()));
            }
        }
        if (!offenders.isEmpty()) {
            helper.fail("the guide book/JEI illustration picked an item the modifier would refuse for: " + offenders);
        }
        helper.succeed();
    }
}
