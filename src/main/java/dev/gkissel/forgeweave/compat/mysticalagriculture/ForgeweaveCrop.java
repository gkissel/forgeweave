package dev.gkissel.forgeweave.compat.mysticalagriculture;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import net.neoforged.neoforge.registries.DeferredBlock;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * The roster of resource crops Forgeweave asks Mystical Agriculture to grow (issue #999, D-M8-20),
 * and the one place that decides which materials get one. Deliberately names no {@code com.blakebr0}
 * type: {@link MysticalAgricultureCompat} turns each row here into a Mystical Agriculture
 * {@code Crop}, so this class stays loadable -- and so unit- and GameTest-reachable -- on a classpath
 * with no Mystical Agriculture on it at all.
 *
 * <p>{@link #ALL} walks {@link TrackBOre#ALL} rather than listing eleven rows by hand, the
 * anti-drift shape every other per-material provider in this repo uses, so a twelfth Track B ore
 * inherits a crop with no edit here.
 *
 * <h2>Which fuel materials get a crop, and which do not (#999's open question)</h2>
 *
 * <p>The fuel ladder's three Forgeweave-owned rungs are molten magma, brimspar and pyrealloy
 * ({@code ForgeweaveFluids}). A crop needs something to drop and something to grow on, so the test is
 * whether the material has an item form at all:
 *
 * <ul>
 *   <li><b>Brimspar gets a crop.</b> It owns {@code brimspar_crystal} (#903), the item its ore drops
 *       to be melted, so there is a real thing for the crop to be worth growing.
 *   <li><b>Molten magma does not.</b> It is not a Forgeweave material: it is what a vanilla magma
 *       block melts into ({@code melting_recipe/magma_block.json}), and vanilla already grows nothing
 *       for it. Forgeweave owns no magma item to hang a crop on.
 *   <li><b>Pyrealloy does not.</b> It is fluid-only by design (#897) -- no ingot, no nugget, no
 *       block, no material JSON -- and nothing consumes it but its own {@code smeltery_fuel} row.
 * </ul>
 *
 * <p>Inventing an item form for either of those two to hang a crop on would be a material decision,
 * and #999 is explicit that it belongs in a material issue rather than here.
 *
 * <h2>The crux</h2>
 *
 * <p>A crop's crux is the material's own storage block, which every Track B ore has. Brimspar has no
 * storage block -- it is a fuel and never a tool material, so it was never given an ingot to stack
 * into one -- so its crux is {@link ForgeweaveBlocks#BRIMSPAR_ORE}, the only block it owns. That is
 * the one deviation from #999's "crux is the storage block" rule and it is deliberate: the
 * alternative is no brimspar crop at all.
 *
 * @param id the crop's own path, e.g. {@code resonite}; its essence and seeds are named off it
 * @param displayName the material's display name, for the lang lines
 * @param tier the essence tier, derived from the mining ladder by {@link EssenceTier#forOre}
 * @param crux the block that must sit under the crop, per the section above
 * @param color the material's own colour, which Mystical Agriculture tints the flower, essence and
 *     seed sprites with -- see {@link MysticalAgricultureCompat} for why that means no new art
 */
public record ForgeweaveCrop(String id, String displayName, EssenceTier tier, DeferredBlock<?> crux,
        int color) {

    /**
     * Brimspar's own flavour colour -- the hex {@code UnstableOreBlock#BRIMSPAR_CRYSTAL_COLOR} and
     * {@code ForgeweaveFluids#BRIMSPAR} already share, repeated here rather than imported because
     * neither of those is on this class's own dependency path.
     */
    private static final int BRIMSPAR_COLOR = 0x0FBD59;

    /** Every crop Forgeweave registers: the eleven Track B ores, then brimspar. */
    public static final List<ForgeweaveCrop> ALL = build();

    /** The crop's full id, {@code forgeweave:<id>}. */
    public ResourceLocation cropId() {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, id);
    }

    /** The item a harvest yields, which Mystical Agriculture registers as {@code <id>_essence}. */
    public String essenceItemId() {
        return id + "_essence";
    }

    /** The seed that plants the crop, registered as {@code <id>_seeds}. */
    public String seedsItemId() {
        return id + "_seeds";
    }

    /** The crux block, resolved. Only ever called once registries are populated. */
    public Block cruxBlock() {
        return crux.get();
    }

    private static List<ForgeweaveCrop> build() {
        List<ForgeweaveCrop> crops = new ArrayList<>();
        for (TrackBOre ore : TrackBOre.ALL) {
            crops.add(new ForgeweaveCrop(ore.id(), ore.displayName(), EssenceTier.forOre(ore.tier()),
                    ForgeweaveBlocks.trackBStorageBlock(ore.id()), ore.color()));
        }
        // Brimspar: the one fuel material with an item form, cruxed on its ore -- see the class
        // javadoc. Its ore rides cobalt/ardite's netherite gate (#903), which is Tier.NETHERITE's own
        // rung, so it reads its tier through the same total function every other crop does.
        crops.add(new ForgeweaveCrop("brimspar", "Brimspar", EssenceTier.forOre(TrackBOre.Tier.NETHERITE),
                ForgeweaveBlocks.BRIMSPAR_ORE, BRIMSPAR_COLOR));
        return List.copyOf(crops);
    }
}
