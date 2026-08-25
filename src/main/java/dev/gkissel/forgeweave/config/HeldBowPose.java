package dev.gkissel.forgeweave.config;

/**
 * Which {@code display} block the shortbow, longbow and crossbow are held with, in every draw state
 * (issue #723, maintainer decision from the beta.1 playtest review). Both sets are datagen'd side
 * by side by {@code ForgeweaveItemModelProvider}; {@code ForgeweaveItemProperties#modernPose} is
 * the runtime switch between them.
 */
public enum HeldBowPose {
    /** Upstream 1.12's {@code <bow>.tcon.json} display blocks, verbatim (#693, #712). */
    CLASSIC,
    /** Vanilla 1.21.1's {@code item/bow.json} / {@code item/crossbow.json} display blocks. */
    MODERN;

    /** 1.12 parity is the default (CLAUDE.md maintainer directive). */
    public static final HeldBowPose DEFAULT = CLASSIC;
}
