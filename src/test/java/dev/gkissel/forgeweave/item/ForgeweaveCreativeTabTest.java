package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

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
 * never added to the tab's display-items method. Builds each tab's contents by calling those
 * methods directly (the minimal equivalent of the real
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

    /**
     * Issue #507 / T76: upstream 1.12 spreads its content over six creative tabs
     * ({@code TinkerRegistry:76-81}), Forgeweave shipped a single one. Four of the six have content
     * here -- General, Tools, Tool Parts, Smeltery; upstream's World tab would hold only the two
     * nether ores and its Gadgets content is absent entirely (T56).
     */
    @Test
    void theModRegistersOneTabPerUpstreamContentGroup() {
        List<String> ids = ForgeweaveCreativeTab.TABS.getEntries().stream()
                .map(holder -> holder.getId().getPath())
                .toList();

        assertEquals(List.of("general", "tools", "parts", "smeltery"), ids);
    }

    /** No item may be filed under two tabs -- upstream picks exactly one per item class. */
    @Test
    void noItemIsListedByTwoTabs() {
        List<Item> shared = tab(generalItems()).stream()
                .map(ItemStack::getItem)
                .filter(item -> tab(ForgeweaveCreativeTab::addToolItems).stream().anyMatch(s -> s.getItem() == item)
                        || tab(ForgeweaveCreativeTab::addSmelteryItems).stream().anyMatch(s -> s.getItem() == item)
                        || partsTab().stream().anyMatch(s -> s.getItem() == item))
                .toList();

        assertTrue(shared.isEmpty(), () -> "items listed by more than one tab: "
                + shared.stream().map(item -> BuiltInRegistries.ITEM.getKey(item)).toList());
    }

    /**
     * Upstream files every {@code ToolCore} under the Tools tab ({@code ToolCore:74}). The single
     * tab this replaced kept its own hand-written list and had silently lost the mattock and the
     * kama; the tab now reads {@code ToolAssemblyRecipes#ENTRIES}, so this asserts that table
     * really does cover every registered tool.
     */
    @Test
    void theToolsTabHoldsEveryRegisteredTool() {
        List<Item> displayed = tab(ForgeweaveCreativeTab::addToolItems).stream().map(ItemStack::getItem).toList();

        List<Item> missing = ForgeweaveItems.ITEMS.getEntries().stream()
                .<Item>map(DeferredHolder::get)
                .filter(item -> item instanceof ToolItem)
                .filter(item -> !displayed.contains(item))
                .toList();

        assertTrue(missing.isEmpty(), () -> "tools missing from the tools tab: "
                + missing.stream().map(item -> BuiltInRegistries.ITEM.getKey(item)).toList());
        assertEquals(List.of(), displayed.stream().filter(item -> !(item instanceof ToolItem)).toList(),
                "the tools tab holds assembled tools only");
    }

    /** Upstream files every {@code ToolPart} under the Tool Parts tab ({@code ToolPart:41}). */
    @Test
    void thePartsTabHoldsEveryRegisteredPart() {
        List<Item> displayed = partsTab().stream().map(ItemStack::getItem).toList();

        List<Item> missing = ForgeweaveItems.ITEMS.getEntries().stream()
                .<Item>map(DeferredHolder::get)
                .filter(item -> item instanceof PartItem)
                .filter(item -> !displayed.contains(item))
                .toList();

        assertTrue(missing.isEmpty(), () -> "parts missing from the tool parts tab: "
                + missing.stream().map(item -> BuiltInRegistries.ITEM.getKey(item)).toList());
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

    /** The union of all four tabs, i.e. everything the mod puts in front of a creative player. */
    private static List<ItemStack> build(boolean listAllPartMaterials, List<ResourceLocation> materialIds) {
        CreativeModeTab.ItemDisplayParameters parameters = parameters(materialIds);
        List<ItemStack> displayed = new ArrayList<>();
        CreativeModeTab.Output output = (stack, visibility) -> displayed.add(stack);
        ForgeweaveCreativeTab.addGeneralItems(parameters, output, true);
        ForgeweaveCreativeTab.addToolItems(parameters, output);
        ForgeweaveCreativeTab.addPartItems(parameters, output, listAllPartMaterials);
        ForgeweaveCreativeTab.addSmelteryItems(parameters, output);
        return displayed;
    }

    /** The General tab with table variants on -- unit tests have no item tags bound, so it lists one of each. */
    private static BiConsumer<CreativeModeTab.ItemDisplayParameters, CreativeModeTab.Output> generalItems() {
        return (parameters, output) -> ForgeweaveCreativeTab.addGeneralItems(parameters, output, true);
    }

    private static List<ItemStack> tab(BiConsumer<CreativeModeTab.ItemDisplayParameters, CreativeModeTab.Output> tab) {
        List<ItemStack> displayed = new ArrayList<>();
        tab.accept(parameters(List.of()), (stack, visibility) -> displayed.add(stack));
        return displayed;
    }

    private static List<ItemStack> partsTab() {
        List<ItemStack> displayed = new ArrayList<>();
        ForgeweaveCreativeTab.addPartItems(parameters(MATERIALS), (stack, visibility) -> displayed.add(stack), true);
        return displayed;
    }

    private static CreativeModeTab.ItemDisplayParameters parameters(List<ResourceLocation> materialIds) {
        MappedRegistry<Material> materials = new MappedRegistry<>(Material.REGISTRY, Lifecycle.stable());
        for (ResourceLocation id : materialIds) {
            materials.register(ResourceKey.create(Material.REGISTRY, id), dummyMaterial(), RegistrationInfo.BUILT_IN);
        }
        materials.freeze();
        RegistryAccess.Frozen registryAccess = new RegistryAccess.ImmutableRegistryAccess(List.of(materials)).freeze();
        return new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, true, registryAccess);
    }

    /**
     * Carries every stat block there is, so the counts above stay a statement about
     * {@code listAllPartMaterials} alone. Since issue #392 the tab also drops any (part, material)
     * pair the material has no stat block for -- a different rule with its own tests
     * ({@code BowMaterialTest}, {@code BowPartGameTests}) -- and a material missing the bow blocks
     * would silently turn those two assertions into a test of that rule instead.
     */
    private static Material dummyMaterial() {
        return new Material(
                Optional.of(new Material.Head(100, 1f, 1f)),
                Optional.of(new Material.Handle(1f, 0)),
                Optional.of(0),
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(new Material.CraftingItem(Ingredient.of(Items.BONE), 1)),
                Ingredient.of(Items.BONE),
                TextColor.parseColor("#FFFFFF").getOrThrow(),
                Optional.of(new Material.Bow(1f, 1f, 0f)),
                Optional.of(new Material.Bowstring(1f)));
    }
}
