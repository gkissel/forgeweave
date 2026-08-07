package dev.gkissel.forgeweave.tool;

import dev.gkissel.forgeweave.material.Material;

/**
 * Derives an assembled tool's base stats from its head/binding/handle materials. Ported from
 * upstream 1.12's {@code ToolNBT#head}/{@code #extra}/{@code #handle}
 * (tinkers-1.12 {@code library/tools/ToolNBT.java}, pinned commit in NOTICE.md): that class
 * averages across multiple materials per slot (a tool could have two heads, e.g. a hammer), but
 * Forgeweave's parts are always exactly one material each, so the averaging divides by 1 and drops
 * out, leaving:
 *
 * <pre>
 * durability = round((headDurability + bindingExtraDurability) * handleDurabilityModifier)
 *              + handleDurabilityBonus, minimum 1
 * miningSpeed = headMiningSpeed
 * attackDamage = headAttackDamage
 * </pre>
 *
 * <p>ponytail: issue #11 owns tool behavior (mining, combat, repair, traits) and will build on top
 * of this -- kept intentionally small and pure (materials in, stats out; no NBT/component/item
 * plumbing) so it's easy to extend or replace piecemeal.
 */
public final class ToolStats {

    public record Stats(int durability, float miningSpeed, float attackDamage) {}

    public static Stats compute(Material head, Material binding, Material handle) {
        int durability = head.head().durability() + binding.extraDurability();
        durability = Math.round(durability * handle.handle().durabilityModifier()) + handle.handle().durability();
        durability = Math.max(1, durability);

        return new Stats(durability, head.head().miningSpeed(), head.head().attackDamage());
    }

    private ToolStats() {}
}
