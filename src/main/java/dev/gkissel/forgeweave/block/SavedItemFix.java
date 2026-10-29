package dev.gkissel.forgeweave.block;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.DynamicOps;

import net.minecraft.SharedConstants;
import net.minecraft.core.NonNullList;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.ItemStackWithSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Runs the item stacks a Forgeweave block entity saved on 1.21.1 through vanilla's item data fixer.
 *
 * <p>Vanilla fixes the items inside its own block entities when it loads an old chunk, but its schema
 * routes any block entity id it does not know to {@code DSL.remainder()}, so a stack saved in a tool
 * station, pattern chest, casting table or smeltery reaches the 26.1 codec in its 1.21.1 shape. Without
 * this, an enchanted tool left in a station loses its enchantments on load (1.21.5 unwrapped
 * {@code minecraft:enchantments}' {@code levels}) and its custom name shows as raw JSON.
 *
 * <p>Same scheme as vanilla's {@code DataFixTypes#wrapCodec}: every save writes {@link #VERSION_KEY},
 * and a tag without it is a 1.21.1 one, since Forgeweave 1.0.0 shipped only for 1.21.1.
 */
public final class SavedItemFix {

    /** Root-level key, beside the block entity's other fields. */
    public static final String VERSION_KEY = "forgeweave:data_version";

    /** The data version 1.21.1 stamps on everything it saves. */
    static final int DATA_VERSION_1_21_1 = 3955;

    private SavedItemFix() {
    }

    private static int currentVersion() {
        return SharedConstants.getCurrentVersion().dataVersion().version();
    }

    /** Call from {@code saveAdditional} of every block entity that reads its items through this class. */
    public static void writeVersion(ValueOutput output) {
        output.putInt(VERSION_KEY, currentVersion());
    }

    /**
     * {@code codec}, or {@code codec} with vanilla's item stack fix in front of it when {@code root}
     * was saved before this version. {@code root} is the block entity's own input, the one
     * {@link #writeVersion} wrote to; {@code codec} decodes one item stack shaped tag
     * ({@link ItemStack#CODEC}, {@link ItemStackWithSlot#CODEC}).
     */
    public static <A> Codec<A> items(ValueInput root, Codec<A> codec) {
        int from = root.getIntOr(VERSION_KEY, DATA_VERSION_1_21_1);
        return from >= currentVersion() ? codec : fixingFrom(from, codec);
    }

    /** {@code ContainerHelper#loadAllItems(items, stacks)}, with the fix {@link #items} applies. */
    public static void loadAllItems(ValueInput root, ValueInput items, NonNullList<ItemStack> stacks) {
        for (ItemStackWithSlot item : items.listOrEmpty("Items", items(root, ItemStackWithSlot.CODEC))) {
            if (item.isValidInContainer(stacks.size())) {
                stacks.set(item.slot(), item.stack());
            }
        }
    }

    static <A> Codec<A> fixingFrom(int from, Codec<A> codec) {
        return new Codec<>() {
            @Override
            public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
                return codec.encode(input, ops, prefix);
            }

            @Override
            public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
                Dynamic<T> fixed = DataFixers.getDataFixer().update(References.ITEM_STACK,
                        new Dynamic<>(ops, input), from, currentVersion());
                return codec.decode(fixed);
            }
        };
    }
}
