package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.SideInventorySlots;

/**
 * Guards issue #376's info-panel hover parity, in the same shape {@link StationScreenTooltipTest}
 * guards the {@code renderTooltip} override: what a panel row carries is data (assertable), and
 * whether a screen bothers to ask the panel about it is a source scan (the unit-test classpath has
 * no Minecraft client, so no screen can be instantiated here).
 *
 * <p>Both halves have already failed once each in this codebase. Upstream 1.12 hands every info
 * panel a {@code tips} list next to its text ({@code GuiInfoPanel#setText(text, tooltips)}) and
 * every station fills it in; here the hover events went on the Tool Station's modifier rows in issue
 * #258 and nowhere else, so the Part Builder's whole material panel -- upstream's most tooltip-dense
 * screen ({@code GuiPartBuilder:198-230}, a description per stat row) -- had no hover text at all
 * and nothing said so. A row that carries a {@code HoverEvent} no screen asks about is invisible in
 * exactly the same way a missing {@code renderTooltip} is.
 */
class StationPanelHoverTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // Material holds an Ingredient and a block TagKey; same two lines SmelteryTooltipTest needs.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ResourceLocation TRAIT_A =
            ResourceLocation.fromNamespaceAndPath("forgeweave", "sharp");
    private static final ResourceLocation TRAIT_B =
            ResourceLocation.fromNamespaceAndPath("forgeweave", "sturdy");

    private static Material material() {
        return new Material(
                new Material.Head(500, 6.0F, 4.0F),
                new Material.Handle(1.2F, 25),
                50,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                Material.Traits.general(TRAIT_A, TRAIT_B),
                List.of(),
                Ingredient.EMPTY,
                null);
    }

    /** The plain text of a line's SHOW_TEXT hover, or {@code null} when it carries none. */
    private static String hoverText(Component line) {
        HoverEvent hover = line.getStyle().getHoverEvent();
        if (hover == null) {
            return null;
        }
        Component text = hover.getValue(HoverEvent.Action.SHOW_TEXT);
        return text == null ? null : text.getString();
    }

    // ------------------------------------------------------------------ what the rows carry

    @Test
    void everyStatRowExplainsItselfOnHover() {
        Material material = material();
        List<Component> rows = new ArrayList<>(StationText.headStats(material));
        rows.addAll(StationText.handleStats(material));
        rows.addAll(StationText.extraStats(material));

        assertEquals(6, rows.size(), "the three stat groups are upstream's six rows");
        for (Component row : rows) {
            String hover = hoverText(row);
            assertNotNull(hover, "stat row '" + row.getString() + "' carries no hover text; upstream's "
                    + "GuiPartBuilder gives every stat row its stat's getLocalizedDesc()");
            assertTrue(hover.contains(".desc"),
                    "the hover should end in the row's gui.forgeweave.stat.<key>.desc line, got: " + hover);
        }
    }

    @Test
    void theDurabilityRowOfAnAssembledToolExplainsItselfToo() {
        assertNotNull(hoverText(StationText.durabilityStat(120, 160)),
                "the Tool Station's durability row is a stat row like any other");
    }

    @Test
    void aTraitIsOneRowWithItsDescriptionOnHover() {
        List<Component> rows = StationText.traits(null, List.of(TRAIT_A, TRAIT_B));

        assertEquals(2, rows.size(), "issue #376: one visible line per trait, not a name plus a grey "
                + "description line -- that is what halves the trait panel's scroll length");
        for (Component row : rows) {
            String hover = hoverText(row);
            assertNotNull(hover, "trait row '" + row.getString() + "' carries no hover text");
            assertTrue(hover.contains(".description"),
                    "the hover should carry the trait's .description key, got: " + hover);
        }
        assertTrue(rows.get(0).getString().contains(".name"),
                "the visible half of a trait row is its .name key");
    }

    @Test
    void theMaterialPanelGroupsItsStatsUnderUnderlinedHeadings() {
        List<Component> lines = StationText.materialStats(material());

        List<String> headings = lines.stream()
                .filter(line -> line != null && line.getStyle().isUnderlined())
                .map(Component::getString)
                .toList();
        assertEquals(List.of("tooltip.forgeweave.stat_type.head", "tooltip.forgeweave.stat_type.handle",
                        "tooltip.forgeweave.stat_type.extra"), headings,
                "upstream's GuiPartBuilder#setDisplayForMaterial heads each stat group with an "
                        + "underlined stat-type name -- the same stat.<type>.name issue #379 already "
                        + "put over a part's item tooltip, so both surfaces share one key");

        assertEquals(2, lines.stream().filter(line -> line == null).count(),
                "a blank line between groups and none after the last, as upstream trims");
        assertFalse(lines.get(lines.size() - 1) == null, "no trailing spacer");
    }

    // ------------------------------------------------------------------ who asks about them

    private static Path clientDir() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate.resolve("src/main/java/dev/gkissel/forgeweave/client");
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    @Test
    void everyScreenWithAnInfoPanelAsksItWhatIsHovered() throws IOException {
        List<String> offenders = new ArrayList<>();
        List<String> scanned = new ArrayList<>();
        try (Stream<Path> files = Files.walk(clientDir())) {
            for (Path java : files.filter(p -> p.toString().endsWith("Screen.java")).sorted().toList()) {
                String source = Files.readString(java, StandardCharsets.UTF_8);
                if (!source.contains("InfoPanel.render(")) {
                    continue;
                }
                scanned.add(java.getFileName().toString());
                if (!source.contains("InfoPanel.hoveredStyle(")) {
                    offenders.add(java.getFileName() + " draws an InfoPanel but never calls "
                            + "InfoPanel.hoveredStyle, so every hover event StationText hangs on its "
                            + "rows is dead weight (issue #376)");
                }
            }
        }
        assertTrue(scanned.contains("PartBuilderScreen.java") && scanned.contains("ToolStationScreen.java"),
                "expected the scan to find both info-panel screens, found " + scanned);
        assertTrue(offenders.isEmpty(), String.join("\n", offenders));
    }

    // ------------------------------------------------------------------ slider visibility & geometry

    /** Vanilla's font, which is what both panels are measured against at runtime. */
    private static final int LINE_HEIGHT = 9;

    @Test
    void theInfoPanelSliderAppearsExactlyOnOverflow() {
        // A 83px-tall panel less its 5px insets, no caption: upstream's own natural panel.
        int textHeight = InfoPanel.HEIGHT - 10;
        int fits = 0;
        while (!InfoPanel.needsSlider(LINE_HEIGHT, textHeight, fits + 1)) {
            fits++;
        }

        assertTrue(fits >= 7, "a natural-height panel should hold at least seven lines, held " + fits);
        assertFalse(InfoPanel.needsSlider(LINE_HEIGHT, textHeight, fits),
                "the last line that fits must not bring a slider with it");
        assertTrue(InfoPanel.needsSlider(LINE_HEIGHT, textHeight, fits + 1),
                "one line past the bottom is exactly when upstream shows its slider");
        assertFalse(InfoPanel.needsSlider(LINE_HEIGHT, textHeight, 0), "an empty panel has no slider");
    }

    @Test
    void theInfoPanelKnobSpansItsTrackAndSurvivesARoundTrip() {
        int trackY = 40;
        int trackHeight = InfoPanel.HEIGHT - 28;
        int maxScroll = 12;

        assertEquals(trackY, InfoPanel.knobY(trackY, trackHeight, 0, maxScroll), "scroll 0 pins the knob to the top");
        assertEquals(trackY + trackHeight - 5, InfoPanel.knobY(trackY, trackHeight, maxScroll, maxScroll),
                "the last scroll pins the 5px knob to the bottom");
        assertEquals(trackY, InfoPanel.knobY(trackY, trackHeight, 4, 0),
                "a panel that cannot scroll has nowhere to put the knob but the top");

        for (int scroll = 0; scroll <= maxScroll; scroll++) {
            int y = InfoPanel.knobY(trackY, trackHeight, scroll, maxScroll);
            assertEquals(scroll, InfoPanel.scrollAt(trackY, trackHeight, maxScroll, y + 2.5),
                    "dragging the knob to where it already is must not move it");
        }
        assertEquals(0, InfoPanel.scrollAt(trackY, trackHeight, maxScroll, trackY - 200),
                "a drag off the top of the track clamps rather than wrapping");
        assertEquals(maxScroll, InfoPanel.scrollAt(trackY, trackHeight, maxScroll, trackY + 500),
                "and so does one off the bottom");
    }

    @Test
    void theSideInventorySliderAppearsExactlyOnOverflow() {
        int parentHeight = 174;
        int slotY = 40;

        // A single chest is 27 slots over six columns: five rows, which fit under a Tool Station.
        int rows = SideInventorySlots.rows(27);
        assertEquals(rows, SideInventoryPanel.visibleRows(rows, parentHeight, slotY),
                "27 slots fit, so there is nothing to scroll and no slider to draw");

        // A double chest is 54: nine rows, more than fit, so upstream widens the panel and shows one.
        int doubleRows = SideInventorySlots.rows(54);
        assertTrue(SideInventoryPanel.visibleRows(doubleRows, parentHeight, slotY) < doubleRows,
                "54 slots overflow a 174px station, which is what puts the slider on screen");
    }

    @Test
    void theSideInventoryKnobSpansItsTrackAndSurvivesARoundTrip() {
        int trackY = 30;
        int trackHeight = 5 * SideInventorySlots.SLOT_SIZE;
        int maxRow = 4;

        assertEquals(trackY, SideInventoryPanel.knobY(trackY, trackHeight, 0, maxRow));
        assertEquals(trackY + trackHeight - 15, SideInventoryPanel.knobY(trackY, trackHeight, maxRow, maxRow),
                "the last row pins the 15px knob to the bottom of the track");
        for (int row = 0; row <= maxRow; row++) {
            int y = SideInventoryPanel.knobY(trackY, trackHeight, row, maxRow);
            assertEquals(row, SideInventoryPanel.scrollRowAt(trackY, trackHeight, maxRow, y + 7.5),
                    "dragging the knob to where it already is must not move it");
        }
    }

    /** The slider column costs width; the station still has to fit a small window (issue #88's rule). */
    @Test
    void aScrollingSidePanelStillFitsATypicalWindow() {
        int window = 480;
        int left = (window - 176) / 2;
        int panelLeft = left + SideInventorySlots.LEFT_SLOT_X - SideInventorySlots.SLOT_INSET
                - SideInventoryPanel.SLIDER_WIDTH;
        assertTrue(panelLeft >= 0,
                "a left-hand side panel grows leftwards by the slider's width when it needs one; at "
                        + window + "px that puts its edge at " + panelLeft);
    }

    /** Sanity check that the hover-text helper above is reading a real event, not always null. */
    @Test
    void theHoverHelperSeesAPlainRow() {
        assertEquals(null, hoverText(Component.literal("x").withStyle(ChatFormatting.GRAY)));
    }
}
