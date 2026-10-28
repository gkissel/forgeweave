package dev.gkissel.forgeweave;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;

/**
 * {@link RegistryOps} for plain JUnit, where no datapack is loaded and so no tag exists.
 *
 * <p>1.21.1's ingredient codec carried a tag as a bare {@code TagKey} and never looked it up, so a
 * test could parse {@code {"tag": "c:ingots/iron"}} with nothing bound. 26.x resolves an
 * ingredient's {@code "#c:ingots/iron"} through the registry lookup ({@code HolderSetCodec}), and
 * a frozen registry answers only the tags it actually has: every other one is "Missing tag". These
 * ops answer a missing tag by binding it, empty, on the registry itself -- which is what every tag
 * was in a 1.21.1 unit test, and what keeps the holder sets owned by the real registry so they
 * encode again. A tag that is already bound (see {@link TestTags}) is answered as it is.
 */
public final class TestRegistries {

    private TestRegistries() {}

    /** Ops over every built-in registry. */
    public static <T> RegistryOps<T> ops(DynamicOps<T> ops) {
        return ops(ops, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /** Ops over {@code provider}'s registries, lenient on missing tags the same way. */
    public static <T> RegistryOps<T> ops(DynamicOps<T> ops, HolderLookup.Provider provider) {
        return RegistryOps.create(ops, new RegistryOps.RegistryInfoLookup() {
            @Override
            public <E> Optional<RegistryOps.RegistryInfo<E>> lookup(ResourceKey<? extends Registry<? extends E>> key) {
                return provider.lookup(key).map(TestRegistries::lenient);
            }
        });
    }

    private static <E> RegistryOps.RegistryInfo<E> lenient(HolderLookup.RegistryLookup<E> lookup) {
        HolderGetter<E> getter = new HolderGetter<>() {
            @Override
            public Optional<Holder.Reference<E>> get(ResourceKey<E> id) {
                return lookup.get(id);
            }

            @Override
            public Optional<HolderSet.Named<E>> get(TagKey<E> id) {
                Optional<HolderSet.Named<E>> bound = lookup.get(id);
                return bound.isPresent() ? bound : bindEmpty(lookup, id);
            }
        };
        // The owner stays the registry itself, so a holder or tag it hands out serializes back.
        return new RegistryOps.RegistryInfo<>(lookup, getter, lookup.registryLifecycle());
    }

    /** Adds {@code tag}, empty, to {@code lookup}'s registry, keeping every tag it already has as it is. */
    private static synchronized <E> Optional<HolderSet.Named<E>> bindEmpty(HolderLookup.RegistryLookup<E> lookup, TagKey<E> tag) {
        if (!(lookup instanceof Registry<E> registry)) {
            return Optional.empty();
        }
        Map<TagKey<E>, List<Holder<E>>> all = new HashMap<>();
        registry.getTags().forEach(named -> all.put(named.key(), named.stream().toList()));
        all.put(tag, List.of());
        registry.prepareTagReload(new TagLoader.LoadResult<>(registry.key(), all)).apply();
        return registry.get(tag);
    }
}
