package dev.gkissel.forgeweave.tool;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.BlockTransformer;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.DataMapHooks;

/**
 * Finds what a strip, scrape, wax-off, flatten or till would turn a block into. NeoForge 26.3
 * removed the item abilities {@code BlockState#getToolModifiedState} used to answer this with:
 * vanilla moved those transforms into the {@code block_transformer} registry
 * ({@code BlockTransformers#AXE}, {@code #SHOVEL}, {@code #HOE}, plus NeoForge's data-map
 * additions). This is the matching half of {@link BlockTransformer#transformBlock} without its side
 * effects, so {@link AxeStrip}, {@link ShovelPath} and the mattock keep their own sound, damage and
 * XP handling.
 */
public final class BlockTransforms {

    /** The transform entry that applies, and the state it produces at the clicked position. */
    public record Match(BlockTransformer.BlockTransformData data, BlockState state) {}

    /** The first entry of {@code transformer} that applies to the clicked block and face, or {@code null}. */
    @Nullable
    public static Match match(UseOnContext context, ResourceKey<BlockTransformer> transformer) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Optional<Holder.Reference<BlockTransformer>> holder =
                level.registryAccess().lookupOrThrow(Registries.BLOCK_TRANSFORMER).get(transformer);
        if (holder.isEmpty()) {
            return null;
        }
        for (BlockTransformer.BlockTransformData data : DataMapHooks.getAllTransformers(holder.get())) {
            if (data.disallowedFaces().contains(context.getClickedFace())) {
                continue;
            }
            BlockState state = data.blockStateProvider().value().getOptionalState(level, level.getRandom(), pos);
            if (state != null) {
                return new Match(data, data.updateFromNeighbors() ? Block.updateFromNeighbourShapes(state, level, pos) : state);
            }
        }
        return null;
    }

    private BlockTransforms() {}
}
