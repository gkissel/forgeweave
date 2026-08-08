package dev.gkissel.forgeweave.trait;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * A behavior a {@code Material} grants to every Tool containing it (CONTEXT.md glossary). Trait
 * behavior is Java, the Material -&gt; Trait assignment is data (ADR-0002), so implementations live in
 * {@link ForgeweaveTraits}'s registry and material JSON only names an id.
 *
 * <p>ponytail: exactly the five hooks the four M1 traits need, no more. Upstream 1.12's
 * {@code library/traits/ITrait.java} has around twenty; every one of them that no shipped trait uses
 * would be an empty seam here. Add a hook when a trait needs it.
 *
 * <p>Everything here runs server-side only, from the seams in {@code ToolItem},
 * {@code ToolAssemblyRecipes} and {@link ForgeweaveTraits#onIncomingDamage} -- except
 * {@link #headDurability}, which {@code ToolStats} applies while the tool is being assembled.
 */
public interface Trait {

    /**
     * The assembled tool's durability after this trait has adjusted it, applied only when the trait
     * came from the <b>head</b> material. Upstream 1.12's head-only traits reach the finished stat
     * block through {@code TinkerEvent.OnItemBuilding} (see {@code TraitCheapskate}), which fires
     * once at assembly with the whole {@code ToolNBT} in hand; this is the same seam narrowed to the
     * one field an M1 trait touches.
     *
     * @param durability what {@code ToolStats}'s material formula alone produced
     */
    default int headDurability(int durability) {
        return durability;
    }

    /**
     * Called every tick the tool sits in a living entity's inventory, server side, and never while
     * the tool is Broken.
     */
    default void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {}

    /**
     * Extra durability restored on top of {@code amount}, per repair item, at the Tool Station.
     *
     * @param amount what {@code ToolRepair} alone would restore
     */
    default int repairBonus(int amount) {
        return 0;
    }

    /** Flat attack damage added to the tool's attack damage attribute modifier. */
    default float attackDamageBonus() {
        return 0.0F;
    }

    /**
     * Extra damage this hit deals to {@code target}, on top of what the tool already deals.
     *
     * @param damage the incoming damage before any trait touched it
     */
    default float bonusDamageAgainst(LivingEntity target, float damage) {
        return 0.0F;
    }
}
