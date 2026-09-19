package dev.gkissel.forgeweave.api.tool;

/**
 * Which of a material's stat blocks a part <em>item</em> carries, and so which of that material's
 * trait scopes it grants and which materials can stamp it at all. The public mirror of Forgeweave's
 * own internal part kinds (issue #1066); see {@link PartRole} for why this package mirrors rather
 * than re-exports, and {@code PartRoleMirrorTest} for what keeps the two in step.
 *
 * <p>A kind is a property of the item, a {@link PartRole} is a property of a slot in a tool. They
 * are not the same list: a bow limb is one item kind ({@link #BOW}) but fills a slot that reads two
 * blocks ({@link PartRole#LIMB}).
 */
public enum PartKind {
    HEAD,
    HANDLE,
    /** Flat durability only: bindings, guards, grips. */
    EXTRA,
    /** A bow limb's own stat block. */
    BOW,
    BOWSTRING,
    SHAFT,
    FLETCHING,
    /** The dummy block every head material carries; no part item registers with it. */
    PROJECTILE,
    PLATING,
    MAILLE,
    /** No stat block at all -- a leftover such as a shard, which every material can produce. */
    NONE
}
