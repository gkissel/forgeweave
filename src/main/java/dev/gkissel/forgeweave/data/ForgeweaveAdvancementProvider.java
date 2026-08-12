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
 * The M2/M3 advancement chain (docs/SCOPE.md M2 issue #110/M2-19, extended by M3 issue #166/M3-17):
 * build smeltery -> first melt -> first cast -> first alloy -> first modifier -> forge -> large tool
 * -> emboss -> combat modifier. SCOPE.md's M2 acceptance test asks that "the advancement chain
 * (build -> melt -> cast -> alloy -> modify) completes"; M3-17 hangs its own four steps off that
 * chain's tail rather than starting a second tree, since a player who reaches "first modifier" is
 * already most of the way to a Tool Forge. Generated the same way {@code ForgeweaveRecipeProvider}
 * generates recipes and {@code ForgeweaveLootTableProvider} generates loot tables -- datapack JSON,
 * not hand-written (ADR-0002).
 *
 * <p>A root plus nine linear children, one tab (reusing vanilla's stone background -- SCOPE.md has no
 * derived-art budget for a bespoke one, and M9 is the original-asset milestone anyway). Titles and
 * descriptions are flavor text, not load-bearing, so they follow vanilla's own
 * {@code advancements.<namespace>.<path>.title}/{@code .description} key convention rather than one of
 * CLAUDE.md's named families (none of which cover advancements) -- see {@code
 * ForgeweaveLanguageProvider}'s comment at the same keys.
 *
 * <p>Every criterion but "forge" fires from a real gameplay hook; see {@link
 * ForgeweaveCriteriaTriggers}'s javadoc for where each one lives (#98 filled in the last of the M2
 * five, {@code first_alloy}; #166's own javadoc entry covers the M3 three). "Forge" uses the root's
 * own {@code InventoryChangeTrigger.hasItems} idiom instead of a custom criterion -- owning a Tool
 * Forge needs no gameplay hook, the same reasoning the root's "own a seared brick" step already
 * relies on.
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

        AdvancementHolder firstModifier = child(saver, existingFileHelper, firstAlloy, "first_modifier",
                new ItemStack(ForgeweaveItems.EXTRA_MODIFIER.get()),
                ForgeweaveCriteriaTriggers.FIRST_MODIFIER);

        // M3-17's tail (docs/SCOPE.md M3 issue #166): forge -> large tool -> emboss -> combat
        // modifier. "Forge" is the one step with no custom criterion -- owning a Tool Forge is exactly
        // the root's own "own the item" idiom (InventoryChangeTrigger.hasItems), so it needs no entry
        // in ForgeweaveCriteriaTriggers at all; see that class's javadoc for where the other three fire.
        AdvancementHolder forge = Advancement.Builder.advancement()
                .parent(firstModifier)
                .display(new ItemStack(ForgeweaveItems.TOOL_FORGE.get()),
                        Component.translatable("advancements.forgeweave.forge.title"),
                        Component.translatable("advancements.forgeweave.forge.description"),
                        null, AdvancementType.TASK, true, true, false)
                .addCriterion("tool_forge", InventoryChangeTrigger.TriggerInstance.hasItems(ForgeweaveItems.TOOL_FORGE.get()))
                .save(saver, id("smeltery/forge"), existingFileHelper);

        AdvancementHolder largeTool = child(saver, existingFileHelper, forge, "large_tool",
                new ItemStack(ForgeweaveItems.TOOL_HAMMER.get()), ForgeweaveCriteriaTriggers.LARGE_TOOL_ASSEMBLED);

        AdvancementHolder emboss = child(saver, existingFileHelper, largeTool, "emboss",
                new ItemStack(ForgeweaveItems.PART_TOOL_HANDLE.get()), ForgeweaveCriteriaTriggers.FIRST_EMBOSSMENT);

        child(saver, existingFileHelper, emboss, "combat_modifier",
                new ItemStack(ForgeweaveItems.INGOT_MANYULLYN.get()),
                ForgeweaveCriteriaTriggers.COMBAT_MODIFIER_APPLIED);
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
