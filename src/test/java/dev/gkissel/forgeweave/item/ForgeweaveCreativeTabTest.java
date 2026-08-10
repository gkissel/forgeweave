package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.Lifecycle;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.registries.DeferredHolder;

import dev.gkissel.forgeweave.material.Material;

/**
 * Regression for issue #139: the maintainer could not find the smeltery controller, seared tanks,
 * drain, faucet, or casting blocks in the creative tab because several M2 functional blocks were
 * never added to {@link ForgeweaveCreativeTab#addDisplayItems}. Builds the tab's contents by
 * calling that method directly (the minimal equivalent of the real
 * {@code CreativeModeTab#buildContents} path -- that path also posts a NeoForge mod-bus event this
 * unit test environment doesn't stand up) and asserts every Forgeweave item backed by a BlockItem
 * shows up, so the next new block can't be forgotten the same way.
 */
class ForgeweaveCreativeTabTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyBlockItemAppearsInTheCreativeTab() {
        // addDisplayItems looks up the Material registry (for the per-material part variants); an
        // empty one is enough since this test only cares about the plain BlockItems.
        MappedRegistry<Material> materials = new MappedRegistry<>(Material.REGISTRY, Lifecycle.stable());
        RegistryAccess.Frozen registryAccess = new RegistryAccess.ImmutableRegistryAccess(List.of(materials)).freeze();
        CreativeModeTab.ItemDisplayParameters parameters =
                new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, true, registryAccess);

        List<ItemStack> displayed = new ArrayList<>();
        ForgeweaveCreativeTab.addDisplayItems(parameters, (stack, visibility) -> displayed.add(stack));

        List<Item> displayedItems = displayed.stream().map(ItemStack::getItem).toList();

        List<Item> missing = ForgeweaveItems.ITEMS.getEntries().stream()
                .<Item>map(DeferredHolder::get)
                .filter(item -> item instanceof BlockItem)
                .filter(item -> !displayedItems.contains(item))
                .toList();

        assertTrue(missing.isEmpty(), () -> "Forgeweave block items missing from the creative tab: "
                + missing.stream().map(item -> BuiltInRegistries.ITEM.getKey(item)).toList());
    }
}
