package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.TraitStacks;

/**
 * M6-7 (issue #831): one test per registered instance of the armor trait behavior library, on a real
 * chestplate assembled at the Armor Station and worn by a survival mock player -- the same staging
 * {@link ArmorTraitGameTests} uses for the M4 ARMOR traits.
 *
 * <p>The one difference from that class: no material grants these traits yet (the issue ships no
 * material JSON; the M6 preset batches assign the ids), so a piece is assembled from iron and its
 * {@code forgeweave:traits} component is then overwritten with exactly the ids under test --
 * {@link MiningTraitGameTests} and {@link UtilityTraitGameTests} stage the tool-side library batches
 * the same way, and {@link ArmorTraitGameTests#lostWithoutTraits} already writes that component by
 * hand for its own ratios.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArmorTraitLibraryGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    private static final float BLOW = 8.0F;
    private static final long NOON = 6000L;
    private static final long MIDNIGHT = 18000L;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /** A survival mock player wearing an iron chestplate whose trait list is exactly {@code traits}. */
    private static Player wearing(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.ARMOR_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of("iron", "iron"));
        piece.set(ForgeweaveDataComponents.TRAITS.get(), List.of(traits).stream()
                .map(ArmorTraitLibraryGameTests::id).toList());
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        return player;
    }

    private static ItemStack worn(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST);
    }

    /** One blow onto a healed, non-invulnerable player; what it cost them ({@link ArmorTraitGameTests}' own helper). */
    private static float lost(Player player, DamageSource source, float amount) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(source, amount);
        return before - player.getHealth();
    }

    private static DamageSource explosion(GameTestHelper helper, Zombie exploder) {
        return helper.getLevel().damageSources().explosion(exploder, exploder);
    }

    private static Zombie zombie(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.setNoAi(true);
        return zombie;
    }

    private static void daytime(GameTestHelper helper, long time) {
        helper.getLevel().setDayTime(time);
        helper.getLevel().updateSkyBrightness();
    }

    /** {@code bloodtoll}: stormrind zeroes a lightning blow, the floor puts a half heart back. */
    @GameTest(template = "empty")
    public static void bloodtollKeepsACancelledBlowFromBeingFree(GameTestHelper helper) {
        DamageSource lightning = helper.getLevel().damageSources().lightningBolt();

        Player immune = wearing(helper, "stormrind");
        helper.assertTrue(lost(immune, lightning, BLOW) == 0.0F, "stormrind alone must cancel the blow outright");

        Player floored = wearing(helper, "stormrind", "bloodtoll");
        float lost = lost(floored, lightning, BLOW);
        helper.assertTrue(lost > 0.0F && lost < BLOW,
                "the floor must put something back without restoring the whole blow, lost " + lost);
        helper.succeed();
    }

    /** {@code hexward}: a quarter of direct blows weaken the attacker. Rolled often enough that a miss is impossible in practice. */
    @GameTest(template = "empty")
    public static void hexwardWeakensDirectAttackers(GameTestHelper helper) {
        Player player = wearing(helper, "hexward");
        Zombie attacker = zombie(helper);
        DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);
        for (int i = 0; i < 60 && attacker.getEffect(MobEffects.WEAKNESS) == null; i++) {
            lost(player, source, 1.0F);
        }
        helper.assertTrue(attacker.getEffect(MobEffects.WEAKNESS) != null,
                "60 blows at a 25% chance must have weakened the attacker at least once");
        attacker.discard();
        helper.succeed();
    }

    /** {@code mendbond}: a heal lands a quarter larger than the same heal without the trait. */
    @GameTest(template = "empty")
    public static void mendbondAmplifiesHealing(GameTestHelper helper) {
        Player plain = wearing(helper);
        plain.setHealth(10.0F);
        plain.heal(4.0F);
        float without = plain.getHealth() - 10.0F;

        Player player = wearing(helper, "mendbond");
        player.setHealth(10.0F);
        player.heal(4.0F);
        float with = player.getHealth() - 10.0F;

        helper.assertTrue(Math.abs(without - 4.0F) < 0.01F, "the control heal is the plain 4, got " + without);
        helper.assertTrue(Math.abs(with - 5.0F) < 0.01F, "mendbond must turn 4 into 5, got " + with);
        helper.succeed();
    }

    /** {@code emberdrink}: fire does no damage and heals half of what it would have dealt. */
    @GameTest(template = "empty")
    public static void emberdrinkTurnsFireIntoHealing(GameTestHelper helper) {
        Player player = wearing(helper, "emberdrink");
        player.setHealth(10.0F);
        player.invulnerableTime = 0;
        player.hurt(helper.getLevel().damageSources().inFire(), BLOW);
        helper.assertTrue(player.getHealth() > 10.0F,
                "a fire blow must heal rather than hurt, health " + player.getHealth());
        helper.succeed();
    }

    /** {@code bracingplate}: repeated blows build protection, and the stacks live on the piece. */
    @GameTest(template = "empty")
    public static void bracingplateBuildsProtectionAsBlowsLand(GameTestHelper helper) {
        Player player = wearing(helper, "bracingplate");
        DamageSource source = helper.getLevel().damageSources().generic();
        // 10, not 20: a blow that exactly kills this player would drop their armor on the floor
        // (Player#die empties the inventory), and every later blow in the loop would land unarmored.
        float first = lost(player, source, 10.0F);
        float last = first;
        for (int i = 0; i < 6; i++) {
            last = lost(player, source, 10.0F);
        }
        helper.assertTrue(last < first, "the seventh blow must cost less than the first, " + last + " vs " + first);
        TraitStacks stacks = worn(player).get(ForgeweaveDataComponents.RESISTANCE_STACKS.get());
        helper.assertTrue(stacks != null && stacks.level() == 6,
                "seven blows must leave the stacks at the cap of 6, got " + stacks);
        helper.succeed();
    }

    /** {@code sapmend}: taking a blow starts the wearer regenerating. */
    @GameTest(template = "empty")
    public static void sapmendRegeneratesAfterABlow(GameTestHelper helper) {
        Player player = wearing(helper, "sapmend");
        lost(player, helper.getLevel().damageSources().generic(), BLOW);
        helper.assertTrue(player.getEffect(MobEffects.REGENERATION) != null,
                "a blow that landed must leave Regeneration on the wearer");
        helper.succeed();
    }

    /** {@code lastbreath}: the first killing blow is spent on the piece, the second is not. */
    @GameTest(template = "empty")
    public static void lastbreathSavesOnceThenGoesOnCooldown(GameTestHelper helper) {
        Player player = wearing(helper, "lastbreath");
        DamageSource source = helper.getLevel().damageSources().generic();
        int before = worn(player).getDamageValue();

        player.setHealth(2.0F);
        player.invulnerableTime = 0;
        player.hurt(source, 40.0F);
        helper.assertTrue(player.getHealth() == 2.0F, "the killing blow must be negated, health " + player.getHealth());
        helper.assertTrue(worn(player).getDamageValue() == before + 100,
                "the save must cost the piece 100 durability, damage " + worn(player).getDamageValue());
        helper.assertTrue(worn(player).get(ForgeweaveDataComponents.DEATH_SAVE_COOLDOWN.get()) != null,
                "and leave the cooldown on the piece");

        player.invulnerableTime = 0;
        player.hurt(source, 40.0F);
        helper.assertTrue(player.getHealth() < 2.0F, "a second killing blow inside the cooldown must land");
        helper.succeed();
    }

    /** {@code aegispulse}: struck at full health, the wearer's recovery window is doubled. */
    @GameTest(template = "empty")
    public static void aegispulseLengthensTheInvulnerabilityWindow(GameTestHelper helper) {
        Player plain = wearing(helper);
        lost(plain, helper.getLevel().damageSources().generic(), BLOW);
        helper.assertTrue(plain.invulnerableTime == 20, "vanilla's own window is 20, got " + plain.invulnerableTime);

        Player player = wearing(helper, "aegispulse");
        lost(player, helper.getLevel().damageSources().generic(), BLOW);
        helper.assertTrue(player.invulnerableTime == 40,
                "a blow at full health must buy 40 ticks, got " + player.invulnerableTime);
        helper.succeed();
    }

    /** {@code windstep}: one blow in ten misses. 200 rolls; zero misses is a 7e-10 event. */
    @GameTest(template = "empty")
    public static void windstepSometimesAvoidsABlowEntirely(GameTestHelper helper) {
        Player player = wearing(helper, "windstep");
        DamageSource source = helper.getLevel().damageSources().generic();
        int misses = 0;
        for (int i = 0; i < 200; i++) {
            if (lost(player, source, BLOW) == 0.0F) {
                misses++;
            }
        }
        helper.assertTrue(misses > 0, "200 blows at a 10% dodge must have missed at least once");
        helper.assertTrue(misses < 200, "and must not have missed all of them");
        helper.succeed();
    }

    /** {@code nightveil}: half visibility in the dark, none of it by day. */
    @GameTest(template = "empty")
    public static void nightveilHidesTheWearerOnlyInTheDark(GameTestHelper helper) {
        Player player = wearing(helper, "nightveil");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        Zombie looker = zombie(helper);

        daytime(helper, NOON);
        double lit = player.getVisibilityPercent(looker);
        daytime(helper, MIDNIGHT);
        double dark = player.getVisibilityPercent(looker);

        helper.assertTrue(Math.abs(lit - 1.0) < 0.01, "broad daylight must not conceal anyone, got " + lit);
        helper.assertTrue(Math.abs(dark - 0.5) < 0.01, "darkness must halve visibility, got " + dark);
        looker.discard();
        helper.succeed();
    }

    /** {@code swiftstride}: the piece grants its movement speed as a worn attribute. */
    @GameTest(template = "empty")
    public static void swiftstrideRaisesMovementSpeed(GameTestHelper helper) {
        Player plain = wearing(helper);
        double without = plain.getAttributeValue(Attributes.MOVEMENT_SPEED);
        Player player = wearing(helper, "swiftstride");
        double with = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
        helper.assertTrue(with > without, "the piece must speed the wearer up, " + with + " vs " + without);
        helper.succeed();
    }

    /** {@code battleworn}: a battered piece protects better than a fresh one. */
    @GameTest(template = "empty")
    public static void battlewornProtectsMoreAsItWearsDown(GameTestHelper helper) {
        DamageSource source = helper.getLevel().damageSources().generic();
        Player fresh = wearing(helper, "battleworn");
        float whole = lost(fresh, source, 10.0F);

        Player battered = wearing(helper, "battleworn");
        worn(battered).setDamageValue(worn(battered).getMaxDamage() - 1);
        float nearlyBroken = lost(battered, source, 10.0F);

        helper.assertTrue(nearlyBroken < whole,
                "a nearly-broken piece must protect more, " + nearlyBroken + " vs " + whole);
        helper.succeed();
    }

    /** {@code stormrind}: lightning does nothing, everything else lands as usual. */
    @GameTest(template = "empty")
    public static void stormrindIgnoresLightningOnly(GameTestHelper helper) {
        Player player = wearing(helper, "stormrind");
        helper.assertTrue(lost(player, helper.getLevel().damageSources().lightningBolt(), BLOW) == 0.0F,
                "lightning must do nothing at all");
        helper.assertTrue(lost(player, helper.getLevel().damageSources().generic(), BLOW) > 0.0F,
                "and nothing else must be affected");
        helper.succeed();
    }

    /** {@code blastvent}: an explosion throws the wearer instead of hurting them. */
    @GameTest(template = "empty")
    public static void blastventTurnsExplosionsIntoKnockback(GameTestHelper helper) {
        Player player = wearing(helper, "blastvent");
        Zombie exploder = zombie(helper);
        // Away from the blast, so the push has a direction to go in (a zero vector normalizes to zero).
        BlockPos pos = helper.absolutePos(new BlockPos(0, 2, 0));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(lost(player, explosion(helper, exploder), 20.0F) == 0.0F,
                "the blast must do no damage");
        helper.assertTrue(player.getDeltaMovement().lengthSqr() > 0.0,
                "and must push the wearer instead, delta " + player.getDeltaMovement());
        exploder.discard();
        helper.succeed();
    }

    private ArmorTraitLibraryGameTests() {}
}
