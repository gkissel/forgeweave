package dev.gkissel.forgeweave.worldgen;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
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

    private final int count;

    public TrackBOrePlacement(int count) {
        this.count = count;
    }

    public int count() {
        return count;
    }

    /** Vanilla {@code CountPlacement}'s own contract, gated to zero while the Track B ore group is switched off. */
    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int effective = ForgeweaveConfig.GEN_TRACK_B_ORES.get() ? count : 0;
        return IntStream.range(0, effective).mapToObj(i -> pos);
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE.get();
    }
}
