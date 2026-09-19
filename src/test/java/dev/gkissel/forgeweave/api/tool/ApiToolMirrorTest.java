package dev.gkissel.forgeweave.api.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * {@code api.tool}'s three enums are name-for-name mirrors of the internal ones they stand in for
 * (issue #1066). They are mirrors rather than the internal enums themselves because
 * {@code ApiSourceIsolationTest} forbids the api package from naming anything under
 * {@code dev.gkissel.forgeweave} outside itself, and they are safe to be mirrors only while this
 * test passes: {@code menu.RegisteredTools} maps between the two sides with {@code valueOf}, so a
 * constant added to one and not the other is a crash at the Tool Station rather than a compile
 * error.
 */
class ApiToolMirrorTest {

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
