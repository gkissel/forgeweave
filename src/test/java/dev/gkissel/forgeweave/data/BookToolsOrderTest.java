package dev.gkissel.forgeweave.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.world.item.Item;

import dev.gkissel.forgeweave.client.book.BookContent;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Guards the guide book's tools section order (parity audit 2026-08-18, T77): upstream
 * {@code book/sections/tools.json:21-124} lists the harvest tools pickaxe/shovel/hatchet/mattock/
 * kama/scythe/hammer/excavator/lumberaxe before the weapons, i.e. scythe sits right after kama --
 * not after the whole harvest run. {@link BookContent#TOOLS}' javadoc already claims this
 * "harvest-then-weapons order"; this test pins it so a future edit can't silently drift again.
 * Forgeweave-only tools (vein hammer, dagger, battleaxe, scimitar, katana, warmace) have no
 * upstream slot, so they're only required to keep their neighbours' relative order.
 */
class BookToolsOrderTest {

    @Test
    void scytheFollowsKamaLikeUpstream() {
        // BookContent.TOOLS resolves from book/sections/tools.json (#651), so compare the items.
        List<Item> tools = BookContent.TOOLS.stream().map(supplier -> (Item) supplier.get()).toList();
        int kama = tools.indexOf(ForgeweaveItems.TOOL_KAMA.get());
        int scythe = tools.indexOf(ForgeweaveItems.TOOL_SCYTHE.get());
        int hammer = tools.indexOf(ForgeweaveItems.TOOL_HAMMER.get());

        assertEquals(kama + 1, scythe,
                "upstream tools.json puts scythe immediately after kama, before hammer/excavator/lumberaxe");
        assertEquals(scythe + 1, hammer,
                "scythe must precede hammer, matching upstream's harvest-tool order");
    }
}
