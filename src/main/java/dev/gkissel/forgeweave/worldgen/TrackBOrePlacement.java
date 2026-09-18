package dev.gkissel.forgeweave.worldgen;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * The config-aware vein count for Track B's ore family (issue #839, epic #824). Same seam as
 * {@link NetherOrePlacement} (issue #276) -- Forgeweave's ore generation is pure datapack JSON, which
 * has no place a config toggle can reach on its own -- but grouped rather than per-ore: #839's
 * deliverable 3 asks for "one toggle per ore group, not one per ore", so unlike
 * {@code NetherOrePlacement.Ore} (one enum constant, one config pair, per ore) this modifier reads a
 * single {@link ForgeweaveConfig#GEN_TRACK_B_ORES} switch shared by all twelve Track B ores, and each
 * placed feature supplies its own vein count in its own JSON (see {@link dev.gkissel.forgeweave.trackb.TrackBOre}'s
 * per-material rate) rather than a config-editable rate. Disabling the group is exactly the
 * already-supported {@code count = 0} case, same as {@code NetherOrePlacement}.
 *
 * <p>No NOTICE.md row -- a novel placement modifier type, not ported code (same reasoning as
 * {@code NetherOrePlacement}'s own javadoc).
 */
public class TrackBOrePlacement extends PlacementModifier {
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Forgeweave.MODID);

    public static final MapCodec<TrackBOrePlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(com.mojang.serialization.Codec.INT.fieldOf("count").forGetter(TrackBOrePlacement::count))
            .apply(instance, TrackBOrePlacement::new));

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<TrackBOrePlacement>> TYPE =
            PLACEMENT_MODIFIERS.register("track_b_ore_rate", () -> () -> CODEC);

    /**
     * Allthemodium's own mining dimension (issue #998, D-M8-19), reused unqualified rather than
     * through a compile dependency -- see the PR body for why this integration never needs one.
     */
    public static final ResourceKey<Level> ALLTHEMODIUM_MINING =
            ResourceKey.create(Registries.DIMENSION, ResourceLocation.fromNamespaceAndPath("allthemodium", "mining"));

    private final int count;

    public TrackBOrePlacement(int count) {
        this.count = count;
    }

    public int count() {
        return count;
    }

    /**
     * Vanilla {@code CountPlacement}'s own contract, gated to zero while the Track B ore group is
     * switched off, and -- issue #998 (D-M8-19) -- gated a second way inside Allthemodium's own
     * mining dimension, where these same placed features also generate (the biome-modifier tree
     * {@code generate_track_b_worldgen.py} emits there): {@code allthemodiumTiers} off stops that
     * generation specifically, without touching the ore's generation anywhere else. This is the one
     * runtime hook the tier-equivalence toggle has -- the tag equivalence itself has none, since a
     * live config value has no site in a static tag file (see the PR body).
     *
     * <p>{@code context} is null-tolerant ({@code null} reads as "not the mining dimension"):
     * {@code TrackBOreGameTests#trackBOreGroupToggleGatesEveryOre} already calls this with a
     * {@code null} context to exercise {@code genTrackBOres} alone, predating this method needing a
     * real level at all.
     */
    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        ResourceKey<Level> dimension = context == null ? null : context.getLevel().getLevel().dimension();
        int effective = allowed(dimension) ? count : 0;
        return IntStream.range(0, effective).mapToObj(i -> pos);
    }

    /**
     * The decision {@link #getPositions} makes, pulled out as a pure function of the dimension so it
     * is directly unit- and GameTestable with no {@code WorldGenLevel} to construct
     * ({@code AllthemodiumElementariumGameTests}). {@code dimension} is {@code null}-tolerant, read
     * as "not the mining dimension" -- see {@link #getPositions}'s own javadoc for why that matters.
     */
    public static boolean allowed(@Nullable ResourceKey<Level> dimension) {
        boolean trackBOn = ForgeweaveConfig.read(ForgeweaveConfig.GEN_TRACK_B_ORES);
        boolean inAllthemodiumMining = ALLTHEMODIUM_MINING.equals(dimension);
        boolean allowedHere = !inAllthemodiumMining || ForgeweaveConfig.enabled(ForgeweaveConfig.ALLTHEMODIUM_TIERS);
        return trackBOn && allowedHere;
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE.get();
    }
}
