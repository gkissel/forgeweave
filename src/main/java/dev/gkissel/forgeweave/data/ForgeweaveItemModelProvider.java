package dev.gkissel.forgeweave.data;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolArt;

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

        // M3 tool parts + patterns (docs/SCOPE.md M3 issue #151). All patterns are composited PNGs
        // (scripts/generate_pattern_textures.py, same algorithm as the five above). Every part's base
        // texture is a straight upstream port under derived/item/ (issue #198: vein_hammer_head,
        // curved_blade and katana_blade used to be freshly-authored exceptions living under the plain
        // item/ folder; scripts/derive_m3_weapon_art.py replaced all three with derived art).
        singleLayerModel(ForgeweaveItems.PATTERN_SWORD_BLADE, derivedItem("pattern_sword_blade"));
        singleLayerModel(ForgeweaveItems.PATTERN_WIDE_GUARD, derivedItem("pattern_wide_guard"));
        singleLayerModel(ForgeweaveItems.PATTERN_HAND_GUARD, derivedItem("pattern_hand_guard"));
        singleLayerModel(ForgeweaveItems.PATTERN_CROSS_GUARD, derivedItem("pattern_cross_guard"));
        singleLayerModel(ForgeweaveItems.PATTERN_SIGN_PLATE, derivedItem("pattern_sign_plate"));
        singleLayerModel(ForgeweaveItems.PATTERN_PAN, derivedItem("pattern_pan"));
        singleLayerModel(ForgeweaveItems.PATTERN_KNIFE_BLADE, derivedItem("pattern_knife_blade"));
        singleLayerModel(ForgeweaveItems.PATTERN_LARGE_SWORD_BLADE, derivedItem("pattern_large_sword_blade"));
        singleLayerModel(ForgeweaveItems.PATTERN_TOUGH_TOOL_ROD, derivedItem("pattern_tough_tool_rod"));
        singleLayerModel(ForgeweaveItems.PATTERN_TOUGH_BINDING, derivedItem("pattern_tough_binding"));
        singleLayerModel(ForgeweaveItems.PATTERN_LARGE_PLATE, derivedItem("pattern_large_plate"));
        singleLayerModel(ForgeweaveItems.PATTERN_HAMMER_HEAD, derivedItem("pattern_hammer_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_EXCAVATOR_HEAD, derivedItem("pattern_excavator_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_SCYTHE_HEAD, derivedItem("pattern_scythe_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_KAMA_HEAD, derivedItem("pattern_kama_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_BROAD_AXE_HEAD, derivedItem("pattern_broad_axe_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_VEIN_HAMMER_HEAD, derivedItem("pattern_vein_hammer_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_WAR_MACE_HEAD, derivedItem("pattern_war_mace_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_CURVED_BLADE, derivedItem("pattern_curved_blade"));
        singleLayerModel(ForgeweaveItems.PATTERN_KATANA_BLADE, derivedItem("pattern_katana_blade"));

        singleLayerModel(ForgeweaveItems.PART_SWORD_BLADE, derivedItem("sword_blade"));
        singleLayerModel(ForgeweaveItems.PART_WIDE_GUARD, derivedItem("wide_guard"));
        singleLayerModel(ForgeweaveItems.PART_HAND_GUARD, derivedItem("hand_guard"));
        singleLayerModel(ForgeweaveItems.PART_CROSS_GUARD, derivedItem("cross_guard"));
        singleLayerModel(ForgeweaveItems.PART_SIGN_PLATE, derivedItem("sign_plate"));
        singleLayerModel(ForgeweaveItems.PART_PAN, derivedItem("pan"));
        singleLayerModel(ForgeweaveItems.PART_KNIFE_BLADE, derivedItem("knife_blade"));
        singleLayerModel(ForgeweaveItems.PART_LARGE_SWORD_BLADE, derivedItem("large_sword_blade"));
        singleLayerModel(ForgeweaveItems.PART_TOUGH_TOOL_ROD, derivedItem("tough_tool_rod"));
        singleLayerModel(ForgeweaveItems.PART_TOUGH_BINDING, derivedItem("tough_binding"));
        singleLayerModel(ForgeweaveItems.PART_LARGE_PLATE, derivedItem("large_plate"));
        singleLayerModel(ForgeweaveItems.PART_HAMMER_HEAD, derivedItem("hammer_head"));
        singleLayerModel(ForgeweaveItems.PART_EXCAVATOR_HEAD, derivedItem("excavator_head"));
        singleLayerModel(ForgeweaveItems.PART_SCYTHE_HEAD, derivedItem("scythe_head"));
        singleLayerModel(ForgeweaveItems.PART_KAMA_HEAD, derivedItem("kama_head"));
        singleLayerModel(ForgeweaveItems.PART_BROAD_AXE_HEAD, derivedItem("broad_axe_head"));
        // #161: derived from the clone's hammer head with a minimal reshape (issue #198's decision:
        // no freshly-authored art -- scripts/derive_warmace_art.py, NOTICE.md).
        singleLayerModel(ForgeweaveItems.PART_WAR_MACE_HEAD, derivedItem("war_mace_head"));
        // #198: vein_hammer_head, curved_blade and katana_blade all used to be freshly-authored
        // exceptions living under the plain item/ folder (no upstream counterpart existed for any of
        // the three). scripts/derive_m3_weapon_art.py replaced them: vein_hammer_head now reuses the
        // already-derived tool-layer pixels (NOTICE.md), and curved_blade/katana_blade are minimal
        // reshapes/reuses of the sword blade family, the closest upstream equivalent.
        singleLayerModel(ForgeweaveItems.PART_VEIN_HAMMER_HEAD, derivedItem("vein_hammer_head"));
        singleLayerModel(ForgeweaveItems.PART_CURVED_BLADE, derivedItem("curved_blade"));
        singleLayerModel(ForgeweaveItems.PART_KATANA_BLADE, derivedItem("katana_blade"));

        // Seared brick (docs/SCOPE.md M2 issue #93). Grout is a block now (issue #129); its item
        // model is the standard block-item parent generated by ForgeweaveBlockStateProvider's
        // cubeAllBlock, not a flat single-layer model like this one.
        singleLayerModel(ForgeweaveItems.SEARED_BRICK, derivedItem("seared_brick"));

        // #100/#140 -- casting (docs/SCOPE.md M2 issue #100; art fix issue #140). The ingot and nugget
        // casts have their own upstream sprite. The five part casts are pre-composited, single-layer
        // PNGs (scripts/generate_cast_textures.py): the blank cast base with a transparent hole
        // punched in the part's silhouette and a darkened bevel rim around it, matching upstream's
        // gold-cast-with-a-mold-cavity look (upstream instead composites this at texture-stitch time
        // via CustomTextureCreator/CastTexture -- see NOTICE.md). Neither the base nor the part shape
        // is tinted: a cast has no material.
        singleLayerModel(ForgeweaveItems.CAST_INGOT, derivedItem("cast_ingot"));
        singleLayerModel(ForgeweaveItems.CAST_NUGGET, derivedItem("cast_nugget"));
        singleLayerModel(ForgeweaveItems.CAST_PICKAXE_HEAD, derivedItem("cast_pickaxe_head"));
        singleLayerModel(ForgeweaveItems.CAST_SHOVEL_HEAD, derivedItem("cast_shovel_head"));
        singleLayerModel(ForgeweaveItems.CAST_AXE_HEAD, derivedItem("cast_axe_head"));
        singleLayerModel(ForgeweaveItems.CAST_TOOL_BINDING, derivedItem("cast_tool_binding"));
        singleLayerModel(ForgeweaveItems.CAST_TOOL_HANDLE, derivedItem("cast_tool_handle"));

        // Every assemblable tool, straight off the station's own table (ToolAssemblyRecipes.ENTRIES):
        // one model layer per part, so a two-part M3 weapon gets two layers and a three-part one gets
        // three, and no tool can be registered without a model or vice versa.
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            toolModel(entry.tool(), entry.constants().id(), ToolArt.layers(entry.constants().parts()));
        }

        // The M3 station tools (issue #156) come out of that same loop; their textures are upstream
        // tools/tools/{Mattock,Kama}.java's own (NOTICE.md), with layer0/1/2 matching each tool's
        // .tcon.json exactly -- the mattock's "binding" layer is its back.png (the shovel side drawn
        // behind the axe head, it has no binding part at all), and the kama's handle layer reuses the
        // pickaxe's handle texture, upstream's own choice (kama.tcon.json has no kama/handle.png).

        // #159's two come out of the same loop as well. The battleaxe's four layers are upstream's
        // own handle/backhead/fronthead/binding (scripts/generate_battleaxe_head.py -- NOTICE.md).
        // The scimitar's three used to be freshly authored under textures/tools/; issue #198 replaced
        // them with derived art under textures/derived/tools/ (scripts/derive_m3_weapon_art.py,
        // NOTICE.md) -- same for the dagger's two and the katana's three below.

        // #157's five large harvest tools come out of the same loop. Their layer names are the
        // role-derived ones ToolArt#layers produces, mapped from upstream's own layerN-draws-partN
        // convention: the hammer's back/front plates are its second and third HEAD slots, the
        // scythe's "accessory" is its second HANDLE. NOTICE.md carries a row per file.

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

        // #103 -- metal materials (docs/SCOPE.md M2 issue #103). Cobalt/ardite/manyullyn ingots and
        // nuggets are straight upstream texture ports (NOTICE.md); rose gold's are a recoloured
        // derivation of upstream's manyullyn art (NOTICE.md, no 1.12 counterpart otherwise). Raw
        // cobalt/ardite have no upstream art to derive from (1.12 predates raw ores), so per issue
        // #140 they are vanilla recolors instead (raw_gold/netherite_scrap, NOTICE.md); raw
        // manyullyn/rose gold stay fresh, non-derived placeholder icons (ForgeweaveItems#RAW_MANYULLYN).
        // All four live under the standard item texture folder either way.
        singleLayerModel(ForgeweaveItems.INGOT_COBALT, derivedItem("cobalt_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_COBALT, derivedItem("cobalt_nugget"));
        singleLayerModel(ForgeweaveItems.RAW_COBALT, itemTexture("raw_cobalt"));
        singleLayerModel(ForgeweaveItems.INGOT_ARDITE, derivedItem("ardite_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_ARDITE, derivedItem("ardite_nugget"));
        singleLayerModel(ForgeweaveItems.RAW_ARDITE, itemTexture("raw_ardite"));
        singleLayerModel(ForgeweaveItems.INGOT_MANYULLYN, derivedItem("manyullyn_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_MANYULLYN, derivedItem("manyullyn_nugget"));
        singleLayerModel(ForgeweaveItems.RAW_MANYULLYN, itemTexture("raw_manyullyn"));
        singleLayerModel(ForgeweaveItems.INGOT_ROSE_GOLD, derivedItem("rose_gold_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_ROSE_GOLD, derivedItem("rose_gold_nugget"));
        singleLayerModel(ForgeweaveItems.RAW_ROSE_GOLD, itemTexture("raw_rose_gold"));
    }

    private ResourceLocation derivedItem(String name) {
        return modLoc("derived/item/" + name);
    }

    private ResourceLocation itemTexture(String name) {
        return modLoc("item/" + name);
    }

    private ResourceLocation toolLayer(String tool, String layer) {
        return modLoc(ToolArt.layer(tool, layer));
    }

    // Unchecked parent, matching basicItem()'s approach: "item/generated" is a vanilla builtin
    // model that isn't guaranteed to resolve through ExistingFileHelper in every datagen run mode.
    private void singleLayerModel(DeferredItem<? extends Item> item, ResourceLocation texture) {
        getBuilder(item.getId().toString())
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", texture);
    }

    private void toolModel(Supplier<? extends Item> item, String tool, List<String> layers) {
        ItemModelBuilder builder = getBuilder(
                BuiltInRegistries.ITEM.getKey(item.get()).toString())
                .parent(new ModelFile.UncheckedModelFile("item/generated"));
        for (int layer = 0; layer < layers.size(); layer++) {
            builder.texture("layer" + layer, toolLayer(tool, layers.get(layer)));
        }
    }
}
