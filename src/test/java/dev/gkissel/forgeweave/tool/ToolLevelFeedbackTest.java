package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Pins {@link ToolLevelFeedback#message}'s key selection against Tinkers' Tool Leveling's {@code
 * CommonProxy#sendLevelUpMessage} (tool-leveling-1.12, pinned commit in NOTICE.md; issue #922,
 * D-M7-8). Upstream picks the per-level key with {@code I18n.canTranslate} at the point of use, an
 * idiom that cannot run on a dedicated server in 1.21.1 -- this asserts the explicit-constant
 * replacement never falls through to a raw key, since {@link ToolLevelFeedback#onLevelUp} sends the
 * result over a mock connection this test cannot read a packet back from ({@code
 * ToolLevelFeedbackGameTests} covers the chime through a real dedicated-server player instead).
 */
class ToolLevelFeedbackTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String key(Component component) {
        assertTrue(component.getContents() instanceof TranslatableContents,
                "expected a translatable component, got " + component.getContents());
        return ((TranslatableContents) component.getContents()).getKey();
    }

    /** Level 5 falls in the 2-11 range: its own key, not the generic one. */
    @Test
    void levelFivePicksItsOwnKey() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        assertEquals("message.forgeweave.levelup.5", key(ToolLevelFeedback.message(stack, 5)));
    }

    /** Level 40 is well outside 2-11: the generic key. */
    @Test
    void levelFortyPicksTheGenericKey() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        assertEquals("message.forgeweave.levelup.generic", key(ToolLevelFeedback.message(stack, 40)));
    }

    /** Level 1 -- the very first level-up -- has no key of its own in upstream's own en_us.lang either. */
    @Test
    void levelOneFallsBackToGeneric() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        assertEquals("message.forgeweave.levelup.generic", key(ToolLevelFeedback.message(stack, 1)));
    }

    /** The boundary levels of the specific range, 2 and 11, both pick their own key. */
    @Test
    void theSpecificRangeBoundariesPickTheirOwnKey() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        assertEquals("message.forgeweave.levelup.2", key(ToolLevelFeedback.message(stack, 2)));
        assertEquals("message.forgeweave.levelup.11", key(ToolLevelFeedback.message(stack, 11)));
        assertEquals("message.forgeweave.levelup.generic", key(ToolLevelFeedback.message(stack, 12)));
    }

    /** Upstream wraps the whole line dark aqua ({@code CommonProxy#sendLevelUpMessage}). */
    @Test
    void theMessageIsStyledDarkAqua() {
        ItemStack stack = new ItemStack(Items.DIAMOND_PICKAXE);
        TextColor expected = TextColor.fromLegacyFormat(ChatFormatting.DARK_AQUA);
        assertEquals(expected, ToolLevelFeedback.message(stack, 5).getStyle().getColor());
    }
}
