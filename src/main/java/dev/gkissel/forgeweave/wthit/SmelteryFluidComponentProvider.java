package dev.gkissel.forgeweave.wthit;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.neoforged.neoforge.fluids.FluidStack;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;

import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;

/**
 * Issue #720: the smeltery controller's molten contents while the player holds shift, WTHIT's side
 * of {@code dev.gkissel.forgeweave.jade.SmelteryFluidComponentProvider} -- see that class's javadoc
 * for why this reads the tank straight off the client-side block entity rather than syncing its own
 * data.
 */
public final class SmelteryFluidComponentProvider implements IBlockComponentProvider {
    public static final SmelteryFluidComponentProvider INSTANCE = new SmelteryFluidComponentProvider();

    private SmelteryFluidComponentProvider() {}

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof SmelteryControllerBlockEntity smeltery)) {
            return;
        }
        if (!Screen.hasShiftDown()) {
            if (!smeltery.tank().fluids().isEmpty()) {
                tooltip.addLine(Component.translatable("tooltip.forgeweave.hold_shift"));
            }
            return;
        }
        for (FluidStack fluid : smeltery.tank().fluids()) {
            tooltip.addLine(Component.translatable("waila.forgeweave.smeltery.fluid", fluid.getHoverName(), fluid.getAmount() + " mb"));
        }
    }
}
