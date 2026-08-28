package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
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
import dev.gkissel.forgeweave.combat.ForgeweaveMobEffects;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * M4-5 (issue #680; SCOPE.md D8/D17/D23): one test per ARMOR-scope trait, on a chestplate assembled
 * at the Tool Station from the material that grants it, plus the "no armor: the defensive pass
 * changes nothing" regression. Protection numbers are checked as a <em>ratio</em> against the same
 * blow on the same piece with its traits stripped, so the armor value, toughness and any
 * difficulty scaling cancel out and only the clone's {@code 1 - value / 25} remains.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArmorTraitGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    private static final float BLOW = 4.0F;

    /** A survival mock player wearing a chestplate of {@code plating} over {@code maille}, ticked once (see ArmorGameTests#wearing). */
    private static Player wearing(GameTestHelper helper, String plating, String maille) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of(plating, maille));
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        return player;
    }

    private static ItemStack worn(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST);
    }

    /** One blow onto a healed, non-invulnerable player; what it cost them. */
    private static float lost(Player player, DamageSource source, float amount) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(source, amount);
        return before - player.getHealth();
    }

    /** The same blow with the piece's traits stripped and put back: the denominator of every ratio. */
    private static float lostWithoutTraits(Player player, DamageSource source, float amount) {
        ItemStack piece = worn(player);
        List<ResourceLocation> traits = piece.get(ForgeweaveDataComponents.TRAITS.get());
        piece.remove(ForgeweaveDataComponents.TRAITS.get());
        float result = lost(player, source, amount);
        piece.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        return result;
    }

    private static void assertRatio(GameTestHelper helper, float with, float without, float protection, String what) {
        float expected = without * (1.0F - Mth.clamp(protection, -20.0F, 20.0F) / 25.0F);
        helper.assertTrue(Math.abs(with - expected) < 0.01F,
                what + ": expected " + expected + " (protection " + protection + " on " + without + "), lost " + with);
    }

    private static DamageSource explosion(GameTestHelper helper) {
        return helper.getLevel().damageSources().explosion(null, null);
    }

    private static Zombie zombie(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.setNoAi(true);
        return zombie;
    }

    @GameTest(template = "empty")
    public static void noArmorLeavesTheBlowUntouched(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        float lost = lost(player, explosion(helper), BLOW);
        helper.assertTrue(Math.abs(lost - BLOW) < 0.001F, "nothing worn: the blow must land whole, lost " + lost);
        helper.succeed();
    }

    /** Iron -&gt; projectile_protection: 2 against projectile-tagged blows (a falling anvil), nothing against an explosion. */
    @GameTest(template = "empty")
    public static void projectileProtectionOnlyAgainstProjectiles(GameTestHelper helper) {
        Player player = wearing(helper, "iron", "iron");
        DamageSource anvil = helper.getLevel().damageSources().anvil(null);
        assertRatio(helper, lost(player, anvil, BLOW), lostWithoutTraits(player, anvil, BLOW), 2.0F, "anvil");
        assertRatio(helper, lost(player, explosion(helper), BLOW), lostWithoutTraits(player, explosion(helper), BLOW), 0.0F, "explosion");
        helper.assertTrue(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) > 0.0F,
                "the piece grants knockback resistance");
        helper.succeed();
    }

    /** Obsidian -&gt; blast_protection: 2.5 against explosions. */
    @GameTest(template = "empty")
    public static void blastProtectionAgainstExplosions(GameTestHelper helper) {
        Player player = wearing(helper, "obsidian", "obsidian");
        assertRatio(helper, lost(player, explosion(helper), BLOW), lostWithoutTraits(player, explosion(helper), BLOW), 2.5F, "explosion");
        helper.succeed();
    }

    /** Cobalt -&gt; melee_protection: 2 against a direct player attack. */
    @GameTest(template = "empty")
    public static void meleeProtectionAgainstDirectBlows(GameTestHelper helper) {
        Player player = wearing(helper, "cobalt", "cobalt");
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        DamageSource punch = helper.getLevel().damageSources().playerAttack(attacker);
        assertRatio(helper, lost(player, punch, BLOW), lostWithoutTraits(player, punch, BLOW), 2.0F, "punch");
        helper.succeed();
    }

    /** Silver -&gt; consecrated: 1.25 against an undead attacker, nothing against a player. */
    @GameTest(template = "empty")
    public static void consecratedAgainstTheUndead(GameTestHelper helper) {
        Player player = wearing(helper, "silver", "silver");
        Zombie zombie = zombie(helper);
        DamageSource bite = helper.getLevel().damageSources().mobAttack(zombie);
        assertRatio(helper, lost(player, bite, BLOW), lostWithoutTraits(player, bite, BLOW), 1.25F, "zombie");
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        DamageSource punch = helper.getLevel().damageSources().playerAttack(attacker);
        assertRatio(helper, lost(player, punch, BLOW), lostWithoutTraits(player, punch, BLOW), 0.0F, "player");
        zombie.discard();
        helper.succeed();
    }

    /** Copper -&gt; depth_protection: 1.25 at Y=0, -1.25 at Y=160 and above. */
    @GameTest(template = "empty")
    public static void depthProtectionScalesWithDepth(GameTestHelper helper) {
        Player player = wearing(helper, "copper", "copper");
        player.setPos(player.getX(), 0.0, player.getZ());
        assertRatio(helper, lost(player, explosion(helper), BLOW), lostWithoutTraits(player, explosion(helper), BLOW), 1.25F, "at y=0");
        player.setPos(player.getX(), 200.0, player.getZ());
        assertRatio(helper, lost(player, explosion(helper), BLOW), lostWithoutTraits(player, explosion(helper), BLOW), -1.25F, "at y=200");
        helper.succeed();
    }

    /** Manyullyn -&gt; warded: one damage off after armor at full health, none once hurt. */
    @GameTest(template = "empty")
    public static void wardedCutsOneAtFullHealth(GameTestHelper helper) {
        Player player = wearing(helper, "manyullyn", "manyullyn");
        float without = lostWithoutTraits(player, explosion(helper), BLOW);
        float full = lost(player, explosion(helper), BLOW);
        helper.assertTrue(Math.abs(full - (without - 1.0F)) < 0.01F,
                "at full health expected " + (without - 1.0F) + ", lost " + full);
        player.setHealth(player.getMaxHealth() - 2.0F);
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(explosion(helper), BLOW);
        float hurt = before - player.getHealth();
        helper.assertTrue(Math.abs(hurt - without) < 0.01F, "already hurt: expected " + without + ", lost " + hurt);
        helper.succeed();
    }

    /** Amethyst bronze -&gt; crystalstrike: +5% attack speed, and knockback taken snaps to one of 32 directions. */
    @GameTest(template = "empty")
    public static void crystalstrikeSpeedsAttacksAndSnapsKnockback(GameTestHelper helper) {
        Player player = wearing(helper, "amethyst_bronze", "amethyst_bronze");
        helper.assertTrue(player.getAttribute(Attributes.ATTACK_SPEED).getModifier(
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "trait/crystalstrike/chest")) != null,
                "the piece grants the attack speed modifier");
        player.setDeltaMovement(Vec3.ZERO);
        player.knockback(0.4, 0.3, 1.0); // 16.7 degrees off north: not on the 11.25-degree grid
        Vec3 push = player.getDeltaMovement();
        helper.assertTrue(push.horizontalDistanceSqr() > 0.0, "the push must land");
        double increment = 2 * Math.PI / 32;
        double angle = Mth.atan2(push.x, push.z);
        double off = Math.abs(angle / increment - Math.round(angle / increment));
        helper.assertTrue(off < 0.01, "the push must snap to a 32-direction grid, angle " + angle + " is off by " + off);
        helper.succeed();
    }

    /**
     * Knightslime -&gt; overslime (#728, the clone's {@code OverslimeModifier}): 50 capacity per
     * trait, -0.5 armor unless an overslime_friend maille sits under it, and durability loss is
     * paid from the overslime first ({@code DurabilityShieldModule#onDamageTool}).
     */
    @GameTest(template = "empty")
    public static void overslimeShieldsDurabilityAndCostsHalfAnArmor(GameTestHelper helper) {
        Player player = wearing(helper, "knightslime", "knightslime");
        ItemStack piece = worn(player);
        helper.assertTrue(ForgeweaveTraits.overslimeCapacity(piece) == ForgeweaveTraits.OVERSLIME_CAPACITY,
                "one overslime trait is 50 capacity, got " + ForgeweaveTraits.overslimeCapacity(piece));
        helper.assertTrue(ForgeweaveTraits.overslime(piece) == 0, "a fresh piece has no overslime");
        helper.assertTrue(piece.get(ForgeweaveDataComponents.ARMOR_STATS.get()).armor() == 6.5F,
                "knightslime's 7 armor minus the overslime penalty, got " + piece.get(ForgeweaveDataComponents.ARMOR_STATS.get()).armor());
        ForgeweaveTraits.setOverslime(piece, 5);
        piece.hurtAndBreak(3, player, EquipmentSlot.CHEST);
        helper.assertTrue(ForgeweaveTraits.overslime(piece) == 2 && piece.getDamageValue() == 0,
                "3 loss paid from overslime: " + ForgeweaveTraits.overslime(piece) + " left, damage " + piece.getDamageValue());
        piece.hurtAndBreak(5, player, EquipmentSlot.CHEST);
        helper.assertTrue(ForgeweaveTraits.overslime(piece) == 0 && piece.getDamageValue() == 3,
                "the remainder past the overslime hits durability: " + ForgeweaveTraits.overslime(piece) + " left, damage " + piece.getDamageValue());

        Player friend = wearing(helper, "knightslime", "slimevine_blue");
        helper.assertTrue(worn(friend).get(ForgeweaveDataComponents.ARMOR_STATS.get()).armor() == 7.0F,
                "blue slime vine maille is an overslime friend: no penalty");
        helper.succeed();
    }

    /**
     * Knightslime -&gt; overshield ({@code OvershieldModule}: 1.25 protection per 2 overslime) and
     * the absorption order of one blow: the protection hook runs first ({@code CombatSeams}'
     * defensive pass rides {@code LivingIncomingDamageEvent}, as the clone's rides
     * {@code LivingHurtEvent} -- both before vanilla's armor wear), then the wear is paid from what
     * is left. So 3 overslime take one explosion for 2 (shield) + 1 (wear) and leave the piece
     * undamaged; the next blow is unshielded and wears the piece; 2 overslime shield in full and
     * leave nothing for the wear.
     */
    @GameTest(template = "empty")
    public static void overshieldSpendsOverslimeBeforeItShieldsWear(GameTestHelper helper) {
        Player player = wearing(helper, "knightslime", "knightslime");
        ItemStack piece = worn(player);
        float without = lostWithoutTraits(player, explosion(helper), BLOW);
        piece.setDamageValue(0);
        ForgeweaveTraits.setOverslime(piece, 3);
        assertRatio(helper, lost(player, explosion(helper), BLOW), without, 1.25F, "3 overslime");
        helper.assertTrue(ForgeweaveTraits.overslime(piece) == 0, "two shield plus one wear spent, got " + ForgeweaveTraits.overslime(piece));
        helper.assertTrue(piece.getDamageValue() == 0, "the overslime paid the wear, damage " + piece.getDamageValue());
        assertRatio(helper, lost(player, explosion(helper), BLOW), without, 0.0F, "empty");
        helper.assertTrue(piece.getDamageValue() == 1, "no overslime: the wear hits durability, damage " + piece.getDamageValue());
        ForgeweaveTraits.setOverslime(piece, 2);
        assertRatio(helper, lost(player, explosion(helper), BLOW), without, 1.25F, "2 overslime: all to the shield");
        helper.assertTrue(ForgeweaveTraits.overslime(piece) == 0 && piece.getDamageValue() == 2,
                "nothing left for the wear: " + ForgeweaveTraits.overslime(piece) + " overslime, damage " + piece.getDamageValue());
        helper.succeed();
    }

    /** Bone maille -&gt; piercing_guard: the attacker loses one armor for four seconds; the piece pays one durability. */
    @GameTest(template = "empty")
    public static void piercingGuardStripsAttackerArmor(GameTestHelper helper) {
        Player player = wearing(helper, "iron", "bone");
        Zombie zombie = zombie(helper);
        int armorBefore = zombie.getArmorValue();
        lost(player, helper.getLevel().damageSources().mobAttack(zombie), 2.0F);
        helper.assertTrue(zombie.hasEffect(ForgeweaveMobEffects.PIERCE), "the attacker is pierced");
        helper.assertTrue(zombie.getArmorValue() == armorBefore - 1,
                "expected armor " + (armorBefore - 1) + ", got " + zombie.getArmorValue());
        helper.assertTrue(worn(player).getDamageValue() == 2,
                "vanilla's 1 armor damage plus the counter's 1, got " + worn(player).getDamageValue());
        zombie.discard();
        helper.succeed();
    }

    /** Cactus maille -&gt; thorns: a 15% chance per blow to hurt the attacker back and wear the piece. */
    @GameTest(template = "empty")
    public static void thornsSometimesHurtTheAttacker(GameTestHelper helper) {
        Player player = wearing(helper, "iron", "cactus");
        Zombie zombie = zombie(helper);
        float zombieBefore = zombie.getHealth();
        for (int i = 0; i < 100; i++) { // P(no proc in 100) = 0.85^100 ~ 1e-7
            zombie.invulnerableTime = 0;
            lost(player, helper.getLevel().damageSources().mobAttack(zombie), 1.0F);
        }
        helper.assertTrue(zombie.getHealth() < zombieBefore, "thorns must have hurt the attacker at least once");
        helper.assertTrue(worn(player).getDamageValue() > 100, "each proc wears the piece beyond vanilla's 1 per blow");
        zombie.discard();
        helper.succeed();
    }

    /** Chorus maille -&gt; enderclearance: a 25% chance per blow to teleport the attacker away. */
    @GameTest(template = "empty")
    public static void enderclearanceTeleportsTheAttacker(GameTestHelper helper) {
        Player player = wearing(helper, "iron", "chorus");
        Zombie zombie = zombie(helper);
        Vec3 start = zombie.position();
        for (int i = 0; i < 60 && zombie.position().distanceToSqr(start) < 0.25; i++) { // 0.75^60 ~ 3e-8
            lost(player, helper.getLevel().damageSources().mobAttack(zombie), 1.0F);
        }
        helper.assertTrue(zombie.position().distanceToSqr(start) >= 0.25, "the attacker must have been teleported");
        zombie.discard();
        helper.succeed();
    }

    /** Blue slime vine maille -&gt; skyfall: -15% gravity and +1 safe fall distance while worn. */
    @GameTest(template = "empty")
    public static void skyfallLightensGravity(GameTestHelper helper) {
        Player player = wearing(helper, "iron", "slimevine_blue");
        double gravity = player.getAttributeValue(Attributes.GRAVITY);
        helper.assertTrue(Math.abs(gravity - 0.08 * 0.85) < 1e-6, "expected 85% gravity, got " + gravity);
        double safeFall = player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE);
        helper.assertTrue(Math.abs(safeFall - 4.0) < 1e-6, "expected safe fall 4, got " + safeFall);
        helper.succeed();
    }
}
