package dev.gkissel.forgeweave.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.BowItem;

/**
 * Upstream 1.12's {@code ToolCore#preventSlowDown} (M3.5 issue #400): a bow that says so lets its
 * user keep walking while drawing, instead of being pinned at vanilla's using-an-item crawl.
 * {@code ShortBow} asks for {@code 0.5f} ("shortbows are more mobile than other bows"),
 * {@code CrossBow} for {@code 0.195f}, and {@code LongBow} deliberately for nothing at all ("no
 * speedup on charging") -- see {@link BowItem#drawMovementSpeed()}.
 *
 * <p>Client-only, exactly as upstream: it is a movement-<em>input</em> hack, and the value it edits
 * ({@code LocalPlayer}'s impulses) is what the client then sends to the server, so the server needs
 * no counterpart. Upstream does the same thing from {@code ClientProxy#preventPlayerSlowdown}, off
 * the item's {@code onUpdate} tick, because 1.12's own tick hook ran too early and got overwritten;
 * NeoForge's {@link MovementInputUpdateEvent} <em>is</em> the right seam and fires from
 * {@code LocalPlayer#aiStep} immediately before vanilla applies its own multiplier:
 *
 * <pre>
 * this.input.tick(...);
 * ClientHooks.onMovementInputUpdate(this, this.input);   // &lt;- here
 * if (this.isUsingItem() &amp;&amp; !this.isPassenger()) {
 *     this.input.leftImpulse *= 0.2F;
 *     this.input.forwardImpulse *= 0.2F;
 * }
 * </pre>
 *
 * <p>So the pre-multiply is upstream's own arithmetic, unchanged: multiplying by {@code speed * 5}
 * before vanilla multiplies by {@code 0.2} leaves exactly {@code speed}. The longbow's
 * {@link BowItem#VANILLA_DRAW_MOVEMENT_SPEED} multiplies by 1 and needs no special case.
 *
 * <p>Not ported: {@code FryPan#onUpdate}'s {@code 0.7f} and {@code LongSword#onUpdate}'s
 * {@code 0.9f}. Both belong to upstream's <em>blocking</em> pose, which Forgeweave does not ship
 * (see {@code ForgeweaveItemModelProvider}'s note on {@code battlesign.tcon.json}); when a blocking
 * ticket lands, it is one more branch here.
 *
 * <h2>Diagonal speed (issue #420)</h2>
 *
 * <p>Keyboard impulses are always {@code -1}, {@code 0}, or {@code 1} per axis, so a diagonal press
 * hands us a {@code (forward, strafe)} pair of length {@code sqrt(2)} where a straight press hands
 * us a pair of length {@code 1}. Multiplying both axes by the same draw speed before vanilla's own
 * {@code 0.2F} (see above) leaves that {@code sqrt(2)} gap in place -- and vanilla's downstream
 * clamp in {@code Entity#getInputVector} only normalizes once the combined length exceeds {@code 1},
 * which our multiplier does not reliably push it past. Net effect: diagonal drawing is up to ~41%
 * faster than straight, the same quirk vanilla sneaking and 1.12's own {@code TraitPreventSlowdown}
 * have, just more visible here because the multiplier is larger. Fixed by normalizing the pair to
 * unit length whenever both axes are engaged, before the multiplier is applied -- a deliberate
 * deviation from vanilla/1.12 (maintainer request, #420), scoped to this bow-drawing path only.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class BowDrawMovement {

    /** Undoes vanilla's {@code 0.2F}: {@code speed * 5 * 0.2 == speed}. */
    private static final float VANILLA_SLOWDOWN_INVERSE = 5.0F;

    @SubscribeEvent
    static void onMovementInputUpdate(MovementInputUpdateEvent event) {
        if (!(event.getEntity() instanceof LocalPlayer player) || !player.isUsingItem()) {
            return;
        }
        ItemStack using = player.getUseItem();
        if (!(using.getItem() instanceof BowItem bow)) {
            return;
        }
        float multiplier = bow.drawMovementSpeed() * VANILLA_SLOWDOWN_INVERSE;
        Impulse normalized = Impulse.normalize(event.getInput().forwardImpulse, event.getInput().leftImpulse);
        event.getInput().forwardImpulse = normalized.forward() * multiplier;
        event.getInput().leftImpulse = normalized.strafe() * multiplier;
    }

    /**
     * The pure scaling step behind #420's fix: a {@code (forward, strafe)} impulse pair, normalized
     * to unit length when both axes are non-zero so a diagonal press carries the same length as a
     * straight one. Single-axis and zero input pass through untouched, and sign is preserved.
     */
    record Impulse(float forward, float strafe) {
        static Impulse normalize(float forward, float strafe) {
            if (forward == 0.0F || strafe == 0.0F) {
                return new Impulse(forward, strafe);
            }
            float length = (float) Math.sqrt(forward * forward + strafe * strafe);
            return new Impulse(forward / length, strafe / length);
        }
    }

    private BowDrawMovement() {}
}
