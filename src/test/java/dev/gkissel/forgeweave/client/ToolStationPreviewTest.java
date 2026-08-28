package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationTabs;
import dev.gkissel.forgeweave.menu.ToolStationTabs.Tab;
import dev.gkissel.forgeweave.tool.ToolArt;

/** Issue #733: the big preview's tints come from the placed parts, missing ones grey. */
class ToolStationPreviewTest {
    private static final ResourceLocation IRON = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron");
    private static final int IRON_COLOR = 0xD8D8D8;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Tab pickaxe() {
        return ToolStationTabs.get(ToolStationTabs.indexOfTool(ForgeweaveItems.TOOL_PICKAXE.get()));
    }

    private static int color(ResourceLocation id) {
        return id.equals(IRON) ? IRON_COLOR : -1;
    }

    @Test
    void emptySlotsAreAllGrey() {
        Tab tab = pickaxe();
        List<Integer> colors = ToolStationPreview.layerColors(tab, i -> ItemStack.EMPTY, ToolStationPreviewTest::color);
        assertEquals(3, colors.size(), "a pickaxe has head, binding and handle layers");
        assertEquals(List.of(ToolStationPreview.MISSING, ToolStationPreview.MISSING, ToolStationPreview.MISSING), colors);
    }

    @Test
    void aPlacedPartTintsItsOwnLayerOnly() {
        Tab tab = pickaxe();
        ItemStack head = new ItemStack(tab.part(0));
        head.set(ForgeweaveDataComponents.MATERIAL.get(), IRON);
        List<Integer> colors = ToolStationPreview.layerColors(tab, i -> i == 0 ? head : ItemStack.EMPTY,
                ToolStationPreviewTest::color);
        // Layer order is the art's (handle under head under binding), not the slot order.
        List<Integer> layerSlots = ToolArt.layerSlots(tab.entry().constants().parts());
        for (int layer = 0; layer < colors.size(); layer++) {
            int expected = layerSlots.get(layer) == 0 ? IRON_COLOR : ToolStationPreview.MISSING;
            assertEquals(expected, colors.get(layer), "layer " + layer + " draws slot " + layerSlots.get(layer)
                    + " and must take that slot's material colour, or grey while it is empty");
        }
    }

    @Test
    void theWrongPartOrAnUnknownMaterialStaysGrey() {
        Tab tab = pickaxe();
        ItemStack handleInHeadSlot = new ItemStack(tab.part(2));
        handleInHeadSlot.set(ForgeweaveDataComponents.MATERIAL.get(), IRON);
        ItemStack unknown = new ItemStack(tab.part(1));
        unknown.set(ForgeweaveDataComponents.MATERIAL.get(),
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "nothing"));
        List<Integer> colors = ToolStationPreview.layerColors(tab,
                i -> i == 0 ? handleInHeadSlot : i == 1 ? unknown : ItemStack.EMPTY, ToolStationPreviewTest::color);
        assertEquals(List.of(ToolStationPreview.MISSING, ToolStationPreview.MISSING, ToolStationPreview.MISSING), colors);
    }
}
