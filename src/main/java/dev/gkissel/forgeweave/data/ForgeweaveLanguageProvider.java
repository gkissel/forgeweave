package dev.gkissel.forgeweave.data;

import net.minecraft.data.PackOutput;

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

        // The smeltery multiblock (docs/SCOPE.md M2 issue #95). Tank/drain names follow upstream
        // 1.12's tile.tconstruct.*.name entries; the two core tiers are SCOPE.md's own vocabulary.
        addBlock(ForgeweaveBlocks.STANDARD_CORE, "Standard Core");
        addBlock(ForgeweaveBlocks.NETHER_CORE, "Nether Core");
        addBlock(ForgeweaveBlocks.SEARED_TANK, "Seared Tank");
        addBlock(ForgeweaveBlocks.SEARED_GAUGE, "Seared Gauge");
        addBlock(ForgeweaveBlocks.SEARED_WINDOW, "Seared Window");
        addBlock(ForgeweaveBlocks.SEARED_DRAIN, "Seared Drain");

        // Plain seared glass (docs/SCOPE.md M3.3 issue #289), name from upstream's tile.tconstruct.seared_glass.name.
        addBlock(ForgeweaveBlocks.SEARED_GLASS, "Seared Glass");

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

        // #101: the smeltery GUI's tank and fuel tooltips, following upstream 1.12's gui.smeltery.*
        // entries word for word -- the unit abbreviations are deliberately lowercase and terse
        // because they trail a number in a dense tooltip ("3 Ingots", "144 mb").
        add("gui.forgeweave.smeltery.capacity", "Capacity:");
        add("gui.forgeweave.smeltery.capacity_available", "Free:");
        add("gui.forgeweave.smeltery.capacity_used", "Used:");
        add("gui.forgeweave.smeltery.liquid.block", "Blocks");
        add("gui.forgeweave.smeltery.liquid.ingot", "Ingots");
        add("gui.forgeweave.smeltery.liquid.nugget", "Nuggets");
        add("gui.forgeweave.smeltery.liquid.kilobucket", "kb");
        add("gui.forgeweave.smeltery.liquid.bucket", "b");
        add("gui.forgeweave.smeltery.liquid.millibucket", "mb");
        add("gui.forgeweave.smeltery.fuel", "Fuel");
        add("gui.forgeweave.smeltery.fuel.empty", "No fuel found");
        // Upstream's gui.smeltery.fuel.heat, shown while a burn is under way (#131).
        add("gui.forgeweave.smeltery.fuel.heat", "Temperature: %s");
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

        // Why an attempted modifier application was refused (issue #105), shown in the Tool Station's
        // tool info panel where upstream 1.12 shows its TinkerGuiException text.
        add("gui.forgeweave.modifier.no_slots", "This tool has no modifier slots left (%s to start with).");
        add("gui.forgeweave.modifier.max_level", "%s is already at its maximum level on this tool.");
        add("gui.forgeweave.modifier.invalid_reagent", "Apply one modifier at a time -- the other slot holds something else.");
        add("gui.forgeweave.modifier.not_enough_reagents", "Not enough of that reagent: %s are needed per step.");
        // Issue #259 (multi-unit reagents): a whole reagent worth more units than the cap has room
        // for -- e.g. a 9-unit redstone block against 5 remaining units of haste.
        add("gui.forgeweave.modifier.reagent_overshoot", "That reagent is worth more than %s has room for on this tool.");
        // Issue #223 (wind burst): the tool the loaded modifier's own vanilla enchantment doesn't
        // support -- e.g. a breeze rod on anything but the warmace.
        add("gui.forgeweave.modifier.unsupported_tool", "%s cannot be applied to this tool.");

        // Why an attempted part exchange was refused (issue #264), same info-panel surface. The
        // durability line mirrors upstream 1.12's gui.error.not_enough_durability.
        add("gui.forgeweave.exchange.wrong_part", "This tool has no slot for that part.");
        add("gui.forgeweave.exchange.same_material", "The tool already has a part of that material there.");
        add("gui.forgeweave.exchange.not_enough_durability",
                "Not enough durability to replace parts! %s more durability required.");
        add("gui.forgeweave.exchange.needs_forge",
                "This tool is too large to work on here. Exchange its parts at a Tool Forge.");

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
        add("trait.forgeweave.fireproof.name", "Fireproof");
        add("trait.forgeweave.fireproof.description", "Survives fire and lava like a vanilla netherite item.");
        add("trait.forgeweave.reinforced_core.name", "Reinforced Core");
        add("trait.forgeweave.reinforced_core.description", "Adds an extra modifier slot to the tool.");

        // M3.2 stateful/special traits (issue #230). Wording follows upstream 1.12's
        // modifier.<id>.name/.desc entries where the trait is a port; vintage is a Forgeweave
        // adaptation (maintainer decision on the issue), so its wording is this PR's own.
        add("trait.forgeweave.alien.name", "Alien");
        add("trait.forgeweave.alien.description",
                "The stats feel off... as if they're changing! Maybe time will tell?");
        add("trait.forgeweave.shocking.name", "Shocking");
        add("trait.forgeweave.shocking.description",
                "Running around, breaking blocks or hitting things charges the tool; "
                        + "a fully charged hit discharges it as lightning.");
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
}
