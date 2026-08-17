package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.CrossbowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolArt;

/**
 * M3.5 issue #400: the three draw-state item properties a bow's model branches on. The holder-driven
 * two are pinned at their no-holder branch -- an item frame, a JEI slot, a GUI render with no player
 * -- which is where a bow must read as undrawn; their drawing branch is the pure
 * {@code BowItem#drawbackProgress(ItemStack, int)} formula {@code ShortbowStatsTest} and
 * {@code ForgeBowStatsTest} already pin, mapped through {@link ToolArt#drawStage} ({@code
 * BowDrawArtTest}).
 */
class BowItemPropertiesTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Nothing is being drawn when nobody is holding it: {@code BowCore}'s own {@code entityIn == null}
     * branch. {@code pulling} at 0 is what keeps every {@code {"pulling": 1, ...}} override from
     * matching, so an unheld bow resolves the plain undrawn model however far {@code pull} reads.
     */
    @Test
    void aBowWithNoHolderIsNeitherPullingNorPulled() {
        for (var bow : List.of(ForgeweaveItems.TOOL_SHORTBOW, ForgeweaveItems.TOOL_LONGBOW,
                ForgeweaveItems.TOOL_CROSSBOW)) {
            ItemStack stack = new ItemStack(bow.get());
            assertEquals(0.0f, ForgeweaveItemProperties.pulling(stack, null), 0.0f);
            assertEquals(0.0f, ForgeweaveItemProperties.pull(stack, null), 0.0f);
        }
    }

    /**
     * The loaded flag is the stack's own state, so unlike the two above it does not need a holder --
     * the deliberate deviation from upstream's {@code entityIn != null && isLoaded(stack)}, without
     * which a loaded crossbow in an item frame renders uncranked.
     */
    @Test
    void loadedReadsTheStacksOwnFlagWithOrWithoutAHolder() {
        ItemStack crossbow = new ItemStack(ForgeweaveItems.TOOL_CROSSBOW.get());
        assertEquals(0.0f, ForgeweaveItemProperties.loaded(crossbow), 0.0f, "a fresh crossbow holds no crank");

        CrossbowItem.setLoaded(crossbow, true);
        assertEquals(1.0f, ForgeweaveItemProperties.loaded(crossbow), 0.0f);

        CrossbowItem.setLoaded(crossbow, false);
        assertEquals(0.0f, ForgeweaveItemProperties.loaded(crossbow), 0.0f,
                "upstream writes the flag false rather than removing it, and so does this");
    }

    /** A bow that is not a crossbow never carries the component, so it never resolves the loaded model. */
    @Test
    void onlyACrossbowIsEverLoaded() {
        ItemStack shortbow = new ItemStack(ForgeweaveItems.TOOL_SHORTBOW.get());
        assertEquals(0.0f, ForgeweaveItemProperties.loaded(shortbow), 0.0f);
        assertEquals(null, shortbow.get(ForgeweaveDataComponents.CROSSBOW_LOADED.get()));
    }

    /** Broken is unchanged by #400, and still the property every tool -- bow or not -- branches on. */
    @Test
    void brokenStillReadsTheComponent() {
        ItemStack shortbow = new ItemStack(ForgeweaveItems.TOOL_SHORTBOW.get());
        assertEquals(0.0f, ForgeweaveItemProperties.broken(shortbow), 0.0f);
        shortbow.set(ForgeweaveDataComponents.BROKEN.get(), true);
        assertEquals(1.0f, ForgeweaveItemProperties.broken(shortbow), 0.0f);
    }
}
