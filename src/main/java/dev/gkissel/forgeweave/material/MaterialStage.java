package dev.gkissel.forgeweave.material;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;

/**
 * How far into the game a material sits, for the guide book's materials chapter (issue #1104).
 * Seven stages, first day to endgame, and every registered material lands in exactly one.
 *
 * <h2>Why this is derived and not a field</h2>
 *
 * <p>A {@code stage} field would mean editing 216 material JSONs, and it would ride along in the
 * material sync payload for every material on every join ({@code MaterialSyncSizeTest}'s budget).
 * Both stage inputs are already in the data:
 *
 * <ul>
 *   <li>{@link Material#incorrectForTool}, the harvest rung -- Forgeweave's tag stand-in for a
 *       numeric harvest level, read through {@link ForgeweaveModifiers#tierIndexOf(TagKey)}. Eight
 *       rungs fold into six bands here, because the three Track B rungs above netherite are a stat
 *       plateau rather than three separate shopping trips.
 *   <li>whether the material is an alloy, and so needs a second smeltery load of something else
 *       before it exists at all -- read off the {@code alloy_recipe} registry, not off the material.
 * </ul>
 *
 * <p>The rung alone leaves 64 materials on the netherite rung (a third of the roster), which is why
 * the alloy bump is there: a netherite-rung ore you can mine and a netherite-rung alloy you have to
 * build a chain for are not the same afternoon.
 *
 * <h2>The override</h2>
 *
 * <p>A pack that disagrees puts the material in one of the seven {@code forgeweave:stage/<id>}
 * material tags ({@link #tag()}) and that wins. The tags cost nothing for the materials nobody
 * overrides and they need no change to {@link Material} itself.
 *
 * <p>Forgeweave ships one such override itself (issue #1113):
 * {@code data/forgeweave/tags/forgeweave/material/stage/endgame.json} puts
 * {@code atomic_matter_alloy} in {@link #ENDGAME}. That material is the one whose real gate lives
 * outside its own data -- the only thing that makes its ingot is a nucleosynthesis run on Mekanism's
 * own deepest machine -- so the derivation, which reads only the rung and the alloy depth, lands it
 * a stage early at {@link #DEEP_ALLOYS} alongside mined resonite.
 *
 * <p>ponytail: the override rides on registry tags, which is the cheapest carrier that needs no new
 * field and no new sync payload, and it has still not been exercised in a live client. If a tag on a
 * datapack registry turns out not to reach the client, the derivation still stands and only the
 * override goes quiet -- atomic matter alloy would show up one stage early rather than break; the
 * next carrier to try would be a small {@code stage_override} datapack registry of its own, which
 * syncs the same way {@code material} does.
 */
public enum MaterialStage {

    /** Wood, stone, flint, bone: carved at a Part Builder before there is a smeltery. */
    FIRST_DAY("first_day"),
    /** The first ores worth pouring, once a basic smeltery and a cast exist. */
    FIRST_SMELTERY("first_smeltery"),
    /** Materials that mine what iron cannot, plus the first alloys of iron-rung metals. */
    DEEPER_MINING("deeper_mining"),
    /** The nether-rung roster, plus alloys of diamond-rung metals. */
    NETHER_METALS("nether_metals"),
    /** Forgeweave's own ores above netherite, plus alloys of nether-rung metals. */
    PAST_NETHERITE("past_netherite"),
    /** The top rung, plus the alloys that need it. */
    DEEP_ALLOYS("deep_alloys"),
    /** Alloys poured out of other alloys, two or more smeltery loads deep. */
    ENDGAME("endgame");

    private static final List<MaterialStage> ORDER = List.of(values());

    /**
     * Which band each of {@link ForgeweaveModifiers}' eight harvest rungs starts in: wooden and
     * stone share the first day, and hardcinder/warspar share one band so the eight rungs fold into
     * the six a seven-stage ladder has room for once the alloy bump is added on top.
     *
     * <p>That fold used to be justified by the rungs' stats being a plateau (review
     * 06-progression.md &sect;3, "break 2"). Issue #1113 fixed the plateau -- every rung-5, -6 and -7
     * material now clears plain netherite and the rungs read as three steps -- so what keeps the fold
     * is the stage count alone: splitting rung 5 from rung 6 needs a seventh band, and band 6 plus
     * the alloy bump runs off the end of the seven stages.
     */
    private static final int[] RUNG_BAND = {0, 0, 1, 2, 3, 4, 4, 5};

    private final String id;

    MaterialStage(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    /** The stage's player-facing name, e.g. "First day". */
    public String nameKey() {
        return "book.forgeweave.stage." + this.id + ".name";
    }

    /** One sentence saying what a player has to have before this stage opens. */
    public String unlockKey() {
        return "book.forgeweave.stage." + this.id + ".unlock";
    }

    /** The material tag that forces a material into this stage, {@code forgeweave:stage/<id>}. */
    public TagKey<Material> tag() {
        return TagKey.create(Material.REGISTRY,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "stage/" + this.id));
    }

