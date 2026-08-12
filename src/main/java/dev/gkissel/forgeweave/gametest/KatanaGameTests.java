package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.DamageRamp;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * docs/SCOPE.md M3 issue #160's verification for the katana's in-combat damage ramp: it grows across
 * consecutive landed hits, stops at the cap, lapses after the idle window, and its serialized state
 * survives a save/load round trip.
 *
 * <p>Magnitudes are the issue's maintainer decision (2026-08-12) and are read from
 * {@link DamageRamp#KATANA} rather than restated here, so a test can't quietly disagree with the
 * shipped numbers -- but the <em>shape</em> of each expectation (first blow unbuffed, one step per
 * landed hit, flat at the cap, zero after the window) is spelled out, which is the part that would
 * otherwise be assert-what-the-code-does.
 *
 * <p>Every blow goes through {@code LivingEntity#hurt} with a real player-attack damage source, the
 * same path a swing takes once vanilla's cooldown and crit maths are done with it -- the same staging
 * {@link CombatGameTests} uses. The target is an iron golem: 100 max health with no armor and no
 * damage-reduction attributes, so the health it loses <em>is</em> the damage the seam produced, with
 * nothing in between to explain away a wrong number.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class KatanaGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    private static final BlockPos TARGET = new BlockPos(3, 2, 3);
    /** Enough to read a percentage bonus off cleanly, and far below the golem's 100 health. */
    private static final float BASE_DAMAGE = 2.0F;
    private static final float TOLERANCE = 0.01F;

    /**
     * The ramp builds one step per landed hit and then holds at the cap: the first blow lands
     * unbuffed (nothing has been landed yet), each following blow carries the stacks the earlier ones
     * left, and once the bonus reaches the cap two more blows deal exactly the same as each other --
     * which is what "and not beyond" has to mean for a test that would otherwise pass on any
     * monotonic curve.
     */
    @GameTest(template = "empty")
    public static void rampGrowsPerHitAndHoldsAtTheCap(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        armWithKatana(helper, player);
        LivingEntity target = helper.spawn(EntityType.IRON_GOLEM, TARGET);
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        helper.assertTrue(source.getWeaponItem() != null && source.getWeaponItem().is(ForgeweaveItems.TOOL_KATANA.get()),
                "the blow must be attributed to the katana being tested");

        DamageRamp ramp = DamageRamp.KATANA;
        int blows = ramp.maxStacks() + 2;
        float previous = 0.0F;
        for (int i = 0; i < blows; i++) {
            float expectedBonus = Math.min(ramp.maxBonus(), i * ramp.stepPerStack());
            float dealt = strike(helper, target, source);

            helper.assertTrue(Math.abs(dealt - BASE_DAMAGE * (1.0F + expectedBonus)) < TOLERANCE,
                    "blow " + i + " should have carried a +" + expectedBonus + " ramp: expected "
                            + BASE_DAMAGE * (1.0F + expectedBonus) + " damage, got " + dealt);
            if (i > 0 && i <= ramp.maxStacks()) {
                helper.assertTrue(dealt > previous, "blow " + i + " should have hit harder than blow " + (i - 1));
            }
            previous = dealt;
        }

        float atCap = strike(helper, target, source);
        helper.assertTrue(Math.abs(atCap - previous) < TOLERANCE,
                "past the cap every further blow deals the same: expected " + previous + ", got " + atCap);

        target.discard();
        helper.succeed();
    }

    /**
     * The reset is a real wall-clock wait, not a rewritten timestamp: three landed hits, then nothing
     * for the whole idle window, and the next blow lands as unbuffed as the very first one did.
     * Waiting the window out in-game is the only version of this test that would catch a ramp keyed
     * off something other than elapsed time.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void rampLapsesAfterTheIdleWindow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack katana = armWithKatana(helper, player);
        LivingEntity target = helper.spawn(EntityType.IRON_GOLEM, TARGET);
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);

        for (int i = 0; i < 3; i++) {
            strike(helper, target, source);
        }
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(DamageRamp.KATANA.liveStacks(katana, gameTime) == 3,
                "three landed hits should have left three stacks, got " + DamageRamp.KATANA.liveStacks(katana, gameTime));

        // One tick past the window, so the boundary itself (elapsed == resetTicks) counts as lapsed.
        helper.runAfterDelay(DamageRamp.KATANA.resetTicks() + 1L, () -> {
            helper.assertTrue(DamageRamp.KATANA.liveStacks(katana, helper.getLevel().getGameTime()) == 0,
                    "the ramp should have lapsed after " + DamageRamp.KATANA.resetTicks() + " idle ticks");
            float dealt = strike(helper, target, source);
            helper.assertTrue(Math.abs(dealt - BASE_DAMAGE) < TOLERANCE,
                    "the first blow after the window should be unbuffed: expected " + BASE_DAMAGE + ", got " + dealt);

            target.discard();
            helper.succeed();
        });
    }

    /**
     * The save-compat half in-game, next to the {@code fixtures/save_compat/m3_tool_katana_ramp.snbt}
     * corpus entry: a katana with a live ramp goes through the same encode/decode the game uses to
     * write and read a stack, and comes back with the same stacks and the same last-hit stamp -- so
     * the ramp survives a world save rather than only surviving inside one session's memory.
     */
    @GameTest(template = "empty")
    public static void rampStateSurvivesASerializationRoundTrip(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack katana = armWithKatana(helper, player);
        LivingEntity target = helper.spawn(EntityType.IRON_GOLEM, TARGET);
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        strike(helper, target, source);
        strike(helper, target, source);

        DamageRamp.State before = katana.get(ForgeweaveDataComponents.KATANA_RAMP.get());
        helper.assertTrue(before != null, "two landed hits should have written a ramp component");

        CompoundTag saved = (CompoundTag) katana.save(helper.getLevel().registryAccess());
        ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), saved)
                .orElseThrow(() -> new AssertionError("a saved katana failed to load back"));
        DamageRamp.State after = loaded.get(ForgeweaveDataComponents.KATANA_RAMP.get());

        helper.assertTrue(before.equals(after),
                "the ramp must survive save/load unchanged: wrote " + before + ", read back " + after);
        helper.assertTrue(DamageRamp.KATANA.liveStacks(loaded, helper.getLevel().getGameTime()) == 2,
                "the loaded katana should still carry both stacks");

        target.discard();
        helper.succeed();
    }

    /**
     * Assembles a katana at a real Tool Station and puts it in the player's hand. Materials are one
     * per part slot in {@code ToolConstants.KATANA}'s own order -- wooden handle, stone blade, wooden
     * hand guard.
     */
    private static ItemStack armWithKatana(GameTestHelper helper, Player player) {
        ItemStack katana = ToolAssembly.assemble(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_KATANA.get()), List.of("wood", "stone", "wood"));
        helper.assertTrue(katana.is(ForgeweaveItems.TOOL_KATANA.get()),
                "the Tool Station should have assembled a katana, got " + katana);
        player.setItemInHand(InteractionHand.MAIN_HAND, katana);
        return katana;
    }

    /**
     * One blow, and the health it actually cost. The target is topped up and its invulnerability
     * window cleared first, so consecutive blows all measure against the same starting health instead
     * of being swallowed by vanilla's post-hit immunity.
     */
    private static float strike(GameTestHelper helper, LivingEntity target, DamageSource source) {
        target.setHealth(target.getMaxHealth());
        target.invulnerableTime = 0;
        float before = target.getHealth();
        target.hurt(source, BASE_DAMAGE);
        return before - target.getHealth();
    }
}
