package dev.gkissel.forgeweave.compat.mysticalagriculture;

import java.util.EnumSet;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import com.blakebr0.mysticalagriculture.api.tinkering.AugmentType;
import com.blakebr0.mysticalagriculture.api.tinkering.ITinkerable;

import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.MeleeWeaponItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.AoeHarvest;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The compat item subclasses that make Forgeweave gear {@code ITinkerable} (issue #999, D-M8-20), and
 * the half of the registration factory that names Mystical Agriculture. {@code CompatItems} in the
 * parent package is the mod-free front door every call site in {@code ForgeweaveItems} goes through;
 * this class exists so that the branch which constructs a tinkerable item is in a class the JVM only
 * ever loads once Mystical Agriculture is confirmed present. Putting the {@code new} in
 * {@code CompatItems} itself would not do: verifying that method requires proving the subclass is
 * assignable to {@code ToolItem}, which loads the subclass, which loads {@code ITinkerable}.
 *
 * <h2>The limitation, and why it is here rather than hidden</h2>
 *
 * <p>Verified against {@code MysticalAgriculture:1.21.1-8.0.28:api}: <b>not one method on
 * {@code ITinkerable} takes an {@code ItemStack}</b>.
 *
 * <pre>
 * int getAugmentSlots();
 * EnumSet&lt;AugmentType&gt; getAugmentTypes();
 * int getTinkerableTier();
 * default boolean canApplyAugment(Augment);     // augment types overlap AND augment.getTier() &lt;= getTinkerableTier()
 * </pre>
 *
 * <p>Every caller reaches it as {@code stack.getItem() instanceof ITinkerable} and then reads the
 * <em>Item instance's</em> fields -- {@code AugmentSlot#isActive}, {@code AugmentSlot#mayPlace},
 * {@code AugmentUtils#addAugment}, {@code TinkerableHandler}, {@code MobDropHandler}. Mystical
 * Agriculture's own model is one item id per essence tier ({@code supremium_sword} is tier 5 with one
 * slot, {@code awakened_supremium_sword} is tier 5 with two), so there is no stack-aware seam to hand
 * a per-material answer to, and no capability, event or tag that substitutes for the interface.
 *
 * <p>Forgeweave's tier is per stack: it lives in {@code forgeweave:tool_materials}, and one item id
 * covers every material. So these classes answer with a constant, and the per-material answers #999
 * specifies live in {@link MysticalAugments#tierOf} and {@link MysticalAugments#augmentSlots} where
 * they are computed and tested, waiting for a seam that can carry them. The constant is chosen to
 * under-grant rather than over-grant: one slot, which is the base of #999's "one, or two for
 * awakened", so awakened gear does not get its second slot yet. The tier is the ceiling, 5, because
 * the tier number only widens which of Mystical Agriculture's own augments may be installed and a
 * lower number would leave most of them inapplicable for no gain. The PR spells this out for the
 * maintainer.
 */
public final class MysticalItems {

    private MysticalItems() {}

    /** A tool, tinkerable. Mirrors {@code ToolItem}'s funnel constructor one for one. */
    public static ToolItem tool(Item.Properties properties, List<TagKey<Block>> mineableBlocks,
            float attackSpeed, float damagePotential, float miningSpeedModifier, float damageCutoff,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate, AoeHarvest.Shape aoeShape) {
        return new TinkerableToolItem(properties, mineableBlocks, attackSpeed, damagePotential,
                miningSpeedModifier, damageCutoff, weapon, innate, aoeShape);
    }

    /** A sword-family weapon, tinkerable. Mirrors {@code MeleeWeaponItem}'s constructor. */
    public static ToolItem meleeWeapon(Item.Properties properties, ToolConstants.Entry constants,
            List<TagKey<Block>> mineableBlocks, boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        return new TinkerableMeleeWeaponItem(properties, constants, mineableBlocks, weapon, innate);
    }

    /** An armour piece, tinkerable. Mirrors {@code ArmorPieceItem}'s constructor. */
    public static ArmorPieceItem armor(ArmorItem.Type type, boolean heavy, Item.Properties properties) {
        return new TinkerableArmorPieceItem(type, heavy, properties);
    }

    /**
     * The slot count every tinkerable Forgeweave item reports, or zero while the toggle is off. Zero
     * is what makes the off path work at runtime with no restart: {@code AugmentSlot#isActive} is
     * {@code slot < getAugmentSlots()}, so no augment slot is usable, and
     * {@code AugmentUtils#addAugment} refuses for the same reason. Nothing already installed is
     * touched -- the augment list is Mystical Agriculture's own {@code equipped_augments} component,
     * which Forgeweave never writes.
     */
    private static int slots() {
        return MysticalAugments.enabled() ? EssenceTier.SUPREMIUM.augmentSlots() : 0;
    }

    /** The tinkerable tier, or zero while the toggle is off -- see {@link #slots()}. */
    private static int tier() {
        return MysticalAugments.enabled() ? EssenceTier.SUPREMIUM.value() : 0;
    }

    /**
     * Which augment types a tool claims. {@code TOOL} and {@code WEAPON} only, plus whichever of
     * Mystical Agriculture's shape types the tool's own {@code mineable/*} tags line up with, which is
     * information {@code ToolItem} already carries and needs no per-call-site table.
     */
    private static EnumSet<AugmentType> toolTypes(List<TagKey<Block>> mineableBlocks) {
        EnumSet<AugmentType> types = EnumSet.of(AugmentType.TOOL, AugmentType.WEAPON);
        if (mineableBlocks.contains(BlockTags.MINEABLE_WITH_PICKAXE)) {
            types.add(AugmentType.PICKAXE);
        }
        if (mineableBlocks.contains(BlockTags.MINEABLE_WITH_SHOVEL)) {
            types.add(AugmentType.SHOVEL);
        }
        if (mineableBlocks.contains(BlockTags.MINEABLE_WITH_AXE)) {
            types.add(AugmentType.AXE);
        }
        if (mineableBlocks.contains(BlockTags.MINEABLE_WITH_HOE)) {
            types.add(AugmentType.HOE);
        }
        if (mineableBlocks.contains(BlockTags.SWORD_EFFICIENT)) {
            types.add(AugmentType.SWORD);
        }
        return types;
    }

    /** The armour types a piece claims: {@code ARMOR} plus its own slot. */
    private static EnumSet<AugmentType> armorTypes(ArmorItem.Type type) {
        return EnumSet.of(AugmentType.ARMOR, switch (type) {
            case HELMET -> AugmentType.HELMET;
            case CHESTPLATE -> AugmentType.CHESTPLATE;
            case LEGGINGS -> AugmentType.LEGGINGS;
            case BOOTS -> AugmentType.BOOTS;
            // Vanilla's BODY type is for animal armour and no Forgeweave item registers one.
            case BODY -> AugmentType.ARMOR;
        });
    }

    /** {@code ToolItem} plus {@code ITinkerable}, and nothing else. */
    private static final class TinkerableToolItem extends ToolItem implements ITinkerable {

        private final EnumSet<AugmentType> augmentTypes;

        private TinkerableToolItem(Item.Properties properties, List<TagKey<Block>> mineableBlocks,
                float attackSpeed, float damagePotential, float miningSpeedModifier, float damageCutoff,
                boolean weapon, @Nullable ForgeweaveInnates.Innate innate, AoeHarvest.Shape aoeShape) {
            super(properties, mineableBlocks, attackSpeed, damagePotential, miningSpeedModifier,
                    damageCutoff, weapon, innate, aoeShape);
            this.augmentTypes = toolTypes(mineableBlocks);
        }

        @Override
        public int getAugmentSlots() {
            return slots();
        }

        @Override
        public EnumSet<AugmentType> getAugmentTypes() {
            return augmentTypes;
        }

        @Override
        public int getTinkerableTier() {
            return tier();
        }
    }

    /** {@code MeleeWeaponItem} plus {@code ITinkerable}. */
    private static final class TinkerableMeleeWeaponItem extends MeleeWeaponItem implements ITinkerable {

        private final EnumSet<AugmentType> augmentTypes;

        private TinkerableMeleeWeaponItem(Item.Properties properties, ToolConstants.Entry constants,
                List<TagKey<Block>> mineableBlocks, boolean weapon,
                @Nullable ForgeweaveInnates.Innate innate) {
            super(properties, constants, mineableBlocks, weapon, innate);
            this.augmentTypes = toolTypes(mineableBlocks);
        }

        @Override
        public int getAugmentSlots() {
            return slots();
        }

        @Override
        public EnumSet<AugmentType> getAugmentTypes() {
            return augmentTypes;
        }

        @Override
        public int getTinkerableTier() {
            return tier();
        }
    }

    /** {@code ArmorPieceItem} plus {@code ITinkerable}. */
    private static final class TinkerableArmorPieceItem extends ArmorPieceItem implements ITinkerable {

        private final EnumSet<AugmentType> augmentTypes;

        private TinkerableArmorPieceItem(ArmorItem.Type type, boolean heavy, Item.Properties properties) {
            super(type, heavy, properties);
            this.augmentTypes = armorTypes(type);
        }

        @Override
        public int getAugmentSlots() {
            return slots();
        }

        @Override
        public EnumSet<AugmentType> getAugmentTypes() {
            return augmentTypes;
        }

        @Override
        public int getTinkerableTier() {
            return tier();
        }
    }
}
