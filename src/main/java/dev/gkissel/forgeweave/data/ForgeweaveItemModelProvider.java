package dev.gkissel.forgeweave.data;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;

import net.neoforged.neoforge.client.model.generators.ItemModelBuilder;
import net.neoforged.neoforge.client.model.generators.ItemModelProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.client.model.generators.loaders.DynamicFluidContainerModelBuilder;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import net.neoforged.neoforge.registries.DeferredItem;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolArt;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Item models for every Forgeweave item (docs/adr/0002): a plain {@code minecraft:item/generated}
 * model per item, except the assembled tools, which parent {@code minecraft:item/handheld} for their
 * held-render transforms (issue #217 -- see {@link #TOOL_MODEL_PARENT}).
 *
 * <p>Part patterns (issue #43) are single-layer now: each is a committed static composite PNG under
 * {@code textures/derived/item/pattern_<part>.png} (the part's silhouette darkened onto the pattern
 * base -- see {@code scripts/generate_pattern_textures.py} and NOTICE.md), replacing the old
 * two-layer "pattern base + faint greyscale overlay" look. The blank pattern has no part to etch, so
 * it stays the plain base texture.
 *
 * <p>Tools (issue #10, reworked by issue #43) use dedicated per-tool layer art positioned for the
 * assembled item -- {@code <root>/tools/<tool>_{handle,head,binding}.png}, the root being whichever
 * of {@code textures/derived/} or {@code textures/} {@code ToolArt#layer} picks -- rather than
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
        // The guide book (issue #273), art derived from upstream 1.12's items/book.png (NOTICE.md).
        singleLayerModel(ForgeweaveItems.GUIDE_BOOK, derivedItem("guide_book"));
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
        singleLayerModel(ForgeweaveItems.PATTERN_BOW_LIMB, derivedItem("pattern_bow_limb"));
        singleLayerModel(ForgeweaveItems.PATTERN_BOW_STRING, derivedItem("pattern_bow_string"));
        singleLayerModel(ForgeweaveItems.PATTERN_ARROW_HEAD, derivedItem("pattern_arrow_head"));
        singleLayerModel(ForgeweaveItems.PATTERN_ARROW_SHAFT, derivedItem("pattern_arrow_shaft"));
        singleLayerModel(ForgeweaveItems.PATTERN_FLETCHING, derivedItem("pattern_fletching"));
        singleLayerModel(ForgeweaveItems.PATTERN_PLATING_HELMET, derivedItem("pattern_plating_helmet")); // #677
        singleLayerModel(ForgeweaveItems.PATTERN_PLATING_CHESTPLATE, derivedItem("pattern_plating_chestplate"));
        singleLayerModel(ForgeweaveItems.PATTERN_PLATING_LEGGINGS, derivedItem("pattern_plating_leggings"));
        singleLayerModel(ForgeweaveItems.PATTERN_PLATING_BOOTS, derivedItem("pattern_plating_boots"));
        singleLayerModel(ForgeweaveItems.PATTERN_MAILLE, derivedItem("pattern_maille"));
        singleLayerModel(ForgeweaveItems.PATTERN_SHARPENING_KIT, derivedItem("pattern_sharpening_kit"));
        singleLayerModel(ForgeweaveItems.PATTERN_SHARD, derivedItem("pattern_shard"));

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
        // exceptions living under the plain item/ folder (no upstream counterpart was found for any
        // of the three). scripts/derive_m3_weapon_art.py replaced them: vein_hammer_head reuses the
        // already-derived tool-layer pixels (NOTICE.md), and curved_blade is 1.12's own cutlass
        // blade -- #279 found the curved shape #198 thought was missing, in the art of a tool
        // upstream never registered (the battleaxe's situation exactly).
        singleLayerModel(ForgeweaveItems.PART_VEIN_HAMMER_HEAD, derivedItem("vein_hammer_head"));
        // #375: curved_blade is Spartan Weaponry's saber blade now, not 1.12's cutlass blade.
        // Spartan Weaponry ships no scimitar (verified across every branch of the repository), so
        // the maintainer decision on #375 signs off on the saber -- its only curved single-edged
        // sword -- as the donor explicitly, rather than substituting one silently.
        singleLayerModel(ForgeweaveItems.PART_CURVED_BLADE, derivedItem("curved_blade"));
        // #375: the katana blade is derived art again, and back under derived/item/ with a
        // NOTICE.md row. Neither Tinkers' clone has a katana shape -- #198's stand-in (the large
        // sword blade, copied unchanged) failed playtest 0.3.2 for wearing the cleaver's
        // silhouette, and #279 authored one from scratch instead -- so the maintainer decision on
        // #375 sourced it from a third mod, Spartan Weaponry, under Apache-2.0 rather than MIT
        // (licenses/APACHE-2.0-SpartanWeaponry.txt, scripts/derive_spartan_blade_art.py). The
        // katana's guard and grip layers stay authored; ToolArt#ORIGINAL_ART routes per layer now.
        singleLayerModel(ForgeweaveItems.PART_KATANA_BLADE, derivedItem("katana_blade"));
        // #393: both bow parts are straight upstream copies (NOTICE.md). The limb is upstream's own
        // shortbow limb sprite -- bow_limb.tmat.json names items/shortbow/limb_bottom as its layer0,
        // since upstream draws the part item from the assembled bow's bottom limb rather than
        // shipping a separate part sprite for it.
        singleLayerModel(ForgeweaveItems.PART_BOW_LIMB, derivedItem("bow_limb"));
        singleLayerModel(ForgeweaveItems.PART_BOW_STRING, derivedItem("bow_string"));
        // #626: all three arrow parts are straight upstream copies (NOTICE.md). The shaft's sprite
        // is upstream's items/arrow/shaft.png -- arrow_shaft.tmat.json names it as its layer0, the
        // same assembled-tool-layer reuse the bow limb's model does -- while the head and fletching
        // have dedicated items/parts/ sprites.
        singleLayerModel(ForgeweaveItems.PART_ARROW_HEAD, derivedItem("arrow_head"));
        singleLayerModel(ForgeweaveItems.PART_ARROW_SHAFT, derivedItem("arrow_shaft"));
        singleLayerModel(ForgeweaveItems.PART_FLETCHING, derivedItem("fletching"));
        // #677: the 1.20 clone's grayscale plating/maille part sprites (scripts/derive_armor_part_art.py,
        // NOTICE.md), tinted per material like every other PartItem.
        singleLayerModel(ForgeweaveItems.PART_PLATING_HELMET, derivedItem("plating_helmet"));
        singleLayerModel(ForgeweaveItems.PART_PLATING_CHESTPLATE, derivedItem("plating_chestplate"));
        singleLayerModel(ForgeweaveItems.PART_PLATING_LEGGINGS, derivedItem("plating_leggings"));
        singleLayerModel(ForgeweaveItems.PART_PLATING_BOOTS, derivedItem("plating_boots"));
        singleLayerModel(ForgeweaveItems.PART_MAILLE, derivedItem("maille"));
        // #271: the sharpening kit's own art, copied straight from the clone (NOTICE.md). Tinted per
        // material by ForgeweaveItemColors like every other PartItem, which is why only the grey base
        // is ported and not upstream's three per-material overrides beside it.
        singleLayerModel(ForgeweaveItems.PART_SHARPENING_KIT, derivedItem("sharpening_kit"));

        // Seared brick (docs/SCOPE.md M2 issue #93). Grout is a block now (issue #129); its item
        // model is the standard block-item parent generated by ForgeweaveBlockStateProvider's
        // cubeAllBlock, not a flat single-layer model like this one.
        singleLayerModel(ForgeweaveItems.SEARED_BRICK, derivedItem("seared_brick"));

        // #502 (T71 parity audit): mud brick, upstream's other "materials" meta item (NOTICE.md).
        // Mud brick block's item model is the standard block-item parent generated by
        // ForgeweaveBlockStateProvider's cubeAllBlock, same as grout/graveyard soil above it.
        singleLayerModel(ForgeweaveItems.MUD_BRICK, derivedItem("mud_brick"));

        // #100/#140 -- casting (docs/SCOPE.md M2 issue #100; art fix issue #140). The ingot and nugget
        // casts have their own upstream sprite. The five part casts are pre-composited, single-layer
        // PNGs (scripts/generate_cast_textures.py): the blank cast base with a transparent hole
        // punched in the part's silhouette and a darkened bevel rim around it, matching upstream's
        // gold-cast-with-a-mold-cavity look (upstream instead composites this at texture-stitch time
        // via CustomTextureCreator/CastTexture -- see NOTICE.md). Neither the base nor the part shape
        // is tinted: a cast has no material.
        singleLayerModel(ForgeweaveItems.CAST_INGOT, derivedItem("cast_ingot"));
        singleLayerModel(ForgeweaveItems.CAST_NUGGET, derivedItem("cast_nugget"));
        // #272 -- the other two upstream CastCustom metas ported the same way (own upstream sprite).
        singleLayerModel(ForgeweaveItems.CAST_GEM, derivedItem("cast_gem"));
        singleLayerModel(ForgeweaveItems.CAST_PLATE, derivedItem("cast_plate"));
        singleLayerModel(ForgeweaveItems.CAST_GEAR, derivedItem("cast_gear"));
        singleLayerModel(ForgeweaveItems.CAST_PICKAXE_HEAD, derivedItem("cast_pickaxe_head"));
        singleLayerModel(ForgeweaveItems.CAST_SHOVEL_HEAD, derivedItem("cast_shovel_head"));
        singleLayerModel(ForgeweaveItems.CAST_AXE_HEAD, derivedItem("cast_axe_head"));
        singleLayerModel(ForgeweaveItems.CAST_TOOL_BINDING, derivedItem("cast_tool_binding"));
        singleLayerModel(ForgeweaveItems.CAST_TOOL_HANDLE, derivedItem("cast_tool_handle"));

        // #222 -- the M3 roster's own casts, same composited-PNG treatment as the five above
        // (scripts/generate_cast_textures.py, extended; NOTICE.md).
        singleLayerModel(ForgeweaveItems.CAST_SWORD_BLADE, derivedItem("cast_sword_blade"));
        singleLayerModel(ForgeweaveItems.CAST_WIDE_GUARD, derivedItem("cast_wide_guard"));
        singleLayerModel(ForgeweaveItems.CAST_HAND_GUARD, derivedItem("cast_hand_guard"));
        singleLayerModel(ForgeweaveItems.CAST_CROSS_GUARD, derivedItem("cast_cross_guard"));
        singleLayerModel(ForgeweaveItems.CAST_SIGN_PLATE, derivedItem("cast_sign_plate"));
        singleLayerModel(ForgeweaveItems.CAST_PAN, derivedItem("cast_pan"));
        singleLayerModel(ForgeweaveItems.CAST_KNIFE_BLADE, derivedItem("cast_knife_blade"));
        singleLayerModel(ForgeweaveItems.CAST_LARGE_SWORD_BLADE, derivedItem("cast_large_sword_blade"));
        singleLayerModel(ForgeweaveItems.CAST_TOUGH_TOOL_ROD, derivedItem("cast_tough_tool_rod"));
        singleLayerModel(ForgeweaveItems.CAST_TOUGH_BINDING, derivedItem("cast_tough_binding"));
        singleLayerModel(ForgeweaveItems.CAST_LARGE_PLATE, derivedItem("cast_large_plate"));
        singleLayerModel(ForgeweaveItems.CAST_HAMMER_HEAD, derivedItem("cast_hammer_head"));
        singleLayerModel(ForgeweaveItems.CAST_EXCAVATOR_HEAD, derivedItem("cast_excavator_head"));
        singleLayerModel(ForgeweaveItems.CAST_SCYTHE_HEAD, derivedItem("cast_scythe_head"));
        singleLayerModel(ForgeweaveItems.CAST_KAMA_HEAD, derivedItem("cast_kama_head"));
        singleLayerModel(ForgeweaveItems.CAST_BROAD_AXE_HEAD, derivedItem("cast_broad_axe_head"));
        singleLayerModel(ForgeweaveItems.CAST_VEIN_HAMMER_HEAD, derivedItem("cast_vein_hammer_head"));
        singleLayerModel(ForgeweaveItems.CAST_WAR_MACE_HEAD, derivedItem("cast_war_mace_head"));
        singleLayerModel(ForgeweaveItems.CAST_CURVED_BLADE, derivedItem("cast_curved_blade"));
        singleLayerModel(ForgeweaveItems.CAST_KATANA_BLADE, derivedItem("cast_katana_blade"));
        singleLayerModel(ForgeweaveItems.CAST_BOW_LIMB, derivedItem("cast_bow_limb"));
        singleLayerModel(ForgeweaveItems.CAST_ARROW_HEAD, derivedItem("cast_arrow_head")); // #626
        singleLayerModel(ForgeweaveItems.CAST_PLATING_HELMET, derivedItem("cast_plating_helmet")); // #677
        singleLayerModel(ForgeweaveItems.CAST_PLATING_CHESTPLATE, derivedItem("cast_plating_chestplate"));
        singleLayerModel(ForgeweaveItems.CAST_PLATING_LEGGINGS, derivedItem("cast_plating_leggings"));
        singleLayerModel(ForgeweaveItems.CAST_PLATING_BOOTS, derivedItem("cast_plating_boots"));
        singleLayerModel(ForgeweaveItems.CAST_MAILLE, derivedItem("cast_maille"));
        singleLayerModel(ForgeweaveItems.CAST_SHARPENING_KIT, derivedItem("cast_sharpening_kit"));
        // #471/T40
        singleLayerModel(ForgeweaveItems.CAST_SHARD, derivedItem("cast_shard"));

        // #292 -- one clay counterpart per cast above, the same single-layer model against the
        // mauve-tinted copy of that cast's sprite (scripts/generate_clay_cast_textures.py; upstream
        // instead re-bakes the gold model with a colour multiplier at load time, NOTICE.md).
        ForgeweaveItems.CLAY_CASTS.forEach((cast, clay) -> singleLayerModel(clay, derivedItem("clay_" + cast)));

        // Every assemblable tool, straight off the station's own table (ToolAssemblyRecipes.ENTRIES):
        // one model layer per part, so a two-part M3 weapon gets two layers and a three-part one gets
        // three, and no tool can be registered without a model or vice versa.
        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            // #679: a plate armor piece is the same maille-behind-plating layer stack (ToolArt#layers)
            // with the same broken-plating override, over the flat item/generated -- it is worn, not held.
            toolModel(entry.tool(), entry.constants().id(), ToolArt.layers(entry.constants().parts()),
                    entry.constants().category() == ToolConstants.Category.ARMOR ? "item/generated" : TOOL_MODEL_PARENT);
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
        // NOTICE.md) -- same for the dagger's two, and #375 re-sourced the scimitar's head layer
        // again, to Spartan Weaponry's saber blade. The katana's three went the other way at #279 --
        // back to freshly authored under textures/tools/ -- and #375 split them: its head layer is
        // derived from Spartan Weaponry now, its binding and handle stay authored. That split is why
        // ToolArt#ORIGINAL_ART is keyed by "<tool>_<layer>" rather than by tool.

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
        // #429 -- necrotic bone, the same straight upstream port (materials/necrotic_bone.png).
        singleLayerModel(ForgeweaveItems.NECROTIC_BONE, derivedItem("necrotic_bone"));

        // #438 -- the two expanders, straight upstream texture ports like the six above
        // (materials/expander_w.png, materials/expander_h.png; NOTICE.md).
        singleLayerModel(ForgeweaveItems.EXPANDER_W, derivedItem("expander_w"));
        singleLayerModel(ForgeweaveItems.EXPANDER_H, derivedItem("expander_h"));

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
        singleLayerModel(ForgeweaveItems.NAHUATL_BOARD, itemTexture("nahuatl_board")); // #727
        singleLayerModel(ForgeweaveItems.INGOT_MANYULLYN, derivedItem("manyullyn_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_MANYULLYN, derivedItem("manyullyn_nugget"));
        singleLayerModel(ForgeweaveItems.RAW_MANYULLYN, itemTexture("raw_manyullyn"));
        singleLayerModel(ForgeweaveItems.INGOT_ROSE_GOLD, derivedItem("rose_gold_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_ROSE_GOLD, derivedItem("rose_gold_nugget"));
        singleLayerModel(ForgeweaveItems.RAW_ROSE_GOLD, itemTexture("raw_rose_gold"));
        // #234 -- steel (M3.2), recolor-of-manyullyn derivations like rose gold's (NOTICE.md).
        singleLayerModel(ForgeweaveItems.INGOT_STEEL, derivedItem("steel_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_STEEL, derivedItem("steel_nugget"));

        // #232 -- slime crystals and knightslime (docs/SCOPE.md M3.2): straight upstream texture
        // ports, slimecrystal_{green,blue,magma} and ingot/nugget_knightslime (NOTICE.md).
        singleLayerModel(ForgeweaveItems.GREEN_SLIME_CRYSTAL, derivedItem("green_slime_crystal"));
        singleLayerModel(ForgeweaveItems.BLUE_SLIME_CRYSTAL, derivedItem("blue_slime_crystal"));
        singleLayerModel(ForgeweaveItems.MAGMA_SLIME_CRYSTAL, derivedItem("magma_slime_crystal"));
        // #635 (parity audit T57) -- the five coloured slime balls, upstream's
        // items/materials/slimeball_*.png ports (NOTICE.md).
        for (ForgeweaveItems.SlimeBall ball : ForgeweaveItems.slimeBalls()) {
            singleLayerModel(ball.item(), derivedItem(ball.colour().id() + "_slime_ball"));
        }
        // #649 (parity audit T57) -- the five slime drops, upstream's items/food/slimedrop_*.png
        // ports (NOTICE.md).
        for (ForgeweaveItems.SlimeDrop drop : ForgeweaveItems.slimeDrops()) {
            singleLayerModel(drop.item(), derivedItem(drop.colour().id() + "_slime_drop"));
        }

        singleLayerModel(ForgeweaveItems.INGOT_KNIGHTSLIME, derivedItem("knightslime_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_KNIGHTSLIME, derivedItem("knightslime_nugget"));

        // #451 (T20) -- the blue slime's spawn egg. Vanilla's template model paints itself from the
        // item's own two colours (ForgeweaveItems#BLUE_SLIME_SPAWN_EGG), so it needs no texture.
        getBuilder(ForgeweaveItems.BLUE_SLIME_SPAWN_EGG.getId().toString())
                .parent(new ModelFile.UncheckedModelFile("item/template_spawn_egg"));

        // #452 -- the slime boots (T21), upstream's items/armor/slime_boots.png with the green slime
        // ball tint baked in (NOTICE.md). Upstream's own model parents item/handheld, which is an
        // upstream quirk for a boots item; vanilla boots are item/generated, which is what
        // singleLayerModel produces.
        singleLayerModel(ForgeweaveItems.SLIME_BOOTS, derivedItem("slime_boots"));

        // T22 (issue #453, six colours by #649) -- the Slimeslings, upstream's
        // models/item/slimesling.json: a generated model plus the four held transforms it carries,
        // which turn the sling sideways in the hand. Upstream renders every colour from the one
        // greyscale sprite through an ItemColors ball-colour tint; here each colour's model takes
        // its own pre-tinted sprite (scripts/derive_slime_sling_art.py, NOTICE.md), the same
        // baked-tint adaptation the slime boots made at #452.
        for (ForgeweaveItems.SlimeSling sling : ForgeweaveItems.slimeSlings()) {
            getBuilder(sling.item().getId().toString())
                    .parent(new ModelFile.UncheckedModelFile("item/generated"))
                    .texture("layer0", derivedItem(sling.item().getId().getPath()))
                    .transforms()
                    .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(0, 90, 0).translation(0, 4.0F, 2.5F).scale(0.85F).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(0, -90, 0).translation(0, 4.0F, 2.5F).scale(0.85F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0, 90, 0).translation(0, 1.6F, 0.8F).scale(0.68F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(0, -90, 0).translation(0, 1.6F, 0.8F).scale(0.68F).end()
                    .end();
        }

        // #233 -- pig iron ingot/nugget, straight upstream texture ports (ingot_pigiron.png/
        // nugget_pigiron.png, NOTICE.md). No raw form (alloy-only metal).
        singleLayerModel(ForgeweaveItems.INGOT_PIG_IRON, derivedItem("pig_iron_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_PIG_IRON, derivedItem("pig_iron_nugget"));

        // #235 -- amethyst bronze (M3.2), the 1.20 clone's own art copied byte-for-byte (NOTICE.md).
        singleLayerModel(ForgeweaveItems.INGOT_AMETHYST_BRONZE, derivedItem("amethyst_bronze_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_AMETHYST_BRONZE, derivedItem("amethyst_bronze_nugget"));

        // #286 -- one bucket model per molten fluid, off ForgeweaveFluids#all so a new fluid gets
        // one by existing. No bucket art of our own: NeoForge's dynamic fluid container model
        // (parent neoforge:item/bucket = vanilla's own item/bucket sprite plus NeoForge's fluid
        // mask) re-textures the fluid layer from the fluid's still texture and tints it via
        // ForgeweaveItemColors. Both upstream generations do the same thing rather than draw 20
        // buckets: 1.12 registers every smeltery fluid on Forge's universal bucket
        // (TinkerFluids#registerItems -> FluidRegistry.addBucketForFluid, one shared bucket item
        // recolored per fluid), and the 1.20 clone's generated bucket models are
        // {"parent": "forge:item/bucket_drip", "loader": "tconstruct:fluid_container", "fluid": ...}
        // -- the same dynamic-container mechanism this uses.
        for (ForgeweaveFluids.MoltenMetal fluid : ForgeweaveFluids.all()) {
            withExistingParent(fluid.name() + "_bucket", BUCKET_MODEL_PARENT)
                    .customLoader(DynamicFluidContainerModelBuilder::begin)
                    .fluid(fluid.still().get())
                    .flipGas(false)
                    .end();
        }
    }

    /** NeoForge's stock bucket container model: vanilla's {@code item/bucket} sprite plus NeoForge's fluid mask (#286). */
    private static final ResourceLocation BUCKET_MODEL_PARENT =
            ResourceLocation.fromNamespaceAndPath("neoforge", "item/bucket");

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

    private void toolModel(Supplier<? extends Item> item, String tool, List<String> layers, String parent) {
        ItemModelBuilder builder = toolLayerModel(
                BuiltInRegistries.ITEM.getKey(item.get()).toString(), tool, layers, null, parent);
        drawStageOverrides(builder, tool, layers);
        String broken = ToolArt.brokenLayer(tool);
        if (broken != null) {
            // Last, so it wins: vanilla resolves overrides by "the last entry whose every predicate
            // still matches", and a Broken bow must render broken whatever else is true of it.
            builder.override().predicate(BROKEN_PREDICATE, 1)
                    .model(toolLayerModel(tool + "_broken", tool, layers, broken, parent)).end();
        }
    }

    /**
     * A bow's draw states (M3.5 issue #400), upstream's {@code <bow>.tcon.json} {@code overrides}
     * block in vanilla's own format: three {@code {"pulling": 1, "pull": <threshold>}} entries in
     * ascending order, each pointing at a {@code <bow>_pulling_<stage>} model that is the bow's own
     * layer stack with the bent-limb and stretched-string art swapped in
     * ({@link ToolArt#drawLayer}), plus the crossbow's fourth {@code {"loaded": 1}} entry.
     *
     * <p>Upstream expresses the same thing as texture <em>patches</em> inside one model, which
     * vanilla's format cannot do; whole sibling models are the same translation issue #284's Broken
     * swap already made, and they keep the layer count and order identical so
     * {@code ForgeweaveItemColors}' tintIndex-to-part mapping survives every swap.
     *
     * <p>A no-op for every tool that is not a bow -- {@link ToolArt#drawThresholds} returns null.
     */
    private void drawStageOverrides(ItemModelBuilder builder, String tool, List<String> layers) {
        float[] thresholds = ToolArt.drawThresholds(tool);
        if (thresholds == null) {
            return;
        }
        for (int stage = 1; stage <= ToolArt.DRAW_STAGES; stage++) {
            builder.override()
                    .predicate(PULLING_PREDICATE, 1)
                    .predicate(PULL_PREDICATE, thresholds[stage - 1])
                    .model(drawStageModel(tool + "_pulling_" + stage, tool, layers, stage,
                            DRAWING_DISPLAY_OVERRIDES.get(tool)))
                    .end();
        }
        if (ToolArt.hasLoadedState(tool)) {
            builder.override().predicate(LOADED_PREDICATE, 1)
                    .model(drawStageModel(tool + "_loaded", tool, layers, ToolArt.LOADED_STAGE,
                            LOADED_DISPLAY_OVERRIDES.get(tool)))
                    .end();
        }
    }

    /** One draw state's model: {@link #toolLayerModel}'s layer stack at {@code stage}'s art. */
    private ItemModelBuilder drawStageModel(String name, String tool, List<String> layers, int stage,
            Consumer<ItemModelBuilder> extraDisplay) {
        ItemModelBuilder builder =
                getBuilder(name).parent(new ModelFile.UncheckedModelFile(TOOL_MODEL_PARENT));
        for (int layer = 0; layer < layers.size(); layer++) {
            builder.texture("layer" + layer, modLoc(ToolArt.drawLayer(tool, layers.get(layer), stage)));
        }
        applyDisplayOverrides(builder, TOOL_DISPLAY_OVERRIDES.get(tool));
        applyDisplayOverrides(builder, extraDisplay);
        return builder;
    }

    private static void applyDisplayOverrides(ItemModelBuilder builder, Consumer<ItemModelBuilder> override) {
        if (override != null) {
            override.accept(builder);
        }
    }

    /**
     * One tool's layer model. {@code brokenLayer} names the single layer that draws its {@code
     * _broken} art instead of its own (null for the intact model) -- see {@link ToolArt#brokenLayer}.
     * Both models carry the same layers in the same order, so {@code ForgeweaveItemColors#
     * toolMaterialTint}'s tintIndex-to-part mapping keeps working across the swap, and both get the
     * tool's display transforms.
     */
    private ItemModelBuilder toolLayerModel(String name, String tool, List<String> layers, String brokenLayer,
            String parent) {
        ItemModelBuilder builder = getBuilder(name).parent(new ModelFile.UncheckedModelFile(parent));
        for (int layer = 0; layer < layers.size(); layer++) {
            String art = layers.get(layer);
            builder.texture("layer" + layer, art.equals(brokenLayer)
                    ? modLoc(ToolArt.brokenLayerTexture(tool, art))
                    : toolLayer(tool, art));
        }
        applyDisplayOverrides(builder, TOOL_DISPLAY_OVERRIDES.get(tool));
        return builder;
    }

    /**
     * The item property {@code ForgeweaveItemProperties} registers for the Broken state, and the
     * predicate each tool model's {@code overrides} entry tests (issue #284). Upstream 1.12 keeps the
     * broken textures inside its one tool model ({@code broken<N>} keys beside {@code layer<N>}) and
     * swaps quads in {@code BakedToolModel}; that is its own model loader's format, so this uses
     * vanilla's predicate-plus-separate-model {@code overrides} instead -- which is what upstream
     * itself moved to once it was on a modern Minecraft ({@code TinkerItemProperties#BROKEN_ID} and
     * the {@code "tconstruct:broken": 1} override in the 1.20 clone's {@code models/item/pickaxe.json}).
     */
    private static final ResourceLocation BROKEN_PREDICATE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "broken");

    /**
     * The three draw-state predicates (M3.5 issue #400), the other half of the properties
     * {@code ForgeweaveItemProperties} registers -- and, like them, {@code pulling}/{@code pull} in
     * vanilla's namespace (they are vanilla's own names for a drawn bow, and upstream 1.12's) while
     * {@code loaded} is Forgeweave's own.
     */
    private static final ResourceLocation PULLING_PREDICATE = ResourceLocation.withDefaultNamespace("pulling");
    private static final ResourceLocation PULL_PREDICATE = ResourceLocation.withDefaultNamespace("pull");
    private static final ResourceLocation LOADED_PREDICATE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "loaded");

    /**
     * The held-render transform set every tool inherits (issue #217). Vanilla's {@code item/handheld}
     * is numerically the same transform table upstream 1.12 gives every tool: its {@code .tcon.json}
     * models carry no {@code display} block of their own, and {@code ToolModel#getDefaultState}
     * returns Mantle's {@code ModelHelper.DEFAULT_TOOL_STATE} -- third person
     * {@code rotation [0,-/+90,+/-55] translation [0,4,0.5] scale 0.85}, first person
     * {@code rotation [0,-/+90,+/-25] translation [1.13,3.2,1.13] scale 0.68}, and
     * {@code item/generated}'s own ground/fixed/head/gui entries underneath. The flat
     * {@code item/generated} parent this used to carry is what made a held tool read as a
     * near-invisible edge-on sliver in the third-person harness captures.
     *
     * <p>Unchecked for the same reason {@link #singleLayerModel}'s parent is: it is a vanilla builtin
     * that does not always resolve through {@code ExistingFileHelper}. Layering still works -- the
     * model's root parent is still {@code item/generated}, which is what makes the item model
     * generator build geometry from the {@code layerN} textures.
     */
    private static final String TOOL_MODEL_PARENT = "item/handheld";

    /**
     * The tools upstream 1.12 gives a {@code display} block of their own (the only {@code .tcon.json}
     * tool models with one -- the rest, including every large harvest tool, use the inherited set
     * above unchanged, so hammer/excavator/lumberaxe/scythe/vein hammer/warmace/battleaxe deliberately
     * get no oversize scale here).
     *
     * <p>M3.5 issue #400 added the three bows', which #394/#395 had left for the rendering ticket:
     * a bow is held across the body rather than swung, so its poses are nothing like the inherited
     * {@code item/handheld} set -- upstream lays the two bows nearly flat and rotates them 260/-260
     * about Y, and stands the crossbow on end. The crossbow gets two more sets on top, in
     * {@link #DRAWING_DISPLAY_OVERRIDES} and {@link #LOADED_DISPLAY_OVERRIDES}, which its own
     * {@code overrides} entries carry per draw state.
     *
     * <p>Issues #615/#699: every {@code *_lefthand} entry below is upstream 1.12's, verbatim. 1.12's
     * {@code .tcon.json} files author the left-hand entry the way vanilla's own {@code item/handheld}
     * and {@code item/bow} do -- {@code rotation.y}/{@code rotation.z} negated, {@code translation.x}
     * kept -- and vanilla's {@code ItemTransform#apply} then mirrors that stored entry again for every
     * left-hand context. That second mirror is the convention, not a defect: vanilla feeds its own
     * pre-mirrored left entries through the same {@code apply}, and Forge 1.12's
     * {@code ForgeHooksClient#handleCameraTransforms} conjugated the authored left matrix by
     * {@code flipX} the same way, so upstream's rendered off-hand pose was {@code mirror(authored)} on
     * both generations. #615 read the stored entries as already-rendered values and dropped or
     * sign-compensated them, which negated these five tools' off-hand rotation relative to every other
     * handheld item (beta.1 playtest, #699). The crossbow is back on its 1.12 block (#693) and follows the
     * same convention. {@code LeftHandDisplayTransformTest} pins every value below.
     */
    private static final Map<String, Consumer<ItemModelBuilder>> TOOL_DISPLAY_OVERRIDES = Map.of(
            // cleaver.tcon.json: a two-block slab of a weapon, held bigger and higher than a sword.
            "cleaver", builder -> builder.transforms()
                    .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(0, -90, 55).translation(0, 10.0F, 0.5F).scale(1.5F).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(0, 90, -55).translation(0, 10.0F, 0.5F).scale(1.5F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0, -95, 30).translation(2.13F, 6.0F, 0.13F).scale(1.2F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(0, 95, -30).translation(2.13F, 6.0F, 0.13F).scale(1.2F).end()
                    .end(),
            // rapier.tcon.json: held point-forward for the thrust rather than shouldered like a
            // sword (note the inverted yaw against handheld's), and laid flat in the inventory.
            "rapier", builder -> builder.transforms()
                    .transform(ItemDisplayContext.GUI).rotation(0, 0, 90).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(0, 90, 15).translation(0, 4.5F, -1).scale(0.85F).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(0, -90, -15).translation(0, 4.5F, -1).scale(0.85F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0, 90, -25).translation(0, 2, 0.8F).scale(0.68F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(0, -90, 25).translation(0, 2, 0.8F).scale(0.68F).end()
                    .end(),
            // battlesign.tcon.json: a sign, carried face-out and unrotated rather than shouldered.
            // Upstream's first-person left entry negates translation.z, not rotation.z -- kept as is.
            "battlesign", builder -> builder.transforms()
                    .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(0, 0, 0).translation(0, 4.0F, 2.5F).scale(1).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(0, 0, 0).translation(0, 4.0F, 2.5F).scale(1).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0, 0, -5).translation(0, -2, 0.8F).scale(1).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(0, 0, -5).translation(0, -2, -0.8F).scale(1).end()
                    .end(),
            // shortbow.tcon.json (M3.5 issue #400): the same left-hand authoring as vanilla's bow.json.
            "shortbow", builder -> builder.transforms()
                    .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(-90, 260, -45).translation(-1, -2, 2.5F).scale(0.875F, 0.875F, 0.75F).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(-90, -260, 45).translation(-1, -2, 2.5F).scale(0.875F, 0.875F, 0.75F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0, -90, 25).translation(1.13F, 3.2F, 1.13F).scale(0.68F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(0, 90, -25).translation(1.13F, 3.2F, 1.13F).scale(0.68F).end()
                    .end(),
            // longbow.tcon.json: the shortbow's angles, held further out and scaled up.
            "longbow", builder -> builder.transforms()
                    .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(-90, 260, -45).translation(-1.875F, -1.25F, 4.0F)
                    .scale(1.0625F, 1.0625F, 0.875F).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(-90, -260, 45).translation(-1.875F, -1.25F, 4.0F)
                    .scale(1.0625F, 1.0625F, 0.875F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0, -90, 25).translation(1.13F, 3.2F, 1.13F).scale(0.875F, 0.875F, 0.7F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(0, 90, -25).translation(1.13F, 3.2F, 1.13F).scale(0.875F, 0.875F, 0.7F).end()
                    .end(),
            // crossbow.tcon.json: stood on end rather than laid flat, and the one bow whose pose
            // changes with its draw state -- see the two maps below. Upstream 1.12's numbers verbatim
            // on both hands (issue #693; #616 had replaced them with vanilla's own crossbow block on
            // the theory that a .tcon.json display block went through a different pipeline on Forge
            // 1.12 -- it did not: TConstruct's TransformDeserializer hands it to
            // PerspectiveMapWrapper#getTransforms, the same path every vanilla item took, and
            // vanilla's own item/bow.json display numbers are identical in 1.12 and 1.21, so these
            // render in 1.21 as they did upstream). BowDrawModelTest pins all of it.
            "crossbow", builder -> builder.transforms()
                    .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(90, 180, -225).translation(-1, 0.75F, -2.5F).scale(0.85F).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(90, 180, 225).translation(1, 0.75F, -2.5F).scale(0.85F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(-75, -5, -45).translation(0, 2, 0).scale(0.68F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(-75, -5, 45).translation(0, 2, 0).scale(0.68F).end()
                    .end());

    /**
     * The first-person pose a crossbow takes <em>while being cranked</em>, carried identically by all
     * three of its {@code pull} overrides upstream: pulled down and in toward the chest, the
     * two-handed winding stance. The third-person pose is unchanged, so those two entries are
     * inherited from {@link #TOOL_DISPLAY_OVERRIDES} rather than repeated.
     *
     * <p>Upstream's {@code crossbow.tcon.json} override blocks verbatim, both hands (issue #693).
     */
    private static final Map<String, Consumer<ItemModelBuilder>> DRAWING_DISPLAY_OVERRIDES = Map.of(
            "crossbow", builder -> builder.transforms()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(-115, -25, -45).translation(-3, 2.5F, 1).scale(0.68F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(-115, -25, 45).translation(-3, 2.5F, 1).scale(0.68F).end()
                    .end());

    /**
     * And the pose a <em>loaded</em> crossbow takes: upstream's {@code [-90,-5,-45] / [0,2,0]}
     * verbatim, both hands -- its idle pose pitched up 15 degrees, a cranked crossbow held ready to aim.
     */
    private static final Map<String, Consumer<ItemModelBuilder>> LOADED_DISPLAY_OVERRIDES = Map.of(
            "crossbow", builder -> builder.transforms()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(-90, -5, -45).translation(0, 2, 0).scale(0.68F).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(-90, -5, 45).translation(0, 2, 0).scale(0.68F).end()
                    .end());
}
