package dev.gkissel.forgeweave.api.tool;

/**
 * Which of a material's stat blocks one slot of a registered tool draws from, and which trait scope
 * that slot grants. The public mirror of Forgeweave's own internal part roles (issue #1066).
 *
 * <p>It is a mirror rather than the internal enum itself because this package is the only Forgeweave
 * code an addon compiles against, and it imports nothing but Minecraft, NeoForge and itself. The two
 * enums carry the same constant names and {@code ApiToolMirrorTest} fails the build if they ever
 * drift, so a new role reaches addons by being added here too.
 *
 * <p>The roles are the upstream 1.12 {@code PartMaterialType} set. A few read two blocks at once:
 * {@link #CROSSBOW_BODY} counts as both an extra part and a handle, {@link #SHURIKEN_BLADE} as both
 * a head and an extra part, and {@link #LIMB} feeds both the melee stats and the launcher's.
 */
public enum PartRole {
    /** Durability, attack damage and mining speed; the slot a repair is normally paid in. */
    HEAD,
    /** Flat durability only -- a binding, a guard, a grip. */
    EXTRA,
    /** A durability multiplier plus flat durability -- a rod or haft. */
    HANDLE,
    /** A crossbow's body: an extra part and a handle at once. */
    CROSSBOW_BODY,
    /** A bow limb: a head for the melee stats, a bow block for the launcher stats. */
    LIMB,
    /** A bowstring: one durability multiplier. */
    BOWSTRING,
    /** A thrown blade: a head and an extra part at once. */
    SHURIKEN_BLADE,
    /** An arrow head: a head statwise, with the projectile trait scope on top. */
    ARROW_HEAD,
    /** An arrow shaft: a durability multiplier plus bonus ammo. */
    SHAFT,
    /** An armor piece's plating, which carries that piece's whole stat block. */
    PLATING,
    /** An armor piece's maille: statless, traits and the inner texture layer only. */
    MAILLE,
    /** An arrow's fletching: a durability multiplier plus accuracy. */
    FLETCHING
}
