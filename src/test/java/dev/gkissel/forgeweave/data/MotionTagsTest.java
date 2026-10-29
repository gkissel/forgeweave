package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.registries.DeferredHolder;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;

/**
 * 26.3 replaced {@code BlockState#blocksMotion} with the {@code blocks_motion} block tags and made
 * washing away by fluids opt-in ({@code washed_away_by_fluids}). The generated tags have to put
 * every Forgeweave block where 26.2's per-state rules did: solid blocks block motion, every other
 * block except a liquid container is washed away. A tag is per block, so this also fails on a
 * block whose states disagree about being solid, which a per-block tag could not follow.
 */
class MotionTagsTest {

    /**
     * The seared channel's bounds only reach 26.2's solid size when it is open on all four sides, so
     * a crossing channel blocked motion there and a plain one did not. The tag follows the default
     * state, the plain channel, which 26.2's water washed away.
     */
    private static final Set<String> MIXED_STATES = Set.of("forgeweave:seared_channel");

    @Test
    void everyBlockKeepsItsTwentySixTwoMotionAndFluidRules() throws IOException {
        Set<String> motion = values("blocks_motion_no_leaves");
        Set<String> washedAway = values("washed_away_by_fluids");
        List<String> wrong = new ArrayList<>();
        Stream.concat(ForgeweaveBlocks.BLOCKS.getEntries().stream(), ForgeweaveFluids.BLOCKS.getEntries().stream())
                .map(DeferredHolder::get)
                .forEach(block -> {
                    String id = BuiltInRegistries.BLOCK.getKey(block).toString();
                    boolean solid = block.defaultBlockState().isSolid();
                    if (!MIXED_STATES.contains(id) && block.getStateDefinition().getPossibleStates().stream().anyMatch(s -> s.isSolid() != solid)) {
                        wrong.add(id + " (states disagree on isSolid)");
                    }
                    boolean leaves = block instanceof LeavesBlock;
                    boolean expectMotion = !leaves && solid;
                    boolean expectWashed = !leaves && !solid && !(block instanceof LiquidBlockContainer);
                    if (motion.contains(id) != expectMotion || washedAway.contains(id) != expectWashed) {
                        wrong.add(id + " (motion " + motion.contains(id) + ", washed away " + washedAway.contains(id) + ")");
                    }
                });
        assertTrue(wrong.isEmpty(), "blocks whose 26.3 motion tags differ from 26.2's rules (rerun runData?): " + wrong);
    }

    @Test
    void representativeBlocksLandInTheRightTag() throws IOException {
        BlockState bricks = ForgeweaveBlocks.SEARED_BRICKS.get().defaultBlockState();
        assertTrue(bricks.isSolid(), "isSolid needs the state cache; without it every block reads as not solid");
        assertTrue(values("blocks_motion_no_leaves").contains("forgeweave:seared_bricks"));
        assertTrue(values("washed_away_by_fluids").contains("forgeweave:molten_iron"));
        assertTrue(values("washed_away_by_fluids").contains("forgeweave:blue_slime_fern"));
        assertEquals(Set.of(), intersection(values("blocks_motion_no_leaves"), values("washed_away_by_fluids")));
    }

    private static Set<String> intersection(Set<String> a, Set<String> b) {
        Set<String> both = new HashSet<>(a);
        both.retainAll(b);
        return both;
    }

    private static Set<String> values(String tag) throws IOException {
        Path file = LocalizationAuditTest.projectRoot()
                .resolve("src/generated/resources/data/minecraft/tags/block/" + tag + ".json");
        Set<String> values = new HashSet<>();
        JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8))
                .getAsJsonObject().getAsJsonArray("values").forEach(v -> values.add(v.getAsString()));
        return values;
    }
}
