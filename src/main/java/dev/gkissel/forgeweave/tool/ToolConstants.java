package dev.gkissel.forgeweave.tool;

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

    /** Which of a material's stat blocks a part slot draws from -- see {@link PartItem.Kind}. */
    public enum Role {
        HEAD,
        EXTRA,
        HANDLE
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
     * @param id the tool's identifier, matching its future item registry name
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
     */
    public record Entry(
            String id,
            List<PartSlot> parts,
            float attackSpeed,
            float damagePotential,
            float preAttackMultiplier,
            float flatAttackBonus,
            float durabilityMultiplier,
            float miningSpeedModifier,
            boolean sumHeadsForAttack,
            boolean durabilitySkipsExtraAndHandle) {}

    private static final String TOOL_HANDLE = "tool_handle";
    private static final String TOOL_BINDING = "tool_binding";
    // Reconciled against the part items #151 actually registered (issue #155): this read
    // "tough_tool_handle", which no item has -- the registered id is forgeweave:tough_tool_rod
    // (ForgeweaveItems#PART_TOUGH_TOOL_ROD, upstream's own `toughToolRod`). Registered id wins.
    private static final String TOUGH_TOOL_HANDLE = "tough_tool_rod";
    private static final String TOUGH_BINDING = "tough_binding";

    /** Upstream {@code tools/melee/item/BroadSword.java}: sweep-attack sword, wide guard. */
    public static final Entry BROADSWORD = new Entry("broadsword",
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "sword_blade"),
                    new PartSlot(Role.EXTRA, "wide_guard")),
            1.6f, 1.0f, 1.0f, 1.0f, 1.1f, 1.0f, false, false);

    /** Upstream {@code tools/melee/item/LongSword.java}: charged-leap sword, hand guard. */
    public static final Entry LONGSWORD = new Entry("longsword",
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "sword_blade"),
                    new PartSlot(Role.EXTRA, "hand_guard")),
            1.4f, 1.1f, 1.0f, 0.5f, 1.05f, 1.0f, false, false);

    /** Upstream {@code tools/melee/item/Rapier.java}: fast hybrid-damage sword, cross guard. */
    public static final Entry RAPIER = new Entry("rapier",
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "sword_blade"),
                    new PartSlot(Role.EXTRA, "cross_guard")),
            3.0f, 0.55f, 1.0f, 0.0f, 0.8f, 1.0f, false, false);

    /** Upstream {@code tools/melee/item/BattleSign.java}: blocking/reflecting sign, no extra part. */
    public static final Entry BATTLESIGN = new Entry("battlesign",
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "sign_plate")),
            1.2f, 0.86f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    /** Upstream {@code tools/melee/item/FryPan.java}: charged-knockback pan, no extra part. */
    public static final Entry FRYING_PAN = new Entry("frying_pan",
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "pan")),
            1.4f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    /** Upstream {@code tools/tools/Mattock.java}: axe+shovel dual head, unweighted average. */
    public static final Entry MATTOCK = new Entry("mattock",
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "axe_head"),
                    new PartSlot(Role.HEAD, "shovel_head")),
            0.9f, 0.90f, 1.0f, 3.0f, 1.0f, 0.95f, false, false);

    /** Upstream {@code tools/tools/Kama.java}: single head, M1's shared binding part. */
    public static final Entry KAMA = new Entry("kama",
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "kama_head"),
                    new PartSlot(Role.EXTRA, TOOL_BINDING)),
            1.3f, 1.0f, 1.0f, 0.0f, 1.0f, 1.0f, false, false);

    /**
     * Part composition from upstream {@code tools/melee/item/BattleAxe.java} (never shipped, cited
     * for the part list only); damage/durability/speed/mining REBALANCED from scratch by maintainer
     * decision 2026-08-12 -- upstream's own unshipped numbers are explicitly not adopted. Damage
     * sums (not averages) its two heads; durability is the head average plus a flat 10% from the
     * binding, skipping the usual extra/handle chain -- see {@link Entry#sumHeadsForAttack}/
     * {@link Entry#durabilitySkipsExtraAndHandle}.
     */
    public static final Entry BATTLEAXE = new Entry("battleaxe",
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "broad_axe_head"),
                    new PartSlot(Role.HEAD, "broad_axe_head"), new PartSlot(Role.EXTRA, TOUGH_BINDING)),
            0.95f, 1.0f, 1.2f, 0.0f, 1.10f, 0.6f, true, true);

    /** Upstream {@code tools/melee/item/Cleaver.java}: head+shield average, pre-multiply then bonus. */
    public static final Entry CLEAVER = new Entry("cleaver",
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "large_sword_blade"),
                    new PartSlot(Role.HEAD, "large_plate"), new PartSlot(Role.EXTRA, TOUGH_TOOL_HANDLE)),
            0.7f, 1.2f, 1.3f, 3.0f, 2.0f, 1.0f, false, false);

    /** Upstream {@code tools/tools/Hammer.java}: hammer head weighted double over two large plates. */
    public static final Entry HAMMER = new Entry("hammer",
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "hammer_head", 2.0f),
                    new PartSlot(Role.HEAD, "large_plate"), new PartSlot(Role.HEAD, "large_plate")),
            0.8f, 1.2f, 1.0f, 0.0f, 2.5f, 0.4f, false, false);

    /** Upstream {@code tools/tools/Excavator.java}: excavator head + large plate, unweighted average. */
    public static final Entry EXCAVATOR = new Entry("excavator",
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "excavator_head"),
                    new PartSlot(Role.HEAD, "large_plate"), new PartSlot(Role.EXTRA, TOUGH_BINDING)),
            0.7f, 1.25f, 1.0f, 0.0f, 1.75f, 0.28f, false, false);

    /** Upstream {@code tools/tools/LumberAxe.java}: broad axe head + large plate, unweighted average. */
    public static final Entry LUMBERAXE = new Entry("lumberaxe",
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "broad_axe_head"),
                    new PartSlot(Role.HEAD, "large_plate"), new PartSlot(Role.EXTRA, TOUGH_BINDING)),
            0.8f, 1.2f, 1.0f, 2.0f, 2.0f, 0.35f, false, false);

    /** Upstream {@code tools/tools/Scythe.java}: single head, two tough handles averaged. */
    public static final Entry SCYTHE = new Entry("scythe",
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "scythe_head"),
                    new PartSlot(Role.EXTRA, TOUGH_BINDING), new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE)),
            0.9f, 0.75f, 1.0f, 0.0f, 2.2f, 1.0f, false, false);

    /**
     * No 1.12 counterpart. Part weights (hammer head 0.75 / large plate 0.25) and stats ported from
     * the 1.20 branch's {@code ToolDefinitionDataProvider} ({@code VEIN_HAMMER}) -- feature-scope
     * deviation authorized by maintainer 2026-08-12 (docs/SCOPE.md M3 sources table), numbers
     * confirmed unchanged in the issue #153 decision comment.
     */
    public static final Entry VEIN_HAMMER = new Entry("vein_hammer",
            // Reconciled against #151's registered parts (issue #155): the vein hammer has its own
            // vein_hammer_head part item, not the plain hammer_head this used to name. Weights are
            // unchanged. Registered id wins.
            List.of(new PartSlot(Role.HEAD, "vein_hammer_head", 0.75f), new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE),
                    new PartSlot(Role.EXTRA, TOUGH_BINDING), new PartSlot(Role.HEAD, "large_plate", 0.25f)),
            0.85f, 1.25f, 1.0f, 3.0f, 5.0f, 0.3f, false, false);

    /**
     * No 1.12 counterpart. Shape from the 1.20 branch's {@code DAGGER} ({@code smallBlade} + handle,
     * no extra part); base damage and attack speed are the issue #153 decision comment's numbers,
     * which deliberately do not carry over 1.20's own attack/mining/durability multipliers.
     */
    public static final Entry DAGGER = new Entry("dagger",
            List.of(new PartSlot(Role.HEAD, "knife_blade"), new PartSlot(Role.HANDLE, TOOL_HANDLE)),
            2.0f, 1.0f, 1.0f, 3.0f, 1.0f, 1.0f, false, false);

    /**
     * New shape (Forgeweave design, no upstream source): station-tier curved blade. Distinct head
     * part from the clone-derived swords above so assembly can tell them apart; numbers are the
     * issue #153 decision comment's (base damage 2.5 / speed 1.8), DoT innate decided on #159.
     */
    public static final Entry SCIMITAR = new Entry("scimitar",
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "curved_blade"),
                    new PartSlot(Role.EXTRA, "cross_guard")),
            1.8f, 1.0f, 1.0f, 2.5f, 1.0f, 1.0f, false, false);

    /**
     * New shape (Forgeweave design, no upstream source): station-tier ramping blade. Numbers are the
     * issue #153 decision comment's (base damage 2.75 / speed 1.6), ramp magnitudes decided on #160.
     */
    public static final Entry KATANA = new Entry("katana",
            List.of(new PartSlot(Role.HANDLE, TOOL_HANDLE), new PartSlot(Role.HEAD, "katana_blade"),
                    new PartSlot(Role.EXTRA, "hand_guard")),
            1.6f, 1.0f, 1.0f, 2.75f, 1.0f, 1.0f, false, false);

    /**
     * New shape (Forgeweave design, no upstream source): Tool Forge-tier mace-alike, smash rides
     * vanilla mace scaling (#161). Numbers are the issue #153 decision comment's (base damage 4.0 /
     * speed 0.8).
     */
    public static final Entry WARMACE = new Entry("warmace",
            List.of(new PartSlot(Role.HANDLE, TOUGH_TOOL_HANDLE), new PartSlot(Role.HEAD, "war_mace_head"),
                    new PartSlot(Role.EXTRA, TOUGH_BINDING)),
            0.8f, 1.0f, 1.0f, 4.0f, 1.0f, 1.0f, false, false);

    /** All 18 M3 tools, in docs/SCOPE.md content-manifest order. */
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

        for (int i = 0; i < parts.size(); i++) {
            PartSlot slot = parts.get(i);
            Material material = partMaterials.get(i);
            switch (slot.role()) {
                case HEAD -> {
                    float weight = slot.weight();
                    headDurabilityWeighted += material.head().durability() * weight;
                    headAttackWeighted += material.head().attackDamage() * weight;
                    headSpeedWeighted += material.head().miningSpeed() * weight;
                    headWeightTotal += weight;
                }
                case EXTRA -> {
                    extraDurabilitySum += material.extraDurability();
                    extraCount++;
                }
                case HANDLE -> {
                    handleDurabilitySum += material.handle().durability();
                    handleModifierSum += material.handle().durabilityModifier();
                    handleCount++;
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
        durability = Math.max(1, (int) (durability * entry.durabilityMultiplier()));

        float attack = entry.sumHeadsForAttack() ? headAttackWeighted : headAttackAvg;
        attack = attack * entry.preAttackMultiplier() + entry.flatAttackBonus();

        return new ToolStats.Stats(durability, headSpeedAvg, attack);
    }

    private ToolConstants() {}
}
