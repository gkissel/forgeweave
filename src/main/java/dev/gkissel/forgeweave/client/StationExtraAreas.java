package dev.gkissel.forgeweave.client;

import java.util.List;

import net.minecraft.client.renderer.Rect2i;

/**
 * Implemented by every station screen that draws chrome outside its own {@code imageWidth}/{@code
 * imageHeight} box -- tool tabs, information panels, side-inventory panels (issue #68 fix 4).
 *
 * <p>{@code AbstractContainerScreen} only ever tells anything about the base panel rectangle, so an
 * overlay mod (JEI here, via {@code jei.StationGuiHandler}) has no way to know those modules are
 * there and happily draws its item list on top of them. This is the one place each screen states
 * where its extra rectangles are, in absolute screen coordinates, so the JEI plugin can hand them
 * over as exclusion zones without reaching into any screen's private layout constants.
 */
public interface StationExtraAreas {
    List<Rect2i> extraGuiAreas();
}
