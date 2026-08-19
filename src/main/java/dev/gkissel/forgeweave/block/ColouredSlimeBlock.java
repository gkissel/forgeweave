package dev.gkissel.forgeweave.block;

import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A coloured slime block: upstream 1.12's {@code BlockSlime} (NOTICE.md), which extends vanilla's
 * own slime block and adds exactly two things -- the slime sound type, and
 * {@code isStickyBlock -> true} so pistons drag it the way they drag vanilla's (issue #635, parity
 * audit T57).
 *
 * <p>The sound is a block property, set where these register. The stickiness is not: vanilla decides
 * it by identity ({@code PistonBaseBlock} only knows {@code Blocks.SLIME_BLOCK} and
 * {@code Blocks.HONEY_BLOCK}), and NeoForge's {@code IBlockStateExtension#isStickyBlock} is the hook
 * that widens it -- so that override is the whole reason this class exists, exactly as upstream's
 * own override was.
 */
public class ColouredSlimeBlock extends SlimeBlock {
    public ColouredSlimeBlock(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isStickyBlock(BlockState state) {
        return true;
    }
}
