package dev.gkissel.forgeweave.compat;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import net.neoforged.fml.ModList;

import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.compat.mysticalagriculture.MysticalAugments;
import dev.gkissel.forgeweave.compat.mysticalagriculture.MysticalItems;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.MeleeWeaponItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.AoeHarvest;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The registration factory for tool and armour items whose <b>class</b> depends on which other mods
 * are installed (issue #999, docs/SCOPE.md D-M8-20). {@code ForgeweaveItems} builds its tools and
 * armour through here instead of calling a constructor directly, so the base classes stay clean and
 * name no other mod's type -- which is what lets Mystical Agriculture's {@code ITinkerable} be
 * implemented at all without a hard dependency.
 *
 * <p>It lives in the parent {@code compat} package rather than under one mod's own, because #999 and
 * <a href="https://github.com/gkissel/forgeweave/issues/993">#993</a> (Mekanism module containers)
 * both want this seam and were explicitly told to share one rather than build two. #999 got here
 * first, so this is the one: a second interface to implement adds a branch to each method below and a
 * sibling of {@code MysticalItems} for the mod that needs it, and nothing else moves.
 *
 * <p>The overloads mirror the {@code ToolItem} and {@code MeleeWeaponItem} constructors the call sites
 * already used, one for one, so adopting the factory was a rename at each site rather than a rewrite
 * of its arguments. They all fold into {@link #tool} and {@link #meleeWeapon}, the same funnel the
 * constructors themselves fold into.
 *
 * <h2>Why the branch is here and the {@code new} is not</h2>
 *
 * <p>{@link #MYSTICAL_AGRICULTURE} is read once, at class load, from {@code ModList} -- a load-time
 * fact that is available while items register, unlike a config value. The branch then calls into
 * {@code MysticalItems}, which is the class that names {@code ITinkerable}. Constructing the subclass
 * inline here would not do: verifying a method that returns {@code new TinkerableToolItem(...)} as a
 * {@code ToolItem} makes the JVM load the subclass, and loading it loads {@code ITinkerable}, which is
 * absent on a Forgeweave-only install. The indirection is the whole point.
 *
 * <h2>Why the config toggle is deliberately not read here</h2>
 *
 * <p>{@code compat.mysticalAgricultureAugments} lives on a {@code SERVER} spec, and a server config is
 * not loaded when registries freeze (#1024), so a registration-time read would see the default rather
 * than the pack's choice. The toggle is read where the integration answers instead --
 * {@code MysticalItems} reports zero augment slots and tier zero while it is off -- which leaves the
 * feature inert, needs no restart, and never touches stored state. That is D-M8-5's own contract, and
 * it is a deliberate departure from #999's "this toggle takes effect at registration, a change needs a
 * restart" framing; the PR records why.
 */
public final class CompatItems {

    /**
     * Whether Mystical Agriculture is installed, read once at class load. Its absence is the only
     * reason a Forgeweave tool is not tinkerable, and installing or removing a mod needs a restart
     * whatever Forgeweave does.
     */
    private static final boolean MYSTICAL_AGRICULTURE = ModList.get().isLoaded(MysticalAugments.MODID);

    private CompatItems() {}

    /**
     * A tool: the compat subclass where Mystical Agriculture is installed, a plain {@code ToolItem}
     * where it is not. Arguments are {@code ToolItem}'s own funnel constructor's.
     */
    public static ToolItem tool(Item.Properties properties, List<TagKey<Block>> mineableBlocks,
            float attackSpeed, float damagePotential, float miningSpeedModifier, float damageCutoff,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate, AoeHarvest.Shape aoeShape) {
        if (MYSTICAL_AGRICULTURE) {
            return MysticalItems.tool(properties, mineableBlocks, attackSpeed, damagePotential,
                    miningSpeedModifier, damageCutoff, weapon, innate, aoeShape);
        }
        return new ToolItem(properties, mineableBlocks, attackSpeed, damagePotential, miningSpeedModifier,
                damageCutoff, weapon, innate, aoeShape);
    }

    /** {@link #tool} with raw stats and the default damage cutoff -- the pickaxe and shovel's form. */
    public static ToolItem tool(Item.Properties properties, List<TagKey<Block>> mineableBlocks,
            float attackSpeed, float damagePotential, float miningSpeedModifier, boolean weapon,
            @Nullable ForgeweaveInnates.Innate innate, AoeHarvest.Shape aoeShape) {
        return tool(properties, mineableBlocks, attackSpeed, damagePotential, miningSpeedModifier,
                ToolConstants.DEFAULT_DAMAGE_CUTOFF, weapon, innate, aoeShape);
    }

    /** {@link #tool} from a {@code ToolConstants} entry and one mineable tag, with an AoE shape. */
    public static ToolItem tool(Item.Properties properties, ToolConstants.Entry constants,
            TagKey<Block> mineableBlocks, boolean weapon, @Nullable ForgeweaveInnates.Innate innate,
            AoeHarvest.Shape aoeShape) {
        return tool(properties, List.of(mineableBlocks), constants.attackSpeed(), constants.damagePotential(),
                constants.miningSpeedModifier(), constants.damageCutoff(), weapon, innate, aoeShape);
    }

    /** {@link #tool} from a {@code ToolConstants} entry and one mineable tag, with no AoE shape. */
    public static ToolItem tool(Item.Properties properties, ToolConstants.Entry constants,
            TagKey<Block> mineableBlocks, boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        return tool(properties, constants, mineableBlocks, weapon, innate, AoeHarvest.Shape.NONE);
    }

    /** A sword-family weapon, the same split as {@link #tool}. */
    public static ToolItem meleeWeapon(Item.Properties properties, ToolConstants.Entry constants,
            List<TagKey<Block>> mineableBlocks, boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        if (MYSTICAL_AGRICULTURE) {
            return MysticalItems.meleeWeapon(properties, constants, mineableBlocks, weapon, innate);
        }
        return new MeleeWeaponItem(properties, constants, mineableBlocks, weapon, innate);
    }

    /** {@link #meleeWeapon} for a weapon effective on a single mineable tag. */
    public static ToolItem meleeWeapon(Item.Properties properties, ToolConstants.Entry constants,
            TagKey<Block> mineableBlocks, boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        return meleeWeapon(properties, constants, List.of(mineableBlocks), weapon, innate);
    }

    /** An armour piece, the same split as {@link #tool}. */
    public static ArmorPieceItem armor(ArmorItem.Type type, boolean heavy, Item.Properties properties) {
        if (MYSTICAL_AGRICULTURE) {
            return MysticalItems.armor(type, heavy, properties);
        }
        return new ArmorPieceItem(type, heavy, properties);
    }

    /**
     * Whether the factory is currently producing compat subclasses. Exposed for the GameTest that pins
     * both paths of the branch above: {@code runGameTestServer} runs with no Mystical Agriculture
     * present, so it asserts the plain half directly and asserts that this flag is what decided it.
     */
    public static boolean tinkerableItemsRegistered() {
        return MYSTICAL_AGRICULTURE;
    }
}
