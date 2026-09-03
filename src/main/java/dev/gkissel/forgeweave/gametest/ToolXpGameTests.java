package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ForgeweaveAttachments;
import dev.gkissel.forgeweave.tool.ToolLevel;
import dev.gkissel.forgeweave.tool.ToolLeveling;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #919's verification: the two XP sources that carry M7's mechanic, against Tinkers' Tool
 * Leveling's {@code ModToolLeveling#afterBlockBreak} / {@code #afterHit} and its
 * {@code EntityXpHandler} payout (tool-leveling-1.12, pinned commit in NOTICE.md).
 *
 * <p>The melee tests use a real {@link ServerPlayer} rather than the plain mock {@code Player} most
 * GameTests here take, because the death payout looks the owed player back up by UUID off the level
 * the way upstream's {@code distributeXpForPlayer} does, and a mock player is in no level's player
 * list.
 *
 * <p>Tools are built by hand rather than assembled at a Tool Station: what these tests are about is
 * the XP that lands on the component, and the assembly path has its own coverage elsewhere.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolXpGameTests {

    /** Upstream's {@code afterBlockBreak}: +1 for a block the tool was effective against. */
    @GameTest(template = "empty")
    public static void effectiveBreakGrantsOneXp(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe();
        helper.assertTrue(mine(helper, pickaxe, Blocks.STONE.defaultBlockState()) == 1,
                "an effective break must grant exactly 1 XP, granted " + ToolLevel.of(pickaxe).xp());
        helper.succeed();
    }

    /** The other half of the same gate: an off-type break grants nothing at all. */
    @GameTest(template = "empty")
    public static void ineffectiveBreakGrantsNothing(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe();
        helper.assertTrue(mine(helper, pickaxe, Blocks.DIRT.defaultBlockState()) == 0,
                "a pickaxe breaking dirt is not effective and must grant nothing");
        helper.succeed();
    }

    /** Upstream's {@code afterHit} on a lethal blow: {@code round(damageDealt)} to the weapon. */
    @GameTest(template = "empty")
    public static void killingBlowGrantsRoundedDamage(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = pickaxe();
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 10.0F);

        helper.assertFalse(pig.isAlive(), "a 10-damage blow was meant to kill a pig outright");
        helper.assertTrue(ToolLevel.of(sword).xp() == 10,
                "a killing blow must grant round(damageDealt) = 10, granted " + ToolLevel.of(sword).xp());
        helper.succeed();
    }

    /**
     * Melee XP is paid on the kill and never on the hit: a survivable blow leaves the tool at zero
     * and banks its damage on the target instead.
     */
    @GameTest(template = "empty")
    public static void aSurvivedHitGrantsNothingOnItsOwn(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = pickaxe();
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 3.0F);

        helper.assertTrue(pig.isAlive(), "a 3-damage blow must not kill a pig, or this proves nothing");
        helper.assertTrue(ToolLevel.of(sword).xp() == 0,
                "a hit the target survived must grant nothing at the time, granted " + ToolLevel.of(sword).xp());
        UUID toolId = sword.get(ForgeweaveDataComponents.TOOL_ID.get());
        helper.assertTrue(toolId != null, "the tool must have been given a ledger id when it banked damage");
        helper.assertTrue(pig.getData(ForgeweaveAttachments.DAMAGE_XP).damage(player.getUUID(), toolId) == 3.0F,
                "the blow's damage must have been banked on the target");
        helper.succeed();
    }

    /**
     * Upstream's {@code EntityXpHandler}: when the target finally dies, every tool that damaged it is
     * paid its own accumulated total -- here through a kill the tool had no part in, and with the
     * tool pocketed in another inventory slot rather than still in hand.
     */
    @GameTest(template = "empty")
    public static void bankedDamageIsPaidOutOnDeath(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = pickaxe();
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        DamageSource swing = helper.getLevel().damageSources().playerAttack(player);

        pig.hurt(swing, 3.0F);
        pig.invulnerableTime = 0; // vanilla's post-hit window would swallow the second blow
        pig.hurt(swing, 4.0F);
        helper.assertTrue(pig.isAlive(), "seven damage must not kill a pig, or this proves nothing");
        helper.assertTrue(ToolLevel.of(sword).xp() == 0, "neither blow may have paid out yet");

        // Pocket the weapon, then let something else finish the pig off.
        player.getInventory().setItem(9, sword);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        pig.invulnerableTime = 0;
        pig.hurt(helper.getLevel().damageSources().generic(), 100.0F);

        helper.assertFalse(pig.isAlive(), "the pig was meant to be dead by now");
        helper.assertTrue(ToolLevel.of(sword).xp() == 7,
                "the ledger owed this tool 3 + 4 = 7 XP, it was paid " + ToolLevel.of(sword).xp());
        helper.assertTrue(pig.getData(ForgeweaveAttachments.DAMAGE_XP).isEmpty(),
                "the ledger must be cleared once it has paid out");
        helper.succeed();
    }

    /**
     * The gate line M7's acceptance test opens with (docs/SCOPE.md M7, "CI and release gates"), run
     * the long way rather than through {@link ToolLeveling#addXp}: 500 effective breaks with a
     * pickaxe at the default base XP are exactly one level, and that level is exactly one more free
     * modifier slot. The 499th break must still leave the tool at level 0, or "500" would only mean
     * "at least 500".
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fiveHundredEffectiveBreaksLevelThePickaxeOnce(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe();
        int slotsBefore = ForgeweaveModifiers.freeSlots(pickaxe);
        for (int i = 0; i < 499; i++) {
            mine(helper, pickaxe, Blocks.STONE.defaultBlockState());
        }
        helper.assertTrue(ToolLevel.of(pickaxe).level() == 0,
                "499 breaks must still be level 0, got " + ToolLevel.of(pickaxe));

        mine(helper, pickaxe, Blocks.STONE.defaultBlockState());

        ToolLevel level = ToolLevel.of(pickaxe);
        helper.assertTrue(level.level() == 1 && level.xp() == 0 && level.bonusSlots() == 1,
                "the 500th effective break must be level 1 with one earned slot, got " + level);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == slotsBefore + 1,
                "the level must show up as exactly one more free slot, got "
                        + ForgeweaveModifiers.freeSlots(pickaxe));
        helper.succeed();
    }

    /**
     * The gate line the save-compat corpus cannot carry: the ledger lives in <em>entity</em> NBT, not
     * on an item stack, so {@code SaveCompatCorpusTest}'s item/block-entity/material walk has no
     * shape for it and this round trip is where it is pinned instead (that class's javadoc records
     * the decision; issue #925). What upstream's capability promised and this has to keep: a mob hit
     * and then saved, unloaded and reloaded still owes the tool that hit it, and still pays out when
     * it finally dies.
     */
    @GameTest(template = "empty")
    public static void theDamageLedgerSurvivesAnEntitySaveAndReload(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack sword = pickaxe();
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 3.0F);
        helper.assertTrue(pig.isAlive(), "a 3-damage blow must not kill a pig, or this proves nothing");
        UUID toolId = sword.get(ForgeweaveDataComponents.TOOL_ID.get());
        helper.assertTrue(toolId != null, "the tool must have been given a ledger id when it banked damage");

        CompoundTag tag = new CompoundTag();
        helper.assertTrue(pig.save(tag), "a damaged pig must be saved to the region file");
        pig.discard();

        Entity reloaded = EntityType.loadEntityRecursive(tag, helper.getLevel(), entity -> entity);
        helper.assertTrue(reloaded instanceof Pig, "the saved pig must reload as a pig");
        helper.assertTrue(reloaded.getData(ForgeweaveAttachments.DAMAGE_XP).damage(player.getUUID(), toolId) == 3.0F,
                "the reloaded pig must still owe the tool the 3 damage it banked");

        helper.getLevel().addFreshEntity(reloaded);
        reloaded.hurt(helper.getLevel().damageSources().generic(), 100.0F);
        helper.assertFalse(reloaded.isAlive(), "the reloaded pig was meant to be dead by now");
        helper.assertTrue(ToolLevel.of(sword).xp() == 3,
                "a ledger that survived the round trip must still pay out, granted " + ToolLevel.of(sword).xp());
        helper.succeed();
    }

    /** D-M7-3: with {@code toolLeveling} off the mechanic is inert on both paths. */
    @GameTest(template = "empty")
    public static void configOffGrantsNothingAnywhere(GameTestHelper helper) {
        ForgeweaveConfig.TOOL_LEVELING.set(false);
        try {
            ItemStack pickaxe = pickaxe();
            helper.assertTrue(mine(helper, pickaxe, Blocks.STONE.defaultBlockState()) == 0,
                    "no mining XP may be granted with toolLeveling off");

            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            ItemStack sword = pickaxe();
            player.setItemInHand(InteractionHand.MAIN_HAND, sword);
            Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
            DamageSource swing = helper.getLevel().damageSources().playerAttack(player);
            pig.hurt(swing, 3.0F);
            pig.invulnerableTime = 0;
            pig.hurt(swing, 20.0F);

            helper.assertFalse(pig.isAlive(), "the pig was meant to be dead by now");
            helper.assertTrue(ToolLevel.of(sword).xp() == 0,
                    "no melee XP may be granted with toolLeveling off, granted " + ToolLevel.of(sword).xp());
            helper.assertTrue(sword.get(ForgeweaveDataComponents.TOOL_ID.get()) == null,
                    "nothing may be written to the ledger with toolLeveling off either");
        } finally {
            ForgeweaveConfig.TOOL_LEVELING.set(true);
        }
        helper.succeed();
    }

    /** Breaks one block of {@code state} with {@code tool} and hands back the XP the tool now holds. */
    private static int mine(GameTestHelper helper, ItemStack tool, BlockState state) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, state);
        tool.mineBlock(helper.getLevel(), state, helper.absolutePos(pos), helper.makeMockPlayer(GameType.SURVIVAL));
        return ToolLevel.of(tool).xp();
    }

    /** A plain stone-headed pickaxe with no traits, built by hand -- see the class javadoc. */
    private static ItemStack pickaxe() {
        ToolItem toolItem = ForgeweaveItems.TOOL_PICKAXE.get();
        ToolStats.Stats stats = new ToolStats.Stats(1000, 1.0F, 1.0F);
        Material head = new Material(
                new Material.Head(1000, 1.0F, 1.0F),
                new Material.Handle(1.0F, 0),
                0,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));

        ItemStack stack = new ItemStack(toolItem);
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        stack.set(DataComponents.TOOL, toolItem.toolComponent(head, stats));
        stack.set(DataComponents.MAX_DAMAGE, 1000);
        stack.set(DataComponents.DAMAGE, 0);
        return stack;
    }
}
