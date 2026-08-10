package dev.gkissel.forgeweave.modifier;

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
 * <p>ponytail: exactly the three hooks the framework and its one shipped modifier need, following
 * {@code trait.Trait}'s precedent of adding a hook when a behavior needs it rather than porting
 * upstream's twenty-method {@code IModifier}. ADR-0004's M6 commitment (these classes become the
 * first entries of a parameterized behavior library) is why they stay this narrow: each is a pure
 * function of level, so a JSON-configured generic implementation can replace it without touching a
 * caller.
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
     * Extra modifier slots this modifier grants, on top of the {@value ForgeweaveModifiers#DEFAULT_SLOTS}
     * every tool starts with -- upstream's {@code ModCreative}, which adds its level to the tool's
     * {@code Tags.FREE_MODIFIERS}.
     *
     * <p>Note for issue #107's extra-slot items: a modifier occupies one slot itself
     * ({@link ForgeweaveModifiers#freeSlots}), so an extra-slot modifier that should net {@code +1}
     * per level returns {@code level + 1} here.
     */
    default int bonusSlots(int level) {
        return 0;
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
}
