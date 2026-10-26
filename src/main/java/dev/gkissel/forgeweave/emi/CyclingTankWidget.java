package dev.gkissel.forgeweave.emi;

import java.util.List;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.TankWidget;

/**
 * A tank that cycles through every fluid it holds, once a second, the way a JEI fluid slot given
 * several fluids does (issue #971). EMI's own {@link TankWidget} only ever draws the first fluid of a
 * list, which would freeze melting's fuel column and entity melting's fuel tank on one fuel. Showing
 * the cycled fluid as the slot's stack keeps the tooltip on the fluid actually drawn, too.
 *
 * <p>A tank holding one fluid hands back the very ingredient it was built with, since EMI's fill
 * overlay matches a recipe's inputs to its slots by identity.
 */
final class CyclingTankWidget extends TankWidget {
    private static final long CYCLE_MILLIS = 1000;

    CyclingTankWidget(EmiIngredient stack, int x, int y, int width, int height, long capacity) {
        super(stack, x, y, width, height, capacity);
    }

    @Override
    public EmiIngredient getStack() {
        List<EmiStack> fluids = stack.getEmiStacks();
        if (fluids.size() <= 1) {
            return stack;
        }
        EmiStack shown = fluids.get((int) (System.currentTimeMillis() / CYCLE_MILLIS % fluids.size()));
        return shown.copy().setAmount(stack.getAmount());
    }
}
