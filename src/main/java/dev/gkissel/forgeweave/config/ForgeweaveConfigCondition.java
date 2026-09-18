package dev.gkissel.forgeweave.config;

import java.util.Map;
import java.util.function.BooleanSupplier;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Issue #995 (D-M8-16, D-M8-13): a {@code neoforge:conditions} predicate reading one of the four
 * {@code compat} toggles {@link ForgeweaveConfig#CREATE_RECIPES}, {@link
 * ForgeweaveConfig#IMMERSIVE_ENGINEERING_RECIPES}, {@link ForgeweaveConfig#ENDER_IO_RECIPES} and
 * {@link ForgeweaveConfig#POWAH_HEAT_SOURCES} gate -- {@code {"type": "forgeweave:compat_toggle",
 * "toggle": "createRecipes"}}.
 *
 * <p>None of the four rows this issue generates is Forgeweave's own recipe type (they are {@code
 * create:mixing}, {@code immersiveengineering:arc_furnace}, {@code enderio:alloy_smelting} and a
 * Powah {@code data_maps} value), so none of them has a Forgeweave-owned lookup site to filter at the
 * way {@code ENABLE_CLAY_CASTS} filters {@link dev.gkissel.forgeweave.casting.CastingRecipe#matches}
 * or {@code DRACONIC_FUSION} filters {@code ForgeweaveDraconicCompat#acceptsFusionCatalyst}. This
 * condition is the smallest thing that reaches the same "off means the row does not resolve"
 * contract for a row Forgeweave does not own the matching code for -- one class, reused by all four
 * toggles, evaluated by NeoForge's own generic conditional-JSON loader (the same one every {@code
 * neoforge:mod_loaded} row in this codebase already goes through), not a bespoke parallel system.
 *
 * <p>Off is inert, not destructive, the same as every other {@code compat} toggle: nothing here holds
 * state, so a row that stops resolving loses nothing, and it resolves again the moment the toggle
 * returns and the datapack reloads.
 */
public record ForgeweaveConfigCondition(String toggle) implements ICondition {

    private static final Map<String, BooleanSupplier> TOGGLES = Map.of(
            "createRecipes", () -> ForgeweaveConfig.enabled(ForgeweaveConfig.CREATE_RECIPES),
            "immersiveEngineeringRecipes", () -> ForgeweaveConfig.enabled(ForgeweaveConfig.IMMERSIVE_ENGINEERING_RECIPES),
            "enderIoRecipes", () -> ForgeweaveConfig.enabled(ForgeweaveConfig.ENDER_IO_RECIPES),
            "powahHeatSources", () -> ForgeweaveConfig.enabled(ForgeweaveConfig.POWAH_HEAT_SOURCES));

    public static final MapCodec<ForgeweaveConfigCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.STRING.fieldOf("toggle").forGetter(ForgeweaveConfigCondition::toggle))
            .apply(instance, ForgeweaveConfigCondition::new));

    public static final DeferredRegister<MapCodec<? extends ICondition>> CONDITION_CODECS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, Forgeweave.MODID);

    public static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ForgeweaveConfigCondition>> COMPAT_TOGGLE =
            CONDITION_CODECS.register("compat_toggle", () -> CODEC);

    @Override
    public boolean test(IContext context) {
        BooleanSupplier value = TOGGLES.get(toggle);
        if (value == null) {
            throw new IllegalArgumentException("unknown forgeweave compat toggle: " + toggle);
        }
        return value.getAsBoolean();
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }

    @Override
    public String toString() {
        return "forgeweave:compat_toggle(\"" + toggle + "\")";
    }
}
