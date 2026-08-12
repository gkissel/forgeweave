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
 * <p>Four of the five fire from code that already exists on master:
 *
 * <ul>
 *   <li>{@link #SMELTERY_FORMED} from {@code SmelteryControllerBlock#useWithoutItem} (issue #95),
 *       once the structure scan the player just triggered finds the smeltery formed.
 *   <li>{@link #FIRST_MELT} from {@code SmelteryControllerBlockEntity#insertForMelting(ItemStack,
 *       ServerPlayer)} (issue #96), when a player inserts an item with a valid melting recipe into a
 *       formed, hot smeltery -- see that overload's javadoc for why insertion, not completion, is the
 *       chosen moment, and its documented gap (a cold insert that only starts melting once fuel later
 *       arrives doesn't retroactively grant it).
 *   <li>{@link #FIRST_CAST} from {@code CastingBlockEntity#interact} (issue #100), when a player
 *       collects a finished casting result.
 *   <li>{@link #FIRST_MODIFIER} from {@code ToolStationMenu.OutputSlot#onTake} (issue #105), when the
 *       taken output came from a modifier application rather than assembly or repair.
 * </ul>
 *
 * <p>The fifth, {@link #FIRST_ALLOY}, fires from {@code
 * SmelteryControllerBlockEntity#grantFirstAlloy} (issue #98) whenever an alloy forms in a smeltery's
 * tank. Alloying genuinely has no player in its call chain -- it happens because the tank's own
 * contents became alloyable, which can be many ticks after the player who caused it last touched the
 * smeltery -- so of the options this javadoc originally left open (an NBT-tracked owner, or skipping
 * the trigger), #98 took the proximity one: every {@code ServerPlayer} within 16 blocks of the core is
 * credited. See that method's javadoc for the reasoning and its documented gap.
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

    /**
     * The M3-17 chain's tail (docs/SCOPE.md M3 issue #166), hung off {@link #FIRST_MODIFIER}: forge
     * (having a Tool Forge -- the M2 root's own "own the item" idiom, no new hook needed) -> large
     * tool -> emboss -> combat modifier. Three of the four need a real hook, all three from the same
     * place: {@code ToolStationMenu.OutputSlot#onTake}, the one spot that already knows which of
     * assembly/repair/modifier/embossing just produced the output taken.
     *
     * <ul>
     *   <li>{@link #LARGE_TOOL_ASSEMBLED} when the resolved output is one of {@code
     *       ToolAssemblyRecipes#LARGE_TOOLS} and the input was a fresh assembly, not a repair of one.
     *   <li>{@link #FIRST_EMBOSSMENT} when {@code Embossing#resolve} is the outcome that matched.
     *   <li>{@link #COMBAT_MODIFIER_APPLIED} alongside {@link #FIRST_MODIFIER}, when the applied
     *       recipe's modifier id is one of {@code ForgeweaveModifiers#isCombatModifier}'s eight.
     * </ul>
     */
    public static final DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> LARGE_TOOL_ASSEMBLED =
            TRIGGERS.register("large_tool_assembled", SimpleForgeweaveTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> FIRST_EMBOSSMENT =
            TRIGGERS.register("first_embossment", SimpleForgeweaveTrigger::new);
    public static final DeferredHolder<CriterionTrigger<?>, SimpleForgeweaveTrigger> COMBAT_MODIFIER_APPLIED =
            TRIGGERS.register("combat_modifier_applied", SimpleForgeweaveTrigger::new);

    private ForgeweaveCriteriaTriggers() {}
}
