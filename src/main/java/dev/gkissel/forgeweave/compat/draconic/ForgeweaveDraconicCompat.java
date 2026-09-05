package dev.gkissel.forgeweave.compat.draconic;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.draconic.modules.DraconicModuleHost;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

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

    /** The registered name of {@link FusionUpgradeRecipe.CatalystDisplay}'s ingredient type (issue #952). */
    public static final String CATALYST_INGREDIENT_NAME = "fusion_catalyst";

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
     * One of the four metals a Draconic Evolution fusion craft makes (issues #946 and #965), and the
     * whole of its {@code draconicevolution:fusion_crafting} row.
     *
     * <p>The catalyst is always {@link #CATALYST}, the weldheart, and the result is always one ingot
     * of {@code material}. The shape follows the parity target's -- one craft per DE tier, one DE
     * core per craft, an escalating vanilla rare alongside it -- with Forgeweave's own metals, ids,
     * energies and ingredients.
     *
     * @param material the metal's id, also its {@code Material} JSON's file name and its item prefix
     * @param techLevel Draconic Evolution's own tier name, the lowest injector tier the craft needs
     * @param energy the craft's total RF
     * @param evolved the level of the {@code evolved} trait the metal grants, and so the highest
     *     fusion upgrade rung a tool made of it can stand on ({@link #requiredEvolved})
     * @param gate the DE item whose existence the row and the metal's material JSON are gated on
     * @param ingredients the injectors, in order
     */
    public record FusionMetal(String material, String techLevel, long energy, int evolved, String gate,
            List<String> ingredients) {}

    /** The catalyst every fusion metal craft puts in the crafting core; {@code ForgeweaveItems#WELDHEART}. */
    public static final String CATALYST = Forgeweave.MODID + ":weldheart";

    /**
     * The four fusion metals, lowest tier first.
     *
     * <p>Energy is half the {@link #UPGRADE_LINES} rung at the same tech level (1M against 2M, 4M
     * against 8M, 16M against 32M, 64M against 128M) -- making the metal costs less than upgrading a
     * finished tool at the same tier, which is the order a player meets the two in.
     *
     * <p>The vanilla rare climbs emerald block, diamond block, netherite ingot, nether star. The
     * parity target asks for two dragon eggs at the top tier; Forgeweave does not, because a world
     * has exactly one dragon egg and that would make the tier uncraftable rather than expensive. One
     * nether star per craft, on top of a chaotic core, is the same "this is the last thing you build"
     * signal without the dead end.
     *
     * <p>Duskweld is issue #965's inert-tier sibling, sitting under emberweld at Draconic Evolution's
     * own fourth tech level. It is the only rung whose Draconic ingredient is a core rather than an
     * ingot tag on both sides: the draconium core is what DE gates its own inert tier behind.
     */
    public static final List<FusionMetal> FUSION_METALS = List.of(
            new FusionMetal("duskweld", "draconium", 1_000_000L, 1, "draconicevolution:draconium_core",
                    List.of("draconicevolution:draconium_core", "minecraft:emerald_block",
                            "#c:ingots/draconium", "#c:ingots/draconium")),
            new FusionMetal("emberweld", "wyvern", 4_000_000L, 2, "draconicevolution:wyvern_core",
                    List.of("draconicevolution:wyvern_core", "minecraft:diamond_block",
                            "#c:ingots/draconium", "#c:ingots/draconium")),
            new FusionMetal("starweld", "draconic", 16_000_000L, 3, "draconicevolution:awakened_core",
                    List.of("draconicevolution:awakened_core", "minecraft:netherite_ingot",
                            "#c:ingots/draconium_awakened", "#c:ingots/draconium_awakened")),
            new FusionMetal("voidweld", "chaotic", 64_000_000L, 4, "draconicevolution:chaotic_core",
                    List.of("draconicevolution:chaotic_core", "minecraft:nether_star",
                            "#c:ingots/draconium_awakened", "#c:ingots/draconium_awakened")));

    /**
     * The {@code evolved} level a tool has to carry before a fusion upgrade at {@code techLevel}
     * will take it as a catalyst (issue #946) -- which is to say, the fusion metal it has to be made
     * of. One level per Draconic Evolution tech level since issue #965 gave the ladder its fourth,
     * inert rung; before that the draconium and wyvern rungs shared level 1, because emberweld was
     * the lowest fusion metal there was.
     *
     * <p>This is what turns the {@link #UPGRADE_LINES} ladder from "any assembled tool" into a path
     * that starts at the smeltery: build a fusion metal tool first, then upgrade it. Without it the
     * parity target's own rule -- only an "evolved" tool is fusion-upgradable -- is missing.
     */
    public static int requiredEvolved(String techLevel) {
        return switch (techLevel) {
            case "draconium" -> 1;
            case "wyvern" -> 2;
            case "draconic" -> 3;
            case "chaotic" -> 4;
            default -> throw new IllegalArgumentException("no Draconic Evolution tech level named " + techLevel);
        };
    }

    /**
     * The fusion metal a tool has to be made of to stand on a {@code techLevel} rung -- the material
     * name behind {@link #requiredEvolved}'s level, since the two are the same fact read two ways.
     * What {@link FusionDisplay} builds that rung's display tools out of (issue #952).
     */
    public static String fusionMetal(String techLevel) {
        return FUSION_METALS.get(requiredEvolved(techLevel) - 1).material();
    }

    /**
     * The {@code evolved} level {@code tool} carries -- 1 to 4, inert through chaotic -- or 0 for a
     * tool made of anything but a fusion metal or a Draconic Evolution core. Read off
     * {@code ForgeweaveDataComponents#TRAITS} rather than off the tool's materials
     * because by the time a stack is sitting in a crafting core its parts are gone and the trait list
     * is the only record left of what it was built from.
     *
     * <p>Highest wins when a tool carries more than one, which a head-and-handle mix of two fusion
     * metals does.
     */
    public static int evolvedLevel(ItemStack tool) {
        List<ResourceLocation> traits = tool.get(ForgeweaveDataComponents.TRAITS.get());
        if (traits == null) {
            return 0;
        }
        int level = 0;
        for (int i = 0; i < EVOLVED_IDS.size(); i++) {
            if (traits.contains(EVOLVED_IDS.get(i))) {
                level = i + 1;
            }
        }
        return level;
    }

    /**
     * The four tier markers in ladder order -- {@code forgeweave:evolving}, {@code evolved},
     * {@code evolved2}, {@code evolved3}; see {@code ForgeweaveTraits#EVOLVING}.
     *
     * <p>Issue #965 added the inert tier at the bottom under a new id rather than by renumbering the
     * three above it, so a tool sitting in a save keeps the tier it was built at: {@code evolved} is
     * still wyvern, {@code evolved2} still draconic, {@code evolved3} still chaotic. That is why the
     * ids read one rung behind the level they now stand on.
     */
    private static final List<ResourceLocation> EVOLVED_IDS = List.of(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "evolving"),
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "evolved"),
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "evolved2"),
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "evolved3"));

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
        // #956: evolved gear is also a Draconic Evolution module host. Same guard, same reason -- the
        // class behind this call names com.brandon3055 types and cannot link without the mod.
        DraconicModuleHost.register(modEventBus);

        // #952: the catalyst an upgrade row hands JEI is a custom ingredient (it matches the tag but
        // draws assembled tools), and NeoForge requires every custom ingredient's type to be
        // registered. Nothing serializes this one -- the recipe's own codec writes the raw tag it
        // wraps -- but an unregistered type would be a crash waiting for the first thing that tried.
        DeferredRegister<IngredientType<?>> ingredientTypes =
                DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES, Forgeweave.MODID);
        ingredientTypes.register(CATALYST_INGREDIENT_NAME, () -> FusionUpgradeRecipe.CatalystDisplay.TYPE);
        ingredientTypes.register(modEventBus);
    }


    private ForgeweaveDraconicCompat() {}
}
