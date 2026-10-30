package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import dev.gkissel.forgeweave.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import dev.gkissel.forgeweave.gametest.GameTestHolder;
import dev.gkissel.forgeweave.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.particle.ForgeweaveParticles;
import dev.gkissel.forgeweave.trait.Evasion;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.TraitFeedback;

/**
 * Issue #1112 on a running server: the proc cue resolves and broadcasts once, and the rate limit
 * swallows a repeat inside its window. The parse-side and override-side halves are unit tests
 * ({@code TraitFeedbackTest}); what needs a real server is the two things a unit test cannot reach
 * -- that the mod's own heart particles resolve out of the deferred registry, and that building and
 * sending the payload over a real level does not throw.
 *
 * <p>Each test uses a fresh behaviour instance, because the limiter keys on
 * (entity, behaviour identity) and a fresh instance is a clean slate without reaching into it.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TraitFeedbackGameTests {

    @GameTest(template = "empty")
    public static void aProcAnnouncesItselfOncePerCooldownPerEntity(GameTestHelper helper) {
        Pig first = helper.spawn(EntityTypes.PIG, new BlockPos(1, 2, 1));
        Pig second = helper.spawn(EntityTypes.PIG, new BlockPos(2, 2, 2));
        first.setNoAi(true);
        second.setNoAi(true);
        Evasion behaviour = new Evasion(1.0F);

        helper.assertTrue(TraitFeedback.fire(behaviour, TraitFeedback.Kind.DODGE, helper.getLevel(), first),
                "the first proc on an entity must broadcast");
        helper.assertFalse(TraitFeedback.fire(behaviour, TraitFeedback.Kind.DODGE, helper.getLevel(), first),
                "a second proc in the same tick must be swallowed by the rate limit");
        helper.assertTrue(TraitFeedback.fire(behaviour, TraitFeedback.Kind.DODGE, helper.getLevel(), second),
                "another entity's proc must not be swallowed by the first entity's cooldown");
        helper.assertTrue(TraitFeedback.fire(new Evasion(1.0F), TraitFeedback.Kind.DODGE, helper.getLevel(), first),
                "another behaviour's proc on the same entity must not be swallowed either");
        helper.succeed();
    }

    /**
     * The five heart particles are {@code DeferredRegister} entries, so a {@link TraitFeedback.Kind}
     * naming one only resolves once the registry is filled -- which is what this asserts, and what
     * no unit test can.
     */
    @GameTest(template = "empty")
    public static void aKindNamingAHeartParticleResolvesOnARunningServer(GameTestHelper helper) {
        TraitFeedback.Feedback shock = TraitFeedback.resolve(new Evasion(1.0F), TraitFeedback.Kind.SHOCK);
        helper.assertValueEqual(shock.particle(), Optional.of(ForgeweaveParticles.HEART_ELECTRO.get()),
                "the shock cue's particle");
        helper.assertTrue(shock.sound().isPresent(), "every kind names a sound");
        helper.succeed();
    }

    /**
     * Checklist line 88: which behaviour fires which cue, and on whom. A dodge is DODGE and an effect
     * left on the attacker is AFFLICT on the attacker; self-repair is MEND on the holder; lifesteal is
     * DRAIN on the wielder, which is why it shows above you; a death save is SAVE on the wearer. Each
     * runs a shipped trait through its real hook, with {@link TraitFeedback#record} watching.
     */
    @GameTest(template = "empty")
    public static void eachBehaviourAnnouncesItsOwnKindOnTheRightEntity(GameTestHelper helper) {
        List<Map.Entry<TraitFeedback.Kind, Entity>> fired = new ArrayList<>();
        TraitFeedback.record((kind, at) -> fired.add(Map.entry(kind, at)));
        try {
            Zombie attacker = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
            attacker.setNoAi(true);
            DamageSource bite = helper.getLevel().damageSources().mobAttack(attacker);

            Player dodger = TraitPairingGameTests.wearing(helper, "voidward2");
            for (int i = 0; i < 300 && !fired.contains(Map.entry(TraitFeedback.Kind.DODGE, (Entity) dodger)); i++) {
                TraitPairingGameTests.lost(dodger, bite, 1.0F);
            }
            assertFired(helper, fired, TraitFeedback.Kind.DODGE, dodger, "a dodge, on the wearer");

            Player warded = TraitPairingGameTests.wearing(helper, "venomward");
            for (int i = 0; i < 300 && !fired.contains(Map.entry(TraitFeedback.Kind.AFFLICT, (Entity) attacker)); i++) {
                TraitPairingGameTests.lost(warded, bite, 1.0F);
            }
            assertFired(helper, fired, TraitFeedback.Kind.AFFLICT, attacker, "an effect on the attacker, on the attacker");

            Player holder = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack worn = CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(),
                    List.of(CombatTraitGameTests.traitId("ecological")), 3.0F);
            worn.setDamageValue(500);
            holder.setItemInHand(InteractionHand.MAIN_HAND, worn);
            for (int i = 0; i < 6000 && !fired.contains(Map.entry(TraitFeedback.Kind.MEND, (Entity) holder)); i++) {
                ForgeweaveTraits.inventoryTick(worn, helper.getLevel(), holder);
            }
            assertFired(helper, fired, TraitFeedback.Kind.MEND, holder, "self-repair, on the holder");

            Player wielder = helper.makeMockPlayer(GameType.SURVIVAL);
            wielder.setHealth(wielder.getMaxHealth() - 5.0F);
            CombatTraitGameTests.onHit(helper, wielder, CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(),
                    List.of(CombatTraitGameTests.traitId("soulrend")), 3.0F), attacker);
            assertFired(helper, fired, TraitFeedback.Kind.DRAIN, wielder, "lifesteal, on the wielder");

            Player saved = TraitPairingGameTests.wearing(helper, "lastbreath");
            saved.setHealth(2.0F);
            saved.damageCooldownTime = 0;
            saved.hurt(helper.getLevel().damageSources().generic(), 40.0F);
            assertFired(helper, fired, TraitFeedback.Kind.SAVE, saved, "a death save, on the wearer");
            attacker.discard();
        } finally {
            TraitFeedback.record(null);
        }
        helper.succeed();
    }

    private static void assertFired(GameTestHelper helper, List<Map.Entry<TraitFeedback.Kind, Entity>> fired,
            TraitFeedback.Kind kind, Entity at, String what) {
        helper.assertTrue(fired.contains(Map.entry(kind, at)), what + " must fire " + kind + ", fired " + fired);
    }

    private TraitFeedbackGameTests() {}
}
