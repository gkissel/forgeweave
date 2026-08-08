package dev.gkissel.forgeweave.jei;

import java.util.List;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.AbstractContainerMenu;

import mezz.jei.api.gui.handlers.IGuiContainerHandler;

import dev.gkissel.forgeweave.client.StationExtraAreas;

/**
 * Tells JEI about the chrome a station screen draws outside its own {@code imageWidth}/{@code
 * imageHeight} box (docs/SCOPE.md issue #68 fix 4): tool tabs, information panels and side-inventory
 * panels. JEI only knows a container screen's base rectangle, so without this its item list happily
 * renders on top of -- and swallows clicks meant for -- every one of those modules.
 *
 * <p>One handler serves all four stations; each screen states its own rectangles via {@link
 * StationExtraAreas}, so no station layout constant has to be duplicated here.
 */
final class StationGuiHandler<M extends AbstractContainerMenu, T extends AbstractContainerScreen<M> & StationExtraAreas>
        implements IGuiContainerHandler<T> {

    @Override
    public List<Rect2i> getGuiExtraAreas(T screen) {
        return screen.extraGuiAreas();
    }
}
