package dev.gkissel.forgeweave.compat.occultism;

import java.util.List;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Forgeweave's Occultism integration (issue #997, docs/SCOPE.md M8, D-M8-18): the seam between the
 * mod proper and {@link SpiritBindingRitual}, which is the only class here that names a
 * {@code com.klikli_dev} type.
 *
 * <p>Everything in this class is Occultism-free on purpose, the same split
 * {@code ForgeweaveDraconicCompat} makes against {@code FusionUpgradeRecipe}. {@link Forgeweave}
 * calls {@link #register} behind a {@code ModList.get().isLoaded(MODID)} check, while the datagen and
 * tag providers read {@link #RITUAL_BINDABLE}, {@link #RITUALS}, {@link #crusherTier} and
 * {@link #minerWeight} unconditionally -- so this class is classloaded on every install, including
 * one with no Occultism, and must stay loadable there. The serializer is created inside
 * {@link #register} rather than held in a static field so that {@link SpiritBindingRitual} is only
 * ever reached from inside the guard.
 *
 * <p>Same soft-dependency shape as {@code jade}, {@code kubejs}, {@code jei} and Draconic Evolution:
 * a compileOnly dependency, an {@code optional} entry in {@code neoforge.mods.toml}, and
 * {@code neoforge:conditions} on every recipe JSON so a Forgeweave-only datapack drops these rows.
 */
public final class ForgeweaveOccultismCompat {

    /** Occultism's mod id -- the {@code ModList} guard and every recipe condition key on it. */
    public static final String MODID = "occultism";

    /** The registered name of {@link SpiritBindingRitual.Serializer}, i.e. the recipes' {@code type}. */
    public static final String RITUAL_SERIALIZER_NAME = "occultism_spirit_binding";

    /**
     * What may be laid in a pentacle as a spirit binding ritual's item: every item either station
     * assembles ({@code ToolAssemblyRecipes.ENTRIES}, filled in by {@code ForgeweaveItemTagsProvider}),
     * so a new tool family joins the ladder with no code change.
     *
     * <p>Deliberately one broad tag rather than a per-ritual tag, and deliberately not shared with
     * {@code ForgeweaveDraconicCompat#FUSION_UPGRADABLE}: which shapes a given ritual actually accepts
     * is already decided, once, by {@link dev.gkissel.forgeweave.modifier.Modifier}'s own gates, and
     * {@link SpiritBindingRitual} reads them. A second copy of that in tag form would be the drift
     * {@code jei.ModifierApplicationCategory}'s issue #764 bug was made of.
     */
    public static final TagKey<Item> RITUAL_BINDABLE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "ritual_bindable"));

    /**
     * One spirit binding ritual: the modifier it grants, the level it grants it at, and the pentacle
     * Occultism asks the player to draw.
     *
     * @param modifier the Forgeweave modifier id the ritual grants, fully qualified
     * @param level the level it lands at, in {@link dev.gkissel.forgeweave.modifier.ModifierEntry}'s
     *     application units -- at or under the modifier's own shipped {@code max_level}
     * @param pentacle the Occultism pentacle id the ritual is performed in
     * @param sacrifice the item consumed beside the tool, one per ritual
     */
    public record Ritual(String modifier, int level, String pentacle, String sacrifice) {

        /** The recipe file's name, and the last segment of its id. */
        public String recipeName() {
            return ResourceLocation.parse(modifier).getPath();
        }
    }

    /**
     * The shipped roster: four rituals, one modifier each (issue #997).
     *
     * <p>Deliberately not the Draconic ladder's shape. A fusion upgrade is a tier climb -- eight
     * lines by four tech levels, each rung asking for the next Draconic core -- because Draconic
     * Evolution's own economy is a tier climb and its multiblock is something a player upgrades
     * four times. Occultism's is not: a ritual is drawn once, on the ground, for one bound spirit,
     * and nothing about a pentacle gets a second tier. So each entry here is one ritual granting one
     * modifier outright, and the roster is four rituals rather than thirty-two rows.
     *
     * <p>Every modifier is one Forgeweave already ships and one a bound spirit reads as: a spirit
     * that follows the tool back from death (soulbound), one that fetches what the tool breaks
     * (magnetic pull), one that keeps it whole (mending moss), and one that feeds on what it kills
     * (necrotic). None is a modifier the ritual invents, and none is gated on
     * {@link ForgeweaveConfig#OCCULTISM_RITUALS}: with the toggle off the ritual cannot be performed,
     * but a level already granted keeps working, because every one of the four is reachable at the
     * Tool Station too.
     *
     * <p>Levels: soulbound, magnetic pull and mending moss land at their shipped caps (1, 1 and 3 --
     * {@code data/forgeweave/forgeweave/modifier_recipe/}), since none of the three has a middle to
     * stop at. Necrotic caps at 10 and the ritual grants 5, half of it, so the station route still
     * goes somewhere a ritual cannot.
     */
    public static final List<Ritual> RITUALS = List.of(
            // The canonical Occultism act: a spirit bound into the tool, which follows it out of a
            // death drop. Soulbound occupies no slot of its own (ForgeweaveModifiers#SOULBOUND), so
            // this is the one ritual a tool with a full slot budget can still take.
            ritual("soulbound", 1, "bind_foliot", "minecraft:nether_star"),
            // A transport spirit, the one Occultism already summons to move items: what the tool
            // breaks comes to the player instead of the floor.
            ritual("magnetic_pull", 1, "summon_foliot_transport_items", "minecraft:iron_block"),
            // A spirit kept on to mend the tool, at mending moss's own cap of 3.
            ritual("mending_moss", 3, "bind_djinni", "minecraft:moss_block"),
            // The sacrifice ritual: lifesteal, at half necrotic's reachable cap of 10.
            ritual("necrotic", 5, "summon_afrit_crusher", "minecraft:wither_skeleton_skull"));

    private static Ritual ritual(String modifier, int level, String pentacle, String sacrifice) {
        return new Ritual(Forgeweave.MODID + ":" + modifier, level,
                MODID + ":" + pentacle, sacrifice);
    }

    /**
     * Whether {@code tool} is something a spirit binding ritual will take at all: an assembled
     * Forgeweave tool, with the {@code compat.occultismRituals} toggle on (D-M8-5).
     *
     * <p>Extracted out of {@link SpiritBindingRitual} for the reason
     * {@code ForgeweaveDraconicCompat#acceptsFusionCatalyst} documents: the ritual class is only
     * loadable with Occultism installed, so its half cannot be reached by {@code runGameTestServer},
     * while this method can. The ritual adds the rest -- the modifier's own gates, the slot budget,
     * and whether the binding would change anything.
     *
     * <p>Off is inert, never destructive: a tool refused here keeps every component it has, rituals
     * already performed on it included, and is accepted again the moment the toggle returns.
     */
    public static boolean acceptsRitualTool(ItemStack tool) {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.OCCULTISM_RITUALS)
                && ToolAssemblyRecipes.isAssembled(tool);
    }

    /**
     * The lowest crusher spirit rank that will grind an ore at {@code tier} -- Occultism's own
     * {@code min_tier} field, whose four values are its four spirit ranks: {@code 0} foliot,
     * {@code 1} djinni, {@code 2} afrit, {@code 3} marid.
     *
     * <p>Written down as a table rather than derived at read time, because the mapping is a design
     * choice and not arithmetic: what it says is that a foliot grinds the ores a netherite pickaxe
     * already reaches, a djinni handles the rung above netherite, and only a marid touches the top
     * two. Without it a foliot would chew through resonite, which is the failure mode that makes the
     * whole Track B ladder pointless.
     */
    public static int crusherTier(TrackBOre.Tier tier) {
        return switch (tier) {
            case STONE, DIAMOND, NETHERITE -> 0;
            case HARDCINDER -> 1;
            case WARSPAR -> 2;
            case RESONITE -> 3;
        };
    }

    /**
     * How often a mining spirit returns an ore at {@code tier}, as Occultism's {@code miner}
     * {@code weight} -- one entry's share of the weighted pick across every miner row the spirit's
     * own ore list holds.
     *
     * <p>The ladder is 100 / 60 / 30 / 12 / 5 / 2, strictly decreasing rung by rung, so a higher
     * rung is never more common than a lower one ({@code OccultismRecipeTest} pins exactly that).
     * The top three rungs are the low end on purpose: a mining spirit that pulled resonite as often
     * as fulmenite would hand a player the top of the ladder without ever making them climb it.
     */
    public static int minerWeight(TrackBOre.Tier tier) {
        return switch (tier) {
            case STONE -> 100;
            case DIAMOND -> 60;
            case NETHERITE -> 30;
            case HARDCINDER -> 12;
            case WARSPAR -> 5;
            case RESONITE -> 2;
        };
    }

    /**
     * Registers {@link SpiritBindingRitual.Serializer}. Called from {@link Forgeweave}'s constructor
     * only when Occultism is present, which is what keeps the ritual class -- and with it every
     * {@code com.klikli_dev} type it names -- off a Forgeweave-only install's classloader.
     */
    public static void register(IEventBus modEventBus) {
        DeferredRegister<RecipeSerializer<?>> serializers =
                DeferredRegister.create(Registries.RECIPE_SERIALIZER, Forgeweave.MODID);
        serializers.register(RITUAL_SERIALIZER_NAME, () -> SpiritBindingRitual.Serializer.INSTANCE);
        serializers.register(modEventBus);
    }

    private ForgeweaveOccultismCompat() {}
}
