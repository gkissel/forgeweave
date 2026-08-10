package dev.gkissel.forgeweave.block;

import java.util.function.Supplier;

import net.minecraft.world.level.block.entity.BlockEntityType;

/**
 * The two smeltery core tiers (docs/SCOPE.md M2 content manifest and acceptance test step 4:
 * "replace the Standard Core with a Nether Core (netherite-built) and observe 2x yields"). The core
 * is the controller block itself, so which tier a formed structure has is simply which block is
 * sitting in its wall -- no separate tier field to keep in sync, and swapping tiers is swapping the
 * block, exactly as the acceptance test describes.
 *
 * <p>Tiered cores have no upstream 1.12 equivalent (there is one smeltery controller there); the
 * tier and its multipliers come from SCOPE.md. {@link #yieldMultiplier()} is consumed by the melting
 * work (issues #96/#99) -- melting recipes carry a base amount and the core multiplies it.
 */
public enum SmelteryCore {
    STANDARD("standard_core", 1.5F),
    NETHER("nether_core", 2.0F);

    private final String id;
    private final float yieldMultiplier;

    SmelteryCore(String id, float yieldMultiplier) {
        this.id = id;
        this.yieldMultiplier = yieldMultiplier;
    }

    public String id() {
        return id;
    }

    /** Ore yield multiplier applied to a melting recipe's base amount. */
    public float yieldMultiplier() {
        return yieldMultiplier;
    }

    public Supplier<BlockEntityType<SmelteryControllerBlockEntity>> blockEntityType() {
        return switch (this) {
            case STANDARD -> ForgeweaveBlockEntities.STANDARD_CORE;
            case NETHER -> ForgeweaveBlockEntities.NETHER_CORE;
        };
    }
}
