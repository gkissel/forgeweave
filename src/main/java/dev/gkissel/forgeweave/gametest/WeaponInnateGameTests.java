package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.combat.ForgeweaveMobEffects;
import dev.gkissel.forgeweave.combat.LacerateEffect;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * docs/SCOPE.md M3 issue #159's verification: the battleaxe's sweeping heavy blow and the scimitar's
 * lacerate behave exactly as the maintainer decided on 2026-08-12 --
 *
 * <ul>
 *   <li><b>battleaxe</b>: a full-charge hit strikes all enemies in a short arc for 50% damage and
 *       applies slowness I (1.5s) to the primary target;
 *   <li><b>scimitar</b>: 1 damage per second for 4 seconds per application, stacking up to 3
 *       concurrent bleeds.
 * </ul>
 *
 * <p>Both weapons are assembled through a real Tool Station, so a passing test is also evidence the
 * station accepts the new part sets (the scimitar's cross guard, the battleaxe's <em>two</em> broad
 * axe heads in one slot) and writes the stat block {@link ToolConstants} describes.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class WeaponInnateGameTests {

    private static final String STONE = "stone";
    private static final String WOOD = "wood";

    /**
     * Both tools come out of the station carrying {@link ToolConstants}' numbers.
     *
     * <p>A stone head is upstream's {@code HeadMaterialStats(120, 4.00f, 3.00f, IRON)} -- 3.0 attack
     * -- and neither wood's {@code ecological} nor stone's {@code cheap} touches attack, so what the
     * attribute reads is the formula and nothing else:
     *
     * <table>
     *   <tr><th>Tool</th><th>formula</th><th>expected damage</th><th>attack speed</th></tr>
     *   <tr><td>battleaxe</td><td>(3.0 + 3.0) x 1.2, two heads summed</td><td>7.2</td><td>0.95</td></tr>
     *   <tr><td>scimitar</td><td>3.0 + 2.5 flat</td><td>5.5</td><td>1.8</td></tr>
     * </table>
     */
    @GameTest(template = "empty")
    public static void battleaxeAndScimitarCarryTheirToolConstants(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        assertAttackAttributes(helper, battleaxe(helper, player, pos), "battleaxe", 7.2,
                ToolConstants.BATTLEAXE.attackSpeed());
        assertAttackAttributes(helper, scimitar(helper, player, pos), "scimitar", 5.5,
                ToolConstants.SCIMITAR.attackSpeed());

        helper.succeed();
    }

    /**
     * The sweeping heavy blow: every enemy in the wedge in front of the swing takes half of what the
     * primary target did, the primary takes slowness I for 1.5s, and something outside the wedge is
     * left alone (which is what makes it an arc rather than a radius).
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void battleaxeSweepStrikesTheArcAtHalfDamage(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack tool = battleaxe(helper, player, stationPos);
        player.setItemSlot(EquipmentSlot.MAINHAND, tool);

        // Facing +Z (yaw 0), so the wedge covers the three pigs ahead and not the one behind.
        BlockPos origin = new BlockPos(3, 2, 3);
        player.moveTo(helper.absoluteVec(new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5)));
        player.setYRot(0.0F);
        player.setYHeadRot(0.0F);
        player.setXRot(0.0F);

        Pig primary = pig(helper, origin.offset(0, 0, 2));
        Pig flankLeft = pig(helper, origin.offset(-1, 0, 2));
        Pig flankRight = pig(helper, origin.offset(1, 0, 2));
        Pig behind = pig(helper, origin.offset(0, 0, -2));

        float blow = 4.0F;
        // The sweep's wedge lookup is an entity-index query; wait until the index serves the pigs
        // before swinging (see SpawnCapture -- same defect class as magnetic's zero pull).
        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, primary, flankLeft, flankRight, behind))
                .thenExecute(() -> {
                    primary.hurt(helper.getLevel().damageSources().playerAttack(player), blow);

                    assertHealthLost(helper, flankLeft, blow * 0.5F, "the pig to the left of the swing");
                    assertHealthLost(helper, flankRight, blow * 0.5F, "the pig to the right of the swing");
                    assertHealthLost(helper, behind, 0.0F, "the pig behind the swing");

                    MobEffectInstance slow = primary.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    helper.assertTrue(slow != null, "expected the primary target to be slowed by the sweeping blow");
                    helper.assertTrue(slow.getAmplifier() == 0,
                            "expected slowness I on the primary target, got amplifier " + slow.getAmplifier());
                    helper.assertTrue(slow.getDuration() == ForgeweaveInnates.SWEEPING_BLOW_SEAM.primaryEffectTicks(),
                            "expected the decided 1.5s of slowness, got " + slow.getDuration() + " ticks");

                    discard(primary, flankLeft, flankRight, behind);
                })
                .thenSucceed();
    }

    /**
     * The decision says <em>full-charge</em> hits sweep. A mock player has never charged its attack,
     * so posting the attack event vanilla posts (which is where {@code CombatSeams} captures the
     * charge, before {@code Player#attack} zeroes it) proves a spam-clicked hit does not sweep.
     */
    @GameTest(template = "empty", timeoutTicks = 1200)
    public static void battleaxeSweepNeedsAFullCharge(GameTestHelper helper) {
        BlockPos stationPos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemSlot(EquipmentSlot.MAINHAND, battleaxe(helper, player, stationPos));

        BlockPos origin = new BlockPos(3, 2, 3);
        player.moveTo(helper.absoluteVec(new Vec3(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5)));
        player.setYRot(0.0F);
        player.setYHeadRot(0.0F);

        Pig primary = pig(helper, origin.offset(0, 0, 2));
        Pig flank = pig(helper, origin.offset(1, 0, 2));

        // Same pre-wait as the arc test: without an index that can serve the flank, "the sweep hit
        // nobody" would pass vacuously here rather than prove the charge gate.
        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, primary, flank))
                .thenExecute(() -> {
                    player.resetAttackStrengthTicker(); // scale ~0: the swing has had no time to recover
                    helper.assertTrue(player.getAttackStrengthScale(0.5F) < 0.9F,
                            "this test is meaningless unless the mock player's swing really is uncharged");
                    NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, primary));
                    primary.hurt(helper.getLevel().damageSources().playerAttack(player), 4.0F);

                    assertHealthLost(helper, flank, 0.0F, "a bystander of an uncharged swing");
                    helper.assertTrue(primary.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == null,
                            "an uncharged swing must not slow its target either");

                    discard(primary, flank);
                })
                .thenSucceed();
        helper.succeed();
    }

    /** Lacerate deepens per hit and stops at the decided three concurrent bleeds. */
    @GameTest(template = "empty")
    public static void lacerateStacksToThreeAndNoFurther(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tool = scimitar(helper, player, pos);
        player.setItemSlot(EquipmentSlot.MAINHAND, tool);
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        Pig target = pig(helper, new BlockPos(3, 2, 3));
        target.setInvulnerable(false);

        for (int hit = 1; hit <= LacerateEffect.MAX_STACKS + 2; hit++) {
            target.invulnerableTime = 0;
            target.setHealth(target.getMaxHealth()); // the bleed itself is not what this test measures
            target.hurt(source, 1.0F);

            MobEffectInstance bleed = target.getEffect(ForgeweaveMobEffects.LACERATE);
            helper.assertTrue(bleed != null, "expected hit " + hit + " to apply lacerate");
            int expected = Math.min(hit, LacerateEffect.MAX_STACKS);
            helper.assertTrue(LacerateEffect.stacks(bleed.getAmplifier()) == expected,
                    "expected " + expected + " bleed(s) after hit " + hit + ", got "
                            + LacerateEffect.stacks(bleed.getAmplifier()));
            helper.assertTrue(bleed.getDuration() == LacerateEffect.DURATION_TICKS,
                    "expected every hit to refresh the bleed to " + LacerateEffect.DURATION_TICKS
                            + " ticks, got " + bleed.getDuration());
        }

        target.discard();
        helper.succeed();
    }

    /**
     * One application bleeds for exactly the decided 4 seconds: 1 damage a second, four times, and
     * then it is over -- so the test also fails if the effect were to tick a fifth time or expire
     * early.
     */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void lacerateBleedsOneDamagePerSecondForFourSeconds(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.MAINHAND, scimitar(helper, player, pos));

        Pig target = pig(helper, new BlockPos(3, 2, 3));
        target.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        float healthAfterBlow = target.getHealth();
        helper.assertTrue(target.getEffect(ForgeweaveMobEffects.LACERATE) != null,
                "the blow was supposed to apply lacerate");

        // The cadence and total are driven through the effect's own tick contract rather than world
        // ticks: a GameTest plot far from spawn is not reliably entity-ticking, so a test that waits
        // on the level to tick the pig's effects hangs forever on some runs (issue #212 -- the only
        // effect test that needs ticks, not just presence; the seam application above stays real).
        LacerateEffect effect = (LacerateEffect) ForgeweaveMobEffects.LACERATE.value();
        int fires = 0;
        for (int duration = LacerateEffect.DURATION_TICKS; duration >= 1; duration--) {
            if (effect.shouldApplyEffectTickThisTick(duration, 0)) {
                target.invulnerableTime = 0; // each simulated fire is a full second apart in game time
                effect.applyEffectTick(target, 0);
                fires++;
            }
        }
        helper.assertTrue(fires == 4, "1 damage per second for 4 seconds must fire exactly 4 times, fired " + fires);
        assertBled(helper, target, healthAfterBlow, LacerateEffect.DURATION_TICKS / 20.0F, "the full four seconds");
        target.discard();
        helper.succeed();
    }

    private static void assertBled(GameTestHelper helper, Pig target, float healthAfterBlow, float expected, String after) {
        float lost = healthAfterBlow - target.getHealth();
        helper.assertTrue(Math.abs(lost - expected) < 0.001F,
                "expected " + expected + " bleed damage after " + after + ", got " + lost);
    }


    // ---------------------------------------------------------------- fixtures

    /**
     * A battleaxe from a real Tool <em>Forge</em> -- issue #336 put it in the Tool Forge tier, so a
     * Tool Station now refuses it and this fixture would come back empty. Four slots in {@code
     * ToolConstants.BATTLEAXE}'s own part order -- wooden tough rod, two stone broad axe heads,
     * wooden tough binding. The two heads take a slot each since #155's N-part assembly, so they are
     * two independent materials; this fixture gives them the same one because every stat this file
     * asserts is a head-average.
     */
    private static ItemStack battleaxe(GameTestHelper helper, Player player, BlockPos pos) {
        ItemStack assembled = ToolAssembly.assembleAtForge(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_BATTLEAXE.get()), List.of(WOOD, STONE, STONE, WOOD));
        helper.assertTrue(!assembled.isEmpty(), "the Tool Forge produced nothing for the battleaxe's parts");
        return assembled;
    }

    /** A scimitar from a real Tool Station: wooden handle, stone curved blade, wooden cross guard. */
    private static ItemStack scimitar(GameTestHelper helper, Player player, BlockPos pos) {
        return assemble(helper, player, pos, ForgeweaveItems.TOOL_SCIMITAR.get(),
                List.of(WOOD, STONE, WOOD));
    }

    /** One material name per part slot, in the tool's own order -- {@link ToolAssembly#assemble}. */
    private static ItemStack assemble(GameTestHelper helper, Player player, BlockPos pos, ToolItem tool,
            List<String> materials) {
        ItemStack assembled = ToolAssembly.assemble(helper, player, pos, ToolAssembly.entryFor(tool), materials);
        helper.assertTrue(!assembled.isEmpty(), "the Tool Station produced nothing for " + tool + "'s parts");
        return assembled;
    }

    /** A gravity-free pig, so a multi-second test isn't measuring fall damage or a fall out of the world. */
    private static Pig pig(GameTestHelper helper, BlockPos pos) {
        Pig pig = helper.spawn(EntityType.PIG, pos);
        pig.setNoGravity(true);
        pig.setNoAi(true);
        return pig;
    }

    private static void assertHealthLost(GameTestHelper helper, LivingEntity entity, float expected, String who) {
        float lost = entity.getMaxHealth() - entity.getHealth();
        helper.assertTrue(Math.abs(lost - expected) < 0.001F,
                "expected " + who + " to lose " + expected + " health, it lost " + lost);
    }

    private static void discard(Entity... entities) {
        for (Entity entity : entities) {
            entity.discard();
        }
    }

    /** Same two attribute assertions {@code CombatGameTests} makes, against a given assembled tool. */
    private static void assertAttackAttributes(GameTestHelper helper, ItemStack tool, String name,
            double expectedDamage, double expectedAttackSpeed) {
        double damage = modifier(tool, Attributes.ATTACK_DAMAGE.getKey(), name);
        helper.assertTrue(Math.abs(damage - expectedDamage) < 0.001,
                "expected the " + name + "'s attack damage modifier to read " + expectedDamage + ", got " + damage);

        double speed = modifier(tool, Attributes.ATTACK_SPEED.getKey(), name);
        helper.assertTrue(Math.abs(speed - (expectedAttackSpeed - 4.0)) < 0.001,
                "expected the " + name + "'s attack speed modifier to read " + (expectedAttackSpeed - 4.0)
                        + " (" + expectedAttackSpeed + " attacks/second), got " + speed);
    }

    private static double modifier(ItemStack tool, ResourceKey<Attribute> attribute, String name) {
        List<Double> found = tool.getAttributeModifiers().modifiers().stream()
                .filter(entry -> entry.attribute().is(attribute)
                        && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE)
                .map(entry -> entry.modifier().amount())
                .toList();
        if (found.size() != 1) {
            throw new AssertionError("expected exactly one " + attribute.location() + " modifier on the " + name
                    + ", found " + found.size());
        }
        return found.get(0);
    }
}
