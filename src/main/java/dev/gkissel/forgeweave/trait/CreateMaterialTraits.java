package dev.gkissel.forgeweave.trait;

import java.util.function.Consumer;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.api.combat.CombatDefense;
import dev.gkissel.forgeweave.api.combat.CombatSeam;
import dev.gkissel.forgeweave.api.combat.DefendedBlow;
import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.combat.Protection;
import dev.gkissel.forgeweave.item.ArmorPieceItem;

/** Material bonuses for Create's four crafting materials. */
public final class CreateMaterialTraits {
    public static final Trait GALVANIZED = new Trait() {
        @Override
        public int durabilityDamage(ItemStack stack, RandomSource random, int originalAmount, int amount) {
            return !(stack.getItem() instanceof ArmorPieceItem)
                    && amount > 0 && random.nextFloat() < 0.15F ? 0 : amount;
        }
    };

    public static final Trait GALVANIZED_GUARD = new Trait() {
        @Override
        public float armorDurabilityNegationChance() {
            return 0.05F;
        }
    };

    public static final Trait PRECISION = new Trait() {
        @Override
        public float miningSpeed(ItemStack stack, boolean effective, float originalSpeed, float speed) {
            return effective && stack.getMaxDamage() > 0
                    && stack.getDamageValue() < stack.getMaxDamage() * 0.25F
                    ? speed + originalSpeed * 0.10F : speed;
        }
    };

    public static final Trait INDUSTRIAL = new Trait() {
        @Override
        public float breakSpeed(ItemStack stack, Player player, BlockState state, float originalSpeed, float speed) {
            return state.is(BlockTags.STONE_ORE_REPLACEABLES) ? speed + originalSpeed * 0.15F : speed;
        }
    };

    public static final Trait CLOCKWORK_STRIDE = armorAttribute(Attributes.MOVEMENT_SPEED, 0.03,
            AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
    public static final Trait ANCHORED = armorAttribute(Attributes.KNOCKBACK_RESISTANCE, 0.05,
            AttributeModifier.Operation.ADD_VALUE);

    private static final CombatSeam FOCUSED_SEAM = new CombatSeam() {
        @Override
        public float armorPenetration() {
            return 0.10F;
        }
    };

    public static final Trait FOCUSED = new Trait() {
        @Override
        public void combatSeams(Consumer<CombatSeam> out) {
            out.accept(FOCUSED_SEAM);
        }
    };

    public static final Trait CRYSTAL_WARD = new Trait() {
        private final Protection protection = Protection.against(TagKey.create(Registries.DAMAGE_TYPE,
                ResourceLocation.fromNamespaceAndPath("forgeweave", "magic_protection")), 1.0F);

        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            protection.onDefend(defense, blow);
        }
    };

    private static Trait armorAttribute(Holder<Attribute> attribute,
            double amount, AttributeModifier.Operation operation) {
        return new Trait() {
            @Override
            public void armorAttributes(ResourceLocation id, EquipmentSlot slot, ItemAttributeModifiers.Builder out) {
                out.add(attribute, new AttributeModifier(id, amount, operation), EquipmentSlotGroup.bySlot(slot));
            }
        };
    }

    private CreateMaterialTraits() {}
}
