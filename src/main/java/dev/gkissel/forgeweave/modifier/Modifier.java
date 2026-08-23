package dev.gkissel.forgeweave.modifier;

import java.util.Optional;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;

import dev.gkissel.forgeweave.combat.CombatSeam;

/**
 * A post-assembly upgrade applied to a finished Tool at the Tool Station (CONTEXT.md glossary --
 * distinct from a {@code Trait}, which a Material grants). Modifier behavior is Java with 1.12-parity
 * constants; the reagents, costs and level cap that <em>apply</em> it are datapack JSON
 * ({@link ModifierRecipe}). That split is ADR-0004's decision 1, and it is why nothing here knows
 * anything about items.
 *
 * <p>ADR-0004's hard rule: a tool serializes its modifiers as {@code id + level} only
 * ({@link ModifierEntry}) -- never a reference to one of these objects -- so a save written by any
 * version decodes in any other. Every method below therefore takes the level as a plain {@code int}
 * rather than reading state of its own.
 *
 * <p>ponytail: only the hooks a shipped modifier actually needs, following {@code trait.Trait}'s
 * precedent of adding a hook when a behavior needs it rather than porting upstream's twenty-method
 * {@code IModifier}. ADR-0004's M6 commitment (these classes become the first entries of a
 * parameterized behavior library) is why they stay this narrow: each is a pure function of level, so
 * a JSON-configured generic implementation can replace it without touching a caller. Issue #107 added
 * {@link #attackDamage}, {@link #durabilityNegationChance} and {@link #grantsSilkTouch} for reinforced
 * and silky; soulbound and mending moss need no hook here at all -- their behavior is event-driven and
 * lives in {@code ForgeweaveModifiers}' static handlers instead (see that class's javadoc).
 */
public interface Modifier {

    /**
     * How many application units make one displayed level. Upstream 1.12 expresses this as
     * {@code ModifierAspect.MultiAspect}'s {@code countPerLevel} -- haste's 50 redstone per level --
     * and stores the running count as {@code ModifierNBT.IntegerNBT#current}. Forgeweave stores that
     * same count as {@link ModifierEntry#level}, so this is what turns it into the level a player
     * sees. A modifier that is simply on or off leaves it at 1.
     */
    default int unitsPerLevel() {
        return 1;
    }

    /**
     * The tool's mining speed after this modifier has adjusted it.
     *
     * @param level accumulated application units (see {@link ModifierEntry#level})
     * @param miningSpeed the speed the tool's materials alone produced, plus whatever earlier
     *     modifiers in the list already did to it
     */
    default float miningSpeed(int level, float miningSpeed) {
        return miningSpeed;
    }

    /**
     * Multiplier on the tool's attack speed, applied only to tools upstream gives
     * {@code Category.WEAPON} (of Forgeweave's three, only the hatchet). Upstream's
     * {@code ToolNBT#attackSpeedMultiplier}, which starts at 1 and modifiers add to.
     */
    default float attackSpeedMultiplier(int level) {
        return 1.0F;
    }

    /**
     * Multiplier on a launcher's draw speed (M3.5 issue #396) -- upstream {@code ModHaste#applyEffect}'s
     * {@code Category.LAUNCHER} branch, {@code drawSpeed += drawSpeed * getDrawspeedBonus}, the one
     * modifier in the 1.12 clone that touches {@code ProjectileLauncherNBT#drawSpeed}. Read by
     * {@code BowItem#drawSpeed} and nothing else; a tool that does not draw never asks. Multiplicative
     * across modifiers ({@code ForgeweaveModifiers#drawSpeedMultiplier}), which is what upstream's
     * sequential {@code +=} on the same tag amounts to.
     */
    default float drawSpeedMultiplier(int level) {
        return 1.0F;
    }

