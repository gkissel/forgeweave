package dev.gkissel.forgeweave.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.menu.ToolStationMenu;

/**
 * Draws the parts lying on the Tool Station's (and Tool Forge's -- one block entity type backs both,
 * {@code ToolStationBlockEntity#isForge}) top (issue #567, T75's leftover half of parity audit
 * 2026-08-18 §3.6). Upstream's {@code TileToolStation#setInventoryDisplay} positions each of its six
 * slots from {@code GuiButtonRepair.info}, a {@code ToolBuildGuiInfo} of six pixel positions around
 * an anvil-button centre ({@code x = 33}, {@code y = 42}) that was meant to switch per tool-build
 * mode but never does -- the branch that would swap it (an {@code if (Minecraft...currentScreen
 * instanceof GuiToolStation) info = ...}) is commented out with a {@code // todo: evaluate this
 * again} in the pinned commit, so every stored slot always renders at this one fixed layout
 * regardless of which recipe it is building or repairing. {@link #POSITION_X}/{@link #POSITION_Z}
 * are that layout, converted from pixels to block fractions the same way upstream's own {@code
 * TileToolStation} does ({@code (33 - x) / 61f}, {@code (42 - y) / 61f}).
 *
 * <p>Forgeweave's own {@link ToolStationMenu} keeps the same "six fixed input slots" shape (issue
 * #47's tabs choose which of the six are visible in the GUI, not how many exist -- see that class's
 * javadoc), so upstream's six positions and Forgeweave's six {@link ToolStationMenu#INPUT_SLOTS} line
 * up index-for-index with no role remapping needed, unlike {@link PartBuilderBlockEntityRenderer}.
 */
public class ToolStationBlockEntityRenderer implements BlockEntityRenderer<ToolStationBlockEntity> {
    /** {@code (33 - x) / 61f} for each of upstream's six {@code GuiButtonRepair.info} positions. */
    static final float[] POSITION_X = {0f, 18f / 61f, 22f / 61f, 0f, -22f / 61f, -18f / 61f};
    /** {@code (42 - y) / 61f} for the same six positions. */
    static final float[] POSITION_Z = {0f, -20f / 61f, 5f / 61f, 23f / 61f, 5f / 61f, -20f / 61f};

    /** {@code item.s *= 0.46875f}, upstream's shared scale-down for every slot. */
    static final float SCALE_FACTOR = 0.46875f;
    /** Upstream's extra {@code item.s *= 1.3f} for slot 0 (the head) only. */
    static final float HEAD_BONUS = 1.3f;
    static final int HEAD_INDEX = 0;

    @Override
    public void render(ToolStationBlockEntity blockEntity, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Container container = blockEntity.container();
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            ItemStack stack = container.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            TableItemRenderer.render(stack, POSITION_X[i], POSITION_Z[i], scaleFor(stack, i), poseStack,
                    bufferSource, packedLight, packedOverlay, blockEntity);
        }
    }

    static float scaleFor(ItemStack stack, int slotIndex) {
        float base = TableItemRenderer.isFlat(stack) ? 0.8f : 0.375f;
        return base * SCALE_FACTOR * (slotIndex == HEAD_INDEX ? HEAD_BONUS : 1f);
    }
}
