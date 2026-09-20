# ADR-0006: What the addon surface promises, and from when

Status: accepted (2026-09-19)

## Context

Issue #1008 opened Forgeweave to addons in four parts: the `dev.gkissel.forgeweave.api` package and its trait, modifier, combat and upgrade seams (#1065), tools and parts registered from Java (#1066), smeltery walls by block tag (#1067), and the test addon, the guide and this record (#1083). Before that work there was no Java surface to promise anything about, and the datapack shapes were stable only by habit.

`docs/research/addon-surface-audit.md` split the question into three tiers and the maintainer accepted its recommendation on 2026-09-18. The datapack codecs have been written accept-old, write-new from the start (`Material.TRAITS_CODEC`, `ModifierRecipe`'s `reagent`/`reagents` pair), and the stack contract has been fixed since ADR-0004 item 2 with a CI-gating save-compat fixture corpus. The Java package is days old and has no outside user yet, so freezing it now would freeze whatever the first real addon turns out not to want.

## Decision

Three tiers, three different promises.

1. **Datapack JSON shapes are stable from the first release that carries the api package.** The twelve datapack registries — `material`, `trait_definition`, `modifier_definition`, `melting_recipe`, `casting_recipe`, `alloy_recipe`, `smeltery_fuel`, `entity_melting_recipe`, `core_transform_recipe`, `modifier_recipe`, `embossing_recipe` and `worktable_recipe` — never lose a field or change a field's meaning inside a major version. New fields are optional and default to the old behavior. A file written against that release loads unchanged on every later release in the same major version.

2. **The stack serialization contract is stable from the same release.** A modifier on a tool is an id plus a level and nothing else. Materials are one id per part slot. An id whose addon is gone is kept inertly, never dropped, and works again the moment the addon returns. This is ADR-0004 item 2, restated here as a promise rather than an internal rule.

3. **The Java API under `dev.gkissel.forgeweave.api` is provisional for one minor release**, then stable on the same terms as tier 1. Provisional means a signature may change with a note in the release notes and no deprecation cycle. After that release it gets the deprecation cycle in "Announcing a break" below.

**Everything outside `dev.gkissel.forgeweave.api` is internal.** `ForgeweaveTraits`, `ForgeweaveModifiers`, `ToolConstants`, `ToolAssemblyRecipes`, `PartBuilderRecipes`, `ToolArt`, every `client/` class and every `compat/<mod>/` package may change in any release without notice, whatever their visibility. `ToolLeveling.addXp` is the one seam meant for callers that sits outside the package (it is a call into Forgeweave rather than a type an addon implements, and moving it would drag `config`, `item`, `menu` and `tool` in behind it); it carries the tier 3 promise where it stands.

Two things the promise explicitly does not cover, in or out of the package: `ToolConstants`' stat numbers and `ForgeweaveModifiers`' behavior constants are parity values the maintainer rebalances, and a rebalance is not a breaking change.

## Announcing a break

A breaking change to a stable tier needs all four, in the same release:

- the old shape keeps working for one minor release, deprecated rather than removed;
- `@Deprecated(forRemoval = true, since = "<version>")` on the Java side, or the old field still accepted by the codec on the data side;
- a row in the release notes under a "Breaking changes" heading, naming the old shape, the new one and the version the old one stops working in;
- a GitHub issue labelled `breaking-change`, open until the removal ships, so an addon author watching the repository sees it before their users do.

A break that cannot wait a release (a security fix, or a shape that corrupts saves) skips the first two and keeps the last two.

## Consequences

- A pack author can write JSON today and expect it to load for the life of the major version. That is the promise most addons actually need, and it costs nothing new: the codecs were already written that way.
- The first real Java addon gets a release in which to find the wrong shapes before they are frozen. Waiting is the whole point of tier 3; shipping a permanent API against zero outside users is how APIs get bad.
- Anything an addon reaches outside the api package is the addon's own risk, and `ApiSourceIsolationTest` keeps the package honest by failing the build if it imports anything outside Minecraft, NeoForge and itself.
- A new extension point lands in the api package or not at all. Making an existing internal class public is not an answer, because a promise about `ForgeweaveTraits` would be a promise about a 3,900-line class.
