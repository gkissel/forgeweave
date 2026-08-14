package dev.gkissel.forgeweave.material;

import java.util.List;
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

    /**
     * The colour of the first of {@code materialIds} that grants {@code traitId}, or {@code null}
     * when none of them granted it (or the registry is unreachable). Upstream 1.12 renders a trait
     * name in its granting material's colour ({@code Material#getTextColor}, applied by
     * {@code ToolPart#getTooltipTraitInfo} and {@code GuiPartBuilder#setDisplayForMaterial}); issue
     * #64 wants that everywhere a trait is named, so the lookup lives here rather than once per
     * tooltip and once per info panel.
     */
    @Nullable
    public static TextColor traitColor(@Nullable HolderLookup.Provider registries, List<ResourceLocation> materialIds,
            ResourceLocation traitId) {
        for (ResourceLocation materialId : materialIds) {
            Optional<Material> material = lookup(registries, materialId);
            if (material.isPresent() && material.get().traits().all().contains(traitId)) {
                return material.get().color();
            }
        }
        return null;
    }

    /** The material's translated name, tinted with its own colour when the registry is reachable. */
    public static MutableComponent name(@Nullable HolderLookup.Provider registries, ResourceLocation materialId) {
        MutableComponent name = plainName(materialId);
        TextColor color = lookup(registries, materialId).map(Material::color).orElse(null);
        return color == null ? name : name.withStyle(Style.EMPTY.withColor(color));
    }

    /**
     * The material's translated name with no tint -- upstream's plain {@code getLocalizedName()},
     * as opposed to the {@code getLocalizedNameColored()} {@link #name} mirrors. Wanted wherever the
     * surrounding line already has a colour of its own that a tint would fight with: the Part
     * Builder's grey "Material Value" readout and its {@code useless_tool_part} warning both name a
     * material this way upstream (issue #378).
     */
    public static MutableComponent plainName(ResourceLocation materialId) {
        return Component.translatable("material." + materialId.getNamespace() + "." + materialId.getPath());
    }

    private MaterialDisplay() {}
}
