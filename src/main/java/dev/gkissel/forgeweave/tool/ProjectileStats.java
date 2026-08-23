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
 * The ranged half of an assembled projectile's stat block (issue #653, parity audit T17): upstream
 * 1.12's {@code ProjectileNBT}'s one field beyond {@code ToolNBT}, stored on the tool as
 * {@code forgeweave:projectile_stats} beside {@code ToolStats.Stats} -- the same split
 * {@link LauncherStats} already is for the bows. A save-compat fixture pins this shape
 * ({@code m653_tool_arrow.snbt}).
 *
 * @param accuracy the fletching's flight accuracy, {@code ProjectileNBT#fletchings}' average
 *     clamped into {@code [0, 1]} (feather 1.0, leaf 0.5). What it does to a shot is
 *     {@code MaterialArrowItem}'s inaccuracy adjustment, upstream {@code Arrow#getProjectile}:
 *     {@code inaccuracy -= (1 - 1/accuracy) * speed / 2}.
 */
public record ProjectileStats(float accuracy) {

    public static final Codec<ProjectileStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("accuracy").forGetter(ProjectileStats::accuracy))
            .apply(instance, ProjectileStats::new));

    public static final StreamCodec<ByteBuf, ProjectileStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ProjectileStats::accuracy,
            ProjectileStats::new);

    /**
     * The projectile stats one set of part materials produces, or empty for a tool with no
     * {@link ToolConstants.Role#FLETCHING} slot (everything but the arrow). Upstream
     * {@code ProjectileNBT#fletchings}: the fletchings' accuracy averaged, then
     * {@code min(1, max(0, accuracy))}.
     */
    public static Optional<ProjectileStats> of(ToolConstants.Entry entry, List<Material> partMaterials) {
        List<ToolConstants.PartSlot> parts = entry.parts();
        float accuracy = 0f;
        int fletchings = 0;
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).role() != ToolConstants.Role.FLETCHING) {
                continue;
            }
            accuracy += partMaterials.get(i).fletching().orElseThrow(() -> ToolStats.noStats("fletching"))
                    .accuracy();
            fletchings++;
        }
        if (fletchings == 0) {
            return Optional.empty();
        }
        return Optional.of(new ProjectileStats(Math.min(1f, Math.max(0f, accuracy / fletchings))));
    }
}
