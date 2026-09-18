package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.create.CreateGoggles;
import dev.gkissel.forgeweave.compat.draconic.ForgeweaveDraconicCompat;
import dev.gkissel.forgeweave.compat.draconic.modules.DraconicModules;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.MiningLevel;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.Trait;

/**
 * Every {@code compat} toggle (D-M8-5) switched off and back on -- the four issue #968 backfills
 * plus issue #1007's Create goggles, which that PR left to this mechanism. {@code
 * ContentFamilyGameTests}' shape applied to compat, including its reason for keeping every
 * set/assert/restore inside one synchronous method: these mutate a global config value and GameTests
 * in a batch tick concurrently, so no other test may ever observe a flipped value.
 *
 * <p>Three of them are also tested for the half that must <em>not</em> change. Off is inert, never
 * destructive: a stack carrying fusion, module or modifier state keeps its components through a
 * toggle-off round trip and works again when the toggle returns, which is D-M7-3's rule applied to
 * compat.
 *
 * <h2>What these tests can and cannot reach</h2>
 *
 * <p>{@code runGameTestServer} runs with none of the compat mods present, so no test here may touch
 * a class that names one of their types: {@code FusionUpgradeRecipe} and {@code DraconicModuleHost}
 * name {@code com.brandon3055} types, {@code ForgeweaveCreateCompat} names a
 * {@code com.simibubi.create} one, and both overlay plugins are compiled against APIs absent from
 * that run. Each toggle is therefore read at a site in the mod-free half of its integration, and
 * those sites are what these tests exercise: {@code ForgeweaveDraconicCompat#acceptsFusionCatalyst},
 * {@link DraconicModules}'s bridge queries, {@link CreateGoggles#isWearingGoggles},
 * {@code MiningLevel#line} and {@code ForgeweaveTraits#lookup}. The remaining per-provider guards
 * and the Draconic capability itself are release-checklist lines on #975, alongside the overlay
 * rendering the issue already notes a GameTest cannot cover.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CompatToggleGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /** {@code chaosmark} plus {@code evolved3}: a chaotic-tier Draconic core tool. */
    private static final List<ResourceLocation> CHAOTIC_CORE = List.of(
            ForgeweaveDraconicCompat.CORE_MARKERS.get(3),
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "evolved3"));

    /**
     * Fusion off: no tool is a fusion catalyst at any tech level, and the tool's own trait component
     * -- the record of what it was built from, and so of the tier it stands on -- is untouched by
     * the flip, so turning the toggle back on accepts the same stack again with no reload.
     */
    @GameTest(template = "empty")
    public static void draconicFusionOffAcceptsNoCatalystAndKeepsTheToolIntact(GameTestHelper helper) {
        ItemStack tool = coreTool(helper);
        List<ResourceLocation> before = tool.get(ForgeweaveDataComponents.TRAITS.get());

        helper.assertTrue(ForgeweaveDraconicCompat.acceptsFusionCatalyst("chaotic", tool),
                "a chaotic-tier core tool is a fusion catalyst while draconicFusion is on, or this "
                        + "test proves nothing");

        ForgeweaveConfig.DRACONIC_FUSION.set(false);
        try {
            for (ForgeweaveDraconicCompat.FusionMetal metal : ForgeweaveDraconicCompat.FUSION_METALS) {
                helper.assertFalse(ForgeweaveDraconicCompat.acceptsFusionCatalyst(metal.techLevel(), tool),
                        "no rung may accept a catalyst while draconicFusion is off, got " + metal.techLevel());
            }
            helper.assertTrue(before.equals(tool.get(ForgeweaveDataComponents.TRAITS.get())),
                    "a refused tool must keep every component it had, got "
                            + tool.get(ForgeweaveDataComponents.TRAITS.get()));
        } finally {
            ForgeweaveConfig.DRACONIC_FUSION.set(true);
        }

        helper.assertTrue(ForgeweaveDraconicCompat.acceptsFusionCatalyst("chaotic", tool),
                "turning draconicFusion back on must accept the same stack again with no reload");
        helper.succeed();
    }

    /**
     * Modules off: every module query answers exactly as it does on an install with no Draconic
     * Evolution at all, while the stack keeps its trait component -- so an installed module is inert
     * rather than lost, and acts again when the toggle returns.
     */
    @GameTest(template = "empty")
    public static void draconicModulesOffAnswersNothingAndKeepsTheStackIntact(GameTestHelper helper) {
        ItemStack tool = coreTool(helper);
        List<ResourceLocation> before = tool.get(ForgeweaveDataComponents.TRAITS.get());

        DraconicModules.install(new DraconicModules.Bridge() {
            @Override
            public int installedModules(ItemStack stack) {
                return 3;
            }

            @Override
            public int moduleEnergyCapacity(ItemStack stack) {
                return 64_000;
            }

            @Override
            public float digSpeedMultiplier(ItemStack stack) {
                return 2.0F;
            }
        });
        try {
            helper.assertTrue(DraconicModules.installedModules(tool) == 3,
                    "the fixture bridge must answer while draconicModules is on, got "
                            + DraconicModules.installedModules(tool));

            ForgeweaveConfig.DRACONIC_MODULES.set(false);
            try {
                helper.assertTrue(DraconicModules.installedModules(tool) == 0,
                        "no module may be counted while draconicModules is off, got "
                                + DraconicModules.installedModules(tool));
                helper.assertTrue(DraconicModules.moduleEnergyCapacity(tool) == 0,
                        "and no module energy either, got " + DraconicModules.moduleEnergyCapacity(tool));
                helper.assertTrue(DraconicModules.digSpeedMultiplier(tool) == 1.0F,
                        "and every effect must be neutral, got " + DraconicModules.digSpeedMultiplier(tool));
                helper.assertTrue(before.equals(tool.get(ForgeweaveDataComponents.TRAITS.get())),
                        "and the stack must keep every component it had, got "
                                + tool.get(ForgeweaveDataComponents.TRAITS.get()));
            } finally {
                ForgeweaveConfig.DRACONIC_MODULES.set(true);
            }

            helper.assertTrue(DraconicModules.installedModules(tool) == 3,
                    "turning draconicModules back on must count the same modules again with no reload");
        } finally {
            // Back to where an install with no Draconic Evolution has it, per DraconicModules#install.
            DraconicModules.install(null);
        }
        helper.succeed();
    }

    /**
     * Overlays off: the mining-level line the Jade and WTHIT providers draw is absent, and present
     * again the moment the toggle returns. The line's contents are {@code MiningLevelGameTests}'
     * subject; this is only about the toggle.
     */
    @GameTest(template = "empty")
    public static void overlaysOffDrawNoForgeweaveLine(GameTestHelper helper) {
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);

        helper.assertTrue(MiningLevel.line(Blocks.IRON_ORE.defaultBlockState(), pickaxe) != null,
                "an ore carries a mining-level line while overlays is on, or this test proves nothing");

        ForgeweaveConfig.OVERLAYS.set(false);
        try {
            helper.assertTrue(MiningLevel.line(Blocks.IRON_ORE.defaultBlockState(), pickaxe) == null,
                    "no overlay line may be produced while overlays is off");
        } finally {
            ForgeweaveConfig.OVERLAYS.set(true);
        }

        helper.assertTrue(MiningLevel.line(Blocks.IRON_ORE.defaultBlockState(), pickaxe) != null,
                "turning overlays back on must restore the line with no reload");
        helper.succeed();
    }

    /**
     * The KubeJS binding off: a script-registered trait id resolves to nothing, so a material naming
     * it behaves as if no source implemented the id. Built-in traits are unaffected either way, which
     * is what proves this gates the script source rather than the trait system.
     */
    @GameTest(template = "empty")
    public static void kubejsTraitsOffResolveNoScriptedTrait(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("forgeweave_gametest", "scripted_probe");
        ForgeweaveTraits.registerScripted(id, new Trait() { });

        helper.assertTrue(ForgeweaveTraits.lookup(id) != null,
                "a scripted trait resolves while kubejsTraits is on, or this test proves nothing");

        ForgeweaveConfig.KUBEJS_TRAITS.set(false);
        try {
            helper.assertTrue(ForgeweaveTraits.lookup(id) == null,
                    "no scripted trait may resolve while kubejsTraits is off");
            helper.assertTrue(ForgeweaveTraits.lookup(
                            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "established")) != null,
                    "a built-in trait is not a script trait and must be unaffected");
        } finally {
            ForgeweaveConfig.KUBEJS_TRAITS.set(true);
        }

        helper.assertTrue(ForgeweaveTraits.lookup(id) != null,
                "turning kubejsTraits back on must resolve the same trait again with no reload");
        helper.succeed();
    }

    /**
     * Create's goggles off: a helmet carrying the goggles modifier stops counting as goggles, so
     * Create's own overlays ignore it -- and the modifier stays on the helmet through the flip, so
     * the overlays fire for it again when the toggle returns.
     */
    @GameTest(template = "empty")
    public static void createGogglesOffStopCountingAsGoggles(GameTestHelper helper) {
        ItemStack helmet = new ItemStack(ForgeweaveItems.ARMOR_HELMET.get());
        helmet.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(ForgeweaveModifiers.GOGGLES_ID, 1)));
        List<ModifierEntry> before = helmet.get(ForgeweaveDataComponents.MODIFIERS.get());

        helper.assertTrue(CreateGoggles.isWearingGoggles(helmet),
                "a goggled helmet counts while createGoggles is on, or this test proves nothing");

        ForgeweaveConfig.CREATE_GOGGLES.set(false);
        try {
            helper.assertFalse(CreateGoggles.isWearingGoggles(helmet),
                    "no helmet may count as goggles while createGoggles is off");
            helper.assertTrue(before.equals(helmet.get(ForgeweaveDataComponents.MODIFIERS.get())),
                    "and the modifier must stay on the helmet, got "
                            + helmet.get(ForgeweaveDataComponents.MODIFIERS.get()));
        } finally {
            ForgeweaveConfig.CREATE_GOGGLES.set(true);
        }

        helper.assertTrue(CreateGoggles.isWearingGoggles(helmet),
                "turning createGoggles back on must count the same helmet again with no reload");
        helper.succeed();
    }

    /**
     * An assembled pickaxe wearing the chaotic Draconic core's trait markers. Built by setting the
     * component rather than by assembling from the {@code chaotic} material, because every Draconic
     * material's JSON is {@code mod_loaded}-gated and so is absent from this run -- and the markers
     * are what {@code ForgeweaveDraconicCompat#evolvedLevel} and {@code isCoreTool} read anyway,
     * since a tool's parts are gone by the time it sits in a crafting core.
     */
    private static ItemStack coreTool(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tool = ToolAssembly.pickaxe(helper, player, POS, "iron", "iron", "wood");
        tool.set(ForgeweaveDataComponents.TRAITS.get(), CHAOTIC_CORE);
        return tool;
    }
}
