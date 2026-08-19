package dev.gkissel.forgeweave.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.FastColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import net.minecraft.client.renderer.block.model.BakedQuad;

import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;

/**
 * Playtest alpha.3 item 30.a (issue #600), second half: the nocked arrow rendered as the plain
 * white one whatever it actually was, because T52 (#536) stripped every ammo quad to tint index -1
 * without first resolving what that tint index meant.
 *
 * <p>The bow's own composed model cannot carry the ammo's tint <em>indices</em> -- index 0 on a
 * Forgeweave tool means "the first limb's material" ({@code ForgeweaveItemColors#toolMaterialTint}),
 * not "the potion" -- so {@link ModifierOverlayModels#nockedQuads} bakes the resolved colour into
 * the quad's vertices and only then clears the index. These pin that: a tipped arrow's quads come
 * out the potion's colour, an untinted quad is left alone.
 */
class NockedAmmoTintTest {

    /** No transform at all, so only the colours move. */
    private static final float[] IN_PLACE = {0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f};

    /** Whatever an item-layer quad bakes at: opaque white, which the renderer then multiplies. */
    private static final int WHITE = 0xFFFFFFFF;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static BakedQuad quad(int tintIndex) {
        int[] vertices = new int[4 * IQuadTransformer.STRIDE];
        for (int i = 0; i < 4; i++) {
            vertices[i * IQuadTransformer.STRIDE + IQuadTransformer.COLOR] = WHITE;
        }
        return new BakedQuad(vertices, tintIndex, Direction.NORTH, null, true, false);
    }

    private static int vertexColor(BakedQuad quad, int vertex) {
        return quad.getVertices()[vertex * IQuadTransformer.STRIDE + IQuadTransformer.COLOR];
    }

    /**
     * The colour vanilla's own {@code ItemColors} handler yields for a tipped arrow: the potion's,
     * forced opaque ({@code ItemColors#createDefault}, the {@code Items.TIPPED_ARROW} registration).
     */
    private static int tippedArrowColor() {
        ItemStack arrow = new ItemStack(Items.TIPPED_ARROW);
        arrow.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
        return FastColor.ARGB32.opaque(arrow.getOrDefault(DataComponents.POTION_CONTENTS, PotionContents.EMPTY).getColor());
    }

    @Test
    void aTippedArrowsTintedQuadCarriesThePotionColorInItsVertices() {
        int color = tippedArrowColor();
        assertEquals(0xFF, FastColor.ARGB32.alpha(color), "a tint with no alpha renders the arrow invisible (#8)");

        List<BakedQuad> nocked = ModifierOverlayModels.nockedQuads(List.of(quad(0)), IN_PLACE, tint -> color);

        BakedQuad tip = nocked.get(0);
        assertEquals(-1, tip.getTintIndex(), "the bow's own item colour must never be asked about the arrow's layer");
        for (int vertex = 0; vertex < 4; vertex++) {
            assertEquals(QuadTransformers.toABGR(color), vertexColor(tip, vertex),
                    "vertex " + vertex + " must carry the potion colour, since the tint index no longer can");
        }
    }

    @Test
    void anUntintedQuadKeepsItsOwnVertexColors() {
        List<BakedQuad> nocked = ModifierOverlayModels.nockedQuads(List.of(quad(-1)), IN_PLACE, tint -> 0xFFFF0000);

        BakedQuad shaft = nocked.get(0);
        assertEquals(-1, shaft.getTintIndex());
        assertEquals(WHITE, vertexColor(shaft, 0), "an untinted layer -- a plain arrow's whole model -- is not recoloured");
    }
}
