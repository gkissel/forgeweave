package dev.gkissel.forgeweave.tool;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import dev.gkissel.forgeweave.material.Material;

/**
 * The ranged half of an assembled bow's stat block (M3.5 issue #394): upstream 1.12's
 * {@code ProjectileLauncherNBT}'s three fields, stored on the tool as
 * {@code forgeweave:launcher_stats} beside {@link ToolStats.Stats} (which stays the melee half --
 * durability, attack, mining speed -- exactly as {@code ProjectileLauncherNBT extends ToolNBT}
 * keeps both on one tag). A save-compat fixture pins this shape (docs/SCOPE.md).
 *
 * <p>{@link #of} is {@code ProjectileLauncherNBT#limb(BowMaterialStats...)}, ported whole: every
 * {@link ToolConstants.Role#LIMB} slot's BOW block, averaged field by field, then {@code drawSpeed}
 * and {@code range} floored at {@code 0.001f} (upstream's guard against a zero divisor in the draw
 * formula). Upstream's per-tool follow-ups -- the crossbow's {@code bonusDamage *= 1.5f} -- are
 * M3.5-4's to add here when it lands.
 *
 * @param drawSpeed how fast the bow draws; {@code BowItem}'s draw progress is
 *     {@code drawSpeed * ticks / drawTime} ({@code BowCore#getDrawbackProgress})
 * @param range multiplier on the arrow's launch velocity ({@code BowCore#shootProjectile})
 * @param bonusDamage flat damage added to what the arrow deals
 */
public record LauncherStats(float drawSpeed, float range, float bonusDamage) {

    public static final Codec<LauncherStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("draw_speed").forGetter(LauncherStats::drawSpeed),
            Codec.FLOAT.fieldOf("range").forGetter(LauncherStats::range),
            Codec.FLOAT.fieldOf("bonus_damage").forGetter(LauncherStats::bonusDamage))
            .apply(instance, LauncherStats::new));

    public static final StreamCodec<ByteBuf, LauncherStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, LauncherStats::drawSpeed,
            ByteBufCodecs.FLOAT, LauncherStats::range,
            ByteBufCodecs.FLOAT, LauncherStats::bonusDamage,
            LauncherStats::new);

    /** Upstream's floor on {@code drawSpeed} and {@code range} ({@code ProjectileLauncherNBT#limb}). */
    private static final float MINIMUM = 0.001f;

    /**
     * The launcher stats one set of part materials produces, or empty for a tool with no
     * {@link ToolConstants.Role#LIMB} slot (every non-bow) -- see the class javadoc.
     */
    public static Optional<LauncherStats> of(ToolConstants.Entry entry, List<Material> partMaterials) {
        List<ToolConstants.PartSlot> parts = entry.parts();
        float drawSpeed = 0f;
        float range = 0f;
        float bonusDamage = 0f;
        int limbs = 0;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).role() != ToolConstants.Role.LIMB) {
                continue;
            }
            Material.Bow bow = partMaterials.get(i).bow().orElseThrow(() -> ToolStats.noStats("bow"));
            drawSpeed += bow.drawspeed();
            range += bow.range();
            bonusDamage += bow.bonusDamage();
            limbs++;
        }
        if (limbs == 0) {
            return Optional.empty();
        }
        return Optional.of(new LauncherStats(
                Math.max(MINIMUM, drawSpeed / limbs),
                Math.max(MINIMUM, range / limbs),
                bonusDamage / limbs));
    }
}
