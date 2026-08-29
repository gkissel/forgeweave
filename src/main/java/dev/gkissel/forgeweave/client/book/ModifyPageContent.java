package dev.gkissel.forgeweave.client.book;

import java.util.List;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.AmmoToolItem;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.Modifier;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The tool and modifier pages' modify-station diagram, as data (issue #651): the sprite regions of
 * the derived {@code modify.png} sheet, the slot offsets the part and reagent items sit at, and the
 * demo materials the diagram's tool is assembled from. All of it is Tinkers' 1.12
 * {@code library/book/content/ContentTool}/{@code ContentModifier} (pinned clone commit in
 * NOTICE.md, MIT) re-expressed as constants, split out of {@link BookScreen} so
 * {@code BookModifyPageTest} pins every coordinate against upstream's without a client -- the same
 * seam {@link MaterialPageContent} and {@link BookGeometry} are.
 */
public final class ModifyPageContent {

    /** One region of {@code modify.png} -- upstream's {@code ImageData(BOOK_MODIFY, u, v, w, h)}. */
    public record Sprite(int u, int v, int w, int h) {}

    /** {@code ContentTool.TEX_SIZE}/{@code ContentModifier.TEX_SIZE}: the sheet is 256x256. */
    public static final int TEX_SIZE = 256;

    /** {@code ContentTool.IMG_SLOTS}: the five-part slot ring the tool page's diagram draws. */
    public static final Sprite SLOTS = new Sprite(0, 0, 72, 72);
    /** {@code ContentModifier.IMG_SLOT_1}: one slot -- also the untinted plate under the demo tool. */
    public static final Sprite SLOT_1 = new Sprite(0, 75, 22, 22);
    /** {@code ContentModifier.IMG_SLOT_2}. */
    public static final Sprite SLOT_2 = new Sprite(0, 97, 40, 22);
    /** {@code ContentModifier.IMG_SLOT_3}. */
    public static final Sprite SLOT_3 = new Sprite(0, 119, 58, 22);
    /** {@code ContentModifier.IMG_SLOT_5}. */
    public static final Sprite SLOT_5 = new Sprite(0, 141, 58, 41);
    /** {@code ContentModifier.IMG_TABLE}: the Tool Station tabletop the demo tool rests on. */
    public static final Sprite TABLE = new Sprite(214, 0, 42, 46);

    /**
     * The tint on the slot sprites: Mantle {@code AppearanceData.slotColor}'s default, which
     * Tinkers' {@code appearance.json} leaves alone (it overrides only {@code coverColor}).
     */
    public static final int SLOT_COLOR = 0xFF844C;

    /** {@code ContentTool#build}'s part-slot offsets around the demo tool, in slot order. */
    public static final int[] TOOL_SLOT_X = {-21, -25, 0, 25, 21};
    public static final int[] TOOL_SLOT_Y = {22, -4, -25, -4, 22};

    /**
     * {@code ContentModifier#build}'s slot offsets inside the slot plate, index = slot number. Every
     * legacy OR recipe ({@code ModifierRecipe#requireAllReagents} false) still only ever draws index
     * 0 -- its {@code reagents} list is <em>alternatives</em> that cycle in that one slot. Issue #781:
     * an AND recipe needs one slot per declared reagent instead ({@link ModifierRecipe#reagentSlotCount}),
     * up to the five upstream ever lays out.
     */
    public static final int[] MODIFIER_SLOT_X = {3, 21, 39, 12, 30};
    public static final int[] MODIFIER_SLOT_Y = {3, 3, 3, 22, 22};

    /** {@code ContentModifier#build}'s {@code switch(inCount)}: which plate frames that many slots. */
    public static Sprite modifierSlotSprite(int slotCount) {
        return switch (slotCount) {
            case 0, 1 -> SLOT_1;
            case 2 -> SLOT_2;
            case 3 -> SLOT_3;
            default -> SLOT_5;
        };
    }

    /** Upstream {@code tool.properties} / {@code modifier.effect}, the bullet lists' headers. */
    public static final String TOOL_PROPERTIES_TITLE = "book.forgeweave.tool.properties";
    public static final String MODIFIER_EFFECTS_TITLE = "book.forgeweave.modifier.effect";

    /**
     * {@code ContentModifier#getDemoTools}' wood/cobalt/ardite/manyullyn, cycled per slot index the
     * way {@code TinkersItem#getMaterialForPartForGuiRendering} cycles its four GUI render
     * materials. Deviation, recorded: upstream's tool page paints fake {@code _internal_render}
     * GUI-only materials; Forgeweave has no GUI-only materials, so the diagram assembles from these
     * four real ones -- the exact set upstream's own modifier page demo uses.
     */
    private static final List<ResourceLocation> DEMO_MATERIALS =
            List.of(id("wood"), id("cobalt"), id("ardite"), id("manyullyn"));

