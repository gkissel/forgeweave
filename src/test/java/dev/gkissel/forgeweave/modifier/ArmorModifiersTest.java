package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.Protection;
import dev.gkissel.forgeweave.combat.ThornsCounterSeam;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * M4-6 (issue #681): the pure arithmetic of the seven armor modifiers and the shape of their
 * shipped recipes, against the 1.20 clone pinned at {@code de26560d}:
 * {@code tools/data/ModifierProvider.java} (the magnitudes), {@code recipes/tools/modifiers/defense/*.json}
 * and {@code .../upgrade/thorns.json} (the costs and caps). Everything needing a station, a level
 * or a blow is {@code gametest.ArmorModifierGameTests}.
 */
class ArmorModifiersTest {

    private static RegistryOps<JsonElement> ops;

    private static final Set<ResourceLocation> ARMOR_ONLY = Set.of(
            id("fire_protection"), id("blast_protection"), id("magic_protection"),
            id("melee_protection"), id("projectile_protection"), id("knockback_resistance"), id("thorns"));

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static ModifierRecipe shippedRecipe(String name) {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/" + name + ".json";
        JsonElement json;
        try (InputStream in = ArmorModifiersTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
        return ModifierRecipe.CODEC.parse(ops, json).getOrThrow();
    }

    /** The clone's {@code tconstruct:modifiable/armor} recipe tool tag: exactly these seven, no other. */
    @Test
    void theSevenArmorModifiersAreTheOnlyArmorOnlyOnes() {
        for (ResourceLocation id : ForgeweaveModifiers.ids()) {
            assertEquals(ARMOR_ONLY.contains(id), ForgeweaveModifiers.get(id).armorOnly(), id + " armorOnly");
            // No shipped modifier is armor-only and harvest/projectile-only at once.
            if (ARMOR_ONLY.contains(id)) {
                assertFalse(ForgeweaveModifiers.get(id).harvestOnly(), id.toString());
                assertFalse(ForgeweaveModifiers.get(id).projectileOnly(), id.toString());
            }
        }
    }

    /** {@code ProtectionModule.builder().eachLevel(2.5f / 2f)} over the incremental entry's {@code amount / needed}. */
    @Test
    void protectionScalesWithEffectiveLevel() {
        assertEquals(2.5F, ForgeweaveModifiers.protectionPoints(ForgeweaveModifiers.PROTECTION_STRONG_PER_LEVEL, 5), 1e-6F);
        assertEquals(0.5F, ForgeweaveModifiers.protectionPoints(ForgeweaveModifiers.PROTECTION_STRONG_PER_LEVEL, 1), 1e-6F);
        assertEquals(4.0F, ForgeweaveModifiers.protectionPoints(ForgeweaveModifiers.PROTECTION_WEAK_PER_LEVEL, 10), 1e-6F);
        for (String name : List.of("fire_protection", "blast_protection", "magic_protection", "melee_protection", "projectile_protection")) {
            float perLevel = name.startsWith("melee") || name.startsWith("projectile") ? 2.0F : 2.5F;
            Optional<CombatSeam> seam = ForgeweaveModifiers.get(id(name)).combatSeam(5);
            assertTrue(seam.isPresent() && seam.get() instanceof Protection, name + " rides the shared Protection seam");
            assertEquals(perLevel, ((Protection) seam.get()).value(), 1e-6F, name + " at level 1");
            assertEquals(perLevel * 8, ((Protection) ForgeweaveModifiers.get(id(name)).combatSeam(40).get()).value(), 1e-6F);
            assertEquals(5, ForgeweaveModifiers.get(id(name)).unitsPerLevel(), name + " needed_per_level");
            assertEquals(1, ForgeweaveModifiers.get(id(name)).occupiedSlots(5), name + " one slot per level");
            assertEquals(2, ForgeweaveModifiers.get(id(name)).occupiedSlots(6), name + " the sixth unit starts level 2");
        }
    }

    /** {@code StatBoostModule.add(KNOCKBACK_RESISTANCE).eachLevel(0.1f)}, summed over the piece's entries. */
    @Test
    void knockbackResistanceIsOneTenthPerLevel() {
        assertEquals(0.1F, ForgeweaveModifiers.KNOCKBACK_RESISTANCE.knockbackResistanceBonus(1), 1e-6F);
        ItemStack piece = new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get());
        assertEquals(0.0F, ForgeweaveModifiers.knockbackResistanceBonus(piece), 1e-6F);
        piece.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(id("knockback_resistance"), 1)));
        assertEquals(0.1F, ForgeweaveModifiers.knockbackResistanceBonus(piece), 1e-6F);
        assertEquals(0.0F, ForgeweaveModifiers.HASTE.knockbackResistanceBonus(50), 1e-6F, "no other modifier touches it");
    }

    /** {@code ThornsModule.type(THORNS).constantFlat(1).randomFlat(3)}, chance {@code 0.15 * effectiveLevel}, 25 cactus per level. */
    @Test
    void thornsIsAFifteenPercentPerLevelOneToFourCounter() {
        assertEquals(25, ForgeweaveModifiers.THORNS.unitsPerLevel());
        assertEquals(0.15F, ForgeweaveModifiers.thornsChance(25), 1e-6F);
        assertEquals(0.45F, ForgeweaveModifiers.thornsChance(75), 1e-6F);
        assertEquals(0.15F * 13 / 25, ForgeweaveModifiers.thornsChance(13), 1e-6F, "partial levels count, as the clone's incremental entry does");
        Optional<CombatSeam> seam = ForgeweaveModifiers.THORNS.combatSeam(50);
        assertTrue(seam.isPresent() && seam.get() instanceof ThornsCounterSeam);
        ThornsCounterSeam thorns = (ThornsCounterSeam) seam.get();
        assertEquals(0.30F, thorns.chance(), 1e-6F);
        assertEquals(1.0F, thorns.constant(), 1e-6F);
        assertEquals(3.0F, thorns.random(), 1e-6F);
    }

    /** {@code IncrementalModifierRecipe}: 1 unit per item, 5 per level; caps at the clone's own stated 80% set-wide ceiling. */
    @Test
    void theShippedProtectionRecipesAreOneReagentPerUnitFivePerLevel() {
        record Expected(String name, ItemStack reagent, int maxLevel) {}
        for (Expected expected : List.of(
                new Expected("fire_protection", new ItemStack(ForgeweaveItems.SEARED_BRICK.get()), 40),
                new Expected("blast_protection", new ItemStack(Items.CRYING_OBSIDIAN), 40),
                new Expected("magic_protection", new ItemStack(Items.GOLD_INGOT), 40),
                new Expected("melee_protection", new ItemStack(ForgeweaveItems.INGOT_COBALT.get()), 50),
                new Expected("projectile_protection", new ItemStack(Items.IRON_INGOT), 50))) {
            ModifierRecipe recipe = shippedRecipe(expected.name());
            assertEquals(id(expected.name()), recipe.modifier());
            assertEquals(1, recipe.cost(), expected.name() + " amount_per_item 1");
            assertEquals(expected.maxLevel(), recipe.maxLevel(), expected.name());
            assertTrue(recipe.matches(expected.reagent()), expected.name() + " reagent");
            assertFalse(recipe.matches(new ItemStack(Items.DIRT)));
        }
    }

    /** {@code defense/knockback_resistance.json}: any of the three anvils, level 1; {@code upgrade/thorns.json}: cactus, 25 per level, max 3. */
    @Test
    void theShippedAnvilAndCactusRecipes() {
        ModifierRecipe anvil = shippedRecipe("knockback_resistance");
        assertEquals(1, anvil.maxLevel());
        for (ItemStack stack : List.of(new ItemStack(Items.ANVIL), new ItemStack(Items.CHIPPED_ANVIL), new ItemStack(Items.DAMAGED_ANVIL))) {
            assertTrue(anvil.matches(stack), stack + " is an anvil");
        }
        ModifierRecipe thorns = shippedRecipe("thorns");
        assertEquals(1, thorns.cost());
        assertEquals(75, thorns.maxLevel(), "3 levels of 25 cactus");
        assertTrue(thorns.matches(new ItemStack(Items.CACTUS)));
        assertEquals(3, thorns.levelsReached(75));
    }
}
