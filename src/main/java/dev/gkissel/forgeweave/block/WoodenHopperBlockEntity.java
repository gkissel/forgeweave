package dev.gkissel.forgeweave.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Wooden Hopper (docs/SCOPE.md M5, issue #822), ported from upstream 1.12's {@code
 * TileWoodenHopper extends TileEntityHopper} (NOTICE.md): a hopper that moves items at half a
 * vanilla hopper's speed. Upstream's entire delta is one override, {@code
 * setTransferCooldown(ticks) -> super.setTransferCooldown(ticks * 2)}; the modern equivalent of
 * that method is {@link #setCooldown(int)} (renamed by Mojang since 1.12, still public and
 * non-final on {@link HopperBlockEntity}), doubled the same way here. Every internal caller of
 * {@code setCooldown} -- the hopper's own tick, and another hopper pushing into this one -- goes
 * through this override too, since {@code HopperBlockEntity} always calls it virtually on {@code
 * this}/the destination instance.
 *
 * <p>{@code isOnCustomCooldown()} (vanilla's {@code cooldownTime > MOVE_ITEM_SPEED}, used to decide
 * whether an upstream hopper pushing into this one may reset its cooldown to keep a hopper chain in
 * sync) is deliberately left un-overridden: the backing {@code cooldownTime} field is private with
 * no protected accessor, so there is no way to compare it against a doubled threshold from here.
 * Left alone it still behaves sensibly -- our doubled cooldown values (14-16) already read as
 * "on cooldown" against vanilla's un-doubled threshold (8) for nearly the entire wait, so a feeding
 * hopper still correctly treats this one as already delayed rather than stomping its timer.
 */
public class WoodenHopperBlockEntity extends HopperBlockEntity {
    public WoodenHopperBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /**
     * Reports this block entity's own registered {@link BlockEntityType} rather than the private
     * field {@link HopperBlockEntity}'s only constructor hardcodes to vanilla's {@code
     * BlockEntityType.HOPPER}. {@code BlockEntity#getType()} is the NeoForge-added extension point
     * for exactly this ("use getter so correct type is checked for modded subclasses"); without this
     * override, saving and reloading a Wooden Hopper would silently decode it back into a plain
     * vanilla {@link HopperBlockEntity} on the next chunk load, since block-entity NBT
     * deserialization looks the saved type up by {@code getType()}'s registry id alone.
     */
    @Override
    public BlockEntityType<?> getType() {
        return ForgeweaveBlockEntities.WOODEN_HOPPER.get();
    }

    @Override
    public void setCooldown(int cooldownTime) {
        super.setCooldown(cooldownTime * 2);
    }
}
