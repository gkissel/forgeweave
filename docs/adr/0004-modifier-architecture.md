# ADR-0004: Modifier architecture and the data-boundary roadmap

Status: accepted (2026-08-09)

## Context

M2 introduces Modifiers. ADR-0002 fixed "behavior = Java, wiring = data" for Traits and warned against widening the code/data boundary speculatively. Maintainer requirement (M2 planning): modpack authors (ATM-style) must eventually be able to create fully custom traits and modifiers without writing Java.

## Decision

1. **M2 modifiers follow the trait precedent**: each modifier's behavior is a registered Java class with 1.12-parity constants (ported from the pinned clone); its *application recipes* (ingredients, counts, level caps) are datapack JSON. Pack authors can retune and disable, not invent.
2. **Serialization is strictly `modifier id + level`** on the tool's data component. No class references, ever — every save and fixture stays valid across item 3's migration.
3. **Commitment**: at M6, trait and modifier *definitions* become datapack-creatable via a parameterized behavior library (~10–15 generic Java behaviors — `potion_effect_on_hit`, `bonus_damage_vs`, `speed_scales_with_durability`, … — composed with parameters in JSON). KubeJS bindings are evaluated at M8.
   - **Delivered at M6 by #832 (traits).** Maintainer decision 2026-09-02, option (a) scoped to traits: the `forgeweave:trait_definition` datapack registry dispatches a `behavior` field over one codec per library class from #827/#828/#829/#830 (`TraitBehaviors`), synced with the codec as network codec and existence-gated by `neoforge:conditions` like `Material`. The ~85 Java-registered trait ids are untouched and win any id collision; serialization stays the plain id list. The same delivery adds the **KubeJS binding** ahead of the M8 evaluation, for traits whose runtime logic JSON parameters cannot express: `ForgeweaveEvents.traits` (startup scripts) hands out a `ScriptTrait` builder whose callbacks mirror the `Trait` hooks. Schema and script example in docs/SCOPE.md's M6 section. **Modifier definitions remain deferred to M8**, where the KubeJS evaluation now starts from a shipped binding rather than a blank page; modifiers keep their datapack *application recipes* (item 1) meanwhile.
4. ADR-0002's consequences section gains an amendment pointing here; its "revisit if demand pushes" clause is now exercised, with M6 as the delivery milestone.

## Consequences

- M2 stays shippable with no framework project; the smeltery is not delayed by modifier infrastructure.
- Until M6, adding a new trait or modifier behavior requires a Forgeweave code change, same as traits since ADR-0002.
- The M6 schema work must treat the M2 behavior classes as the first entries of the parameterized library — M2 implementations should avoid gratuitous coupling that would block that extraction.
