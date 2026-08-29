package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.client.book.ModifyPageContent;
import dev.gkissel.forgeweave.client.book.ModifyPageContent.Sprite;
import dev.gkissel.forgeweave.item.AmmoToolItem;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Modifier;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
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
    void theModifierPageInputSlotsSitAtUpstreamsOffsets() {
        // ContentModifier.build's slotX/slotY inside the slot plate. A legacy OR recipe's
        // alternatives still cycle in index 0 alone; issue #781's AND recipes use more.
        assertArrayEquals(new int[] {3, 21, 39, 12, 30}, ModifyPageContent.MODIFIER_SLOT_X);
        assertArrayEquals(new int[] {3, 3, 3, 22, 22}, ModifyPageContent.MODIFIER_SLOT_Y);
    }

    /**
     * Issue #781: {@code ContentModifier.build}'s {@code switch(inCount)} picking the plate sized
     * for that many slots -- {@code IMG_SLOT_1/2/3/5}. 0 reagents (no shipped recipe found) still
     * gets the smallest plate, matching {@link ModifyPageContent#SLOT_1}'s use as the untinted plate
     * fallback everywhere else on this page.
     */
    @Test
    void theModifierSlotSpriteMatchesUpstreamsSwitchOnSlotCount() {
        assertEquals(ModifyPageContent.SLOT_1, ModifyPageContent.modifierSlotSprite(0));
        assertEquals(ModifyPageContent.SLOT_1, ModifyPageContent.modifierSlotSprite(1));
        assertEquals(ModifyPageContent.SLOT_2, ModifyPageContent.modifierSlotSprite(2));
        assertEquals(ModifyPageContent.SLOT_3, ModifyPageContent.modifierSlotSprite(3));
        assertEquals(ModifyPageContent.SLOT_5, ModifyPageContent.modifierSlotSprite(4));
        assertEquals(ModifyPageContent.SLOT_5, ModifyPageContent.modifierSlotSprite(5));
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
        ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(null, new Modifier() {
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
        ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(null, new Modifier() {
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
        ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(null, new Modifier() {
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
        ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(null, new Modifier() {});

        assertEquals(ToolConstants.Category.MELEE, entry.constants().category());
        assertEquals(ForgeweaveItems.TOOL_BROADSWORD.get(), entry.tool().get());
    }

    /**
     * Issue #794: the bug report itself. Width++/Height++ ({@link ForgeweaveModifiers#HARVEST_WIDTH}/
     * {@link ForgeweaveModifiers#HARVEST_HEIGHT}) declare no {@link Modifier#harvestOnly}/{@link
     * Modifier#armorOnly}/{@link Modifier#projectileOnly} predicate at all -- only {@link
     * Modifier#aoeExpansion} -- so #760's category heuristic fell through to its melee-weapon default
     * (a sword), which no expander can ever actually widen ({@code ModifierApplication}'s
     * {@code aoeOnly} gate refuses every tool whose {@code aoeShape} isn't expandable, and no melee
     * weapon's is). The fix must pick a tool the expander actually accepts instead.
     */
    @Test
    void aWidthOrHeightExpanderIsIllustratedWithAnExpandableHarvestTool() {
        for (Modifier expander : List.of(ForgeweaveModifiers.HARVEST_WIDTH, ForgeweaveModifiers.HARVEST_HEIGHT)) {
            ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(null, expander);
            ItemStack tool = new ItemStack(entry.tool().get());

            assertTrue(tool.getItem() instanceof ToolItem toolItem && toolItem.aoeShape().expandable(),
                    entry.tool().get() + " has no expandable area, so " + expander + " would refuse it");
        }
    }

    /**
     * Issue #794's second offender: elytra flight and creative flight are {@link
     * Modifier#heavyChestplateOnly}, narrower than {@link Modifier#armorOnly} above -- the plain
     * helmet #760's armor-only bucket would pick (first {@code Category.ARMOR} entry) is refused by
     * {@code ModifierApplication} just as surely as a sword is refused for the expanders; only the
     * heavy chestplate qualifies.
     */
    @Test
    void aHeavyChestplateOnlyModifierIsIllustratedWithTheHeavyChestplate() {
        for (Modifier modifier : List.of(ForgeweaveModifiers.ELYTRA_FLIGHT, ForgeweaveModifiers.CREATIVE_FLIGHT)) {
            ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(null, modifier);

            assertTrue(entry.tool().get() instanceof ArmorPieceItem armor && armor.isHeavy()
                            && armor.getType() == ArmorItem.Type.CHESTPLATE,
                    entry.tool().get() + " is not the heavy chestplate, so " + modifier + " would refuse it");
        }
    }

    /**
     * Issue #794's regression guard: every modifier {@link ForgeweaveModifiers} registers illustrates
     * with an item {@link ModifierApplication#acceptsToolShape} -- the same gate the Tool Station
     * checks before a real application -- actually accepts, so a future modifier with a new predicate
     * combination fails this loudly instead of shipping a wrong picture. {@code null} registries: this
     * is a plain unit test with no world, so wind burst's mace-tag check ({@link
     * ModifierApplication#acceptsToolShape}'s javadoc) is not exercised here -- {@code
     * gametest.ModifierIllustrationGameTests} covers that with a real registry.
     */
    @Test
    void everyRegisteredModifierIllustratesWithAnItemItAccepts() {
        for (ResourceLocation modifierId : ForgeweaveModifiers.ids()) {
            Modifier modifier = ForgeweaveModifiers.get(modifierId);
            assertTrue(modifier != null, modifierId + " is a registered id with no Modifier behind it");

            ToolAssemblyRecipes.Entry entry = ModifyPageContent.representativeEntry(null, modifier);
            ItemStack tool = new ItemStack(entry.tool().get());

            assertTrue(ModifierApplication.acceptsToolShape(null, modifier, tool),
                    modifierId + " is illustrated with " + entry.tool().get() + ", which it would refuse");
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }
}
