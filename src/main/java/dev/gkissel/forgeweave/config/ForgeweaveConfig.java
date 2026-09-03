package dev.gkissel.forgeweave.config;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

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
 * handled the same way -- {@link #ENABLE_CLAY_CASTS}'s recipes are filtered at lookup time
 * (issue #292) -- so no conditional-recipe machinery is needed anywhere.
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
     * Upstream {@code chestsKeepInventory} (parity audit T47): a harvested Pattern or Part Chest
     * carries its contents on the dropped item instead of spilling them. See {@code
     * ChestBlockEntity#collectImplicitComponents} for how the contents ride along.
     */
    public static final ModConfigSpec.BooleanValue CHESTS_KEEP_INVENTORY;

    /**
     * Upstream {@code spawnWithBook} (parity audit T13): a player who has never received one before is
     * given the guide book on their first login, tracked with the same once-per-player idiom as
     * {@link dev.gkissel.forgeweave.ponder.ForgeweavePonderHint} -- see
     * {@link dev.gkissel.forgeweave.item.GuideBookGift}.
     */
    public static final ModConfigSpec.BooleanValue SPAWN_WITH_BOOK;

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
     * Upstream {@code addFlintRecipe} (parity audit T55, issue #486): a shapeless crafting-table
     * recipe turns 3 gravel into a flint. Checked at match time, the same "no restart" idiom every
     * other option here uses ({@link dev.gkissel.forgeweave.recipe.GravelFlintRecipe#matches}) --
     * upstream instead marks its own property {@code requiresMcRestart(true)} and gates the recipe's
     * datapack JSON with a load-time condition (`Config.java:201-205`, `recipes/common/flint.json`),
     * but that is the one upstream option that would need genuinely different (restart-requiring)
     * machinery from every other Forgeweave toggle for no behavioral gain, so it is adapted to match
     * this codebase's uniform runtime-check convention instead.
     */
    public static final ModConfigSpec.BooleanValue ADD_FLINT_RECIPE;

    /**
     * Upstream {@code matchVanillaSlimeblock} (issue #635, parity audit T57), default {@code false}
     * as upstream's is ({@code Config.java:47}): nine slime balls of mixed colour give a pink slime
     * block instead of a vanilla one. Read at match time by
     * {@link dev.gkissel.forgeweave.recipe.MixedSlimeBlockRecipe}, the same runtime-check convention
     * {@link #ADD_FLINT_RECIPE} above explains.
     */
    public static final ModConfigSpec.BooleanValue MATCH_VANILLA_SLIMEBLOCK;

    /**
     * Upstream {@code enableClayCasts}: the single-use clay casts (issue #292) can be moulded and
     * cast through. Upstream skips registering their recipes when this is off; casting recipes are
     * datapack entries here, so {@link dev.gkissel.forgeweave.casting.CastingRecipe#matches} filters
     * them at lookup instead. The items themselves stay registered either way, exactly as upstream's
     * do.
     */
    public static final ModConfigSpec.BooleanValue ENABLE_CLAY_CASTS;

    /**
     * Upstream {@code craftCastableMaterials} (issue #435, parity audit T3): let the Part Builder
     * craft parts from materials that are meant to be cast. Upstream's default is {@code false} --
     * {@code Config.java:38} -- which is what makes its whole metal roster Smeltery-only, and
     * {@link #craftCastableMaterials()} is how the one gate reads it.
     *
     * <p>Which materials this frees is a datapack question, not a Java one: a material says
     * {@code "cast_only": true} and keeps listing its {@code crafting_items}, so turning this on
     * makes exactly those items pay for parts again.
     */
    public static final ModConfigSpec.BooleanValue CRAFT_CASTABLE_MATERIALS;

    /**
     * Upstream {@code craftingStationBlacklist} (parity audit T74, issue #505): registry names or
     * block-entity classnames that a station's side-inventory scan ({@code
     * dev.gkissel.forgeweave.block.SideInventory#findExternal}) should never treat as a neighboring
     * inventory, mainly for compatibility with a third-party block that misbehaves under it. Upstream
     * defaults to one Actually Additions class it shipped with; that mod-compat entry is dropped here
     * since Forgeweave has no reason to assume that mod is installed -- the default is empty, same as
     * upstream's own {@code Config.craftingStationBlacklist} before its config file is first read.
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CRAFTING_STATION_BLACKLIST;

    /**
     * Content-family toggles (the content-family toggles ticket, maintainer decisions 2026-08-15).
     * Forgeweave's own surface, not a 1.12 port: upstream has no equivalent, and a pack that wants
     * only the smeltery, or only the harvest tools, has to delete recipes by hand there.
     *
     * <p>Semantics are <b>unobtainable, never unregistered</b>: an off family stops being
     * <em>assemblable</em>, its exclusive parts/patterns/casts stop being obtainable, and its
     * recipes vanish from JEI and its items from the creative tab -- but every item stays
     * registered, so a world that already contains one keeps loading and the tool keeps mining and
     * hitting. That is the same lookup-time filter {@link #ENABLE_CLAY_CASTS} uses, applied to a
     * whole roster, and it is what lets these be hot-reloadable with no {@code worldRestart()}.
     *
     * <p>Membership is derived, never listed: {@link dev.gkissel.forgeweave.tool.ToolConstants.Entry#category()}
     * says which family a tool belongs to, and
     * {@link dev.gkissel.forgeweave.menu.ContentFamilies} walks
     * {@link dev.gkissel.forgeweave.menu.ToolAssemblyRecipes#ENTRIES} for everything else, so a new
     * tool inherits its family's gate with no second table to update.
     */
    public static final ModConfigSpec.BooleanValue HARVEST_TOOLS;

    /** @see #HARVEST_TOOLS */
    public static final ModConfigSpec.BooleanValue MELEE_WEAPONS;

    /**
     * Registered now, with no behavior behind it yet: docs/SCOPE.md M3.5 is what adds the bows and
     * crossbows this would gate, and it wires this key up when it lands. Shipping the key early
     * keeps a pack's config file stable across that milestone instead of growing a new section.
     *
     * @see #HARVEST_TOOLS
     */
    public static final ModConfigSpec.BooleanValue RANGED_WEAPONS;

    /**
     * Registered now, with no behavior behind it yet -- docs/SCOPE.md M4 (armor) wires it up.
     *
     * @see #RANGED_WEAPONS
     */
    public static final ModConfigSpec.BooleanValue ARMOR;

    /**
     * Registered now, with no behavior behind it yet -- docs/SCOPE.md M5 (gadgets) wires it up.
     *
     * @see #RANGED_WEAPONS
     */
    public static final ModConfigSpec.BooleanValue GADGETS;

    /**
     * The smeltery as a whole: melting, entity melting, alloying and casting all stop resolving, and
     * the smeltery GUI says why. Its blocks stay registered and a formed structure keeps its fluids
     * -- see {@link #HARVEST_TOOLS} for the shared "unobtainable, never unregistered" rule.
     */
    public static final ModConfigSpec.BooleanValue SMELTERY;

    /**
     * Issue #847 (M6 epic #824, JC7 scope call): a global multiplier on the smeltery's per-tick melt
     * progress. Not a content-family toggle -- melting still resolves the same recipes, just faster or
     * slower -- but a pack operator reaches for it alongside {@link #SMELTERY}, so it is registered
     * here rather than opening a new section for one value.
     *
     * <p>Applied in {@link dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity#meltTick()} to
     * the whole-number heat step upstream's {@code TileHeatingStructure#heatItems} derives ({@code
     * heat / 100}), after that floor division so the default {@code 1.0} reproduces upstream's step
     * exactly rather than introducing rounding noise no pack asked for. The scaled result is rounded
     * to the nearest {@code int} ({@link Math#round(double)}); a step of {@code 0} (smeltery heat
     * under 100) stays {@code 0} at every multiplier, same as upstream's own heat floor.
     */
    public static final ModConfigSpec.DoubleValue MELT_SPEED_MULTIPLIER;

    /**
     * Everything that alters a tool at the Tool Station beyond repair and part exchange (maintainer
     * decision): generic modifier application (issue #105), embossing (#154) and fortification
     * (#271). Each is refused where it resolves, with a translatable reason the info panel takes
     * over with (#378), and their recipe categories are hidden from JEI.
     *
     * <p>Repair and part exchange stay available because neither changes what the tool <em>is</em>;
     * every modifier, embossment and fortification already on a tool likewise keeps working, since
     * nothing gated here touches an assembled stack. Only the act of applying a new one stops.
     */
    public static final ModConfigSpec.BooleanValue MODIFIERS;

    /**
     * Tool and armor leveling (docs/SCOPE.md M7, D-M7-3; issue #918), a port of Tinkers' Tool
     * Leveling. Off means the mechanic is fully inert -- no XP accrues on any path, and M7-5's
     * tooltip lines, chat line and chime stay silent. It does <b>not</b> revoke levels: a slot a
     * tool already earned keeps counting, because a flag flip that invalidated modifiers already
     * spent into that slot would be a save-corruption bug wearing a config's clothes.
     */
    public static final ModConfigSpec.BooleanValue TOOL_LEVELING;

    /**
     * Upstream {@code defaultBaseXP}: what a tool's first level costs before its own
     * {@link dev.gkissel.forgeweave.tool.ToolConstants.Entry#baseXpMultiplier()} is applied. Read
     * through {@link #defaultBaseXp()}, never {@code .get()}.
     */
    public static final ModConfigSpec.IntValue DEFAULT_BASE_XP;

    /**
     * Upstream {@code levelMultiplier}: what each further level multiplies the last one's cost by.
     * Upstream clamps a value below 2 back up to 2 after loading its config file; the floor is the
     * range's lower bound here instead, which refuses the value where it is typed rather than
     * silently rewriting it.
     */
    public static final ModConfigSpec.DoubleValue LEVEL_MULTIPLIER;

    /**
     * Upstream {@code maximumLevels}, with its off-by-one corrected (D-M7-9): a cap of N stops a
     * tool at exactly N, where upstream's {@code maximumLevels >= currentLevel} let it reach N + 1.
     * Zero or negative means no cap, which is the default.
     */
    public static final ModConfigSpec.IntValue MAXIMUM_LEVELS;

    /** Upstream {@code defaultBaseXP}'s own default -- see {@link #DEFAULT_BASE_XP}. */
    public static final int DEFAULT_BASE_XP_DEFAULT = 500;

    /**
     * Upstream {@code levelMultiplier}'s own default, which is also its floor -- see
     * {@link #LEVEL_MULTIPLIER}.
     */
    public static final double LEVEL_MULTIPLIER_FLOOR = 2.0D;

    /** {@link #MAXIMUM_LEVELS}'s default: no cap. */
    public static final int NO_LEVEL_CAP = -1;

    /** Upstream {@code genCobalt}: cobalt ore generates in the Nether. */
    public static final ModConfigSpec.BooleanValue GEN_COBALT;
    /** Upstream {@code cobaltRate}: approximate cobalt veins per Nether chunk. */
    public static final ModConfigSpec.IntValue COBALT_RATE;
    /** Upstream {@code genArdite}: ardite ore generates in the Nether. */
    public static final ModConfigSpec.BooleanValue GEN_ARDITE;
    /** Upstream {@code arditeRate}: approximate ardite veins per Nether chunk. */
    public static final ModConfigSpec.IntValue ARDITE_RATE;

    /**
     * Issue #839 (M6 epic #824, Track B): one grouped toggle for all twelve Track B ores
     * ({@link dev.gkissel.forgeweave.trackb.TrackBOre#ALL}), following {@code genCobalt}/{@code
     * genArdite}'s naming but -- per #839's own deliverable 3 -- one switch for the whole group
     * rather than one per ore; each ore's own vein count is fixed in its placed-feature JSON instead
     * of a per-ore config rate. See {@link dev.gkissel.forgeweave.worldgen.TrackBOrePlacement}.
     */
    public static final ModConfigSpec.BooleanValue GEN_TRACK_B_ORES;

    /** Upstream {@code generateSlimeIslands} (#449, parity audit T18). */
    public static final ModConfigSpec.BooleanValue GEN_SLIME_ISLANDS;
    /** Upstream {@code generateIslandsInSuperflat}. */
    public static final ModConfigSpec.BooleanValue GEN_ISLANDS_IN_SUPERFLAT;
    /**
     * Upstream {@code slimeIslandRate}: one chunk in this many carries an island. Islands are a
     * structure since #629, and a structure set's candidate grid cannot be re-spaced at runtime, so
     * this thins that grid rather than replacing it -- exact from
     * {@link dev.gkissel.forgeweave.worldgen.SlimeIslandStructure#GRID_DENSITY} upwards (upstream's
     * default of 730 included), and capped at the grid below it.
     */
    public static final ModConfigSpec.IntValue SLIME_ISLAND_RATE;
    /**
     * Upstream {@code magmaIslandRate} (#450, parity audit T19): one Nether chunk in this many
     * carries a magma island.
     */
    public static final ModConfigSpec.IntValue MAGMA_ISLAND_RATE;
    /**
     * Upstream {@code slimeIslandBlacklist}, whose numeric dimension ids ({@code -1, 1}) become the
     * modern named ones -- see {@code SlimeIslandStructure}.
     */
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SLIME_ISLAND_BLACKLIST;
    /**
     * Upstream {@code slimeIslandsOnlyGenerateInSurfaceWorlds}, whose name is inverted the same way
     * upstream's is: set it false to let islands into non-surface dimensions.
     */
    public static final ModConfigSpec.BooleanValue SLIME_ISLANDS_ONLY_IN_SURFACE_WORLDS;

    /**
     * One of the {@code content} flags, answering "on" whenever the spec is not loaded.
     *
     * <p>A {@code SERVER} spec exists only once a world is running, and three of the callers here
     * legitimately run outside one: the creative tab is built in the main menu, and both the casting
     * and melting recipe filters are exercised by unit tests that never stand a server up. The
     * fallback is deliberately the permissive one -- showing or resolving something a joined server
     * would then refuse is a far smaller surprise than hiding content because no server has spoken
     * yet. The older options above read {@code .get()} directly because every one of their call
     * sites is already inside a running world.
     */
    public static boolean enabled(ModConfigSpec.BooleanValue value) {
        return !SPEC.isLoaded() || value.get();
    }

    /**
     * {@link #CRAFT_CASTABLE_MATERIALS}, answering with upstream's {@code false} default whenever no
     * server has spoken. The permissive fallback {@link #enabled} uses would be the wrong way round
     * here: this option <em>adds</em> crafts rather than gating them, so falling back to "on" would
     * make JEI (which builds its recipe list outside any world) advertise every metal as Part
     * Builder craftable on a server that refuses it -- the exact surprise {@link #enabled} exists to
     * avoid, mirrored.
     */
    public static boolean craftCastableMaterials() {
        return SPEC.isLoaded() && CRAFT_CASTABLE_MATERIALS.get();
    }

    /**
     * {@link #DEFAULT_BASE_XP}, answering with its own default whenever the spec is not loaded --
     * the same guard {@link #enabled} exists for, and for the same reason: the leveling curve is
     * exercised by unit tests that never stand a server up. The three leveling numbers have no
     * permissive-versus-strict question to settle; the default value simply is the right answer when
     * no server has spoken.
     */
    public static int defaultBaseXp() {
        return SPEC.isLoaded() ? DEFAULT_BASE_XP.get() : DEFAULT_BASE_XP_DEFAULT;
    }

    /** @see #defaultBaseXp() */
    public static double levelMultiplier() {
        return SPEC.isLoaded() ? LEVEL_MULTIPLIER.get() : LEVEL_MULTIPLIER_FLOOR;
    }

    /** @see #defaultBaseXp() */
    public static int maximumLevels() {
        return SPEC.isLoaded() ? MAXIMUM_LEVELS.get() : NO_LEVEL_CAP;
    }

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
        CHESTS_KEEP_INVENTORY = builder
                .comment("Pattern and Part chests keep their inventory when harvested.")
                .define("chestsKeepInventory", true);
        SPAWN_WITH_BOOK = builder
                .comment("Players who enter the world for the first time get the guide book.")
                .define("spawnWithBook", true);
        ORE_TO_INGOT_RATIO = builder
                .comment("How many ingots one ore melts into at the top smeltery core tier. Lower tiers scale",
                        "down from this by their own yield multiplier. Cannot go below 1.")
                .defineInRange("oreToIngotRatio", ORE_TO_INGOT_BASELINE, 1.0D, 64.0D);
        OBSIDIAN_ALLOY = builder
                .comment("Allows the creation of molten obsidian in the smeltery from lava and water.")
                .define("obsidianAlloy", true);
        ADD_FLINT_RECIPE = builder
                .comment("Adds a crafting-table recipe that turns 3 gravel into a flint.")
                .define("addFlintRecipe", true);
        MATCH_VANILLA_SLIMEBLOCK = builder
                .comment("Crafting nine slime balls of mixed colours gives a pink slime block instead of",
                        "a vanilla slime block.")
                .define("matchVanillaSlimeblock", false);
        ENABLE_CLAY_CASTS = builder
                .comment("Allows single-use clay casts to be moulded from molten clay and cast through.")
                .define("enableClayCasts", true);
        CRAFT_CASTABLE_MATERIALS = builder
                .comment("Allows the Part Builder to craft parts from materials that are meant to be cast",
                        "in the Smeltery (every metal). Off by default: a metal part comes from a cast.")
                .define("craftCastableMaterials", false);
        CRAFTING_STATION_BLACKLIST = builder
                .comment("Registry names or block-entity classnames that a station's side-inventory panel",
                        "should never connect to. Mainly for compatibility.")
                .defineListAllowEmpty("craftingStationBlacklist", List.<String>of(), value -> value instanceof String);

        builder.comment("Content family toggles. A family that is off cannot be assembled or obtained,",
                        "its recipes are hidden from JEI and its items from the creative tab, and the",
                        "parts, patterns and casts that serve only it become unobtainable too. Nothing is",
                        "unregistered: items already in a world keep working, and every option here takes",
                        "effect the moment it is reloaded.")
                .push("content");
        HARVEST_TOOLS = builder
                .comment("If true, harvest tools (pickaxe, shovel, hatchet, mattock, kama, hammer, excavator,",
                        "lumber axe, scythe, vein hammer) can be assembled and obtained.")
                .define("harvestTools", true);
        MELEE_WEAPONS = builder
                .comment("If true, melee weapons (broadsword, longsword, rapier, battlesign, frying pan, dagger,",
                        "battleaxe, scimitar, katana, warmace, cleaver) can be assembled and obtained.")
                .define("meleeWeapons", true);
        RANGED_WEAPONS = builder
                .comment("If true, ranged weapons (shortbow) can be assembled and obtained.")
                .define("rangedWeapons", true);
        ARMOR = builder
                .comment("If true, armor can be assembled and obtained. Reserved: Forgeweave ships no armor yet,",
                        "so this has no effect until it lands.")
                .define("armor", true);
        GADGETS = builder
                .comment("If true, gadgets can be assembled and obtained. Reserved: Forgeweave ships no gadgets",
                        "yet, so this has no effect until they land.")
                .define("gadgets", true);
        SMELTERY = builder
                .comment("If true, the smeltery melts, alloys and casts. With this off its blocks stay placeable",
                        "but no melting, alloying or casting recipe resolves, and the smeltery GUI says so.")
                .define("smeltery", true);
        MELT_SPEED_MULTIPLIER = builder
                .comment("Multiplies the smeltery's per-tick melt progress. 1.0 matches unmodified melt speed;",
                        "2.0 halves the number of ticks a melt takes, 0.5 doubles it. Does not change what",
                        "temperature a recipe requires, only how fast progress accumulates once melting.")
                .defineInRange("meltSpeedMultiplier", 1.0D, 0.01D, 100.0D);
        MODIFIERS = builder
                .comment("If true, modifiers, embossments and fortifications can be applied to tools at the Tool",
                        "Station. Repair and part exchange are unaffected, and anything already on a tool keeps",
                        "working either way -- only applying a new one is refused.")
                .define("modifiers", true);
        // M7 (issue #918). The three numbers are not content-family toggles, but a pack operator
        // reaches for them alongside toolLeveling, so they sit beside it rather than opening a new
        // section for three values -- the same call meltSpeedMultiplier made above.
        TOOL_LEVELING = builder
                .comment("If true, tools and armor gain XP from being used and earn a modifier slot per level.",
                        "With this off nothing accrues XP and no level-up message, sound or tooltip appears.",
                        "Slots already earned keep counting either way, so no modifier already applied is lost.")
                .define("toolLeveling", true);
        DEFAULT_BASE_XP = builder
                .comment("How much XP a tool's first level costs. The area-of-effect tools (hammer, excavator,",
                        "lumber axe, scythe, vein hammer) cost nine times this, since they break nine blocks",
                        "at a time.")
                .defineInRange("defaultBaseXP", DEFAULT_BASE_XP_DEFAULT, 1, Integer.MAX_VALUE);
        LEVEL_MULTIPLIER = builder
                .comment("How much the XP cost multiplies per level, minimum 2. Note that the first two levels",
                        "both cost the base amount; the multiplier starts applying from the third.")
                .defineInRange("levelMultiplier", LEVEL_MULTIPLIER_FLOOR, LEVEL_MULTIPLIER_FLOOR, 100.0D);
        MAXIMUM_LEVELS = builder
                .comment("The highest level a tool can reach. Zero or lower means no limit.")
                .defineInRange("maximumLevels", NO_LEVEL_CAP, NO_LEVEL_CAP, Integer.MAX_VALUE);
        builder.pop();

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
        // #839 -- M6 Track B's own ore family (epic #824): one grouped switch, not one per ore.
        GEN_TRACK_B_ORES = builder
                .comment("If true, Track B's self-contained ore ladder (fulmenite, duskspar, ",
                        "voltcinder, murkiron, hardcinder, nightshale, warspar, hollowstone, resonite, ",
                        "starfall_stone, voidglass) generates in the world.")
                .define("genTrackBOres", true);
        // #449 (parity audit T18) and #450 (T19) -- upstream 1.12 Config's six slime island options,
        // verbatim names and defaults except the blacklist's dimension ids (see SlimeIslandStructure).
        GEN_SLIME_ISLANDS = builder
                .comment("If true slime islands will generate.")
                .define("generateSlimeIslands", true);
        GEN_ISLANDS_IN_SUPERFLAT = builder
                .comment("If true slime islands generate in superflat worlds.")
                .define("generateIslandsInSuperflat", false);
        SLIME_ISLAND_RATE = builder
                .comment("One in every X chunks will contain a slime island. Values below 81 are capped",
                        "at one in 81, the density of the island structure set's candidate grid.")
                .defineInRange("slimeIslandRate", 730, 0, 100000);
        MAGMA_ISLAND_RATE = builder
                .comment("One in every X chunks will contain a magma island in the nether. Values below 81",
                        "are capped at one in 81, the density of the island structure set's candidate grid.")
                .defineInRange("magmaIslandRate", 100, 0, 100000);
        SLIME_ISLAND_BLACKLIST = builder
                .comment("Prevents generation of slime islands in the listed dimensions.")
                .defineListAllowEmpty("slimeIslandBlacklist",
                        List.<String>of("minecraft:the_nether", "minecraft:the_end"),
                        value -> value instanceof String id && ResourceLocation.tryParse(id) != null);
        SLIME_ISLANDS_ONLY_IN_SURFACE_WORLDS = builder
                .comment("If false, slime islands only generate in dimensions which are of type surface. This",
                        "means they won't generate in modded cave dimensions. Note that the name of this property",
                        "is inverted: it must be set to false to prevent slime islands from generating in",
                        "non-surface dimensions.")
                .define("slimeIslandsOnlyGenerateInSurfaceWorlds", true);
        builder.pop();

        SPEC = builder.build();
    }

    private ForgeweaveConfig() {}
}
