package dev.gkissel.forgeweave.worldgen;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementModifier;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * Whether this chunk gets a slime island: upstream 1.12's four {@code SlimeIslandGenerator} gates
 * plus its rarity roll (issue #449, parity audit T18), as a placement modifier.
 *
 * <p>Upstream asks all five questions at the top of {@code generate}: {@code genSlimeIslands}, the
 * superflat exemption, the surface-world rule, the dimension blacklist, and finally
 * {@code random.nextInt(slimeIslandRate) > 0}. Forgeweave's world generation is datapack JSON
 * (configured feature + placed feature + {@code neoforge:add_features} biome modifier), which has no
 * seam a config can reach -- the same problem {@link NetherOrePlacement} solves for the Nether ores,
 * solved the same way: one registered modifier that answers "does an island start here", with every
 * gate folded into it so a disabled option is just the already-supported empty stream.
 *
 * <p>Placement runs per chunk at generation time, so each option is read at the moment it applies:
 * changing one affects newly generated chunks only, exactly as upstream.
 *
 * <p>Two of the four gates change shape in the port, both because their 1.12 concepts no longer
 * exist. Upstream's {@code isSurfaceWorld()} becomes {@code DimensionType#natural()}, modern
 * Minecraft's own "behaves like the overworld" flag. Upstream's blacklist is a list of numeric
 * dimension ids ({@code -1, 1} -- the Nether and the End); dimensions are named by
 * {@link ResourceLocation} now, so the option holds ids like {@code minecraft:the_nether} and keeps
 * the same two defaults.
 */
public class SlimeIslandPlacement extends PlacementModifier {
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Forgeweave.MODID);

    public static final MapCodec<SlimeIslandPlacement> CODEC = MapCodec.unit(SlimeIslandPlacement::new);

    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<SlimeIslandPlacement>> TYPE =
            PLACEMENT_MODIFIERS.register("slime_island_rate", () -> () -> CODEC);

    /** Whether an island may start in this dimension at all -- upstream's first four gates. */
    public static boolean enabledIn(ServerLevel level, boolean superflat) {
        if (!ForgeweaveConfig.GEN_SLIME_ISLANDS.get()) {
            return false;
        }
        if (superflat && !ForgeweaveConfig.GEN_ISLANDS_IN_SUPERFLAT.get()) {
            return false;
        }
        if (ForgeweaveConfig.SLIME_ISLANDS_ONLY_IN_SURFACE_WORLDS.get() && !level.dimensionType().natural()) {
            return false;
        }
        String dimension = level.dimension().location().toString();
        return !ForgeweaveConfig.SLIME_ISLAND_BLACKLIST.get().contains(dimension);
    }

    @Override
    public Stream<BlockPos> getPositions(PlacementContext context, RandomSource random, BlockPos pos) {
        if (!enabledIn(context.getLevel().getLevel(), context.generator() instanceof FlatLevelSource)) {
            return Stream.of();
        }
        // Upstream: one chunk in slimeIslandRate, and nothing at all when the rate is zero or less.
        int rate = ForgeweaveConfig.SLIME_ISLAND_RATE.get();
        if (rate <= 0 || random.nextInt(rate) != 0) {
            return Stream.of();
        }
        return Stream.of(pos);
    }

    @Override
    public PlacementModifierType<?> type() {
        return TYPE.get();
    }
}
