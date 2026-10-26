package dev.gkissel.forgeweave.compat.apotheosis;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * What {@code forgeweave:sockets} holds: the gems this install can read, plus the raw saved form of
 * any it cannot.
 *
 * <p>A gem stops being readable when the mod that owns its item or one of its components is gone,
 * which is every Apotheosis gem on a world loaded without Apotheosis. Vanilla's
 * {@link ItemContainerContents} codec drops such an entry with "Tried to load invalid item", and the
 * next save writes the tool without it, so reinstalling the mod could not bring it back. Here the
 * entry is kept in {@link #unreadable} as the exact tag it was saved as, written back unchanged, and
 * decoded again on the first load that has the mod. While it is unreadable it grants nothing but still
 * fills its socket, so the station cannot seat a new gem over it.
 *
 * <p>The saved shape is vanilla's, a list of {@code {slot, item}}, so every save made before this
 * class existed loads as it is. Client sync carries the raw tag as opaque NBT, never as an item, so a
 * client without the mod never tries to resolve it. Sending the tag rather than a bare marker keeps
 * a creative-mode client, which hands whole stacks back to the server, from writing the gem away.
 *
 * @param gems the readable gems, indexed by socket
 * @param unreadable each unreadable socket's saved {@code item} tag, keyed by socket
 */
public record GemSockets(ItemContainerContents gems, Map<Integer, Tag> unreadable) {

    public static final GemSockets EMPTY = new GemSockets(ItemContainerContents.EMPTY, Map.of());

    /** One saved socket: a gem this install can decode, or the tag of one it cannot. */
    private record Slot(int slot, Either<ItemStack, Tag> item) {}

    private static final Codec<Tag> RAW = Codec.PASSTHROUGH.xmap(
            dynamic -> dynamic.convert(NbtOps.INSTANCE).getValue(),
            tag -> new Dynamic<>(NbtOps.INSTANCE, tag));

    /** Same fields and range as {@link ItemContainerContents}' own slot codec. */
    private static final Codec<Slot> SLOT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.intRange(0, 255).fieldOf("slot").forGetter(Slot::slot),
            // Either tries the stack first and falls back to the raw tag when that fails. A partial
            // result (an unknown component on a known item) counts as a failure, because keeping the
            // partial stack would drop that component.
            Codec.either(ItemStack.CODEC, RAW).fieldOf("item").forGetter(Slot::item))
            .apply(instance, Slot::new));

    public static final Codec<GemSockets> CODEC = SLOT_CODEC.sizeLimitedListOf(256)
            .xmap(GemSockets::fromSlots, GemSockets::toSlots);

    public static final StreamCodec<RegistryFriendlyByteBuf, GemSockets> STREAM_CODEC = StreamCodec.composite(
            ItemContainerContents.STREAM_CODEC, GemSockets::gems,
            ByteBufCodecs.map(HashMap::new, ByteBufCodecs.VAR_INT, ByteBufCodecs.TAG), GemSockets::unreadable,
            GemSockets::new);

    public GemSockets {
        unreadable = Map.copyOf(unreadable);
    }

    /** Readable gems only, indexed by socket, {@link ItemStack#EMPTY} for a socket left open. */
    public static GemSockets of(List<ItemStack> gems) {
        return new GemSockets(ItemContainerContents.fromItems(gems), Map.of());
    }

    /** These sockets with {@code gems} as the readable set, every unreadable entry kept as it was. */
    public GemSockets withGems(List<ItemStack> gems) {
        return new GemSockets(ItemContainerContents.fromItems(gems), unreadable);
    }

    private static GemSockets fromSlots(List<Slot> slots) {
        List<ItemStack> gems = new ArrayList<>();
        Map<Integer, Tag> unreadable = new HashMap<>();
        for (Slot slot : slots) {
            slot.item().ifLeft(gem -> {
                while (gems.size() <= slot.slot()) {
                    gems.add(ItemStack.EMPTY);
                }
                gems.set(slot.slot(), gem);
            }).ifRight(raw -> unreadable.put(slot.slot(), raw));
        }
        return new GemSockets(ItemContainerContents.fromItems(gems), unreadable);
    }

    /** Back to vanilla's shape, in socket order, so an untouched list re-encodes byte for byte. */
    private List<Slot> toSlots() {
        List<Slot> slots = new ArrayList<>();
        List<ItemStack> readable = gems.stream().toList();
        for (int i = 0; i < readable.size(); i++) {
            if (!readable.get(i).isEmpty()) {
                slots.add(new Slot(i, Either.left(readable.get(i))));
            }
        }
        unreadable.forEach((socket, raw) -> slots.add(new Slot(socket, Either.right(raw))));
        slots.sort(Comparator.comparingInt(Slot::slot));
        return slots;
    }
}
