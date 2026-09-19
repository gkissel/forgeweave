package dev.gkissel.forgeweave.api.tool;

import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * A tool part another mod wants Forgeweave's stations to handle: the part item itself, the stat
 * block it draws from, what stamping one costs, and the pattern that stamps it (issue #1066).
 *
 * <p>Registering a part is what puts it on the Stencil Table's pattern grid and in the Part
 * Builder's table. A part that is only ever cast, never stamped, still registers -- with
 * {@link #castOnly} -- so the Part Builder knows the cost the Smeltery should price its cast at;
 * it simply has no pattern and so never appears at the Stencil Table.
 *
 * <p>Both suppliers are read after the item registry is populated, so a {@code DeferredItem} or any
 * other lazy holder is fine, and registering the part with Forgeweave and with the item registry can
 * happen in either order.
 *
 * @param partId the part item's registry id, which is what a {@link ToolDefinition} slot names
 * @param kind which stat block this part carries
 * @param part the part item, which must have been created by
 *     {@link ForgeweaveTools#partItem(PartKind, Item.Properties)}
 * @param pattern the pattern that stamps it, or {@code null} for a part that is only ever cast
 * @param cost what one costs in Forgeweave's material-value units, where one ingot is
 *     {@link ForgeweaveTools#INGOT_VALUE}
 */
public record PartDefinition(
        ResourceLocation partId,
        PartKind kind,
        Supplier<? extends Item> part,
        Supplier<? extends Item> pattern,
        int cost) {

    /** A part stamped from a pattern at the Part Builder, and offered at the Stencil Table. */
    public static PartDefinition stamped(ResourceLocation partId, PartKind kind, Supplier<? extends Item> part,
            Supplier<? extends Item> pattern, int cost) {
        return new PartDefinition(partId, kind, part, pattern, cost);
    }

    /** A part with no pattern: castable, never stamped. */
    public static PartDefinition castOnly(ResourceLocation partId, PartKind kind, Supplier<? extends Item> part,
            int cost) {
        return new PartDefinition(partId, kind, part, null, cost);
    }

    public PartDefinition {
        if (cost <= 0) {
            throw new IllegalArgumentException(partId + ": a part costs at least one value unit");
        }
    }
}
