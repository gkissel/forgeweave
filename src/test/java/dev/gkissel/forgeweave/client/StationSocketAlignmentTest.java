package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.menu.SideInventorySlots;

/**
 * Guards the 1px socket alignment the issue #79 GUI audit found broken: every station GUI paints a
 * slot as a bevelled <em>socket</em> whose 16x16 hole is inset from the sprite's own corner, so a
 * slot coordinate that is off by one puts every item in that slot one pixel out of its hole. It is
 * invisible in code review, invisible in a unit test that only checks numbers against numbers, and
 * only shows up when somebody looks closely at a screenshot -- which is how it shipped.
 *
 * <p>So this test reads the real PNGs with {@link ImageIO} and measures the sockets. For the two
 * stations whose sockets are painted into the background art ({@code part_builder.png}, {@code
 * stencil_table.png}) it checks each menu slot really is centred in a socket; for the Tool Station,
 * whose sockets are blitted per slot from a sprite because its slot positions move with the
 * selected tab, it checks that sprite's inset; and for the shared side-inventory panel it checks
 * {@link SideInventorySlots#SLOT_INSET} against {@code generic.png}'s slot tile.
 *
 * <p>Menu slot coordinates come from a source scan rather than from constructing the menus: the
 * unit-test classpath has no Minecraft, so no {@code AbstractContainerMenu} can be instantiated
 * here. Same approach as {@code StationScreenTooltipTest} and {@code data.TextureReferenceAuditTest}.
 */
class StationSocketAlignmentTest {

    private static final int ITEM_SIZE = 16;
    private static final String GUI = "src/main/resources/assets/forgeweave/textures/derived/gui/";

    /** {@code new Slot(container, NAME, x, y)} / {@code new OutputSlot(container, NAME, x, y)}. */
    private static final Pattern CONTAINER_SLOT =
            Pattern.compile("new\\s+\\w*Slot\\(container,\\s*(\\w+),\\s*(-?\\d+),\\s*(-?\\d+)\\)");

    private record Slot(String name, int x, int y) {}

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static BufferedImage image(String relativePath) throws IOException {
        Path png = projectRoot().resolve(relativePath);
        assertTrue(Files.exists(png), png + " is missing");
        return ImageIO.read(png.toFile());
    }

