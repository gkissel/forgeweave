package dev.gkissel.forgeweave.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

/**
 * Guards issue #597: the Tool Station's rename box reset on every keystroke, so typing a name was
 * impossible -- only the characters that landed inside one tick survived.
 *
 * <p>Issue #443 made the typed name station state, echoed to the other players standing there, and
 * had {@code ToolStationScreen#containerTick} fold the menu's name back into the box. But the typing
 * path stayed server-only: the client's own menu never saw the keystroke, so the poll compared a
 * name the server had (and the client mirror had not) against a "last sent" string only the screen
 * updated. Those two disagreed the instant anyone typed, and the poll read that as "another player
 * renamed it" and overwrote the box.
 *
 * <p>Vanilla's anvil is the model the issue names: {@code AnvilScreen#onNameChanged} writes the
 * typed text into its own menu <em>before</em> sending it up, so there is only ever one string to
 * keep honest and an echo can never fight the typist. This test covers both halves -- the poll
 * decision as a pure function, and (by source scan, since the unit-test classpath has no Minecraft
 * client and no screen can be instantiated here) that the screen actually wires it that way.
 */
class ToolStationRenameSyncTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        // ToolStationMenu's static initializer reaches SoundEvents.
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // ------------------------------------------------------------------ the poll decision

    /**
     * The screen's two moving parts, wired the way {@code ToolStationScreen} wires them: the box's
     * text, and the client menu's copy of the station's name.
     */
    private static final class Field {
        private String text = "";
        private String menuName = "";

        /** The {@code EditBox} responder, i.e. {@code onNameChanged}. */
        void type(String typed) {
            text = typed;
            menuName = typed; // menu.setToolName, before the payload goes up -- vanilla AnvilScreen
        }

        /** A {@code RenameStationItemPayload} from another player at the same station. */
        void remoteRename(String name) {
            menuName = name;
        }

        /** One {@code containerTick}. */
        void tick() {
            ToolStationMenu.renameFieldUpdate(menuName, text).ifPresent(applied -> text = applied);
        }
    }

    @Test
    void sequentialKeystrokesSurviveTheTicksBetweenThem() {
        Field field = new Field();

        field.type("H");
        field.tick();
        assertEquals("H", field.text, "the first keystroke was clobbered by the next tick (issue #597)");

        field.type("Ha");
        field.tick();
        field.tick();
        assertEquals("Ha", field.text,
                "the box reset between keystrokes, so only characters typed inside one tick survive "
                        + "-- this is the #597 regression exactly");
    }

    @Test
    void anotherPlayersRenameIsStillFoldedIn() {
        Field field = new Field();
        field.type("Ha");
        field.tick();

        field.remoteRename("Hammer");
        field.tick();

        assertEquals("Hammer", field.text,
                "#443's cross-player sync has to keep working: a name that arrived from the other "
                        + "player at the station is not this client's typing and is taken");
    }

    @Test
    void anEchoOfThisClientsOwnNameIsIgnored() {
        assertEquals(Optional.empty(), ToolStationMenu.renameFieldUpdate("Hammer", "Hammer"),
                "the name already in the box is not an update and must never be re-applied -- "
                        + "re-applying resets the cursor mid-word");
        assertEquals(Optional.of("Hammer"), ToolStationMenu.renameFieldUpdate("Hammer", "Ha"),
                "a menu name the box does not have is another player's edit and is taken");
    }

    // ------------------------------------------------------------------ who wires it

    private static String screenSource() throws IOException {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return Files.readString(
                        candidate.resolve("src/main/java/dev/gkissel/forgeweave/client/ToolStationScreen.java"),
                        StandardCharsets.UTF_8);
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    @Test
    void typingIsAppliedToTheLocalMenuBeforeItIsSent() throws IOException {
        String source = screenSource();
        int start = source.indexOf("private void onNameChanged(");
        assertTrue(start >= 0, "ToolStationScreen no longer has onNameChanged");
        String body = source.substring(start, source.indexOf("\n    }", start));

        int applied = body.indexOf("menu.setToolName(");
        int sent = body.indexOf("sendToServer(");
        assertTrue(applied >= 0,
                "onNameChanged does not apply the typed name to its own menu, so the menu the tick "
                        + "polls stays stale and overwrites the box (issue #597). Vanilla "
                        + "AnvilScreen#onNameChanged calls menu.setItemName before it sends. Body was:\n" + body);
        assertTrue(sent > applied,
                "the local menu has to take the name before the payload goes up, so no tick in "
                        + "between can find a stale one (issue #597). Body was:\n" + body);
    }

    @Test
    void theTickPollsTheMenuAgainstTheBoxItself() throws IOException {
        String source = screenSource();
        int start = source.indexOf("protected void containerTick()");
        assertTrue(start >= 0, "ToolStationScreen no longer has containerTick");
        String body = source.substring(start, source.indexOf("\n    }", start));

        assertTrue(body.contains("ToolStationMenu.renameFieldUpdate("),
                "containerTick decides the clobber itself instead of going through "
                        + "ToolStationMenu#renameFieldUpdate, so the decision this test covers is not the "
                        + "one that runs (issue #597). Body was:\n" + body);
        assertTrue(!source.contains("lastSentName"),
                "the screen still tracks a 'last sent' string beside the menu's name -- two copies of "
                        + "one piece of state is what drifted apart in #597. Compare against the box.");
    }
}
