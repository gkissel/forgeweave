package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.HeftSeam;
import dev.gkissel.forgeweave.combat.ReapSeam;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.KamaItem;
import dev.gkissel.forgeweave.item.MattockItem;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * docs/SCOPE.md M3 issue #156's verification: the mattock chops (axe-tag) and digs (shovel-tag)
 * effectively and tills soil; the kama shears a sheep and harvests+replants a mature crop; both
 * tools' combat riders (heft, reap -- maintainer decision 2026-08-12) fire through the real
 * {@link CombatSeams} pipeline.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MattockKamaGameTests {

    // ------------------------------------------------------------------------------- mattock

    /**
     * "Chops a log + digs dirt": the mattock's {@code tool} component carries a rule per tag
     * ({@link dev.gkissel.forgeweave.item.ToolItem#toolComponent}), so an oak log resolves under the
     * axe rule and dirt under the shovel rule -- both correct-for-drops, both faster than the
     * default 1.0 speed a block outside either tag would get.
     */
    @GameTest(template = "empty")
    public static void mattockIsEffectiveOnBothAxeAndShovelBlockFamilies(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = ToolAssembly.assemble(helper, player, pos, ToolAssembly.entryFor(ForgeweaveItems.TOOL_MATTOCK.get()), List.of("wood", "stone", "stone"));

        Tool tool = mattock.get(net.minecraft.core.component.DataComponents.TOOL);
        helper.assertTrue(tool != null, "an assembled mattock must carry a tool component");

        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        helper.assertTrue(tool.isCorrectForDrops(log), "the mattock must chop logs (axe tag) effectively");
        helper.assertTrue(tool.getMiningSpeed(log) > 1.0F, "log-chopping speed must be above the default");

        BlockState dirt = Blocks.DIRT.defaultBlockState();
        helper.assertTrue(tool.isCorrectForDrops(dirt), "the mattock must dig dirt (shovel tag) effectively");
        helper.assertTrue(tool.getMiningSpeed(dirt) > 1.0F, "dirt-digging speed must be above the default");

        helper.succeed();
    }

    /** Tills dirt into farmland on right-click, upstream {@code Mattock#useHoe} (NOTICE.md). */
    @GameTest(template = "empty")
    public static void mattockTillsSoilLikeAHoe(GameTestHelper helper) {
        BlockPos standPos = new BlockPos(1, 2, 1);
        BlockPos tillPos = new BlockPos(1, 1, 2);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = ToolAssembly.assemble(helper, player, standPos, ToolAssembly.entryFor(ForgeweaveItems.TOOL_MATTOCK.get()), List.of("wood", "stone", "stone"));

        helper.setBlock(tillPos, Blocks.DIRT);
        player.setItemInHand(InteractionHand.MAIN_HAND, mattock);

        helper.useBlock(tillPos, player);

        helper.assertBlockPresent(Blocks.FARMLAND, tillPos);
        helper.succeed();
    }

    /**
     * Heft (maintainer decision 2026-08-12). Two parts, deliberately not routed through the live
     * {@link CombatSeams} pipeline with a second registration: {@link Forgeweave}'s constructor
     * already registers the real, 15%-chance {@link HeftSeam#SEAM} once for every {@link
     * MattockItem}, permanently, for the whole test-server JVM -- registering a second provider here
     * (even a forced one) would double up on every hit a mattock ever lands for the rest of the
     * suite. So this checks resolution (a mattock really does resolve to {@code HeftSeam#SEAM},
     * {@link CombatSeams#seams} is public exactly for this, per its own javadoc) and behavior
     * (a forced-chance {@link HeftSeam} instance's {@code onHit} called directly -- the "expose the
     * chance as a parameter you can force" idiom) separately, instead of at once.
     */
    @GameTest(template = "empty")
    public static void heftFiresAStrongKnockbackWhenForced(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack mattock = ToolAssembly.assemble(helper, player, pos, ToolAssembly.entryFor(ForgeweaveItems.TOOL_MATTOCK.get()), List.of("wood", "stone", "stone"));

        helper.assertTrue(CombatSeams.seams(mattock).contains(HeftSeam.SEAM),
                "an assembled mattock must resolve to the real heft seam");

        var attacker = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        var target = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);
        dev.gkissel.forgeweave.combat.CombatHit hit =
                new dev.gkissel.forgeweave.combat.CombatHit(helper.getLevel(), mattock, attacker, target, source);

        double before = target.getDeltaMovement().horizontalDistanceSqr();
        new HeftSeam(1.0F).onHit(hit, 1.0F);
        double after = target.getDeltaMovement().horizontalDistanceSqr();

        helper.assertTrue(after > before, "a forced heft roll must add horizontal knockback");
        attacker.discard();
        target.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------------------- kama

    /** Shears a sheep, upstream {@code Kama#itemInteractionForEntity}/{@code #shearEntity} (NOTICE.md). */
    @GameTest(template = "empty")
    public static void kamaShearsASheep(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack kama = ToolAssembly.assemble(helper, player, pos, ToolAssembly.entryFor(ForgeweaveItems.TOOL_KAMA.get()), List.of("wood", "stone", "stone"));
        player.setItemInHand(InteractionHand.MAIN_HAND, kama);

        Sheep sheep = helper.spawn(EntityType.SHEEP, new BlockPos(2, 1, 1));
        sheep.setSheared(false);
        sheep.setColor(DyeColor.WHITE);

        InteractionResult result = kama.getItem().interactLivingEntity(kama, player, sheep, InteractionHand.MAIN_HAND);

        helper.assertTrue(result.consumesAction(), "shearing a ready sheep must consume the interaction");
        helper.assertTrue(sheep.isSheared(), "the sheep must end up sheared");
        sheep.discard();
        helper.succeed();
    }

    /** Right-click harvests a mature crop and replants it, upstream {@code Kama#doHarvestCrop} (NOTICE.md). */
    @GameTest(template = "empty")
    public static void kamaHarvestsAndReplantsAMatureCrop(GameTestHelper helper) {
        BlockPos standPos = new BlockPos(1, 2, 1);
        BlockPos cropPos = new BlockPos(1, 1, 2);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack kama = ToolAssembly.assemble(helper, player, standPos, ToolAssembly.entryFor(ForgeweaveItems.TOOL_KAMA.get()), List.of("wood", "stone", "stone"));
        player.setItemInHand(InteractionHand.MAIN_HAND, kama);

        helper.setBlock(cropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        helper.useBlock(cropPos, player);

        helper.assertBlockState(cropPos, state -> state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) == 0,
                () -> "expected the wheat to be replanted at age 0");
        helper.succeed();
    }

    /**
     * Reap (maintainer decision 2026-08-12): +25% damage against a target already below 25% health.
     * Relies on the real seam {@link Forgeweave}'s constructor already registers for every {@link
     * KamaItem} -- registering a second one here would double the bonus for the rest of the suite
     * (see {@link #heftFiresAStrongKnockbackWhenForced}'s javadoc) -- and, since {@link ReapSeam} has
     * no randomness to force, that resolved seam is exactly what a real hit should use.
     */
    @GameTest(template = "empty")
    public static void reapAppliesBonusDamageBelowHealthThreshold(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack kama = ToolAssembly.assemble(helper, player, pos, ToolAssembly.entryFor(ForgeweaveItems.TOOL_KAMA.get()), List.of("wood", "stone", "stone"));
        player.setItemInHand(InteractionHand.MAIN_HAND, kama);

        helper.assertTrue(CombatSeams.seams(kama).contains(ReapSeam.SEAM),
                "an assembled kama must resolve to the real reap seam");

        var target = helper.spawn(EntityType.PIG, new BlockPos(2, 1, 1));
        target.setHealth(target.getMaxHealth() * 0.2F); // below the 25% threshold
        float healthBefore = target.getHealth();

        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        target.hurt(source, 1.0F);
        float dealt = healthBefore - target.getHealth();

        helper.assertTrue(Math.abs(dealt - 1.25F) < 1.0e-3F,
                "expected the boosted 1.25 damage against a low-health target, got " + dealt);
        target.discard();
        helper.succeed();
    }

    private MattockKamaGameTests() {}
}
