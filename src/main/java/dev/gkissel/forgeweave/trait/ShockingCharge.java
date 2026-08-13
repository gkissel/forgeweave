package dev.gkissel.forgeweave.trait;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The shocking trait's serialized state (upstream 1.12 {@code TraitShocking.Data} -- see
 * {@code ForgeweaveTraits#SHOCKING}): the 0-100 charge the tool has built up, plus the holder
 * position the last movement sample was taken at, which is what the movement half of charging
 * measures distance against. Upstream persists the identical four fields
 * ({@code charge}/{@code x}/{@code y}/{@code z}) in the tool's modifier tag; this is that block as a
 * 1.21 data component ({@code ForgeweaveDataComponents#SHOCKING_CHARGE}).
 *
 * <p>Save-compat promised from the first M3.2 beta
 * ({@code fixtures/save_compat/m3_2_tool_shocking_charged.snbt}) -- see that fixture before changing
 * a field name or type. Charge is clamped to 100 on write (upstream stores small overshoots and only
 * ever compares {@code >= 100}), so the serialized range really is 0-100.
 */
public record ShockingCharge(float charge, double x, double y, double z) {

    public static final float FULL = 100.0F;

    public static final Codec<ShockingCharge> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("charge").forGetter(ShockingCharge::charge),
            Codec.DOUBLE.fieldOf("x").forGetter(ShockingCharge::x),
            Codec.DOUBLE.fieldOf("y").forGetter(ShockingCharge::y),
            Codec.DOUBLE.fieldOf("z").forGetter(ShockingCharge::z))
            .apply(instance, ShockingCharge::new));

    public static final StreamCodec<ByteBuf, ShockingCharge> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ShockingCharge::charge,
            ByteBufCodecs.DOUBLE, ShockingCharge::x,
            ByteBufCodecs.DOUBLE, ShockingCharge::y,
            ByteBufCodecs.DOUBLE, ShockingCharge::z,
            ShockingCharge::new);

    public boolean isFull() {
        return charge >= FULL;
    }

    /** This state with {@code amount} more charge, clamped to {@link #FULL}; position unchanged. */
    public ShockingCharge plus(float amount) {
        return new ShockingCharge(Math.min(FULL, charge + amount), x, y, z);
    }

    /** This state discharged back to zero; position unchanged. */
    public ShockingCharge discharged() {
        return new ShockingCharge(0.0F, x, y, z);
    }
}
