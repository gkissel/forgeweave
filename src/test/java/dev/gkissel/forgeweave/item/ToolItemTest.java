package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ShockingCharge;

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

    // --------------------------------------------------------------------- #164: M1 innate retrofit

    /**
     * Sunder's shield-disable half (docs/SCOPE.md M3 issue #164): "make the hatchet count as an axe
     * for shield-disabling" is {@link ToolItem#canDisableShield} returning {@code true} only for the
     * hatchet -- the {@link #weapon} flag it already reads is upstream's one {@code Category.WEAPON}
     * tool. All four parameters are ignored by that override, so {@code null} is fine here.
     */
    @Test
    void onlyTheHatchetCanDisableShields() {
        assertTrue(ForgeweaveItems.TOOL_HATCHET.get().canDisableShield(null, null, null, null),
                "the hatchet should count as an axe for vanilla's shield-disable mechanic");
        assertFalse(ForgeweaveItems.TOOL_PICKAXE.get().canDisableShield(null, null, null, null));
        assertFalse(ForgeweaveItems.TOOL_SHOVEL.get().canDisableShield(null, null, null, null));
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

    // -------------------------------------------------------------------------- #283: Broken state

    /**
     * Upstream 1.12's {@code ToolHelper#calcDigSpeed} gives a Broken tool a flat 0.3 dig speed (a
     * real penalty), not bare-hand (1.0) speed.
     */
    @Test
    void brokenToolMinesAt0Point3Speed() {
        ItemStack broken = brokenPickaxe();
        BlockState stone = Blocks.STONE.defaultBlockState();
        assertEquals(0.3F, ForgeweaveItems.TOOL_PICKAXE.get().getDestroySpeed(broken, stone), 1.0e-5);
    }

    /**
     * Upstream's {@code ToolCore#showDurabilityBar} suppresses the durability bar once Broken; ours
     * kept showing a sliver because damage is clamped to {@code maxDamage - 1}, which vanilla's
     * default {@code isBarVisible} (damage > 0) still reads as damaged.
     */
    @Test
    void durabilityBarHiddenOnceBrokenButVisibleWhenMerelyDamaged() {
        ItemStack broken = brokenPickaxe();
        assertFalse(ForgeweaveItems.TOOL_PICKAXE.get().isBarVisible(broken),
                "a Broken tool should not show a durability bar");

        ItemStack merelyDamaged = assembledPickaxe();
        merelyDamaged.set(DataComponents.MAX_DAMAGE, 100);
        merelyDamaged.set(DataComponents.DAMAGE, 50);
        assertTrue(ForgeweaveItems.TOOL_PICKAXE.get().isBarVisible(merelyDamaged),
                "a merely damaged (not Broken) tool should still show its durability bar");
    }

    private static ItemStack brokenPickaxe() {
        ItemStack stack = assembledPickaxe();
        stack.set(DataComponents.MAX_DAMAGE, 100);
        stack.set(DataComponents.DAMAGE, 99);
        stack.set(ForgeweaveDataComponents.BROKEN.get(), true);
        return stack;
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

    // --------------------------------------------------------------------------- #295: damage cutoff

    /**
     * Pins {@link ToolItem#calcCutoffDamage} against upstream 1.12's {@code ToolHelper#calcCutoffDamage}
     * (tinkers-1.12 {@code library/utils/ToolHelper.java}, pinned commit in NOTICE.md):
     *
     * <pre>
     * float p = 1f;
     * float d = damage;
     * damage = 0f;
     * while(d &gt; cutoff) {
     *   damage += p * cutoff;
     *   if(p &gt; 0.001f) { p *= 0.9f; } else { damage += p * cutoff * ((d / cutoff) - 1f); return damage; }
     *   d -= cutoff;
     * }
     * damage += p * d;
     * return damage;
     * </pre>
     *
     * <p>Values below hand-computed from that formula: 20 raw damage on the default 15 cutoff is one
     * 15-sized full chunk plus a 5-sized remainder at the first 0.9x step, {@code 15 + 0.9*5 = 19.5}.
     * Rapier (13) and LongSword (18) get their own worked examples since issue #295 names them
     * explicitly, and a three-chunk case (cutoff 15, 50 raw) exercises the geometric falloff compounding
     * across more than one step: {@code 15 + 0.9*15 + 0.81*15 + 0.729*5 = 44.295}.
     */
    @Test
    void calcCutoffDamageMatchesUpstreamsGeometricFalloff() {
        // At or below the cutoff, the damage is unchanged.
        assertEquals(10.0F, ToolItem.calcCutoffDamage(10.0F, 15.0F), 1.0e-4F);
        assertEquals(15.0F, ToolItem.calcCutoffDamage(15.0F, 15.0F), 1.0e-4F);

        // Default 15 cutoff, 20 raw: one full 15 chunk, then a 5-sized remainder at 0.9x.
        assertEquals(19.5F, ToolItem.calcCutoffDamage(20.0F, 15.0F), 1.0e-4F);

        // Rapier's 13 cutoff, 20 raw: 13 + 0.9*7 = 19.3.
        assertEquals(19.3F, ToolItem.calcCutoffDamage(20.0F, 13.0F), 1.0e-4F);

        // LongSword's 18 cutoff, 20 raw: 18 + 0.9*2 = 19.8.
        assertEquals(19.8F, ToolItem.calcCutoffDamage(20.0F, 18.0F), 1.0e-4F);

        // Three full chunks past the default 15 cutoff: 15 + 0.9*15 + 0.81*15 + 0.729*5 = 44.295.
        assertEquals(44.295F, ToolItem.calcCutoffDamage(50.0F, 15.0F), 1.0e-3F);
    }

    /**
     * End to end: {@link ToolItem#attackDamage} runs its result through {@link ToolItem#calcCutoffDamage}
     * with the tool's own {@code damageCutoff} -- the M1 pickaxe defaults to 15 (no upstream override).
     */
    @Test
    void attackDamageAppliesTheToolsCutoff() {
        ItemStack overCutoff = assembledPickaxe();
        overCutoff.set(ForgeweaveDataComponents.TOOL_STATS.get(), new ToolStats.Stats(160, 4.0F, 20.0F));
        assertEquals(19.5F, ForgeweaveItems.TOOL_PICKAXE.get().attackDamage(overCutoff), 1.0e-4F,
                "20 raw attack on the default 15 cutoff must fall to 19.5, matching calcCutoffDamage");

        ItemStack underCutoff = assembledPickaxe();
        assertEquals(3.0F, ForgeweaveItems.TOOL_PICKAXE.get().attackDamage(underCutoff), 1.0e-4F,
                "below the cutoff, attack damage must be unaffected");
    }

    // ----------------------------------------------------------- #414: re-equip / block-break reset

    /**
     * Upstream 1.12's {@code ToolCore#shouldCauseReequipAnimation} (tinkers-1.12
     * {@code library/tools/ToolCore.java}:583-620, pinned commit in NOTICE.md) deliberately does
     * <em>not</em> re-equip on every NBT change: it re-equips only on a slot change, a glint
     * ({@code hasEffect}) change, a MAINHAND attribute-modifier change, or a change in the tool's
     * identity -- materials, modifiers and base stats, via {@code isEqualTinkersItem}:655. Everything
     * else in the tag is ignored, and {@code shouldCauseBlockBreakReset}:577 delegates to the same
     * answer. NeoForge's default is vanilla's "any component differs", which makes a tool carrying a
     * per-tick trait component bob and reset mining progress continuously --
     * {@code ForgeweaveTraits#SHOCKING} rewrites {@code SHOCKING_CHARGE} every 5 ticks while the
     * holder is moving.
     */
    @Test
    void perTickTraitComponentChurnDoesNotReequip() {
        ItemStack before = assembledPickaxe();
        ItemStack after = before.copy();
        after.set(ForgeweaveDataComponents.SHOCKING_CHARGE.get(), new ShockingCharge(37.5F, 1.0, 2.0, 3.0));

        assertFalse(ToolItem.shouldReequip(before, after, false),
                "a shocking-charge tick must not re-equip the tool");
        assertFalse(ForgeweaveItems.TOOL_PICKAXE.get().shouldCauseReequipAnimation(before, after, false));
        assertFalse(ForgeweaveItems.TOOL_PICKAXE.get().shouldCauseBlockBreakReset(before, after),
                "a shocking-charge tick must not reset mining progress");
    }

    /** The same for the other churn a held tool sees: durability ticking down, mending moss XP. */
    @Test
    void durabilityAndMossXpChurnDoesNotReequip() {
        ItemStack before = assembledPickaxe();
        ItemStack after = before.copy();
        after.set(DataComponents.DAMAGE, 12);
        after.set(ForgeweaveDataComponents.MENDING_MOSS_XP.get(), 44);

        assertFalse(ToolItem.shouldReequip(before, after, false));
    }

    /** Identity: the very same stack object is never a re-equip, whatever it carries. */
    @Test
    void theSameStackNeverReequips() {
        ItemStack stack = assembledPickaxe();
        assertFalse(ToolItem.shouldReequip(stack, stack, false));
    }

    @Test
    void slotChangeAlwaysReequips() {
        ItemStack stack = assembledPickaxe();
        assertTrue(ToolItem.shouldReequip(stack, stack.copy(), true));
    }

    @Test
    void aDifferentItemReequips() {
        assertTrue(ToolItem.shouldReequip(assembledPickaxe(),
                new ItemStack(ForgeweaveItems.TOOL_SHOVEL.get()), false));
    }

    /**
     * The glint seam is {@link ToolItem#isFoil} plus vanilla's {@code enchantment_glint_override},
     * which {@code ForgeweaveTraits#SHOCKING} flips on at full charge and off on discharge --
     * upstream compares {@code hasEffect()} for exactly this.
     */
    @Test
    void glintChangeReequips() {
        ItemStack before = assembledPickaxe();
        ItemStack after = before.copy();
        after.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);

        assertTrue(ToolItem.shouldReequip(before, after, false));
        assertTrue(ToolItem.shouldReequip(after, before, false));
    }

    /**
     * A modifier that grants an attribute is caught by the attribute comparison; one that grants none
     * ({@code diamond}) is caught only by the modifier-list comparison, which is upstream's
     * {@code isEqualTinkersItem} modifier check.
     */
    @Test
    void modifierChangeReequips() {
        ItemStack before = assembledPickaxe();
        assertTrue(ToolItem.shouldReequip(before, withModifier(before, "far_reach"), false),
                "a modifier that grants an attribute must re-equip");
        assertTrue(ToolItem.shouldReequip(before, withModifier(before, "diamond"), false),
                "a modifier that grants no attribute still changes the tool's identity");
    }

    /** Re-assembly with different base stats, and breaking, are both new tools. */
    @Test
    void statAndBreakageChangesReequip() {
        ItemStack before = assembledPickaxe();

        ItemStack restatted = before.copy();
        restatted.set(ForgeweaveDataComponents.TOOL_STATS.get(), new ToolStats.Stats(160, 4.0F, 9.0F));
        assertTrue(ToolItem.shouldReequip(before, restatted, false));

        assertTrue(ToolItem.shouldReequip(before, brokenPickaxe(), false),
                "breaking swaps the model and drops every attribute modifier");
    }
}