    /**
     * The stage a material derives into: its harvest rung's band, one stage further along if it is
     * an alloy.
     *
     * @param alloyDepth how many alloying steps deep the material's own fluid is, 0 for anything
     *                   that is not an alloy (see {@link Lookup#alloyDepth})
     */
    public static MaterialStage derive(Material material, int alloyDepth) {
        int rung = ForgeweaveModifiers.tierIndexOf(material.incorrectForTool());
        int band = RUNG_BAND[rung < 0 ? 0 : Math.min(rung, RUNG_BAND.length - 1)];
        return ORDER.get(Math.min(ORDER.size() - 1, band + (alloyDepth > 0 ? 1 : 0)));
    }

    /**
     * Every material's stage, resolved once: the {@code stage/<id>} tag where a pack set one, the
     * derivation otherwise. Built at book-open time from the registries the client already has --
     * both {@code material} and {@code alloy_recipe} are datapack registries with a network codec
     * ({@code Forgeweave#registerDataPackRegistries}), so both are present client side.
     */
    public record Lookup(Map<ResourceLocation, MaterialStage> overrides, Map<ResourceLocation, Integer> alloyDepth) {

        /** No overrides and no alloy data: what a unit test that only cares about rungs wants. */
        public static final Lookup EMPTY = new Lookup(Map.of(), Map.of());

        public Lookup {
            overrides = Map.copyOf(overrides);
            alloyDepth = Map.copyOf(alloyDepth);
        }

        public static Lookup of(Registry<Material> materials, Registry<AlloyRecipe> alloys) {
            Map<ResourceLocation, MaterialStage> overrides = new HashMap<>();
            for (MaterialStage stage : values()) {
                for (Holder<Material> holder : materials.getTagOrEmpty(stage.tag())) {
                    holder.unwrapKey().map(ResourceKey::location)
                            .ifPresent(id -> overrides.putIfAbsent(id, stage));
                }
            }
            return new Lookup(overrides, alloyDepth(alloys.stream().toList()));
        }

        /** The derivation half on its own, for a caller with alloy recipes but no live registry. */
        public static Lookup derive(List<AlloyRecipe> alloys) {
            return new Lookup(Map.of(), alloyDepth(alloys));
        }

        public MaterialStage of(ResourceLocation id, Material material) {
            MaterialStage override = this.overrides.get(id);
            return override != null ? override : MaterialStage.derive(material, depthOf(id));
        }

        /** How many alloying steps deep this material's own molten fluid is. */
        public int depthOf(ResourceLocation id) {
            return this.alloyDepth.getOrDefault(moltenFluidId(id), 0);
        }

        /**
         * Depth per result fluid: 1 for an alloy of plain metals, 2 for an alloy one of whose
         * inputs is itself an alloy, and so on. The walk carries the fluids already on the path so
         * a pack that writes a cycle gets a finite answer instead of a stack overflow (the shipped
         * chains bottom out at 4: truesteel &larr; sunsteel &larr; glowveil &larr; dreadalloy).
         */
        private static Map<ResourceLocation, Integer> alloyDepth(List<AlloyRecipe> alloys) {
            Map<ResourceLocation, List<ResourceLocation>> inputs = new HashMap<>();
            for (AlloyRecipe recipe : alloys) {
                List<ResourceLocation> ids = new ArrayList<>();
                recipe.inputs().forEach(input -> ids.add(fluidId(input.getFluid())));
                inputs.put(fluidId(recipe.result().getFluid()), List.copyOf(ids));
            }
            Map<ResourceLocation, Integer> depth = new HashMap<>();
            inputs.keySet().forEach(result -> depth.put(result, depth(result, inputs, List.of())));
            return depth;
        }

        private static int depth(ResourceLocation fluid, Map<ResourceLocation, List<ResourceLocation>> inputs,
                List<ResourceLocation> path) {
            List<ResourceLocation> recipe = inputs.get(fluid);
            if (recipe == null || path.contains(fluid)) {
                return 0;
            }
            List<ResourceLocation> walked = new ArrayList<>(path);
            walked.add(fluid);
            int deepest = 0;
            for (ResourceLocation input : recipe) {
                deepest = Math.max(deepest, depth(input, inputs, walked));
            }
            return 1 + deepest;
        }

        private static ResourceLocation fluidId(Fluid fluid) {
            return BuiltInRegistries.FLUID.getKey(fluid);
        }
    }

    /** The fluid a material melts into, {@code forgeweave:molten_<material>}. */
    public static ResourceLocation moltenFluidId(ResourceLocation material) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "molten_" + material.getPath());
    }
}
