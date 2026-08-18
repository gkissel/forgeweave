package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #429: graveyard soil, consecrated soil and the necrotic bone -- the two reagents smite and
 * necrotic had been standing in for with glowstone dust and a wither skeleton skull. Verified against
 * tinkers-1.12 @ {@code c01173c0} (NOTICE.md): {@code BlockSoil.SoilTypes.GRAVEYARD/CONSECRATED}
 * with its {@code onEntityWalk} effects, {@code recipes/common/soil/graveyard_soil.json},
 * {@code TinkerCommons#registerSmeltingRecipes}, and {@code ToolEvents#onLootTableLoad}'s wither
 * skeleton drop.
 *
 * <p>The drop test rolls the real vanilla wither-skeleton table many times rather than once: the
 * injected pool is a 7% chance upstream, so a single roll proves nothing either way. 400 player
 * kills leave a {@code 0.93^400} (~1e-13) chance of a false failure, and the two negative runs are
 * exact -- {@code killed_by_player} and the table id either match or they do not.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SoilGameTests {

    /** Enough rolls that a 7% drop is a certainty in practice; see the class javadoc. */
    private static final int DROP_ROLLS = 400;

    @GameTest(template = "empty")
    public static void bothSoilsArePlaceableBlocks(GameTestHelper helper) {
        BlockPos graveyard = new BlockPos(1, 1, 1);
        BlockPos consecrated = new BlockPos(2, 1, 1);
        helper.setBlock(graveyard, ForgeweaveBlocks.GRAVEYARD_SOIL.get());
        helper.setBlock(consecrated, ForgeweaveBlocks.CONSECRATED_SOIL.get());

        helper.assertBlockPresent(ForgeweaveBlocks.GRAVEYARD_SOIL.get(), graveyard);
        helper.assertBlockPresent(ForgeweaveBlocks.CONSECRATED_SOIL.get(), consecrated);

        helper.succeed();
    }

    /** Upstream {@code recipes/common/soil/graveyard_soil.json}: dirt + rotten flesh + bone meal. */
    @GameTest(template = "empty")
    public static void graveyardSoilCraftsFromDirtFleshAndBoneMeal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        CraftingInput input = CraftingInput.of(3, 1, List.of(
                new ItemStack(Items.DIRT), new ItemStack(Items.ROTTEN_FLESH), new ItemStack(Items.BONE_MEAL)));

        ItemStack crafted = level.getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(crafted.is(ForgeweaveItems.GRAVEYARD_SOIL.get()) && crafted.getCount() == 1,
                "expected dirt + rotten flesh + bone meal to craft 1 graveyard soil, got " + crafted);

        helper.succeed();
    }

    /** Upstream {@code TinkerCommons#registerSmeltingRecipes}: {@code addSmelting(graveyardSoil, consecratedSoil, 0.1f)}. */
    @GameTest(template = "empty")
    public static void graveyardSoilSmeltsIntoConsecratedSoil(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        SingleRecipeInput input = new SingleRecipeInput(new ItemStack(ForgeweaveItems.GRAVEYARD_SOIL.get()));

        ItemStack smelted = level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, input, level)
                .map(match -> match.value().assemble(input, level.registryAccess()))
                .orElse(ItemStack.EMPTY);

        helper.assertTrue(smelted.is(ForgeweaveItems.CONSECRATED_SOIL.get()),
                "expected furnace-smelting graveyard soil to give consecrated soil, got " + smelted);

        helper.succeed();
    }

    /** Upstream {@code BlockSoil#processGraveyardSoil}: an undead mob walking on it heals 1. */
    @GameTest(template = "empty")
    public static void graveyardSoilHealsUndeadThatWalkOnIt(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.GRAVEYARD_SOIL.get());

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        zombie.setHealth(5.0F);
        ForgeweaveBlocks.GRAVEYARD_SOIL.get()
                .stepOn(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), zombie);

        helper.assertTrue(zombie.getHealth() == 6.0F,
                "expected graveyard soil to heal the zombie by 1, left it at " + zombie.getHealth());

        zombie.discard();
        helper.succeed();
    }

    /**
     * Upstream {@code BlockSoil#processConsecratedSoil}: an undead mob walking on it takes 1 magic
     * damage and catches fire; a living non-undead one is untouched.
     */
    @GameTest(template = "empty")
    public static void consecratedSoilBurnsUndeadButNotTheLiving(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ForgeweaveBlocks.CONSECRATED_SOIL.get());

        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        float before = zombie.getHealth();
        ForgeweaveBlocks.CONSECRATED_SOIL.get()
                .stepOn(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), zombie);

        helper.assertTrue(zombie.getHealth() < before,
                "expected consecrated soil to hurt the zombie, left it at " + zombie.getHealth());
        helper.assertTrue(zombie.isOnFire(), "expected consecrated soil to set the zombie alight");

        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 1));
        float pigBefore = pig.getHealth();
        ForgeweaveBlocks.CONSECRATED_SOIL.get()
                .stepOn(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), pig);

        helper.assertTrue(pig.getHealth() == pigBefore && !pig.isOnFire(),
                "expected consecrated soil to leave a non-undead mob alone");

        zombie.discard();
        pig.discard();
        helper.succeed();
    }

    /**
     * Upstream {@code ToolEvents#onLootTableLoad}: a wither skeleton killed by a player drops the
     * necrotic bone at a 7% chance (+5%/looting level). Ported as the NeoForge global loot modifier
     * {@code data/forgeweave/loot_modifiers/necrotic_bone.json}, so this rolls the real vanilla table
     * through {@code LootTable#getRandomItems}, which is where the modifier is applied.
     */
    @GameTest(template = "empty")
    public static void witherSkeletonsDropNecroticBonesForPlayerKills(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        WitherSkeleton skeleton = helper.spawn(EntityType.WITHER_SKELETON, new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        int killedByPlayer = rollLoot(level, EntityType.WITHER_SKELETON.getDefaultLootTable(), skeleton, player);
        int noKiller = rollLoot(level, EntityType.WITHER_SKELETON.getDefaultLootTable(), skeleton, null);

        helper.assertTrue(killedByPlayer > 0,
                "expected a player-killed wither skeleton to drop necrotic bones over " + DROP_ROLLS + " rolls");
        helper.assertTrue(noKiller == 0,
                "expected no necrotic bone without a player kill (upstream's KilledByPlayer condition), got " + noKiller);

        skeleton.discard();
        helper.succeed();
    }

    /** The injection is keyed on the wither skeleton's table alone -- a plain skeleton drops nothing extra. */
    @GameTest(template = "empty")
    public static void plainSkeletonsDropNoNecroticBone(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(1, 2, 1));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        int bones = rollLoot(level, EntityType.SKELETON.getDefaultLootTable(), skeleton, player);

        helper.assertTrue(bones == 0, "expected the injection to be keyed on wither skeletons only, got " + bones);

        skeleton.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static int rollLoot(ServerLevel level, ResourceKey<LootTable> tableKey, Entity victim, Player killer) {
        LootTable table = level.getServer().reloadableRegistries().getLootTable(tableKey);
        int bones = 0;
        for (int i = 0; i < DROP_ROLLS; i++) {
            for (ItemStack stack : table.getRandomItems(entityParams(level, victim, killer))) {
                if (stack.is(ForgeweaveItems.NECROTIC_BONE.get())) {
                    bones += stack.getCount();
                }
            }
        }
        return bones;
    }

    private static LootParams entityParams(ServerLevel level, Entity victim, Player killer) {
        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE,
                        killer == null
                                ? level.damageSources().generic()
                                : level.damageSources().playerAttack(killer));
        if (killer != null) {
            builder = builder
                    .withParameter(LootContextParams.ATTACKING_ENTITY, killer)
                    .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer);
        }
        return builder.create(LootContextParamSets.ENTITY);
    }
}
