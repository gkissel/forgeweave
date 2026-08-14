package dev.gkissel.forgeweave.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Forgeweave's display preferences (docs/SCOPE.md M3.4-7 issue #276): upstream 1.12's {@code
 * clientside} config category, minus the options whose feature Forgeweave does not have. Each keeps
 * upstream's own default.
 *
 * <p>Registered as a {@code CLIENT}-type config ({@code Forgeweave#Forgeweave}), unlike the
 * gameplay {@link ForgeweaveConfig}: none of these change what the server does, so there is nothing
 * to sync and nothing that could desync -- they are per-player preferences about how the same
 * server state is drawn, and a {@code SERVER} config would wrongly let a server dictate them (and
 * would not even be loaded when the value is read in a main-menu creative search).
 *
 * <p>Because a {@code CLIENT} spec is never loaded on a dedicated server, these values must not be
 * read from any code path that runs there -- every read below sits behind a screen, a tooltip, or
 * the creative tab. That also means {@code runGameTestServer} cannot toggle them; their gates are
 * covered by unit tests that take the flag as a parameter instead (see the PR body).
 */
public final class ForgeweaveClientConfig {
    public static final ModConfigSpec SPEC;

    /** Upstream {@code extraTooltips}: tools and parts show their detailed Shift tooltip. */
    public static final ModConfigSpec.BooleanValue EXTRA_TOOLTIPS;

    /**
     * Upstream {@code temperatureCelsius}: smeltery and JEI temperatures render in celsius rather
     * than the internal kelvin scale whose zero sits at
     * {@link dev.gkissel.forgeweave.recipe.MeltingRecipe#AMBIENT_TEMPERATURE}.
     */
    public static final ModConfigSpec.BooleanValue TEMPERATURE_CELSIUS;

    /**
     * Upstream {@code listAllPartMaterials}: the creative tab lists every material variant of every
     * tool part. With this off it lists one variant per part, which is upstream's own escape hatch
     * for how large that cartesian product gets.
     */
    public static final ModConfigSpec.BooleanValue LIST_ALL_PART_MATERIALS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        EXTRA_TOOLTIPS = builder
                .comment("If true, tools and tool parts show additional info in their tooltips while Shift is held.")
                .define("extraTooltips", true);
        TEMPERATURE_CELSIUS = builder
                .comment("If true, temperatures in the smeltery and in JEI display in celsius. If false they use",
                        "the internal units of kelvin, which may be better for pack authors.")
                .define("temperatureCelsius", true);
        LIST_ALL_PART_MATERIALS = builder
                .comment("If true, all material variants of every tool part are listed in creative. Set to false to",
                        "list only the first material for each part.")
                .define("listAllPartMaterials", true);

        SPEC = builder.build();
    }

    private ForgeweaveClientConfig() {}
}
