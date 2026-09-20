package dev.gkissel.forgeweave.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.ToIntFunction;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolArt;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Resolves the big station preview's per-layer tints from the parts actually in the slots (issue
 * #733): a layer whose slot holds its part takes that part's material colour, every other layer is
 * {@link #MISSING} grey. The sidebar icons keep upstream's fixed GUI tints
 * ({@link ToolStationScreen#TOOL_LAYER_COLORS}); only the preview reads the slots.
 */
public final class ToolStationPreview {
    /** The tint a layer takes while its part is not in the station yet. */
    public static final int MISSING = 0x5A5A5A;

    private ToolStationPreview() {}

    /**
     * One 0xRRGGBB per {@link ToolArt#layers} entry of {@code entry}'s tool.
     *
     * <p>Takes the entry rather than the tab since issue #1081: an armor tab stands for four pieces
     * and the caller has already resolved which of them the plating in the first slot picked.
     *
     * @param slotItem what sits in the tab's slot {@code i}
     * @param materialColor a material id's bare 0xRRGGBB, or a negative value when unknown
     */
    public static List<Integer> layerColors(ToolAssemblyRecipes.Entry entry, IntFunction<ItemStack> slotItem,
            ToIntFunction<ResourceLocation> materialColor) {
        List<ToolConstants.PartSlot> parts = entry.constants().parts();
        List<Integer> slots = ToolArt.layerSlots(parts);
        List<Integer> colors = new ArrayList<>(slots.size());
        for (int slot : slots) {
            ItemStack stack = slotItem.apply(slot);
            ResourceLocation material = stack.is(entry.part(slot))
                    ? stack.get(ForgeweaveDataComponents.MATERIAL.get()) : null;
            int color = material == null ? -1 : materialColor.applyAsInt(material);
            colors.add(color < 0 ? MISSING : color & 0xFFFFFF);
        }
        return colors;
    }
}
