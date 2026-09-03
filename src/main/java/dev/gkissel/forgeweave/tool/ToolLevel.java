package dev.gkissel.forgeweave.tool;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * A tool's or armor piece's leveling state (docs/SCOPE.md M7, D-M7-7; issue #918): how many levels
 * it has earned, how much XP it has banked toward the next one, and how many modifier slots those
 * levels granted it. Ported from Tinkers' Tool Leveling's {@code ToolLevelNBT} (MIT), whose three
 * fields these are -- {@code level} off its {@code ModifierNBT} base, plus {@code xp} and
 * {@code bonus_modifiers}, renamed {@code bonus_slots} for Forgeweave's own vocabulary.
 *
 * <p>The slot count is stored rather than recomputed from {@code level} for upstream's reason,
 * kept: a pack that retunes {@code levelMultiplier} or {@code defaultBaseXP} must never take back a
 * slot a tool already earned, because a modifier may already be spent into it.
 *
 * <p>Absent from a stack means {@link #NONE} -- level 0, no XP, no earned slots -- so nothing built
 * before M7 needs migrating or backfilling.
 */
public record ToolLevel(int level, int xp, int bonusSlots) {
    /** What an absent component reads as: an unleveled tool. */
    public static final ToolLevel NONE = new ToolLevel(0, 0, 0);

    public static final Codec<ToolLevel> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("level").forGetter(ToolLevel::level),
            Codec.INT.fieldOf("xp").forGetter(ToolLevel::xp),
            Codec.INT.fieldOf("bonus_slots").forGetter(ToolLevel::bonusSlots))
            .apply(instance, ToolLevel::new));

    public static final StreamCodec<ByteBuf, ToolLevel> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ToolLevel::level,
            ByteBufCodecs.VAR_INT, ToolLevel::xp,
            ByteBufCodecs.VAR_INT, ToolLevel::bonusSlots,
            ToolLevel::new);

    /** The stack's leveling state, or {@link #NONE} when it has none. */
    public static ToolLevel of(ItemStack stack) {
        ToolLevel level = stack.get(ForgeweaveDataComponents.TOOL_LEVEL.get());
        return level == null ? NONE : level;
    }

    /**
     * This state with {@code amount} XP added and any levels that buys already applied, each level
     * granting one modifier slot. Returns {@code this} unchanged once the cap is reached -- past the
     * cap XP is discarded rather than banked, which is what upstream's early return does too.
     *
     * <p>Levels roll while the threshold is still met (issue #918); upstream levels at most once per
     * call, which only differs for a single grant worth two whole levels.
     *
     * @param baseXp what this tool's first level costs -- see {@link ToolLeveling#baseXp}
     * @param levelMultiplier what each further level multiplies the last one's cost by
     * @param maximumLevels the level cap, or {@code 0}/negative for none
     */
    public ToolLevel plusXp(int amount, int baseXp, double levelMultiplier, int maximumLevels) {
        if (!ToolLeveling.canLevelUp(level, maximumLevels)) {
            return this;
        }
        ToolLevel result = new ToolLevel(level, xp + amount, bonusSlots);
        int cost = ToolLeveling.xpForLevelup(result.level, baseXp, levelMultiplier);
        while (result.xp >= cost && ToolLeveling.canLevelUp(result.level, maximumLevels)) {
            result = new ToolLevel(result.level + 1, result.xp - cost, result.bonusSlots + 1);
            cost = ToolLeveling.xpForLevelup(result.level, baseXp, levelMultiplier);
        }
        return result;
    }
}
