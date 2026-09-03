package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.BlockingXpSeam;
import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolLevel;

/**
 * M7-3 (issue #920, docs/SCOPE.md D-M7-6/D-M7-10): the ranged XP grant on projectile impact and the
 * three utility grants (mattock till, AoE/kama crop harvest, shovel path) plus blocking, ported from
 * Tinkers' Tool Leveling's {@code ModToolLeveling} (MIT, NOTICE.md).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolLevelingXpGameTests {

    // ------------------------------------------------------------------------------- helpers

    /**
     * One material in every part slot, except a BOWSTRING slot -- which only a dedicated
     * {@code "string"} material carries stats for, the same material every bow GameTest elsewhere
     * uses -- so role differences never matter to these tests beyond that one carve-out.
     */
    private static ItemStack assembleUniform(GameTestHelper helper, ServerPlayer player, BlockPos pos,
            ToolItem tool, String material, boolean atForge) {
        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(tool);
        List<ToolConstants.PartSlot> parts = entry.constants().parts();
        List<String> materials = new ArrayList<>(parts.size());
        for (ToolConstants.PartSlot part : parts) {
            materials.add(part.role() == ToolConstants.Role.BOWSTRING ? "string" : material);
        }
        ItemStack stack = atForge
                ? ToolAssembly.assembleAtForge(helper, player, pos, entry, materials)
                : ToolAssembly.assemble(helper, player, pos, entry, materials);
        helper.assertTrue(stack.is(tool), "expected the assembly to build " + tool + ", got " + stack);
        return stack;
    }

    /** {@link BowItem#createArrow}'s entity, and the damage source vanilla fires it with. */
    private static DamageSource arrowFrom(GameTestHelper helper, ServerPlayer player, ItemStack bow) {
        AbstractArrow arrow = ((ArrowItem) Items.ARROW).createArrow(helper.getLevel(), new ItemStack(Items.ARROW),
                player, bow);
        return helper.getLevel().damageSources().arrow(arrow, player);
    }

    /** The formula {@link dev.gkissel.forgeweave.combat.RangedXpSeam} must reproduce, read off the real stack. */
    private static int expectedRangedXp(ItemStack bow) {
        BowItem item = (BowItem) bow.getItem();
        float drawSpeed = item.drawSpeed(bow);
        return Mth.ceil(5.0F * item.drawTime() / (20.0F * drawSpeed));
    }

    // ------------------------------------------------------------------------------- ranged

    /**
     * D-M7-6: {@code ceil(5 * drawTime / (20 * drawSpeed))} on impact -- a longbow (drawTime 30) and a
     * shortbow (drawTime 12) must grant different amounts, purely from their own draw time.
     */
    @GameTest(template = "empty")
    public static void rangedXpDiffersBetweenBowsByTheirOwnDrawTime(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        // Two separate targets: vanilla's post-hurt invulnerability window would silently swallow a
        // second same-tick hit on the same entity, which would look identical to a miss here.
        Pig shortbowTarget = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        Pig longbowTarget = helper.spawn(EntityType.PIG, new BlockPos(5, 2, 2));

        ItemStack shortbow = assembleUniform(helper, player, new BlockPos(1, 1, 1),
                ForgeweaveItems.TOOL_SHORTBOW.get(), "wood", false);
        player.setItemInHand(InteractionHand.MAIN_HAND, shortbow);
        int expectedShort = expectedRangedXp(shortbow);
        shortbowTarget.hurt(arrowFrom(helper, player, shortbow), 1.0F);
        int gotShort = ToolLevel.of(player.getMainHandItem()).xp();
        helper.assertTrue(gotShort == expectedShort,
                "a shortbow hit must grant ceil(5 * drawTime / (20 * drawSpeed)) = " + expectedShort
                        + ", got " + gotShort);

        ItemStack longbow = assembleUniform(helper, player, new BlockPos(4, 1, 1),
                ForgeweaveItems.TOOL_LONGBOW.get(), "iron", true);
        player.setItemInHand(InteractionHand.MAIN_HAND, longbow);
        int expectedLong = expectedRangedXp(longbow);
        longbowTarget.hurt(arrowFrom(helper, player, longbow), 1.0F);
        int gotLong = ToolLevel.of(player.getMainHandItem()).xp();
        helper.assertTrue(gotLong == expectedLong,
                "a longbow hit must grant ceil(5 * drawTime / (20 * drawSpeed)) = " + expectedLong
                        + ", got " + gotLong);

        helper.assertTrue(gotShort != gotLong,
                "a shortbow's grant (" + gotShort + ") must differ from a longbow's (" + gotLong
                        + ") purely by their drawTime ratio");
        shortbowTarget.discard();
        longbowTarget.discard();
        helper.succeed();
    }

    /** D-M7-6: the crossbow (drawTime 45, no upstream counterpart) takes the same formula. */
    @GameTest(template = "empty")
    public static void crossbowHitGrantsXpForItsOwnDrawTime(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack crossbow = assembleUniform(helper, player, new BlockPos(1, 1, 1),
                ForgeweaveItems.TOOL_CROSSBOW.get(), "iron", true);
        helper.assertTrue(((BowItem) crossbow.getItem()).drawTime() == 45, "CrossBow#getDrawTime() = 45");
        player.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        int expected = expectedRangedXp(crossbow);
        target.hurt(arrowFrom(helper, player, crossbow), 1.0F);

        int got = ToolLevel.of(player.getMainHandItem()).xp();
        helper.assertTrue(got == expected, "a crossbow hit must grant " + expected + ", got " + got);
        target.discard();
        helper.succeed();
    }

    /**
     * D-M7-6: a miss grants nothing -- an arrow that lands in a block never posts the
     * {@code LivingDamageEvent.Post} {@link dev.gkissel.forgeweave.combat.RangedXpSeam} rides.
     */
    @GameTest(template = "empty")
    public static void aMissAgainstABlockGrantsNothing(GameTestHelper helper) {
        BlockPos wallPos = new BlockPos(4, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack bow = assembleUniform(helper, player, new BlockPos(1, 1, 1), ForgeweaveItems.TOOL_SHORTBOW.get(), "wood", false);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.moveTo(helper.absoluteVec(new Vec3(1.5, 1.0, 1.5)));
        helper.setBlock(wallPos, Blocks.STONE);

        AbstractArrow arrow = ((ArrowItem) Items.ARROW).createArrow(helper.getLevel(), new ItemStack(Items.ARROW),
                player, bow);
        arrow.setPos(helper.absoluteVec(new Vec3(1.5, 1.5, 1.5)));
        arrow.shoot(1.0, 0.0, 0.0, 3.0F, 0.0F);
        helper.getLevel().addFreshEntity(arrow);

        helper.runAfterDelay(20, () -> {
            helper.assertTrue(ToolLevel.of(player.getMainHandItem()).equals(ToolLevel.NONE),
                    "an arrow that hits a block must grant no XP, got " + ToolLevel.of(player.getMainHandItem()));
            helper.succeed();
        });
    }

    /** D-M7-3: with the flag off, a ranged hit is inert too, the same as every other grant. */
    @GameTest(template = "empty")
    public static void toolLevelingOffMakesRangedGrantsInert(GameTestHelper helper) {
        ForgeweaveConfig.TOOL_LEVELING.set(false);
        try {
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ItemStack bow = assembleUniform(helper, player, new BlockPos(1, 1, 1), ForgeweaveItems.TOOL_SHORTBOW.get(), "wood", false);
            player.setItemInHand(InteractionHand.MAIN_HAND, bow);
            Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

            target.hurt(arrowFrom(helper, player, bow), 1.0F);

            helper.assertTrue(ToolLevel.of(player.getMainHandItem()).equals(ToolLevel.NONE),
                    "with toolLeveling off, a ranged hit must grant nothing, got "
                            + ToolLevel.of(player.getMainHandItem()));
            target.discard();
        } finally {
            ForgeweaveConfig.TOOL_LEVELING.set(true);
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------------------- utility grants

    /** D-M7-10: hoe-till, beside {@code MattockItem#useOn}'s existing {@code hurtAndBreak}. */
    @GameTest(template = "empty")
    public static void mattockTillGrantsOneXp(GameTestHelper helper) {
        BlockPos standPos = new BlockPos(1, 2, 1);
        BlockPos tillPos = new BlockPos(1, 1, 2);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack mattock = assembleUniform(helper, player, standPos, ForgeweaveItems.TOOL_MATTOCK.get(), "wood", false);
        player.setItemInHand(InteractionHand.MAIN_HAND, mattock);
        helper.setBlock(tillPos, Blocks.DIRT);

        helper.useBlock(tillPos, player);

        helper.assertBlockPresent(Blocks.FARMLAND, tillPos);
        int got = ToolLevel.of(player.getMainHandItem()).xp();
        helper.assertTrue(got == 1, "tilling must grant 1 XP, got " + got);
        helper.succeed();
    }

    /**
     * D-M7-10: the mattock deliberately omits {@code SHOVEL_FLATTEN} from {@code canPerformAction}
     * (see {@code MattockItem}), so it can never reach {@code ShovelPath#flattenOne} and never earns a
     * path grant -- intended, not a gap.
     */
    @GameTest(template = "empty")
    public static void mattockNeverEarnsAPathGrant(GameTestHelper helper) {
        ItemStack mattock = new ItemStack(ForgeweaveItems.TOOL_MATTOCK.get());
        helper.assertTrue(!mattock.getItem().canPerformAction(mattock, ItemAbilities.SHOVEL_FLATTEN),
                "the mattock must never claim SHOVEL_FLATTEN, so it can never earn a path grant");
        helper.succeed();
    }

    /**
     * D-M7-10: +1 per harvested block, shared by the kama's single-block harvest ({@code KamaItem#useOn}
     * -> {@code CropHarvest#harvestAt}) and the scythe's 3x3x3 loop ({@code ToolItem#useOn}'s
     * {@code CUBE_3X3X3} branch -> {@code CropHarvest#harvestAround}) -- both funnel through the same
     * per-block {@code CropHarvest#harvest}, so this covers both call sites the issue names.
     */
    @GameTest(template = "empty")
    public static void kamaSingleHarvestGrantsOneXp(GameTestHelper helper) {
        BlockPos standPos = new BlockPos(1, 2, 1);
        BlockPos cropPos = new BlockPos(1, 1, 2);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack kama = assembleUniform(helper, player, standPos, ForgeweaveItems.TOOL_KAMA.get(), "wood", false);
        player.setItemInHand(InteractionHand.MAIN_HAND, kama);
        helper.setBlock(cropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        helper.useBlock(cropPos, player);

        int got = ToolLevel.of(player.getMainHandItem()).xp();
        helper.assertTrue(got == 1, "a single crop harvest must grant 1 XP, got " + got);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void scytheAoeHarvestGrantsOnePerHarvestedBlock(GameTestHelper helper) {
        // Same layout as LargeToolGameTests#scytheHarvestsAndReplantsCrops (station and crop area
        // well apart, farmland under each crop): "stone", not "wood", is that test's own known-good
        // scythe material, and a direct ItemStack#useOn call, not helper.useBlock, is its own shape
        // for the scythe's 3x3x3 harvest.
        BlockPos standPos = new BlockPos(0, 1, 0);
        BlockPos origin = new BlockPos(3, 2, 3);
        BlockPos adjacent = origin.offset(1, 0, 0);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack scythe = assembleUniform(helper, player, standPos, ForgeweaveItems.TOOL_SCYTHE.get(), "stone", true);
        player.setItemInHand(InteractionHand.MAIN_HAND, scythe);
        helper.setBlock(origin.below(), Blocks.FARMLAND);
        helper.setBlock(origin, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
        helper.setBlock(adjacent.below(), Blocks.FARMLAND);
        helper.setBlock(adjacent, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        BlockPos clicked = helper.absolutePos(origin);
        player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));

        int got = ToolLevel.of(player.getMainHandItem()).xp();
        helper.assertTrue(got == 2, "harvesting two crops in one 3x3x3 swing must grant 2 XP, got " + got);
        helper.succeed();
    }

    /** D-M7-10: +1 per flattened block, {@code ShovelPath#flattenOne}. */
    @GameTest(template = "empty")
    public static void shovelPathGrantsOneXp(GameTestHelper helper) {
        BlockPos standPos = new BlockPos(1, 2, 1);
        BlockPos pathPos = new BlockPos(1, 1, 2);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack shovel = assembleUniform(helper, player, standPos, ForgeweaveItems.TOOL_SHOVEL.get(), "wood", false);
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel);
        helper.setBlock(pathPos, Blocks.GRASS_BLOCK);

        helper.useBlock(pathPos, player);

        helper.assertBlockPresent(Blocks.DIRT_PATH, pathPos);
        int got = ToolLevel.of(player.getMainHandItem()).xp();
        helper.assertTrue(got == 1, "flattening a grass block must grant 1 XP, got " + got);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------- blocking

    /**
     * D-M7-10: {@code max(1, round(originalDamage))} to the actively-blocking tool -- the incoming
     * damage, not what the block absorbed. Driven through the real, globally-registered
     * {@link BlockingXpSeam#INSTANCE} directly (same idiom as {@code MattockKamaGameTests
     * #heftFiresAStrongKnockbackWhenForced}: {@link Forgeweave}'s constructor already registers it
     * once for every tool, so a second registration here would double-grant for the rest of the
     * suite) -- first confirming the battlesign actually resolves to it, then calling it with a
     * {@link CombatDefense} shaped the way {@code CombatSeams#defensePass} builds one for a tool that
     * is both the active item and blocking (the battlesign's only state, since its BLOCK use
     * animation means {@code using} implies {@code blocking} -- see {@code ForgeweaveInnates.Deflect}).
     */
    @GameTest(template = "empty")
    public static void blockingGrantsIncomingDamageRoundedWithAFloorOfOne(GameTestHelper helper) {
        ServerPlayer defender = helper.makeMockServerPlayerInLevel();
        ItemStack battlesign = CombatTraitGameTests.tool(ForgeweaveItems.TOOL_BATTLESIGN.get(), List.of(), 3.0F);
        defender.setItemInHand(InteractionHand.MAIN_HAND, battlesign);

        helper.assertTrue(CombatSeams.seams(battlesign).contains(BlockingXpSeam.INSTANCE),
                "an assembled battlesign must resolve to the real blocking XP seam");

        CombatDefense defense = new CombatDefense(helper.getLevel(), battlesign, defender, null,
                helper.getLevel().damageSources().generic(), true, true);
        BlockingXpSeam.INSTANCE.incomingHit(defense, 10.0F, 10.0F);

        int got = ToolLevel.of(defender.getMainHandItem()).xp();
        helper.assertTrue(got == 10, "blocking a 10-damage blow must grant max(1, round(10)) = 10 XP, got " + got);
        helper.succeed();
    }

    // ------------------------------------------------------------------------------- config off

    /**
     * D-M7-3 across the paths this class owns, in one run: with {@code toolLeveling} off the mattock
     * still tills, the kama still harvests, the shovel still makes a path and the battlesign still
     * blocks -- and none of them writes a single point of XP. The ranged half has its own test above,
     * mining and melee are {@code ToolXpGameTests#configOffGrantsNothingAnywhere}, armor is {@code
     * ArmorLevelingGameTests#nothingAccruesWithTheConfigOff}, and the earned slots that keep counting
     * regardless are {@code ToolLevelSlotGameTests#toolLevelingOffKeepsEarnedSlotsAndTheModifierInThem};
     * between them the flag is pinned off on every path M7 grants XP on (issue #925's sweep).
     */
    @GameTest(template = "empty")
    public static void toolLevelingOffMakesTheUtilityAndBlockingGrantsInert(GameTestHelper helper) {
        ForgeweaveConfig.TOOL_LEVELING.set(false);
        try {
            BlockPos standPos = new BlockPos(1, 2, 1);
            ServerPlayer player = helper.makeMockServerPlayerInLevel();

            BlockPos tillPos = new BlockPos(1, 1, 2);
            ItemStack mattock =
                    assembleUniform(helper, player, standPos, ForgeweaveItems.TOOL_MATTOCK.get(), "wood", false);
            player.setItemInHand(InteractionHand.MAIN_HAND, mattock);
            helper.setBlock(tillPos, Blocks.DIRT);
            helper.useBlock(tillPos, player);
            helper.assertBlockPresent(Blocks.FARMLAND, tillPos);
            helper.assertTrue(ToolLevel.of(player.getMainHandItem()).xp() == 0,
                    "tilling must grant nothing with toolLeveling off, granted "
                            + ToolLevel.of(player.getMainHandItem()).xp());

            BlockPos cropPos = new BlockPos(2, 1, 2);
            ItemStack kama = assembleUniform(helper, player, standPos, ForgeweaveItems.TOOL_KAMA.get(), "wood", false);
            player.setItemInHand(InteractionHand.MAIN_HAND, kama);
            helper.setBlock(cropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));
            helper.useBlock(cropPos, player);
            helper.assertTrue(ToolLevel.of(player.getMainHandItem()).xp() == 0,
                    "harvesting must grant nothing with toolLeveling off, granted "
                            + ToolLevel.of(player.getMainHandItem()).xp());

            BlockPos pathPos = new BlockPos(3, 1, 2);
            ItemStack shovel =
                    assembleUniform(helper, player, standPos, ForgeweaveItems.TOOL_SHOVEL.get(), "wood", false);
            player.setItemInHand(InteractionHand.MAIN_HAND, shovel);
            helper.setBlock(pathPos, Blocks.GRASS_BLOCK);
            helper.useBlock(pathPos, player);
            helper.assertBlockPresent(Blocks.DIRT_PATH, pathPos);
            helper.assertTrue(ToolLevel.of(player.getMainHandItem()).xp() == 0,
                    "pathing must grant nothing with toolLeveling off, granted "
                            + ToolLevel.of(player.getMainHandItem()).xp());

            ItemStack battlesign = CombatTraitGameTests.tool(ForgeweaveItems.TOOL_BATTLESIGN.get(), List.of(), 3.0F);
            player.setItemInHand(InteractionHand.MAIN_HAND, battlesign);
            BlockingXpSeam.INSTANCE.incomingHit(new CombatDefense(helper.getLevel(), battlesign, player, null,
                    helper.getLevel().damageSources().generic(), true, true), 10.0F, 10.0F);
            helper.assertTrue(ToolLevel.of(battlesign).xp() == 0,
                    "blocking must grant nothing with toolLeveling off, granted " + ToolLevel.of(battlesign).xp());
        } finally {
            ForgeweaveConfig.TOOL_LEVELING.set(true);
        }
        helper.succeed();
    }

    private ToolLevelingXpGameTests() {}
}
