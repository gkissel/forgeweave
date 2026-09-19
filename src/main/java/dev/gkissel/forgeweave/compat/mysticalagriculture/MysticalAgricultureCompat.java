package dev.gkissel.forgeweave.compat.mysticalagriculture;

import com.blakebr0.mysticalagriculture.api.IMysticalAgriculturePlugin;
import com.blakebr0.mysticalagriculture.api.MysticalAgriculturePlugin;
import com.blakebr0.mysticalagriculture.api.crop.Crop;
import com.blakebr0.mysticalagriculture.api.crop.CropTextures;
import com.blakebr0.mysticalagriculture.api.crop.CropTier;
import com.blakebr0.mysticalagriculture.api.crop.CropType;
import com.blakebr0.mysticalagriculture.api.lib.LazyIngredient;
import com.blakebr0.mysticalagriculture.api.registry.ICropRegistry;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Forgeweave's Mystical Agriculture plugin (issue #999, docs/SCOPE.md D-M8-20): the class that turns
 * {@link ForgeweaveCrop#ALL} into Mystical Agriculture crops. Nothing constructs it -- Mystical
 * Agriculture finds it itself, which is why it is public with a no-arg constructor and carries the
 * annotation.
 *
 * <p>This and {@link MysticalItems} are the only classes allowed to name a {@code com.blakebr0} type;
 * {@code MysticalAgricultureSourceIsolationTest} keeps that true, the same guard
 * {@code ForgeweaveDraconicCompat} has for Draconic Evolution. There is no {@code ModList} check here
 * because there is nothing to check: this class is only ever loaded by Mystical Agriculture's own
 * plugin scan, so its presence is the guard.
 *
 * <h2>Verified API coordinates</h2>
 *
 * <p>Read off {@code com.blakebr0.mysticalagriculture:MysticalAgriculture:1.21.1-8.0.28:api} with
 * {@code javap} on 2026-09-18, not from memory:
 *
 * <ul>
 *   <li>Plugins are found by an ASM scan for {@code @MysticalAgriculturePlugin} on a <b>type</b>
 *       implementing {@code IMysticalAgriculturePlugin}, then instantiated with
 *       {@code Class.forName(...).newInstance()} ({@code registry.PluginRegistry#loadPlugins}). There
 *       is no plugin JSON and no service file, and a non-public or non-no-arg constructor fails
 *       silently into a {@code catch (Exception)}.
 *   <li>{@code ICropRegistry#register(Crop)} is the only registration overload. It is driven from
 *       {@code CropRegistry#onRegisterBlocks}/{@code onRegisterItems}, i.e. inside NeoForge's own
 *       {@code RegisterEvent}, which is why the crux is handed over as a {@code Supplier<Block>}
 *       rather than a resolved block.
 *   <li>{@code Crop}'s own constructor is the builder; {@code CropTier.ONE} through {@code FIVE} are
 *       the registered tiers (ids {@code mysticalagriculture:1} to {@code :5}) and there is no sixth.
 *   <li>Mystical Agriculture registers the crop block, the essence item and the seed item itself when
 *       the crop does not carry them, under <b>its own</b> namespace with our path:
 *       {@code mysticalagriculture:resonite_essence}, not {@code forgeweave:resonite_essence}.
 *   <li>Those items name themselves off {@code Crop#getDisplayName}, whose own default is
 *       {@code Component.translatable("crop.<modid>.<name>")}. That is why nothing here calls
 *       {@code setDisplayName}: the default key is already {@code crop.forgeweave.<id>}, and
 *       {@code ForgeweaveLanguageProvider} is where those twelve lines live.
 * </ul>
 *
 * <h2>Why this ships no art</h2>
 *
 * <p>Mystical Agriculture draws a third-party crop with its own greyscale template sprites, tinted by
 * the crop's colour: {@code client.ModelHandler#onModifyBakingResults} swaps any crop model that
 * resolved to {@code missingno} for one baked from the crop's {@code CropTextures}, and
 * {@code client.ColorHandler} registers a block and item colour for every crop whose colour is
 * non-zero. Handing each crop a preset {@code CropTextures} plus the material's own hex therefore
 * produces a flower, an essence and a seed that already look like the material, with no sprite of
 * Forgeweave's own and nothing derived from Mystical Agriculture's assets either -- Forgeweave ships
 * no file here at all, so there is no {@code NOTICE.md} row to write.
 */
@MysticalAgriculturePlugin
public class MysticalAgricultureCompat implements IMysticalAgriculturePlugin {

    /** Mystical Agriculture's own scan calls this; nothing else constructs the class. */
    public MysticalAgricultureCompat() {}

    @Override
    public void onRegisterCrops(ICropRegistry registry) {
        for (ForgeweaveCrop crop : ForgeweaveCrop.ALL) {
            registry.register(cropOf(crop));
        }
    }

    /**
     * One roster row as a Mystical Agriculture crop. The crafting material -- what a seed is made
     * from -- is the material's own ingot, except for brimspar, which has no ingot and offers its
     * crystal instead. That is the same "what does this material actually own an item of" question
     * {@link ForgeweaveCrop}'s javadoc answers for the fuel ladder.
     */
    private static Crop cropOf(ForgeweaveCrop crop) {
        boolean gem = crop.id().equals("brimspar") || crop.id().equals("fulmenite");
        String craftingItem = Forgeweave.MODID + ":" + (gem ? crop.id() + "_crystal" : crop.id() + "_ingot");
        return new Crop(crop.cropId(), tierOf(crop.tier()), CropType.RESOURCE,
                gem ? CropTextures.GEM_CROP_TEXTURES : CropTextures.INGOT_CROP_TEXTURES,
                crop.color(), LazyIngredient.item(craftingItem))
                .setCruxBlock(crop.crux());
    }

    /**
     * The essence ladder as Mystical Agriculture's own crop tiers. Total over the five values
     * {@link EssenceTier#value()} can hold; anything else is a programming error rather than a
     * runtime condition, so it throws rather than quietly picking a rung.
     */
    private static CropTier tierOf(EssenceTier tier) {
        return switch (tier.value()) {
            case 1 -> CropTier.ONE;
            case 2 -> CropTier.TWO;
            case 3 -> CropTier.THREE;
            case 4 -> CropTier.FOUR;
            case 5 -> CropTier.FIVE;
            default -> throw new IllegalStateException(
                    "no Mystical Agriculture crop tier for " + tier + " (value " + tier.value() + ")");
        };
    }
}
