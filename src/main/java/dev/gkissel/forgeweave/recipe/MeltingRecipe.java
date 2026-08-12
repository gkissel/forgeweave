package dev.gkissel.forgeweave.recipe;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.PartItem;

/**
 * What one item melts into inside a smeltery, defined entirely in datapack JSON under
 * {@code data/<namespace>/forgeweave/melting_recipe/<name>.json} (docs/SCOPE.md M2 issue #96).
 * Ported from upstream 1.12's {@code library/smeltery/MeltingRecipe} (NOTICE.md), which is a
 * hardcoded Java registry there and datapack JSON here.
 *
 * <pre>
 * {
 *   "input": {"tag": "c:ores/iron"},
 *   "fluid": "forgeweave:molten_iron",
 *   "amount": 144,
 *   "temperature": 534
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code input} -- a vanilla {@link Ingredient}, so an item or a tag. Tag inputs are what make
 *       docs/SCOPE.md's M2 ladder promise work: the shipped recipes key off the {@code c:} convention
 *       tags ({@code c:ores/iron}, {@code c:ingots/copper}, ...), so a mod that tags its own ore or
 *       ingot melts with no Forgeweave code and no Forgeweave datapack. It is upstream's
 *       {@code registerOredictMeltingCasting} with 1.21's tags in place of 1.12's ore dictionary.
 *   <li>{@code amount} -- the <b>base</b> output in mB, before any core-tier multiplier. Upstream's
 *       ore recipes bake their 2x ore bonus into this number; docs/SCOPE.md splits the two ("melting
 *       recipes hold base amounts, the core multiplies"), so an ore melts here as its raw-drop
 *       equivalent and {@code SmelteryCore#yieldMultiplier} scales it in issue #99. "Raw-drop
 *       equivalent" is read off the block's own loot table: the {@code c:ores/*} recipes assume one
 *       raw unit, and an ore whose expected un-fortuned drop is not one raw unit gets an item-keyed
 *       override that {@link #find} prefers (vanilla copper ore, vanilla nether gold ore).
 *   <li>{@code temperature} -- optional. Left out, it is derived from the output fluid's own
 *       temperature by {@link #calcTemperature}, exactly as upstream's two-argument constructor does.
 *   <li>{@code ore} -- optional, defaults to {@code false}. Marks an ore-class input (a mined ore
 *       block or its raw-material drop): {@link dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity}
 *       multiplies only these by the core's {@code yieldMultiplier} (issue #99). Ingot, nugget, and
 *       storage-block recipes leave this {@code false} so re-melting a refined metal always stays 1:1,
 *       per docs/SCOPE.md M2 ("core tier is the ONLY yield axis; ingot re-melts 1:1").
 * </ul>
 */
public record MeltingRecipe(Ingredient input, Fluid fluid, int amount, int temperature, boolean ore) {

    /** Upstream {@code Material.VALUE_Nugget}: a ninth of an ingot. */
    public static final int VALUE_NUGGET = 16;
    /** Upstream {@code Material.VALUE_Ingot}. */
    public static final int VALUE_INGOT = 144;
    /** Upstream {@code Material.VALUE_Block}: nine ingots, and {@link #calcTemperature}'s baseline. */
    public static final int VALUE_BLOCK = VALUE_INGOT * 9;

    /**
     * Where both upstream and Forgeweave put "cold": a recipe or a smeltery at 300 is at zero working
     * heat. Upstream's {@code getUsableTemperature} and its {@code getTemperature() - 300} fuel
     * conversion are the two halves of this one constant.
     */
    public static final int AMBIENT_TEMPERATURE = 300;

    /**
     * Upstream {@code TileHeatingStructure.TIME_FACTOR}: melt progress is tracked at eight times the
     * required temperature so a slow smeltery still makes visible progress each step.
     */
    public static final int TIME_FACTOR = 8;

