package dev.gkissel.forgeweave.client.book;

import java.util.Map;

import javax.annotation.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The guide book's 3D structure data (issue #651): a port of Mantle's {@code StructureInfo} plus
 * the {@code BlockData} spans it is built from and the visibility rule of its
 * {@code StructureBlockAccess} (branch {@code 1.12}, commit
 * {@code 340a386af51a97efaac0e71a3f1ff87fb267efe9}, MIT -- NOTICE.md). The file shape is
 * {@code ContentStructure}'s: {@code size} = [length, height, width] and a {@code structure} array
 * of {@code {pos, endPos, block, state}} spans, first span wins per cell, an unresolvable block id
 * becomes air. 1.12's {@code meta} numbers have no modern equivalent, so the shipped data spells
 * every non-default state through upstream's own {@code state} property-map option instead
 * (upstream {@code StructureInfo#convert}'s other branch).
 *
 * <p>The animation counters are upstream's, verbatim: {@code blockIndex} is a limiter over the
 * flattened cell index {@code y*(length*width) + x*width + z}; a full structure has
 * {@code blockIndex == maxBlockIndex}, {@code step()} advances it one placed block at a time
 * (wrapping past the end), {@code canStep()} answers whether a placed block remains above it, and
 * {@code setShowLayer(n)} clamps it to the first n+1 layers. {@code BookSmelteryStructureTest}
 * pins all of it.
 */
public final class StructureInfo {

    private final int length;
    private final int height;
    private final int width;
    /** {@code [y][x][z]}; {@code null} is air, exactly upstream's unset cell. */
    private final BlockState[][][] data;
    private final int maxBlockIndex;
    private int blockIndex;

    private StructureInfo(int length, int height, int width, BlockState[][][] data) {
        this.length = length;
        this.height = height;
        this.width = width;
        this.data = data;
        this.maxBlockIndex = height * length * width;
        this.blockIndex = this.maxBlockIndex;
    }

    /** Loads a structure file from the shipped book tree, {@code assets/forgeweave/book/<path>}. */
    public static StructureInfo load(String path) {
        return parse(BookStructure.read(path).getAsJsonObject());
    }

    /** The raw-JSON seam, so the parse contract is unit-testable without a data file. */
    public static StructureInfo parse(String json) {
        return parse(JsonParser.parseString(json).getAsJsonObject());
    }

    private static StructureInfo parse(JsonObject json) {
        int[] size = new int[3];
        int i = 0;
        for (JsonElement dim : json.getAsJsonArray("size")) {
            size[i++] = dim.getAsInt();
        }
        int length = size[0];
        int height = size[1];
        int width = size[2];

        BlockState[][][] states = new BlockState[height][length][width];
        // Upstream fills per cell with the first span containing it; iterating the spans in order
        // and keeping the first write per cell is the same rule.
        for (JsonElement entry : json.getAsJsonArray("structure")) {
            JsonObject span = entry.getAsJsonObject();
            int[] pos = intVec(span, "pos");
            int[] endPos = intVec(span, "endPos");
            BlockState state = convert(span);
            for (int y = pos[1]; y <= endPos[1]; y++) {
                for (int x = pos[0]; x <= endPos[0]; x++) {
                    for (int z = pos[2]; z <= endPos[2]; z++) {
                        if (states[y][x][z] == null) {
                            states[y][x][z] = state;
                        }
                    }
                }
            }
        }
        return new StructureInfo(length, height, width, states);
    }

    private static int[] intVec(JsonObject span, String key) {
        int[] vec = new int[3];
        int i = 0;
        for (JsonElement value : span.getAsJsonArray(key)) {
            vec[i++] = value.getAsInt();
        }
        return vec;
    }

    /**
     * Upstream {@code StructureInfo#convert}: the named block's default state with the {@code
     * state} map's properties applied by name; an unknown block, and a property value the block
     * does not parse, degrade silently -- authored data errors show as air/default rather than a
     * crash, upstream's own contract.
     */
    @Nullable
    private static BlockState convert(JsonObject span) {
        Block block = BuiltInRegistries.BLOCK.getOptional(
                ResourceLocation.parse(span.get("block").getAsString())).orElse(null);
        if (block == null) {
            return null;
        }
        BlockState state = block.defaultBlockState();
        if (span.has("state")) {
            for (Map.Entry<String, JsonElement> entry : span.getAsJsonObject("state").entrySet()) {
                Property<?> property = block.getStateDefinition().getProperty(entry.getKey());
                if (property != null) {
                    state = withValue(state, property, entry.getValue().getAsString());
                }
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state,
            Property<T> property, String value) {
        return property.getValue(value).map(parsed -> state.setValue(property, parsed)).orElse(state);
    }

    public int length() {
        return this.length;
    }

    public int height() {
        return this.height;
    }

    public int width() {
        return this.width;
    }

    /** The cell's state regardless of the animation limiter, or {@code null} for air. */
    @Nullable
    public BlockState stateAt(int x, int y, int z) {
        return this.data[y][x][z];
    }

    /**
     * {@code StructureBlockAccess#getBlockState}: the cell's state, but only while its flattened
     * index has been reached by the build-up animation -- {@code null} otherwise.
     */
    @Nullable
    public BlockState visibleStateAt(int x, int y, int z) {
        return indexOf(x, y, z) <= this.blockIndex ? this.data[y][x][z] : null;
    }

    /** {@code setShowLayer}: only the first {@code layer + 1} layers pass the limiter. */
    public void setShowLayer(int layer) {
        this.blockIndex = (layer + 1) * (this.length * this.width) - 1;
    }

    /** Back to everything visible. */
    public void reset() {
        this.blockIndex = this.maxBlockIndex;
    }

    /** Whether a placed block remains above the limiter -- false once the build-up is complete. */
    public boolean canStep() {
        int index = this.blockIndex;
        do {
            if (++index >= this.maxBlockIndex) {
                return false;
            }
        } while (isEmpty(index));
        return true;
    }

    /** Advances the limiter to the next placed block, wrapping to the first after a full build. */
    public void step() {
        int start = this.blockIndex;
        do {
            if (++this.blockIndex >= this.maxBlockIndex) {
                this.blockIndex = 0;
            }
        } while (isEmpty(this.blockIndex) && this.blockIndex != start);
    }

    private boolean isEmpty(int index) {
        int y = index / (this.length * this.width);
        int r = index % (this.length * this.width);
        int x = r / this.width;
        int z = r % this.width;
        return this.data[y][x][z] == null;
    }

    private int indexOf(int x, int y, int z) {
        return y * (this.length * this.width) + x * this.width + z;
    }
}
