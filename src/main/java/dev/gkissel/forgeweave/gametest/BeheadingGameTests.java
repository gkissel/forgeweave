package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.combat.Beheading;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * docs/SCOPE.md M3 issue #158's verification: the beheading system, on the shared post-kill seam.
 *
 * <p>Kills go through {@code LivingEntity#hurt} with a real player-attack damage source naming the
 * tool, the same path {@code CombatGameTests} uses -- so what these tests exercise is the whole
 * chain {@code LivingDeathEvent -> CombatSeams#onDeath -> Beheading#postKill}, not the utility in
 * isolation.
 *
 * <p>Every kill here is done with an effective level of {@link Beheading#CHANCE_DENOMINATOR} or more,
 * which upstream's own {@code level > random.nextInt(10)} makes certain -- that is the force-chance
 * idiom, no seeded random needed. The one test that is about the chance curve itself
 * ({@link #higherLevelRaisesDropChance}) drives the pure roll off a fixed-seed {@link RandomSource}
 * instead of counting corpses.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class BeheadingGameTests {

    /** Certain-drop level: upstream's roll is {@code level > random.nextInt(10)}. */
    private static final int CERTAIN = Beheading.CHANCE_DENOMINATOR;

    /**
     * Each of the six vanilla head items drops. Five of them are killed for real; the ender dragon is
     * a boss whose hurt path only accepts damage through its own body parts and which does not fit in
     * a GameTest structure, so its row of the table is asserted directly -- see
     * {@link Beheading#headDropFor}'s javadoc.
     */
    @GameTest(template = "empty")
    public static void everyVanillaHeadTypeDrops(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, beheadingTool(CERTAIN));

        assertBeheads(helper, player, EntityType.SKELETON, Items.SKELETON_SKULL);
        assertBeheads(helper, player, EntityType.WITHER_SKELETON, Items.WITHER_SKELETON_SKULL);
        assertBeheads(helper, player, EntityType.ZOMBIE, Items.ZOMBIE_HEAD);
        assertBeheads(helper, player, EntityType.CREEPER, Items.CREEPER_HEAD);
        assertBeheads(helper, player, EntityType.PIGLIN, Items.PIGLIN_HEAD);

        helper.assertTrue(Beheading.headDropFor(EntityType.ENDER_DRAGON).is(Items.DRAGON_HEAD),
                "the ender dragon must map to a dragon head");

        helper.succeed();
    }

    /** docs/SCOPE.md M3: a mob with no vanilla head item drops nothing, however high the level. */
    @GameTest(template = "empty")
    public static void headlessMobDropsNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, beheadingTool(CERTAIN));

        helper.assertTrue(Beheading.headDropFor(EntityType.PIG).isEmpty(), "a pig has no head item");
        withoutMobLoot(helper, () -> {
            List<ItemEntity> drops = SpawnCapture.spawnedDuring(helper, ItemEntity.class,
                    () -> kill(helper, player, EntityType.PIG));
            helper.assertTrue(drops.isEmpty(),
                    "a beheaded pig must drop nothing at all, got " + drops.size() + " item(s)");
        });

        helper.succeed();
    }

    /** A PvP kill drops the victim's own head, carrying their game profile so it renders as them. */
    @GameTest(template = "empty")
    public static void playerHeadCarriesVictimProfile(GameTestHelper helper) {
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, beheadingTool(CERTAIN));
        // Both sides are mock Players in the test's own ServerLevel, moved into the structure so the
        // head lands where {@link #drops} looks.
        //
        // ponytail: not GameTestHelper#makeMockServerPlayerInLevel's real ServerPlayer, which cannot be
        // killed inside a single-tick test at all -- its `spawnInvulnerableTime` is a private field that
        // only `tick()` decrements, so a real login is immune for its first 60 ticks (and ServerPlayer
        // additionally refuses any player-sourced blow while the server has PvP off). What the head drop
        // actually keys off is `target instanceof Player` and that player's game profile
        // ({@code Beheading#headDrop}), and a mock Player has both.
        Player victim = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 stand = helper.absoluteVec(new BlockPos(2, 2, 2).getCenter());
        victim.moveTo(stand.x, stand.y, stand.z);

        withoutMobLoot(helper, () -> {
            // Captured at the spawn seam, not queried back out of the level: see SpawnCapture (this
            // exact assertion flaked CI-only when the plot landed millions of blocks out and the
            // just-spawned head was not yet visible to the entity index).
            List<ItemEntity> drops = SpawnCapture.spawnedDuring(helper, ItemEntity.class, () -> {
                victim.hurt(helper.getLevel().damageSources().playerAttack(attacker), 1000.0F);
                helper.assertFalse(victim.isAlive(), "the blow was meant to kill the victim");
            });

            ItemStack head = drops.stream()
                    .map(ItemEntity::getItem)
                    .filter(stack -> stack.is(Items.PLAYER_HEAD))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("a beheaded player must drop a player head"));
            ResolvableProfile profile = head.get(DataComponents.PROFILE);
            helper.assertTrue(profile != null, "the dropped player head must carry a profile");
            helper.assertTrue(profile.gameProfile().equals(victim.getGameProfile()),
                    "the dropped player head must carry the victim's profile, got " + profile.gameProfile());
        });

        helper.succeed();
    }

    /**
     * The chance curve: upstream {@code level > random.nextInt(10)}, so over a fixed sequence of rolls
     * a higher level must behead strictly more often, and level 10 must never miss. Driven off a
     * fixed-seed {@link RandomSource} so the assertion is exact rather than statistical.
     */
    @GameTest(template = "empty")
    public static void higherLevelRaisesDropChance(GameTestHelper helper) {
        int trials = 1000;
        int previous = -1;
        for (int level = 0; level <= CERTAIN; level++) {
            RandomSource random = RandomSource.create(158L);
            int hits = 0;
            for (int trial = 0; trial < trials; trial++) {
                if (Beheading.rollsHead(level, random)) {
                    hits++;
                }
            }
            helper.assertTrue(hits > previous,
                    "beheading " + level + " must roll a head more often than " + (level - 1)
                            + ", got " + hits + " vs " + previous + " out of " + trials);
            previous = hits;
        }
        helper.assertTrue(previous == trials,
                "beheading " + CERTAIN + " must never miss, got " + previous + " out of " + trials);

        helper.succeed();
    }

    /**
     * The cleaver's innate levels stack with the applied modifier, which is the observable result of
     * upstream's {@code ModBeheading#applyEffect} absorbing its {@code beheading_cleaver} id -- see
     * {@link Beheading}'s class javadoc.
     */
    @GameTest(template = "empty")
    public static void cleaverInnateStacksWithModifier(GameTestHelper helper) {
        ItemStack bareCleaver = new ItemStack(ForgeweaveItems.TOOL_CLEAVER.get());
        helper.assertTrue(Beheading.effectiveLevel(bareCleaver) == Beheading.CLEAVER_INNATE_LEVELS,
                "an unmodified cleaver must behead at its innate " + Beheading.CLEAVER_INNATE_LEVELS
                        + ", got " + Beheading.effectiveLevel(bareCleaver));

        ItemStack modifiedCleaver = new ItemStack(ForgeweaveItems.TOOL_CLEAVER.get());
        modifiedCleaver.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(Beheading.MODIFIER_ID, 3)));
        helper.assertTrue(Beheading.effectiveLevel(modifiedCleaver) == Beheading.CLEAVER_INNATE_LEVELS + 3,
                "three applied levels on a cleaver must stack onto its innate two, got "
                        + Beheading.effectiveLevel(modifiedCleaver));

        ItemStack modifiedHatchet = new ItemStack(ForgeweaveItems.TOOL_HATCHET.get());
        modifiedHatchet.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(Beheading.MODIFIER_ID, 3)));
        helper.assertTrue(Beheading.effectiveLevel(modifiedHatchet) == 3,
                "a tool with no innate beheading must read only its applied levels, got "
                        + Beheading.effectiveLevel(modifiedHatchet));

        // And the innate alone is enough to actually behead: two levels is a 20% chance, so this
        // proves the wiring rather than the odds -- the odds are higherLevelRaisesDropChance's job.
        helper.assertTrue(Beheading.rollsHead(Beheading.CLEAVER_INNATE_LEVELS, fixedRoll(0)),
                "a bare cleaver must behead on a roll its innate level beats");
        helper.assertFalse(Beheading.rollsHead(Beheading.CLEAVER_INNATE_LEVELS, fixedRoll(9)),
                "a bare cleaver must not behead on a roll its innate level does not beat");

        helper.succeed();
    }

    /**
     * The whole chain through a real station: a Tool Forge-assembled tool, obsidian applied through the
     * station's own menu (so {@code modifier_recipe/beheading.json} is what set the level), and a kill
     * that drops a head.
     */
    @GameTest(template = "empty")
    public static void obsidianAppliedAtTheStationBeheads(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = ToolAssembly.toolAtForge(helper, player, pos, ForgeweaveItems.PART_AXE_HEAD.get(),
                "stone", "wood", "wood");

        ItemStack beheading = applyReagent(helper, player, pos, hatchet, new ItemStack(Items.OBSIDIAN, CERTAIN));
        helper.assertTrue(Beheading.effectiveLevel(beheading) == CERTAIN,
                "ten obsidian must buy ten beheading levels, got " + Beheading.effectiveLevel(beheading));

        player.setItemInHand(InteractionHand.MAIN_HAND, beheading);
        assertBeheads(helper, player, EntityType.ZOMBIE, Items.ZOMBIE_HEAD);

        helper.succeed();
    }

    /** One application through the station's own menu; same shape as {@code ModifierGameTests}'s. */
    private static ItemStack applyReagent(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool,
            ItemStack reagent) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, reagent);
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "expected the station to apply the obsidian");
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    /** Kills one {@code type} with whatever the player is holding and asserts the expected head dropped. */
    private static void assertBeheads(GameTestHelper helper, Player player, EntityType<? extends LivingEntity> type,
            Item expected) {
        withoutMobLoot(helper, () -> {
            // The drop is captured at the spawn seam rather than queried from the entity index,
            // which lags behind synchronous spawns in a freshly-loaded plot (see SpawnCapture).
            long heads = SpawnCapture.spawnedDuring(helper, ItemEntity.class, () -> kill(helper, player, type))
                    .stream().filter(drop -> drop.getItem().is(expected)).count();
            helper.assertTrue(heads == 1,
                    "expected exactly one " + expected + " from a beheaded " + type.getDescriptionId()
                            + ", got " + heads);
        });
    }

    private static void kill(GameTestHelper helper, Player player, EntityType<? extends LivingEntity> type) {
        LivingEntity target = helper.spawn(type, new BlockPos(2, 2, 2));
        target.hurt(helper.getLevel().damageSources().playerAttack(player), 1000.0F);
        helper.assertFalse(target.isAlive(), "the blow was meant to kill the " + type.getDescriptionId());
    }

    /**
     * Runs {@code body} with {@code doMobLoot} off and every loose item cleared first and after, so the
     * only thing a kill can leave on the ground is the beheading drop -- {@code Entity#spawnAtLocation}
     * is not gated on that rule. Without it a wither skeleton's own 2.5% skull would make
     * {@link #assertBeheads} flaky, and a zombie's rotten flesh would make
     * {@link #headlessMobDropsNothing}'s "nothing at all" untestable.
     */
    private static void withoutMobLoot(GameTestHelper helper, Runnable body) {
        GameRules rules = helper.getLevel().getGameRules();
        boolean previous = rules.getBoolean(GameRules.RULE_DOMOBLOOT);
        rules.getRule(GameRules.RULE_DOMOBLOOT).set(false, helper.getLevel().getServer());
        try {
            drops(helper).forEach(Entity::discard);
            body.run();
        } finally {
            drops(helper).forEach(Entity::discard);
            rules.getRule(GameRules.RULE_DOMOBLOOT).set(previous, helper.getLevel().getServer());
        }
    }

    /** Every loose item in (and just around) the test structure. */
    private static List<ItemEntity> drops(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0));
    }

    /** A tool with {@code level} applied beheading and no innate of its own. */
    private static ItemStack beheadingTool(int level) {
        ItemStack hatchet = new ItemStack(ForgeweaveItems.TOOL_HATCHET.get());
        hatchet.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(Beheading.MODIFIER_ID, level)));
        return hatchet;
    }

    /** A {@link RandomSource} whose {@code nextInt} always answers {@code value}. */
    private static RandomSource fixedRoll(int value) {
        return new FixedRandom(value);
    }

    private static final class FixedRandom extends LegacyRandomSource {
        private final int value;

        FixedRandom(int value) {
            super(0L);
            this.value = value;
        }

        @Override
        public int nextInt(int bound) {
            return value;
        }
    }
}
