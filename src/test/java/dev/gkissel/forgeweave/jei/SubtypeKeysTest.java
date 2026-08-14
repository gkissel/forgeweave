package dev.gkissel.forgeweave.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * Pins {@link SubtypeKeys}, the pure logic behind {@link ForgeweaveJeiPlugin#registerItemSubtypes}
 * (issue #307): two stacks that differ only by material/texture must produce different keys, or JEI
 * collapses them into one entry -- the exact regression this issue reports. Deliberately touches no
 * JEI class, the same split {@code JeiRecipesTest} already documents (JEI is compileOnly, see
 * build.gradle), so this needs no more than the same Minecraft bootstrap the rest of the suite uses.
 */
class SubtypeKeysTest {

    private static final ResourceLocation WOOD_ID = ResourceLocation.fromNamespaceAndPath("forgeweave", "wood");
    private static final ResourceLocation STONE_ID = ResourceLocation.fromNamespaceAndPath("forgeweave", "stone");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ------------------------------------------------------------------ part()

    @Test
    void partKeyDiffersByMaterial() {
        ItemStack wood = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        wood.set(ForgeweaveDataComponents.MATERIAL.get(), WOOD_ID);
        ItemStack stone = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        stone.set(ForgeweaveDataComponents.MATERIAL.get(), STONE_ID);

        assertNotEquals(SubtypeKeys.part(wood), SubtypeKeys.part(stone));
        assertEquals(WOOD_ID.toString(), SubtypeKeys.part(wood));
    }

    @Test
    void partKeyIsNullWithNoMaterialComponent() {
        ItemStack stack = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());

        assertNull(SubtypeKeys.part(stack));
    }

    // ------------------------------------------------------------------ tool()

    @Test
    void toolKeyDiffersByMaterial() {
        ItemStack wood = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        wood.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(WOOD_ID, Optional.empty(), WOOD_ID, List.of(WOOD_ID, WOOD_ID)));
        ItemStack stone = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stone.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(STONE_ID, Optional.empty(), STONE_ID, List.of(STONE_ID, STONE_ID)));

        assertNotEquals(SubtypeKeys.tool(wood), SubtypeKeys.tool(stone));
    }

    /** A pickaxe with a stone head and a wood handle must differ from an all-wood one -- not just "has stone". */
    @Test
    void toolKeyCoversEveryPartNotJustTheHead() {
        ItemStack allWood = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        allWood.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(WOOD_ID, Optional.empty(), WOOD_ID, List.of(WOOD_ID, WOOD_ID)));
        ItemStack stoneHeadWoodHandle = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stoneHeadWoodHandle.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(STONE_ID, Optional.empty(), WOOD_ID, List.of(STONE_ID, WOOD_ID)));

        assertNotEquals(SubtypeKeys.tool(allWood), SubtypeKeys.tool(stoneHeadWoodHandle));
    }

    @Test
    void toolKeyIsNullWithNoMaterialsComponent() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());

        assertNull(SubtypeKeys.tool(stack));
    }

    // ------------------------------------------------------------------ texture()

    @Test
    void textureKeyDiffersByWood() {
        ItemStack oak = new ItemStack(ForgeweaveItems.PART_BUILDER.get());
        oak.set(ForgeweaveDataComponents.TEXTURE.get(), ResourceLocation.withDefaultNamespace("oak_log"));
        ItemStack spruce = new ItemStack(ForgeweaveItems.PART_BUILDER.get());
        spruce.set(ForgeweaveDataComponents.TEXTURE.get(), ResourceLocation.withDefaultNamespace("spruce_log"));

        assertNotEquals(SubtypeKeys.texture(oak), SubtypeKeys.texture(spruce));
    }

    @Test
    void textureKeyIsNullWithNoTextureComponent() {
        ItemStack stack = new ItemStack(ForgeweaveItems.PART_BUILDER.get());

        assertNull(SubtypeKeys.texture(stack));
    }
}
