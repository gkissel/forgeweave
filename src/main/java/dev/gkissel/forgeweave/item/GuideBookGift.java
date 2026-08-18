package dev.gkissel.forgeweave.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * Parity audit T13: upstream 1.12's {@code shared/PlayerDataEvents} gives every player the guide
 * book the first time they log in, gated by {@code Config.spawnWithBook} (default {@code true}).
 * The ticket's "PR #360 misstated upstream" note is about the book item existing at all, not this
 * grant -- #360 shipped {@link GuideBookItem} itself but never the first-login gift; nothing here
 * contradicts it.
 *
 * <p>"Once per player" is tracked the same way {@link dev.gkissel.forgeweave.ponder.ForgeweavePonderHint}
 * already does: a flag in the player's {@code PlayerPersisted} sub-tag, the part of
 * {@code Entity#getPersistentData()} that survives death and respawn. Upstream's own flag
 * ({@code TAG_PLAYER_HAS_BOOK = Util.prefix("spawned_book")}) lives in the same place for the same
 * reason.
 */
public final class GuideBookGift {
    private static final String PLAYER_PERSISTED_TAG = "PlayerPersisted";
    private static final String GIVEN_KEY = "forgeweave_spawned_book";

    /** Wired to {@code PlayerEvent.PlayerLoggedInEvent} in {@code Forgeweave}'s constructor. */
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            maybeGrant(player);
        }
    }

    /** No-op when the config option is off or the player has already received the book. */
    public static void maybeGrant(ServerPlayer player) {
        if (!ForgeweaveConfig.SPAWN_WITH_BOOK.get()) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompound(PLAYER_PERSISTED_TAG);
        if (persisted.getBoolean(GIVEN_KEY)) {
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ForgeweaveItems.GUIDE_BOOK.get()));
        persisted.putBoolean(GIVEN_KEY, true);
        root.put(PLAYER_PERSISTED_TAG, persisted);
    }

    private GuideBookGift() {}
}
