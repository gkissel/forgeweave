package dev.gkissel.forgeweave.compat.occultism;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.IEventBus;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Modifier;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
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
 * one with no Occultism, and must stay loadable there. Every registration lives inside
 * {@link SpiritBindingRitual#register}, reached only from inside the guard, so the class that names
 * {@code com.klikli_dev} types is never linked on an install without them.
 *
 * <p>Same soft-dependency shape as {@code jade}, {@code kubejs}, {@code jei} and Draconic Evolution:
 * a compileOnly dependency, an {@code optional} entry in {@code neoforge.mods.toml}, and
 * {@code neoforge:conditions} on every recipe JSON so a Forgeweave-only datapack drops these rows.
 *
 * <p><b>Licensing.</b> Occultism is MIT (its own {@code LICENSE}, pinned below), so an API dependency
 * carries no question at all -- but nothing here is copied from it either, and no Forgeweave asset
 * derives from its art. Its {@code THIRD_PARTY_NOTICES.md} puts most textures under the same MIT and
 * a named handful under CC BY, which is the second reason none of its art is touched. So no
 * {@code NOTICE.md} row exists for any file in this package.
 */
public final class ForgeweaveOccultismCompat {

    /** Occultism's mod id -- the {@code ModList} guard and every recipe condition key on it. */
    public static final String MODID = "occultism";

    /**
     * What may be right-clicked onto a Golden Sacrificial Bowl to start a spirit binding ritual:
     * every item either station assembles ({@code ToolAssemblyRecipes.ENTRIES}, filled in by
     * {@code ForgeweaveItemTagsProvider}), so a new tool family joins the ladder with no code change.
     *
     * <p>Deliberately one broad tag rather than a per-ritual tag, and deliberately not shared with
     * {@code ForgeweaveDraconicCompat#FUSION_UPGRADABLE}: which shapes a given ritual actually accepts
     * is already decided, once, by {@link dev.gkissel.forgeweave.modifier.Modifier}'s own gates, and
     * {@link SpiritBindingRitual} reads them. A second copy of that in tag form would be the drift
     * {@code jei.ModifierApplicationCategory}'s issue #764 bug was made of.
     */
    public static final TagKey<Item> RITUAL_BINDABLE = TagKey.create(Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "ritual_bindable"));

    /** The Occultism item every binding ritual consumes one of: the spirit itself, in gem form. */
    public static final String SPIRIT_GEM = MODID + ":spirit_attuned_gem";

    /** The Occultism item a binding ritual consumes {@link Ritual#essence} of, scaled by pentacle rank. */
    public static final String OTHERWORLD_ESSENCE = MODID + ":otherworld_essence";

    /**
     * One spirit binding ritual: the modifier it grants, the level it grants it at, the Occultism
     * pentacle the player draws, and how much otherworld essence that pentacle's rank asks for.
     *
     * @param modifier the Forgeweave modifier id the ritual grants, fully qualified
     * @param level the level it lands at, in {@link dev.gkissel.forgeweave.modifier.ModifierEntry}'s
     *     application units -- at or under the modifier's own shipped {@code max_level}
     * @param pentacle the Occultism pentacle id the ritual is performed in
     * @param essence how many {@link #OTHERWORLD_ESSENCE} the ritual consumes beside one
     *     {@link #SPIRIT_GEM}, one per rank of {@code pentacle}
     */
    public record Ritual(String modifier, int level, String pentacle, int essence) {

        /** The modifier's own path -- the recipe file's name and the last segment of the factory id. */
        public String name() {
            return ResourceLocation.parse(modifier).getPath();
        }

        /** This ritual's {@code occultism:ritual_factories} entry, i.e. the JSON's {@code ritual_type}. */
        public String factoryName() {
            return "bind_" + name();
        }

        /** The ritual's ingredient list, one entry per sacrificial bowl the ritual needs. */
        public List<String> ingredients() {
            List<String> ingredients = new ArrayList<>();
            ingredients.add(SPIRIT_GEM);
            for (int i = 0; i < essence; i++) {
                ingredients.add(OTHERWORLD_ESSENCE);
            }
            return List.copyOf(ingredients);
        }
    }

    /**
     * The shipped roster: four rituals, one modifier each (issue #997).
     *
     * <p>Deliberately not the Draconic ladder's shape. A fusion upgrade is a tier climb -- eight
     * lines by four tech levels, each rung asking for the next Draconic core -- because Draconic
     * Evolution's economy <em>is</em> a tier climb and its multiblock is a thing a player rebuilds
     * four times. Occultism's is not. A ritual is drawn once, on the ground, for one bound spirit,
     * and what escalates in Occultism is the <em>rank of the spirit</em> being contacted, not a
     * machine tier. So the ladder here is four rituals, one per modifier, climbing Occultism's own
     * four pentacle ranks -- foliot, djinni, afrit, marid -- with one grant each and no second rung.
     *
     * <p>Every modifier is one Forgeweave already ships and one a bound spirit reads as: a spirit
     * that fetches what the tool breaks (magnetic pull, foliot -- the rank Occultism itself uses for
     * item transport), one that keeps the tool whole (mending moss, djinni), one that feeds on what
     * it kills (necrotic, afrit), and one that follows the tool back out of a death drop (soulbound,
     * marid -- the rank Occultism reserves for its own endgame crafts).
     *
     * <p>None of the four is gated on {@link ForgeweaveConfig#OCCULTISM_RITUALS}: with the toggle off
     * the ritual cannot be performed, but a level already granted keeps <em>working</em>, not merely
     * its id, because all four are reachable at the Tool Station too. That is the answer to the
     * issue's "which of the two is it" for every modifier this ladder offers -- all four are the
     * still-working case, and none is the inert one.
     *
     * <p>Levels, and why they are small. Because a binding spends the modifier slots its entry
     * occupies ({@link SpiritBindingRitual}'s own note), every level here has to fit the
     * {@value dev.gkissel.forgeweave.modifier.ForgeweaveModifiers#DEFAULT_SLOTS} a freshly assembled
     * tool has, or the ritual would refuse the only tool a player is likely to bring it. So magnetic
     * pull and soulbound land at their shipped caps of 1 (soulbound costing nothing, since its entry
     * occupies no slot at all -- {@code ForgeweaveModifiers#SOULBOUND}), while mending moss stops at
     * 2 of its cap of 3 and necrotic at 3 of its cap of 10. The station route still reaches past both,
     * which is the point: a ritual is a shortcut past the reagent grind, not a way past the cap.
     *
     * <p>Spending all four on one tool costs 6 slots against a budget of 3, so a player picks two or
     * buys the room with {@code extra_slot}. That is a real choice rather than an accident of the
     * numbers, and it is the price the ritual path pays for asking no reagents.
     */
    public static final List<Ritual> RITUALS = List.of(
            ritual("magnetic_pull", 1, "craft_foliot", 1),
            ritual("mending_moss", 2, "craft_djinni", 2),
            ritual("necrotic", 3, "craft_afrit", 3),
            ritual("soulbound", 1, "craft_marid", 4));

    private static Ritual ritual(String modifier, int level, String pentacle, int essence) {
        return new Ritual(Forgeweave.MODID + ":" + modifier, level, MODID + ":" + pentacle, essence);
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
     * The bound tool, or empty when a ritual granting {@code modifier} at {@code level} has nothing
     * to give {@code tool}: it is not an assembled Forgeweave tool or the toggle is off
     * ({@link #acceptsRitualTool}), the modifier id is not registered, the modifier refuses the
     * tool's shape ({@link ModifierApplication#acceptsToolShape}), the tool carries something the
     * modifier cannot sit beside, the tool already sits at or above {@code level}, or the tool has no
     * modifier slots left to spend.
     *
     * <p>The whole of what a binding decides, deliberately on this side of the package's split rather
     * than inside {@link SpiritBindingRitual}, which is a one-line delegation to it. The ritual class
     * is only loadable with Occultism installed and Occultism is not loadable on this repo's test
     * classpath (see build.gradle's comment on the dependency), so a decision left over there could
     * not be executed by anything -- while everything here runs under both {@code ./gradlew test} and
     * {@code runGameTestServer}.
     *
     * <p>Spends the slots the entry occupies, unlike a Draconic fusion upgrade. See
     * {@link ModifierApplication#applyLevelSpendingSlots} for why the two differ.
     */
    public static Optional<ItemStack> bind(@Nullable HolderLookup.Provider registries,
            ItemStack tool, ResourceLocation modifier, int level) {
        if (!acceptsRitualTool(tool)) {
            return Optional.empty();
        }
        Modifier behavior = ForgeweaveModifiers.get(modifier);
        if (behavior == null || !ModifierApplication.acceptsToolShape(registries, behavior, tool)) {
            return Optional.empty();
        }
        ItemStack one = tool.copy();
        one.setCount(1);
        ItemStack bound = ModifierApplication.applyLevelSpendingSlots(one, modifier, level).output();
        return bound.isEmpty() ? Optional.empty() : Optional.of(bound);
    }

    /**
     * The lowest crusher spirit rank that will grind an ore at {@code tier} -- Occultism's own
     * {@code min_tier} field on an {@code occultism:crushing} row, whose values are its four spirit
     * ranks: {@code 1} foliot, {@code 2} djinni, {@code 3} afrit, {@code 4} marid (its
     * {@code OccultismServerConfig} crusher settings, and {@code CrusherJob} compares
     * {@code minTier <= currentTier}).
     *
     * <p>Written down as a table rather than derived at read time, because the mapping is a design
     * choice and not arithmetic. What it says is that a foliot grinds only what a diamond pickaxe
     * already reaches, a djinni handles the netherite rung, an afrit the one above it, and only a
     * marid touches the top two. Without it a foliot would chew through resonite, which is the
     * failure mode that makes the whole Track B ladder pointless.
     */
    public static int crusherTier(TrackBOre.Tier tier) {
        return switch (tier) {
            case STONE, DIAMOND -> 1;
            case NETHERITE -> 2;
            case HARDCINDER -> 3;
            case WARSPAR, RESONITE -> 4;
        };
    }

    /**
     * How often a mining spirit returns an ore at {@code tier}, as the {@code weight} inside an
     * {@code occultism:miner} row's result -- one entry's share of the weighted pick Occultism's
     * Dimensional Mineshaft runs across every miner row the held spirit matches
     * ({@code DimensionalMineshaftBlockEntity#mine}, vanilla {@code WeightedRandom} over the raw
     * ints, no normalising).
     *
     * <p>The ladder is 300 / 150 / 80 / 40 / 15 / 5, strictly decreasing rung by rung, so a higher
     * rung is never more common than a lower one ({@code OccultismRecipeTest} pins exactly that).
     *
     * <p>The numbers are picked in Occultism's own units rather than on a scale of their own, since
     * they are drawn from the same pool as its rows: it gives coal 1000, iron 750, diamond 218,
     * emerald 156 and titanium 10, and its own iesnium 100. So fulmenite at 150 sits just under
     * emerald, the netherite rung at 80 under iesnium, and resonite at 5 below titanium -- the
     * rarest thing a mining spirit can hand back. The top three rungs are the low end on purpose: a
     * spirit that pulled resonite as often as fulmenite would hand a player the top of the ladder
     * without ever making them climb it.
     */
    public static int minerWeight(TrackBOre.Tier tier) {
        return switch (tier) {
            case STONE -> 300;
            case DIAMOND -> 150;
            case NETHERITE -> 80;
            case HARDCINDER -> 40;
            case WARSPAR -> 15;
            case RESONITE -> 5;
        };
    }

    /**
     * Registers the four {@code occultism:ritual_factories} entries. Called from {@link Forgeweave}'s
     * constructor only when Occultism is present, which is what keeps {@link SpiritBindingRitual} --
     * and with it every {@code com.klikli_dev} type it names -- off a Forgeweave-only install's
     * classloader. Nothing is held in a static field here for the same reason.
     */
    public static void register(IEventBus modEventBus) {
        SpiritBindingRitual.register(modEventBus);
    }

    private ForgeweaveOccultismCompat() {}
}
