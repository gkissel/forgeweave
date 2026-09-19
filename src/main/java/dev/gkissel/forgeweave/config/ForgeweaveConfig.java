package dev.gkissel.forgeweave.config;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.common.ModConfigSpec;

import dev.gkissel.forgeweave.block.EnergizedHeat;
import dev.gkissel.forgeweave.compat.mekanism.ForgeweaveMekanismCompat;
import dev.gkissel.forgeweave.compat.mekanism.modules.MekanismGearModules;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * Forgeweave's gameplay config (docs/SCOPE.md M3.4-7 issue #276): the subset of upstream 1.12's
 * {@code common/config/Config.java} that has a behavior site here, each keeping upstream's own
 * default, plus Forgeweave's own content-family and compat toggles. Purely visual preferences live
 * in {@link ForgeweaveClientConfig} instead -- see that class for the split.
 *
 * <p>Registered as {@code SERVER}-type configs ({@code Forgeweave#Forgeweave}), not {@code COMMON}:
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
 * asks to prefer. The upstream options that gate <em>registration</em> rather than behavior are
 * handled the same way -- {@link #ENABLE_CLAY_CASTS}'s recipes are filtered at lookup time
 * (issue #292) -- so no conditional-recipe machinery is needed anywhere.
 *
 * <h2>Four files, not one (D-M8-8, issue #968)</h2>
 *
 * <p>One flat {@code forgeweave-server.toml} had grown past the point where a pack operator could
 * find anything in it, so the spec is split into four, registered under {@code config/forgeweave/}:
 * {@link #GENERAL_SPEC} ({@code general-server.toml}), {@link #CONTENT_SPEC}
 * ({@code content-server.toml}), {@link #COMPAT_SPEC} ({@code compat-server.toml}) and
 * {@link #WORLDGEN_SPEC} ({@code worldgen-server.toml}). NeoForge registers one spec per file, so
 * these are four {@code registerConfig} calls rather than one spec that happens to be written out
 * in pieces.
 *
 * <p>The split follows the sections the options already grouped into rather than the compat /
 * smeltery / leveling / materials / worldgen shape D-M8-8 sketched, which the options did not bear
 * out: there is no materials option at all (materials are never toggled, D-M8-5), and the smeltery
 * and leveling keys are members of the {@code content} family roster
 * ({@link #SMELTERY}, {@link #TOOL_LEVELING}), so lifting them into files of their own would split
 * a family from its siblings. Every option therefore keeps the path it already had inside its
 * section, and the eleven that used to sit at the top level move under a new {@code general}
 * section so that each file has exactly one section to carry its header comment.
 *
 * <p>Two standing rules ride the header comments, stated there so later issues inherit them.
 * Everything numeric is a config value, not a constant in Java. And every integration beyond
 * materials gets a toggle in {@code compat}.
 *
 * <p><b>Migration.</b> An existing {@code config/forgeweave-server.toml} is read once and its values
 * are split into the four new files before anything is registered -- see
 * {@link ForgeweaveConfigMigration}. Silently resetting a pack's tuned config would be the config
 * equivalent of eating a save, so the old file is carried over rather than abandoned, and it is
 * renamed to {@code forgeweave-server.toml.migrated} afterwards instead of deleted.
 *
 * <p>{@link ForgeweaveClientConfig} stays where it is, at {@code config/forgeweave-client.toml}: it
 * is a single short spec of display preferences that is not growing, and D-M8-8 is about the server
 * file.
 */
public final class ForgeweaveConfig {
    /**
     * The ore-to-ingot ratio {@link dev.gkissel.forgeweave.block.SmelteryCore}'s own multipliers are
     * already calibrated against: at this value the config is a no-op and a Nether Core yields
     * exactly upstream's 2 ingots per ore. See {@link #ORE_TO_INGOT_RATIO}.
     */
    public static final double ORE_TO_INGOT_BASELINE = 2.0D;

    /** {@code config/forgeweave/general-server.toml} -- the {@code general} section. */
    public static final ModConfigSpec GENERAL_SPEC;

    /** {@code config/forgeweave/content-server.toml} -- the {@code content} section. */
    public static final ModConfigSpec CONTENT_SPEC;

    /** {@code config/forgeweave/compat-server.toml} -- the {@code compat} section. */
    public static final ModConfigSpec COMPAT_SPEC;

    /** {@code config/forgeweave/worldgen-server.toml} -- the {@code worldgen} section. */
    public static final ModConfigSpec WORLDGEN_SPEC;

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

    /**
     * Compat toggles (D-M8-5, maintainer decision 2026-09-04): one per integration beyond
     * materials, so a pack can drop a bridge to another mod without dropping the mod. They live in
     * {@code compat} rather than {@code content} because {@code content} means "this family of
     * Forgeweave items exists" while these mean "this bridge to another mod exists".
     *
     * <p>Semantics are <b>inert, never destructive</b>, which is D-M7-3's rule applied to compat:
     * an off toggle stops the integration answering, and nothing else. Gear that already carries a
     * fusion upgrade or an installed module keeps every component it has, untouched, and works
     * again the moment the toggle returns. A flag flip that silently discarded that state would be
     * a save-corruption bug wearing a config's clothes.
     *
     * <p>Every one of these is read where the integration answers rather than where it registers,
     * which is the same runtime-check convention {@link #ADD_FLINT_RECIPE} explains and the only
     * one a {@code SERVER} spec can honour: a serializer, a capability and an overlay plugin are
     * all registered during mod loading, and a {@code SERVER} config does not exist until a world
     * does. Each gate therefore sits at the earliest point the integration is actually asked
     * anything -- see the PR body for the site chosen per toggle.
     *
     * <p>This one: Forgeweave's Draconic Evolution fusion upgrade ladder
     * ({@code compat.draconic.FusionUpgradeRecipe}). Off means no upgrade row matches, so Draconic
     * Evolution's fusion crafting multiblock finds no Forgeweave recipe and upgrades nothing.
     */
    public static final ModConfigSpec.BooleanValue DRACONIC_FUSION;

    /**
     * Draconic Evolution module hosting ({@code compat.draconic.modules}). Off means no Forgeweave
     * stack answers {@code DECapabilities.Host.ITEM}, so Draconic Evolution's module screen does not
     * open on Forgeweave gear and no module effect is granted.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue DRACONIC_MODULES;

    /**
     * The Jade and WTHIT block overlays ({@code dev.gkissel.forgeweave.jade} and
     * {@code dev.gkissel.forgeweave.wthit}). Off means every Forgeweave provider answers empty and
     * adds no tooltip line to either overlay.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue OVERLAYS;

    /**
     * The KubeJS trait binding ({@code kubejs.ForgeweaveKubeJSPlugin}). Off means a trait a startup
     * script registered resolves to nothing, so a material naming it behaves as if the id had no
     * implementation -- which is what a datapack material naming an unknown trait already does.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue KUBEJS_TRAITS;

    /**
     * Create's goggle overlays firing for a helmet carrying {@code forgeweave:goggles} (issue
     * #1007). Off means Create's own overlays ignore Forgeweave helmets; the modifier stays on the
     * helmet and works again the moment the toggle returns.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue CREATE_GOGGLES;

    /**
     * Apotheosis gem sockets (issue #969, {@code compat.apotheosis.ApotheosisSockets#enabled}). Off
     * means no socket can be added and no gem seated, and a seated gem grants nothing; the sockets
     * already on a stack keep their contents and grant again when the toggle returns.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue APOTHEOSIS_SOCKETS;

    /**
     * Issue #996 (D-M8-17). Covers only {@code surgebound}'s application recipes and effect, never
     * the six Powah material presets: D-M8-5 is explicit that Track A material presets are never
     * toggled, so uraninite, energised_steel and the four crystals stay active whenever Powah's own
     * item exists regardless of this flag.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue POWAH_MODIFIERS;

    /**
     * Issue #997 (D-M8-18). Covers the Occultism ritual recipe type and the crushing and miner rows,
     * never the iesnium, silver or spirit attuned gem presets: D-M8-5 keeps Track A material presets
     * off every toggle, so all three stay active whenever Occultism's own item exists.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue OCCULTISM_RITUALS;

    /**
     * Issue #998 (D-M8-19). Covers the Allthemodium tier-equivalence half that has a genuine runtime
     * hook: {@code TrackBOrePlacement}'s mining-dimension gate. The tag equivalence itself (both
     * directions) is existence-gated only, the same as every Track A preset (D-M8-5) -- a live
     * {@code SERVER} config value has no site in a {@code neoforge:conditions} block or in a static
     * tag file (see the PR body), so there is nothing this flag could switch off there. Off here
     * means Track B's ore family stops generating in {@code allthemodium:mining}; ore already
     * generated in an existing chunk is untouched, worldgen is not retroactive.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue ALLTHEMODIUM_TIERS;

    /**
     * Issue #998 (D-M8-19). The one deliberate exception to "material presets are never toggled"
     * (D-M8-5): Elementarium's presets are <em>generated</em> from its {@code c:ingots/*} tag family
     * rather than hand-authored, so a pack that dislikes the interpolation needs a way out that is
     * not hand-editing generated JSON. Read by {@link ForgeweaveConfigCondition}
     * ({@code forgeweave:compat_toggle}, shared with #995's four processing-mod toggles), the
     * existence condition every generated Elementarium material carries alongside {@code
     * neoforge:mod_loaded}. Off means none of those materials register -- the same save-compat shape
     * any other
     * existence-gated Track A preset already has if its provider mod is removed (Material.java's own
     * javadoc): a tool built from one keeps its stored part components, but the material record they
     * point at no longer resolves. Turning the toggle back on restores it with no further action.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue ELEMENTARIUM_MATERIALS;

    /** The fraction of the tool's trait-derived FE capacity {@code surgebound} adds per level (I-IV). */
    public static final ModConfigSpec.DoubleValue SURGEBOUND_CAPACITY_PER_LEVEL;
    /** The fraction of the tool's base mining speed {@code surgebound} adds per level (I-IV). */
    public static final ModConfigSpec.DoubleValue SURGEBOUND_MINING_SPEED_PER_LEVEL;
    /** What the nitro step (level V) multiplies {@link #SURGEBOUND_CAPACITY_PER_LEVEL} by instead of adding a fifth flat step. */
    public static final ModConfigSpec.DoubleValue SURGEBOUND_NITRO_CAPACITY_MULTIPLIER;
    /** What the nitro step (level V) multiplies {@link #SURGEBOUND_MINING_SPEED_PER_LEVEL} by instead of adding a fifth flat step. */
    public static final ModConfigSpec.DoubleValue SURGEBOUND_NITRO_MINING_SPEED_MULTIPLIER;

    /** {@link #SURGEBOUND_CAPACITY_PER_LEVEL}'s own default -- D-M8-17's "+25% energy capacity". */
    public static final double SURGEBOUND_CAPACITY_PER_LEVEL_DEFAULT = 0.25D;
    /** {@link #SURGEBOUND_MINING_SPEED_PER_LEVEL}'s own default -- D-M8-17's "+5% mining speed". */
    public static final double SURGEBOUND_MINING_SPEED_PER_LEVEL_DEFAULT = 0.05D;
    /** {@link #SURGEBOUND_NITRO_CAPACITY_MULTIPLIER}'s own default -- D-M8-17's "nitro doubles both". */
    public static final double SURGEBOUND_NITRO_CAPACITY_MULTIPLIER_DEFAULT = 2.0D;
    /** {@link #SURGEBOUND_NITRO_MINING_SPEED_MULTIPLIER}'s own default -- D-M8-17's "nitro doubles both". */
    public static final double SURGEBOUND_NITRO_MINING_SPEED_MULTIPLIER_DEFAULT = 2.0D;

    /**
     * Issue #995 (D-M8-12, D-M8-16): Create's heated mixer, crushing wheels and mechanical press
     * carrying Forgeweave's generated recipe JSON (the four basic alloys, Track B ore crushing, and
     * ingot-to-plate pressing). Off means {@link ForgeweaveConfigCondition#COMPAT_TOGGLE} reads false
     * for {@code "createRecipes"}, so none of those rows resolve; Forgeweave's own items and tags are
     * untouched either way, so a modpack loses nothing by flipping this and gains the rows back the
     * moment it flips back and reloads.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue CREATE_RECIPES;

    /**
     * Issue #995 (D-M8-12, D-M8-16): Immersive Engineering's arc furnace, crusher and metal press
     * carrying the same generated rows as {@link #CREATE_RECIPES}, for Immersive Engineering's recipe
     * types instead of Create's.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue IMMERSIVE_ENGINEERING_RECIPES;

    /**
     * Issue #995 (D-M8-12, D-M8-16): EnderIO's alloy smelter and SAG mill carrying the basic alloys
     * and Track B ore crushing rows.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue ENDER_IO_RECIPES;

    /**
     * Issue #995 (D-M8-13): Forgeweave's molten fluids registered as Powah thermo generator heat
     * sources through {@code powah:heat_source}'s fluid data map. Off means
     * {@link ForgeweaveConfigCondition#COMPAT_TOGGLE} reads false for {@code "powahHeatSources"} on
     * every entry Forgeweave contributes, so a thermo generator no longer burns them; the data map
     * entries Powah ships for its own fluids are untouched either way.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue POWAH_HEAT_SOURCES;

    /**
     * Datapack modifier definitions (issue #973, {@code forgeweave:modifier_definition}). Off means
     * a pack-defined modifier id resolves to nothing, so a tool carrying one behaves as if the id
     * had no implementation -- which is what a tool carrying an unknown modifier already does. The
     * entry keeps its id and its level either way and acts again when the toggle returns.
     *
     * <p>Unlike #995's four toggles above, this one needs no {@link ForgeweaveConfigCondition}: it is
     * read at lookup, on a running server well after the config exists, rather than while a datapack
     * is being loaded. See {@code ForgeweaveModifiers#datapackModifier}.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue MODIFIER_DEFINITIONS;

    /**
     * Issue #999 (D-M8-20). Covers the Mystical Agriculture crop registrations and the augment seam
     * that makes Forgeweave gear {@code ITinkerable}, never the Mystical Agriculture material
     * presets: D-M8-5 is explicit that Track A presets are never toggled, so prosperity, soulium,
     * the essence ladder and insanium stay active whenever their own item exists.
     *
     * <p>Read at the point the integration answers, like every other flag here, even though what the
     * integration does is decide a class: {@code compat.CompatItems} branches on whether Mystical
     * Agriculture is <em>installed</em>, which is a load-time fact a {@code SERVER} spec cannot
     * contribute to (#1024), and this flag then zeroes the augment slot count and the tinkerable tier
     * the registered item reports. Off is therefore inert and needs no restart: nothing can be
     * installed, nothing installed grants anything, and Mystical Agriculture's own augment component
     * stays exactly where it is -- Forgeweave neither copies nor migrates that data, the rule JC-D set
     * for Apotheosis affixes.
     *
     * @see #DRACONIC_FUSION
     */
    public static final ModConfigSpec.BooleanValue MYSTICAL_AGRICULTURE_AUGMENTS;

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
     * The energized tank (docs/SCOPE.md M8, D-M8-11; issue #972), in the same {@code compat} section
     * as every other integration toggle. Off means the block goes inert, not unregistered: it
     * contributes no heat and its crafting recipe stops resolving, but a tank already standing in a
     * world still loads and keeps its fuel sample, its energy buffer and its overdrive setting, so
     * turning the toggle back on restores it intact. A server config is not loaded when registries
     * freeze, so "does not register" is not a state this toggle could reach; dormant is the same
     * inert-not-destructive contract the rest of D-M8-5 spells out.
     */
    public static final ModConfigSpec.BooleanValue ENERGIZED_TANK;

    /**
     * Apotheosis loot affixes on Forgeweave gear (D-M8-5; issue #970), read through
     * {@code ApotheosisAffixes.affixesEnabled()} and nowhere else. Affixability itself is Apotheosis'
     * own predicate walk over the item, with no seam a mod can veto at runtime, so what this reaches
     * is the one runtime point Forgeweave owns: off, no loot roll assembles a Forgeweave tool, and a
     * tool with no parts is a category Apotheosis declines. Nothing stored is touched either way --
     * Forgeweave never reads, copies or writes affix state (JC-D), so a tool that already carries it
     * keeps every component with the toggle in either position.
     */
    public static final ModConfigSpec.BooleanValue APOTHEOSIS_AFFIXES;

    /**
     * Apotheosis enchanting on Forgeweave gear (D-M8-5; issue #970). This does <em>not</em> replace
     * {@code allowVanillaEnchanting}: enchanting needs both, and the gameplay flag stays exactly what
     * it was. Off refuses Forgeweave gear at the enchanting table and the anvil the same way
     * {@code allowVanillaEnchanting = false} refuses it, stripping nothing -- an already enchanted
     * tool keeps its enchantments and keeps applying them.
     */
    public static final ModConfigSpec.BooleanValue APOTHEOSIS_ENCHANTING;

    /**
     * Mekanism module containers on Forgeweave gear (D-M8-5; issue #993), read through
     * {@code MekanismGearModules.modulesEnabled()} and nowhere else. Off makes the container inert,
     * never absent: every module effect answers its neutral value, the radiation shielding capability
     * answers nothing, and the module screen stops opening because the capability provider answers
     * null. Two things it deliberately cannot reach, both committed before a server config exists:
     * the module container's default data component, added on
     * {@code ModifyDefaultComponentsEvent}, and the inter-mod message that tells Mekanism which items
     * accept which module roster. Neither is visible on a tool nobody takes to a Modification Station,
     * and nothing stored is ever touched -- a tool carrying installed modules keeps Mekanism's own
     * component untouched and starts working again the moment the toggle returns, which is D-M7-3's
     * rule applied to compat. The {@code atomic_matter_alloy} material itself is not toggled at all:
     * D-M8-5 exempts materials, because a preset that vanishes takes a metal out of a world built
     * with it.
     */
    public static final ModConfigSpec.BooleanValue MEKANISM_MODULES;

    /** FE one block break costs while a powered Mekanism mining module is installed (#993). */
    public static final ModConfigSpec.IntValue MEKANISM_ENERGY_PER_BLOCK;

    /** FE the MekaSuit absorption modules spend per point of damage they take off a blow (#993). */
    public static final ModConfigSpec.IntValue MEKANISM_ENERGY_PER_ABSORBED_POINT;

    /** How many blocks one Mekanism vein mining swing breaks at most (#993). */
    public static final ModConfigSpec.IntValue MEKANISM_VEIN_MINING_MAX_BLOCKS;

    /** How many of Mekanism's own atomic alloys one {@code atomic_matter_alloy} ingot takes (#993). */
    public static final ModConfigSpec.IntValue MEKANISM_NUCLEOSYNTHESIZING_ALLOY_COUNT;

    /** How much antimatter, in mB, one {@code atomic_matter_alloy} ingot takes (#993). */
    public static final ModConfigSpec.IntValue MEKANISM_NUCLEOSYNTHESIZING_ANTIMATTER;

    /** How long, in ticks, the nucleosynthesizer takes over one ingot (#993). */
    public static final ModConfigSpec.IntValue MEKANISM_NUCLEOSYNTHESIZING_DURATION;

    /** FE one Mekanism teleportation jump costs (#994). */
    public static final ModConfigSpec.IntValue MEKANISM_ENERGY_PER_TELEPORT;

    /** FE a Mekanism jetpack or gravitational modulating unit spends per tick of flight (#994). */
    public static final ModConfigSpec.IntValue MEKANISM_ENERGY_PER_FLIGHT_TICK;

    /** How far, in blocks, a Mekanism teleportation unit moves the player (#994). */
    public static final ModConfigSpec.IntValue MEKANISM_TELEPORT_MAX_DISTANCE;

    /**
     * The fraction of incoming radiation one level of {@code forgeweave:rayward} blocks (#994). Four
     * levels at the default 0.25 come to exactly 1.0, which is the whole set shielding completely.
     */
    public static final ModConfigSpec.DoubleValue RADIATION_SHIELDING_PER_LEVEL;

    /** How much Forge Energy one energized tank's buffer holds (#972). */
    public static final ModConfigSpec.IntValue ENERGIZED_TANK_BUFFER;

    /**
     * The {@code rfPerMeltTickBase} of the energized tank's cost, {@code base x temperature /
     * divisor} per melt tick -- see {@link EnergizedHeat#costPerMeltTick}.
     */
    public static final ModConfigSpec.IntValue ENERGIZED_TANK_RF_PER_MELT_TICK_BASE;

    /** The divisor of that same cost (#972). */
    public static final ModConfigSpec.IntValue ENERGIZED_TANK_TEMPERATURE_DIVISOR;

    /** What overdrive multiplies an energized tank's energy cost per melt tick by (#972). */
    public static final ModConfigSpec.DoubleValue ENERGIZED_TANK_OVERDRIVE_COST;

    /** What overdrive multiplies the smeltery's melt progress per melt tick by (#972). */
    public static final ModConfigSpec.DoubleValue ENERGIZED_TANK_OVERDRIVE_PROGRESS;

    /** {@link #ENERGIZED_TANK_BUFFER}'s default: a hair over two minutes of melting at lava's heat. */
    public static final int ENERGIZED_TANK_BUFFER_DEFAULT = 100_000;

    /** {@link #ENERGIZED_TANK_RF_PER_MELT_TICK_BASE}'s default. */
    public static final int ENERGIZED_TANK_RF_PER_MELT_TICK_BASE_DEFAULT = 100;

    /** {@link #ENERGIZED_TANK_TEMPERATURE_DIVISOR}'s default. */
    public static final int ENERGIZED_TANK_TEMPERATURE_DIVISOR_DEFAULT = 1000;

    /** The default for both overdrive factors, so overdrive is neither a discount nor a penalty. */
    public static final double ENERGIZED_TANK_OVERDRIVE_DEFAULT = 2.0D;

    /**
     * {@link #ENERGIZED_TANK_BUFFER}, answering with its own default whenever no server has spoken
     * -- {@link #defaultBaseXp()}'s reasoning, for the same reason: {@code EnergizedHeatTest} and
     * JEI's own recipe list are both built without a running server.
     */
    public static int energizedTankBuffer() {
        return loaded() ? ENERGIZED_TANK_BUFFER.get() : ENERGIZED_TANK_BUFFER_DEFAULT;
    }

    /** @see #energizedTankBuffer() */
    public static int energizedTankRfPerMeltTickBase() {
        return loaded() ? ENERGIZED_TANK_RF_PER_MELT_TICK_BASE.get()
                : ENERGIZED_TANK_RF_PER_MELT_TICK_BASE_DEFAULT;
    }

    /** @see #energizedTankBuffer() */
    public static int energizedTankTemperatureDivisor() {
        return loaded() ? ENERGIZED_TANK_TEMPERATURE_DIVISOR.get()
                : ENERGIZED_TANK_TEMPERATURE_DIVISOR_DEFAULT;
    }

    /** @see #energizedTankBuffer() */
    public static double energizedTankOverdriveCost() {
        return loaded() ? ENERGIZED_TANK_OVERDRIVE_COST.get() : ENERGIZED_TANK_OVERDRIVE_DEFAULT;
    }

    /** @see #energizedTankBuffer() */
    public static double energizedTankOverdriveProgress() {
        return loaded() ? ENERGIZED_TANK_OVERDRIVE_PROGRESS.get() : ENERGIZED_TANK_OVERDRIVE_DEFAULT;
    }

    /**
     * One of the {@code content} flags, answering "on" whenever the spec is not loaded.
     *
     * <p>A {@code SERVER} spec exists only once a world is running, and three of the callers here
     * legitimately run outside one: the creative tab is built in the main menu, and both the casting
     * and melting recipe filters are exercised by unit tests that never stand a server up. The
     * fallback is deliberately the permissive one -- showing or resolving something a joined server
     * would then refuse is a far smaller surprise than hiding content because no server has spoken
     * yet. Options with no permissive reading go through {@link #read} instead, which answers with
     * the declared default.
     */
    public static boolean enabled(ModConfigSpec.BooleanValue value) {
        return !loaded() || value.get();
    }

    /**
     * Whether a server has spoken, i.e. whether every one of the four {@code SERVER} files is
     * loaded. All four are opened by the same {@code ConfigTracker#loadConfigs(SERVER, ...)} call
     * and delivered to a joining client by the same configuration task, so in practice they are
     * loaded together or not at all; the conjunction is what makes that safe to rely on, since
     * {@code .get()} on a value whose own file has not loaded throws.
     */
    public static boolean loaded() {
        return GENERAL_SPEC.isLoaded() && CONTENT_SPEC.isLoaded() && COMPAT_SPEC.isLoaded()
                && WORLDGEN_SPEC.isLoaded();
    }

    /**
     * Any option's value, answering with its declared default whenever the spec is not loaded (issue
     * #1023). Every read outside this package goes through here or through one of the named helpers:
     * a {@code SERVER} spec exists only while a world is running, but other mods walk recipes and
     * query items before that (Replication calls {@code getResultItem} on every recipe from a
     * resource reload listener), and a raw {@code .get()} there throws and takes the client down.
     * {@code ConfigReadAuditTest} fails the build on a new raw read.
     *
     * <p>Takes an option from any of the four specs, which is why the guard is {@link #loaded()}
     * rather than one file's own {@code isLoaded()}: this method cannot tell which file the value it
     * was handed came from, and the all-four probe is right for every one of them.
     */
    public static <T> T read(ModConfigSpec.ConfigValue<T> value) {
        return loaded() ? value.get() : value.getDefault();
    }

    /** @see #MEKANISM_ENERGY_PER_BLOCK */
    public static int mekanismEnergyPerBlock() {
        return read(MEKANISM_ENERGY_PER_BLOCK);
    }

    /** @see #MEKANISM_ENERGY_PER_ABSORBED_POINT */
    public static int mekanismEnergyPerAbsorbedPoint() {
        return read(MEKANISM_ENERGY_PER_ABSORBED_POINT);
    }

    /** @see #MEKANISM_VEIN_MINING_MAX_BLOCKS */
    public static int mekanismVeinMiningMaxBlocks() {
        return read(MEKANISM_VEIN_MINING_MAX_BLOCKS);
    }

    /** @see #MEKANISM_NUCLEOSYNTHESIZING_ALLOY_COUNT */
    public static int mekanismNucleosynthesizingAlloyCount() {
        return read(MEKANISM_NUCLEOSYNTHESIZING_ALLOY_COUNT);
    }

    /** @see #MEKANISM_NUCLEOSYNTHESIZING_ANTIMATTER */
    public static int mekanismNucleosynthesizingAntimatter() {
        return read(MEKANISM_NUCLEOSYNTHESIZING_ANTIMATTER);
    }

    /** @see #MEKANISM_NUCLEOSYNTHESIZING_DURATION */
    public static int mekanismNucleosynthesizingDuration() {
        return read(MEKANISM_NUCLEOSYNTHESIZING_DURATION);
    }

    /** @see #MEKANISM_ENERGY_PER_TELEPORT */
    public static int mekanismEnergyPerTeleport() {
        return read(MEKANISM_ENERGY_PER_TELEPORT);
    }

    /** @see #MEKANISM_ENERGY_PER_FLIGHT_TICK */
    public static int mekanismEnergyPerFlightTick() {
        return read(MEKANISM_ENERGY_PER_FLIGHT_TICK);
    }

    /** @see #MEKANISM_TELEPORT_MAX_DISTANCE */
    public static int mekanismTeleportMaxDistance() {
        return read(MEKANISM_TELEPORT_MAX_DISTANCE);
    }

    /** @see #RADIATION_SHIELDING_PER_LEVEL */
    public static double radiationShieldingPerLevel() {
        return read(RADIATION_SHIELDING_PER_LEVEL);
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
        return loaded() && CRAFT_CASTABLE_MATERIALS.get();
    }

    /**
     * {@link #DEFAULT_BASE_XP}, answering with its own default whenever the spec is not loaded --
     * the same guard {@link #enabled} exists for, and for the same reason: the leveling curve is
     * exercised by unit tests that never stand a server up. The three leveling numbers have no
     * permissive-versus-strict question to settle; the default value simply is the right answer when
     * no server has spoken.
     */
    public static int defaultBaseXp() {
        return loaded() ? DEFAULT_BASE_XP.get() : DEFAULT_BASE_XP_DEFAULT;
    }

    /** @see #defaultBaseXp() */
    public static double levelMultiplier() {
        return loaded() ? LEVEL_MULTIPLIER.get() : LEVEL_MULTIPLIER_FLOOR;
    }

    /** @see #defaultBaseXp() */
    public static int maximumLevels() {
        return loaded() ? MAXIMUM_LEVELS.get() : NO_LEVEL_CAP;
    }

    /** {@link #SURGEBOUND_CAPACITY_PER_LEVEL}, answering its own default whenever no server has spoken. */
    public static double surgeboundCapacityPerLevel() {
        return loaded() ? SURGEBOUND_CAPACITY_PER_LEVEL.get() : SURGEBOUND_CAPACITY_PER_LEVEL_DEFAULT;
    }

    /** @see #surgeboundCapacityPerLevel() */
    public static double surgeboundMiningSpeedPerLevel() {
        return loaded() ? SURGEBOUND_MINING_SPEED_PER_LEVEL.get() : SURGEBOUND_MINING_SPEED_PER_LEVEL_DEFAULT;
    }

    /** @see #surgeboundCapacityPerLevel() */
    public static double surgeboundNitroCapacityMultiplier() {
        return loaded() ? SURGEBOUND_NITRO_CAPACITY_MULTIPLIER.get() : SURGEBOUND_NITRO_CAPACITY_MULTIPLIER_DEFAULT;
    }

    /** @see #surgeboundCapacityPerLevel() */
    public static double surgeboundNitroMiningSpeedMultiplier() {
        return loaded() ? SURGEBOUND_NITRO_MINING_SPEED_MULTIPLIER.get()
                : SURGEBOUND_NITRO_MINING_SPEED_MULTIPLIER_DEFAULT;
    }

    static {
        // One builder per file (D-M8-8): NeoForge registers a config spec per file, so a folder of
        // files is several specs rather than one spec written out in pieces.
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        // The gameplay options used to sit at the top level of the one flat file. #968 pushed them
        // under `general` so this file, like the other three, has a section to carry its header;
        // ForgeweaveConfigMigration is what carries an existing file's values across that move.
        builder.comment("Forgeweave gameplay options: the upstream 1.12 options that have a behavior",
                        "site here, each keeping upstream's own default.",
                        "",
                        "This folder replaces the single config/forgeweave-server.toml. An existing copy of",
                        "that file was read once and its values split across these four files, and it was",
                        "then renamed to forgeweave-server.toml.migrated; nothing a pack had tuned was reset.",
                        "",
                        "Two standing rules for everything added here from Milestone 8 on. Everything numeric",
                        "is a config value, not a constant in Java. And every integration with another mod",
                        "beyond a material preset gets its own toggle in compat-server.toml -- material",
                        "presets are never toggled, because a preset that vanishes takes a material out of a",
                        "world that was built with it.",
                        "",
                        "No option in this folder needs a restart: each is read at the moment its behavior",
                        "runs, so editing a file and reloading is enough.")
                .push("general");
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
        builder.pop();
        GENERAL_SPEC = builder.build();

        builder = new ModConfigSpec.Builder();
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
        CONTENT_SPEC = builder.build();

        // D-M8-5 (issue #968): one toggle per integration beyond materials. Adding the next one is a
        // single define here plus one enabled(...) read at the point that integration answers.
        builder = new ModConfigSpec.Builder();
        builder.comment("Compat toggles: one per integration with another mod. An integration that is off",
                        "answers nothing and adds nothing, and that is all it does -- gear that already",
                        "carries a fusion upgrade or an installed module keeps every component it has and",
                        "works again the moment the toggle comes back. Nothing here can lose saved state.",
                        "",
                        "Material presets are deliberately absent: they are never toggled, because a preset",
                        "that vanishes takes a material out of a world that was built with it.",
                        "",
                        "Every option in this folder is numeric where it can be, rather than a constant in",
                        "Java, so a pack can retune an integration without a fork.")
                .push("compat");
        DRACONIC_FUSION = builder
                .comment("If true, Forgeweave's Draconic Evolution fusion upgrade ladder works: Draconic",
                        "Evolution's fusion crafting multiblock accepts a Forgeweave tool as a catalyst and",
                        "raises the rung's modifier. With this off no upgrade row matches, so the multiblock",
                        "finds no Forgeweave recipe. Upgrades already on a tool are untouched and keep working.",
                        "Note this does not remove the four fusion-metal ingot recipes: those are Draconic",
                        "Evolution's own recipe type, so a datapack is what drops them.")
                .define("draconicFusion", true);
        DRACONIC_MODULES = builder
                .comment("If true, Forgeweave gear made of a fusion metal hosts Draconic Evolution modules:",
                        "Draconic Evolution's module screen opens on it and its modules act on the tool. With",
                        "this off no Forgeweave stack is a host, so the screen does not open and no module",
                        "effect applies. Modules already installed stay on the stack, inert, and act again the",
                        "moment the toggle comes back.")
                .define("draconicModules", true);
        OVERLAYS = builder
                .comment("If true, the Jade and WTHIT block overlays show Forgeweave's own lines: a casting",
                        "table's cooling progress, a smeltery's molten contents, and the mining level a block",
                        "needs beside the level of the tool being held. With this off both overlays still work,",
                        "they just carry no Forgeweave line.")
                .define("overlays", true);
        KUBEJS_TRAITS = builder
                .comment("If true, traits registered from a KubeJS startup script through ForgeweaveEvents.traits",
                        "take effect. With this off a scripted trait resolves to nothing, so a material naming",
                        "one behaves as if the id had no implementation. Built-in and datapack traits are",
                        "unaffected either way.")
                .define("kubejsTraits", true);
        CREATE_GOGGLES = builder
                .comment("If true, Create's goggle overlays (stress readouts, fluid contents, goggle tooltips)",
                        "fire for a helmet carrying the Forgeweave goggles modifier. With this off Create's",
                        "overlays ignore Forgeweave helmets; the modifier stays on the helmet either way.")
                .define("createGoggles", true);
        APOTHEOSIS_SOCKETS = builder
                .comment("If true, Forgeweave gear takes Apotheosis gem sockets: the socketed modifier can be",
                        "applied, a gem can be seated in a socket, and a seated gem's bonus reaches the tool.",
                        "With this off no socket is added and no gem is seated, and a seated gem grants nothing.",
                        "The gems already in a stack's sockets stay there and grant again when this comes back.")
                .define("apotheosisSockets", true);
        // #1014 (D-M8-11) and #1015 (D-M8-17): the energized tank and surgebound, moved here from
        // the compat push those PRs opened at the end of the old flat spec.
        ENERGIZED_TANK = builder
                .comment("If true, the energized tank heats a smeltery to its fuel sample's temperature by",
                        "burning Forge Energy, and its crafting recipe resolves. With this off the block goes",
                        "dormant: it contributes no heat and cannot be crafted, but one already placed still",
                        "loads and keeps its sample, its buffer and its overdrive setting.")
                .define("energizedTank", true);
        ENERGIZED_TANK_BUFFER = builder
                .comment("How much Forge Energy an energized tank's buffer holds.")
                .defineInRange("energizedTankBuffer", ENERGIZED_TANK_BUFFER_DEFAULT, 1, Integer.MAX_VALUE);
        ENERGIZED_TANK_RF_PER_MELT_TICK_BASE = builder
                .comment("An energized tank spends base x temperature / divisor Forge Energy per melt tick, so",
                        "a hotter fuel sample costs proportionally more. This is the base; a smeltery melt tick",
                        "runs once every four game ticks.")
                .defineInRange("energizedTankRfPerMeltTickBase", ENERGIZED_TANK_RF_PER_MELT_TICK_BASE_DEFAULT,
                        0, Integer.MAX_VALUE);
        ENERGIZED_TANK_TEMPERATURE_DIVISOR = builder
                .comment("The divisor in the cost above. At the default of 1000 the base is what one melt tick",
                        "costs a tank imitating a fuel that burns at 1000 degrees.")
                .defineInRange("energizedTankTemperatureDivisor", ENERGIZED_TANK_TEMPERATURE_DIVISOR_DEFAULT,
                        1, Integer.MAX_VALUE);
        ENERGIZED_TANK_OVERDRIVE_COST = builder
                .comment("What pressing an energized tank's overdrive button multiplies its energy cost by.")
                .defineInRange("energizedTankOverdriveCost", ENERGIZED_TANK_OVERDRIVE_DEFAULT, 1.0D, 100.0D);
        ENERGIZED_TANK_OVERDRIVE_PROGRESS = builder
                .comment("What pressing an energized tank's overdrive button multiplies the smeltery's melt",
                        "progress by. Equal to the cost factor means overdrive is neither a discount nor a",
                        "penalty, only a choice to go faster.")
                .defineInRange("energizedTankOverdriveProgress", ENERGIZED_TANK_OVERDRIVE_DEFAULT, 1.0D, 100.0D);
        // Issue #996 (D-M8-17): surgebound's own flag under the same compat section above.
        POWAH_MODIFIERS = builder
                .comment("If true, the surgebound modifier (Powah's crystal ladder) can be applied at",
                        "the Tool Station. A tool already carrying it keeps the modifier either way --",
                        "off makes its bonus inert rather than revoking a level already spent on it.")
                .define("powahModifiers", true);
        SURGEBOUND_CAPACITY_PER_LEVEL = builder
                .comment("Fraction of the tool's own FE capacity surgebound adds per level, levels I-IV.")
                .defineInRange("surgeboundCapacityPerLevel", SURGEBOUND_CAPACITY_PER_LEVEL_DEFAULT, 0.0D, 10.0D);
        SURGEBOUND_MINING_SPEED_PER_LEVEL = builder
                .comment("Fraction of the tool's own base mining speed surgebound adds per level, levels I-IV.")
                .defineInRange("surgeboundMiningSpeedPerLevel", SURGEBOUND_MINING_SPEED_PER_LEVEL_DEFAULT, 0.0D, 10.0D);
        SURGEBOUND_NITRO_CAPACITY_MULTIPLIER = builder
                .comment("What the nitro step (level V) multiplies surgeboundCapacityPerLevel by, instead",
                        "of adding a fifth flat step.")
                .defineInRange("surgeboundNitroCapacityMultiplier", SURGEBOUND_NITRO_CAPACITY_MULTIPLIER_DEFAULT,
                        0.0D, 100.0D);
        SURGEBOUND_NITRO_MINING_SPEED_MULTIPLIER = builder
                .comment("What the nitro step (level V) multiplies surgeboundMiningSpeedPerLevel by,",
                        "instead of adding a fifth flat step.")
                .defineInRange("surgeboundNitroMiningSpeedMultiplier",
                        SURGEBOUND_NITRO_MINING_SPEED_MULTIPLIER_DEFAULT, 0.0D, 100.0D);
        // Issue #997 (D-M8-18): the Occultism ritual ladder, the crushing rows and the miner rows.
        OCCULTISM_RITUALS = builder
                .comment("If true, Forgeweave's Occultism integration works: a ritual binds a spirit into a",
                        "tool and grants it that ritual's modifier, Occultism's crusher spirits grind",
                        "Forgeweave ores, and its mining spirits can return them. With this off the ritual",
                        "recipe type is not registered and none of the three sets of rows load. A tool that",
                        "already carries a ritual's modifier keeps it and keeps its effect.")
                .define("occultismRituals", true);
        // #970 (M8-2, D-M8-5), the second and third Apotheosis toggles. Appended rather than grouped
        // beside apotheosisSockets above, so a compat-server.toml written by an earlier build keeps
        // the key order it already has. Both are read through ApotheosisAffixes and nowhere else.
        APOTHEOSIS_AFFIXES = builder
                .comment("If true, Forgeweave tools and armor that a loot table hands out arrive assembled, which",
                        "is what makes them eligible for Apotheosis loot affixes: an unassembled tool is a",
                        "category Apotheosis declines. Apotheosis decides affixability by its own predicate over",
                        "the item and offers no seam a mod can veto at runtime, so this is the only part of that",
                        "path Forgeweave owns. Off never discards anything: Forgeweave neither reads nor writes",
                        "affix state, so a tool already carrying affixes keeps every component either way. Has no",
                        "effect at all without Apotheosis installed.")
                .define("apotheosisAffixes", true);
        APOTHEOSIS_ENCHANTING = builder
                .comment("If true, Forgeweave gear may be enchanted at Apotheosis' enchanting table. This does not",
                        "replace allowVanillaEnchanting: enchanting needs both, and with either one off the table",
                        "and the anvil refuse the item. Nothing is stripped when it is off, and an already",
                        "enchanted tool keeps its enchantments and keeps applying them. An enchantment never costs",
                        "a modifier slot. Has no effect without Apothic Enchanting installed, which is the mod",
                        "that owns the table.")
                .define("apotheosisEnchanting", true);
        // Issue #995 (D-M8-12, D-M8-13, D-M8-16): the four processing-mod bridges, added last so the
        // sections above keep the order every existing config file on disk already has.
        CREATE_RECIPES = builder
                .comment("If true, Forgeweave's generated Create recipes resolve: the heated mixer for",
                        "the basic alloys, crushing wheels for Track B ores, and the mechanical press for",
                        "ingot-to-plate. With this off none of those rows match; nothing Forgeweave owns",
                        "changes either way.")
                .define("createRecipes", true);
        IMMERSIVE_ENGINEERING_RECIPES = builder
                .comment("If true, Forgeweave's generated Immersive Engineering recipes resolve: the arc",
                        "furnace for the basic alloys, the crusher for Track B ores, and the metal press",
                        "for ingot-to-plate. With this off none of those rows match.")
                .define("immersiveEngineeringRecipes", true);
        ENDER_IO_RECIPES = builder
                .comment("If true, Forgeweave's generated EnderIO recipes resolve: the alloy smelter for",
                        "the basic alloys and the SAG mill for Track B ores. With this off neither resolves.")
                .define("enderIoRecipes", true);
        POWAH_HEAT_SOURCES = builder
                .comment("If true, Forgeweave's molten fluids read as Powah thermo generator heat sources.",
                        "With this off a thermo generator no longer burns them; Powah's own heat sources",
                        "for its own fluids are untouched either way.")
                .define("powahHeatSources", true);
        // #973 (M8-5, D-M8-5), the eleventh toggle the section was planned with. Appended after
        // #995's four for the same reason they were appended, so an existing compat-server.toml
        // keeps the key order it already has.
        MODIFIER_DEFINITIONS = builder
                .comment("If true, modifiers a datapack defines through forgeweave:modifier_definition take",
                        "effect. With this off a pack-defined modifier resolves to nothing, so a tool carrying",
                        "one behaves as if the id had no implementation. The modifier stays on the tool either",
                        "way, with its level, and acts again the moment the toggle comes back. Built-in",
                        "modifiers are unaffected either way.")
                .define("modifierDefinitions", true);
        // Issue #998 (D-M8-19): Allthemodium and Elementarium, the two closed-roster/closed-source
        // integrations that reach Forgeweave entirely through tags.
        ALLTHEMODIUM_TIERS = builder
                .comment("If true, Track B's ore family generates in Allthemodium's mining dimension",
                        "(allthemodium:mining). The tag-based tier equivalence itself (Forgeweave tools",
                        "mining Allthemodium ore and Allthemodium tools mining Forgeweave ore) is not",
                        "covered by this toggle: it is existence-gated only, the same as every material",
                        "preset (D-M8-5), because a live config value has no site in a static tag file.")
                .define("allthemodiumTiers", true);
        ELEMENTARIUM_MATERIALS = builder
                .comment("If true, the Track A presets generated from Elementarium's c:ingots/* tag family",
                        "register. Unlike every other material preset (D-M8-5 says presets are never",
                        "toggled) these are generated rather than authored, so this is the way out of a",
                        "bad interpolation that is not hand-editing generated JSON.")
                .define("elementariumMaterials", true);
        // #993 (M8-9, D-M8-5, D-M8-15): the Mekanism module container's own toggle and its numbers.
        // Appended rather than grouped, so a compat-server.toml written by an earlier build keeps the
        // key order it already has. Read through MekanismGearModules and the named helpers above.
        MEKANISM_MODULES = builder
                .comment("If true, Forgeweave gear carrying an atomic_matter_alloy part is a Mekanism module",
                        "container: its Modification Station installs MekaTool and MekaSuit modules into it and",
                        "Forgeweave's own hooks run their effects. Off makes the container inert rather than",
                        "absent -- every effect goes neutral and the module screen stops opening, but a tool that",
                        "already carries installed modules keeps Mekanism's own component untouched and works",
                        "again the moment this returns. The atomic_matter_alloy material itself is never toggled.",
                        "Has no effect at all without Mekanism installed.")
                .define("mekanismModules", true);
        MEKANISM_ENERGY_PER_BLOCK = builder
                .comment("Forge Energy one block break costs while a Mekanism excavation, blasting or vein mining",
                        "module is installed and switched on. Paid out of the tool's own buffer, per block, so an",
                        "area swing costs one block's price for each block it takes. A buffer that cannot pay it",
                        "leaves the module doing nothing rather than working for free.")
                .defineInRange("mekanismEnergyPerBlock", MekanismGearModules.ENERGY_PER_BLOCK_DEFAULT,
                        0, Integer.MAX_VALUE);
        MEKANISM_ENERGY_PER_ABSORBED_POINT = builder
                .comment("Forge Energy a worn piece spends per point of damage its Mekanism absorption modules",
                        "take off an incoming blow. An absorption the buffer cannot pay for in full does not",
                        "happen at all, rather than happening at a discount.")
                .defineInRange("mekanismEnergyPerAbsorbedPoint",
                        MekanismGearModules.ENERGY_PER_ABSORBED_POINT_DEFAULT, 0, Integer.MAX_VALUE);
        MEKANISM_VEIN_MINING_MAX_BLOCKS = builder
                .comment("How many extra blocks one Mekanism vein mining swing breaks at most. A second bound on",
                        "top of Mekanism's own traversal limit, so a generous Mekanism config cannot stall a tick.")
                .defineInRange("mekanismVeinMiningMaxBlocks", MekanismGearModules.VEIN_MINING_MAX_BLOCKS_DEFAULT,
                        0, 4096);
        MEKANISM_NUCLEOSYNTHESIZING_ALLOY_COUNT = builder
                .comment("How many of Mekanism's own atomic alloys the Antiprotonic Nucleosynthesizer turns into",
                        "one atomic_matter_alloy ingot. Baked into the generated recipe, so changing it needs a",
                        "datagen run rather than a reload.")
                .defineInRange("mekanismNucleosynthesizingAlloyCount",
                        ForgeweaveMekanismCompat.NUCLEOSYNTHESIZING_ALLOY_COUNT_DEFAULT, 1, 64);
        MEKANISM_NUCLEOSYNTHESIZING_ANTIMATTER = builder
                .comment("How much antimatter, in mB, that craft spends. Mekanism's own hardest nucleosynthesizing",
                        "recipe spends 5.")
                .defineInRange("mekanismNucleosynthesizingAntimatter",
                        ForgeweaveMekanismCompat.NUCLEOSYNTHESIZING_ANTIMATTER_DEFAULT, 1, 10_000);
        MEKANISM_NUCLEOSYNTHESIZING_DURATION = builder
                .comment("How long, in ticks, that craft takes. Mekanism's own longest is 1250.")
                .defineInRange("mekanismNucleosynthesizingDuration",
                        ForgeweaveMekanismCompat.NUCLEOSYNTHESIZING_DURATION_DEFAULT, 1, 100_000);
        // #994 (M8-10, D-M8-15): phase 2's own numbers, appended for the same key-order reason.
        MEKANISM_ENERGY_PER_TELEPORT = builder
                .comment("Forge Energy one jump of a Mekanism teleportation unit costs, paid out of the tool's",
                        "own buffer. A buffer that cannot pay it leaves the right-click doing whatever the tool",
                        "would have done anyway.")
                .defineInRange("mekanismEnergyPerTeleport", MekanismGearModules.ENERGY_PER_TELEPORT_DEFAULT,
                        0, Integer.MAX_VALUE);
        MEKANISM_ENERGY_PER_FLIGHT_TICK = builder
                .comment("Forge Energy a Mekanism jetpack or gravitational modulating unit spends per tick, paid",
                        "out of the worn piece's own buffer. An empty buffer grounds the module rather than",
                        "flying for free.")
                .defineInRange("mekanismEnergyPerFlightTick",
                        MekanismGearModules.ENERGY_PER_FLIGHT_TICK_DEFAULT, 0, Integer.MAX_VALUE);
        MEKANISM_TELEPORT_MAX_DISTANCE = builder
                .comment("How far, in blocks, a Mekanism teleportation unit will move the player. Mekanism's own",
                        "tool reaches 10.")
                .defineInRange("mekanismTeleportMaxDistance",
                        MekanismGearModules.TELEPORT_MAX_DISTANCE_DEFAULT, 1, 256);
        RADIATION_SHIELDING_PER_LEVEL = builder
                .comment("The fraction of incoming radiation one level of the rayward modifier blocks. Four",
                        "levels at the default 0.25 come to exactly 1.0, a piece that shields completely; the",
                        "total is clamped there, so raising this only makes the earlier levels worth more.",
                        "Rayward is a Forgeweave modifier and follows the modifiers content toggle, not",
                        "mekanismModules -- without Mekanism installed nothing asks it for a number.")
                .defineInRange("radiationShieldingPerLevel",
                        ForgeweaveModifiers.RAYWARD_SHIELDING_PER_LEVEL_DEFAULT, 0.0D, 1.0D);
        // Issue #999 (D-M8-20). Appended for the same key-order reason the two above are.
        MYSTICAL_AGRICULTURE_AUGMENTS = builder
                .comment("If true, Forgeweave works with Mystical Agriculture: the Track B ores and brimspar",
                        "get resource crops, and gear built from a Mystical Agriculture essence metal is",
                        "accepted by Mystical Agriculture's own Tinkering Table so augments can be installed.",
                        "",
                        "Off makes the augment seam inert rather than absent: a Forgeweave tool still goes into",
                        "the Tinkering Table's first slot, but it offers no augment slot, so nothing can be",
                        "installed and nothing already installed does anything. Augment data is Mystical",
                        "Agriculture's own and Forgeweave neither copies nor clears it, so a stack that already",
                        "carries some keeps it and works again the moment this comes back. No restart needed.",
                        "",
                        "Whether the crops exist is decided when registries freeze, so adding or removing",
                        "Mystical Agriculture itself needs a restart -- installing a mod always does. The",
                        "material presets are unaffected either way: they are never toggled.")
                .define("mysticalAgricultureAugments", true);
        builder.pop();
        COMPAT_SPEC = builder.build();

        builder = new ModConfigSpec.Builder();
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
        WORLDGEN_SPEC = builder.build();
    }

    private ForgeweaveConfig() {}
}
