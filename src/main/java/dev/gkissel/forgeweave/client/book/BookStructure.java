package dev.gkissel.forgeweave.client.book;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * The guide book's data-driven structure (issue #651): {@code assets/forgeweave/book/} holds
 * {@code appearance.json}, {@code index.json} and {@code sections/*.json} in the file shapes the
 * 1.12 book ships in {@code resources/assets/tconstruct/book/} (pinned commit c01173c, MIT), read
 * through Mantle's {@code BookLoader}/{@code SectionData}/{@code PageData} semantics ({@code 1.12}
 * @ {@code 340a386}, NOTICE.md): the index file lists the sections in order with an item icon each
 * and a {@code data} reference to the section file, whose entries are {@code {name, type, ...}}
 * page defs. {@link BookContent} turns the defs into pages and runs the generated-section
 * transformers (listing, material grid, index) over them.
 *
 * <p>Deviations from upstream's tree, both forced by repo rules: the per-language content dirs
 * ({@code book/en_us/**.json}) collapse into the lang system -- a page def carries no content data
 * file; its text lives in {@code book.forgeweave.<section>.<page>.title|.text} keys via
 * {@code ForgeweaveLanguageProvider}, this repo's one localization channel -- and
 * {@code appearance.json}'s title/subtitle are therefore lang keys rather than literal text.
 * Files load from the mod classpath, not the resource manager, so a resource pack cannot override
 * the book's structure (its text it can, through a lang pack).
 *
 * <p>ponytail: only the page types the shipped Forgeweave book uses parse ({@code text},
 * {@code image with text below}, {@code tool}); an unknown type is an authoring error and fails
 * loudly. Upstream's remaining types are not applicable content-wise -- see the PR.
 */
public record BookStructure(Appearance appearance, List<SectionDef> sections) {

    /** Where the book tree lives on the classpath. */
    private static final String ROOT = "/assets/forgeweave/book/";

    private static final Set<String> PAGE_TYPES = Set.of("text", "image with text below", "tool");

    /**
     * Upstream {@code AppearanceData}, reduced to the fields Tinkers' {@code appearance.json}
     * actually sets: the cover title/subtitle (lang keys here), whether the index grid draws the
     * section titles under the icons, and the cover tint. Mantle parses the {@code 0x}-prefixed
     * colour through its {@code HexStringDeserializer}; {@link Integer#decode} is the same rule.
     */
    public record Appearance(String title, String subtitle, boolean drawSectionListText, int coverColor) {}

    /**
     * One {@code index.json} entry plus its resolved {@code sections/*.json}: the section's name
     * (its lang keys and bookmark prefix derive from it), its index-button icon item id, and its
     * authored page defs. Registry-generated pages (the material roster, the modifier roster, the
     * listing and index pages) are not defs; {@code BookContent} appends them.
     */
    public record SectionDef(String name, String iconItem, List<PageDef> pages) {}

    /**
     * One page def: {@code name} (bookmark segment and lang-key segment), upstream's {@code type}
     * string, the tool item id for {@code tool} pages, and the image for
     * {@code image with text below} pages.
     */
    public record PageDef(String name, String type, @Nullable String item, @Nullable String image) {}

    /** Parses the whole shipped tree; fails loudly on a missing file or unknown page type. */
    public static BookStructure load() {
        JsonObject appearanceJson = read("appearance.json").getAsJsonObject();
        Appearance appearance = new Appearance(
                appearanceJson.get("title").getAsString(),
                appearanceJson.get("subtitle").getAsString(),
                appearanceJson.get("drawSectionListText").getAsBoolean(),
                Integer.decode(appearanceJson.get("coverColor").getAsString()));

        List<SectionDef> sections = new ArrayList<>();
        for (JsonElement entry : read("index.json").getAsJsonArray()) {
            JsonObject section = entry.getAsJsonObject();
            String name = section.get("name").getAsString();
            String icon = section.getAsJsonObject("icon").getAsJsonObject("item").get("id").getAsString();
            List<PageDef> pages = section.has("data")
                    ? parseSection(name, read(section.get("data").getAsString()).toString())
                    : List.of();
            sections.add(new SectionDef(name, icon, pages));
        }
        return new BookStructure(appearance, List.copyOf(sections));
    }

    /** The section-file parse, taking raw JSON so the unknown-type contract is unit-testable. */
    public static List<PageDef> parseSection(String sectionName, String json) {
        List<PageDef> pages = new ArrayList<>();
        for (JsonElement entry : JsonParser.parseString(json).getAsJsonArray()) {
            JsonObject page = entry.getAsJsonObject();
            String type = page.get("type").getAsString();
            if (!PAGE_TYPES.contains(type)) {
                throw new IllegalStateException("book section " + sectionName
                        + " uses unsupported page type \"" + type + "\"; supported: " + PAGE_TYPES);
            }
            pages.add(new PageDef(
                    page.get("name").getAsString(),
                    type,
                    page.has("item") ? page.get("item").getAsString() : null,
                    page.has("image") ? page.get("image").getAsString() : null));
        }
        return List.copyOf(pages);
    }

    private static JsonElement read(String path) {
        try (InputStream in = BookStructure.class.getResourceAsStream(ROOT + path)) {
            if (in == null) {
                throw new IllegalStateException("missing book data file " + ROOT + path);
            }
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("could not read " + ROOT + path, e);
        }
    }
}
