package dev.gkissel.forgeweave.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Forgeweave's gameplay config (docs/SCOPE.md M3.4-7 issue #276): the subset of upstream 1.12's
 * {@code common/config/Config.java} that has a behavior site here, each keeping upstream's own
 * default. Purely visual preferences live in {@link ForgeweaveClientConfig} instead -- see that
 * class for the split.
 *
 * <p>Registered as a {@code SERVER}-type config ({@code Forgeweave#Forgeweave}), not {@code COMMON}:
 * every option here changes what the server will actually do (what a slot accepts, what a melt
 * yields, what generates in the Nether), and only {@code SERVER} configs are synced from server to
 * client on login (NeoForge's {@code ConfigSync}). The invariant CONTEXT.md requires
 * ("Dedicated-server multiplayer correctness is required for every shipped feature") would break
 * with {@code COMMON}, since a client's local copy could disagree with the server's -- e.g.
 * {@link #REUSE_STENCILS} is read by both sides of the Stencil Table menu, and
 * {@link dev.gkissel.forgeweave.item.ToolItem#isEnchantable} drives the client's enchanting-table
 * UI.
 *
 * <p>None of these need a game restart: every one is read at the moment its behavior runs
 * ({@code worldRestart()} is deliberately unused), which is the runtime-check bucket issue #276
 * asks to prefer. The two upstream options that gate <em>registration</em> rather than behavior are
 * handled the same way -- {@link #ENABLE_CLAY_CASTS}'s recipes will be filtered at lookup time by
 * issue #292 -- so no conditional-recipe machinery is needed anywhere.
 */
public final class ForgeweaveConfig {
    /**
     * The ore-to-ingot ratio {@link dev.gkissel.forgeweave.block.SmelteryCore}'s own multipliers are
     * already calibrated against: at this value the config is a no-op and a Nether Core yields
     * exactly upstream's 2 ingots per ore. See {@link #ORE_TO_INGOT_RATIO}.
     */
    public static final double ORE_TO_INGOT_BASELINE = 2.0D;

    public static final ModConfigSpec SPEC;

    /** CONTEXT.md invariant: tools are not enchantable at the vanilla enchanting table by default. */
    public static final ModConfigSpec.BooleanValue ALLOW_VANILLA_ENCHANTING;

    /** Upstream {@code reuseStencils}: an already-stamped pattern may be reshaped in the Stencil Table. */
    public static final ModConfigSpec.BooleanValue REUSE_STENCILS;

    /**
     * Upstream {@code oreToIngotRatio}: how many ingots one ore melts into. Upstream bakes this into
     * each ore melting recipe's amount; Forgeweave's recipes hold a base (raw-drop equivalent) amount
     * and {@link dev.gkissel.forgeweave.block.SmelteryCore#yieldMultiplier()} scales it at melt time
     * (docs/SCOPE.md M2, issues #96/#99), so this is applied there as a global scalar relative to
     * {@link #ORE_TO_INGOT_BASELINE} -- keeping "core tier is the ONLY yield axis" true while still
     * letting a pack dial total ore yield the way upstream's option does.
     */
    public static final ModConfigSpec.DoubleValue ORE_TO_INGOT_RATIO;

    /** Upstream {@code obsidianAlloy}: molten obsidian may be alloyed from lava and water. */
    public static final ModConfigSpec.BooleanValue OBSIDIAN_ALLOY;

    /**
     * Upstream {@code enableClayCasts}. Registered here with upstream's default so the option exists
     * on the M3.4 config surface; the single-use clay casts it gates are issue #292's work, which
     * wires this to their recipes. Until then this reads as a no-op.
     */
    public static final ModConfigSpec.BooleanValue ENABLE_CLAY_CASTS;

    /** Upstream {@code genCobalt}: cobalt ore generates in the Nether. */
    public static final ModConfigSpec.BooleanValue GEN_COBALT;
    /** Upstream {@code cobaltRate}: approximate cobalt veins per Nether chunk. */
    public static final ModConfigSpec.IntValue COBALT_RATE;
    /** Upstream {@code genArdite}: ardite ore generates in the Nether. */
    public static final ModConfigSpec.BooleanValue GEN_ARDITE;
    /** Upstream {@code arditeRate}: approximate ardite veins per Nether chunk. */
    public static final ModConfigSpec.IntValue ARDITE_RATE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // Gameplay options stay at the top level: allowVanillaEnchanting already shipped there, and
        // pushing a category now would silently reset every existing config file's copy of it.
        ALLOW_VANILLA_ENCHANTING = builder
                .comment("If true, Forgeweave tools can be enchanted at the vanilla enchanting table.")
                .define("allowVanillaEnchanting", false);
        REUSE_STENCILS = builder
                .comment("Allows reusing patterns in the Stencil Table to turn them into other patterns.")
                .define("reuseStencils", true);
        ORE_TO_INGOT_RATIO = builder
                .comment("How many ingots one ore melts into at the top smeltery core tier. Lower tiers scale",
                        "down from this by their own yield multiplier. Cannot go below 1.")
                .defineInRange("oreToIngotRatio", ORE_TO_INGOT_BASELINE, 1.0D, 64.0D);
        OBSIDIAN_ALLOY = builder
                .comment("Allows the creation of molten obsidian in the smeltery from lava and water.")
                .define("obsidianAlloy", true);
        ENABLE_CLAY_CASTS = builder
                .comment("Adds single-use clay casts. Has no effect yet -- the casts themselves are issue #292.")
                .define("enableClayCasts", true);

        builder.comment("World generation options").push("worldgen");
        GEN_COBALT = builder
                .comment("If true, cobalt ore generates in the Nether.")
                .define("genCobalt", true);
        COBALT_RATE = builder
                .comment("Approximate cobalt veins per Nether chunk.")
                .defineInRange("cobaltRate", 20, 0, 256);
        GEN_ARDITE = builder
                .comment("If true, ardite ore generates in the Nether.")
                .define("genArdite", true);
        ARDITE_RATE = builder
                .comment("Approximate ardite veins per Nether chunk.")
                .defineInRange("arditeRate", 20, 0, 256);
        builder.pop();

        SPEC = builder.build();
    }

    private ForgeweaveConfig() {}
}
