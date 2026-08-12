package dev.gkissel.forgeweave;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import net.minecraft.core.Registry;

import dev.gkissel.forgeweave.advancement.ForgeweaveCriteriaTriggers;
import dev.gkissel.forgeweave.block.CastingBlockEntity;
import dev.gkissel.forgeweave.block.ChestBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlockEntities;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedDrainBlockEntity;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.data.ForgeweaveDataGenerators;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveCreativeTab;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.RenameStationItemPayload;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.ponder.ForgeweavePonderPlugin;
import dev.gkissel.forgeweave.recipe.AlloyRecipe; // #98
import dev.gkissel.forgeweave.recipe.ForgeweaveRecipeSerializers;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;
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
        ForgeweaveFluids.FLUID_TYPES.register(modEventBus);
        ForgeweaveFluids.FLUIDS.register(modEventBus);
        ForgeweaveFluids.BLOCKS.register(modEventBus);
        ForgeweaveItems.ITEMS.register(modEventBus);
        ForgeweaveMenus.MENUS.register(modEventBus);
        ForgeweaveCreativeTab.TABS.register(modEventBus);
        ForgeweaveRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        // #110 -- the M2 advancement chain's custom criteria (docs/SCOPE.md M2 issue #110).
        ForgeweaveCriteriaTriggers.TRIGGERS.register(modEventBus);
        // SERVER type: see ForgeweaveConfig javadoc for why this must sync client<->server.
        modContainer.registerConfig(ModConfig.Type.SERVER, ForgeweaveConfig.SPEC);
        modEventBus.addListener(this::registerDataPackRegistries);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        // The Pattern Chest/Part Chest (issue #66) expose their inventory as an IItemHandler so
        // adjacent stations' side-inventory panels pick them up (SideInventory#find).
        modEventBus.addListener(ChestBlockEntity::registerCapabilities);
        // The smeltery's seared tanks hold fluids directly; a drain re-exposes its core's tank (#95).
        modEventBus.addListener(SearedTankBlockEntity::registerCapabilities);
        modEventBus.addListener(SearedDrainBlockEntity::registerCapabilities);
        // #100 -- a faucet pours into a casting table/basin through its fluid handler.
        modEventBus.addListener(CastingBlockEntity::registerCapabilities);
        modEventBus.addListener(ForgeweaveDataGenerators::gatherData);
        NeoForge.EVENT_BUS.addListener(this::onServerStarted);
        // #150 -- the shared per-hit pipeline (ADR-0005 decision 3): pre-hit, on-hit and post-kill
        // for every blow struck with a Forgeweave tool, and the only place combat innates and combat
        // modifiers attach. See CombatSeams for which NeoForge event drives which hook.
        NeoForge.EVENT_BUS.addListener(CombatSeams::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(CombatSeams::onDamageDealt);
        NeoForge.EVENT_BUS.addListener(CombatSeams::onDeath);
        // Providers register here, not in their own static initializers, so the order the pipeline
        // runs them in is visible in one place. Materials' traits are first (see COMBAT_SEAM); M3's
        // per-tool innates and combat modifiers add theirs below as they land.
        CombatSeams.register((weapon, out) -> out.accept(ForgeweaveTraits.COMBAT_SEAM));
        // established's kill-XP bonus (issue #102): no Item hook for a kill's dropped XP either.
        NeoForge.EVENT_BUS.addListener(ForgeweaveTraits::onExperienceDrop);
        // #103 -- netherite's fireproof: a dropped ItemEntity's fire immunity has no per-stack Item
        // hook (Item.Properties#fireResistant is per-Item), so this listens on the generic
        // invulnerability check every entity goes through instead.
        NeoForge.EVENT_BUS.addListener(ForgeweaveTraits::onEntityInvulnerabilityCheck);
        // #108 batch: Searing/Magnetic Pull/Resonant key off what a mined block drops, which has no
        // Item hook either (see ForgeweaveModifiers#onBlockDrops).
        NeoForge.EVENT_BUS.addListener(ForgeweaveModifiers::onBlockDrops);
        // #107 batch: modifiers whose behavior is event-driven rather than a per-level hook: mending
        // moss's XP banking and acquisition, and soulbound's death/respawn pair. See
        // ForgeweaveModifiers's class javadoc on MENDING_MOSS and SOULBOUND for why these live here
        // instead of on Modifier.
        NeoForge.EVENT_BUS.addListener(ForgeweaveModifiers::onXpPickup);
        NeoForge.EVENT_BUS.addListener(ForgeweaveModifiers::onRightClickBookshelf);
        NeoForge.EVENT_BUS.addListener(ForgeweaveModifiers::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(ForgeweaveModifiers::onPlayerClone);
        // #110 -- Ponder is a soft dependency (docs/SCOPE.md M2): only touch its API, and only
        // register the client-setup listener, when it's actually on the mod list. See
        // ForgeweavePonderPlugin's javadoc for why this isn't an @EventBusSubscriber instead.
        if (ModList.get().isLoaded("ponder")) {
            modEventBus.addListener(ForgeweavePonderPlugin::registerScenes);
        }
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
        // #100 -- casting recipes, same deal: the client needs them for JEI (#109) and for showing
        // what a casting block is currently making.
        event.dataPackRegistry(CastingRecipe.REGISTRY, CastingRecipe.CODEC, CastingRecipe.CODEC);
        // #96 -- melting recipes, same idiom again: the client needs them for JEI's melting category
        // and the smeltery screen's "what is this turning into" readout.
        event.dataPackRegistry(MeltingRecipe.REGISTRY, MeltingRecipe.CODEC, MeltingRecipe.CODEC);
        // #97 -- smeltery fuels, same idiom again: the client needs them for the smeltery screen's
        // fuel gauge (#101).
        event.dataPackRegistry(SmelteryFuel.REGISTRY, SmelteryFuel.CODEC, SmelteryFuel.CODEC);
        // #98 -- alloy recipes, same idiom again: the client needs them for JEI's alloy category
        // (#109) and to explain what a smeltery is about to combine.
        event.dataPackRegistry(AlloyRecipe.REGISTRY, AlloyRecipe.CODEC, AlloyRecipe.CODEC);
    }

    /** The Tool Station's rename field is the mod's only message that a menu button can't carry. */
    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        RenameStationItemPayload.register(event.registrar("1"));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Forgeweave common setup complete");
    }
}
