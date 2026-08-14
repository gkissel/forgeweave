package dev.gkissel.forgeweave.gametest;

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

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
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
     *   <tr><td>hatchet</td><td>1.1</td><td>1.1</td><td>3.3</td></tr>
     * </table>
     *
     * <p>Handle and binding are wood, whose {@code ecological} trait touches neither attack stat, and
     * stone's {@code cheap} only touches durability -- so what the attribute reads is the head
     * material's number times the tool's potential, nothing else.
     */
    @GameTest(template = "empty")
    public static void attackAttributesMatchCloneConstants(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        assertAttackAttributes(helper, player, pos, ForgeweaveItems.PART_PICKAXE_HEAD.get(), "pickaxe", 3.0, 1.2);
        assertAttackAttributes(helper, player, pos, ForgeweaveItems.PART_SHOVEL_HEAD.get(), "shovel", 2.7, 1.0);
        assertAttackAttributes(helper, player, pos, ForgeweaveItems.PART_AXE_HEAD.get(), "hatchet", 3.3, 1.1);

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
}
