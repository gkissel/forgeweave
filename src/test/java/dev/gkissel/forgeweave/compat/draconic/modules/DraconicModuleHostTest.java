package dev.gkissel.forgeweave.compat.draconic.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.modules.ModuleCategory;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleHostImpl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.energy.IEnergyStorage;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.trait.EnergyBuffer;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #956's unit half: which Forgeweave stacks are Draconic Evolution module hosts, how big their
 * grid is, which module categories their shape accepts, and that a Draconic energy module lands in
 * the same buffer a Forgeweave charger fills.
 *
 * <p>Runs with Draconic Evolution and BrandonsCore on the test classpath only, the same
 * {@code testCompileOnly}/{@code testRuntimeOnly} rows {@code FusionUpgradeRecipeTest} uses. What is
 * deliberately not here is anything needing Draconic Evolution's own registries to be populated: its
 * data component types, its module items and {@code ModuleEntity.CODEC}'s dispatch all exist only on
 * a running install, so module persistence and the energy-module sum are exercised by the maintainer
 * playtest rather than here. See the pull request.
 */
class DraconicModuleHostTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** Leaves the static bridge back where an install without Draconic Evolution has it. */
    @AfterEach
    void clearBridge() {
        DraconicModules.install(null);
    }

    /**
     * A stack of {@code item} carrying the tier marker for {@code level}, or none at level 0. The
     * ids read one behind the level because issue #965 added {@code evolving} under the three
     * shipped {@code evolved} ids rather than renumbering them.
     */
    private static final List<String> TIER_TRAITS = List.of("evolving", "evolved", "evolved2", "evolved3");

    private static ItemStack evolved(net.minecraft.world.level.ItemLike item, int level) {
        ItemStack stack = new ItemStack(item);
        if (level > 0) {
            stack.set(ForgeweaveDataComponents.TRAITS.get(), List.of(
                    ResourceLocation.fromNamespaceAndPath("forgeweave", TIER_TRAITS.get(level - 1))));
        }
        return stack;
    }

    /** Issue #965's grid table: 2x3, 2x6, 4x5 and 6x6, so 6 / 12 / 20 / 36 one-cell modules. */
    @Test
    void theGridTableIsTheMaintainersFourTierTable() {
        assertEquals(4, DraconicModules.MAX_EVOLVED, "one grid per Draconic Evolution tech level");
        assertEquals(List.of(2, 3), List.of(DraconicModules.gridWidth(1), DraconicModules.gridHeight(1)));
        assertEquals(List.of(2, 6), List.of(DraconicModules.gridWidth(2), DraconicModules.gridHeight(2)));
        assertEquals(List.of(4, 5), List.of(DraconicModules.gridWidth(3), DraconicModules.gridHeight(3)));
        assertEquals(List.of(6, 6), List.of(DraconicModules.gridWidth(4), DraconicModules.gridHeight(4)));

        assertEquals(List.of(6, 12, 20, 36), List.of(DraconicModules.moduleSlots(1),
                DraconicModules.moduleSlots(2), DraconicModules.moduleSlots(3), DraconicModules.moduleSlots(4)));
        for (int level = 1; level <= DraconicModules.MAX_EVOLVED; level++) {
            assertEquals(DraconicModules.moduleSlots(level),
                    DraconicModules.gridWidth(level) * DraconicModules.gridHeight(level),
                    "the slot count must be the grid's own cell count at evolved " + level);
        }
    }

    /**
     * Where Draconic Evolution's two multi-cell modules land, which is a shape question rather than
     * a cell-count one. The shield controller is 2x2 and the energy link 4x4, both read off
     * {@code ModuleTypes} in DE 1.21.1-3.1.4.633: the shield controller fits every tier, and the
     * energy link needs a grid at least four wide, so the two-wide inert and wyvern grids refuse it
     * however many cells they hold.
     */
    @Test
    void theShieldControllerFitsEveryTierAndTheEnergyLinkOnlyTheTopTwo() {
        for (int level = 1; level <= DraconicModules.MAX_EVOLVED; level++) {
            assertTrue(fits(level, 2, 2), "the 2x2 shield controller must fit at evolved " + level);
        }
        assertFalse(fits(1, 4, 4), "the inert 2x3 grid is too narrow for the 4x4 energy link");
        assertFalse(fits(2, 4, 4), "so is the wyvern 2x6 grid, wide as its cell count is");
        assertTrue(fits(3, 4, 4), "the awakened 4x5 grid takes it");
        assertTrue(fits(4, 4, 4), "and so does the chaotic 6x6 one");
    }

    private static boolean fits(int level, int width, int height) {
        return DraconicModules.gridWidth(level) >= width && DraconicModules.gridHeight(level) >= height;
    }

    @Test
    void aToolWithNoEvolvedLevelHasNoGridAndNoHost() {
        assertEquals(0, DraconicModules.moduleSlots(0));
        assertEquals(0, DraconicModules.moduleSlots(DraconicModules.MAX_EVOLVED + 1));
        assertNull(DraconicModuleHost.newHost(evolved(ForgeweaveItems.TOOL_PICKAXE.get(), 0)),
                "a tool with no evolved trait must not be a module host at all");
        assertNull(DraconicModuleHost.newHost(new ItemStack(Items.IRON_PICKAXE)));
    }

    @Test
    void theHostTierAndGridFollowTheEvolvedLevel() {
        List<TechLevel> expected =
                List.of(TechLevel.DRACONIUM, TechLevel.WYVERN, TechLevel.DRACONIC, TechLevel.CHAOTIC);
        for (int level = 1; level <= DraconicModules.MAX_EVOLVED; level++) {
            ModuleHostImpl host = DraconicModuleHost.newHost(evolved(ForgeweaveItems.TOOL_PICKAXE.get(), level));
            assertNotNull(host, "evolved " + level + " must be a host");
            assertEquals(expected.get(level - 1), host.getHostTechLevel());
            assertEquals(DraconicModules.gridWidth(level), host.getGridWidth());
            assertEquals(DraconicModules.gridHeight(level), host.getGridHeight());
        }
    }

    private static List<ModuleCategory> categoriesOf(net.minecraft.world.level.ItemLike item) {
        ModuleHostImpl host = DraconicModuleHost.newHost(evolved(item, 3));
        assertNotNull(host, item + " must be a host at evolved III");
        return List.copyOf(host.getModuleCategories());
    }

    @Test
    void everyHostTakesEnergyModules() {
        assertTrue(categoriesOf(ForgeweaveItems.TOOL_PICKAXE.get()).contains(ModuleCategory.ENERGY));
        assertTrue(categoriesOf(ForgeweaveItems.ARMOR_BOOTS.get()).contains(ModuleCategory.ENERGY));
        assertTrue(categoriesOf(ForgeweaveItems.TOOL_SHORTBOW.get()).contains(ModuleCategory.ENERGY));
    }

    @Test
    void toolCategoriesFollowTheToolsShape() {
        List<ModuleCategory> pickaxe = categoriesOf(ForgeweaveItems.TOOL_PICKAXE.get());
        assertTrue(pickaxe.contains(ModuleCategory.MINING_TOOL));
        assertFalse(pickaxe.contains(ModuleCategory.TOOL_AXE));
        assertFalse(pickaxe.contains(ModuleCategory.RANGED_WEAPON));

        assertTrue(categoriesOf(ForgeweaveItems.TOOL_HATCHET.get()).contains(ModuleCategory.TOOL_AXE));
        assertTrue(categoriesOf(ForgeweaveItems.TOOL_EXCAVATOR.get()).contains(ModuleCategory.TOOL_SHOVEL));
        assertTrue(categoriesOf(ForgeweaveItems.TOOL_KAMA.get()).contains(ModuleCategory.TOOL_HOE));

        List<ModuleCategory> broadsword = categoriesOf(ForgeweaveItems.TOOL_BROADSWORD.get());
        assertTrue(broadsword.contains(ModuleCategory.MELEE_WEAPON));

        List<ModuleCategory> shortbow = categoriesOf(ForgeweaveItems.TOOL_SHORTBOW.get());
        assertTrue(shortbow.contains(ModuleCategory.RANGED_WEAPON));
        assertFalse(shortbow.contains(ModuleCategory.MINING_TOOL));
    }

    @Test
    void armourCategoriesFollowTheSlotAndOnlyTheChestplateIsAChestpiece() {
        assertTrue(categoriesOf(ForgeweaveItems.ARMOR_HELMET.get()).contains(ModuleCategory.ARMOR_HEAD));
        assertTrue(categoriesOf(ForgeweaveItems.ARMOR_LEGGINGS.get()).contains(ModuleCategory.ARMOR_LEGS));
        assertTrue(categoriesOf(ForgeweaveItems.ARMOR_BOOTS.get()).contains(ModuleCategory.ARMOR_FEET));

        List<ModuleCategory> chestplate = categoriesOf(ForgeweaveItems.ARMOR_CHESTPLATE.get());
        assertTrue(chestplate.contains(ModuleCategory.ARMOR_CHEST));
        assertTrue(chestplate.contains(ModuleCategory.CHESTPIECE),
                "the chestplate carries DE's flight, shield and undying modules");
        assertFalse(categoriesOf(ForgeweaveItems.ARMOR_BOOTS.get()).contains(ModuleCategory.CHESTPIECE));
        assertTrue(categoriesOf(ForgeweaveItems.ARMOR_HEAVY_CHESTPLATE.get())
                .contains(ModuleCategory.CHESTPIECE));
    }

    /**
     * The energy adapter: a Draconic energy module's capacity lands in the same buffer
     * {@code EnergyBuffer} reads and writes, rather than a second one beside it. Uses a stub bridge
     * because the real sum needs Draconic Evolution's module registry, which no unit test has.
     */
    @Test
    void aDraconicEnergyModuleFillsTheSameBufferAForgeweaveChargerDoes() {
        ItemStack pickaxe = evolved(ForgeweaveItems.TOOL_PICKAXE.get(), 1);
        assertEquals(0, ForgeweaveTraits.energyCapacity(pickaxe));
        assertNull(EnergyBuffer.capability(pickaxe), "no trait and no module means no capability");

        DraconicModules.install(new DraconicModules.Bridge() {
            @Override
            public int installedModules(ItemStack stack) {
                return 1;
            }

            @Override
            public int moduleEnergyCapacity(ItemStack stack) {
                return 32_000;
            }
        });

        assertEquals(32_000, ForgeweaveTraits.energyCapacity(pickaxe));
        IEnergyStorage buffer = EnergyBuffer.capability(pickaxe);
        assertNotNull(buffer, "an installed energy module alone makes the tool chargeable");
        assertEquals(32_000, buffer.getMaxEnergyStored());
        assertEquals(5_000, buffer.receiveEnergy(5_000, false));
        assertEquals(5_000, buffer.getEnergyStored());
        assertEquals(5_000, EnergyBuffer.stored(pickaxe),
                "the module's charge is the same component a Forgeweave charger fills");
        assertEquals(27_000, buffer.receiveEnergy(30_000, false), "capacity still caps the buffer");
    }
}
