package dev.gkissel.forgeweave.wthit;

import net.minecraft.network.chat.Component;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IDataProvider;
import mcp.mobius.waila.api.IDataWriter;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.IServerAccessor;
import mcp.mobius.waila.api.ITooltip;

import dev.gkissel.forgeweave.block.CastingBlockEntity;

/**
 * Issue #720: the casting table/basin's cooling progress as a percentage, WTHIT's side of
 * {@code dev.gkissel.forgeweave.jade.CastingCoolingProvider} -- see that class's javadoc for why
 * this reads {@link CastingBlockEntity#coolingPercent()} through the overlay's own server-data sync
 * rather than the block entity's regular network sync.
 */
public final class CastingCoolingProvider implements IBlockComponentProvider, IDataProvider<CastingBlockEntity> {
    public static final CastingCoolingProvider INSTANCE = new CastingCoolingProvider();

    private static final String TAG_PERCENT = "forgeweave_cooling_percent";

    private CastingCoolingProvider() {}

    @Override
    public void appendData(IDataWriter data, IServerAccessor<CastingBlockEntity> accessor, IPluginConfig config) {
        data.raw().putInt(TAG_PERCENT, accessor.getTarget().coolingPercent());
    }

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        int percent = accessor.getData().raw().getInt(TAG_PERCENT);
        if (percent > 0) {
            tooltip.addLine(Component.translatable("waila.forgeweave.casting.cooling", percent + "%"));
        }
    }
}
