package dev.gkissel.forgeweave;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

// The value here must match the modId in META-INF/neoforge.mods.toml.
@Mod(Forgeweave.MODID)
public class Forgeweave {
    public static final String MODID = "forgeweave";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Forgeweave(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Forgeweave common setup complete");
    }
}
