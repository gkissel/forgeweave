package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;

import net.neoforged.neoforge.common.data.LanguageProvider;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryScan;
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
        addBlock(ForgeweaveBlocks.CRAFTING_STATION, "Crafting Station");
        addBlock(ForgeweaveBlocks.STENCIL_TABLE, "Stencil Table");
        addBlock(ForgeweaveBlocks.PATTERN_CHEST, "Pattern Chest");
        addBlock(ForgeweaveBlocks.PART_CHEST, "Part Chest");

        // The seared brick block family (docs/SCOPE.md M2 issue #93), names ported from upstream
        // 1.12's tile.tconstruct.seared.*.name entries (NOTICE.md).
        addBlock(ForgeweaveBlocks.SEARED_STONE, "Seared Stone");
        addBlock(ForgeweaveBlocks.SEARED_COBBLESTONE, "Seared Cobblestone");
        addBlock(ForgeweaveBlocks.SEARED_PAVER, "Seared Paver");
        addBlock(ForgeweaveBlocks.SEARED_BRICKS, "Seared Bricks");
        addBlock(ForgeweaveBlocks.SEARED_CRACKED_BRICKS, "Cracked Seared Bricks");
        addBlock(ForgeweaveBlocks.SEARED_FANCY_BRICKS, "Fancy Seared Bricks");
        addBlock(ForgeweaveBlocks.SEARED_SQUARE_BRICKS, "Square Seared Bricks");
        addBlock(ForgeweaveBlocks.SEARED_TRIANGLE_BRICKS, "Triangle Seared Bricks");
        addBlock(ForgeweaveBlocks.SEARED_SMALL_BRICKS, "Small Seared Bricks");
        addBlock(ForgeweaveBlocks.SEARED_ROAD, "Seared Road");
        addBlock(ForgeweaveBlocks.SEARED_TILE, "Seared Tiles");
        addBlock(ForgeweaveBlocks.SEARED_CREEPER, "Seared Creeperface");

        // The smeltery multiblock (docs/SCOPE.md M2 issue #95). Tank/drain names follow upstream
        // 1.12's tile.tconstruct.*.name entries; the two core tiers are SCOPE.md's own vocabulary.
        addBlock(ForgeweaveBlocks.STANDARD_CORE, "Standard Core");
        addBlock(ForgeweaveBlocks.NETHER_CORE, "Nether Core");
        addBlock(ForgeweaveBlocks.SEARED_TANK, "Seared Tank");
        addBlock(ForgeweaveBlocks.SEARED_GAUGE, "Seared Gauge");
        addBlock(ForgeweaveBlocks.SEARED_WINDOW, "Seared Window");
        addBlock(ForgeweaveBlocks.SEARED_DRAIN, "Seared Drain");

        // What a core reports when a player uses it (issue #95: "the controller reports why an
        // invalid structure fails to form"). Positions are passed as three numbers so the message
        // reads naturally in any language.
        add(SmelteryScan.KEY_FORMED, "Smeltery formed: %s x %s interior, %s high");
        add(SmelteryScan.KEY_NOT_SCANNED, "Smeltery not checked yet");
        add(SmelteryScan.KEY_NOT_LOADED, "Part of the smeltery is not loaded");
        add(SmelteryScan.KEY_BLOCKED_INTERIOR, "The inside of the smeltery is blocked at %s, %s, %s");
        add(SmelteryScan.KEY_TOO_LARGE, "The smeltery interior is %s x %s, larger than the maximum of %s");
        add(SmelteryScan.KEY_INVALID_FLOOR, "The floor needs a seared block at %s, %s, %s");
        add(SmelteryScan.KEY_INVALID_WALL, "The wall needs a seared block, tank or drain at %s, %s, %s");
        add(SmelteryScan.KEY_NO_TANK, "The smeltery needs at least one seared tank in its walls");
        add(SmelteryScan.KEY_CORE_OUTSIDE, "The core has to sit in a wall of the smeltery");

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

        addItem(ForgeweaveItems.GROUT, "Grout");
        addItem(ForgeweaveItems.SEARED_BRICK, "Seared Brick");

        // #107 batch: modifier reagent items (docs/SCOPE.md M2 issue #107), names ported from upstream
        // 1.12's item.materials.*.name entries (NOTICE.md).
        addItem(ForgeweaveItems.MOSS, "Moss");
        addItem(ForgeweaveItems.MENDING_MOSS, "Mending Moss");
        addItem(ForgeweaveItems.REINFORCED_PLATE, "Reinforced Plate");
        addItem(ForgeweaveItems.SILKY_CLOTH, "Silky Cloth");
        addItem(ForgeweaveItems.SILKY_JEWEL, "Silky Jewel");
        addItem(ForgeweaveItems.EXTRA_MODIFIER, "Extra Modifier");

        // Shown on a tool that ran out of durability (CONTEXT.md: Broken -- unusable, never destroyed).
        add("tooltip.forgeweave.broken", "Broken");

        // Tool descriptions, shown in the Tool Station's info panel while that tool's tab is selected
        // but nothing is built yet (issue #47). Wording follows upstream 1.12's tool.<id>.desc lines.
        add("item.forgeweave.pickaxe.description", "A basic mining tool. Digs stone, ores and anything else a pickaxe is meant for.");
        add("item.forgeweave.shovel.description", "Moves dirt, sand and gravel faster than your hands ever will.");
        add("item.forgeweave.hatchet.description", "Fells trees, and doubles as a weapon in a pinch.");

        // The stations' information panels (issue #47).
        add("gui.forgeweave.tool_station.name", "Tool name");
        add("gui.forgeweave.tool_station.repair", "Repair");
        add("gui.forgeweave.tool_station.repair.description",
                "Place a damaged tool in the middle slot and the material its head is made of alongside it "
                        + "to restore durability. A repaired tool keeps its parts, its stats and its traits. "
                        + "The same slots take modifier reagents: a tool has three modifier slots, and levelling "
                        + "a modifier up stays inside the slot it already occupies.");
        add("gui.forgeweave.tool_station.components", "Components");
        add("gui.forgeweave.tool_station.materials", "Materials");
        add("gui.forgeweave.tool_station.traits", "Traits");
        add("gui.forgeweave.tool_station.no_traits", "None");
        add("gui.forgeweave.tool_station.modifiers", "Modifiers");
        add("gui.forgeweave.tool_station.modifier_slots", "Free slots: %s");

        // Why an attempted modifier application was refused (issue #105), shown in the Tool Station's
        // tool info panel where upstream 1.12 shows its TinkerGuiException text.
        add("gui.forgeweave.modifier.no_slots", "This tool has no modifier slots left (%s to start with).");
        add("gui.forgeweave.modifier.max_level", "%s is already at its maximum level on this tool.");
        add("gui.forgeweave.modifier.invalid_reagent", "Apply one modifier at a time -- the other slot holds something else.");
        add("gui.forgeweave.modifier.not_enough_reagents", "Not enough of that reagent: %s are needed per step.");

        add("gui.forgeweave.stat.durability", "Durability: %s");
        add("gui.forgeweave.stat.mining_speed", "Mining Speed: %s");
        add("gui.forgeweave.stat.attack_damage", "Attack Damage: %s");
        add("gui.forgeweave.stat.handle_modifier", "Handle Modifier: %sx");
        add("gui.forgeweave.stat.handle_durability", "Handle Durability: %s");
        add("gui.forgeweave.stat.extra_durability", "Binding Durability: %s");

        add("gui.forgeweave.part_builder.info",
                "Put a pattern in the left slot and a material next to it. The part comes out on the right, "
                        + "and any material value left over comes back as shards.");
        add("gui.forgeweave.part_builder.cost", "Cost: %s");
        add("gui.forgeweave.part_builder.material_value", "Material Value: %s");

        // Assembled tool tooltip stat labels (issue #54), ported from upstream 1.12's
        // stat.head.*.name entries (NOTICE.md).
        add("tooltip.forgeweave.durability", "Durability");
        add("tooltip.forgeweave.mining_speed", "Mining Speed");
        add("tooltip.forgeweave.attack_damage", "Attack Damage");
        add("tooltip.forgeweave.tool_tier", "Tool Tier");
        // Upstream 1.12's "Modifiers: %d" line, shown on a tool that still has slots free.
        add("tooltip.forgeweave.modifier_slots", "Modifiers: %s");

        // Tool tier names (issue #65), keyed off the vanilla incorrect_for_<tier>_tool block tag each
        // material's incorrect_for_tool points at (ToolTooltip#tierName) -- only the tiers M1's
        // materials actually use; an unmapped tier degrades to a visible untranslated key, same as an
        // unknown trait id (MaterialDisplay).
        // Issue #79: M1's four materials are upstream's STONE (wood) and IRON (stone/flint/bone)
        // harvest levels, so `wooden` is no longer among them.
        add("tooltip.forgeweave.tier.stone", "Stone");
        add("tooltip.forgeweave.tier.iron", "Iron");
        // #106 batch: diamond/emerald can bump a tool onto these two tiers in play, unlike the pair
        // above which are the only ones M1's own materials start on.
        add("tooltip.forgeweave.tier.diamond", "Diamond");
        add("tooltip.forgeweave.tier.netherite", "Netherite");

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
        // Upstream's cheap and cheapskate descriptions, joined because stone's one Forgeweave trait id
        // carries both behaviors (issue #79; see ForgeweaveTraits#CHEAP and NOTICE.md).
        add("trait.forgeweave.cheap.description",
                "Increases durability gained when repairing the tool, but the tool has less durability.");
        add("trait.forgeweave.crude.name", "Crude");
        add("trait.forgeweave.crude.description", "Bonus damage against unarmored targets.");
        add("trait.forgeweave.fractured.name", "Fractured");
        add("trait.forgeweave.fractured.description", "Your tool's damage is increased.");

        // Modifier names and descriptions, keyed by modifier id the same way (issue #105, ADR-0004:
        // behavior is Java, the recipe that applies it is data). Wording follows upstream 1.12's
        // modifier.<id>.name/.desc entries.
        add("modifier.forgeweave.haste.name", "Haste");
        add("modifier.forgeweave.haste.description", "Redstone speeds the tool up. Every 50 pieces is another level.");

        // M2 metal traits (issue #102; material wiring is issue #103). Wording follows upstream
        // 1.12's modifier.<id>.name/.desc entries, same as the M1 traits above.
        add("trait.forgeweave.magnetic.name", "Magnetic");
        add("trait.forgeweave.magnetic.description", "Pulls nearby item drops toward you.");
        add("trait.forgeweave.magnetic2.name", "Magnetic II");
        add("trait.forgeweave.magnetic2.description", "Pulls nearby item drops toward you, from further away.");
        add("trait.forgeweave.momentum.name", "Momentum");
        add("trait.forgeweave.momentum.description", "Mining speed increases the longer you mine continuously.");
        add("trait.forgeweave.lightweight.name", "Lightweight");
        add("trait.forgeweave.lightweight.description", "Increases mining and attack speed.");
        add("trait.forgeweave.stonebound.name", "Stonebound");
        add("trait.forgeweave.stonebound.description", "Mining speed increases as the tool's durability drops.");
        add("trait.forgeweave.petramor.name", "Petramor");
        add("trait.forgeweave.petramor.description", "Chance to repair itself when mining stone.");
        add("trait.forgeweave.insatiable.name", "Insatiable");
        add("trait.forgeweave.insatiable.description",
                "Consecutive hits deal more damage, at the cost of extra durability.");
        add("trait.forgeweave.coldblooded.name", "Coldblooded");
        add("trait.forgeweave.coldblooded.description", "Bonus damage against undamaged targets.");
        add("trait.forgeweave.established.name", "Established");
        add("trait.forgeweave.established.description", "Grants bonus experience from kills.");

        // #108 batch: modern-vanilla modifiers (issue #108) -- Forgeweave originals, not upstream
        // ports, so these names and descriptions are this PR's own wording rather than a translation.
        add("modifier.forgeweave.searing.name", "Searing");
        add("modifier.forgeweave.searing.description", "Blocks this tool mines drop their furnace-smelted result.");
        add("modifier.forgeweave.magnetic_pull.name", "Magnetic Pull");
        add("modifier.forgeweave.magnetic_pull.description", "Block drops go straight into your inventory.");
        add("modifier.forgeweave.aquadynamic.name", "Aquadynamic");
        add("modifier.forgeweave.aquadynamic.description", "No mining speed penalty while your head is underwater.");
        add("modifier.forgeweave.resonant.name", "Resonant");
        add("modifier.forgeweave.resonant.description", "Bonus experience from blocks that drop it. Every level adds 50%.");
        add("modifier.forgeweave.far_reach.name", "Far Reach");
        add("modifier.forgeweave.far_reach.description", "Extends how far you can reach to mine blocks. Every level adds one block.");

        // #107 batch: reinforced, mending moss, silky, soulbound, extra-slot (docs/SCOPE.md M2 issue
        // #107), wording ported from upstream 1.12's modifier.<id>.name/.desc entries.
        add("modifier.forgeweave.reinforced.name", "Reinforced");
        add("modifier.forgeweave.reinforced.description",
                "Gives a chance to completely negate durability damage. Every level is another 20% chance.");
        add("modifier.forgeweave.mending_moss.name", "Mending Moss");
        add("modifier.forgeweave.mending_moss.description",
                "Stores experience and slowly uses it to repair the tool while it is carried.");
        add("modifier.forgeweave.silky.name", "Silky");
        add("modifier.forgeweave.silky.description", "Grants Silk Touch, at the cost of some mining speed and attack damage.");
        add("modifier.forgeweave.soulbound.name", "Soulbound");
        add("modifier.forgeweave.soulbound.description", "The tool stays with you even after you die.");
        add("modifier.forgeweave.extra_slot.name", "Extra Modifier");
        add("modifier.forgeweave.extra_slot.description", "Adds an extra modifier slot to the tool.");

        // Mending moss's acquisition (issue #107): shown when a player right-clicks a bookshelf with
        // moss but fewer than 10 XP levels, ported from upstream's message.mending_moss.not_enough_levels.
        add("message.forgeweave.mending_moss.not_enough_levels", "You need at least %s experience levels.");
        // #106 batch: luck, sharpness, diamond, emerald.
        add("modifier.forgeweave.luck.name", "Luck");
        add("modifier.forgeweave.luck.description",
                "Lapis lazuli grants Fortune, and Looting to weapons. Each level takes more than the last.");
        add("modifier.forgeweave.sharpness.name", "Sharpness");
        add("modifier.forgeweave.sharpness.description",
                "Quartz increases attack damage. Every 72 pieces is another level.");
        add("modifier.forgeweave.diamond.name", "Diamond");
        add("modifier.forgeweave.diamond.description", "Adds 500 durability and raises the tool's tier.");
        add("modifier.forgeweave.emerald.name", "Emerald");
        add("modifier.forgeweave.emerald.description", "Adds 50% durability and raises the tool's tier.");

        // JEI recipe category titles (issue #11); only shown when JEI is installed, since the
        // integration is optional (neoforge.mods.toml).
        add("jei.category.forgeweave.part_crafting", "Part Crafting");
        add("jei.category.forgeweave.tool_assembly", "Tool Assembly");
        add("jei.category.forgeweave.tool_repair", "Tool Repair");

        // The nine molten metal fluids (docs/SCOPE.md M2 issue #92). FluidType's default description
        // id is "fluid_type.<namespace>.<path>" (no addFluidType helper on LanguageProvider), shown
        // wherever a fluid stack's name is displayed (tanks, JEI).
        add("fluid_type.forgeweave.molten_iron", "Molten Iron");
        add("fluid_type.forgeweave.molten_copper", "Molten Copper");
        add("fluid_type.forgeweave.molten_gold", "Molten Gold");
        add("fluid_type.forgeweave.molten_cobalt", "Molten Cobalt");
        add("fluid_type.forgeweave.molten_ardite", "Molten Ardite");
        add("fluid_type.forgeweave.molten_manyullyn", "Molten Manyullyn");
        add("fluid_type.forgeweave.molten_rose_gold", "Molten Rose Gold");
        add("fluid_type.forgeweave.molten_netherite_scrap", "Molten Netherite Scrap");
        add("fluid_type.forgeweave.molten_netherite", "Molten Netherite");

        // #100 -- casting (docs/SCOPE.md M2 issue #100). Names follow upstream 1.12's
        // tile.casting.{table,basin}.name / tile.faucet.name and its cast item names.
        addBlock(ForgeweaveBlocks.CASTING_TABLE, "Casting Table");
        addBlock(ForgeweaveBlocks.CASTING_BASIN, "Casting Basin");
        addBlock(ForgeweaveBlocks.FAUCET, "Faucet");
        addItem(ForgeweaveItems.CAST_INGOT, "Ingot Cast");
        addItem(ForgeweaveItems.CAST_NUGGET, "Nugget Cast");
        addItem(ForgeweaveItems.CAST_PICKAXE_HEAD, "Pickaxe Head Cast");
        addItem(ForgeweaveItems.CAST_SHOVEL_HEAD, "Shovel Head Cast");
        addItem(ForgeweaveItems.CAST_AXE_HEAD, "Axe Head Cast");
        addItem(ForgeweaveItems.CAST_TOOL_BINDING, "Tool Binding Cast");
        addItem(ForgeweaveItems.CAST_TOOL_HANDLE, "Tool Handle Cast");
    }
}
