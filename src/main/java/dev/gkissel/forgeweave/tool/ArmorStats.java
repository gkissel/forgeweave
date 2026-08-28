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
 * An assembled armor piece's stat block (issue #678, M4-3; SCOPE.md D14), stored as
 * {@code forgeweave:armor_stats} the way a bow's ranged half is {@link LauncherStats} -- a parallel
 * component, so {@code forgeweave:tool_stats} keeps its shape. The 1.20 clone's
 * {@code PlatingMaterialStats#apply} copies the plating material's four per-piece numbers onto the
 * tool's stats verbatim (durability, armor, toughness, knockback resistance), and so does
 * {@link #of}: the maille contributes nothing here (D9, D19). A save-compat fixture pins this shape.
 */
public record ArmorStats(float armor, float toughness, float knockbackResistance, int durability) {

    public static final Codec<ArmorStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("armor").forGetter(ArmorStats::armor),
            Codec.FLOAT.fieldOf("toughness").forGetter(ArmorStats::toughness),
            Codec.FLOAT.fieldOf("knockback_resistance").forGetter(ArmorStats::knockbackResistance),
            Codec.INT.fieldOf("durability").forGetter(ArmorStats::durability))
            .apply(instance, ArmorStats::new));

    public static final StreamCodec<ByteBuf, ArmorStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ArmorStats::armor,
            ByteBufCodecs.FLOAT, ArmorStats::toughness,
            ByteBufCodecs.FLOAT, ArmorStats::knockbackResistance,
            ByteBufCodecs.VAR_INT, ArmorStats::durability,
            ArmorStats::new);

    /** This block with another armor value (#728: the overslime trait's build-time penalty). */
    public ArmorStats withArmor(float armor) {
        return new ArmorStats(armor, toughness, knockbackResistance, durability);
    }

    /**
     * The stats one set of part materials produces, or empty for an entry with no
     * {@link ToolConstants.Role#PLATING} slot (every tool). The piece is the entry's own id --
     * {@code helmet}, {@code chestplate}, {@code leggings}, {@code boots}.
     */
    public static Optional<ArmorStats> of(ToolConstants.Entry entry, List<Material> partMaterials) {
        List<ToolConstants.PartSlot> parts = entry.parts();
        for (int i = 0; i < parts.size(); i++) {
            if (parts.get(i).role() != ToolConstants.Role.PLATING) {
                continue;
            }
            Material.Plating plating = partMaterials.get(i).plating().orElseThrow(() -> ToolStats.noStats("plating"));
            Material.PlatingPiece piece = switch (entry.id()) {
                case "helmet" -> plating.helmet();
                case "chestplate" -> plating.chestplate();
                case "leggings" -> plating.leggings();
                case "boots" -> plating.boots();
                default -> throw new IllegalArgumentException(entry.id() + " is not an armor piece");
            };
            return Optional.of(new ArmorStats(piece.armor(), piece.toughness(), piece.knockbackResistance(),
                    piece.durability()));
        }
        return Optional.empty();
    }
}
