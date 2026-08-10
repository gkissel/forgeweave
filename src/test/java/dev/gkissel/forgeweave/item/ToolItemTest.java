package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolStats;

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

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

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

    // ------------------------------------------------------------------ #108 batch: modern-vanilla modifiers

    /**
     * Aquadynamic: {@link ToolItem#getDefaultAttributeModifiers} only adds the
     * {@code player.submerged_mining_speed} attribute modifier when the tool actually carries the
     * modifier, and adds our chosen {@code +0.8} (restoring the vanilla 0.2x submerged penalty to a
     * full 1.0x) when it does.
     */
    @Test
    void aquadynamicAddsASubmergedMiningSpeedAttributeModifierOnlyWhenPresent() {
        ItemStack plain = assembledPickaxe();
        assertTrue(attribute(plain, Attributes.SUBMERGED_MINING_SPEED).isEmpty(),
                "an unmodified tool must not carry the attribute at all");

        ItemStack aquadynamic = withModifier(plain, "aquadynamic");
        Optional<ItemAttributeModifiers.Entry> entry = attribute(aquadynamic, Attributes.SUBMERGED_MINING_SPEED);
        assertTrue(entry.isPresent(), "expected a submerged mining speed attribute modifier");
        assertEquals(0.8, entry.get().modifier().amount(), 1.0e-5);
    }

    /** Far Reach: same shape, {@code player.block_interaction_range}, our chosen {@code +1} per level. */
    @Test
    void farReachAddsABlockInteractionRangeAttributeModifierOnlyWhenPresent() {
        ItemStack plain = assembledPickaxe();
        assertTrue(attribute(plain, Attributes.BLOCK_INTERACTION_RANGE).isEmpty(),
                "an unmodified tool must not carry the attribute at all");

        ItemStack farReach = withModifier(plain, "far_reach");
        Optional<ItemAttributeModifiers.Entry> entry = attribute(farReach, Attributes.BLOCK_INTERACTION_RANGE);
        assertTrue(entry.isPresent(), "expected a block interaction range attribute modifier");
        assertEquals(1.0, entry.get().modifier().amount(), 1.0e-5);
    }

    private static ItemStack assembledPickaxe() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), new ToolStats.Stats(160, 4.0F, 3.0F));
        return stack;
    }

    private static ItemStack withModifier(ItemStack stack, String modifierPath) {
        ItemStack copy = stack.copy();
        copy.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", modifierPath), 1)));
        return copy;
    }

    private static Optional<ItemAttributeModifiers.Entry> attribute(ItemStack stack, Holder<Attribute> attribute) {
        return ForgeweaveItems.TOOL_PICKAXE.get().getDefaultAttributeModifiers(stack).modifiers().stream()
                .filter(entry -> entry.attribute() == attribute)
                .findFirst();
    }
}
