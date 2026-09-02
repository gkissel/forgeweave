package dev.gkissel.forgeweave.trait;

/**
 * {@code extra_modifier_slots(count)}: the tool carrying this trait gets {@code count} more
 * modifier slots -- the M6 library shape issue #829 generalized paper's {@code writable} /
 * {@code writable2} pair onto ({@code ForgeweaveTraits#WRITABLE}), now a record so a datapack can
 * instantiate it too (issue #832, {@link TraitBehaviors}).
 */
public record ExtraModifierSlots(int count) implements Trait {

    @Override
    public int bonusSlots() {
        return count;
    }
}
