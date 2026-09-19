package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Issue #1066's art fallback: a tool registered from another mod ships its layers at predictable
 * paths in its own namespace and needs no row in any Forgeweave table. Forgeweave's own tools keep
 * the two-tree split between authored and ported art, which is what the first test here pins --
 * the fallback would be worthless if it also moved a shipped sprite.
 */
class ToolArtConventionTest {

    private static final List<ToolConstants.PartSlot> TWO_HEADED = List.of(
            new ToolConstants.PartSlot(ToolConstants.Role.HANDLE, "tool_handle"),
            new ToolConstants.PartSlot(ToolConstants.Role.HEAD, "pickaxe_head"),
            new ToolConstants.PartSlot(ToolConstants.Role.HEAD, "pickaxe_head"));

    @Test
    void aForgeweaveToolResolvesThroughForgeweavesOwnTwoTrees() {
        assertEquals(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, ToolArt.layer("pickaxe", "head")),
                ToolArt.layerTexture("pickaxe", "head"),
                "an unnamespaced tool is Forgeweave's own and keeps whichever tree its sprite lives in");
    }

    @Test
    void aRegisteredToolResolvesByConvention() {
        assertEquals(ResourceLocation.fromNamespaceAndPath("someaddon", "item/dirk_head"),
                ToolArt.layerTexture("someaddon:dirk", "head"),
                "a registered tool's layers live at <namespace>:item/<tool>_<layer>");
    }

    /** The layer names an addon has to name its files after come off the part roles, not a table. */
    @Test
    void layerNamesNumberTheSecondOccurrenceOfARole() {
        assertEquals(List.of("handle", "head", "head2"), ToolArt.layers(TWO_HEADED));
    }

    @Test
    void aRegisteredToolBreaksItsHead() {
        assertEquals("head", ToolArt.brokenLayer("someaddon:dirk"),
                "a registered tool is in no broken-art table, so it follows the rule the table follows");
        assertNull(ToolArt.brokenLayer("sharpening_kit"),
                "and an unlisted Forgeweave item still draws no broken art");
    }
}
