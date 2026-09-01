package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

/**
 * Guards Forgeweave's built-in Legacy resource pack (issue #796) against the failure modes its own
 * issue -- and issue #807, filed after one slipped through -- call out: a file it ships that
 * overrides nothing real (an orphan -- a stray leftover, a typo'd path, or a sprite swap that got
 * reverted without cleaning up), the inverse mistake of a pack file that is actually byte-identical
 * to what the default Forged tree already ships (dead weight -- see
 * {@code scripts/sprite_sets.py}'s dedup contract, which every generator script is supposed to
 * uphold), and issue #807's own miss: pre-#796 art still shipping somewhere in the *default* tree,
 * at a path a Forged sprite swap did not touch. A plain filesystem walk and byte comparison, no
 * resource-pack loading, the same "fast, catches drift without a running client" shape
 * {@link TextureReferenceAuditTest} uses.
 */
class LegacyResourcePackTest {

    /**
     * The 32px weapon batch deliberately replaced assembled-tool renders only. These item icons and
     * other tools still reuse the old 16px donor pixels until their own Forged sprites arrive.
     */
    private static final Map<String, Set<String>> INTENTIONAL_UNSWAPPED_SIBLINGS = Map.of(
            "derived/tools/scimitar_binding.png", Set.of(
                    "derived/item/cross_guard.png"),
            "derived/tools/scimitar_handle.png", Set.of(
                    "derived/tools/broadsword_handle.png",
                    "derived/tools/cleaver_handle.png",
                    "derived/tools/frying_pan_handle.png",
                    "derived/tools/longsword_handle.png"),
            "derived/tools/scimitar_head.png", Set.of("derived/item/curved_blade.png"),
            "derived/tools/warmace_binding.png", Set.of("derived/tools/hammer_head3.png"),
            "derived/tools/warmace_handle.png", Set.of("derived/tools/hammer_handle.png"),
            "derived/tools/warmace_head.png", Set.of("derived/item/war_mace_head.png"),
            // A later Forged sprite batch (dagger and rapier's 16px->32px upgrade) retired
            // rapier_binding.png/rapier_handle.png to the Legacy pack too. Upstream's rapier reused
            // the broadsword's guard/handle art (same as the scimitar rows above), so these
            // default-tree siblings still legitimately carry the same pre-Forged pixels.
            "derived/tools/rapier_binding.png", Set.of(
                    "derived/item/cross_guard.png"),
            "derived/tools/rapier_handle.png", Set.of(
                    "derived/tools/broadsword_handle.png",
                    "derived/tools/cleaver_handle.png",
                    "derived/tools/frying_pan_handle.png",
                    "derived/tools/longsword_handle.png"));

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    private static Path legacyRoot() {
        return projectRoot().resolve("src/main/resources/resourcepacks/legacy");
    }

    private static Path legacyTextures() {
        return legacyRoot().resolve("assets/forgeweave/textures");
    }

    private static Path defaultTextures() {
        return projectRoot().resolve("src/main/resources/assets/forgeweave/textures");
    }

