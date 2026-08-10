package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #107's verification for the five parity modifiers batch 2 ships: one test per modifier,
 * pinning both the shipped recipe JSON against upstream 1.12's constants (same shape as
 * {@code ModifierRecipeTest#theShippedHasteRecipeMatchesUpstreamsNumbers}) and the Java behavior
 * behind it. The station-integration coverage (a real Tool Station applying a reagent, the slot cap)
 * is {@code gametest.ModifierGameTests#anExtraSlotItemRaisesTheCap} -- docs/SCOPE.md's gate names that
 * one by name ("slot cap + extra-slot").
 */
class ModifierBatch2Test {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static ModifierRecipe shippedRecipe(String path) {
        JsonElement json;
        try (InputStream in = ModifierBatch2Test.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
        return ModifierRecipe.CODEC.parse(ops, json).getOrThrow();
    }

    private static ItemStack pickaxe() {
        return new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
    }

    private static ItemStack pickaxeWithStats() {
        ItemStack tool = pickaxe();
        tool.set(ForgeweaveDataComponents.TOOL_STATS.get(), new ToolStats.Stats(100, 6.0F, 5.0F));
        return tool;
    }

    // ------------------------------------------------------------------ reinforced

    @Test
    void reinforcedChanceIsTwentyPercentPerLevelUpToUnbreakableAtFive() {
        ModifierRecipe recipe = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/reinforced.json");
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "reinforced"), recipe.modifier());
        assertEquals(5, recipe.maxLevel(), "upstream ModReinforced's own maxLevel");
        assertTrue(recipe.reagent().test(new ItemStack(ForgeweaveItems.REINFORCED_PLATE.get())));

        assertEquals(0.20F, ForgeweaveModifiers.REINFORCED.durabilityNegationChance(1), 1.0e-6F);
        assertEquals(0.60F, ForgeweaveModifiers.REINFORCED.durabilityNegationChance(3), 1.0e-6F);
        assertEquals(1.0F, ForgeweaveModifiers.REINFORCED.durabilityNegationChance(5), 1.0e-6F, "level 5 reads unbreakable");
        assertEquals(1.0F, ForgeweaveModifiers.REINFORCED.durabilityNegationChance(50), 1.0e-6F, "clamped, never over 1");

        ItemStack tool = pickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(recipe.modifier(), 5)));
        assertEquals(1.0F, ForgeweaveModifiers.durabilityNegationChance(tool), 1.0e-6F);
    }

    // ------------------------------------------------------------------ mending moss

    @Test
    void mendingMossHealsMoreAndStoresMorePerLevel() {
        ModifierRecipe recipe = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/mending_moss.json");
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "mending_moss"), recipe.modifier());
        assertEquals(3, recipe.maxLevel(), "upstream ModMendingMoss's own maxLevel");
        assertTrue(recipe.reagent().test(new ItemStack(ForgeweaveItems.MENDING_MOSS.get())));

        // Upstream ModMendingMoss#getDurabilityPerXP: 2 + level.
        assertEquals(3, ForgeweaveModifiers.mendingMossDurabilityPerXp(1));
        assertEquals(5, ForgeweaveModifiers.mendingMossDurabilityPerXp(3));
        // Upstream ModMendingMoss#getMaxXp: 100 * 3^(level-1).
        assertEquals(100, ForgeweaveModifiers.mendingMossXpCap(1));
        assertEquals(300, ForgeweaveModifiers.mendingMossXpCap(2));
        assertEquals(900, ForgeweaveModifiers.mendingMossXpCap(3));

        assertEquals(ForgeweaveModifiers.MENDING_MOSS, ForgeweaveModifiers.get(recipe.modifier()));
    }

    // ------------------------------------------------------------------ silky

    @Test
    void silkyGrantsSilkTouchAtTheCostOfThreeOffEachStat() {
        ModifierRecipe recipe = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/silky.json");
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "silky"), recipe.modifier());
        assertEquals(1, recipe.maxLevel(), "silky is a single application (upstream's SingleAspect)");
        assertTrue(recipe.reagent().test(new ItemStack(ForgeweaveItems.SILKY_JEWEL.get())));

        assertFalse(ForgeweaveModifiers.SILKY.grantsSilkTouch(0));
        assertTrue(ForgeweaveModifiers.SILKY.grantsSilkTouch(1));
        assertEquals(3.0F, ForgeweaveModifiers.SILKY.miningSpeed(1, 6.0F), 1.0e-6F);
        assertEquals(2.0F, ForgeweaveModifiers.SILKY.attackDamage(1, 5.0F), 1.0e-6F);
        // Upstream floors both at 1 rather than letting them go negative.
        assertEquals(1.0F, ForgeweaveModifiers.SILKY.miningSpeed(1, 2.0F), 1.0e-6F);
        assertEquals(1.0F, ForgeweaveModifiers.SILKY.attackDamage(1, 1.5F), 1.0e-6F);

        ItemStack tool = pickaxeWithStats();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(recipe.modifier(), 1)));
        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(tool);
        assertEquals(3.0F, effective.miningSpeed(), 1.0e-6F);
        assertEquals(2.0F, effective.attackDamage(), 1.0e-6F);
        assertTrue(ForgeweaveModifiers.grantsSilkTouch(tool));
    }

    // ------------------------------------------------------------------ soulbound

    @Test
    void soulboundIsANetherStarSingleApplication() {
        ModifierRecipe recipe = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/soulbound.json");
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "soulbound"), recipe.modifier());
        assertEquals(1, recipe.maxLevel());
        assertTrue(recipe.reagent().test(new ItemStack(Items.NETHER_STAR)));
        assertFalse(recipe.reagent().test(new ItemStack(Items.DIAMOND)));

        assertEquals(ForgeweaveModifiers.SOULBOUND, ForgeweaveModifiers.get(recipe.modifier()));
        assertEquals(0, ForgeweaveModifiers.SOULBOUND.bonusSlots(1), "soulbound grants no extra slots");
    }

    // ------------------------------------------------------------------ extra-slot

    @Test
    void extraSlotNetsPlusOneFreeSlotPerLevel() {
        ModifierRecipe recipe = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/extra_slot.json");
        assertEquals(ResourceLocation.fromNamespaceAndPath("forgeweave", "extra_slot"), recipe.modifier());
        assertTrue(recipe.reagent().test(new ItemStack(ForgeweaveItems.EXTRA_MODIFIER.get())));

        // The bonusSlots trap this modifier exists to demonstrate (Modifier#bonusSlots's javadoc):
        // its own entry occupies one slot, so netting +1 per level needs level + 1 here.
        assertEquals(2, ForgeweaveModifiers.EXTRA_SLOT.bonusSlots(1));
        assertEquals(3, ForgeweaveModifiers.EXTRA_SLOT.bonusSlots(2));

        ItemStack tool = pickaxe();
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(recipe.modifier(), 1)));
        assertEquals(ForgeweaveModifiers.DEFAULT_SLOTS + 1, ForgeweaveModifiers.freeSlots(tool),
                "one application must net exactly +1 free slot");
    }
}
