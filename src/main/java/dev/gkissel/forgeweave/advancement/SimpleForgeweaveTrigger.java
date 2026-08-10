package dev.gkissel.forgeweave.advancement;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

/**
 * A criterion trigger with no payload beyond "this happened to this player" (docs/SCOPE.md M2 issue
 * #110's advancement chain). None of the chain's five steps -- the smeltery forming, first melt,
 * first cast, first alloy, first modifier application -- needs extra predicate data the way a vanilla
 * trigger like {@code EnchantedItemTrigger} needs to know which enchantment, so one class is reused
 * for all five {@link ForgeweaveCriteriaTriggers} registrations instead of five near-identical
 * subclasses that would each only ever construct a {@code TriggerInstance(Optional.empty())}.
 */
public class SimpleForgeweaveTrigger extends SimpleCriterionTrigger<SimpleForgeweaveTrigger.TriggerInstance> {
    @Override
    public Codec<TriggerInstance> codec() {
        return TriggerInstance.CODEC;
    }

    /** Fires unconditionally for {@code player} -- the "player" predicate is the only thing a datapack can vary. */
    public void trigger(ServerPlayer player) {
        trigger(player, instance -> true);
    }

    public record TriggerInstance(Optional<ContextAwarePredicate> player) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<TriggerInstance> CODEC = RecordCodecBuilder.create(builder -> builder
                .group(EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(TriggerInstance::player))
                .apply(builder, TriggerInstance::new));
    }
}