    /** Upstream {@code MeltingRecipe.LOG9_2}, i.e. log base 9 of 2: a ninth of a block melts at half a block's heat. */
    private static final double LOG9_2 = 0.31546487678;

    public static final ResourceKey<Registry<MeltingRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "melting_recipe"));

    public static final Codec<MeltingRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Ingredient.CODEC.fieldOf("input").forGetter(MeltingRecipe::input),
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(MeltingRecipe::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(MeltingRecipe::amount),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("temperature").forGetter(recipe -> Optional.of(recipe.temperature())),
            Codec.BOOL.optionalFieldOf("ore", Boolean.FALSE).forGetter(MeltingRecipe::ore))
            .apply(instance, MeltingRecipe::withDerivedTemperature));

    private static MeltingRecipe withDerivedTemperature(Ingredient input, Fluid fluid, int amount, Optional<Integer> temperature, boolean ore) {
        return new MeltingRecipe(input, fluid, amount,
                temperature.orElseGet(() -> calcTemperature(fluid.getFluidType().getTemperature(), amount)), ore);
    }

    /**
     * Upstream {@code MeltingRecipe.calcTemperature}: a full block of the metal melts at the fluid's
     * own temperature, and every ninth of that costs half the heat above {@link #AMBIENT_TEMPERATURE}
     * -- so an ingot sits halfway, a nugget a quarter of the way.
     */
    public static int calcTemperature(int fluidTemperature, int amount) {
        int headroom = Math.max(0, fluidTemperature - AMBIENT_TEMPERATURE);
        double fraction = Math.pow((double) amount / (double) VALUE_BLOCK, LOG9_2);
        return AMBIENT_TEMPERATURE + (int) (fraction * (double) headroom);
    }

    /** Upstream {@code MeltingRecipe#getUsableTemperature}: {@link #temperature} on the smeltery's own zero-is-300 scale. */
    public int usableTemperature() {
        return Math.max(1, temperature - AMBIENT_TEMPERATURE);
    }

    /**
     * Total melt progress this recipe needs, in the units the smeltery accumulates -- upstream's
     * {@code updateHeatRequired} floor of 5 followed by {@code setHeatRequiredForSlot}'s
     * {@link #TIME_FACTOR} scaling.
     */
    public int heatRequired() {
        return Math.max(5, usableTemperature()) * TIME_FACTOR;
    }

    /** The base output; the yield multiplier of the core melting it is issue #99's, not this recipe's. */
    public FluidStack result() {
        return new FluidStack(fluid, amount);
    }

    /**
     * Whether this recipe's input names a tag rather than items. Tag recipes are the family default
     * that covers every mod's version of a metal; an item recipe is a deliberate override for one
     * block whose drops are not one raw unit, and {@link #find} lets it win.
     *
     * <p>A NeoForge custom ingredient refuses to expose its values at all, and counts as specific --
     * it was written for a particular case, which is the same side of the tie-break. That covers the
     * shipped {@code vanilla_copper_ore} recipe, whose two-item list form NeoForge models as one.
     */
    public boolean isTagInput() {
        return !input.isCustom()
                && Arrays.stream(input.getValues()).anyMatch(value -> value instanceof Ingredient.TagValue);
    }

    /**
     * The recipe for {@code stack}, or empty if nothing melts it.
     *
     * <p>Resolution is <b>most specific wins</b>: an item-keyed recipe beats a tag-keyed one, so
     * {@code minecraft:copper_ore}'s 2-5 raw-copper override beats the {@code c:ores/copper} default
     * that every modded copper ore falls back on. Remaining ties break on registry id, so two
     * equally specific overlapping recipes always pick the same winner -- that case is a datapack
     * authoring mistake, not something to resolve cleverly.
     *
     * <p>ponytail: a linear scan of a registry holding a few dozen entries, run once per item
     * inserted rather than per tick. Index it by item if a pack ever makes the scan show up in a
     * profile.
     */
    public static Optional<MeltingRecipe> find(RegistryAccess registries, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        Registry<MeltingRecipe> recipes = registries.registryOrThrow(REGISTRY);
        return recipes.entrySet().stream()
                .filter(entry -> entry.getValue().input.test(stack))
                // false sorts before true, so item inputs come first.
                .min(Comparator.<Map.Entry<ResourceKey<MeltingRecipe>, MeltingRecipe>, Boolean>comparing(
                                entry -> entry.getValue().isTagInput())
                        .thenComparing(entry -> entry.getKey().location()))
                .map(Map.Entry::getValue)
                .or(() -> meltBackOfACastPart(registries, stack));
    }

    /**
     * #184 -- upstream 1.12's {@code TinkerSmeltery#registerToolpartMeltingCasting}: a tool part that
     * was cast from molten metal melts back into exactly what cast it. Upstream registers both
     * directions from one loop over parts and materials; here the casting registry (issue #100) is
     * already that table, so melting reads it backwards rather than restating a part-cost table as
     * another sixty datapack rows that could drift out of step with it.
     *
     * <p>Only tool parts, and only the casting <em>table</em>'s recipes. A cast, an ingot or a metal
     * block has a melting recipe of its own -- this is the last resort, reached only when no datapack
     * recipe matched -- and a part of a material with no molten form (wood, stone, flint, bone) has no
     * casting recipe to read backwards, so it stays unmeltable exactly as upstream leaves it.
     *
     * <p>The result is <b>not</b> ore-class ({@code ore = false}): upstream returns a part's exact
     * value, so no core tier turns a pickaxe head into more metal than went into it.
     *
     * <p>ponytail: a linear scan of a few dozen casting recipes, on the same path (and behind the same
     * per-slot cache) as {@link #find}'s own scan.
     */
    private static Optional<MeltingRecipe> meltBackOfACastPart(RegistryAccess registries, ItemStack stack) {
        ResourceLocation material = stack.get(ForgeweaveDataComponents.MATERIAL.get());
        if (material == null || !(stack.getItem() instanceof PartItem)) {
            return Optional.empty();
        }
        for (CastingRecipe casting : registries.registryOrThrow(CastingRecipe.REGISTRY)) {
            ItemStack result = casting.result();
            if (casting.station() == CastingRecipe.Station.TABLE && result.is(stack.getItem())
                    && material.equals(result.get(ForgeweaveDataComponents.MATERIAL.get()))) {
                // Non-strict: the part still melts with a custom name or any other component the
                // casting recipe never asked about.
                return Optional.of(withDerivedTemperature(DataComponentIngredient.of(false, result),
                        casting.fluid(), casting.amount(), Optional.empty(), false));
            }
        }
        return Optional.empty();
    }

    /**
     * Same "most specific wins" resolution as {@link #find}, but over an explicit snapshot map
     * rather than live {@link RegistryAccess} -- what the JEI plugin already has from its own
     * per-session registry read ({@code dev.gkissel.forgeweave.jei.ForgeweaveJeiPlugin}, the same
     * shape {@code material.Material} is read into for the M1 categories). This is what lets the
     * melting JEI category (issue #109) dedupe an item that both a tag recipe and a more specific
     * item-keyed override match -- e.g. vanilla copper ore under both {@code c:ores/copper} (144)
     * and its own item-keyed override (504) -- without a second copy of {@link #find}'s tie-break
     * rule that could drift from it.
     */
    // #109
    public static Optional<MeltingRecipe> resolve(Map<ResourceLocation, MeltingRecipe> recipes, ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }
        return recipes.entrySet().stream()
                .filter(entry -> entry.getValue().input.test(stack))
                .min(Comparator.<Map.Entry<ResourceLocation, MeltingRecipe>, Boolean>comparing(
                                entry -> entry.getValue().isTagInput())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getValue);
    }
}
