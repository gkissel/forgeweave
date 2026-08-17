package dev.gkissel.forgeweave.casting;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluid;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ClayCastItem;
import dev.gkissel.forgeweave.menu.ContentFamilies;

/**
 * One casting recipe: what a Casting Table or Casting Basin turns a held item plus a poured fluid
 * into (docs/SCOPE.md M2 issue #100). Ported from upstream 1.12's {@code
 * library/smeltery/CastingRecipe} (NOTICE.md), with upstream's two in-code registries
 * ({@code TinkerRegistry.registerTableCasting}/{@code registerBasinCasting}) replaced by one
 * datapack registry under {@code data/<namespace>/forgeweave/casting_recipe/<name>.json}, the same
 * shape {@code material.Material} and {@code modifier.ModifierRecipe} already use.
 *
 * <pre>
 * {
 *   "station": "table",
 *   "cast": {"item": "forgeweave:cast_pickaxe_head"},
 *   "fluid": "forgeweave:molten_iron",
 *   "amount": 288,
 *   "result": {"id": "forgeweave:pickaxe_head", "components": {"forgeweave:material": "forgeweave:iron"}}
 * }
 * </pre>
 *
 * <ul>
 *   <li>{@code station} -- {@code table} or {@code basin}. Upstream keeps two separate recipe lists;
 *       one registry with a discriminator is the same thing with fewer moving parts.
 *   <li>{@code cast} -- what has to be sitting in the block. Omit it to require an <em>empty</em>
 *       block, which is how basin block casting works.
 *   <li>{@code fluid} -- the still fluid that must be poured in. Flowing variants never reach a
 *       casting block; a faucet moves fluid through the capability, not as a block.
 *   <li>{@code amount} -- millibuckets, on the same scale as melting (#96): upstream's
 *       {@code Material.VALUE_Ingot} is 144.
 *   <li>{@code result} -- the finished stack, data components and all, so a metal part is a pure
 *       data row rather than one Java entry per material.
 *   <li>{@code time} -- cooling ticks. Optional; defaults to upstream's
 *       {@link #cooldownTicks(Fluid, int)} formula, which is hotter-and-more = slower.
 *   <li>{@code consumes_cast} -- clears the input when the pour finishes. Upstream's cast creation
 *       melts the part it was moulded around, and its single-use clay casts (issue #292) are eaten
 *       by the pour that casts through them.
 *   <li>{@code result_in_input} -- upstream's {@code switchOutputs}: put the result back in the
 *       input slot (and whatever was in the input into the output slot). Cast creation uses it so a
 *       fresh cast is already in place for the next pour.
 * </ul>
 */
public record CastingRecipe(Station station, Optional<Ingredient> cast, Fluid fluid, int amount,
        ItemStack result, Optional<Integer> time, boolean consumesCast, boolean resultInInput) {

    /** Which of the two casting blocks a recipe belongs to (upstream's two separate recipe lists). */
    public enum Station implements StringRepresentable {
        TABLE("table"),
        BASIN("basin");

        public static final Codec<Station> CODEC = StringRepresentable.fromEnum(Station::values);

        private final String name;

        Station(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final ResourceKey<Registry<CastingRecipe>> REGISTRY = ResourceKey.createRegistryKey(
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "casting_recipe"));

    public static final Codec<CastingRecipe> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Station.CODEC.fieldOf("station").forGetter(CastingRecipe::station),
            Ingredient.CODEC.optionalFieldOf("cast").forGetter(CastingRecipe::cast),
            BuiltInRegistries.FLUID.byNameCodec().fieldOf("fluid").forGetter(CastingRecipe::fluid),
            ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(CastingRecipe::amount),
            ItemStack.CODEC.fieldOf("result").forGetter(CastingRecipe::result),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("time").forGetter(CastingRecipe::time),
            Codec.BOOL.optionalFieldOf("consumes_cast", false).forGetter(CastingRecipe::consumesCast),
            Codec.BOOL.optionalFieldOf("result_in_input", false).forGetter(CastingRecipe::resultInInput))
            .apply(instance, CastingRecipe::new));

    /** Whether this recipe applies to {@code held} sitting in a {@code station} block being poured {@code fluid}. */
    public boolean matches(Station station, ItemStack held, Fluid fluid) {
        if (this.station != station || this.fluid != fluid) {
            return false;
        }
        if (!cast.map(ingredient -> ingredient.test(held)).orElseGet(held::isEmpty)) {
            return false;
        }
        if (usesClayCast(held) && !ForgeweaveConfig.ENABLE_CLAY_CASTS.get()) {
            return false;
        }
        if (!ForgeweaveConfig.enabled(ForgeweaveConfig.SMELTERY)) {
            // Content-family toggles ticket: casting is part of the smeltery family, and with it off
            // no pour resolves anywhere -- same lookup-time filter as the clay-cast option above.
            return false;
        }
        // ...and the family gate on what is being poured: a cast or a part that serves only tool
        // families the server switched off cannot be moulded or cast through. Asked of both sides
        // because a table pour either shapes a cast around a part or a part inside a cast.
        return ContentFamilies.itemEnabled(held) && ContentFamilies.itemEnabled(result);
    }

    /**
     * Whether this recipe is one of the two halves of the clay-cast system (issue #292): moulding a
     * clay cast, or casting through one. Upstream 1.12 never registers either while its
     * {@code enableClayCasts} option is off; casting recipes are datapack entries here, so they are
     * filtered at lookup instead -- the runtime-check bucket issue #276 asks for.
     *
     * <p>Asked only of a recipe that already matched, so the config is never read on the hot path of
     * a pour that has nothing to do with clay.
     */
    private boolean usesClayCast(ItemStack held) {
        return ClayCastItem.is(result) || ClayCastItem.is(held);
    }

    /** How long the pour takes to cool, explicit or derived. */
    public int cooldownTicks() {
        return time.orElseGet(() -> cooldownTicks(fluid, amount));
    }

    /**
     * Upstream's {@code CastingRecipe#calcCooldownTime}: 24 ticks (its faucet's own pour animation
     * length, the floor below which cooling cannot go) plus one tick per 1600 degree-millibuckets
     * above the 300 K room temperature its fluids are measured against.
     */
    public static int cooldownTicks(Fluid fluid, int amount) {
        return 24 + ((fluid.getFluidType().getTemperature() - 300) * amount) / 1600;
    }

    /** The first recipe in {@code recipes} that {@link #matches}, or {@code null}. */
    public static CastingRecipe find(Iterable<CastingRecipe> recipes, Station station, ItemStack held, Fluid fluid) {
        for (CastingRecipe recipe : recipes) {
            if (recipe.matches(station, held, fluid)) {
                return recipe;
            }
        }
        return null;
    }
}
