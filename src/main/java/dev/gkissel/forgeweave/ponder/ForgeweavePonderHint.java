package dev.gkissel.forgeweave.ponder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.fml.ModList;

/**
 * The chat hint docs/SCOPE.md M2 issue #110 asks for when Ponder isn't installed: "Install Ponder for
 * in-game build tutorials -- recipes are in JEI", shown once per player on their first interaction
 * with a smeltery controller ({@code SmelteryControllerBlock#useWithoutItem}).
 *
 * <p>"Once" is tracked server-side in the player's persistent data, under NeoForge's special
 * {@code PlayerPersisted} sub-tag -- the only part of {@code Entity#getPersistentData()} that
 * survives death and respawn (a new {@code ServerPlayer} instance), which matters here since dying
 * must not re-show a one-time hint.
 */
public final class ForgeweavePonderHint {
    private static final String PLAYER_PERSISTED_TAG = "PlayerPersisted";
    private static final String SHOWN_KEY = "forgeweave_ponder_hint_shown";

    /** No-op once Ponder is installed (its scenes carry the tutorial instead) or after the first call per player. */
    public static void maybeShow(ServerPlayer player) {
        if (ModList.get().isLoaded("ponder")) {
            return;
        }
        CompoundTag root = player.getPersistentData();
        CompoundTag persisted = root.getCompoundOrEmpty(PLAYER_PERSISTED_TAG);
        if (persisted.getBooleanOr(SHOWN_KEY, false)) {
            return;
        }
        persisted.putBoolean(SHOWN_KEY, true);
        root.put(PLAYER_PERSISTED_TAG, persisted);
        player.sendSystemMessage(Component.translatable("chat.forgeweave.ponder_hint"));
    }

    private ForgeweavePonderHint() {}
}
