package dev.gkissel.forgeweave.emi;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.SlotWidget;
import dev.emi.emi.api.widget.TextWidget;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.jei.JeiCategoryGeometry;

/**
 * The few drawing moves every Forgeweave EMI recipe makes, in the JEI categories' own coordinates
 * (issue #971). Each JEI category places a slot by its 16x16 item origin, while an EMI slot widget
 * is placed by its 18x18 frame, one pixel up and left of that; every method here takes the JEI
 * number and does the one-pixel shift itself, so the positions copied from a JEI category read the
 * same in both.
 *
 * <p>Every slot is drawn without EMI's own slot background: the panel art already has its frames
 * baked in, and where it does not, the recipe draws upstream's own frame with {@link #slotFrame},
 * exactly as the JEI category does.
 */
final class EmiArt {
    /** JEI's animation unit is a game tick; EMI's is a millisecond. */
    private static final int MILLIS_PER_TICK = 50;

    private EmiArt() {}

    /** The category's whole background, at the exact upstream rect {@code panel} names. */
    static void panel(WidgetHolder widgets, JeiCategoryGeometry.Panel panel) {
        widgets.addTexture(panel.background(), 0, 0, panel.width(), panel.height(), panel.u(), panel.v());
    }

    /** Upstream's own 18x18 slot frame around the 16x16 slot whose origin is {@code (x, y)}. */
    static void slotFrame(WidgetHolder widgets, int x, int y) {
        widgets.addTexture(JeiCategoryGeometry.TINKER_STATION, x - 1, y - 1, JeiCategoryGeometry.SLOT_FRAME_SIZE,
                JeiCategoryGeometry.SLOT_FRAME_SIZE, JeiCategoryGeometry.SLOT_FRAME_U, JeiCategoryGeometry.SLOT_FRAME_V);
    }

    /** An item slot whose 16x16 origin is {@code (x, y)}. */
    static SlotWidget slot(WidgetHolder widgets, EmiIngredient ingredient, int x, int y) {
        return widgets.addSlot(ingredient, x - 1, y - 1).drawBack(false);
    }

    /** A fluid tank whose inner {@code width}x{@code height} area starts at {@code (x, y)}, full at {@code capacity}. */
    static SlotWidget tank(WidgetHolder widgets, EmiIngredient ingredient, int x, int y, int width, int height, long capacity) {
        return widgets.add(new CyclingTankWidget(ingredient, x - 1, y - 1, width + 2, height + 2, capacity)).drawBack(false);
    }

    /** A crop of {@code texture} drawn at {@code (x, y)}. */
    static void texture(WidgetHolder widgets, ResourceLocation texture, int u, int v, int width, int height, int x, int y) {
        widgets.addTexture(texture, x, y, width, height, u, v);
    }

    /** JEI's {@code buildAnimated(ticks, LEFT, false)}: the crop sweeps in from the left over {@code ticks}. */
    static void arrow(WidgetHolder widgets, ResourceLocation texture, int u, int v, int width, int height,
            int x, int y, int ticks) {
        widgets.addAnimatedTexture(texture, x, y, width, height, u, v, Math.max(1, ticks) * MILLIS_PER_TICK,
                true, false, false);
    }

    /** {@link JeiCategoryGeometry#drawCentered}, ellipsized to {@code maxWidth} the way the JEI categories trim theirs. */
    static void centeredText(WidgetHolder widgets, Component text, int centerX, int y, int colour, boolean shadow,
            int maxWidth) {
        Component shown = JeiCategoryGeometry.trimToWidth(Minecraft.getInstance().font, text, maxWidth);
        widgets.addText(shown, centerX, y, colour, shadow).horizontalAlign(TextWidget.Alignment.CENTER);
    }

    /** Left-aligned text at {@code (x, y)}, without a shadow, ellipsized to {@code maxWidth}. */
    static void leftText(WidgetHolder widgets, Component text, int x, int y, int colour, int maxWidth) {
        widgets.addText(JeiCategoryGeometry.trimToWidth(Minecraft.getInstance().font, text, maxWidth), x, y, colour, false);
    }

    /** JEI's {@code addItemStacks}: every stack in one slot, cycling, or {@link EmiStack#EMPTY} for none. */
    static EmiIngredient items(List<ItemStack> stacks) {
        return EmiIngredient.of(stacks.stream().map(EmiStack::of).toList());
    }

    /** Every output stack of {@code stacks}, for a recipe's {@code getOutputs()}. */
    static List<EmiStack> outputs(List<ItemStack> stacks) {
        return stacks.stream().filter(stack -> !stack.isEmpty()).map(EmiStack::of).toList();
    }

    /** One plain tooltip line. */
    static ClientTooltipComponent line(Component text) {
        return ClientTooltipComponent.create(text.getVisualOrderText());
    }
}
