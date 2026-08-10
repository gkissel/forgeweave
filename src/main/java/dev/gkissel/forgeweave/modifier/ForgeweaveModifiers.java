package dev.gkissel.forgeweave.modifier;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
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

    // #107 batch: parity modifiers (issue #107), ported from tinkers-1.12 (NOTICE.md); appended after
    // the #108 batch above per this PR's rebase.

    /** Upstream {@code ModReinforced#chancePerLevel}: 20% per level, so level 5 rolls unbreakable. */
    private static final float REINFORCED_CHANCE_PER_LEVEL = 0.20F;

    /**
     * Obsidian + reinforced plate (issue #107). Upstream {@code ModReinforced}, ported whole: each
     * level is a flat 20% chance to negate a point of durability damage outright
     * ({@link Modifier#durabilityNegationChance}, rolled in {@code ToolItem#damageItem}); at the
     * level-5 cap the chance is {@code 1.0}, which always succeeds and so reads as unbreakable without
     * upstream's separate {@code Unbreakable} flag.
     */
    public static final Modifier REINFORCED = new Modifier() {
        @Override
        public float durabilityNegationChance(int level) {
            return Math.min(1.0F, REINFORCED_CHANCE_PER_LEVEL * level);
        }
    };

    /**
     * Upstream {@code ModMendingMoss#getDurabilityPerXP}: {@code 2 + level} durability per stored XP.
     * Package-private and pure so it's unit-testable, {@code ToolItem#attackDurabilityCost}'s precedent.
     */
    static int mendingMossDurabilityPerXp(int level) {
        return 2 + level;
    }

    /** Upstream {@code ModMendingMoss#getMaxXp}: {@code 100 * 3^(level-1)}, recursive in the original. */
    static int mendingMossXpCap(int level) {
        int cap = 100;
        for (int step = 1; step < level; step++) {
            cap *= 3;
        }
        return cap;
    }

    /** Upstream {@code ModMendingMoss#DELAY}: 7.5s between heals (150 ticks), 20 * 7 + 10 in ticks. */
    private static final int MENDING_MOSS_HEAL_PERIOD_TICKS = 20 * 7 + 10;

    /**
     * Mending moss (issue #107, item obtained by right-clicking a bookshelf while holding moss with
     * 10+ XP levels -- {@link #onRightClickBookshelf}). Upstream {@code ModMendingMoss}: while an XP
     * orb is picked up, the tool banks some of it ({@link #onXpPickup}, capped at
     * {@link #mendingMossXpCap}); while carried and damaged, it spends one banked point roughly every
     * 7.5s to heal {@link #mendingMossDurabilityPerXp} durability ({@link #inventoryTick}, called from
     * {@code ToolItem#inventoryTick} alongside traits).
     *
     * <p>No {@link Modifier} hook carries this behavior -- it needs the XP-pickup and per-tick seams
     * traits and other modifiers don't reach, so it lives in the two static handlers instead, gated by
     * this id the same way a hook would be. The banked amount is state beyond {@code id + level}, which
     * ADR-0004 forbids adding to {@link ModifierEntry}; it lives on the stack as its own component
     * ({@code ForgeweaveDataComponents#MENDING_MOSS_XP}) instead, the same pattern {@code BROKEN} and
     * {@code REPAIR_COUNT} already use for state that isn't the modifier list itself.
     *
     * <p>ponytail: heals from any inventory slot each tick rather than upstream's hotbar/offhand-only
     * restriction (that needs the global slot index NeoForge's {@code Inventory#tick} doesn't expose
     * to {@code Item#inventoryTick} in a directly reusable form) -- a minor buff, not a correctness gap,
     * flagged in the PR for review. The exact 150-tick timer is also replaced with an equal-average
     * per-tick roll ({@code 1/150} chance) rather than a stored last-heal timestamp, for the same
     * ADR-0004 reason: one component of state (banked XP) is the minimum this modifier needs, and
     * {@code ForgeweaveTraits#ECOLOGICAL} already establishes the roll-instead-of-timer idiom.
     */
    public static final Modifier MENDING_MOSS = new Modifier() {};

    /** Upstream {@code ModSilktouch#applyEffect}: a flat 3 off both stats, floored at 1. */
    private static final float SILKY_STAT_PENALTY = 3.0F;

    /**
     * Silky jewel (issue #107). Upstream {@code ModSilktouch}: grants vanilla Silk Touch outright
     * ({@link Modifier#grantsSilkTouch}, applied in {@code ToolAssemblyRecipes#resolveModifier} since
     * it needs registry access {@code ModifierApplication} deliberately doesn't have), at the cost of
     * a flat 3 off mining speed and attack damage (each floored at 1) the moment it's applied.
     *
     * <p>Not ported: upstream also refuses to apply alongside Fortune/Looting or its {@code luck}
     * modifier. Forgeweave ships no Fortune-granting modifier in this PR, so the interaction has
     * nothing to conflict with yet; left for whichever issue adds one (flagged in the PR).
     */
    public static final Modifier SILKY = new Modifier() {
        @Override
        public float miningSpeed(int level, float miningSpeed) {
            return level > 0 ? Math.max(1.0F, miningSpeed - SILKY_STAT_PENALTY) : miningSpeed;
        }

        @Override
        public float attackDamage(int level, float attackDamage) {
            return level > 0 ? Math.max(1.0F, attackDamage - SILKY_STAT_PENALTY) : attackDamage;
        }

        @Override
        public boolean grantsSilkTouch(int level) {
            return level > 0;
        }
    };

    /**
     * Nether star (issue #107). Upstream {@code ModSoulbound}: a soulbound tool survives death instead
     * of dropping. No {@link Modifier} hook carries this either -- {@link #onLivingDrops} pulls the
     * item back out of the death drops and parks it on the dying {@code Player} instance's own
     * inventory, and {@link #onPlayerClone} copies it across to the respawned player, mirroring
     * upstream's own corpse-inventory trick ({@code PlayerDropsEvent} + {@code PlayerEvent.Clone}) with
     * no extra state of Forgeweave's own.
     */
    public static final Modifier SOULBOUND = new Modifier() {};

    /**
     * Upstream {@code ModCreative}: each application adds its own level to the tool's free-slot pool,
     * with no {@code FreeModifierAspect} of its own -- i.e. the application is effectively free. Every
     * Forgeweave modifier's entry occupies one slot regardless ({@link #freeSlots}), so returning
     * {@code level + 1} nets the same +1-per-level upstream grants (the trap {@link Modifier#bonusSlots}
     * documents).
     *
     * <p>Deviation (issue #107, recorded for maintainer review): upstream's {@code creative_modifier}
     * reagent has no survival crafting recipe -- {@code ModCreative#isHidden} marks it admin/creative
     * only, and it is also uncapped. Forgeweave gives it a real recipe (gold block + diamond, shapeless)
     * and a finite cap of 5 so docs/SCOPE.md acceptance test 5 ("an extra-slot item raises the cap") is
     * actually reachable in survival.
     */
    public static final Modifier EXTRA_SLOT = new Modifier() {
        @Override
        public int bonusSlots(int level) {
            return level + 1;
        }
    };

    private static final Map<ResourceLocation, Modifier> REGISTRY = Map.ofEntries(
            Map.entry(id("haste"), HASTE),
            Map.entry(id("searing"), SEARING),
            Map.entry(id("magnetic_pull"), MAGNETIC_PULL),
            Map.entry(id("aquadynamic"), AQUADYNAMIC),
            Map.entry(id("resonant"), RESONANT),
            Map.entry(id("far_reach"), FAR_REACH),
            Map.entry(id("reinforced"), REINFORCED),
            Map.entry(id("mending_moss"), MENDING_MOSS),
            Map.entry(id("silky"), SILKY),
            Map.entry(id("soulbound"), SOULBOUND),
            Map.entry(id("extra_slot"), EXTRA_SLOT));

    private static final ResourceLocation MENDING_MOSS_ID = id("mending_moss");
    private static final ResourceLocation SOULBOUND_ID = id("soulbound");

    /** Upstream {@code ModMendingMoss.MENDING_MOSS_LEVELS}: 10 XP levels per moss -> mending moss. */
    private static final int MENDING_MOSS_ACQUIRE_LEVELS = 10;

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
        float attackDamage = base.attackDamage();
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                miningSpeed = modifier.miningSpeed(entry.level(), miningSpeed);
                attackDamage = modifier.attackDamage(entry.level(), attackDamage);
            }
        }
        return miningSpeed == base.miningSpeed() && attackDamage == base.attackDamage()
                ? base
                : new ToolStats.Stats(base.durability(), miningSpeed, attackDamage);
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

    // #107 batch: parity modifiers (issue #107), appended after the #108 batch above.

    /**
     * Combined chance {@code [0, 1]} that a hit of durability damage is negated outright (issue #107,
     * reinforced); 0 when nothing grants it. Summed rather than combined as independent probabilities
     * -- only one shipped modifier uses this hook, so the distinction has no observable effect yet, and
     * summing keeps the level-5 cap's {@code 1.0} exact.
     */
    public static float durabilityNegationChance(ItemStack stack) {
        float chance = 0.0F;
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                chance += modifier.durabilityNegationChance(entry.level());
            }
        }
        return Math.min(1.0F, chance);
    }

    /** Whether any of this tool's modifiers grant vanilla Silk Touch (issue #107, silky). */
    public static boolean grantsSilkTouch(ItemStack stack) {
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null && modifier.grantsSilkTouch(entry.level())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Mending moss's periodic self-repair (issue #107, see {@link #MENDING_MOSS}'s javadoc). Called
     * from {@code ToolItem#inventoryTick} alongside {@code ForgeweaveTraits#inventoryTick}, which has
     * already ruled out clients and Broken tools -- a no-op for a tool without the modifier, without
     * banked XP, or already at full durability.
     */
    public static void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        ModifierEntry entry = entry(stack, MENDING_MOSS_ID);
        if (entry == null || stack.getDamageValue() <= 0) {
            return;
        }
        int stored = stack.getOrDefault(ForgeweaveDataComponents.MENDING_MOSS_XP.get(), 0);
        if (stored <= 0 || level.getRandom().nextInt(MENDING_MOSS_HEAL_PERIOD_TICKS) != 0) {
            return;
        }
        stack.set(ForgeweaveDataComponents.MENDING_MOSS_XP.get(), stored - 1);
        stack.setDamageValue(Math.max(0, stack.getDamageValue() - mendingMossDurabilityPerXp(entry.level())));
    }

    /**
     * Mending moss banking XP as it's picked up (issue #107), upstream {@code ModMendingMoss#onPickupXp}
     * ported whole: tries the main hand then the off hand, and only ever takes as much of the orb's
     * value as the tool still has room for ({@link #mendingMossXpCap}), leaving the remainder for the
     * vanilla pickup that runs right after this event ({@code ExperienceOrb#playerTouch} reads
     * {@code value} again once the event returns).
     */
    public static void onXpPickup(PlayerXpEvent.PickupXp event) {
        Player player = event.getEntity();
        ExperienceOrb orb = event.getOrb();
        for (ItemStack stack : List.of(player.getMainHandItem(), player.getOffhandItem())) {
            ModifierEntry entry = entry(stack, MENDING_MOSS_ID);
            if (entry == null) {
                continue;
            }
            int stored = stack.getOrDefault(ForgeweaveDataComponents.MENDING_MOSS_XP.get(), 0);
            int change = Math.min(orb.value, mendingMossXpCap(entry.level()) - stored);
            if (change > 0) {
                stack.set(ForgeweaveDataComponents.MENDING_MOSS_XP.get(), stored + change);
                orb.value -= change;
            }
        }
    }

    /**
     * Mending moss's acquisition (issue #107): right-click a bookshelf while holding moss, with 10+ XP
     * levels banked, to trade both for one mending moss. Upstream {@code ToolEvents#onInteract} checks
     * any block whose {@code getEnchantPowerBonus >= 1.0} (a generic "bookshelf-like power" query 1.21
     * has no equivalent for); ponytail: checking the bookshelf block directly is the only vanilla block
     * that ever qualified there, so the behavior is unchanged.
     */
    public static void onRightClickBookshelf(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (!held.is(ForgeweaveItems.MOSS.get()) || !event.getLevel().getBlockState(event.getPos()).is(Blocks.BOOKSHELF)) {
            return;
        }
        Player player = event.getEntity();
        if (player.experienceLevel < MENDING_MOSS_ACQUIRE_LEVELS) {
            if (!event.getLevel().isClientSide) {
                player.displayClientMessage(Component.translatable("message.forgeweave.mending_moss.not_enough_levels",
                        MENDING_MOSS_ACQUIRE_LEVELS), true);
            }
            event.setCanceled(true);
            return;
        }
        if (!event.getLevel().isClientSide) {
            held.shrink(1);
            player.giveExperienceLevels(-MENDING_MOSS_ACQUIRE_LEVELS);
            player.getInventory().add(new ItemStack(ForgeweaveItems.MENDING_MOSS.get()));
        }
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.FALSE);
        event.setCanceled(true);
    }

    /**
     * Soulbound (issue #107), upstream {@code ModSoulbound#onPlayerDeath} ported whole: pulls a
     * soulbound tool back out of the death drops before they spawn as world entities, and parks it in
     * the dying player's own (still-live) inventory object instead. {@link #onPlayerClone} then copies
     * it across to the respawned player. Nothing to do when {@code keepInventory} is on -- vanilla
     * never populates the drops in the first place, so there is nothing here to find.
     */
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Iterator<ItemEntity> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack stack = iterator.next().getItem();
            if (entry(stack, SOULBOUND_ID) != null) {
                iterator.remove();
                player.getInventory().add(stack);
            }
        }
    }

    /** The other half of {@link #onLivingDrops}: copies any soulbound tools onto the respawned player. */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) {
            return;
        }
        Player original = event.getOriginal();
        Player respawned = event.getEntity();
        for (int slot = 0; slot < original.getInventory().getContainerSize(); slot++) {
            ItemStack stack = original.getInventory().getItem(slot);
            if (!stack.isEmpty() && entry(stack, SOULBOUND_ID) != null) {
                respawned.getInventory().add(stack);
                original.getInventory().setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ForgeweaveModifiers() {}
}
