package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Pins {@link ToolItem#attackDurabilityCost} against upstream 1.12's
 * {@code ToolCore#reduceDurabilityOnHit} (tinkers-1.12 {@code library/tools/ToolCore.java}, pinned
 * commit in NOTICE.md):
 *
 * <pre>
 * damage = Math.max(1f, damage / 10f);
 * if(!hasCategory(Category.WEAPON)) damage *= 2;
 * ToolHelper.damageTool(stack, (int) damage, player);
 * </pre>
 *
 * <p>Every M1 material lands under 10 attack damage, where the formula bottoms out on its
 * {@code max(1f, ...)} floor and produces the same 2 (or 1, for the hatchet) a flat constant would.
 * So the cases that actually distinguish the ported formula from a constant are the hypothetical
 * high-damage ones, which is most of what this test is: it is the only thing standing between the
 * formula and a future material that scales past 10.
 */
class ToolItemTest {

    @Test
    void nonWeaponsPayTwicePerHit() {
        // Below the floor: max(1, d/10) = 1, doubled = 2. Every M1 material is here.
        assertEquals(2, ToolItem.attackDurabilityCost(0.0F, false));
        assertEquals(2, ToolItem.attackDurabilityCost(4.0F, false));
        assertEquals(2, ToolItem.attackDurabilityCost(10.0F, false));

        // Above it the cost tracks damage. The doubling happens before the truncation, which is what
        // makes 15 cost 3 rather than the 2 a truncate-then-double reading would give.
        assertEquals(3, ToolItem.attackDurabilityCost(15.0F, false));
        assertEquals(4, ToolItem.attackDurabilityCost(20.0F, false));
        assertEquals(19, ToolItem.attackDurabilityCost(99.0F, false));
    }

    /** Upstream's hatchet is {@code Category.WEAPON}, which skips the doubling. */
    @Test
    void weaponsPayHalfThat() {
        assertEquals(1, ToolItem.attackDurabilityCost(4.0F, true));
        assertEquals(1, ToolItem.attackDurabilityCost(15.0F, true));
        assertEquals(2, ToolItem.attackDurabilityCost(20.0F, true));
        assertEquals(9, ToolItem.attackDurabilityCost(99.0F, true));
    }
}
