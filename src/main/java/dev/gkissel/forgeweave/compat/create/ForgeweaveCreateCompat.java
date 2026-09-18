package dev.gkissel.forgeweave.compat.create;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import com.simibubi.create.content.equipment.goggles.GogglesItem;

import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

/**
 * Forgeweave's Create integration (issue #1007, docs/SCOPE.md M8): teaches Create's own goggle
 * overlays (stress units, fluid contents, goggle tooltips -- {@code GogglesItem#isWearingGoggles})
 * to also fire for a helmet carrying the {@code forgeweave:goggles} modifier. {@code Forgeweave}
 * calls {@link #register} behind a {@code ModList.get().isLoaded(MODID)} check, so this is the only
 * class that may name a {@code com.simibubi.create} type -- {@code CreateSourceIsolationTest} guards
 * that, the same pattern {@code ForgeweaveDraconicCompat} set for Draconic Evolution.
 *
 * <p>Verified 2026-09-18 against {@code com.simibubi.create:create-1.21.1:6.0.10-280:slim} (javap on
 * {@code GogglesItem.class}, per the issue): {@code public static synchronized void
 * addIsWearingPredicate(java.util.function.Predicate<Player>)}, a one-shot registration into a
 * static list {@code GogglesItem.isWearingGoggles} iterates. No live client run with Create
 * installed has exercised this yet -- see the PR for what has and hasn't been checked.
 */
public final class ForgeweaveCreateCompat {

    /** Create's mod id -- the {@code ModList} guard and the recipe's {@code neoforge:mod_loaded} condition. */
    public static final String MODID = "create";

    /**
     * Whether {@code helmet} carries the {@code forgeweave:goggles} modifier -- a plain function of
     * the stack so it can be unit tested without Create on the classpath at all. {@link #register}'s
     * lambda is the only place this ever gets wrapped as the {@code Predicate<Player>} Create's API
     * asks for.
     */
    public static boolean isWearingGoggles(ItemStack helmet) {
        return ForgeweaveModifiers.entry(helmet, ForgeweaveModifiers.GOGGLES_ID) != null;
    }

    /** Registers {@link #isWearingGoggles} with Create. Called only once Create is confirmed present. */
    public static void register() {
        GogglesItem.addIsWearingPredicate(player -> isWearingGoggles(player.getItemBySlot(EquipmentSlot.HEAD)));
    }

    private ForgeweaveCreateCompat() {}
}
