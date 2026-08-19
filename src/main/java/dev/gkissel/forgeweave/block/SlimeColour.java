package dev.gkissel.forgeweave.block;

import java.util.Locale;

import net.minecraft.world.level.material.MapColor;

/**
 * One colour of the slime family -- upstream 1.12's {@code BlockSlime.SlimeType} (NOTICE.md), the
 * metadata enum shared by its slime block, its congealed slime block and its edible slime ball
 * (issue #635, parity audit T57).
 *
 * <p>Upstream carries six values and hides {@link #GREEN} from the creative listing of its
 * <em>slime block</em> only, because vanilla already ships one; Forgeweave does the same by giving
 * green no slime block of its own (vanilla's {@code minecraft:slime_block} is it) while still
 * registering a green congealed block, which vanilla has no counterpart for. Every other colour gets
 * all three.
 *
 * <p>Upstream's {@code getColor}/{@code getBallColor} are deliberately not ported: they exist for
 * 1.12's tinted item/block <em>models</em>, and every sprite this ships is already coloured art
 * copied from the clone, so nothing would sample them.
 */
public enum SlimeColour {
    /** Upstream {@code SlimeType.GREEN}: vanilla slime, the only colour with no slime block of its own. */
    GREEN(MapColor.COLOR_GREEN),
    /** Upstream {@code SlimeType.BLUE}: the blue slime island's colour. */
    BLUE(MapColor.COLOR_LIGHT_BLUE),
    /** Upstream {@code SlimeType.PURPLE}: the purple slime island's colour, and knightslime's slime half. */
    PURPLE(MapColor.COLOR_PURPLE),
    /** Upstream {@code SlimeType.BLOOD}: cast from molten blood rather than found in the world. */
    BLOOD(MapColor.COLOR_RED),
    /** Upstream {@code SlimeType.MAGMA}: the Nether magma island's colour (issue #450). */
    MAGMA(MapColor.COLOR_ORANGE),
    /** Upstream {@code SlimeType.PINK}: what mixing slime colours gives, behind {@code matchVanillaSlimeblock}. */
    PINK(MapColor.COLOR_PINK);

    private final MapColor mapColor;

    SlimeColour(MapColor mapColor) {
        this.mapColor = mapColor;
    }

    /** Lowercase name, the registry-id prefix of every block and item of this colour. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The map colour its blocks paint with. */
    public MapColor mapColor() {
        return mapColor;
    }

    /**
     * Whether this colour has a coloured slime block of its own. False only for {@link #GREEN},
     * whose slime block is vanilla's -- upstream's {@code BlockSlime#getSubBlocks} skips it for the
     * same reason.
     */
    public boolean hasSlimeBlock() {
        return this != GREEN;
    }
}
