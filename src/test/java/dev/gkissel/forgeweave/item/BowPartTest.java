package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.menu.PartBuilderRecipes;
import dev.gkissel.forgeweave.menu.StencilTableMenu;

/**
 * Pins issue #393's two parts against the pinned 1.12 clone's own registration
 * ({@code tools/TinkerTools.java:210-211}):
 *
 * <pre>
 *   bowLimb   = registerToolPart(registry, new ToolPart(Material.VALUE_Ingot * 3), "bow_limb");
 *   bowString = registerToolPart(registry, new ToolPart(Material.VALUE_Ingot),     "bow_string");
 * </pre>
 *
 * <p>Cost is asserted in {@link PartBuilderRecipes#INGOT_VALUE} multiples rather than against the
 * {@code MEDIUM_PART_COST}/{@code SMALL_PART_COST} constants the table happens to reuse, so the
 * numbers here read as the ingot counts upstream states and stay true even if a constant is
 * renamed or re-tiered.
 *
 * <p>The cast assertions record the one place upstream does <em>not</em> treat these two parts
 * alike -- see {@link #bowStringHasNoCastBecauseNoBowstringMaterialMelts()}.
 */
class BowPartTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceLocation forgeweave(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    @Test
    void bowLimbIsABowPartCostingThreeIngots() {
        PartItem part = ForgeweaveItems.PART_BOW_LIMB.get();

        assertEquals(PartItem.Kind.BOW, part.kind());
        assertEquals(Optional.of(3 * PartBuilderRecipes.INGOT_VALUE),
                PartBuilderRecipes.patternCost(new ItemStack(ForgeweaveItems.PATTERN_BOW_LIMB.get())));
        assertEquals(Optional.of(part),
                PartBuilderRecipes.patternPart(new ItemStack(ForgeweaveItems.PATTERN_BOW_LIMB.get())));
    }

    @Test
    void bowStringIsABowstringPartCostingOneIngot() {
        PartItem part = ForgeweaveItems.PART_BOW_STRING.get();

        assertEquals(PartItem.Kind.BOWSTRING, part.kind());
        assertEquals(Optional.of(PartBuilderRecipes.INGOT_VALUE),
                PartBuilderRecipes.patternCost(new ItemStack(ForgeweaveItems.PATTERN_BOW_STRING.get())));
        assertEquals(Optional.of(part),
                PartBuilderRecipes.patternPart(new ItemStack(ForgeweaveItems.PATTERN_BOW_STRING.get())));
    }

    @Test
    void bothPatternsAreStencilTableSelectable() {
        assertTrue(StencilTableMenu.PATTERNS.contains(ForgeweaveItems.PATTERN_BOW_LIMB),
                "the stencil table must offer the bow limb pattern");
        assertTrue(StencilTableMenu.PATTERNS.contains(ForgeweaveItems.PATTERN_BOW_STRING),
                "the stencil table must offer the bow string pattern");
    }

    /**
     * Upstream registers a part's cast from {@code TinkerSmeltery#registerToolpartMeltingCasting},
     * which is only ever reached through a {@code MaterialIntegration} (i.e. a material with a
     * molten fluid) and skips any part whose {@code canUseMaterial} rejects that material. Every
     * BOW material that melts -- iron, copper, cobalt, ardite, manyullyn, steel, ... -- therefore
     * gets a bow limb cast, and the bow limb gets one.
     *
     * <p>{@code bow_string} gets none: the only BOWSTRING materials are {@code string} and
     * {@code vine} (issue #392, upstream's {@code TinkerMaterials#registerBowMaterialStats}), and
     * neither has a fluid or a {@code MaterialIntegration}, so upstream never reaches the cast
     * registration for it. Mirrored here rather than "corrected" -- a bow string cast would be a
     * cast no fluid could ever fill.
     */
    @Test
    void bowStringHasNoCastBecauseNoBowstringMaterialMelts() {
        assertTrue(BuiltInRegistries.ITEM.containsKey(forgeweave("cast_bow_limb")),
                "the bow limb is castable upstream, so it needs a gold cast");
        assertTrue(ForgeweaveItems.CLAY_CASTS.containsKey("cast_bow_limb"),
                "every gold cast has a clay counterpart (issue #292)");

        assertFalse(BuiltInRegistries.ITEM.containsKey(forgeweave("cast_bow_string")),
                "upstream registers no bow string cast -- no BOWSTRING material melts");
        assertFalse(ForgeweaveItems.CLAY_CASTS.containsKey("cast_bow_string"),
                "upstream registers no bow string cast -- no BOWSTRING material melts");
    }
}
