package dev.gkissel.forgeweave.trait;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.tool.AoeHarvest;

/**
 * Breaking one block takes the whole connected run of that same block with it, and each extra block
 * costs the tool durability on top of its own (issue #1114's {@code vein_break}).
 *
 * <p>The price is the point. A vein hammer gets its sweep from the tool kind and pays nothing extra
 * for it; this is a <em>material</em> saying "any tool made of me mines an ore body in one swing,
 * and you will feel it in the durability bar". {@code veinseeker} pulls 12 blocks at one extra
 * durability each, {@code veinseeker2} pulls 28. Both numbers are datapack fields.
 *
 * <p>Reuses {@link AoeHarvest#veinFrom} for the flood fill and its breakability filter and
 * {@link AoeHarvest#breakEach} for the removal, the same way {@link CascadingBreak} does -- there is
 * no third break loop here, and no third copy of the drops/Broken-tool accounting.
 * {@link #veining} is {@link CascadingBreak}'s own re-entrancy guard for the same reason: every
 * extra block breaks through the path that re-fires this hook.
 *
 * @param maxBlocks the deepest run one swing pulls, the origin block not counted
 * @param durabilityPerBlock extra durability each pulled block costs; {@code 0} for a free sweep
 */
public record VeinBreak(int maxBlocks, int durabilityPerBlock) implements Trait {

    /** See the class javadoc. A plain field is enough: every path that touches it is the server thread. */
    private static boolean veining;

    @Override
    public void afterBlockBreak(ItemStack stack, ServerLevel level, BlockState state, BlockPos pos,
            LivingEntity breaker, boolean effective) {
        if (veining || !effective || !(breaker instanceof ServerPlayer player)) {
            return;
        }
        List<BlockPos> rest = AoeHarvest.veinFrom(stack, level, player, pos, state, maxBlocks);
        if (rest.isEmpty()) {
            return;
        }
        veining = true;
        try {
            AoeHarvest.breakEach(stack, player, rest);
        } finally {
            veining = false;
        }
        if (durabilityPerBlock > 0) {
            stack.hurtAndBreak(rest.size() * durabilityPerBlock, player, EquipmentSlot.MAINHAND);
        }
        // #1112: TraitFeedback.fire(this, TraitFeedback.Kind.HARVEST, level, player);
        SignatureFeedback.fire(SignatureFeedback.Kind.HARVEST, level, player);
    }
}
