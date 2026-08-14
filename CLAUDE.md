# Repository guidance

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues for `gkissel/forgeweave`. See `docs/agents/issue-tracker.md`.

### Triage labels

The repository uses the standard `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix` labels. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repository. See `docs/agents/domain.md`.

## Upstream reference code

Real Tinkers' Construct source (MIT) is available for reading while implementing Forgeweave. Local read-only clones:

| Path | Branch | Pinned commit | Use for |
| --- | --- | --- | --- |
| `~/development/minecraft/references/tinkers-1.12` | `1.12` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | **Feature target.** Gameplay behavior, stat math, traits, station flows, and M1 asset derivation all follow this generation. |
| `~/development/minecraft/references/tinkers-1.20` | `1.20.1` | `de26560d26c15edf93e6078520202d1c0518394e` | Modern API idioms only: registries, data components, networking, datagen, GameTests on recent Forge — adapt patterns, not features. |
| `~/development/minecraft/references/spartan-weaponry-1.12.2` | `1.12.2` | `af87ea162cc043f1ea4236e5da5e723c600001ed` | **Not Tinkers'.** [Spartan Weaponry](https://github.com/ObliviousSpartan/SpartanWeaponry), **Apache-2.0, not MIT.** Sole source of the katana and scimitar blade art (issue #375); `scripts/derive_spartan_blade_art.py` reads it. Nothing else derives from this clone. |

Rules (see ADR-0003 and CONTEXT.md):

- Forgeweave is a **new independent implementation** whose feature set stays close to the 1.12.2 generation. The 1.20 branch never sets feature scope.
- Reading upstream code is always fine. Any file **derived** from either Tinkers' branch (code or asset) gets a `NOTICE.md` row — Forgeweave path, upstream path, pinned commit above, MIT — in the same PR.
- Material derived from the Spartan Weaponry clone is **Apache-2.0**, which unlike MIT requires shipping the license text and marking modified files. It gets a `NOTICE.md` row *and* an entry in the modification notice in `licenses/APACHE-2.0-SpartanWeaponry.txt`. Do not widen its use beyond issue #375's blades without a maintainer decision — every new file adds an Apache-2.0 obligation.
- All identifiers, file names, and player-facing text use Forgeweave vocabulary; never "tinker"/"tic" (CONTEXT.md avoided terminology).
- If the clones are missing, re-create them: `git clone --depth 1 --branch <branch> https://github.com/SlimeKnights/TinkersConstruct.git <path>` and checkout the pinned commit.

## 1.12 parity is the default (maintainer directive)

For any feature that exists in Tinkers' Construct 2 (the 1.12 clone), the default is **1:1 parity**: derive its actual textures (blocks, items, GUIs — never freshly-authored approximations when upstream art exists) and mirror its implementation semantics (layouts, slot positions, behaviors, magnitudes), adapted only as far as modern NeoForge APIs force. The 1.20 clone is a fallback reference for how upstream itself adapted a mechanic to modern Minecraft — not an alternative design source. Deviations require an explicit maintainer decision recorded in the PR; "close enough" substitutions have repeatedly failed playtest review.

## Derived texture organization

All upstream-derived art lives under `assets/forgeweave/textures/derived/` (`derived/item/`, `derived/tools/`, `derived/block/`, `derived/gui/`), one `NOTICE.md` row per file. Freshly-authored/original art lives in the standard `textures/item|block|gui` folders alongside it. M9 executes by replacing/emptying the `derived/` tree.

## Localization

Every player-facing string is a `Component.translatable` lang key added to `ForgeweaveLanguageProvider` — never `Component.literal` for real text (numeric/glue literals like `"/"`, `": "`, or a `DecimalFormat` pattern are fine; so is wrapping player-typed input, e.g. a renamed tool). Follow existing key families rather than inventing new ones: `item.forgeweave.*`/`block.forgeweave.*` (registered names, via `addItem`/`addBlock`), `material.forgeweave.*` (datapack material names), `trait.forgeweave.<id>.name`/`.description`, `tooltip.forgeweave.*` (item hover text), `gui.forgeweave.*` (station/screen labels), `jei.category.forgeweave.*`. `LocalizationAuditTest` scans `client/`, `menu/`, `item/`, and `jei/` for stray `Component.literal("...")` calls containing a letter and fails the build on new ones.
