package dev.gkissel.forgeweave.jei;

import java.util.Optional;

import mezz.jei.api.gui.builder.IClickableIngredientFactory;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.runtime.IClickableIngredient;

import dev.gkissel.forgeweave.client.SmelteryScreen;
import dev.gkissel.forgeweave.client.SmelteryScreen.TankHover;

/**
 * Maps the mouse position over the smeltery tank to the hovered fluid, so JEI's R/U recipe lookup
 * works on tank contents (issue #308) -- upstream 1.12's {@code TinkerGuiTankHandler}, registered in
 * {@code JEIPlugin#registerGuiHandlers}. The mouse-position-to-fluid-band maths itself lives on
 * {@link SmelteryScreen} ({@code hoveredTankFluid}/{@code tankBandTop}, both JEI-free and pinned by
 * {@code SmelteryTooltipTest} against the same band maths the tank's tooltip and click handler use);
 * this class only wraps that answer as JEI's {@link IClickableIngredient}.
 */
final class SmelteryTankGuiHandler implements IGuiContainerHandler<SmelteryScreen> {

    @Override
    public Optional<? extends IClickableIngredient<?>> getClickableIngredientUnderMouse(
            IClickableIngredientFactory factory, SmelteryScreen screen, double mouseX, double mouseY) {
        Optional<TankHover> hover = screen.hoveredTankFluid(mouseX, mouseY);
        if (hover.isEmpty()) {
            return Optional.empty();
        }
        TankHover tank = hover.get();
        return factory.createBuilder(NeoForgeTypes.FLUID_STACK, tank.fluid()).buildWithArea(tank.area());
    }
}
