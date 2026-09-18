package dev.gkissel.forgeweave.wthit;

import net.minecraft.network.chat.Component;

import mcp.mobius.waila.api.IBlockAccessor;
import mcp.mobius.waila.api.IBlockComponentProvider;
import mcp.mobius.waila.api.IPluginConfig;
import mcp.mobius.waila.api.ITooltip;

import dev.gkissel.forgeweave.tool.MiningLevel;

/**
 * D-M8-9 (issue #968): WTHIT's side of {@code dev.gkissel.forgeweave.jade.MiningLevelProvider} --
 * see that class for why the line exists and why both facts are already client-side. The shared
 * logic is in {@code MiningLevel}, which names neither overlay.
 */
public final class MiningLevelProvider implements IBlockComponentProvider {
    public static final MiningLevelProvider INSTANCE = new MiningLevelProvider();

    private MiningLevelProvider() {}

    @Override
    public void appendBody(ITooltip tooltip, IBlockAccessor accessor, IPluginConfig config) {
        Component line = MiningLevel.line(accessor.getBlockState(), accessor.getPlayer().getMainHandItem());
        if (line != null) {
            tooltip.addLine(line);
        }
    }
}
