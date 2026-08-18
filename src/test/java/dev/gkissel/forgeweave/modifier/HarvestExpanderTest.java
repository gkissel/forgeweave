package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
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
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.AoeHarvest;

/**
 * Issue #438's unit half: the two expander modifiers, their shipped recipes, and which tool shapes
 * upstream's {@code ModifierAspect.aoeOnly} lets them onto. The geometry they produce is
 * {@code gametest.ExpanderGameTests}, which needs a real level to break blocks in.
 *
 * <p>Upstream references, all pinned {@code c01173c}: {@code tools/modifiers/ModHarvestSize.java}
 * (the marker modifier and its {@code SingleAspect + DataAspect + aoeOnly + freeModifier} aspects),
 * {@code tools/TinkerModifiers.java:169-173} (one {@code matExpanderW}/{@code matExpanderH} per
 * application) and {@code tools/ToolEvents.java:38-70} (the per-tool magnitudes).
 */
class HarvestExpanderTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static ModifierRecipe shippedRecipe(String path) {
        JsonElement json;
        try (InputStream in = HarvestExpanderTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
        return ModifierRecipe.CODEC.parse(ops, json).getOrThrow();
    }

    private static ItemStack withExpanders(ToolItem tool, ResourceLocation... modifiers) {
        ItemStack stack = new ItemStack(tool);
        stack.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(modifiers).stream().map(id -> new ModifierEntry(id, 1)).toList());
        return stack;
    }

    private static final ResourceLocation WIDTH = ResourceLocation.fromNamespaceAndPath("forgeweave", "harvest_width");
    private static final ResourceLocation HEIGHT = ResourceLocation.fromNamespaceAndPath("forgeweave", "harvest_height");

    @Test
    void bothExpandersAreOneShotAndTakeTheirOwnReagent() {
        ModifierRecipe width = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/harvest_width.json");
        assertEquals(WIDTH, width.modifier());
        assertEquals(1, width.maxLevel(), "upstream ModHarvestSize's SingleAspect: one application only");
        assertEquals(1, width.cost());
        assertTrue(width.reagent().test(new ItemStack(ForgeweaveItems.EXPANDER_W.get())));
        assertFalse(width.reagent().test(new ItemStack(ForgeweaveItems.EXPANDER_H.get())),
                "the horizontal expander is the width reagent, not the height one");

        ModifierRecipe height = shippedRecipe("/data/forgeweave/forgeweave/modifier_recipe/harvest_height.json");
        assertEquals(HEIGHT, height.modifier());
        assertEquals(1, height.maxLevel());
        assertEquals(1, height.cost());
        assertTrue(height.reagent().test(new ItemStack(ForgeweaveItems.EXPANDER_H.get())));

        assertEquals(ForgeweaveModifiers.HARVEST_WIDTH, ForgeweaveModifiers.get(WIDTH));
        assertEquals(ForgeweaveModifiers.HARVEST_HEIGHT, ForgeweaveModifiers.get(HEIGHT));
    }

    /** Upstream's {@code freeModifier} aspect: one application, one modifier slot. */
    @Test
    void eachExpanderCostsExactlyOneModifierSlot() {
        assertEquals(1, ForgeweaveModifiers.HARVEST_WIDTH.occupiedSlots(1));
        assertEquals(1, ForgeweaveModifiers.HARVEST_HEIGHT.occupiedSlots(1));
        assertEquals(2, ForgeweaveModifiers.occupiedSlots(
                withExpanders(ForgeweaveItems.TOOL_PICKAXE.get(), WIDTH, HEIGHT)));
    }

    @Test
    void aModifierOnlyNamesItsAxisAndOnlyWhenApplied() {
        assertEquals(java.util.Optional.of(Modifier.AoeAxis.WIDTH), ForgeweaveModifiers.HARVEST_WIDTH.aoeExpansion(1));
        assertEquals(java.util.Optional.of(Modifier.AoeAxis.HEIGHT), ForgeweaveModifiers.HARVEST_HEIGHT.aoeExpansion(1));
        assertTrue(ForgeweaveModifiers.HARVEST_WIDTH.aoeExpansion(0).isEmpty());
        // Upstream ModHarvestSize#applyEffect is empty: no stat on the tool moves.
        assertEquals(6.0F, ForgeweaveModifiers.HARVEST_WIDTH.miningSpeed(1, 6.0F), 1.0e-6F);
        assertEquals(5.0F, ForgeweaveModifiers.HARVEST_WIDTH.attackDamage(1, 5.0F, 5.0F), 1.0e-6F);
    }

    @Test
    void theToolsAxesAreReadOffTheStack() {
        assertEquals(Set.of(), ForgeweaveModifiers.aoeExpansion(new ItemStack(ForgeweaveItems.TOOL_HAMMER.get())));
        assertEquals(EnumSet.of(Modifier.AoeAxis.WIDTH),
                ForgeweaveModifiers.aoeExpansion(withExpanders(ForgeweaveItems.TOOL_HAMMER.get(), WIDTH)));
        assertEquals(EnumSet.of(Modifier.AoeAxis.WIDTH, Modifier.AoeAxis.HEIGHT),
                ForgeweaveModifiers.aoeExpansion(withExpanders(ForgeweaveItems.TOOL_HAMMER.get(), WIDTH, HEIGHT)));
    }

    /**
     * Upstream's {@code aoeOnly} aspect is {@code Category.AOE}, which every {@code AoeToolCore}
     * subclass has and nothing else does: the pickaxe, shovel, hatchet, kama and mattock (whose base
     * box is just the block hit) as well as the four large tools. The vein hammer is Forgeweave's own
     * and has no width or height axis at all, so it is excluded -- a deviation recorded in the PR.
     */
    @Test
    void onlyToolsWithAnAreaToWidenAcceptTheExpanders() {
        assertTrue(expandable(ForgeweaveItems.TOOL_PICKAXE.get()));
        assertTrue(expandable(ForgeweaveItems.TOOL_SHOVEL.get()));
        assertTrue(expandable(ForgeweaveItems.TOOL_HATCHET.get()));
        assertTrue(expandable(ForgeweaveItems.TOOL_KAMA.get()));
        assertTrue(expandable(ForgeweaveItems.TOOL_MATTOCK.get()));
        assertTrue(expandable(ForgeweaveItems.TOOL_HAMMER.get()));
        assertTrue(expandable(ForgeweaveItems.TOOL_EXCAVATOR.get()));
        assertTrue(expandable(ForgeweaveItems.TOOL_LUMBERAXE.get()));
        assertTrue(expandable(ForgeweaveItems.TOOL_SCYTHE.get()));

        assertFalse(expandable(ForgeweaveItems.TOOL_VEIN_HAMMER.get()), "a vein has no width or height");
        assertFalse(expandable(ForgeweaveItems.TOOL_BROADSWORD.get()), "a sword mines no area");
        assertFalse(AoeHarvest.Shape.NONE.expandable());
    }

    private static boolean expandable(ToolItem tool) {
        return tool.aoeShape().expandable();
    }
}
