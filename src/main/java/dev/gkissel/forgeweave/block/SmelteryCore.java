package dev.gkissel.forgeweave.block;

import java.util.Locale;
import java.util.function.Supplier;

import net.minecraft.util.StringRepresentable;
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
 *
 * <p>#845 adds the top two tiers, reached only by pour-to-transform ({@link
 * dev.gkissel.forgeweave.recipe.CoreTransformRecipe}) rather than a crafting recipe: pouring molten
 * dragon breath over a Nether Core yields an End Core, and pouring deep blood over an End Core yields
 * a Deep Core. Their multipliers (2.5x, 3.0x) are the maintainer decision proposed on #845's thread,
 * settling docs/SCOPE.md's long-standing open question -- see that issue for the full four-row table.
 */
public enum SmelteryCore implements StringRepresentable {
    STANDARD("standard_core", 1.5F),
    NETHER("nether_core", 2.0F),
    END("end_core", 2.5F),
    DEEP("deep_core", 3.0F);

    private final String id;
    private final float yieldMultiplier;

    SmelteryCore(String id, float yieldMultiplier) {
        this.id = id;
        this.yieldMultiplier = yieldMultiplier;
    }

    public String id() {
        return id;
    }

    /** The {@link TieredSearedBricksBlock#TIER} blockstate value: {@code standard}, {@code nether}, {@code end}, {@code deep}. */
    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Ore yield multiplier applied to a melting recipe's base amount. */
    public float yieldMultiplier() {
        return yieldMultiplier;
    }

    public Supplier<BlockEntityType<SmelteryControllerBlockEntity>> blockEntityType() {
        return switch (this) {
            case STANDARD -> ForgeweaveBlockEntities.STANDARD_CORE;
            case NETHER -> ForgeweaveBlockEntities.NETHER_CORE;
            case END -> ForgeweaveBlockEntities.END_CORE;
            case DEEP -> ForgeweaveBlockEntities.DEEP_CORE;
        };
    }
}
