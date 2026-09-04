package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.draconic.modules.DraconicModules;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.EnergyBuffer;

/**
 * Issue #956 phase 2, the Forgeweave side: that each hook actually reaches
 * {@link DraconicModules}'s bridge and applies what it answers -- the dig-speed multiplier, the added
 * attack damage, the per-block energy, the widened sweep -- and that all four are inert with no
 * bridge installed, which is every Forgeweave install without Draconic Evolution.
 *
 * <p>The bridge here is a fake, not the real {@code DraconicModuleHost}. Draconic Evolution is
 * compileOnly and never enters a run classpath (build.gradle), so no GameTest server can hold a real
 * module host; what a real one <em>answers</em> is
 * {@code compat.draconic.modules.DraconicModuleEffectsTest}, which does have that mod on the test
 * classpath. The split is deliberate: that class covers Draconic Evolution's arithmetic, this one
 * covers Forgeweave's plumbing.
 *
 * <p>Tools are built by hand, same call {@link EnergyTraitGameTests} makes and for the same reason.
 * None carries {@code energized}: that trait spends the buffer on durability too, and a test about
 * what a module spends should have one thing draining the buffer.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class DraconicModuleEffectGameTests {

    /** The block every sweep test breaks; see {@code ExpanderGameTests.ORIGIN} for the X choice. */
    private static final BlockPos ORIGIN = new BlockPos(2, 3, 3);

    /** What the fake bridge's mining module charges per block. */
    private static final int ENERGY_PER_BLOCK = 100;

    @GameTest(template = "empty")
    public static void aSpeedModuleMultipliesTheToolsOwnDigSpeed(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe();
        ToolItem tool = (ToolItem) pickaxe.getItem();
        BlockState stone = Blocks.STONE.defaultBlockState();
        float own = tool.getDestroySpeed(pickaxe, stone);
        helper.assertTrue(own > 0.0F, "setup: the tool must dig stone at all, got " + own);

        withBridge(new DraconicModules.Bridge() {
            @Override
            public int installedModules(ItemStack stack) {
                return 1;
            }

            @Override
            public int moduleEnergyCapacity(ItemStack stack) {
                return 0;
            }

            @Override
            public float digSpeedMultiplier(ItemStack stack) {
                return 2.0F;
            }
        }, () -> {
            float boosted = tool.getDestroySpeed(pickaxe, stone);
            helper.assertTrue(boosted == own * 2.0F,
                    "a x2 speed module must double the tool's own " + own + ", got " + boosted);
        });

        helper.assertTrue(tool.getDestroySpeed(pickaxe, stone) == own,
                "and the tool must be back to its own speed once the module is gone");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void aDamageModuleAddsToTheToolsOwnAttackDamage(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe();
        ToolItem tool = (ToolItem) pickaxe.getItem();
        float own = tool.attackDamage(pickaxe);
        helper.assertTrue(own > 0.0F, "setup: the tool must hit for something, got " + own);

        withBridge(new DraconicModules.Bridge() {
            @Override
            public int installedModules(ItemStack stack) {
                return 1;
            }

            @Override
            public int moduleEnergyCapacity(ItemStack stack) {
                return 0;
            }

            @Override
            public float attackDamageBonus(ItemStack stack) {
                return 4.0F;
            }
        }, () -> {
            float boosted = tool.attackDamage(pickaxe);
            helper.assertTrue(boosted == own + 4.0F,
                    "a 4-point damage module must add to the tool's own " + own + ", got " + boosted);
        });

        helper.assertTrue(tool.attackDamage(pickaxe) == own,
                "and the tool must be back to its own damage once the module is gone");
        helper.succeed();
    }

    /** The inline drain: one block broken spends the module's cost out of the shared buffer, once. */
    @GameTest(template = "empty")
    public static void aMiningModuleSpendsTheSharedBufferOncePerBlock(GameTestHelper helper) {
        ServerPlayer player = holdingPickaxe(helper);
        ItemStack pickaxe = player.getMainHandItem();
        pickaxe.set(ForgeweaveDataComponents.ENERGY.get(), 250);
        helper.setBlock(ORIGIN, Blocks.STONE.defaultBlockState());

        withBridge(miningModule(), () -> player.gameMode.destroyBlock(helper.absolutePos(ORIGIN)));

        helper.assertBlockPresent(Blocks.AIR, ORIGIN);
        int left = EnergyBuffer.stored(pickaxe);
        helper.assertTrue(left == 250 - ENERGY_PER_BLOCK,
                "one block must spend exactly " + ENERGY_PER_BLOCK + " FE, leaving 150, got " + left);
        helper.succeed();
    }

    /**
     * The area module through Forgeweave's own sweep rather than a second breaker: a radius of 1 on a
     * one-block pickaxe is a 3x3 face, and each of the nine blocks pays the module's energy.
     */
    @GameTest(template = "empty")
    public static void anAreaModuleWidensTheToolsOwnSweep(GameTestHelper helper) {
        ServerPlayer player = holdingPickaxe(helper);
        ItemStack pickaxe = player.getMainHandItem();
        pickaxe.set(ForgeweaveDataComponents.ENERGY.get(), 10_000);
        fillSlab(helper, Blocks.STONE.defaultBlockState());

        withBridge(miningModule(), () -> player.gameMode.destroyBlock(helper.absolutePos(ORIGIN)));

        int broken = countBrokenSlab(helper);
        helper.assertTrue(broken == 9, "a radius of 1 must make the pickaxe's sweep 3x3, broke " + broken);
        int spent = 10_000 - EnergyBuffer.stored(pickaxe);
        helper.assertTrue(spent == 9 * ENERGY_PER_BLOCK,
                "all nine blocks must pay, expected " + (9 * ENERGY_PER_BLOCK) + " FE spent, got " + spent);
        helper.succeed();
    }

    /**
     * The shipped state of every install without Draconic Evolution: no bridge, so every hook above
     * answers with the tool's own numbers and nothing touches the buffer.
     */
    @GameTest(template = "empty")
    public static void withNoDraconicEvolutionEveryEffectIsInert(GameTestHelper helper) {
        ServerPlayer player = holdingPickaxe(helper);
        ItemStack pickaxe = player.getMainHandItem();
        ToolItem tool = (ToolItem) pickaxe.getItem();
        pickaxe.set(ForgeweaveDataComponents.ENERGY.get(), 250);
        BlockState stone = Blocks.STONE.defaultBlockState();
        float speed = tool.getDestroySpeed(pickaxe, stone);
        float damage = tool.attackDamage(pickaxe);
        fillSlab(helper, stone);

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));

        helper.assertTrue(countBrokenSlab(helper) == 1,
                "a pickaxe with no module must still break exactly one block");
        helper.assertTrue(EnergyBuffer.stored(pickaxe) == 250,
                "and must spend nothing, got " + EnergyBuffer.stored(pickaxe) + " FE left of 250");
        helper.assertTrue(tool.getDestroySpeed(pickaxe, stone) == speed, "its dig speed must be its own");
        helper.assertTrue(tool.attackDamage(pickaxe) == damage, "and its attack damage its own");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** A fake bridge whose mining module is a radius of 1 costing {@link #ENERGY_PER_BLOCK} a block. */
    private static DraconicModules.Bridge miningModule() {
        return new DraconicModules.Bridge() {
            @Override
            public int installedModules(ItemStack stack) {
                return 1;
            }

            @Override
            public int moduleEnergyCapacity(ItemStack stack) {
                return 0;
            }

            @Override
            public int miningAoe(ItemStack stack) {
                return 1;
            }

            @Override
            public int miningEnergyCost(ItemStack stack) {
                return ENERGY_PER_BLOCK;
            }
        };
    }

    /**
     * Runs {@code body} with {@code bridge} installed and takes it back out afterwards. The bridge is
     * one static field for the whole game, so leaving one behind would change every test that ran
     * after this one.
     */
    private static void withBridge(DraconicModules.Bridge bridge, Runnable body) {
        DraconicModules.install(bridge);
        try {
            body.run();
        } finally {
            DraconicModules.install(null);
        }
    }

    /** A survival player holding a hand-built pickaxe; creative never reaches the break paths. */
    private static ServerPlayer holdingPickaxe(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe());
        return player;
    }

    /** See the class javadoc: built by hand, no traits, plenty of durability for a 3x3. */
    private static ItemStack pickaxe() {
        int durability = 1000;
        ToolItem tool = ForgeweaveItems.TOOL_PICKAXE.get();
        ToolStats.Stats stats = new ToolStats.Stats(durability, 6.0F, 2.0F);
        Material head = new Material(
                new Material.Head(durability, 6.0F, 2.0F),
                new Material.Handle(1.0F, 0),
                0,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));

        ItemStack stack = new ItemStack(tool);
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        stack.set(ForgeweaveDataComponents.TRAITS.get(), List.of());
        stack.set(DataComponents.TOOL, tool.toolComponent(head, stats));
        stack.set(DataComponents.MAX_DAMAGE, durability);
        stack.set(DataComponents.DAMAGE, 0);
        return stack;
    }

    /** A 5x5 wall of {@code state} in the X/Y plane at {@link #ORIGIN}'s Z, as ExpanderGameTests does. */
    private static void fillSlab(GameTestHelper helper, BlockState state) {
        forEachSlabPos(pos -> helper.setBlock(pos, state));
    }

    private static int countBrokenSlab(GameTestHelper helper) {
        int[] broken = {0};
        forEachSlabPos(pos -> {
            if (helper.getBlockState(pos).isAir()) {
                broken[0]++;
            }
        });
        return broken[0];
    }

    private static void forEachSlabPos(Consumer<BlockPos> action) {
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                action.accept(ORIGIN.offset(dx, dy, 0));
            }
        }
    }

    private DraconicModuleEffectGameTests() {}
}
