package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issues #162 and #163's verification (docs/SCOPE.md M3): both combat-modifier batches -- smite, bane
 * of arthropods, fiery, necrotic (#162); knockback, shulking, webbed (#163) -- driven through the real
 * per-hit pipeline ({@code CombatSeams}) the same way {@code CombatGameTests} exercises innates and
 * traits, plus the slot cap every modifier respects ({@code ModifierGameTests}' established coverage,
 * repeated here against these seven ids specifically). Every tool here is a bare stack carrying one
 * modifier entry, the same shortcut {@code ModifierGameTests#withModifier} uses for modifiers that
 * don't need an assembled tool's stats; the pure-function coverage of {@code Modifier#combatSeam} and
 * the #162 math it's built from is {@code modifier.CombatModifiersTest}/{@code CombatModifierBatch1Test}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CombatModifierGameTests {

    // ---------------------------------------------------------------- #162 batch (smite, bane of
    // arthropods, fiery, necrotic)

    private static final ResourceLocation SMITE = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "smite");
    private static final ResourceLocation BANE_OF_ARTHROPODS =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "bane_of_arthropods");
    private static final ResourceLocation FIERY = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "fiery");
    private static final ResourceLocation NECROTIC = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "necrotic");

    /**
     * Smite (upstream {@code ModAntiMonsterType("smite", ..., 5, 24, UNDEAD)}): one full level (24
     * raw units) adds a flat +7 damage against undead only -- a zombie loses the bonus, a pig does not.
     */
    @GameTest(template = "empty")
    public static void smiteBonusAppliesOnlyAgainstUndead(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, withModifier(SMITE, 24));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        // Zombies carry 2 points of base armor, so the +7 bonus (applied before mitigation, ADR-0005)
        // lands as ~7.87 actual health lost rather than a clean 8 -- the threshold below only needs to
        // separate that from an unbonused hit's ~0.94, not match the mitigated number exactly.
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.hurt(source, 1.0F);
        helper.assertTrue(zombie.getHealth() <= zombie.getMaxHealth() - 5.0F,
                "1 base damage + smite's +7 at level 1 must take well over 1 health off an undead target, "
                        + "left it at " + zombie.getHealth() + "/" + zombie.getMaxHealth());

        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 2));
        pig.hurt(source, 1.0F);
        helper.assertTrue(pig.getHealth() > pig.getMaxHealth() - 2.0F,
                "smite must not bonus a non-undead target, left it at " + pig.getHealth() + "/" + pig.getMaxHealth());

        zombie.discard();
        pig.discard();
        helper.succeed();
    }

    /**
     * Bane of arthropods (upstream {@code ModAntiMonsterType("bane_of_arthopods", ..., 5, 24,
     * ARTHROPOD)}): same +7-per-level shape as smite, keyed on arthropods instead -- a spider takes the
     * bonus, a pig does not.
     */
    @GameTest(template = "empty")
    public static void baneBonusAppliesOnlyAgainstArthropods(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, withModifier(BANE_OF_ARTHROPODS, 24));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        Spider spider = helper.spawn(EntityType.SPIDER, new BlockPos(2, 2, 2));
        spider.hurt(source, 1.0F);
        helper.assertTrue(spider.getHealth() <= spider.getMaxHealth() - 8.0F,
                "1 base damage + bane's +7 at level 1 must take at least 8 health off an arthropod, "
                        + "left it at " + spider.getHealth() + "/" + spider.getMaxHealth());

        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 2));
        pig.hurt(source, 1.0F);
        helper.assertTrue(pig.getHealth() > pig.getMaxHealth() - 2.0F,
                "bane must not bonus a non-arthropod target, left it at " + pig.getHealth() + "/" + pig.getMaxHealth());

        spider.discard();
        pig.discard();
        helper.succeed();
    }

    /**
     * Fiery (upstream {@code ModFiery#dealFireDamage}): 15 raw units gives a clean {@code 15/8 = 1}
     * second duration bump ({@code getFireDuration = 1 + units/8}, so 2 seconds total) and exactly 1.0
     * true fire damage ({@code getFireDamage = units/15}) on top of the primary blow.
     */
    @GameTest(template = "empty")
    public static void fieryIgnitesAndDealsBonusFireDamage(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, withModifier(FIERY, 15));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        target.hurt(source, 1.0F);

        helper.assertTrue(target.getRemainingFireTicks() > 0, "fiery must ignite the target on hit");
        helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 2.0F,
                "the primary 1 damage plus fiery's own 1.0 true fire damage must take at least 2 health, "
                        + "left it at " + target.getHealth() + "/" + target.getMaxHealth());

        target.discard();
        helper.succeed();
    }

    /**
     * Necrotic (upstream {@code ModNecrotic#afterHit}): {@code lifesteal = 0.10f * level}, so level 5
     * heals the attacker for half the damage it deals.
     */
    @GameTest(template = "empty")
    public static void necroticHealsTheAttackerOnHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, withModifier(NECROTIC, 5));
        player.setHealth(10.0F);
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        target.hurt(source, 4.0F);

        helper.assertTrue(Math.abs(player.getHealth() - 12.0F) < 0.01F,
                "level 5 necrotic (50% lifesteal) on a 4-damage hit must heal the attacker to 12, got "
                        + player.getHealth());

        target.discard();
        helper.succeed();
    }

    /**
     * Slot cap (docs/SCOPE.md M3 acceptance test 4, ADR-0004): these four modifiers occupy a slot each
     * like every other modifier -- three of them fill a fresh tool's three slots, and applying the
     * fourth through the real Tool Station with its shipped reagent (necrotic's wither skeleton skull)
     * is rejected.
     */
    @GameTest(template = "empty")
    public static void combatModifiersRespectTheModifierSlotCap(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(SMITE, 24),
                new ModifierEntry(BANE_OF_ARTHROPODS, 24),
                new ModifierEntry(FIERY, 25)));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(pickaxe) == 0,
                "three combat modifiers must fill the tool's three slots, got "
                        + ForgeweaveModifiers.freeSlots(pickaxe) + " free");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().setItem(0, pickaxe);
        blockEntity.container().setItem(1, new ItemStack(Items.WITHER_SKELETON_SKULL, 1));
        blockEntity.container().setItem(2, ItemStack.EMPTY);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a fourth distinct combat modifier must produce no output once the three slots are full");
        helper.assertTrue(menu.rejection() != null, "a refused application must tell the player why");
        helper.succeed();
    }

    // ---------------------------------------------------------------- #163 batch (knockback, shulking, webbed)

    private static final ResourceLocation KNOCKBACK = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "knockback");
    private static final ResourceLocation SHULKING = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "shulking");
    private static final ResourceLocation WEBBED = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "webbed");

    /** More application units must push a target back further -- issue #163's own acceptance line. */
    @GameTest(template = "empty")
    public static void knockbackDisplacementScalesWithLevel(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        double weakPush = pushOnHit(helper, player, pos, withModifier(KNOCKBACK, 10));
        double strongPush = pushOnHit(helper, player, pos, withModifier(KNOCKBACK, 100));

        helper.assertTrue(weakPush > 0.0, "ten application units must push at all, got " + weakPush);
        helper.assertTrue(strongPush > weakPush,
                "a hundred application units must push further than ten: " + strongPush + " vs " + weakPush);
        helper.succeed();
    }

    /** Slot cap: one entry, however far levelled, occupies exactly one of the tool's three slots. */
    @GameTest(template = "empty")
    public static void knockbackOccupiesExactlyOneSlot(GameTestHelper helper) {
        ItemStack tool = withModifier(KNOCKBACK, 500);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(tool) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "knockback must occupy exactly one modifier slot regardless of level, got "
                        + ForgeweaveModifiers.freeSlots(tool) + " free");
        helper.succeed();
    }

    /** Shulking (issue #163): a landed hit grants Levitation I, duration upstream's raw-unit formula. */
    @GameTest(template = "empty")
    public static void shulkingAppliesLevitation(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tool = withModifier(SHULKING, 30);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(tool) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "shulking must occupy exactly one modifier slot");

        Pig target = strike(helper, player, pos, tool);

        MobEffectInstance levitation = target.getEffect(MobEffects.LEVITATION);
        helper.assertTrue(levitation != null, "expected Levitation on the struck target");
        helper.assertTrue(levitation.getAmplifier() == 0, "Levitation I, got amplifier " + levitation.getAmplifier());
        // Upstream ModShulking#getDuration: current / 2 + 10 -- 30 / 2 + 10 == 25.
        helper.assertTrue(levitation.getDuration() == 25,
                "expected 25 duration ticks for 30 application units, got " + levitation.getDuration());
        target.discard();
        helper.succeed();
    }

    /** Webbed (issue #163): a landed hit grants Slowness II, one second per level. */
    @GameTest(template = "empty")
    public static void webbedAppliesSlowness(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack tool = withModifier(WEBBED, 2);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(tool) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "webbed must occupy exactly one modifier slot");

        Pig target = strike(helper, player, pos, tool);

        MobEffectInstance slowness = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slowness != null, "expected Slowness on the struck target");
        helper.assertTrue(slowness.getAmplifier() == 1, "Slowness II, got amplifier " + slowness.getAmplifier());
        helper.assertTrue(slowness.getDuration() == 40,
                "expected 40 duration ticks (2 levels * 20), got " + slowness.getDuration());
        target.discard();
        helper.succeed();
    }

    /** Strikes a fresh pig with {@code tool} and returns it, still alive, for the caller to inspect. */
    private static Pig strike(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool) {
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);
        Pig target = helper.spawn(EntityType.PIG, pos.offset(1, 0, 1));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        helper.assertTrue(source.getWeaponItem() == tool, "the blow must be attributed to the tool under test");
        target.hurt(source, 1.0F);
        helper.assertTrue(target.isAlive(), "a 1-damage blow must not kill a pig, or this proves nothing");
        return target;
    }

    /** As {@link #strike}, returning the horizontal knockback the target ended up with. */
    private static double pushOnHit(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool) {
        Pig target = strike(helper, player, pos, tool);
        double dx = target.getDeltaMovement().x;
        double dz = target.getDeltaMovement().z;
        target.discard();
        return Math.hypot(dx, dz);
    }

    /** A bare pickaxe carrying one entry of {@code id} at {@code level} raw application units. */
    private static ItemStack withModifier(ResourceLocation id, int level) {
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(id, level)));
        return pickaxe;
    }
}
