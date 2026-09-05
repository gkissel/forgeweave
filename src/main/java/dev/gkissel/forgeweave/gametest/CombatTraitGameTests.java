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
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.BleedEffect;
import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.DamageRamp;
import dev.gkissel.forgeweave.combat.ForgeweaveMobEffects;
import dev.gkissel.forgeweave.combat.LacerateEffect;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.Trait;

/**
 * docs/SCOPE.md M3.2 issue #229's verification: one test per combat-seam trait, exercising the
 * behavior that distinguishes it. Like {@link MetalTraitGameTests}, tools are assembled by hand
 * ({@link #tool}) with the trait ids set directly -- the materials that grant these traits (cactus,
 * netherrack, magma slime, silver, lead, steel, ...) land in later M3.2 issues, so there is nothing
 * for a Tool Station to assemble from yet.
 *
 * <p>Offensive traits ride a <b>hatchet</b>: its own innate (sunder, bonus vs a blocking target) is
 * inert against mobs, so nothing pollutes a damage measurement -- unlike the pickaxe, whose pierce
 * subtracts an extra flat 1 health per landed hit. Defensive traits ride the pickaxe (merely held;
 * the attacker swings no Forgeweave tool, so pierce never runs) or the battlesign (blocking). Blows
 * are staged the same tick the stance opens, which is before vanilla's own 5-tick shield warm-up --
 * so vanilla shield-blocking never swallows a blow the defensive seams are being tested on.
 *
 * <p>Damage-math assertions drive {@link CombatSeams#seams} directly (public for exactly this,
 * per its javadoc); state and side effects (potion marks, fire, reflected health, DoT ticks) go
 * through real {@code LivingEntity#hurt} blows. The one exception is a <em>blocking</em> defensive
 * seam's own math (stiff, spiky, flammable): since issue #302 the battlesign carries its own
 * melee-block reduction and thorns reflect, which would otherwise compound with the trait under
 * test on the same blow, so those three drive the trait's {@link Trait#combatSeams} directly
 * instead ({@link #incomingHit}) rather than the whole stack's. Waits use {@code thenWaitUntil} --
 * no fixed-tick idles -- and all mobs are no-AI adults.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CombatTraitGameTests {

    /** Cactus, head -&gt; {@code forgeweave:prickly}: a random armor-bypassing follow-up hit, mean ~0.5. */
    @GameTest(template = "empty")
    public static void pricklyLandsARandomArmorBypassingFollowup(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("prickly")), 3.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Zombie target = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        float before = target.getHealth();
        int hits = 15;
        for (int i = 0; i < hits; i++) {
            target.invulnerableTime = 0;
            target.hurt(helper.getLevel().damageSources().playerAttack(player), 0.1F);
        }
        float lost = before - target.getHealth();
        // The 15 base blows account for at most 1.5; prickly's follow-ups average ~0.55 each, so
        // anything clearly above the base total proves armor-bypassing extra damage landed. The
        // chance all 15 gaussian rolls came out non-positive is ~1e-9.
        helper.assertTrue(target.isAlive(), "the target was not meant to die, or the numbers prove nothing");
        helper.assertTrue(lost > 2.0F,
                "expected prickly follow-up damage beyond the 1.5 the base blows carry, lost only " + lost);

        target.discard();
        helper.succeed();
    }

    /** Cactus -&gt; {@code forgeweave:spiky}: thorns reflect tool damage -- halved held, full blocking. */
    @GameTest(template = "empty")
    public static void spikyReflectsToolDamageHalvedHeldFullBlocking(GameTestHelper helper) {
        Player defender = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie attacker = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        // Merely held: half the tool's attack damage, past armor (so the zombie's 2 armor is moot).
        ItemStack pickaxe = tool(ForgeweaveItems.TOOL_PICKAXE.get(), List.of(traitId("spiky")), 3.0F);
        defender.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        float expectedHeld = ((ToolItem) pickaxe.getItem()).attackDamage(pickaxe) / 2.0F;
        float attackerBefore = attacker.getHealth();
        defender.hurt(helper.getLevel().damageSources().mobAttack(attacker), 2.0F);
        float reflectedHeld = attackerBefore - attacker.getHealth();
        helper.assertTrue(Math.abs(reflectedHeld - expectedHeld) < 0.001F,
                "expected a held spiky tool to reflect half its damage (" + expectedHeld + "), got " + reflectedHeld);

        // Blocking: the full amount. Driven through spiky's own seam directly (class javadoc) so the
        // battlesign's own melee-block reflect (issue #302) does not add onto the same blow.
        ItemStack battlesign = tool(ForgeweaveItems.TOOL_BATTLESIGN.get(), List.of(traitId("spiky")), 3.0F);
        float expectedBlocking = ((ToolItem) battlesign.getItem()).attackDamage(battlesign);
        attackerBefore = attacker.getHealth();
        incomingHit(helper, defender, battlesign, attacker, ForgeweaveTraits.SPIKY,
                helper.getLevel().damageSources().mobAttack(attacker), 2.0F);
        float reflectedBlocking = attackerBefore - attacker.getHealth();
        helper.assertTrue(Math.abs(reflectedBlocking - expectedBlocking) < 0.001F,
                "expected a blocking spiky tool to reflect its full damage (" + expectedBlocking + "), got "
                        + reflectedBlocking);

        attacker.discard();
        helper.succeed();
    }

    /**
     * Issue #460, upstream {@code TraitEvents#playerBlockOrHurtEvent}: the {@code onBlock} state is
     * {@code player.isActiveItemStackBlocking()} -- a question about the <em>player</em>, not about
     * the tool the trait rides. A raised vanilla shield in the off hand therefore makes a stiff tool
     * in the main hand block, which before this fix it did not (the shield is not a Forgeweave tool,
     * so the pass fell through to the merely-held branch).
     */
    @GameTest(template = "empty")
    public static void aRaisedShieldMakesAHeldToolBlock(GameTestHelper helper) {
        Player defender = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie attacker = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));
        ItemStack pickaxe = tool(ForgeweaveItems.TOOL_PICKAXE.get(), List.of(traitId("stiff")), 3.0F);
        defender.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);

        // Merely held, no shield: stiff does nothing.
        float taken = hurtFor(helper, defender, attacker, 10.0F);
        helper.assertTrue(Math.abs(taken - 10.0F) < 0.05F,
                "a merely-held stiff tool must not shave the blow, took " + taken);

        // Off-hand shield raised: stiff's -1 applies. The blow is staged the same tick the stance
        // opens, before vanilla's own 5-tick warm-up, so vanilla shield-blocking never swallows it.
        defender.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        defender.startUsingItem(InteractionHand.OFF_HAND);
        helper.assertTrue(defender.isUsingItem(), "the shield must actually be raised");
        taken = hurtFor(helper, defender, attacker, 10.0F);
        helper.assertTrue(Math.abs(taken - 9.0F) < 0.05F,
                "expected a raised shield to put the main-hand stiff tool in its blocking state "
                        + "(10 -> 9), took " + taken);

        defender.stopUsingItem();
        attacker.discard();
        helper.succeed();
    }

    /**
     * Issue #460: upstream collects every tool in {@code getHeldEquipment()}, so an off-hand tool's
     * defensive traits run too. Before this fix the pass looked at the main hand only.
     */
    @GameTest(template = "empty")
    public static void offHandToolTraitsRunOnAnIncomingBlow(GameTestHelper helper) {
        Player defender = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie attacker = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));
        ItemStack pickaxe = tool(ForgeweaveItems.TOOL_PICKAXE.get(), List.of(traitId("spiky")), 3.0F);
        defender.setItemInHand(InteractionHand.OFF_HAND, pickaxe);
        float expected = ((ToolItem) pickaxe.getItem()).attackDamage(pickaxe) / 2.0F;

        float attackerBefore = attacker.getHealth();
        hurtFor(helper, defender, attacker, 2.0F);
        float reflected = attackerBefore - attacker.getHealth();

        helper.assertTrue(Math.abs(reflected - expected) < 0.001F,
                "expected an off-hand spiky tool to reflect half its damage (" + expected + "), got "
                        + reflected);

        attacker.discard();
        helper.succeed();
    }

    /**
     * Issue #460: upstream's block gate is the BLOCK use animation, and the longsword's is BOW
     * ({@code LongSword#getItemUseAction}). Charging its leap is therefore not a block -- before this
     * fix any active use of any Forgeweave tool counted as one, so a charging longsword got stiff's
     * shave, spiky's full-strength thorns and flammable's fire absorb for free.
     */
    @GameTest(template = "empty")
    public static void chargingTheLongswordIsNotABlock(GameTestHelper helper) {
        Player defender = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie attacker = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));
        ItemStack longsword = tool(ForgeweaveItems.TOOL_LONGSWORD.get(), List.of(traitId("stiff")), 3.0F);
        defender.setItemInHand(InteractionHand.MAIN_HAND, longsword);
        defender.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(defender.isUsingItem(), "the leap charge must actually be open");

        float taken = hurtFor(helper, defender, attacker, 10.0F);
        helper.assertTrue(Math.abs(taken - 10.0F) < 0.05F,
                "charging the longsword must not count as a block, so stiff must not shave the blow "
                        + "(expected 10), took " + taken);

        defender.stopUsingItem();
        attacker.discard();
        helper.succeed();
    }

    /** Netherrack, head -&gt; {@code forgeweave:hellish}: +4 damage, but only against non-fire-immune targets. */
    @GameTest(template = "empty")
    public static void hellishAddsFlatDamageAgainstNonFireImmune(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("hellish")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));
        Blaze blaze = noAi(helper.spawn(EntityType.BLAZE, new BlockPos(3, 2, 3)));

        float vsPig = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(vsPig - 5.0F) < 0.001F,
                "expected +4 against a pig (1 -> 5), got " + vsPig);
        float vsBlaze = preHit(helper, player, hatchet, blaze, 1.0F);
        helper.assertTrue(Math.abs(vsBlaze - 1.0F) < 0.001F,
                "hellish must not touch a fire-immune blaze, got " + vsBlaze);

        pig.discard();
        blaze.discard();
        helper.succeed();
    }

    /** Magma slime, head -&gt; {@code forgeweave:superheat}: +35% of the blow against burning targets. */
    @GameTest(template = "empty")
    public static void superheatScalesDamageAgainstBurningTargets(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("superheat")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        float unlit = preHit(helper, player, hatchet, pig, 2.0F);
        helper.assertTrue(Math.abs(unlit - 2.0F) < 0.001F,
                "superheat must not touch an unlit target, got " + unlit);

        pig.igniteForSeconds(5);
        float burning = preHit(helper, player, hatchet, pig, 2.0F);
        helper.assertTrue(Math.abs(burning - 2.7F) < 0.001F,
                "expected +35% against a burning target (2 -> 2.7), got " + burning);

        pig.clearFire();
        pig.discard();
        helper.succeed();
    }

    /** Silver -&gt; {@code forgeweave:holy}: +5 against undead, plus Weakness I for 2.5s on a landed hit. */
    @GameTest(template = "empty")
    public static void holyAddsDamageAndWeaknessAgainstUndead(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("holy")), 3.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Zombie zombie = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(3, 2, 3)));

        float vsZombie = preHit(helper, player, hatchet, zombie, 1.0F);
        helper.assertTrue(Math.abs(vsZombie - 6.0F) < 0.001F,
                "expected +5 against undead (1 -> 6), got " + vsZombie);
        float vsPig = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(vsPig - 1.0F) < 0.001F, "holy must not touch the living, got " + vsPig);

        zombie.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        MobEffectInstance weakness = zombie.getEffect(MobEffects.WEAKNESS);
        helper.assertTrue(weakness != null && weakness.getAmplifier() == 0,
                "a landed holy hit must leave Weakness I on an undead target");
        pig.invulnerableTime = 0;
        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        helper.assertTrue(pig.getEffect(MobEffects.WEAKNESS) == null, "no weakness on the living");

        zombie.discard();
        pig.discard();
        helper.succeed();
    }

    /** Lead -&gt; {@code forgeweave:poisonous}: Poison I for ~5 seconds on every landed hit. */
    @GameTest(template = "empty")
    public static void poisonousPoisonsOnHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("poisonous")), 3.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        MobEffectInstance poison = pig.getEffect(MobEffects.POISON);
        helper.assertTrue(poison != null && poison.getAmplifier() == 0,
                "a landed poisonous hit must leave Poison I on the target");
        helper.assertTrue(poison.getDuration() <= 101 && poison.getDuration() > 90,
                "expected upstream's 101-tick duration, got " + poison.getDuration());

        pig.discard();
        helper.succeed();
    }

    /** Lead -&gt; {@code forgeweave:heavy}: a flat +1 knockback-resistance attribute while held. */
    @GameTest(template = "empty")
    public static void heavyGrantsKnockbackResistanceWhileHeld(GameTestHelper helper) {
        ItemStack heavy = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("heavy")), 3.0F);

        Double resistance = null;
        for (ItemAttributeModifiers.Entry entry : heavy.getAttributeModifiers().modifiers()) {
            if (entry.attribute().is(Attributes.KNOCKBACK_RESISTANCE.getKey())
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                resistance = entry.modifier().amount();
            }
        }
        helper.assertTrue(resistance != null && Math.abs(resistance - 1.0) < 0.001,
                "expected a +1 knockback resistance modifier, got " + resistance);

        ItemStack plain = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(), 3.0F);
        for (ItemAttributeModifiers.Entry entry : plain.getAttributeModifiers().modifiers()) {
            helper.assertFalse(entry.attribute().is(Attributes.KNOCKBACK_RESISTANCE.getKey()),
                    "a tool without heavy must not carry knockback resistance");
        }
        helper.succeed();
    }

    /** Steel -&gt; {@code forgeweave:stiff}: blocking shaves a flat 1 off a blow, but never below 1. */
    @GameTest(template = "empty")
    public static void stiffShavesBlockedDamageWithAFloor(GameTestHelper helper) {
        Player defender = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie attacker = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));
        ItemStack battlesign = tool(ForgeweaveItems.TOOL_BATTLESIGN.get(), List.of(traitId("stiff")), 3.0F);
        defender.setItemInHand(InteractionHand.MAIN_HAND, battlesign);

        // Merely held: stiff does nothing.
        float before = defender.getHealth();
        defender.hurt(helper.getLevel().damageSources().mobAttack(attacker), 5.0F);
        float heldLoss = before - defender.getHealth();
        helper.assertTrue(Math.abs(heldLoss - 5.0F) < 0.001F,
                "stiff must not reduce damage while merely held, lost " + heldLoss);

        // Blocking: -1. Driven through stiff's own seam directly (class javadoc) so the battlesign's
        // own melee-block reduction (issue #302) does not compound with the number under test here.
        DamageSource mobAttack = helper.getLevel().damageSources().mobAttack(attacker);
        float blocked = incomingHit(helper, defender, battlesign, attacker, ForgeweaveTraits.STIFF, mobAttack, 5.0F);
        helper.assertTrue(Math.abs(blocked - 4.0F) < 0.001F,
                "expected a blocked 5-damage blow to land for 4, got " + blocked);

        // The floor: a small blow still lands for 1, never 0.
        float floored = incomingHit(helper, defender, battlesign, attacker, ForgeweaveTraits.STIFF, mobAttack, 1.5F);
        helper.assertTrue(Math.abs(floored - 1.0F) < 0.001F,
                "expected upstream's 1-damage floor, got " + floored);

        attacker.discard();
        helper.succeed();
    }

    /** Steel, head -&gt; {@code forgeweave:sharp}: an armor-ignoring 1/3-damage bleed every 15 ticks. */
    @GameTest(template = "empty")
    public static void sharpBleedsArmorIgnoringOverTime(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("sharp")), 3.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        helper.assertTrue(pig.hasEffect(ForgeweaveMobEffects.BLEED), "a landed sharp hit must leave the bleed");
        float afterHit = pig.getHealth();

        // The DoT is driven through the effect's own tick contract rather than by waiting on the
        // level to tick the pig: a GameTest plot far from spawn is not reliably entity-ticking, so a
        // wait on the pig's effects can outlive any timeout (issue #212's diagnosis; this test's CI
        // flake read "health still 9.0"). The hit above still applies the effect through the real
        // trait seam -- only the cadence is stepped by hand.
        BleedEffect effect = (BleedEffect) ForgeweaveMobEffects.BLEED.value();
        int fires = 0;
        for (int duration = BleedEffect.DURATION_TICKS; duration >= 1; duration--) {
            if (effect.shouldApplyEffectTickThisTick(duration, 0)) {
                effect.applyEffectTick(pig, 0);
                fires++;
            }
        }
        helper.assertValueEqual(fires, 8, "a 121-tick application fires every 15 ticks");
        float lost = afterHit - pig.getHealth();
        helper.assertTrue(Math.abs(lost - fires * BleedEffect.DAMAGE_PER_TICK) < 0.01F,
                "eight 1/3-damage ticks must land in full despite invulnerability windows, got " + lost);
        pig.discard();
        helper.succeed();
    }

    /**
     * Steel, head -&gt; {@code forgeweave:sharp}: a bleed tick must not knock the target back (issue
     * #436, upstream {@code Modifier#attackEntitySecondary}'s {@code noKnockback} flag -- a transient
     * {@code KNOCKBACK_RESISTANCE} attribute modifier, not a damage-type tag; {@link BleedEffect}
     * javadoc). The attacker sits off to the side so a real push would show up as nonzero horizontal
     * velocity.
     */
    @GameTest(template = "empty")
    public static void sharpBleedDealsNoKnockback(GameTestHelper helper) {
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));
        Zombie attacker = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(6, 2, 2)));
        pig.setLastHurtByMob(attacker);

        BleedEffect effect = (BleedEffect) ForgeweaveMobEffects.BLEED.value();
        effect.applyEffectTick(pig, 0);

        helper.assertTrue(pig.getDeltaMovement().horizontalDistanceSqr() < 1.0E-6,
                "a bleed tick must not apply knockback, got " + pig.getDeltaMovement());
        pig.discard();
        attacker.discard();
        helper.succeed();
    }

    /**
     * Steel, head -&gt; {@code forgeweave:sharp}: the bleed credits the attacker for a later kill
     * (issue #297 parity fix; upstream {@code TraitSharp#afterHit}'s {@code setLastAttackedEntity}).
     * Drives the trait's seam directly ({@link #onHit}) rather than through a real
     * {@code LivingEntity#hurt}, since vanilla's own hit-crediting would otherwise mask the bug: a
     * real landed blow already remembers its own attacker regardless of whether sharp's bleed does.
     */
    @GameTest(template = "empty")
    public static void sharpBleedCreditsTheAttackerForAKill(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("sharp")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));
        helper.assertTrue(pig.getLastHurtByMob() == null, "a fresh pig starts with no remembered attacker");

        onHit(helper, attacker, hatchet, pig);
        helper.assertTrue(pig.hasEffect(ForgeweaveMobEffects.BLEED), "sharp's seam must apply the bleed");
        helper.assertTrue(pig.getLastHurtByMob() == attacker,
                "sharp must remember the attacker so the bleed's later kill credits them, got "
                        + pig.getLastHurtByMob());

        pig.discard();
        helper.succeed();
    }

    /** Bone, head (retrofit) -&gt; {@code forgeweave:splintering}: +0.3 per landed hit, capping at +1.8. */
    @GameTest(template = "empty")
    public static void splinteringStacksBonusDamagePerHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("splintering")), 3.0F);
        Zombie target = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        float unmarked = preHit(helper, player, hatchet, target, 1.0F);
        helper.assertTrue(Math.abs(unmarked - 1.0F) < 0.001F,
                "the first hit of a fight carries no splinter bonus, got " + unmarked);

        onHit(helper, player, hatchet, target); // the first hit leaves the first mark
        float oneMark = preHit(helper, player, hatchet, target, 1.0F);
        helper.assertTrue(Math.abs(oneMark - 1.3F) < 0.001F, "expected +0.3 after one mark, got " + oneMark);

        for (int i = 0; i < 10; i++) {
            onHit(helper, player, hatchet, target);
        }
        MobEffectInstance mark = target.getEffect(ForgeweaveMobEffects.SPLINTER);
        helper.assertTrue(mark != null && mark.getAmplifier() == 5,
                "the mark must cap at amplifier 5 (6 stacks), got " + (mark == null ? "none" : mark.getAmplifier()));
        float capped = preHit(helper, player, hatchet, target, 1.0F);
        helper.assertTrue(Math.abs(capped - 2.8F) < 0.001F, "expected the +1.8 cap, got " + capped);

        target.discard();
        helper.succeed();
    }

    /** Magma slime -&gt; {@code forgeweave:flammable}: attackers catch fire; blocking eats fire damage. */
    @GameTest(template = "empty")
    public static void flammableIgnitesAttackersAndAbsorbsFireWhileBlocking(GameTestHelper helper) {
        Player defender = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie attacker = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        // Merely held: whoever lands a blow is set on fire (upstream's onPlayerHurt half).
        ItemStack pickaxe = tool(ForgeweaveItems.TOOL_PICKAXE.get(), List.of(traitId("flammable")), 3.0F);
        defender.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        defender.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1.0F);
        helper.assertTrue(attacker.getRemainingFireTicks() > 0, "hitting a flammable holder must ignite the attacker");
        attacker.discard();

        // Blocking: fire damage is negated outright, for 3 durability (upstream's onBlock half).
        // Driven through flammable's own seam directly (class javadoc) so the battlesign's own
        // melee-block reduction (issue #302) does not also spend durability on the same blow.
        ItemStack battlesign = tool(ForgeweaveItems.TOOL_BATTLESIGN.get(), List.of(traitId("flammable")), 3.0F);
        float result = incomingHit(helper, defender, battlesign, null, ForgeweaveTraits.FLAMMABLE,
                helper.getLevel().damageSources().inFire(), 2.0F);
        helper.assertTrue(result <= 0.0F, "a blocked fire hit must be cancelled outright, got " + result);
        helper.assertTrue(battlesign.getDamageValue() == 3,
                "the absorb costs 3 durability, tool at " + battlesign.getDamageValue());

        helper.succeed();
    }

    /** Endstone -&gt; {@code forgeweave:enderference}: hits mark the target; a marked entity cannot teleport. */
    @GameTest(template = "empty")
    public static void enderferenceMarksTargetsAndCancelsTeleports(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("enderference")), 3.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        EnderMan marked = noAi(helper.spawn(EntityType.ENDERMAN, new BlockPos(2, 2, 2)));
        EnderMan unmarked = noAi(helper.spawn(EntityType.ENDERMAN, new BlockPos(4, 2, 4)));

        marked.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        MobEffectInstance mark = marked.getEffect(ForgeweaveMobEffects.ENDERFERENCE);
        helper.assertTrue(mark != null, "a landed enderference hit must leave the mark");
        helper.assertTrue(mark.getDuration() <= 100 && mark.getDuration() > 90,
                "expected upstream's 100-tick mark, got " + mark.getDuration());

        // The mark is what the teleport listener reads -- post the real event through the real bus.
        EntityTeleportEvent.EnderEntity blocked =
                new EntityTeleportEvent.EnderEntity(marked, marked.getX() + 8, marked.getY(), marked.getZ());
        NeoForge.EVENT_BUS.post(blocked);
        helper.assertTrue(blocked.isCanceled(), "a marked entity's teleport must be cancelled");

        EntityTeleportEvent.EnderEntity free =
                new EntityTeleportEvent.EnderEntity(unmarked, unmarked.getX() + 8, unmarked.getY(), unmarked.getZ());
        NeoForge.EVENT_BUS.post(free);
        helper.assertFalse(free.isCanceled(), "an unmarked entity's teleport must go through");

        marked.discard();
        unmarked.discard();
        helper.succeed();
    }

    /** Nahuatl -&gt; {@code forgeweave:lacerating}: the scimitar's stacking bleed, as a material trait. */
    @GameTest(template = "empty")
    public static void laceratingAppliesTheScimitarBleed(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("lacerating")), 3.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        helper.assertTrue(pig.hasEffect(ForgeweaveMobEffects.LACERATE),
                "a landed lacerating hit must leave the scimitar's bleed");
        float afterHit = pig.getHealth();

        // The first bleed tick is driven through the effect's own contract rather than by waiting on
        // the level to tick the pig -- a GameTest plot far from spawn is not reliably entity-ticking
        // (issue #212), which is what this test's CI flake ("health still 9.0") was. The hit above
        // still applies the effect through the real trait seam; the cadence and 4-second total are
        // WeaponInnateGameTests#lacerateBleedsOneDamagePerSecondForFourSeconds's to prove.
        pig.invulnerableTime = 0; // the first tick lands a full second after the blow (LacerateEffect javadoc)
        ((LacerateEffect) ForgeweaveMobEffects.LACERATE.value()).applyEffectTick(pig, 0);
        helper.assertTrue(pig.getHealth() <= afterHit - 1.0F,
                "the bleed's tick must cost 1 damage, health still " + pig.getHealth());
        pig.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ M6 damage-scaling trait
    // behavior library (issue #827, ADR-0004): one test per registered instance, same shape as the
    // #229 batch above -- ids set directly on a hatchet (no material grants these yet).

    /** {@code forgeweave:pristine}: bonus damage rises with how undamaged the weapon still is. */
    @GameTest(template = "empty")
    public static void pristineScalesDamageWithRemainingDurability(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("pristine")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        float atFullDurability = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(atFullDurability - 4.0F) < 0.001F,
                "expected the full +3 at full durability (1 -> 4), got " + atFullDurability);

        hatchet.setDamageValue(hatchet.getMaxDamage() / 2);
        float atHalfDurability = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(atHalfDurability - 2.5F) < 0.001F,
                "expected half the bonus at half durability (1 -> 2.5), got " + atHalfDurability);

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:vigorous}: bonus damage rises with the wielder's own health. */
    @GameTest(template = "empty")
    public static void vigorousScalesDamageWithWielderHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("vigorous")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        float atFullHealth = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(atFullHealth - 3.0F) < 0.001F,
                "expected the full +2 at full health (1 -> 3), got " + atFullHealth);

        player.setHealth(player.getMaxHealth() / 2.0F);
        float atHalfHealth = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(atHalfHealth - 2.0F) < 0.001F,
                "expected half the bonus at half health (1 -> 2), got " + atHalfHealth);

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:predatory}: bonus damage rises with how much health the target already lost. */
    @GameTest(template = "empty")
    public static void predatoryScalesDamageWithTheTargetsMissingHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("predatory")), 3.0F);
        Zombie target = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        float atFullHealth = preHit(helper, player, hatchet, target, 1.0F);
        helper.assertTrue(Math.abs(atFullHealth - 1.0F) < 0.001F,
                "predatory must add nothing against an untouched target, got " + atFullHealth);

        target.setHealth(5.0F);
        float wounded = preHit(helper, player, hatchet, target, 1.0F);
        helper.assertTrue(Math.abs(wounded - 3.25F) < 0.001F,
                "expected +2.25 against a target missing 15 health (1 -> 3.25), got " + wounded);

        target.discard();
        helper.succeed();
    }

    /** {@code forgeweave:colossal}: bonus damage rises with the target's own max health. */
    @GameTest(template = "empty")
    public static void colossalScalesDamageWithTheTargetsMaxHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("colossal")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));
        Zombie zombie = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3)));

        float vsPig = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(vsPig - 1.5F) < 0.001F,
                "expected +0.5 against a 10-max-health pig (1 -> 1.5), got " + vsPig);
        float vsZombie = preHit(helper, player, hatchet, zombie, 1.0F);
        helper.assertTrue(Math.abs(vsZombie - 2.0F) < 0.001F,
                "expected +1.0 against a 20-max-health zombie (1 -> 2), got " + vsZombie);

        pig.discard();
        zombie.discard();
        helper.succeed();
    }

    /** {@code forgeweave:kinetic}: bonus damage rises with the wielder's own current motion. */
    @GameTest(template = "empty")
    public static void kineticScalesDamageWithTheWieldersMotion(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("kinetic")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        player.setDeltaMovement(Vec3.ZERO);
        float stationary = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(stationary - 1.0F) < 0.001F,
                "kinetic must add nothing while the wielder is not moving, got " + stationary);

        player.setDeltaMovement(new Vec3(1.0, 0.0, 0.0));
        float moving = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(moving - 4.0F) < 0.001F,
                "expected +3 at 1 block/tick of motion (1 -> 4), got " + moving);

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:dominant}: bonus damage against a target already weaker than the wielder. */
    @GameTest(template = "empty")
    public static void dominantAddsDamageAgainstATargetWeakerThanTheWielder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("dominant")), 3.0F);
        Zombie target = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        float evenHealth = preHit(helper, player, hatchet, target, 1.0F);
        helper.assertTrue(Math.abs(evenHealth - 1.0F) < 0.001F,
                "dominant must not trigger against a target at the wielder's own health, got " + evenHealth);

        target.setHealth(target.getHealth() - 10.0F);
        float weakerTarget = preHit(helper, player, hatchet, target, 1.0F);
        helper.assertTrue(Math.abs(weakerTarget - 3.0F) < 0.001F,
                "expected +2 against a target below the wielder's health (1 -> 3), got " + weakerTarget);

        target.discard();
        helper.succeed();
    }

    /** {@code forgeweave:armor_breaker}: bonus damage against an armored target. */
    @GameTest(template = "empty")
    public static void armorBreakerAddsDamageAgainstAnArmoredTarget(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("armor_breaker")), 3.0F);
        // A pig, not a zombie: zombies carry a nonzero base armor value even with no equipped piece,
        // which would make the "unarmored" half of this test trigger the trait it's meant to rule out.
        Pig target = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        float unarmored = preHit(helper, player, hatchet, target, 1.0F);
        helper.assertTrue(Math.abs(unarmored - 1.0F) < 0.001F,
                "armor_breaker must not trigger against an unarmored target, got " + unarmored);

        // ArmorGameTests#wearing: a tick is what makes LivingEntity#detectEquipmentUpdates apply the
        // piece's attribute modifiers -- getArmorValue() would still read 0 right after setItemSlot.
        target.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
        target.tick();
        float armored = preHit(helper, player, hatchet, target, 1.0F);
        helper.assertTrue(Math.abs(armored - 3.0F) < 0.001F,
                "expected +2 against an armored target (1 -> 3), got " + armored);

        target.discard();
        helper.succeed();
    }

    /** {@code forgeweave:opportunist}: bonus damage against a target already carrying a harmful effect. */
    @GameTest(template = "empty")
    public static void opportunistAddsDamageAgainstADebuffedTarget(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("opportunist")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        float clean = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(clean - 1.0F) < 0.001F,
                "opportunist must not trigger against a target with no harmful effect, got " + clean);

        pig.addEffect(new MobEffectInstance(MobEffects.POISON, 100));
        float debuffed = preHit(helper, player, hatchet, pig, 1.0F);
        helper.assertTrue(Math.abs(debuffed - 3.0F) < 0.001F,
                "expected +2 against a poisoned target (1 -> 3), got " + debuffed);

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:surging}: extra damage on a fully-charged swing only. */
    @GameTest(template = "empty")
    public static void surgingAddsDamageOnlyOnAFullyChargedSwing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("surging")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        float partial = preHitCharged(helper, player, hatchet, pig, 1.0F, 0.5F);
        helper.assertTrue(Math.abs(partial - 1.0F) < 0.001F,
                "surging must not add damage on a partial-charge swing, got " + partial);

        float charged = preHitCharged(helper, player, hatchet, pig, 1.0F, 1.0F);
        helper.assertTrue(Math.abs(charged - 2.5F) < 0.001F,
                "expected +1.5 on a fully-charged swing (1 -> 2.5), got " + charged);

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:surging2}/{@code surging3}: the same shape, scaled by level. */
    @GameTest(template = "empty")
    public static void surging2And3ScaleTheChargedBonusByLevel(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack surging2 = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("surging2")), 3.0F);
        ItemStack surging3 = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("surging3")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        float levelTwo = preHitCharged(helper, player, surging2, pig, 1.0F, 1.0F);
        helper.assertTrue(Math.abs(levelTwo - 4.0F) < 0.001F,
                "expected +3.0 at level II (1 -> 4), got " + levelTwo);
        float levelThree = preHitCharged(helper, player, surging3, pig, 1.0F, 1.0F);
        helper.assertTrue(Math.abs(levelThree - 5.5F) < 0.001F,
                "expected +4.5 at level III (1 -> 5.5), got " + levelThree);

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:ruthless}: extra damage only on a critical hit, scaled by the crit multiplier. */
    @GameTest(template = "empty")
    public static void ruthlessAddsExtraDamageOnlyOnACriticalHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("ruthless")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        float notCrit = preHitCrit(helper, player, hatchet, pig, 3.0F, 1.0F);
        helper.assertTrue(Math.abs(notCrit - 3.0F) < 0.001F,
                "ruthless must not touch a non-critical blow, got " + notCrit);

        float crit = preHitCrit(helper, player, hatchet, pig, 3.0F, 1.5F);
        helper.assertTrue(Math.abs(crit - 4.0F) < 0.001F,
                "expected +1.0 on a vanilla 1.5x crit (3 -> 4), got " + crit);

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:escalating}: the ramp only builds from consecutive fully-charged landed hits. */
    @GameTest(template = "empty")
    public static void escalatingBuildsOnlyFromConsecutiveFullyChargedHits(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("escalating")), 3.0F);
        Zombie target = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        onHitCharged(helper, player, hatchet, target, 0.5F); // not fully charged: must not build a stack
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(DamageRamp.ESCALATING.liveStacks(hatchet, gameTime) == 0,
                "a non-full-charge hit must not build the escalating ramp, got "
                        + DamageRamp.ESCALATING.liveStacks(hatchet, gameTime));

        onHitCharged(helper, player, hatchet, target, 1.0F);
        onHitCharged(helper, player, hatchet, target, 1.0F);
        gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(DamageRamp.ESCALATING.liveStacks(hatchet, gameTime) == 2,
                "two consecutive fully-charged hits should leave two stacks, got "
                        + DamageRamp.ESCALATING.liveStacks(hatchet, gameTime));

        float bonused = preHitCharged(helper, player, hatchet, target, 1.0F, 1.0F);
        helper.assertTrue(Math.abs(bonused - 1.3F) < 0.001F,
                "expected +0.30 from two stacks (1 -> 1.3), got " + bonused);

        target.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ M6 on-hit effect trait
    // behavior library (issue #828, ADR-0004): one test per registered instance, same shape as the
    // #827 batch above -- ids set directly on a hatchet (no material grants these yet).

    /** {@code forgeweave:blighted}: repeated hits stack Wither up to III instead of only refreshing. */
    @GameTest(template = "empty")
    public static void blightedStacksWitherUpToIIIOnRepeatHits(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("blighted")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        onHit(helper, player, hatchet, pig);
        MobEffectInstance first = pig.getEffect(MobEffects.WITHER);
        helper.assertTrue(first != null && first.getAmplifier() == 0,
                "the first blighted hit must leave Wither I, got " + (first == null ? "none" : first.getAmplifier()));

        onHit(helper, player, hatchet, pig);
        onHit(helper, player, hatchet, pig);
        MobEffectInstance capped = pig.getEffect(MobEffects.WITHER);
        helper.assertTrue(capped != null && capped.getAmplifier() == 2,
                "three hits must cap at Wither III (amplifier 2), got "
                        + (capped == null ? "none" : capped.getAmplifier()));

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:enfeebling}: Weakness I on every landed hit. */
    @GameTest(template = "empty")
    public static void enfeeblingWeakensOnHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("enfeebling")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        onHit(helper, player, hatchet, pig);
        MobEffectInstance weakness = pig.getEffect(MobEffects.WEAKNESS);
        helper.assertTrue(weakness != null && weakness.getAmplifier() == 0,
                "an enfeebling hit must leave Weakness I, got " + (weakness == null ? "none" : weakness.getAmplifier()));

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:shackling}: a brief, deep Slowness on every landed hit. */
    @GameTest(template = "empty")
    public static void shacklingRootsBrieflyOnHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("shackling")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        onHit(helper, player, hatchet, pig);
        MobEffectInstance slow = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slow != null && slow.getAmplifier() == 3,
                "a shackling hit must leave Slowness IV, got " + (slow == null ? "none" : slow.getAmplifier()));
        helper.assertTrue(slow.getDuration() <= 20 && slow.getDuration() > 10,
                "expected a short ~1-second root, got " + slow.getDuration());

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:revealing}: a landed hit leaves the target Glowing. */
    @GameTest(template = "empty")
    public static void revealingMarksTheTargetWithGlowOnHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("revealing")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        onHit(helper, player, hatchet, pig);
        helper.assertTrue(pig.hasEffect(MobEffects.GLOWING), "a revealing hit must leave the target glowing");

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:merciful}: heals whatever it hits -- a deliberately unhelpful novelty. */
    @GameTest(template = "empty")
    public static void mercifulRegeneratesTheTargetOnHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("merciful")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        onHit(helper, player, hatchet, pig);
        MobEffectInstance regen = pig.getEffect(MobEffects.REGENERATION);
        helper.assertTrue(regen != null && regen.getAmplifier() == 0,
                "a merciful hit must regenerate the target it just struck, got "
                        + (regen == null ? "none" : regen.getAmplifier()));

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:quickstep}: a fully-charged hit grants the wielder Speed II, briefly. */
    @GameTest(template = "empty")
    public static void quickstepGrantsSpeedOnlyOnAFullyChargedHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("quickstep")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        onHitCharged(helper, player, hatchet, pig, 0.5F);
        helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SPEED),
                "a non-full-charge hit must not grant quickstep's speed burst");

        onHitCharged(helper, player, hatchet, pig, 1.0F);
        MobEffectInstance speed = player.getEffect(MobEffects.MOVEMENT_SPEED);
        helper.assertTrue(speed != null && speed.getAmplifier() == 1,
                "a fully-charged quickstep hit must grant Speed II, got " + (speed == null ? "none" : speed.getAmplifier()));

        pig.discard();
        helper.succeed();
    }

    /**
     * {@code forgeweave:unraveling3} (and, deterministically, every level's charge gate):
     * non-full-charge hits never strip, and a fully-charged hit strips a beneficial effect with
     * enough attempts that only astronomical bad luck fails ({@link #pricklyLandsARandomArmorBypassingFollowup}'s
     * same statistical shape).
     */
    @GameTest(template = "empty")
    public static void unravelingStripsABeneficialEffectOnlyOnAFullyChargedHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet3 = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("unraveling3")), 3.0F);
        // A pig, not a zombie: undead entities are tagged minecraft:ignores_poison_and_regen and
        // silently refuse Regeneration outright (LivingEntity#canBeAffected), which would make this
        // test measure vanilla immunity rather than unraveling's own strip.
        Pig target = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));
        onHitCharged(helper, player, hatchet3, target, 0.5F);
        helper.assertTrue(target.hasEffect(MobEffects.REGENERATION),
                "the FULL_CHARGE gate must reject a non-full-charge swing before any chance is rolled");

        boolean stripped = false;
        for (int attempt = 0; attempt < 40 && !stripped; attempt++) {
            target.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200));
            onHitCharged(helper, player, hatchet3, target, 1.0F);
            stripped = !target.hasEffect(MobEffects.REGENERATION);
        }
        // unraveling3's 75% chance failing 40 times running is on the order of 1e-5.
        helper.assertTrue(stripped, "expected unraveling3 to eventually strip the buff at 75% a swing");

        target.discard();
        helper.succeed();
    }

    /** {@code forgeweave:grievous}: marks the target so incoming heals are shaved for a few seconds. */
    @GameTest(template = "empty")
    public static void grievousShavesHealingOnTheMarkedTarget(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("grievous")), 3.0F);
        Pig marked = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));
        Pig unmarked = noAi(helper.spawn(EntityType.PIG, new BlockPos(4, 2, 4)));

        onHit(helper, player, hatchet, marked);
        helper.assertTrue(marked.hasEffect(ForgeweaveMobEffects.REDUCED_HEALING), "a grievous hit must leave the mark");

        LivingHealEvent shaved = new LivingHealEvent(marked, 4.0F);
        NeoForge.EVENT_BUS.post(shaved);
        helper.assertTrue(Math.abs(shaved.getAmount() - 2.0F) < 0.001F,
                "expected a marked heal of 4 shaved by 50% to 2, got " + shaved.getAmount());

        LivingHealEvent untouched = new LivingHealEvent(unmarked, 4.0F);
        NeoForge.EVENT_BUS.post(untouched);
        helper.assertTrue(Math.abs(untouched.getAmount() - 4.0F) < 0.001F,
                "an unmarked heal must be untouched, got " + untouched.getAmount());

        marked.discard();
        unmarked.discard();
        helper.succeed();
    }

    /** {@code forgeweave:harrying}: shaves 10 ticks off the target's post-hit invulnerability window. */
    @GameTest(template = "empty")
    public static void harryingShortensTheTargetsInvulnerabilityWindow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("harrying")), 3.0F);
        Pig pig = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));

        pig.hurt(helper.getLevel().damageSources().generic(), 1.0F);
        int before = pig.invulnerableTime;
        helper.assertTrue(before > 0, "a landed blow must have opened an invulnerability window to shorten");

        onHit(helper, player, hatchet, pig);
        int after = pig.invulnerableTime;
        // The proposed magnitude (ShortenInvulnerability's javadoc): 10 ticks, half vanilla's default.
        helper.assertTrue(before - after == 10,
                "expected harrying to shave exactly 10 ticks (" + before + " -> " + after + ")");

        pig.discard();
        helper.succeed();
    }

    /** {@code forgeweave:leeching}: heals the wielder for a share of the damage just dealt. */
    @GameTest(template = "empty")
    public static void leechingHealsTheWielderByAFractionOfDamageDealt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("leeching")), 3.0F);
        Zombie target = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        player.setHealth(player.getMaxHealth() - 5.0F);
        float before = player.getHealth();
        onHit(helper, player, hatchet, target); // 1.0 damage dealt, per the onHit helper
        float healed = player.getHealth() - before;
        // The proposed magnitude (leeching's javadoc): 15% of 1.0 damage, well under the 4.0 cap.
        helper.assertTrue(Math.abs(healed - 0.15F) < 0.01F, "expected a 0.15 lifesteal heal, got " + healed);

        target.discard();
        helper.succeed();
    }

    /** {@code forgeweave:arcing}: a fully-charged hit has a chance to arc to a nearby enemy. */
    @GameTest(template = "empty")
    public static void arcingReachesANearbyEnemyOnAFullyChargedHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("arcing")), 3.0F);
        Zombie primary = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));
        Zombie nearby = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 2)));

        boolean arced = false;
        for (int attempt = 0; attempt < 40 && !arced; attempt++) {
            float before = nearby.getHealth();
            nearby.invulnerableTime = 0;
            onHitCharged(helper, player, hatchet, primary, 1.0F);
            arced = nearby.getHealth() < before;
        }
        // arcing's proposed 35% chance failing 40 times running is on the order of 1e-7.
        helper.assertTrue(arced, "expected arcing to eventually reach the nearby zombie at 35% a swing");

        primary.discard();
        nearby.discard();
        helper.succeed();
    }

    /** {@code forgeweave:stormcaller}: strikes lightning on the target, but only at full wielder health. */
    @GameTest(template = "empty")
    public static void stormcallerStrikesLightningOnlyAtFullWielderHealth(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId("stormcaller")), 3.0F);
        Pig target = noAi(helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2)));
        AABB nearTarget = target.getBoundingBox().inflate(2.0);

        player.setHealth(player.getMaxHealth() - 5.0F);
        onHit(helper, player, hatchet, target);
        helper.assertTrue(helper.getLevel().getEntitiesOfClass(LightningBolt.class, nearTarget).isEmpty(),
                "no lightning while the wielder is short of full health");

        player.setHealth(player.getMaxHealth());
        onHit(helper, player, hatchet, target);
        helper.assertFalse(helper.getLevel().getEntitiesOfClass(LightningBolt.class, nearTarget).isEmpty(),
                "expected a lightning bolt once the wielder is at full health");

        target.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ plumbing

    /** The blow's pre-mitigation damage after every seam the stack resolves to adjusted it. */
    private static float preHit(GameTestHelper helper, Player attacker, ItemStack weapon, LivingEntity target,
            float damage) {
        CombatHit hit = new CombatHit(helper.getLevel(), weapon, attacker, target,
                helper.getLevel().damageSources().playerAttack(attacker));
        float result = damage;
        for (CombatSeam seam : CombatSeams.seams(weapon)) {
            result = seam.preHit(hit, damage, result);
        }
        return result;
    }

    /**
     * {@link #preHit}, with the swing's charge set explicitly rather than defaulting to a full one
     * -- {@code surging}/{@code escalating}'s gate ({@link HitCondition#FULL_CHARGE}) is otherwise
     * unreachable from a test.
     */
    private static float preHitCharged(GameTestHelper helper, Player attacker, ItemStack weapon,
            LivingEntity target, float damage, float attackStrengthScale) {
        CombatHit hit = new CombatHit(helper.getLevel(), weapon, attacker, target,
                helper.getLevel().damageSources().playerAttack(attacker), attackStrengthScale);
        float result = damage;
        for (CombatSeam seam : CombatSeams.seams(weapon)) {
            result = seam.preHit(hit, damage, result);
        }
        return result;
    }

    /** {@link #preHit}, with the blow's crit multiplier set explicitly -- {@code ruthless}'s gate. */
    private static float preHitCrit(GameTestHelper helper, Player attacker, ItemStack weapon, LivingEntity target,
            float damage, float critMultiplier) {
        CombatHit hit = new CombatHit(helper.getLevel(), weapon, attacker, target,
                helper.getLevel().damageSources().playerAttack(attacker), 1.0F, critMultiplier);
        float result = damage;
        for (CombatSeam seam : CombatSeams.seams(weapon)) {
            result = seam.preHit(hit, damage, result);
        }
        return result;
    }

    /**
     * Issue #946's soul rend, the three fusion metals' on-hit trait. Shipped as datapack
     * {@code trait_definition} rows over the {@code lifesteal} behaviour {@code TraitBehaviors}
     * already had, so this test doubles as proof that a definition Forgeweave itself ships (rather
     * than the gametest pack's, see {@code DatapackTraitGameTests}) loads and fires.
     *
     * <p>Needs no Draconic Evolution: the trait ids go straight onto the tool, the same way every
     * other test in this class builds one.
     */
    @GameTest(template = "empty")
    public static void soulRendHealsMoreAtEachLevel(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie target = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        // The shipped fractions (trait_definition/soulrend*.json) against the onHit helper's 1.0
        // damage, all well under each level's own cap.
        // Soul wick (#965) is duskweld's own faint rung under the three, shipped the same way.
        float[] expected = { 0.05F, 0.10F, 0.18F, 0.26F };
        String[] ids = { "soulwick", "soulrend", "soulrend2", "soulrend3" };
        for (int level = 0; level < ids.length; level++) {
            helper.assertTrue(ForgeweaveTraits.lookup(traitId(ids[level])) != null,
                    "expected " + ids[level] + "'s trait definition to resolve to a behaviour after data load");
            ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId(ids[level])), 3.0F);
            player.setHealth(player.getMaxHealth() - 5.0F);
            float before = player.getHealth();
            onHit(helper, player, hatchet, target);
            float healed = player.getHealth() - before;
            helper.assertTrue(Math.abs(healed - expected[level]) < 0.01F,
                    "expected " + ids[level] + " to heal " + expected[level] + ", got " + healed);
        }

        target.discard();
        helper.succeed();
    }

    /**
     * Issue #946's evolved marker: it is registered, it resolves, and it does nothing. All it exists
     * for is {@code compat.draconic.FusionUpgradeRecipe}'s catalyst check, so a tool carrying it must
     * hit exactly like a tool that does not.
     */
    @GameTest(template = "empty")
    public static void evolvedIsRegisteredAndChangesNothingAboutAHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie target = noAi(helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2)));

        for (String id : List.of("evolving", "evolved", "evolved2", "evolved3")) {
            helper.assertTrue(ForgeweaveTraits.lookup(traitId(id)) != null,
                    "expected " + id + " to be a registered Forgeweave trait");
            ItemStack hatchet = tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(traitId(id)), 3.0F);
            player.setHealth(player.getMaxHealth() - 5.0F);
            float before = player.getHealth();
            float targetBefore = target.getHealth();
            onHit(helper, player, hatchet, target);
            helper.assertTrue(player.getHealth() == before, id + " must not heal the wielder");
            helper.assertTrue(target.getHealth() == targetBefore, id + " must not touch the target");
        }

        target.discard();
        helper.succeed();
    }

    /** {@link #onHit}, with the swing's charge set explicitly -- {@code escalating}'s build gate. */
    private static void onHitCharged(GameTestHelper helper, Player attacker, ItemStack weapon, LivingEntity target,
            float attackStrengthScale) {
        CombatHit hit = new CombatHit(helper.getLevel(), weapon, attacker, target,
                helper.getLevel().damageSources().playerAttack(attacker), attackStrengthScale);
        for (CombatSeam seam : CombatSeams.seams(weapon)) {
            seam.onHit(hit, 1.0F);
        }
    }

    /** Runs every seam's on-hit for one landed blow, the way {@link CombatSeams#onDamageDealt} would. */
    private static void onHit(GameTestHelper helper, Player attacker, ItemStack weapon, LivingEntity target) {
        CombatHit hit = new CombatHit(helper.getLevel(), weapon, attacker, target,
                helper.getLevel().damageSources().playerAttack(attacker));
        for (CombatSeam seam : CombatSeams.seams(weapon)) {
            seam.onHit(hit, 1.0F);
        }
    }

    /**
     * The blow's damage while blocking, after only {@code trait}'s own defensive seams adjusted it --
     * deliberately narrower than {@link CombatSeams#seams}, which would also pull in whatever the
     * tool's own innate carries (issue #302's battlesign melee-block seam, for every test that calls
     * this). See the class javadoc.
     */
    private static float incomingHit(GameTestHelper helper, Player defender, ItemStack tool, LivingEntity attacker,
            Trait trait, DamageSource source, float damage) {
        CombatDefense defense = new CombatDefense(helper.getLevel(), tool, defender, attacker, source, true, true);
        List<CombatSeam> seams = new ArrayList<>();
        trait.combatSeams(seams::add);
        float result = damage;
        for (CombatSeam seam : seams) {
            result = seam.incomingHit(defense, damage, result);
        }
        return result;
    }

    /** One real blow onto a healed, non-invulnerable defender; answers what it cost them. */
    private static float hurtFor(GameTestHelper helper, Player defender, LivingEntity attacker, float damage) {
        defender.setHealth(defender.getMaxHealth());
        defender.invulnerableTime = 0;
        float before = defender.getHealth();
        defender.hurt(helper.getLevel().damageSources().mobAttack(attacker), damage);
        return before - defender.getHealth();
    }

    /** Every mob in these tests is a no-AI adult: nothing wanders, retaliates or panics mid-assert. */
    private static <T extends Mob> T noAi(T mob) {
        mob.setNoAi(true);
        return mob;
    }

    /** Builds a tool {@code ItemStack} with the given traits directly (see class javadoc). */
    static ItemStack tool(ToolItem toolItem, List<ResourceLocation> traits, float attackDamage) {
        int durability = 1000;
        ToolStats.Stats stats = new ToolStats.Stats(durability, 1.0F, attackDamage);
        Material head = new Material(
                new Material.Head(durability, 1.0F, attackDamage),
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

    static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
