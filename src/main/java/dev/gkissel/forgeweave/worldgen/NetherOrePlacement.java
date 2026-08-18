package dev.gkissel.forgeweave.worldgen;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * The config-aware vein count for the Nether ores (docs/SCOPE.md M3.4-7 issue #276), standing in for
 * the {@code minecraft:count} modifier the two placed features used to carry. Upstream 1.12 reads
 * {@code genCobalt}/{@code cobaltRate}/{@code genArdite}/{@code arditeRate} straight out of its
 * {@code NetherOreGenerator} loop; Forgeweave's ore generation is pure datapack JSON (configured
 * feature + placed feature + {@code neoforge:add_features} biome modifier), which has no seam a
 * config can reach, so this is that seam -- one placement modifier that answers "how many veins",
 * with zero meaning "not at all".
 *
 * <p>Folding the on/off switch into the count (rather than adding a second, filtering modifier)
 * keeps it to one registered type for both options and both ores, and makes the disabled case
 * exactly the already-supported {@code count = 0}. Placement runs per chunk at generation time, so
 * the config is read at the moment it applies: changing it affects newly generated chunks only,
 * the same as upstream.
 *
 * <p><b>T78 (parity audit 2026-08-18):</b> upstream's {@code generateNetherOre} (NOTICE.md row,
 * {@code NetherOreGenerator:48-59}) doesn't spend the whole configured rate on one height band --
 * its {@code for (i = 0; i < rate; i += 2)} loop places one vein at {@code y 32 + [0,64)} (y32-95)
 * and one at {@code y 0 + [0,128)} (the full column) per iteration, so each ore's two placed
 * features (the y32-95 band and the y0-127 column) both carry this same modifier instance and each
 * needs half the configured rate, rounded up to match the loop's iteration count -- not the full
 * rate placed twice over. {@link #getPositions} does that halving so the JSON on both placed
 * features can stay identical ({@code ore: cobalt}/{@code ore: ardite}) and only their
 * {@code minecraft:height_range} differs.
 *
 * <p>No NOTICE.md row for this class itself -- ported semantics (the four option names, their
 * defaults, "approx ores per chunk", and now the two-band split), not copied code; there is no 1.12
 * counterpart to a 1.21 placement modifier.
 */
public class NetherOrePlacement extends PlacementModifier {
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Forgeweave.MODID);

    /** Which pair of {@link ForgeweaveConfig} options a given placed feature reads. */
    public enum Ore implements StringRepresentable {
        COBALT("cobalt"),
        ARDITE("ardite");

        private final String name;

        Ore(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }

        /** Veins this ore should place in one chunk, or {@code 0} while its generation is switched off. */
        public int veinsPerChunk() {
            return switch (this) {
                case COBALT -> ForgeweaveConfig.GEN_COBALT.get() ? ForgeweaveConfig.COBALT_RATE.get() : 0;
                case ARDITE -> ForgeweaveConfig.GEN_ARDITE.get() ? ForgeweaveConfig.ARDITE_RATE.get() : 0;
            };
        }
    }

    public static final MapCodec<NetherOrePlacement> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(StringRepresentable.fromEnum(Ore::values).fieldOf("ore").forGetter(NetherOrePlacement::ore))
            .apply(instance, NetherOrePlacement::new));

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<NetherOrePlacement>> TYPE =
            PLACEMENT_MODIFIERS.register("nether_ore_rate", () -> () -> CODEC);

    private final Ore ore;

    public NetherOrePlacement(Ore ore) {
        this.ore = ore;
    }

    public Ore ore() {
        return ore;
    }

    /**
     * Same contract as {@code CountPlacement}: repeat the incoming position once per vein -- but
     * only half the configured rate (rounded up), since this modifier is placed on two placed
     * features per ore (see the class javadoc's T78 note) and each gets one iteration's worth of
     * upstream's split loop.
     */
    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        int perBand = (ore.veinsPerChunk() + 1) / 2;
        return IntStream.range(0, perBand).mapToObj(i -> pos);
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE.get();
    }
}