    /**
     * Whether this modifier may be applied to a launcher (a bow) at all -- upstream's category
     * aspects (M3.5 issue #396): {@code ModLuck} takes {@code CategoryAnyAspect(HARVEST, WEAPON,
     * PROJECTILE)} and a bow is {@code TOOL + LAUNCHER} only, so upstream's station declines it.
     * {@code ModifierApplication} refuses with {@code gui.forgeweave.modifier.unsupported_tool} when
     * this is false; every other shipped modifier's aspects admit a launcher, and {@code ModHaste}'s
     * {@code canApplyCustom} refuses only {@code NO_MELEE} (the projectiles themselves, which
     * Forgeweave does not ship). Kept as a category predicate rather than an item check so this
     * interface stays as item-free as ADR-0004 wants it.
     */
    default boolean appliesToLaunchers() {
        return true;
    }

    /**
     * Whether this modifier may only be applied to a harvest tool -- upstream's
     * {@code ModifierAspect.harvestOnly}, i.e. {@code CategoryAspect(Category.HARVEST)}. Blasting
     * (parity audit T24) is the first shipped user; {@code ModifierApplication} reads the tool's own
     * {@link dev.gkissel.forgeweave.tool.ToolConstants.Category} off its assembly entry and refuses
     * with the same {@code gui.forgeweave.modifier.unsupported_tool} message the launcher and
     * {@code aoeOnly} gates use. A category predicate rather than an item check, for the same reason
     * {@link #appliesToLaunchers} is one.
     */
    default boolean harvestOnly() {
        return false;
    }

    /**
     * Whether this modifier may only be applied to a projectile (an ammo tool -- the arrow, the
     * shuriken) -- upstream's {@code ModifierAspect.projectileOnly}, i.e.
     * {@code CategoryAspect(Category.PROJECTILE)}. Fins (issue #653, {@code ModFins}) is the first
     * shipped user; {@code ModifierApplication} refuses everything that is not an
     * {@code AmmoToolItem} with the same {@code gui.forgeweave.modifier.unsupported_tool} message
     * the other category gates use.
     */
    default boolean projectileOnly() {
        return false;
    }

    /**
     * The tool's attack damage after this modifier has adjusted it -- silky's only shipped user
     * (issue #107): upstream {@code ModSilktouch#applyEffect} takes a flat 3 off both {@code speed}
     * and {@code attack} (floored at 1) the moment the modifier is applied. Same shape as
     * {@link #miningSpeed}, extended like {@link #durability} to also receive the untouched
     * materials-derived base (issue #295): sharpness's only shipped user needs it, since upstream
     * {@code ModSharpness#applyEffect} seeds its diminishing-returns curve from
     * {@code getOriginalToolStats().attack} -- the tool's original stat, never the running total other
     * modifiers may already have folded in -- then adds the curve's delta on top of that running total.
     * A hook that only saw the running total (as silky's still does; it ignores the parameter) cannot
     * reproduce that without a second stored copy of the original stat ADR-0004 forbids.
     *
     * @param level accumulated application units (see {@link ModifierEntry#level})
     * @param attackDamage the damage the tool's materials and traits alone produced, plus whatever
     *     earlier modifiers in the list already did to it
     * @param baseAttackDamage the tool's untouched materials-derived attack damage
     */
    default float attackDamage(int level, float attackDamage, float baseAttackDamage) {
        return attackDamage;
    }

    /**
     * Chance in {@code [0, 1]} that a point of durability damage is negated outright -- reinforced's
     * only shipped user (issue #107): upstream {@code ModReinforced#onToolDamage} rolls this chance
     * per hit, and a chance {@code >= 1} (its level-5 cap) reads as unbreakable without a separate
     * flag, since the roll then always succeeds. The chance itself is pure; the roll happens in
     * {@code ToolItem#damageItem}, the single choke point for every durability loss (its class
     * javadoc).
     */
    default float durabilityNegationChance(int level) {
        return 0.0F;
    }

