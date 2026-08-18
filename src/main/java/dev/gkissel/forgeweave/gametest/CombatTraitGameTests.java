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

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.BleedEffect;
import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
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
        CombatDefense defense = new CombatDefense(helper.getLevel(), tool, defender, attacker, source, true);
        List<CombatSeam> seams = new ArrayList<>();
        trait.combatSeams(seams::add);
        float result = damage;
        for (CombatSeam seam : seams) {
            result = seam.incomingHit(defense, damage, result);
        }
        return result;
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
