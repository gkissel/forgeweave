package dev.gkissel.forgeweave.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;

import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Playtest checklist 1.0.0, line 61: every tool and part shows at the Stencil Table, the Part Builder
 * and the Tool Station in the same place and order as before the addon API (epic #1008), and no
 * tool's slots at the Tool Station or the Tool Forge moved a pixel. Both stations draw their slots
 * from the one {@link ToolStationTabs#TABS} table.
 *
 * <p>"Before" is the tree at {@code 102c68108^}, the commit ahead of #1078, the first addon API
 * change. The lists below were copied from {@code StencilTableMenu}, {@code PartBuilderRecipes} and
 * {@code ToolStationTabs} at that commit. Addon registrations append after the built-in rows, so
 * nothing a player learned the position of can move, and a unit test loads no addon. The two armor
 * tabs are left out on purpose: #1081 merged eight armor tabs into two and moved the light set to
 * upstream 1.20's positions, and {@code ToolStationTabsTest#theArmorTabsSitAtTheirUpstreamPositions}
 * pins those.
 */
class StationLayoutSnapshotTest {

    /** Every part with a pattern, in the Stencil Table's and the Part Builder's order before #1078. */
    private static final List<String> PARTS = List.of(
            "pickaxe_head", "shovel_head", "axe_head", "tool_binding", "tool_handle",
            "sword_blade", "wide_guard", "hand_guard", "cross_guard", "sign_plate", "pan", "knife_blade",
            "large_sword_blade", "tough_tool_rod", "tough_binding", "large_plate", "hammer_head",
            "excavator_head", "scythe_head", "kama_head", "broad_axe_head", "vein_hammer_head",
            "curved_blade", "katana_blade", "bow_limb", "bow_string", "arrow_head", "arrow_shaft", "fletching",
            "plating_helmet", "plating_chestplate", "plating_leggings", "plating_boots", "maille",
            "sharpening_kit", "shard");

    /** {@code ToolStationTabs}' slot origin before #1078; every position below is an offset from it. */
    private static final int ORIGIN_X = 33;
    private static final int ORIGIN_Y = 42;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static String id(Supplier<? extends Item> item) {
        return BuiltInRegistries.ITEM.getKey(item.get()).getPath();
    }

    /** The Stencil Table's buttons, one per pattern, in the order they had. */
    @Test
    void theStencilTableListsEveryPatternInItsOldOrder() {
        List<String> patterns = StencilTableMenu.PATTERNS.stream().map(StationLayoutSnapshotTest::id).toList();
        assertEquals(PARTS.stream().map(part -> "pattern_" + part).toList(), patterns);
    }

    /** The Part Builder's table: each pattern still stamps the same part, in the same order. */
    @Test
    void thePartBuilderMatchesEveryPatternToItsPartInTheOldOrder() {
        List<String> patterns = PartBuilderRecipes.ENTRIES.stream().map(entry -> id(entry.pattern())).toList();
        List<String> parts = PartBuilderRecipes.ENTRIES.stream().map(entry -> id(entry.part())).toList();
        assertEquals(PARTS.stream().map(part -> "pattern_" + part).toList(), patterns);
        assertEquals(PARTS, parts);
    }

    /**
     * Every tab but the two armor ones: the same tool, at the same place in the sidebar, with every
     * slot at the pixel it had. The repair tab is the first row.
     */
    @Test
    void everyNonArmorTabKeepsItsPlaceAndItsSlotPixels() {
        Map<String, List<ToolStationTabs.Pos>> expected = new LinkedHashMap<>();
        expected.put("repair", at(0, 0, -18, 20, -22, -5, 0, -23, 22, -5, 18, 20));
        expected.put("pickaxe", at(20, -20, 0, 0, -18, 18));
        expected.put("shovel", at(18, -18, -20, 20, 0, 0));
        expected.put("hatchet", at(-2, -20, 18, -8, -11, 11));
        expected.put("broadsword", at(-21, 20, 15, -16, -3, 2));
        expected.put("longsword", at(-21, 20, 15, -16, -3, 2));
        expected.put("rapier", at(19, 20, -15, -16, -1, 2));
        expected.put("battlesign", at(-6, 18, -6, -8));
        expected.put("frying_pan", at(-21, 20, 1, -6));
        expected.put("dagger", at(14, -14, -14, 14));
        expected.put("warmace", at(-18, 18, 20, -20, 0, 0));
        expected.put("mattock", at(-11, 11, -2, -20, 18, -8));
        expected.put("kama", at(-11, 11, -2, -20, 18, -8));
        expected.put("battleaxe", at(-11, 16, -16, -16, 6, -18, 16, 4));
        expected.put("scimitar", at(-11, 11, -2, -20, 18, -8));
        expected.put("katana", at(-21, 20, 15, -16, -3, 2));
        expected.put("cleaver", at(-24, 22, -8, -6, 14, -12, 0, 16));
        expected.put("hammer", at(-12, 10, 11, -13, 24, 6, -8, -26));
        expected.put("excavator", at(-8, 4, 12, -16, -8, -16, -26, 20));
        expected.put("lumberaxe", at(-1, 4, 0, -20, 20, -4, -20, 20));
        expected.put("scythe", at(-16, 12, 3, -23, 23, -13, 4, 5));
        expected.put("vein_hammer", at(11, -13, -12, 10, 24, 6, -8, -26));
        expected.put("shortbow", at(3, -19, -19, 3, 5, 5));
        expected.put("longbow", at(11, -23, -23, 11, -16, -16, 5, 5));
        expected.put("crossbow", at(5, 5, 11, -23, -19, -19, -15, 9));
        expected.put("shuriken", at(-13, -13, 11, -13, 11, 11, -13, 11));
        expected.put("arrow", at(-1, -1, 17, -19, -19, 17));

        Map<String, List<ToolStationTabs.Pos>> actual = new LinkedHashMap<>();
        for (ToolStationTabs.Tab tab : ToolStationTabs.TABS) {
            if (tab.isRepair()) {
                actual.put("repair", tab.slots());
            } else if (tab.entry().constants().category() != ToolConstants.Category.ARMOR) {
                actual.put(tab.entry().constants().id(), tab.slots());
            }
        }
        assertEquals(List.copyOf(expected.keySet()), List.copyOf(actual.keySet()), "the sidebar's tool order");
        expected.forEach((tool, slots) -> assertEquals(slots, actual.get(tool), tool + "'s slot pixels"));
    }

    /** Pairs of offsets from the old origin, turned into absolute slot positions. */
    private static List<ToolStationTabs.Pos> at(int... offsets) {
        ToolStationTabs.Pos[] slots = new ToolStationTabs.Pos[offsets.length / 2];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = new ToolStationTabs.Pos(ORIGIN_X + offsets[2 * i], ORIGIN_Y + offsets[2 * i + 1]);
        }
        return List.of(slots);
    }
}
