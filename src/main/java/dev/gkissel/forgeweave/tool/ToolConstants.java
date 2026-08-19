package dev.gkissel.forgeweave.tool;

import java.util.ArrayList;
import java.util.List;

import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;

/**
 * M3's per-tool constants and part composition (issue #153): attack speed, damage potential,
 * durability/mining modifiers, and the ordered part list each of the 18 M3 tools assembles from.
 * {@code #155}-{@code #161} register the actual {@code ToolItem}s and Tool Station/Forge recipes
 * -- this class only lands the data those issues consume, following M1's split between
 * {@link ToolStats} (pure material math) and {@code ToolItem}'s per-type constructor constants.
 *
 * <h2>Formula, ported from upstream 1.12's {@code ToolNBT}/{@code ToolCore}/{@code ToolHelper}</h2>
 *
 * <p>Upstream stores a tool's stats in two steps: {@code ToolCore#buildTagData} writes the
 * <em>stored</em> NBT (head/extra/handle averaging, plus each tool class's own flat adjustments),
 * then {@code ToolHelper#getActualAttack}/{@code #getActualMiningSpeed} multiply that stored value
 * by the tool type's {@code damagePotential()}/{@code miningSpeedModifier()} at read time. {@link
 * #compute} reproduces the stored half (matching {@link ToolStats.Stats}, so {@code ToolItem}'s
 * existing {@code attackDamage(stack) = stats.attackDamage() * damagePotential + ...} applies the
 * second half exactly as it already does for M1); {@link Entry#miningSpeedModifier} is landed here
 * for {@code #155}-{@code #161} to apply the same way once they generalize {@code ToolItem}.
 *
 * <pre>
 * headAvg     = weighted average of every HEAD part's stat (durability/attack/speed), upstream's
 *               {@code ToolNBT#head} -- a part repeated in the list (Hammer) or given a fractional
 *               weight (Vein Hammer, ported from the 1.20 branch's {@code PartStatsModule}) counts
 *               for more
 * durability  = round((headAvg.durability + avg(EXTRA.extraDurability)) * avg(HANDLE.durabilityModifier))
 *               + avg(HANDLE.durability), floor 1, all folded through {@code Entry#durabilityMultiplier}
 *               (upstream's own post-hoc {@code data.durability *= X}) -- Battleaxe (rebalanced,
 *               maintainer 2026-08-12) skips the extra/handle terms entirely, per its explicit
 *               "head avg +10% from binding" formula
 * attackStored = headAvg.attack * Entry#preAttackMultiplier + Entry#flatAttackBonus (Battleaxe sums
 *               its two heads instead of averaging: {@code sumHeadsForAttack})
 * miningSpeed = headAvg.miningSpeed (Entry#miningSpeedModifier applies at the ToolItem layer)
 * </pre>
 *
 * <p>ponytail: one generic formula for 17 of the 18 tools (every field on {@link Entry} defaults to
 * the identity value -- {@code 1.0} multiplier, {@code 0} bonus -- for a tool that doesn't need it),
 * plus two boolean escape hatches for Battleaxe's rebalanced formula rather than a second compute
 * method; add a third path only if a future tool needs one too.
 */
public final class ToolConstants {

    /**
     * Which content family a tool belongs to (the content-family toggles ticket), and the single
     * source the pack-facing {@code content} config section derives every other membership question
     * from -- see {@link dev.gkissel.forgeweave.menu.ContentFamilies}.
     *
     * <p>Upstream 1.12 tags a tool with a <em>set</em> of {@code library/tinkering/Category} values
     * ({@code TinkersItem#categories} is a {@code Set}, {@code addCategory} appends to it), and four
     * of its tools carry both {@code HARVEST} and {@code WEAPON} -- Hatchet, Kama, Hammer and
     * Scythe. A toggle needs one answer per tool, not a set, so this collapses that to upstream's
     * own primary split, which its source tree already draws: everything under {@code tools/tools/}
     * declares {@code HARVEST} and lands in {@link #HARVEST}; everything under
     * {@code tools/melee/item/} declares {@code WEAPON} without {@code HARVEST} and lands in
     * {@link #MELEE}. No upstream tool is in neither set, so the split is total and unambiguous.
     *
     * <p>The five shapes with no 1.12 counterpart are classified by what they are: the vein hammer
     * mines ({@link #HARVEST}), the dagger, scimitar, katana and warmace hit ({@link #MELEE}).
     *
     * <p>{@link #RANGED} is upstream's {@code Category.LAUNCHER} ({@code BowCore}'s constructor adds
     * it), M3.5's bows. {@code armor} and {@code gadgets} are config keys with no roster yet (M4/M5);
     * their constants land with the tools they gate.
     */
    public enum Category {
        HARVEST,
        MELEE,
        RANGED
    }

