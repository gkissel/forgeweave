# Java trait audit: does each trait do what its name and description say, and can it fire where it is used

Issue #1092, 2026-09-20. Companion to PR #1094, which audited the 52 datapack `trait_definition`
files after three of them sold the armor library's drawback as protection. This one covers the rest:
the 197 hardcoded traits in `trait/ForgeweaveTraits.java`, the 21 named combat innates in
`combat/ForgeweaveInnates.java` (plus its four unnamed per-tool knockback multipliers) and the 44
modifiers in `modifier/ForgeweaveModifiers.java`.

For each entry the audit answers the five questions the issue asks: what the code does, what the
player is told, whether the two agree, whether the hook can fire on the part kinds the material
actually builds, and whether a ported trait still matches upstream. The verdict vocabulary is
#1094's: **ok**, **text wrong**, **cannot fire**, **backwards**, **magnitude off**, **needs a
maintainer decision**.

The mechanical columns are derived, not typed. `TraitReachabilityTest` reflects over `Trait` and
`CombatSeam` to find which hooks each registered trait overrides, reads every shipped material's
JSON for the part kinds it has stats for, and writes `build/trait-audit/traits.md` and
`build/trait-audit/materials.md`. Run `./gradlew test --tests '*TraitReachabilityTest'` to refresh
them. The judgement columns below are a read of the code against the lang strings.

## The defects, first

Thirteen entries were wrong. Three could not fire at all; ten described themselves incorrectly.

### Cannot fire: three ids nothing implemented

| Trait | Material | What shipped | What it does now |
| --- | --- | --- | --- |
| `searing` | `seared_stone`, every part | The material named `forgeweave:searing`, but only the *modifier* of that id existed. `ForgeweaveTraits.lookup` answered null, `of` dropped the id with one warning line, and the station drew `trait.forgeweave.searing.name` raw. | A `Trait` whose `autoSmelt` returns true, the same single-level behaviour the modifier of that name has. |
| `fire_protection` | `seared_stone`, armor | Same, on the armor list. | `defendTrait(Protection.against(FIRE_PROTECTION, 2.5))`, the shape `blast_protection` already had and the `PROTECTION_STRONG_PER_LEVEL` the modifier pays. |
| `necrotic` | `necrotic_bone`, every part | Same, on every part. | `LifestealOnHitSeam(necroticLifestealFraction(1))`, a tenth of the damage dealt. |

All three come from one row batch. The 1.20 clone's `MaterialTraitsDataProvider` gives seared stone
`searing` plus `fire_protection` on armor and necrotic bone `necrotic` plus `restore` on armor; #843
copied all four ids into the two materials' JSON, and only `restore` got a `Trait` to stand on.
Nothing in the build noticed, because a trait id with no implementation has always been a warning
rather than an error (ADR-0002 wants a datapack naming a trait a future version adds to keep
loading). `TraitReachabilityTest#everyMaterialTraitIdResolvesToAnImplementation` is what notices now,
for shipped materials only.

Proved by `RestoredMaterialTraitGameTests`, five GameTests on real gear, each with a control that
carries no trait: the ore is smelted where a plain pickaxe leaves it alone, the wielder heals 0.1 on
a 1.0-damage hit where a plain hatchet heals nothing, a fire blow costs a `fire_protection` piece
less than a plain iron one, a non-fire blow costs it exactly the same as a plain one, and all three
ids resolve.

### Text wrong: ten descriptions that did not match the code

