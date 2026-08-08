package dev.gkissel.forgeweave.block;

import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

/**
 * The two 1.12 storage-station chests (docs/SCOPE.md M1 issue #66): the Pattern Chest, which only
 * accepts pattern items, and the Part Chest, which only accepts tool parts. Upstream's {@code
 * TilePatternChest}/{@code TilePartChest} (NOTICE.md) are otherwise near-identical -- same base
 * class, same dynamic-inventory/GUI machinery, differing only in {@code isItemValidForSlot} -- so
 * this is one {@link ChestBlock}/{@link ChestBlockEntity}/{@code ChestMenu}/{@code ChestScreen}
 * implementation parameterized by kind rather than two duplicated class families.
 *
 * <p>The Part Chest's filter is exactly "{@code itemstack.getItem() instanceof IToolPart}" ported
 * ({@link PartItem}, upstream's {@code IToolPart} equivalent -- including {@link
 * ForgeweaveItems#SHARD}, which reuses {@link PartItem} for its rendering machinery per that field's
 * own javadoc). The Pattern Chest's filter is the blank pattern plus {@link
 * PartBuilderRecipes#isPattern}'s five part patterns -- upstream additionally accepts {@code ICast}
 * (a "cast chest" mode), which Forgeweave has no equivalent of (no smeltery/casting system in scope
 * yet), so that branch is not ported.
 */
public enum ChestKind {
    PATTERN("pattern_chest", stack -> stack.is(ForgeweaveItems.PATTERN_BLANK.get()) || PartBuilderRecipes.isPattern(stack)),
    PART("part_chest", stack -> stack.getItem() instanceof PartItem);

    private final String id;
    private final Predicate<ItemStack> filter;

    ChestKind(String id, Predicate<ItemStack> filter) {
        this.id = id;
        this.filter = filter;
    }

    public String id() {
        return id;
    }

    /** Whether {@code stack} belongs in this chest kind -- enforced by both the GUI slots and the exposed {@code IItemHandler} capability. */
    public boolean accepts(ItemStack stack) {
        return filter.test(stack);
    }

    public Supplier<BlockEntityType<ChestBlockEntity>> blockEntityType() {
        return switch (this) {
            case PATTERN -> ForgeweaveBlockEntities.PATTERN_CHEST;
            case PART -> ForgeweaveBlockEntities.PART_CHEST;
        };
    }
}
