package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Item models for every Forgeweave item (docs/adr/0002): a plain {@code minecraft:item/generated}
 * model per item.
 *
 * <p>Part patterns (issue #43) are single-layer now: each is a committed static composite PNG under
 * {@code textures/derived/item/pattern_<part>.png} (the part's silhouette darkened onto the pattern
 * base -- see {@code scripts/generate_pattern_textures.py} and NOTICE.md), replacing the old
 * two-layer "pattern base + faint greyscale overlay" look. The blank pattern has no part to etch, so
 * it stays the plain base texture.
 *
 * <p>Tools (issue #10, reworked by issue #43) use dedicated per-tool layer art positioned for the
 * assembled item -- {@code textures/derived/tools/<tool>_{handle,head,binding}.png} -- rather than
 * the standalone part sprites (those are centered for a loose inventory item, not an assembled
 * tool). Layer order matches upstream 1.12's own tool models ({@code models/item/tools/*.tcon.json}:
 * layer0 = handle, layer1 = head, layer2 = binding); {@code ForgeweaveItemColors#toolMaterialTint}'s
 * tintIndex-to-material mapping matches this order, not {@code ToolMaterials}'s field order.
 */
public class ForgeweaveItemModelProvider extends ItemModelProvider {
    public ForgeweaveItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Forgeweave.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        singleLayerModel(ForgeweaveItems.PATTERN_BLANK, derivedItem("pattern"));
        singleLayerModel(ForgeweaveItems.PATTERN_PICKAXE_HEAD, derivedItem("pattern_pickaxe_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_SHOVEL_HEAD, derivedItem("pattern_shovel_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_AXE_HEAD, derivedItem("pattern_axe_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_TOOL_BINDING, derivedItem("pattern_tool_binding"));
        singleLayerModel(ForgeweaveItems.PATTERN_TOOL_HANDLE, derivedItem("pattern_tool_handle"));

        singleLayerModel(ForgeweaveItems.PART_PICKAXE_HEAD, derivedItem("pickaxe_head"));
        singleLayerModel(ForgeweaveItems.PART_SHOVEL_HEAD, derivedItem("shovel_head"));
        singleLayerModel(ForgeweaveItems.PART_AXE_HEAD, derivedItem("axe_head"));
        singleLayerModel(ForgeweaveItems.PART_TOOL_BINDING, derivedItem("tool_binding"));
        singleLayerModel(ForgeweaveItems.PART_TOOL_HANDLE, derivedItem("tool_handle"));
        singleLayerModel(ForgeweaveItems.SHARD, derivedItem("shard"));

        // Grout and seared brick (docs/SCOPE.md M2 issue #93). Grout reuses upstream's block-form
        // texture as a flat item icon (NOTICE.md) -- see ForgeweaveItems#GROUT for why grout is a
        // plain item here rather than a block.
        singleLayerModel(ForgeweaveItems.GROUT, derivedItem("grout"));
        singleLayerModel(ForgeweaveItems.SEARED_BRICK, derivedItem("seared_brick"));

        // #100 -- casting (docs/SCOPE.md M2 issue #100). The ingot and nugget casts have their own
        // upstream sprite; the five part casts are the blank cast with the part's own sprite laid over
        // it, which is a two-layer model here where upstream composites the same two images into one
        // generated texture at load time (CustomTextureCreator -- see ForgeweaveItems#CAST_INGOT).
        // Neither layer is tinted: a cast has no material.
        singleLayerModel(ForgeweaveItems.CAST_INGOT, derivedItem("cast_ingot"));
        singleLayerModel(ForgeweaveItems.CAST_NUGGET, derivedItem("cast_nugget"));
        castModel(ForgeweaveItems.CAST_PICKAXE_HEAD, "pickaxe_head");
        castModel(ForgeweaveItems.CAST_SHOVEL_HEAD, "shovel_head");
        castModel(ForgeweaveItems.CAST_AXE_HEAD, "axe_head");
        castModel(ForgeweaveItems.CAST_TOOL_BINDING, "tool_binding");
        castModel(ForgeweaveItems.CAST_TOOL_HANDLE, "tool_handle");

        toolModel(ForgeweaveItems.TOOL_PICKAXE, "pickaxe");
        toolModel(ForgeweaveItems.TOOL_SHOVEL, "shovel");
        toolModel(ForgeweaveItems.TOOL_HATCHET, "hatchet");

        // Modifier reagents (docs/SCOPE.md M2 issue #107), each a straight upstream texture port
        // (NOTICE.md): moss.png, mending_moss.png, reinforcement.png, silky_cloth.png, silky_jewel.png
        // and -- for the extra-slot item -- skull_char_gold.png, which is what upstream's own
        // materials.json blockstate points the creative_modifier variant at.
        singleLayerModel(ForgeweaveItems.MOSS, derivedItem("moss"));
        singleLayerModel(ForgeweaveItems.MENDING_MOSS, derivedItem("mending_moss"));
        singleLayerModel(ForgeweaveItems.REINFORCED_PLATE, derivedItem("reinforced_plate"));
        singleLayerModel(ForgeweaveItems.SILKY_CLOTH, derivedItem("silky_cloth"));
        singleLayerModel(ForgeweaveItems.SILKY_JEWEL, derivedItem("silky_jewel"));
        singleLayerModel(ForgeweaveItems.EXTRA_MODIFIER, derivedItem("extra_modifier"));
    }

    private ResourceLocation derivedItem(String name) {
        return modLoc("derived/item/" + name);
    }

    private ResourceLocation derivedTool(String tool, String layer) {
        return modLoc("derived/tools/" + tool + "_" + layer);
    }

    // Unchecked parent, matching basicItem()'s approach: "item/generated" is a vanilla builtin
    // model that isn't guaranteed to resolve through ExistingFileHelper in every datagen run mode.
    private void singleLayerModel(DeferredItem<? extends Item> item, ResourceLocation texture) {
        getBuilder(item.getId().toString())
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", texture);
    }

    private void castModel(DeferredItem<? extends Item> item, String part) {
        getBuilder(item.getId().toString())
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", derivedItem("cast"))
                .texture("layer1", derivedItem(part));
    }

    private void toolModel(DeferredItem<? extends Item> item, String tool) {
        getBuilder(item.getId().toString())
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", derivedTool(tool, "handle"))
                .texture("layer1", derivedTool(tool, "head"))
                .texture("layer2", derivedTool(tool, "binding"));
    }
}
