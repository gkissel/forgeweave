package dev.gkissel.forgeweave.config;

/**
 * What the Tool Station, Tool Forge and Modifier Worktable draw their result on
 * ({@code client/StandPreview}). A display preference only, so it lives in the client config.
 */
public enum StationPreviewModel {
    /** Upstream 1.20's armor stand. */
    ARMOR_STAND,
    /** A copy of the player looking at the screen, in their own skin. */
    PLAYER;

    /** Upstream parity is the default (CLAUDE.md maintainer directive). */
    public static final StationPreviewModel DEFAULT = ARMOR_STAND;
}
