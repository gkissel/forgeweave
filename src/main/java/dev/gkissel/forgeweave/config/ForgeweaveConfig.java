package dev.gkissel.forgeweave.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Registered as a {@code SERVER}-type config ({@code Forgeweave#Forgeweave}), not {@code COMMON}: it
 * gates behavior visible in the client's enchanting table UI ({@link dev.gkissel.forgeweave.item.ToolItem#isEnchantable}),
 * and only {@code SERVER} configs are synced from server to client on login (NeoForge's
 * {@code ConfigSync}) -- the invariant CONTEXT.md requires ("Dedicated-server multiplayer
 * correctness is required for every shipped feature") would break with {@code COMMON}, since a
 * client's local copy could disagree with the server's and desync the UI from what the server
 * actually allows.
 */
public final class ForgeweaveConfig {
    public static final ModConfigSpec SPEC;

    /** CONTEXT.md invariant: tools are not enchantable at the vanilla enchanting table by default. */
    public static final ModConfigSpec.BooleanValue ALLOW_VANILLA_ENCHANTING;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        ALLOW_VANILLA_ENCHANTING = builder
                .comment("If true, Forgeweave tools can be enchanted at the vanilla enchanting table.")
                .define("allowVanillaEnchanting", false);
        SPEC = builder.build();
    }

    private ForgeweaveConfig() {}
}
