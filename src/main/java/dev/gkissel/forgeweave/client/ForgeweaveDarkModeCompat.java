package dev.gkissel.forgeweave.client;

import net.neoforged.fml.InterModComms;
import net.neoforged.fml.event.lifecycle.InterModEnqueueEvent;

/**
 * DarkModeEverywhere compat (issue #335): DME (Buuz135/DarkModeEverywhere, MIT) swaps the vanilla GUI
 * shader for every {@code GuiGraphics.blit} caller via mixin, so every Forgeweave screen is darkened
 * by construction with no registration needed. The one carve-out is a renderer whose colour *carries
 * meaning* rather than being decorative chrome -- DME's own IMC key {@code dme-shaderblacklist} (a
 * {@code fully.qualified.Class:method} String, substring-matched against the caller, consumed on
 * {@code InterModProcessEvent}) excludes a renderer from the darkening swap. Two Forgeweave renderers
 * qualify: the Smeltery screen's fluid tank tint ({@link SmelteryScreen#renderFluid}, the tank's whole
 * signal) and the Tool Station's material-tinted tool preview ({@link
 * ToolStationScreen#renderToolLayers}). JEI needs nothing here -- Forgeweave uses JEI's own fluid
 * renderer, which DME already ships blacklisted by default.
 *
 * <p>Only registered when DarkModeEverywhere is actually on the mod list (see the call site in
 * {@code Forgeweave}'s constructor) -- the same soft-dependency idiom {@link
 * dev.gkissel.forgeweave.ponder.ForgeweavePonderPlugin} already uses for Ponder. IMC to a mod that
 * isn't loaded is a no-op, but gating keeps the listener off the bus entirely on installs that never
 * asked for DME.
 *
 * <p>{@link #SMELTERY_FLUID_CALLER} and {@link #TOOL_STATION_LAYERS_CALLER} are guarded by
 * {@code ForgeweaveDarkModeCompatTest}: a rename of either anchor method fails the build instead of
 * silently breaking the blacklist entry.
 */
public final class ForgeweaveDarkModeCompat {
    /** DME's IMC key (kept as a literal -- DME is a soft dependency, not a compile-time one). */
    private static final String SHADER_BLACKLIST_KEY = "dme-shaderblacklist";

    static final String SMELTERY_FLUID_CALLER = "dev.gkissel.forgeweave.client.SmelteryScreen:renderFluid";
    static final String TOOL_STATION_LAYERS_CALLER =
            "dev.gkissel.forgeweave.client.ToolStationScreen:renderToolLayers";

    public static void sendShaderBlacklist(final InterModEnqueueEvent event) {
        InterModComms.sendTo("darkmodeeverywhere", SHADER_BLACKLIST_KEY, () -> SMELTERY_FLUID_CALLER);
        InterModComms.sendTo("darkmodeeverywhere", SHADER_BLACKLIST_KEY, () -> TOOL_STATION_LAYERS_CALLER);
    }

    private ForgeweaveDarkModeCompat() {}
}
