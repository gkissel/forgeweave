package dev.gkissel.forgeweave.api.tool;

/**
 * Which content family a registered tool belongs to. The public mirror of Forgeweave's own tool
 * categories (issue #1066); see {@link PartRole} for why this package mirrors rather than
 * re-exports.
 *
 * <p>A family is not decoration. Forgeweave's {@code content} config section has one switch per
 * family, and turning a family off hides every tool in it from the stations and the creative tab,
 * an addon's tools included. Pick the family a player would expect the tool to share a switch with.
 */
public enum ToolFamily {
    /** Mining and digging shapes. */
    HARVEST,
    /** Melee weapons. */
    MELEE,
    /** Bows, crossbows and thrown shapes. */
    RANGED,
    /** Worn armor pieces. */
    ARMOR
}
