package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import net.minecraft.SharedConstants;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.sounds.SoundEvents;

import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.combat.Lifesteal;

/**
 * Issue #1112's decision half: which particle and sound a proc resolves to, and how often it is
 * allowed to fire. Both are the pure part of {@code TraitFeedback#fire}, so they are pinned here
 * rather than on a running server; {@code TraitFeedbackGameTests} covers the rest.
 *
 * <p>Every kind asserted here names a vanilla particle on purpose: the five heart particles are
 * {@code DeferredRegister} entries that a bare {@code Bootstrap} does not fill, and resolving one
 * is the GameTest's job.
 */
class TraitFeedbackTest {

    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("somepack", "a_trait");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @BeforeEach
    @AfterEach
    void clearState() {
        TraitFeedback.datapack(Map.of());
        TraitFeedback.resetRateLimit();
    }

    @Test
    void aTraitWithNoDefinitionUsesItsKindsOwnPair() {
        TraitFeedback.Feedback dodge = TraitFeedback.resolve(new Evasion(0.15F), TraitFeedback.Kind.DODGE);
        assertEquals(Optional.of(ParticleTypes.POOF), dodge.particle());
        assertEquals(Optional.of(SoundEvents.SHIELD_BLOCK), dodge.sound());
        assertFalse(dodge.isEmpty());
    }

    /** The five kinds whose particle is a vanilla one; the other five are the GameTest's. */
    @ParameterizedTest
    @EnumSource(value = TraitFeedback.Kind.class,
            names = {"AFFLICT", "DODGE", "MEND", "SAVE", "HARVEST"})
    void aKindNamesBothAParticleAndASound(TraitFeedback.Kind kind) {
        TraitFeedback.Feedback defaults = kind.defaults();
        assertTrue(defaults.particle().isPresent(), kind + " must name a particle");
        assertTrue(defaults.sound().isPresent(), kind + " must name a sound");
        assertFalse(defaults.isEmpty());
    }

    @Test
    void aDefinitionsOwnSoundReplacesItsKindsAndKeepsTheKindsParticle() {
        Trait trait = new Evasion(0.15F);
        declare(trait, new TraitFeedback.Feedback(Optional.empty(), Optional.of(SoundEvents.ANVIL_LAND), false));

        TraitFeedback.Feedback resolved = TraitFeedback.resolve(trait, TraitFeedback.Kind.DODGE);
        assertEquals(Optional.of(ParticleTypes.POOF), resolved.particle(), "the kind still supplies the particle");
        assertEquals(Optional.of(SoundEvents.ANVIL_LAND), resolved.sound());
    }

    @Test
    void aSilentDefinitionSaysNothingWhateverItsKindWouldHaveDone() {
        Trait trait = new Evasion(0.15F);
        declare(trait, new TraitFeedback.Feedback(Optional.of(ParticleTypes.FLAME),
                Optional.of(SoundEvents.ANVIL_LAND), true));

        assertTrue(TraitFeedback.resolve(trait, TraitFeedback.Kind.DODGE).isEmpty(),
                "silent wins over both its own fields and the kind's");
    }

    /**
     * A combat-seam behaviour fires the proc and only ever sees itself, never the
     * {@link TraitBehaviors.SeamTrait} the definition wrapped it in -- so the override table has to
     * carry the seam too.
     */
    @Test
    void aSeamBehaviourFindsTheOverrideItsWrappingDefinitionDeclared() {
        Lifesteal seam = new Lifesteal(0.15F, 4.0F);
        TraitBehaviors.SeamTrait wrapper = new TraitBehaviors.SeamTrait(TraitBehaviors.Gate.NONE, seam);
        declare(wrapper, new TraitFeedback.Feedback(Optional.of(ParticleTypes.FLAME), Optional.empty(), false));

        assertEquals(Optional.of(ParticleTypes.FLAME), TraitFeedback.resolve(seam, TraitFeedback.Kind.DRAIN).particle());
    }

    @Test
    void theRateLimitIsPerEntityAndPerBehaviour() {
        Object behaviour = new Evasion(0.15F);
        Object other = new Evasion(0.15F);

        assertTrue(TraitFeedback.allow(1, behaviour, 100L), "a first proc always goes through");
        assertFalse(TraitFeedback.allow(1, behaviour, 100L), "the same tick is inside the window");
        assertFalse(TraitFeedback.allow(1, behaviour, 100L + TraitFeedback.COOLDOWN_TICKS - 1));
        assertTrue(TraitFeedback.allow(1, behaviour, 100L + TraitFeedback.COOLDOWN_TICKS));

        assertTrue(TraitFeedback.allow(2, behaviour, 100L + TraitFeedback.COOLDOWN_TICKS),
                "another entity has its own cooldown");
        assertTrue(TraitFeedback.allow(1, other, 100L + TraitFeedback.COOLDOWN_TICKS),
                "another behaviour on the same entity has its own cooldown too");
    }

    @Test
    void aClockThatMovedBackwardsDoesNotLockAPairOut() {
        Object behaviour = new Evasion(0.15F);
        assertTrue(TraitFeedback.allow(1, behaviour, 10_000L));
        assertTrue(TraitFeedback.allow(1, behaviour, 5L), "a reloaded world's earlier tick is not a live cooldown");
    }

    private static void declare(Trait trait, TraitFeedback.Feedback feedback) {
        TraitFeedback.datapack(Map.of(ID,
                new TraitDefinition(ID, trait, TraitFamilies.Rung.NONE, feedback)));
    }
}
