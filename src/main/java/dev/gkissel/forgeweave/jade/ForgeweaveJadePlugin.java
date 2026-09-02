package dev.gkissel.forgeweave.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

import dev.gkissel.forgeweave.block.CastingBlock;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;

/**
 * Jade integration (issue #720, docs/SCOPE.md M8 deep-compat, pulled forward 2026-09-02): the
 * casting table/basin's cooling progress as a percentage, and the smeltery controller's molten
 * contents while the player holds shift. Jade ({@code https://github.com/Snownee/Jade},
 * {@code snownee.jade}, MIT) discovers this class itself via classpath scanning for
 * {@link WailaPlugin @WailaPlugin} -- see build.gradle's compileOnly/localRuntime split and
 * neoforge.mods.toml's "optional" entry, the same soft-dependency shape as {@code
 * dev.gkissel.forgeweave.jei.ForgeweaveJeiPlugin}. A world with Jade absent never loads this class
 * at all, so the mod's own classpath never needs it.
 *
 * <p>Registration is split the way Jade's own API is: {@link #register} (common/server side --
 * where the cooling percentage's {@link CastingCoolingProvider} appends its server-only data) and
 * {@link #registerClient} (client-side rendering -- both providers' tooltip text). One class each
 * for the two block families, registered against {@link CastingBlock} and
 * {@link SmelteryControllerBlock} directly rather than per-instance: every casting table/basin
 * shares one {@link CastingBlock} subclass and every smeltery core tier
 * ({@code ForgeweaveBlocks#STANDARD_CORE}/{@code NETHER_CORE}/{@code END_CORE}/{@code DEEP_CORE})
 * shares one {@link SmelteryControllerBlock} subclass, so one registration call each covers all of
 * them.
 */
@WailaPlugin
public final class ForgeweaveJadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(CastingCoolingProvider.INSTANCE, CastingBlock.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CastingCoolingProvider.INSTANCE, CastingBlock.class);
        registration.registerBlockComponent(SmelteryFluidComponentProvider.INSTANCE, SmelteryControllerBlock.class);
    }
}
