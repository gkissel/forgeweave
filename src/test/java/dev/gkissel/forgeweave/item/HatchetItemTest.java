package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The non-leaf half of {@link HatchetItem#miningDurabilityCost} -- upstream 1.12's
 * {@code tools/tools/Hatchet.java} leaf carve-out (parity audit 2026-08-18 T65, issue #496) is
 * otherwise a plain {@code effective ? 1 : 2}, unchanged from {@link ToolItem}'s own default.
 *
 * <p>The leaf-specific half ({@code state.is(BlockTags.LEAVES)} reading true, and the matching
 * {@link HatchetItem#toolComponent} speed/correctness rules) needs real block-tag data that a bare
 * unit test never has -- {@code Bootstrap.bootStrap()} loads no datapack, so every tag reads empty
 * and {@code state.is(anyTag)} is always false here, leaves included. {@code HatchetGameTests}
 * covers that half through a real Tool Station assembly and a real block break instead.
 */
class HatchetItemTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nonLeafBlocksKeepTheOrdinaryOneOrTwoCost() {
        HatchetItem hatchet = ForgeweaveItems.TOOL_HATCHET.get();
        BlockState stone = Blocks.STONE.defaultBlockState();

        assertEquals(1, hatchet.miningDurabilityCost(stone, true));
        assertEquals(2, hatchet.miningDurabilityCost(stone, false));
    }
}
