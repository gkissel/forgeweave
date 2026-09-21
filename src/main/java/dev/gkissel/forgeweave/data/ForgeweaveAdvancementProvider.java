package dev.gkissel.forgeweave.data;

import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.advancements.Criterion;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.InventoryChangeTrigger;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;

import net.neoforged.neoforge.common.data.AdvancementProvider;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredHolder;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.advancement.ForgeweaveCriteriaTriggers;
import dev.gkissel.forgeweave.advancement.SimpleForgeweaveTrigger;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The advancement tree, generated as datapack JSON the same way {@code ForgeweaveRecipeProvider}
 * generates recipes (ADR-0002).
 *
 * <p>Issue #1106 re-rooted it. M2/M3 (#110, #166) shipped ten advancements in one strand from "craft
 * a seared brick" to "apply a combat modifier", which told a player exactly one next step at a time
 * and said nothing about the book, the Part Builder, a first tool, armor, bows or leveling. The root
 * is now the guide book, the way 1.20's own tree is rooted at having its first book, and the tree
 * branches per system so the advancement screen doubles as a reading order:
 *
 * <pre>
 * root (have the guide book)
 * |- tools/pattern -> part_builder -> tool_station -> first_tool (goal)
 * |    |- tools/repair, tools/part_exchange
 * |    |- armor/first_piece -> armor/full_set (goal) -> armor/leveled (challenge)
 * |    |- ranged/bow -> ranged/battlesign
 * |    '- smeltery/first_modifier
 * |         |- smeltery/combat_modifier
 * |         |- modifiers/slots_filled (challenge)
 * |         '- leveling/first_level (goal)
 * '- smeltery/root -> build_smeltery -> first_melt -> first_cast
 *      |- smeltery/cast, smeltery/core_tier (goal), smeltery/hotter_fuel
 *      |- smeltery/first_alloy -> alloys/deep (goal) -> alloys/deepest (challenge)
 *      '- smeltery/forge -> large_tool -> emboss
 * </pre>
 *
 * <p><b>All ten M2/M3 ids are kept.</b> {@code PlayerAdvancements} keys progress by advancement id
 * and re-parenting does not touch that, so a world that already earned {@code
 * forgeweave:smeltery/first_melt} keeps it; only three parents move ({@code smeltery/root} gains the
 * book root, {@code smeltery/first_modifier} moves under the first tool, {@code smeltery/forge} moves
 * under the first cast, which is where its seared bricks come from anyway). Nothing is renamed and
 * nothing needs migrating.
 *
 * <p>Criteria stay on vanilla triggers wherever one fits -- {@code InventoryChangeTrigger} covers
 * every "own the thing" step, which is most of the new tree. The six steps no vanilla trigger can see
 * (a tool assembled, repaired or part-swapped at a station, a tool whose last modifier slot went, and
 * a tool or armor piece leveling up) are the six {@link ForgeweaveCriteriaTriggers} added for this
 * issue, each fired from the single server-side path that already handles the event; see that class
 * for where. Every criterion here therefore fires on a dedicated server with no client involvement.
 *
 * <p>Alloy depth is data, not a list: {@link ForgeweaveItemTagsProvider#ALLOYS_DEEP} and {@link
 * ForgeweaveItemTagsProvider#ALLOYS_DEEPEST} are built from the shipped {@code alloy_recipe} JSON
 * ({@link AlloyDepths}), so rebalancing the alloy graph moves the two alloy advancements with it.
 *
 * <p>One tab, reusing vanilla's stone background: SCOPE.md has no derived-art budget for a bespoke
 * one, which is the same reasoning #110 recorded. Titles and descriptions are Forgeweave's own text
 * under vanilla's {@code advancements.<namespace>.<path>.title}/{@code .description} key convention
 * (see {@code ForgeweaveLanguageProvider} at the same keys), one plain sentence each naming the next
 * thing to do.
 */
public final class ForgeweaveAdvancementProvider implements AdvancementProvider.AdvancementGenerator {
    private static final String ROOT_BACKGROUND = "textures/gui/advancements/backgrounds/stone.png";

    @Override
    public void generate(HolderLookup.Provider registries, Consumer<AdvancementHolder> saver, ExistingFileHelper existingFileHelper) {
        Saver save = new Saver(saver, existingFileHelper);

        AdvancementHolder root = Advancement.Builder.advancement()
                .display(new ItemStack(ForgeweaveItems.GUIDE_BOOK.get()),
                        title("guide_book"), description("guide_book"),
                        ResourceLocation.withDefaultNamespace(ROOT_BACKGROUND),
                        AdvancementType.TASK, true, true, false)
                .addCriterion("guide_book", owns(ForgeweaveItems.GUIDE_BOOK.get()))
                .save(saver, id("root"), existingFileHelper);

        buildToolsBranch(save, root);
        buildSmelteryBranch(save, root);
    }

    /**
     * The first-tools branch and everything a Tool Station unlocks: repair, part exchange, armor,
     * bows, and the modifier/leveling sub-branch that M3's {@code smeltery/first_modifier} heads.
     */
    private static void buildToolsBranch(Saver save, AdvancementHolder root) {
        AdvancementHolder pattern = save.owning(root, "tools/pattern", "pattern",
                ForgeweaveItems.PATTERN_BLANK.get(), AdvancementType.TASK, ForgeweaveItems.PATTERN_BLANK.get());
        AdvancementHolder partBuilder = save.owning(pattern, "tools/part_builder", "part_builder",
                ForgeweaveItems.PART_BUILDER.get(), AdvancementType.TASK, ForgeweaveItems.PART_BUILDER.get());
        AdvancementHolder toolStation = save.owning(partBuilder, "tools/tool_station", "tool_station",
                ForgeweaveItems.TOOL_STATION.get(), AdvancementType.TASK, ForgeweaveItems.TOOL_STATION.get());

        AdvancementHolder firstTool = save.triggered(toolStation, "tools/first_tool", "first_tool",
                ForgeweaveItems.TOOL_PICKAXE.get(), AdvancementType.GOAL,
                ForgeweaveCriteriaTriggers.TOOL_ASSEMBLED);
        save.triggered(firstTool, "tools/repair", "repair",
                ForgeweaveItems.PART_SHARPENING_KIT.get(), AdvancementType.TASK,
                ForgeweaveCriteriaTriggers.TOOL_REPAIRED);
        save.triggered(firstTool, "tools/part_exchange", "part_exchange",
                ForgeweaveItems.PART_PICKAXE_HEAD.get(), AdvancementType.TASK,
                ForgeweaveCriteriaTriggers.PART_EXCHANGED);

        buildArmorBranch(save, firstTool);
        buildRangedBranch(save, firstTool);
        buildModifierBranch(save, firstTool);
    }

    /** Armor: any piece, then one of each slot, then a piece that has taken enough hits to level. */
    private static void buildArmorBranch(Saver save, AdvancementHolder firstTool) {
        AdvancementHolder firstPiece = save.finish(save.advancement(firstTool, "armor_piece",
                ForgeweaveItems.ARMOR_HELMET.get(), AdvancementType.TASK)
                .addCriterion("armor_piece", owns(anyOf(
                        ForgeweaveItems.ARMOR_HELMET.get(), ForgeweaveItems.ARMOR_CHESTPLATE.get(),
                        ForgeweaveItems.ARMOR_LEGGINGS.get(), ForgeweaveItems.ARMOR_BOOTS.get(),
                        ForgeweaveItems.ARMOR_HEAVY_HELMET.get(), ForgeweaveItems.ARMOR_HEAVY_CHESTPLATE.get(),
                        ForgeweaveItems.ARMOR_HEAVY_LEGGINGS.get(), ForgeweaveItems.ARMOR_HEAVY_BOOTS.get()))),
                "armor/first_piece");

        // Four criteria, one per slot, each satisfied by either weight -- the light set is a Tool
        // Station build and the heavy set a Tool Forge one (#1006), and a mixed set is a real loadout.
        AdvancementHolder fullSet = save.finish(save.advancement(firstPiece, "armor_set",
                ForgeweaveItems.ARMOR_CHESTPLATE.get(), AdvancementType.GOAL)
                .addCriterion("helmet", owns(anyOf(
                        ForgeweaveItems.ARMOR_HELMET.get(), ForgeweaveItems.ARMOR_HEAVY_HELMET.get())))
                .addCriterion("chestplate", owns(anyOf(
                        ForgeweaveItems.ARMOR_CHESTPLATE.get(), ForgeweaveItems.ARMOR_HEAVY_CHESTPLATE.get())))
                .addCriterion("leggings", owns(anyOf(
                        ForgeweaveItems.ARMOR_LEGGINGS.get(), ForgeweaveItems.ARMOR_HEAVY_LEGGINGS.get())))
                .addCriterion("boots", owns(anyOf(
                        ForgeweaveItems.ARMOR_BOOTS.get(), ForgeweaveItems.ARMOR_HEAVY_BOOTS.get()))),
                "armor/full_set");

        save.triggered(fullSet, "armor/leveled", "armor_level",
                ForgeweaveItems.ARMOR_HEAVY_CHESTPLATE.get(), AdvancementType.CHALLENGE,
                ForgeweaveCriteriaTriggers.ARMOR_LEVEL_UP);
    }

    /** Ranged: any of the three drawn weapons, then the battlesign. */
    private static void buildRangedBranch(Saver save, AdvancementHolder firstTool) {
        AdvancementHolder bow = save.finish(save.advancement(firstTool, "bow",
                ForgeweaveItems.TOOL_SHORTBOW.get(), AdvancementType.TASK)
                .addCriterion("bow", owns(anyOf(ForgeweaveItems.TOOL_SHORTBOW.get(),
                        ForgeweaveItems.TOOL_LONGBOW.get(), ForgeweaveItems.TOOL_CROSSBOW.get()))),
                "ranged/bow");

        save.owning(bow, "ranged/battlesign", "battlesign",
                ForgeweaveItems.TOOL_BATTLESIGN.get(), AdvancementType.TASK, ForgeweaveItems.TOOL_BATTLESIGN.get());
    }

    /** M3's {@code smeltery/first_modifier}, kept at its own id, plus the two ends #1106 hangs on it. */
    private static void buildModifierBranch(Saver save, AdvancementHolder firstTool) {
        AdvancementHolder firstModifier = save.triggered(firstTool, "smeltery/first_modifier", "first_modifier",
                ForgeweaveItems.EXTRA_MODIFIER.get(), AdvancementType.TASK,
                ForgeweaveCriteriaTriggers.FIRST_MODIFIER);

        save.triggered(firstModifier, "smeltery/combat_modifier", "combat_modifier",
                ForgeweaveItems.INGOT_MANYULLYN.get(), AdvancementType.TASK,
                ForgeweaveCriteriaTriggers.COMBAT_MODIFIER_APPLIED);
        save.triggered(firstModifier, "modifiers/slots_filled", "slots_filled",
                ForgeweaveItems.EXTRA_MODIFIER.get(), AdvancementType.CHALLENGE,
                ForgeweaveCriteriaTriggers.MODIFIER_SLOTS_FILLED);
        save.triggered(firstModifier, "leveling/first_level", "first_level",
                Items.EXPERIENCE_BOTTLE, AdvancementType.GOAL, ForgeweaveCriteriaTriggers.TOOL_LEVEL_UP);
    }

    /** M2's smeltery line, kept at its own ids, plus casts, hotter cores, hotter fuel and alloy depth. */
    private static void buildSmelteryBranch(Saver save, AdvancementHolder root) {
        AdvancementHolder smelteryRoot = save.owning(root, "smeltery/root", "smeltery_root",
                ForgeweaveItems.SEARED_BRICK.get(), AdvancementType.TASK, ForgeweaveItems.SEARED_BRICK.get());
        AdvancementHolder buildSmeltery = save.triggered(smelteryRoot, "smeltery/build_smeltery", "build_smeltery",
                ForgeweaveItems.STANDARD_CORE.get(), AdvancementType.TASK,
                ForgeweaveCriteriaTriggers.SMELTERY_FORMED);
        AdvancementHolder firstMelt = save.triggered(buildSmeltery, "smeltery/first_melt", "first_melt",
                Items.RAW_IRON, AdvancementType.TASK, ForgeweaveCriteriaTriggers.FIRST_MELT);
        AdvancementHolder firstCast = save.triggered(firstMelt, "smeltery/first_cast", "first_cast",
                ForgeweaveItems.CAST_INGOT.get(), AdvancementType.TASK, ForgeweaveCriteriaTriggers.FIRST_CAST);

        save.finish(save.advancement(firstCast, "cast",
                ForgeweaveItems.CAST_PICKAXE_HEAD.get(), AdvancementType.TASK)
                .addCriterion("cast", owns(ItemPredicate.Builder.item().of(ForgeweaveItemTagsProvider.CASTS_GOLD))),
                "smeltery/cast");

        save.finish(save.advancement(firstCast, "core_tier",
                ForgeweaveItems.NETHER_CORE.get(), AdvancementType.GOAL)
                .addCriterion("hotter_core", owns(anyOf(ForgeweaveItems.NETHER_CORE.get(),
                        ForgeweaveItems.END_CORE.get(), ForgeweaveItems.DEEP_CORE.get()))),
                "smeltery/core_tier");

        // The four rungs above lava (1300) on ForgeweaveFluids' own fuel ladder. A bucket, because
        // every molten fluid is bucketable (#286) and a seared tank fills one both ways -- there is no
        // vanilla criterion for "this smeltery is burning something hotter".
        save.finish(save.advancement(firstCast, "hotter_fuel",
                ForgeweaveFluids.BLAZING_BLOOD.bucket().get(), AdvancementType.TASK)
                .addCriterion("hotter_fuel", owns(anyOf(
                        ForgeweaveFluids.BLAZING_BLOOD.bucket().get(), ForgeweaveFluids.MOLTEN_MAGMA.bucket().get(),
                        ForgeweaveFluids.BRIMSPAR.bucket().get(), ForgeweaveFluids.PYREALLOY.bucket().get()))),
                "smeltery/hotter_fuel");

        AdvancementHolder firstAlloy = save.triggered(firstCast, "smeltery/first_alloy", "first_alloy",
                Items.NETHERITE_INGOT, AdvancementType.TASK, ForgeweaveCriteriaTriggers.FIRST_ALLOY);

        // Icons only: which metals actually sit at each depth is whatever the alloy_recipe graph says
        // today, and the tag is what decides the criterion.
        AdvancementHolder deepAlloy = save.finish(save.advancement(firstAlloy, "deep_alloy",
                ForgeweaveItems.trackBAlloyIngot("tideiron").get(), AdvancementType.GOAL)
                .addCriterion("deep_alloy",
                        owns(ItemPredicate.Builder.item().of(ForgeweaveItemTagsProvider.ALLOYS_DEEP))),
                "alloys/deep");
        save.finish(save.advancement(deepAlloy, "deepest_alloy",
                ForgeweaveItems.trackBAlloyIngot("truesteel").get(), AdvancementType.CHALLENGE)
                .addCriterion("deepest_alloy",
                        owns(ItemPredicate.Builder.item().of(ForgeweaveItemTagsProvider.ALLOYS_DEEPEST))),
                "alloys/deepest");

        AdvancementHolder forge = save.owning(firstCast, "smeltery/forge", "forge",
                ForgeweaveItems.TOOL_FORGE.get(), AdvancementType.TASK, ForgeweaveItems.TOOL_FORGE.get());
        AdvancementHolder largeTool = save.triggered(forge, "smeltery/large_tool", "large_tool",
                ForgeweaveItems.TOOL_HAMMER.get(), AdvancementType.TASK,
                ForgeweaveCriteriaTriggers.LARGE_TOOL_ASSEMBLED);
        save.triggered(largeTool, "smeltery/emboss", "emboss",
                ForgeweaveItems.PART_TOOL_HANDLE.get(), AdvancementType.TASK,
                ForgeweaveCriteriaTriggers.FIRST_EMBOSSMENT);
    }

    /** One {@code items} predicate satisfied by any of {@code items} -- an OR inside a single criterion. */
    private static ItemPredicate.Builder anyOf(ItemLike... items) {
        return ItemPredicate.Builder.item().of(items);
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> owns(ItemLike item) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(item);
    }

    private static Criterion<InventoryChangeTrigger.TriggerInstance> owns(ItemPredicate.Builder predicate) {
        return InventoryChangeTrigger.TriggerInstance.hasItems(predicate);
    }

    private static Component title(String key) {
        return Component.translatable("advancements.forgeweave." + key + ".title");
    }

    private static Component description(String key) {
        return Component.translatable("advancements.forgeweave." + key + ".description");
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /**
     * The saver plus the two builder shapes every node in the tree is one of: "own this item" and
     * "this custom trigger fired". Nodes that need more than one criterion (a full armor set, an
     * either-weight slot) call {@link #advancement} and finish the builder themselves.
     */
    private record Saver(Consumer<AdvancementHolder> saver, ExistingFileHelper existingFileHelper) {

        /** A node with its display set and no criteria yet; the caller adds them and calls {@link #finish}. */
        Advancement.Builder advancement(AdvancementHolder parent, String langKey, ItemLike icon, AdvancementType type) {
            return Advancement.Builder.advancement()
                    .parent(parent)
                    .display(new ItemStack(icon), title(langKey), description(langKey), null, type, true, true, false);
        }

        AdvancementHolder finish(Advancement.Builder builder, String path) {
            return builder.save(saver, id(path), existingFileHelper);
        }

        AdvancementHolder owning(AdvancementHolder parent, String path, String langKey, ItemLike icon,
                AdvancementType type, ItemLike required) {
            return finish(advancement(parent, langKey, icon, type).addCriterion("has_item", owns(required)), path);
        }

        AdvancementHolder triggered(AdvancementHolder parent, String path, String langKey, ItemLike icon,
                AdvancementType type,
                DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> trigger) {
            return finish(advancement(parent, langKey, icon, type).addCriterion("triggered",
                    trigger.get().createCriterion(new SimpleForgeweaveTrigger.TriggerInstance(Optional.empty()))), path);
        }
    }
}