    /**
     * Whether this modifier grants vanilla Silk Touch outright -- silky's only shipped user (issue
     * #107). Upstream's {@code ModSilktouch#applyEffect} calls {@code ToolBuilder#addEnchantment}
     * directly rather than routing through the enchanting table (CONTEXT.md: Forgeweave tools aren't
     * enchantable there by default), so {@code ToolAssemblyRecipes} grants the enchantment the same
     * way once a modifier reports it here -- the one hook whose effect lands on a vanilla component
     * ({@code DataComponents#ENCHANTMENTS}) rather than a Forgeweave one, which needs registry access
     * {@link ModifierApplication} deliberately does not have (its own javadoc).
     */
    default boolean grantsSilkTouch(int level) {
        return false;
    }

    /**
     * A vanilla enchantment (and the level to grant it at) this modifier hands out outright -- issue
     * #223's wind burst, the general form of {@link #grantsSilkTouch} that ADR-0004 decision 3
     * anticipates: any later modifier that grants a fixed vanilla enchantment implements this instead
     * of adding another one-off boolean hook, and a JSON-configured behavior can replace it at M6
     * without touching a caller. Applied the same way silky's Silk Touch is --
     * {@code ToolAssemblyRecipes#grantEnchantments}, the one call site with the registry access
     * resolving an enchantment holder needs, which this registry-free interface deliberately does not
     * have. Silky predates this hook and keeps its own boolean rather than being folded in -- nothing
     * needs the generalization to touch code that already works.
     *
     * @param level accumulated application units (see {@link ModifierEntry#level})
     */
    default Optional<EnchantmentGrant> grantedEnchantment(int level) {
        return Optional.empty();
    }

    /** One vanilla enchantment at one level -- {@link #grantedEnchantment}'s return type. */
    record EnchantmentGrant(ResourceKey<Enchantment> enchantment, int level) {}

    // ---------------------------------------------------------------- issue #438 (Width++ / Height++)

    /**
     * Which axis of a harvest tool's mined area an expander widens -- {@link #aoeExpansion}'s return
     * type. Upstream 1.12 expresses the pair as two instances of one {@code ModHarvestSize("width")}/
     * {@code ModHarvestSize("height")} class whose {@code applyEffect} is empty: the modifier is pure
     * marker, and every actual number lives in the event handler that reads it
     * ({@code tools/ToolEvents#onExtraBlockBreak}). {@code tool.AoeHarvest} is Forgeweave's
     * counterpart of that handler, and it owns the per-tool magnitudes for the same reason -- how far
     * an axis grows is a property of the tool, not of the reagent.
     */
    enum AoeAxis {
        /** {@code modHarvestWidth}: the horizontal axis of the mined face. */
        WIDTH,
        /** {@code modHarvestHeight}: the vertical axis of the mined face. */
        HEIGHT
    }

    /**
     * The mined-area axis this modifier expands, or empty for the modifiers that expand none (issue
     * #438). A modifier that reports an axis is refused on any tool with no expandable area at all --
     * upstream's {@code ModifierAspect.aoeOnly}, the {@code Category.AOE} check every
     * {@code AoeToolCore} passes and nothing else does; {@link ModifierApplication} applies that gate
     * off this method alone, so a future third axis needs no second hook.
     *
     * <p>Both shipped expanders are one-shot ({@code ModifierAspect.SingleAspect}, expressed as the
     * recipe's {@code max_level: 1}), so the level is only ever 0 or 1 here -- the parameter exists
     * for the same reason every other hook's does, and a second application would not widen anything
     * twice even if a datapack raised that cap.
     */
    default Optional<AoeAxis> aoeExpansion(int level) {
        return Optional.empty();
    }

    /**
     * Extra modifier slots this modifier grants, on top of the {@value ForgeweaveModifiers#DEFAULT_SLOTS}
     * every tool starts with -- upstream's {@code ModCreative}, which adds its level to the tool's
     * {@code Tags.FREE_MODIFIERS}.
     *
     * <p>Note for issue #107's extra-slot items: a modifier occupies at least one slot itself
     * ({@link #occupiedSlots}), so an extra-slot modifier that should net {@code +1} per level
     * charges a flat slot and returns {@code level + 1} here.
     */
    default int bonusSlots(int level) {
        return 0;
    }

