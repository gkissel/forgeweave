package dev.gkissel.forgeweave.api.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/**
 * The registration window fails loudly (issue #1066), the way {@code ModifierRegistryTest} and
 * {@code TraitRegistryTest} pin the same rule for the other two api packages. A silently dropped
 * registration is the failure worth preventing: an addon author would see a tool missing from the
 * Tool Station with nothing in the log to say why.
 */
class ForgeweaveToolsTest {

    private static final ResourceLocation TOOL = ResourceLocation.fromNamespaceAndPath("someaddon", "dirk");
    private static final ResourceLocation BLADE = ResourceLocation.fromNamespaceAndPath("someaddon", "dirk_blade");

    private static ToolDefinition definition(ResourceLocation id) {
        return ToolDefinition.builder(id, ToolFamily.MELEE).part(PartRole.HEAD, BLADE).weapon().build();
    }

    @BeforeEach
    @AfterEach
    void reopenTheWindow() {
        ForgeweaveTools.resetForTests();
    }

    @Test
    void aRegisteredToolLandsInRegistrationOrder() {
        ForgeweaveTools.registerTool(definition(TOOL), () -> Items.STICK);

        assertEquals(1, ForgeweaveTools.tools().size());
        assertEquals(TOOL, ForgeweaveTools.tools().get(0).definition().id());
    }

    @Test
    void aSecondRegistrationOfOneIdIsRefused() {
        ForgeweaveTools.registerTool(definition(TOOL), () -> Items.STICK);

        assertThrows(IllegalArgumentException.class,
                () -> ForgeweaveTools.registerTool(definition(TOOL), () -> Items.STICK));
    }

    @Test
    void aPartCannotTakeAnIdAToolAlreadyHas() {
        ForgeweaveTools.registerTool(definition(TOOL), () -> Items.STICK);

        assertThrows(IllegalArgumentException.class, () -> ForgeweaveTools.registerPart(
                PartDefinition.castOnly(TOOL, PartKind.HEAD, () -> Items.STICK, ForgeweaveTools.INGOT_VALUE)));
    }

    @Test
    void registeringAfterTheTablesHaveBeenReadThrows() {
        assertTrue(ForgeweaveTools.tools().isEmpty());
        assertTrue(ForgeweaveTools.isFrozen(), "reading a table closes the window");

        assertThrows(IllegalStateException.class,
                () -> ForgeweaveTools.registerTool(definition(TOOL), () -> Items.STICK));
        assertThrows(IllegalStateException.class, () -> ForgeweaveTools.registerPart(
                PartDefinition.castOnly(BLADE, PartKind.HEAD, () -> Items.STICK, ForgeweaveTools.INGOT_VALUE)));
    }

    @Test
    void aToolNeedsAtLeastOnePartSlot() {
        assertThrows(IllegalArgumentException.class,
                () -> ToolDefinition.builder(TOOL, ToolFamily.MELEE).build());
    }

    @Test
    void aPartCostsSomething() {
        Item stick = Items.STICK;
        assertThrows(IllegalArgumentException.class,
                () -> PartDefinition.castOnly(BLADE, PartKind.HEAD, () -> stick, 0));
    }
}
