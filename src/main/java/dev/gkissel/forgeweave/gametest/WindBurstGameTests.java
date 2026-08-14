package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #223's verification (maintainer decision 2026-08-12): a breeze rod per level, up to vanilla's
 * own Wind Burst III cap, applies only to the warmace, and a landed smash with it launches the
 * attacker the way vanilla's own mace + Wind Burst combo does.
 *
 * <p>The smash test calls {@code EnchantmentHelper.doPostAttackEffects} directly rather than staging a
 * full swing, the same shortcut {@code WarmaceGameTests} takes with the item's own vanilla hooks: Wind
 * Burst's effect is entirely vanilla's own data-driven {@code minecraft:post_attack} enchantment
 * component (ADR-0005 decision 4 -- the smash rides vanilla mace mechanics, and so does whatever
 * enchantment is granted on it), fired from the generic attack pipeline rather than from
 * {@code WarmaceItem} itself, so exercising it directly tests the same code the game runs.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class WindBurstGameTests {

    private static final ResourceLocation WIND_BURST = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "wind_burst");

    /** {@link WarmaceGameTests}'s own part order: handle, head, binding. */
    private static final List<String> STONE_HEAD = List.of("wood", "stone", "wood");
    /** {@code ToolConstants#BROADSWORD}'s own part order: handle, head, guard. */
    private static final List<String> SWORD_MATERIALS = List.of("wood", "stone", "wood");

    private static ToolAssemblyRecipes.Entry warmaceEntry() {
        return ToolAssembly.entryFor(ForgeweaveItems.TOOL_WARMACE.get());
    }

    private static ItemStack warmace(GameTestHelper helper, Player player, BlockPos pos) {
        return ToolAssembly.assembleAtForge(helper, player, pos, warmaceEntry(), STONE_HEAD);
    }

    /**
     * One breeze rod per level (maintainer decision): the first records {@code wind_burst} at level 1
     * and grants vanilla Wind Burst I, and each further rod raises both the modifier entry and the
     * vanilla enchantment level in lockstep, up to Wind Burst's own III cap.
     */
    @GameTest(template = "empty")
    public static void breezeRodsGrantWindBurstAtTheAppliedLevel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack warmace = warmace(helper, player, pos);

        ItemStack levelOne = applyReagent(helper, player, pos, warmace, new ItemStack(Items.BREEZE_ROD, 1));
        assertLevel(helper, levelOne, 1);

        ItemStack levelTwo = applyReagent(helper, player, pos, levelOne, new ItemStack(Items.BREEZE_ROD, 1));
        assertLevel(helper, levelTwo, 2);

        ItemStack levelThree = applyReagent(helper, player, pos, levelTwo, new ItemStack(Items.BREEZE_ROD, 1));
        assertLevel(helper, levelThree, 3);

        helper.assertTrue(ForgeweaveModifiers.freeSlots(levelThree) == ForgeweaveModifiers.DEFAULT_SLOTS - 3,
                "three levels of wind_burst must occupy three modifier slots (issue #344, one per level), got "
                        + ForgeweaveModifiers.freeSlots(levelThree) + " free");
        helper.succeed();
    }

    /** A fourth breeze rod is refused once wind_burst is already at its level-3 cap. */
    @GameTest(template = "empty")
    public static void aFourthBreezeRodIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack warmace = warmace(helper, player, pos);
        warmace.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(WIND_BURST, 3)));

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, warmace);
        blockEntity.container().setItem(1, new ItemStack(Items.BREEZE_ROD, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a capped wind_burst must produce no output");
        helper.assertTrue(menu.rejection() != null, "a capped wind_burst must say so");
        helper.succeed();
    }

    /**
     * Restricted to tools vanilla's own {@code wind_burst} enchantment supports
     * ({@code #minecraft:enchantable/mace}, which the warmace joins -- {@code ForgeweaveItemTagsProvider}):
     * a broadsword refuses the same breeze rod that upgrades a warmace, with a message.
     */
    @GameTest(template = "empty")
    public static void applyingToABroadswordIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolAssemblyRecipes.Entry broadswordEntry = ToolAssembly.entryFor(ForgeweaveItems.TOOL_BROADSWORD.get());
        ItemStack broadsword = ToolAssembly.assemble(helper, player, pos, broadswordEntry, SWORD_MATERIALS);

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, broadsword);
        blockEntity.container().setItem(1, new ItemStack(Items.BREEZE_ROD, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "wind burst must not apply to a broadsword, which vanilla's own enchantment doesn't support");
        helper.assertTrue(menu.rejection() != null, "the refusal must tell the player why");
        helper.succeed();
    }

    /**
     * The whole point (maintainer decision): a smash landed with fall distance past the threshold and
     * Wind Burst on the warmace launches the attacker upward, vanilla's own {@code minecraft:explode}
     * post-attack effect. A blow with no Wind Burst granted leaves the attacker's velocity alone.
     */
    @GameTest(template = "empty")
    public static void smashWithWindBurstLaunchesTheAttackerUpward(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack warmace = warmace(helper, player, pos);
        warmace = applyReagent(helper, player, pos, warmace, new ItemStack(Items.BREEZE_ROD, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, warmace);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        // Past MaceItem.SMASH_ATTACK_FALL_THRESHOLD -- wind burst's own requirement (fall_distance
        // min 1.5, not flying) is exactly vanilla's own smash gate.
        player.fallDistance = 6.0F;
        double before = player.getDeltaMovement().y;

        EnchantmentHelper.doPostAttackEffects(helper.getLevel(), target, source);

        double after = player.getDeltaMovement().y;
        helper.assertTrue(after > before,
                "a smash with wind burst must launch the attacker upward, got a Y velocity delta of "
                        + (after - before));

        target.discard();
        helper.succeed();
    }

    /** Below the smash threshold, wind burst's own requirement refuses to fire at all. */
    @GameTest(template = "empty")
    public static void noSmashMeansNoLaunch(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack warmace = warmace(helper, player, pos);
        warmace = applyReagent(helper, player, pos, warmace, new ItemStack(Items.BREEZE_ROD, 1));
        player.setItemInHand(InteractionHand.MAIN_HAND, warmace);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        player.fallDistance = 0.0F;
        double before = player.getDeltaMovement().y;

        EnchantmentHelper.doPostAttackEffects(helper.getLevel(), target, source);

        double after = player.getDeltaMovement().y;
        helper.assertTrue(Math.abs(after - before) < 0.0001,
                "a blow from the ground must not trigger wind burst's launch, got a Y velocity delta of "
                        + (after - before));

        target.discard();
        helper.succeed();
    }

    /** The vanilla enchantment level matches the modifier's own level, at every step. */
    private static void assertLevel(GameTestHelper helper, ItemStack warmace, int expected) {
        ModifierEntry entry = ForgeweaveModifiers.entry(warmace, WIND_BURST);
        helper.assertTrue(entry != null && entry.level() == expected,
                "expected wind_burst at level " + expected + ", got " + entry);
        int enchantLevel = warmace.getEnchantments().getLevel(
                helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.WIND_BURST));
        helper.assertTrue(enchantLevel == expected,
                "expected vanilla Wind Burst " + expected + " granted, got " + enchantLevel);
    }

    /** Runs one application through the station and returns the modified tool. */
    private static ItemStack applyReagent(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool,
            ItemStack reagent) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, reagent);
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "expected the station to produce a modified warmace");
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }
}
