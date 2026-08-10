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
}
