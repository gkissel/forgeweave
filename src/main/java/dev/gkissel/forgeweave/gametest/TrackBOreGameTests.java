package dev.gkissel.forgeweave.gametest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.OreConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

import net.neoforged.neoforge.common.world.BiomeModifier;
import net.neoforged.neoforge.common.world.BiomeModifiers;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trackb.TrackBOre;
import dev.gkissel.forgeweave.worldgen.TrackBOrePlacement;

/**
 * Track B's ore family (issue #839, epic #824): every ore's own harvest-tier gate and drop, plus the
 * config-aware group toggle and worldgen JSON's own wiring -- the same split {@link NetherOreGameTests}
 * already draws between "provable without a real chunk" and "needs an actual generated world, left to
 * the manual release checklist" (this class's javadoc there applies here too: only "does a chunk
 * actually contain the ore" is out of reach).
 *
 * <p>Issue #877 (the JC10 reversal) re-rung six of these ores onto the three new tiers above netherite
 * (see {@link TrackBOre.Tier}'s own javadoc), so {@link #everyTrackBOreHasTheRightTierGateAndDrop}
 * now also proves the new rungs gate for real: a netherite pickaxe -- previously correct for every
 * netherite-and-up Track B ore -- must now refuse the six ores that moved up, and a synthetic pick at
 * the ore's own new rung ({@link #syntheticPick}, since no vanilla item sits above netherite) must
 * still succeed.
 *
 * <p>Issue #883 moved voidglass to the End (the game's uniquely rarest ore), off the Overworld biome
 * modifier and onto its own {@code track_b_end_ores.json}; issue #909 then re-homed seven more ores
 * across all three dimensions. {@link #everyTrackBOreGeneratesOnlyInItsHostDimension} covers that
 * wiring for the whole roster the same "hand-written JSON resolves against the real registry" way the
 * placed-feature tests above do, since the biome modifier and configured feature registries are both
 * reachable without a real chunk too.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TrackBOreGameTests {

    /**
     * #839's own "grouped, not per-ore" toggle: every Track B placed feature must route its vein
     * count through {@link TrackBOrePlacement}, and switching {@link ForgeweaveConfig#GEN_TRACK_B_ORES}
     * off must zero every ore's count in the same tick -- the group-toggle test the issue's own "Test
     * strategy" section asks for ("an ore group with its toggle off generates nothing").
     */
    @GameTest(template = "empty")
    public static void trackBOreGroupToggleGatesEveryOre(GameTestHelper helper) {
        TrackBOrePlacement onePerFeature = new TrackBOrePlacement(5);
        helper.assertValueEqual((int) onePerFeature.getPositions(null, null, BlockPos.ZERO).count(),
                5, "positions while genTrackBOres is on");

        ForgeweaveConfig.GEN_TRACK_B_ORES.set(false);
        try {
            helper.assertValueEqual((int) onePerFeature.getPositions(null, null, BlockPos.ZERO).count(),
                    0, "positions while genTrackBOres is off");
        } finally {
            ForgeweaveConfig.GEN_TRACK_B_ORES.set(true);
        }

        helper.succeed();
    }

    /**
     * Every Track B placed feature JSON parses and references a registered block, and routes its
     * count through {@link TrackBOrePlacement} rather than a fixed {@code minecraft:count} -- the
     * same "the hand-written JSON still resolves against the registered modifier type" check
     * {@link NetherOreGameTests#assertPlacedThroughTheConfigModifier} makes for cobalt/ardite.
     */
    @GameTest(template = "empty")
    public static void everyTrackBOrePlacedFeatureRoutesThroughTheGroupModifier(GameTestHelper helper) {
        for (TrackBOre ore : TrackBOre.ALL) {
            PlacedFeature feature = getPlacedFeature(helper, ore.oreBlockId());
            helper.assertTrue(feature.placement().stream().anyMatch(TrackBOrePlacement.class::isInstance),
                    ore.oreBlockId() + " must take its vein count from TrackBOrePlacement");
        }
        helper.succeed();
    }

    private static PlacedFeature getPlacedFeature(GameTestHelper helper, String name) {
        ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name));
        PlacedFeature feature = helper.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).get(key);
        helper.assertTrue(feature != null, "expected a placed feature registered as " + key.location());
        return feature;
    }

    /** The biome modifier that carries a host's ores -- one per dimension (#909). */
    private static String modifierFor(TrackBOre.Host host) {
        return switch (host) {
            case OVERWORLD_STONE, OVERWORLD_DEEPSLATE -> "track_b_overworld_ores";
            case NETHER -> "track_b_nether_ores";
            case END -> "track_b_end_ores";
        };
    }

    /**
     * #909: every ore generates in the dimension {@link TrackBOre#host} names and in no other. For
     * each of the eleven this checks both halves of "which dimension", since they are separate files
     * that can drift apart: the placed feature is carried by exactly one of the three biome modifiers
     * -- the one for its host, and provably <em>not</em> by the other two -- and its configured
     * feature replaces that host's own rock (netherrack for the three Nether ores, end stone for the
     * four End ones, deepslate/stone for the four Overworld ones) with that ore's own block. A wrong
     * host block, a stale modifier entry, or an ore left behind on the modifier it used to ride all
     * fail here.
     *
     * <p>Also keeps #883's End-biome precision: {@code track_b_end_ores} is wired to exactly the
     * End's four outer-island biomes (end_highlands, end_midlands, end_barrens, small_end_islands),
     * never {@code minecraft:the_end}, the small central island the dragon fight uses, which the
     * blanket {@code #minecraft:is_end} tag would have pulled in.
     */
    @GameTest(template = "empty")
    public static void everyTrackBOreGeneratesOnlyInItsHostDimension(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var biomeModifiers = level.registryAccess().registryOrThrow(NeoForgeRegistries.Keys.BIOME_MODIFIERS);
        var biomes = level.registryAccess().registryOrThrow(Registries.BIOME);

        Map<String, BiomeModifiers.AddFeaturesBiomeModifier> modifiers = new LinkedHashMap<>();
        for (String name : List.of("track_b_overworld_ores", "track_b_nether_ores", "track_b_end_ores")) {
            BiomeModifiers.AddFeaturesBiomeModifier modifier = getAddFeaturesBiomeModifier(helper, biomeModifiers, name);
            helper.assertTrue(modifier.step() == GenerationStep.Decoration.UNDERGROUND_ORES,
                    "expected " + name + " to run in the underground_ores step");
            modifiers.put(name, modifier);
        }

        for (TrackBOre ore : TrackBOre.ALL) {
            PlacedFeature placed = getPlacedFeature(helper, ore.oreBlockId());
            String expected = modifierFor(ore.host());
            for (var entry : modifiers.entrySet()) {
                boolean carried = entry.getValue().features().stream().anyMatch(holder -> holder.value() == placed);
                helper.assertTrue(carried == entry.getKey().equals(expected),
                        ore.oreBlockId() + " belongs to " + expected + " only, but " + entry.getKey()
                                + (carried ? " also carries it" : " does not carry it"));
            }
            assertReplacesItsHostRock(helper, ore);
        }

        BiomeModifiers.AddFeaturesBiomeModifier endModifier = modifiers.get("track_b_end_ores");
        for (String id : List.of("end_highlands", "end_midlands", "end_barrens", "small_end_islands")) {
            Holder<Biome> biome = biomes.getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace(id)));
            helper.assertTrue(endModifier.biomes().contains(biome), "expected track_b_end_ores to include " + id);
        }
        Holder<Biome> theEnd = biomes.getHolderOrThrow(ResourceKey.create(Registries.BIOME, ResourceLocation.withDefaultNamespace("the_end")));
        helper.assertFalse(endModifier.biomes().contains(theEnd),
                "track_b_end_ores must not include the central the_end island");

        helper.succeed();
    }

    /**
     * An ore's configured feature swaps its {@link TrackBOre.Host}'s own rock for that ore's block --
     * the other half of "which dimension does this generate in" (a Nether-registered ore whose
     * feature still targets deepslate would generate nowhere at all).
     */
    private static void assertReplacesItsHostRock(GameTestHelper helper, TrackBOre ore) {
        ResourceKey<ConfiguredFeature<?, ?>> key = ResourceKey.create(Registries.CONFIGURED_FEATURE,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, ore.oreBlockId()));
        ConfiguredFeature<?, ?> configured =
                helper.getLevel().registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE).get(key);
        helper.assertTrue(configured != null, "expected a configured feature registered as " + key.location());
        helper.assertTrue(configured.config() instanceof OreConfiguration,
                ore.oreBlockId() + " must be a minecraft:ore feature");

        List<OreConfiguration.TargetBlockState> targets = ((OreConfiguration) configured.config()).targetStates;
        helper.assertTrue(targets.size() == 1, ore.oreBlockId() + " must have exactly one ore target");
        OreConfiguration.TargetBlockState target = targets.get(0);

        Block host = BuiltInRegistries.BLOCK.get(ResourceLocation.parse(ore.host().targetBlock));
        helper.assertTrue(target.target.test(host.defaultBlockState(), helper.getLevel().getRandom()),
                ore.oreBlockId() + " must replace " + ore.host().targetBlock + ", its " + ore.host() + " host rock");
        helper.assertTrue(target.state.is(ForgeweaveBlocks.trackBOre(ore.id()).get()),
                ore.oreBlockId() + " must place its own ore block");
    }

    private static BiomeModifiers.AddFeaturesBiomeModifier getAddFeaturesBiomeModifier(
            GameTestHelper helper, Registry<BiomeModifier> registry, String name) {
        ResourceKey<BiomeModifier> key = ResourceKey.create(NeoForgeRegistries.Keys.BIOME_MODIFIERS,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name));
        BiomeModifier modifier = registry.get(key);
        helper.assertTrue(modifier != null, "expected a biome modifier registered as " + key.location());
        helper.assertTrue(modifier instanceof BiomeModifiers.AddFeaturesBiomeModifier,
                key.location() + " must be an AddFeaturesBiomeModifier");
        return (BiomeModifiers.AddFeaturesBiomeModifier) modifier;
    }

    /**
     * Every ore's harvest-tier gate matches its {@link TrackBOre.Tier}: the four remaining
     * netherite-tier ores take cobalt/ardite's exact needs_diamond_tool + incorrect_for_diamond_tool
     * combo (netherite pickaxe only, diamond refused); the six ores #877 re-rung above netherite each
     * refuse the rung directly below them (a netherite pickaxe for hardcinder, a hardcinder-tier
     * synthetic pick for warspar, a warspar-tier synthetic pick for resonite) and accept only a
     * synthetic pick at their own rung; the one diamond-tier ore (fulmenite) accepts an iron pickaxe.
     * The roster's former stone-tier ore (cinderstone) was retired by issue #884 (1) -- basalt
     * replaces it as a Part-Builder-only vanilla-item material, no ore of its own, so {@link
     * TrackBOre.Tier#STONE} has no member left to test here. Each ore also drops exactly one raw
     * item, same unconditional self-drop as cobalt/ardite.
     */
    @GameTest(template = "empty")
    public static void everyTrackBOreHasTheRightTierGateAndDrop(GameTestHelper helper) {
        for (TrackBOre ore : TrackBOre.ALL) {
            Block oreBlock = ForgeweaveBlocks.trackBOre(ore.id()).get();
            Item rawItem = ForgeweaveItems.trackBRawItem(ore.id()).get();
            switch (ore.tier()) {
                case RESONITE -> assertGate(helper, oreBlock, rawItem,
                        syntheticPick(TrackBOre.INCORRECT_FOR_WARSPAR_TOOL),
                        syntheticPick(TrackBOre.INCORRECT_FOR_RESONITE_TOOL));
                case WARSPAR -> assertGate(helper, oreBlock, rawItem,
                        syntheticPick(TrackBOre.INCORRECT_FOR_HARDCINDER_TOOL),
                        syntheticPick(TrackBOre.INCORRECT_FOR_WARSPAR_TOOL));
                case HARDCINDER -> assertGate(helper, oreBlock, rawItem,
                        new ItemStack(Items.NETHERITE_PICKAXE),
                        syntheticPick(TrackBOre.INCORRECT_FOR_HARDCINDER_TOOL));
                case NETHERITE -> assertGate(helper, oreBlock, rawItem,
                        new ItemStack(Items.DIAMOND_PICKAXE), new ItemStack(Items.NETHERITE_PICKAXE));
                case DIAMOND -> assertAccepts(helper, oreBlock, rawItem, new ItemStack(Items.IRON_PICKAXE));
                case STONE -> assertAccepts(helper, oreBlock, rawItem, new ItemStack(Items.WOODEN_PICKAXE));
            }
        }
        helper.succeed();
    }

    /**
     * A pickaxe with a real vanilla {@code tool} component whose only deny-drops rule is
     * {@code incorrectForTool}, built the same way {@code ToolItem#toolComponent} builds a real
     * assembled tool's -- so it stands in for "a tool at this rung" for the three tiers #877 mints
     * above netherite, where no vanilla item exists to test against.
     */
    private static ItemStack syntheticPick(TagKey<Block> incorrectForTool) {
        ToolItem toolItem = ForgeweaveItems.TOOL_PICKAXE.get();
        ToolStats.Stats stats = new ToolStats.Stats(250, 1.0F, 1.0F);
        Material head = new Material(new Material.Head(250, 1.0F, 1.0F), new Material.Handle(1.0F, 0), 0,
                incorrectForTool, new Material.Traits(List.of(), List.of()), List.of(), Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));

        ItemStack stack = new ItemStack(toolItem);
        stack.set(DataComponents.TOOL, toolItem.toolComponent(head, stats));
        stack.set(DataComponents.MAX_DAMAGE, 250);
        stack.set(DataComponents.DAMAGE, 0);
        return stack;
    }

    /** Mines {@code oreBlock} with {@code correct} and asserts it works and yields exactly one {@code rawItem}. */
    private static void assertAccepts(GameTestHelper helper, Block oreBlock, Item rawItem, ItemStack correct) {
        assertGate(helper, oreBlock, rawItem, null, correct);
    }

    /**
     * Asserts {@code below} (if given) refuses {@code oreBlock}'s drops, then mines it with
     * {@code correct} and asserts that tool is accepted and yields exactly one {@code rawItem}.
     */
    private static void assertGate(GameTestHelper helper, Block oreBlock, Item rawItem, ItemStack below, ItemStack correct) {
        BlockState state = oreBlock.defaultBlockState();

        if (below != null) {
            helper.assertFalse(below.isCorrectToolForDrops(state), oreBlock + " must refuse " + below.getItem());
        }
        helper.assertTrue(correct.isCorrectToolForDrops(state), oreBlock + " must accept " + correct.getItem());

        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ServerLevel level = helper.getLevel();
        BlockPos absolute = helper.absolutePos(pos);
        helper.setBlock(pos, state);
        List<ItemStack> drops = Block.getDrops(state, level, absolute, level.getBlockEntity(absolute), player, correct);
        helper.setBlock(pos, Blocks.AIR);

        helper.assertTrue(drops.size() == 1, "expected exactly one item stack of drops for " + oreBlock + ", got " + drops.size());
        ItemStack drop = drops.get(0);
        helper.assertTrue(drop.is(rawItem), "expected " + rawItem + " from " + oreBlock + ", got " + drop);
        helper.assertTrue(drop.getCount() == 1, "expected a single raw item, no fortune bonus, got " + drop.getCount());
    }
}
