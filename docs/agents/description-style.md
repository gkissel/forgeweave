# Description style

How a trait or modifier description is written. One voice, real numbers, no ticks.

A description is the sentence a player reads on the tool tooltip, in the Tool Station, in JEI and in
the guide book. It is the only place most players ever learn what a trait does, so it has to say
what the thing does and by how much. `DescriptionStyleTest` enforces rules 1 and 2 on every
registered trait and modifier.

## The rules

1. **State the number.** Every description whose effect has a magnitude in code says that magnitude
   in the description itself, not only in an `.extra` line or a book bullet. If the effect genuinely
   has no magnitude (an immunity, a boolean opt-in, a tier marker), add it to
   `DescriptionStyleTest`'s allowlist with a line saying why.
2. **Seconds, never ticks.** Player-facing text has no such word as "tick". 100 ticks is
   "5 seconds". Round to one decimal (`2.5 seconds`) and never past that.
3. **Numerals, not number words.** `40%`, not "two hits in five"; `5 seconds`, not "five seconds";
   `25% more`, not "a quarter more". Number words read like prose and stop a player comparing two
   materials.
4. **One unit per quantity.** Damage is damage points (`2.5 damage`), never hearts, because that is
   the unit the tool's own stat line uses. Chances and multipliers are whole percent. Protection is
   points, the same unit vanilla's own Protection enchantment counts in. Durability is points.
   Energy is FE.
5. **Worn effects say per piece and the full set.** Compute the set total the way the code
   aggregates, which is not always addition: `protection` and `movement_bonus` add per piece,
   `evasion` and `effect_on_attacker` roll per piece so a set compounds to `1 - (1 - p)^4`, and
   `knockback_resistance` pays each piece a quarter of its value so a set reaches the held figure.
   Check the behaviour class before writing the total.
6. **Say the condition and the price.** A bonus that only lands at full health, at night, on a
   fully charged swing or against armour says so. A bonus that costs durability, armour, movement
   speed or a reagent says that too, in the same sentence.
7. **Per level where there are levels.** A leveled family's one sentence takes its numbers as
   description arguments, so each rung reads its own (see `TraitFamilies`). A modifier says what one
   level is worth; its max level, its slot cost and the item that applies it live in its `effect.N`
   bullets, which the book's modify page shows in full.
8. **Flavour is its own line.** Upstream 1.12's shape is `§o<flavour>§r\n<mechanism>`: an italic
   quip, a line break, then the plain sentence. Never glue an interjection onto the front of the
   mechanical sentence ("Aw man! Protects against explosion damage."), because nothing then tells a
   player which half is the joke. Most descriptions carry no flavour at all, and that is fine.
9. **Sentence case, and no naming a sibling.** Capitalise the first word and proper nouns only.
   Never explain one trait by comparing it to another by name; a player reading a tooltip cannot see
   the other one.
10. **The number comes from the code.** A description argument fed from the constant beats a typed
    number, because the typed one drifts the first time the value is tuned. Java traits declare
    theirs in `TraitFamilies.JAVA`; datapack traits declare theirs in the `trait_definition` file
    next to the value they quote.

## Where the numbers live

| Source | Where its description arguments go |
| --- | --- |
| A Java trait | a `TraitFamilies.JAVA` rung, its args read off the `ForgeweaveTraits` constant |
| A datapack trait | the `trait_definition` file's `description_args`, next to the value |
| A modifier | `ModifierApplication.descriptionArgs`, read off the `ForgeweaveModifiers` constant |

A standalone trait with no family still takes a rung: give it its own id as the family name at
level 1 of 1, and `TraitFamilies.name` adds no numeral while `description` interpolates the args.
