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
 * <p>No NOTICE.md row -- ported semantics (the four option names, their defaults, and "approx ores
 * per chunk"), not copied code; there is no 1.12 counterpart to a 1.21 placement modifier.
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

    /** Same contract as {@code CountPlacement}: repeat the incoming position once per vein. */
    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        return IntStream.range(0, ore.veinsPerChunk()).mapToObj(i -> pos);
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE.get();
    }
}
