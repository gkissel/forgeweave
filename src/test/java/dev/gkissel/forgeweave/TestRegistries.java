package dev.gkissel.forgeweave;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.mojang.serialization.DynamicOps;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.neoforged.neoforge.registries.DataPackRegistriesHooks;

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

    /**
     * The built-in registries plus the data-driven ones (enchantments, damage types, banner patterns,
     * jukebox songs, ...), loaded from the vanilla data pack and every loaded mod's pack the way a
     * server loads them, so every registry owns its own holders and they encode again. A mod's item
     * can name its own data-driven entry (Apotheosis's jukebox songs), so vanilla's pack alone is not
     * enough to bind every item's components. Built once per test run.
     *
     * <p>26.x: data-driven entries name built-in tags (an enchantment's supported_items), so the
     * static tags are loaded and handed to the loader as pending lookups, the way WorldLoader does
     * it. They are not applied: the built-in registries keep the empty tags a unit test expects.
     */
    public static synchronized HolderLookup.Provider vanilla() {
        if (vanilla == null) {
            RegistryAccess.Frozen builtins = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            PackRepository packs = ServerPacksSource.createVanillaTrustedRepository();
            packs.reload();
            packs.setSelected(packs.getAvailableIds().stream()
                    .filter(id -> id.equals("vanilla") || id.startsWith("mod/")).toList());
            ResourceManager data = new MultiPackResourceManager(PackType.SERVER_DATA, packs.openAllSelected());
            List<Registry.PendingTags<?>> staticTags = TagLoader.loadTagsForExistingRegistries(data, builtins);
            List<HolderLookup.RegistryLookup<?>> context = TagLoader.buildUpdatedLookups(builtins, staticTags);
            RegistryAccess.Frozen loaded = RegistryDataLoader.load(data, context,
                    DataPackRegistriesHooks.getDataPackRegistries(), Runnable::run, staticTags).join();
            vanilla = HolderLookup.Provider.create(Stream.concat(builtins.listRegistries(), loaded.listRegistries()));
        }
        return vanilla;
    }

    private static HolderLookup.Provider vanilla;

    /** {@code provider}, answering a missing tag the way {@link #ops} does: bound, empty. */
    public static HolderLookup.Provider lenient(HolderLookup.Provider provider) {
        return new HolderLookup.Provider() {
            @Override
            public Stream<ResourceKey<? extends Registry<?>>> listRegistryKeys() {
                return provider.listRegistryKeys();
            }

            @Override
            public <E> Optional<HolderLookup.RegistryLookup<E>> lookup(ResourceKey<? extends Registry<? extends E>> key) {
                return provider.<E>lookup(key).map(TestRegistries::lenientLookup);
            }
        };
    }

    private static <E> HolderLookup.RegistryLookup<E> lenientLookup(HolderLookup.RegistryLookup<E> lookup) {
        return new HolderLookup.RegistryLookup.Delegate<>() {
            @Override
            public HolderLookup.RegistryLookup<E> parent() {
                return lookup;
            }

            @Override
            public Optional<HolderSet.Named<E>> get(TagKey<E> id) {
                Optional<HolderSet.Named<E>> bound = lookup.get(id);
                return bound.isPresent() ? bound : bindEmpty(lookup, id);
            }
        };
    }

    private static <E> RegistryOps.RegistryInfo<E> lenient(HolderLookup.RegistryLookup<E> lookup) {
        // The owner stays the registry itself, so a holder or tag it hands out serializes back.
        return new RegistryOps.RegistryInfo<>(lookup, lenientLookup(lookup), lookup.registryLifecycle());
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
