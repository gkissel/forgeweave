package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;
import net.minecraft.world.item.DyeColor;

import net.neoforged.neoforge.common.data.LanguageProvider;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SmelteryScan;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
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

        // #447 -- the entity every dropped tool spawns as; name ported from upstream 1.12's
        // EntityRegistry.registerModEntity("indestructible", ..., "Indestructible Item") (NOTICE.md).
        add("entity.forgeweave.indestructible_item", "Indestructible Item");

        addBlock(ForgeweaveBlocks.PART_BUILDER, "Part Builder");
        addBlock(ForgeweaveBlocks.TOOL_STATION, "Tool Station");
        addBlock(ForgeweaveBlocks.TOOL_FORGE, "Tool Forge");
        addBlock(ForgeweaveBlocks.CRAFTING_STATION, "Crafting Station");
        addBlock(ForgeweaveBlocks.STENCIL_TABLE, "Stencil Table");
        addBlock(ForgeweaveBlocks.PATTERN_CHEST, "Pattern Chest");
        addBlock(ForgeweaveBlocks.PART_CHEST, "Part Chest");

        // Grout (docs/SCOPE.md M2 issue #93; block per issue #129), name ported from upstream 1.12's
        // tile.tconstruct.soil.grout.name entry (NOTICE.md).
        addBlock(ForgeweaveBlocks.GROUT, "Grout");
        addBlock(ForgeweaveBlocks.SLIMY_MUD_GREEN, "Green Slimy Mud"); // #339
        addBlock(ForgeweaveBlocks.SLIMY_MUD_MAGMA, "Magma Slimy Mud"); // #339

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

        // Seared stairs + slabs (docs/SCOPE.md M3.4-5 issue #274), names ported from upstream 1.12's
        // tile.tconstruct.seared_stairs_*.name / tile.tconstruct.seared_slab(2).*.name entries
        // (NOTICE.md) -- including upstream's own "Seared Tile" singular for the tile variant, versus
        // "Seared Tiles" plural for the base block above.
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_STONE, "Seared Stone Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_COBBLESTONE, "Seared Cobblestone Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_PAVER, "Seared Paver Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_BRICKS, "Seared Bricks Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_CRACKED_BRICKS, "Cracked Seared Bricks Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_FANCY_BRICKS, "Fancy Seared Bricks Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_SQUARE_BRICKS, "Square Seared Bricks Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_TRIANGLE_BRICKS, "Triangle Seared Bricks Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_SMALL_BRICKS, "Small Seared Bricks Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_ROAD, "Seared Road Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_TILE, "Seared Tile Stairs");
        addBlock(ForgeweaveBlocks.SEARED_STAIRS_CREEPER, "Seared Creeperface Stairs");

        addBlock(ForgeweaveBlocks.SEARED_SLAB_STONE, "Seared Stone Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_COBBLESTONE, "Seared Cobblestone Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_PAVER, "Seared Paver Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_BRICKS, "Seared Bricks Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_CRACKED_BRICKS, "Cracked Seared Bricks Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_FANCY_BRICKS, "Fancy Seared Bricks Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_SQUARE_BRICKS, "Square Seared Bricks Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_TRIANGLE_BRICKS, "Triangle Seared Bricks Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_SMALL_BRICKS, "Small Seared Bricks Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_ROAD, "Seared Road Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_TILE, "Seared Tile Slab");
        addBlock(ForgeweaveBlocks.SEARED_SLAB_CREEPER, "Seared Creeperface Slab");

        // The smeltery multiblock (docs/SCOPE.md M2 issue #95). Tank/drain names follow upstream
        // 1.12's tile.tconstruct.*.name entries; the two core tiers are SCOPE.md's own vocabulary.
        addBlock(ForgeweaveBlocks.STANDARD_CORE, "Standard Core");
        addBlock(ForgeweaveBlocks.NETHER_CORE, "Nether Core");
        addBlock(ForgeweaveBlocks.SEARED_TANK, "Seared Tank");
        addBlock(ForgeweaveBlocks.SEARED_GAUGE, "Seared Gauge");
        addBlock(ForgeweaveBlocks.SEARED_WINDOW, "Seared Window");
        addBlock(ForgeweaveBlocks.SEARED_DRAIN, "Seared Drain");

        // #277 -- filtered fluid I/O and item I/O (docs/SCOPE.md M3.4). Names from the 1.20 clone's
        // block.tconstruct.seared_duct/seared_chute entries. No hover tooltips: Forgeweave has no
        // tooltip-carrying block item (the drain has none either), and upstream's would be the only
        // two in the mod.
        addBlock(ForgeweaveBlocks.SEARED_DUCT, "Seared Duct");
        addBlock(ForgeweaveBlocks.SEARED_CHUTE, "Seared Chute");

        // Plain seared glass (docs/SCOPE.md M3.3 issue #289), name from upstream's tile.tconstruct.seared_glass.name.
        addBlock(ForgeweaveBlocks.SEARED_GLASS, "Seared Glass");

        // #275 -- clear glass and its 16 clear stained glass colors, names ported from upstream's
        // tile.tconstruct.glass_clear.name / the color's own English word (EnumGlassColor has no lang
        // entry of its own -- upstream's tile.tconstruct.stained_glass_clear.name is unqualified and
        // relies on the item's damage-bar tooltip for the color, so "<Color> Stained Clear Glass" is
        // this provider's own name, following the recipe files' own "<color>_stained_clear_glass"
        // word order).
        addBlock(ForgeweaveBlocks.CLEAR_GLASS, "Clear Glass");
        for (ForgeweaveBlocks.StainedGlassColor color : ForgeweaveBlocks.clearStainedGlassColors()) {
            addBlock(color.block(), titleCase(color.dye().getName()) + " Stained Clear Glass");
        }

        // What a core reports when a player uses it (issue #95: "the controller reports why an
        // invalid structure fails to form"). Positions are passed as three numbers so the message
        // reads naturally in any language.
        add(SmelteryScan.KEY_FORMED, "Smeltery formed: %s x %s interior, %s high");
        add(SmelteryScan.KEY_NOT_SCANNED, "Smeltery not checked yet");
        add(SmelteryScan.KEY_NOT_LOADED, "Part of the smeltery is not loaded");
        add(SmelteryScan.KEY_BLOCKED_INTERIOR, "The inside of the smeltery is blocked at %s, %s, %s");
        add(SmelteryScan.KEY_TOO_LARGE, "The smeltery interior is %s x %s, larger than the maximum of %s");
        add(SmelteryScan.KEY_INVALID_FLOOR, "The floor needs a seared block at %s, %s, %s");
        add(SmelteryScan.KEY_INVALID_WALL, "The wall needs a seared block, tank, drain, duct or chute at %s, %s, %s");
        add(SmelteryScan.KEY_NO_TANK, "The smeltery needs at least one seared tank in its walls");
        add(SmelteryScan.KEY_CLAIMED, "Another smeltery already uses the block at %s, %s, %s");
        add(SmelteryScan.KEY_CORE_OUTSIDE, "The core has to sit in a wall of the smeltery");

        // #101: the smeltery GUI's tank and fuel tooltips, following upstream 1.12's gui.smeltery.*
        // entries word for word -- the unit abbreviations are deliberately lowercase and terse
        // because they trail a number in a dense tooltip ("3 Ingots", "144 mb").
        add("gui.forgeweave.smeltery.capacity", "Capacity:");
        add("gui.forgeweave.smeltery.capacity_available", "Free:");
        add("gui.forgeweave.smeltery.capacity_used", "Used:");
        add("gui.forgeweave.smeltery.liquid.block", "Blocks");
        add("gui.forgeweave.smeltery.liquid.ingot", "Ingots");
        add("gui.forgeweave.smeltery.liquid.nugget", "Nuggets");
        // #377: upstream's fourth unit, for fluids cast through the gem cast rather than an ingot
        // one -- molten emerald since #361, at Material.VALUE_Gem = 666 mB apiece.
        add("gui.forgeweave.smeltery.liquid.gem", "Gems");
        add("gui.forgeweave.smeltery.liquid.kilobucket", "kb");
        add("gui.forgeweave.smeltery.liquid.bucket", "b");
        add("gui.forgeweave.smeltery.liquid.millibucket", "mb");
        add("gui.forgeweave.smeltery.fuel", "Fuel");
        add("gui.forgeweave.smeltery.fuel.empty", "No fuel found");
        // Upstream's gui.smeltery.fuel.heat, shown while a burn is under way (#131). Its %s is a
        // gui.forgeweave.temperature.* component, so the unit follows the temperatureCelsius
        // preference (#276, upstream's Util#temperatureString).
        add("gui.forgeweave.smeltery.fuel.heat", "Temperature: %s");
        // #377: upstream's gui.smeltery.fuel.invalid, shown when the wall tank holds a fluid the
        // smeltery cannot burn. Its colour comes from the screen (upstream bakes a section sign into
        // the string, which a Component cannot carry).
        add("gui.forgeweave.smeltery.fuel.invalid", "%s is not a valid smeltery fuel!");
        // #377: why a melt slot's heat bar is not advancing, upstream's gui.smeltery.progress.*.
        // upstream's fourth, no_recipe, has no Forgeweave use -- see SmelteryScreen#stallReason.
        add("gui.forgeweave.smeltery.progress.no_fuel", "No valid fuel in smeltery");
        add("gui.forgeweave.smeltery.progress.no_heat", "Not enough heat to melt this item");
        add("gui.forgeweave.smeltery.progress.no_space", "Not enough free space in the smeltery");
        add("gui.forgeweave.temperature.celsius", "%s°C");
        add("gui.forgeweave.temperature.kelvin", "%sK");
        add("tooltip.forgeweave.hold_shift", "Hold Shift for buckets");

        // The Pattern/Part Chest's page label (issue #305: self-expanding, paged storage).
        add("gui.forgeweave.chest.page", "Page %s/%s");

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

        // M3 tool parts + patterns (docs/SCOPE.md M3 issue #151), names ported from upstream 1.12's
        // item.materials.*.name entries for the twelve parts with a clone counterpart (NOTICE.md);
        // "Vein Hammer Head"/"Vein Hammer Head Pattern" are this PR's own wording (no upstream name to
        // port -- the part itself has no 1.12/1.20 counterpart either).
        addItem(ForgeweaveItems.PATTERN_SWORD_BLADE, "Sword Blade Pattern");
        addItem(ForgeweaveItems.PATTERN_WIDE_GUARD, "Wide Guard Pattern");
        addItem(ForgeweaveItems.PATTERN_HAND_GUARD, "Hand Guard Pattern");
        addItem(ForgeweaveItems.PATTERN_CROSS_GUARD, "Cross Guard Pattern");
        addItem(ForgeweaveItems.PATTERN_SIGN_PLATE, "Sign Plate Pattern");
        addItem(ForgeweaveItems.PATTERN_PAN, "Pan Pattern");
        addItem(ForgeweaveItems.PATTERN_KNIFE_BLADE, "Knife Blade Pattern");
        addItem(ForgeweaveItems.PATTERN_LARGE_SWORD_BLADE, "Large Sword Blade Pattern");
        addItem(ForgeweaveItems.PATTERN_TOUGH_TOOL_ROD, "Tough Tool Rod Pattern");
        addItem(ForgeweaveItems.PATTERN_TOUGH_BINDING, "Tough Binding Pattern");
        addItem(ForgeweaveItems.PATTERN_LARGE_PLATE, "Large Plate Pattern");
        addItem(ForgeweaveItems.PATTERN_HAMMER_HEAD, "Hammer Head Pattern");
        addItem(ForgeweaveItems.PATTERN_EXCAVATOR_HEAD, "Excavator Head Pattern");
        addItem(ForgeweaveItems.PATTERN_SCYTHE_HEAD, "Scythe Head Pattern");
        addItem(ForgeweaveItems.PATTERN_KAMA_HEAD, "Kama Head Pattern");
        addItem(ForgeweaveItems.PATTERN_BROAD_AXE_HEAD, "Broad Axe Head Pattern");
        addItem(ForgeweaveItems.PATTERN_VEIN_HAMMER_HEAD, "Vein Hammer Head Pattern");
        addItem(ForgeweaveItems.PATTERN_WAR_MACE_HEAD, "War Mace Head Pattern");
        addItem(ForgeweaveItems.PATTERN_CURVED_BLADE, "Curved Blade Pattern");
        addItem(ForgeweaveItems.PATTERN_KATANA_BLADE, "Katana Blade Pattern");
        // #393: upstream's own one-word spellings, item.tconstruct.{bow_limb,bow_string}.name,
        // through its item.tconstruct.pattern.name="%s Pattern" / cast.name="%s Cast" formats.
        addItem(ForgeweaveItems.PATTERN_BOW_LIMB, "Bowlimb Pattern");
        addItem(ForgeweaveItems.PATTERN_BOW_STRING, "Bowstring Pattern");
        addItem(ForgeweaveItems.PATTERN_SHARPENING_KIT, "Sharpening Kit Pattern");

        addItem(ForgeweaveItems.PART_SWORD_BLADE, "Sword Blade");
        addItem(ForgeweaveItems.PART_WIDE_GUARD, "Wide Guard");
        addItem(ForgeweaveItems.PART_HAND_GUARD, "Hand Guard");
        addItem(ForgeweaveItems.PART_CROSS_GUARD, "Cross Guard");
        addItem(ForgeweaveItems.PART_SIGN_PLATE, "Sign Plate");
        addItem(ForgeweaveItems.PART_PAN, "Pan");
        addItem(ForgeweaveItems.PART_KNIFE_BLADE, "Knife Blade");
        addItem(ForgeweaveItems.PART_LARGE_SWORD_BLADE, "Large Sword Blade");
        addItem(ForgeweaveItems.PART_TOUGH_TOOL_ROD, "Tough Tool Rod");
        addItem(ForgeweaveItems.PART_TOUGH_BINDING, "Tough Binding");
        addItem(ForgeweaveItems.PART_LARGE_PLATE, "Large Plate");
        addItem(ForgeweaveItems.PART_HAMMER_HEAD, "Hammer Head");
        addItem(ForgeweaveItems.PART_EXCAVATOR_HEAD, "Excavator Head");
        addItem(ForgeweaveItems.PART_SCYTHE_HEAD, "Scythe Head");
        addItem(ForgeweaveItems.PART_KAMA_HEAD, "Kama Head");
        addItem(ForgeweaveItems.PART_BROAD_AXE_HEAD, "Broad Axe Head");
        addItem(ForgeweaveItems.PART_VEIN_HAMMER_HEAD, "Vein Hammer Head");
        // #161's own wording; the warmace has no clone counterpart to port a name from.
        addItem(ForgeweaveItems.PART_WAR_MACE_HEAD, "War Mace Head");
        addItem(ForgeweaveItems.PART_CURVED_BLADE, "Curved Blade");
        // #160: the katana has no upstream counterpart, so its part and tool names are ours.
        addItem(ForgeweaveItems.PART_KATANA_BLADE, "Katana Blade");
        // #393: upstream item.tconstruct.{bow_limb,bow_string}.name, one word each.
        addItem(ForgeweaveItems.PART_BOW_LIMB, "Bowlimb");
        addItem(ForgeweaveItems.PART_BOW_STRING, "Bowstring");
        // #271: upstream item.tconstruct.sharpening_kit.name.
        addItem(ForgeweaveItems.PART_SHARPENING_KIT, "Sharpening Kit");

        addItem(ForgeweaveItems.TOOL_PICKAXE, "Pickaxe");
        addItem(ForgeweaveItems.TOOL_SHOVEL, "Shovel");
        addItem(ForgeweaveItems.TOOL_HATCHET, "Hatchet");
        addItem(ForgeweaveItems.TOOL_WARMACE, "Warmace");

        // M3 Tool Station weapons (docs/SCOPE.md M3 issue #155). Names follow upstream 1.12's own
        // item.*.name entries for the five it ships; the dagger is Forgeweave's, from the modern
        // branch's shape. The ".description" key next to each is the Tool Station tab blurb
        // (ToolStationTabs.Tab#descriptionKey), same family the M1 tools' tabs use.
        addItem(ForgeweaveItems.TOOL_BROADSWORD, "Broadsword");
        add("item.forgeweave.broadsword.description",
                "A balanced sword. A solid, grounded blow carries to everything nearby, and right-click "
                        + "raises a brief parry that turns aside one blow and slows whoever threw it.");
        addItem(ForgeweaveItems.TOOL_LONGSWORD, "Longsword");
        add("item.forgeweave.longsword.description",
                "A long blade with reach. Hold right-click to charge, then release to leap in the direction you are looking.");
        addItem(ForgeweaveItems.TOOL_RAPIER, "Rapier");
        add("item.forgeweave.rapier.description",
                "A quick, light blade. Every hit also tears out a fraction of the target's remaining health, straight through armour.");
        addItem(ForgeweaveItems.TOOL_BATTLESIGN, "Battlesign");
        add("item.forgeweave.battlesign.description",
                "A broad slab on a stick. Hold right-click to block; an arrow caught head-on is sent back at whoever fired it.");
        addItem(ForgeweaveItems.TOOL_FRYING_PAN, "Frying Pan");
        add("item.forgeweave.frying_pan.description",
                "Heavy, flat and loud. Its blows send whatever they land on flying.");
        addItem(ForgeweaveItems.TOOL_DAGGER, "Dagger");
        add("item.forgeweave.dagger.description",
                "A short, fast blade. Strike from behind and it bites far deeper than its size suggests.");

        // Innate names and descriptions (docs/SCOPE.md M3: every tool carries a combat innate).
        // ToolTooltip shows these on Shift, next to the material traits.
        add("tooltip.forgeweave.innate.parry.name", "Parry");
        add("tooltip.forgeweave.innate.parry.description",
                "A full-strength, grounded blow also strikes everything within reach. Right-click opens "
                        + "a brief window that negates one incoming melee blow and slows the attacker.");
        add("tooltip.forgeweave.innate.charged_leap.name", "Charged Leap");
        add("tooltip.forgeweave.innate.charged_leap.description",
                "Hold right-click to charge, then release to leap; the longer the charge, the further the jump.");
        add("tooltip.forgeweave.innate.vital_thrust.name", "Vital Thrust");
        add("tooltip.forgeweave.innate.vital_thrust.description",
                "Each hit deals a further 5% of the target's remaining health, ignoring armour. "
                        + "Right-click on the ground to hop back out of reach.");
        add("tooltip.forgeweave.innate.deflect.name", "Deflect");
        add("tooltip.forgeweave.innate.deflect.description",
                "While blocking, a projectile caught head-on is returned to its sender.");
        add("tooltip.forgeweave.innate.heavy_swing.name", "Heavy Swing");
        add("tooltip.forgeweave.innate.heavy_swing.description",
                "Hits knock the target back twice as far as an ordinary blow. Hold right-click to charge, "
                        + "then release to launch whatever you are looking at; a full charge sears it.");
        add("tooltip.forgeweave.innate.backstab.name", "Backstab");
        add("tooltip.forgeweave.innate.backstab.description",
                "Striking from behind adds up to double damage, at its strongest directly behind the target.");
        add("tooltip.forgeweave.innate.heft.name", "Heft");
        add("tooltip.forgeweave.innate.heft.description",
                "A blow now and then lands with the tool's full weight behind it, sending the target flying.");
        add("tooltip.forgeweave.innate.reap.name", "Reap");
        add("tooltip.forgeweave.innate.reap.description",
                "Hits against an already badly wounded target bite a quarter deeper.");
        // M3 station tools (docs/SCOPE.md issue #156).
        addItem(ForgeweaveItems.TOOL_MATTOCK, "Mattock");
        addItem(ForgeweaveItems.TOOL_KAMA, "Kama");
        // M3 station-tier weapons (docs/SCOPE.md M3 issue #159).
        addItem(ForgeweaveItems.TOOL_BATTLEAXE, "Battleaxe");
        addItem(ForgeweaveItems.TOOL_SCIMITAR, "Scimitar");
        // The scimitar's bleed (issue #159); see combat.LacerateEffect.
        add("effect.forgeweave.lacerate", "Lacerated");
        addItem(ForgeweaveItems.TOOL_KATANA, "Katana");
        addItem(ForgeweaveItems.TOOL_CLEAVER, "Cleaver"); // #158
        // M3.5 #394: upstream item.tconstruct.shortbow.name / .desc, minus its closing "Uses
        // Tinkers' Arrows." -- M3.5 fires vanilla arrows only (docs/SCOPE.md), and the sentence
        // about fast movement while drawing describes BowCore#preventSlowDown, which is not ported.
        addItem(ForgeweaveItems.TOOL_SHORTBOW, "Shortbow");
        add("item.forgeweave.shortbow.description",
                "The Shortbow is a quick and nimble weapon. It draws fast and looses arrows at a rapid rate. Fires vanilla arrows.");
        // M3.5 #395: upstream item.tconstruct.longbow.name / .desc and .crossbow.name / .desc, each
        // minus its closing "Uses Tinkers' Arrows." / "Uses Tinkers' Bolts." -- M3.5 ships no
        // material projectile, so both fire vanilla arrows (docs/SCOPE.md).
        addItem(ForgeweaveItems.TOOL_LONGBOW, "Longbow");
        add("item.forgeweave.longbow.description",
                "The Longbow is a powerful long range weapon. It provides high damage but is less mobile than its short brother. Fires vanilla arrows.");
        addItem(ForgeweaveItems.TOOL_CROSSBOW, "Crossbow");
        add("item.forgeweave.crossbow.description",
                "The Crossbow is a slow but very powerful weapon. It has to be loaded beforehand by holding right click, but can be fired at any moment afterwards. Fires vanilla arrows.");

        // The large harvest tools (docs/SCOPE.md M3 issue #157), names ported from upstream 1.12's
        // item.<tool>.name entries; "Vein Hammer" is this repository's own wording (no 1.12 tool).
        addItem(ForgeweaveItems.TOOL_HAMMER, "Hammer");
        addItem(ForgeweaveItems.TOOL_EXCAVATOR, "Excavator");
        addItem(ForgeweaveItems.TOOL_LUMBERAXE, "Lumber Axe");
        addItem(ForgeweaveItems.TOOL_SCYTHE, "Scythe");
        addItem(ForgeweaveItems.TOOL_VEIN_HAMMER, "Vein Hammer");

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

        // #160's innate, in the same name/description family as every other one above.
        add("tooltip.forgeweave.innate.damage_ramp.name", "Rising Edge");
        add("tooltip.forgeweave.innate.damage_ramp.description",
                "Every landed hit adds 10% damage to the next, up to 75%. Stop swinging for five "
                        + "seconds and it lapses.");

        // Tool descriptions, shown in the Tool Station's info panel while that tool's tab is selected
        // but nothing is built yet (issue #47). Wording follows upstream 1.12's tool.<id>.desc lines.
        add("item.forgeweave.pickaxe.description", "A basic mining tool. Digs stone, ores and anything else a pickaxe is meant for.");
        add("item.forgeweave.shovel.description", "Moves dirt, sand and gravel faster than your hands ever will.");
        add("item.forgeweave.hatchet.description", "Fells trees, and doubles as a weapon in a pinch.");
        // #161: no upstream line to follow. Describes the smash in the terms a player meets it in --
        // the numbers themselves are vanilla's mace's, so the wording deliberately quotes none.
        add("item.forgeweave.warmace.description",
                "A heavy flanged mace, forged at the Tool Forge. Strike while falling and the blow lands "
                        + "harder the further you fell, shoves everything nearby away, and leaves you unhurt "
                        + "by the landing.");
        add("item.forgeweave.mattock.description",
                "An axe and a shovel in one head. Tills soil like a hoe, and its heft carries a chance "
                        + "of a strong knockback on hit.");
        add("item.forgeweave.kama.description",
                "Shears sheep and harvests+replants mature crops at a right-click. Deals extra damage "
                        + "against targets already at low health.");
        add("item.forgeweave.battleaxe.description",
                "Two heavy heads on one haft. A fully charged swing carries through everything in a "
                        + "short arc in front of you, and staggers whatever it lands on.");
        add("item.forgeweave.scimitar.description",
                "A light curved blade. Its cuts keep bleeding after the swing, and fresh cuts stack "
                        + "on top of the ones already open.");
        // #160 -- no upstream tool.katana.desc to follow; this PR's own wording.
        add("item.forgeweave.katana.description",
                "A long, single-edged blade. Every blow that lands makes the next one hit harder, "
                        + "until you stop swinging.");
        add("item.forgeweave.hammer.description", "Breaks a 3x3 of stone at once, slowly. Leaves what it hits reeling.");
        add("item.forgeweave.excavator.description", "Moves a 3x3 of earth at a time, and knocks anything in the way flat.");
        add("item.forgeweave.lumberaxe.description", "Fells a whole tree in one swing, and hits hardest on the first blow.");
        add("item.forgeweave.scythe.description", "Reaps a 3x3x3 of crops and replants them, and cuts everything around what it strikes.");
        add("item.forgeweave.vein_hammer.description", "Follows an ore vein through the stone, and shrugs armor aside.");

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
        // #152: why a large tool refuses to assemble at a Tool Station.
        add("gui.forgeweave.tool_station.needs_forge", "This tool is too large to assemble here. Build it at a Tool Forge.");
        add("gui.forgeweave.tool_station.modifier_slots", "Free slots: %s");
        // Content-family toggles ticket: this kind of tool, or a part only this kind of tool takes,
        // belongs to a content family the server has switched off. Deliberately does not name the
        // family or the option -- a player at the station cannot act on either.
        add("gui.forgeweave.tool_station.family_disabled",
                "This kind of equipment is disabled on this server.");
        // #378, upstream gui.error.wrong_material_part: a part of the right shape whose material
        // this world has no definition for, so the station can build nothing from it.
        add("gui.forgeweave.tool_station.wrong_material_part",
                "One or multiple items have an unsupported material for this tool.");

        // The captions a rejection takes an info panel over with (issue #378), upstream's gui.error
        // and gui.warning. Errors are crafts that were refused, warnings are loadouts that were
        // never going to craft -- see StationMenu.Rejection.
        add("gui.forgeweave.error", "ERROR");
        add("gui.forgeweave.warning", "WARNING");

        // Why an attempted modifier application was refused (issue #105), shown in the Tool Station's
        // tool info panel where upstream 1.12 shows its TinkerGuiException text.
        add("gui.forgeweave.modifier.no_slots", "This tool has no modifier slots left (%s to start with).");
        add("gui.forgeweave.modifier.max_level", "%s is already at its maximum level on this tool.");
        add("gui.forgeweave.modifier.invalid_reagent", "The other slot holds something no modifier accepts.");
        // Content-family toggles ticket: `modifiers` is off, which covers embossing and fortification
        // too (maintainer decision). Deliberately says "applied" rather than "disabled" -- what is
        // already on a tool keeps working, and repair and part exchange are unaffected.
        add("gui.forgeweave.modifier.modifiers_disabled",
                "Tools cannot be modified on this server. What is already on a tool still works, "
                        + "and it can still be repaired and have its parts replaced.");
        add("gui.forgeweave.modifier.not_enough_reagents", "Not enough of that reagent: %s are needed per step.");
        // Issue #259 (multi-unit reagents): a whole reagent worth more units than the cap has room
        // for -- e.g. a 9-unit redstone block against 5 remaining units of haste.
        add("gui.forgeweave.modifier.reagent_overshoot", "That reagent is worth more than %s has room for on this tool.");
        // Issue #223 (wind burst): the tool the loaded modifier's own vanilla enchantment doesn't
        // support -- e.g. a breeze rod on anything but the warmace.
        add("gui.forgeweave.modifier.unsupported_tool", "%s cannot be applied to this tool.");
        // Issue #424, upstream's modifier.haste.extra ("Bonus-Speed: +%s"): what a modifier adds to
        // the speed the tool it sits on actually uses -- draw speed on a bow, attack speed on a
        // weapon (StationText#modifierExtraInfo).
        add("gui.forgeweave.modifier.bonus_speed", "Bonus Speed: +%s");

        // Why an attempted part exchange was refused (issue #264), same info-panel surface. The
        // durability line mirrors upstream 1.12's gui.error.not_enough_durability.
        add("gui.forgeweave.exchange.wrong_part", "This tool has no slot for that part.");
        add("gui.forgeweave.exchange.same_material", "The tool already has a part of that material there.");
        add("gui.forgeweave.exchange.not_enough_durability",
                "Not enough durability to replace parts! %s more durability required.");
        // Issue #293, upstream's gui.error.not_enough_modifiers ("Not enough Modifiers. (%d needed)"):
        // the new part set grants fewer modifier slots than the tool's modifiers already occupy.
        add("gui.forgeweave.exchange.not_enough_slots",
                "Not enough modifier slots to replace parts! %s more needed.");
        add("gui.forgeweave.exchange.needs_forge",
                "This tool is too large to work on here. Exchange its parts at a Tool Forge.");

        add("gui.forgeweave.stat.durability", "Durability: %s");
        add("gui.forgeweave.stat.mining_speed", "Mining Speed: %s");
        add("gui.forgeweave.stat.attack_damage", "Attack Damage: %s");
        add("gui.forgeweave.stat.handle_modifier", "Handle Modifier: %sx");
        add("gui.forgeweave.stat.handle_durability", "Handle Durability: %s");
        add("gui.forgeweave.stat.extra_durability", "Binding Durability: %s");
        // #392, upstream stat.bow.{drawspeed,range,damage}.name and stat.bowstring.modifier.name.
        // The draw speed row is upstream's own inverted view of the stored value (StationText).
        add("gui.forgeweave.stat.drawspeed", "Drawspeed: %s");
        add("gui.forgeweave.stat.range", "Range Multiplier: %s");
        add("gui.forgeweave.stat.bonus_damage", "Bonus Damage: %s");
        // Upstream's row is the bare "Modifier"; qualified here the way "Handle Modifier" already is,
        // so the two multiplier rows can't be told apart only by which group they sit under.
        add("gui.forgeweave.stat.bowstring_modifier", "Bowstring Modifier: %sx");

        // What each stat row says on hover (issue #376), ported from upstream 1.12's
        // stat.head/handle/extra.*.desc entries (NOTICE.md). The underlined heading each group sits
        // under is issue #379's tooltip.forgeweave.stat_type.* below, shared with the part tooltip.
        add("gui.forgeweave.stat.durability.desc",
                "The base value for durability calculations. Usually the largest part of a tool's total durability.");
        add("gui.forgeweave.stat.mining_speed.desc",
                "How fast a tool with a head of this material mines blocks. Other parts may influence it.");
        add("gui.forgeweave.stat.attack_damage.desc",
                "The base value for attack calculations. The end result depends on the tool and its other parts.");
        add("gui.forgeweave.stat.handle_modifier.desc",
                "How well this material serves as a handle. The tool's total durability is multiplied by it.");
        add("gui.forgeweave.stat.handle_durability.desc",
                "How well the material can be held. Tool durability is changed by this amount.");
        add("gui.forgeweave.stat.extra_durability.desc",
                "How much durability this part contributes when used as a binding.");
        // #392, upstream stat.bow.*.desc / stat.bowstring.modifier.desc verbatim (NOTICE.md).
        add("gui.forgeweave.stat.drawspeed.desc", "How fast you can draw the bow.");
        add("gui.forgeweave.stat.range.desc", "How far the projectile can be propelled.");
        add("gui.forgeweave.stat.bonus_damage.desc", "Bonus damage dealt on hit. The force of the arrow.");
        add("gui.forgeweave.stat.bowstring_modifier.desc", "Tool durability will be multiplied by this.");

        // Upstream's gui.general.hover, shown by the grey "?" every info panel puts in its top-right
        // corner while some line on it has hover text (issue #376).
        add("gui.forgeweave.general.hover", "Hover over the entries for more info");

        add("gui.forgeweave.part_builder.info",
                "Put a pattern in the left slot and a material next to it. The part comes out on the right, "
                        + "and any material value left over comes back as shards.");
        add("gui.forgeweave.part_builder.cost", "Cost: %s");
        // #378: upstream's own two-argument gui.partbuilder.material_value. The amount is in ingots
        // (fractional when the stacks don't come out even) and the material's name follows it, which
        // with two material slots is the only thing saying which stack the total was counted against.
        add("gui.forgeweave.part_builder.material_value", "Material Value: %s %s");
        // #378, upstream gui.error.invalid_pattern / gui.error.useless_tool_part.
        add("gui.forgeweave.part_builder.invalid_pattern", "Pattern does not contain a valid tool part!");
        // Content-family toggles ticket: the smeltery family is off, shown over the smeltery's melt
        // grid because a fully built, fully fuelled structure otherwise gives no clue why it is idle.
        add("gui.forgeweave.smeltery.disabled", "The smeltery is disabled on this server.");
        add("gui.forgeweave.part_builder.useless_tool_part",
                "This part cannot be used to craft any tool! Either the material %s is missing some "
                        + "information, or no tool uses a %s in its crafting.");

        // Assembled tool tooltip stat labels (issue #54), ported from upstream 1.12's
        // stat.head.*.name entries (NOTICE.md).
        add("tooltip.forgeweave.durability", "Durability");
        add("tooltip.forgeweave.mining_speed", "Mining Speed");
        add("tooltip.forgeweave.attack_damage", "Attack Damage");
        add("tooltip.forgeweave.tool_tier", "Tool Tier");
        // Upstream 1.12's "Modifiers: %d" line, shown on a tool that still has slots free.
        add("tooltip.forgeweave.modifier_slots", "Modifiers: %s");
        // Issue #380: the heading of each per-part section in the Shift tier -- material name, then
        // part name ("Stone Pickaxe Head"). One key rather than a bare space so a language that puts
        // the material after the part can reorder it.
        add("tooltip.forgeweave.part_name", "%s %s");

        // The 1.12 tooltip-parity batch (issue #379), each key naming its upstream original.
        // tooltip.tool.holdShift -- closes the compact tier of tools and parts alike. The existing
        // tooltip.forgeweave.hold_shift is the smeltery's unrelated "for buckets" hint, hence the
        // separate key.
        add("tooltip.forgeweave.hold_shift_stats", "Hold Shift for Stats");
        // tooltip.pattern.cost, quoted in ingots off PartBuilderRecipes' own cost constants.
        add("tooltip.forgeweave.pattern_cost", "Material Cost: %s");
        // tooltip.part.missing_material / tooltip.part.missing_info -- a part whose material
        // component names nothing this world defines, and one carrying no material at all.
        add("tooltip.forgeweave.part.missing_material", "Missing material: %s");
        add("tooltip.forgeweave.part.missing_info", "Part has no data");
        // stat.head.name / stat.handle.name / stat.extra.name -- the underlined heading over a
        // part's Shift-tier stat block, keyed by PartItem.Kind, and (issue #376) over the same stat
        // block in the Part Builder's info panel.
        add("tooltip.forgeweave.stat_type.head", "Head");
        add("tooltip.forgeweave.stat_type.handle", "Handle");
        // "Binding", not upstream's "Extra": the part is PART_TOOL_BINDING and its own stat line
        // right above reads "Binding Durability", so the heading was the odd one out (CLAUDE.md's
        // Forgeweave-vocabulary rule). Issue #379 shipped the literal upstream word.
        add("tooltip.forgeweave.stat_type.extra", "Binding");
        // #392: stat.bow.name / stat.bowstring.name, the headings over the two ranged stat blocks.
        add("tooltip.forgeweave.stat_type.bow", "Bow");
        add("tooltip.forgeweave.stat_type.bowstring", "Bowstring");
        // tooltip.tank.amount -- the fluid a broken seared tank/gauge/window kept on its stack. The
        // fluid's own name is its registered display name, so it needs no key of its own.
        add("tooltip.forgeweave.tank.amount", "%s mb");
        // item.tconstruct.book.tooltip, the guide book's grey flavour line.
        add("tooltip.forgeweave.guide_book", "The book every smith needs");

        // M1 tool innate retrofit (issue #164, maintainer directive 2026-08-12): pickaxe, shovel and
        // hatchet each carry a fixed combat innate, shown the same name/description shape as a trait
        // but keyed by tool type instead of material (ForgeweaveInnates#innateId).
        add("tooltip.forgeweave.innate.pierce.name", "Pierce");
        add("tooltip.forgeweave.innate.pierce.description", "Deals a small amount of armor-ignoring damage on every hit.");
        add("tooltip.forgeweave.innate.flatten.name", "Flatten");
        add("tooltip.forgeweave.innate.flatten.description", "Hits briefly slow the target.");
        // #158 -- the cleaver's, on the same key family (ForgeweaveInnates#innateId).
        add("tooltip.forgeweave.innate.beheading.name", "Beheading");
        add("tooltip.forgeweave.innate.beheading.description",
                "A killing blow may drop the victim's head. Adds to the Beheading modifier.");
        // #303 -- the warmace's, same key family. Its smash is vanilla's own mace (WarmaceItem), so
        // this is the tooltip half only; the numbers themselves are vanilla's, not restated here.
        add("tooltip.forgeweave.innate.smash.name", "Smash");
        add("tooltip.forgeweave.innate.smash.description",
                "Striking while falling deals more damage the further you fell, and the landing leaves you unhurt.");
        add("tooltip.forgeweave.innate.sunder.name", "Sunder");
        add("tooltip.forgeweave.innate.sunder.description",
                "Disables an active shield and deals bonus damage against a blocking target.");
        // The two M3 station weapons' innates (issue #159), same key family.
        add("tooltip.forgeweave.innate.sweeping_blow.name", "Sweeping Heavy Blow");
        add("tooltip.forgeweave.innate.sweeping_blow.description",
                "A fully charged hit strikes every enemy in a short arc for half damage, and briefly "
                        + "slows the target it lands on.");
        add("tooltip.forgeweave.innate.lacerate.name", "Lacerate");
        add("tooltip.forgeweave.innate.lacerate.description",
                "Hits open a bleeding wound that keeps dealing damage. Fresh cuts stack on top of it.");

        // The large harvest tools' innates (issue #157, maintainer decision 2026-08-12), same shape.
        add("tooltip.forgeweave.innate.concussion.name", "Concussion");
        add("tooltip.forgeweave.innate.concussion.description", "Hits sometimes leave the target badly slowed.");
        add("tooltip.forgeweave.innate.flat_smack.name", "Flat Smack");
        add("tooltip.forgeweave.innate.flat_smack.description", "Every hit knocks the target further back.");
        add("tooltip.forgeweave.innate.timber.name", "Timber");
        add("tooltip.forgeweave.innate.timber.description", "Deals bonus damage to a target that is still unhurt.");
        // "Sweep", not the issue's "Reap": the kama already shipped an innate under that id
        // (issue #156) and an innate's id is its lang key. Behavior and magnitude are unchanged.
        add("tooltip.forgeweave.innate.sweep.name", "Sweep");
        add("tooltip.forgeweave.innate.sweep.description", "The blow carries to everything around the target.");
        add("tooltip.forgeweave.innate.crushing_blow.name", "Crushing Blow");
        add("tooltip.forgeweave.innate.crushing_blow.description", "Knocks armored targets back harder.");

        // Tool tier names (issue #65), keyed off the vanilla incorrect_for_<tier>_tool block tag each
        // material's incorrect_for_tool points at (ToolTooltip#tierName) -- only the tiers M1's
        // materials actually use; an unmapped tier degrades to a visible untranslated key, same as an
        // unknown trait id (MaterialDisplay).
        // Issue #79: M1's four materials are upstream's STONE (wood) and IRON (stone/flint/bone)
        // harvest levels, so `wooden` is no longer among them.
        // #254: head-part tooltips map the whole vanilla ladder (ToolTooltip#tierLine(TagKey)), so
        // wooden gets a key even though no shipped material starts there yet; worded "Wood" to match
        // vanilla's tier vocabulary rather than the tag path's "wooden".
        add("tooltip.forgeweave.tier.wooden", "Wood");
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

        // #103 -- the seven metal materials (docs/SCOPE.md M2 issue #103).
        add("material.forgeweave.iron", "Iron");
        add("material.forgeweave.copper", "Copper");
        add("material.forgeweave.cobalt", "Cobalt");
        add("material.forgeweave.ardite", "Ardite");
        add("material.forgeweave.manyullyn", "Manyullyn");
        add("material.forgeweave.rose_gold", "Rose Gold");
        add("material.forgeweave.netherite", "Netherite");

        // #234 -- M3.2: steel (FW-native) plus the four tag-gated compat metals. The four have no
        // Forgeweave items of their own, but their material names still surface on parts, tools and
        // the info panel once another mod supplies the c: ingot tag.
        add("material.forgeweave.steel", "Steel");
        add("material.forgeweave.bronze", "Bronze");
        add("material.forgeweave.lead", "Lead");
        add("material.forgeweave.silver", "Silver");
        add("material.forgeweave.electrum", "Electrum");

        // #231 -- the seven vanilla-sourced M3.2 materials. Names are upstream 1.12's
        // material.<id>.name entries verbatim, including endstone's odd "End" ("End Pickaxe Head"
        // is how upstream reads).
        add("material.forgeweave.cactus", "Cactus");
        add("material.forgeweave.obsidian", "Obsidian");
        add("material.forgeweave.prismarine", "Prismarine");
        add("material.forgeweave.endstone", "End");
        add("material.forgeweave.paper", "Paper");
        add("material.forgeweave.sponge", "Sponge");
        add("material.forgeweave.netherrack", "Netherrack");
        // #232 -- the slime-family materials (docs/SCOPE.md M3.2), names ported from upstream 1.12's
        // material.{slime,blueslime,magmaslime,knightslime}.name entries.
        add("material.forgeweave.slime", "Slime");
        add("material.forgeweave.blueslime", "Blue Slime");
        add("material.forgeweave.magmaslime", "Magma Slime");
        add("material.forgeweave.knightslime", "Knightslime");

        // #233 -- pig iron + firewood (docs/SCOPE.md M3.2). "Pig Iron" is upstream 1.12's own
        // material.pigiron.name; "Firewood" its material.firewood.name (NOTICE.md).
        add("material.forgeweave.pig_iron", "Pig Iron");
        add("material.forgeweave.firewood", "Firewood");

        // #235 -- the four by-name modern-branch additions (docs/SCOPE.md M3.2). Names are the 1.20
        // clone's material.tconstruct.<id> entries verbatim (NOTICE.md).
        add("material.forgeweave.amethyst_bronze", "Amethyst Bronze");
        add("material.forgeweave.nahuatl", "Nahuatl");
        add("material.forgeweave.chorus", "Chorus");
        add("material.forgeweave.ancient", "Ancient");

        // #392 -- the two bowstring materials (docs/SCOPE.md M3.5). Names are upstream 1.12's
        // material.string.name / material.vine.name. Neither carries any tool stat block, so they
        // only ever surface on a bow string.
        add("material.forgeweave.string", "String");
        add("material.forgeweave.vine", "Vine");

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
        // #231 flint retrofit: upstream's head-scoped crude2, named like magnetic2/writable2 are.
        add("trait.forgeweave.crude2.name", "Crude II");
        add("trait.forgeweave.crude2.description", "Bonus damage against unarmored targets.");
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

        // Rose gold and netherite (issue #103): maintainer decision recorded on the issue, no upstream
        // 1.12 counterpart for either material or trait, so wording is this PR's own.
        add("trait.forgeweave.quick.name", "Quick");
        add("trait.forgeweave.quick.description", "Greatly increases mining and attack speed.");
        add("trait.forgeweave.reinforced_core.name", "Reinforced Core");
        add("trait.forgeweave.reinforced_core.description", "Adds an extra modifier slot to the tool.");

        // M3.2 stateful/special traits (issue #230). Wording follows upstream 1.12's
        // modifier.<id>.name/.desc entries where the trait is a port; vintage is a Forgeweave
        // adaptation (maintainer decision on the issue), so its wording is this PR's own.
        add("trait.forgeweave.alien.name", "Alien");
        add("trait.forgeweave.alien.description",
                "The stats feel off... as if they're changing! Maybe time will tell?");
        add("trait.forgeweave.shocking.name", "Shocking");
        // Verbatim upstream en_us.lang:697 (issue #415 -- the prior wording had drifted); the italic
        // "Bzzzzzt!" opener is upstream's own §o/§r markup, kept as-is since the tooltip that renders
        // this (StationText#traitLine) processes legacy formatting codes embedded in the string.
        add("trait.forgeweave.shocking.description",
                "§oBzzzzzt!§r\nRunning around, breaking blocks or hitting things charges your tool. "
                        + "Hitting an enemy discharges it, dealing damage and providing a speed boost. "
                        + "Mining a block discharges it, giving a mining speed boost.");
        // Upstream shows both slimey ids under the one "Slimey" name (TraitSlimey#getLocalizedName);
        // two Forgeweave keys, same display, so the ids stay distinct for the info panel.
        add("trait.forgeweave.slimey_green.name", "Slimey");
        add("trait.forgeweave.slimey_green.description", "Eww, gooey! Sometimes spawns a little slime.");
        add("trait.forgeweave.slimey_blue.name", "Slimey");
        add("trait.forgeweave.slimey_blue.description", "Eww, gooey! Sometimes spawns a little slime.");
        add("trait.forgeweave.baconlicious.name", "Baconlicious");
        add("trait.forgeweave.baconlicious.description", "Breaking blocks and hitting things sometimes gives bacon.");
        add("trait.forgeweave.tasty.name", "Tasty");
        add("trait.forgeweave.tasty.description",
                "You'd rather eat your tool than starve: it feeds you when you're hungry, at a durability cost.");
        add("trait.forgeweave.vintage.name", "Vintage");
        add("trait.forgeweave.vintage.description",
                "Adds an extra modifier slot, but slows you down while the tool is held.");

        // M3.2 mining/durability-economy traits (issue #228). Wording follows upstream 1.12's
        // modifier.<id>.name/.desc entries, same as the trait batches above ("Duritae" is upstream's
        // own display name for the duritos id).
        add("trait.forgeweave.duritos.name", "Duritae");
        add("trait.forgeweave.duritos.description", "Your tool lasts longer... most of the time.");
        add("trait.forgeweave.jagged.name", "Jagged");
        add("trait.forgeweave.jagged.description", "Every point of durability lost increases damage.");
        add("trait.forgeweave.aquadynamic.name", "Aquadynamic");
        add("trait.forgeweave.aquadynamic.description",
                "The tool is unhindered by water and loves rainy evenings.");
        add("trait.forgeweave.aridiculous.name", "Aridiculous");
        add("trait.forgeweave.aridiculous.description", "The tool works better in hotter environments.");
        add("trait.forgeweave.crumbling.name", "Crumbling");
        add("trait.forgeweave.crumbling.description", "The tool breaks soft blocks that don't need a tool faster.");
        add("trait.forgeweave.unnatural.name", "Unnatural");
        add("trait.forgeweave.unnatural.description",
                "The tool mines faster the higher its mining level is above the required one.");
        add("trait.forgeweave.dense.name", "Dense");
        add("trait.forgeweave.dense.description", "Your tool lasts longer when it has less durability.");
        add("trait.forgeweave.writable.name", "Writable");
        add("trait.forgeweave.writable.description", "More words. More modifiers. It's only logical!");
        add("trait.forgeweave.writable2.name", "Writable II");
        add("trait.forgeweave.writable2.description", "More words. More modifiers. It's only logical!");
        add("trait.forgeweave.squeaky.name", "Squeaky");
        add("trait.forgeweave.squeaky.description",
                "Your tool is so soft and squeaky it gained Silk Touch, but deals no damage.");
        add("trait.forgeweave.autosmelt.name", "Autosmelt");
        add("trait.forgeweave.autosmelt.description", "Harvested blocks get smelted.");

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

        // #223 -- wind burst. Breeze rod, one per level, up to vanilla's own Wind Burst III cap.
        add("modifier.forgeweave.wind_burst.name", "Wind Burst");
        add("modifier.forgeweave.wind_burst.description",
                "Grants Wind Burst on the warmace. Each breeze rod raises it another level, up to III.");

        // #158 -- beheading. The 10% per level is the clone's own chance curve (combat.Beheading).
        add("modifier.forgeweave.beheading.name", "Beheading");
        add("modifier.forgeweave.beheading.description",
                "A killing blow may drop the victim's head. Every level adds 10%, certain at ten.");

        // #154 -- embossing. One shared pair of keys for every material, because the modifier ids are
        // generated per material and a datapack can add materials this mod has never heard of; the
        // material's own name fills the placeholder, which is upstream's ModExtraTrait#getLocalizedName
        // ("Embossment (Iron)") reproduced through Component.translatable's argument instead of
        // string concatenation.
        add("modifier.forgeweave.embossment.name", "Embossment (%s)");
        add("modifier.forgeweave.embossment.description",
                "Carries the traits of %s, without changing anything else about the tool.");
        add("gui.forgeweave.embossment.already_embossed", "This tool is already embossed. One embossment per tool.");
        add("gui.forgeweave.embossment.no_traits", "%s grants no traits through that part, so there is nothing to emboss.");
        // #271 -- fortification. Same shape as embossing above, and for the same reason: upstream
        // builds one ModFortify per head-stat material, so the per-material name/description are one
        // shared pair with the material's own name filling the placeholder -- upstream's
        // `translate(LOC_Name, "fortify") + " (" + material.getLocalizedName() + ")"` and its
        // modifier.fortify.desc, whose %s is the material too. The bare `.name`/`.description` pair
        // names the family rather than any one material, which is what the JEI entry for the shared
        // `forgeweave:fortification` recipe shows (there is one recipe, not one per material).
        add("modifier.forgeweave.fortification.name", "Fortified");
        add("modifier.forgeweave.fortification.description",
                "A sharpening kit and a flint raise the tool's mining level to the kit material's.");
        add("modifier.forgeweave.fortification.material", "Fortified (%s)");
        add("modifier.forgeweave.fortification.material_description",
                "Mining level increased to the same level as %s.");
        add("gui.forgeweave.fortification.already_fortified",
                "This tool is already fortified with %s. Use a different material to change it.");
        add("gui.forgeweave.fortification.no_tier", "%s has no mining level to fortify with.");
        // M3.5 #396: upstream ModFortify is harvestOnly; a launcher has no mining level to set.
        add("gui.forgeweave.fortification.launcher", "A bow mines nothing; there is no mining level to fortify.");
        add("tooltip.forgeweave.sharpening_kit", "Combine with a flint to raise a tool's mining level to this material's.");
        // Combat modifiers batch 2 (issue #163, docs/SCOPE.md M3): knockback, shulking, webbed.
        // Wording follows upstream 1.12's modifier.<id>.name/.desc entries.
        add("modifier.forgeweave.knockback.name", "Knockback");
        add("modifier.forgeweave.knockback.description", "Hits push targets back further. Every piston adds more.");
        add("modifier.forgeweave.shulking.name", "Shulking");
        add("modifier.forgeweave.shulking.description", "Hits briefly make the target levitate.");
        add("modifier.forgeweave.webbed.name", "Webbed");
        add("modifier.forgeweave.webbed.description", "Hits slow the target. Every level adds another second.");

        // #162 batch: combat modifiers batch 1 (smite, bane of arthropods, fiery, necrotic), wording
        // ported from upstream 1.12's modifier.<id>.name/.desc entries.
        add("modifier.forgeweave.smite.name", "Smite");
        add("modifier.forgeweave.smite.description", "Bonus damage against undead. Every 24 pieces is another level.");
        add("modifier.forgeweave.bane_of_arthropods.name", "Bane of Arthropods");
        add("modifier.forgeweave.bane_of_arthropods.description",
                "Bonus damage against arthropods. Every 24 pieces is another level.");
        add("modifier.forgeweave.fiery.name", "Fiery");
        add("modifier.forgeweave.fiery.description",
                "Sets targets on fire and deals bonus fire damage. Every 25 pieces is another level.");
        add("modifier.forgeweave.necrotic.name", "Necrotic");
        add("modifier.forgeweave.necrotic.description",
                "Heals you for a portion of the damage you deal. Every level adds 10%.");

        // JEI recipe category titles (issue #11); only shown when JEI is installed, since the
        // integration is optional (neoforge.mods.toml).
        add("jei.category.forgeweave.part_crafting", "Part Crafting");
        add("jei.category.forgeweave.tool_assembly", "Tool Assembly");
        // #165: the Tool Forge tier's own category (AssemblyCategory#LARGE_TYPE) -- Tool Station
        // never appears as this one's catalyst, so the title says so up front.
        add("jei.category.forgeweave.large_tool_assembly", "Tool Assembly (Tool Forge only)");
        add("jei.category.forgeweave.tool_repair", "Tool Repair");

        // #109 -- smeltery/casting/modifier JEI categories (docs/SCOPE.md M2 issue #109).
        add("jei.category.forgeweave.melting", "Melting");
        // Shown on the melting category's fluid slot only for ore inputs (SmelteryCore#yieldMultiplier,
        // issue #99) -- ingots, nuggets and blocks always melt 1:1, so they get no note.
        add("jei.category.forgeweave.melting.core_multiplier",
                "Base yield -- your smeltery's core tier multiplies ore inputs (Standard 1.5x, Nether 2x).");
        add("jei.category.forgeweave.melting.temperature", "Temperature: %s");
        add("jei.category.forgeweave.alloying", "Alloying");
        add("jei.category.forgeweave.alloying.ratio_note",
                "Shown as a ratio -- the smeltery alloys as many whole batches as the tank holds.");
        add("jei.category.forgeweave.casting_table", "Casting Table");
        add("jei.category.forgeweave.casting_basin", "Casting Basin");
        add("jei.category.forgeweave.casting.cast_reusable", "Cast is not consumed");
        add("jei.category.forgeweave.casting.cast_consumed", "Cast is consumed");
        add("jei.category.forgeweave.modifier_application", "Modifier Application");
        add("jei.category.forgeweave.modifier_application.level_cap", "Level cap: %s");
        // #165: embossing category (issue #154's mechanic).
        add("jei.category.forgeweave.embossing", "Embossing");
        add("jei.category.forgeweave.embossing.one_per_tool", "One embossment per tool");

        // The nine molten metal fluids (docs/SCOPE.md M2 issue #92) and everything added since.
        // See addFluid: each call names both the fluid and its bucket (#286).
        addFluid(ForgeweaveFluids.IRON, "Molten Iron");
        addFluid(ForgeweaveFluids.COPPER, "Molten Copper");
        addFluid(ForgeweaveFluids.GOLD, "Molten Gold");
        addFluid(ForgeweaveFluids.COBALT, "Molten Cobalt");
        addFluid(ForgeweaveFluids.ARDITE, "Molten Ardite");
        addFluid(ForgeweaveFluids.MANYULLYN, "Molten Manyullyn");
        addFluid(ForgeweaveFluids.ROSE_GOLD, "Molten Rose Gold");
        addFluid(ForgeweaveFluids.NETHERITE_SCRAP, "Molten Netherite Scrap");
        addFluid(ForgeweaveFluids.NETHERITE, "Molten Netherite");
        // #234 -- steel and its carbon alloy partner (M3.2).
        addFluid(ForgeweaveFluids.STEEL, "Molten Steel");
        addFluid(ForgeweaveFluids.CARBON, "Molten Carbon");
        // #231: upstream 1.12's fluid.tconstruct.obsidian.name.
        addFluid(ForgeweaveFluids.OBSIDIAN, "Molten Obsidian");
        // #235 -- amethyst and amethyst bronze (M3.2), the 1.20 clone's fluid.tconstruct.* names.
        addFluid(ForgeweaveFluids.AMETHYST, "Molten Amethyst");
        addFluid(ForgeweaveFluids.AMETHYST_BRONZE, "Molten Amethyst Bronze");
        // #270 -- what the entity-melting set pours. "Molten Emerald" follows the molten-metal naming
        // family; the two blood variants follow "Blood" (upstream's fluid.tconstruct.blood.name) and,
        // for blazing blood, the 1.20 clone's own fluid.tconstruct.blazing_blood name.
        addFluid(ForgeweaveFluids.EMERALD, "Molten Emerald");
        addFluid(ForgeweaveFluids.BLAZING_BLOOD, "Blazing Blood");
        addFluid(ForgeweaveFluids.DEEP_BLOOD, "Deep Blood");

        // #232 -- the knightslime alloy chain's three fluids (docs/SCOPE.md M3.2). Upstream calls
        // its seared stone fluid plainly "Seared Stone"; the molten_ prefix names follow this
        // family's convention instead.
        addFluid(ForgeweaveFluids.SLIME, "Molten Slime");
        addFluid(ForgeweaveFluids.SEARED_STONE, "Molten Seared Stone");
        addFluid(ForgeweaveFluids.KNIGHTSLIME, "Molten Knightslime");

        // #233 -- the pig iron alloy chain's three fluids. "Molten Pig Iron" spaces upstream's
        // fluid.tconstruct.pigiron.name ("Molten Pigiron") the way material.pigiron.name already
        // does; "Blood" follows upstream's fluid.tconstruct.blood.name; molten clay follows the
        // molten-metal naming family.
        addFluid(ForgeweaveFluids.PIG_IRON, "Molten Pig Iron");
        addFluid(ForgeweaveFluids.BLOOD, "Blood");
        addFluid(ForgeweaveFluids.MOLTEN_CLAY, "Molten Clay");

        // #100 -- casting (docs/SCOPE.md M2 issue #100). Names follow upstream 1.12's
        // tile.casting.{table,basin}.name / tile.faucet.name and its cast item names.
        addBlock(ForgeweaveBlocks.CASTING_TABLE, "Casting Table");
        addBlock(ForgeweaveBlocks.CASTING_BASIN, "Casting Basin");
        addBlock(ForgeweaveBlocks.FAUCET, "Faucet");
        addItem(ForgeweaveItems.CAST_INGOT, "Ingot Cast");
        addItem(ForgeweaveItems.CAST_NUGGET, "Nugget Cast");
        // #272 -- upstream's item.tconstruct.cast_custom.{gem,plate,gear}.name.
        addItem(ForgeweaveItems.CAST_GEM, "Gem Cast");
        addItem(ForgeweaveItems.CAST_PLATE, "Plate Cast");
        addItem(ForgeweaveItems.CAST_GEAR, "Gear Cast");
        addItem(ForgeweaveItems.CAST_PICKAXE_HEAD, "Pickaxe Head Cast");
        addItem(ForgeweaveItems.CAST_SHOVEL_HEAD, "Shovel Head Cast");
        addItem(ForgeweaveItems.CAST_AXE_HEAD, "Axe Head Cast");
        addItem(ForgeweaveItems.CAST_TOOL_BINDING, "Tool Binding Cast");
        addItem(ForgeweaveItems.CAST_TOOL_HANDLE, "Tool Handle Cast");

        // #222 -- casts for every M3 part (docs/SCOPE.md M3 issue #151/#159/#160/#161's roster),
        // named "<part name> Cast" the same way the five above are.
        addItem(ForgeweaveItems.CAST_SWORD_BLADE, "Sword Blade Cast");
        addItem(ForgeweaveItems.CAST_WIDE_GUARD, "Wide Guard Cast");
        addItem(ForgeweaveItems.CAST_HAND_GUARD, "Hand Guard Cast");
        addItem(ForgeweaveItems.CAST_CROSS_GUARD, "Cross Guard Cast");
        addItem(ForgeweaveItems.CAST_SIGN_PLATE, "Sign Plate Cast");
        addItem(ForgeweaveItems.CAST_PAN, "Pan Cast");
        addItem(ForgeweaveItems.CAST_KNIFE_BLADE, "Knife Blade Cast");
        addItem(ForgeweaveItems.CAST_LARGE_SWORD_BLADE, "Large Sword Blade Cast");
        addItem(ForgeweaveItems.CAST_TOUGH_TOOL_ROD, "Tough Tool Rod Cast");
        addItem(ForgeweaveItems.CAST_TOUGH_BINDING, "Tough Binding Cast");
        addItem(ForgeweaveItems.CAST_LARGE_PLATE, "Large Plate Cast");
        addItem(ForgeweaveItems.CAST_HAMMER_HEAD, "Hammer Head Cast");
        addItem(ForgeweaveItems.CAST_EXCAVATOR_HEAD, "Excavator Head Cast");
        addItem(ForgeweaveItems.CAST_SCYTHE_HEAD, "Scythe Head Cast");
        addItem(ForgeweaveItems.CAST_KAMA_HEAD, "Kama Head Cast");
        addItem(ForgeweaveItems.CAST_BROAD_AXE_HEAD, "Broad Axe Head Cast");
        addItem(ForgeweaveItems.CAST_VEIN_HAMMER_HEAD, "Vein Hammer Head Cast");
        addItem(ForgeweaveItems.CAST_WAR_MACE_HEAD, "War Mace Head Cast");
        addItem(ForgeweaveItems.CAST_CURVED_BLADE, "Curved Blade Cast");
        addItem(ForgeweaveItems.CAST_KATANA_BLADE, "Katana Blade Cast");
        addItem(ForgeweaveItems.CAST_BOW_LIMB, "Bowlimb Cast");
        addItem(ForgeweaveItems.CAST_SHARPENING_KIT, "Sharpening Kit Cast");

        // #292 -- the single-use clay counterpart of every cast above, named the way upstream's
        // item.tconstruct.clay_cast.name ("%s Clay Cast") names them, off the cast's registry name
        // (every cast above is "<Subject> Cast" for the same subject).
        ForgeweaveItems.CLAY_CASTS.forEach((cast, clay) -> addItem(clay, titleCase(cast.substring("cast_".length())) + " Clay Cast"));

        // #103 -- metal materials (docs/SCOPE.md M2 issue #103): item names for the four metals with
        // no vanilla item forms. Ingot/nugget names follow upstream 1.12's item.materials.*.name
        // entries (NOTICE.md); the raw forms have no upstream counterpart to name after and follow
        // vanilla's own "Raw <Metal>" convention instead.
        addItem(ForgeweaveItems.INGOT_COBALT, "Cobalt Ingot");
        addItem(ForgeweaveItems.NUGGET_COBALT, "Cobalt Nugget");
        addItem(ForgeweaveItems.RAW_COBALT, "Raw Cobalt");
        addItem(ForgeweaveItems.INGOT_ARDITE, "Ardite Ingot");
        addItem(ForgeweaveItems.NUGGET_ARDITE, "Ardite Nugget");
        addItem(ForgeweaveItems.RAW_ARDITE, "Raw Ardite");
        addItem(ForgeweaveItems.INGOT_MANYULLYN, "Manyullyn Ingot");
        addItem(ForgeweaveItems.NUGGET_MANYULLYN, "Manyullyn Nugget");
        addItem(ForgeweaveItems.RAW_MANYULLYN, "Raw Manyullyn");
        addItem(ForgeweaveItems.INGOT_ROSE_GOLD, "Rose Gold Ingot");
        addItem(ForgeweaveItems.NUGGET_ROSE_GOLD, "Rose Gold Nugget");
        addItem(ForgeweaveItems.RAW_ROSE_GOLD, "Raw Rose Gold");
        // #234 -- steel (M3.2): no upstream item rows to port (1.12 steel items came from other
        // mods' ore dict), so the names follow the same "<Metal> Ingot/Nugget" convention.
        addItem(ForgeweaveItems.INGOT_STEEL, "Steel Ingot");
        addItem(ForgeweaveItems.NUGGET_STEEL, "Steel Nugget");
        // #235 -- amethyst bronze (M3.2): the 1.20 clone's own item names.
        addItem(ForgeweaveItems.INGOT_AMETHYST_BRONZE, "Amethyst Bronze Ingot");
        addItem(ForgeweaveItems.NUGGET_AMETHYST_BRONZE, "Amethyst Bronze Nugget");

        // #232 -- slime crystals and knightslime's item forms (docs/SCOPE.md M3.2), names following
        // upstream 1.12's item.tconstruct.materials.{slimecrystal*,knightslime_*}.name entries.
        addItem(ForgeweaveItems.GREEN_SLIME_CRYSTAL, "Green Slime Crystal");
        addItem(ForgeweaveItems.BLUE_SLIME_CRYSTAL, "Blue Slime Crystal");
        addItem(ForgeweaveItems.MAGMA_SLIME_CRYSTAL, "Magma Slime Crystal");
        addItem(ForgeweaveItems.INGOT_KNIGHTSLIME, "Knightslime Ingot");
        addItem(ForgeweaveItems.NUGGET_KNIGHTSLIME, "Knightslime Nugget");

        // #104 -- cobalt + ardite nether ore (docs/SCOPE.md M2 issue #104), names ported from
        // upstream 1.12's tile.tconstruct.ore.{cobalt,ardite}.name entries (NOTICE.md).
        addBlock(ForgeweaveBlocks.COBALT_ORE, "Cobalt Ore");
        addBlock(ForgeweaveBlocks.ARDITE_ORE, "Ardite Ore");

        // #206 -- storage blocks for cobalt/ardite/manyullyn, names ported from upstream 1.12's
        // tile.tconstruct.metal.{cobalt,ardite,manyullyn}.name entries (NOTICE.md). Rose gold has no
        // upstream row; "Block of Rose Gold" follows the same vanilla "Block of <Metal>" convention.
        addBlock(ForgeweaveBlocks.COBALT_BLOCK, "Block of Cobalt");
        addBlock(ForgeweaveBlocks.ARDITE_BLOCK, "Block of Ardite");
        addBlock(ForgeweaveBlocks.MANYULLYN_BLOCK, "Block of Manyullyn");
        addBlock(ForgeweaveBlocks.ROSE_GOLD_BLOCK, "Block of Rose Gold");
        addBlock(ForgeweaveBlocks.STEEL_BLOCK, "Block of Steel");
        addBlock(ForgeweaveBlocks.KNIGHTSLIME_BLOCK, "Block of Knightslime"); // #232

        // #233 -- pig iron items + firewood. Ingot/nugget/block names space upstream 1.12's
        // item.tconstruct.{ingots,nuggets}.pigiron.name / tile.tconstruct.metal.pigiron.name the way
        // material.pigiron.name ("Pig Iron") already does; "Firewood" is upstream's
        // tile.tconstruct.firewood.firewood.name verbatim (NOTICE.md).
        addItem(ForgeweaveItems.INGOT_PIG_IRON, "Pig Iron Ingot");
        addItem(ForgeweaveItems.NUGGET_PIG_IRON, "Pig Iron Nugget");
        addBlock(ForgeweaveBlocks.PIG_IRON_BLOCK, "Block of Pig Iron");
        addBlock(ForgeweaveBlocks.FIREWOOD, "Firewood");

        addBlock(ForgeweaveBlocks.AMETHYST_BRONZE_BLOCK, "Block of Amethyst Bronze");

        // #110 -- the M2 advancement chain (docs/SCOPE.md M2 issue #110): build smeltery -> first
        // melt -> first cast -> first alloy -> first modifier. Keys follow vanilla's own
        // advancements.<namespace>.<path>.title/.description convention (see
        // ForgeweaveAdvancementProvider) rather than one of this file's usual families, none of which
        // cover advancements.
        add("advancements.forgeweave.smeltery_root.title", "Playing with Fire");
        add("advancements.forgeweave.smeltery_root.description", "Craft a seared brick");
        add("advancements.forgeweave.build_smeltery.title", "Under Construction");
        add("advancements.forgeweave.build_smeltery.description", "Form a working smeltery structure");
        add("advancements.forgeweave.first_melt.title", "Liquid Assets");
        add("advancements.forgeweave.first_melt.description", "Melt an ore in the smeltery");
        add("advancements.forgeweave.first_cast.title", "Cast in Metal");
        add("advancements.forgeweave.first_cast.description", "Collect a finished casting table result");
        add("advancements.forgeweave.first_alloy.title", "Mixed Metallurgy");
        add("advancements.forgeweave.first_alloy.description", "Alloy two molten metals together");
        add("advancements.forgeweave.first_modifier.title", "Fine Tuning");
        add("advancements.forgeweave.first_modifier.description", "Apply a modifier at the Tool Station");

        // #166 -- the M3-17 chain's tail (docs/SCOPE.md M3 issue #166): forge -> large tool -> emboss
        // -> combat modifier, hung off "first modifier" (see ForgeweaveAdvancementProvider).
        add("advancements.forgeweave.forge.title", "Bigger Hammer");
        add("advancements.forgeweave.forge.description", "Build a Tool Forge");
        add("advancements.forgeweave.large_tool.title", "Go Big");
        add("advancements.forgeweave.large_tool.description", "Assemble a large tool at the Tool Forge");
        add("advancements.forgeweave.emboss.title", "Best of Both");
        add("advancements.forgeweave.emboss.description", "Emboss a tool with a donor part's traits");
        add("advancements.forgeweave.combat_modifier.title", "Sharpened Edge");
        add("advancements.forgeweave.combat_modifier.description", "Apply a combat modifier to a tool");

        // The Ponder soft dependency's one-time chat hint (issue #110): shown on a player's first
        // smeltery controller interaction only when Ponder isn't installed (ForgeweavePonderHint).
        // A new "chat" family, following vanilla's own chat.* namespace -- none of this file's usual
        // families cover a directly-displayed player chat message.
        add("chat.forgeweave.ponder_hint", "Install Ponder for in-game build tutorials -- recipes are in JEI");

        // #229 -- the M3.2 combat-seam trait batch. Wording follows upstream 1.12's
        // modifier.<id>.name/.desc entries (NOTICE.md), same as the M1/M2 trait families above;
        // lacerating has no upstream row (nahuatl is a 1.20-branch material) and reuses the
        // scimitar innate's wording.
        add("trait.forgeweave.prickly.name", "Prickly");
        add("trait.forgeweave.prickly.description", "Nobody is safe from those thorns, they always hurt.");
        add("trait.forgeweave.spiky.name", "Spiky");
        add("trait.forgeweave.spiky.description", "Blocking and getting hurt deals damage to the attacker.");
        add("trait.forgeweave.hellish.name", "Hellish");
        add("trait.forgeweave.hellish.description", "Deal bonus damage to non-Nether mobs.");
        add("trait.forgeweave.superheat.name", "Superheat");
        add("trait.forgeweave.superheat.description", "Deal bonus damage to enemies on fire.");
        add("trait.forgeweave.holy.name", "Holy");
        add("trait.forgeweave.holy.description", "Deal bonus damage to undead enemies.");
        add("trait.forgeweave.poisonous.name", "Poisonous");
        add("trait.forgeweave.poisonous.description", "Poisons enemies on hit.");
        add("trait.forgeweave.heavy.name", "Heavy");
        add("trait.forgeweave.heavy.description", "Prevents knockback.");
        add("trait.forgeweave.stiff.name", "Stiff");
        add("trait.forgeweave.stiff.description", "Blocking reduces the damage taken even more.");
        add("trait.forgeweave.sharp.name", "Sharp");
        add("trait.forgeweave.sharp.description", "Hitting an enemy leaves them bleeding for a short time.");
        add("trait.forgeweave.splintering.name", "Splintering");
        add("trait.forgeweave.splintering.description", "Hit them more to deal more damage.");
        add("trait.forgeweave.flammable.name", "Flammable");
        add("trait.forgeweave.flammable.description",
                "Blocking blocks fire damage and getting hit sets the attacker on fire.");
        add("trait.forgeweave.enderference.name", "Enderference");
        add("trait.forgeweave.enderference.description", "Prevents Endermen from teleporting around for a short time.");
        add("trait.forgeweave.lacerating.name", "Lacerating");
        add("trait.forgeweave.lacerating.description", "Hits open a bleeding wound that stacks and ticks over time.");
        // The two visible status effects those traits apply (splinter and the enderference mark are
        // markers, but they still show in the HUD, so they get names too).
        add("effect.forgeweave.bleed", "Bleeding");
        add("effect.forgeweave.splinter", "Splintered");
        add("effect.forgeweave.enderference", "Enderference");

        // The guide book (issue #273). The item name, book title/subtitle and the static pages'
        // text are ported (rewritten in Forgeweave vocabulary) from upstream 1.12's book data tree
        // `resources/assets/tconstruct/book/en_us/` -- one NOTICE.md row covers the ported set.
        // The `book.forgeweave.*` family is the book's own; registry-driven pages (tools,
        // materials, modifiers) reuse the item/material/modifier/trait families instead of
        // duplicating them here.
        addItem(ForgeweaveItems.GUIDE_BOOK, "Materials and You");
        add("item.forgeweave.cleaver.description",
                "A massive blade for hewing through foes. Slow to swing, but a killing blow is far more likely to claim the victim's head.");
        add("book.forgeweave.title", "Materials and You");
        add("book.forgeweave.subtitle", "Surviving the first day and beyond");
        add("book.forgeweave.index.title", "Index");
        add("book.forgeweave.section.intro", "Introduction");
        add("book.forgeweave.section.tools", "Tools");
        add("book.forgeweave.section.materials", "Materials");
        add("book.forgeweave.section.modifiers", "Modifiers");
        add("book.forgeweave.section.smeltery", "Smeltery");
        add("book.forgeweave.intro.welcome.title", "Surviving the First Day");
        add("book.forgeweave.intro.welcome.text",
                "Welcome to Materials and You: surviving the first day and beyond. Within these pages you will find the first steps to making tools from the materials you gather.\n\nThe first step is to craft a blank pattern. It is a blank slate to stamp a shape into, providing a reference for future creations.\n\nThis book grows with the workshop; check back occasionally for new chapters.");
        add("book.forgeweave.intro.workshop.title", "The Tool Workshop");
        add("book.forgeweave.intro.workshop.text",
                "Shape a non-metal material in the Part Builder with a pattern, then combine the finished parts at the Tool Station. Patterns keep in the Pattern Chest and spare parts in the Part Chest. A crafting table converts into a Crafting Station that holds an unfinished project while you step away.\n\nTogether these make the Tool Workshop; they work best side by side. The Tool Forge, the station's sturdier sibling, is needed for the largest tools.");
        add("book.forgeweave.tools.repairing.title", "Repairing");
        add("book.forgeweave.tools.repairing.text",
                "As you use your tools they take damage, and once all of their durability is gone they break. To fix that, repair your tool -- no need to wait until it breaks.\n\nPut the tool into a Tool Station or Tool Forge and add material matching the tool's head. If the head is made of several materials, any of them will do, and repairing with several at once grants bonus durability.");
        add("book.forgeweave.materials.intro.title", "Materials");
        add("book.forgeweave.materials.intro.text",
                "Every part of a tool contributes the stats of the material it is made from: the head brings durability, mining speed and attack; the handle multiplies durability; a binding adds a flat bonus.\n\nMaterials also grant traits -- special behaviours listed on the pages that follow.");
        add("book.forgeweave.modifiers.intro.title", "Modifiers");
        add("book.forgeweave.modifiers.intro.text",
                "A finished tool is never truly finished. At the Tool Station or Tool Forge, sacrifice items to imbue a tool with modifiers. Each tool starts with a limited number of free slots, and some modifiers can be applied repeatedly for a stronger effect.\n\nThe pages that follow list every modifier known to this workshop.");
        add("book.forgeweave.smeltery.intro.title", "The Smeltery");
        add("book.forgeweave.smeltery.intro.text",
                "There are many smelteries, but this one is yours -- and it melts things really well.");
        add("book.forgeweave.smeltery.structure.title", "Building the Smeltery");
        add("book.forgeweave.smeltery.structure.text",
                "The smeltery's interior can be any size up to 9x9, with walls as short as a single block or as tall as you like. A larger structure holds more molten metal at once. The structure needs a complete seared floor, but no ceiling.\n\nThe walls are built from seared bricks, seared glass, tanks and drains, with the core set into a wall; the floor must be solely seared blocks. At least one seared tank is required to hold fuel -- lava works nicely -- and faucets attach to drains to pour fluids out.");
        add("book.forgeweave.smeltery.working.title", "Working the Smeltery");
        add("book.forgeweave.smeltery.working.text",
                "Place ore or metal into the smeltery through its core and it slowly melts down. Different molten metals pool together below -- some combinations mix into alloys.\n\nDrain the result through a faucet into a Casting Table holding a cast to shape tool parts and ingots, or into a Casting Basin for full blocks.");
    }

    /**
     * One molten fluid's two player-facing names. {@code FluidType}'s default description id is
     * {@code fluid_type.<namespace>.<path>} (no addFluidType helper on {@link LanguageProvider}),
     * shown wherever a fluid stack's name is displayed (tanks, JEI); its bucket (#286) is
     * "&lt;fluid&gt; Bucket", the same shape as the 1.20 clone's own generated
     * {@code item.tconstruct.molten_iron_bucket} = "Molten Iron Bucket".
     *
     * <p>Both come off one call so a fluid cannot get a name without its bucket getting one.
     */
    private void addFluid(ForgeweaveFluids.MoltenMetal fluid, String name) {
        add("fluid_type." + Forgeweave.MODID + "." + fluid.name(), name);
        addItem(fluid.bucket(), name + " Bucket");
    }

    /** {@code "light_gray"} to {@code "Light Gray"}, for {@link DyeColor#getName()} (issue #275). */
    private static String titleCase(String snakeCase) {
        StringBuilder result = new StringBuilder();
        for (String word : snakeCase.split("_")) {
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return result.substring(0, result.length() - 1);
    }
}
