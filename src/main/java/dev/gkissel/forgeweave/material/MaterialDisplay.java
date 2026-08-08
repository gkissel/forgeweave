package dev.gkissel.forgeweave.material;

import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

/**
 * Turning a material id into something a player reads: the lookup into the datapack registry and the
 * material's name in its own tint colour. Both the item tooltips ({@code PartItem}, {@code ToolItem})
 * and the stations' info panels (issue #47) need exactly this and must agree on it, so it lives in
 * one place rather than being restated per caller.
 *
 * <p>Materials are datapack data (ADR-0002) whose names have no registered Java object to derive a
 * translation key from, so the key is built from the id -- {@code material.<namespace>.<path>} --
 * matching {@code ForgeweaveLanguageProvider}'s explicit entries.
 */
public final class MaterialDisplay {

    public static Optional<Material> lookup(@Nullable HolderLookup.Provider registries, ResourceLocation materialId) {
        if (registries == null) {
            return Optional.empty();
        }
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, materialId)))
                .map(holder -> holder.value());
    }

    /** The material's translated name, tinted with its own colour when the registry is reachable. */
    public static MutableComponent name(@Nullable HolderLookup.Provider registries, ResourceLocation materialId) {
        MutableComponent name =
                Component.translatable("material." + materialId.getNamespace() + "." + materialId.getPath());
        TextColor color = lookup(registries, materialId).map(Material::color).orElse(null);
        return color == null ? name : name.withStyle(Style.EMPTY.withColor(color));
    }

    private MaterialDisplay() {}
}
