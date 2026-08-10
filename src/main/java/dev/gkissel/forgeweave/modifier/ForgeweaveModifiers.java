package dev.gkissel.forgeweave.modifier;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

import net.neoforged.neoforge.event.level.BlockDropsEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * The modifier ids Forgeweave ships, the behavior behind each, and the slot accounting the Tool
 * Station and the tooltips read. Same shape as {@code trait.ForgeweaveTraits}, which ADR-0004 names
 * as the precedent modifiers follow.
 *
 * <h2>Registry</h2>
 *
 * <p>A plain immutable map, not a Minecraft registry: modifier behavior is Java and only ever added
 * by a mod update (ADR-0004), so there is nothing a registry event could contribute. An id on a tool
 * that this version doesn't implement is <b>logged once and kept</b> -- see {@link #of}.
 *
 * <h2>Slots</h2>
 *
 * <p>Upstream 1.12's {@code ToolCore#DEFAULT_MODIFIERS = 3}. One distinct modifier occupies one
 * slot for its lifetime; leveling it up stays inside that slot, so a tool can carry at most three
 * distinct modifiers however far each is leveled (docs/SCOPE.md M2 acceptance test 5). That is the
 * one place Forgeweave deliberately simplifies upstream, whose {@code MultiAspect} charges a fresh
 * slot per level -- see the PR for issue #105.
 *
 * <p>{@link #freeSlots} is {@value #DEFAULT_SLOTS} plus whatever {@link Modifier#bonusSlots} grants,
 * minus the slots in use, so issue #107's extra-slot items raise the cap without this class changing.
 */
public final class ForgeweaveModifiers {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Upstream {@code ToolCore#DEFAULT_MODIFIERS}: every assembled tool starts with three. */
    public static final int DEFAULT_SLOTS = 3;

    /** Ids already reported as unknown, so an old save logs once rather than every tooltip frame. */
    private static final Set<ResourceLocation> WARNED_UNKNOWN = ConcurrentHashMap.newKeySet();

    /** Upstream {@code ModHaste#applyHarvestBoost}'s two diminishing-returns thresholds. */
    private static final float HASTE_STEP_1 = 15.0F;
    private static final float HASTE_STEP_2 = 25.0F;
    /** Upstream {@code TinkerModifiers}: {@code new ModHaste(50)} -- 50 redstone per level. */
    private static final int HASTE_REDSTONE_PER_LEVEL = 50;

    /**
     * Redstone. Upstream {@code ModHaste}, ported whole:
     *
     * <ul>
     *   <li><b>Mining speed</b> ({@code applyHarvestBoost}): one diminishing-returns step per
     *       redstone -- {@code +0.15} tapering to {@code +0.10} below speed 15, {@code +0.10}
     *       tapering to {@code +0.05} below 25, a flat {@code +0.05} above -- plus a flat
     *       {@code +0.5} per completed level on top, undiminished.
     *   <li><b>Attack speed</b> ({@code getSpeedBonus}): {@code 0.2f * current / max} on the tool's
     *       attack speed multiplier -- {@code +0.2} per level, {@code +0.004} per redstone below one,
     *       which is upstream's own comment on that line. Upstream gates this on
     *       {@code Category.WEAPON}, which of Forgeweave's three tools only the hatchet has, so
     *       {@code ToolItem} applies it there only.
     * </ul>
     *
     * <p>Upstream's bow draw-speed branch has no counterpart: Forgeweave ships no launcher (M5).
     */
    public static final Modifier HASTE = new Modifier() {
        @Override
        public int unitsPerLevel() {
            return HASTE_REDSTONE_PER_LEVEL;
        }

        @Override
        public float miningSpeed(int level, float miningSpeed) {
            float speed = miningSpeed;
            for (int count = level; count > 0; count--) {
                if (speed <= HASTE_STEP_1) {
                    speed += 0.15F - 0.05F * speed / HASTE_STEP_1;
                } else if (speed <= HASTE_STEP_2) {
                    speed += 0.1F - 0.05F * (speed - HASTE_STEP_1) / (HASTE_STEP_2 - HASTE_STEP_1);
                } else {
                    speed += 0.05F;
                }
            }
            return speed + level / HASTE_REDSTONE_PER_LEVEL * 0.5F;
        }

        @Override
        public float attackSpeedMultiplier(int level) {
            return 1.0F + 0.2F * level / HASTE_REDSTONE_PER_LEVEL;
        }
    };

    // #108 batch: modern-vanilla modifiers (issue #108). Forgeweave originals -- no clone to cite, so
    // every constant below records this PR's own numbers instead of an upstream one.

    /** Magma cream. Auto-smelts every block this tool mines ({@link #onBlockDrops}). Single level. */
    public static final Modifier SEARING = new Modifier() {
        @Override
        public boolean autoSmelt(int level) {
            return true;
        }
    };

    /**
     * Ender pearl. Sends this tool's block drops straight into the breaking player's inventory
     * instead of the ground ({@link #onBlockDrops}). Single level; id kept distinct from issue #102's
     * item-pulling {@code magnetic} trait (see {@link Modifier#magnetic} javadoc).
     */
    public static final Modifier MAGNETIC_PULL = new Modifier() {
        @Override
        public boolean magnetic(int level) {
            return true;
        }
    };

    /**
     * Turtle scute. Cancels the vanilla submerged mining penalty by restoring
     * {@code player.submerged_mining_speed} to its unpenalized 1.0x (see {@link Modifier#submergedMiningSpeedBonus}).
     * Single level.
     */
    public static final Modifier AQUADYNAMIC = new Modifier() {
        @Override
        public float submergedMiningSpeedBonus(int level) {
            return 0.8F;
        }
    };

    /** Echo shard. +50% block-drop experience per level, our chosen number, capped at 3 levels. */
    private static final float RESONANT_XP_PER_LEVEL = 0.5F;
    public static final Modifier RESONANT = new Modifier() {
        @Override
        public float bonusExperienceFraction(int level) {
            return RESONANT_XP_PER_LEVEL * level;
        }
    };

    /** Amethyst shard. +1 block interaction range per level, our chosen number, capped at 2 levels. */
    public static final Modifier FAR_REACH = new Modifier() {
        @Override
        public float blockInteractionRangeBonus(int level) {
            return level;
        }
    };

    private static final Map<ResourceLocation, Modifier> REGISTRY = Map.of(
            id("haste"), HASTE,
            id("searing"), SEARING,
            id("magnetic_pull"), MAGNETIC_PULL,
            id("aquadynamic"), AQUADYNAMIC,
            id("resonant"), RESONANT,
            id("far_reach"), FAR_REACH);

    /**
     * The behavior for {@code id}, or {@code null} if this version doesn't implement it.
     *
     * <p><b>Unknown ids are kept, not dropped</b> (issue #105, favoring save safety): the entry stays
     * on the tool and simply contributes no effect and no slot bonus, so removing the datapack that a
     * modifier came from -- or loading a save written by a newer Forgeweave -- costs the player
     * nothing once the id is available again. Dropping would silently and permanently delete
     * upgrades the player paid for. The cost is that a tool can sit at its slot cap with a modifier
     * that currently does nothing, which is visible in the tooltip and reversible.
     */
    @Nullable
    public static Modifier get(ResourceLocation id) {
        Modifier modifier = REGISTRY.get(id);
        if (modifier == null && WARNED_UNKNOWN.add(id)) {
            LOGGER.warn("Unknown modifier '{}' on a tool; keeping it as inert data so it works again if a "
                    + "later version implements it (ADR-0004).", id);
        }
        return modifier;
    }

    /** The modifiers on a tool, in application order; empty for anything unmodified. */
    public static List<ModifierEntry> of(ItemStack stack) {
        return stack.getOrDefault(ForgeweaveDataComponents.MODIFIERS.get(), List.of());
    }

    /** The entry for {@code id} on this tool, or {@code null} if it doesn't have that modifier. */
    @Nullable
    public static ModifierEntry entry(ItemStack stack, ResourceLocation id) {
        for (ModifierEntry entry : of(stack)) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    /**
     * Modifier slots still available on this tool: the three every tool starts with, plus what its
     * modifiers grant, minus the one each distinct modifier occupies.
     */
    public static int freeSlots(ItemStack stack) {
        List<ModifierEntry> entries = of(stack);
        int bonus = 0;
        for (ModifierEntry entry : entries) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                bonus += modifier.bonusSlots(entry.level());
            }
        }
        return DEFAULT_SLOTS + bonus - entries.size();
    }

    /**
     * The level a player sees for {@code level} application units of {@code id}: 1 from the first
     * unit, stepping up every {@link Modifier#unitsPerLevel}, exactly where upstream's
     * {@code MultiAspect} steps ({@code current > countPerLevel * level} starts the next one).
     */
    public static int displayLevel(ResourceLocation id, int level) {
        Modifier modifier = get(id);
        int perLevel = modifier == null ? 1 : Math.max(1, modifier.unitsPerLevel());
        return 1 + (Math.max(1, level) - 1) / perLevel;
    }

    /** How many application units the current displayed level ends at, for the {@code 51/100} readout. */
    public static int unitsForDisplayLevel(ResourceLocation id, int level) {
        Modifier modifier = get(id);
        int perLevel = modifier == null ? 1 : Math.max(1, modifier.unitsPerLevel());
        return displayLevel(id, level) * perLevel;
    }

    /**
     * The tool's stats with its modifiers applied, or {@code null} if it has no stat block at all.
     * The stored {@code forgeweave:tool_stats} component stays the untouched materials-derived base
     * -- modifiers are re-derived from it every time rather than folded into it, so applying the
     * fourth redstone can never compound onto the effect of the first three.
     */
    @Nullable
    public static ToolStats.Stats effectiveStats(ItemStack stack) {
        ToolStats.Stats base = stack.get(ForgeweaveDataComponents.TOOL_STATS.get());
        if (base == null) {
            return null;
        }
        float miningSpeed = base.miningSpeed();
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                miningSpeed = modifier.miningSpeed(entry.level(), miningSpeed);
            }
        }
        return miningSpeed == base.miningSpeed()
                ? base
                : new ToolStats.Stats(base.durability(), miningSpeed, base.attackDamage());
    }

    /** Combined attack-speed multiplier of the tool's modifiers; 1 when nothing touches it. */
    public static float attackSpeedMultiplier(ItemStack stack) {
        float multiplier = 1.0F;
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                multiplier += modifier.attackSpeedMultiplier(entry.level()) - 1.0F;
            }
        }
        return multiplier;
    }

    // #108 batch: modern-vanilla modifiers (issue #108).

    /** Whether any of the tool's modifiers auto-smelt what it mines ({@link Modifier#autoSmelt}). */
    public static boolean hasAutoSmelt(ItemStack stack) {
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null && modifier.autoSmelt(entry.level())) {
                return true;
            }
        }
        return false;
    }

    /** Whether any of the tool's modifiers redirect its drops to the player's inventory ({@link Modifier#magnetic}). */
    public static boolean isMagnetic(ItemStack stack) {
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null && modifier.magnetic(entry.level())) {
                return true;
            }
        }
        return false;
    }

    /** Combined bonus experience fraction of the tool's modifiers; 0 when nothing touches it. */
    public static float bonusExperienceFraction(ItemStack stack) {
        float bonus = 0.0F;
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                bonus += modifier.bonusExperienceFraction(entry.level());
            }
        }
        return bonus;
    }

    /** Combined submerged-mining-speed attribute bonus of the tool's modifiers; 0 when nothing touches it. */
    public static float submergedMiningSpeedBonus(ItemStack stack) {
        float bonus = 0.0F;
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                bonus += modifier.submergedMiningSpeedBonus(entry.level());
            }
        }
        return bonus;
    }

    /** Combined block-interaction-range attribute bonus of the tool's modifiers; 0 when nothing touches it. */
    public static float blockInteractionRangeBonus(ItemStack stack) {
        float bonus = 0.0F;
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                bonus += modifier.blockInteractionRangeBonus(entry.level());
            }
        }
        return bonus;
    }

    /**
     * Searing, Magnetic Pull and Resonant all key off what a mined block drops, and none of upstream
     * 1.12's {@code IModifier}/{@code ITrait} hooks (nor anything on {@code Item}) sees drops before
     * they hit the ground -- {@link BlockDropsEvent} is the seam. Registered on the game event bus in
     * {@code Forgeweave}, same idiom as {@code ForgeweaveTraits#onIncomingDamage}. Fires server side
     * only ({@link BlockDropsEvent#getLevel()} returns a {@code ServerLevel}), so every behavior below
     * is dedicated-server correct by construction.
     */
    public static void onBlockDrops(BlockDropsEvent event) {
        ItemStack tool = event.getTool();
        if (!(tool.getItem() instanceof ToolItem) || ToolItem.isBroken(tool)) {
            return;
        }
        if (hasAutoSmelt(tool)) {
            smelt(event);
        }
        float bonusXp = bonusExperienceFraction(tool);
        if (bonusXp > 0.0F) {
            event.setDroppedExperience(Math.round(event.getDroppedExperience() * (1.0F + bonusXp)));
        }
        if (isMagnetic(tool) && event.getBreaker() instanceof ServerPlayer player) {
            pullToInventory(event, player);
        }
    }

    /** Searing: each drop becomes its furnace-smelted result, count preserved, or itself if none exists. */
    private static void smelt(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        for (ItemEntity itemEntity : event.getDrops()) {
            ItemStack drop = itemEntity.getItem();
            level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(drop), level)
                    .ifPresent(recipe -> {
                        ItemStack smelted = recipe.value().getResultItem(level.registryAccess()).copy();
                        smelted.setCount(drop.getCount());
                        itemEntity.setItem(smelted);
                    });
        }
    }

    /**
     * Magnetic Pull: whatever {@code player}'s inventory can take is removed from the drop list before
     * it ever spawns in the world; a drop the inventory can't fully absorb is left in the list to fall
     * as usual (upstream's own fallback for a full inventory).
     */
    private static void pullToInventory(BlockDropsEvent event, ServerPlayer player) {
        for (Iterator<ItemEntity> iterator = event.getDrops().iterator(); iterator.hasNext();) {
            if (player.getInventory().add(iterator.next().getItem())) {
                iterator.remove();
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ForgeweaveModifiers() {}
}
