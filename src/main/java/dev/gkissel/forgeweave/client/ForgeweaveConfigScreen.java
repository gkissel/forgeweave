package dev.gkissel.forgeweave.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * T80 (parity audit checklist, "register NeoForge's ConfigurationScreen"): both {@code
 * ForgeweaveConfig} (SERVER -- singleplayer/LAN host only) and {@code ForgeweaveClientConfig}
 * (CLIENT) edit in place from the mod list's config button, through NeoForge's own generic screen
 * rather than a bespoke one -- neither spec has enough entries to earn a hand-built GUI.
 *
 * <p>{@code ConfigurationScreen} and {@code IConfigScreenFactory} are client-only NeoForge types
 * ({@code net.neoforged.neoforge.client.gui}), so this registration cannot live inline in {@code
 * Forgeweave}'s constructor the way {@link dev.gkissel.forgeweave.config.ForgeweaveConfig}'s own
 * registration does: a common-loaded class whose bytecode merely *references* a client-only type
 * fails NeoForge's dist-cleaner at class-verification time on a dedicated server, even behind a
 * runtime {@code Dist.CLIENT} check -- the same reason every other screen/renderer registration in
 * this mod is its own {@code @EventBusSubscriber(..., Dist.CLIENT)} class (see {@link
 * ForgeweaveItemProperties}) rather than a branch in the shared constructor. FML's own annotation
 * scan skips this class entirely on a dedicated server before any of it is loaded, which an {@code
 * if} guard inside a shared class cannot do.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveConfigScreen {

    @SubscribeEvent
    static void onClientSetup(final FMLClientSetupEvent event) {
        ModContainer modContainer = ModList.get().getModContainerById(Forgeweave.MODID)
                .orElseThrow(() -> new IllegalStateException(Forgeweave.MODID + " is missing its own ModContainer"));
        // IConfigScreenFactory#createScreen(ModContainer, Screen) -- both parameters are the ones the
        // mod list menu itself supplies at click time, not necessarily this same modContainer capture,
        // but they are the same object in practice; ConfigurationScreen only needs the container.
        modContainer.registerExtensionPoint(IConfigScreenFactory.class,
                (container, modListScreen) -> new ConfigurationScreen(container, modListScreen));
    }

    private ForgeweaveConfigScreen() {}
}
