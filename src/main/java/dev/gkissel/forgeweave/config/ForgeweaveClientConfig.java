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

    /**
     * Upstream {@code listAllTables} (parity audit T75, issue #506): the creative tab lists a
     * retextured variant of the Stencil Table and Part Builder for every plank/log the crafting
     * station-table's own {@code RetexturedShapedRecipe} can key its {@code TEXTURE} component off
     * ({@code #minecraft:planks}, {@code #minecraft:logs} -- see {@code ForgeweaveRecipeProvider}).
     * With this off, only the plain (default-textured) block is listed, upstream's own escape hatch
     * for how large that variant list gets. The Crafting Station and Tool Station are unaffected:
     * upstream's own {@code getSubBlocks} never runs this expansion for either of them either (a
     * boring single entry for the Crafting Station, and the Tool Station's single {@code workbench}
     * ore-dict ingredient never had more than one match to begin with).
     */
    public static final ModConfigSpec.BooleanValue LIST_ALL_TABLE_VARIANTS;

    /**
     * Upstream {@code renderTableItems} (parity audit T75, issue #567 -- #506's leftover half): the
     * Crafting Station, Stencil Table, Part Builder and Tool Station draw whatever is sitting in
     * their input slots lying on their top, via {@link dev.gkissel.forgeweave.client.TableItemRenderer}.
     * With this off, a station renders bare no matter what it holds, upstream's own escape hatch for
     * players who find the extra geometry distracting or costly to render.
     */
    public static final ModConfigSpec.BooleanValue RENDER_TABLE_ITEMS;

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
        LIST_ALL_TABLE_VARIANTS = builder
                .comment("If true, a retextured variant of the Stencil Table and Part Builder is listed in creative",
                        "for every plank/log. Set to false to list only the default-textured block.")
                .define("listAllTables", true);
        RENDER_TABLE_ITEMS = builder
                .comment("If true, the Crafting Station, Stencil Table, Part Builder and Tool Station draw whatever",
                        "is in their input slots lying on their top. Set to false to render these stations bare.")
                .define("renderTableItems", true);

        SPEC = builder.build();
    }

    private ForgeweaveClientConfig() {}
}
