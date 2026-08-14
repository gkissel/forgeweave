package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.Lifecycle;

import net.minecraft.SharedConstants;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

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
        // An empty Material registry is enough here since this test only cares about plain BlockItems.
        List<Item> displayedItems = build(true).stream().map(ItemStack::getItem).toList();

        List<Item> missing = ForgeweaveItems.ITEMS.getEntries().stream()
                .<Item>map(DeferredHolder::get)
                .filter(item -> item instanceof BlockItem)
                .filter(item -> !displayedItems.contains(item))
                .toList();

        assertTrue(missing.isEmpty(), () -> "Forgeweave block items missing from the creative tab: "
                + missing.stream().map(item -> BuiltInRegistries.ITEM.getKey(item)).toList());
    }

    /**
     * Issue #276, upstream 1.12's {@code listAllPartMaterials}: on (its default) the tab holds the
     * full part x material product; off, one variant per part. Drives the flag as a parameter --
     * it lives in a {@code CLIENT}-type config spec no unit test environment loads.
     */
    @Test
    void listAllPartMaterialsDecidesHowManyPartVariantsTheTabHolds() {
        int parts = ForgeweaveCreativeTab.PART_ITEMS.size();

        assertEquals(parts * MATERIALS.size(), partStacks(build(true, MATERIALS)).size(),
                "with listAllPartMaterials on, every part appears once per material");
        assertEquals(parts, partStacks(build(false, MATERIALS)).size(),
                "with listAllPartMaterials off, every part appears exactly once");
    }

    /** The first registered material is the one upstream's "first found material" keeps. */
    @Test
    void switchingListAllPartMaterialsOffKeepsTheFirstMaterial() {
        List<ResourceLocation> kept = partStacks(build(false, MATERIALS)).stream()
                .map(stack -> stack.get(ForgeweaveDataComponents.MATERIAL.get()))
                .distinct()
                .toList();

        assertEquals(List.of(MATERIALS.getFirst()), kept);
    }

    private static final List<ResourceLocation> MATERIALS = List.of(
            ResourceLocation.fromNamespaceAndPath("forgeweave", "test_first"),
            ResourceLocation.fromNamespaceAndPath("forgeweave", "test_second"),
            ResourceLocation.fromNamespaceAndPath("forgeweave", "test_third"));

    private static List<ItemStack> partStacks(List<ItemStack> displayed) {
        return displayed.stream().filter(stack -> stack.getItem() instanceof PartItem).toList();
    }

    private static List<ItemStack> build(boolean listAllPartMaterials) {
        return build(listAllPartMaterials, List.of());
    }

    private static List<ItemStack> build(boolean listAllPartMaterials, List<ResourceLocation> materialIds) {
        MappedRegistry<Material> materials = new MappedRegistry<>(Material.REGISTRY, Lifecycle.stable());
        for (ResourceLocation id : materialIds) {
            materials.register(ResourceKey.create(Material.REGISTRY, id), dummyMaterial(), RegistrationInfo.BUILT_IN);
        }
        materials.freeze();
        RegistryAccess.Frozen registryAccess = new RegistryAccess.ImmutableRegistryAccess(List.of(materials)).freeze();
        CreativeModeTab.ItemDisplayParameters parameters =
                new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, true, registryAccess);

        List<ItemStack> displayed = new ArrayList<>();
        ForgeweaveCreativeTab.addDisplayItems(parameters, (stack, visibility) -> displayed.add(stack), listAllPartMaterials);
        return displayed;
    }

    /** Only the identity matters here -- the tab keys part stacks off the material's id, not its stats. */
    private static Material dummyMaterial() {
        return new Material(
                new Material.Head(100, 1f, 1f),
                new Material.Handle(1f, 0),
                0,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(new Material.CraftingItem(Ingredient.of(Items.BONE), 1)),
                Ingredient.of(Items.BONE),
                TextColor.parseColor("#FFFFFF").getOrThrow());
    }
}
