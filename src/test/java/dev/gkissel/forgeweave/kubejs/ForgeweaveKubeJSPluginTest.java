package dev.gkissel.forgeweave.kubejs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.ScriptTrait;

/**
 * Issue #832's KubeJS gates. With KubeJS on the test classpath (build.gradle's {@code
 * testCompileOnly}/{@code testRuntimeOnly} pair) the plugin class KubeJS would instantiate from
 * {@code kubejs.plugins.txt} loads, exposes the one event, and its builder lands in {@link
 * ForgeweaveTraits#lookup}; and the isolation rule that makes the mod work <em>without</em> KubeJS
 * -- no KubeJS import outside the {@code kubejs} package -- is pinned by a source scan (the
 * {@code LocalizationAuditTest} idiom). The without-KubeJS boot itself is every other test in this
 * suite plus {@code runGameTestServer}: neither ever references this package.
 */
class ForgeweaveKubeJSPluginTest {

    private static final String PLUGIN_CLASS = "dev.gkissel.forgeweave.kubejs.ForgeweaveKubeJSPlugin";

    @Test
    void pluginsTxtNamesThePluginAndItLoadsAsAKubeJSPlugin() throws Exception {
        // Read from the source tree rather than the classloader: KubeJS's own jar ships a
        // kubejs.plugins.txt at the same path and wins the classpath lookup here.
        Path pluginsTxt = projectRoot().resolve("src/main/resources/kubejs.plugins.txt");
        assertTrue(Files.exists(pluginsTxt), "kubejs.plugins.txt must ship at the jar root for KubeJS to find the plugin");
        List<String> classes = Files.readString(pluginsTxt, StandardCharsets.UTF_8).lines()
                .map(line -> line.split("#", 2)[0].trim())
                .filter(line -> !line.isBlank())
                .toList();
        assertEquals(List.of(PLUGIN_CLASS), classes);

        Object plugin = Class.forName(PLUGIN_CLASS).getDeclaredConstructor().newInstance();
        assertInstanceOf(KubeJSPlugin.class, plugin);
        assertEquals("ForgeweaveEvents", ForgeweaveKubeJSPlugin.GROUP.name);
        assertSame(ForgeweaveKubeJSPlugin.TRAITS, ForgeweaveKubeJSPlugin.GROUP.getHandlers().get("traits"));
    }

    @Test
    void theEventsRegisterHandsBackABuilderThatLookupResolves() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("somepack", "scripted");
        ScriptTrait trait = new ForgeweaveKubeJSPlugin.TraitsKubeEvent().register(id);
        assertSame(trait, ForgeweaveTraits.lookup(id));
    }

    /** The mod must boot without KubeJS: only this package may touch its API. */
    @Test
    void noKubeJSImportOutsideTheKubejsPackage() throws IOException {
        Path main = projectRoot().resolve("src/main/java/dev/gkissel/forgeweave");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(main)) {
            for (Path java : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (java.startsWith(main.resolve("kubejs"))) {
                    continue;
                }
                if (Files.readString(java, StandardCharsets.UTF_8).contains("dev.latvian.")) {
                    offenders.add(main.relativize(java).toString());
                }
            }
        }
        assertTrue(offenders.isEmpty(), "KubeJS API referenced outside the kubejs package (issue #832 isolation rule): "
                + offenders);
    }

    private static Path projectRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (!Files.exists(root.resolve("settings.gradle"))) {
            root = root.getParent();
        }
        return root;
    }
}
