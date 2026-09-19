package dev.gkissel.forgeweave.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.api.tool.PartKind;
import dev.gkissel.forgeweave.api.tool.PartRole;
import dev.gkissel.forgeweave.api.tool.ToolFamily;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * What keeps {@code dev.gkissel.forgeweave.api} a surface another mod can rely on (issue #1066).
 *
 * <p>Two rules, and both of them fail here rather than in an addon author's build. The package
 * imports nothing but Minecraft, NeoForge, the JDK and itself, so compiling against it never drags
 * in a Forgeweave internal that is free to move. And its three enums are name-for-name mirrors of
 * the internal ones they stand in for, which is what lets {@code menu.RegisteredTools} map between
 * them with {@code valueOf} instead of a switch that a new constant could be left out of.
 */
class ApiSurfaceTest {

    private static final List<String> ALLOWED_IMPORT_PREFIXES = List.of(
            "java.", "javax.", "net.minecraft.", "net.neoforged.", "org.jetbrains.annotations.",
            "dev.gkissel.forgeweave.api.");

    private static Path projectRoot() {
        for (Path candidate = Path.of("").toAbsolutePath(); candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("settings.gradle not found above " + Path.of("").toAbsolutePath());
    }

    @Test
    void theApiPackageImportsNothingItDoesNotPromise() throws IOException {
        Path api = projectRoot().resolve("src/main/java/dev/gkissel/forgeweave/api");
        assertTrue(Files.isDirectory(api), "the api package is missing: " + api);

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(api)) {
            for (Path java : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                for (String line : Files.readAllLines(java, StandardCharsets.UTF_8)) {
                    if (!line.startsWith("import ")) {
                        continue;
                    }
                    String imported = line.substring("import ".length()).replace("static ", "").trim();
                    if (ALLOWED_IMPORT_PREFIXES.stream().noneMatch(imported::startsWith)) {
                        offenders.add(api.relativize(java) + ": " + imported);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "The api package is what an addon compiles against, so it imports only Minecraft, NeoForge,"
                        + " the JDK and itself:\n" + String.join("\n", offenders));
    }

    @Test
    void partRoleMirrorsTheInternalRoles() {
        assertEquals(names(ToolConstants.Role.values()), names(PartRole.values()),
                "api.tool.PartRole is the public mirror of ToolConstants.Role; add the new constant to both");
    }

    @Test
    void partKindMirrorsTheInternalPartKinds() {
        assertEquals(names(PartItem.Kind.values()), names(PartKind.values()),
                "api.tool.PartKind is the public mirror of PartItem.Kind; add the new constant to both");
    }

    @Test
    void toolFamilyMirrorsTheInternalCategories() {
        assertEquals(names(ToolConstants.Category.values()), names(ToolFamily.values()),
                "api.tool.ToolFamily is the public mirror of ToolConstants.Category; add the new constant to both");
    }

    private static List<String> names(Enum<?>[] constants) {
        return Stream.of(constants).map(Enum::name).toList();
    }
}
