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
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ArmorStats;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #736 (epic #730 slice 3): the netherite modifier's pure arithmetic against the 1.20 clone
 * pinned at {@code de26560d}, {@code tools/data/ModifierProvider.java}'s {@code netherite} row, and
 * the maintainer's slotless-application decision. The station flow is
 * {@code gametest.ArmorModifierGameTests}.
 */
class NetheriteModifierTest {

    private static final ResourceLocation NETHERITE = ResourceLocation.fromNamespaceAndPath("forgeweave", "netherite");
    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static ModifierRecipe shippedRecipe() {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/netherite.json";
        JsonElement json;
        try (InputStream in = NetheriteModifierTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
        return ModifierRecipe.CODEC.parse(ops, json).getOrThrow();
    }

    /** The clone's {@code StatBoostModule}/{@code SetStatModule} rows, each a pure function of the base. */
    @Test
    void theCloneConstants() {
        Modifier netherite = ForgeweaveModifiers.NETHERITE;
        assertEquals(1200, netherite.durability(1, 1000, 1000), "multiplyBase(DURABILITY) 0.2");
        assertEquals(1700, netherite.durability(1, 1500, 1000), "+20% of the base, not the running total");
        assertEquals(12.0F, netherite.attackDamage(1, 10.0F, 10.0F), 1e-6F, "multiplyBase(ATTACK_DAMAGE) 0.2");
        assertEquals(12.5F, netherite.miningSpeed(1, 10.0F, 10.0F), 1e-6F, "multiplyBase(MINING_SPEED) 0.25");
        assertEquals(1.0F, netherite.armorToughnessBonus(1), 1e-6F, "add(ARMOR_TOUGHNESS) 1");
        assertEquals(0.05F, netherite.knockbackResistanceBonus(1), 1e-6F, "add(KNOCKBACK_RESISTANCE) 0.05");
        assertEquals(4, netherite.toolTierIndex(1, 0), "set(HARVEST_TIER) NETHERITE from wood");
        assertEquals(4, netherite.toolTierIndex(1, 4), "and from netherite itself");
        assertTrue(netherite.fireResistant(1), "INDESTRUCTIBLE_ENTITY, as vanilla's fire_resistant");
        assertFalse(netherite.armorOnly(), "the clone's recipe tool tag is #tconstruct:modifiable/durability: tools too");
        assertEquals(0, netherite.occupiedSlots(1), "#736: slotless (maintainer decision; the clone charges one upgrade slot)");
    }

    @Test
    void wornToughnessSumsThePiecesModifiers() {
        ItemStack piece = new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get());
        assertEquals(0.0F, ForgeweaveModifiers.armorToughnessBonus(piece), 1e-6F);
        piece.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(NETHERITE, 1)));
        assertEquals(1.0F, ForgeweaveModifiers.armorToughnessBonus(piece), 1e-6F);
        assertEquals(0.05F, ForgeweaveModifiers.knockbackResistanceBonus(piece), 1e-6F);
        assertTrue(ForgeweaveModifiers.fireResistant(piece));
        assertEquals(0.0F, ForgeweaveModifiers.HASTE.armorToughnessBonus(50), 1e-6F, "no other modifier touches it");
    }

    /** Slotless: applies on a piece whose three slots are full, once; a slotted modifier still can't. */
    @Test
    void appliesOnAFullPieceButOnlyOnce() {
        ItemStack piece = new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get());
        piece.set(ForgeweaveDataComponents.ARMOR_STATS.get(), new ArmorStats(5, 0, 0, 1000));
        piece.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "one"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "two"), 1),
                new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "three"), 1)));
        assertEquals(0, ForgeweaveModifiers.freeSlots(piece));

        ModifierApplication.Outcome outcome = ModifierApplication.apply(shippedRecipe(), piece, 2, 0);
        assertFalse(outcome.output().isEmpty(), "netherite needs no free slot");
        assertEquals(1, outcome.firstUsed(), "one reagent, level 1");
        assertEquals(0, ForgeweaveModifiers.freeSlots(outcome.output()), "and occupies none");
        assertEquals(1200, outcome.output().get(DataComponents.MAX_DAMAGE), "+20% of the plating's 1000");
        assertTrue(outcome.output().has(DataComponents.FIRE_RESISTANT), "the dropped piece survives fire");

        ModifierApplication.Outcome again = ModifierApplication.apply(shippedRecipe(), outcome.output(), 1, 0);
        assertTrue(again.output().isEmpty(), "max level 1");
        assertEquals("gui.forgeweave.modifier.max_level", translationKey(again));
    }

    @Test
    void aToolGetsTheMiningSpeedAndAttackBoostOffItsBase() {
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        pickaxe.set(ForgeweaveDataComponents.TOOL_STATS.get(), new ToolStats.Stats(500, 4.0F, 3.0F));
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(NETHERITE, 1)));
        ToolStats.Stats effective = ForgeweaveModifiers.effectiveStats(pickaxe);
        assertEquals(600, effective.durability());
        assertEquals(5.0F, effective.miningSpeed(), 1e-6F);
        assertEquals(3.6F, effective.attackDamage(), 1e-6F);
    }

    /**
     * The clone's {@code upgrade/netherite.json}: one netherite upgrade smithing template, max
     * level 1. Not a plain netherite ingot -- {@code modifier_recipe/extra_slot_netherite.json}
     * (issue #107/#135) already claims that item as a reagent, and {@code ModifierApplication
     * #recipeFor}'s reagent lookup picks the first recipe whose ingredient matches with no
     * modifier-aware tie-break, so a second recipe on the same bare item would be permanently
     * unreachable. The template is the one item upstream's own combo ingredient
     * ({@code NETHERITE_UPGRADE_SMITHING_TEMPLATE} + an ingot) actually adds beyond what
     * extra_slot already spends, and it collides with nothing else in this mod.
     */
    @Test
    void theShippedRecipe() {
        ModifierRecipe recipe = shippedRecipe();
        assertEquals(NETHERITE, recipe.modifier());
        assertEquals(1, recipe.cost());
        assertEquals(1, recipe.maxLevel());
        assertTrue(recipe.matches(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE)));
        assertFalse(recipe.matches(new ItemStack(Items.NETHERITE_INGOT)),
                "a bare ingot stays extra_slot's reagent (modifier_recipe/extra_slot_netherite.json)");
    }

    private static String translationKey(ModifierApplication.Outcome outcome) {
        return ((net.minecraft.network.chat.contents.TranslatableContents) outcome.rejection().getContents()).getKey();
    }
}
