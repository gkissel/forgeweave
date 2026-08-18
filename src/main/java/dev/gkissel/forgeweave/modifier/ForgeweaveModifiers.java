package dev.gkissel.forgeweave.modifier;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderSet;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.BonusDamageVsSeam;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.IgniteOnHitSeam;
import dev.gkissel.forgeweave.combat.KnockbackOnHitSeam;
import dev.gkissel.forgeweave.combat.LifestealOnHitSeam;
import dev.gkissel.forgeweave.combat.PotionEffectOnHitSeam;
import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.Trait;

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
 * <p>Upstream 1.12's {@code ToolCore#DEFAULT_MODIFIERS = 3}. Since issue #344 the charge mirrors
 * upstream exactly: a leveled modifier occupies one slot per displayed <em>level</em>
 * ({@code MultiAspect} spends a free modifier every time a new level starts -- max Haste is 5
 * slots), while the handful whose upstream aspect set charges differently override
 * {@link Modifier#occupiedSlots} (luck flat 1, soulbound 0, extra_slot flat 1). Issue #105's
 * one-slot-per-modifier simplification is gone.
 *
 * <p>{@link #freeSlots} is {@value #DEFAULT_SLOTS} plus whatever {@link Modifier#bonusSlots} grants,
 * minus the slots in use, so issue #107's extra-slot items raise the cap without this class changing.
 * Issue #103 adds a second source on top: a tool's <em>traits</em> can grant bonus slots too
 * ({@link Trait#bonusSlots}, netherite's {@code reinforced_core}) -- summed in alongside the modifier
 * total, since a trait comes from a material rather than a modifier application and so never occupies
 * a slot of its own.
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
     *   <li><b>Draw speed</b> ({@code getDrawspeedBonus}, M3.5 issue #396): {@code drawSpeed +=
     *       drawSpeed * 0.1f * current / max} on {@code Category.LAUNCHER} -- {@code +0.2%} per
     *       redstone, {@code +10%} per level, {@code +50%} at level V. Read only by
     *       {@code BowItem#drawSpeed}, so a tool that never draws is untouched, the way upstream's
     *       category check keeps it.
     * </ul>
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

        @Override
        public float drawSpeedMultiplier(int level) {
            return 1.0F + 0.1F * level / HASTE_REDSTONE_PER_LEVEL;
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

    // ---------------------------------------------------------------- #106 batch (luck, sharpness, diamond, emerald)

    /**
     * Vanilla's {@code incorrect_for_*_tool} ladder, ascending tool power -- the vanilla-tag
     * equivalent of upstream's numeric {@code HarvestLevels} (CONTEXT.md: no numeric harvest levels).
     * Used only by {@link #DIAMOND}/{@link #EMERALD}'s {@code toolTierIndex}: index {@code n} is "this
     * tool cannot mine what index {@code n}'s tag denies", so bumping the index is a strict upgrade.
     *
     * <p>Issue #433: the ladder starts at {@code incorrect_for_wooden_tool}, so index {@code n} is
     * literally upstream's {@code HarvestLevels} number {@code n} -- {@code STONE = 0} (the level
     * that mines stone, which a wooden pickaxe has) through {@code COBALT = 4}. It used to start a
     * rung higher, which made every index one tier too generous.
     */
    static final List<TagKey<Block>> TIER_TAGS = List.of(
            BlockTags.INCORRECT_FOR_WOODEN_TOOL,
            BlockTags.INCORRECT_FOR_STONE_TOOL,
            BlockTags.INCORRECT_FOR_IRON_TOOL,
            BlockTags.INCORRECT_FOR_DIAMOND_TOOL,
            BlockTags.INCORRECT_FOR_NETHERITE_TOOL);

    /** Upstream {@code ModDiamond}: bumps up to but not past {@code HarvestLevels.OBSIDIAN} (3). */
    private static final int DIAMOND_TIER_CAP = 3;
    /** Upstream {@code ModEmerald}: bumps up to but not past {@code HarvestLevels.DIAMOND} (2). */
    private static final int EMERALD_TIER_CAP = 2;

    /**
     * {@link #TIER_TAGS}'s index for a deny-drops rule's block set, or -1 if it isn't on the ladder.
     * Public: {@code ModifierApplication} uses it to find what to bump, and {@code ToolTooltip} uses
     * it to display the tool's <em>current</em> tier rather than its material's starting one, since a
     * diamond/emerald bump changes the stack's rule without changing its head material.
     */
    public static int tierIndexOf(HolderSet<Block> blocks) {
        for (int i = 0; i < TIER_TAGS.size(); i++) {
            if (blocks.equals(BuiltInRegistries.BLOCK.getOrCreateTag(TIER_TAGS.get(i)))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * As {@link #tierIndexOf(HolderSet)}, for a {@code Material#incorrectForTool} tag straight off a
     * material record, or -1 for a tag off the ladder. Public for {@code ToolAssemblyRecipes}, which
     * compares a multi-head tool's HEAD materials to find the highest (issue #294 -- upstream's
     * {@code ToolNBT#head} takes the max harvest level across heads, and this ladder is Forgeweave's
     * vanilla-tag stand-in for that number).
     */
    public static int tierIndexOf(TagKey<Block> incorrectForTool) {
        return TIER_TAGS.indexOf(incorrectForTool);
    }

    public static TagKey<Block> tierTag(int index) {
        return TIER_TAGS.get(index);
    }

    /** Upstream {@code ModDiamond}: {@code data.durability += 500}. */
    private static final int DIAMOND_DURABILITY_BONUS = 500;
    /** Upstream {@code ModDiamond}: {@code data.attack += 1f}. */
    private static final float DIAMOND_ATTACK_BONUS = 1.0F;
    /** Upstream {@code ModDiamond}: {@code data.speed += 0.5f}. */
    private static final float DIAMOND_SPEED_BONUS = 0.5F;

    /**
     * Diamond. Upstream {@code ModDiamond}, ported whole: {@code +500} durability, a one-tier bump,
     * {@code +1} attack and {@code +0.5} mining speed -- all flat, single application. The
     * attack/speed half was left out of issue #106's scope and flagged for maintainer review; issue
     * #265 (maintainer, 2026-08-13) closed that gap.
     */
    public static final Modifier DIAMOND = new Modifier() {
        @Override
        public int durability(int level, int durability, int baseDurability) {
            return durability + DIAMOND_DURABILITY_BONUS;
        }

        @Override
        public int toolTierIndex(int level, int tierIndex) {
            return Math.min(tierIndex + 1, DIAMOND_TIER_CAP);
        }

        @Override
        public float attackDamage(int level, float attackDamage, float baseAttackDamage) {
            return level > 0 ? attackDamage + DIAMOND_ATTACK_BONUS : attackDamage;
        }

        @Override
        public float miningSpeed(int level, float miningSpeed) {
            return level > 0 ? miningSpeed + DIAMOND_SPEED_BONUS : miningSpeed;
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
     * Emerald. Upstream {@code ModEmerald}: {@code +50%} of the tool's untouched materials-derived
     * durability (not the running total -- see {@link Modifier#durability}'s javadoc), plus a tool
     * tier bump capped one rung below diamond's.
     */
    public static final Modifier EMERALD = new Modifier() {
        @Override
        public int durability(int level, int durability, int baseDurability) {
            return durability + baseDurability / 2;
        }

        @Override
        public int toolTierIndex(int level, int tierIndex) {
            return Math.min(tierIndex + 1, EMERALD_TIER_CAP);
        }
    };

    /** Upstream {@code TinkerModifiers}: {@code new ModSharpness(72)} -- 72 quartz per level, 5 levels. */
    private static final int SHARPNESS_QUARTZ_PER_LEVEL = 72;
    /** Upstream {@code ModSharpness#applyEffect}'s two diminishing-returns thresholds. */
    private static final float SHARPNESS_STEP_1 = 10.0F;
    private static final float SHARPNESS_STEP_2 = 20.0F;

    /**
     * Sharpness. Upstream {@code ModSharpness}, ported whole: a diminishing step per quartz --
     * {@code +0.05} tapering to {@code +0.025} below 10 damage, {@code +0.025} tapering to
     * {@code +0.015} below 20, a flat {@code +0.015} above -- plus a flat {@code +0.25} per completed
     * level (72 quartz) on top, undiminished.
     *
     * <p>Issue #295: the curve is seeded from {@code baseAttackDamage} -- the tool's untouched
     * materials-derived stat, upstream's {@code getOriginalToolStats().attack} -- never from
     * {@code attackDamage}'s running total, which earlier modifiers in the list (diamond, silky) may
     * already have folded in. Only the curve's own delta rides on top of that running total, matching
     * upstream's {@code attack -= toolData.attack; attack += tag.getFloat(Tags.ATTACK);}.
     */
    public static final Modifier SHARPNESS = new Modifier() {
        @Override
        public int unitsPerLevel() {
            return SHARPNESS_QUARTZ_PER_LEVEL;
        }

        @Override
        public float attackDamage(int level, float attackDamage, float baseAttackDamage) {
            float attack = baseAttackDamage;
            for (int count = level; count > 0; count--) {
                if (attack <= SHARPNESS_STEP_1) {
                    attack += 0.05F - 0.025F * attack / SHARPNESS_STEP_1;
                } else if (attack <= SHARPNESS_STEP_2) {
                    attack += 0.025F - 0.01F * attack / SHARPNESS_STEP_2;
                } else {
                    attack += 0.015F;
                }
            }
            float delta = attack - baseAttackDamage + level / SHARPNESS_QUARTZ_PER_LEVEL * 0.25F;
            return attackDamage + delta;
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
        public float attackDamage(int level, float attackDamage, float baseAttackDamage) {
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
    public static final Modifier SOULBOUND = new Modifier() {
        @Override
        public int occupiedSlots(int level) {
            // Issue #344: upstream ModSoulbound's aspects are DataAspect + SingleAspect only -- no
            // FreeModifierAspect -- so it never spends a modifier slot.
            return 0;
        }
    };

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

        @Override
        public int occupiedSlots(int level) {
            // Issue #344: upstream ModCreative registers no aspects at all, netting +level free
            // slots. Forgeweave's entry keeps a flat one-slot charge against bonusSlots' level + 1,
            // which nets the same +level -- never the default per-level charge, which would cancel
            // the grant out.
            return level > 0 ? 1 : 0;
        }
    };

    private static final ResourceLocation MENDING_MOSS_ID = id("mending_moss");
    private static final ResourceLocation SOULBOUND_ID = id("soulbound");
    private static final ResourceLocation GLOWING_ID = id("glowing");

    /** Upstream {@code ModMendingMoss.MENDING_MOSS_LEVELS}: 10 XP levels per moss -> mending moss. */
    private static final int MENDING_MOSS_ACQUIRE_LEVELS = 10;

    // ---------------------------------------------------------------- #106 batch (luck, sharpness, diamond, emerald)

    /**
     * Upstream {@code ModLuck}: {@code baseCount = 60}, 3 levels, and upstream's own
     * {@code LuckAspect#getMaxForLevel} is triangular ({@code countPerLevel * level * (level + 1) / 2}:
     * 60/180/360 lapis cumulative for levels 1/2/3) rather than uniform like haste's. That curve is
     * datapack data now, not a Java constant here (issue #106 review, ADR-0004 decision 1): the shipped
     * {@code luck.json}'s {@code cost_per_level: [60, 120, 180]} reproduces it exactly
     * ({@code ModifierRecipe#levelsReached}). This constant remains only as the uniform fallback
     * {@link #unitsPerLevel} feeds the tooltip's generic "Luck II (65/180)" readout
     * ({@link #displayLevel}/{@link #unitsForDisplayLevel}, haste's shipped precedent) -- a cosmetic
     * approximation, since that display doesn't consult the recipe; the actual Fortune/Looting grant
     * always uses the recipe's real thresholds via {@code ModifierApplication#applyEnchantmentGrants}.
     */
    private static final int LUCK_LAPIS_PER_LEVEL = 60;

    private static final ResourceLocation LUCK_ID = id("luck");

    /**
     * Luck. Upstream {@code ModLuck#applyEnchantments}: grants Fortune to every harvest tool (every
     * Forgeweave tool) and Looting to weapon tools (the hatchet) up to the modifier's display level --
     * see {@link Modifier#fortuneLevel}/{@link Modifier#lootingLevel}'s javadoc for where the
     * registry-dependent half of this lives, and {@code ModifierRecipe#levelsReached} for how the raw
     * lapis count becomes the level passed in here.
     *
     * <p>Issue #296: also grows itself for free, capped, off the shared per-hit pipeline -- see
     * {@link #LUCK_GROWTH_SEAM} and {@link #onBlockDrops}, upstream {@code ModLuck#rewardProgress}'s
     * two call sites ({@code afterBlockBreak}/{@code afterHit}).
     */
    public static final Modifier LUCK = new Modifier() {
        @Override
        public int unitsPerLevel() {
            return LUCK_LAPIS_PER_LEVEL;
        }

        @Override
        public boolean appliesToLaunchers() {
            // M3.5 #396: ModLuck's CategoryAnyAspect(HARVEST, WEAPON, PROJECTILE); a bow is neither.
            return false;
        }

        @Override
        public int occupiedSlots(int level) {
            // Issue #344: upstream ModLuck.LuckAspect swaps MultiAspect's charge for a
            // FreeFirstModifierAspect(parent, 1) -- one slot when the modifier first lands, and
            // every later level (lapis or #296's free growth) stays inside it.
            return level > 0 ? 1 : 0;
        }

        @Override
        public int fortuneLevel(int level) {
            return level;
        }

        @Override
        public int lootingLevel(int level) {
            return level;
        }

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(LUCK_GROWTH_SEAM);
        }
    };

    // ---------------------------------------------------------------- issue #296 (luck's growth-on-use)

    /**
     * Upstream {@code ModLuck#rewardProgress}: {@code random.nextFloat() > 0.03f} returns early, so
     * progress lands on the roughly-3%-of-calls complement -- a flat 3% chance, per qualifying block
     * break or point of combat damage dealt, to add one free raw application unit toward luck's cap on
     * top of whatever lapis has been applied at the Tool Station.
     */
    private static final float LUCK_GROWTH_CHANCE = 0.03F;

    /**
     * The roll itself, package-private and pure so it's testable off a seeded {@link RandomSource}
     * without depending on the block-break/combat-hit plumbing below -- same idiom as
     * {@code ForgeweaveTraits#rollsSlimeyProc}/{@code combat.Beheading#rollsHead}.
     */
    static boolean rollsLuckGrowth(RandomSource random) {
        return random.nextFloat() < LUCK_GROWTH_CHANCE;
    }

    /**
     * Upstream {@code ModLuck#rewardProgress}'s {@code apply(tool)} half, minus the registry lookup a
     * recipe's cap needs (see {@link #growLuckOnUse}, the real seam-facing entry point that supplies
     * it): rolls once (always, so every call costs exactly one {@link RandomSource} draw regardless of
     * outcome -- matching upstream's own roll-before-{@code canApply} order) and, if it lands, adds one
     * raw application unit (upstream's {@code data.current++}) to {@code tool}'s luck entry, never past
     * {@code cap}. A no-op -- deliberately, not upstream's own behavior -- when {@code tool} doesn't
     * carry luck at all yet: upstream's {@code MultiAspect#canApply} would let a free roll spend a free
     * modifier slot to grant luck itself from a bare block break, with no lapis ever applied, which
     * reads as a surprising side effect the issue never asks for; flagged in the PR for maintainer
     * review. Pure once past the roll, so the progress-and-cap arithmetic is unit-testable with a plain
     * {@link ItemStack} and no {@code ServerLevel}.
     *
     * @return whether {@code tool}'s luck entry actually grew, so a caller knows whether to re-grant
     *     whatever a crossed level threshold now grants
     */
    static boolean growLuckByOneUnit(ItemStack tool, RandomSource random, int cap) {
        boolean rolled = rollsLuckGrowth(random);
        List<ModifierEntry> entries = of(tool);
        int index = -1;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id().equals(LUCK_ID)) {
                index = i;
                break;
            }
        }
        if (!rolled || index < 0 || entries.get(index).level() >= cap) {
            return false;
        }
        List<ModifierEntry> updated = new ArrayList<>(entries);
        updated.set(index, entries.get(index).withLevel(entries.get(index).level() + 1));
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(), List.copyOf(updated));
        return true;
    }

    /**
     * {@link #growLuckByOneUnit}, with the cap read off the shipped {@code luck.json} recipe's
     * {@code max_level} (360, the same cumulative total {@code cost_per_level: [60, 120, 180]} reaches --
     * {@code ModifierBatch1Test#theShippedLuckRecipeMatchesUpstreamsTriangularSchedule}) so autonomous
     * growth can never push a tool past what applying lapis by hand could, and with Fortune/Looting
     * re-granted through the same {@link ModifierApplication#applyEnchantmentGrants} the Tool Station
     * uses whenever a level actually crossed -- upstream's {@code apply(tool)} re-running
     * {@code applyEnchantments} on every successful roll, not just on the next lapis. The one gate
     * before the registry lookup is cheap on purpose: this runs off every block break and every hit
     * landed with a Forgeweave tool, most of which carry no luck at all.
     */
    static void growLuckOnUse(ItemStack tool, ServerLevel level) {
        if (entry(tool, LUCK_ID) == null) {
            return;
        }
        Optional<ModifierRecipe> recipe = level.registryAccess().registryOrThrow(ModifierRecipe.REGISTRY).stream()
                .filter(candidate -> candidate.modifier().equals(LUCK_ID))
                .findFirst();
        int cap = recipe.map(ModifierRecipe::maxLevel).orElse(Integer.MAX_VALUE);
        if (growLuckByOneUnit(tool, level.getRandom(), cap) && recipe.isPresent()) {
            ModifierApplication.applyEnchantmentGrants(level.registryAccess(), recipe.get(), tool);
        }
    }

    /**
     * Upstream {@code ModLuck#afterHit}: {@code (int) (damageDealt / 2f)} -- one roll per full heart of
     * damage actually dealt, so a single heavy blow can grant several free rolls in one swing while a
     * graze under 2 damage grants none. Pure and package-private so the count itself is unit-testable
     * without staging a real hit.
     */
    static int luckGrowthRolls(float damageDealt) {
        return (int) (damageDealt / 2.0F);
    }

    /**
     * Combat-hit half of {@link #growLuckOnUse} (issue #296) -- {@link Modifier#combatSeam}'s
     * {@code onHit}, the hook whose own javadoc names "advance per-tool combat state" as exactly what
     * it is for.
     */
    private static final CombatSeam LUCK_GROWTH_SEAM = new CombatSeam() {
        @Override
        public void onHit(CombatHit hit, float damageDealt) {
            for (int hearts = luckGrowthRolls(damageDealt); hearts > 0; hearts--) {
                growLuckOnUse(hit.weapon(), hit.level());
            }
        }
    };

    /**
     * Beheading (issue #158). Obsidian. Its whole effect is a chance to drop the victim's head on a
     * killing blow, which ADR-0005 decision 3 puts on the shared post-kill seam -- see
     * {@code combat.Beheading}, which holds the clone constants and the head table.
     *
     * <p>Deliberately <b>not</b> a {@link Modifier#combatSeam} like #162/#163's batch, even though its
     * effect is a combat seam: the chance is rolled once off the sum of this modifier's level and the
     * cleaver's innate levels, and a per-entry seam sees neither the innate nor an unmodified cleaver.
     * {@code Beheading} registers a provider of its own that reads both.
     */
    public static final Modifier BEHEADING = new Modifier() {};

    /**
     * Embossing (issue #154). Every {@code embossment.<material>} id maps here, because a generated
     * per-material id can't be a map key -- materials are datapack data. No hooks: an embossment's
     * whole effect is the traits {@link Embossing} appended to the tool, so there is nothing for a
     * stat hook to do, and {@link Modifier#unitsPerLevel} staying 1 is what makes the tooltip read
     * "Embossment (Iron)" with no level or {@code n/m} progress after it.
     */
    public static final Modifier EMBOSSMENT = new Modifier() {};
    // ---------------------------------------------------------------- issue #163 (combat modifiers batch 2)
    // Knockback, shulking and webbed: docs/SCOPE.md M3's second combat-modifier batch (the first,
    // smite/bane of arthropods/fiery/necrotic, is issue #162). All three are pure Modifier#combatSeam
    // implementations -- no event listener of their own -- consumed by COMBAT_SEAMS below.

    /** Upstream {@code ModKnockback#calcKnockback}: {@code 0.1} added knockback per application unit. */
    private static final float KNOCKBACK_MAGNITUDE_PER_UNIT = 0.1F;
    /** Upstream {@code TinkerModifiers}: {@code new ModKnockback()} -- {@code countPerLevel} 10. */
    private static final int KNOCKBACK_UNITS_PER_LEVEL = 10;

    /**
     * Piston (upstream also accepts sticky piston; not ported -- a single reagent item keeps
     * {@link dev.gkissel.forgeweave.modifier.ModifierRecipe}'s single-{@code Ingredient} shape, flagged
     * for maintainer review in the PR). Upstream {@code ModKnockback}, ported whole: extra knockback on
     * every hit, linear in the raw application count with no upstream-defined cap ("the sky is the
     * limit" -- upstream's own comment) -- {@link KnockbackOnHitSeam} carries the behavior since the
     * combat modifier this PR ships nothing else to attach it to.
     */
    public static final Modifier KNOCKBACK = new Modifier() {
        @Override
        public int unitsPerLevel() {
            return KNOCKBACK_UNITS_PER_LEVEL;
        }

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new KnockbackOnHitSeam(KNOCKBACK_MAGNITUDE_PER_UNIT * level));
        }
    };

    /** Upstream {@code ModShulking}: {@code current / 2 + 10} levitation ticks. */
    private static final int SHULKING_DURATION_OFFSET_TICKS = 10;
    /** Upstream {@code TinkerModifiers}: {@code new ModShulking()} -- {@code countPerLevel} 50. */
    private static final int SHULKING_UNITS_PER_LEVEL = 50;

    /**
     * Shulker shell (upstream: popped chorus fruit -- substituted per issue #163's maintainer
     * direction, since a shulker shell is obtainable in 1.21 and thematically matches Levitation far
     * better; recorded deviation). Upstream {@code ModShulking}, ported whole: Levitation I on hit, its
     * duration a smooth function of the raw application count rather than a stepped display level.
     */
    public static final Modifier SHULKING = new Modifier() {
        @Override
        public int unitsPerLevel() {
            return SHULKING_UNITS_PER_LEVEL;
        }

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new PotionEffectOnHitSeam(MobEffects.LEVITATION, 0, shulkingDurationTicks(level)));
        }
    };

    /** Upstream {@code ModShulking#getDuration}: {@code current / 2 + 10} ticks of Levitation. */
    static int shulkingDurationTicks(int units) {
        return units / 2 + SHULKING_DURATION_OFFSET_TICKS;
    }

    /** Upstream {@code ModWebbed}: {@code level * 20} slowness ticks -- 1 second per level. */
    private static final int WEBBED_DURATION_TICKS_PER_LEVEL = 20;
    /**
     * Upstream {@code ModWebbed}'s hardcoded {@code PotionEffect(MobEffects.SLOWNESS, ..., 1)}: Slowness
     * II -- {@code MobEffects.MOVEMENT_SLOWDOWN} is 1.21's field name for the same effect.
     */
    private static final int WEBBED_SLOWNESS_AMPLIFIER = 1;

    /**
     * Cobweb. Upstream {@code ModWebbed}, ported whole: Slowness II on hit, one second per level (a
     * plain {@code LevelAspect} upstream, so raw application units already equal the display level --
     * unlike shulking, no {@link #unitsPerLevel} override is needed).
     */
    public static final Modifier WEBBED = new Modifier() {
        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new PotionEffectOnHitSeam(
                    MobEffects.MOVEMENT_SLOWDOWN, WEBBED_SLOWNESS_AMPLIFIER, level * WEBBED_DURATION_TICKS_PER_LEVEL));
        }
    };

    /**
     * Combat modifiers as a consumer of the shared per-hit pipeline (ADR-0005 decision 3), the
     * modifier-side counterpart to {@code ForgeweaveTraits#COMBAT_SEAM}. Registered once in
     * {@code Forgeweave}. One provider for the whole modifier list, same reasoning as the trait one:
     * it keeps modifiers running in {@link #of}'s order and a hit with no combat modifier allocates
     * nothing beyond the (already-required) entry list walk.
     */
    public static final CombatSeams.Provider COMBAT_SEAMS = (weapon, out) -> {
        for (ModifierEntry entry : of(weapon)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                modifier.combatSeam(entry.level()).ifPresent(out);
            }
        }
    };

    // ---------------------------------------------------------------- #162 batch (smite, bane of
    // arthropods, fiery, necrotic), ported from tinkers-1.12 (NOTICE.md). Same shape as issue #163's
    // batch above: no Modifier stat hook, since their effect needs the target entity a hit sees (bonus
    // damage keyed on what was struck, an ignite/heal that happens to that entity) -- only
    // Modifier#combatSeam, consumed by COMBAT_SEAMS above, has access to that.

    /** Upstream {@code ModAntiMonsterType}: {@code countPerLevel} 24, both smite and bane share it. */
    private static final int SMITE_BANE_UNITS_PER_LEVEL = 24;
    /** Upstream {@code ModAntiMonsterType}: {@code dmgPerItem = 7f / countPerLevel}, i.e. +7 damage per completed level. */
    private static final float SMITE_BANE_DAMAGE_PER_LEVEL = 7.0F;

    /** Upstream {@code ModAntiMonsterType#calcIncreasedDamage}: raw application units * (7 / countPerLevel). */
    static float smiteBaneBonusDamage(int units) {
        return units * SMITE_BANE_DAMAGE_PER_LEVEL / SMITE_BANE_UNITS_PER_LEVEL;
    }

    /**
     * Consecrated soil. Upstream {@code ModAntiMonsterType("smite", ..., 5, 24, UNDEAD)}: bonus damage
     * against undead, +7 per level, 5 levels, via {@link BonusDamageVsSeam} keyed on 1.21's own
     * {@code sensitive_to_smite} tag (what vanilla's own Smite enchantment keys off, in place of
     * upstream's {@code EnumCreatureAttribute}). The reagent is upstream's own consecrated soil
     * (issue #429): the glowstone-dust stand-in #162 shipped is gone, and
     * {@code ForgeweaveBlocks#CONSECRATED_SOIL} is what {@code modifier_recipe/smite.json} names.
     */
    public static final Modifier SMITE = new Modifier() {
        @Override
        public int unitsPerLevel() {
            return SMITE_BANE_UNITS_PER_LEVEL;
        }

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new BonusDamageVsSeam(EntityTypeTags.SENSITIVE_TO_SMITE, smiteBaneBonusDamage(level)));
        }
    };

    /**
     * Fermented spider eye. Upstream {@code ModAntiMonsterType("bane_of_arthopods", ..., 5, 24,
     * ARTHROPOD)}: same shape as {@link #SMITE}, keyed on {@code sensitive_to_bane_of_arthropods}
     * instead. Reagent item is unchanged from upstream (a plain vanilla item, no substitution needed).
     */
    public static final Modifier BANE_OF_ARTHROPODS = new Modifier() {
        @Override
        public int unitsPerLevel() {
            return SMITE_BANE_UNITS_PER_LEVEL;
        }

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new BonusDamageVsSeam(
                    EntityTypeTags.SENSITIVE_TO_BANE_OF_ARTHROPODS, smiteBaneBonusDamage(level)));
        }
    };

    /** Upstream {@code TinkerModifiers}: {@code new ModFiery()} -> {@code super("fiery", ..., 5, 25)}. */
    private static final int FIERY_UNITS_PER_LEVEL = 25;

    /** Upstream {@code ModFiery#getFireDamage}: raw application units / 15. */
    static float fieryDamage(int units) {
        return units / 15.0F;
    }

    /** Upstream {@code ModFiery#getFireDuration}: {@code 1 + units / 8} (integer division), in seconds. */
    static int fieryDurationSeconds(int units) {
        return 1 + units / 8;
    }

    /**
     * Blaze powder. Upstream {@code ModFiery}: ignites the target and deals a small true-fire damage
     * instance on hit ({@link IgniteOnHitSeam}), 5 levels. Reagent item is unchanged from upstream.
     */
    public static final Modifier FIERY = new Modifier() {
        @Override
        public int unitsPerLevel() {
            return FIERY_UNITS_PER_LEVEL;
        }

        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new IgniteOnHitSeam(fieryDurationSeconds(level), fieryDamage(level)));
        }
    };

    /** Upstream {@code ModNecrotic#lifesteal}: {@code 0.10f * level}. */
    private static final float NECROTIC_LIFESTEAL_PER_LEVEL = 0.10F;

    /** Upstream {@code ModNecrotic#lifesteal}: {@code 0.10f * level} (raw units == display level here). */
    static float necroticLifestealFraction(int level) {
        return NECROTIC_LIFESTEAL_PER_LEVEL * level;
    }

    /**
     * Necrotic bone. Upstream {@code ModNecrotic}: heals the attacker for a fraction of the
     * damage dealt ({@link LifestealOnHitSeam}), 10% per level, 10 levels, one item per level
     * ({@code unitsPerLevel} left at its default of 1, upstream's {@code LevelAspect} rather than a
     * {@code MultiAspect}). The reagent is upstream's own necrotic bone (issue #429): the wither
     * skeleton skull #162 stood in with is gone, and {@code ForgeweaveItems#NECROTIC_BONE} -- dropped
     * by wither skeletons via {@code data/forgeweave/loot_modifiers/necrotic_bone.json}, upstream's
     * {@code ToolEvents#onLootTableLoad} -- is what {@code modifier_recipe/necrotic.json} names.
     */
    public static final Modifier NECROTIC = new Modifier() {
        @Override
        public Optional<CombatSeam> combatSeam(int level) {
            return Optional.of(new LifestealOnHitSeam(necroticLifestealFraction(level)));
        }
    };

    // ---------------------------------------------------------------- issue #223 (wind burst)

    /** Vanilla {@code minecraft:wind_burst}'s own cap (data/minecraft/enchantment/wind_burst.json). */
    private static final int WIND_BURST_MAX_LEVEL = 3;

    /**
     * Breeze rod (issue #223, maintainer decision 2026-08-12: "one breeze rod per level, levels
     * 1-3"). Grants vanilla Wind Burst outright at the applied level -- silky's Silk Touch idiom
     * (issue #107), generalized by {@link Modifier#grantedEnchantment} to carry a level rather than
     * silky's flat on/off. {@code unitsPerLevel} is left at its default of 1, so one breeze rod is one
     * level and the recipe's own {@code max_level: 3} (wind_burst.json) is what stops a fourth; this
     * modifier only clamps defensively in case a datapack retunes that cap past vanilla's own.
     *
     * <p>No stat hooks of its own: unlike silky, there is no upstream Tinkers modifier to port a
     * penalty from, and the maintainer request names only the enchantment grant. Which tools accept
     * it at all is decided by {@code ModifierApplication}, off wind_burst's own vanilla
     * {@code supported_items} tag ({@code minecraft:enchantable/mace}) rather than a Forgeweave-side
     * item check -- see {@code ForgeweaveItemTagsProvider}, which adds the warmace to that tag.
     */
    public static final Modifier WIND_BURST = new Modifier() {
        @Override
        public Optional<EnchantmentGrant> grantedEnchantment(int level) {
            return level > 0
                    ? Optional.of(new EnchantmentGrant(Enchantments.WIND_BURST, Math.min(level, WIND_BURST_MAX_LEVEL)))
                    : Optional.empty();
        }
    };

    // ---------------------------------------------------------------- issue #438 (Width++ / Height++)

    /**
     * Width++ (issue #438), upstream {@code ModHarvestSize("width")} plus the {@code matExpanderW}
     * reagent {@code TinkerModifiers:169-170} binds to it. Upstream's class has an <em>empty</em>
     * {@code applyEffect}: the modifier stores nothing and changes no stat, and every magnitude lives
     * in {@code tools/ToolEvents#onExtraBlockBreak}, which reads the tool's modifier list and widens
     * the mined area by an amount that depends on the tool. Forgeweave splits it the same way -- this
     * object only names the axis, {@code tool.AoeHarvest} owns the per-tool numbers.
     *
     * <p>One shot, one slot: upstream's aspect set is {@code SingleAspect + DataAspect + aoeOnly +
     * freeModifier}, i.e. at most one application, costing one modifier slot, refused on a tool with
     * no area to widen. The first two are the shipped recipe's {@code max_level: 1} / {@code cost: 1};
     * the {@code aoeOnly} gate is {@link ModifierApplication}'s, read off
     * {@link Modifier#aoeExpansion} itself.
     */
    public static final Modifier HARVEST_WIDTH = new Modifier() {
        @Override
        public Optional<AoeAxis> aoeExpansion(int level) {
            return level > 0 ? Optional.of(AoeAxis.WIDTH) : Optional.empty();
        }
    };

    /** Height++ (issue #438), {@link #HARVEST_WIDTH}'s twin -- upstream {@code ModHarvestSize("height")}. */
    public static final Modifier HARVEST_HEIGHT = new Modifier() {
        @Override
        public Optional<AoeAxis> aoeExpansion(int level) {
            return level > 0 ? Optional.of(AoeAxis.HEIGHT) : Optional.empty();
        }
    };

    // ---------------------------------------------------------------- parity audit T25 (issue #456)

    /**
     * Upstream {@code ModGlowing#onUpdate}: the tool only lights the way where
     * {@code world.getLightFromNeighbors(pos) < 8}.
     */
    private static final int GLOWING_LIGHT_THRESHOLD = 8;

    /**
     * Ender eye (parity audit T25, issue #456). Upstream {@code ModGlowing}: while the tool is the
     * held item and the holder stands somewhere darker than light {@value #GLOWING_LIGHT_THRESHOLD},
     * it drops a light source next to them for one durability. One shot, one slot -- upstream's
     * aspect set is bare {@code ModifierTrait(identifier, color)} with {@code maxLevel} 0, which
     * wires {@code DataAspect + freeModifier}, i.e. it applies once and costs one slot (the shipped
     * recipe's {@code max_level: 1} / {@code cost: 1}).
     *
     * <p>No {@link Modifier} hook carries this: like {@link #MENDING_MOSS} it is a per-tick behavior
     * rather than a function of the tool's stats, so it lives in {@link #inventoryTick} gated by this
     * id. The light itself is vanilla {@code minecraft:light} rather than a Forgeweave block of its
     * own -- upstream 1.12 had no vanilla equivalent and shipped {@code BlockGlow} for it, 1.21 does;
     * the deviations that follow from that are recorded in the PR.
     */
    public static final Modifier GLOWING = new Modifier() {};

    private static final Map<ResourceLocation, Modifier> REGISTRY = Map.ofEntries(
            Map.entry(id("harvest_width"), HARVEST_WIDTH),
            Map.entry(id("harvest_height"), HARVEST_HEIGHT),
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
            Map.entry(id("extra_slot"), EXTRA_SLOT),
            Map.entry(id("luck"), LUCK),
            Map.entry(id("sharpness"), SHARPNESS),
            Map.entry(id("diamond"), DIAMOND),
            Map.entry(id("emerald"), EMERALD),
            Map.entry(id("knockback"), KNOCKBACK),
            Map.entry(id("shulking"), SHULKING),
            Map.entry(id("webbed"), WEBBED),
            Map.entry(id("smite"), SMITE),
            Map.entry(id("bane_of_arthropods"), BANE_OF_ARTHROPODS),
            Map.entry(id("fiery"), FIERY),
            Map.entry(id("necrotic"), NECROTIC),
            Map.entry(id("beheading"), BEHEADING),
            Map.entry(id("wind_burst"), WIND_BURST),
            Map.entry(id("glowing"), GLOWING));

    /**
     * docs/SCOPE.md's "8 combat modifiers" (M3 acceptance test 4): the #162/#163 batches' seven
     * {@link Modifier#combatSeam} implementations plus {@link #BEHEADING}, which isn't one (see its
     * own javadoc) but is still counted among the eight there. Used only by {@link #isCombatModifier}
     * -- the M3-17 advancement chain's "apply a combat modifier" step -- so a future ninth needs one
     * line here rather than a parallel table.
     */
    private static final Set<ResourceLocation> COMBAT_MODIFIER_IDS = Set.of(
            id("knockback"), id("shulking"), id("webbed"),
            id("smite"), id("bane_of_arthropods"), id("fiery"), id("necrotic"),
            id("beheading"));

    /** Whether {@code id} is one of the eight combat modifiers (docs/SCOPE.md M3 acceptance test 4). */
    public static boolean isCombatModifier(ResourceLocation id) {
        return COMBAT_MODIFIER_IDS.contains(id);
    }

    /**
     * Every registered modifier id (not embossing's generated per-material ids, which have no fixed
     * registration to enumerate). {@code ModifierLangCoverageTest} walks this so a new modifier
     * cannot ship without its {@code .name}/{@code .description} lang keys (issue #258).
     */
    public static Set<ResourceLocation> ids() {
        return REGISTRY.keySet();
    }

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
        if (Embossing.isEmbossment(id)) {
            return EMBOSSMENT; // #154: generated per-material, so it can't be in the map above.
        }
        if (Fortification.isFortification(id)) {
            return Fortification.BEHAVIOR; // #271: likewise generated per-material.
        }
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
        int bonus = 0;
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                bonus += modifier.bonusSlots(entry.level());
            }
        }
        // #103 -- netherite's reinforced_core: a trait-granted bonus, not a modifier-granted one, so
        // it is summed in on top rather than tied to any ModifierEntry.
        for (Trait trait : ForgeweaveTraits.of(stack)) {
            bonus += trait.bonusSlots();
        }
        return DEFAULT_SLOTS + bonus - occupiedSlots(stack);
    }

    /**
     * How many of the tool's modifier slots are occupied: each entry's {@link Modifier#occupiedSlots}
     * (issue #344 -- upstream charges one free modifier per level, so this is the per-level total
     * upstream's {@code TagUtil#getBaseModifiersUsed} accumulates, which also feeds
     * {@code TinkersItem#calculateRepair}'s modifier-count repair penalty
     * ({@link dev.gkissel.forgeweave.tool.ToolRepair})), except embossments (issue #154 -- upstream's
     * {@code ModExtraTrait} carries a {@code SingleAspect} and a {@code DataAspect} but deliberately
     * no {@code FreeModifierAspect}, which is what charges slots).
     *
     * <p>Fortifications (issue #271) are chargeless for the very same upstream reason -- {@code
     * ModFortify}'s aspect set is {@code SingleAspect + DataAspect + harvestOnly}, no {@code
     * FreeModifierAspect} -- but they need no exception here: unlike an embossment, a fortification
     * id resolves to a real behavior ({@link Fortification#BEHAVIOR}), so it says so itself through
     * {@link Modifier#occupiedSlots} and the loop below simply asks.
     */
    public static int occupiedSlots(ItemStack stack) {
        int occupied = 0;
        for (ModifierEntry entry : of(stack)) {
            if (!Embossing.isEmbossment(entry.id())) {
                occupied += occupiedSlots(entry.id(), entry.level());
            }
        }
        return occupied;
    }

    /**
     * The slots one {@code id + level} entry occupies -- {@link Modifier#occupiedSlots}, or a flat 1
     * for an id this version doesn't implement: inert data (see {@link #of}) keeps the one slot the
     * player's version charged, since its unit scale is unknowable here.
     */
    public static int occupiedSlots(ResourceLocation id, int level) {
        Modifier modifier = get(id);
        if (modifier != null) {
            return modifier.occupiedSlots(level);
        }
        return level > 0 ? 1 : 0;
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

    // ---------------------------------------------------------------- extra-info lines (parity audit
    // T26, issue #457) -- upstream 1.12's IModifier#getExtraInfo, collected by
    // TooltipBuilder#addModifierInfo (TooltipBuilder.java:120-139) and shown by
    // ToolCore#getInformation(stack, true), i.e. the Tool Station's tool info panel only.

    private static final ResourceLocation HASTE_ID = id("haste");
    private static final ResourceLocation SMITE_ID = id("smite");
    private static final ResourceLocation BANE_ID = id("bane_of_arthropods");
    private static final ResourceLocation FIERY_ID = id("fiery");
    private static final ResourceLocation NECROTIC_ID = id("necrotic");
    private static final ResourceLocation REINFORCED_ID = id("reinforced");
    private static final ResourceLocation SHULKING_ID = id("shulking");

    /** {@code modifier.forgeweave.reinforced.unbreakable} -- upstream's own key, used twice (see below). */
    public static final String UNBREAKABLE_KEY = "modifier.forgeweave.reinforced.unbreakable";

    /**
     * Which modifier ids produce {@code .extra} lines, so {@code ModifierLangCoverageTest} can guard
     * the keys the same way it guards {@code .name}/{@code .description}. Fiery is the one that also
     * needs {@code .extra2} (upstream's second {@code loc + 2} key).
     */
    public static Set<ResourceLocation> extraInfoIds() {
        return Set.of(HASTE_ID, SMITE_ID, BANE_ID, FIERY_ID, NECROTIC_ID, REINFORCED_ID, SHULKING_ID,
                MENDING_MOSS_ID);
    }

    /**
     * What one modifier on this tool adds beyond its own description -- upstream's
     * {@code IModifier#getExtraInfo(tool, modifierTag)}, one implementation per modifier, ported for
     * every modifier whose 1.12 counterpart has one:
     *
     * <ul>
     *   <li><b>haste</b> ({@code ModHaste:110-127}): one {@code Bonus Speed: +x%} line per speed
     *       category the tool has -- the draw-speed bonus for a {@code Category.LAUNCHER}, the
     *       attack-speed bonus for a {@code Category.WEAPON}. Derived from
     *       {@link Modifier#drawSpeedMultiplier}/{@link Modifier#attackSpeedMultiplier} rather than
     *       from haste's own constants, so a later modifier that touches either speed gets the line
     *       for free (issue #424, kept).
     *   <li><b>smite / bane of arthropods</b> ({@code ModAntiMonsterType:46-55}): the bonus damage.
     *   <li><b>fiery</b> ({@code ModFiery:53-64}): fire damage and burn duration, two lines.
     *   <li><b>necrotic</b> ({@code ModNecrotic:37-43}): the lifesteal fraction.
     *   <li><b>reinforced</b> ({@code ModReinforced:71-84}): the negation chance, replaced by
     *       "Unbreakable" once it reaches 100%.
     *   <li><b>shulking</b> ({@code ModShulking:34-41}): the levitation duration in seconds.
     *   <li><b>mending moss</b> ({@code ModMendingMoss:131-138}): the XP currently banked on the
     *       stack, which is the one line that reads live state rather than the level.
     * </ul>
     *
     * <p>The wording lives here, next to the numbers, for the same reason upstream puts
     * {@code getExtraInfo} on the modifier class itself; {@code StationText} only decides where the
     * lines go.
     */
    public static List<Component> extraInfo(ItemStack tool, ModifierEntry entry) {
        ResourceLocation id = entry.id();
        int level = entry.level();
        String key = "modifier." + id.getNamespace() + "." + id.getPath() + ".extra";
        if (HASTE_ID.equals(id)) {
            return speedExtraInfo(tool, entry, key);
        }
        if (SMITE_ID.equals(id) || BANE_ID.equals(id)) {
            return List.of(Component.translatable(key, StationText.formatNumber(smiteBaneBonusDamage(level))));
        }
        if (FIERY_ID.equals(id)) {
            return List.of(
                    Component.translatable(key, StationText.formatNumber(fieryDamage(level))),
                    Component.translatable(key + "2", StationText.formatNumber(fieryDurationSeconds(level))));
        }
        if (NECROTIC_ID.equals(id)) {
            return List.of(Component.translatable(key,
                    StationText.formatPercent(necroticLifestealFraction(level))));
        }
        if (REINFORCED_ID.equals(id)) {
            float chance = REINFORCED.durabilityNegationChance(level);
            Component value = chance >= 1.0F
                    ? Component.translatable(UNBREAKABLE_KEY)
                    : Component.literal(StationText.formatPercent(chance));
            return List.of(Component.translatable(key, value));
        }
        if (SHULKING_ID.equals(id)) {
            return List.of(Component.translatable(key,
                    StationText.formatNumber(shulkingDurationTicks(level) / 20.0F)));
        }
        if (MENDING_MOSS_ID.equals(id)) {
            return List.of(Component.translatable(key,
                    String.valueOf(tool.getOrDefault(ForgeweaveDataComponents.MENDING_MOSS_XP.get(), 0))));
        }
        return List.of();
    }

    /** Haste's pair of lines, and any later modifier that moves either speed (issue #424). */
    private static List<Component> speedExtraInfo(ItemStack tool, ModifierEntry entry, String key) {
        Modifier modifier = get(entry.id());
        if (modifier == null) {
            return List.of();
        }
        float bonus = 0.0F;
        if (tool.getItem() instanceof BowItem) {
            bonus = modifier.drawSpeedMultiplier(entry.level()) - 1.0F;
        } else if (tool.getItem() instanceof ToolItem melee && melee.isWeapon()) {
            bonus = modifier.attackSpeedMultiplier(entry.level()) - 1.0F;
        }
        return bonus == 0.0F
                ? List.of()
                : List.of(Component.translatable(key, StationText.formatPercent(bonus)));
    }

    /**
     * How many levels of {@code id} have a name of their own -- upstream's {@code modifier.<id>.nameN}
     * ladder ({@code Modifier#getLeveledTooltip}, Modifier.java:214-235: it walks down from the
     * current level looking for the highest defined key and falls back to the plain name plus a roman
     * numeral). Only haste and sharpness ship one in the 1.12 clone's {@code en_us.lang}
     * (Haster/Hastest/Hastester/Hastestest, Sharper/Sharpest/Sharpester/Sharpestest); reinforced's
     * max-level rename is not part of this ladder upstream either -- it is {@code
     * ModReinforced#getTooltip}'s own {@code .unbreakable} branch, reproduced in
     * {@link ModifierApplication#name(ResourceLocation, int)}.
     */
    public static int leveledNameCount(ResourceLocation id) {
        return LEVELED_NAMES.getOrDefault(id, 1);
    }

    private static final Map<ResourceLocation, Integer> LEVELED_NAMES =
            Map.of(id("haste"), 5, id("sharpness"), 5);

    /**
     * A modifier's own colour, which upstream stores per application in {@code ModifierNBT#color} and
     * every modifier line is prefixed with ({@code TooltipBuilder#addModifierTooltips} and
     * {@code #addModifierInfo}, both via {@code ModifierNBT#getColorString}). The values are the ones
     * each upstream class passes to its {@code super(identifier, color)} constructor
     * ({@code tools/TinkerModifiers#registerModifiers} for the two built inline); the six
     * Forgeweave-only modifiers have no upstream class to take one from, so theirs are chosen here
     * from their reagent (recorded as a deviation -- there is nothing to be 1:1 with).
     *
     * <p>Falls back to {@code StationText#MODIFIER_COLOR} for an id with no entry, which is every
     * generated embossment/fortification id: upstream colours those with the donor material's own
     * text colour, which needs a registry lookup the tooltip callers here do not have. Recorded
     * deviation.
     */
    public static TextColor color(ResourceLocation id) {
        return COLORS.getOrDefault(id, StationText.MODIFIER_COLOR);
    }

    private static final Map<ResourceLocation, TextColor> COLORS = Map.ofEntries(
            // Upstream 1.12 constants.
            Map.entry(id("haste"), TextColor.fromRgb(0x910000)),
            Map.entry(id("harvest_width"), TextColor.fromRgb(0xCAF6A2)),
            Map.entry(id("harvest_height"), TextColor.fromRgb(0xCAF6A2)),
            Map.entry(id("reinforced"), TextColor.fromRgb(0x502E83)),
            Map.entry(id("mending_moss"), TextColor.fromRgb(0x43AB32)),
            Map.entry(id("silky"), TextColor.fromRgb(0xFBE28B)),
            Map.entry(id("soulbound"), TextColor.fromRgb(0xF5FBAC)),
            Map.entry(id("luck"), TextColor.fromRgb(0x2D51E2)),
            Map.entry(id("sharpness"), TextColor.fromRgb(0xFFF6F6)),
            Map.entry(id("diamond"), TextColor.fromRgb(0x8CF4E2)),
            Map.entry(id("emerald"), TextColor.fromRgb(0x41F384)),
            Map.entry(id("knockback"), TextColor.fromRgb(0x9F9F9F)),
            Map.entry(id("shulking"), TextColor.fromRgb(0xAACCFF)),
            Map.entry(id("webbed"), TextColor.fromRgb(0xFFFFFF)),
            Map.entry(id("smite"), TextColor.fromRgb(0xE8D500)),
            Map.entry(id("bane_of_arthropods"), TextColor.fromRgb(0x61BA49)),
            Map.entry(id("fiery"), TextColor.fromRgb(0xEA9E32)),
            Map.entry(id("necrotic"), TextColor.fromRgb(0x5E0000)),
            Map.entry(id("beheading"), TextColor.fromRgb(0x10574B)),
            Map.entry(id("glowing"), TextColor.fromRgb(0xFFFFAA)),
            // Upstream has no modifier for these; the trait of the same name is the nearest source
            // (searing <- TraitAutosmelt 0xff5500, magnetic_pull <- TraitMagnetic 0xdddddd,
            // aquadynamic <- TraitAquadynamic AQUA), the rest are this ticket's own picks.
            Map.entry(id("searing"), TextColor.fromRgb(0xFF5500)),
            Map.entry(id("magnetic_pull"), TextColor.fromRgb(0xDDDDDD)),
            Map.entry(id("aquadynamic"), TextColor.fromRgb(0x55FFFF)),
            Map.entry(id("resonant"), TextColor.fromRgb(0x2EBAA4)),
            Map.entry(id("far_reach"), TextColor.fromRgb(0xB180D9)),
            Map.entry(id("wind_burst"), TextColor.fromRgb(0xC0F3F0)),
            Map.entry(id("extra_slot"), TextColor.fromRgb(0xAAAAAA)));

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
        int durability = base.durability();
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                miningSpeed = modifier.miningSpeed(entry.level(), miningSpeed);
                attackDamage = modifier.attackDamage(entry.level(), attackDamage, base.attackDamage());
                durability = modifier.durability(entry.level(), durability, base.durability());
            }
        }
        return miningSpeed == base.miningSpeed() && attackDamage == base.attackDamage() && durability == base.durability()
                ? base
                : new ToolStats.Stats(durability, miningSpeed, attackDamage);
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

    /**
     * Combined draw-speed multiplier of the tool's modifiers (M3.5 issue #396, {@link
     * Modifier#drawSpeedMultiplier}); 1 when nothing touches it. Multiplicative, unlike
     * {@link #attackSpeedMultiplier}'s additive fold: upstream's {@code ToolNBT#attackSpeedMultiplier}
     * is a sum modifiers add to, while {@code ProjectileLauncherNBT#drawSpeed} is scaled in place by
     * each {@code applyEffect} in turn.
     */
    public static float drawSpeedMultiplier(ItemStack stack) {
        float multiplier = 1.0F;
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                multiplier *= modifier.drawSpeedMultiplier(entry.level());
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

    /**
     * Which mined-area axes {@code stack}'s modifiers expand (issue #438) -- the set upstream's
     * {@code ToolEvents#onExtraBlockBreak} builds by walking the tool's modifier list for its two
     * expander ids. A {@link java.util.Set} rather than a pair of booleans because the mattock's
     * upstream branch cares only how <em>many</em> expanders are on the tool, not which.
     */
    public static Set<Modifier.AoeAxis> aoeExpansion(ItemStack stack) {
        Set<Modifier.AoeAxis> axes = EnumSet.noneOf(Modifier.AoeAxis.class);
        for (ModifierEntry entry : of(stack)) {
            Modifier modifier = get(entry.id());
            if (modifier != null) {
                modifier.aoeExpansion(entry.level()).ifPresent(axes::add);
            }
        }
        return axes;
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
        // #228: the autosmelt trait (firewood) shares Searing's smelt path outright rather than
        // duplicating it -- the one trait consulted here, through its own aggregator.
        if (hasAutoSmelt(tool) || ForgeweaveTraits.autoSmelts(tool)) {
            smelt(event);
        }
        float bonusXp = bonusExperienceFraction(tool);
        if (bonusXp > 0.0F) {
            event.setDroppedExperience(Math.round(event.getDroppedExperience() * (1.0F + bonusXp)));
        }
        if (isMagnetic(tool) && event.getBreaker() instanceof ServerPlayer player) {
            pullToInventory(event, player);
        }
        // Issue #296: upstream ModLuck#afterBlockBreak's half of the autonomous-growth roll.
        growLuckOnUse(tool, event.getLevel());
    }

    /** Upstream {@code TraitAutosmelt#afterBlockBreak}: 3 FLAME particles per effective break. */
    private static final int AUTOSMELT_FLAME_PARTICLES = 3;

    /**
     * Searing / autosmelt (issue #228 shares this path; XP, particles, the effectiveness gate and the
     * Silk Touch exclusion added for issue #458): each drop becomes its furnace-smelted result, count
     * preserved, or itself if none exists.
     *
     * <p>Upstream {@code TraitAutosmelt#blockHarvestDrops} wraps the whole thing -- smelting and the
     * XP it drops -- in {@code ToolHelper#isToolEffective2}, and its {@code canApplyTogether} refuses
     * to ever pair the trait with the Silk Touch enchantment or with squeaky/silky (which both grant
     * it); Forgeweave has no apply-time incompatibility layer yet (parity audit T23), so both checks
     * live here on the shared smelt path instead, gating Searing -- the Forgeweave-only modifier this
     * trait rides (issue #228) -- the same way. The FLAME particles are upstream's separate
     * {@code afterBlockBreak} hook, fired on the same effectiveness check independent of whether
     * anything actually had a smelting result (upstream draws them for every effective break).
     */
    private static void smelt(BlockDropsEvent event) {
        ItemStack tool = event.getTool();
        if (!tool.isCorrectToolForDrops(event.getState())) {
            return;
        }
        ServerLevel level = event.getLevel();
        if (autoSmeltExcluded(level, tool)) {
            return;
        }
        int xpGained = 0;
        for (ItemEntity itemEntity : event.getDrops()) {
            ItemStack drop = itemEntity.getItem();
            Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager()
                    .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(drop), level);
            if (recipe.isEmpty()) {
                continue;
            }
            ItemStack smelted = recipe.get().value().getResultItem(level.registryAccess()).copy();
            smelted.setCount(drop.getCount());
            itemEntity.setItem(smelted);
            xpGained += roundedSmeltingXp(recipe.get().value().getExperience(), level.getRandom());
        }
        if (xpGained > 0) {
            event.setDroppedExperience(event.getDroppedExperience() + xpGained);
        }
        BlockPos pos = event.getPos();
        RandomSource random = level.getRandom();
        for (int i = 0; i < AUTOSMELT_FLAME_PARTICLES; i++) {
            level.sendParticles(ParticleTypes.FLAME, pos.getX() + random.nextDouble(),
                    pos.getY() + random.nextDouble(), pos.getZ() + random.nextDouble(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /**
     * Upstream {@code TraitAutosmelt#canApplyTogether}: refuses Silk Touch (vanilla enchantment or a
     * squeaky/silky grant of it, both of which land as the real enchantment -- see
     * {@code ToolAssemblyRecipes#retuneSilkTouch}/{@code #grantEnchantments} -- checked directly here
     * too so this holds even for a stack built outside that assembly path).
     */
    private static boolean autoSmeltExcluded(ServerLevel level, ItemStack tool) {
        if (ForgeweaveTraits.grantsSilkTouch(tool) || grantsSilkTouch(tool)) {
            return true;
        }
        return level.registryAccess().lookup(Registries.ENCHANTMENT)
                .flatMap(lookup -> lookup.get(Enchantments.SILK_TOUCH))
                .map(silkTouch -> EnchantmentHelper.getItemEnchantmentLevel(silkTouch, tool) > 0)
                .orElse(false);
    }

    /**
     * Upstream {@code TraitAutosmelt}'s probabilistic round-up: {@code float xp = ...; if(xp < 1 &&
     * Math.random() < xp) xp += 1f; if(xp >= 1f) dropXp((int) xp)}. A fraction under 1 gets that
     * fraction's own chance of rounding up to 1 instead of dropping nothing; 1 or more carries over
     * truncated to an int, same as upstream's cast.
     */
    static int roundedSmeltingXp(float xp, RandomSource random) {
        if (xp < 1.0F && random.nextFloat() < xp) {
            xp += 1.0F;
        }
        return xp >= 1.0F ? (int) xp : 0;
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
     * The per-tick modifier behaviors, called from {@code ToolItem#inventoryTick} alongside
     * {@code ForgeweaveTraits#inventoryTick}, which has already ruled out clients and Broken tools.
     * A no-op for a tool carrying neither {@link #MENDING_MOSS} nor {@link #GLOWING}.
     *
     * @param isSelected whether this is the holder's held item -- upstream {@code ITrait}/modifier
     *     {@code onUpdate}'s own parameter, which {@code ModGlowing} gates its whole effect on.
     *     Mending moss ignores it, as it always has (see {@link #MENDING_MOSS}'s ponytail note).
     */
    public static void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder, boolean isSelected) {
        mendingMossTick(stack, level, holder);
        if (isSelected) {
            glowingTick(stack, level, holder);
        }
    }

    /** Mending moss's periodic self-repair (issue #107, see {@link #MENDING_MOSS}'s javadoc). */
    private static void mendingMossTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
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
     * Glowing's light-dropping (parity audit T25, issue #456), upstream {@code ModGlowing#onUpdate}
     * whole: standing anywhere darker than light {@value #GLOWING_LIGHT_THRESHOLD}, the first of the
     * holder's seven neighbouring positions that will take a light gets one, for one durability.
     * Upstream's own damage exemption for a creative player is vanilla's here --
     * {@code ItemStack#hurtAndBreak} skips a holder with infinite materials on its own.
     */
    private static void glowingTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        if (entry(stack, GLOWING_ID) == null) {
            return;
        }
        BlockPos standing = holder.blockPosition();
        if (level.getMaxLocalRawBrightness(standing) >= GLOWING_LIGHT_THRESHOLD) {
            return;
        }
        // Upstream ModGlowing#onUpdate's own seven candidates, in its own order.
        for (BlockPos pos : List.of(standing, standing.above(), standing.north(), standing.east(),
                standing.south(), standing.west(), standing.below())) {
            BlockState state = level.getBlockState(pos);
            // Upstream BlockGlow#addGlow: replaceable, and never inside a liquid.
            if (!state.canBeReplaced() || !state.getFluidState().isEmpty()) {
                continue;
            }
            level.setBlockAndUpdate(pos, Blocks.LIGHT.defaultBlockState());
            stack.hurtAndBreak(1, holder, EquipmentSlot.MAINHAND);
            return;
        }
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
