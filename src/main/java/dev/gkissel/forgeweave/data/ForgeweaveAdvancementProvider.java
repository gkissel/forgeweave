package dev.gkissel.forgeweave.data;

import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.advancement.ForgeweaveCriteriaTriggers;
import dev.gkissel.forgeweave.advancement.SimpleForgeweaveTrigger;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The M2 advancement chain (docs/SCOPE.md M2 issue #110, M2-19): build smeltery -> first melt ->
 * first cast -> first alloy -> first modifier. SCOPE.md's M2 acceptance test asks that "the
 * advancement chain (build -> melt -> cast -> alloy -> modify) completes"; this is that chain,
 * generated the same way {@code ForgeweaveRecipeProvider} generates recipes and {@code
 * ForgeweaveLootTableProvider} generates loot tables -- datapack JSON, not hand-written (ADR-0002).
 *
 * <p>A root plus five linear children, one tab (reusing vanilla's stone background -- SCOPE.md has no
 * derived-art budget for a bespoke one, and M9 is the original-asset milestone anyway). Titles and
 * descriptions are flavor text, not load-bearing, so they follow vanilla's own
 * {@code advancements.<namespace>.<path>.title}/{@code .description} key convention rather than one of
 * CLAUDE.md's named families (none of which cover advancements) -- see {@code
 * ForgeweaveLanguageProvider}'s comment at the same keys.
 *
 * <p>Two criteria are seams, not real hooks: {@code first_melt} and {@code first_alloy} use {@link
 * ForgeweaveCriteriaTriggers#FIRST_MELT}/{@link ForgeweaveCriteriaTriggers#FIRST_ALLOY}, neither of
 * which anything fires yet (melting/#96 and alloying/#98 aren't merged) -- see that class's javadoc.
 */
public final class ForgeweaveAdvancementProvider implements AdvancementProvider.AdvancementGenerator {
    private static final String ROOT_BACKGROUND = "textures/gui/advancements/backgrounds/stone.png";

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper existingFileHelper) {
        AdvancementHolder root = Advancement.Builder.advancement()
                .display(new ItemStack(ForgeweaveItems.SEARED_BRICK.get()),
                        Component.translatable("advancements.forgeweave.smeltery_root.title"),
                        Component.translatable("advancements.forgeweave.smeltery_root.description"),
                        ResourceLocation.withDefaultNamespace(ROOT_BACKGROUND),
                        AdvancementType.TASK, true, true, false)
                .addCriterion("seared_brick", InventoryChangeTrigger.TriggerInstance.hasItems(ForgeweaveItems.SEARED_BRICK.get()))
                .save(saver, id("smeltery/root"), existingFileHelper);

        AdvancementHolder buildSmeltery = child(saver, existingFileHelper, root, "build_smeltery",
                new ItemStack(ForgeweaveItems.STANDARD_CORE.get()), ForgeweaveCriteriaTriggers.SMELTERY_FORMED);

        AdvancementHolder firstMelt = child(saver, existingFileHelper, buildSmeltery, "first_melt",
                new ItemStack(Items.RAW_IRON), ForgeweaveCriteriaTriggers.FIRST_MELT);

        AdvancementHolder firstCast = child(saver, existingFileHelper, firstMelt, "first_cast",
                new ItemStack(ForgeweaveItems.CAST_INGOT.get()), ForgeweaveCriteriaTriggers.FIRST_CAST);

        AdvancementHolder firstAlloy = child(saver, existingFileHelper, firstCast, "first_alloy",
                new ItemStack(Items.NETHERITE_INGOT), ForgeweaveCriteriaTriggers.FIRST_ALLOY);

        child(saver, existingFileHelper, firstAlloy, "first_modifier",
                new ItemStack(ForgeweaveItems.EXTRA_MODIFIER.get()),
                ForgeweaveCriteriaTriggers.FIRST_MODIFIER);
    }

    /** One linear step of the chain: a task advancement, parented to the previous step, triggered by one custom criterion. */
    private static AdvancementHolder child(Consumer<AdvancementHolder> saver, ExistingFileHelper existingFileHelper,
            AdvancementHolder parent, String path, ItemStack icon,
            DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> trigger) {
        return Advancement.Builder.advancement()
                .parent(parent)
                .display(icon,
                        Component.translatable("advancements.forgeweave." + path + ".title"),
                        Component.translatable("advancements.forgeweave." + path + ".description"),
                        null, AdvancementType.TASK, true, true, false)
                .addCriterion("triggered", trigger.get().createCriterion(new SimpleForgeweaveTrigger.TriggerInstance(Optional.empty())))
                .save(saver, id("smeltery/" + path), existingFileHelper);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
