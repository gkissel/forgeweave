package dev.gkissel.forgeweave.compat.create;

import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * The Create-free half of Forgeweave's goggles compat (issue #1007, toggle from #968): whether a
 * helmet counts as goggles, and the {@code compat.createGoggles} toggle that answers for it.
 *
 * <p>Split out of {@link ForgeweaveCreateCompat} for the reason {@code DraconicModules} is split out
 * of {@code DraconicModuleHost}: that class names a {@code com.simibubi.create} type, so it is only
 * loadable on an install with Create present and cannot be reached by {@code runGameTestServer},
 * where the toggle's off path is tested ({@code CompatToggleGameTests}). Nothing here names a Create
 * type, so this class loads everywhere.
 *
 * <p>Off is inert, never destructive: the modifier stays on the helmet and is listed in its tooltip
 * exactly as before, and Create's overlays fire for it again the moment the toggle returns. Nothing
 * about the stack changes either way.
 */
public final class CreateGoggles {

    /**
     * Whether {@code helmet} carries the {@code forgeweave:goggles} modifier and the toggle is on. A
     * plain function of the stack, so it is unit testable with Create absent from the classpath
     * altogether.
     */
    public static boolean isWearingGoggles(ItemStack helmet) {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.CREATE_GOGGLES)
                && ForgeweaveModifiers.entry(helmet, ForgeweaveModifiers.GOGGLES_ID) != null;
    }

    private CreateGoggles() {}
}