    /**
     * How many of the tool's modifier slots this modifier occupies at {@code level} accumulated
     * application units -- issue #344's 1.12 parity: upstream charges one free modifier per
     * <em>level</em>, not one per modifier. {@code ModifierAspect.MultiAspect#canApply}/{@code
     * updateNBT} spend {@code freeModifierAspect} every time a new level starts (haste, sharpness,
     * fiery, knockback, shulking, smite, bane of arthropods), and the {@code LevelAspect} +
     * {@code freeModifier} pairs {@code ModifierTrait} wires when {@code countPerLevel} is 0
     * (reinforced, mending moss, necrotic, webbed -- and {@code ModBeheading}'s explicit pair)
     * charge one per application, which is one per level too. So the default is the displayed
     * level: one slot from the first unit, another every {@link #unitsPerLevel}.
     *
     * <p>Overridden flat by the modifiers whose upstream aspect set charges differently: luck's
     * {@code FreeFirstModifierAspect} (one slot on first application, later levels free), soulbound's
     * chargeless {@code DataAspect + SingleAspect}, and extra_slot/{@code ModCreative} (no aspects at
     * all -- see {@link #bonusSlots}).
     */
    default int occupiedSlots(int level) {
        return level <= 0 ? 0 : 1 + (level - 1) / Math.max(1, unitsPerLevel());
    }

    // #108 batch: modern-vanilla modifiers (issue #108) -- Forgeweave originals, not upstream ports,
    // so unlike HASTE's numbers above these are recorded as this PR's own decision rather than cited
    // from a clone. Each is still a pure function of level, same rule as every hook above.

    /**
     * Whether a block this tool mines drops its furnace-smelted result instead of its raw drop
     * (Searing). Checked from {@link ForgeweaveModifiers#onBlockDrops}, which has no Item hook to live
     * in -- NeoForge's block-drops event is the only seam that sees the drops before they hit the
     * ground.
     */
    default boolean autoSmelt(int level) {
        return false;
    }

    /**
     * Whether this tool's block drops go straight into the breaking player's inventory instead of the
     * ground (Magnetic Pull -- distinct from issue #102's {@code magnetic} trait, which pulls item
     * entities already on the ground toward the holder rather than skipping the drop entirely).
     */
    default boolean magnetic(int level) {
        return false;
    }

    /**
     * Extra fraction of a mined block's dropped experience this modifier adds, e.g. {@code 0.5} for
     * +50% (Resonant). Summed across a tool's modifiers in
     * {@link ForgeweaveModifiers#bonusExperienceFraction}.
     */
    default float bonusExperienceFraction(int level) {
        return 0.0F;
    }

    /**
     * Bonus added to the holder's {@code minecraft:player.submerged_mining_speed} attribute while this
     * tool is held (Aquadynamic). That attribute defaults to 0.2 -- vanilla's {@code Player#getDigSpeed}
     * multiplies mining speed by it whenever the player's eyes are in water -- so {@code +0.8} restores
     * full (1.0x) speed underwater.
     */
    default float submergedMiningSpeedBonus(int level) {
        return 0.0F;
    }

    /**
     * Bonus added to the holder's {@code minecraft:player.block_interaction_range} attribute, in
     * blocks, while this tool is held (Far Reach).
     */
    default float blockInteractionRangeBonus(int level) {
        return 0.0F;
    }

    // ---------------------------------------------------------------- #106 batch (luck, sharpness, diamond, emerald)
    // (sharpness rides the shared attackDamage hook declared above)

