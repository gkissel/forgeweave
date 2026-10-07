# Compatibility material audit, 2026-10-07

The roster contains 224 materials, with condition-gated presets for 29 provider mods.
[The inventory](compat-material-inventory-2026-10-07.tsv) records their providers, head stats,
trait assignments and crafting path. Shared metals remain one material across providers.

## Findings and changes

- Mekanism's seven primary ore chains are iron, gold, copper, osmium, tin, lead and uranium.
  Ores and raw materials receive the smeltery core bonus. Raw blocks carry nine raw units.
  Dusts, dirty dusts, clumps, shards and crystals carry one processed ingot and receive no bonus.
  Existing copper and nether-gold loot-equivalent overrides remain in place.
- Bronze, steel, refined obsidian, refined glowstone, HDPE and fluorite already had materials.
  Infused, Reinforced and Atomic Alloy now complete the crafting-alloy ladder. They are made
  at the Part Builder from Mekanism's own alloy items. Their energized traits provide increasing
  energy capacity; their armor traits provide mobility or knockback resistance.
- Bronze now alloys at 3 copper + 1 tin = 4 bronze. Electrum, Invar and Constantan also gain
  smeltery alloy recipes. Refined obsidian, refined glowstone and the crafting alloys retain
  Mekanism's own production recipes.
- Common-tag melting gaps are filled across the existing castable metal roster, including
  silver, nickel, lead and aluminium. Small and tiny dusts carry 36 and 16 mB respectively.
  Empty optional tags supply no input; they do not invent an item from another mod.
- Immersive Engineering's `aluminum` tags now work alongside the existing `aluminium` spelling.
  Its Hop Graphite ingot and dust use the existing Graphite material, with a separate ingot
  casting output. Shared-material bucket visibility now recognizes the Eternal Ores providers
  already accepted by the material definitions.
- Casting recipes require their own concrete output item, even when the molten material accepts
  several providers. The live server caught a clay Graphite cast naming an absent Big Reactors
  ingot with only Immersive Engineering installed; the same guard now covers every foreign output.
- Constantan and Invar now improve on their alloy ingredients in two head stats. Their plating
  durability increases with the head durability. Electrum retains its established 50 durability /
  12 mining speed specialist tradeoff.

## Neo Vitae replaces the Blood Magic watch

The maintainer's 2026-10-07 instruction supersedes the Blood Magic watch in
[issue #858](https://github.com/gkissel/forgeweave/issues/858). Neo Vitae 1.21.1-1.1.32 supplies
Hellforged Metal. Its dungeon ore and raw demonite melt with the core bonus; its ingot, block,
dust, fragment and gravel do not. The material is hidden when Neo Vitae is absent.

Hellforged heads grant Vitae Siphon: healing for 10% of damage dealt, capped at 2 health per hit.
Armor grants Magic Protection II. The trait uses Forgeweave's existing lifesteal behavior;
it does not consume Neo Vitae's Essentia Vitae or Spiritus. No imaginary Bound Metal or
Sentient Metal ingot ids are registered.

## Evidence and coverage

Item ids and tags were checked against installed JAR data for Mekanism 10.7.19.85, Immersive
Engineering 12.4.2-194, Ender IO 8.2.12-beta, Create 6.0.10, Occultism 1.224.4 and Mystical
Agriculture 8.0.28, plus the downloaded Neo Vitae 1.1.32 JAR. All their published `c:ingots/*`
families map to an existing material, including the aluminum and Hop Graphite aliases.
Draconic Evolution 3.1.4.633's cached JAR also has no uncovered ingot family.

Primary source references:

- [Mekanism resource roster](https://github.com/mekanism/Mekanism/blob/bcd7a8bf594cff9614eb12238fe3776f19da24d9/src/main/java/mekanism/common/resource/PrimaryResource.java)
- [Mekanism item registry](https://github.com/mekanism/Mekanism/blob/bcd7a8bf594cff9614eb12238fe3776f19da24d9/src/main/java/mekanism/common/registries/MekanismItems.java)
- [Neo Vitae item registry](https://github.com/breakinblocks/NeoVitae/blob/fc2353c035c949c0ba4229768406ad7cdcd266d4/src/main/java/com/breakinblocks/neovitae/common/item/NVItems.java)
- [Neo Vitae material stages](https://github.com/breakinblocks/NeoVitae/blob/fc2353c035c949c0ba4229768406ad7cdcd266d4/src/main/java/com/breakinblocks/neovitae/common/material/MaterialRegistry.java)

The trait reachability, trait-definition audit, alloy payoff, armor, localization, finish-asset
and synchronization-budget tests check the entire shipped roster. The inventory is a review
of existing presets, not a claim that every item in every provider mod is a tool material.
Machine parts, fuels, reagents, gases, nuclear pellets and equipment are outside that roster.
Providers without a local JAR retain their existing source-verified presets; their current
upstream inventory was not rechecked in a running instance.
