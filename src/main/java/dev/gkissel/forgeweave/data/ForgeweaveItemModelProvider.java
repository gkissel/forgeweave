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
 * model per item. The six pattern items share one flat icon texture; the five part items each get
 * their own texture named after the item (upstream hand-written models this replaces used the same
 * convention).
 */
public class ForgeweaveItemModelProvider extends ItemModelProvider {
    public ForgeweaveItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, Forgeweave.MODID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        ResourceLocation patternTexture = modLoc("item/pattern");
        patternModel(ForgeweaveItems.PATTERN_BLANK, patternTexture);
        patternModel(ForgeweaveItems.PATTERN_PICKAXE_HEAD, patternTexture);
        patternModel(ForgeweaveItems.PATTERN_SHOVEL_HEAD, patternTexture);
        patternModel(ForgeweaveItems.PATTERN_AXE_HEAD, patternTexture);
        patternModel(ForgeweaveItems.PATTERN_TOOL_BINDING, patternTexture);
        patternModel(ForgeweaveItems.PATTERN_TOOL_HANDLE, patternTexture);

        basicItem(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        basicItem(ForgeweaveItems.PART_SHOVEL_HEAD.get());
        basicItem(ForgeweaveItems.PART_AXE_HEAD.get());
        basicItem(ForgeweaveItems.PART_TOOL_BINDING.get());
        basicItem(ForgeweaveItems.PART_TOOL_HANDLE.get());
    }

    // Unchecked parent, matching basicItem()'s approach: "item/generated" is a vanilla builtin
    // model that isn't guaranteed to resolve through ExistingFileHelper in every datagen run mode.
    private void patternModel(DeferredItem<Item> item, ResourceLocation texture) {
        getBuilder(item.getId().toString())
                .parent(new ModelFile.UncheckedModelFile("item/generated"))
                .texture("layer0", texture);
    }
}