    /**
     * Which of a material's stat blocks a part slot draws from -- see {@link PartItem.Kind}.
     *
     * <p>{@link #LIMB} is upstream's {@code PartMaterialType.bow(...)}: a slot that reads
     * <em>both</em> the HEAD block (durability/attack/speed, folded through {@code ToolNBT#head}
     * exactly like a {@link #HEAD} slot -- {@code ShortBow#buildTagData} calls {@code data.head(limb1,
     * limb2)}) and the BOW block ({@code ProjectileLauncherNBT#limb}, {@link LauncherStats}).
     * {@link #BOWSTRING} is {@code PartMaterialType.bowstring(...)}: the BOWSTRING block only, one
     * durability multiplier ({@code ProjectileLauncherNBT#bowstring}).
     *
     * <p>{@link #CROSSBOW_BODY} is upstream's {@code PartMaterialType.crossbow(...)} (M3.5 issue
     * #395), the one slot that reads two durability blocks at once: {@code CrossBow#buildTagData}
     * takes the tough rod's HANDLE block for {@code data.handle(body)} <em>and</em> its EXTRA block
     * for {@code data.extra(binding, bodyExtra)}, so the slot counts as both an {@link #EXTRA} and a
     * {@link #HANDLE} everywhere those are summed -- and grants both scopes' traits.
     */
    public enum Role {
        HEAD,
        EXTRA,
        HANDLE,
        CROSSBOW_BODY,
        LIMB,
        BOWSTRING,
        /**
         * Issue #448 (parity audit T17): a shuriken blade, upstream's {@code PartMaterialType(knifeBlade,
         * HEAD, EXTRA, PROJECTILE)} -- the slot that reads <em>both</em> the HEAD block ({@code
         * Shuriken#buildTagData} hands all four blades to {@code data.head(...)}) and the EXTRA block
         * ({@code data.extra(...)} over the same four), so it lands in both averages and grants both
         * scopes' traits, the same dual duty {@link #CROSSBOW_BODY} does for HANDLE+EXTRA. Upstream's
         * third stat type there, PROJECTILE, is deferred with the rest of the projectile stat layer
         * (the arrow follow-up ticket) -- no Forgeweave material carries a PROJECTILE block yet.
         */
        SHURIKEN_BLADE
    }

    /**
     * One part slot in a tool's assembly order. {@code weight} only matters for {@link Role#HEAD}
     * slots with more than one HEAD part (Hammer, Vein Hammer, Battleaxe): the weighted average
     * upstream's {@code ToolNBT#head} computes by literally repeating a material in its varargs
     * array (Hammer) or, ported from the 1.20 branch, an explicit fractional weight (Vein Hammer).
     */
    public record PartSlot(Role role, String partId, float weight) {
        public PartSlot(Role role, String partId) {
            this(role, partId, 1.0f);
        }
    }

    /**
     * One slot a tool can be repaired through, with the factor its material's head durability is
     * multiplied by -- upstream 1.12's {@code TinkersItem#getRepairParts()} and
     * {@code #getRepairModifierForPart(int)} folded into one value per slot, since the two are only
     * ever read together ({@code TinkersItem#calculateRepairAmount}).
     *
     * @param slot index into {@link Entry#parts()}
     * @param modifier upstream's {@code getRepairModifierForPart(slot)} -- {@code 1.0} for the eight
     *     upstream tools that never override it
     */
    public record RepairPart(int slot, float modifier) {}

