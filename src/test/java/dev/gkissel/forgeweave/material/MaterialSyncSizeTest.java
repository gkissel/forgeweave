package dev.gkissel.forgeweave.material;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import io.netty.buffer.Unpooled;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;

/**
 * Guards the material registry-sync payload (issue #227; docs/SCOPE.md performance budget: "the
 * material sync packet stays trivially small even at M6 scale"). {@code Material.REGISTRY} is a
 * datapack registry whose network codec is {@link Material#CODEC} itself (see
 * {@code Forgeweave#registerDataPackRegistries}), and vanilla registry sync encodes each entry with
 * that codec over {@code NbtOps} and writes the resulting tag to the packet buffer
 * ({@code RegistrySynchronization#packEntry}). This test mirrors that path for every shipped
 * material JSON and asserts the summed buffer size stays under {@link #SYNC_BUDGET_BYTES}.
 */
class MaterialSyncSizeTest {

    /**
     * The whole-roster budget. Measured at 4,701 bytes for the 11 pre-M3.2 materials (~430 bytes
     * each), so the M3.2 roster's ~33 materials land around 14 KB; x5 the measured size covers that
     * 3x count growth plus headroom for richer entries (more crafting items, per-part traits)
     * while still catching a payload that grows an order of magnitude unnoticed. Rounded to 24 KB.
     *
     * <p>Revisited for M4-1 (issue #676): the 43-material roster measured 21,201 bytes before the
     * armor blocks and 26,797 after -- four per-piece {@code plating} rows on 18 materials plus the
     * {@code maille} marker and {@code traits.armor} list add ~310 bytes per plating material, a new
     * stat family rather than an entry growing out of the roster's norm (~620 bytes each now). Same
     * rule re-applied -- headroom for M4-3's remaining schema work and the M6 roster growth while a
     * 10x surprise still trips it -- lands at 32 KB, still a fraction of one chunk packet.
     *
     * <p>Revisited again for M6 Track A batch 1 (issue #833): the 54-material roster (43 plus this
     * batch's 11 generic tech metals, 10 of them with a full plating block) measured 36,504 bytes,
     * over the 32 KB budget -- expected, since the epic (#824) sizes M6's eventual roster near 170
     * materials and this batch is "the first big step toward the ~140-material sync payload" (issue
     * #833's own test-strategy note). Rather than re-derive a multiplier, this raises the budget to
     * 64 KB: roughly 1.8x the newly-measured payload, i.e. enough headroom for the remaining M6
     * preset batches (#834-#837) to land without tripping the budget on every PR, while a further
     * 10x surprise (a material accidentally carrying a huge field) still catches it. Still a small
     * fraction of one chunk packet.
     *
     * <p>Batch 2 (issue #834) landed on top of batch 1 without needing another revisit: the combined
     * 67-material roster (46 pre-M6 plus batch 1's 11 plus this batch's 10 Mekanism/AE2/Occultism
     * materials) stays under the 64 KB line #833 already drew.
     *
     * <p>Batches 3 (#835, Ender IO), and #843 (closes #180, the 1.20-branch material gap's five
     * by-name additions) landed the same way: the combined 80-material roster measures 54,283 bytes,
     * still under the 64 KB line with room for the remaining M6 batches before another revisit is due.
     *
     * <p>Batches 4 (#836, Draconic Evolution) and 5 (#837, the gem/crystal tier) landed on top of each
     * other and finally tripped the 64 KB line: the combined 98-material roster measures 67,090 bytes.
     * That's the last of the Track A preset batches (#833-#837), so this is a real, expected step
     * rather than a runaway field -- raised to 96 KB, ~1.46x the newly-measured payload, enough
     * headroom for Track B's self-contained ladder (~30 more materials, #838-#841) before another
     * revisit is due.
     *
     * <p>Issue #841 (Track B's own 30-material roster, closing the epic) landed on top of the above
     * without tripping the line: the combined roster -- 128 materials, matching the epic's own ~128
     * projection -- stays under the 96 KB budget #837 already drew, the same "landed without another
     * revisit" shape #834 saw at the Track A midpoint.
     *
     * <p>Issue #846 (M6-18, UI/schema hardening at the final roster scale) re-measured the shipped
     * 128-material roster precisely: 95,235 bytes, ~93 KB, against the 96 KB (98,304-byte) budget --
     * about 3 KB (3%) of headroom left. Left as-is rather than raised: the roster is now final (the
     * epic's own closing issue), there is no further planned growth to budget headroom for, and the
     * measured payload is still comfortably a small fraction of one chunk packet. A future material
     * roster expansion should re-measure and raise deliberately rather than assume this margin holds.
     *
     * <p>Issue #872 (the M6 recovery batch: concrete item ids in {@code crafting_items}/{@code
     * repair_item}, unblocking ProjectE/AvaritiaNeo/Refined Storage/Powah's tag-less materials plus
     * Draconic Evolution's core-tier pair) is exactly the "future material roster expansion" the
     * note above anticipated -- the 3% margin didn't hold. The combined 138-material roster measures
     * 102,426 bytes, over the 96 KB line. Raised to 104 KB (106,496 bytes), ~4 KB (4%) of headroom
     * above the new measurement -- deliberately tight rather than another multiplier-based jump,
     * since this really is the epic's closing batch (#824's child issue list has nothing left that
     * ships a material) and there is no further planned growth to budget for.
     *
     * <p>Issue #873 (M6 epic #824's JC3 reversal) turned out to be one more: the two new
     * unconditional gem materials (emerald, amethyst) and the three PlusTiC-inspiration alloys
     * (alumite, osgloglas, osmiridium) push the roster to 143 materials, 106,921 bytes -- just over
     * the 104 KB line. Raised to 108 KB (110,592 bytes), ~3.6 KB (3%) of headroom above the new
     * measurement, same deliberately-tight-rather-than-multiplier approach as the #872 raise above.
     */
    private static final int SYNC_BUDGET_BYTES = 108 * 1024;

    private static RegistryOps<JsonElement> jsonOps;
    private static RegistryOps<Tag> nbtOps;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        RegistryAccess.Frozen registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        jsonOps = RegistryOps.create(JsonOps.INSTANCE, registries);
        nbtOps = RegistryOps.create(NbtOps.INSTANCE, registries);
    }

    @Test
    void shippedMaterialsFitTheSyncBudget() throws Exception {
        Path materialDir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/material");
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        int materials = 0;

        try (Stream<Path> files = Files.list(materialDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                materials++;
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                Material material = Material.CODEC.parse(jsonOps, json).getOrThrow();
                buf.writeNbt(Material.CODEC.encodeStart(nbtOps, material).getOrThrow());
            }
        }

        // Non-vacuity: an empty walk encoding zero bytes would prove nothing.
        assertTrue(materials >= 11, "expected at least the 11 pre-M3.2 materials, walked only " + materials);
        System.out.println("[#872] material sync payload: " + materials + " materials encode to "
                + buf.readableBytes() + " bytes (budget " + SYNC_BUDGET_BYTES + ")");
        assertTrue(buf.readableBytes() <= SYNC_BUDGET_BYTES,
                materials + " materials encode to " + buf.readableBytes() + " bytes of registry-sync payload, "
                        + "over the " + SYNC_BUDGET_BYTES + "-byte budget -- either a material grew far beyond "
                        + "the roster's norm or the budget needs a deliberate revisit (SCOPE.md performance budgets)");
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }
}
