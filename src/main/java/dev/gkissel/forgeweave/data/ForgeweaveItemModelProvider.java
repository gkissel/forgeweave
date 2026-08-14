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
        singleLayerModel(ForgeweaveItems.PATTERN_SHARPENING_KIT, derivedItem("pattern_sharpening_kit"));

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
        singleLayerModel(ForgeweaveItems.PART_CURVED_BLADE, derivedItem("curved_blade"));
        // #279: the katana is the one shape neither clone has any counterpart for, and #198's
        // stand-in (the large sword blade, copied unchanged) failed playtest 0.3.2 -- it wears the
        // same silhouette as the cleaver and broadsword. Its art is freshly authored again, so it
        // lives under the plain item/ folder and carries no NOTICE.md row
        // (scripts/generate_katana_art.py; the tool layers are routed by ToolArt#ORIGINAL_ART).
        singleLayerModel(ForgeweaveItems.PART_KATANA_BLADE, itemTexture("katana_blade"));
        // #271: the sharpening kit's own art, copied straight from the clone (NOTICE.md). Tinted per
        // material by ForgeweaveItemColors like every other PartItem, which is why only the grey base
        // is ported and not upstream's three per-material overrides beside it.
        singleLayerModel(ForgeweaveItems.PART_SHARPENING_KIT, derivedItem("sharpening_kit"));

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
        singleLayerModel(ForgeweaveItems.CAST_SHARPENING_KIT, derivedItem("cast_sharpening_kit"));

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
        // NOTICE.md) -- same for the dagger's two. The katana's three went the other way at #279:
        // back to freshly authored under textures/tools/, routed there by ToolArt#ORIGINAL_ART.

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
        // #234 -- steel (M3.2), recolor-of-manyullyn derivations like rose gold's (NOTICE.md).
        singleLayerModel(ForgeweaveItems.INGOT_STEEL, derivedItem("steel_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_STEEL, derivedItem("steel_nugget"));

        // #232 -- slime crystals and knightslime (docs/SCOPE.md M3.2): straight upstream texture
        // ports, slimecrystal_{green,blue,magma} and ingot/nugget_knightslime (NOTICE.md).
        singleLayerModel(ForgeweaveItems.GREEN_SLIME_CRYSTAL, derivedItem("green_slime_crystal"));
        singleLayerModel(ForgeweaveItems.BLUE_SLIME_CRYSTAL, derivedItem("blue_slime_crystal"));
        singleLayerModel(ForgeweaveItems.MAGMA_SLIME_CRYSTAL, derivedItem("magma_slime_crystal"));
        singleLayerModel(ForgeweaveItems.INGOT_KNIGHTSLIME, derivedItem("knightslime_ingot"));
        singleLayerModel(ForgeweaveItems.NUGGET_KNIGHTSLIME, derivedItem("knightslime_nugget"));

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

    private void toolModel(Supplier<? extends Item> item, String tool, List<String> layers) {
        ItemModelBuilder builder = toolLayerModel(
                BuiltInRegistries.ITEM.getKey(item.get()).toString(), tool, layers, null);
        String broken = ToolArt.brokenLayer(tool);
        if (broken != null) {
            builder.override().predicate(BROKEN_PREDICATE, 1)
                    .model(toolLayerModel(tool + "_broken", tool, layers, broken)).end();
        }
    }

    /**
     * One tool's layer model. {@code brokenLayer} names the single layer that draws its {@code
     * _broken} art instead of its own (null for the intact model) -- see {@link ToolArt#brokenLayer}.
     * Both models carry the same layers in the same order, so {@code ForgeweaveItemColors#
     * toolMaterialTint}'s tintIndex-to-part mapping keeps working across the swap, and both get the
     * tool's display transforms.
     */
    private ItemModelBuilder toolLayerModel(String name, String tool, List<String> layers, String brokenLayer) {
        ItemModelBuilder builder =
                getBuilder(name).parent(new ModelFile.UncheckedModelFile(TOOL_MODEL_PARENT));
        for (int layer = 0; layer < layers.size(); layer++) {
            String art = layers.get(layer);
            builder.texture("layer" + layer, art.equals(brokenLayer)
                    ? modLoc(ToolArt.brokenLayerTexture(tool, art))
                    : toolLayer(tool, art));
        }
        Consumer<ItemModelBuilder> override = TOOL_DISPLAY_OVERRIDES.get(tool);
        if (override != null) {
            override.accept(builder);
        }
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
     * The three tools upstream 1.12 gives a {@code display} block of their own, mirrored entry for
     * entry (the only {@code .tcon.json} tool models with one -- the rest, including every large
     * harvest tool, use the inherited set above unchanged, so hammer/excavator/lumberaxe/scythe/vein
     * hammer/warmace/battleaxe deliberately get no oversize scale here).
     *
     * <p>Not mirrored: {@code battlesign.tcon.json}'s extra {@code overrides} block, a second display
     * set for the blocking pose. That is 1.12's custom tool-model loader format, not vanilla's
     * predicate-plus-separate-model {@code overrides}, and a blocking pose is out of issue #217's
     * scope.
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
            "battlesign", builder -> builder.transforms()
                    .transform(ItemDisplayContext.THIRD_PERSON_RIGHT_HAND)
                    .rotation(0, 0, 0).translation(0, 4.0F, 2.5F).scale(1).end()
                    .transform(ItemDisplayContext.THIRD_PERSON_LEFT_HAND)
                    .rotation(0, 0, 0).translation(0, 4.0F, 2.5F).scale(1).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_RIGHT_HAND)
                    .rotation(0, 0, -5).translation(0, -2, 0.8F).scale(1).end()
                    .transform(ItemDisplayContext.FIRST_PERSON_LEFT_HAND)
                    .rotation(0, 0, -5).translation(0, -2, -0.8F).scale(1).end()
                    .end());
}
