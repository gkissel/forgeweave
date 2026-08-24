package dev.gkissel.forgeweave.client.book;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.registries.BuiltInRegistries;

import dev.gkissel.forgeweave.client.book.BookPage.IconGridPage;
import dev.gkissel.forgeweave.client.book.BookPage.ListingPage;
import dev.gkissel.forgeweave.client.book.BookPage.MaterialPage;
import dev.gkissel.forgeweave.client.book.BookPage.ModifierPage;
import dev.gkissel.forgeweave.client.book.BookPage.TextPage;
import dev.gkissel.forgeweave.client.book.BookPage.ToolPage;

/**
 * The book's bookmark strings (issue #623): upstream 1.12 saves the open page on the book item as
 * {@code section.name + "." + page.name} ({@code GuiBook#onGuiClosed}) and resolves it back to a
 * flat page number when the book reopens ({@code BookData#findPageNumber}, Mantle {@code 1.12} @
 * {@code 340a386}, NOTICE.md). Upstream's names come from its authored JSON files; Forgeweave's
 * pages are generated from registries, so the name is derived from the page's own identity
 * instead -- a tool page from the tool's registered path, a material or modifier page from its id,
 * a text page from its title key, the generated listing/grid navigation page (#479) as
 * {@code listing}. Either way the saved string has upstream's shape and contract: lowercase,
 * {@code section.page}, and anything that does not resolve means the cover ({@code -1}).
 */
public final class SavedPage {

    /**
     * The bookmark for the index spread: upstream's, since the index is a real generated section
     * as of issue #651 ({@code BookTransformer#IndexTranformer} naming its pages {@code page1},
     * {@code page2}, ...). Books bookmarked before #651 carry the bare literal {@code "index"}
     * instead; {@link #find} aliases it here so they keep opening on the index spread
     * ({@code m651_book_saved_index_legacy.snbt}).
     */
    public static final String INDEX = "index.page1";

    /** The pre-#651 index bookmark, when the index was screen chrome rather than a section. */
    private static final String LEGACY_INDEX = "index";

    private SavedPage() {
    }

    /** The {@code section.page} bookmark for the flat page index {@code page}, upstream-lowercase. */
    public static String name(List<BookSection> sections, int page) {
        int before = 0;
        for (BookSection section : sections) {
            if (page < before + section.pages().size()) {
                return sectionName(section) + "." + pageName(section.pages().get(page - before));
            }
            before += section.pages().size();
        }
        throw new IndexOutOfBoundsException("no page " + page);
    }

    /**
     * Upstream {@code BookData#findPageNumber}: lowercases, requires a {@code section.page} shape,
     * and walks the sections counting pages; the flat page index of the match, or {@code -1} -- the
     * cover -- when nothing matches.
     */
    public static int find(List<BookSection> sections, String saved) {
        String location = saved.toLowerCase(Locale.ROOT);
        if (LEGACY_INDEX.equals(location)) {
            location = INDEX;
        }
        int dot = location.indexOf('.');
        if (dot < 0) {
            return -1;
        }
        String sectionName = location.substring(0, dot);
        String pageName = location.substring(dot + 1);

        int pages = 0;
        for (BookSection section : sections) {
            if (!sectionName.equals(sectionName(section))) {
                pages += section.pages().size();
                continue;
            }
            for (BookPage page : section.pages()) {
                if (pageName.equals(pageName(page))) {
                    return pages;
                }
                pages++;
            }
        }
        return -1;
    }

    /** {@code book.forgeweave.section.tools} -> {@code tools} -- the 1.12 section names themselves. */
    private static String sectionName(BookSection section) {
        return lastSegment(section.titleKey());
    }

    private static String pageName(BookPage page) {
        return switch (page) {
            // book.forgeweave.intro.welcome.title -> welcome
            case TextPage text -> {
                String key = text.titleKey();
                yield lastSegment(key.endsWith(".title") ? key.substring(0, key.length() - ".title".length()) : key);
            }
            case ToolPage tool -> BuiltInRegistries.ITEM.getKey(tool.tool()).getPath();
            case MaterialPage material -> material.id().getPath();
            case ModifierPage modifier -> modifier.id().getPath();
            case ListingPage listing -> "listing";
            case IconGridPage grid -> "listing";
            // IndexTranformer names its generated pages page1, page2, ... itself.
            case BookPage.SectionListPage sectionList -> sectionList.name();
            // The structure page carries its authored JSON name, upstream's own source for it.
            case BookPage.StructurePage structure -> structure.name();
        };
    }

    private static String lastSegment(String key) {
        return key.substring(key.lastIndexOf('.') + 1);
    }
}
