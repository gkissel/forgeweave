package dev.gkissel.forgeweave.jade;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.CastingBlockEntity;

/**
 * Issue #720: the casting table/basin's cooling progress as a percentage. {@link CastingBlockEntity}
 * tracks the wall-clock cooldown itself (server-only, since it reads {@code Level#getGameTime()}),
 * so this reads it through Jade's own {@link IServerDataProvider} sync -- the ticket's preferred
 * transport over piggybacking the block entity's existing network sync, which only fires on a
 * content change and would leave the percentage frozen between pours.
 */
public final class CastingCoolingProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    public static final CastingCoolingProvider INSTANCE = new CastingCoolingProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "casting_cooling");
    private static final String TAG_PERCENT = "percent";

    private CastingCoolingProvider() {}

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getBlockEntity() instanceof CastingBlockEntity casting) {
            data.putInt(TAG_PERCENT, casting.coolingPercent());
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        int percent = accessor.getServerData().getInt(TAG_PERCENT);
        if (percent > 0) {
            tooltip.add(Component.translatable("waila.forgeweave.casting.cooling", percent + "%"));
        }
    }
}
