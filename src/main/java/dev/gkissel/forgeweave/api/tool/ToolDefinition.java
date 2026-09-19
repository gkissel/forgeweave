package dev.gkissel.forgeweave.api.tool;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * A tool another mod wants Forgeweave's stations to build: what it is called, which family it
 * belongs to, the parts it assembles from in slot order, and the handful of numbers that separate
 * one shape from another (issue #1066).
 *
 * <p>Everything but the id, the family and the part list has a default, because most of what makes
 * a tool is its part list. Build one with {@link #builder}:
 *
 * <pre>
 * ToolDefinition.builder(id, ToolFamily.MELEE)
 *         .part(PartRole.HANDLE, rodId)
 *         .part(PartRole.HEAD, bladeId)
 *         .attackSpeed(1.6f)
 *         .flatAttackBonus(2.0f)
 *         .weapon()
 *         .build();
 * </pre>
 *
 * <p>Slot order is assembly order: slot {@code i} of the Tool Station takes the part named by part
 * {@code i}, and that slot's material feeds part {@code i} of the stat formula. A part may repeat,
 * in the same role or a different one.
 *
 * <p>ponytail: the tool-specific escape hatches Forgeweave's own roster carries -- the pre-attack
 * multiplier, the per-tool damage cutoff, the launcher bonus multiplier, the two battleaxe booleans
 * and an explicit per-slot repair table -- are deliberately not here. Every one of them exists for a
 * single 1.12 tool, and leaving them out keeps the promised surface to what a shape actually needs.
 * Adding one later is additive: a new builder method and a new field with the identity default.
 *
 * @param id the tool's registry id, which is also its texture and lang key base
 * @param family the content family whose config switch this tool follows
 * @param parts the ordered part composition; at least one slot must read a head block
 * @param mineableBlocks the {@code mineable/*} tags this tool is effective on, which also decide
 *     which tool abilities it answers for; empty for a pure weapon
 * @param weapon whether a hit costs this tool half of what a block break does, as upstream gives its
 *     weapon-category tools
 * @param attackSpeed attacks per second
 * @param damagePotential multiplier on the head material's attack damage, applied when the tool
 *     swings rather than when it is built
 * @param flatAttackBonus flat damage added once, at assembly
 * @param durabilityMultiplier multiplier on the assembled durability
 * @param miningSpeedModifier multiplier on the head material's mining speed
 */
public record ToolDefinition(
        ResourceLocation id,
        ToolFamily family,
        List<PartSlot> parts,
        List<TagKey<Block>> mineableBlocks,
        boolean weapon,
        float attackSpeed,
        float damagePotential,
        float flatAttackBonus,
        float durabilityMultiplier,
        float miningSpeedModifier) {

    /**
     * One slot in a tool's assembly order.
     *
     * @param role which stat block and trait scope this slot reads
     * @param partId the part item this slot takes, in any namespace
     * @param weight how far this slot counts in the head average; only a tool with more than one
     *     head-reading slot has any use for a value other than {@code 1}
     */
    public record PartSlot(PartRole role, ResourceLocation partId, float weight) {
        public PartSlot(PartRole role, ResourceLocation partId) {
            this(role, partId, 1.0f);
        }
    }

    public ToolDefinition {
        parts = List.copyOf(parts);
        mineableBlocks = List.copyOf(mineableBlocks);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException(id + ": a tool needs at least one part slot");
        }
    }

    public static Builder builder(ResourceLocation id, ToolFamily family) {
        return new Builder(id, family);
    }

    /** Collects a {@link ToolDefinition}; every number starts at the value that changes nothing. */
    public static final class Builder {
        private final ResourceLocation id;
        private final ToolFamily family;
        private final List<PartSlot> parts = new ArrayList<>();
        private final List<TagKey<Block>> mineableBlocks = new ArrayList<>();
        private boolean weapon;
        private float attackSpeed = 1.0f;
        private float damagePotential = 1.0f;
        private float flatAttackBonus;
        private float durabilityMultiplier = 1.0f;
        private float miningSpeedModifier = 1.0f;

        private Builder(ResourceLocation id, ToolFamily family) {
            this.id = id;
            this.family = family;
        }

        public Builder part(PartRole role, ResourceLocation partId) {
            return part(role, partId, 1.0f);
        }

        public Builder part(PartRole role, ResourceLocation partId, float weight) {
            parts.add(new PartSlot(role, partId, weight));
            return this;
        }

        /** A {@code mineable/*} tag this tool is effective on. Call it once per tag. */
        public Builder mineable(TagKey<Block> tag) {
            mineableBlocks.add(tag);
            return this;
        }

        public Builder weapon() {
            this.weapon = true;
            return this;
        }

        public Builder attackSpeed(float attackSpeed) {
            this.attackSpeed = attackSpeed;
            return this;
        }

        public Builder damagePotential(float damagePotential) {
            this.damagePotential = damagePotential;
            return this;
        }

        public Builder flatAttackBonus(float flatAttackBonus) {
            this.flatAttackBonus = flatAttackBonus;
            return this;
        }

        public Builder durabilityMultiplier(float durabilityMultiplier) {
            this.durabilityMultiplier = durabilityMultiplier;
            return this;
        }

        public Builder miningSpeedModifier(float miningSpeedModifier) {
            this.miningSpeedModifier = miningSpeedModifier;
            return this;
        }

        public ToolDefinition build() {
            return new ToolDefinition(id, family, parts, mineableBlocks, weapon, attackSpeed, damagePotential,
                    flatAttackBonus, durabilityMultiplier, miningSpeedModifier);
        }
    }
}