    /**
     * @param id the tool's identifier, matching its future item registry name
     * @param category the content family this tool belongs to -- see {@link Category}
     * @param parts ordered part composition; {@link #compute} expects one {@link Material} per slot
     *     in this order
     * @param attackSpeed upstream's {@code ToolCore#attackSpeed()}
     * @param damagePotential upstream's {@code ToolCore#damagePotential()} -- the runtime multiplier
     *     {@code ToolItem#attackDamage} applies on top of {@link #compute}'s stored attack
     * @param preAttackMultiplier a tool-specific multiplier on the head average before the flat bonus
     *     is added, upstream's own {@code data.attack *= X} (Cleaver's {@code 1.3f}); {@code 1.0} for
     *     every other tool
     * @param flatAttackBonus upstream's {@code data.attack += X} (or the new shapes' stated base
     *     damage), added after {@code preAttackMultiplier}
     * @param durabilityMultiplier upstream's post-hoc {@code data.durability *= X}; {@code 1.0} when
     *     a tool has none
     * @param miningSpeedModifier upstream's {@code ToolCore#miningSpeedModifier()}; {@code 1.0}
     *     (upstream's own default) for every tool that doesn't override it
     * @param sumHeadsForAttack Battleaxe only (rebalanced, maintainer 2026-08-12): sums its two heads'
     *     attack instead of averaging them, so {@code preAttackMultiplier} lands as a true multiplier
     *     on the sum rather than the average
     * @param durabilitySkipsExtraAndHandle Battleaxe only: its durability is the head average plus the
     *     binding's flat percentage (via {@code durabilityMultiplier}), not the usual extra/handle
     *     chain
     * @param damageCutoff upstream {@code ToolCore#damageCutoff()} (issue #295): the per-hit damage
     *     value above which {@code ToolItem#attackDamage} starts applying diminishing returns
     *     ({@link #DEFAULT_DAMAGE_CUTOFF} for every tool that doesn't override it -- Rapier 13,
     *     LongSword 18, Cleaver 25, BattleAxe 30 are upstream's four overrides, the only tool classes
     *     in {@code tools/melee}/{@code tools/harvest} that touch {@code damageCutoff()} at all)
     * @param bonusDamageMultiplier upstream's post-hoc {@code data.bonusDamage *= X} on a launcher's
     *     {@link LauncherStats} (M3.5 issue #395: {@code CrossBow#buildTagData}'s {@code 1.5f}, the
     *     only one in the tree); {@code 1.0} for every other tool, and inert for a tool with no
     *     {@link Role#LIMB} slot at all
     * @param repairModifiers upstream's {@code getRepairParts()}/{@code getRepairModifierForPart(int)}
     *     pair, index-aligned with {@code parts}: a slot's entry is the factor its material's head
     *     durability is multiplied by when it is spent on a repair, or {@code 0} for a slot that
     *     repairs nothing (every HANDLE, every bowstring, and the plain guards). An empty list means
     *     upstream's default {@code getRepairParts() == {1}} at factor {@code 1} -- resolved here as
     *     the tool's first HEAD/LIMB slot, which is what slot {@code 1} is for every upstream tool
     *     that keeps the default, and the only sensible reading for the tools with no 1.12
     *     counterpart. Read through {@link Entry#repairSlots()}, never directly.
     */
    public record Entry(
            String id,
            Category category,
            List<PartSlot> parts,
            float attackSpeed,
            float damagePotential,
            float preAttackMultiplier,
            float flatAttackBonus,
            float durabilityMultiplier,
            float miningSpeedModifier,
            boolean sumHeadsForAttack,
            boolean durabilitySkipsExtraAndHandle,
            float damageCutoff,
            float bonusDamageMultiplier,
            List<Float> repairModifiers) {

        public Entry {
            if (!repairModifiers.isEmpty() && repairModifiers.size() != parts.size()) {
                throw new IllegalArgumentException(id + ": repair modifiers must be index-aligned with parts");
            }
        }

        /** Convenience constructor for the {@link #DEFAULT_DAMAGE_CUTOFF} every tool but the four upstream overrides uses. */
        public Entry(String id, Category category, List<PartSlot> parts, float attackSpeed, float damagePotential,
                float preAttackMultiplier, float flatAttackBonus, float durabilityMultiplier,
                float miningSpeedModifier, boolean sumHeadsForAttack, boolean durabilitySkipsExtraAndHandle) {
            this(id, category, parts, attackSpeed, damagePotential, preAttackMultiplier, flatAttackBonus,
                    durabilityMultiplier, miningSpeedModifier, sumHeadsForAttack, durabilitySkipsExtraAndHandle,
                    DEFAULT_DAMAGE_CUTOFF);
        }

        /** Convenience constructor for the four tools with an explicit {@code damageCutoff} and no launcher. */
        public Entry(String id, Category category, List<PartSlot> parts, float attackSpeed, float damagePotential,
                float preAttackMultiplier, float flatAttackBonus, float durabilityMultiplier,
                float miningSpeedModifier, boolean sumHeadsForAttack, boolean durabilitySkipsExtraAndHandle,
                float damageCutoff) {
            this(id, category, parts, attackSpeed, damagePotential, preAttackMultiplier, flatAttackBonus,
                    durabilityMultiplier, miningSpeedModifier, sumHeadsForAttack, durabilitySkipsExtraAndHandle,
                    damageCutoff, 1.0f, List.of());
        }

        /** Convenience constructor for the two launchers that need {@code bonusDamageMultiplier} and no repair table. */
        public Entry(String id, Category category, List<PartSlot> parts, float attackSpeed, float damagePotential,
                float preAttackMultiplier, float flatAttackBonus, float durabilityMultiplier,
                float miningSpeedModifier, boolean sumHeadsForAttack, boolean durabilitySkipsExtraAndHandle,
                float damageCutoff, float bonusDamageMultiplier) {
            this(id, category, parts, attackSpeed, damagePotential, preAttackMultiplier, flatAttackBonus,
                    durabilityMultiplier, miningSpeedModifier, sumHeadsForAttack, durabilitySkipsExtraAndHandle,
                    damageCutoff, bonusDamageMultiplier, List.of());
        }

        /**
         * This entry with an explicit per-slot repair table -- one factor per {@link #parts()} slot,
         * {@code 0} for a slot that repairs nothing. Written as a wither so the eleven tools that
         * need one read as "the tool, plus its repair table" instead of pushing a fourteenth argument
         * through every other tool's constructor call.
         */
        public Entry withRepairModifiers(Float... modifiers) {
            return new Entry(id, category, parts, attackSpeed, damagePotential, preAttackMultiplier,
                    flatAttackBonus, durabilityMultiplier, miningSpeedModifier, sumHeadsForAttack,
                    durabilitySkipsExtraAndHandle, damageCutoff, bonusDamageMultiplier, List.of(modifiers));
        }

        /**
         * The slots a repair may be paid in, in slot order, with each one's factor -- upstream
         * {@code TinkersItem#getRepairParts()} zipped with {@code #getRepairModifierForPart(int)}.
         */
        public List<RepairPart> repairSlots() {
            if (repairModifiers.isEmpty()) {
                for (int i = 0; i < parts.size(); i++) {
                    Role role = parts.get(i).role();
                    if (role == Role.HEAD || role == Role.LIMB) {
                        return List.of(new RepairPart(i, 1.0f));
                    }
                }
                return List.of();
            }
            List<RepairPart> slots = new ArrayList<>(repairModifiers.size());
            for (int i = 0; i < repairModifiers.size(); i++) {
                if (repairModifiers.get(i) > 0f) {
                    slots.add(new RepairPart(i, repairModifiers.get(i)));
                }
            }
            return List.copyOf(slots);
        }
    }

