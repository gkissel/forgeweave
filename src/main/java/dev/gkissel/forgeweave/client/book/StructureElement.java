package dev.gkissel.forgeweave.client.book;

import javax.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The rotating 3D schematic on a {@code structure} book page (issue #651): a port of Mantle's
 * {@code ElementStructure} (branch {@code 1.12}, commit
 * {@code 340a386af51a97efaac0e71a3f1ff87fb267efe9}, MIT -- NOTICE.md). Upstream's numbers survive
 * intact: {@code scale = 100/maxDim * min(w/PAGE_WIDTH, h/PAGE_HEIGHT)}, the initial 25/-45 degree
 * tilt, the joystick drag (while the button is held, each frame adds
 * {@code (mouse - clickPoint)/10} capped at 10 degrees), and the build-up animation (one
 * {@link StructureInfo#step} every 20 draws, a five-step pause once the structure completes,
 * everything visible while the toggle is off).
 *
 * <p>Rendering is the modern adaptation: upstream tessellated through {@code BlockRendererDispatcher
 * #renderBlock} against a fake {@code IBlockAccess}; here each frame snapshots the visible cells
 * and {@link StructureRenderer} draws them as full-bright block models into a picture-in-picture
 * texture the size of this element -- the same translate/scale(scale, -scale, scale)/rotate/centre
 * matrix stack. Single block models are not neighbour-culled the way upstream's block access culled
 * them; on the smeltery's opaque shell the difference is invisible.
 */
public final class StructureElement {

    /** {@code ElementStructure.draw}: one animation step every 20 draws... */
    private static final int STEP_FRAMES = 20;
    /** ...and a pause of five step periods once the structure is complete. */
    private static final int FULL_STRUCTURE_STEPS = 5;
    /** The joystick drag's divisor and per-frame cap, upstream's {@code maxSpeed}/10f. */
    private static final float DRAG_DIVISOR = 10f;
    private static final float DRAG_MAX_SPEED = 10f;
    /** {@code setShowLayer(9)}: upstream's show-everything layer while the animation is off. */
    private static final int SHOW_ALL_LAYER = 9;

    private final StructureInfo structure;
    private final float scale;
    private final float xTranslate;
    private final float yTranslate;

    private float rotX = 25;
    private float rotY = -45;
    private boolean animating;
    private int frame;
    private int fullStructureSteps = FULL_STRUCTURE_STEPS;
    @Nullable
    private double[] dragOrigin;

    public StructureElement(StructureInfo structure, int width, int height) {
        this.structure = structure;
        int maxDim = Math.max(structure.length(), Math.max(structure.height(), structure.width()));
        this.scale = 100f / maxDim * Math.min((float) width / BookGeometry.PAGE_WIDTH,
                (float) height / BookGeometry.PAGE_HEIGHT);
        this.xTranslate = width / 2f;
        this.yTranslate = height / 2f;
    }

    public float scale() {
        return this.scale;
    }

    public float rotX() {
        return this.rotX;
    }

    public float rotY() {
        return this.rotY;
    }

    public boolean animating() {
        return this.animating;
    }

    /** The refresh button's {@code BUTTON_ID_ANIMATE} click: toggles the build-up animation. */
    public void toggleAnimation() {
        this.animating = !this.animating;
    }

    /** Mouse-down on the element: the joystick pivot is where the drag began. */
    public void beginDrag(double mouseX, double mouseY) {
        this.dragOrigin = new double[] {mouseX, mouseY};
    }

    public void endDrag() {
        this.dragOrigin = null;
    }

    /**
     * Applied every frame while the button is held, upstream's {@code lastClick} handling at the
     * top of {@code draw}: the further the cursor sits from where it went down, the faster the
     * structure turns, capped at {@link #DRAG_MAX_SPEED} degrees per frame.
     */
    public void drag(double mouseX, double mouseY) {
        if (this.dragOrigin == null) {
            return;
        }
        this.rotY += Math.min(DRAG_MAX_SPEED, (float) (mouseX - this.dragOrigin[0]) / DRAG_DIVISOR);
        this.rotX += Math.min(DRAG_MAX_SPEED, (float) (mouseY - this.dragOrigin[1]) / DRAG_DIVISOR);
    }

    /** Draws the structure with its element origin at {@code (x, y)}, advancing the animation. */
    public void render(GuiGraphicsExtractor graphics, int x, int y) {
        this.frame++;
        if (this.animating) {
            if (this.frame % STEP_FRAMES == 0) {
                if (this.structure.canStep() || ++this.fullStructureSteps >= FULL_STRUCTURE_STEPS) {
                    this.structure.step();
                    this.fullStructureSteps = 0;
                }
            }
        } else {
            this.structure.reset();
            this.structure.setShowLayer(SHOW_ALL_LAYER);
        }

        List<StructureRenderer.Cell> cells = new ArrayList<>();
        for (int h = 0; h < this.structure.height(); h++) {
            for (int l = 0; l < this.structure.length(); l++) {
                for (int w = 0; w < this.structure.width(); w++) {
                    BlockState state = this.structure.visibleStateAt(l, h, w);
                    if (state != null) {
                        cells.add(new StructureRenderer.Cell(l, h, w, state));
                    }
                }
            }
        }
        // The box ignores the pose stack, which the book never moves: (x, y) is already on screen.
        graphics.submitPictureInPictureRenderState(new StructureRenderer.State(List.copyOf(cells),
                this.structure.length(), this.structure.height(), this.structure.width(), this.rotX, this.rotY,
                x, y, x + Math.round(this.xTranslate * 2), y + Math.round(this.yTranslate * 2), this.scale));
    }
}
