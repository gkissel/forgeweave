package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.BleedEffect;
import dev.gkissel.forgeweave.combat.BonusDamageFraction;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.ForgeweaveMobEffects;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * docs/SCOPE.md M3 issue #150's verification (ADR-0005): the attack attributes an assembled tool
 * carries are the clone's numbers, and the shared per-hit pipeline fires each of its hooks exactly
 * once per blow.
 *
 * <p>Every tool here is assembled through a real Tool Station ({@link ToolAssembly}), so a passing
 * test is also evidence the station wrote the components the attributes derive from.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CombatGameTests {

    /**
     * ADR-0005 decisions 1 and 2: attack damage and attack speed derive from parts and land as plain
     * vanilla attribute modifiers, with the clone's per-tool numbers.
     *
     * <p>A stone head is upstream {@code TinkerMaterials}' {@code new HeadMaterialStats(120, 4.00f,
     * 3.00f, IRON)} -- 3.0 attack -- and upstream {@code ToolCore#getAttributeModifiers} puts
     * {@code getActualAttack(stack)} on {@code ATTACK_DAMAGE} and {@code getActualAttackSpeed(stack)
     * - 4} on {@code ATTACK_SPEED}. The per-tool factors are the clone's
     * {@code ToolCore#damagePotential()}/{@code #attackSpeed()}:
     *
     * <table>
     *   <tr><th>Tool</th><th>damagePotential</th><th>attackSpeed</th><th>expected damage</th></tr>
     *   <tr><td>pickaxe</td><td>1.0</td><td>1.2</td><td>3.0</td></tr>
     *   <tr><td>shovel</td><td>0.9</td><td>1.0</td><td>2.7</td></tr>
     *   <tr><td>hatchet</td><td>1.1</td><td>1.1</td><td>3.85</td></tr>
     * </table>
     *
     * <p>Handle and binding are wood, whose {@code ecological} trait touches neither attack stat, and
     * stone's head trait {@code cheapskate} only touches durability -- so what the attribute reads is the head
     * material's number times the tool's potential, plus the hatchet's own flat bonus. Upstream
     * {@code Hatchet#buildTagData}'s {@code data.attack += 0.5f} (parity audit 2026-08-18 T65, issue
     * #496) adds to the <em>stored</em> attack before {@code ToolHelper#getActualAttack} multiplies
     * the whole thing by {@code damagePotential} -- {@code ToolConstants#compute}/{@code ToolItem
     * #attackDamage} mirror that order exactly, so the bonus is scaled too: {@code (3.0 + 0.5) * 1.1 =
     * 3.85}, not {@code 3.0 * 1.1 + 0.5 = 3.8}. Pickaxe and shovel carry no such bonus, so their
     * numbers are unchanged.
     */
    @GameTest(template = "empty")
    public static void attackAttributesMatchCloneConstants(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        assertAttackAttributes(helper, player, pos, ForgeweaveItems.PART_PICKAXE_HEAD.get(), "pickaxe", 3.0, 1.2);
        assertAttackAttributes(helper, player, pos, ForgeweaveItems.PART_SHOVEL_HEAD.get(), "shovel", 2.7, 1.0);
        assertAttackAttributes(helper, player, pos, ForgeweaveItems.PART_AXE_HEAD.get(), "hatchet", 3.85, 1.1);

        helper.succeed();
    }

    /**
     * ADR-0005 decision 3: one shared pipeline, fired exactly once per hit and exactly once per kill.
     * A synthetic seam counts its three hooks across a survivable blow and then a lethal one, so a
     * duplicated listener or a hook wired to an event that fires twice would show up as a count of 2.
     *
     * <p>The blows go through {@code LivingEntity#hurt} with a real player-attack damage source --
     * the same path a swing takes once vanilla's cooldown and crit maths (which ADR-0005 leaves
     * alone) are done with it.
     */
    @GameTest(template = "empty")
    public static void seamsFireExactlyOncePerHitAndPerKill(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = ToolAssembly.tool(helper, player, pos, ForgeweaveItems.PART_AXE_HEAD.get(),
                "stone", "wood", "wood");
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        helper.assertTrue(source.getWeaponItem() == hatchet, "the blow must be attributed to the tool being tested");

        COUNTER.arm();
        try {
            target.hurt(source, 1.0F);
            helper.assertTrue(target.isAlive(), "a 1-damage blow must not kill a pig, or this proves nothing");
            COUNTER.assertCounts(helper, 1, 1, 0, "one survivable blow");

            // Vanilla's post-hit invulnerability window would swallow the second blow's own event.
            target.invulnerableTime = 0;
            target.hurt(source, 100.0F);
            helper.assertFalse(target.isAlive(), "the second blow was meant to be lethal");
            COUNTER.assertCounts(helper, 2, 2, 1, "a second, lethal blow");
        } finally {
            COUNTER.armed = false;
        }

        target.discard();
        helper.succeed();
    }

    /**
     * Issue #295 sanity check: a real, Tool-Station-assembled pickaxe whose attack damage is pushed
     * past its (default 15) cutoff shows the curved number, not the raw one, on the actual
     * {@code ATTACK_DAMAGE} attribute modifier vanilla combat reads. The 20-raw / 15-cutoff / 19.5
     * expected number is the same one {@code ToolItemTest#calcCutoffDamageMatchesUpstreamsGeometricFalloff}
     * pins directly against upstream's formula; this test only proves the wiring from a real assembled
     * stack through {@code ToolItem#getDefaultAttributeModifiers} reaches it.
     */
    @GameTest(template = "empty")
    public static void attackDamageAttributeReflectsTheCutoffCurve(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");

        // Force the assembled tool's attack past its cutoff -- easier to pin than hunting for a
        // material combination that happens to land past 15 on its own.
        ToolStats.Stats stats = pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get());
        pickaxe.set(ForgeweaveDataComponents.TOOL_STATS.get(),
                new ToolStats.Stats(stats.durability(), stats.miningSpeed(), 20.0F));

        double damage = modifier(pickaxe, Attributes.ATTACK_DAMAGE.getKey(), "pickaxe");
        helper.assertTrue(Math.abs(damage - 19.5) < 0.001,
                "20 raw attack on the pickaxe's default 15 cutoff must read 19.5 on the attribute, got " + damage);

        helper.succeed();
    }

    /**
     * Issue #422: a flat trait bonus (hellish's +4) is part of the blow vanilla scales, not a number
     * added after vanilla is done. Upstream {@code ToolHelper#attackEntity} runs {@code ITrait#damage}
     * on the base, then crit x1.5, then the cooldown factor {@code 0.2 + c^2 * 0.8}; the seam pipeline
     * receives vanilla's already-scaled amount, so it unwinds that factor, runs the seams, and re-applies
     * it. Staged as vanilla stages it: {@link AttackEntityEvent} carries the charge, {@link
     * CriticalHitEvent} the crit, and the amount handed to {@code hurt} is what {@code Player#attack}
     * would hand it for a 1.0 base -- {@code base * k}.
     */
    @GameTest(template = "empty")
    public static void flatBonusFollowsVanillaChargeAndCrit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(),
                List.of(CombatTraitGameTests.traitId("hellish")), 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Pig pig = tankPig(helper);
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        // Full charge, no crit: no swing captured for this player, k = 1 -- the +4 lands whole.
        assertLost(helper, pig, source, 1.0F, 5.0F, "a full-charge hit");

        // Full charge, crit: k = 1.5, and the bonus is inside the multiplier (upstream: crit after traits).
        NeoForge.EVENT_BUS.post(new CriticalHitEvent(player, pig, 1.5F, true));
        assertLost(helper, pig, source, 1.5F, 7.5F, "a full-charge crit");

        // Spam-click: the swing has had no time to recover, so vanilla lands the base at ~20% -- and
        // the +4 must shrink by the same factor rather than land whole.
        player.resetAttackStrengthTicker();
        float scale = player.getAttackStrengthScale(0.5F);
        helper.assertTrue(scale < 0.2F, "this test is meaningless unless the mock player's swing really is uncharged");
        float k = 0.2F + scale * scale * 0.8F;
        NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, pig)); // also clears the crit above
        assertLost(helper, pig, source, k, 5.0F * k, "a spam-clicked hit");

        pig.discard();
        helper.succeed();
    }

    /**
     * Issue #422: the tool's damage cutoff applies to the seam-boosted total, upstream's
     * {@code calcCutoffDamage(damage, tool.damageCutoff())} after {@code ITrait#damage}. A 14 base
     * plus hellish's 4 is 18 on the hatchet's default 15 cutoff: 15 + 0.9 * 3 = 17.7.
     */
    @GameTest(template = "empty")
    public static void flatBonusPastTheCutoffIsCurved(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(),
                List.of(CombatTraitGameTests.traitId("hellish")), 14.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Pig pig = tankPig(helper);

        assertLost(helper, pig, helper.getLevel().damageSources().playerAttack(player), 14.0F, 17.7F,
                "a hit pushed past the cutoff");

        pig.discard();
        helper.succeed();
    }

    /**
     * Issue #422's invariant for the fractional seams (rapier's +5%, katana's ramp, timber, superheat):
     * they scale the blow, so unwinding and re-applying vanilla's factor around them changes nothing --
     * a +50% seam on a spam-clicked hit still lands 1.5x what vanilla sent.
     */
    @GameTest(template = "empty")
    public static void fractionSeamsAreUnchangedByTheUnwind(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = ToolAssembly.tool(helper, player, new BlockPos(1, 1, 1),
                ForgeweaveItems.PART_AXE_HEAD.get(), "stone", "wood", "wood");
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Pig pig = tankPig(helper);

        player.resetAttackStrengthTicker();
        float scale = player.getAttackStrengthScale(0.5F);
        float k = 0.2F + scale * scale * 0.8F;
        NeoForge.EVENT_BUS.post(new AttackEntityEvent(player, pig));

        FRACTION.arm();
        try {
            assertLost(helper, pig, helper.getLevel().damageSources().playerAttack(player), 2.0F * k, 3.0F * k,
                    "a +50% seam on a spam-clicked hit");
        } finally {
            FRACTION.armed = false;
        }

        pig.discard();
        helper.succeed();
    }

    /**
     * Issue #422's re-entrancy check: a bleed tick credits the wielder but names no weapon, so it must
     * not run the tool's seams again -- a hellish wielder's bleed ticking for 1 + 4 was half of the
     * "effects combo" the issue reports.
     */
    @GameTest(template = "empty")
    public static void bleedTicksDoNotFireSeams(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = ToolAssembly.tool(helper, player, new BlockPos(1, 1, 1),
                ForgeweaveItems.PART_AXE_HEAD.get(), "stone", "wood", "wood");
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Pig pig = tankPig(helper);

        COUNTER.arm();
        try {
            pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
            COUNTER.assertCounts(helper, 1, 1, 0, "the blow that leaves the bleed");
            helper.assertTrue(pig.getLastHurtByMob() == player, "the tick below must be crediting the wielder to prove anything");
            float before = pig.getHealth();
            ((BleedEffect) ForgeweaveMobEffects.BLEED.value()).applyEffectTick(pig, 0);
            helper.assertTrue(pig.getHealth() < before, "the bleed tick must still land");
            COUNTER.assertCounts(helper, 1, 1, 0, "a bleed tick");
        } finally {
            COUNTER.armed = false;
        }

        pig.discard();
        helper.succeed();
    }

    /** A pig that survives every blow staged here: 100 max health, no armor, no AI. */
    private static Pig tankPig(GameTestHelper helper) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.setNoAi(true);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100.0);
        pig.setHealth(100.0F);
        return pig;
    }

    /** Lands {@code amount} on a healed, non-invulnerable {@code pig} and asserts it lost {@code expected}. */
    private static void assertLost(GameTestHelper helper, Pig pig, DamageSource source, float amount, float expected,
            String what) {
        pig.setHealth(pig.getMaxHealth());
        pig.invulnerableTime = 0;
        pig.hurt(source, amount);
        float lost = pig.getMaxHealth() - pig.getHealth();
        helper.assertTrue(Math.abs(lost - expected) < 0.01F,
                "expected " + what + " to land " + expected + ", landed " + lost);
    }

    /** Assembles {@code headPart} from stone/wood/wood and checks both attack attributes on it. */
    private static void assertAttackAttributes(GameTestHelper helper, Player player, BlockPos pos, Item headPart,
            String name, double expectedDamage, double expectedAttackSpeed) {
        ItemStack tool = ToolAssembly.tool(helper, player, pos, headPart, "stone", "wood", "wood");

        double damage = modifier(tool, Attributes.ATTACK_DAMAGE.getKey(), name);
        helper.assertTrue(Math.abs(damage - expectedDamage) < 0.001,
                "expected the " + name + "'s attack damage modifier to read " + expectedDamage + ", got " + damage);

        // Vanilla's ATTACK_SPEED attribute has a base of 4 attacks/second and the item modifier is the
        // offset from it -- upstream 1.12 writes the same `attackSpeed() - 4` (ToolCore).
        double speed = modifier(tool, Attributes.ATTACK_SPEED.getKey(), name);
        helper.assertTrue(Math.abs(speed - (expectedAttackSpeed - 4.0)) < 0.001,
                "expected the " + name + "'s attack speed modifier to read " + (expectedAttackSpeed - 4.0)
                        + " (" + expectedAttackSpeed + " attacks/second), got " + speed);
    }

    /** The single {@code ADD_VALUE} modifier {@code tool} carries for the given vanilla attribute. */
    private static double modifier(ItemStack tool, ResourceKey<Attribute> attribute, String name) {
        Double found = null;
        for (ItemAttributeModifiers.Entry entry : tool.getAttributeModifiers().modifiers()) {
            if (entry.attribute().is(attribute)
                    && entry.modifier().operation() == AttributeModifier.Operation.ADD_VALUE) {
                if (found != null) {
                    throw new AssertionError("the " + name + " carries more than one " + attribute.location() + " modifier");
                }
                found = entry.modifier().amount();
            }
        }
        if (found == null) {
            throw new AssertionError("no " + attribute.location() + " modifier on the " + name);
        }
        return found;
    }

    /**
     * The synthetic consumer. Registered as a {@link CombatSeams.Provider} once and handed out only
     * while {@link #armed}, so it never rides a real player's hit outside this test -- the gametest
     * package ships in the jar (see {@code build.gradle}'s jar excludes for the datapack half).
     *
     * <p>The arm/disarm pair opens and closes inside one test body, which the server runs start to
     * finish within a single tick, so no concurrently-scheduled GameTest can land a blow of its own
     * in the counts.
     */
    private static final class CountingSeam implements CombatSeam {
        private boolean registered;
        private boolean armed;
        private int preHits;
        private int onHits;
        private int kills;

        void arm() {
            if (!registered) {
                registered = true;
                CombatSeams.register((weapon, out) -> {
                    if (armed) {
                        out.accept(this);
                    }
                });
            }
            armed = true;
            preHits = 0;
            onHits = 0;
            kills = 0;
        }

        void assertCounts(GameTestHelper helper, int expectedPreHits, int expectedOnHits, int expectedKills, String after) {
            helper.assertTrue(preHits == expectedPreHits,
                    "expected " + expectedPreHits + " pre-hit call(s) after " + after + ", got " + preHits);
            helper.assertTrue(onHits == expectedOnHits,
                    "expected " + expectedOnHits + " on-hit call(s) after " + after + ", got " + onHits);
            helper.assertTrue(kills == expectedKills,
                    "expected " + expectedKills + " post-kill call(s) after " + after + ", got " + kills);
        }

        @Override
        public float preHit(CombatHit hit, float originalDamage, float damage) {
            preHits++;
            return damage;
        }

        @Override
        public void onHit(CombatHit hit, float damageDealt) {
            onHits++;
        }

        @Override
        public void postKill(CombatHit hit) {
            kills++;
        }
    }

    private static final CountingSeam COUNTER = new CountingSeam();

    /** A +50% seam handed out only while armed, same arm/disarm contract as {@link CountingSeam}. */
    private static final class ArmedFraction {
        private final CombatSeam seam = new BonusDamageFraction(0.5F);
        private boolean registered;
        private boolean armed;

        void arm() {
            if (!registered) {
                registered = true;
                CombatSeams.register((weapon, out) -> {
                    if (armed) {
                        out.accept(seam);
                    }
                });
            }
            armed = true;
        }
    }

    private static final ArmedFraction FRACTION = new ArmedFraction();
}
