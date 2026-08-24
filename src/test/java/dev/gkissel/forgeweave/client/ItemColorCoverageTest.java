package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolArt;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Regression for issue #256: the curved blade and katana blade parts rendered white because
 * {@code ForgeweaveItemColors}' part-tint registration was a hand list that #159/#160's two newest
 * head parts were never added to. Coverage-shaped, like {@code ForgeweaveCreativeTabTest}: asserts
 * the color-handler registration covers every registered part item and every assemblable tool, and
 * that every tool model layer resolves a real part slot to read its material tint from -- so the
 * next part or tool cannot silently lose its tint the same way.
 */
class ItemColorCoverageTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyRegisteredPartItemGetsTheMaterialTintHandler() {
        List<Item> tinted = List.of(ForgeweaveItemColors.tintedPartItems());

        List<Item> missing = ForgeweaveItems.ITEMS.getEntries().stream()
                .<Item>map(DeferredHolder::get)
                .filter(item -> item instanceof PartItem)
                .filter(item -> !tinted.contains(item))
                .toList();

        assertTrue(missing.isEmpty(), () -> "part items missing a material tint handler (they render white): "
                + missing.stream().map(BuiltInRegistries.ITEM::getKey).toList());
    }

    @Test
    void everyAssemblableToolGetsTheToolTintHandler() {
        List<Item> tinted = List.of(ForgeweaveItemColors.tintedToolItems());

        List<Item> missing = ToolAssemblyRecipes.ENTRIES.stream()
                .<Item>map(entry -> entry.tool().get())
                .filter(tool -> !tinted.contains(tool))
                .toList();

        assertTrue(missing.isEmpty(), () -> "assemblable tools missing a material tint handler: "
                + missing.stream().map(BuiltInRegistries.ITEM::getKey).toList());
    }

    /**
     * Every model layer of every tool tints from a real part slot: {@code ForgeweaveItemColors}'
     * {@code toolMaterialTint} answers tintIndex {@code n} from {@code ToolArt.layerSlots(...).get(n)},
     * so a layer without a slot behind it (or a slot out of the materials list's range) is a layer
     * that silently renders white.
     */
    @Test
    void everyToolModelLayerTintsFromARealPartSlot() {
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            List<ToolConstants.PartSlot> parts = entry.constants().parts();
            List<Integer> slots = ToolArt.layerSlots(parts);

            assertEquals(parts.size(), slots.size(),
                    entry.constants().id() + ": every part must draw exactly one tinted model layer");
            for (int layer = 0; layer < slots.size(); layer++) {
                int slot = slots.get(layer);
                assertTrue(slot >= 0 && slot < parts.size(), entry.constants().id() + " layer" + layer
                        + " tints from part slot " + slot + ", which is outside its " + parts.size() + " part slots");
            }
        }
    }
}
