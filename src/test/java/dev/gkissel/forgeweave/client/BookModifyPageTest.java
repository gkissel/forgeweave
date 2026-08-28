package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;

import dev.gkissel.forgeweave.client.book.ModifyPageContent;
import dev.gkissel.forgeweave.client.book.ModifyPageContent.Sprite;
import dev.gkissel.forgeweave.item.AmmoToolItem;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.Modifier;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Issue #651: the tool and modifier pages render upstream's {@code ContentTool}/{@code
 * ContentModifier} layouts -- the modify-station diagram from {@code modify.png}, the part/reagent
 * items in its slots, and the bullet lists. Everything asserted here is the pure geometry and key
 * data {@link ModifyPageContent} carries, pinned against the constants in Tinkers' 1.12
 * {@code library/book/content/ContentTool.java}/{@code ContentModifier.java} (pinned clone commit
 * in NOTICE.md) so a drive-by "tidy" of a coordinate fails loudly.
 */
class BookModifyPageTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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
        // #682: the armor pages' plating slot (slot 0) must not draw wood, which has no plating stats.
        assertEquals(id("cobalt"), ModifyPageContent.demoMaterial(PartItem.Kind.PLATING, 0));
        assertEquals(id("cobalt"), ModifyPageContent.demoMaterial(PartItem.Kind.MAILLE, 1));
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

    /**
     * Issue #760: the modifier page's diagram must not always paint a pickaxe. A projectile-only
     * modifier (fins) illustrates with an ammo item ({@code AmmoToolItem}), the same restriction
     * {@code ModifierApplication} enforces.
     */
    @Test
    void aProjectileOnlyModifierIsIllustratedWithAnAmmoItem() {
        ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(new Modifier() {
            @Override
            public boolean projectileOnly() {
                return true;
            }
        });

        assertTrue(entry.tool().get() instanceof AmmoToolItem, entry.tool().get().toString());
    }

    /** A harvest-only modifier (blasting, fortification) illustrates with a harvest tool. */
    @Test
    void aHarvestOnlyModifierIsIllustratedWithAHarvestTool() {
        ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(new Modifier() {
            @Override
            public boolean harvestOnly() {
                return true;
            }
        });

        assertEquals(ToolConstants.Category.HARVEST, entry.constants().category());
    }

    /** An armor-only modifier (the protections, knockback resistance, thorns) illustrates with an armor piece. */
    @Test
    void anArmorOnlyModifierIsIllustratedWithAnArmorPiece() {
        ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(new Modifier() {
            @Override
            public boolean armorOnly() {
                return true;
            }
        });

        assertEquals(ToolConstants.Category.ARMOR, entry.constants().category());
    }

    /** Every other modifier (haste, luck, silky, soulbound, ...) illustrates with a melee weapon. */
    @Test
    void anUnrestrictedModifierIsIllustratedWithAMeleeWeapon() {
        ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(new Modifier() {});

        assertEquals(ToolConstants.Category.MELEE, entry.constants().category());
        assertEquals(ForgeweaveItems.TOOL_BROADSWORD.get(), entry.tool().get());
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }
}
