package dev.gkissel.forgeweave;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.TagLoader;
import net.minecraft.world.item.Item;

/**
 * Binds item tags by hand in plain JUnit, where no datapack is loaded.
 *
 * <p>26.x removed {@code Registry#bindTags}. A frozen registry now takes tags only through
 * {@code prepareTagReload(...).apply()}, and that replaces the whole tag set, so a tag left out of
 * the map disappears and a later {@code getOrThrow(tag)} fails with "Missing tag". 1.21.1's
 * {@code bindTags} instead emptied every tag it was not given and kept it. This keeps that: every
 * tag the registry already knows is rebound empty, then the given ones are bound to their values.
 */
public final class TestTags {

    private TestTags() {}

    /** Binds {@code tags}; every other known item tag is left present and empty. */
    public static void bindItemTags(Map<TagKey<Item>, List<Holder<Item>>> tags) {
        Map<TagKey<Item>, List<Holder<Item>>> all = new HashMap<>();
        BuiltInRegistries.ITEM.getTags().forEach(named -> all.put(named.key(), List.of()));
        all.putAll(tags);
        BuiltInRegistries.ITEM.prepareTagReload(new TagLoader.LoadResult<>(Registries.ITEM, all)).apply();
    }
}
