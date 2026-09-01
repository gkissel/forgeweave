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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.AlienProgress;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.WarMemory;

/**
 * Issue #884 (the TAIGA-faithful trait pass): one GameTest per new or changed trait id in the
 * batch, same hand-built-tool pattern as {@link DedupeBatchGameTests}, whose {@code holding}/{@code
 * pickaxe}/{@code traitId} helpers this class copies rather than shares (that class is scoped to
 * #876's own batch).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class Issue884GameTests {

    /** Basalt -&gt; earthmend: mining dirt-like blocks eventually heals the wielder, wellspring's sibling. */
    @GameTest(template = "empty")
    public static void earthmendHealsTheWielderFromDirt(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("earthmend")));
        player.setHealth(10.0F);
        BlockPos pos = new BlockPos(1, 1, 1);

        for (int i = 0; i < 200 && player.getHealth() <= 10.0F; i++) {
            helper.setBlock(pos, Blocks.DIRT);
            player.gameMode.destroyBlock(helper.absolutePos(pos));
        }

        helper.assertTrue(player.getHealth() > 10.0F,
                "earthmend should have healed the wielder over 200 mined dirt blocks, still at " + player.getHealth());
        helper.succeed();
    }

    /** Murkiron -&gt; duskgrasp: a landed hit applies the Darkness effect to the target. */
    @GameTest(template = "empty")
    public static void duskgraspAppliesDarkness(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of());
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        CombatHit hit = new CombatHit(helper.getLevel(), player.getMainHandItem(), player, target,
                helper.getLevel().damageSources().generic());

        List<CombatSeam> seams = new ArrayList<>();
        ForgeweaveTraits.DUSKGRASP.combatSeams(seams::add);
        for (CombatSeam seam : seams) {
            seam.onHit(hit, 1.0F);
        }

        helper.assertTrue(target.hasEffect(MobEffects.DARKNESS), "duskgrasp must apply Darkness to the target on hit");
        helper.succeed();
    }

    /** Hardcinder -&gt; leanharvest: mined blocks sometimes drop nothing, but always grant bonus XP. */
    @GameTest(template = "empty")
    public static void leanharvestTradesDropsForXp(GameTestHelper helper) {
        helper.assertTrue(ForgeweaveTraits.LEANHARVEST.dropDestroyChance() > 0.0F,
                "leanharvest must report a positive drop-destroy chance");
        int xp = ForgeweaveTraits.LEANHARVEST.blockBreakExperience(RandomSource.create(), 0);
        helper.assertTrue(xp > 0, "leanharvest must grant bonus block-break XP, got " + xp);
        helper.succeed();
    }

    /** Warspar -&gt; warmemory: repeated fights against one entity type grow that type's bonus damage. */
    @GameTest(template = "empty")
    public static void warmemoryGrowsBonusDamageWithRepeatedFights(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = holding(helper, List.of(traitId("warmemory")));
        ItemStack sword = player.getMainHandItem();
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));

        float freshBonus = ForgeweaveTraits.WARMEMORY.bonusDamageAgainst(sword, target, 5.0F);
        helper.assertTrue(freshBonus == 0.0F, "an unfought entity type must carry no bonus yet, got " + freshBonus);

        for (int i = 0; i < 10; i++) {
            ForgeweaveTraits.WARMEMORY.afterHit(sword, level, player, target);
        }
        float grownBonus = ForgeweaveTraits.WARMEMORY.bonusDamageAgainst(sword, target, 5.0F);

        helper.assertTrue(grownBonus > freshBonus,
                "warmemory's bonus after 10 fights (" + grownBonus + ") must exceed the fresh bonus (" + freshBonus + ")");
        WarMemory memory = sword.get(ForgeweaveDataComponents.WAR_MEMORY.get());
        helper.assertTrue(memory != null && memory.count(ResourceLocation.parse("minecraft:zombie")) == 10,
                "expected 10 recorded zombie fights, got " + memory);
        helper.succeed();
    }

    /** Hollowstone -&gt; hollowyield: mined blocks always drop nothing but grant bonus XP instead. */
    @GameTest(template = "empty")
    public static void hollowyieldAlwaysTradesLootForXp(GameTestHelper helper) {
        helper.assertTrue(ForgeweaveTraits.HOLLOWYIELD.dropDestroyChance() == 1.0F,
                "hollowyield must always destroy loot (chance 1.0), got " + ForgeweaveTraits.HOLLOWYIELD.dropDestroyChance());
        int xp = ForgeweaveTraits.HOLLOWYIELD.blockBreakExperience(RandomSource.create(), 0);
        helper.assertTrue(xp > 0, "hollowyield must grant bonus block-break XP, got " + xp);
        helper.succeed();
    }

    /** Starfall stone -&gt; swiftdig: faster on soft (no-tool-needed) blocks, unchanged on hard ones. */
    @GameTest(template = "empty")
    public static void swiftdigSpeedsUpOnlySoftBlocks(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("swiftdig")));
        ItemStack pickaxe = player.getMainHandItem();
        BlockState soft = Blocks.DIRT.defaultBlockState();
        BlockState hard = Blocks.STONE.defaultBlockState();

        float softSpeed = ForgeweaveTraits.SWIFTDIG.breakSpeed(pickaxe, player, soft, 1.0F, 1.0F);
        float hardSpeed = ForgeweaveTraits.SWIFTDIG.breakSpeed(pickaxe, player, hard, 1.0F, 1.0F);

        helper.assertTrue(softSpeed > 1.0F, "swiftdig must speed up a soft block, got " + softSpeed);
        helper.assertTrue(hardSpeed == 1.0F, "swiftdig must leave a hard block's speed untouched, got " + hardSpeed);
        helper.succeed();
    }

    /** Voidglass -&gt; alien2: the same 72-tick cadence as alien, at triple the per-step growth. */
    @GameTest(template = "empty")
    public static void alien2GrowsThreeTimesFasterThanAlien(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack pickaxe = pickaxe(List.of(traitId("alien2")), 100, 2.0F, 1.0F);

        AlienProgress.Portion pool = new AlienProgress.Portion(30, 0.21F, 0.15F);
        pickaxe.set(ForgeweaveDataComponents.ALIEN_PROGRESS2.get(), new AlienProgress(pool, AlienProgress.Portion.ZERO));

        player.tickCount = 72; // durability step, tripled: +3 instead of alien's +1.
        pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);

        helper.assertTrue(pickaxe.getMaxDamage() == 103,
                "expected alien2's tripled 72-tick step to grow max damage to 103, got " + pickaxe.getMaxDamage());
        helper.succeed();
    }

    /** Quakestone -&gt; quakecrumble: mining a block has a chance to crack a mineable neighbor loose too. */
    @GameTest(template = "empty")
    public static void quakecrumbleSometimesBreaksAdjacentBlocks(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("quakecrumble")));
        BlockPos pos = new BlockPos(1, 1, 1);
        boolean sawCrumble = false;

        for (int i = 0; i < 400 && !sawCrumble; i++) {
            helper.setBlock(pos, Blocks.STONE);
            helper.setBlock(pos.north(), Blocks.STONE);
            player.gameMode.destroyBlock(helper.absolutePos(pos));
            sawCrumble = helper.getBlockState(pos.north()).isAir();
        }

        helper.assertTrue(sawCrumble, "quakecrumble should have cracked an adjacent block at least once within 400 mined blocks");
        helper.succeed();
    }

    /** Riftalloy -&gt; riftstep: a landed hit sometimes teleports the target or the wielder a short distance. */
    @GameTest(template = "empty")
    public static void riftstepSometimesTeleportsSomeone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = holding(helper, List.of());
        BlockPos startPos = new BlockPos(2, 2, 2);
        Zombie target = helper.spawn(EntityType.ZOMBIE, startPos);
        ItemStack weapon = player.getMainHandItem();
        double targetStartX = target.getX();
        double targetStartY = target.getY();
        double targetStartZ = target.getZ();
        double playerStartX = player.getX();
        double playerStartY = player.getY();
        double playerStartZ = player.getZ();
        boolean moved = false;

        for (int i = 0; i < 500 && !moved; i++) {
            ForgeweaveTraits.RIFTSTEP.afterHit(weapon, level, player, target);
            moved = target.getX() != targetStartX || target.getY() != targetStartY || target.getZ() != targetStartZ
                    || player.getX() != playerStartX || player.getY() != playerStartY || player.getZ() != playerStartZ;
        }

        helper.assertTrue(moved, "riftstep should have teleported someone within 500 landed hits");
        helper.succeed();
    }

    /** Dreadalloy -&gt; dreadgrip: a landed hit slows, weakens, and drops the target's current AI focus. */
    @GameTest(template = "empty")
    public static void dreadgripDebuffsAndUnfocusesTheTarget(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = holding(helper, List.of());
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        Zombie decoy = helper.spawn(EntityType.ZOMBIE, new BlockPos(4, 2, 4));
        target.setTarget(decoy);
        ItemStack weapon = player.getMainHandItem();

        ForgeweaveTraits.DREADGRIP.afterHit(weapon, level, player, target);

        helper.assertTrue(target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN), "dreadgrip must apply Slowness");
        helper.assertTrue(target.hasEffect(MobEffects.WEAKNESS), "dreadgrip must apply Weakness");
        helper.assertTrue(target.getTarget() == null, "dreadgrip must drop the target's current AI focus");
        helper.succeed();
    }

    /** Hollowsteel -&gt; bloodtally: a permanent, capped, per-kill attack-damage bonus. */
    @GameTest(template = "empty")
    public static void bloodtallyGrowsWithLifetimeKills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = holding(helper, List.of(traitId("bloodtally")));
        ItemStack sword = player.getMainHandItem();

        float freshBonus = ForgeweaveTraits.BLOODTALLY.attackDamageBonus(sword);
        helper.assertTrue(freshBonus == 0.0F, "a fresh tool must carry no kill-tally bonus yet, got " + freshBonus);

        for (int i = 0; i < 5; i++) {
            Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
            victim.setHealth(0.0F);
            ForgeweaveTraits.BLOODTALLY.afterHit(sword, level, player, victim);
        }

        int tally = sword.getOrDefault(ForgeweaveDataComponents.KILL_TALLY.get(), 0);
        float grownBonus = ForgeweaveTraits.BLOODTALLY.attackDamageBonus(sword);
        helper.assertTrue(tally == 5, "expected 5 recorded kills, got " + tally);
        helper.assertTrue(grownBonus > freshBonus,
                "bloodtally's bonus after 5 kills (" + grownBonus + ") must exceed the fresh bonus (" + freshBonus + ")");
        helper.succeed();
    }

    /** Ironbrand -&gt; gamedrop: kills grant no XP, and sometimes drop a cut of meat instead. */
    @GameTest(template = "empty")
    public static void gamedropSuppressesXpAndSometimesDropsMeat(GameTestHelper helper) {
        int xp = ForgeweaveTraits.GAMEDROP.killExperience(RandomSource.create(), 10);
        helper.assertTrue(xp == 0, "gamedrop must suppress all kill XP, got " + xp);

        ServerLevel level = helper.getLevel();
        ServerPlayer player = holding(helper, List.of());
        ItemStack weapon = player.getMainHandItem();
        Zombie victim = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        victim.setHealth(0.0F);
        boolean dropped = false;

        for (int i = 0; i < 200 && !dropped; i++) {
            ForgeweaveTraits.GAMEDROP.afterHit(weapon, level, player, victim);
            dropped = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    victim.getBoundingBox().inflate(4.0)).isEmpty();
        }

        helper.assertTrue(dropped, "gamedrop should have dropped meat at least once within 200 kills");
        helper.succeed();
    }

    /** A survival {@link ServerPlayer} holding a hand-built sword carrying {@code traits}. */
    private static ServerPlayer holding(GameTestHelper helper, List<ResourceLocation> traits) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(traits, 1000, 6.0F, 5.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        return player;
    }

    /** Builds a pickaxe {@code ItemStack} with the given traits/stats directly. */
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
