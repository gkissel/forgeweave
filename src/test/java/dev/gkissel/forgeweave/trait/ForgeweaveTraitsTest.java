package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.material.Material;

/**
 * The per-part trait resolution an assembled tool goes through (issue #94). The trait <em>behaviors</em>
 * are covered on real assembled tools in {@code TraitGameTests}; this covers only which ids come out
 * of which material slot.
 */
class ForgeweaveTraitsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    /**
     * Upstream 1.12's iron ({@code addTrait(magnetic2, HEAD)} + {@code addTrait(magnetic)}): the head
     * slot contributes the head-scoped list, the binding and handle slots the general one.
     */
    @Test
    void resolveTakesEachMaterialsTraitsForTheSlotItWasUsedIn() {
        Material iron = material(new Material.Traits(List.of(id("magnetic")), List.of(id("magnetic2"))));
        Material wood = material(Material.Traits.general(id("ecological")));

        assertEquals(List.of(id("magnetic2"), id("ecological")), ForgeweaveTraits.resolve(iron, wood, wood));
        assertEquals(List.of(id("ecological"), id("magnetic")), ForgeweaveTraits.resolve(wood, iron, iron));
    }

    /** The same id from two slots still counts once, head order first (see the class javadoc). */
    @Test
    void resolveDeduplicatesAcrossSlots() {
        Material wood = material(Material.Traits.general(id("ecological")));
        Material stone = material(Material.Traits.general(id("cheap")));

        assertEquals(List.of(id("cheap"), id("ecological")), ForgeweaveTraits.resolve(stone, wood, stone));
    }

    /** Head-scoped traits stack onto the durability pool in order; unknown ids leave it alone. */
    @Test
    void headDurabilityAppliesEveryKnownHeadTrait() {
        assertEquals(100, ForgeweaveTraits.headDurability(List.of(), 100));
        assertEquals(100, ForgeweaveTraits.headDurability(List.of(id("not_a_trait")), 100));
        // forgeweave:cheapskate: max(1, durability * 80 / 100) -- issue #493 split it off cheap.
        assertEquals(80, ForgeweaveTraits.headDurability(List.of(id("cheapskate")), 100));
        assertEquals(64, ForgeweaveTraits.headDurability(List.of(id("cheapskate"), id("cheapskate")), 100));
    }

    private static Material material(Material.Traits traits) {
        return new Material(
                new Material.Head(100, 1.0F, 1.0F),
                new Material.Handle(1.0F, 0),
                0,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                traits,
                List.of(new Material.CraftingItem(Ingredient.of(Items.STICK), 1)),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));
    }
}
