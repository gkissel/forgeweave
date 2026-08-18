package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * Issue #446 (parity audit T15). Upstream 1.12 prefixes a tool's and a part's display name with the
 * material(s) it is made of: {@code ToolCore#getItemStackDisplayName} (ToolCore.java:379-393) feeds
 * its repair parts' materials through {@code Material#getCombinedItemName} (Material.java:464-489),
 * and {@code ToolPart#getItemStackDisplayName} (ToolPart.java:190-204) does the same with its one
 * material. Distinct materials are hyphenated; a repeat is named once ({@code LinkedHashSet}).
 *
 * <p>No mod translation is loaded in a unit test, so every key resolves to itself and the assertions
 * below read as {@code "<material key> <item key>"} -- which is exactly the structure under test.
 * The one material that carries a real prefix entry ({@code wood}) is pinned by key instead.
 */
class MaterialPrefixedNameTest {

    private static final ResourceLocation STONE = ResourceLocation.fromNamespaceAndPath("forgeweave", "stone");
    private static final ResourceLocation WOOD = ResourceLocation.fromNamespaceAndPath("forgeweave", "wood");
    private static final ResourceLocation IRON = ResourceLocation.fromNamespaceAndPath("forgeweave", "iron");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String itemKey(ItemStack stack) {
        return stack.getItem().getDescriptionId();
    }

    @Test
    void partNameIsPrefixedByItsMaterial() {
        ItemStack stack = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), STONE);

        assertEquals("material.forgeweave.stone " + itemKey(stack), stack.getHoverName().getString());
    }

    /** A part with no material component -- the creative-tab and JEI ghost stacks -- stays plain. */
    @Test
    void partWithoutMaterialKeepsItsPlainName() {
        ItemStack stack = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());

        assertEquals(itemKey(stack), stack.getHoverName().getString());
    }

    /** Upstream's repair parts are the HEAD slots, so a pickaxe is named after its head only. */
    @Test
    void toolNameIsPrefixedByItsHeadMaterialOnly() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stack.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(STONE, Optional.of(WOOD), Optional.of(WOOD), List.of(STONE, WOOD, WOOD)));

        assertEquals("material.forgeweave.stone " + itemKey(stack), stack.getHoverName().getString());
    }

    /**
     * The hammer's three HEAD slots (hammer head plus two large plates, {@code ToolConstants#HAMMER})
     * are upstream's {@code Hammer#getRepairParts() == {1, 2, 3}}: distinct ones are hyphenated in
     * slot order, and the tough tool rod in slot 0 never appears because it is not a head.
     */
    @Test
    void multiHeadToolHyphenatesItsDistinctHeadMaterials() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_HAMMER.get());
        stack.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(STONE, Optional.empty(), Optional.of(WOOD), List.of(WOOD, STONE, IRON, IRON)));

        assertEquals("material.forgeweave.stone-material.forgeweave.iron " + itemKey(stack),
                stack.getHoverName().getString());
    }

    /** {@code Sets.newLinkedHashSet} upstream: one material across three head slots is named once. */
    @Test
    void multiHeadToolWithOneMaterialIsNamedOnce() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_HAMMER.get());
        stack.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(STONE, Optional.empty(), Optional.of(WOOD), List.of(WOOD, STONE, STONE, STONE)));

        assertEquals("material.forgeweave.stone " + itemKey(stack), stack.getHoverName().getString());
    }

    /** A componentless tool (creative tab, JEI ghost) keeps its plain name. */
    @Test
    void toolWithoutMaterialsKeepsItsPlainName() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());

        assertEquals(itemKey(stack), stack.getHoverName().getString());
    }

    /** A bow is named after its limbs ({@code ShortBow#getRepairParts() == {0, 1}}), not its string. */
    @Test
    void bowIsNamedAfterItsLimbsAndNotItsString() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_SHORTBOW.get());
        stack.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(STONE, Optional.empty(), Optional.empty(), List.of(STONE, STONE, WOOD)));

        assertEquals("material.forgeweave.stone " + itemKey(stack), stack.getHoverName().getString());
    }

    /**
     * The {@code material.<id>.prefix} hook itself: wood's entry ("Wooden %2$s") replaces the whole
     * construction, so the name has to be built on that key with the material name and the item name
     * as its two arguments. Upstream {@code Material#getLocalizedItemName} (Material.java:439-450).
     */
    @Test
    void nameIsBuiltOnTheMaterialsPrefixKey() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stack.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(WOOD, Optional.of(WOOD), Optional.of(WOOD), List.of(WOOD, WOOD, WOOD)));

        assertEquals(Component.translatableWithFallback("material.forgeweave.wood.prefix", "%s %s",
                        MaterialDisplay.plainName(WOOD), Component.translatable(itemKey(stack))),
                stack.getHoverName());
    }

    /** An anvil or Tool Station rename still wins -- {@code ItemStack#getHoverName} decides that. */
    @Test
    void customNameTakesPrecedenceOverTheMaterialPrefix() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stack.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(),
                new ToolMaterials(STONE, Optional.of(WOOD), Optional.of(WOOD), List.of(STONE, WOOD, WOOD)));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Rocky"));

        assertEquals("Rocky", stack.getHoverName().getString());
    }
}
