package dev.gkissel.forgeweave.jade;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.tool.MiningLevel;

/**
 * D-M8-9 (issue #968): the mining level the looked-at block needs, beside the level of the tool the
 * player is holding. Registered against {@code Block.class} rather than a Forgeweave block, because
 * the line is about the ladder rather than about Forgeweave's own blocks -- a vanilla pickaxe held
 * at a vanilla ore has to read correctly too.
 *
 * <p>Client-side only, with no {@code IServerDataProvider} half: both facts are already on the
 * client. The block state is what the overlay is drawn from, and the held stack is the player's own.
 * That is the difference from {@link CastingCoolingProvider}, whose cooldown only exists server-side.
 *
 * <p>All of the work is in {@code MiningLevel}, which names nothing from Jade, so the WTHIT
 * provider ({@code wthit.MiningLevelProvider}) is the same three lines against the other API.
 */
public final class MiningLevelProvider implements IBlockComponentProvider {
    public static final MiningLevelProvider INSTANCE = new MiningLevelProvider();

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "mining_level");

    private MiningLevelProvider() {}

    @Override
    public ResourceLocation getUid() {
        return UID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        Component line = MiningLevel.line(accessor.getBlockState(), accessor.getPlayer().getMainHandItem());
        if (line != null) {
            tooltip.add(line);
        }
    }
}