    private static List<Slot> containerSlotsOf(String menuClass) throws IOException {
        Path java = projectRoot().resolve("src/main/java/dev/gkissel/forgeweave/menu/" + menuClass + ".java");
        Matcher matcher = CONTAINER_SLOT.matcher(Files.readString(java, StandardCharsets.UTF_8));
        List<Slot> slots = new ArrayList<>();
        while (matcher.find()) {
            slots.add(new Slot(matcher.group(1), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))));
        }
        return slots;
    }

    /**
     * Grows the 16x16 box at {@code (x, y)} outwards while the pixels keep the interior's single
     * colour, giving the socket hole that box sits in. A socket in this art style is one flat
     * interior colour ringed by a bevel, so the first differing pixel in each direction is the
     * bevel -- no need to know whether the socket is upstream's 18x18 input or 26x26 output one.
     */
    private static int[] socketHole(BufferedImage png, int x, int y) {
        int interior = png.getRGB(x, y);
        int left = x;
        int top = y;
        int right = x + ITEM_SIZE - 1;
        int bottom = y + ITEM_SIZE - 1;
        while (left > 0 && png.getRGB(left - 1, y) == interior) {
            left--;
        }
        while (right < png.getWidth() - 1 && png.getRGB(right + 1, y) == interior) {
            right++;
        }
        while (top > 0 && png.getRGB(x, top - 1) == interior) {
            top--;
        }
        while (bottom < png.getHeight() - 1 && png.getRGB(x, bottom + 1) == interior) {
            bottom++;
        }
        return new int[] {left, top, right, bottom};
    }

    /**
     * Asserts the 16x16 item box at {@code (x, y)} is a flat, fully centred socket hole -- the one
     * property that breaks when a slot coordinate or a panel origin drifts by a pixel.
     */
    private static void assertCentredInSocket(BufferedImage png, String what, int x, int y) {
        int interior = png.getRGB(x, y);
        for (int dy = 0; dy < ITEM_SIZE; dy++) {
            for (int dx = 0; dx < ITEM_SIZE; dx++) {
                assertEquals(interior, png.getRGB(x + dx, y + dy),
                        what + ": (" + x + ", " + y + ") is not a flat socket interior at +(" + dx + ", " + dy + ")");
            }
        }
        int[] hole = socketHole(png, x, y);
        int leftMargin = x - hole[0];
        int topMargin = y - hole[1];
        int rightMargin = hole[2] - (x + ITEM_SIZE - 1);
        int bottomMargin = hole[3] - (y + ITEM_SIZE - 1);
        assertEquals(leftMargin, rightMargin,
                what + ": item box at (" + x + ", " + y + ") is off-centre horizontally in its socket");
        assertEquals(topMargin, bottomMargin,
                what + ": item box at (" + x + ", " + y + ") is off-centre vertically in its socket");
        assertNotEquals(interior, png.getRGB(hole[0] - 1, hole[1] - 1),
                what + ": no bevel above/left of the socket at (" + x + ", " + y + ") -- is it a socket at all?");
    }

    @Test
    void partBuilderSlotsSitInTheBakedSockets() throws IOException {
        BufferedImage png = image(GUI + "part_builder.png");
        List<Slot> slots = containerSlotsOf("PartBuilderMenu");
        assertEquals(5, slots.size(), "expected the scan to find the Part Builder's five container slots, got " + slots);
        for (Slot slot : slots) {
            assertCentredInSocket(png, "PartBuilderMenu." + slot.name(), slot.x(), slot.y());
        }
    }

    @Test
    void stencilTableSlotsSitInTheBakedSockets() throws IOException {
        BufferedImage png = image(GUI + "stencil_table.png");
        List<Slot> slots = containerSlotsOf("StencilTableMenu");
        assertEquals(2, slots.size(), "expected the scan to find the Stencil Table's two container slots, got " + slots);
        for (Slot slot : slots) {
            assertCentredInSocket(png, "StencilTableMenu." + slot.name(), slot.x(), slot.y());
        }
    }

    /**
     * The Tool Station's slots move with the selected tab, so its sockets aren't in the background
     * art -- {@code ToolStationScreen} blits the sheet's {@code SlotBorder} sprite at {@code
     * (slot.x - 1, slot.y - 1)}. That {@code -1} is only right while the sprite's hole is inset by
     * one, which is what this checks.
     */
    @Test
    void toolStationSlotSpriteHasAOnePixelInset() throws IOException {
        BufferedImage sheet = image(GUI + "tool_station.png");
        assertCentredInSocket(sheet, "tool_station.png SlotBorder (194, 0)", 194 + 1, 1);
    }

    /**
     * The side panel lays its own tiles out from the menu's first-slot coordinate, so the inset
     * lives in {@link SideInventorySlots#SLOT_INSET} rather than in the art. Issue #79: it was
     * {@code BORDER}, one pixel short, and every side-inventory item sat on its socket's bevel.
     */
    @Test
    void sidePanelSlotInsetMatchesTheGenericSocketTile() throws IOException {
        BufferedImage sheet = image(GUI + "generic.png");
        int tileU = SideInventorySlots.BORDER;
        int tileV = SideInventorySlots.BORDER;
        int inset = SideInventorySlots.SLOT_INSET - SideInventorySlots.BORDER;
        assertCentredInSocket(sheet, "generic.png slot tile", tileU + inset, tileV + inset);
        assertEquals(SideInventorySlots.SLOT_SIZE - ITEM_SIZE, inset * 2,
                "SLOT_INSET must centre the 16x16 item in generic.png's " + SideInventorySlots.SLOT_SIZE + "px slot tile");
    }
}
