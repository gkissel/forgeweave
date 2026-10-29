package dev.gkissel.forgeweave.gametest;

import java.util.Optional;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/**
 * Small stand-ins for 1.21.1 calls that 26.1 removed, shared by the GameTests: day time is now a
 * world clock, item stacks save through their codec, and block entities load from a ValueInput.
 */
final class GameTestCompat {
    private GameTestCompat() {
    }

    /** {@code ServerLevel#setDayTime} plus the sky refresh, so the day/night checks see it this tick. */
    static void setDayTime(ServerLevel level, long time) {
        level.clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), time);
        level.environmentAttributes().invalidateTickCache();
        level.updateSkyBrightness();
    }

    /** {@code ItemStack#save(registries)}. */
    static Tag saveStack(HolderLookup.Provider registries, ItemStack stack) {
        return ItemStack.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), stack)
                .getOrThrow(AssertionError::new);
    }

    /** {@code ItemStack#parse(registries, tag)}. */
    static Optional<ItemStack> parseStack(HolderLookup.Provider registries, Tag tag) {
        return ItemStack.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag).result();
    }

    /** {@code BlockEntity#loadWithComponents(tag, registries)}, the client applying an update packet. */
    static void load(BlockEntity blockEntity, CompoundTag tag, HolderLookup.Provider registries) {
        blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
    }

    /** {@code AbstractArrow#getBaseDamage}, gone in 26.1: read back from the arrow's own save. */
    static double baseDamage(AbstractArrow arrow) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, arrow.registryAccess());
        arrow.saveWithoutId(output);
        return output.buildResult().getDoubleOr("damage", Double.NaN);
    }
}
