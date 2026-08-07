package dev.gkissel.forgeweave;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import net.minecraft.core.Registry;

import dev.gkissel.forgeweave.item.ForgeweaveCreativeTab;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;

// The value here must match the modId in META-INF/neoforge.mods.toml.
@Mod(Forgeweave.MODID)
public class Forgeweave {
    public static final String MODID = "forgeweave";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Forgeweave(IEventBus modEventBus, ModContainer modContainer) {
        ForgeweaveDataComponents.DATA_COMPONENTS.register(modEventBus);
        ForgeweaveItems.ITEMS.register(modEventBus);
        ForgeweaveCreativeTab.TABS.register(modEventBus);
        modEventBus.addListener(this::registerDataPackRegistries);
        modEventBus.addListener(this::commonSetup);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
    }

    private void onServerStarted(final ServerStartedEvent event) {
        // Datapack authors need to see whether their material JSON was picked up (ADR-0002).
        Registry<Material> materials = event.getServer().registryAccess().registryOrThrow(Material.REGISTRY);
        LOGGER.info("Loaded {} materials: {}", materials.size(), materials.keySet());
    }

    private void registerDataPackRegistries(final DataPackRegistryEvent.NewRegistry event) {
        // Passing the codec as the network codec too makes NeoForge sync materials server -> client.
        event.dataPackRegistry(Material.REGISTRY, Material.CODEC, Material.CODEC);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Forgeweave common setup complete");
    }
}
