package dev.gkissel.forgeweave.gametest;

import java.util.Collections;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.AoeHarvest;

/**
 * docs/SCOPE.md M3 issue #157's verification: the five large harvest tools' area behaviors and their
 * combat riders, each tested through the same path a player takes -- a tool assembled at a real Tool
 * Forge, held in a real {@link ServerPlayer}'s main hand, breaking a real block through
 * {@code ServerPlayerGameMode#destroyBlock}. Nothing here calls {@link AoeHarvest} directly except
 * the two tests that are about its arithmetic rather than its effect.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class LargeToolGameTests {

    private static final BlockPos STATION = new BlockPos(0, 1, 0);
    /** Clear of the station, so the block scenes never overlap the Tool Forge that built the tool. */
    private static final BlockPos ORIGIN = new BlockPos(3, 2, 3);

    // ------------------------------------------------------------------ 3x3 mining

    /**
     * The hammer breaks its 3x3 and nothing else -- nine blocks, counted as the stone that is gone,
     * with a tenth block one step outside the plane left standing to prove the shape has an edge.
     */
    @GameTest(template = "empty")
    public static void hammerBreaksExactlyNineBlocks(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_HAMMER.get(), "stone");
        fill(helper, ORIGIN, 1, Blocks.STONE.defaultBlockState());
        BlockPos outside = ORIGIN.offset(0, 0, 2);
        helper.setBlock(outside, Blocks.STONE);

        int broken = breakAndCount(helper, player, ORIGIN, 1);

        helper.assertTrue(broken == 9, "a hammer must break exactly its 3x3, broke " + broken);
        helper.assertBlockPresent(Blocks.STONE, outside);
        helper.succeed();
    }

    /**
     * The 3x3 honors tool tier: a stone-headed hammer takes the stone it is swung at and leaves the
     * obsidian beside it, because the head material's {@code incorrect_for_tool} tag denies drops on
     * it -- which is the one check {@link AoeHarvest} makes ({@code ItemStack#isCorrectToolForDrops}).
     */
    @GameTest(template = "empty")
    public static void aoeHonorsToolTier(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_HAMMER.get(), "stone");
        fill(helper, ORIGIN, 1, Blocks.STONE.defaultBlockState());
        BlockPos obsidian = ORIGIN.offset(1, 0, 0);
        helper.setBlock(obsidian, Blocks.OBSIDIAN);

        breakAndCount(helper, player, ORIGIN, 1);

        helper.assertBlockPresent(Blocks.OBSIDIAN, obsidian);
        helper.succeed();
    }

    /**
     * Durability cost per extra block, upstream's {@code ToolCore#onBlockDestroyed} reached through
     * {@code breakExtraBlock}: 1 for a block the tool is meant for, 2 for anything else. Nine blocks
     * of stone in a pickaxe-tier tool's plane therefore cost exactly 9 -- the same per-block price
     * the first block pays, which is what makes area mining expensive rather than free.
     */
    @GameTest(template = "empty")
    public static void eachExtraBlockCostsOneDurability(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_HAMMER.get(), "stone");
        ItemStack hammer = player.getMainHandItem();
        fill(helper, ORIGIN, 1, Blocks.STONE.defaultBlockState());

        helper.assertTrue(hammer.getDamageValue() == 0, "the test needs an undamaged hammer");
        int broken = breakAndCount(helper, player, ORIGIN, 1);

        helper.assertTrue(broken == 9, "expected the full 3x3, broke " + broken);
        helper.assertTrue(hammer.getDamageValue() == 9,
                "expected 1 durability per block of the 3x3, got " + hammer.getDamageValue());
        helper.succeed();
    }

    /** The excavator digs the same 3x3, in its own {@code mineable/shovel} material. */
    @GameTest(template = "empty")
    public static void excavatorBreaksExactlyNineBlocks(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_EXCAVATOR.get(), "stone");
        fill(helper, ORIGIN, 1, Blocks.DIRT.defaultBlockState());

        int broken = breakAndCount(helper, player, ORIGIN, 1);

        helper.assertTrue(broken == 9, "an excavator must break exactly its 3x3, broke " + broken);
        helper.succeed();
    }

    // ------------------------------------------------------------------ tree felling

    /**
     * The lumber axe takes the whole trunk and stops at the first non-log: a six-log trunk under a
     * leaf canopy goes, and the log two blocks above it with air in between stays.
     */
    @GameTest(template = "empty")
    public static void lumberAxeFellsTheTrunkAndStopsAtNonLog(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_LUMBERAXE.get(), "stone");
        // Canopy first, trunk second, so the trunk's own column is logs rather than leaves.
        fill(helper, ORIGIN.offset(0, 6, 0), 1, Blocks.OAK_LEAVES.defaultBlockState());
        for (int y = 0; y < 6; y++) {
            helper.setBlock(ORIGIN.offset(0, y, 0), Blocks.OAK_LOG);
        }
        // Three blocks of air away: a separate tree as far as the fell is concerned, and the plain
        // statement of "stops at a non-log" -- the fell reaches the air beside the trunk and halts.
        BlockPos detached = ORIGIN.offset(3, 0, 0);
        helper.setBlock(detached, Blocks.OAK_LOG);

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));

        for (int y = 0; y < 6; y++) {
            helper.assertBlockPresent(Blocks.AIR, ORIGIN.offset(0, y, 0));
        }
        helper.assertBlockPresent(Blocks.OAK_LOG, detached);
        helper.succeed();
    }

    // ------------------------------------------------------------------ vein mining

    /**
     * The vein mine follows connected ore and stops at the maintainer's 64-block cap (2026-08-12).
     * The scene is a 5x5x5 solid block of ore -- 125 connected blocks, comfortably past the cap -- so
     * "stopped at the cap" and "ran out of ore" cannot be confused for each other.
     */
    @GameTest(template = "empty")
    public static void veinMineStopsAtTheBlockCap(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_VEIN_HAMMER.get(), "stone");
        BlockPos center = new BlockPos(3, 3, 3);
        fill(helper, center, 2, Blocks.IRON_ORE.defaultBlockState());

        int broken = breakAndCount(helper, player, center, 2);

        helper.assertTrue(broken == AoeHarvest.VEIN_LIMIT + 1,
                "expected the origin plus the " + AoeHarvest.VEIN_LIMIT + "-block cap, broke " + broken);
        helper.succeed();
    }

    /** A vein is only the connected run: ore with a block of stone between it is a different vein. */
    @GameTest(template = "empty")
    public static void veinMineTakesOnlyTheConnectedVein(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_VEIN_HAMMER.get(), "stone");
        helper.setBlock(ORIGIN, Blocks.IRON_ORE);
        helper.setBlock(ORIGIN.offset(1, 0, 0), Blocks.IRON_ORE);
        helper.setBlock(ORIGIN.offset(2, 0, 0), Blocks.STONE);
        BlockPos detached = ORIGIN.offset(3, 0, 0);
        helper.setBlock(detached, Blocks.IRON_ORE);

        player.gameMode.destroyBlock(helper.absolutePos(ORIGIN));

        helper.assertBlockPresent(Blocks.AIR, ORIGIN.offset(1, 0, 0));
        helper.assertBlockPresent(Blocks.STONE, ORIGIN.offset(2, 0, 0));
        helper.assertBlockPresent(Blocks.IRON_ORE, detached);
        helper.succeed();
    }

    // ------------------------------------------------------------------ scythe

    /**
     * The scythe harvests a 3x3x3 of mature wheat and replants every one of them: the crops are back
     * at age 0 rather than gone, which is what separates a harvest from a break.
     */
    @GameTest(template = "empty")
    public static void scytheHarvestsAndReplantsCrops(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_SCYTHE.get(), "stone");
        BlockState mature = Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7);
        List<BlockPos> crops = List.of(ORIGIN, ORIGIN.offset(1, 0, 0), ORIGIN.offset(-1, 0, 1));
        for (BlockPos pos : crops) {
            helper.setBlock(pos.below(), Blocks.FARMLAND);
            helper.setBlock(pos, mature);
        }

        BlockPos clicked = helper.absolutePos(ORIGIN);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));

        for (BlockPos pos : crops) {
            BlockState state = helper.getBlockState(pos);
            helper.assertTrue(state.is(Blocks.WHEAT), "a harvested crop must be replanted, found " + state);
            helper.assertTrue(state.getValue(CropBlock.AGE) == 0,
                    "a replanted crop must start over, found age " + state.getValue(CropBlock.AGE));
        }
        helper.succeed();
    }

    /**
     * The scythe's area attack passes the blow to everything around what it hit (upstream's own
     * sweep).
     *
     * <p>timeoutTicks: the sweep needs the level's <em>entity index</em> to serve the pigs, and a
     * GameTest server runs unthrottled -- game ticks race far ahead of the async chunk promotion
     * that makes a freshly force-loaded plot's entities query-visible, so the default 100 ticks can
     * elapse in well under a second of wall time while the index is still empty (reproduced locally
     * at a plot 12.6M blocks out). The wait below is on the index itself; the budget buys the chunk
     * system the wall time it needs.
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void scytheAttackHitsEveryoneAroundTheTarget(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_SCYTHE.get(), "stone");
        Pig target = staticAdultPig(helper, new BlockPos(2, 2, 2));
        Pig bystander = staticAdultPig(helper, new BlockPos(2, 2, 3));
        Pig outOfReach = staticAdultPig(helper, new BlockPos(2, 2, 6));
        float before = bystander.getHealth();
        float outOfReachBefore = outOfReach.getHealth();

        // The sweep finds its bystanders through the level's entity index, which registers freshly
        // spawned entities asynchronously -- in a just-force-loaded plot that can lag several ticks
        // (one CI-only failure, 2026-08-12; see SpawnCapture for the mechanism). A fixed settle
        // tick was not a guarantee, so wait until the index actually serves all three pigs before
        // swinging. The edge assertion stays exact.
        helper.startSequence()
                // The "empty" template is a single block, so the query spans the pigs themselves
                // (the sweep's own production lookup is an AABB around the target, so pig-relative
                // is also the visibility that actually matters).
                .thenWaitUntil(() -> helper.assertTrue(
                        helper.getLevel().getEntitiesOfClass(Pig.class, target.getBoundingBox().inflate(8.0))
                                .containsAll(java.util.List.of(target, bystander, outOfReach)),
                        "waiting for the entity index to see the three pigs the sweep needs"))
                .thenExecute(() -> {
                    DamageSource source = helper.getLevel().damageSources().playerAttack(player);
                    helper.assertTrue(source.getWeaponItem() == player.getMainHandItem(),
                            "the blow must be attributed to the scythe being tested");
                    target.hurt(source, 2.0F);
                })
                .thenWaitUntil(() -> helper.assertTrue(bystander.getHealth() < before,
                        "the scythe's area attack must reach a second target, it read " + bystander.getHealth()))
                .thenExecute(() -> {
                    helper.assertTrue(outOfReach.getHealth() == outOfReachBefore,
                            "the area attack must have an edge; a pig four blocks away took "
                                    + (outOfReachBefore - outOfReach.getHealth()));
                    target.discard();
                    bystander.discard();
                    outOfReach.discard();
                })
                .thenSucceed();
    }

    // ------------------------------------------------------------------ combat riders

    /**
     * Every rider the maintainer decided on (2026-08-12) is attached, and attached through the shared
     * pipeline rather than by a tool class of its own -- {@link CombatSeams#seams} is exactly what the
     * per-hit hooks iterate. Asserted against {@link ForgeweaveInnates}'s own constants, which is
     * where the magnitudes live: a rider resolving to a different seam entirely is what this catches,
     * and it is the failure a behavioral test on a 20%-chance effect would miss.
     */
    @GameTest(template = "empty")
    public static void everyLargeToolCarriesItsRider(GameTestHelper helper) {
        assertRider(helper, ForgeweaveItems.TOOL_HAMMER.get(), "hammer", ForgeweaveInnates.CONCUSSION_SEAM);
        assertRider(helper, ForgeweaveItems.TOOL_EXCAVATOR.get(), "excavator", ForgeweaveInnates.FLAT_SMACK_SEAM);
        assertRider(helper, ForgeweaveItems.TOOL_LUMBERAXE.get(), "lumber axe", ForgeweaveInnates.TIMBER_SEAM);
        assertRider(helper, ForgeweaveItems.TOOL_SCYTHE.get(), "scythe", ForgeweaveInnates.SWEEP_SEAM);
        assertRider(helper, ForgeweaveItems.TOOL_VEIN_HAMMER.get(), "vein hammer", ForgeweaveInnates.CRUSHING_BLOW_SEAM);
        helper.succeed();
    }

    /**
     * A rider firing for real, on the one that is deterministic and observable: the lumber axe's
     * "timber" adds 15% against a target that has lost no health, and nothing against one that has.
     */
    @GameTest(template = "empty")
    public static void lumberAxeHitsHarderOnAFullHealthTarget(GameTestHelper helper) {
        ServerPlayer player = holdingLargeTool(helper, ForgeweaveItems.TOOL_LUMBERAXE.get(), "stone");
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        Pig fresh = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        float freshMax = fresh.getMaxHealth();
        fresh.hurt(source, 4.0F);
        float firstBlow = freshMax - fresh.getHealth();

        fresh.invulnerableTime = 0;
        fresh.hurt(source, 4.0F);
        float secondBlow = freshMax - fresh.getHealth() - firstBlow;

        helper.assertTrue(firstBlow > secondBlow,
                "timber must make the first blow the bigger one, got " + firstBlow + " then " + secondBlow);
        fresh.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** Assembles {@code tool} at a Tool Forge and puts it in a real server player's main hand. */
    private static ServerPlayer holdingLargeTool(GameTestHelper helper, ToolItem tool, String material) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Survival, not the mock player's default: a creative break skips ItemStack#mineBlock
        // entirely, so the per-block durability cost these tests are about would never be charged.
        player.setGameMode(GameType.SURVIVAL);
        ItemStack assembled = largeTool(helper, player, tool, material);
        helper.assertTrue(assembled.is(tool), "the Tool Forge must build the tool under test, got " + assembled);
        player.setItemInHand(InteractionHand.MAIN_HAND, assembled);
        return player;
    }

    /**
     * One large tool, every part of the same material -- no large-tool behavior here depends on the
     * parts differing, and a four-part tool would otherwise need four names at every call site.
     */
    private static ItemStack largeTool(GameTestHelper helper, Player player, ToolItem tool, String material) {
        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(tool);
        return ToolAssembly.assembleAtForge(helper, player, STATION, entry,
                Collections.nCopies(entry.slotCount(), material));
    }

    /** A solid cube of {@code state} of the given radius, centered on {@code center}. */
    private static void fill(GameTestHelper helper, BlockPos center, int radius, BlockState state) {
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    helper.setBlock(center.offset(x, y, z), state);
                }
            }
        }
    }

    /** Breaks {@code origin} as the player would, and counts how much of the cube around it went. */
    private static int breakAndCount(GameTestHelper helper, ServerPlayer player, BlockPos origin, int radius) {
        player.gameMode.destroyBlock(helper.absolutePos(origin));
        int broken = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (helper.getBlockState(origin.offset(x, y, z)).isAir()) {
                        broken++;
                    }
                }
            }
        }
        return broken;
    }

    /** That {@code large}'s assembled tool resolves to exactly {@code expected} through the pipeline. */
    private static void assertRider(GameTestHelper helper, ToolItem large, String name,
            CombatSeam expected) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack tool = largeTool(helper, player, large, "stone");
        List<CombatSeam> seams = CombatSeams.seams(tool);
        helper.assertTrue(seams.stream().anyMatch(seam -> seam == expected),
                "the " + name + " must carry its innate through the combat seams, got " + seams);
    }

    /**
     * A pig with every source of spawn randomness removed: no AI (it cannot wander off its block
     * between spawn and blow) and forced adult (a randomly-spawned baby's hitbox is small enough to
     * slip out of an AoE reach assertion). The scythe sweep test failed on every CI run and almost
     * never locally, which is what per-run spawn randomness looks like across differently-seeded
     * machines.
     */
    private static Pig staticAdultPig(GameTestHelper helper, BlockPos pos) {
        Pig pig = helper.spawn(EntityType.PIG, pos);
        pig.setNoAi(true);
        pig.setBaby(false);
        pig.moveTo(helper.absoluteVec(new net.minecraft.world.phys.Vec3(
                pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5)));
        return pig;
    }
}
