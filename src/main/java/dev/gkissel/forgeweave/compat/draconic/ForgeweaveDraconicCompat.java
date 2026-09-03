package dev.gkissel.forgeweave.compat.draconic;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.RecipeSerializer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Forgeweave's Draconic Evolution integration (issue #915, docs/SCOPE.md M8): the seam between the
 * mod proper and {@link FusionUpgradeRecipe}, which is the only class here that names a
 * {@code com.brandon3055} type.
 *
 * <p>Everything in this class is Draconic-free on purpose. {@link Forgeweave} calls
 * {@link #register} behind a {@code ModList.get().isLoaded(MODID)} check, and the datagen and tag
 * providers read {@link #FUSION_UPGRADABLE} and {@link #UPGRADE_LINES} unconditionally -- so this
 * class is classloaded on every install, including one with no Draconic Evolution, and must stay
 * loadable there. The serializer is created inside {@link #register} rather than held in a static
 * field so that {@link FusionUpgradeRecipe} (which does name Draconic types, and would fail to link
 * without them) is only ever reached from inside the guard.
 *
 * <p>Same soft-dependency shape as {@code jade}, {@code kubejs} and {@code jei}: a compileOnly
 * dependency, an {@code optional} entry in {@code neoforge.mods.toml}, and {@code neoforge:conditions}
 * on every recipe JSON so a Forgeweave-only datapack simply drops these rows.
 */
public final class ForgeweaveDraconicCompat {

    /** Draconic Evolution's mod id -- the {@code ModList} guard and every recipe condition key on it. */
    public static final String MODID = "draconicevolution";

    /**
     * What may sit in the Fusion Crafting core as a fusion upgrade's catalyst: every item the Tool
     * Station and Armor Station can assemble ({@code ToolAssemblyRecipes.ENTRIES}, filled in by
     * {@code ForgeweaveItemTagsProvider}), so a new tool family joins the ladder with no code change
     * -- the same "roster as a tag" call {@code ToolAssemblyRecipes#LARGE_TOOLS} makes.
     *
     * <p>Deliberately one broad tag rather than a per-line tag: which shapes a given line actually
     * accepts is already decided, once, by {@link dev.gkissel.forgeweave.modifier.Modifier}'s own
     * gates, and {@link FusionUpgradeRecipe#upgrade} reads them. A second copy of that in tag form
     * would be the drift {@code jei.ModifierApplicationCategory}'s issue #764 bug was made of.
     */
    public static final TagKey<Item> FUSION_UPGRADABLE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "fusion_upgradable"));

    /** The registered name of {@link FusionUpgradeRecipe.Serializer}, i.e. the recipes' {@code type}. */
    public static final String UPGRADE_SERIALIZER_NAME = "draconic_fusion_upgrade";

    /**
     * One rung of one upgrade line: {@code techLevel} is Draconic Evolution's own tier name (the
     * lowest injector tier the craft needs), {@code level} is the modifier level the rung grants in
     * {@link dev.gkissel.forgeweave.modifier.ModifierEntry}'s application units, and {@code energy}
     * is the craft's total RF.
     *
     * <p>The energy ladder is 2M / 8M / 32M / 128M, a straight x4 per tier. The top three numbers are
     * exactly what Draconic Evolution charges for its own tier equipment (its wyvern tools cost 8M,
     * draconic 32M, chaotic 128M, from its recipe datagen); the draconium rung continues that ladder
     * one step down, since DE has no draconium-tier equipment of its own to copy. For scale, DE's
     * Awakened Core is 1M at wyvern and its Chaotic Core 100M at draconic.
     */
    public record Rung(String techLevel, int level, long energy) {}

    /**
     * A fusion upgrade line: one modifier, four rungs, plus the item each rung asks for on top of the
     * tier's Draconic materials -- the modifier's own Tool Station reagent, so the fusion path reads
     * as the same upgrade paid for differently rather than as an unrelated recipe.
     *
     * @param modifier the Forgeweave modifier id the line raises
     * @param reagent the line's own ingredient, one per craft
     * @param rungs draconium, wyvern, draconic and chaotic, in that order
     */
    public record Line(String modifier, String reagent, List<Rung> rungs) {}

    private static final long DRACONIUM_ENERGY = 2_000_000L;
    private static final long WYVERN_ENERGY = 8_000_000L;
    private static final long DRACONIC_ENERGY = 32_000_000L;
    private static final long CHAOTIC_ENERGY = 128_000_000L;

    /**
     * The shipped roster: eight lines, four tiers each (issue #915).
     *
     * <p>Shape mirrors the parity target's fusion upgrade ladder -- a handful of stat lines, each
     * climbing through Draconic Evolution's four tech levels -- with Forgeweave's own ids and
     * numbers, as the issue asks. It is not a line-for-line translation of Draconic Evolution's own
     * tool upgrades, and cannot be: DE's ladder includes three bow lines (draw speed, arrow speed,
     * arrow damage) and an attack-AoE line, and Forgeweave has no modifier for any of the four --
     * the only launcher-facing modifiers in the roster are luck and fins, and fins caps at one
     * application. So each line here is a real Forgeweave modifier that genuinely ladders four steps
     * inside its existing Tool Station cap, and no modifier's cap is raised to fit this table. The
     * DE line each one stands in for is in the PR's roster table.
     *
     * <p>Every level below is at or under the modifier's shipped {@code max_level}
     * ({@code data/forgeweave/forgeweave/modifier_recipe/}), so a fusion upgrade can never put a tool
     * somewhere the Tool Station could not also have reached -- it only makes getting there cost
     * Draconic materials and RF instead of a slot and a pile of reagents.
     */
    public static final List<Line> UPGRADE_LINES = List.of(
            // Dig speed. Haste is 50 application units per display level, capped at 250 (Haste V), so
            // the rungs land on Haste I / II / III+half / V.
            line("haste", "minecraft:redstone_block", 50, 100, 175, 250),
            // Attack damage. Sharpness is 72 units per display level, capped at 360 (Sharpness V).
            line("sharpness", "minecraft:quartz_block", 72, 144, 252, 360),
            // Durability. Reinforced is one unit per level, capped at 5.
            line("reinforced", "forgeweave:reinforced_plate", 2, 3, 4, 5),
            // Modifier slots -- the closest Forgeweave has to DE's "upgrade capacity". One per level,
            // capped at 5.
            line("extra_slot", "forgeweave:extra_modifier", 2, 3, 4, 5),
            // Dig area. Vein mining widens by VEINMINE_BLOCKS_PER_LEVEL per level, capped at 5.
            line("veinmine", "minecraft:prismarine_shard", 2, 3, 4, 5),
            // Attack burn. Fiery is 25 units per display level, capped at 125.
            line("fiery", "minecraft:blaze_powder", 25, 50, 90, 125),
            // Fortune. Luck's display levels sit at 60 / 180 / 360 units (its cost_per_level ladder),
            // so the four rungs are I / II / most of III / III.
            line("luck", "minecraft:lapis_block", 60, 180, 270, 360),
            // Head drops. Beheading is one unit per level, capped at 10.
            line("beheading", "minecraft:obsidian", 3, 5, 8, 10));

    private static Line line(String modifier, String reagent, int draconium, int wyvern, int draconic, int chaotic) {
        return new Line(Forgeweave.MODID + ":" + modifier, reagent, List.of(
                new Rung("draconium", draconium, DRACONIUM_ENERGY),
                new Rung("wyvern", wyvern, WYVERN_ENERGY),
                new Rung("draconic", draconic, DRACONIC_ENERGY),
                new Rung("chaotic", chaotic, CHAOTIC_ENERGY)));
    }

    /**
     * The Draconic Evolution item each tier's craft consumes two of, on top of the line's own reagent.
     * Keyed by {@link Rung#techLevel}. Draconium is the tier's ingot tag; the three tiers above it use
     * the fusion cores DE itself gates that tier behind.
     */
    public static String tierIngredient(String techLevel) {
        return switch (techLevel) {
            case "draconium" -> "#c:ingots/draconium";
            case "wyvern" -> "draconicevolution:wyvern_core";
            case "draconic" -> "draconicevolution:awakened_core";
            case "chaotic" -> "draconicevolution:chaotic_core";
            default -> throw new IllegalArgumentException("no Draconic Evolution tech level named " + techLevel);
        };
    }

    /**
     * Registers {@link FusionUpgradeRecipe.Serializer}. Called from {@link Forgeweave}'s constructor
     * only when Draconic Evolution is present, which is what keeps the recipe class -- and with it
     * every {@code com.brandon3055} type it names -- off a Forgeweave-only install's classloader.
     */
    public static void register(IEventBus modEventBus) {
        DeferredRegister<RecipeSerializer<?>> serializers =
                DeferredRegister.create(Registries.RECIPE_SERIALIZER, Forgeweave.MODID);
        serializers.register(UPGRADE_SERIALIZER_NAME, () -> FusionUpgradeRecipe.Serializer.INSTANCE);
        serializers.register(modEventBus);
    }

    private ForgeweaveDraconicCompat() {}
}
