package dev.gkissel.forgeweave.block;

import java.util.function.Supplier;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.menu.PartBuilderRecipes;

/**
 * The two 1.12 storage-station chests (docs/SCOPE.md M1 issue #66): the Pattern Chest, which accepts
 * patterns and (issue #100/#222/#292's casts, #477/T46) casts too, and the Part Chest, which only
 * accepts tool parts. Upstream's {@code TilePatternChest}/{@code TilePartChest} (NOTICE.md) are
 * otherwise near-identical -- same base class, same dynamic-inventory/GUI machinery, differing only
 * in {@code isItemValidForSlot} -- so this is one {@link ChestBlock}/{@link ChestBlockEntity}/{@code
 * ChestMenu}/{@code ChestScreen} implementation parameterized by kind rather than two duplicated
 * class families.
 *
 * <p><b>#477/T46 -- the actual filter rules, ported from upstream's {@code isItemValidForSlot}</b>
 * (the parity audit's own read of the previous version of this file was right: duplicates were
 * allowed and casts were never accepted at all):
 *
 * <ul>
 *   <li><b>Pattern Chest.</b> Accepts {@link ForgeweaveItems#PATTERN_BLANK}, any part pattern
 *       ({@link PartBuilderRecipes#isPattern}), or any cast ({@link #CASTS}, gold or clay -- upstream's
 *       {@code clayCast} is a plain {@code Cast} instance and so is itself an {@code ICast}). An empty
 *       chest accepts any of those; once it holds something, upstream's {@code castChest} flag pins
 *       the whole chest to "patterns only" or "casts only" for as long as it holds anything (mixing is
 *       refused). Within that, only one of each distinct part is allowed -- upstream finds this by
 *       comparing the NBT-tagged part identity {@code Pattern.getPartFromTag} reads off its single
 *       shared {@code Pattern}/{@code Cast} item; Forgeweave registers one item per pattern/cast
 *       instead (this class's pre-existing javadoc, issue #93's precedent), so the same part identity
 *       is already exactly the {@link ItemStack#getItem()} identity and the comparison is simpler.
 *       <p><b>Deviation (recorded per the 1.12-parity directive):</b> upstream's per-slot dedup loop
 *       also happens to reject a cast whose underlying {@code Item} differs from an already-present
 *       cast's -- in upstream's single-item architecture that only ever fires between the gold
 *       {@code cast} item and the clay {@code clayCast} item (since every *part* cast shares one of
 *       those two item instances, NBT-tagged), and the loop's own comment says its intent is just
 *       "only the same item (== cast or pattern)" -- i.e. exactly what the earlier {@code castChest}
 *       check already guarantees. That check is redundant on upstream's own architecture for anything
 *       but gold-vs-clay, and Forgeweave's one-item-per-part split has no single shared "the cast
 *       item" to key it off in the first place, so porting a literal "no mixing gold and clay casts"
 *       rule would be inventing a new restriction from an accidental side effect, not preserving one.
 *       This class does not add it; a cast chest freely mixes gold and clay casts, matching the
 *       dedup loop's stated intent.
 *   <li><b>Part Chest.</b> Accepts any {@link PartItem}. A duplicate of an item already present is
 *       only accepted into the same slot it already occupies (upstream's {@code i == slot}) -- so
 *       stacking a duplicate grows its existing stack instead of spreading across new slots, and a
 *       genuinely new part still gets a fresh one.
 * </ul>
 *
 * <p>Stack size: the Pattern/Cast Chest limits every slot to 1 (upstream's {@code
 * TilePatternChest(MAX_INVENTORY, 1)}), so even the very first copy of a pattern or cast can't stack
 * past one -- {@link ChestBlockEntity}'s container reads {@link #maxStackSize()}. The Part Chest keeps
 * the default (matching upstream, which never overrides it there).
 */
public enum ChestKind {
    PATTERN("pattern_chest", 1) {
        @Override
        public boolean accepts(ItemStack stack, Container container, int slot) {
            if (!isPatternOrCast(stack)) {
                return false;
            }
            boolean hasContents = false;
            boolean castChest = false;
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack existing = container.getItem(i);
                if (existing.isEmpty()) {
                    continue;
                }
                hasContents = true;
                if (isCast(existing)) {
                    castChest = true;
                }
            }
            if (!hasContents) {
                return true;
            }
            if (castChest != isCast(stack)) {
                return false;
            }
            for (int i = 0; i < container.getContainerSize(); i++) {
                if (container.getItem(i).is(stack.getItem())) {
                    return false;
                }
            }
            return true;
        }
    },
    PART("part_chest", 99) {
        @Override
        public boolean accepts(ItemStack stack, Container container, int slot) {
            for (int i = 0; i < container.getContainerSize(); i++) {
                ItemStack existing = container.getItem(i);
                if (!existing.isEmpty() && ItemStack.isSameItemSameComponents(existing, stack)) {
                    return i == slot;
                }
            }
            return stack.getItem() instanceof PartItem;
        }
    };

    /** Every cast item, gold or clay (issue #100/#222/#292) -- filled by {@code ForgeweaveItemTagsProvider}. */
    public static final TagKey<Item> CASTS =
            TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "casts"));

    private final String id;
    private final int maxStackSize;

    ChestKind(String id, int maxStackSize) {
        this.id = id;
        this.maxStackSize = maxStackSize;
    }

    public String id() {
        return id;
    }

    /** The container's per-slot stack cap for this kind -- see the class javadoc's stack-size note. */
    public int maxStackSize() {
        return maxStackSize;
    }

    /** Whether {@code stack} may go into {@code slot} of {@code container} right now -- see the class javadoc. */
    public abstract boolean accepts(ItemStack stack, Container container, int slot);

    private static boolean isPatternOrCast(ItemStack stack) {
        return stack.is(ForgeweaveItems.PATTERN_BLANK.get()) || PartBuilderRecipes.isPattern(stack) || isCast(stack);
    }

    /** Whether {@code stack} is a cast (gold or clay) -- also drives the Pattern Chest's "Cast Chest" display name. */
    public static boolean isCast(ItemStack stack) {
        return stack.is(CASTS);
    }

    public Supplier<BlockEntityType<ChestBlockEntity>> blockEntityType() {
        return switch (this) {
            case PATTERN -> ForgeweaveBlockEntities.PATTERN_CHEST;
            case PART -> ForgeweaveBlockEntities.PART_CHEST;
        };
    }
}
