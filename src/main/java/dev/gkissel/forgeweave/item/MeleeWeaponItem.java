package dev.gkissel.forgeweave.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * A pure melee weapon -- one of the tool types upstream 1.12 refuses to break blocks with in
 * creative mode (issue #437, parity audit T5).
 *
 * <p>{@code library/tools/SwordCore.java} (broadsword, longsword, rapier, cleaver),
 * {@code tools/melee/item/FryPan.java} and {@code tools/melee/item/BattleSign.java} each override
 * {@code ToolCore#canDestroyBlockInCreative} to {@code false}, so a creative player swinging one
 * hits mobs instead of instantly deleting the block behind them. 1.21 spells that hook
 * {@link #canAttackBlock}, and vanilla's own {@code SwordItem} and {@code MaceItem} implement it the
 * same way; the whole of this class is that one override.
 *
 * <p>What a weapon <em>does</em> mine stays a registration-site choice as it is for every other
 * {@link ToolItem}: the sword shapes take {@code #minecraft:sword_efficient} (plus the cobweb rule
 * {@link ToolItem#toolComponent} adds for it) at {@code ToolConstants.Entry#miningSpeedModifier} 0.5,
 * while the frying pan, the battlesign and the warmace take no mineable tag at all -- upstream's
 * {@code ToolCore#isEffective} default is {@code false}, and vanilla's mace ships
 * {@code new Tool(List.of(), ...)}.
 */
public class MeleeWeaponItem extends ToolItem {

    /** The one-tag shape, for a sword: {@code #minecraft:sword_efficient}. */
    public MeleeWeaponItem(Properties properties, ToolConstants.Entry constants, TagKey<Block> mineableBlocks,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        super(properties, constants, mineableBlocks, weapon, innate);
    }

    /** The no-tag shape, for a weapon that mines nothing: pass {@link List#of()}. */
    public MeleeWeaponItem(Properties properties, ToolConstants.Entry constants, List<TagKey<Block>> mineableBlocks,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        super(properties, constants, mineableBlocks, weapon, innate);
    }

    /** Upstream {@code canDestroyBlockInCreative() == false}; see this class's javadoc. */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return !player.isCreative();
    }
}
