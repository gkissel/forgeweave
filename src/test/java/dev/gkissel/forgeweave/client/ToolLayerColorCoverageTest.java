package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Regression for issue #669: {@code ToolStationScreen#renderToolLayers} unboxes
 * {@code TOOL_LAYER_COLORS.get(role)} straight into an {@code int}, so a {@link
 * ToolConstants.Role} value with no map entry is a client-crashing NPE the moment the sidebar
 * renders that tool's layer preview -- which is exactly what #653's three arrow roles did.
 * Coverage-shaped, like {@code ItemColorCoverageTest}: any future role added without a 1.12-parity
 * preview tint fails the build here instead of a real client.
 */
class ToolLayerColorCoverageTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyRoleHasALayerPreviewTint() {
        for (ToolConstants.Role role : ToolConstants.Role.values()) {
            assertTrue(ToolStationScreen.TOOL_LAYER_COLORS.containsKey(role),
                    "TOOL_LAYER_COLORS has no preview tint for role " + role
                            + "; renderToolLayers would NPE rendering that tool's tab preview"
                            + " (issue #669)");
        }
    }
}
