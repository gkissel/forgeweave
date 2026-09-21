package dev.gkissel.forgeweave.gametest;

import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.particle.ForgeweaveParticles;
import dev.gkissel.forgeweave.trait.Evasion;
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
        Pig first = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 1));
        Pig second = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
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

    private TraitFeedbackGameTests() {}
}
