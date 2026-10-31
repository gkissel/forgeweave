package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import dev.gkissel.forgeweave.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import dev.gkissel.forgeweave.gametest.GameTestHolder;
import dev.gkissel.forgeweave.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolLeveling;

/**
 * Issue #110's GameTest coverage: the advancement grants that have a real hook to exercise
 * headlessly (structure forming, a melted item, a modifier application), and the Ponder chat hint's
 * silence now that issue #664 embeds Ponder. {@code first_alloy} is covered in {@link SmelteryAlloyGameTests}
 * instead, next to the alloying pass that fires it (#98).
 *
 * <p>Issue #166's M3-17 tail (forge -> large tool -> emboss -> combat modifier) adds a structural
 * check that all four exist and parent correctly ({@link
 * #theM317ChainAdvancementsExistAndParentCorrectly}) plus real-hook coverage for the three that have
 * one -- "forge" has none, the same "own the item" idiom the root's own {@code smeltery_root} step
 * already uses with no GameTest of its own.
 *
 * <p>A real {@link ServerPlayer} (not the plain mock {@code Player} most other GameTests use) is
 * required throughout: {@code PlayerAdvancements} only tracks progress for a player registered with
 * the server, which {@link GameTestHelper#makeMockServerPlayerInLevel()} provides.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class AdvancementGameTests {

    /**
     * #1106: the ten M2/M3 ids the re-rooted tree keeps, so a world that already earned one does not
     * lose it. Re-parenting is invisible to {@code PlayerAdvancements}, which keys progress by id.
     */
    private static final List<String> M2_M3_IDS = List.of(
            "smeltery/root", "smeltery/build_smeltery", "smeltery/first_melt", "smeltery/first_cast",
            "smeltery/first_alloy", "smeltery/first_modifier", "smeltery/forge", "smeltery/large_tool",
            "smeltery/emboss", "smeltery/combat_modifier");

    /** The ten above, the new root, and the eighteen branch steps #1106 adds. */
    private static final int EXPECTED_TREE_SIZE = 29;

    /** Comfortably past the first level-up's cost at any {@code baseXp} a Forgeweave tool carries. */
    private static final int LEVEL_UP_XP = 100_000;

    @GameTest(template = "smeltery")
    public static void formingTheStructureGrantsBuildSmelteryAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        helper.useBlock(SmelteryGameTests.CORE_POS, player);

        helper.assertTrue(isGranted(helper, player, "smeltery/build_smeltery"),
                "expected forming the smeltery through a real player interaction to grant the advancement");
        helper.succeed();
    }

    /** {@link SmelteryControllerBlockEntity#insertForMelting(ItemStack, ServerPlayer)}'s own chosen "first melt" moment: a player-attributed insert into a formed, hot smeltery. */
    @GameTest(template = "smeltery")
    public static void insertingAMeltableItemIntoAHotSmelteryGrantsFirstMeltAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SearedTankBlockEntity tank = helper.getBlockEntity(SmelteryGameTests.TANK_POS, SearedTankBlockEntity.class);
        tank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos, SmelteryControllerBlockEntity.class);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());

        ItemStack remaining = core.insertForMelting(new ItemStack(Items.IRON_ORE), player);

        helper.assertTrue(remaining.isEmpty(), "expected the iron ore to go into the hot smeltery");
        helper.assertTrue(isGranted(helper, player, "smeltery/first_melt"),
                "expected a player-attributed insert into a hot smeltery to grant the advancement");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void applyingAModifierGrantsFirstModifierAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos, ToolStationBlockEntity.class);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.REDSTONE, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(!output.isEmpty(), "expected one redstone to apply haste before checking the advancement");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);

        helper.assertTrue(isGranted(helper, player, "smeltery/first_modifier"),
                "expected taking a modifier application's output to grant the advancement");
        helper.succeed();
    }

    // ---------------------------------------------------------------- T49 (parity audit 2026-08-18)

    /** Iron crosses both upstream thresholds (harvestLevel {@code DIAMOND=2}, {@code >1}). */
    @GameTest(template = "empty")
    public static void assemblingAnIronPickaxeGrantsBothVanillaStoryAdvancements(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "iron", "iron", "iron");

        helper.assertTrue(pickaxe.is(ForgeweaveItems.TOOL_PICKAXE.get()), "expected an assembled pickaxe, got " + pickaxe);
        helper.assertTrue(isVanillaGranted(helper, player, "story/upgrade_tools"),
                "expected assembling an iron-head pickaxe to grant the vanilla upgrade_tools advancement");
        helper.assertTrue(isVanillaGranted(helper, player, "story/iron_tools"),
                "expected assembling an iron-head pickaxe to grant the vanilla iron_tools advancement");
        helper.succeed();
    }

    /** Stone crosses only the first upstream threshold (harvestLevel {@code IRON=1}, {@code >0} but not {@code >1}). */
    @GameTest(template = "empty")
    public static void assemblingAStonePickaxeGrantsOnlyUpgradeToolsAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ToolAssembly.pickaxe(helper, player, pos, "stone", "stone", "stone");

        helper.assertTrue(isVanillaGranted(helper, player, "story/upgrade_tools"),
                "expected assembling a stone-head pickaxe to grant the vanilla upgrade_tools advancement");
        helper.assertFalse(isVanillaGranted(helper, player, "story/iron_tools"),
                "expected a stone-head pickaxe to not yet grant the vanilla iron_tools advancement");
        helper.succeed();
    }

    /** Wood sits below upstream's own floor (every upstream material is {@code harvestLevel >= STONE = 0}) and grants neither. */
    @GameTest(template = "empty")
    public static void assemblingAWoodPickaxeGrantsNeitherVanillaStoryAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ToolAssembly.pickaxe(helper, player, pos, "wood", "wood", "wood");

        helper.assertFalse(isVanillaGranted(helper, player, "story/upgrade_tools"),
                "expected a wood-head pickaxe to not grant the vanilla upgrade_tools advancement");
        helper.assertFalse(isVanillaGranted(helper, player, "story/iron_tools"),
                "expected a wood-head pickaxe to not grant the vanilla iron_tools advancement");
        helper.succeed();
    }

    /** Upstream's {@code instanceof Pickaxe} also matches its one subclass, {@code Hammer}. */
    @GameTest(template = "empty")
    public static void assemblingAnIronHammerAtTheForgeGrantsBothVanillaStoryAdvancements(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ToolAssemblyRecipes.Entry hammer = ToolAssembly.entryFor(ForgeweaveItems.TOOL_HAMMER.get());

        ItemStack assembled = ToolAssembly.assembleAtForge(helper, player, pos, hammer,
                Collections.nCopies(hammer.slotCount(), "iron"));

        helper.assertTrue(assembled.is(ForgeweaveItems.TOOL_HAMMER.get()),
                "expected the Tool Forge to build the hammer under test, got " + assembled);
        helper.assertTrue(isVanillaGranted(helper, player, "story/upgrade_tools"),
                "expected assembling an iron-head hammer to grant the vanilla upgrade_tools advancement");
        helper.assertTrue(isVanillaGranted(helper, player, "story/iron_tools"),
                "expected assembling an iron-head hammer to grant the vanilla iron_tools advancement");
        helper.succeed();
    }

    /**
     * Upstream's other HARVEST-category tools (Shovel/Excavator, Hatchet/LumberAxe, Kama/Scythe)
     * extend {@code AoeToolCore}, not {@code Pickaxe}, so {@code AchievementEvents} never grants
     * these for them regardless of head tier -- a broadsword (MELEE) makes the same point without a
     * second Tool Forge assembly.
     */
    @GameTest(template = "empty")
    public static void assemblingAnIronBroadswordGrantsNeitherVanillaStoryAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ToolAssemblyRecipes.Entry broadsword = ToolAssembly.entryFor(ForgeweaveItems.TOOL_BROADSWORD.get());

        ItemStack assembled = ToolAssembly.assemble(helper, player, pos, broadsword,
                Collections.nCopies(broadsword.slotCount(), "iron"));

        helper.assertTrue(assembled.is(ForgeweaveItems.TOOL_BROADSWORD.get()),
                "expected the Tool Station to build the broadsword under test, got " + assembled);
        helper.assertFalse(isVanillaGranted(helper, player, "story/upgrade_tools"),
                "expected a non-pickaxe tool to never grant the vanilla upgrade_tools advancement");
        helper.assertFalse(isVanillaGranted(helper, player, "story/iron_tools"),
                "expected a non-pickaxe tool to never grant the vanilla iron_tools advancement");
        helper.succeed();
    }

    /**
     * #166's four M3-17 steps still exist and still chain, at the parents #1106 gave them: the forge
     * line hangs off the first cast (where its seared bricks come from) rather than off the first
     * modifier, and the combat modifier sits next to the other modifier ends rather than after
     * embossing.
     */
    @GameTest(template = "empty")
    public static void theM317ChainAdvancementsExistAndParentCorrectly(GameTestHelper helper) {
        assertParent(helper, "smeltery/forge", "smeltery/first_cast");
        assertParent(helper, "smeltery/large_tool", "smeltery/forge");
        assertParent(helper, "smeltery/emboss", "smeltery/large_tool");
        assertParent(helper, "smeltery/combat_modifier", "smeltery/first_modifier");
        helper.succeed();
    }

    /**
     * #1106: every M2/M3 id survived the re-rooting (a world that earned one keeps it), the tree has
     * exactly one root, and that root is the guide book.
     */
    @GameTest(template = "empty")
    public static void theTreeHasOneRootAndNoOrphans(GameTestHelper helper) {
        List<AdvancementHolder> tree = progressionAdvancements(helper);
        helper.assertTrue(tree.size() >= EXPECTED_TREE_SIZE,
                "expected at least " + EXPECTED_TREE_SIZE + " advancements in the tree, got " + tree.size());

        List<Identifier> roots = tree.stream()
                .filter(holder -> holder.value().parent().isEmpty())
                .map(AdvancementHolder::id)
                .toList();
        helper.assertTrue(roots.equals(List.of(Identifier.fromNamespaceAndPath(Forgeweave.MODID, "root"))),
                "expected the guide book to be the tree's only root, got " + roots);

        for (AdvancementHolder holder : tree) {
            holder.value().parent().ifPresent(parent -> helper.assertTrue(
                    helper.getLevel().getServer().getAdvancements().get(parent) != null,
                    holder.id() + " is an orphan: its parent " + parent + " does not exist"));
        }

        for (String id : M2_M3_IDS) {
            helper.assertTrue(helper.getLevel().getServer().getAdvancements()
                            .get(Identifier.fromNamespaceAndPath(Forgeweave.MODID, id)) != null,
                    "the M2/M3 id " + id + " must survive the re-rooting so earned progress is kept");
        }
        helper.succeed();
    }

    /** #1106's "first tool": {@link ToolAssembly#pickaxe} takes the output, which is what grants it. */
    @GameTest(template = "empty")
    public static void assemblingAToolGrantsFirstToolAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        helper.assertTrue(isGranted(helper, player, "tools/first_tool"),
                "expected assembling a tool at the Tool Station to grant the advancement");
        helper.succeed();
    }

    /** #1106's "repair": a fully damaged iron pickaxe plus one iron ingot, upstream's own repair shape. */
    @GameTest(template = "empty")
    public static void repairingAToolGrantsRepairAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "iron", "wood", "wood");
        pickaxe.setDamageValue(pickaxe.getMaxDamage() / 2);

        take(helper, player, pos, pickaxe, new ItemStack(Items.IRON_INGOT));

        helper.assertTrue(isGranted(helper, player, "tools/repair"),
                "expected repairing a tool with its own material to grant the advancement");
        helper.succeed();
    }

    /** #1106's "part exchange": an iron head over a stone one, {@link PartExchangeGameTests}' own swap. */
    @GameTest(template = "empty")
    public static void exchangingAPartGrantsPartExchangeAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        take(helper, player, pos, pickaxe, ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "iron"));

        helper.assertTrue(isGranted(helper, player, "tools/part_exchange"),
                "expected swapping a tool part to grant the advancement");
        helper.succeed();
    }

    /**
     * #1106's "every slot spent": enough redstone to push haste into the tool's last slot. Haste
     * charges a slot every {@code unitsPerLevel} units, so the units that occupy exactly the tool's
     * free slot count are {@code 1 + (free - 1) * unitsPerLevel} -- read off the tool rather than
     * written out, so a slot-granting trait on the test material cannot turn it into a no-op.
     */
    @GameTest(template = "empty")
    public static void fillingEveryModifierSlotGrantsSlotsFilledAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        int free = ForgeweaveModifiers.freeSlots(pickaxe);
        helper.assertTrue(free > 0, "the test needs a tool with at least one free slot, got " + free);

        ItemStack hasted = take(helper, player, pos, pickaxe,
                redstone(1 + (free - 1) * ForgeweaveModifiers.HASTE.unitsPerLevel()));

        helper.assertTrue(ForgeweaveModifiers.freeSlots(hasted) <= 0,
                "the test needs an application that spends every slot, got "
                        + ForgeweaveModifiers.freeSlots(hasted) + " free");
        helper.assertTrue(isGranted(helper, player, "modifiers/slots_filled"),
                "expected spending a tool's last modifier slot to grant the advancement");
        helper.succeed();
    }

    /** {@code units} redstone, split into stack-sized piles for as many station slots as it takes. */
    private static ItemStack[] redstone(int units) {
        List<ItemStack> stacks = new ArrayList<>();
        for (int left = units; left > 0; left -= Items.REDSTONE.getDefaultMaxStackSize()) {
            stacks.add(new ItemStack(Items.REDSTONE, Math.min(Items.REDSTONE.getDefaultMaxStackSize(), left)));
        }
        return stacks.toArray(ItemStack[]::new);
    }

    /** #1106's "first level": {@code ToolLeveling#addXp} is the one seam every XP grant lands on. */
    @GameTest(template = "empty")
    public static void levelingAToolGrantsFirstLevelAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        boolean leveled = ToolLeveling.addXp(pickaxe, LEVEL_UP_XP, player);

        helper.assertTrue(leveled, "the test needs enough XP to cross a level");
        helper.assertTrue(isGranted(helper, player, "leveling/first_level"),
                "expected a tool level-up to grant the advancement");
        helper.assertFalse(isGranted(helper, player, "armor/leveled"),
                "a tool is not an armor piece, so the armor branch must stay unearned");
        helper.succeed();
    }

    /** #1106's "armor level": armor levels through the same seam, so the stack is what tells them apart. */
    @GameTest(template = "empty")
    public static void levelingAnArmorPieceGrantsArmorLevelAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack helmet = ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryOf(ToolConstants.HELMET), List.of("iron", "iron"));
        helper.assertTrue(helmet.is(ForgeweaveItems.ARMOR_HELMET.get()),
                "expected the station to build the helmet under test, got " + helmet);

        boolean leveled = ToolLeveling.addXp(helmet, LEVEL_UP_XP, player);

        helper.assertTrue(leveled, "the test needs enough XP to cross a level");
        helper.assertTrue(isGranted(helper, player, "armor/leveled"),
                "expected an armor piece level-up to grant the advancement");
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 104: the pattern, Part Builder and Tool Station steps unlock
     * the moment their item reaches the inventory, and in that order. The shipped tree chains
     * {@code root -> tools/pattern -> tools/part_builder -> tools/tool_station -> tools/first_tool},
     * so this checks the parents first, then hands the three items over one at a time and expects
     * each pickup to light exactly its own step and nothing further down the chain. Assembling the
     * pickaxe then lights first tool, the end of the same chain.
     */
    @GameTest(template = "empty")
    public static void theWorkshopStepsUnlockOnPickupInChainOrder(GameTestHelper helper) {
        assertParent(helper, "tools/pattern", "root");
        assertParent(helper, "tools/part_builder", "tools/pattern");
        assertParent(helper, "tools/tool_station", "tools/part_builder");
        assertParent(helper, "tools/first_tool", "tools/tool_station");
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<String> steps = List.of("tools/pattern", "tools/part_builder", "tools/tool_station", "tools/first_tool");
        List<ItemStack> pickups = List.of(new ItemStack(ForgeweaveItems.PATTERN_BLANK.get()),
                new ItemStack(ForgeweaveItems.PART_BUILDER.get()), new ItemStack(ForgeweaveItems.TOOL_STATION.get()));

        for (int i = 0; i < pickups.size(); i++) {
            give(player, pickups.get(i));
            for (int j = 0; j < steps.size(); j++) {
                helper.assertTrue(isGranted(helper, player, steps.get(j)) == (j <= i),
                        "after picking up " + pickups.get(i) + ", expected " + steps.get(j)
                                + (j <= i ? " to be granted" : " to still be locked"));
            }
        }
        ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "wood", "wood", "wood");
        helper.assertTrue(isGranted(helper, player, "tools/first_tool"),
                "expected the first assembled tool to grant the last step of the chain");
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 105, the mixed set: {@code armor/full_set} has one criterion
     * per slot and each criterion accepts the light or the heavy piece, so a light helmet and
     * leggings with a heavy chestplate and boots must count as a full set. Three pieces must not.
     */
    @GameTest(template = "empty")
    public static void aMixedLightAndHeavySetGrantsFullSetAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        give(player, new ItemStack(ForgeweaveItems.ARMOR_HELMET.get()));
        give(player, new ItemStack(ForgeweaveItems.ARMOR_HEAVY_CHESTPLATE.get()));
        give(player, new ItemStack(ForgeweaveItems.ARMOR_LEGGINGS.get()));
        helper.assertFalse(isGranted(helper, player, "armor/full_set"),
                "three pieces are not a full set, so the advancement must wait for the boots");

        give(player, new ItemStack(ForgeweaveItems.ARMOR_HEAVY_BOOTS.get()));

        helper.assertTrue(isGranted(helper, player, "armor/full_set"),
                "expected two light and two heavy pieces, one per slot, to grant the full set advancement");
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 105, the two level-ups: leveling a tool and leveling an armor
     * piece unlock two advancements on different branches. {@code leveling/first_level} hangs off
     * the first modifier and {@code armor/leveled} off the full set, so the parents differ and
     * neither advancement sits above the other, which means earning one never implies the other.
     */
    @GameTest(template = "empty")
    public static void theToolAndArmorLevelAdvancementsSitOnDifferentBranches(GameTestHelper helper) {
        assertParent(helper, "leveling/first_level", "smeltery/first_modifier");
        assertParent(helper, "armor/leveled", "armor/full_set");
        helper.assertFalse(ancestors(helper, "armor/leveled").contains(id("leveling/first_level")),
                "the armor level advancement must not sit under the tool level one");
        helper.assertFalse(ancestors(helper, "leveling/first_level").contains(id("armor/leveled")),
                "the tool level advancement must not sit under the armor level one");
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 106, first half: a gold cast, a Nether core and a blazing
     * blood bucket each give their own advancement ({@code smeltery/cast}, {@code
     * smeltery/core_tier}, {@code smeltery/hotter_fuel}). All three are inventory criteria, so
     * picking the item up is the whole trigger, and each pickup must light only its own.
     */
    @GameTest(template = "empty")
    public static void goldCastNetherCoreAndBlazingBloodEachGrantTheirOwnAdvancement(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        give(player, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        helper.assertTrue(isGranted(helper, player, "smeltery/cast"), "expected a gold ingot cast to grant smeltery/cast");
        helper.assertFalse(isGranted(helper, player, "smeltery/core_tier"), "a cast is not a hotter core");

        give(player, new ItemStack(ForgeweaveItems.NETHER_CORE.get()));
        helper.assertTrue(isGranted(helper, player, "smeltery/core_tier"),
                "expected a Nether core to grant smeltery/core_tier");
        helper.assertFalse(isGranted(helper, player, "smeltery/hotter_fuel"), "a core is not a hotter fuel");

        give(player, new ItemStack(ForgeweaveFluids.BLAZING_BLOOD.bucket().get()));
        helper.assertTrue(isGranted(helper, player, "smeltery/hotter_fuel"),
                "expected a blazing blood bucket to grant smeltery/hotter_fuel");
        helper.succeed();
    }

    /**
     * Playtest checklist 1.0.0, line 106, second half: the alloy advancements count the cast
     * ingot, not the fluid. Tideiron is two steps deep (cobalt plus ironbrand, and ironbrand is an
     * alloy itself), so its ingot lands in {@code forgeweave:alloys/deep} only; sunsteel is three
     * steps deep and lands in {@code alloys/deepest} too. Holding each one's molten bucket first
     * must grant nothing, because both criteria read the ingot tags.
     */
    @GameTest(template = "empty")
    public static void alloyAdvancementsCountTheIngotNotTheMoltenBucket(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        give(player, new ItemStack(item("molten_tideiron_bucket")));
        give(player, new ItemStack(item("molten_sunsteel_bucket")));
        helper.assertFalse(isGranted(helper, player, "alloys/deep"),
                "a molten tideiron or sunsteel bucket alone must not grant alloys/deep");
        helper.assertFalse(isGranted(helper, player, "alloys/deepest"),
                "a molten sunsteel bucket alone must not grant alloys/deepest");

        give(player, new ItemStack(item("tideiron_ingot")));
        helper.assertTrue(isGranted(helper, player, "alloys/deep"), "expected a tideiron ingot to grant alloys/deep");
        helper.assertFalse(isGranted(helper, player, "alloys/deepest"),
                "tideiron is two steps deep, so its ingot must not grant alloys/deepest");

        give(player, new ItemStack(item("sunsteel_ingot")));
        helper.assertTrue(isGranted(helper, player, "alloys/deepest"),
                "expected a sunsteel ingot, three steps deep, to grant alloys/deepest");
        helper.succeed();
    }

    /** Puts {@code stack} in the player's inventory and lets the inventory menu report it, the way a pickup does. */
    private static void give(ServerPlayer player, ItemStack stack) {
        player.getInventory().add(stack);
        player.inventoryMenu.broadcastChanges();
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getOptional(id(path))
                .orElseThrow(() -> new AssertionError("no item forgeweave:" + path));
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /** Every advancement above {@code path} in the tree, nearest first. */
    private static List<Identifier> ancestors(GameTestHelper helper, String path) {
        List<Identifier> chain = new ArrayList<>();
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements().get(id(path));
        while (holder != null && holder.value().parent().isPresent()) {
            Identifier parent = holder.value().parent().get();
            chain.add(parent);
            holder = helper.getLevel().getServer().getAdvancements().get(parent);
        }
        return chain;
    }

    /** Loads the station with a tool and its free-slot inputs and takes whatever it resolves. */
    private static ItemStack take(GameTestHelper helper, ServerPlayer player, BlockPos pos, ItemStack tool,
            ItemStack... inputs) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos, ToolStationBlockEntity.class);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, tool);
        for (int i = 0; i < inputs.length; i++) {
            blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT + 1 + i, inputs[i]);
        }
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "expected the station to produce an output"
                + (menu.rejection() == null ? "" : "; it says: " + menu.rejection().message().getString()));
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    /** Every Forgeweave advancement that is part of the tree, i.e. not a vanilla recipe unlock. */
    private static List<AdvancementHolder> progressionAdvancements(GameTestHelper helper) {
        return helper.getLevel().getServer().getAdvancements().getAllAdvancements().stream()
                .filter(holder -> holder.id().getNamespace().equals(Forgeweave.MODID))
                .filter(holder -> !holder.id().getPath().startsWith("recipes/"))
                .toList();
    }

    /** #166's "large tool": {@link ToolAssembly#assembleAtForge} already calls {@code onTake} for us. */
    @GameTest(template = "empty")
    public static void assemblingALargeToolAtTheForgeGrantsLargeToolAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ToolAssemblyRecipes.Entry hammer = ToolAssembly.entryFor(ForgeweaveItems.TOOL_HAMMER.get());

        ItemStack assembled = ToolAssembly.assembleAtForge(helper, player, pos, hammer,
                Collections.nCopies(hammer.slotCount(), "stone"));

        helper.assertTrue(assembled.is(ForgeweaveItems.TOOL_HAMMER.get()),
                "expected the Tool Forge to build the hammer under test, got " + assembled);
        helper.assertTrue(isGranted(helper, player, "smeltery/large_tool"),
                "expected assembling a large tool at the Tool Forge to grant the advancement");
        helper.succeed();
    }

    /** #166's "emboss", same reagent set {@link EmbossingGameTests} exercises (iron head, three slime crystals, gold block -- #248's parity cost). */
    @GameTest(template = "empty")
    public static void embossingAToolGrantsFirstEmbossmentAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos, ToolStationBlockEntity.class);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, pickaxe);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT,
                ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "iron"));
        blockEntity.container().setItem(ToolStationMenu.HANDLE_SLOT,
                new ItemStack(ForgeweaveItems.GREEN_SLIME_CRYSTAL.get()));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_1,
                new ItemStack(ForgeweaveItems.BLUE_SLIME_CRYSTAL.get()));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_2,
                new ItemStack(ForgeweaveItems.MAGMA_SLIME_CRYSTAL.get()));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_3, new ItemStack(Items.GOLD_BLOCK));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertTrue(!output.isEmpty(),
                "expected the station to produce an embossed tool before checking the advancement");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);

        helper.assertTrue(isGranted(helper, player, "smeltery/emboss"),
                "expected taking an embossed tool's output to grant the advancement");
        helper.succeed();
    }

    /** #166's "combat modifier": knockback (a piston), one of the eight {@code isCombatModifier} names. */
    @GameTest(template = "empty")
    public static void applyingAKnockbackModifierGrantsCombatModifierAdvancement(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos, ToolStationBlockEntity.class);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.PISTON, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(!output.isEmpty(), "expected one piston to apply knockback before checking the advancement");

        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);

        helper.assertTrue(isGranted(helper, player, "smeltery/first_modifier"),
                "expected taking a combat modifier's output to also grant first_modifier");
        helper.assertTrue(isGranted(helper, player, "smeltery/combat_modifier"),
                "expected taking a combat modifier application's output to grant the advancement");
        helper.succeed();
    }

    private static boolean isGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(Forgeweave.MODID, path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** As {@link #isGranted}, for a vanilla advancement (T49's {@code story/upgrade_tools}/{@code story/iron_tools}). */
    private static boolean isVanillaGranted(GameTestHelper helper, ServerPlayer player, String path) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(Identifier.withDefaultNamespace(path));
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    /** Asserts {@code path} exists and its declared parent is exactly {@code expectedParentPath}. */
    private static void assertParent(GameTestHelper helper, String path, String expectedParentPath) {
        AdvancementHolder holder = helper.getLevel().getServer().getAdvancements()
                .get(Identifier.fromNamespaceAndPath(Forgeweave.MODID, path));
        helper.assertTrue(holder != null, "expected advancement " + path + " to exist");
        Identifier expectedParent = Identifier.fromNamespaceAndPath(Forgeweave.MODID, expectedParentPath);
        helper.assertTrue(holder.value().parent().map(expectedParent::equals).orElse(false),
                "expected " + path + "'s parent to be " + expectedParentPath + ", got " + holder.value().parent());
    }

    private AdvancementGameTests() {}
}
