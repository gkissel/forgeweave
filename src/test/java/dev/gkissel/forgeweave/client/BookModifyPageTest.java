package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.client.book.ModifyPageContent;
import dev.gkissel.forgeweave.client.book.ModifyPageContent.Sprite;
import dev.gkissel.forgeweave.item.PartItem;

/**
 * Issue #651: the tool and modifier pages render upstream's {@code ContentTool}/{@code
 * ContentModifier} layouts -- the modify-station diagram from {@code modify.png}, the part/reagent
 * items in its slots, and the bullet lists. Everything asserted here is the pure geometry and key
 * data {@link ModifyPageContent} carries, pinned against the constants in Tinkers' 1.12
 * {@code library/book/content/ContentTool.java}/{@code ContentModifier.java} (pinned clone commit
 * in NOTICE.md) so a drive-by "tidy" of a coordinate fails loudly.
 */
class BookModifyPageTest {

    @Test
    void theAtlasRegionsAreUpstreamsExactCoordinates() {
        // ContentTool.IMG_SLOTS / ContentModifier.IMG_SLOT_* / IMG_TABLE on modify.png (TEX_SIZE 256).
        assertEquals(256, ModifyPageContent.TEX_SIZE);
        assertEquals(new Sprite(0, 0, 72, 72), ModifyPageContent.SLOTS);
        assertEquals(new Sprite(0, 75, 22, 22), ModifyPageContent.SLOT_1);
        assertEquals(new Sprite(0, 97, 40, 22), ModifyPageContent.SLOT_2);
        assertEquals(new Sprite(0, 119, 58, 22), ModifyPageContent.SLOT_3);
        assertEquals(new Sprite(0, 141, 58, 41), ModifyPageContent.SLOT_5);
        assertEquals(new Sprite(214, 0, 42, 46), ModifyPageContent.TABLE);
    }

    @Test
    void theSlotColourIsMantlesAppearanceDefault() {
        // AppearanceData.slotColor, which Tinkers' appearance.json does not override.
        assertEquals(0xFF844C, ModifyPageContent.SLOT_COLOR);
    }

    @Test
    void theToolPagePartSlotsSitAtUpstreamsOffsets() {
        // ContentTool.build's slotX/slotY around the demo tool.
        assertArrayEquals(new int[] {-21, -25, 0, 25, 21}, ModifyPageContent.TOOL_SLOT_X);
        assertArrayEquals(new int[] {22, -4, -25, -4, 22}, ModifyPageContent.TOOL_SLOT_Y);
    }

    @Test
    void theModifierPageInputSlotSitsAtUpstreamsOffset() {
        // ContentModifier.build's slotX/slotY inside the slot plate; Forgeweave recipes hold one
        // reagent slot whose alternatives cycle, so only index 0 is ever drawn.
        assertEquals(3, ModifyPageContent.MODIFIER_SLOT_X);
        assertEquals(3, ModifyPageContent.MODIFIER_SLOT_Y);
    }

    @Test
    void theDemoMaterialsAreUpstreamsGuiRoster() {
        // ContentModifier.getDemoTools' wood/cobalt/ardite/manyullyn, cycled per slot index like
        // TinkersItem.getMaterialForPartForGuiRendering's RenderMaterials[index % 4] ...
        assertEquals(id("wood"), ModifyPageContent.demoMaterial(PartItem.Kind.HEAD, 0));
        assertEquals(id("cobalt"), ModifyPageContent.demoMaterial(PartItem.Kind.HANDLE, 1));
        assertEquals(id("ardite"), ModifyPageContent.demoMaterial(PartItem.Kind.EXTRA, 2));
        assertEquals(id("manyullyn"), ModifyPageContent.demoMaterial(PartItem.Kind.HEAD, 3));
        assertEquals(id("wood"), ModifyPageContent.demoMaterial(PartItem.Kind.HEAD, 4));
        // ... except a bowstring slot, which BowCore pins to its string material regardless of index.
        assertEquals(id("string"), ModifyPageContent.demoMaterial(PartItem.Kind.BOWSTRING, 2));
    }

    @Test
    void theBulletListKeysFollowTheExistingLangFamilies() {
        assertEquals("item.forgeweave.pickaxe.property.0",
                ModifyPageContent.toolPropertyKey("item.forgeweave.pickaxe", 0));
        assertEquals("modifier.forgeweave.haste.effect.2",
                ModifyPageContent.modifierEffectKey(id("haste"), 2));
        assertEquals("book.forgeweave.tool.properties", ModifyPageContent.TOOL_PROPERTIES_TITLE);
        assertEquals("book.forgeweave.modifier.effect", ModifyPageContent.MODIFIER_EFFECTS_TITLE);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }
}
