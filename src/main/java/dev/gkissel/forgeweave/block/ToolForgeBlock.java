package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Tool Forge (docs/SCOPE.md M3 issue #152): a Tool Station superset -- everything the Tool
 * Station does, plus large-tool assembly (which the Tool Station refuses) and a 5% cheaper repair.
 *
 * <p>Upstream 1.12's {@code BlockToolForge} says it out loud: "This literally only is its own block
 * because it has a different material" -- it extends the same {@code BlockTable}, returns the same
 * gui number as the Tool Station (25), and its tile entity is a {@code TileToolStation} subclass
 * that swaps the buildable-tool set and the craft sound. Forgeweave mirrors that by subclassing
 * {@link ToolStationBlock} and sharing its block entity, menu and screen outright; the only things
 * this class carries of its own are its {@link #CODEC} and its metal {@code Properties} (registered
 * in {@link ForgeweaveBlocks}: iron sound, upstream's hardness 2 / resistance 10, pickaxe-mineable).
 *
 * <p>Everything else follows from the shared block entity: {@link ToolStationBlockEntity} answers
 * {@code isForge()} from the block it sits on, so nothing here needs a flag of its own, and
 * {@code ForgeweaveBlockEntities.TOOL_STATION} simply lists both blocks as valid. That is also why
 * {@link ToolStationBlock}'s inherited {@code setPlacedBy}/{@code getCloneItemStack}/{@code
 * onRemove}/{@code useWithoutItem} work here unchanged -- they all test for
 * {@code ToolStationBlockEntity}, which is exactly what a Tool Forge has.
 */
public class ToolForgeBlock extends ToolStationBlock {
    public static final MapCodec<ToolForgeBlock> CODEC = simpleCodec(ToolForgeBlock::new);

    public ToolForgeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends ToolForgeBlock> codec() {
        return CODEC;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ToolStationBlockEntity(pos, state);
    }
}
