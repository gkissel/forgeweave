package dev.gkissel.forgeweave.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Base class for every Forgeweave container screen (issue #75 defect 2).
 *
 * <p>It exists for exactly one reason: {@link AbstractContainerScreen#render} does <em>not</em> call
 * {@link #renderTooltip}. Every vanilla container screen overrides {@code render} to add that call,
 * and a mod screen that forgets to is silently shipped with no item tooltips at all -- no compile
 * error, no test failure, nothing visible until someone hovers a slot in a playtest. That happened
 * once already (issue #43, fixed in #57 by adding the override to {@code PartBuilderScreen} and
 * {@code ToolStationScreen}) and then happened again to {@link CraftingStationScreen}, {@link
 * StencilTableScreen} and {@link ChestScreen}, because the #57 fix was three copies of a two-line
 * override rather than one shared place.
 *
 * <p>So the override lives here, once, and {@code StationScreenTooltipTest} fails the build if any
 * {@code AbstractContainerScreen} subclass in this package neither extends this class nor overrides
 * {@code render} itself. Subclasses that need extra hover text (tab buttons, pattern buttons --
 * things that aren't slots) override {@link #renderTooltip}, call {@code super}, and are picked up
 * automatically.
 *
 * <p>Deliberately thin: only the behaviour all five screens must share. Panel geometry, side panels
 * and JEI exclusion rectangles ({@link StationExtraAreas}) stay per-screen, because they genuinely
 * differ -- {@code ChestScreen} has no side chrome at all.
 */
public abstract class StationScreen<T extends AbstractContainerMenu> extends AbstractContainerScreen<T> {

    protected StationScreen(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
