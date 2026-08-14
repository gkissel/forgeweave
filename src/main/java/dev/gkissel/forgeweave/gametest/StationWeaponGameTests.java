package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * docs/SCOPE.md M3 issue #155's verification: one GameTest per station-weapon innate -- the longsword
 * leap's displacement, the battlesign's projectile reflect, the frying pan's knockback against a
 * baseline, the rapier's %-health strike against an armored target, the dagger's backstab gradient,
 * and the broadsword parry's negation.
 *
 * <p>Every weapon here is assembled through a real Tool Station ({@link ToolAssembly}), in the tool's
 * own per-part slot order, so a passing test is also evidence that the entry-driven assembly wrote
 * the components the innate needs and that {@code ForgeweaveInnates#collect} attached the seam.
 *
 * <p>Magnitudes are the maintainer decision recorded on the issue (2026-08-12): rapier 5% of current
 * health past armor, broadsword a 0.5s parry negating one melee blow plus Slowness I for 1s, dagger
 * +100% within 45&deg; of dead-behind falling to +25% at the 90&deg; edge of the rear cone. The
 * longsword leap, battlesign reflect and frying pan knockback are ported from the 1.12 clone.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class StationWeaponGameTests {

    /** Stone head everywhere: neither wood's ecological nor stone's cheap touches attack damage. */
    private static final String HEAD = "stone";
    private static final String OTHER = "wood";

    /**
     * Longsword: holding the use button and letting go throws the player in the direction they are
     * looking. Upstream {@code LongSword#onPlayerStoppedUsing}: nothing at all below 5 ticks held,
     * then a rise of {@code min(0.02t + 0.2, 0.56)} and a horizontal {@code min(0.05t, 0.925)}.
     */
    @GameTest(template = "empty")
    public static void longswordLeapThrowsThePlayerForward(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack longsword = weapon(helper, player, pos, ForgeweaveItems.TOOL_LONGSWORD.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, longsword);
        player.setYRot(0.0F); // facing +Z
        player.setXRot(0.0F);
        ToolItem tool = (ToolItem) longsword.getItem();
        int duration = longsword.getUseDuration(player);

        // A flick of the button is not a leap.
        player.setDeltaMovement(Vec3.ZERO);
        tool.releaseUsing(longsword, helper.getLevel(), player, duration - 3);
        helper.assertTrue(player.getDeltaMovement().equals(Vec3.ZERO),
                "a 3-tick hold is below the 5-tick floor and must not move the player, got "
                        + player.getDeltaMovement());

        // A half-second charge is: 10 ticks -> rise 0.4, horizontal 0.5 along +Z.
        player.setDeltaMovement(Vec3.ZERO);
        tool.releaseUsing(longsword, helper.getLevel(), player, duration - 10);
        Vec3 leap = player.getDeltaMovement();
        helper.assertTrue(Math.abs(leap.y - 0.4) < 1.0E-4,
                "expected a 10-tick charge to rise 0.02*10 + 0.2 = 0.4, got " + leap.y);
        helper.assertTrue(Math.abs(leap.z - 0.5) < 1.0E-4,
                "expected a 10-tick charge to carry 0.05*10 = 0.5 along the look direction, got " + leap.z);
        helper.assertTrue(Math.abs(leap.x) < 1.0E-4, "a leap due +Z must not drift sideways, got " + leap.x);

        // And it caps: a full 200-tick charge is still 0.56 up and 0.925 along.
        player.setDeltaMovement(Vec3.ZERO);
        tool.releaseUsing(longsword, helper.getLevel(), player, 0);
        Vec3 capped = player.getDeltaMovement();
        helper.assertTrue(Math.abs(capped.y - 0.56) < 1.0E-4, "expected the rise to cap at 0.56, got " + capped.y);
        helper.assertTrue(Math.abs(capped.z - 0.925) < 1.0E-4,
                "expected the horizontal speed to cap at 0.925, got " + capped.z);

        helper.succeed();
    }

    /**
     * Battlesign: an arrow shot at a player who is blocking with one is stopped and sent back where
     * it came from, with the blocker as its new owner. Upstream {@code BattleSign#reflectProjectiles}.
     */
    @GameTest(template = "empty")
    public static void battlesignReflectsAProjectileItIsFacing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack battlesign = weapon(helper, player, pos, ForgeweaveItems.TOOL_BATTLESIGN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, battlesign);
        player.setYRot(0.0F); // facing +Z
        player.setXRot(0.0F);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(player.isUsingItem(), "raising the battlesign must open its blocking stance");

        Arrow arrow = helper.spawn(EntityType.ARROW, new BlockPos(1, 2, 4));
        arrow.setDeltaMovement(0.0, 0.0, -1.0); // flying at the player, who is looking at it
        float before = player.getHealth();

        boolean hurt = player.hurt(helper.getLevel().damageSources().arrow(arrow, arrow), 4.0F);

        helper.assertFalse(hurt, "a reflected arrow must not land at all");
        helper.assertTrue(player.getHealth() == before, "a reflected arrow must cost no health");
        helper.assertTrue(arrow.getDeltaMovement().z > 0.0,
                "the arrow must be sent back the way it came, got " + arrow.getDeltaMovement());
        helper.assertTrue(arrow.getOwner() == player, "the reflector becomes the arrow's owner");

        arrow.discard();
        helper.succeed();
    }

    /**
     * Battlesign (issue #302): a blocked melee blow -- not magic, not an explosion, not a projectile --
     * takes an extra 30% reduction, and half of the reduced amount reflects onto the attacker as
     * thorns damage, at a durability cost. Upstream {@code BattleSign#reducedDamageBlocked}.
     */
    @GameTest(template = "empty")
    public static void battlesignReducesAndReflectsABlockedMeleeBlow(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player defender = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack battlesign = weapon(helper, defender, pos, ForgeweaveItems.TOOL_BATTLESIGN.get());
        defender.setItemInHand(InteractionHand.MAIN_HAND, battlesign);
        Pig attacker = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        defender.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(defender.isUsingItem(), "raising the battlesign must open its blocking stance");

        float defenderBefore = defender.getHealth();
        float attackerBefore = attacker.getHealth();
        int durabilityBefore = battlesign.getDamageValue();

        boolean landed = defender.hurt(helper.getLevel().damageSources().mobAttack(attacker), 10.0F);

        float taken = defenderBefore - defender.getHealth();
        float reflected = attackerBefore - attacker.getHealth();

        helper.assertTrue(landed, "a blocked melee blow must still land, just reduced");
        helper.assertTrue(Math.abs(taken - 7.0F) < 0.05F,
                "expected the extra 30% reduction to leave 10 * 0.7 = 7 damage, got " + taken);
        helper.assertTrue(Math.abs(reflected - 3.5F) < 0.05F,
                "expected half the reduced damage (3.5) to reflect onto the attacker as thorns, got " + reflected);
        // Upstream's odd durability shape: round(10 / 2) = 5, times 3/2 for having an attacker to
        // reflect onto (integer division) = 7.
        helper.assertTrue(battlesign.getDamageValue() - durabilityBefore == 7,
                "expected a durability cost of round(10/2)*3/2 = 7, got "
                        + (battlesign.getDamageValue() - durabilityBefore));

        attacker.discard();
        helper.succeed();
    }

    /**
     * Battlesign (issue #302): magic and explosion damage are excluded from the melee-block reduction,
     * upstream's own exclusion list -- a blocked hit of either kind passes through unreduced,
     * unreflected and free. Projectile damage takes the separate reflect path proven above.
     */
    @GameTest(template = "empty")
    public static void battlesignMeleeBlockExcludesMagicAndExplosionDamage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player defender = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack battlesign = weapon(helper, defender, pos, ForgeweaveItems.TOOL_BATTLESIGN.get());
        defender.setItemInHand(InteractionHand.MAIN_HAND, battlesign);

        assertPassesThroughUnreduced(helper, defender, battlesign, helper.getLevel().damageSources().magic(), "magic");
        assertPassesThroughUnreduced(helper, defender, battlesign,
                helper.getLevel().damageSources().explosion(null), "explosion");

        helper.succeed();
    }

    /** Deals {@code source} to a blocking {@code defender} and asserts it took the full 10 damage. */
    private static void assertPassesThroughUnreduced(GameTestHelper helper, Player defender, ItemStack battlesign,
            DamageSource source, String label) {
        defender.setHealth(defender.getMaxHealth());
        defender.invulnerableTime = 0;
        defender.startUsingItem(InteractionHand.MAIN_HAND);
        int durabilityBefore = battlesign.getDamageValue();
        float before = defender.getHealth();

        defender.hurt(source, 10.0F);

        float taken = before - defender.getHealth();
        helper.assertTrue(Math.abs(taken - 10.0F) < 0.05F,
                "expected " + label + " damage to pass through the melee-block reduction unreduced while "
                        + "blocking, took " + taken);
        helper.assertTrue(battlesign.getDamageValue() == durabilityBefore,
                label + " damage must not cost the battlesign durability");
        defender.stopUsingItem();
    }

    /**
     * Frying pan: the same blow pushes a pig roughly twice as far as an ordinary tool's does.
     * Upstream {@code FryPan#knockback()} returns 2 where {@code ToolCore}'s default is 1.
     */
    @GameTest(template = "empty")
    public static void fryingPanKnocksBackHarderThanABaselineTool(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pan = weapon(helper, player, pos, ForgeweaveItems.TOOL_FRYING_PAN.get());
        // The broadsword is the baseline, not an M1 tool: since issue #164 the pickaxe carries pierce
        // and the hatchet sunder, both of which move the number under test. The broadsword's parry is
        // purely defensive, so it lands an ordinary blow.
        ItemStack baseline = weapon(helper, player, pos, ForgeweaveItems.TOOL_BROADSWORD.get());

        double withPan = knockbackDistance(helper, player, pan);
        double withoutPan = knockbackDistance(helper, player, baseline);

        helper.assertTrue(withPan > withoutPan * 1.5,
                "expected the frying pan to push at least half again as far as a plain tool: "
                        + withPan + " vs " + withoutPan);
        helper.succeed();
    }

    /**
     * Frying pan: holding right-click charges, and letting go hits whatever the player is looking at
     * within 3.2 blocks for a charge-scaled bonus and launches it. Upstream
     * {@code FryPan#onPlayerStoppedUsing}: {@code progress = min(1, held/30)}, a bonus attack of
     * {@code 5p} and a launch of {@code look * (0.1 + 2.5p²)} with {@code 0.1 + 0.4p} of lift.
     *
     * <p>The two charges' launches are compared as a <em>difference</em> because everything else in
     * the pig's motion (the blow's own knockback, the heavy-swing seam's extra push) is identical
     * between them and cancels: {@code 2.6 - 0.725 = 1.875} forward and {@code 0.5 - 0.3 = 0.2} up
     * is the launch formula and nothing else.
     */
    @GameTest(template = "empty")
    public static void fryingPanChargedReleaseLaunchesWhatItLooksAt(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pan = weapon(helper, player, pos, ForgeweaveItems.TOOL_FRYING_PAN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pan);
        player.setYRot(0.0F); // facing +Z
        player.setXRot(0.0F);
        ToolItem tool = (ToolItem) pan.getItem();
        int duration = pan.getUseDuration(player);

        Pig far = pigInFrontOf(helper, player, 5.0);
        float farBefore = far.getHealth();
        tool.releaseUsing(pan, helper.getLevel(), player, 0);
        helper.assertTrue(far.getHealth() == farBefore,
                "a pig beyond the 3.2-block reach must not be hit, it lost " + (farBefore - far.getHealth()));
        helper.assertTrue(far.getDeltaMovement().equals(Vec3.ZERO),
                "a pig beyond the reach must not be launched, got " + far.getDeltaMovement());
        far.discard();

        Pig halfTarget = pigInFrontOf(helper, player, 2.0);
        float halfBefore = halfTarget.getHealth();
        tool.releaseUsing(pan, helper.getLevel(), player, duration - 15); // progress 0.5
        float halfDamage = halfBefore - halfTarget.getHealth();
        Vec3 halfLaunch = halfTarget.getDeltaMovement();
        halfTarget.discard();

        helper.assertTrue(halfDamage > 0.0F, "a charged release must hit the pig it is aimed at");
        helper.assertTrue(halfLaunch.z > 0.0, "the launch must throw the pig away from the player, got " + halfLaunch);

        Pig fullTarget = pigInFrontOf(helper, player, 2.0);
        float fullBefore = fullTarget.getHealth();
        tool.releaseUsing(pan, helper.getLevel(), player, duration - 40); // past the 30-tick full charge
        float fullDamage = fullBefore - fullTarget.getHealth();
        Vec3 fullLaunch = fullTarget.getDeltaMovement();
        fullTarget.discard();

        helper.assertTrue(fullDamage > halfDamage,
                "a full charge must add more bonus damage than a half one: " + fullDamage + " vs " + halfDamage);
        helper.assertTrue(Math.abs((fullLaunch.z - halfLaunch.z) - 1.875) < 1.0E-3,
                "expected the launch to grow by 2.5*(1 - 0.5^2) = 1.875 forward between a half and a full "
                        + "charge, got " + (fullLaunch.z - halfLaunch.z));
        helper.assertTrue(Math.abs((fullLaunch.y - halfLaunch.y) - 0.2) < 1.0E-3,
                "expected the lift to grow by 0.4*(1 - 0.5) = 0.2 between a half and a full charge, got "
                        + (fullLaunch.y - halfLaunch.y));
        helper.assertTrue(Math.abs(fullLaunch.x) < 1.0E-4,
                "a launch straight down +Z must not drift sideways, got " + fullLaunch.x);

        helper.succeed();
    }

    /**
     * Frying pan: a full charge briefly sets its target alight for the blow itself, which is upstream's
     * own joke -- what it kills at full charge drops cooked. The fire is put back out straight after,
     * so the kill is the only place it shows.
     */
    @GameTest(template = "empty")
    public static void fryingPanFullChargeCooksWhatItKills(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pan = weapon(helper, player, pos, ForgeweaveItems.TOOL_FRYING_PAN.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, pan);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        ToolItem tool = (ToolItem) pan.getItem();
        int duration = pan.getUseDuration(player);

        Pig pig = pigInFrontOf(helper, player, 2.0);
        pig.setHealth(1.0F);
        Vec3 where = pig.position();

        tool.releaseUsing(pan, helper.getLevel(), player, duration - 40);

        helper.assertTrue(pig.isDeadOrDying(), "a full-charge release must finish a pig on its last health");
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(where, where).inflate(6.0));
        boolean cooked = drops.stream().anyMatch(drop -> drop.getItem().is(Items.COOKED_PORKCHOP));
        boolean raw = drops.stream().anyMatch(drop -> drop.getItem().is(Items.PORKCHOP));
        drops.forEach(ItemEntity::discard);
        pig.discard();

        helper.assertTrue(cooked, "a full-charge kill must drop cooked porkchop, got " + drops);
        helper.assertFalse(raw, "nothing should drop raw from a full-charge kill, got " + drops);
        helper.succeed();
    }

    /**
     * A pig {@code distance} blocks due +Z of {@code player}, with the player set low enough that a
     * level look-ray runs through the pig's rather short bounding box.
     */
    private static Pig pigInFrontOf(GameTestHelper helper, Player player, double distance) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.setDeltaMovement(Vec3.ZERO);
        player.setPos(pig.getX(), pig.getY() - 1.2, pig.getZ() - distance);
        return pig;
    }

    /** How far a pig is pushed horizontally by one blow with {@code tool}. */
    private static double knockbackDistance(GameTestHelper helper, Player player, ItemStack tool) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        player.setYRot(0.0F);
        pig.setDeltaMovement(Vec3.ZERO);
        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        Vec3 motion = pig.getDeltaMovement();
        double distance = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        pig.discard();
        return distance;
    }

    /**
     * Rapier: on top of whatever the armored target mitigates, it always loses a further 5% of the
     * health it had -- the maintainer's redesign of upstream's hybrid-damage hit, and the point of it
     * is that armor cannot stop that half.
     */
    @GameTest(template = "empty")
    public static void rapierTakesAShareOfCurrentHealthThroughArmor(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack rapier = weapon(helper, player, pos, ForgeweaveItems.TOOL_RAPIER.get());
        // Broadsword, not a pickaxe: the pickaxe's own pierce innate (issue #164) adds flat
        // armor-ignoring damage of its own, which is precisely the thing being measured here.
        ItemStack baseline = weapon(helper, player, pos, ForgeweaveItems.TOOL_BROADSWORD.get());

        float withRapier = hitArmoredPig(helper, player, rapier);
        float withoutRapier = hitArmoredPig(helper, player, baseline);

        // A pig is 10 health, so 5% of its current health is 0.5 -- and 1.0 of raw damage against
        // 20 armour/12 toughness mitigates to well under that, which is what makes the difference
        // legible at all rather than lost in rounding.
        float bonus = withRapier - withoutRapier;
        helper.assertTrue(Math.abs(bonus - 0.5F) < 0.05F,
                "expected the rapier to take a further 5% of the pig's 10 health (0.5) straight through "
                        + "armour: " + withRapier + " lost with it vs " + withoutRapier + " without");
        helper.succeed();
    }

    /** Hits a heavily armored pig once with {@code tool} and returns the health it lost. */
    private static float hitArmoredPig(GameTestHelper helper, Player player, ItemStack tool) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
        pig.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(12.0);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);

        float before = pig.getHealth();
        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        float lost = before - pig.getHealth();
        pig.discard();
        return lost;
    }

    /**
     * Rapier: right-clicking on the ground is the fencing hop of upstream
     * {@code Rapier#onItemRightClick} -- {@code motionY += 0.32} on top of whatever vertical motion is
     * there, a flat 0.5 horizontal dash, and a 4-tick cooldown. In the air it does nothing at all.
     *
     * <p>The dash is upstream's own vector, which is the <em>negation</em> of the look direction: a
     * backwards disengage, not a forward charge. Facing +Z, that is {@code z = -0.5}.
     */
    @GameTest(template = "empty")
    public static void rapierLungeHopsAndDashesFromTheGround(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack rapier = weapon(helper, player, pos, ForgeweaveItems.TOOL_RAPIER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, rapier);
        player.setYRot(0.0F); // facing +Z
        player.setXRot(0.0F);
        ToolItem tool = (ToolItem) rapier.getItem();

        // Airborne: the lunge is a push off the ground, so there is nothing to push off.
        player.setOnGround(false);
        player.setDeltaMovement(Vec3.ZERO);
        tool.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getDeltaMovement().equals(Vec3.ZERO),
                "a mid-air right-click must not lunge, got " + player.getDeltaMovement());
        helper.assertFalse(player.getCooldowns().isOnCooldown(rapier.getItem()),
                "a lunge that never happened must not cost a cooldown");

        player.setOnGround(true);
        player.setDeltaMovement(Vec3.ZERO);
        tool.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

        Vec3 lunge = player.getDeltaMovement();
        helper.assertTrue(Math.abs(lunge.y - 0.32) < 1.0E-4,
                "expected the hop to add 0.32 of vertical motion, got " + lunge.y);
        helper.assertTrue(Math.abs(lunge.z + 0.5) < 1.0E-4,
                "expected a 0.5 dash away from a +Z facing, got " + lunge.z);
        helper.assertTrue(Math.abs(lunge.x) < 1.0E-4,
                "a lunge along the Z axis must not drift sideways, got " + lunge.x);
        helper.assertTrue(player.getCooldowns().isOnCooldown(rapier.getItem()),
                "a lunge must put the rapier on its 4-tick cooldown");

        helper.succeed();
    }

    /**
     * Rapier: a shield-like offhand -- upstream's carve-out names vanilla shields and the battlesign --
     * changes only the answer, not the lunge. Upstream hops first and <em>then</em> returns
     * {@code PASS} so the offhand shield can still go up (maintainer decision 2026-08-13).
     */
    @GameTest(template = "empty")
    public static void rapierLungeStillFiresWithAShieldInTheOffhand(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack rapier = weapon(helper, player, pos, ForgeweaveItems.TOOL_RAPIER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, rapier);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setOnGround(true);
        ToolItem tool = (ToolItem) rapier.getItem();

        for (ItemStack offhand : List.of(new ItemStack(Items.SHIELD),
                weapon(helper, player, pos, ForgeweaveItems.TOOL_BATTLESIGN.get()))) {
            player.setItemInHand(InteractionHand.OFF_HAND, offhand);
            player.setDeltaMovement(Vec3.ZERO);

            InteractionResultHolder<ItemStack> result =
                    tool.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);

            helper.assertTrue(result.getResult() == InteractionResult.PASS,
                    "the click must pass through to " + offhand.getItem() + " in the offhand, got "
                            + result.getResult());
            Vec3 lunge = player.getDeltaMovement();
            helper.assertTrue(Math.abs(lunge.y - 0.32) < 1.0E-4,
                    "the lunge must still hop with " + offhand.getItem() + " in the offhand, got " + lunge.y);
            helper.assertTrue(Math.abs(lunge.z + 0.5) < 1.0E-4,
                    "the lunge must still dash with " + offhand.getItem() + " in the offhand, got " + lunge.z);
            helper.assertTrue(player.getCooldowns().isOnCooldown(rapier.getItem()),
                    "a lunge costs its cooldown whatever the offhand holds");
        }

        helper.succeed();
    }

    /**
     * Dagger: the backstab bonus scales with how directly behind the target the blow lands -- full
     * within 45&deg; of dead-behind, tapering to a quarter at the 90&deg; edge of the rear cone, and
     * nothing in front. Driven through the seam's own pure gradient so every angle is exact; the
     * end-to-end attachment is covered by the dead-behind blow below it.
     */
    @GameTest(template = "empty")
    public static void daggerBackstabScalesWithTheAngleBehindTheTarget(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        ForgeweaveInnates.Backstab backstab = ForgeweaveInnates.BACKSTAB_SEAM;

        // The pig always stands at the same spot; moving the attacker around it is what changes the
        // angle, and the pig's own facing is what "behind" is measured against.
        assertBonus(helper, backstab, attacker, target, 0.0, 1.00F, "dead behind");
        assertBonus(helper, backstab, attacker, target, 45.0, 1.00F, "the edge of the full-bonus cone");
        assertBonus(helper, backstab, attacker, target, 67.5, 0.625F, "half way out to the cone edge");
        assertBonus(helper, backstab, attacker, target, 90.0, 0.25F, "the edge of the rear cone");
        assertBonus(helper, backstab, attacker, target, 120.0, 0.0F, "outside the rear cone");
        assertBonus(helper, backstab, attacker, target, 180.0, 0.0F, "straight in front");

        target.discard();
        helper.succeed();
    }

    /**
     * Places {@code attacker} {@code degrees} around {@code target} from dead-behind and checks the
     * bonus fraction the gradient yields there.
     */
    private static void assertBonus(GameTestHelper helper, ForgeweaveInnates.Backstab backstab, Player attacker,
            Pig target, double degrees, float expected, String where) {
        // The pig faces +Z (yaw 0), so "dead behind" is standing at -Z of it and striking along +Z.
        target.setYRot(0.0F);
        target.setYHeadRot(0.0F);
        double radians = Math.toRadians(degrees);
        attacker.setPos(target.getX() + Math.sin(radians) * 4.0, target.getY(), target.getZ() - Math.cos(radians) * 4.0);

        float actual = backstab.bonusFraction(attacker, target);
        helper.assertTrue(Math.abs(actual - expected) < 1.0E-3,
                "expected " + expected + " bonus at " + degrees + " degrees (" + where + "), got " + actual);
    }

    /** The dagger's gradient, reached the way a real blow reaches it -- through the shared seam chain. */
    @GameTest(template = "empty")
    public static void daggerBackstabDoublesADeadBehindBlow(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack dagger = weapon(helper, player, pos, ForgeweaveItems.TOOL_DAGGER.get());
        player.setItemInHand(InteractionHand.MAIN_HAND, dagger);

        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 3));
        target.setYRot(0.0F);
        target.setYHeadRot(0.0F);
        // Straight behind it, striking along its own facing.
        player.setPos(target.getX(), target.getY(), target.getZ() - 2.0);
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        float before = target.getHealth();
        target.hurt(source, 2.0F);
        float fromBehind = before - target.getHealth();

        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        player.setPos(target.getX(), target.getY(), target.getZ() + 2.0); // now in front of it
        before = target.getHealth();
        target.hurt(source, 2.0F);
        float fromInFront = before - target.getHealth();

        helper.assertTrue(Math.abs(fromBehind - fromInFront * 2.0F) < 0.05F,
                "expected a dead-behind dagger blow to land double a frontal one: " + fromBehind
                        + " from behind vs " + fromInFront + " from the front");

        target.discard();
        helper.succeed();
    }

    /**
     * Broadsword: right-clicking opens a window in which one melee blow is turned aside entirely and
     * its thrower is slowed. Maintainer decision on issue #155 (2026-08-12).
     */
    @GameTest(template = "empty")
    public static void broadswordParryNegatesOneMeleeBlowAndSlowsTheAttacker(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player defender = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack broadsword = weapon(helper, defender, pos, ForgeweaveItems.TOOL_BROADSWORD.get());
        defender.setItemInHand(InteractionHand.MAIN_HAND, broadsword);
        Pig attacker = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        // Nothing raised: the blow lands as usual, which is what makes the parried case mean something.
        float before = defender.getHealth();
        defender.hurt(helper.getLevel().damageSources().mobAttack(attacker), 3.0F);
        helper.assertTrue(defender.getHealth() < before, "an unparried blow must actually hurt");

        defender.setHealth(defender.getMaxHealth());
        defender.invulnerableTime = 0;
        defender.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(defender.isUsingItem(), "right-clicking the broadsword must open the parry window");

        before = defender.getHealth();
        boolean landed = defender.hurt(helper.getLevel().damageSources().mobAttack(attacker), 3.0F);

        helper.assertFalse(landed, "a parried melee blow must not land");
        helper.assertTrue(defender.getHealth() == before, "a parried melee blow must cost no health");
        helper.assertTrue(attacker.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "a parry must slow whoever threw the blow");
        helper.assertFalse(defender.isUsingItem(), "one blow closes the parry window");

        attacker.discard();
        helper.succeed();
    }

    /** Assembles {@code tool} from a stone head and wood everywhere else, through a real station. */
    private static ItemStack weapon(GameTestHelper helper, Player player, BlockPos pos, ToolItem tool) {
        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(tool);
        List<String> materials = entry.constants().parts().stream()
                .map(slot -> slot.role() == dev.gkissel.forgeweave.tool.ToolConstants.Role.HEAD ? HEAD : OTHER)
                .toList();
        ItemStack stack = ToolAssembly.assemble(helper, player, pos, entry, materials);
        if (stack.isEmpty()) {
            throw new AssertionError("the Tool Station built nothing for " + entry.constants().id());
        }
        return stack;
    }
}
