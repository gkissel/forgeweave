package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;

import net.neoforged.neoforge.common.data.LanguageProvider;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * English translations for the creative tab, every item, and the four M1 materials. Material
 * names aren't derived from registered Java objects (materials are datapack data per ADR-0002),
 * so their keys are listed explicitly here, same as the hand-written lang file this replaces.
 */
public class ForgeweaveLanguageProvider extends LanguageProvider {
    public ForgeweaveLanguageProvider(PackOutput output) {
        super(output, Forgeweave.MODID, "en_us");
    }

    @Override
    protected void addTranslations() {
        add("itemGroup.forgeweave", "Forgeweave");

        addBlock(ForgeweaveBlocks.PART_BUILDER, "Part Builder");
        addBlock(ForgeweaveBlocks.TOOL_STATION, "Tool Station");

        addItem(ForgeweaveItems.PATTERN_BLANK, "Blank Pattern");
        addItem(ForgeweaveItems.PATTERN_PICKAXE_HEAD, "Pickaxe Head Pattern");
        addItem(ForgeweaveItems.PATTERN_SHOVEL_HEAD, "Shovel Head Pattern");
        addItem(ForgeweaveItems.PATTERN_AXE_HEAD, "Axe Head Pattern");
        addItem(ForgeweaveItems.PATTERN_TOOL_BINDING, "Tool Binding Pattern");
        addItem(ForgeweaveItems.PATTERN_TOOL_HANDLE, "Tool Handle Pattern");

        addItem(ForgeweaveItems.PART_PICKAXE_HEAD, "Pickaxe Head");
        addItem(ForgeweaveItems.PART_SHOVEL_HEAD, "Shovel Head");
        addItem(ForgeweaveItems.PART_AXE_HEAD, "Axe Head");
        addItem(ForgeweaveItems.PART_TOOL_BINDING, "Tool Binding");
        addItem(ForgeweaveItems.PART_TOOL_HANDLE, "Tool Handle");
        addItem(ForgeweaveItems.SHARD, "Shard");

        addItem(ForgeweaveItems.TOOL_PICKAXE, "Pickaxe");
        addItem(ForgeweaveItems.TOOL_SHOVEL, "Shovel");
        addItem(ForgeweaveItems.TOOL_HATCHET, "Hatchet");

        // Shown on a tool that ran out of durability (CONTEXT.md: Broken -- unusable, never destroyed).
        add("tooltip.forgeweave.broken", "Broken");

        add("material.forgeweave.wood", "Wood");
        add("material.forgeweave.stone", "Stone");
        add("material.forgeweave.flint", "Flint");
        add("material.forgeweave.bone", "Bone");

        // Trait names and descriptions, keyed by trait id like materials are by material id -- traits
        // are Java behavior selected by data (ADR-0002), so nothing derives these keys for us. The
        // tool info panel (issue #47) is what will display them; wording follows upstream 1.12's
        // modifier.<id>.name/.desc entries.
        add("trait.forgeweave.ecological.name", "Ecological");
        add("trait.forgeweave.ecological.description", "Renewable resources are so good, they regenerate by themselves!");
        add("trait.forgeweave.cheap.name", "Cheap");
        add("trait.forgeweave.cheap.description", "Increases durability gained when repairing the tool.");
        add("trait.forgeweave.crude.name", "Crude");
        add("trait.forgeweave.crude.description", "Bonus damage against unarmored targets.");
        add("trait.forgeweave.fractured.name", "Fractured");
        add("trait.forgeweave.fractured.description", "Your tool's damage is increased.");
    }
}
