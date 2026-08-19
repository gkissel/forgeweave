package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.client.book.MaterialPageContent;
import dev.gkissel.forgeweave.client.book.MaterialPageContent.Icon;
import dev.gkissel.forgeweave.client.book.MaterialPageContent.StatGroup;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Parity audit T48 part 2 (issue #633): upstream's material page is a display bar of the stations
 * and demo tools that use the material next to one block per stat type -- the stat type's name
 * underlined beside the parts that read it, its stat lines and its traits, everything hover-explained
 * -- closed by an italic flavour quote. Forgeweave's drew a title and a flat run of stat lines
 * followed by bare trait names.
 *
 * <p>Upstream {@code library/book/content/ContentMaterial#build}, {@code #addStatsDisplay},
 * {@code #addDisplayItems} and {@code #getTraitLines} (1.12 clone, pinned commit in NOTICE.md).
 * Everything asserted here is the page's content rather than its pixels, so it needs no client:
 * {@link MaterialPageContent} is the seam {@code BookScreen} measures and draws from.
 */
class BookMaterialPageTest {

    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    private static Material shipped(String name) {
        String path = "/data/forgeweave/forgeweave/material/" + name + ".json";
        try (InputStream in = BookMaterialPageTest.class.getResourceAsStream(path)) {
            assertNotNull(in, "missing shipped material JSON: " + path);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return Material.CODEC.parse(ops, json).getOrThrow();
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    private static ResourceLocation fw(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private static Component hoverOf(Component line) {
        HoverEvent hover = line.getStyle().getHoverEvent();
        assertNotNull(hover, "no hover event on \"" + line.getString() + "\"");
        Component text = hover.getValue(HoverEvent.Action.SHOW_TEXT);
        assertNotNull(text, "hover event on \"" + line.getString() + "\" is not SHOW_TEXT");
        return text;
    }

    @Test
    void statGroupsFollowUpstreamsBlockOrderAndSkipTheBlocksAMaterialLacks() {
        List<StatGroup> wood = MaterialPageContent.statGroups(shipped("wood"));

        assertEquals(List.of(PartItem.Kind.HEAD, PartItem.Kind.HANDLE, PartItem.Kind.EXTRA, PartItem.Kind.BOW),
                wood.stream().map(StatGroup::kind).toList(),
                "upstream addStatsDisplay runs HEAD, HANDLE then EXTRA; the bow block follows while "
                        + "Forgeweave folds the ranged stats into the material page");
        assertEquals(List.of("tooltip.forgeweave.stat_type.head", "tooltip.forgeweave.stat_type.handle",
                        "tooltip.forgeweave.stat_type.extra", "tooltip.forgeweave.stat_type.bow"),
                wood.stream().map(StatGroup::nameKey).toList(),
                "the block heading is the stat type's own name, the key the part tooltips already use");

        List<StatGroup> string = MaterialPageContent.statGroups(shipped("string"));
        assertEquals(List.of(PartItem.Kind.BOWSTRING), string.stream().map(StatGroup::kind).toList(),
                "a bowstring-only material gets exactly one block -- upstream returns early on a null stat");
    }

    @Test
    void everyStatLineExplainsItselfOnHover() {
        for (StatGroup group : MaterialPageContent.statGroups(shipped("wood"))) {
            assertFalse(group.stats().isEmpty(), group.nameKey() + " contributed a block with no stat lines");
            for (Component stat : group.stats()) {
                assertNotNull(hoverOf(stat));
            }
        }
    }

    @Test
    void traitLinesRepeatUnderEveryBlockInUpstreamsBookStyling() {
        Material prismarine = shipped("prismarine");
        List<StatGroup> groups = MaterialPageContent.statGroups(prismarine);

        for (StatGroup group : groups) {
            assertEquals(prismarine.traits().forPart(group.kind()).size(), group.traits().size(),
                    "upstream getAllTraitsForStats falls back to the general list, so every block "
                            + "lists the traits a part of its kind grants");
            for (Component trait : group.traits()) {
                assertEquals(ChatFormatting.DARK_GRAY.getColor(),
                        trait.getStyle().getColor() == null ? null : trait.getStyle().getColor().getValue(),
                        "upstream getTraitLines colours a book trait line DARK_GRAY");
                assertTrue(trait.getStyle().isUnderlined(), "upstream getTraitLines underlines it");
                assertEquals(prismarine.color(), hoverOf(trait).getStyle().getColor(),
                        "upstream prefixes the hover description with the material's text colour");
            }
        }
        assertTrue(groups.stream().anyMatch(group -> !group.traits().isEmpty()),
                "prismarine grants traits; a page with none would prove nothing here");
    }

    @Test
    void everyStatBlockNamesTheRegisteredPartsThatReadIt() {
        List<ItemStack> heads = MaterialPageContent.partsFor(PartItem.Kind.HEAD, fw("wood"));

        assertFalse(heads.isEmpty(), "upstream shows the tool parts that have a use for the stat");
        assertTrue(heads.stream().anyMatch(stack -> stack.is(ForgeweaveItems.PART_PICKAXE_HEAD.get())),
                "a pickaxe head reads the HEAD block");
        assertTrue(heads.stream().noneMatch(stack -> stack.is(ForgeweaveItems.PART_TOOL_HANDLE.get())),
                "a tool handle does not");
    }

    @Test
    void theDisplayBarLeadsWithTheRepresentativeItemAndTheStationsThatMakeIt() {
        List<Icon> wood = MaterialPageContent.craftIcons(fw("wood"), shipped("wood"));

        assertTrue(wood.size() >= 2, "upstream leads with the representative item, then the Part Builder");
        assertEquals(null, wood.get(0).tooltip(),
                "the representative item keeps its own name as hover text (ElementItem's default)");
        assertTrue(wood.get(1).stack().is(ForgeweaveItems.PART_BUILDER.get()),
                "wood is craftable, so upstream's material.craft_partbuilder icon follows it");
        assertEquals("book.forgeweave.material.craft_partbuilder",
                ((TranslatableContents) wood.get(1).tooltip().getContents()).getKey());
        assertTrue(wood.stream().noneMatch(icon -> icon.stack().is(ForgeweaveItems.CASTING_BASIN.get())),
                "wood has no molten fluid, so it is not castable and gets no basin icon");

        List<Icon> iron = MaterialPageContent.craftIcons(fw("iron"), shipped("iron"));
        assertTrue(iron.stream().anyMatch(icon -> icon.stack().is(ForgeweaveItems.CASTING_BASIN.get())),
                "iron melts to forgeweave:molten_iron, which is what upstream's isCastable() stands for");
    }

    @Test
    void theDemoToolsAreUpstreamsBarFilteredToWhatTheMaterialCanBuild() {
        List<ToolAssemblyRecipes.Entry> wood =
                MaterialPageContent.demoTools(shipped("wood"), MaterialPageContent.DISPLAY_ITEMS);

        assertFalse(wood.isEmpty(), "wood carries head, handle and extra stats, so it builds the whole bar");
        assertEquals(List.of(ForgeweaveItems.TOOL_PICKAXE.get(), ForgeweaveItems.TOOL_MATTOCK.get(),
                        ForgeweaveItems.TOOL_BROADSWORD.get(), ForgeweaveItems.TOOL_HAMMER.get(),
                        ForgeweaveItems.TOOL_CLEAVER.get(), ForgeweaveItems.TOOL_SHURIKEN.get(),
                        ForgeweaveItems.TOOL_FRYING_PAN.get(), ForgeweaveItems.TOOL_LUMBERAXE.get(),
                        ForgeweaveItems.TOOL_BATTLESIGN.get()),
                wood.stream().map(entry -> (Object) entry.tool().get()).toList(),
                "upstream's bar is pickaxe, mattock, broadSword, hammer, cleaver, shuriken, fryPan, "
                        + "lumberAxe, battleSign, in that order");
        assertTrue(wood.size() <= MaterialPageContent.DISPLAY_ITEMS,
                "upstream stops the column at nine items");

        assertEquals(List.of(), MaterialPageContent.demoTools(shipped("string"), MaterialPageContent.DISPLAY_ITEMS),
                "a bowstring-only material builds none of the bar's tools -- upstream's hasValidMaterials");
        assertEquals(2, MaterialPageContent.demoTools(shipped("wood"), 2).size(), "the cap is honoured");
    }

    @Test
    void theFlavourQuoteIsKeyedOnTheMaterial() {
        assertEquals("material.forgeweave.wood.flavour", MaterialPageContent.flavourKey(fw("wood")),
                "upstream reads <material>.flavour out of the book's own language file");
    }
}
