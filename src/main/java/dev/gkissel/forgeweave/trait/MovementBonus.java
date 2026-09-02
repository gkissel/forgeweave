package dev.gkissel.forgeweave.trait;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * A worn piece makes its wearer move better -- the M6 armor library's
 * {@code movement_bonus(kind, magnitude)} (issue #831), one class over the reference pool's
 * flight-speed and step/jump ideas.
 *
 * <p>No new seam: {@code Trait#armorAttributes} (#680, skyfall's gravity and safe-fall grants)
 * already hands a worn piece the vanilla attribute builder, and 1.21 has a vanilla attribute for
 * every kind below -- so all four are attribute grants that come and go with the piece and vanish
 * while it is Broken, with no tick handler anywhere.
 *
 * <p>Additive across pieces, like {@code projectile_protection}'s knockback resistance: four pieces
 * of the same material grant four times the bonus. {@link Kind#operation} is per attribute --
 * speeds read naturally as a percentage, step height and jump strength as flat block counts.
 *
 * @param kind which attribute is raised
 * @param magnitude by how much, in that kind's own units
 */
public record MovementBonus(Kind kind, float magnitude) implements Trait {

    /** The movement attributes a piece may raise; one constant per vanilla attribute worth granting. */
    public enum Kind {
        /** Walking and sprinting speed, as a fraction of the wearer's total. */
        MOVEMENT_SPEED(Attributes.MOVEMENT_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
        /** Creative/elytra-style flight speed, as a fraction ({@code CreativeFlightHandler}'s grant, #737). */
        FLYING_SPEED(Attributes.FLYING_SPEED, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
        /** How high a block the wearer walks up without jumping, flat. */
        STEP_HEIGHT(Attributes.STEP_HEIGHT, AttributeModifier.Operation.ADD_VALUE),
        /** Jump height, flat. */
        JUMP_STRENGTH(Attributes.JUMP_STRENGTH, AttributeModifier.Operation.ADD_VALUE);

        private final Holder<Attribute> attribute;
        private final AttributeModifier.Operation operation;

        Kind(Holder<Attribute> attribute, AttributeModifier.Operation operation) {
            this.attribute = attribute;
            this.operation = operation;
        }

        public Holder<Attribute> attribute() {
            return attribute;
        }

        public AttributeModifier.Operation operation() {
            return operation;
        }
    }

    @Override
    public void armorAttributes(ResourceLocation id, EquipmentSlot slot, ItemAttributeModifiers.Builder out) {
        out.add(kind.attribute(), new AttributeModifier(id, magnitude, kind.operation()),
                EquipmentSlotGroup.bySlot(slot));
    }
}
