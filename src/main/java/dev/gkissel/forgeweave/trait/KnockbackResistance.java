package dev.gkissel.forgeweave.trait;

import dev.gkissel.forgeweave.api.trait.Trait;

/**
 * A held tool plants its wielder: knockback moves them less -- the datapack face of
 * {@link Trait#knockbackResistance()} (issue #1091).
 *
 * <p>The hook already existed and two hardcoded traits already used it ({@code heavy} at
 * {@code 1.0}, full immunity, and {@code verdant_ward} at {@code 0.15}); what was missing was a way
 * for a {@code trait_definition} to reach it, which is why #1077 reached for {@code damage_floor}
 * instead and shipped a drawback under a protective name. One float, added straight onto the
 * vanilla {@code KNOCKBACK_RESISTANCE} attribute by {@code ToolItem#getDefaultAttributeModifiers},
 * where {@code 1} is full immunity.
 *
 * <p>Held, not worn: this is the tool-side attribute, the same scope {@code heavy} and
 * {@code verdant_ward} have always had. A worn piece gets its knockback resistance from its
 * plating's own {@code knockback_resistance} stat, the modifier of the same name and
 * {@code projectile_protection} instead, so a general trait on a material that makes both is a
 * tool-half grant -- the standing rule for general traits (SCOPE.md D17: hooks that do not apply
 * simply never fire).
 *
 * @param resistance 0..1, flat, added to the wielder's knockback resistance attribute while held
 */
public record KnockbackResistance(float resistance) implements Trait {

    @Override
    public float knockbackResistance() {
        return resistance;
    }
}
