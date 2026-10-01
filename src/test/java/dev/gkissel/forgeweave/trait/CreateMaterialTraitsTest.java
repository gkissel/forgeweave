package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import dev.gkissel.forgeweave.api.combat.CombatSeam;

class CreateMaterialTraitsTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void precisionStopsAtExactlySeventyFivePercentAndRequiresAnEffectiveTool() {
        ItemStack stack = new ItemStack(Items.IRON_PICKAXE);
        stack.set(DataComponents.MAX_DAMAGE, 400);
        stack.setDamageValue(99);
        assertEquals(11.0F, CreateMaterialTraits.PRECISION.miningSpeed(stack, true, 10, 10), 0.001);
        assertEquals(10.0F, CreateMaterialTraits.PRECISION.miningSpeed(stack, false, 10, 10), 0.001);
        stack.setDamageValue(100);
        assertEquals(10.0F, CreateMaterialTraits.PRECISION.miningSpeed(stack, true, 10, 10), 0.001);
    }

    @Test
    void galvanizedSavesTheWholeLossAboutFifteenPercentOfTheTime() {
        RandomSource random = RandomSource.create(1234);
        int saved = 0;
        for (int i = 0; i < 10000; i++) {
            int loss = CreateMaterialTraits.GALVANIZED.durabilityDamage(ItemStack.EMPTY, random, 3, 3);
            assertTrue(loss == 0 || loss == 3);
            if (loss == 0) saved++;
        }
        assertTrue(saved > 1400 && saved < 1600, "saved " + saved);
        assertEquals(0, CreateMaterialTraits.GALVANIZED.durabilityDamage(ItemStack.EMPTY, random, 0, 0));
    }

    @Test
    void clockworkSpeedAddsTwelvePercentOfBaseSpeedAcrossFourPieces() {
        ItemAttributeModifiers.Builder out = ItemAttributeModifiers.builder();
        for (EquipmentSlot slot : List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET)) {
            CreateMaterialTraits.CLOCKWORK_STRIDE.armorAttributes(
                    ResourceLocation.fromNamespaceAndPath("forgeweave", "clockwork_" + slot.getName()), slot, out);
        }
        var entries = out.build().modifiers();
        assertEquals(4, entries.size());
        assertEquals(0.12, entries.stream().mapToDouble(e -> e.modifier().amount()).sum(), 0.0001);
        assertTrue(entries.stream().allMatch(e -> e.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    @Test
    void focusedContributesOneTenPercentArmorPenetrationSeam() {
        List<CombatSeam> seams = new ArrayList<>();
        CreateMaterialTraits.FOCUSED.combatSeams(seams::add);
        assertEquals(1, seams.size());
        assertEquals(0.10F, seams.getFirst().armorPenetration(), 0.0001);
    }
}
