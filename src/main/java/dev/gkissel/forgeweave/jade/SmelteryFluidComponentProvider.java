package dev.gkissel.forgeweave.jade;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.fluids.FluidStack;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;

/**
 * Issue #720: the smeltery controller's molten contents while the player holds shift, per the
 * maintainer's 2026-08-28 comment on the issue. Reads {@link SmelteryControllerBlockEntity#tank()}
 * straight off the client-side block entity Jade hands back through {@link BlockAccessor} --
 * {@code SmelteryControllerBlockEntity}'s own network sync ({@code getUpdateTag}/{@code
 * getUpdatePacket}) already carries the whole tank on every content change (see that class's
 * javadoc), so there is nothing this provider needs to sync itself.
 */
public final class SmelteryFluidComponentProvider implements IBlockComponentProvider {
    public static final SmelteryFluidComponentProvider INSTANCE = new SmelteryFluidComponentProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "smeltery_fluids");

    private SmelteryFluidComponentProvider() {}

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        if (!(accessor.getBlockEntity() instanceof SmelteryControllerBlockEntity smeltery)) {
            return;
        }
        if (!Screen.hasShiftDown()) {
            if (!smeltery.tank().fluids().isEmpty()) {
                tooltip.add(Component.translatable("tooltip.forgeweave.hold_shift"));
            }
            return;
        }
        for (FluidStack fluid : smeltery.tank().fluids()) {
            tooltip.add(Component.translatable("waila.forgeweave.smeltery.fluid", fluid.getHoverName(), fluid.getAmount() + " mb"));
        }
    }
}
