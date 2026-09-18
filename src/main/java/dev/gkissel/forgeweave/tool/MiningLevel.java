package dev.gkissel.forgeweave.tool;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * What mining level a block needs and what level a tool has (D-M8-9, issue #968), for the one line
 * the Jade and WTHIT overlays draw: side by side, so "why does this pick bounce off" is answered on
 * screen instead of on a wiki. It matters more now than it did at M6, since Track B put three rungs
 * above netherite ({@code TrackBOre.Tier}) and nothing else in the game explains them.
 *
 * <p>Both numbers come off tags that already decide the answer, rather than a second table that
 * would drift from them. The ladder is {@code ForgeweaveModifiers#TIER_TAGS}, the
 * {@code incorrect_for_*_tool} rungs: index {@code n} means "a tool at rung {@code n} cannot mine
 * what rung {@code n}'s tag denies", so a block's requirement is the first rung whose tag does not
 * deny it, and a tool's level is the rung its own deny-drops rule names. Vanilla blocks and vanilla
 * tools work unchanged: vanilla's {@code incorrect_for_*_tool} tags are the bottom five rungs of the
 * same ladder, and a vanilla tool's {@code TOOL} component carries exactly the deny-drops rule this
 * reads.
 *
 * <p>Nothing here knows about Jade or WTHIT, so the two plugins share one implementation and this
 * class stays unit- and GameTestable without either mod present
 * ({@code MiningLevelGameTests}).
 */
public final class MiningLevel {

    /** A held stack that is not a tool with a rung on the ladder: a bare hand, a block, a sword. */
    public static final int NO_TOOL = -1;

    /**
     * The rung {@code state} needs, 0 for a block nothing on the ladder denies. Read as "the first
     * rung whose tag does not deny this block", which is why it walks down from the top: a block
     * denied to rung {@code n} needs at least rung {@code n + 1}.
     *
     * <p>A block in the top rung's own tag would need a rung above the ladder. Nothing ships in that
     * tag, so this answers {@link ForgeweaveModifiers#tierCount()} for it and {@link #name} clamps.
     */
    public static int required(BlockState state) {
        for (int rung = ForgeweaveModifiers.tierCount() - 1; rung >= 0; rung--) {
            if (state.is(ForgeweaveModifiers.tierTag(rung))) {
                return rung + 1;
            }
        }
        return 0;
    }

    /**
     * The rung {@code stack} stands on, or {@link #NO_TOOL}. Read off the stack's own deny-drops
     * rule rather than its head material, the same choice {@code ToolTooltip#effectiveTierTag}
     * makes: a diamond or emerald modifier, or a fortification, rewrites that rule without touching
     * the material record, and the rewritten rule is what the game actually enforces.
     */
    public static int held(ItemStack stack) {
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) {
            return NO_TOOL;
        }
        for (Tool.Rule rule : tool.rules()) {
            if (rule.speed().isEmpty()) {
                int rung = ForgeweaveModifiers.tierIndexOf(rule.blocks());
                if (rung >= 0) {
                    return rung;
                }
            }
        }
        return NO_TOOL;
    }

    /**
     * A rung's player-facing name, the {@code tooltip.forgeweave.tier.*} family the tool tooltip
     * already uses, so a rung is worded the same way wherever it is shown.
     */
    public static Component name(int rung) {
        // ponytail: clamped rather than given a rung of its own, since nothing ships above the top
        // tag. Give the off-ladder case its own string if a pack ever puts a block in there.
        return name(ForgeweaveModifiers.tierTag(Math.min(rung, ForgeweaveModifiers.tierCount() - 1)));
    }

    /**
     * The same name for a rung named by its tag, which is how {@code ToolTooltip} reaches it: the
     * key is the tag path with the {@code incorrect_for_}/{@code _tool} wrapping taken off, so a new
     * rung needs one lang key and no table entry.
     */
    public static Component name(TagKey<Block> tierTag) {
        return Component.translatable("tooltip.forgeweave.tier."
                + tierTag.location().getPath().replace("incorrect_for_", "").replace("_tool", ""));
    }

    /**
     * The overlay line for a block being looked at while {@code heldStack} is in hand, or
     * {@code null} when there is nothing to say. Silent for a block that drops without a correct
     * tool, since "why does this bounce off" has no answer to give for dirt and the overlays should
     * not grow a line on every block in the game.
     *
     * <p>Also silent while {@code compat.overlays} is off (#968, D-M8-5). The toggle is read here
     * rather than only in the two providers so that it has a site no overlay mod's classes reach:
     * both plugins are compiled against APIs that are absent from {@code runGameTestServer}, so a
     * GameTest can prove the toggle works on this method and nowhere else
     * ({@code CompatToggleGameTests}).
     */
    @Nullable
    public static Component line(BlockState state, ItemStack heldStack) {
        if (!ForgeweaveConfig.enabled(ForgeweaveConfig.OVERLAYS) || !state.requiresCorrectToolForDrops()) {
            return null;
        }
        Component block = name(required(state));
        int rung = held(heldStack);
        return rung == NO_TOOL
                ? Component.translatable("waila.forgeweave.mining_level", block)
                : Component.translatable("waila.forgeweave.mining_level.held", block, name(rung));
    }

    private MiningLevel() {}
}
