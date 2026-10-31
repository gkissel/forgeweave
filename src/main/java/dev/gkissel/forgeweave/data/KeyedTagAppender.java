package dev.gkissel.forgeweave.data;

import java.util.function.Function;

import net.minecraft.data.tags.TagAppender;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagEntry;
import net.minecraft.tags.TagKey;

/**
 * A {@link TagAppender} that also takes registry objects. 26.2's appender takes only resource keys
 * (it dropped 26.1's {@code IntrinsicHolderTagsProvider} and {@code TagAppender#map}), so the tag
 * providers wrap it in this to keep adding blocks, items and fluids directly, the way vanilla's own
 * {@code BlockItemTagAppender} wraps it to take block item ids.
 */
final class KeyedTagAppender<T> implements TagAppender<T> {
    private final TagAppender<T> original;
    private final Function<T, ResourceKey<T>> keyOf;

    KeyedTagAppender(TagAppender<T> original, Function<T, ResourceKey<T>> keyOf) {
        this.original = original;
        this.keyOf = keyOf;
    }

    public KeyedTagAppender<T> add(T element) {
        original.add(keyOf.apply(element));
        return this;
    }

    @SafeVarargs
    public final KeyedTagAppender<T> add(T... elements) {
        for (T element : elements) {
            add(element);
        }
        return this;
    }

    @Override
    public KeyedTagAppender<T> add(ResourceKey<T> element) {
        original.add(element);
        return this;
    }

    @Override
    public KeyedTagAppender<T> addOptional(ResourceKey<T> element) {
        original.addOptional(element);
        return this;
    }

    @Override
    public KeyedTagAppender<T> addTag(TagKey<T> tag) {
        original.addTag(tag);
        return this;
    }

    @Override
    public KeyedTagAppender<T> addOptionalTag(TagKey<T> tag) {
        original.addOptionalTag(tag);
        return this;
    }

    @Override
    public KeyedTagAppender<T> add(TagEntry entry) {
        original.add(entry);
        return this;
    }

    @Override
    public KeyedTagAppender<T> replace(boolean value) {
        original.replace(value);
        return this;
    }

    @Override
    public KeyedTagAppender<T> remove(ResourceKey<T> element) {
        original.remove(element);
        return this;
    }

    @Override
    public KeyedTagAppender<T> remove(TagKey<T> tag) {
        original.remove(tag);
        return this;
    }
}
