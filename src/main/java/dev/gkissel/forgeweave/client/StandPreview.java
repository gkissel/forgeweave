package dev.gkissel.forgeweave.client;

import javax.annotation.Nullable;

import com.mojang.blaze3d.systems.RenderSystem;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * A station's live preview of what it is about to hand back: an armor stand that holds the piece in
 * its off hand, or wears it when the piece is armor, with a ring under its feet the player drags to
 * turn it (issue #1043).
 *
 * <p>Upstream 1.20's {@code ToolTableScreen} owns this, and it owns it for two screens -- the Tinker
 * Station and the Modifier Worktable both extend that class. 1.12 has no preview at all, so 1.20 is
 * the parity target here (CLAUDE.md's fallback rule), and the geometry and behaviour below are its
 * own: the {@code 210}/{@code 25} degree pose, the {@code scale + 30} by {@code scale * 2} grab box
 * dropped five pixels past the feet, and a tenth of a degree of yaw per pixel dragged. This is a
 * class rather than a method on {@link ToolStationScreen} for the same reason it is a base class
 * upstream: a Modifier Worktable would want the identical widget, and one copy is one thing to keep
 * honest.
 *
 * <p>Client only, in every sense the issue asked for. The stand is built from {@link #open} when a
 * screen appears and dropped by {@link #close} when it goes away, so a closed screen holds no entity
 * -- and it is never added to a level, exactly as vanilla's own {@code SmithingScreen} preview is
 * not. Nothing outside {@code client} refers to this class, so a dedicated server never loads it.
 */
public final class StandPreview {
    /** Upstream {@code TinkerStationScreen#init}: {@code setupArmorStandPreview(-55, 195, 35)}. */
    public static final int SCALE = 35;
    /** Upstream's grab box: {@code scale + 30} wide, {@code scale * 2} tall, {@code 5}px past the feet. */
    private static final int GRAB_PAD = 30;
    private static final int GRAB_DROP = 5;
    /** Upstream {@code ToolTableScreen#mouseDragged}: {@code angle += (mouseX - last) / 10f}. */
    private static final float DEGREES_PER_PIXEL = 1 / 10f;
    /** {@code armorStandLastMouseX = -1}: no drag in progress, so the next move sets the origin. */
    private static final double NO_DRAG = -1;

    /** Vanilla {@code SmithingScreen}'s own armor-stand pose, which upstream 1.20 borrows verbatim. */
    private static final Quaternionf POSE = new Quaternionf().rotationXYZ(0.43633232F, 0.0F, (float) Math.PI);
    private static final Vector3f NO_TRANSLATION = new Vector3f();
    private static final float Y_BODY_ROT = 210.0F;
    private static final float X_ROT = 25.0F;

    /** The drag-to-rotate ring, upstream 1.20's {@code icons.png} sprite at (0, 184) (NOTICE.md). */
    private static final ResourceLocation ROTATE_RING =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "textures/derived/gui/stand_rotate.png");
    private static final int RING_SIZE = 32;

    @Nullable
    private ArmorStand stand;
    private int x;
    private int y;
    private int scale = SCALE;
    private float angle;
    private boolean grabbed;
    private double lastMouseX = NO_DRAG;

    /**
     * Builds the stand and puts its feet at {@code (x, y)} in screen pixels. Safe to call again on a
     * resize: the pose the player dragged the stand into survives, only the position moves.
     */
    public void open(@Nullable Level level, int x, int y, int scale) {
        this.x = x;
        this.y = y;
        this.scale = scale;
        if (level == null) {
            return; // no client level to build an entity against; nothing to preview yet either
        }
        ItemStack held = stand == null ? ItemStack.EMPTY : stand.getItemBySlot(EquipmentSlot.OFFHAND);
        stand = new ArmorStand(level, 0.0D, 0.0D, 0.0D);
        stand.setNoBasePlate(true);
        stand.setShowArms(true);
        stand.yBodyRot = Y_BODY_ROT;
        stand.setXRot(X_ROT);
        stand.yHeadRot = stand.getYRot();
        stand.yHeadRotO = stand.getYRot();
        setItem(held);
    }

    /** Drops the stand, so a closed screen costs nothing. */
    public void close() {
        stand = null;
        grabbed = false;
        lastMouseX = NO_DRAG;
    }

    public boolean isOpen() {
        return stand != null;
    }

    /**
     * Shows {@code stack}: worn in its own slot when it is armor, held in the off hand otherwise --
     * upstream {@code ToolTableScreen#updateArmorStandPreview}, which clears every slot first so the
     * stand never keeps wearing the piece the station stopped producing.
     */
    public void setItem(ItemStack stack) {
        if (stand == null) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stand.setItemSlot(slot, ItemStack.EMPTY);
        }
        if (stack.isEmpty()) {
            return;
        }
        stand.setItemSlot(stack.getItem() instanceof ArmorItem armor ? armor.getEquipmentSlot() : EquipmentSlot.OFFHAND,
                stack.copy());
    }

    public void render(GuiGraphics graphics) {
        if (stand == null) {
            return;
        }
        InventoryScreen.renderEntityInInventory(graphics, x, y, scale, NO_TRANSLATION,
                POSE.rotateY(angle, new Quaternionf()), null, stand);
        // Issue #84's hazard, one scope smaller: entity rendering ends with blending off, and both
        // the ring below and the selected station tab drawn after this one are translucent.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.blit(ROTATE_RING, x - RING_SIZE / 2, y - RING_SIZE / 2, 0, 0, RING_SIZE, RING_SIZE,
                RING_SIZE, RING_SIZE);
    }

    /**
     * The rectangle the stand and its ring occupy, which is both the drag target and what a screen
     * hands JEI so its item list keeps off the preview. Pure, so the layout tests can check it
     * without a client.
     */
    public static Rect2i box(int x, int y, int scale) {
        int width = scale + GRAB_PAD;
        int height = scale * 2;
        return new Rect2i(x - width / 2, y - height + GRAB_DROP, width, height);
    }

    public Rect2i bounds() {
        return box(x, y, scale);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        Rect2i box = bounds();
        return stand != null && mouseX >= box.getX() && mouseX < box.getX() + box.getWidth()
                && mouseY >= box.getY() && mouseY < box.getY() + box.getHeight();
    }

    /**
     * Arms the rotation drag when the press landed on the stand.
     *
     * @return whether the press was on the stand, so the screen can swallow it. Upstream lets the
     *     click fall through, which on a container screen is how a stack on the cursor gets thrown
     *     on the floor; swallowing it costs nothing, because the drag below is tracked here rather
     *     than through the click.
     */
    public boolean mouseClicked(double mouseX, double mouseY) {
        grabbed = isMouseOver(mouseX, mouseY);
        return grabbed;
    }

    /** Turns the stand by how far the cursor moved since the last move event, as upstream does. */
    public void mouseDragged(double mouseX) {
        if (grabbed && lastMouseX != NO_DRAG) {
            angle += (float) (mouseX - lastMouseX) * DEGREES_PER_PIXEL;
        }
        lastMouseX = mouseX;
    }

    public void mouseReleased() {
        grabbed = false;
        lastMouseX = NO_DRAG;
    }
}
