package dev.gkissel.forgeweave.recipe;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.common.Tags;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SlimeColour;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;

/**
 * Nine slime balls of mixed colour into one slime block (issue #635, parity audit T57). Upstream
 * 1.12's {@code TinkerCommons#registerRecipes} builds this as a {@code ShapedFallbackRecipe}
 * (NOTICE.md): a 3x3 of the {@code slimeball} ore-dict entry, ignored whenever all nine slots hold
 * the same colour so that colour's own recipe wins, producing a pink slime block when
 * {@code matchVanillaSlimeblock} is on and a vanilla slime block when it is off.
 *
 * <p>Two adaptations, both to modern datapack recipes rather than to the behaviour:
 *
 * <ul>
 *   <li><b>It is added, not substituted.</b> Upstream has to <em>replace</em> vanilla's
 *       {@code minecraft:slime} recipe, because in 1.12 an ore-dict 3x3 of slime balls would
 *       otherwise collide with it. Here vanilla's {@code minecraft:slime_block} stays exactly as it
 *       is and this recipe simply never matches nine identical balls -- which covers upstream's
 *       whole ignore list plus its {@code matchVanillaSlimeblock}-dependent green entry, since nine
 *       vanilla slime balls are then vanilla's own recipe and nine of any colour are that colour's.
 *       Same outputs for every grid, one fewer overridden vanilla file.
 *   <li><b>The config is read at match time</b>, not baked into the datapack by a load-time
 *       condition, which is the convention every other Forgeweave toggle follows (see
 *       {@link ForgeweaveConfig#ADD_FLINT_RECIPE}'s javadoc).
 * </ul>
 */
public class MixedSlimeBlockRecipe extends CustomRecipe {

    public MixedSlimeBlockRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        if (input.width() != 3 || input.height() != 3) {
            return false;
        }
        ItemStack first = input.getItem(0);
        boolean mixed = false;
        for (int i = 0; i < input.size(); i++) {
            ItemStack stack = input.getItem(i);
            if (!stack.is(Tags.Items.SLIMEBALLS)) {
                return false;
            }
            mixed |= !stack.is(first.getItem());
        }
        // Upstream's `ignore` list: nine of one colour belongs to that colour's own recipe.
        return mixed;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        return result();
    }

    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        return result();
    }

    /** Upstream: pink slime block when {@code matchVanillaSlimeblock} is on, vanilla's when it is off. */
    private static ItemStack result() {
        return ForgeweaveConfig.MATCH_VANILLA_SLIMEBLOCK.get()
                ? new ItemStack(ForgeweaveBlocks.slimeFamily(SlimeColour.PINK).slimeBlock().get())
                : new ItemStack(Items.SLIME_BLOCK);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 3 && height >= 3;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ForgeweaveRecipeSerializers.MIXED_SLIME_BLOCK.get();
    }
}