    /** {@code BowCore#getMaterialForPartForGuiRendering}: a bowstring slot always renders string. */
    private static final ResourceLocation DEMO_BOWSTRING = id("string");

    private ModifyPageContent() {
    }

    /** The material the diagram paints part slot {@code index} of a demo tool with. */
    public static ResourceLocation demoMaterial(PartItem.Kind kind, int index) {
        if (kind == PartItem.Kind.BOWSTRING) {
            return DEMO_BOWSTRING;
        }
        // #682: a plating slot is slot 0 and wood makes no plating (D10), which would throw out of
        // ArmorStats#of at page build; cobalt is the roster's first plating material.
        if (kind == PartItem.Kind.PLATING) {
            return DEMO_MATERIALS.get(1);
        }
        return DEMO_MATERIALS.get(index % DEMO_MATERIALS.size());
    }

    /**
     * The demo tool the modifier page's diagram illustrates, first of {@link #compatibleEntries}.
     * Issue #760 picked a category by the modifier's predicates alone (projectileOnly, harvestOnly,
     * armorOnly, else melee) and trusted every entry of that category to accept it; issue #794 found
     * that false for the width/height expanders (Category.AOE, no melee weapon has an expandable
     * area) and, the same hole, wind burst (mace-only) and elytra/creative flight (heavy-chestplate-
     * only) -- see {@link #compatibleEntries}'s javadoc for the fix.
     */
    public static ToolAssemblyRecipes.Entry representativeEntry(@Nullable HolderLookup.Provider registries, Modifier modifier) {
        return compatibleEntries(registries, modifier).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("no assembly entry accepts this modifier"));
    }

    /**
     * Every {@link ToolAssemblyRecipes.Entry} this modifier actually accepts, in assembly-entry
     * order (issue #794). {@link #representativeEntry} is just this list's first element; the JEI
     * {@code ModifierApplicationCategory} uses the whole list to cycle its render-only tool slot
     * through nothing but tools the recipe applies to.
     *
     * <p>Prefers a natural example when the modifier's own predicates narrow it to one category --
     * an ammo item for {@link Modifier#projectileOnly} (the same {@code AmmoToolItem} check {@link
     * ModifierApplication#acceptsToolShape} makes), a harvest tool for {@link Modifier#harvestOnly},
     * an armor piece for {@link Modifier#armorOnly}, a melee weapon for everything else (haste, luck,
     * silky, reinforced, soulbound and the rest, which apply broadly) -- then falls back to scanning
     * every entry when nothing in that preferred category actually qualifies: the two AoE expanders
     * (no melee weapon has an expandable area; a harvest tool does), wind burst (no plain sword is
     * {@code #minecraft:enchantable/mace}; the warmace is), and elytra/creative flight ({@link
     * Modifier#heavyChestplateOnly} rules out every plain armor piece; the heavy chestplate qualifies).
     * A modifier that fits nothing at all ({@link ModifierApplication#acceptsToolShape} refuses every
     * entry) is a data bug this throws loudly on rather than illustrating with the wrong item anyway.
     */
    public static List<ToolAssemblyRecipes.Entry> compatibleEntries(@Nullable HolderLookup.Provider registries, Modifier modifier) {
        Predicate<ToolAssemblyRecipes.Entry> preferredCategory;
        if (modifier.projectileOnly()) {
            preferredCategory = entry -> entry.tool().get() instanceof AmmoToolItem;
        } else if (modifier.harvestOnly()) {
            preferredCategory = entry -> entry.constants().category() == ToolConstants.Category.HARVEST;
        } else if (modifier.armorOnly()) {
            preferredCategory = entry -> entry.constants().category() == ToolConstants.Category.ARMOR;
        } else {
            preferredCategory = entry -> entry.constants().category() == ToolConstants.Category.MELEE;
        }
        List<ToolAssemblyRecipes.Entry> preferred = matching(registries, modifier, preferredCategory);
        if (!preferred.isEmpty()) {
            return preferred;
        }
        return matching(registries, modifier, entry -> true);
    }

    private static List<ToolAssemblyRecipes.Entry> matching(@Nullable HolderLookup.Provider registries, Modifier modifier,
            Predicate<ToolAssemblyRecipes.Entry> category) {
        return ToolAssemblyRecipes.ENTRIES.stream()
                .filter(category)
                .filter(entry -> ModifierApplication.acceptsToolShape(registries, modifier, new ItemStack(entry.tool().get())))
                .toList();
    }

    /** {@code <tool description id>.property.<n>} -- one "Properties:" bullet, collected while it exists. */
    public static String toolPropertyKey(String toolDescriptionId, int index) {
        return toolDescriptionId + ".property." + index;
    }

    /** {@code modifier.<ns>.<path>.effect.<n>} -- one "Effects:" bullet, collected while it exists. */
    public static String modifierEffectKey(ResourceLocation modifier, int index) {
        return "modifier." + modifier.getNamespace() + "." + modifier.getPath() + ".effect." + index;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
