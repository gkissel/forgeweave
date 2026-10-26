package dev.gkissel.forgeweave.emi;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import dev.emi.emi.api.recipe.BasicEmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.WidgetHolder;

import dev.gkissel.forgeweave.jei.EmbossingDisplay;
import dev.gkissel.forgeweave.jei.ModifierApplicationRecipes;
import dev.gkissel.forgeweave.jei.ModifierPanel;
import dev.gkissel.forgeweave.modifier.Embossing;
import dev.gkissel.forgeweave.modifier.ModifierApplication;

/**
 * One embossing row (issue #165): a donor part plus the fixed reagent set adds the donor material's
 * traits to any tool. The JEI {@code EmbossingCategory}'s layout on {@link ModifierPanel}: the donor
 * in the ring's top slot, the reagents in the rest, the same render-only "any tool" pair as modifier
 * application, the embossment's name across the top and the one-per-tool rule under it.
 *
 * <p>Inputs are the donor then each reagent, the order the fill handler places them in the station's
 * free slots, which is the order {@code Embossing#resolve} reads them back.
 */
final class EmbossingEmiRecipe extends BasicEmiRecipe {
    /** Embossing spans the station's five free slots and the donor always claims one. */
    private static final int MAX_REAGENT_SLOTS = ModifierPanel.INPUT_SLOTS.length - 1;

    private final EmbossingDisplay display;
    private final EmiIngredient tools = EmiArt.items(ModifierApplicationRecipes.ANY_TOOL);

    EmbossingEmiRecipe(ResourceLocation id, EmbossingDisplay display) {
        super(ForgeweaveEmiCategories.EMBOSSING, id, ModifierPanel.WIDTH, ModifierPanel.HEIGHT);
        this.display = display;
        List<EmiIngredient> in = new ArrayList<>();
        in.add(EmiArt.items(display.donorParts()));
        display.reagents().stream().limit(MAX_REAGENT_SLOTS).map(EmiIngredient::of).forEach(in::add);
        inputs = List.copyOf(in);
        outputs = List.of();
    }

    @Override
    public void addWidgets(WidgetHolder widgets) {
        EmiArt.panel(widgets, ModifierPanel.PANEL);
        EmiArt.slot(widgets, tools, ModifierPanel.TOOL_X, ModifierPanel.TOOL_Y);
        for (int i = 0; i < inputs.size(); i++) {
            EmiArt.slot(widgets, inputs.get(i), ModifierPanel.INPUT_SLOTS[i][0], ModifierPanel.INPUT_SLOTS[i][1]);
        }
        EmiArt.slot(widgets, tools.copy(), ModifierPanel.RESULT_X, ModifierPanel.RESULT_Y);

        EmiArt.centeredText(widgets, ModifierApplication.name(Embossing.idFor(display.material())),
                ModifierPanel.NAME_CENTER_X, ModifierPanel.NAME_Y, ModifierPanel.NAME_COLOR, true, ModifierPanel.NAME_WIDTH);
        EmiArt.centeredText(widgets, Component.translatable("jei.category.forgeweave.embossing.one_per_tool"),
                ModifierPanel.LEVEL_CENTER_X, ModifierPanel.LEVEL_Y, ModifierPanel.TEXT_COLOR, false, ModifierPanel.LEVEL_WIDTH);
    }
}
