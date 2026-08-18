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

    /**
     * An item name prefixed by the material(s) it is made of -- {@code Iron Pickaxe Head},
     * {@code Wooden Pickaxe}, {@code Stone-Iron Hammer}. Upstream 1.12's
     * {@code Material#getCombinedItemName} (Material.java:464-489) over
     * {@code Material#getLocalizedItemName} (Material.java:439-450), called by
     * {@code ToolCore#getItemStackDisplayName} and {@code ToolPart#getItemStackDisplayName};
     * issue #446. Names are uncoloured, as upstream's are -- {@code getLocalizedName()}, not the
     * coloured variant, because a hover name carries the surrounding rarity colour.
     *
     * <p>Repeats are dropped keeping first-seen order (upstream's {@code LinkedHashSet}), so a
     * hammer whose three head slots hold one material reads {@code Stone Hammer} and not
     * {@code Stone-Stone-Stone Hammer}.
     *
     * <p><b>Deviation from upstream's key contract.</b> Upstream's optional
     * {@code material.<id>.prefix} takes the item name as its only argument and is used only when
     * {@code I18n.canTranslate} says the key exists. A modern {@code Component} is resolved on the
     * client, so a dedicated server cannot ask that question about a mod key; the same choice is
     * made here by {@code translatableWithFallback} instead, which means the prefix entry gets both
     * arguments -- {@code %1$s} the material name, {@code %2$s} the item name -- and a material
     * with no entry falls back to the {@code "%s %s"} upstream builds by hand.
     */
    public static MutableComponent prefixed(List<ResourceLocation> materialIds, Component itemName) {
        List<ResourceLocation> distinct = materialIds.stream().distinct().toList();
        if (distinct.isEmpty()) {
            return itemName.copy();
        }
        if (distinct.size() == 1) {
            ResourceLocation materialId = distinct.get(0);
            return Component.translatableWithFallback(
                    "material." + materialId.getNamespace() + "." + materialId.getPath() + ".prefix",
                    "%s %s", plainName(materialId), itemName);
        }
        MutableComponent names = plainName(distinct.get(0));
        for (ResourceLocation materialId : distinct.subList(1, distinct.size())) {
            names.append("-").append(plainName(materialId));
        }
        // Same "<material> <item>" join the fallback above spells out, and the one the per-part
        // tooltip lines already use -- one key so a language reorders both at once.
        return Component.translatableWithFallback("tooltip.forgeweave.part_name", "%s %s", names, itemName);
    }

    private MaterialDisplay() {}
}
