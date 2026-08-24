package dev.gkissel.forgeweave;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
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
import dev.gkissel.forgeweave.block.SearedChannelBlockEntity;
import dev.gkissel.forgeweave.block.SearedChuteBlockEntity;
import dev.gkissel.forgeweave.block.SearedDrainBlockEntity;
import dev.gkissel.forgeweave.block.SearedDuctBlockEntity;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.client.ForgeweaveDarkModeCompat;
import dev.gkissel.forgeweave.combat.AttackSlash;
import dev.gkissel.forgeweave.combat.Beheading;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.combat.ForgeweaveMobEffects;
import dev.gkissel.forgeweave.config.ForgeweaveClientConfig;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.data.ForgeweaveDataGenerators;
import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveCreativeTab;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.GuideBookGift;
import dev.gkissel.forgeweave.item.SavedBookPagePayload;
import dev.gkissel.forgeweave.item.SlimeBootsItem;
import dev.gkissel.forgeweave.item.SlimeBounceHandler;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ForgeweaveMenus;
import dev.gkissel.forgeweave.menu.RenameStationItemPayload;
import dev.gkissel.forgeweave.modifier.EmbossingRecipe;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.particle.ForgeweaveParticles; // #482
import dev.gkissel.forgeweave.ponder.ForgeweavePonderPlugin;
import dev.gkissel.forgeweave.recipe.AlloyRecipe; // #98
import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe; // #270
import dev.gkissel.forgeweave.recipe.ForgeweaveRecipeSerializers;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;
import dev.gkissel.forgeweave.sound.ForgeweaveSounds; // #453
import dev.gkissel.forgeweave.tool.AoeHarvest;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.worldgen.MagmaSlimeIslandPiece; // #450
import dev.gkissel.forgeweave.worldgen.MagmaSlimeIslandStructure; // #450
import dev.gkissel.forgeweave.worldgen.NetherOrePlacement; // #276
import dev.gkissel.forgeweave.worldgen.SlimeIslandPiece;
import dev.gkissel.forgeweave.worldgen.SlimeIslandStructure;

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
        ForgeweaveFluids.BUCKETS.register(modEventBus); // #286
        ForgeweaveItems.ITEMS.register(modEventBus);
        // #452 -- the slime boots' armour material (parity audit T21). See SlimeBootsItem.
        SlimeBootsItem.ARMOR_MATERIALS.register(modEventBus);
        // #447 -- the entity every dropped tool spawns as (parity audit T16). See
        // entity.IndestructibleItemEntity for why it is a registered type rather than a flag.
        ForgeweaveEntities.ENTITY_TYPES.register(modEventBus);
        ForgeweaveMenus.MENUS.register(modEventBus);
        ForgeweaveCreativeTab.TABS.register(modEventBus);
        // #276 -- the config-aware vein count the Nether ores' placed features use.
        NetherOrePlacement.PLACEMENT_MODIFIERS.register(modEventBus);
        // #449/#629 (parity audit T18) -- the slime island, a structure so /locate can find it.
        // See SlimeIslandStructure and SlimeIslandPiece.
        SlimeIslandStructure.STRUCTURE_TYPES.register(modEventBus);
        SlimeIslandPiece.STRUCTURE_PIECES.register(modEventBus);
        // #450 (parity audit T19) -- the Nether magma island, the same pair one dimension over.
        MagmaSlimeIslandStructure.STRUCTURE_TYPES.register(modEventBus);
        MagmaSlimeIslandPiece.STRUCTURE_PIECES.register(modEventBus);
        // #159 -- the scimitar's lacerate bleed (see LacerateEffect for why it is a status effect).
        ForgeweaveMobEffects.MOB_EFFECTS.register(modEventBus);
        // #482 -- upstream's heart-effect particles (parity audit T51): the little coloured heart a
        // landed secondary hit puts over its target. See ForgeweaveParticles.
        ForgeweaveParticles.PARTICLE_TYPES.register(modEventBus);
        // T22 (#453) -- the Slimesling's launch sound, upstream's own slimesling.ogg.
        ForgeweaveSounds.SOUND_EVENTS.register(modEventBus);
        ForgeweaveRecipeSerializers.RECIPE_SERIALIZERS.register(modEventBus);
        // #110 -- the M2 advancement chain's custom criteria (docs/SCOPE.md M2 issue #110).
        ForgeweaveCriteriaTriggers.TRIGGERS.register(modEventBus);
        // SERVER type: see ForgeweaveConfig javadoc for why this must sync client<->server.
        modContainer.registerConfig(ModConfig.Type.SERVER, ForgeweaveConfig.SPEC);
        // CLIENT type: display preferences only, never read server-side -- see the class javadoc.
        modContainer.registerConfig(ModConfig.Type.CLIENT, ForgeweaveClientConfig.SPEC);
        modEventBus.addListener(this::registerDataPackRegistries);
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);
        // The Pattern Chest/Part Chest (issue #66) expose their inventory as an IItemHandler so
        // adjacent stations' side-inventory panels pick them up (SideInventory#find).
        modEventBus.addListener(ChestBlockEntity::registerCapabilities);
        // The smeltery's seared tanks hold fluids directly; a drain re-exposes its core's tank (#95).
        modEventBus.addListener(SearedTankBlockEntity::registerCapabilities);
        modEventBus.addListener(SearedDrainBlockEntity::registerCapabilities);
        // #277 -- a duct re-exposes that tank filtered to one fluid (plus its own filter slot), and a
        // chute re-exposes the core's melting inventory.
        modEventBus.addListener(SearedDuctBlockEntity::registerCapabilities);
        modEventBus.addListener(SearedChuteBlockEntity::registerCapabilities);
        // #441 (parity audit T9) -- a channel takes fluid on its top and on any side set to `in`.
        modEventBus.addListener(SearedChannelBlockEntity::registerCapabilities);
        // #470 -- the core itself also exposes its melting inventory directly, so a hopper feeding it
        // (no chute required) works the way upstream's Mantle-derived TileInventory always did.
        modEventBus.addListener(SmelteryControllerBlockEntity::registerCapabilities);
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
        // #465/T34 -- upstream's per-tool knockback() multiplier, riding NeoForge's own knockback
        // event rather than a custom pipeline (ADR-0005); see CombatSeam#knockback.
        NeoForge.EVENT_BUS.addListener(CombatSeams::onKnockback);
        // Providers register here, not in their own static initializers, so the order the pipeline
        // runs them in is visible in one place. Materials' traits are first (COMBAT_SEAM's damage
        // fold plus, since #229, each trait's own seams -- see collectCombatSeams); M3's per-tool
        // innates and combat modifiers add theirs below as they land.
        CombatSeams.register(ForgeweaveTraits::collectCombatSeams);
        // #162/#163 -- combat modifiers batches 1 and 2 (smite, bane of arthropods, fiery, necrotic;
        // knockback, shulking, webbed): one shared provider walking the modifier list and consuming
        // each entry's Modifier#combatSeam, the modifier-side counterpart to ForgeweaveTraits#COMBAT_SEAM
        // just above.
        CombatSeams.register(ForgeweaveModifiers.COMBAT_SEAMS);
        // #164/#155 -- per-tool innates: M1's retrofit (pickaxe pierce, shovel flatten, hatchet
        // sunder) plus every M3 tool's own, one provider for both (ForgeweaveInnates). After traits,
        // so a trait that scales a blow scales the blow the tool was always going to land rather than
        // the innate's bonus on top of it.
        CombatSeams.register(ForgeweaveInnates::collect);
        // #584 -- the arc a fully-charged swing draws in front of the player, for the seven weapons
        // upstream spawns one from. Cosmetic only, and deliberately not an innate: see AttackSlash.
        CombatSeams.register(AttackSlash::collect);
        // #159 -- the charge a swing was made with, captured before Player#attack zeroes it; the
        // battleaxe's full-charge-only sweep and every later charged innate read it off CombatHit.
        NeoForge.EVENT_BUS.addListener(CombatSeams::onPlayerAttack);
        // #422 -- the crit multiplier of that same swing, captured for the same reason: vanilla has
        // already multiplied the amount the seams see by it, and flat bonuses must sit inside it.
        NeoForge.EVENT_BUS.addListener(CombatSeams::onCriticalHit);
        // #158 -- beheading: a provider of its own rather than a Modifier#combatSeam, because the level
        // it rolls on is the cleaver's innate plus the applied modifier summed into one roll, and a
        // per-entry seam sees neither the innate nor an unmodified cleaver. See Beheading.
        CombatSeams.register(Beheading::collect);
        // #157 -- area mining (hammer/excavator 3x3, lumber axe tree fell, scythe 3x3x3, vein hammer
        // vein). NeoForge 1.21 dropped the per-item onBlockStartBreak hook upstream 1.12 uses, so
        // this is the one break event every player break goes through -- see AoeHarvest.
        NeoForge.EVENT_BUS.addListener(AoeHarvest::onBlockBreak);
        // #299 -- the lumber axe's tree fell spreads over ticks rather than felling the whole trunk
        // synchronously; this drains each in-flight chop's budget every level tick. See AoeHarvest.
        NeoForge.EVENT_BUS.addListener(AoeHarvest::onLevelTick);
        // established's kill-XP bonus (issue #102): no Item hook for a kill's dropped XP either.
        NeoForge.EVENT_BUS.addListener(ForgeweaveTraits::onExperienceDrop);
        // established's block-break XP bonus (issue #494/T63), riding the same BlockDropsEvent seam
        // ForgeweaveModifiers#onBlockDrops below uses.
        NeoForge.EVENT_BUS.addListener(ForgeweaveTraits::onBlockBreakExperience);
        // #228 -- aquadynamic/aridiculous/crumbling/unnatural adjust break speed off the player and
        // the block, which Item#getDestroySpeed never sees; upstream 1.12 handles this same
        // PlayerEvent.BreakSpeed per trait (see Trait#breakSpeed).
        NeoForge.EVENT_BUS.addListener(ForgeweaveTraits::onBreakSpeed);
        // T24 -- blasting's speed rule needs the block's hardness, so it rides the same event
        // (upstream ModBlasting#miningSpeed). After the traits: its formula blends with the event's
        // untouched original speed either way, so the order costs nothing.
        NeoForge.EVENT_BUS.addListener(ForgeweaveModifiers::onBreakSpeed);
        // #229 -- enderference's teleport block: the combat half rides the seams; these listeners
        // only read the mark the seam left.
        // NeoForge splits 1.12's one EnderTeleportEvent into per-cause subevents.
        NeoForge.EVENT_BUS.addListener(ForgeweaveTraits::onEnderTeleport);
        NeoForge.EVENT_BUS.addListener(ForgeweaveTraits::onChorusFruitTeleport);
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
        // #445 -- parity audit T13: a first-time player is given the guide book (upstream's
        // spawnWithBook, default on). See GuideBookGift.
        NeoForge.EVENT_BUS.addListener(GuideBookGift::onPlayerLoggedIn);
        // #452 -- parity audit T21: the slime boots' bounce and fall-damage cancel, upstream's
        // ItemSlimeBoots#onFall. See SlimeBootsItem for the side split.
        NeoForge.EVENT_BUS.addListener(SlimeBootsItem::onFall);
        // #452/#453 -- parity audit T21 and T22: the boots' rebound and the momentum a Slimesling-flung
        // player keeps through the arc, both upstream's shared SlimeBounceHandler. See that class.
        NeoForge.EVENT_BUS.addListener(SlimeBounceHandler::onPlayerTickPost);
        // #110/#664 -- Ponder is jar-in-jar embedded now (issue #664), but its API is still only
        // touched behind this ModList guard, so a repackaged install that strips the embedded jars
        // degrades to the ForgeweavePonderHint fallback instead of crashing. See
        // ForgeweavePonderPlugin's javadoc for why this isn't an @EventBusSubscriber instead.
        if (ModList.get().isLoaded("ponder")) {
            modEventBus.addListener(ForgeweavePonderPlugin::onClientSetup);
        }
        // #335 -- DarkModeEverywhere compat is a soft dependency too, same idiom as ponder just above.
        // Unlike FMLClientSetupEvent (inherently client-only), InterModEnqueueEvent is a mod-bus
        // lifecycle event that fires on every dist, so this checks FMLEnvironment.dist itself to keep
        // dist handling consistent with the Dist.CLIENT gating every other client wiring class uses
        // (see ForgeweaveDarkModeCompat's javadoc).
        if (ModList.get().isLoaded("darkmodeeverywhere") && FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(ForgeweaveDarkModeCompat::sendShaderBlacklist);
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
        // #270 -- what an entity standing in a smeltery melts into, same idiom again.
        event.dataPackRegistry(EntityMeltingRecipe.REGISTRY, EntityMeltingRecipe.CODEC, EntityMeltingRecipe.CODEC);
        // #98 -- alloy recipes, same idiom again: the client needs them for JEI's alloy category
        // (#109) and to explain what a smeltery is about to combine.
        event.dataPackRegistry(AlloyRecipe.REGISTRY, AlloyRecipe.CODEC, AlloyRecipe.CODEC);
        // #154 -- what an embossment costs, same idiom again: the client needs it so the Tool
        // Station screen can say "already embossed" without a payload of its own.
        event.dataPackRegistry(EmbossingRecipe.REGISTRY, EmbossingRecipe.CODEC, EmbossingRecipe.CODEC);
    }

    /** The Tool Station's rename field and the guide book's bookmark ride custom payloads. */
    private void registerPayloads(final RegisterPayloadHandlersEvent event) {
        RenameStationItemPayload.register(event.registrar("1"));
        SavedBookPagePayload.register(event.registrar("1"));
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        LOGGER.info("Forgeweave common setup complete");
    }
}
