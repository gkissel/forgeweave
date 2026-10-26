package dev.gkissel.forgeweave.jei;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Background texture crop + pixel size for every recipe category (issue #785), factored out of the
 * category classes themselves so a plain unit test can pin them without touching any JEI class:
 * every {@code IRecipeCategory} implementation pulls JEI's API onto the classpath the moment the JVM
 * verifies it, and JEI is compileOnly/optional there (see {@code JeiRecipesTest}'s own javadoc on
 * why it avoids JEI classes entirely) -- a test that references so much as {@code
 * AlloyingCategory.class} already throws {@code NoClassDefFoundError} in a plain unit test run. This
 * class has no JEI import at all, so {@code JeiCategoryBackgroundsTest} can load it safely.
 *
 * <p>Issue #804: every panel below is now an upstream category's own {@code createDrawable} rect,
 * copied verbatim from the 1.20 clone (`~/development/minecraft/references/tinkers-1.20` @
 * de26560d, MIT -- NOTICE.md), origin included. #785 kept only the sizes and always cropped from
 * (0,0), which for assembly read 20 rows past the end of upstream's panel, and for the four
 * station-panel categories cropped or <em>tiled</em> the Part Builder row to a width it was never
 * drawn at -- the tiling repeated that row's baked slot frames across the panel, which is what put
 * ghost slots and a text column outside the JEI popup in the maintainer's screenshots. A category is
 * now sized to the upstream panel it draws, never to a shared worst case.
 *
 * <p>Issue #971: public, with the slot frame and the two text helpers that used to sit on {@link
 * JeiCategoryChrome}, so the EMI plugin draws every category from the same numbers. Still no JEI
 * import, which is now load-bearing twice over: EMI runs the plugin with no JEI installed.
 */
public final class JeiCategoryGeometry {
    private JeiCategoryGeometry() {}

    /**
     * One upstream {@code IGuiHelper#createDrawable} call: which texture, the rect inside it, and
     * therefore the category's own {@code getWidth()}/{@code getHeight()}.
     */
    public record Panel(Identifier background, int u, int v, int width, int height) {}

    private static Identifier derived(String fileName) {
        return Identifier.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/jei/" + fileName);
    }

    /** The derived copy of upstream's `textures/gui/jei/tinker_station.png` (NOTICE.md). */
    public static final Identifier TINKER_STATION = derived("tinker_station.png");

    /** Upstream {@code AlloyRecipeCategory}: `createDrawable(alloy.png, 0, 0, 172, 62)`. */
    public static final Panel ALLOYING = new Panel(derived("alloy.png"), 0, 0, 172, 62);
    /** Upstream {@code AbstractCastingCategory}: `createDrawable(casting.png, 0, 0, 117, 54)`. */
    public static final Panel CASTING = new Panel(derived("casting.png"), 0, 0, 117, 54);
    /** Upstream {@code AbstractMeltingCategory}: `createDrawable(melting.png, 0, 0, 132, 40)`. */
    public static final Panel MELTING = new Panel(derived("melting.png"), 0, 0, 132, 40);
    /** Upstream {@code EntityMeltingRecipeCategory}: `createDrawable(melting.png, 0, 41, 150, 62)` (issue #1029). */
    public static final Panel ENTITY_MELTING = new Panel(derived("melting.png"), 0, 41, 150, 62);
    /** Upstream {@code ToolBuildingCategory}: `createDrawable(tinker_station.png, 122, 77, 134, 66)`. */
    public static final Panel ASSEMBLY = new Panel(TINKER_STATION, 122, 77, 134, 66);
    /** Upstream {@code PartBuilderCategory}: `createDrawable(tinker_station.png, 0, 117, 121, 46)`. */
    public static final Panel PART_CRAFTING = new Panel(TINKER_STATION, 0, 117, 121, 46);
    /**
     * Upstream has no repair category (its Tool Station repair is in-GUI only, with no recipe view),
     * so this borrows the nearest upstream panel shape: {@code SeveringCategory}'s plain
     * "inputs, arrow, one output" row, `createDrawable(tinker_station.png, 0, 78, 100, 38)`. Its
     * input side is blank art, so {@code RepairCategory} draws upstream's own 18x18 slot frame
     * ({@link JeiCategoryChrome#slotFrame}) under each input the way {@code ToolBuildingCategory}
     * does for its own frameless slots.
     */
    public static final Panel REPAIR = new Panel(TINKER_STATION, 0, 78, 100, 38);
    /** Upstream {@code ModifierRecipeCategory}: `createDrawable(tinker_station.png, 0, 0, 128, 77)`. */
    public static final Panel MODIFIER_APPLICATION = new Panel(TINKER_STATION, 0, 0, 128, 77);
    /**
     * Embossing has no upstream category of its own, but it is the same shape as one that does --
     * reagents plus a tool produce a named modifier on that tool -- so it reuses {@link
     * #MODIFIER_APPLICATION}'s panel and slot ring rather than a bespoke row.
     */
    public static final Panel EMBOSSING = MODIFIER_APPLICATION;

    /**
     * Upstream {@code ToolBuildingCategory}'s own {@code slotBorder}, `createDrawable(
     * tinker_station.png, 162, 59, 18, 18)`: the 18x18 inventory-slot frame it draws under each of
     * its own slots, since its panel art has none. Drawn at {@code (slotX - 1, slotY - 1)}, because
     * a slot frame is 18x18 around an 16x16 slot -- upstream's own comment on the same call.
     */
    public static final int SLOT_FRAME_U = 162;
    public static final int SLOT_FRAME_V = 59;
    public static final int SLOT_FRAME_SIZE = 18;

    /** Draws {@code text} centred on {@code centerX}, the way every upstream category centres its own. */
    public static void drawCentered(GuiGraphicsExtractor guiGraphics, Font font, Component text, int centerX, int y, int colour, boolean shadow) {
        guiGraphics.drawString(font, text, centerX - font.width(text) / 2, y, colour, shadow);
    }

    /**
     * Issue #804: {@code EmbossingCategory} and {@code ModifierApplicationCategory} both draw a
     * datapack-named modifier/material with plain {@code GuiGraphicsExtractor#drawString} -- unlike JEI's own
     * ingredient-slot rendering, a raw {@code drawString} call has no scissor of its own, so a name
     * longer than the panel (real examples: the "Projectile Protection" trait, the "Embossment
     * (Purple Slimevine)" material name) drew straight out past the JEI popup's edge. Ellipsizes with
     * vanilla's own {@link Font#plainSubstrByWidth} rather than growing the panel without bound,
     * since a datapack can always add a longer name than whatever fixed width is chosen.
     */
    public static Component trimToWidth(Font font, Component text, int maxWidth) {
        if (font.width(text) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        String trimmed = font.plainSubstrByWidth(text.getString(), maxWidth - font.width(ellipsis));
        return Component.literal(trimmed + ellipsis);
    }
}
