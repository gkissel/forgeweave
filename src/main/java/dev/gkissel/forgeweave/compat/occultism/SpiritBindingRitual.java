package dev.gkissel.forgeweave.compat.occultism;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.klikli_dev.occultism.common.blockentity.GoldenSacrificialBowlBlockEntity;
import com.klikli_dev.occultism.common.ritual.Ritual;
import com.klikli_dev.occultism.common.ritual.RitualFactory;
import com.klikli_dev.occultism.crafting.recipe.RitualRecipe;
import com.klikli_dev.occultism.registry.OccultismRituals;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Modifier;
import dev.gkissel.forgeweave.modifier.ModifierApplication;

/**
 * A Forgeweave tool upgrade performed on Occultism's own Golden Sacrificial Bowl ritual (issue #997,
 * docs/SCOPE.md M8, D-M8-18). The player draws the ritual's pentacle, lays its ingredients on the
 * surrounding sacrificial bowls, right-clicks an assembled Forgeweave tool onto the golden bowl, and
 * the ritual hands the same tool back with one modifier bound into it.
 *
 * <pre>
 * {
 *   "type": "occultism:ritual",
 *   "ritual_type": "forgeweave:bind_soulbound",
 *   "pentacle_id": "occultism:craft_marid",
 *   "activation_item": {"tag": "forgeweave:ritual_bindable"},
 *   "ingredients": [{"item": "occultism:spirit_attuned_gem"}, ...],
 *   "duration": 120,
 *   "ritual_dummy": {"id": "forgeweave:pickaxe", "count": 1, ...},
 *   "result": {"id": "forgeweave:pickaxe", "count": 1, ...},
 *   "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "occultism"}]
 * }
 * </pre>
 *
 * <p><b>Why this is not a Forgeweave recipe type.</b> Issue #997 asks for "a Forgeweave recipe type
 * implementing Occultism's ritual interface", following {@code FusionUpgradeRecipe}. Occultism does
 * not have that extension point, and checking its 1.21.1 source rather than assuming is what turned
 * that up. Its {@code GoldenSacrificialBowlBlockEntity} resolves a ritual by asking the recipe
 * manager for {@code occultism:ritual} specifically and reads {@link RitualRecipe}, a final class
 * with its own serializer; a Forgeweave recipe type would never be looked up. What Occultism
 * <em>does</em> expose is {@code occultism:ritual_factories}, a real NeoForge {@code DeferredRegister}
 * registry of {@link RitualFactory}, and a recipe's {@code ritual_type} field names an entry in it.
 * So the Forgeweave-owned half is a {@link Ritual} subclass registered there, and the rows are
 * Occultism's own recipe type naming it. Same pattern as the fusion upgrade -- a Forgeweave type
 * plugged into another mod's crafting multiblock, one class naming that mod, a registration inside
 * the {@code ModList} guard -- through the seam Occultism actually provides.
 *
 * <p><b>Why not the data route.</b> Two of Occultism's own ritual types come close.
 * {@code occultism:repair} transforms the activation item in place, but only zeroes its damage.
 * {@code occultism:upgrade} copies every data component off {@code ingredients[0]} onto the recipe's
 * fixed {@code result} stack -- which is nearly what is wanted, except it copies <em>onto</em> the
 * result, so a modifier list written into the result JSON is overwritten by the input tool's own and
 * lost; and the result's item id is a literal, so it would take one row per tool item and still not
 * add a level. Neither can apply a modifier, which is why this class exists.
 *
 * <p><b>Slot cost.</b> Unlike a fusion upgrade, a binding <em>does</em> spend the modifier slots the
 * entry occupies -- {@link ModifierApplication#applyLevelSpendingSlots} rather than
 * {@code applyLevel}. A fusion craft's price scales with the tool's tier in Draconic materials and
 * RF; a ritual's price is one pentacle and one pile of otherworld essence, paid once and the same for
 * a wooden pickaxe as for a voidweld one. Charging the slot is what keeps a ritual from being
 * strictly better than the Tool Station route it shortcuts. It is also why a tool with a full slot
 * budget is refused rather than upgraded for free.
 *
 * <p><b>JEI.</b> These rows need no JEI code of Forgeweave's and get none, for the reason
 * {@code FusionUpgradeRecipe} documents: Occultism's own plugin collects the category's contents with
 * {@code recipeManager.getAllRecipesFor(OccultismRecipes.RITUAL_TYPE)} and its
 * {@code RitualRecipeCategory} is generic over {@code RecipeHolder<RitualRecipe>} with no
 * {@code instanceof} anywhere, so an {@code occultism:ritual} row of ours shows up by being one.
 * What that leaves is making the two stacks the category draws read well, which is
 * {@code ForgeweaveOccultismRecipeProvider}'s job: both {@code ritual_dummy} (the row's own icon) and
 * {@code result} are a Forgeweave tool carrying the modifier entry the ritual grants, so the
 * category's output slot shows the modifier by name in its tooltip.
 *
 * <p><b>Isolation.</b> This class is the only one that names a {@code com.klikli_dev} type
 * ({@code OccultismSourceIsolationTest} enforces that), and nothing outside it classloads this class
 * unless {@link ForgeweaveOccultismCompat#register} ran -- the {@code jade}/{@code kubejs}/{@code jei}
 * soft-dependency idiom, see build.gradle's comment on the dependency.
 */