| Trait or innate | What the code does | What the text said | What it says now |
| --- | --- | --- | --- |
| `smolderveil` | `self_repair_when(night, 500 ticks per point)` | "Mends faster than duskmend's own base rate at night." `duskmend` is 400 ticks per point, so it is slower, not faster. | "Mends itself slowly after dark." |
| `shattermail` | `bonus_damage_vs(armored, 1.5)` | "Cracks armor a little harder than armor_breaker's base." `armor_breaker` is 2.0, so it is weaker, not harder. | "Bites a little deeper into armored targets." |
| `ashenbond` | `self_repair_when(sunlit, 700 ticks per point)` | "A slow daylight mend, the mirror of duskmend." Mirrors the condition but not the rate: 700 against 400. | "A slow daylight mend." |
| `crystalline_ward` | `knockbackResistance() = 0.18` | "An end-forged plate turns aside a blow." Reads as damage reduction; the trait only resists the shove, and only while held. | "An end-forged plate holds you steady against a blow's shove." |
| `rubberize` | `knockbackResistance() = 0.08` | "A bouncy slime cushions a blow." Same misread. | "A bouncy slime soaks up a blow's shove." |
| `prismward` | `knockbackResistance() = 0.1` | "A crystalline ward softens incoming force." "Force" is ambiguous between damage and knockback. | "A crystalline ward softens a blow's shove." |
| `landslide` | `maxDurabilityBonus() = 25` | "Packed dense, wears slower." The pool is bigger; each loss still costs the same. | "Packed dense, with a deeper durability pool." |
| `matrixbloom` | `self_repair_when(sunlit, 650)` | "A psionic weave that mends best in daylight." It mends *only* in daylight. | "A psionic weave that mends itself in daylight." |
| `overlord` | `headDurability() = durability * 0.85` | "Reduces durability by 15% and grants overslime in exchange." The overslime half is queen's slime's separate `overslime` grant; the trait itself only takes the 15%. | "Cuts the tool's durability by 15%." |
| `deflect` (innate) | `Deflect`: returns a head-on projectile, *and* shaves 30% off a blocked melee blow with half of that reflected onto the attacker (#302) | "While blocking, a projectile caught head-on is returned to its sender." Only half the innate. | Adds the melee half. |

`vital_thrust`'s description also read "ignoring armour" where every other string in the mod spells
it "armor"; that is a spelling fix, not a claim change.

The three wording fixes in the knockback-resistance family are the same failure #1094 found from the
other side. There the text promised protection and the code gave a drawback; here the text promises
protection and the code gives knockback resistance, which is a real benefit but not the one the
sentence sells. `verdant_ward` ("absorbs a blow's shove"), `ballast` ("too heavy to be knocked far")
and `gravitic` ("resists being knocked back") already named the shove and were left alone.

Text-only fixes carry no GameTest: the claim under test is a lang string, and `runData` plus the
committed `en_us.json` is where it is asserted.

## The hook-to-side map

Question 4 ("can it fire where it is used") is answered per side, following the maintainer's comment
on the issue. A trait grants on two sides: **tool** (head, handle, extra, bow, bowstring, shaft,
fletching and the projectile scope) and **armor** (plating and maille). Whether a trait can reach a
side is decided entirely by which hooks it overrides and where those hooks are driven from, so the
mapping below is the core of every per-material answer. It is encoded as
`TraitReachabilityTest.TRAIT_HOOK_SIDES` and a new hook fails the build until it is added.

| `Trait` hook | Driven from | Tool | Armor |
| --- | --- | --- | --- |
| `headDurability` | `ToolStats`, `ToolAssemblyRecipes#statsOf`, at assembly, head material only | yes | no |
| `inventoryTick` | `ToolItem#inventoryTick` and `ArmorPieceItem#inventoryTick` | yes | yes |
| `repairBonus` | `ToolAssemblyRecipes`' repair loop, which armor pieces share | yes | yes |
| `attackDamageBonus` | `ToolItem#getDefaultAttributeModifiers` | yes | no |
| `bonusDamageAgainst` | `ForgeweaveTraits.COMBAT_SEAM#preHit`, off the weapon | yes | no |
| `miningSpeed` | `ToolItem#getMiningSpeed` | yes | no |
| `afterBlockBreak` | `ToolItem`, once a block is destroyed | yes | no |
| `attackSpeedBonus` | `ToolItem#getDefaultAttributeModifiers` | yes | no |
| `drawSpeedBonus` | `BowItem#drawSpeed` | yes | no |
| `afterHit` | `ToolItem#postHurtEnemy` | yes | no |
| `attackDurabilityBonus` | `ToolItem#postHurtEnemy` | yes | no |
| `magneticLevel` | read inside `ForgeweaveTraits#inventoryTick`, which runs for a worn piece too | yes | yes |
| `killExperience` | `LivingExperienceDropEvent`, main hand | yes | no |
| `blockBreakExperience` | `BlockDropsEvent` | yes | no |
| `bonusSlots` | `ForgeweaveModifiers#freeSlots`, any stack with a modifier list | yes | yes |
| `onCombatHit` | `COMBAT_SEAM#onHit`, off the weapon | yes | no |
| `movementSpeedBonus` | `ToolItem#getDefaultAttributeModifiers` | yes | no |
| `maxDurabilityBonus` | `ModifierApplication#retuneStats`, which handles armor since #721 | yes | yes |
| `energyCapacity` | `EnergyBuffer`, off any stack | yes | yes |
| `durabilityDamage` | `ToolItem#damageKeepingItem`, which `ArmorPieceItem#damageItem` delegates to (#721) | yes | yes |
| `breakSpeed` | `PlayerEvent.BreakSpeed` | yes | no |
| `grantsSilkTouch` | `ToolAssemblyRecipes#assemble` | yes | no |
| `zeroesAttackDamage` | `ToolItem`, `COMBAT_SEAM#preHit` | yes | no |
| `autoSmelt` | `ForgeweaveModifiers#onBlockDrops` | yes | no |
| `knockbackResistance` | `ToolItem#getDefaultAttributeModifiers`, main hand | yes | no |
| `onDefend` | `CombatSeams#armorPass`: the defender's two hands first (#729), then the four worn slots | yes | yes |
| `armorAttributes` | `ArmorPieceItem#getDefaultAttributeModifiers` | no | yes |
| `useOnBlock` | `ToolItem#useOn` | yes | no |
| `dropDestroyChance` | `BlockDropsEvent` | yes | no |
| `healingMultiplier` | `ForgeweaveTraits#onLivingHeal`, worn pieces only | no | yes |
| `visibilityMultiplier` | `ForgeweaveTraits#onLivingVisibility`, worn pieces only | no | yes |
| `stateLines` | `ToolTooltip`, for tools and armor alike | yes | yes |
| `combatSeams` | resolved through the seam's own hooks, below | n/a | n/a |

| `CombatSeam` hook | Driven from | Tool | Armor |
| --- | --- | --- | --- |
| `preHit` | `CombatSeams#weaponPass` | yes | no |
| `onHit` | `LivingDamageEvent.Post`, off the weapon | yes | no |
| `postKill` | `LivingDeathEvent`, off the weapon | yes | no |
| `knockback` | `LivingKnockBackEvent`, off the weapon | yes | no |
| `incomingHit` | `CombatSeams#defensePass`, the defender's two hands only | yes | no |
| `onDefend` | `CombatSeams#armorPass`, hands then worn slots | yes | yes |

Two corrections to what the codebase previously assumed. `onDefend` is not worn-only: `armorPass` walks
held tools first, so a defensive trait on a held sword counts like one on a worn piece (#729's own
comment says so). And `durabilityDamage` is not tool-only: `ArmorPieceItem#damageItem` delegates
straight into `ToolItem#damageKeepingItem`, so knightslime's `overslime` pool really does absorb an
armor piece's durability loss. Both mattered: the first classification the reachability test was
given had `durabilityDamage` as tool-only, and the test promptly failed on knightslime's armor-scoped
`overslime`. The failure was in the map, not in the game.

## Counts

249 trait ids resolve today: 197 Java (194 shipped before this issue plus the three restored) and 52
datapack definitions. `build/trait-audit/traits.md` has the full generated table.

| Reach | Count |
| --- | --- |
| Tool side only | 139 |
| Both sides | 94 |
| Armor side only | 10 |
| No `Trait` hook at all (see "what it cannot decide") | 6 |

| Verdict | Count | Where |
| --- | --- | --- |
| ok | 249 | 185 Java traits, 20 innates and all 44 modifiers |
| text wrong | 10 | fixed here, table above |
| cannot fire | 3 | fixed here, table above |
| backwards | 0 | all four were datapack traits and #1094 fixed them |
| magnitude off | 0 | none |
| needs a maintainer decision | 7 | all seven decided and carried out by #1097; see "Decided" below |

The datapack rows are #1094's 52-row table and are not repeated. 190 shipped materials grant at least
one trait; 165 of them build both tools and armor, 25 build tools only, and none builds armor only.

## Decided

Seven things read oddly but were balance or design calls rather than defects, so #1092 left them
alone and asked. The maintainer read the list on 2026-09-20 and decided all seven; issue #1097
carried the decisions out. Each row below is what shipped, not what was proposed.

1. **`featherfall` keeps its id and its effect; the text changed.** It is `movementSpeedBonus() =
   0.03` with a falling name, and saved tools name the id, so the id stays. The display name is
   **Featherlight** and the description says what the effect is ("a light metal that carries quick:
   3% more movement speed while held"), so nothing suggests fall damage any more.
2. **`overlord` got its overslime half back.** Upstream's `overlord.json` pairs a `stat_copy` (a
   tenth of durability into overslime capacity) with a `stat_boost` (-15% durability); Forgeweave
   shipped only the second half. Both halves land now. It needed no new `Trait` hook: overslime
   capacity is not a summed stat, it is read from the stack by `ForgeweaveTraits#overslimeCapacity`,
   which answers `overlord` there the same way `overslimeArmorPenalty` answers `overslime_friend`.
   `overslime` is still what spends the pool, the same split upstream has, and queen's slime grants
   both so its tools keep the flat 50 and gain the scaled part on top.
3. **The self-repair rates became one ladder.** A conditional mend (day or night) runs at half the
   ticks of an unconditional one on the same tier, because its condition holds about half of each
   day. Per tier: netherite 400 conditional / 800 unconditional, diamond 440, iron 500 / 1000,
   stone 600. Two rates are set by something else: `sunmend` and `duskmend` are a day/night mirror
   pair and share one rate (the lower of their two materials' tiers), and `smolderveil`, whose whole
   stated idea is to beat `duskmend`, takes the fastest rate on the ladder even though ebony
   psimetal sits a tier under duskspar. Nothing mends faster than 400, the quickest rate that
   shipped before. `ecological` keeps upstream's 800 and is outside the ladder: it is a 1.12 port and
   parity holds its magnitude. Each description now states its rate in seconds.
4. **Five armor-only traits moved off `general` lists.** `azure_electrum_swift`,
   `azure_silver_moonstep`, `ferricore_footing`, `gravitite_levity` and `ironwood_footing` are on
   their materials' `armor` lists now, so they no longer land on tool parts where they do nothing.
   The sixth the count included is `battleworn`, which `StatScalesWithWear` makes reflection report
   on both sides -- the blind spot the next section already names -- and it is on an `armor` list
   already.
5. **`projectile_protection`'s knockback resistance is the clone's cross-piece figure.** A worn set
   reaches 0.05, not the 0.2 four iron pieces used to sum to. The trait declares the value through
   `Trait#knockbackResistance()` and #1093's worn share pays a quarter of it per piece. Upstream
   reaches the same number by taking the maximum across worn pieces; the share mechanism was already
   in the repository and lands on the same set total, so no second one was written. The deviation
   note is gone from the javadoc.
6. **`WARDED`'s formula wins over upstream's lang row.** The clone's text says 0.5 per level and its
   own `AdjustDamageModule` computes 1. Forgeweave ports the formula and says 1, and that is the
   decision. Nothing changed in behavior; it is recorded in `docs/SCOPE.md` so the disagreement is
   not re-opened.
7. **The three weak `stacking_resistance` presets moved into their tier's envelope.**
   `dragonsteel_ice_calm` 3.2% to 16% and `deorum_temper` 0.8% to 14% at netherite,
   `arctic_insulation` 1.8% to 12% at diamond. The ladder runs from the hardcoded `bracingplate`'s
   18% at the top down to `naga_ward`'s 8% at iron, and every description states its real maximum.

## What the guard test could not decide mechanically

`TraitReachabilityTest` is built on the trait and material registries, so four things are outside
what it can see. Each is named in the test's own javadoc too.

1. Six traits override no `Trait` hook at all. `breakable`, `endspeed`, `hovering` and
   `splitting` are read by id from `ArrowEntity` and `BowItem` rather than through a hook;
   `overslime_friend` and `vinewarden` are read by id from `ForgeweaveTraits#overslimeArmorPenalty`.
   They are real behaviours on a side the hook surface does not describe (an in-flight projectile, an
   assembly-time stat), so the guard exempts a trait with no hooks rather than failing it. The same
   pattern exists among modifiers: `fins`, `mending_moss`, `beheading`, `glowing`, `blasting` and
   `veinmine` all carry their behaviour outside the `Modifier` interface, keyed by id. Every one was
   checked by hand and every one is wired.
2. A parameterized behaviour class can override hooks on both sides and gate on a field.
   `StatScalesWithWear` overrides `miningSpeed` and `onDefend` and picks between them on its `stat`
   parameter, so reflection reports `stonebound` (mining speed) as reaching the armor side and
   `battleworn` (protection) as reaching the tool side. Neither actually does. It is the only shipped
   class with that shape; a genuinely stranded trait built on it would pass the guard. Widening the
   guard would mean teaching it to read constructor parameters, which is a much larger surface than
   the one class it would cover today.
3. A reachable hook says nothing about a reachable condition. `dusksnare` needs a night sky and a
   badly wounded non-boss mob; `aridiculous` needs a hot biome. No registry knows whether a player
   will ever meet those, so "can it fire" here means "is there a code path", not "will it happen".
4. Script and partner-mod traits are not covered. A KubeJS startup script's trait and a mod's
   `TraitRegistry` registration are not in either registry at test time.

## Per-trait table

Generated columns come from `build/trait-audit/traits.md`; the behaviour clause and the verdict are a
read of the code against the lang string. Grouped by area, in registry order. "Both" in the Reach
column means the trait has at least one hook on each side, not that it is granted on both.

| Trait | What the code does | Text agrees | Reach | Verdict |
| --- | --- | --- | --- | --- |
| `ecological` | heals one durability every 800 ticks carried | yes | both | ok |
| `cheap` | +5% durability per repair | yes | both | ok |
| `cheapskate` | assembled durability x0.8, head only | yes | tool | ok |
| `crude` / `crude2` | +5%/+10% damage against an unarmored target | yes | tool | ok |
| `fractured` | flat +1.5 attack damage | yes | tool | ok |
| `magnetic` / `magnetic2` | pulls item drops for 30 ticks after a break or hit, range by summed level | yes | both | ok |
| `momentum` | mining speed grows by level/80 while breaking, decaying | yes | both | ok |
| `lightweight` | +10% mining, attack and draw speed | yes | tool | ok |
| `stonebound` | mining speed rises with durability lost | yes | tool | ok |
| `petramor` | 10% chance per stone block mined to heal 5 durability | yes | tool | ok |
| `insatiable` | consecutive hits add level/3 damage and level/3 durability cost | yes | both | ok |
| `coldblooded` | +50% damage against a target at full health | yes | tool | ok |
| `established` | more XP from kills and from block breaks | yes | tool | ok |
| `quick` | large flat mining and attack speed bonus | yes | tool | ok |
| `reinforced_core` | +1 modifier slot | yes | both | ok |
| `alien` / `alien2` | distributes a rolled pool of durability, speed and attack over time | yes | both | ok |
| `shocking` | builds a 0-100 charge from moving, mining and hitting; discharges into lightning damage or haste | yes | both | ok |
| `slimey_green` / `slimey_blue` | 0.33% chance on a break or kill to spawn a hostile slime | yes | tool | ok |
| `baconlicious` | 0.5% per break and 5% per kill to drop cooked porkchop | yes | tool | ok |
| `tasty` | eats itself to feed a hungry holder, 5 durability per food | yes | both | ok |
| `vintage` | +1 modifier slot, -10% movement speed while held | yes | both | ok |
| `duritos` | durability loss doubled 10%, free 40%, unchanged 50% | yes | both | ok |
| `jagged` | attack damage rises with durability lost | yes | tool | ok |
| `aquadynamic` | break speed up in water and rain | yes | tool | ok |
| `aridiculous` | break speed and hit damage scale with biome heat, negative when cold | yes | tool | ok |
| `crumbling` | soft blocks break at half the tool's own mining speed extra | yes | tool | ok |
| `unnatural` | +1 break speed per tier above the block's requirement | yes | tool | ok |
| `dense` | growing chance to halve the durability cost as the tool wears | yes | both | ok |
| `writable` / `writable2` | +1 / +2 modifier slots | yes | both | ok |
| `squeaky` | Silk Touch, zero attack damage, a squeak on every hit | yes | tool | ok |
| `autosmelt` | mined blocks drop their furnace result | yes | tool | ok |
| `prickly` | gaussian armor-piercing damage on every hit | yes | tool | ok |
| `spiky` | reflects a share of the held tool's damage onto an attacker | yes | tool | ok |
| `hellish` | +4 against a target that is not fire-immune | yes | tool | ok |
| `superheat` | +35% against a burning target | yes | tool | ok |
| `holy` | +5 against undead plus Weakness I | yes | tool | ok |
| `poisonous` | Poison I for ~5s on hit | yes | tool | ok |
| `heavy` | +1.0 knockback resistance while held, i.e. immunity | yes | tool | ok |
| `stiff` | blocking cuts more damage | yes | tool | ok |
| `sharp` | a non-stacking bleed on hit | yes | tool | ok |
| `splintering` | stacking hit bonus marked on the target | yes | tool | ok |
| `flammable` | ignites an attacker, and blocking absorbs fire for 3 durability | yes | tool | ok |
| `enderference` | a mark that stops the target teleporting | yes | tool | ok |
| `lacerating` | the scimitar's stacking bleed, same seam instance | yes | tool | ok |
| `pristine` | damage scales with remaining durability | yes | tool | ok |
| `vigorous` | damage scales with the wielder's health | yes | tool | ok |
| `predatory` | damage scales with the target's missing health | yes | tool | ok |
| `colossal` | damage scales with the target's max health | yes | tool | ok |
| `kinetic` | damage scales with impact velocity | yes | tool | ok |
| `dominant` | +2 against a target weaker than the wielder | yes | tool | ok |
| `armor_breaker` | +2 against an armored target | yes | tool | ok |
| `opportunist` | +2 against a target with a harmful effect | yes | tool | ok |
| `surging` / `surging2` / `surging3` | extra damage on a fully charged swing, three levels | yes | tool | ok |
| `ruthless` | a bigger crit multiplier | yes | tool | ok |
| `escalating` | consecutive full-charge hits ramp, sharing the katana's component | yes | tool | ok |
| `sunmend` | heals one durability per 440 ticks in direct sunlight (#1097) | yes | both | ok |
| `duskmend` | heals one durability per 440 ticks at night (#1097) | yes | both | ok |
| `cascading` | breaking a gravity block takes the column above it | yes | tool | ok |
| `fertilizing` | right-click fertilizes crops for durability | yes | tool | ok |
| `energized` | an FE buffer spent before durability | yes | both | ok |
| `solar_recharge` | refills the buffer in daylight | yes | both | ok |
| `kinetic_charge` | converts a share of damage dealt into stored energy | yes | tool | ok |
| `blighted` | Wither stacking up to III on repeat hits | yes | tool | ok |
| `enfeebling` | Weakness on hit | yes | tool | ok |
| `shackling` | Slowness on hit | yes | tool | ok |
| `revealing` | Glowing on hit | yes | tool | ok |
| `merciful` | Regeneration on whatever it hits | yes | tool | ok |
| `quickstep` | Speed to the wielder on a full-charge hit | yes | tool | ok |
| `unraveling` I-III | strips a beneficial effect on a full-charge hit, 25/50/75% | yes | tool | ok |
| `grievous` | marks the target so heals land smaller | yes | tool | ok |
| `harrying` | shortens the target's invulnerability | yes | tool | ok |
| `leeching` | heals the wielder for 15% of damage dealt, capped | yes | tool | ok |
| `arcing` | a full-charge hit may arc to nearby enemies | yes | tool | ok |
| `stormcaller` | lightning on hit while the wielder is at full health | yes | tool | ok |
| `breakable` | 50% chance a fired projectile breaks on impact (read by id from `ArrowEntity`) | yes | none | ok |
| `endspeed` | a projectile travels near-instantly (read by id) | yes | none | ok |
| `freezing` | each hit stacks Slowness deeper, up to IV | yes | tool | ok |
| `hovering` | a projectile is slower but barely minds gravity (read by id) | yes | none | ok |
| `splitting` | a fired arrow may split in two (read by id from `BowItem`) | yes | none | ok |
| `projectile_protection` | protection 2 against projectiles, plus 0.05 knockback resistance across a worn set | yes | both | ok (decision 5, settled by #1097) |
| `depth_protection` | protection scales with depth below Y=64, a penalty high up | yes | both | ok |
| `blast_protection` | protection 2.5 against explosions | yes | both | ok |
| `melee_protection` | protection 2 against direct melee | yes | both | ok |
| `fire_protection` | protection 2.5 against fire | yes | both | **cannot fire, fixed** |
| `searing` | mined blocks drop their furnace result | yes | tool | **cannot fire, fixed** |
| `necrotic` | heals the wielder for a tenth of damage dealt | yes | tool | **cannot fire, fixed** |
| `warded` | at full health, one damage comes off after armor | yes | both | ok (decision 6, settled by #1097) |
| `crystalstrike` | +5% attack speed per worn piece, and snaps knockback to compass directions | yes | armor | ok |
| `consecrated` | protection 1.25 against undead attackers | yes | both | ok |
| `overshield` | spends up to two overslime for protection | yes | both | ok |
| `overslime` | a 50-point pool durability loss is paid from first | yes | both | ok |
| `overslime_friend` | waives overslime's armor penalty (read by id) | yes | none | ok |
| `overgrowth` | 5% chance a second to regenerate one overslime | yes | both | ok |
| `overlord` | assembled durability x0.85, head only, plus an overslime pool of a tenth of that (#1097) | yes | tool | **text wrong, fixed** |
| `restore` | 15% chance on being hit to heal a quarter of it for 1 durability | yes | both | ok |
| `recurrent_protection` | half the blow's damage becomes flat reduction for that blow | yes | both | ok |
| `piercing_guard` | a direct attacker gets -1 armor for four seconds, one durability | yes | both | ok |
| `thorns` | 15% chance a direct attacker takes 1-4 damage | yes | both | ok |
| `enderclearance` | 25% chance a direct attacker is teleported nearby | yes | both | ok |
| `skyfall` | -15% gravity and +1 safe fall distance per worn piece | yes | armor | ok |
| `unyielding` | damage scales with remaining durability | yes | tool | ok |
| `radiant_edge` | +3 on a full-charge swing | yes | tool | ok |
| `verdant_ward` | +0.15 knockback resistance while held | yes | tool | ok |
| `luminous` | Glowing on hit | yes | tool | ok |
| `stormglass` | damage scales with impact velocity | yes | tool | ok |
| `bloodgem` | damage scales with the target's max health | yes | tool | ok |
| `voidtouched` | flat +1 attack damage | yes | tool | ok |
| `brittleforce` | +2 against an armored target | yes | tool | ok |
| `obliterate` | a chance a mined block drops nothing | yes | tool | ok |
| `avalanche` | extra knockback on every hit | yes | tool | ok |
| `landslide` | +25 max durability | **no** | both | **text wrong, fixed** |
| `skyborne` | +8% bow draw speed | yes | tool | ok |
| `featherfall` | +3% movement speed while held | yes | tool | ok (decision 1, settled by #1097: shown as "Featherlight") |
| `buoyant` | +8% attack speed | yes | tool | ok |
| `corebound` | +40 max durability | yes | both | ok |
| `ballast` | +0.2 knockback resistance while held | yes | tool | ok |
| `leadfoot` | -3% movement speed while held | yes | tool | ok |
| `obsidian_heart` | +2.5 against a target weaker than the wielder | yes | tool | ok |
| `voidrend` | 25% chance of Weakness on hit | yes | tool | ok |
| `seismic` | strong knockback on hit | yes | tool | ok |
| `stonewake` | +2 against a target at full health | yes | tool | ok |
| `keenedge` | damage scales with remaining durability | yes | tool | ok |
| `wellspring` | mining stone may heal the wielder | yes | tool | ok |
| `tinseeker` | heals one durability per 800 ticks (#1097) | yes | both | ok |
| `steelfast` | +6% attack speed | yes | tool | ok |
| `brasswind` | +6% bow draw speed | yes | tool | ok |
| `amberflow` | 20% chance of Speed on hit | yes | tool | ok |
| `duskbloom` | heals one durability per 600 ticks at night | yes | both | ok |
| `emberwake` | Speed on hitting a burning target | yes | tool | ok |
| `overburdened` | mining may leave the wielder with Mining Fatigue | yes | tool | ok |
| `smolderveil` | heals one durability per 400 ticks at night (#1097) | yes | both | **text wrong, fixed** |
| `ashenbond` | heals one durability per 400 ticks in sunlight (#1097) | yes | both | **text wrong, fixed** |
| `fallout` | slow self-poison, and mining stone may mutate a neighbour to deepslate | yes | both | ok |
| `nocturnal_edge` | +2 at night, -1 by day | yes | tool | ok |
| `prismward` | +0.1 knockback resistance while held | **no** | tool | **text wrong, fixed** |
| `shattermail` | +1.5 against an armored target | **no** | tool | **text wrong, fixed** |
| `chaosmark` | 15% chance of Nausea on hit | yes | tool | ok |
| `shieldbreaker` | drains 4x the wielder's attack damage off a Draconic shield | yes | tool | ok |
| `vinewarden` | waives overslime's armor penalty (read by id) | yes | none | ok |
| `magmaforge` | mining stone may leave lava | yes | tool | ok |
| `voidwoven` | flat +1.5 attack damage | yes | tool | ok |
| `crystalline_ward` | +0.18 knockback resistance while held | **no** | tool | **text wrong, fixed** |
| `quartzheart` | damage scales with the wielder's health | yes | tool | ok |
| `daybound` | Glowing by day, sometimes Night Vision after dark | yes | both | ok |
| `batteredge` | +2.5 on a full-charge swing | yes | tool | ok |
| `sparkforge` | 20% chance of Haste on hit | yes | tool | ok |
| `unstable_core` | while in use, a small chance of a burst that hurts the wielder and anything close | yes | both | ok |
| `warbond` | +2 against a target weaker than the wielder | yes | tool | ok |
| `steadfast` | +60 max durability | yes | both | ok |
| `coilcharge` | knockback on every hit | yes | tool | ok |
| `smokehouse` | heals one durability per 1000 ticks | yes | both | ok |
| `gravitic` | +0.25 knockback resistance while held | yes | tool | ok |
| `elektronbond` | flat +1 attack damage | yes | tool | ok |
| `starforged` | +10% durability per repair | yes | both | ok |
| `rubberize` | +0.08 knockback resistance while held | **no** | tool | **text wrong, fixed** |
| `tidebreaker` | clears the water around a mined block | yes | tool | ok |
| `matrixbloom` | heals one durability per 500 ticks in sunlight (#1097) | yes | both | **text wrong, fixed** |
| `berserker_stance` | bonus damage while sneaking, paid in extra wear | yes | tool | ok |
| `earthmend` | digging dirt-like blocks may heal the wielder | yes | tool | ok |
| `duskgrasp` | Darkness on hit | yes | tool | ok |
| `dusksnare` | a sneaking hit on a badly wounded non-boss mob captures it | yes | tool | ok |
| `leanharvest` | mined blocks sometimes drop nothing, always grant bonus XP | yes | tool | ok |
| `warmemory` | grows bonus damage per entity type fought, capped | yes | both | ok |
| `hollowyield` | mined blocks never drop loot, always grant bonus XP | yes | tool | ok |
| `swiftdig` | faster on blocks that need no tool | yes | tool | ok |
| `quakecrumble` | mining may break mineable neighbours | yes | tool | ok |
| `riftstep` | a hit may teleport the target or the wielder | yes | tool | ok |
| `dreadgrip` | Slowness II, Weakness, and drops a mob's AI target | yes | tool | ok |
| `bloodtally` | permanent per-kill attack growth, capped | yes | both | ok |
| `gamedrop` | kills grant no XP and sometimes drop cooked beef | yes | tool | ok |
| `bloodtoll` | a blow can never be reduced below half a heart | yes | both | ok (the one deliberate `damage_floor`) |
| `hexward` | 25% chance a direct attacker is weakened | yes | both | ok |
| `mendbond` | heals the wearer receives are a quarter larger | yes | armor | ok |
| `emberdrink` | fire heals the wearer for half of what it would have dealt | yes | both | ok |
| `bracingplate` | protection builds per blow to +4.5, lapsing after 5s | yes | both | ok |
| `sapmend` | Regeneration on being hit | yes | both | ok |
| `lastbreath` | a killing blow is spent on the piece, once per 5 minutes, for 100 durability | yes | both | ok |
| `aegispulse` | struck at full health, the invulnerability window doubles to 40 ticks | yes | both | ok |
| `windstep` | one blow in ten misses | yes | both | ok |
| `nightveil` | mobs notice the wearer at half distance in light 7 or below | yes | armor | ok |
| `swiftstride` | +5% movement speed per worn piece | yes | armor | ok |
| `battleworn` | protection rises with the piece's wear | yes | armor | ok |
| `stormrind` | lightning does nothing to the wearer | yes | both | ok |
| `blastvent` | an explosion throws the wearer instead of hurting them | yes | both | ok |
| `evolving`, `evolved`, `evolved2`, `evolved3` | tier markers read by the fusion recipe; each shows its upgrade count in the tooltip | yes | both | ok |

## Per-innate table

Innates belong to a tool kind rather than to a material, so they are tool side by construction. None
reaches armor and none is meant to.

| Innate | Tool | What the code does | Text agrees | Verdict |
| --- | --- | --- | --- | --- |
| `pierce` | pickaxe | 1.0 armor-ignoring damage per hit | yes | ok |
| `flatten` | shovel | Slowness I for 1.5s | yes | ok |
| `sunder` | hatchet | +20% against a blocking target; the shield disable lives on `ToolItem` | yes | ok |
| `parry` | broadsword | a full-charge grounded hit sweeps 3 blocks; right-click negates one melee blow and slows the attacker | yes | ok |
| `charged_leap` | longsword | upstream's charged leap, constant for constant | yes | ok |
| `vital_thrust` | rapier | 5% of the target's current health past armor, plus a right-click lunge | yes (spelling fixed) | ok |
| `deflect` | battlesign | returns a head-on projectile, and shaves 30% off a blocked melee blow with half reflected | **no** | **text wrong, fixed** |
| `heavy_swing` | frying pan | doubled knockback, plus a charged launch that sears at full charge | yes | ok |
| `backstab` | dagger | up to +100% from directly behind, falling to +25% at the cone edge | yes | ok |
| `heft` | mattock | a chance of a strong knockback | yes | ok |
| `reap` | kama | +25% against a target below a quarter health | yes | ok |
| `concussion` | hammer | 20% chance of Slowness II, plus +3 to +6 against undead | yes | ok |
| `flat_smack` | excavator | one Knockback level on every hit | yes | ok |
| `timber` | lumber axe | +15% against a target at full health | yes | ok |
| `sweep` | scythe | the blow carries to everything in a 3x3x3 | yes | ok |
| `crushing_blow` | vein hammer | extra knockback against an armored target | yes | ok |
| `sweeping_blow` | battleaxe | a full-charge hit strikes a 120-degree, 3-block arc for half damage and slows the primary target | yes | ok |
| `lacerate` | scimitar | 1 damage a second for 4s, stacking to 3 | yes | ok |
| `damage_ramp` | katana | +10% per landed hit to +75%, lapsing after 5s | yes | ok |
| `beheading` | cleaver | tooltip-only: its level is summed into the beheading modifier's one roll | yes | ok |
| `smash` | warmace | tooltip-only: vanilla's own mace fall damage and safe landing | yes | ok |

Four per-tool knockback multipliers (hatchet 1.3, mattock 1.1, lumber axe 1.5, rapier 0.6) ride the
same pipeline with no tooltip of their own. That is deliberate and `ForgeweaveInnates` says so: a
knockback multiplier is a plain stat, not a named piece of combat feel.

## Per-modifier table

A modifier declares its own scope (`armorOnly`, `harvestOnly`, `projectileOnly`, `chestplateOnly`,
`helmetOnly`, `utility`), so its reach is read off the interface rather than inferred. All 44 agree
with their lang name, description and effect lines.

| Scope | Modifiers |
| --- | --- |
| Armor only | `knockback_resistance`, `thorns`, `elytra_flight` (chestplate), `creative_flight` (chestplate), `goggles` (helmet, utility), `rayward` |
| Both sides | `fire_protection`, `blast_protection`, `magic_protection`, `melee_protection`, `projectile_protection`, `netherite`, `reinforced`, `soulbound`, `extra_slot`, `mending_moss`, `diamond`, `emerald`, `surgebound` |
| Harvest only | `blasting`, `veinmine` |
| Projectile only | `fins` |
| Tool side | `harvest_width`, `harvest_height`, `haste`, `searing`, `magnetic_pull`, `aquadynamic`, `resonant`, `far_reach`, `silky`, `luck`, `sharpness`, `knockback`, `shulking`, `webbed`, `smite`, `bane_of_arthropods`, `fiery`, `necrotic`, `beheading`, `wind_burst`, `glowing`, `socketed` |

Six carry their behaviour outside the `Modifier` interface, keyed by id: `fins` (`ArrowEntity`'s
water drag), `mending_moss` (its own XP component and heal tick), `beheading` (`Beheading`'s shared
roll), `glowing` (the light-placing tick), `blasting` and `veinmine` (the harvest paths). Each was
checked by hand and each is wired. Two magnitudes worth recording because they read oddly at a
glance and are right: `aquadynamic`'s `submergedMiningSpeedBonus` of 0.8 restores vanilla's
`submerged_mining_speed` from its 0.2 penalty to a flat 1.0, which is what "no penalty underwater"
means; and `necrotic`'s trait is level 1 of the modifier's ten, so 10% against the modifier's 100% at
level 10.

## Per-material table

`build/trait-audit/materials.md` carries all 242 material-and-trait pairs with the side each grant
lands on and the side it can actually work on. It is regenerated by the test rather than kept here,
because it changes whenever a material's JSON does. Issue #1093 reads it to find which materials
carry a trait that only works on one of the two sides they build.

The shape of the answer today:

- 190 shipped materials grant at least one trait.
- 165 build both tools and armor; 25 build tools only; none builds armor only.
- Every grant now lands on a side the trait can work on, or on a trait that carries no hooks at all
  (the six id-membership traits above).