    /**
     * The tool's durability pool after this modifier has adjusted it, threaded like
     * {@link #miningSpeed} except it also receives the untouched materials-derived durability:
     * upstream {@code ModEmerald#applyEffect} adds half of {@code getOriginalToolStats}'s durability,
     * not half of the running total, so a durability hook needs both to stay order-independent the
     * same way a flat addition (upstream {@code ModDiamond}, {@code +500}) already is.
     *
     * @param level accumulated application units
     * @param durability the durability pool so far (base plus every earlier modifier in the list)
     * @param baseDurability the tool's untouched materials-derived durability
     */
    default int durability(int level, int durability, int baseDurability) {
        return durability;
    }

    /**
     * The tool's tier-ladder index (see {@code ForgeweaveModifiers}'s {@code TIER_TAGS}, the vanilla
     * {@code incorrect_for_*_tool} tags in ascending power -- CONTEXT.md: no numeric harvest levels)
     * after this modifier's bump, threaded like {@link #miningSpeed}. Upstream {@code ModDiamond}/
     * {@code ModEmerald} mutate {@code harvestLevel} imperatively and only once each (both are
     * one-shot, {@code max_level} 1), so unlike the stat hooks above, {@code ModifierApplication}
     * calls this only the moment the modifier is first added to the tool -- never on a later,
     * unrelated modifier application -- which is what keeps a flat "+1, capped" bump from ever
     * compounding without needing a stored original tag to re-fold from.
     *
     * @param level accumulated application units
     * @param tierIndex the ladder index so far
     */
    default int toolTierIndex(int level, int tierIndex) {
        return tierIndex;
    }

    /**
     * The Fortune enchantment level this modifier grants, applied to the tool's stored
     * {@code minecraft:enchantments} component ({@code ModifierApplication#resolve}, the one call
     * site with the registry access resolving an enchantment holder needs -- this interface stays as
     * registry-free as {@link ModifierEntry}'s hard rule requires everywhere else). Upstream
     * {@code ModLuck#applyEnchantments} grants Fortune to every harvest-category tool; every
     * Forgeweave tool mines, so that gate is unconditional here. Default 0 (no fortune).
     *
     * @param level the display level already resolved from raw application units by
     *     {@code ModifierRecipe#levelsReached} -- unlike every other hook's raw-unit {@code level},
     *     because upstream's per-level cost isn't always uniform (luck's triangular schedule; issue
     *     #106 review) and resolving that is squarely a recipe/data concern (ADR-0004 decision 1),
     *     not something this registry-free interface can do itself
     */
    default int fortuneLevel(int level) {
        return 0;
    }

    /**
     * As {@link #fortuneLevel}, for Looting -- upstream gates this on {@code Category.WEAPON}
     * (of Forgeweave's three tools, only the hatchet: {@code ForgeweaveModifiers#HASTE}'s attack-speed
     * bonus is gated the same way, via {@code ToolItem}). Default 0 (no looting).
     *
     * @param level see {@link #fortuneLevel}'s javadoc -- an already-resolved display level
     */
    default int lootingLevel(int level) {
        return 0;
    }

    // ---------------------------------------------------------------- issue #163 (combat modifiers batch 2)

    /**
     * This modifier's contribution to the shared per-hit pipeline (ADR-0005 decision 3), or empty for
     * a modifier with no combat behavior. Knockback, shulking and webbed (issue #163) are the first
     * modifiers to use this hook, each handing back one of ADR-0004's M6 parameterized behaviors
     * ({@code KnockbackOnHitSeam}, {@code PotionEffectOnHitSeam}) rather than a bespoke seam class of
     * its own -- the modifier-side counterpart to {@code Trait#bonusDamageAgainst} and friends.
     *
     * <p>{@link ForgeweaveModifiers#COMBAT_SEAMS} calls this once per hit, per modifier on the weapon,
     * so an implementation should return a fresh, already-parameterized seam rather than a shared
     * mutable one.
     *
     * @param level accumulated application units (see {@link ModifierEntry#level})
     */
    default Optional<CombatSeam> combatSeam(int level) {
        return Optional.empty();
    }
}
