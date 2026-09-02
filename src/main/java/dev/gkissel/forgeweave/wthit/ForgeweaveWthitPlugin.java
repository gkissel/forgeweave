package dev.gkissel.forgeweave.wthit;

import mcp.mobius.waila.api.IRegistrar;
import mcp.mobius.waila.api.IWailaPlugin;
import mcp.mobius.waila.api.TooltipPosition;

import dev.gkissel.forgeweave.block.CastingBlock;
import dev.gkissel.forgeweave.block.SmelteryControllerBlock;

/**
 * WTHIT integration (issue #720, docs/SCOPE.md M8 deep-compat, pulled forward 2026-09-02): the same
 * two features {@code dev.gkissel.forgeweave.jade.ForgeweaveJadePlugin} adds to Jade, against
 * WTHIT's own API. WTHIT ({@code https://github.com/badasintended/wthit}, {@code mcp.mobius.waila},
 * MIT-descended) forked that package name from HWYLA before Jade renamed away from it to
 * {@code snownee.jade.api}, which is why the two plugins share no types despite doing the same job.
 *
 * <p>Unlike Jade's classpath-scanned {@code @WailaPlugin}, WTHIT discovers its plugins from an
 * explicit manifest -- {@code src/main/resources/wthit_plugins.json} names this class -- so there is
 * no annotation here. build.gradle's compileOnly-only dependency split (no {@code localRuntime}
 * pair, see that dependency's comment) means this class compiles against WTHIT's API but the mod
 * never actually loads WTHIT in this project's own dev/test runs; Jade's localRuntime pair is what
 * exercises the "overlay mod present" path in practice, and the "WTHIT present" path is exercised by
 * installing it in a real client instead, the same shape {@code ForgeweavePonderPlugin}'s javadoc
 * describes for Ponder's own guard.
 */
public final class ForgeweaveWthitPlugin implements IWailaPlugin {

    @Override
    public void register(IRegistrar registrar) {
        registrar.addBlockData(CastingCoolingProvider.INSTANCE, CastingBlock.class);
        registrar.addComponent(CastingCoolingProvider.INSTANCE, TooltipPosition.BODY, CastingBlock.class);
        registrar.addComponent(SmelteryFluidComponentProvider.INSTANCE, TooltipPosition.BODY, SmelteryControllerBlock.class);
    }
}
