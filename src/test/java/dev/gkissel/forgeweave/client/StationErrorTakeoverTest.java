package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.menu.StationMenu;

/**
 * Guards issue #378: what a station does with a rejection, and how the Part Builder words its
 * Material Value line. Same split {@link StationPanelHoverTest} uses and for the same reason -- the
 * unit-test classpath has no Minecraft client, so the text a station produces is assertable while
 * "does the screen actually apply it" is a source scan.
 *
 * <p>Both halves had already gone wrong. Upstream's {@code error(...)}/{@code warning(...)} pair
 * <em>replaces</em> an info panel's caption and body and blanks the trait panel next to it
 * ({@code GuiToolStation:562-575}); Forgeweave instead appended the message in red to the end of a
 * panel that already held the tool's full stat, material and modifier block, so on anything but the
 * shortest tool the reason was below the fold and the player saw an unexplained empty output slot.
 * The Part Builder had no error path at all, and its Material Value line printed a raw shard-unit
 * count where upstream prints ingots and the material's name.
 */
class StationErrorTakeoverTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : null;
    }

    private static Object[] args(Component component) {
        return component.getContents() instanceof TranslatableContents translatable
                ? translatable.getArgs() : new Object[0];
    }

    // ------------------------------------------------------------------ the takeover's own shape

    @Test
    void aRefusedCraftIsCaptionedErrorAndAWorthlessLoadoutWarning() {
        Component message = Component.translatable("gui.forgeweave.tool_station.needs_forge");

        assertEquals("gui.forgeweave.error", key(StationMenu.Rejection.error(message).caption()),
                "upstream's gui.error -- everything ContainerToolStation catches as a TinkerGuiException "
                        + "is a craft that was attempted and refused");
        assertEquals("gui.forgeweave.warning", key(StationMenu.Rejection.warning(message).caption()),
                "upstream's gui.warning -- the two messages the GUI derives from the slots instead "
                        + "(wrong_material_part, useless_tool_part) call warning(), not error()");
        assertFalse(StationMenu.Rejection.error(message).warning());
        assertTrue(StationMenu.Rejection.warning(message).warning());
    }

    @Test
    void theMessageIsTheWholeBodyRatherThanALineAppendedToIt() {
        Component message = Component.translatable("gui.forgeweave.modifier.max_level", "Haste");

        assertEquals(List.of(message), StationMenu.Rejection.error(message).body(),
                "upstream's setText(message) replaces the panel's text outright; appending is what put "
                        + "the reason below the fold of a full tool panel (issue #378)");
    }

    // ------------------------------------------------------------------ who applies it

    private static Path clientDir() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate.resolve("src/main/java/dev/gkissel/forgeweave/client");
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static String source(String screen) throws IOException {
        return Files.readString(clientDir().resolve(screen), StandardCharsets.UTF_8);
    }

    @Test
    void bothStationsTakeTheirPanelOverWithTheRejection() throws IOException {
        for (String screen : List.of("ToolStationScreen.java", "PartBuilderScreen.java")) {
            String source = source(screen);
            assertTrue(source.contains("rejection.caption()") && source.contains("rejection.body()"),
                    screen + " has a rejection but does not caption its panel with it and replace the "
                            + "body -- that is the whole of upstream's error()/warning() (issue #378)");
        }
    }

    @Test
    void theToolStationBlanksItsTraitPanelWhileARejectionIsShowing() throws IOException {
        String source = source("ToolStationScreen.java");
        int takeover = source.indexOf("rejection.caption()");
        int end = source.indexOf("}", source.indexOf("rejection.body()"));

        String branch = source.substring(takeover, end);
        assertTrue(branch.contains("traitCaption = null") && branch.contains("traitLines = List.of()"),
                "upstream's GuiToolStation#error also does traitInfo.setCaption(null)/setText(), i.e. the "
                        + "second panel is emptied -- leaving a tool's traits beside an ERROR caption reads "
                        + "as though the tool was built. Branch was:\n" + branch);
    }

    // ------------------------------------------------------------------ the Material Value line

    private static final ResourceLocation IRON = ResourceLocation.fromNamespaceAndPath("forgeweave", "iron");

    @Test
    void theMaterialValueLineIsInIngotsAndNamesItsMaterial() {
        Component line = StationText.materialValue(2.0F, true, IRON);

        assertEquals("gui.forgeweave.part_builder.material_value", key(line));
        Object[] args = args(line);
        assertEquals(2, args.length, "upstream's gui.partbuilder.material_value is \"Material Value: %s %s\" "
                + "-- amount then material name; the one-argument version could not say which of the two "
                + "material slots the total was counted against (issue #378)");
        assertEquals("2", ((Component) args[0]).getString(), "the line quotes ingots (2), not raw value units");
        assertEquals("material.forgeweave.iron", key((Component) args[1]));
    }

    @Test
    void aFractionalIngotAmountKeepsItsFraction() {
        assertEquals("1.5", ((Component) args(StationText.materialValue(1.5F, true, IRON))[0]).getString(),
                "upstream runs the ingot count through Util.df, which keeps halves (three shards is an "
                        + "ingot and a half); truncating them would read as a whole ingot short");
    }

    @Test
    void onlyTheAmountGoesRedWhenItFallsShortOfTheCost() {
        Component enough = (Component) args(StationText.materialValue(1.0F, true, IRON))[0];
        Component short_ = (Component) args(StationText.materialValue(1.0F, false, IRON))[0];

        assertEquals(null, enough.getStyle().getColor(), "a sufficient amount stays in the line's own grey");
        assertEquals(ChatFormatting.DARK_RED.getColor().intValue(), short_.getStyle().getColor().getValue(),
                "upstream wraps the amount alone in DARK_RED (GuiPartBuilder:127-129)");
        assertEquals(null, ((Component) args(StationText.materialValue(1.0F, false, IRON))[1]).getStyle().getColor(),
                "the material name is not part of the shortfall; upstream names it with the uncoloured "
                        + "getLocalizedName() so the grey line stays grey");
    }
}
