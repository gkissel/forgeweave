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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #163's integration verification (docs/SCOPE.md M3, combat modifiers batch 2): knockback,
 * shulking and webbed, each riding a real blow through the shared per-hit pipeline
 * ({@code CombatSeams}) rather than calling their {@code Modifier#combatSeam} hook directly -- the
 * pure-function coverage of that hook is {@code modifier.CombatModifiersTest}. Every tool here is a
 * bare stack carrying one modifier entry, the same shortcut {@code ModifierGameTests#withModifier}
 * uses for modifiers that don't need an assembled tool's stats.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CombatModifierGameTests {

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

    /** A bare pickaxe carrying one level of {@code id} -- these modifiers don't need an assembled tool. */
    private static ItemStack withModifier(ResourceLocation id, int level) {
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(id, level)));
        return pickaxe;
    }
}
