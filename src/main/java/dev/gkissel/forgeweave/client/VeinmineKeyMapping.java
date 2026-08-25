package dev.gkissel.forgeweave.client;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;

import dev.gkissel.forgeweave.tool.VeinmineKey;

/**
 * The veinmine hold-key's client half (issue #719): one rebindable {@link KeyMapping} in vanilla's
 * Controls menu under its own Forgeweave category, default {@code `} (grave accent), and a tick
 * listener that reports each change of its held state to the server -- vanilla's own sneak idiom,
 * one packet per edge rather than one per tick. {@link VeinmineKey} is the server half.
 */
@EventBusSubscriber(modid = "forgeweave", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class VeinmineKeyMapping {

    public static final KeyMapping KEY = new KeyMapping("key.forgeweave.veinmine", GLFW.GLFW_KEY_GRAVE_ACCENT,
            "key.categories.forgeweave");

    private static boolean lastSent;

    @SubscribeEvent
    static void register(RegisterKeyMappingsEvent event) {
        event.register(KEY);
        NeoForge.EVENT_BUS.addListener(VeinmineKeyMapping::onClientTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        if (Minecraft.getInstance().player == null) {
            lastSent = false; // a fresh login starts released on the server too
            return;
        }
        boolean down = KEY.isDown();
        if (down != lastSent) {
            lastSent = down;
            PacketDistributor.sendToServer(new VeinmineKey.Payload(down));
        }
    }

    private VeinmineKeyMapping() {}
}
