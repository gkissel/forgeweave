package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * docs/SCOPE.md M2 issue #102's verification: one test per metal trait, exercising the behavior
 * that distinguishes it. Unlike {@link TraitGameTests} these tools are assembled by hand
 * ({@link #pickaxe}) rather than through a real Tool Station: iron/cobalt/ardite/manyullyn/copper
 * material JSON does not exist yet (issue #103 wires materials to these traits), so there is nothing
 * for a Tool Station to assemble from. The trait ids are set directly, the same {@code ItemStack}
 * shape {@code ToolAssemblyRecipes} would have produced.
 *
 * <p>Magnitudes are upstream 1.12's, cited per trait in {@code ForgeweaveTraits}; deviations forced
 * by the port are cited there too and in the PR body.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MetalTraitGameTests {

    /**
     * Iron -&gt; {@code forgeweave:magnetic2}: within the 30-tick window after a block break, nearby
     * item drops are pulled toward the holder (issue #459 parity fix: upstream gates the pull on a
     * hidden 30-tick potion effect re-applied from {@code afterBlockBreak}/{@code onHit}, not an
     * always-on inventory tick).
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void magneticPullsNearbyItemsTowardTheHolderAfterUse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("magnetic2")), 100, 1.0F, 1.0F);
        ForgeweaveTraits.afterBlockBreak(pickaxe, level, Blocks.STONE.defaultBlockState(), BlockPos.ZERO, player, true);

        ItemEntity dropped = new ItemEntity(level, player.getX() + 1.5, player.getY(), player.getZ(),
                new ItemStack(Items.COBBLESTONE));
        dropped.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dropped);

        // Magnetic's own pull is an entity-index query, and the index registers fresh spawns
        // asynchronously -- ticking the trait in the spawn tick reads as "got (0.0, 0.0, 0.0)"
        // (CI flake at plot -8265235/6470085). Wait for the index to serve the drop first.
        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, dropped))
                .thenExecute(() -> {
                    pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
                    helper.assertTrue(dropped.getDeltaMovement().x < 0.0,
                            "magnetic should push the item toward the holder (negative x), got "
                                    + dropped.getDeltaMovement());
                    dropped.discard();
                })
                .thenSucceed();
    }

    /**
     * Iron -&gt; {@code forgeweave:magnetic2}: with no recent block break or hit, carrying the tool
     * does not pull item drops at all (issue #459 parity fix -- the previous always-on-while-carried
     * behavior deviated from upstream's 30-tick-after-use gate).
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void magneticDoesNotPullWithoutRecentUse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("magnetic2")), 100, 1.0F, 1.0F);

        ItemEntity dropped = new ItemEntity(level, player.getX() + 1.5, player.getY(), player.getZ(),
                new ItemStack(Items.COBBLESTONE));
        dropped.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dropped);

        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, dropped))
                .thenExecute(() -> {
                    pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
                    helper.assertTrue(dropped.getDeltaMovement().equals(Vec3.ZERO),
                            "magnetic must not pull with no recent block break or hit, got "
                                    + dropped.getDeltaMovement());
                    dropped.discard();
                })
                .thenSucceed();
    }

    /**
     * Iron -&gt; {@code forgeweave:magnetic}: the pull window expires 30 ticks after the triggering
     * block break, matching upstream's hidden potion duration.
     */
    @GameTest(template = "empty")
    public static void magneticPullWindowExpiresAfterThirtyTicks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("magnetic2")), 100, 1.0F, 1.0F);
        ForgeweaveTraits.afterBlockBreak(pickaxe, level, Blocks.STONE.defaultBlockState(), BlockPos.ZERO, player, true);

        for (int i = 0; i < 30; i++) {
            pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        }

        ItemEntity dropped = new ItemEntity(level, player.getX() + 1.5, player.getY(), player.getZ(),
                new ItemStack(Items.COBBLESTONE));
        dropped.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dropped);
        pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        helper.assertTrue(dropped.getDeltaMovement().equals(Vec3.ZERO),
                "magnetic's 30-tick window should have expired, got " + dropped.getDeltaMovement());

        dropped.discard();
        helper.succeed();
    }

    /**
     * Iron -&gt; {@code forgeweave:magnetic} + {@code magnetic2}: an all-iron tool sums both levels
     * (1 + 2 = 3) into one pull at the combined range (issue #297 parity fix, upstream
     * {@code AbstractTraitLeveled}'s shared-tag accumulation), not two independent half-strength
     * pulls -- which used to double the force where their ranges overlapped and pull nothing at all
     * past either individual range (2.1 / 2.4 blocks) even though the combined range reaches 2.7.
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void magneticSumsLeveledTraitsIntoOnePull(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("magnetic"), traitId("magnetic2")), 100, 1.0F, 1.0F);
        ForgeweaveTraits.afterBlockBreak(pickaxe, level, Blocks.STONE.defaultBlockState(), BlockPos.ZERO, player, true);

        // 2.6 blocks out: past either individual trait's own range (2.1 / 2.4) but inside the summed
        // level-3 range (1.8 + 3 * 0.3 = 2.7).
        ItemEntity dropped = new ItemEntity(level, player.getX() + 2.6, player.getY(), player.getZ(),
                new ItemStack(Items.COBBLESTONE));
        dropped.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dropped);

        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, dropped))
                .thenExecute(() -> {
                    pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
                    double pull = -dropped.getDeltaMovement().x;
                    helper.assertTrue(pull > 0.0,
                            "an item at 2.6 blocks should be pulled once the levels sum to range 2.7, got "
                                    + dropped.getDeltaMovement());
                    helper.assertTrue(pull < 0.1,
                            "the summed level must still perform one 0.07-strength pull, not two summed "
                                    + "together (0.14), got " + pull);
                    dropped.discard();
                })
                .thenSucceed();
    }

    /**
     * Iron -&gt; {@code forgeweave:magnetic2}: issue #603 parity fix. Upstream's {@code
     * MagneticPotion#performEffect} pulls in all three axes (a plain {@code Vector3d} subtract +
     * normalize, x/y/z alike) at a flat 0.07 blocks/tick, not a horizontal-only projection and not
     * the weaker 0.035 an earlier every-tick adaptation used. An item directly below, and one
     * directly above, the holder must each get the full-strength, correctly-signed vertical pull the
     * instant the window is open -- with the window's ticksRemaining freshly opened at 30 (even), the
     * very next tick is one of the active ones, so a single {@code inventoryTick} call is enough to
     * observe it (matching this class's other magnetic tests, e.g. {@link
     * #magneticPullsNearbyItemsTowardTheHolderAfterUse}). This is what issue #603's "feels stiff and
     * horizontal-only" complaint traces to: 0.035 is real but too weak to read as "pulled" once
     * gravity (0.04/tick) fights it every single tick -- see the trait's own javadoc for the full
     * math and why a naive multi-tick "does the item end up higher" test doesn't hold even for a
     * byte-perfect port.
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void magneticPullsItemsVerticallyAtFullStrength(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("magnetic2")), 100, 1.0F, 1.0F);
        ForgeweaveTraits.afterBlockBreak(pickaxe, level, Blocks.STONE.defaultBlockState(), BlockPos.ZERO, player, true);

        // Both 2.0 blocks out: inside magnetic2's own range (2.4 = 1.8 + 2 * 0.3).
        ItemEntity below = new ItemEntity(level, player.getX(), player.getY() - 2.0, player.getZ(),
                new ItemStack(Items.COBBLESTONE));
        below.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(below);

        ItemEntity above = new ItemEntity(level, player.getX(), player.getY() + 2.0, player.getZ(),
                new ItemStack(Items.COBBLESTONE));
        above.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(above);

        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, below, above))
                .thenExecute(() -> {
                    pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
                    double belowPullY = below.getDeltaMovement().y;
                    double abovePullY = above.getDeltaMovement().y;
                    helper.assertTrue(belowPullY > 0.06,
                            "an item below the holder should get the full 0.07 upward pull, got " + belowPullY);
                    helper.assertTrue(abovePullY < -0.06,
                            "an item above the holder should get the full 0.07 downward pull, got " + abovePullY);
                    below.discard();
                    above.discard();
                })
                .thenSucceed();
    }

    /**
     * Issue #694: the pull must flag the item as impulsed so the server syncs the new motion to
     * clients right away. Items sync position/velocity only every 20 ticks
     * ({@code EntityType.ITEM}'s {@code updateInterval}) unless {@code Entity#hasImpulse} is set, and
     * {@code ItemEntity#tick} only self-flags when its own tick changed velocity by more than 0.1
     * blocks/tick -- a 0.07 pull never does. On a dedicated server the client kept simulating its
     * own gravity between syncs, so the pull looked horizontal-only and jumped every 20 ticks.
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void magneticPullFlagsItemsForClientSync(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("magnetic2")), 100, 1.0F, 1.0F);
        ForgeweaveTraits.afterBlockBreak(pickaxe, level, Blocks.STONE.defaultBlockState(), BlockPos.ZERO, player, true);

        ItemEntity item = new ItemEntity(level, player.getX() + 1.0, player.getY(), player.getZ(),
                new ItemStack(Items.COBBLESTONE));
        item.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(item);

        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, item))
                .thenExecute(() -> {
                    item.hasImpulse = false;
                    pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
                    helper.assertTrue(item.getDeltaMovement().x < -0.06,
                            "the item should have been pulled, got " + item.getDeltaMovement());
                    helper.assertTrue(item.hasImpulse,
                            "the pull must set hasImpulse so the server syncs the item's motion to clients");
                    item.discard();
                })
                .thenSucceed();
    }

    /** Cobalt, head only -&gt; {@code forgeweave:momentum}: mining speed builds up and decays. */
    @GameTest(template = "empty")
    public static void momentumBuildsAndDecaysMiningSpeed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("momentum")), 1000, 2.0F, 1.0F);
        BlockState stone = Blocks.STONE.defaultBlockState();

        helper.assertTrue(ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F) == 2.0F,
                "no bonus before any blocks are broken");

        ForgeweaveTraits.afterBlockBreak(pickaxe, level, stone, BlockPos.ZERO, player, true);
        // level 1: boost = 1/80; speed = 2 + 2 * 1/80 = 2.025
        float afterOne = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(Math.abs(afterOne - 2.025F) < 0.001F, "expected 2.025 after one block, got " + afterOne);

        // duration = (10 / 2) * 1.5 * 20 = 150 ticks
        for (int i = 0; i < 151; i++) {
            pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        }
        float afterDecay = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(afterDecay == 2.0F, "momentum should have decayed to no bonus, got " + afterDecay);

        helper.succeed();
    }

    /** Cobalt -&gt; {@code forgeweave:lightweight}: flat +10% mining and attack speed. */
    @GameTest(template = "empty")
    public static void lightweightBoostsMiningAndAttackSpeed(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("lightweight")), 100, 2.0F, 1.0F);

        float speed = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(Math.abs(speed - 2.2F) < 0.001F, "expected 10% mining speed bonus, got " + speed);

        float attackSpeedBonus = ForgeweaveTraits.attackSpeedBonus(pickaxe);
        helper.assertTrue(Math.abs(attackSpeedBonus - 0.1F) < 0.001F,
                "expected 10% attack speed bonus, got " + attackSpeedBonus);

        helper.succeed();
    }

    /** Ardite, head only -&gt; {@code forgeweave:stonebound}: mining speed rises as durability drops. */
    @GameTest(template = "empty")
    public static void stoneboundGrowsMiningSpeedAsDurabilityDrops(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("stonebound")), 200, 2.0F, 1.0F);

        float fullDurability = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(fullDurability == 2.0F, "no bonus at full durability, got " + fullDurability);

        pickaxe.setDamageValue(100);
        double expected = 2.0 + Math.log(100 / 72.0 + 1.0) * 2.0;
        float worn = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(Math.abs(worn - expected) < 0.001, "expected " + expected + ", got " + worn);

        float ineffective = ForgeweaveTraits.miningSpeed(pickaxe, false, 2.0F);
        helper.assertTrue(ineffective == 2.0F, "stonebound should do nothing when not effective, got " + ineffective);

        helper.succeed();
    }

    /** Ardite -&gt; {@code forgeweave:petramor}: chance to repair itself mining stone, never off-tag blocks. */
    @GameTest(template = "empty")
    public static void petramorSometimesRepairsOnStoneBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("petramor")), 1000, 1.0F, 1.0F);
        pickaxe.setDamageValue(500);

        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int i = 0; i < 200; i++) {
            ForgeweaveTraits.afterBlockBreak(pickaxe, level, stone, BlockPos.ZERO, player, true);
        }
        helper.assertTrue(pickaxe.getDamageValue() < 500,
                "petramor should have healed at least once in 200 stone breaks (p=1e-9 not), still at "
                        + pickaxe.getDamageValue());

        int before = pickaxe.getDamageValue();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (int i = 0; i < 200; i++) {
            ForgeweaveTraits.afterBlockBreak(pickaxe, level, dirt, BlockPos.ZERO, player, true);
        }
        helper.assertTrue(pickaxe.getDamageValue() == before, "petramor must not trigger on non-stone blocks");

        helper.succeed();
    }

    /** Manyullyn, head only -&gt; {@code forgeweave:insatiable}: consecutive hits stack a damage/durability cost. */
    @GameTest(template = "empty")
    public static void insatiableStacksDamageAndDurabilityCostOnConsecutiveHits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("insatiable")), 1000, 1.0F, 3.0F);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        helper.assertTrue(ForgeweaveTraits.bonusDamageAgainst(pickaxe, target, 3.0F) == 0.0F, "no stack yet");

        for (int i = 0; i < 3; i++) {
            ForgeweaveTraits.afterHit(pickaxe, level, attacker, target);
        }
        // level 3: bonus = 3/3 = 1.0
        float bonus = ForgeweaveTraits.bonusDamageAgainst(pickaxe, target, 3.0F);
        helper.assertTrue(Math.abs(bonus - 1.0F) < 0.001F, "expected level 3 / 3 = 1.0 bonus damage, got " + bonus);
        helper.assertTrue(ForgeweaveTraits.attackDurabilityBonus(pickaxe) == 1,
                "expected level 3 / 3 = 1 extra durability loss, got " + ForgeweaveTraits.attackDurabilityBonus(pickaxe));

        // Decay after the 5s (100-tick) window with no more hits.
        for (int i = 0; i < 101; i++) {
            pickaxe.getItem().inventoryTick(pickaxe, level, attacker, 0, false);
        }
        helper.assertTrue(ForgeweaveTraits.bonusDamageAgainst(pickaxe, target, 3.0F) == 0.0F,
                "insatiable should have decayed");

        target.discard();
        helper.succeed();
    }

    /**
     * Manyullyn, head only -&gt; {@code forgeweave:insatiable}: the hit that crosses a stack multiple of
     * 3 pays its own extra durability cost (issue #297 parity fix; upstream {@code ToolHelper
     * #attackEntity} runs every trait's {@code afterHit} before {@code reduceDurabilityOnHit}).
     * {@link ToolItem#postHurtEnemy} is driven directly rather than through a real swing, so the base
     * (non-insatiable) cost is pinned to a known attack-damage attribute.
     */
    @GameTest(template = "empty")
    public static void insatiablePostHurtEnemyChargesTheHitThatCrossesTheThreshold(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("insatiable")), 1000, 1.0F, 3.0F);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        // Two hits already banked: level 2, whose bonus durability truncates to 0 (2 / 3).
        ForgeweaveTraits.afterHit(pickaxe, level, attacker, target);
        ForgeweaveTraits.afterHit(pickaxe, level, attacker, target);
        helper.assertTrue(ForgeweaveTraits.attackDurabilityBonus(pickaxe) == 0, "level 2 pays no bonus yet");

        // A known attack-damage attribute makes the hit's own base cost deterministic:
        // attackDurabilityCost(10, false) == max(1, 10/10) * 2 == 2 (the pickaxe is not Category.WEAPON).
        AttributeInstance attackDamage = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        helper.assertTrue(attackDamage != null, "a mock player must carry the vanilla attack-damage attribute");
        attackDamage.setBaseValue(10.0);
        int before = pickaxe.getDamageValue();
        pickaxe.getItem().postHurtEnemy(pickaxe, target, attacker);
        int cost = pickaxe.getDamageValue() - before;

        // This third hit's own afterHit already grew the stack to level 3 before the durability cost
        // is read, so it pays the new level's +1 bonus (3 / 3), not the +0 the stack owed beforehand.
        helper.assertTrue(cost == 3, "expected base cost 2 + insatiable's new level-3 bonus 1 = 3, got " + cost);
        helper.assertTrue(ForgeweaveTraits.attackDurabilityBonus(pickaxe) == 1,
                "the hit should have grown the stack to level 3, got " + ForgeweaveTraits.attackDurabilityBonus(pickaxe));

        target.discard();
        helper.succeed();
    }

    /** Manyullyn -&gt; {@code forgeweave:coldblooded}: bonus damage only against full-health targets. */
    @GameTest(template = "empty")
    public static void coldbloodedBonusesDamageAgainstFullHealthTargets(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("coldblooded")), 100, 1.0F, 1.0F);
        Pig full = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        Pig hurt = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 2));
        hurt.setHealth(hurt.getMaxHealth() - 1.0F);

        float bonusFull = ForgeweaveTraits.bonusDamageAgainst(pickaxe, full, 4.0F);
        helper.assertTrue(bonusFull == 2.0F, "expected +50% (2.0) against a full-health target, got " + bonusFull);

        float bonusHurt = ForgeweaveTraits.bonusDamageAgainst(pickaxe, hurt, 4.0F);
        helper.assertTrue(bonusHurt == 0.0F, "coldblooded should do nothing against a damaged target, got " + bonusHurt);

        full.discard();
        hurt.discard();
        helper.succeed();
    }

    /** Copper -&gt; {@code forgeweave:established}: bonus XP on kills made with the tool in main hand. */
    @GameTest(template = "empty")
    public static void establishedGrantsBonusKillExperience(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig victim = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        ItemStack established = pickaxe(List.of(traitId("established")), 100, 1.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, established);
        LivingExperienceDropEvent withTrait = new LivingExperienceDropEvent(victim, player, 4);
        ForgeweaveTraits.onExperienceDrop(withTrait);
        // round(4 * 1.25 + r * 0.25) + 1 == 6 for every r in [0, 1): 4*1.25=5.0, +[0,0.25) still rounds to 5.
        helper.assertTrue(withTrait.getDroppedExperience() == 6,
                "expected established's kill-XP formula to give 6, got " + withTrait.getDroppedExperience());

        ItemStack plain = pickaxe(List.of(), 100, 1.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, plain);
        LivingExperienceDropEvent withoutTrait = new LivingExperienceDropEvent(victim, player, 4);
        ForgeweaveTraits.onExperienceDrop(withoutTrait);
        helper.assertTrue(withoutTrait.getDroppedExperience() == 4,
                "a tool without established should leave XP untouched, got " + withoutTrait.getDroppedExperience());

        victim.discard();
        helper.succeed();
    }

    /**
     * Copper -&gt; {@code forgeweave:established}: bonus XP on ordinary block breaks (issue #494/T63).
     * Real per-break rolls are a flat 33% (see {@code ForgeweaveTraits#ESTABLISHED}'s javadoc for why
     * upstream's nominally-two-branch check reduces to that), so this drives a bounded number of
     * breaks through the real {@link BlockDropsEvent} pipeline
     * ({@code ForgeweaveTraits#onBlockBreakExperience}) generous enough that missing every single one
     * is astronomically unlikely ({@code 0.67^200 < 1e-34}), same idiom as {@code
     * ModifierGameTests#luckGrowsFromBlockBreaksUpToTheRecipesCap}.
     */
    @GameTest(template = "empty")
    public static void establishedGrantsBonusBlockBreakExperience(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack established = pickaxe(List.of(traitId("established")), 100, 1.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, established);

        int xp = 0;
        int breaks = 0;
        while (breaks < 200 && xp == 0) {
            BlockDropsEvent event = new BlockDropsEvent(helper.getLevel(), BlockPos.ZERO,
                    Blocks.STONE.defaultBlockState(), null, new ArrayList<>(), player, established);
            ForgeweaveTraits.onBlockBreakExperience(event);
            xp = event.getDroppedExperience();
            breaks++;
        }
        helper.assertTrue(xp == 1, "expected established to eventually roll +1 block-break XP within "
                + breaks + " breaks, got " + xp);

        ItemStack plain = pickaxe(List.of(), 100, 1.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, plain);
        for (int i = 0; i < 200; i++) {
            BlockDropsEvent event = new BlockDropsEvent(helper.getLevel(), BlockPos.ZERO,
                    Blocks.STONE.defaultBlockState(), null, new ArrayList<>(), player, plain);
            ForgeweaveTraits.onBlockBreakExperience(event);
            helper.assertTrue(event.getDroppedExperience() == 0,
                    "a tool without established must never gain block-break XP, got " + event.getDroppedExperience());
        }

        helper.succeed();
    }

    /** Builds a pickaxe {@code ItemStack} with the given traits/stats directly (see class javadoc). */
    private static ItemStack pickaxe(List<ResourceLocation> traits, int durability, float miningSpeed, float attackDamage) {
        ToolItem toolItem = ForgeweaveItems.TOOL_PICKAXE.get();
        ToolStats.Stats stats = new ToolStats.Stats(durability, miningSpeed, attackDamage);
        Material head = new Material(
                new Material.Head(durability, miningSpeed, attackDamage),
                new Material.Handle(1.0F, 0),
                0,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));

        ItemStack stack = new ItemStack(toolItem);
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        stack.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        stack.set(DataComponents.TOOL, toolItem.toolComponent(head, stats));
        stack.set(DataComponents.MAX_DAMAGE, durability);
        stack.set(DataComponents.DAMAGE, 0);
        return stack;
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