    /**
     * Every file the Legacy pack ships must actually override something: a same-relative-path file
     * that exists in the default (Forged) tree. A path with nothing to override is a stray file --
     * either a typo, or a leftover from a sprite swap that never got cleaned up.
     */
    @Test
    void everyLegacyFileOverridesAnExistingDefaultPath() throws IOException {
        List<String> orphans = new ArrayList<>();
        Path legacy = legacyTextures();
        try (Stream<Path> files = Files.walk(legacy)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path relative = legacy.relativize(file);
                Path defaultCounterpart = defaultTextures().resolve(relative);
                if (!Files.isRegularFile(defaultCounterpart)) {
                    orphans.add(relative + " has no default-tree counterpart at " + defaultCounterpart);
                }
            }
        }
        assertTrue(orphans.isEmpty(), "Legacy pack files that override nothing real:\n" + String.join("\n", orphans));
    }

    /**
     * The other half of the contract {@code scripts/sprite_sets.py#save_legacy_if_different}
     * upholds: every file the Legacy pack ships must actually differ from its default-tree
     * counterpart, or it is not really an override -- just a byte-identical duplicate bloating the
     * pack and muddying which files a licensing audit needs to look at twice.
     */
    @Test
    void everyLegacyFileActuallyDiffersFromTheDefault() throws IOException {
        List<String> duplicates = new ArrayList<>();
        Path legacy = legacyTextures();
        try (Stream<Path> files = Files.walk(legacy)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path relative = legacy.relativize(file);
                Path defaultCounterpart = defaultTextures().resolve(relative);
                if (Files.isRegularFile(defaultCounterpart)
                        && Files.mismatch(file, defaultCounterpart) == -1) {
                    duplicates.add(relative.toString());
                }
            }
        }
        assertTrue(duplicates.isEmpty(),
                "Legacy pack files byte-identical to the Forged default (should not ship at all):\n"
                        + String.join("\n", duplicates));
    }

    /**
     * Issue #807: a Forged sprite that replaces a part's *item icon* does not automatically replace
     * a byte-identical *tool layer* at a different path and different file name -- {@code #796}'s
     * first batch swapped {@code derived/item/katana_blade.png} but missed that
     * {@code derived/tools/katana_head.png} carried the exact same pre-#796 pixels under a name that
     * does not match, so the old art kept shipping in the default tree under a name nobody thought to
     * check. Every file the Legacy pack carries is, by definition, pre-#796/pre-Forged art that a
     * sprite swap was supposed to retire from the default tree entirely. If any *other* file in the
     * default tree still carries those exact bytes, some sibling of the swapped file was missed the
     * same way {@code katana_head.png} was.
     *
     * <p>This does not fire on legitimately shared art: an unswapped part (say {@code large_plate.png})
     * whose item icon and tool layer are still identical today has no Legacy-pack entry at all -- it
     * was never overridden, so its hash never enters the comparison set below. Only a file that some
     * Forged sprite *has* replaced (and therefore has a Legacy-pack row) can trigger this check, and
     * only when a *different* default-tree file -- not its own already-verified-different counterpart
     * above -- still carries its exact retired bytes.
     *
     * <p>Issue #809: the comparison hashes decoded pixel data, not raw file bytes. A raw-byte hash
     * missed {@code derived/item/arrow_shaft.png}'s sibling, {@code derived/tools/arrow_shaft.png} --
     * both are the same upstream pixels, but were saved through different code paths with different
     * PNG encodings, so their file bytes never matched even though every pixel did. This is the same
     * decode-before-comparing contract {@code scripts/sprite_sets.py#save_legacy_if_different} already
     * uses when it decides whether a Legacy override differs from the Forged default -- this test now
     * checks retirement the same way production decides replacement.
     */
    @Test
    void noOtherDefaultFileStillCarriesRetiredLegacyBytes() throws IOException {
        Map<String, List<String>> legacyHashToPaths = new HashMap<>();
        Path legacy = legacyTextures();
        try (Stream<Path> files = Files.walk(legacy)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = legacy.relativize(file).toString();
                legacyHashToPaths.computeIfAbsent(pixelHash(file), key -> new ArrayList<>()).add(relative);
            }
        }

        List<String> staleCopies = new ArrayList<>();
        Path defaultRoot = defaultTextures();
        try (Stream<Path> files = Files.walk(defaultRoot)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String relative = defaultRoot.relativize(file).toString();
                List<String> retiredPaths = legacyHashToPaths.get(pixelHash(file));
                if (retiredPaths == null) {
                    continue;
                }
                for (String retiredPath : retiredPaths) {
                    Set<String> intentionalSiblings = INTENTIONAL_UNSWAPPED_SIBLINGS
                            .getOrDefault(retiredPath, Set.of());
                    if (!retiredPath.equals(relative) && !intentionalSiblings.contains(relative)) {
                        staleCopies.add(relative + " still carries the bytes retired at " + retiredPath);
                    }
                }
            }
        }
        assertTrue(staleCopies.isEmpty(),
                "Default-tree files that still carry pre-Forged/Legacy bytes a sprite swap should have retired:\n"
                        + String.join("\n", staleCopies));
    }

    /**
     * Hashes a PNG's decoded ARGB pixel data (plus its dimensions) rather than its raw file bytes, so
     * two PNGs encoding the exact same image differently (palette vs. truecolor, different compressor,
     * stray metadata) still compare equal -- see issue #809's javadoc note above. Non-PNG files under
     * the textures tree (an animation `.mcmeta` sidecar, for instance) are not images to decode, so
     * they fall back to a plain byte hash -- the same comparison this test used everywhere before.
     */
    private static String pixelHash(Path file) throws IOException {
        if (!file.toString().endsWith(".png")) {
            return sha256(file);
        }
        BufferedImage image = ImageIO.read(file.toFile());
        if (image == null) {
            throw new IOException("could not decode PNG: " + file);
        }
        int width = image.getWidth();
        int height = image.getHeight();
        int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
        ByteBuffer buffer = ByteBuffer.allocate(8 + pixels.length * 4);
        buffer.putInt(width).putInt(height);
        for (int pixel : pixels) {
            buffer.putInt(pixel);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(buffer.array()));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available on every JVM", e);
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 must be available on every JVM", e);
        }
    }

    /** The pack needs valid metadata or {@code Pack.readMetaAndCreate} (see {@code ForgeweaveResourcePacks}) fails to load it at all. */
    @Test
    void packHasMetadata() throws IOException {
        Path mcmeta = legacyRoot().resolve("pack.mcmeta");
        assertTrue(Files.isRegularFile(mcmeta), "the Legacy pack needs a pack.mcmeta: " + mcmeta);
        String content = Files.readString(mcmeta);
        assertTrue(content.contains("pack_format"), "pack.mcmeta must declare a pack_format");
    }

    /** Sanity check that the audit above actually walks real content, not an empty/missing directory. */
    @Test
    void theLegacyPackShipsAtLeastTheIssue796Sprites() {
        Path legacyItem = legacyTextures().resolve("derived/item");
        for (String name : List.of("pattern.png", "cast.png", "tool_binding.png", "tough_binding.png", "katana_blade.png")) {
            assertTrue(Files.isRegularFile(legacyItem.resolve(name)),
                    "the Legacy pack should carry the pre-#796 " + name);
        }
        assertTrue(Files.isRegularFile(legacyTextures().resolve("block/armor_station_top.png")),
                "the Legacy pack should carry the pre-#796 armor_station_top.png");
    }
}