public class SpiritBindingRitual extends Ritual {

    private final ResourceLocation modifier;
    private final int level;

    public SpiritBindingRitual(RitualRecipe recipe, ResourceLocation modifier, int level) {
        super(recipe);
        this.modifier = modifier;
        this.level = level;
    }

    /**
     * Occultism's own validity check, plus "and this binding would actually change this tool".
     * Without the second half the pentacle would light up, the ingredients would burn, and the
     * ritual would hand back the tool it was given.
     */
    @Override
    public boolean isValid(Level level, BlockPos goldenBowlPosition, GoldenSacrificialBowlBlockEntity blockEntity,
            @Nullable Player castingPlayer, ItemStack activationItem,
            List<Ingredient> remainingAdditionalIngredients) {
        return super.isValid(level, goldenBowlPosition, blockEntity, castingPlayer, activationItem,
                remainingAdditionalIngredients)
                && bind(level.registryAccess(), activationItem).isPresent();
    }

    /**
     * Consumes the tool off the golden bowl and drops the bound one back, the shape Occultism's own
     * {@code RepairRitual} uses for a ritual whose output is a function of its activation item.
     */
    @Override
    public void finish(Level level, BlockPos goldenBowlPosition, GoldenSacrificialBowlBlockEntity blockEntity,
            @Nullable ServerPlayer castingPlayer, ItemStack activationItem) {
        super.finish(level, goldenBowlPosition, blockEntity, castingPlayer, activationItem);

        Optional<ItemStack> bound = bind(level.registryAccess(), activationItem);
        if (bound.isEmpty()) {
            // isValid already said yes, so this is only reachable if the tool changed under us
            // between the last tick and now. Leaving the activation item alone is the safe half of
            // that race: the player keeps the tool, and nothing is destroyed.
            return;
        }
        activationItem.shrink(1);
        dropResult(level, goldenBowlPosition, blockEntity, castingPlayer, bound.get(), true);
    }

    /**
     * The bound tool, or empty when this ritual has nothing to give {@code tool}: it is not an
     * assembled Forgeweave tool or the {@code compat.occultismRituals} toggle is off
     * ({@link ForgeweaveOccultismCompat#acceptsRitualTool}), the modifier id is not registered, the
     * modifier refuses the tool's shape ({@link ModifierApplication#acceptsToolShape}), the tool
     * carries something the modifier cannot sit beside, the tool already sits at or above this
     * ritual's level, or the tool has no modifier slots left to spend.
     */
    public Optional<ItemStack> bind(HolderLookup.Provider registries, ItemStack tool) {
        // The shape check plus the toggle live in the Occultism-free half of the package so a
        // GameTest can reach them -- see that method.
        if (!ForgeweaveOccultismCompat.acceptsRitualTool(tool)) {
            return Optional.empty();
        }
        Modifier behavior = ForgeweaveModifiers.get(modifier);
        if (behavior == null || !ModifierApplication.acceptsToolShape(registries, behavior, tool)) {
            return Optional.empty();
        }
        ItemStack one = tool.copy();
        one.setCount(1);
        ItemStack bound = ModifierApplication.applyLevelSpendingSlots(one, modifier, level).output();
        return bound.isEmpty() ? Optional.empty() : Optional.of(bound);
    }

    /** The modifier this ritual binds, and the level it binds it at -- read by the GameTests. */
    public ResourceLocation modifier() {
        return modifier;
    }

    public int level() {
        return this.level;
    }

    /**
     * Registers one {@code occultism:ritual_factories} entry per
     * {@link ForgeweaveOccultismCompat#RITUALS} row, named {@code forgeweave:bind_<modifier>}, which
     * is what each row's {@code ritual_type} names.
     *
     * <p>One factory per ritual rather than one factory reading a custom recipe field, because
     * {@link RitualRecipe} has no field to read: its schema is Occultism's and carries no room for a
     * modifier id. The factory's own name is where the modifier lives, and
     * {@link ForgeweaveOccultismCompat#RITUALS} is the single table both this and the recipe provider
     * walk, so a row and its factory cannot drift apart.
     */
    static void register(IEventBus modEventBus) {
        DeferredRegister<RitualFactory> factories =
                DeferredRegister.create(OccultismRituals.RITUAL_FACTORIES_KEY, Forgeweave.MODID);
        for (ForgeweaveOccultismCompat.Ritual ritual : ForgeweaveOccultismCompat.RITUALS) {
            ResourceLocation modifier = ResourceLocation.parse(ritual.modifier());
            int level = ritual.level();
            factories.register(ritual.factoryName(),
                    () -> new RitualFactory(recipe -> new SpiritBindingRitual(recipe, modifier, level)));
        }
        factories.register(modEventBus);
    }
}
