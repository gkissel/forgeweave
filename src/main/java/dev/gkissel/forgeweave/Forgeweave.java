package dev.gkissel.forgeweave;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import net.minecraft.core.Registry;

import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlockEntities;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.data.ForgeweaveDataGenerators;
import dev.gkissel.forgeweave.item.ForgeweaveCreativeTab;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.RenameStationItemPayload;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.recipe.ForgeweaveRecipeSerializers;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

// The value here must match the modId in META-INF/neoforge.mods.toml.
@Mod(Forgeweave.MODID)
public class Forgeweave {
    public static final String MODID = "forgeweave";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Forgeweave(IEventBus modEventBus, ModContainer modContainer) {
        ForgeweaveDataComponents.DATA_COMPONENTS.register(modEventBus);
        ForgeweaveBlocks.BLOCKS.register(modEventBus);
        ForgeweaveBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ForgeweaveItems.ITEMS.register(modEventBus);
        ForgeweaveMenus.MENUS.register(modEventBus);
        ForgeweaveCreativeTab.TABS.register(modEventBus);
        ForgeweaveRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        // SERVER type: see ForgeweaveConfig javadoc for why this must sync client<->server.
        modContainer.registerConfig(ModConfig.Type.SERVER, ForgeweaveConfig.SPEC);
        modEventBus.addListener(this::registerDataPackRegistries);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        // The Pattern Chest/Part Chest (issue #66) expose their inventory as an IItemHandler so
        // adjacent stations' side-inventory panels pick them up (SideInventory#find).
        modEventBus.addListener(ChestBlockEntity::registerCapabilities);
        modEventBus.addListener(ForgeweaveDataGenerators::gatherData);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        // Traits that key off what is being hit have no Item hook to live in (see ForgeweaveTraits).
        NeoForge.EVENT_BUS.addListener(ForgeweaveTraits::onIncomingDamage);
    }

    private void onServerStarted(final ServerStartedEvent event) {
        // Datapack authors need to see whether their material JSON was picked up (ADR-0002).
        Registry<Material> materials = event.getServer().registryAccess().registryOrThrow(Material.REGISTRY);
        LOGGER.info("Loaded {} materials: {}", materials.size(), materials.keySet());
    }

    private void registerDataPackRegistries(final DataPackRegistryEvent.NewRegistry event) {
        // Passing the codec as the network codec too makes NeoForge sync materials server -> client.
        event.dataPackRegistry(Material.REGISTRY, Material.CODEC, Material.CODEC);
        // Modifier application recipes, same deal (ADR-0004): the client needs them so the Tool
        // Station screen can explain a rejection without a payload of its own.
        event.dataPackRegistry(ModifierRecipe.REGISTRY, ModifierRecipe.CODEC, ModifierRecipe.CODEC);
    }

    /** The Tool Station's rename field is the mod's only message that a menu button can't carry. */
    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        RenameStationItemPayload.register(event.registrar("1"));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Forgeweave common setup complete");
    }
}
