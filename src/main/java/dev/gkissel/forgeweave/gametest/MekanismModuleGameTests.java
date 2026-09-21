package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.mekanism.ForgeweaveMekanismCompat;
import dev.gkissel.forgeweave.compat.mekanism.modules.MekanismGearModules;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.trait.EnergyBuffer;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * The Forgeweave half of issue #993's Mekanism module container: the metal assembles into a tool, the
 * compat item factory leaves the item plain with Mekanism absent, and a stack carrying module state
 * survives a {@code mekanismModules} round trip with its components intact.
 *
 * <h2>What these tests can and cannot reach</h2>
 *
 * <p>{@code runGameTestServer} runs with no Mekanism present (build.gradle declares it
 * {@code compileOnly} plus test classpath, deliberately with no {@code localRuntime}), so nothing here
 * may touch a class that names a {@code mekanism} type -- {@code MekanismModuleContainer} and
 * {@code MekanismModuleEffects} both do. Every assertion below therefore goes through
 * {@link MekanismGearModules}, the Mekanism-free seam every Forgeweave hook actually calls, with a
 * fixture bridge standing in for the real container the way {@code CompatToggleGameTests} does for
 * Draconic Evolution's.
 *
 * <p>Four things a GameTest cannot reach at all, and they are manual release-checklist lines on #975:
 * a module actually installing at the Modification Station, vein mining actually finding positions
 * through {@code ModuleVeinMiningUnit}, damage absorption actually firing, and the nucleosynthesizer
 * actually running the recipe. All four need a live Mekanism.
 *
 * <p>Each test keeps its set/assert/restore inside one synchronous method, for
 * {@code ContentFamilyGameTests}' own reason: these mutate a global config value and GameTests in a
 * batch tick concurrently, so no other test may ever observe a flipped value.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MekanismModuleGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static final String METAL = "atomic_matter_alloy";

    /**
     * The metal exists and assembles into a working tool with no Mekanism installed at all. It is
     * deliberately not existence-gated the way the four fusion welds are: D-M8-5 exempts materials,
     * and gating it would take the metal out of a world built with it the moment Mekanism came out of
     * the pack. Unobtainable without Mekanism is the gate, and that lives on the nucleosynthesizing
     * recipe's own {@code mod_loaded} condition.
     */
    @GameTest(template = "empty")
    public static void theMetalAssemblesIntoAToolWithoutMekanism(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tool = ToolAssembly.pickaxe(helper, player, POS, METAL, METAL, METAL);

        helper.assertTrue(tool.is(ForgeweaveItems.TOOL_PICKAXE.get()),
                "an atomic_matter_alloy pickaxe must assemble, got " + tool);
        ToolMaterials materials = tool.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        helper.assertTrue(materials != null && materials.all().contains(metalId()),
                "and must record the metal in its parts, got " + materials);
        helper.assertTrue(ForgeweaveMekanismCompat.isContainerStack(tool),
                "which is what makes it a module container");
        helper.succeed();
    }

    /**
     * The metal's own {@code energized4} trait is what gives the tool the {@code EnergyBuffer} D-M8-15
     * makes the container's energy, so a module has something to spend before any module is installed.
     */
    @GameTest(template = "empty")
    public static void theMetalCarriesTheBufferTheModulesSpend(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tool = ToolAssembly.pickaxe(helper, player, POS, METAL, METAL, METAL);

        int capacity = ForgeweaveTraits.energyCapacity(tool);
        helper.assertTrue(capacity > 0,
                "the energized4 trait must give the tool a Forge Energy buffer, got " + capacity);
        helper.assertTrue(EnergyBuffer.capability(tool) != null,
                "and that buffer must be exposed as a capability");
        helper.assertTrue(EnergyBuffer.stored(tool) == 0,
                "a freshly assembled tool starts empty, got " + EnergyBuffer.stored(tool));
        helper.succeed();
    }

    /**
     * The compat item factory produces the plain item when Mekanism is absent: no module container
     * component on a freshly assembled tool, and nothing across the seam answers for it. This is the
     * whole of issue #993's "produces the plain item when the mod is absent" line -- the component is
     * Mekanism's own type, so on this run it cannot exist at all, which the id walk below proves
     * without naming it in Java.
     */
    @GameTest(template = "empty")
    public static void withoutMekanismNothingCarriesAModuleContainer(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tool = ToolAssembly.pickaxe(helper, player, POS, METAL, METAL, METAL);

        ResourceLocation container = ResourceLocation.fromNamespaceAndPath(
                ForgeweaveMekanismCompat.MODID, "module_container");
        helper.assertTrue(!BuiltInRegistries.DATA_COMPONENT_TYPE.containsKey(container),
                "no Mekanism data component may exist on this run, or the test proves nothing");
        helper.assertTrue(tool.getComponents().stream().noneMatch(component -> ForgeweaveMekanismCompat.MODID
                        .equals(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()) == null
                                ? null
                                : BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component.type()).getNamespace())),
                "and the assembled tool must carry no mekanism component, got " + tool.getComponents());
        helper.assertTrue(MekanismGearModules.installedModules(tool) == 0,
                "and no module may be counted, got " + MekanismGearModules.installedModules(tool));
        helper.succeed();
    }

    /**
     * {@code mekanismModules = false}: every effect answers its neutral value, and the stack keeps
     * every component it had. Turning the toggle back on counts the same modules again with no reload,
     * which is D-M7-3's inert-not-destructive rule applied to compat.
     */
    @GameTest(template = "empty")
    public static void modulesOffGoInertAndKeepTheStackIntact(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tool = ToolAssembly.pickaxe(helper, player, POS, METAL, METAL, METAL);
        EnergyBuffer.receive(tool, ForgeweaveTraits.energyCapacity(tool), 50_000, false);
        ToolMaterials before = tool.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        List<ResourceLocation> traitsBefore = tool.get(ForgeweaveDataComponents.TRAITS.get());
        int storedBefore = EnergyBuffer.stored(tool);

        MekanismGearModules.install(new MekanismGearModules.Bridge() {
            @Override
            public int installedModules(ItemStack stack) {
                return 4;
            }

            @Override
            public int moduleEnergyCapacity(ItemStack stack) {
                return 64_000;
            }

            @Override
            public float digSpeedMultiplier(ItemStack stack) {
                return 3.0F;
            }

            @Override
            public int miningAoe(ItemStack stack) {
                return 2;
            }

            @Override
            public double radiationShielding(ItemStack stack) {
                return 1.0D;
            }
        });
        try {
            helper.assertTrue(MekanismGearModules.installedModules(tool) == 4,
                    "the fixture bridge must answer while mekanismModules is on, got "
                            + MekanismGearModules.installedModules(tool));

            ForgeweaveConfig.MEKANISM_MODULES.set(false);
            try {
                helper.assertTrue(MekanismGearModules.installedModules(tool) == 0,
                        "no module may be counted while mekanismModules is off, got "
                                + MekanismGearModules.installedModules(tool));
                helper.assertTrue(MekanismGearModules.moduleEnergyCapacity(tool) == 0,
                        "and no module energy either, got " + MekanismGearModules.moduleEnergyCapacity(tool));
                helper.assertTrue(MekanismGearModules.digSpeedMultiplier(tool) == 1.0F,
                        "and every effect must be neutral, got " + MekanismGearModules.digSpeedMultiplier(tool));
                helper.assertTrue(MekanismGearModules.miningAoe(tool) == 0,
                        "including the blasting radius, got " + MekanismGearModules.miningAoe(tool));
                helper.assertTrue(MekanismGearModules.radiationShielding(tool) == 0.0D,
                        "and radiation shielding, got " + MekanismGearModules.radiationShielding(tool));

                helper.assertTrue(before != null
                                && before.equals(tool.get(ForgeweaveDataComponents.TOOL_MATERIALS.get())),
                        "and the stack must keep the parts it was built from, got "
                                + tool.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()));
                helper.assertTrue(traitsBefore != null
                                && traitsBefore.equals(tool.get(ForgeweaveDataComponents.TRAITS.get())),
                        "and its traits, got " + tool.get(ForgeweaveDataComponents.TRAITS.get()));
                helper.assertTrue(EnergyBuffer.stored(tool) == storedBefore,
                        "and every unit of stored energy, got " + EnergyBuffer.stored(tool));
            } finally {
                ForgeweaveConfig.MEKANISM_MODULES.set(true);
            }

            helper.assertTrue(MekanismGearModules.installedModules(tool) == 4,
                    "turning mekanismModules back on must count the same modules again with no reload");
            helper.assertTrue(MekanismGearModules.digSpeedMultiplier(tool) == 3.0F,
                    "and every effect must work again, got " + MekanismGearModules.digSpeedMultiplier(tool));
        } finally {
            // Back to where an install with no Mekanism has it, per MekanismGearModules#install.
            MekanismGearModules.install(null);
        }
        helper.succeed();
    }

    /**
     * A module's silk touch reaches {@code getAllEnchantments} without being written to the stack, and
     * goes away again with the toggle. The tool's own enchantment component is never touched, which is
     * why module levels are exempt from {@code ModifierCompatibility}'s blasting exclusion.
     */
    @GameTest(template = "empty")
    public static void moduleEnchantmentsAreVirtualAndFollowTheToggle(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tool = ToolAssembly.pickaxe(helper, player, POS, METAL, METAL, METAL);

        ItemEnchantments granted = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY).toImmutable();
        MekanismGearModules.install(new MekanismGearModules.Bridge() {
            @Override
            public int installedModules(ItemStack stack) {
                return 1;
            }

            @Override
            public int moduleEnergyCapacity(ItemStack stack) {
                return 0;
            }

            @Override
            public ItemEnchantments moduleEnchantments(ItemStack stack) {
                return granted;
            }
        });
        try {
            helper.assertTrue(MekanismGearModules.moduleEnchantments(tool) == granted,
                    "the fixture bridge must hand over its enchantments while the toggle is on");

            ForgeweaveConfig.MEKANISM_MODULES.set(false);
            try {
                helper.assertTrue(MekanismGearModules.moduleEnchantments(tool).isEmpty(),
                        "and none while it is off, got " + MekanismGearModules.moduleEnchantments(tool));
            } finally {
                ForgeweaveConfig.MEKANISM_MODULES.set(true);
            }
        } finally {
            MekanismGearModules.install(null);
        }
        helper.succeed();
    }

    private static ResourceLocation metalId() {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, METAL);
    }
}
