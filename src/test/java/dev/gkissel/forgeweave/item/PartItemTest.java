package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * Pins the one piece of real logic in the item slice: a part's tooltip reflects whatever material
 * id is (or isn't) stored in its {@link ForgeweaveDataComponents#MATERIAL} component.
 */
class PartItemTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PartItem pickaxeHead() {
        return (PartItem) ForgeweaveItems.PART_PICKAXE_HEAD.get();
    }

    @Test
    void tooltipShowsTranslatableMaterialNameWhenComponentIsSet() {
        ItemStack stack = new ItemStack(pickaxeHead());
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), ResourceLocation.fromNamespaceAndPath("forgeweave", "wood"));

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        assertEquals(List.of(Component.translatable("material.forgeweave.wood")), tooltip);
    }

    @Test
    void tooltipStaysEmptyWhenNoMaterialComponentIsSet() {
        ItemStack stack = new ItemStack(pickaxeHead());

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        assertTrue(tooltip.isEmpty());
    }
}
