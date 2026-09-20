# Trait pairing: one side that hits, one side that holds

Issue #1093. Every material carries a trait that does something on a tool or weapon **and** a trait
that does something on armor, and the two belong together: the same idea seen from the attacking
side and from the defending side.

The input is `build/trait-audit/materials.md`, written by `TraitReachabilityTest` (issue #1092): per
material, which sides it builds and which of its traits reach which side. Run
`./gradlew test --tests '*TraitReachabilityTest'` to regenerate it.

## The rule

For every material, for each side it can build:

- **Tool side** (head, handle, extra, bow or an ammo stat): at least one carried trait with a hook
  that fires on a held tool or weapon.
- **Armor side** (plating or maille): at least one carried trait with a hook that fires on a worn
  piece.

`TraitReachabilityTest#everySideAMaterialBuildsHasATraitThatWorksThere` fails the build on a
material that breaks it.

## Where the companions come from

Two things happened before any material was touched.

1. **`knockbackResistance` grew a worn half.** `ForgeweaveTraits#armorAttributes` now pays a worn
   piece a quarter of the trait's held value, so a full four-piece set reaches the held figure and
   no further. That is the whole pair for nine materials whose one idea is weight: their held trait
   plants the wielder and the same trait now steadies the wearer. #1091 had rejected paying the flat
   value per piece, because four pieces of the empowered emeradic crystal would have summed past
   total immunity; a quarter each avoids that.
2. **`forgeweave:protection` became a datapack behavior.** `Protection` is the class the six
   hardcoded `*_protection` traits ride. A `trait_definition` can now name a damage-type tag and an
   amount, which is what the `magic_protection` and `tideward` companions below use.

Everything else reuses a behavior that already shipped.

## The pairs, by batch

### Batch 0: the two shared mechanics

| Material | Builds | Tool side | Armor side | What ties them | New or existing | Source |
| --- | --- | --- | --- | --- | --- | --- |
| `amethyst` | tools and armor | `prismward` | `prismward` | one trait, both sides: a crystal ward softens a shove whichever way you carry it | existing, worn half new | own |
| `compressed_iron` | tools and armor | `compressed_iron_heft` | `compressed_iron_heft` | same | existing, worn half new | own |
| `emeradic_crystal` | tools and armor | `verdant_ward` | `verdant_ward` | same | existing, worn half new | own |
| `empowered_emeradic_crystal` | tools and armor | `empowered_emeradic_bulwark` | `empowered_emeradic_bulwark` | same | existing, worn half new | own |
| `end_steel` | tools and armor | `crystalline_ward` | `crystalline_ward` | same | existing, worn half new | own |
| `lead` | tools and armor | `poisonous`, `gravitic` | `gravitic` | leaden weight shoves and resists the shove | existing, worn half new | own |
| `osmiridium` | tools and armor | `ballast` | `ballast` | same | existing, worn half new | own |
| `osmium` | tools and armor | `leadfoot`, `heavy` | `heavy` | same | existing, worn half new | own |
| `pink_slime` | tools and armor | `rubberize` | `rubberize` | same | existing, worn half new | own |

The remaining batches are filled in as they land.
