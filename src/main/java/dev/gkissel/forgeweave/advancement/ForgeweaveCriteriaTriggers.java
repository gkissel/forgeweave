package dev.gkissel.forgeweave.advancement;

import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.core.registries.Registries;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Custom advancement criterion triggers for the M2-19 advancement chain (docs/SCOPE.md M2 issue
 * #110): build smeltery -> first melt -> first cast -> first alloy -> first modifier. No vanilla
 * trigger fits any of these (there is no "structure formed" or "casting finished" criterion), so all
 * five are {@link SimpleForgeweaveTrigger} registrations -- see that class's javadoc for why one
 * shared class covers all five.
 *
 * <p>Registered against {@code minecraft:trigger_type} ({@link Registries#TRIGGER_TYPE}), the same
 * built-in registry {@code net.minecraft.advancements.CriteriaTriggers} populates for every vanilla
 * trigger -- a {@link DeferredRegister} on it works exactly like one on {@code Registries.BLOCK} or
 * {@code Registries.ITEM}.
 *
 * <p>Three of the five fire from code that already exists on master:
 *
 * <ul>
 *   <li>{@link #SMELTERY_FORMED} from {@code SmelteryControllerBlock#useWithoutItem} (issue #95),
 *       once the structure scan the player just triggered finds the smeltery formed.
 *   <li>{@link #FIRST_CAST} from {@code CastingBlockEntity#interact} (issue #100), when a player
 *       collects a finished casting result.
 *   <li>{@link #FIRST_MODIFIER} from {@code ToolStationMenu.OutputSlot#onTake} (issue #105), when the
 *       taken output came from a modifier application rather than assembly or repair.
 * </ul>
 *
 * <p>{@link #FIRST_MELT} and {@link #FIRST_ALLOY} have no such call site: melting (#96) and alloying
 * (#98) are not merged yet, so both are seams only. When those land, add one line each at the point
 * each recipe finishes, guarded server-side with a real {@code ServerPlayer} the same way the three
 * above are:
 *
 * <pre>{@code
 * ForgeweaveCriteriaTriggers.FIRST_MELT.get().trigger(serverPlayer);   // #96, first item melted
 * ForgeweaveCriteriaTriggers.FIRST_ALLOY.get().trigger(serverPlayer);  // #98, first in-tank alloy
 * }</pre>
 *
 * <p>Melting today has no player in the loop at all -- ore goes in through a faucet pour, not a
 * player interaction -- so #96/#98 may need to resolve one (the smeltery's placer, tracked NBT-side,
 * or simply skip the trigger when no player is nearby) rather than reuse an existing parameter; that
 * call is left to those PRs.
 */
public final class ForgeweaveCriteriaTriggers {
    public static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, Forgeweave.MODID);

    public static final DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> SMELTERY_FORMED =
            TRIGGERS.register("smeltery_formed", SimpleForgeweaveTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> FIRST_MELT =
            TRIGGERS.register("first_melt", SimpleForgeweaveTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> FIRST_CAST =
            TRIGGERS.register("first_cast", SimpleForgeweaveTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> FIRST_ALLOY =
            TRIGGERS.register("first_alloy", SimpleForgeweaveTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> FIRST_MODIFIER =
            TRIGGERS.register("first_modifier", SimpleForgeweaveTrigger::new);

    private ForgeweaveCriteriaTriggers() {}
}
