package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.client.book.BookContent;
import dev.gkissel.forgeweave.client.book.BookPage;
import dev.gkissel.forgeweave.client.book.BookSection;
import dev.gkissel.forgeweave.item.ForgeweaveCreativeTab;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

/**
 * Issue #826's own existence-gating proof, both ways, with a gametest-only material each direction
 * (see src/gametest/resources/README.md) since a GameTest server can fake a {@code c:} tag but not a
 * modid or another mod's item id (docs/research/m6-material-expansion-references.md &sect;1.4): the
 * negative material conditions on {@code neoforge:mod_loaded} for a modid nothing supplies, the
 * positive one on {@code neoforge:item_exists} for {@code minecraft:diamond} (always true). Both
 * halves are proven across every consumer the mechanism claims needs zero code changes at (the M6
 * epic #824 / issue #826): the material registry itself, the creative tab's part expansion
 * ({@link ForgeweaveCreativeTab#addPartItems}), the guide book's material listing
 * ({@link BookContent#sections}), and the Part Builder's material matching
 * ({@link PartBuilderRecipes#materialValue}).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ConditionalMaterialGameTests {

    private static final ResourceLocation ABSENT = materialId("gametest_conditional_absent");
    private static final ResourceLocation PRESENT = materialId("gametest_conditional_present");

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    @GameTest(template = "empty")
    public static void aFailingConditionKeepsTheMaterialOutOfEveryConsumer(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();

        Registry<Material> materials = registries.registryOrThrow(Material.REGISTRY);
        helper.assertTrue(materials.get(ABSENT) == null,
                "expected the mod_loaded-gated material to be absent from the registry");

        helper.assertTrue(partItemMaterials(helper).stream().noneMatch(ABSENT::equals),
                "expected no creative-tab part variant of the mod_loaded-gated material");

        helper.assertTrue(materialPageIds(registries).stream().noneMatch(ABSENT::equals),
                "expected no guide-book material page for the mod_loaded-gated material");

        Optional<PartBuilderRecipes.MaterialMatch> match =
                PartBuilderRecipes.materialValue(registries, new ItemStack(Items.DIAMOND));
        helper.assertTrue(match.isEmpty(),
                "expected minecraft:diamond to match no material now that its only namer is absent, got " + match);

        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aPassingConditionPutsTheMaterialInEveryConsumer(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();

        Registry<Material> materials = registries.registryOrThrow(Material.REGISTRY);
        helper.assertTrue(materials.get(PRESENT) != null,
                "expected the item_exists-gated material to be registered (minecraft:diamond always exists)");

        helper.assertTrue(partItemMaterials(helper).stream().anyMatch(PRESENT::equals),
                "expected a creative-tab part variant of the item_exists-gated material");

        helper.assertTrue(materialPageIds(registries).stream().anyMatch(PRESENT::equals),
                "expected a guide-book material page for the item_exists-gated material");

        helper.succeed();
    }

    /**
     * The two conditions compose with the obtainability gate the real four metals still use (issue
     * #826 deliverable 1): {@code gametest_conditional_present}'s {@code crafting_items} keys on
     * {@code c:ingots/bronze}, the same synthetic tag {@code SteelAndTagGatedGameTests} used to plant
     * a stand-in modded ingot, so two of {@code minecraft:nether_brick} still craft a pickaxe head --
     * existence-gated and obtainability-gated at once, exactly as the shipped four are.
     */
    @GameTest(template = "empty")
    public static void aConditionSatisfiedMaterialsTagStillLightsUpThePartBuilder(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(pos);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), blockEntity.findSideInventory());

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(new ItemStack(Items.NETHER_BRICK, 2));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected a pickaxe head part from two tag-supplied ingots, got " + output);
        helper.assertTrue(PRESENT.equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the part's material to be " + PRESENT + ", got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));
        helper.succeed();
    }

    /** Every material id the creative tab would hand out a HEAD part variant for. */
    private static List<ResourceLocation> partItemMaterials(GameTestHelper helper) {
        CreativeModeTab.ItemDisplayParameters parameters = new CreativeModeTab.ItemDisplayParameters(
                FeatureFlags.VANILLA_SET, true, helper.getLevel().registryAccess());
        List<ItemStack> displayed = new ArrayList<>();
        // The 3-arg overload, explicit true: avoids ForgeweaveClientConfig (a CLIENT-type config
        // spec a dedicated/GameTest server never stands up), matching ForgeweaveCreativeTabGameTests.
        ForgeweaveCreativeTab.addPartItems(parameters, (stack, visibility) -> displayed.add(stack), true);
        return displayed.stream()
                .filter(stack -> stack.is(ForgeweaveItems.PART_PICKAXE_HEAD.get()))
                .map(stack -> stack.get(ForgeweaveDataComponents.MATERIAL.get()))
                .toList();
    }

    /** Every material id the guide book's materials section has a page for. */
    private static List<ResourceLocation> materialPageIds(RegistryAccess registries) {
        List<ResourceLocation> ids = new ArrayList<>();
        for (BookSection section : BookContent.sections(registries)) {
            for (BookPage page : section.pages()) {
                if (page instanceof BookPage.MaterialPage materialPage) {
                    ids.add(materialPage.id());
                }
            }
        }
        return ids;
    }

    private ConditionalMaterialGameTests() {}
}
