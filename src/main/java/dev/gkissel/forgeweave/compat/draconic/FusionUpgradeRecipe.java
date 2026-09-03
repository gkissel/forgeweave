package dev.gkissel.forgeweave.compat.draconic;

import java.util.List;
import java.util.Optional;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.crafting.IFusionInventory;
import com.brandon3055.draconicevolution.api.crafting.IFusionRecipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Modifier;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * A Forgeweave tool upgrade performed on Draconic Evolution's own Fusion Crafting multiblock (issue
 * #915, docs/SCOPE.md M8). The catalyst in the crafting core is an assembled Forgeweave tool, the
 * injectors hold the tier's Draconic ingredients, and the result is that same tool with one modifier
 * raised to a fixed level.
 *
 * <pre>
 * {
 *   "type": "forgeweave:draconic_fusion_upgrade",
 *   "catalyst": {"tag": "forgeweave:fusion_upgradable"},
 *   "modifier": "forgeweave:haste",
 *   "level": 100,
 *   "ingredients": [
 *     {"ingredient": {"item": "draconicevolution:wyvern_core"}, "consume": true},
 *     {"ingredient": {"item": "minecraft:redstone_block"}, "consume": true}
 *   ],
 *   "totalEnergy": 8000000,
 *   "techLevel": "wyvern",
 *   "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "draconicevolution"}]
 * }
 * </pre>
 *
 * <p>{@code catalyst}, {@code ingredients}, {@code totalEnergy} and {@code techLevel} are spelled
 * exactly as Draconic Evolution's own {@code draconicevolution:fusion_crafting} rows spell them, so
 * the two schemas read the same way to a pack author. {@code modifier} + {@code level} replace DE's
 * {@code result}: the result is a function of whatever tool the player put in the core rather than a
 * fixed stack, which is the whole reason this needs a recipe class of its own instead of another DE
 * {@code FusionRecipe} row.
 *
 * <p>{@code level} counts application units, the same unit {@link ModifierEntry#level} stores, not
 * display levels -- a haste tier of {@code 100} is the 100th redstone, i.e. Haste II.
 *
 * <p><b>Licensing.</b> Draconic Evolution ships under the "Don't Be a Jerk" license, which permits
 * addons depending on it as a library but not code derivation, so ADR-0003 treats it the way it
 * treats the inspiration-only clones: its API is compiled against and called, and nothing here is
 * copied from it. That is also why no {@code NOTICE.md} row exists for this file.
 *
 * <p><b>Isolation.</b> This package is the only one that names a {@code com.brandon3055} type
 * ({@code DraconicSourceIsolationTest} enforces that), and nothing outside it classloads this class
 * unless {@link ForgeweaveDraconicCompat#register} ran -- the {@code jade}/{@code kubejs}/{@code jei}
 * soft-dependency idiom, see build.gradle's comment on the dependency.
 *
 * <p><b>JEI.</b> These rows need no JEI code of Forgeweave's and get none. Draconic Evolution's own
 * plugin collects the category's contents with
 * {@code recipeManager.getAllRecipesFor(DraconicAPI.FUSION_RECIPE_TYPE)}, and both its fusion
 * category and its recipe transfer handler are generic over {@code RecipeHolder<IFusionRecipe>}
 * rather than over its own {@code FusionRecipe} class -- there is no {@code instanceof} anywhere in
 * that package. Its crafting core resolves an in-world craft through the same recipe type. So
 * implementing the interface and leaving {@link IFusionRecipe#getType()} at its default is the
 * whole of what showing up takes: no {@code IRecipeCategoryExtension}, no plugin class, nothing to
 * keep in step with DE's GUI. What that costs is {@link #getResultItem}, which DE's category draws
 * in the output slot -- see its note.
 *
 * <p><b>Slot budget.</b> A fusion upgrade deliberately does not spend a modifier slot the way the
 * Tool Station's {@link ModifierApplication#apply} does. What it costs instead is the tier's
 * Draconic materials and its RF, which is what a player standing at that tier actually pays with;
 * charging a slot on top would leave the endgame path strictly worse than the Tool Station's. That
 * is the one deviation from Forgeweave's own modifier economy this class makes, and it is why
 * {@link ModifierApplication#applyLevel} sits next to {@code apply} rather than being folded into it.
 */
public record FusionUpgradeRecipe(Ingredient catalyst, ResourceLocation modifier, int level,
        List<Entry> ingredients, long totalEnergy, TechLevel techLevel) implements IFusionRecipe {

    /**
     * One injector's ingredient. Field for field Draconic Evolution's own
     * {@code {"ingredient": ..., "consume": ...}} entry, restated here rather than reused because
     * DE's codec for that shape is private; the interface it satisfies is the public API one.
     */
    public record Entry(Ingredient ingredient, boolean consume) implements IFusionRecipe.IFusionIngredient {

        public static final MapCodec<Entry> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(Entry::ingredient),
                Codec.BOOL.optionalFieldOf("consume", true).forGetter(Entry::consume))
                .apply(instance, Entry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                Ingredient.CONTENTS_STREAM_CODEC, Entry::ingredient,
                ByteBufCodecs.BOOL, Entry::consume,
                Entry::new);

        @Override
        public Ingredient get() {
            return ingredient;
        }
    }

    @Override
    public TechLevel getRecipeTier() {
        return techLevel;
    }

    @Override
    public long getEnergyCost() {
        return totalEnergy;
    }

    @Override
    public List<IFusionRecipe.IFusionIngredient> fusionIngredients() {
        return List.copyOf(ingredients);
    }

    @Override
    public Ingredient getCatalyst() {
        return catalyst;
    }

    /**
     * Draconic Evolution's own catalyst/injector matching, plus "and this upgrade would actually
     * change this tool". Without the second half, a tool already at the tier's level -- or one whose
     * shape the modifier refuses, a bow under a harvest-only modifier -- would start a craft that
     * produced nothing.
     */
    @Override
    public boolean matches(IFusionInventory inventory, Level level) {
        return IFusionRecipe.super.matches(inventory, level)
                && upgrade(level.registryAccess(), inventory.getCatalystStack()).isPresent();
    }

    @Override
    public ItemStack assemble(IFusionInventory inventory, HolderLookup.Provider registries) {
        return upgrade(registries, inventory.getCatalystStack()).orElse(ItemStack.EMPTY);
    }

    /**
     * The upgraded tool, or empty when this recipe has nothing to give {@code tool}: it is not an
     * assembled Forgeweave tool, it does not carry {@code evolved} at this tier's level, the
     * modifier id is not registered, the modifier refuses the tool's shape
     * ({@link ModifierApplication#acceptsToolShape}), the tool carries something the modifier cannot
     * sit beside, or the tool already sits at or above this tier's level. The last case is what keeps
     * a line's four tiers from all matching at once and makes a player climb them in order.
     *
     * <p>The {@code evolved} check (issue #946) is the parity target's own rule: fusion crafting
     * only upgrades a tool already made of a fusion metal, and only up to that metal's tier. It is
     * read off {@link #techLevel} rather than stored per recipe, since the tier is what decides it --
     * see {@link ForgeweaveDraconicCompat#requiredEvolved}.
     */
    public Optional<ItemStack> upgrade(HolderLookup.Provider registries, ItemStack tool) {
        if (!ToolAssemblyRecipes.isAssembled(tool)) {
            return Optional.empty();
        }
        if (ForgeweaveDraconicCompat.evolvedLevel(tool)
                < ForgeweaveDraconicCompat.requiredEvolved(techLevel.getSerializedName())) {
            return Optional.empty();
        }
        Modifier behavior = ForgeweaveModifiers.get(modifier);
        if (behavior == null || !ModifierApplication.acceptsToolShape(registries, behavior, tool)) {
            return Optional.empty();
        }
        ItemStack upgraded = ModifierApplication.applyLevel(tool, modifier, level).output();
        return upgraded.isEmpty() ? Optional.empty() : Optional.of(upgraded);
    }

    /**
     * What Draconic Evolution's JEI fusion category draws in the output slot. There is no single
     * real result -- it depends on the tool the player brings -- so this is a representative: the
     * first item the catalyst ingredient accepts, carrying the modifier entry this recipe grants so
     * the stack's tooltip names the upgrade. The Tool Station's own JEI category already answers the
     * same question the same way ({@code jei.ModifierApplicationCategory} shows a tool catalog rather
     * than a built result).
     *
     * <p>Empty before tags bind, since recipe load runs ahead of the tag sync a tag ingredient needs.
     * Nothing reads this until a screen asks for it, so that window never shows.
     */
    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        ItemStack[] items = catalyst.getItems();
        if (items.length == 0) {
            return ItemStack.EMPTY;
        }
        ItemStack display = items[0].copy();
        display.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(modifier, level)));
        return display;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return Serializer.INSTANCE;
    }

    /** Registered as {@code forgeweave:draconic_fusion_upgrade} -- see {@link ForgeweaveDraconicCompat}. */
    public static final class Serializer implements RecipeSerializer<FusionUpgradeRecipe> {

        public static final Serializer INSTANCE = new Serializer();

        private static final MapCodec<FusionUpgradeRecipe> CODEC =
                RecordCodecBuilder.mapCodec(instance -> instance.group(
                        Ingredient.CODEC_NONEMPTY.fieldOf("catalyst").forGetter(FusionUpgradeRecipe::catalyst),
                        ResourceLocation.CODEC.fieldOf("modifier").forGetter(FusionUpgradeRecipe::modifier),
                        ExtraCodecs.POSITIVE_INT.fieldOf("level").forGetter(FusionUpgradeRecipe::level),
                        Entry.CODEC.codec().listOf().fieldOf("ingredients").forGetter(FusionUpgradeRecipe::ingredients),
                        Codec.LONG.fieldOf("totalEnergy").forGetter(FusionUpgradeRecipe::totalEnergy),
                        TechLevel.CODEC.fieldOf("techLevel").forGetter(FusionUpgradeRecipe::techLevel))
                        .apply(instance, FusionUpgradeRecipe::new));

        private static final StreamCodec<RegistryFriendlyByteBuf, FusionUpgradeRecipe> STREAM_CODEC =
                StreamCodec.composite(
                        Ingredient.CONTENTS_STREAM_CODEC, FusionUpgradeRecipe::catalyst,
                        ResourceLocation.STREAM_CODEC, FusionUpgradeRecipe::modifier,
                        ByteBufCodecs.VAR_INT, FusionUpgradeRecipe::level,
                        Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), FusionUpgradeRecipe::ingredients,
                        ByteBufCodecs.VAR_LONG, FusionUpgradeRecipe::totalEnergy,
                        TechLevel.STREAM_CODEC, FusionUpgradeRecipe::techLevel,
                        FusionUpgradeRecipe::new);

        @Override
        public MapCodec<FusionUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, FusionUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }

        private Serializer() {}
    }
}
