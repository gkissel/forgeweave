package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Armor Station (docs/SCOPE.md M4 issue #782, reversing D13's "no armor station" call): a
 * second {@link ToolStationBlock} that assembles only {@code Category.ARMOR} entries, leaving the
 * Tool Station and Tool Forge to build everything else.
 *
 * <p>Follows {@link ToolForgeBlock}'s own precedent to the letter -- that class's javadoc quotes
 * upstream 1.12 saying its Tool Forge "literally only is its own block because it has a different
 * material"; the Armor Station is its own block only because it needs a different top texture and a
 * different {@link ToolStationBlockEntity#isArmorStation} answer. Everything else -- the block
 * entity, the menu, the screen, the table shape, the wood-retexture plumbing (unused here: the
 * crafting recipe never sets a {@code TEXTURE} component, so it always wears the shared block
 * entity's default oak look, the same "Tool Station body" the art asks for) -- comes from the
 * superclass and the block entity it shares with the other two table blocks
 * ({@code ForgeweaveBlockEntities#TOOL_STATION} lists all three).
 */
public class ArmorStationBlock extends ToolStationBlock {
    public static final MapCodec<ArmorStationBlock> CODEC = simpleCodec(ArmorStationBlock::new);

    public ArmorStationBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends ArmorStationBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ToolStationBlockEntity(pos, state);
    }
}
