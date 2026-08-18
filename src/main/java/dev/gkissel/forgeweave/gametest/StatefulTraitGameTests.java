package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.AlienProgress;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.ShockingCharge;

/**
 * docs/SCOPE.md M3.2 issue #230's verification: one test per stateful/special trait, exercising the
 * behavior that distinguishes it. Tools are assembled by hand ({@link #pickaxe}), the same shape
 * {@code ToolAssemblyRecipes} produces, because the materials that will grant these traits arrive in
 * the parallel roster issues -- same reasoning as {@link MetalTraitGameTests}'s class javadoc.
 *
 * <p>Randomness is never asserted on: the probabilistic entry points (slimey's 0.33%, baconlicious's
 * 0.5%/5%, tasty's per-tick bite, alien's pool roll) are covered by driving the effect path directly
 * ({@code ForgeweaveTraits#spawnTraitSlime}/{@code #dropBacon}/{@code #tastyNom}, alien with a
 * pre-set pool) plus, where the chance itself matters, a fixed-seed {@link RandomSource} -- the
 * {@code BeheadingGameTests} idiom.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class StatefulTraitGameTests {

    /** End stone -&gt; {@code forgeweave:alien}: the 800-point pool distributes on the 72-tick cadence. */
    @GameTest(template = "empty")
    public static void alienDistributesItsStatPoolOverTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("alien")), 100, 2.0F, 1.0F);

        // A known pool, so every step below is deterministic.
        AlienProgress.Portion pool = new AlienProgress.Portion(10, 0.07F, 0.05F);
        pickaxe.set(ForgeweaveDataComponents.ALIEN_PROGRESS.get(),
                new AlienProgress(pool, AlienProgress.Portion.ZERO));

        player.tickCount = 72; // not a multiple of 144 or 216: the durability step.
        pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        helper.assertTrue(pickaxe.getMaxDamage() == 101,
                "expected the 72-tick step to grow max damage to 101, got " + pickaxe.getMaxDamage());

        player.tickCount = 144; // multiple of 144, not 216: the speed step.
        pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        float speed = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(Math.abs(speed - 2.007F) < 0.0001F,
                "expected one 0.007 speed step on top of 2.0, got " + speed);

        player.tickCount = 216; // multiple of 216: the attack step.
        pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        float attack = ForgeweaveTraits.attackDamageBonus(pickaxe);
        helper.assertTrue(Math.abs(attack - 0.005F) < 0.0001F,
                "expected one 0.005 attack step, got " + attack);

        // Off-cadence ticks distribute nothing.
        player.tickCount = 73;
        pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        AlienProgress progress = pickaxe.get(ForgeweaveDataComponents.ALIEN_PROGRESS.get());
        helper.assertTrue(progress != null && progress.distributed().durability() == 1,
                "an off-cadence tick must not distribute, got " + progress);

        helper.succeed();
    }

    /**
     * End stone -&gt; {@code forgeweave:alien}: a FakePlayer holder (issue #297 parity fix; upstream
     * {@code TraitAlien#onUpdate}'s {@code instanceof FakePlayer} guard) never grows the tool's stats,
     * even on an otherwise on-cadence tick.
     */
    @GameTest(template = "empty")
    public static void alienSkipsStatGrowthForFakePlayers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        FakePlayer holder = new FakePlayer(level, new GameProfile(
                UUID.nameUUIDFromBytes("forgeweave:alien-faketest".getBytes()), "[forgeweave-alien-test]"));
        ItemStack pickaxe = pickaxe(List.of(traitId("alien")), 100, 2.0F, 1.0F);
        AlienProgress.Portion pool = new AlienProgress.Portion(10, 0.07F, 0.05F);
        pickaxe.set(ForgeweaveDataComponents.ALIEN_PROGRESS.get(),
                new AlienProgress(pool, AlienProgress.Portion.ZERO));

        holder.tickCount = 72; // an on-cadence durability-step tick, same as the real-player test above.
        pickaxe.getItem().inventoryTick(pickaxe, level, holder, 0, false);

        helper.assertTrue(pickaxe.getMaxDamage() == 100,
                "a FakePlayer holder must never distribute alien's pool, max damage now " + pickaxe.getMaxDamage());
        AlienProgress progress = pickaxe.get(ForgeweaveDataComponents.ALIEN_PROGRESS.get());
        helper.assertTrue(progress != null && progress.distributed().durability() == 0,
                "a FakePlayer holder must not touch the distributed portion either, got " + progress);

        helper.succeed();
    }

    /** Alien rolls its pool lazily on first tick: 800 points across the three stats. */
    @GameTest(template = "empty")
    public static void alienRollsAnEightHundredPointPoolOnFirstTick(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("alien")), 100, 2.0F, 1.0F);

        player.tickCount = 72;
        pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);

        AlienProgress progress = pickaxe.get(ForgeweaveDataComponents.ALIEN_PROGRESS.get());
        helper.assertTrue(progress != null, "the first on-cadence tick must roll and store the pool");
        AlienProgress.Portion pool = progress.pool();
        int points = pool.durability()
                + Math.round(pool.miningSpeed() / 0.007F)
                + Math.round(pool.attackDamage() / 0.005F);
        helper.assertTrue(points == 800, "the pool must hold exactly 800 points worth of steps, got " + points);

        helper.succeed();
    }

    /** Electrum -&gt; {@code forgeweave:shocking}: hits, mining and movement all build the 0-100 charge. */
    @GameTest(template = "empty")
    public static void shockingBuildsChargeFromHitsMiningAndMovement(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        ItemStack pickaxe = pickaxe(List.of(traitId("shocking")), 1000, 1.0F, 1.0F);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);

        // A full-strength landed hit: +15.
        ForgeweaveTraits.COMBAT_SEAM.onHit(
                new CombatHit(level, pickaxe, attacker, target, level.damageSources().mobAttack(attacker)), 1.0F);
        helper.assertTrue(Math.abs(charge(pickaxe) - 15.0F) < 0.001F,
                "expected +15 charge from a full-strength hit, got " + charge(pickaxe));

        // A block break: +15 more.
        ForgeweaveTraits.afterBlockBreak(pickaxe, level, Blocks.STONE.defaultBlockState(),
                BlockPos.ZERO, attacker, true);
        helper.assertTrue(Math.abs(charge(pickaxe) - 30.0F) < 0.001F,
                "expected +15 charge from a block break, got " + charge(pickaxe));

        // Movement, sampled every 5 holder ticks: 3 blocks moved = +6, and a 10-block sprint clamps
        // to the 5-block cap = +10.
        pickaxe.remove(ForgeweaveDataComponents.SHOCKING_CHARGE.get());
        attacker.setPos(0.0, 2.0, 0.0);
        attacker.tickCount = 5;
        pickaxe.getItem().inventoryTick(pickaxe, level, attacker, 0, true); // baseline sample only
        helper.assertTrue(charge(pickaxe) == 0.0F, "the first sample only sets the baseline");
        attacker.setPos(3.0, 2.0, 0.0);
        attacker.tickCount = 10;
        pickaxe.getItem().inventoryTick(pickaxe, level, attacker, 0, true);
        helper.assertTrue(Math.abs(charge(pickaxe) - 6.0F) < 0.001F,
                "expected 3 blocks * 2 = 6 charge, got " + charge(pickaxe));
        attacker.setPos(13.0, 2.0, 0.0);
        attacker.tickCount = 15;
        pickaxe.getItem().inventoryTick(pickaxe, level, attacker, 0, true);
        helper.assertTrue(Math.abs(charge(pickaxe) - 16.0F) < 0.001F,
                "expected the 10-block move to clamp at 5 * 2 = +10, got " + charge(pickaxe));

        target.discard();
        helper.succeed();
    }

    /**
     * Electrum -&gt; {@code forgeweave:shocking}: a non-player attacker (a mob wielding the tool) never
     * builds charge from a hit (issue #297 parity fix; upstream {@code TraitShocking#onHit}'s
     * {@code else if (player instanceof EntityPlayer)} gate).
     */
    @GameTest(template = "empty")
    public static void shockingChargeOnlyAccruesForPlayerAttackers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pig attacker = helper.spawn(EntityType.PIG, new BlockPos(0, 2, 0));
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        ItemStack pickaxe = pickaxe(List.of(traitId("shocking")), 1000, 1.0F, 1.0F);

        ForgeweaveTraits.COMBAT_SEAM.onHit(
                new CombatHit(level, pickaxe, attacker, target, level.damageSources().mobAttack(attacker)), 1.0F);
        helper.assertTrue(charge(pickaxe) == 0.0F,
                "a non-player attacker must never accrue charge, got " + charge(pickaxe));

        attacker.discard();
        target.discard();
        helper.succeed();
    }

    /** A fully charged hit discharges: +5 lightning damage, Speed for the attacker, charge back to 0. */
    @GameTest(template = "empty")
    public static void shockingDischargesAtFullCharge(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        ItemStack pickaxe = pickaxe(List.of(traitId("shocking")), 1000, 1.0F, 1.0F);
        pickaxe.set(ForgeweaveDataComponents.SHOCKING_CHARGE.get(), new ShockingCharge(100.0F, 0, 0, 0));

        float healthBefore = target.getHealth();
        ForgeweaveTraits.COMBAT_SEAM.onHit(
                new CombatHit(level, pickaxe, attacker, target, level.damageSources().mobAttack(attacker)), 1.0F);

        helper.assertTrue(Math.abs(healthBefore - target.getHealth() - 5.0F) < 0.001F,
                "expected the discharge to deal 5 lightning damage, target went " + healthBefore
                        + " -> " + target.getHealth());
        helper.assertTrue(charge(pickaxe) == 0.0F, "the discharge must reset the charge, got " + charge(pickaxe));
        helper.assertTrue(attacker.hasEffect(MobEffects.MOVEMENT_SPEED),
                "the discharging attacker gets the upstream Speed effect");
        helper.assertTrue(pickaxe.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null,
                "the full-charge glint must clear on discharge");

        // The mining path discharges on the spot into Haste once a break fills the charge.
        Player miner = helper.makeMockPlayer(GameType.SURVIVAL);
        pickaxe.set(ForgeweaveDataComponents.SHOCKING_CHARGE.get(), new ShockingCharge(90.0F, 0, 0, 0));
        ForgeweaveTraits.afterBlockBreak(pickaxe, level, Blocks.STONE.defaultBlockState(),
                BlockPos.ZERO, miner, true);
        helper.assertTrue(charge(pickaxe) == 0.0F,
                "a break that fills the charge must discharge immediately, got " + charge(pickaxe));
        helper.assertTrue(miner.hasEffect(MobEffects.DIG_SPEED),
                "the mining discharge grants the upstream Haste effect");

        target.discard();
        helper.succeed();
    }

    /**
     * Issue #415: reaching full charge and discharging each fire a server-side {@code
     * PlayLevelSoundEvent.AtPosition} for the vanilla stand-in sound cue -- {@link SoundCapture}
     * observes the seam NeoForge fires from inside {@code Level#playSound} itself, since the sound
     * packet delivery has no observer without a tracked client player. The {@code ELECTRIC_SPARK}
     * particle burst emitted alongside each sound (see {@code
     * ForgeweaveTraits#SHOCKING_FEEDBACK_VOLUME}'s javadoc) has no equivalent server-side event to
     * capture and is verified by code inspection at the {@code spawnShockingFullChargeFeedback}/
     * {@code spawnShockingDischargeFeedback} call sites instead.
     */
    @GameTest(template = "empty")
    public static void shockingFullChargeAndDischargeEachPlayASoundCue(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos attackerPos = helper.absolutePos(new BlockPos(1, 2, 1));
        attacker.setPos(attackerPos.getX() + 0.5, attackerPos.getY(), attackerPos.getZ() + 0.5);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        ItemStack pickaxe = pickaxe(List.of(traitId("shocking")), 1000, 1.0F, 1.0F);
        pickaxe.set(ForgeweaveDataComponents.SHOCKING_CHARGE.get(), new ShockingCharge(85.0F, 0, 0, 0));

        // The hit that fills the charge: the full-charge cue plays at the attacker. (The pig's own
        // hurt sound also plays in this window from the landed hit itself -- filtered out below,
        // since only the shocking cue's own sound event is under test.)
        List<SoundCapture.Played> onFullCharge = shockingCues(SoundCapture.playedDuring(helper, () ->
                ForgeweaveTraits.COMBAT_SEAM.onHit(new CombatHit(level, pickaxe, attacker, target,
                        level.damageSources().mobAttack(attacker)), 1.0F)));
        helper.assertTrue(charge(pickaxe) == ShockingCharge.FULL,
                "the hit must fill the charge, got " + charge(pickaxe));
        helper.assertTrue(onFullCharge.size() == 1, "expected exactly one full-charge sound cue, got " + onFullCharge);

        // The next hit discharges: the discharge cue plays at the target.
        List<SoundCapture.Played> onDischarge = shockingCues(SoundCapture.playedDuring(helper, () ->
                ForgeweaveTraits.COMBAT_SEAM.onHit(new CombatHit(level, pickaxe, attacker, target,
                        level.damageSources().mobAttack(attacker)), 1.0F)));
        helper.assertTrue(charge(pickaxe) == 0.0F, "the discharge must reset the charge, got " + charge(pickaxe));
        helper.assertTrue(onDischarge.size() == 1, "expected exactly one discharge sound cue, got " + onDischarge);
        double dischargeDistance = target.position().distanceTo(onDischarge.get(0).position());
        helper.assertTrue(dischargeDistance < 1.5,
                "the discharge cue must play near the target, was " + dischargeDistance + " blocks away");

        // A block break that exactly fills the charge fires both cues in the one call (matching
        // upstream's addCharge-then-discharge back-to-back calls). Both the miner and the broken
        // block need an in-bounds position -- SoundCapture only sees cues inside the test structure.
        Player miner = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos minerPos = helper.absolutePos(new BlockPos(1, 2, 2));
        miner.setPos(minerPos.getX() + 0.5, minerPos.getY(), minerPos.getZ() + 0.5);
        ItemStack miningPickaxe = pickaxe(List.of(traitId("shocking")), 1000, 1.0F, 1.0F);
        miningPickaxe.set(ForgeweaveDataComponents.SHOCKING_CHARGE.get(), new ShockingCharge(90.0F, 0, 0, 0));
        BlockPos brokenBlock = helper.absolutePos(new BlockPos(2, 2, 2));
        List<SoundCapture.Played> onMiningDischarge = shockingCues(SoundCapture.playedDuring(helper, () ->
                ForgeweaveTraits.afterBlockBreak(miningPickaxe, level, Blocks.STONE.defaultBlockState(),
                        brokenBlock, miner, true)));
        helper.assertTrue(onMiningDischarge.size() == 2,
                "a break that fills the charge must fire both the full-charge and discharge cues, got "
                        + onMiningDischarge);

        target.discard();
        helper.succeed();
    }

    /**
     * Issue #341: the full shocking charge is the <em>only</em> thing that makes a Forgeweave tool
     * shimmer. Upstream 1.12's {@code ToolCore#hasEffect} reports its explicit enchant-effect flag and
     * nothing else, so a tool carrying an enchantment a modifier granted (luck's Fortune here) stays
     * dull below full charge, glints while full, and goes back to dull the moment it discharges --
     * rather than falling back on vanilla's "any enchantment glints" default.
     */
    @GameTest(template = "empty")
    public static void onlyTheFullShockingChargeMakesTheToolGlint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        ItemStack pickaxe = pickaxe(List.of(traitId("shocking")), 1000, 1.0F, 1.0F);
        pickaxe.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), 1);

        helper.assertFalse(pickaxe.hasFoil(), "a modifier-granted Fortune must not make the tool glint");

        // 15 charge per landed hit, clamped at the 100 mark: seven hits fill it without discharging.
        for (int i = 0; i < 7; i++) {
            ForgeweaveTraits.COMBAT_SEAM.onHit(
                    new CombatHit(level, pickaxe, attacker, target, level.damageSources().mobAttack(attacker)), 1.0F);
        }
        helper.assertTrue(charge(pickaxe) == ShockingCharge.FULL,
                "seven hits must fill the charge, got " + charge(pickaxe));
        helper.assertTrue(pickaxe.hasFoil(), "a fully charged shocking tool glints");

        ForgeweaveTraits.COMBAT_SEAM.onHit(
                new CombatHit(level, pickaxe, attacker, target, level.damageSources().mobAttack(attacker)), 1.0F);
        helper.assertTrue(charge(pickaxe) == 0.0F, "the eighth hit discharges, got " + charge(pickaxe));
        helper.assertFalse(pickaxe.hasFoil(),
                "once discharged the tool is dull again, Fortune or no Fortune");

        target.discard();
        helper.succeed();
    }

    /** Green/blue slime -&gt; {@code forgeweave:slimey_*}: 0.33% roll, size-1 slime on the spawn path. */
    @GameTest(template = "empty")
    public static void slimeySpawnsASizeOneSlime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos where = helper.absolutePos(new BlockPos(2, 2, 2));

        // The chance, off a fixed seed so the count is deterministic: ~0.33% of 10000 draws.
        RandomSource random = RandomSource.create(230L);
        int procs = 0;
        for (int i = 0; i < 10_000; i++) {
            if (ForgeweaveTraits.rollsSlimeyProc(random)) {
                procs++;
            }
        }
        helper.assertTrue(procs > 0 && procs < 100,
                "0.33% of 10000 seeded rolls should proc a few dozen times, got " + procs);

        // The spawn path, driven deterministically past the roll. Captured at the spawn seam rather
        // than queried from the entity index, which lags behind synchronous spawns in a freshly
        // force-loaded plot (see SpawnCapture).
        List<Slime> slimes = SpawnCapture.spawnedDuring(helper, Slime.class,
                () -> ForgeweaveTraits.spawnTraitSlime(level, player, where.getX() + 0.5, where.getY(), where.getZ() + 0.5));
        helper.assertTrue(slimes.size() == 1, "expected exactly one spawned slime, got " + slimes.size());
        helper.assertTrue(slimes.get(0).getSize() == 1,
                "upstream spawns the smallest slime, got size " + slimes.get(0).getSize());
        helper.assertTrue(slimes.get(0).getLastHurtByMob() == player,
                "the slime must remember the tool's holder as its attacker");

        slimes.forEach(Slime::discard);
        helper.succeed();
    }

    /** Magma slime -&gt; {@code forgeweave:baconlicious}: the drop path leaves a cooked porkchop. */
    @GameTest(template = "empty")
    public static void baconliciousDropsACookedPorkchop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos where = helper.absolutePos(new BlockPos(2, 2, 2));

        // Captured at the spawn seam rather than queried from the entity index (see SpawnCapture).
        List<ItemEntity> drops = SpawnCapture.spawnedDuring(helper, ItemEntity.class,
                () -> ForgeweaveTraits.dropBacon(level, where.getX() + 0.5, where.getY(), where.getZ() + 0.5));
        helper.assertTrue(drops.size() == 1 && drops.get(0).getItem().is(Items.COOKED_PORKCHOP),
                "expected one cooked porkchop drop (Forgeweave's bacon stand-in), got " + drops);

        drops.forEach(ItemEntity::discard);
        helper.succeed();
    }

    /** Slime wood -&gt; {@code forgeweave:tasty}: a bite feeds the holder for 5 durability. */
    @GameTest(template = "empty")
    public static void tastyFeedsTheHolderAtADurabilityCost(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("tasty")), 1000, 1.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        player.getFoodData().setFoodLevel(4);

        ForgeweaveTraits.tastyNom(pickaxe, level, player);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 5,
                "a bite feeds exactly one food point, got " + player.getFoodData().getFoodLevel());
        helper.assertTrue(pickaxe.getDamageValue() == 5,
                "a bite costs exactly 5 durability, got " + pickaxe.getDamageValue());

        // Too worn to bite: the guard skips the nom entirely.
        pickaxe.setDamageValue(pickaxe.getMaxDamage() - 4);
        ForgeweaveTraits.tastyNom(pickaxe, level, player);
        helper.assertTrue(player.getFoodData().getFoodLevel() == 5,
                "a tool with under 5 durability left must not be eaten");

        // Full hunger: the trait's tick never bites, however many ticks pass.
        ItemStack fresh = pickaxe(List.of(traitId("tasty")), 1000, 1.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, fresh);
        player.getFoodData().setFoodLevel(20);
        for (int i = 0; i < 100; i++) {
            fresh.getItem().inventoryTick(fresh, level, player, 0, true);
        }
        helper.assertTrue(fresh.getDamageValue() == 0, "a sated holder never eats the tool");

        helper.succeed();
    }

    /** Ancient -&gt; {@code forgeweave:vintage}: +1 modifier slot, -10% movement speed while held. */
    @GameTest(template = "empty")
    public static void vintageAddsASlotAndSlowsTheHolder(GameTestHelper helper) {
        ItemStack vintage = pickaxe(List.of(traitId("vintage")), 100, 1.0F, 1.0F);
        ItemStack plain = pickaxe(List.of(), 100, 1.0F, 1.0F);

        helper.assertTrue(
                ForgeweaveModifiers.freeSlots(vintage) == ForgeweaveModifiers.freeSlots(plain) + 1,
                "vintage grants one modifier slot over an otherwise identical tool");

        ItemAttributeModifiers modifiers = ((ToolItem) vintage.getItem()).getDefaultAttributeModifiers(vintage);
        boolean found = modifiers.modifiers().stream().anyMatch(entry ->
                entry.attribute().value() == Attributes.MOVEMENT_SPEED.value()
                        && entry.modifier().operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                        && Math.abs(entry.modifier().amount() + 0.10) < 0.0001
                        && entry.slot() == EquipmentSlotGroup.MAINHAND);
        helper.assertTrue(found, "expected a -10% main-hand movement speed modifier, got " + modifiers.modifiers());

        ItemAttributeModifiers plainModifiers = ((ToolItem) plain.getItem()).getDefaultAttributeModifiers(plain);
        boolean foundOnPlain = plainModifiers.modifiers().stream()
                .anyMatch(entry -> entry.attribute().value() == Attributes.MOVEMENT_SPEED.value());
        helper.assertTrue(!foundOnPlain, "a tool without vintage keeps its movement speed");

        helper.succeed();
    }

    private static float charge(ItemStack stack) {
        ShockingCharge charge = stack.get(ForgeweaveDataComponents.SHOCKING_CHARGE.get());
        return charge == null ? 0.0F : charge.charge();
    }

    /** Narrows a captured window down to shocking's own feedback cue, ignoring incidental sounds
     *  (a landed hit's own hurt sound, for one) that play in the same window. */
    private static List<SoundCapture.Played> shockingCues(List<SoundCapture.Played> played) {
        return played.stream().filter(p -> p.sound().value() == SoundEvents.TRIDENT_THUNDER.value()).toList();
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
