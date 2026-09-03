package dev.gkissel.forgeweave.tool;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.sound.ForgeweaveSounds;

/**
 * The level-up chat line and chime (docs/SCOPE.md M7, D-M7-8; issue #922), ported from Tinkers'
 * Tool Leveling's {@code CommonProxy#sendLevelUpMessage}/{@code playLevelupDing} (MIT, NOTICE.md)
 * without the {@code @SidedProxy} split -- 1.21.1 has no client/server proxy pattern to port it
 * onto. {@link ToolLeveling#addXp} is the only caller, once per level a grant crosses.
 */
final class ToolLevelFeedback {
    private ToolLevelFeedback() {}

    /**
     * Levels with their own {@code message.forgeweave.levelup.<n>} key (upstream's {@code en_us.lang}
     * has no key below 2: the first level-up, level 1, always takes the generic line below).
     */
    private static final int CHAT_LEVEL_MIN = 2;
    private static final int CHAT_LEVEL_MAX = 11;

    /** The chime plus the chat line, in upstream's order. */
    static void onLevelUp(ItemStack stack, int level, ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), ForgeweaveSounds.TOOL_LEVEL_UP.get(),
                SoundSource.PLAYERS, 1.0F, 1.0F);
        player.displayClientMessage(message(stack, level), false);
    }

    /**
     * {@code message.forgeweave.levelup.<n>} for level 2-11, taking the tool's name; the generic
     * {@code message.forgeweave.levelup.generic} otherwise, taking the tool's name and
     * {@link ToolLevelName#name}. Upstream wraps the whole line dark aqua ({@code
     * CommonProxy#sendLevelUpMessage}); a {@code Component}'s style cascades to args with no style of
     * their own, so setting it on the outer component alone reproduces that.
     */
    static Component message(ItemStack stack, int level) {
        Component toolName = stack.getHoverName();
        MutableComponent text = level >= CHAT_LEVEL_MIN && level <= CHAT_LEVEL_MAX
                ? Component.translatable("message.forgeweave.levelup." + level, toolName)
                : Component.translatable("message.forgeweave.levelup.generic", toolName, ToolLevelName.name(level));
        return text.withStyle(ChatFormatting.DARK_AQUA);
    }
}