    /** Upstream {@code ToolCore#damageCutoff()}'s own default -- see {@link Entry#damageCutoff}. */
    public static final float DEFAULT_DAMAGE_CUTOFF = 15.0f;

    private static final String TOOL_HANDLE = "tool_handle";
    private static final String TOOL_BINDING = "tool_binding";
    // Reconciled against the part items #151 actually registered (issue #155): this read
    // "tough_tool_handle", which no item has -- the registered id is forgeweave:tough_tool_rod
    // (ForgeweaveItems#PART_TOUGH_TOOL_ROD, upstream's own `toughToolRod`). Registered id wins.
    private static final String TOUGH_TOOL_HANDLE = "tough_tool_rod";
    private static final String TOUGH_BINDING = "tough_binding";

    /**
     * Upstream {@code tools/melee/item/BroadSword.java}: sweep-attack sword, wide guard. Mining speed x0.5 from its {@code SwordCore} base ({@code miningSpeedModifier()}, issue #437).
     */
    public static final Entry BROADSWORD = new Entry("broadsword", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "sword_blade"),
                    new PartSlot(Role.EXTRA, "wide_guard")),
            1.6f, 1.0f, 1.0f, 1.0f, 1.1f, 0.5f, false, false)
            // Upstream BroadSword#getRepairModifierForPart returns DURABILITY_MODIFIER (1.1) for every part.
            .withRepairModifiers(0f, 1.1f, 0f);

    /**
     * Upstream {@code tools/melee/item/LongSword.java}: charged-leap sword, hand guard.
     * {@code damageCutoff() = 18f} (issue #295), one of upstream's four overrides. Mining speed x0.5 from its {@code SwordCore} base ({@code miningSpeedModifier()}, issue #437).
     */
    public static final Entry LONGSWORD = new Entry("longsword", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "sword_blade"),
                    new PartSlot(Role.EXTRA, "hand_guard")),
            1.4f, 1.1f, 1.0f, 0.5f, 1.05f, 0.5f, false, false, 18.0f)
            // Upstream LongSword#getRepairModifierForPart returns DURABILITY_MODIFIER (1.05).
            .withRepairModifiers(0f, 1.05f, 0f);

    /**
     * Upstream {@code tools/melee/item/Rapier.java}: fast hybrid-damage sword, cross guard.
     * {@code damageCutoff() = 13f} (issue #295), one of upstream's four overrides. Mining speed x0.5 from its {@code SwordCore} base ({@code miningSpeedModifier()}, issue #437).
     */
    public static final Entry RAPIER = new Entry("rapier", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "sword_blade"),
                    new PartSlot(Role.EXTRA, "cross_guard")),
            3.0f, 0.55f, 1.0f, 0.0f, 0.8f, 0.5f, false, false, 13.0f)
            // Upstream Rapier#getRepairModifierForPart returns DURABILITY_MODIFIER (0.8) -- a rapier
            // is the one tool that repairs for *less* than its head material's durability.
            .withRepairModifiers(0f, 0.8f, 0f);

    /** Upstream {@code tools/melee/item/BattleSign.java}: blocking/reflecting sign, no extra part. */
    public static final Entry BATTLESIGN = new Entry("battlesign", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "sign_plate")),
            1.2f, 0.86f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    /** Upstream {@code tools/melee/item/FryPan.java}: charged-knockback pan, no extra part. */
    public static final Entry FRYING_PAN = new Entry("frying_pan", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "pan")),
            1.4f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    /** Upstream {@code tools/tools/Mattock.java}: axe+shovel dual head, unweighted average. */
    public static final Entry MATTOCK = new Entry("mattock", Category.HARVEST,
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "axe_head"),
                    new PartSlot(Role.HEAD, "shovel_head")),
            0.9f, 0.90f, 1.0f, 3.0f, 1.0f, 0.95f, false, false)
            // Upstream Mattock#getRepairParts is {1, 2}; it overrides no repair modifier, so both at 1.
            .withRepairModifiers(0f, 1f, 1f);

    /** Upstream {@code tools/tools/Kama.java}: single head, M1's shared binding part. */
    public static final Entry KAMA = new Entry("kama", Category.HARVEST,
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "kama_head"),
                    new PartSlot(Role.EXTRA, TOOL_BINDING)),
            1.3f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    /**
     * Part composition from upstream {@code tools/melee/item/BattleAxe.java} (never shipped, cited
     * for the part list only); damage/durability/speed/mining REBALANCED from scratch by maintainer
     * decision 2026-08-12 -- upstream's own unshipped numbers are explicitly not adopted. Damage
     * sums (not averages) its two heads; durability is the head average plus a flat 10% from the
     * binding, skipping the usual extra/handle chain -- see {@link Entry#sumHeadsForAttack}/
     * {@link Entry#durabilitySkipsExtraAndHandle}. {@code damageCutoff} stays at
     * {@link #DEFAULT_DAMAGE_CUTOFF} (issue #295) rather than upstream's unshipped {@code 30f}: it is
     * one more damage-tuning number the same "not adopted" decision above already covers.
     */
    public static final Entry BATTLEAXE = new Entry("battleaxe", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "broad_axe_head"),
                    new PartSlot(Role.HEAD, "broad_axe_head"), new PartSlot(Role.EXTRA, TOUGH_BINDING)),
            0.95f, 1.0f, 1.2f, 0.0f, 1.10f, 0.6f, true, true)
            // Upstream BattleAxe#getRepairParts is {1, 2} -- both heads -- and it overrides no repair
            // modifier, so both repair at 1 despite its durability multiplier. The rebalance decision
            // above covers its stats, not this, which is upstream's own shipped-source repair table.
            .withRepairModifiers(0f, 1f, 1f, 0f);

    /**
     * Upstream {@code tools/melee/item/Cleaver.java}: head+shield average, pre-multiply then bonus.
     * {@code damageCutoff() = 25f} (issue #295), one of upstream's four overrides. Mining speed x0.5 from its {@code SwordCore} base ({@code miningSpeedModifier()}, issue #437).
     */
    public static final Entry CLEAVER = new Entry("cleaver", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "large_sword_blade"),
                    new PartSlot(Role.HEAD, "large_plate"), new PartSlot(Role.EXTRA, TOUGH_TOOL_HANDLE)),
            0.7f, 1.2f, 1.3f, 3.0f, 2.0f, 0.5f, false, false, 25.0f)
            // Upstream Cleaver: getRepairParts {1, 2}, getRepairModifierForPart index 1 ->
            // DURABILITY_MODIFIER (2), everything else -> DURABILITY_MODIFIER * 0.75 (1.5).
            .withRepairModifiers(0f, 2f, 1.5f, 0f);

    /** Upstream {@code tools/tools/Hammer.java}: hammer head weighted double over two large plates. */
    public static final Entry HAMMER = new Entry("hammer", Category.HARVEST,
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "hammer_head", 2.0f),
                    new PartSlot(Role.HEAD, "large_plate"), new PartSlot(Role.HEAD, "large_plate")),
            0.8f, 1.2f, 1.0f, 0.0f, 2.5f, 0.4f, false, false)
            // Upstream Hammer: getRepairParts {1, 2, 3}, index 1 -> DURABILITY_MODIFIER (2.5),
            // the two plates -> DURABILITY_MODIFIER * 0.6 (1.5).
            .withRepairModifiers(0f, 2.5f, 1.5f, 1.5f);

    /** Upstream {@code tools/tools/Excavator.java}: excavator head + large plate, unweighted average. */
    public static final Entry EXCAVATOR = new Entry("excavator", Category.HARVEST,
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "excavator_head"),
                    new PartSlot(Role.HEAD, "large_plate"), new PartSlot(Role.EXTRA, TOUGH_BINDING)),
            0.7f, 1.25f, 1.0f, 0.0f, 1.75f, 0.28f, false, false)
            // Upstream Excavator: getRepairParts {1, 2}, index 1 -> DURABILITY_MODIFIER (1.75),
            // the plate -> DURABILITY_MODIFIER * 0.75 (1.3125).
            .withRepairModifiers(0f, 1.75f, 1.3125f, 0f);

    /** Upstream {@code tools/tools/LumberAxe.java}: broad axe head + large plate, unweighted average. */
    public static final Entry LUMBERAXE = new Entry("lumberaxe", Category.HARVEST,
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "broad_axe_head"),
                    new PartSlot(Role.HEAD, "large_plate"), new PartSlot(Role.EXTRA, TOUGH_BINDING)),
            0.8f, 1.2f, 1.0f, 2.0f, 2.0f, 0.35f, false, false)
            // Upstream LumberAxe: getRepairParts {1, 2}, index 1 -> DURABILITY_MODIFIER (2),
            // the plate -> DURABILITY_MODIFIER * 0.625 (1.25).
            .withRepairModifiers(0f, 2f, 1.25f, 0f);

    /** Upstream {@code tools/tools/Scythe.java}: single head, two tough handles averaged. */
    public static final Entry SCYTHE = new Entry("scythe", Category.HARVEST,
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "scythe_head"),
                    new PartSlot(Role.EXTRA, TOUGH_BINDING), new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE)),
            0.9f, 0.75f, 1.0f, 0.0f, 2.2f, 1.0f, false, false)
            // Upstream Scythe#getRepairParts is {1, 2} -- head *and tough binding*, since its part
            // order is (handle, head, binding, handle) -- and it overrides no repair modifier, so
            // both repair at 1 despite DURABILITY_MODIFIER = 2.2. Ported verbatim: the scythe is the
            // only tool upstream lets you repair with a non-head part's material.
            .withRepairModifiers(0f, 1f, 1f, 0f);

    /**
     * No 1.12 counterpart. Part weights (hammer head 0.75 / large plate 0.25) and stats ported from
     * the 1.20 branch's {@code ToolDefinitionDataProvider} ({@code VEIN_HAMMER}) -- feature-scope
     * deviation authorized by maintainer 2026-08-12 (docs/SCOPE.md M3 sources table), numbers
     * confirmed unchanged in the issue #153 decision comment.
     */
    public static final Entry VEIN_HAMMER = new Entry("vein_hammer", Category.HARVEST,
            // Reconciled against #151's registered parts (issue #155): the vein hammer has its own
            // vein_hammer_head part item, not the plain hammer_head this used to name. Weights are
            // unchanged. Registered id wins.
            List.of(new PartSlot(Role.HEAD, "vein_hammer_head", 0.75f), new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE),
                    new PartSlot(Role.EXTRA, TOUGH_BINDING), new PartSlot(Role.HEAD, "large_plate", 0.25f)),
            0.85f, 1.25f, 1.0f, 3.0f, 5.0f, 0.3f, false, false);

    /**
     * No 1.12 counterpart. Shape from the 1.20 branch's {@code DAGGER} ({@code smallBlade} + handle,
     * no extra part); base damage and attack speed are the issue #153 decision comment's numbers,
     * which deliberately do not carry over 1.20's own attack/mining/durability multipliers. Mining
     * speed x0.5 is the 1.12 sword family's own {@code SwordCore#miningSpeedModifier()} rather than
     * 1.20's dagger-specific 0.75, since a knife is a sword shape here and carries none of 1.20's
     * tilling/shears utility traits (issue #437).
     */
    public static final Entry DAGGER = new Entry("dagger", Category.MELEE,
            List.of(new PartSlot(Role.HEAD, "knife_blade"), new PartSlot(Role.HANDLE, TOOL_HANDLE)),
            2.0f, 1.0f, 1.0f, 3.0f, 1.0f, 0.5f, false, false);

    /**
     * New shape (Forgeweave design, no upstream source): station-tier curved blade. Distinct head
     * part from the clone-derived swords above so assembly can tell them apart; numbers are the
     * issue #153 decision comment's (base damage 2.5 / speed 1.8), DoT innate decided on #159.
     * Mining speed x0.5: a sword shape, so the 1.12 sword family's modifier, which #153 never
     * decided either way (issue #437).
     */
    public static final Entry SCIMITAR = new Entry("scimitar", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "curved_blade"),
                    new PartSlot(Role.EXTRA, "cross_guard")),
            1.8f, 1.0f, 1.0f, 2.5f, 1.0f, 0.5f, false, false);

    /**
     * New shape (Forgeweave design, no upstream source): station-tier ramping blade. Numbers are the
     * issue #153 decision comment's (base damage 2.75 / speed 1.6), ramp magnitudes decided on #160.
     * Mining speed x0.5, as the scimitar above (issue #437).
     */
    public static final Entry KATANA = new Entry("katana", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "katana_blade"),
                    new PartSlot(Role.EXTRA, "hand_guard")),
            1.6f, 1.0f, 1.0f, 2.75f, 1.0f, 0.5f, false, false);

    /**
     * New shape (Forgeweave design, no upstream source): Tool Forge-tier mace-alike, smash rides
     * vanilla mace scaling (#161). Numbers are the issue #153 decision comment's (base damage 4.0 /
     * speed 0.8).
     */
    public static final Entry WARMACE = new Entry("warmace", Category.MELEE,
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "war_mace_head"),
                    new PartSlot(Role.EXTRA, TOUGH_BINDING)),
            0.8f, 1.0f, 1.0f, 4.0f, 1.0f, 1.0f, false, false);

    /**
     * Upstream {@code tools/ranged/item/ShortBow.java} (M3.5 issue #394): two limbs and a string,
     * {@code attackSpeed() = 1.5}, {@code damagePotential() = 0.7}. Its {@code buildTagData} is
     * {@code head(limb1, limb2)}, {@code limb(limb1, limb2)}, {@code bowstring(string)} with no
     * post-hoc multiplier -- so the melee half of its stats is the plain two-head average and the
     * bowstring modifier is the only extra durability step ({@link #compute}); {@code limb(...)} is
     * {@link LauncherStats}. Draw time, base projectile speed and inaccuracy are per-item constants
     * on {@code BowItem}, not stat math.
     */
    public static final Entry SHORTBOW = new Entry("shortbow", Category.RANGED,
            List.of(new PartSlot(Role.LIMB, "bow_limb"), new PartSlot(Role.LIMB, "bow_limb"),
                    new PartSlot(Role.BOWSTRING, "bow_string")),
            1.5f, 0.7f, 1.0f, 0.0f, 1.0f, 1.0f, false, false)
            // Upstream ShortBow#getRepairParts is {0, 1} -- both limbs, no modifier override.
            .withRepairModifiers(1f, 1f, 0f);

    /**
     * Upstream {@code tools/ranged/item/LongBow.java} (M3.5 issue #395): two limbs, a large plate
     * grip and a string, Tool Forge tier ({@code TinkerRegistry.registerToolForgeCrafting(longBow)}).
     * {@code attackSpeed() = 1.3}; {@code damagePotential()} is not overridden, so it inherits
     * {@code ShortBow}'s {@code 0.7}. Its {@code buildTagData} is the shortbow's chain plus the
     * grip -- {@code head(limb1, limb2)}, {@code limb(limb1, limb2)}, {@code extra(grip)},
     * {@code bowstring(string)} -- and then, alone among the bows, {@code data.durability *=
     * DURABILITY_MODIFIER} ({@code 1.4f}), which {@link #compute} applies after the bowstring step
     * exactly as upstream does. Draw time {@code 30}, base projectile speed {@code 5.5f},
     * inaccuracy {@code 1.2f} are per-item constants on {@code BowItem}.
     */
    public static final Entry LONGBOW = new Entry("longbow", Category.RANGED,
            List.of(new PartSlot(Role.LIMB, "bow_limb"), new PartSlot(Role.LIMB, "bow_limb"),
                    new PartSlot(Role.EXTRA, "large_plate"), new PartSlot(Role.BOWSTRING, "bow_string")),
            1.3f, 0.7f, 1.0f, 0.0f, 1.4f, 1.0f, false, false)
            // Upstream LongBow does NOT override getRepairParts, so it keeps TinkersItem's default
            // {1}: the *second* limb, not both like its ShortBow parent. Ported verbatim rather than
            // "fixed" to both limbs -- it only shows on a bow whose two limbs differ, and inventing a
            // second override here would be a deviation nobody asked for.
            .withRepairModifiers(0f, 1f, 0f, 0f);

    /**
     * Upstream {@code tools/ranged/item/CrossBow.java} (M3.5 issue #395): a tough tool rod body, one
     * limb, a tough binding and a string, Tool Forge tier. {@code attackSpeed() = 2},
     * {@code damagePotential() = 0.8}. Its {@code buildTagData} is {@code head(head)} and
     * {@code limb(limb)} off the single limb, {@code extra(binding, bodyExtra)} -- the binding's
     * EXTRA block <em>and</em> the rod's, averaged -- {@code handle(body)} off the rod's HANDLE
     * block, {@code bowstring(string)}, and then {@code data.bonusDamage *= 1.5f}. The rod's double
     * duty is {@link Role#CROSSBOW_BODY}; the bonus multiplier is
     * {@link Entry#bonusDamageMultiplier}. Draw time {@code 45}, base projectile speed {@code 7f},
     * inaccuracy {@code 0f} ({@code BowCore}'s default -- the crossbow does not override it) are
     * per-item constants on {@code BowItem}.
     */
    public static final Entry CROSSBOW = new Entry("crossbow", Category.RANGED,
            List.of(new PartSlot(Role.CROSSBOW_BODY, TOUGH_TOOL_HANDLE), new PartSlot(Role.LIMB, "bow_limb"),
                    new PartSlot(Role.EXTRA, TOUGH_BINDING), new PartSlot(Role.BOWSTRING, "bow_string")),
            2.0f, 0.8f, 1.0f, 0.0f, 1.0f, 1.0f, false, false, DEFAULT_DAMAGE_CUTOFF, 1.5f);

    /**
     * Upstream {@code tools/ranged/item/Shuriken.java} (issue #448, parity audit T17): four knife
     * blades, Tool Forge tier ({@code TinkerRegistry.registerToolForgeCrafting(shuriken)}). Its
     * {@code buildTagData} is {@code head(...)} and {@code extra(...)} over all four blades'
     * materials -- {@link Role#SHURIKEN_BLADE}'s dual read -- then {@code data.attack += 1f}
     * ({@code flatAttackBonus}). {@code damagePotential() = 0.7f}; {@code attackSpeed() = 100} is
     * {@code ProjectileCore}'s "projectiles behave like regular items" stand-in, inert here because
     * {@code ShurikenItem} carries no melee attributes at all. Throw speed, inaccuracy and the
     * 4-tick cooldown are per-item constants on {@code ShurikenItem}, as a bow's draw constants are
     * on {@code BowItem}.
     */
    public static final Entry SHURIKEN = new Entry("shuriken", Category.RANGED,
            List.of(new PartSlot(Role.SHURIKEN_BLADE, "knife_blade"), new PartSlot(Role.SHURIKEN_BLADE, "knife_blade"),
                    new PartSlot(Role.SHURIKEN_BLADE, "knife_blade"), new PartSlot(Role.SHURIKEN_BLADE, "knife_blade")),
            100.0f, 0.7f, 1.0f, 1.0f, 1.0f, 1.0f, false, false)
            // Upstream Shuriken#getRepairParts is {0, 1, 2, 3} -- every blade -- with no repair
            // modifier override, so all four repair at 1.
            .withRepairModifiers(1f, 1f, 1f, 1f);

    /** All 18 M3 tools, in docs/SCOPE.md content-manifest order (M3.5's bows are not M3 roster). */
    public static final List<Entry> ALL = List.of(
            BROADSWORD, LONGSWORD, RAPIER, BATTLESIGN, FRYING_PAN, MATTOCK, KAMA, DAGGER,
            BATTLEAXE, SCIMITAR, KATANA, WARMACE,
            HAMMER, EXCAVATOR, LUMBERAXE, SCYTHE, CLEAVER, VEIN_HAMMER);

    /**
     * Derives an assembled tool's stored stats from its constants and one {@link Material} per
     * {@link Entry#parts()} slot, in order -- see the class javadoc for the formula.
     */
    public static ToolStats.Stats compute(Entry entry, List<Material> partMaterials) {
        List<PartSlot> parts = entry.parts();
        if (parts.size() != partMaterials.size()) {
            throw new IllegalArgumentException(
                    "%s needs %d part materials, got %d".formatted(entry.id(), parts.size(), partMaterials.size()));
        }

        double headDurabilityWeighted = 0;
        double headWeightTotal = 0;
        float headAttackWeighted = 0;
        float headSpeedWeighted = 0;

        int extraDurabilitySum = 0;
        int extraCount = 0;

        int handleDurabilitySum = 0;
        float handleModifierSum = 0f;
        int handleCount = 0;

        float bowstringModifierSum = 0f;
        int bowstringCount = 0;

        for (int i = 0; i < parts.size(); i++) {
            PartSlot slot = parts.get(i);
            Material material = partMaterials.get(i);
            switch (slot.role()) {
                // A limb is a head as far as ToolNBT#head is concerned (ShortBow#buildTagData feeds
                // the limbs' HEAD block to data.head(...)); its BOW block is LauncherStats' business.
                case HEAD, LIMB -> {
                    // Issue #392: the blocks are optional, and a part whose material lacks its own
                    // never reaches assembly (PartItem#hasUnusableMaterial) -- so this is an
                    // invariant, not a datapack error path.
                    Material.Head head = material.head().orElseThrow(() -> ToolStats.noStats("head"));
                    float weight = slot.weight();
                    headDurabilityWeighted += head.durability() * weight;
                    headAttackWeighted += head.attackDamage() * weight;
                    headSpeedWeighted += head.miningSpeed() * weight;
                    headWeightTotal += weight;
                }
                case EXTRA -> {
                    extraDurabilitySum += material.extraDurability().orElseThrow(() -> ToolStats.noStats("extra"));
                    extraCount++;
                }
                case HANDLE -> {
                    Material.Handle handle = material.handle().orElseThrow(() -> ToolStats.noStats("handle"));
                    handleDurabilitySum += handle.durability();
                    handleModifierSum += handle.durabilityModifier();
                    handleCount++;
                }
                // The crossbow's body is both (issue #395): CrossBow#buildTagData hands the rod's
                // EXTRA block to data.extra(binding, bodyExtra) and its HANDLE block to
                // data.handle(body), so the slot lands in both averages.
                case CROSSBOW_BODY -> {
                    extraDurabilitySum += material.extraDurability().orElseThrow(() -> ToolStats.noStats("extra"));
                    extraCount++;
                    Material.Handle body = material.handle().orElseThrow(() -> ToolStats.noStats("handle"));
                    handleDurabilitySum += body.durability();
                    handleModifierSum += body.durabilityModifier();
                    handleCount++;
                }
                case BOWSTRING -> {
                    bowstringModifierSum += material.bowstring()
                            .orElseThrow(() -> ToolStats.noStats("bowstring")).modifier();
                    bowstringCount++;
                }
                // Issue #448: a shuriken blade is both a head and an extra part -- upstream
                // Shuriken#buildTagData feeds every blade's HEAD block to data.head(...) and its
                // EXTRA block to data.extra(...), so the slot lands in both averages.
                case SHURIKEN_BLADE -> {
                    Material.Head blade = material.head().orElseThrow(() -> ToolStats.noStats("head"));
                    float weight = slot.weight();
                    headDurabilityWeighted += blade.durability() * weight;
                    headAttackWeighted += blade.attackDamage() * weight;
                    headSpeedWeighted += blade.miningSpeed() * weight;
                    headWeightTotal += weight;
                    extraDurabilitySum += material.extraDurability().orElseThrow(() -> ToolStats.noStats("extra"));
                    extraCount++;
                }
            }
        }
        if (headWeightTotal <= 0) {
            throw new IllegalStateException(entry.id() + " has no head parts");
        }

        int headDurabilityAvg = Math.max(1, (int) (headDurabilityWeighted / headWeightTotal));
        float headAttackAvg = headAttackWeighted / (float) headWeightTotal;
        float headSpeedAvg = headSpeedWeighted / (float) headWeightTotal;

        int durability;
        if (entry.durabilitySkipsExtraAndHandle()) {
            durability = headDurabilityAvg;
        } else {
            durability = headDurabilityAvg;
            if (extraCount > 0) {
                durability += Math.round((float) extraDurabilitySum / extraCount);
            }
            if (handleCount > 0) {
                float handleModifierAvg = handleModifierSum / handleCount;
                durability = Math.round(durability * handleModifierAvg);
                durability += Math.round((float) handleDurabilitySum / handleCount);
            }
            durability = Math.max(1, durability);
        }
        if (bowstringCount > 0) {
            // ProjectileLauncherNBT#bowstring: the averaged string modifier scales whatever the chain
            // above produced, floor 1, and it runs before the per-tool multiplier
            // (LongBow#buildTagData applies its DURABILITY_MODIFIER after bowstring()).
            durability = Math.max(1, Math.round(durability * (bowstringModifierSum / bowstringCount)));
        }
        durability = Math.max(1, (int) (durability * entry.durabilityMultiplier()));

        float attack = entry.sumHeadsForAttack() ? headAttackWeighted : headAttackAvg;
        attack = attack * entry.preAttackMultiplier() + entry.flatAttackBonus();

        return new ToolStats.Stats(durability, headSpeedAvg, attack);
    }

    private ToolConstants() {}
}
