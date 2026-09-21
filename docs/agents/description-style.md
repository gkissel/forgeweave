# Description style

How a trait or modifier description is written. One voice, real numbers, no ticks.

A description is the sentence a player reads on the tool tooltip, in the Tool Station, in JEI and in
the guide book. For most traits it is the only place a number is ever shown, so it has to say what
the thing does and by how much. `DescriptionStyleTest` walks every registered trait and modifier and
fails the build on a description that states no number or says "tick".

## The rules

1. **State the number.** If the effect has a magnitude in code, the description says it, not only an
   `.extra` line or a book bullet. If the effect genuinely has no magnitude (an immunity, a boolean
   opt-in, a tier marker), list the id in `DescriptionStyleTest`'s allowlist with the reason.
2. **Seconds, never ticks.** 100 ticks is "5 seconds". Round to one decimal (`2.5 seconds`) and no
   further.
3. **Numerals, not number words.** `40%`, not "two hits in five". `5 seconds`, not "five seconds".
   `25% more`, not "a quarter more". A player comparing two materials is reading for the figure.
4. **One unit per quantity.** Damage is damage points (`2.5 damage`), never hearts, because that is
   what the tool's own stat line counts in. Chances and multipliers are whole percent. Protection is
   points, vanilla's own Protection unit. Durability is points. Energy is FE.
5. **Worn effects say per piece and the full set.** Work the set total out the way the code
   aggregates, which is not always addition. `protection` and `movement_bonus` add per piece.
   `evasion` and `effect_on_attacker` roll once per piece, so a set compounds to `1 - (1 - p)^4`.
   `knockback_resistance` pays each piece a quarter of its value, so a set reaches the held figure
   and no more. Read the behaviour class before writing the total.
6. **Say the condition and the price.** A bonus that only lands at full health, at night, on a fully
   charged swing or against armour says so. A bonus that costs durability, armour, movement speed or
   a reagent says that too, in the same sentence.
7. **Per level where there are levels.** A leveled family has one sentence and takes its numbers as
   description arguments, so each rung reads its own. A modifier's description says what one level is
   worth; its level cap and its slot cost live in the `effect.N` bullets the book's modify page
   shows, and JEI shows the item that applies it.
8. **Flavour goes on its own line or not at all.** Upstream 1.12's shape is
   `§o<flavour>§r\n<mechanism>`: an italic quip, a line break, then the plain sentence. Never glue an
   interjection onto the front of the mechanical sentence ("Aw man! Protects against explosion
   damage."), because then nothing tells a player which half is the joke. Forgeweave keeps that shape
   on `shocking` alone, which `ShockingLangTest` pins to upstream verbatim; everywhere else the
   mechanical sentence stands by itself.
9. **Sentence case, and no naming a sibling.** Capitalise the first word and proper nouns only. Never
   explain one trait by comparing it to another by name, because a player reading a tooltip cannot
   see the other one.
10. **Prefer an argument over a typed number.** A number typed into the lang string drifts the first
    time the value is tuned. Where the render path takes arguments, pass them instead.
11. **In a string that takes arguments, a literal percent is `%%`.** Vanilla's
    `TranslatableContents#decomposeTemplate` throws on any bare `%` outside a format match and on any
    format letter but `s`, and the catch prints the raw template. `"has a %s% chance"` shows the
    player `%s% chance`. `%s` is the only argument form; never `%d` or `%.1f`. A string with no
    arguments and a bare `10%` takes the same exception path and comes out right only because the raw
    template is the sentence, so it is safe exactly as long as nothing passes it arguments.

## Where the numbers live

| Source | How it quotes a number |
| --- | --- |
| A datapack trait | `description_args` in its own `trait_definition` file, next to the value it quotes |
| A Java trait in a family | the rung's `description_args` in `TraitFamilies.JAVA` |
| A standalone Java trait | typed into the lang string, because its constants are private to `ForgeweaveTraits` |
| A modifier | typed into the lang string; `ModifierApplication.description` takes arguments only for embossment and fortification, which interpolate a material name |

A standalone trait can still take arguments: give it its own id as the family name at level 1 of 1 in
its `trait_definition`. `TraitFamilies.name` adds no numeral at level 1, and `description`
interpolates the args. Every ward trait does this.
