package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * docs/SCOPE.md M2 issue #102's verification: one test per metal trait, exercising the behavior
 * that distinguishes it. Unlike {@link TraitGameTests} these tools are assembled by hand
 * ({@link #pickaxe}) rather than through a real Tool Station: iron/cobalt/ardite/manyullyn/copper
 * material JSON does not exist yet (issue #103 wires materials to these traits), so there is nothing
 * for a Tool Station to assemble from. The trait ids are set directly, the same {@code ItemStack}
 * shape {@code ToolAssemblyRecipes} would have produced.
 *
 * <p>Magnitudes are upstream 1.12's, cited per trait in {@code ForgeweaveTraits}; deviations forced
 * by the port are cited there too and in the PR body.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MetalTraitGameTests {

    /**
     * Iron -&gt; {@code forgeweave:magnetic2}: every tick the tool is carried, nearby item drops are
     * pulled toward the holder.
     */
    @GameTest(template = "empty")
    public static void magneticPullsNearbyItemsTowardTheHolder(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("magnetic2")), 100, 1.0F, 1.0F);

        ItemEntity dropped = new ItemEntity(level, player.getX() + 1.5, player.getY(), player.getZ(),
                new ItemStack(Items.COBBLESTONE));
        dropped.setDeltaMovement(Vec3.ZERO);
        level.addFreshEntity(dropped);

        pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        helper.assertTrue(dropped.getDeltaMovement().x < 0.0,
                "magnetic should push the item toward the holder (negative x), got " + dropped.getDeltaMovement());

        dropped.discard();
        helper.succeed();
    }

    /** Cobalt, head only -&gt; {@code forgeweave:momentum}: mining speed builds up and decays. */
    @GameTest(template = "empty")
    public static void momentumBuildsAndDecaysMiningSpeed(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("momentum")), 1000, 2.0F, 1.0F);
        BlockState stone = Blocks.STONE.defaultBlockState();

        helper.assertTrue(ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F) == 2.0F,
                "no bonus before any blocks are broken");

        ForgeweaveTraits.afterBlockBreak(pickaxe, level, stone, BlockPos.ZERO, player, true);
        // level 1: boost = 1/80; speed = 2 + 2 * 1/80 = 2.025
        float afterOne = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(Math.abs(afterOne - 2.025F) < 0.001F, "expected 2.025 after one block, got " + afterOne);

        // duration = (10 / 2) * 1.5 * 20 = 150 ticks
        for (int i = 0; i < 151; i++) {
            pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        }
        float afterDecay = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(afterDecay == 2.0F, "momentum should have decayed to no bonus, got " + afterDecay);

        helper.succeed();
    }

    /** Cobalt -&gt; {@code forgeweave:lightweight}: flat +10% mining and attack speed. */
    @GameTest(template = "empty")
    public static void lightweightBoostsMiningAndAttackSpeed(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("lightweight")), 100, 2.0F, 1.0F);

        float speed = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(Math.abs(speed - 2.2F) < 0.001F, "expected 10% mining speed bonus, got " + speed);

        float attackSpeedBonus = ForgeweaveTraits.attackSpeedBonus(pickaxe);
        helper.assertTrue(Math.abs(attackSpeedBonus - 0.1F) < 0.001F,
                "expected 10% attack speed bonus, got " + attackSpeedBonus);

        helper.succeed();
    }

    /** Ardite, head only -&gt; {@code forgeweave:stonebound}: mining speed rises as durability drops. */
    @GameTest(template = "empty")
    public static void stoneboundGrowsMiningSpeedAsDurabilityDrops(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("stonebound")), 200, 2.0F, 1.0F);

        float fullDurability = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(fullDurability == 2.0F, "no bonus at full durability, got " + fullDurability);

        pickaxe.setDamageValue(100);
        double expected = 2.0 + Math.log(100 / 72.0 + 1.0) * 2.0;
        float worn = ForgeweaveTraits.miningSpeed(pickaxe, true, 2.0F);
        helper.assertTrue(Math.abs(worn - expected) < 0.001, "expected " + expected + ", got " + worn);

        float ineffective = ForgeweaveTraits.miningSpeed(pickaxe, false, 2.0F);
        helper.assertTrue(ineffective == 2.0F, "stonebound should do nothing when not effective, got " + ineffective);

        helper.succeed();
    }

    /** Ardite -&gt; {@code forgeweave:petramor}: chance to repair itself mining stone, never off-tag blocks. */
    @GameTest(template = "empty")
    public static void petramorSometimesRepairsOnStoneBlocks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("petramor")), 1000, 1.0F, 1.0F);
        pickaxe.setDamageValue(500);

        BlockState stone = Blocks.STONE.defaultBlockState();
        for (int i = 0; i < 200; i++) {
            ForgeweaveTraits.afterBlockBreak(pickaxe, level, stone, BlockPos.ZERO, player, true);
        }
        helper.assertTrue(pickaxe.getDamageValue() < 500,
                "petramor should have healed at least once in 200 stone breaks (p=1e-9 not), still at "
                        + pickaxe.getDamageValue());

        int before = pickaxe.getDamageValue();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        for (int i = 0; i < 200; i++) {
            ForgeweaveTraits.afterBlockBreak(pickaxe, level, dirt, BlockPos.ZERO, player, true);
        }
        helper.assertTrue(pickaxe.getDamageValue() == before, "petramor must not trigger on non-stone blocks");

        helper.succeed();
    }

    /** Manyullyn, head only -&gt; {@code forgeweave:insatiable}: consecutive hits stack a damage/durability cost. */
    @GameTest(template = "empty")
    public static void insatiableStacksDamageAndDurabilityCostOnConsecutiveHits(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("insatiable")), 1000, 1.0F, 3.0F);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        helper.assertTrue(ForgeweaveTraits.bonusDamageAgainst(pickaxe, target, 3.0F) == 0.0F, "no stack yet");

        for (int i = 0; i < 3; i++) {
            ForgeweaveTraits.afterHit(pickaxe, level, attacker, target);
        }
        // level 3: bonus = 3/3 = 1.0
        float bonus = ForgeweaveTraits.bonusDamageAgainst(pickaxe, target, 3.0F);
        helper.assertTrue(Math.abs(bonus - 1.0F) < 0.001F, "expected level 3 / 3 = 1.0 bonus damage, got " + bonus);
        helper.assertTrue(ForgeweaveTraits.attackDurabilityBonus(pickaxe) == 1,
                "expected level 3 / 3 = 1 extra durability loss, got " + ForgeweaveTraits.attackDurabilityBonus(pickaxe));

        // Decay after the 5s (100-tick) window with no more hits.
        for (int i = 0; i < 101; i++) {
            pickaxe.getItem().inventoryTick(pickaxe, level, attacker, 0, false);
        }
        helper.assertTrue(ForgeweaveTraits.bonusDamageAgainst(pickaxe, target, 3.0F) == 0.0F,
                "insatiable should have decayed");

        target.discard();
        helper.succeed();
    }

    /** Manyullyn -&gt; {@code forgeweave:coldblooded}: bonus damage only against full-health targets. */
    @GameTest(template = "empty")
    public static void coldbloodedBonusesDamageAgainstFullHealthTargets(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("coldblooded")), 100, 1.0F, 1.0F);
        Pig full = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        Pig hurt = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 2));
        hurt.setHealth(hurt.getMaxHealth() - 1.0F);

        float bonusFull = ForgeweaveTraits.bonusDamageAgainst(pickaxe, full, 4.0F);
        helper.assertTrue(bonusFull == 2.0F, "expected +50% (2.0) against a full-health target, got " + bonusFull);

        float bonusHurt = ForgeweaveTraits.bonusDamageAgainst(pickaxe, hurt, 4.0F);
        helper.assertTrue(bonusHurt == 0.0F, "coldblooded should do nothing against a damaged target, got " + bonusHurt);

        full.discard();
        hurt.discard();
        helper.succeed();
    }

    /** Copper -&gt; {@code forgeweave:established}: bonus XP on kills made with the tool in main hand. */
    @GameTest(template = "empty")
    public static void establishedGrantsBonusKillExperience(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig victim = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        ItemStack established = pickaxe(List.of(traitId("established")), 100, 1.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, established);
        LivingExperienceDropEvent withTrait = new LivingExperienceDropEvent(victim, player, 4);
        ForgeweaveTraits.onExperienceDrop(withTrait);
        // round(4 * 1.25 + r * 0.25) + 1 == 6 for every r in [0, 1): 4*1.25=5.0, +[0,0.25) still rounds to 5.
        helper.assertTrue(withTrait.getDroppedExperience() == 6,
                "expected established's kill-XP formula to give 6, got " + withTrait.getDroppedExperience());

        ItemStack plain = pickaxe(List.of(), 100, 1.0F, 1.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, plain);
        LivingExperienceDropEvent withoutTrait = new LivingExperienceDropEvent(victim, player, 4);
        ForgeweaveTraits.onExperienceDrop(withoutTrait);
        helper.assertTrue(withoutTrait.getDroppedExperience() == 4,
                "a tool without established should leave XP untouched, got " + withoutTrait.getDroppedExperience());

        victim.discard();
        helper.succeed();
    }

    /** Builds a pickaxe {@code ItemStack} with the given traits/stats directly (see class javadoc). */
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
