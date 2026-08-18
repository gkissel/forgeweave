package dev.gkissel.forgeweave.item;

import java.util.List;
import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.config.ForgeweaveClientConfig; // #276
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ContentFamilies;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * The Forgeweave creative tabs. Upstream 1.12 splits its content over six tabs
 * ({@code TinkerRegistry:76-81}: General, Tools, ToolParts, Smeltery, World, Gadgets) and every item
 * class picks one ({@code ToolCore:74}, {@code ToolPart:41}, {@code Pattern:28}, {@code Cast:14},
 * {@code BlockSeared:19}, {@code BlockOre:31}, {@code TinkerCommons:287-290}, ...). Forgeweave has
 * content for four of them; upstream's World tab would hold only the two nether ores here (no slime
 * islands) and its Gadgets content is absent entirely, so those ores ride along in General (issue
 * #507).
 *
 * <p>Materials are a datapack registry (ADR-0002), so the part item variants are enumerated at
 * display time from the registry access the display-items event provides, not fixed at registration
 * time.
 */
public final class ForgeweaveCreativeTab {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Forgeweave.MODID);

    static final List<DeferredItem<PartItem>> PART_ITEMS = List.of(
            ForgeweaveItems.PART_PICKAXE_HEAD,
            ForgeweaveItems.PART_SHOVEL_HEAD,
            ForgeweaveItems.PART_AXE_HEAD,
            ForgeweaveItems.PART_TOOL_BINDING,
            ForgeweaveItems.PART_TOOL_HANDLE,
            ForgeweaveItems.SHARD,
            ForgeweaveItems.PART_SWORD_BLADE,
            ForgeweaveItems.PART_WIDE_GUARD,
            ForgeweaveItems.PART_HAND_GUARD,
            ForgeweaveItems.PART_CROSS_GUARD,
            ForgeweaveItems.PART_SIGN_PLATE,
            ForgeweaveItems.PART_PAN,
            ForgeweaveItems.PART_KNIFE_BLADE,
            ForgeweaveItems.PART_LARGE_SWORD_BLADE,
            ForgeweaveItems.PART_TOUGH_TOOL_ROD,
            ForgeweaveItems.PART_TOUGH_BINDING,
            ForgeweaveItems.PART_LARGE_PLATE,
            ForgeweaveItems.PART_HAMMER_HEAD,
            ForgeweaveItems.PART_EXCAVATOR_HEAD,
            ForgeweaveItems.PART_SCYTHE_HEAD,
            ForgeweaveItems.PART_KAMA_HEAD,
            ForgeweaveItems.PART_BROAD_AXE_HEAD,
            ForgeweaveItems.PART_VEIN_HAMMER_HEAD,
            ForgeweaveItems.PART_WAR_MACE_HEAD,
            ForgeweaveItems.PART_CURVED_BLADE,
            ForgeweaveItems.PART_KATANA_BLADE,
            ForgeweaveItems.PART_BOW_LIMB,
            ForgeweaveItems.PART_BOW_STRING,
            ForgeweaveItems.PART_SHARPENING_KIT);

    /** Icons mirror upstream's: slime ball, assembled pickaxe, pickaxe head, seared tank. */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GENERAL =
            TABS.register("general", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.forgeweave.general"))
                    .icon(() -> new ItemStack(ForgeweaveItems.BLUE_SLIME_CRYSTAL.get()))
                    .displayItems(ForgeweaveCreativeTab::addGeneralItems)
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TOOLS =
            TABS.register("tools", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.forgeweave.tools"))
                    .icon(() -> new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get()))
                    .displayItems(ForgeweaveCreativeTab::addToolItems)
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PARTS =
            TABS.register("parts", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.forgeweave.parts"))
                    .icon(() -> new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get()))
                    .displayItems(ForgeweaveCreativeTab::addPartItems)
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SMELTERY =
            TABS.register("smeltery", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.forgeweave.smeltery"))
                    .icon(() -> new ItemStack(ForgeweaveItems.SEARED_TANK.get()))
                    .displayItems(ForgeweaveCreativeTab::addSmelteryItems)
                    .build());

    /** Content families (#398/#399) can switch whole item groups off; every tab honours them. */
    private static CreativeModeTab.Output enabledOnly(CreativeModeTab.Output rawOutput) {
        return (stack, visibility) -> {
            if (ContentFamilies.itemEnabled(stack)) {
                rawOutput.accept(stack, visibility);
            }
        };
    }

    /**
     * Upstream's {@code tabGeneral}: the book ({@code ItemTinkerBook:26}), the station blocks
     * ({@code BlockToolTable:52}, {@code BlockToolForge:41}), the crafting materials, ingots,
     * nuggets and metal blocks ({@code TinkerCommons:287-290}, {@code BlockMetal:27}), the soils
     * and mud bricks ({@code BlockSoil:45}, {@code BlockDecoGround:25}), firewood and clear glass
     * ({@code BlockFirewood:22}, {@code BlockClearGlass:24}). The two nether ores are upstream's
     * {@code tabWorld} ({@code BlockOre:31}) -- see the class comment.
     */
    static void addGeneralItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output rawOutput) {
        addGeneralItems(parameters, rawOutput, ForgeweaveClientConfig.LIST_ALL_TABLE_VARIANTS.get());
    }

    /**
     * Takes {@code listAllTableVariants} as a parameter rather than reading it (issue #506) so the
     * tests can drive both settings without standing up a {@code CLIENT}-type config spec -- the
     * same split {@link #addPartItems} already uses for its part-material flag. Only the
     * table-variant expansion reads it; every plain item here is listed regardless.
     *
     * <p>Public (unlike the overload above) so {@code ForgeweaveCreativeTabGameTests} can drive it
     * from a real server's registry access -- {@link #addTableVariants} reads the live {@link
     * BuiltInRegistries#ITEM} tag data, which only a running game (GameTest included) ever binds; a
     * bare unit test's {@code Bootstrap.bootStrap()} never loads a datapack, so it cannot exercise
     * the actual variant list, only the "tag absent" fallback the other tests already cover.
     */
    public static void addGeneralItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output rawOutput,
            boolean listAllTableVariants) {
        CreativeModeTab.Output output = enabledOnly(rawOutput);

        output.accept(ForgeweaveItems.GUIDE_BOOK.get()); // the guide book leads the tab (issue #273)
        // #506 (T75): retextured per plank/log behind listAllTables, exactly like upstream's
        // BlockToolTable#addBlocksFromOredict -- see ForgeweaveClientConfig#LIST_ALL_TABLE_VARIANTS.
        addTableVariants(output, ForgeweaveItems.PART_BUILDER.get(), ItemTags.LOGS, listAllTableVariants);
        output.accept(ForgeweaveItems.TOOL_STATION.get());
        output.accept(ForgeweaveItems.TOOL_FORGE.get());
        output.accept(ForgeweaveItems.CRAFTING_STATION.get());
        addTableVariants(output, ForgeweaveItems.STENCIL_TABLE.get(), ItemTags.PLANKS, listAllTableVariants);
        output.accept(ForgeweaveItems.PATTERN_CHEST.get());
        output.accept(ForgeweaveItems.PART_CHEST.get());

        output.accept(ForgeweaveItems.GROUT.get());
        output.accept(ForgeweaveItems.SEARED_BRICK.get());

        output.accept(ForgeweaveItems.SLIMY_MUD_GREEN.get());
        output.accept(ForgeweaveItems.SLIMY_MUD_MAGMA.get());

        output.accept(ForgeweaveItems.GRAVEYARD_SOIL.get());
        output.accept(ForgeweaveItems.CONSECRATED_SOIL.get());

        output.accept(ForgeweaveItems.MUD_BRICK.get());
        output.accept(ForgeweaveItems.MUD_BRICK_BLOCK.get());

        output.accept(ForgeweaveItems.MOSS.get());
        output.accept(ForgeweaveItems.MENDING_MOSS.get());
        output.accept(ForgeweaveItems.REINFORCED_PLATE.get());
        output.accept(ForgeweaveItems.SILKY_CLOTH.get());
        output.accept(ForgeweaveItems.SILKY_JEWEL.get());
        output.accept(ForgeweaveItems.EXTRA_MODIFIER.get());
        output.accept(ForgeweaveItems.NECROTIC_BONE.get());
        output.accept(ForgeweaveItems.EXPANDER_W.get());
        output.accept(ForgeweaveItems.EXPANDER_H.get());

        output.accept(ForgeweaveItems.CLEAR_GLASS.get());
        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            output.accept(color.block().get().asItem());
        }

        output.accept(ForgeweaveItems.INGOT_COBALT.get());
        output.accept(ForgeweaveItems.NUGGET_COBALT.get());
        output.accept(ForgeweaveItems.RAW_COBALT.get());
        output.accept(ForgeweaveItems.INGOT_ARDITE.get());
        output.accept(ForgeweaveItems.NUGGET_ARDITE.get());
        output.accept(ForgeweaveItems.RAW_ARDITE.get());
        output.accept(ForgeweaveItems.INGOT_MANYULLYN.get());
        output.accept(ForgeweaveItems.NUGGET_MANYULLYN.get());
        output.accept(ForgeweaveItems.RAW_MANYULLYN.get());
        output.accept(ForgeweaveItems.INGOT_ROSE_GOLD.get());
        output.accept(ForgeweaveItems.NUGGET_ROSE_GOLD.get());
        output.accept(ForgeweaveItems.RAW_ROSE_GOLD.get());
        output.accept(ForgeweaveItems.INGOT_STEEL.get());
        output.accept(ForgeweaveItems.NUGGET_STEEL.get());
        output.accept(ForgeweaveItems.INGOT_AMETHYST_BRONZE.get());
        output.accept(ForgeweaveItems.NUGGET_AMETHYST_BRONZE.get());

        output.accept(ForgeweaveItems.GREEN_SLIME_CRYSTAL.get());
        output.accept(ForgeweaveItems.BLUE_SLIME_CRYSTAL.get());
        output.accept(ForgeweaveItems.MAGMA_SLIME_CRYSTAL.get());
        output.accept(ForgeweaveItems.INGOT_KNIGHTSLIME.get());
        output.accept(ForgeweaveItems.NUGGET_KNIGHTSLIME.get());

        output.accept(ForgeweaveItems.COBALT_ORE.get());
        output.accept(ForgeweaveItems.ARDITE_ORE.get());

        output.accept(ForgeweaveItems.COBALT_BLOCK.get());
        output.accept(ForgeweaveItems.ARDITE_BLOCK.get());
        output.accept(ForgeweaveItems.MANYULLYN_BLOCK.get());
        output.accept(ForgeweaveItems.ROSE_GOLD_BLOCK.get());
        output.accept(ForgeweaveItems.STEEL_BLOCK.get());
        output.accept(ForgeweaveItems.KNIGHTSLIME_BLOCK.get()); // #232

        output.accept(ForgeweaveItems.INGOT_PIG_IRON.get());
        output.accept(ForgeweaveItems.NUGGET_PIG_IRON.get());
        output.accept(ForgeweaveItems.PIG_IRON_BLOCK.get());
        output.accept(ForgeweaveItems.FIREWOOD.get());

        output.accept(ForgeweaveItems.AMETHYST_BRONZE_BLOCK.get());
    }

    /**
     * Upstream's {@code tabTools}: every {@code ToolCore} ({@code ToolCore:74}). Read off
     * {@code ToolAssemblyRecipes#ENTRIES} -- the one table that already names every assemblable
     * tool, in the Tool Station's own order -- rather than a second hand-kept list, which is how
     * the mattock and the kama went missing from the single tab this replaces (issue #507).
     */
    static void addToolItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output rawOutput) {
        CreativeModeTab.Output output = enabledOnly(rawOutput);

        for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
            output.accept(entry.tool().get());
        }
    }

    static void addPartItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output) {
        addPartItems(parameters, output, ForgeweaveClientConfig.LIST_ALL_PART_MATERIALS.get());
    }

    /**
     * Upstream's {@code tabToolParts}: the patterns ({@code Pattern:28}), the tool parts
     * ({@code ToolPart:41}) and the sharpening kit ({@code TinkerTools:140}).
     *
     * <p>Takes {@code listAllPartMaterials} as a parameter rather than reading it (issue #276), so
     * the unit tests can drive both settings without standing up a {@code CLIENT}-type config spec
     * -- the same split {@code ToolTooltip#append} already uses for its Shift flag. Only the
     * part-material expansion at the bottom reads it; every plain item here is listed regardless.
     */
    static void addPartItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output rawOutput,
            boolean listAllPartMaterials) {
        CreativeModeTab.Output output = enabledOnly(rawOutput);

        output.accept(ForgeweaveItems.PATTERN_BLANK.get());
        output.accept(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_SHOVEL_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_AXE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_TOOL_BINDING.get());
        output.accept(ForgeweaveItems.PATTERN_TOOL_HANDLE.get());

        output.accept(ForgeweaveItems.PATTERN_SWORD_BLADE.get());
        output.accept(ForgeweaveItems.PATTERN_WIDE_GUARD.get());
        output.accept(ForgeweaveItems.PATTERN_HAND_GUARD.get());
        output.accept(ForgeweaveItems.PATTERN_CROSS_GUARD.get());
        output.accept(ForgeweaveItems.PATTERN_SIGN_PLATE.get());
        output.accept(ForgeweaveItems.PATTERN_PAN.get());
        output.accept(ForgeweaveItems.PATTERN_KNIFE_BLADE.get());
        output.accept(ForgeweaveItems.PATTERN_LARGE_SWORD_BLADE.get());
        output.accept(ForgeweaveItems.PATTERN_TOUGH_TOOL_ROD.get());
        output.accept(ForgeweaveItems.PATTERN_TOUGH_BINDING.get());
        output.accept(ForgeweaveItems.PATTERN_LARGE_PLATE.get());
        output.accept(ForgeweaveItems.PATTERN_HAMMER_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_EXCAVATOR_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_SCYTHE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_KAMA_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_BROAD_AXE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_VEIN_HAMMER_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_WAR_MACE_HEAD.get());
        output.accept(ForgeweaveItems.PATTERN_CURVED_BLADE.get());
        output.accept(ForgeweaveItems.PATTERN_KATANA_BLADE.get());
        output.accept(ForgeweaveItems.PATTERN_BOW_LIMB.get());
        output.accept(ForgeweaveItems.PATTERN_BOW_STRING.get());
        output.accept(ForgeweaveItems.PATTERN_SHARPENING_KIT.get());

        List<Holder.Reference<Material>> allMaterials =
                parameters.holders().lookupOrThrow(Material.REGISTRY).listElements().toList();
        List<Holder.Reference<Material>> materials =
                listAllPartMaterials ? allMaterials : allMaterials.stream().limit(1).toList();
        for (DeferredItem<PartItem> partItem : PART_ITEMS) {
            for (Holder.Reference<Material> material : materials) {
                if (!material.value().hasStatsFor(partItem.get().kind())) {
                    continue;
                }
                ItemStack stack = new ItemStack(partItem.get());
                stack.set(ForgeweaveDataComponents.MATERIAL.get(), material.key().location());
                output.accept(stack);
            }
        }
    }

    /**
     * Upstream's {@code tabSmeltery}: the seared block family ({@code BlockSeared:19},
     * {@code BlockEnumSmeltery:37}, {@code BlockSearedSlab:31}), the controllers
     * ({@code BlockSmelteryController:22}, {@code BlockSearedFurnaceController:25}), casting
     * ({@code BlockCasting:50}, {@code BlockFaucet:39}), the casts ({@code Cast:14},
     * {@code CastCustom:16}) and the molten metals ({@code BlockTinkerFluid:22} -- buckets here).
     */
    static void addSmelteryItems(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output rawOutput) {
        CreativeModeTab.Output output = enabledOnly(rawOutput);

        output.accept(ForgeweaveItems.SEARED_STONE.get());
        output.accept(ForgeweaveItems.SEARED_COBBLESTONE.get());
        output.accept(ForgeweaveItems.SEARED_PAVER.get());
        output.accept(ForgeweaveItems.SEARED_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_CRACKED_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_FANCY_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SQUARE_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_TRIANGLE_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SMALL_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_ROAD.get());
        output.accept(ForgeweaveItems.SEARED_TILE.get());
        output.accept(ForgeweaveItems.SEARED_CREEPER.get());

        output.accept(ForgeweaveItems.SEARED_STAIRS_STONE.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_COBBLESTONE.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_PAVER.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_CRACKED_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_FANCY_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_SQUARE_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_TRIANGLE_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_SMALL_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_ROAD.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_TILE.get());
        output.accept(ForgeweaveItems.SEARED_STAIRS_CREEPER.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_STONE.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_COBBLESTONE.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_PAVER.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_CRACKED_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_FANCY_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_SQUARE_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_TRIANGLE_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_SMALL_BRICKS.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_ROAD.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_TILE.get());
        output.accept(ForgeweaveItems.SEARED_SLAB_CREEPER.get());

        output.accept(ForgeweaveItems.STANDARD_CORE.get());
        output.accept(ForgeweaveItems.SEARED_FURNACE_CONTROLLER.get()); // #442
        output.accept(ForgeweaveItems.NETHER_CORE.get());
        output.accept(ForgeweaveItems.SEARED_TANK.get());
        output.accept(ForgeweaveItems.SEARED_GAUGE.get());
        output.accept(ForgeweaveItems.SEARED_WINDOW.get());
        output.accept(ForgeweaveItems.SEARED_DRAIN.get());
        output.accept(ForgeweaveItems.SEARED_DUCT.get());
        output.accept(ForgeweaveItems.SEARED_CHUTE.get());
        output.accept(ForgeweaveItems.SEARED_GLASS.get());

        output.accept(ForgeweaveItems.CASTING_TABLE.get());
        output.accept(ForgeweaveItems.CASTING_BASIN.get());
        output.accept(ForgeweaveItems.FAUCET.get());
        output.accept(ForgeweaveItems.CAST_INGOT.get());
        output.accept(ForgeweaveItems.CAST_NUGGET.get());
        output.accept(ForgeweaveItems.CAST_PICKAXE_HEAD.get());
        output.accept(ForgeweaveItems.CAST_SHOVEL_HEAD.get());
        output.accept(ForgeweaveItems.CAST_AXE_HEAD.get());
        output.accept(ForgeweaveItems.CAST_TOOL_BINDING.get());
        output.accept(ForgeweaveItems.CAST_TOOL_HANDLE.get());

        output.accept(ForgeweaveItems.CAST_SWORD_BLADE.get());
        output.accept(ForgeweaveItems.CAST_WIDE_GUARD.get());
        output.accept(ForgeweaveItems.CAST_HAND_GUARD.get());
        output.accept(ForgeweaveItems.CAST_CROSS_GUARD.get());
        output.accept(ForgeweaveItems.CAST_SIGN_PLATE.get());
        output.accept(ForgeweaveItems.CAST_PAN.get());
        output.accept(ForgeweaveItems.CAST_KNIFE_BLADE.get());
        output.accept(ForgeweaveItems.CAST_LARGE_SWORD_BLADE.get());
        output.accept(ForgeweaveItems.CAST_TOUGH_TOOL_ROD.get());
        output.accept(ForgeweaveItems.CAST_TOUGH_BINDING.get());
        output.accept(ForgeweaveItems.CAST_LARGE_PLATE.get());
        output.accept(ForgeweaveItems.CAST_HAMMER_HEAD.get());
        output.accept(ForgeweaveItems.CAST_EXCAVATOR_HEAD.get());
        output.accept(ForgeweaveItems.CAST_SCYTHE_HEAD.get());
        output.accept(ForgeweaveItems.CAST_KAMA_HEAD.get());
        output.accept(ForgeweaveItems.CAST_BROAD_AXE_HEAD.get());
        output.accept(ForgeweaveItems.CAST_VEIN_HAMMER_HEAD.get());
        output.accept(ForgeweaveItems.CAST_WAR_MACE_HEAD.get());
        output.accept(ForgeweaveItems.CAST_CURVED_BLADE.get());
        output.accept(ForgeweaveItems.CAST_KATANA_BLADE.get());
        output.accept(ForgeweaveItems.CAST_BOW_LIMB.get());
        output.accept(ForgeweaveItems.CAST_SHARPENING_KIT.get());
        output.accept(ForgeweaveItems.CAST_SHARD.get()); // #471/T40

        output.accept(ForgeweaveItems.CAST_GEM.get());
        output.accept(ForgeweaveItems.CAST_PLATE.get());
        output.accept(ForgeweaveItems.CAST_GEAR.get());

        ForgeweaveItems.CLAY_CASTS.values().forEach(clay -> output.accept(clay.get()));

        for (ForgeweaveFluids.MoltenMetal fluid : ForgeweaveFluids.all()) {
            output.accept(fluid.bucket().get());
        }
    }

    /**
     * Issue #506 (T75), upstream's {@code listAllTables}: lists {@code table} once per member of
     * {@code textureSource} (plain, plus a {@link ForgeweaveDataComponents#TEXTURE} component set to
     * that member's block), or just once plain if the tag has no members yet -- a unit-test
     * environment never binds real item tags, so this also keeps {@code
     * everyBlockItemAppearsInTheCreativeTab} green without needing tag fixtures. With {@code
     * listAllTableVariants} off, only the tag's first member is shown (upstream's own "first found"
     * wording, same rule {@code addPartItems} already applies to part materials).
     */
    private static void addTableVariants(CreativeModeTab.Output output, Item table, TagKey<Item> textureSource,
            boolean listAllTableVariants) {
        List<Holder<Item>> variants = BuiltInRegistries.ITEM.getTag(textureSource)
                .map(HolderSet::stream).map(Stream::toList).orElse(List.of());
        if (variants.isEmpty()) {
            output.accept(new ItemStack(table));
            return;
        }
        List<Holder<Item>> shown = listAllTableVariants ? variants : variants.subList(0, 1);
        for (Holder<Item> variant : shown) {
            ItemStack stack = new ItemStack(table);
            if (variant.value() instanceof BlockItem blockItem) {
                stack.set(ForgeweaveDataComponents.TEXTURE.get(), BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()));
            }
            output.accept(stack);
        }
    }

    private ForgeweaveCreativeTab() {}
}
