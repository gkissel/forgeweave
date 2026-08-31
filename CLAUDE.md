# Repository guidance

## Agent skills

### Issue tracker

Issues are tracked in GitHub Issues for `gkissel/forgeweave`. See `docs/agents/issue-tracker.md`.

### Triage labels

The repository uses the standard `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix` labels. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repository. See `docs/agents/domain.md`.

## Agent worktree hygiene

Worktrees under `.claude/worktrees/` are ~250 MB–1.4 GB each and pile up fast. **After merging an agent's PR, the coordinating session removes that agent's worktree** (`git worktree remove --force .claude/worktrees/agent-<id> && git worktree prune`). Removal criterion for sweeps: working tree clean AND the branch's PR is merged (check `gh pr list --state merged --head <branch>`). This repo squash-merges, so `git merge-base --is-ancestor` can NEVER prove a branch merged — don't use it. Keep dirty or PR-less worktrees and report them instead.

## Upstream reference code

Real Tinkers' Construct source (MIT) is available for reading while implementing Forgeweave. Local read-only clones:

| Path | Branch | Pinned commit | Use for |
| --- | --- | --- | --- |
| `~/development/minecraft/references/tinkers-1.12` | `1.12` | `c01173c0408352c50a2e8c5017552323ce42f5b4` | **Feature target.** Gameplay behavior, stat math, traits, station flows, and M1 asset derivation all follow this generation. |
| `~/development/minecraft/references/tinkers-1.20` | `1.20.1` | `de26560d26c15edf93e6078520202d1c0518394e` | Modern API idioms only: registries, data components, networking, datagen, GameTests on recent Forge — adapt patterns, not features. |
| `~/development/minecraft/references/mantle-1.12` | `1.12` | `340a386af51a97efaac0e71a3f1ff87fb267efe9` | **The 1.12 book engine.** [Mantle](https://github.com/SlimeKnights/Mantle), MIT, the SlimeKnights library `GuiBook` and its chrome art live in; the guide book's geometry, navigation, and `derived/gui/book/{book,bookfront}.png` follow it (issue #430). NOTICE.md rows per derived file, same rules as the Tinkers' clones. |
| `~/development/minecraft/references/spartan-weaponry-1.12.2` | `1.12.2` | `af87ea162cc043f1ea4236e5da5e723c600001ed` | **Not Tinkers'.** [Spartan Weaponry](https://github.com/ObliviousSpartan/SpartanWeaponry), **Apache-2.0, not MIT.** Sole source of the katana and scimitar blade art (issue #375); `scripts/derive_spartan_blade_art.py` reads it. Nothing else derives from this clone. |
| `~/development/minecraft/references/taiga-1.12` | `master` | `4ba49b23a3683146e0164a55df0a11f4dfc1abf5` | **Inspiration only — GPL-3.0, never derive code or assets.** [TAIGA](https://somegit.dev/TAIGA/TAIGA) ore/alloy/trait roster, M6 sizing reference (see `docs/research/tic2-addon-ecosystem.md`). |
| `~/development/minecraft/references/plustic-1.12` | `1.12` | `107f2b7196961b87b7a4db559ff0c703b68067a9` | **Inspiration only — Apache-2.0, not derivation-eligible under ADR-0003.** [PlusTiC](https://github.com/TeamDman/PlusTiC) conditional per-mod material registration and modded-metal stat presets (M6/M8). |
| `~/development/minecraft/references/moar-tinkers-1.12` | `master` | `afdb44afd1a753b66339ca5c1a893cbda4714105` | **Inspiration only — license unresolved, treat as all-rights-reserved.** [Moar Tinkers](https://github.com/MinecraftModDevelopmentMods/MoarTinkers) ~63 cross-mod material roster and per-mod gating (M6/M8). |
| `~/development/minecraft/references/tinkers-evolution-1.12` | `1.12.2` | `bcdabe00b58b2eed49f074a0bd8c57c3af4d8a8a` | **Inspiration only — MIT text plus a "Good, not Evil" clause (JSON-license pattern), so not plain MIT and not derivation-eligible under ADR-0003.** [Tinkers' Evolution](https://github.com/phantamanta44/tinkers-evolution) 97-material / 30-mod integration roster and trait catalogue (M6/M8). |

Rules (see ADR-0003 and CONTEXT.md):

- Forgeweave is a **new independent implementation** whose feature set stays close to the 1.12.2 generation. The 1.20 branch never sets feature scope.
- Reading upstream code is always fine. Any file **derived** from either Tinkers' branch (code or asset) gets a `NOTICE.md` row — Forgeweave path, upstream path, pinned commit above, MIT — in the same PR.
- Material derived from the Spartan Weaponry clone is **Apache-2.0**, which unlike MIT requires shipping the license text and marking modified files. It gets a `NOTICE.md` row *and* an entry in the modification notice in `licenses/APACHE-2.0-SpartanWeaponry.txt`. Do not widen its use beyond issue #375's blades without a maintainer decision — every new file adds an Apache-2.0 obligation.
- The TAIGA, PlusTiC, Moar Tinkers, and Tinkers' Evolution clones are **inspiration-only**: read the designs, then write our own code, text, and art. Nothing is ever derived from them — no NOTICE.md rows exist for them because no file may need one. Same rule as Construct's Armory (LGPL, never cloned).
- All identifiers, file names, and player-facing text use Forgeweave vocabulary; never "tinker"/"tic" (CONTEXT.md avoided terminology).
- If the clones are missing, re-create them: `git clone --depth 1 --branch <branch> https://github.com/SlimeKnights/TinkersConstruct.git <path>` (or `.../SlimeKnights/Mantle.git` for the Mantle row) and checkout the pinned commit.

## 1.12 parity is the default (maintainer directive)

For any feature that exists in Tinkers' Construct 2 (the 1.12 clone), the default is **1:1 parity**: derive its actual textures (blocks, items, GUIs — never freshly-authored approximations when upstream art exists) and mirror its implementation semantics (layouts, slot positions, behaviors, magnitudes), adapted only as far as modern NeoForge APIs force. The 1.20 clone is a fallback reference for how upstream itself adapted a mechanic to modern Minecraft — not an alternative design source. Deviations require an explicit maintainer decision recorded in the PR; "close enough" substitutions have repeatedly failed playtest review.

## Derived texture organization

All upstream-derived art lives under `assets/forgeweave/textures/derived/` (`derived/item/`, `derived/tools/`, `derived/block/`, `derived/gui/`), one `NOTICE.md` row per file. Freshly-authored/original art lives in the standard `textures/item|block|gui` folders alongside it.

**Issue #796 changed what "M9 executes by replacing/emptying the `derived/` tree" means.** Forgeweave now ships two art sets: **Forged** (new original art by the maintainer's designer, the default) and **Legacy** (the art Forgeweave shipped before #796, mostly 1.12-derived plus a few Spartan Weaponry files, available as a built-in resource pack -- see `ForgeweaveResourcePacks` -- under `src/main/resources/resourcepacks/legacy/assets/forgeweave/...`, mirroring `assets/forgeweave/...`'s own paths so it overrides by path). A file under `assets/forgeweave/textures/derived/...` is no longer a reliable signal that the shipped pixels are upstream-derived: as each Forged sprite lands, it overwrites the file at its normal default path, and the old derived file it replaced moves to the same relative path under the Legacy pack instead (its `NOTICE.md` row moves with it -- see that file's `#796` rows for the pattern). The `derived/` folder name and path stay put for every file regardless of which set currently owns the pixels there, so the folder now means "this path is where a licensing note would go if the currently-shipping pixels needed one" rather than "these pixels are derived." M9 (the original-asset rewrite) is superseded by this per-file, incremental Forged rollout -- see the M9 row in `docs/SCOPE.md`'s milestone table, dated 2026-08-28.

Adding the next Forged sprite: drop the new file at its normal default path, copy the file it replaced into the Legacy pack at the same relative path, and rerun `scripts/generate_pattern_textures.py`, `generate_cast_textures.py`, `generate_clay_cast_textures.py` and `derive_broken_art.py` (each one's `main()` regenerates both sets -- see `scripts/sprite_sets.py`'s module docstring). Commit whatever those scripts write to the Legacy pack tree alongside the default tree's changes, and update the `NOTICE.md` rows (and, for Spartan Weaponry-sourced files, `licenses/APACHE-2.0-SpartanWeaponry.txt`'s modification notice) for any file that just became fully original at its default path -- its row moves to the Legacy pack path, the same way this PR's rows did.

## Localization

Every player-facing string is a `Component.translatable` lang key added to `ForgeweaveLanguageProvider` — never `Component.literal` for real text (numeric/glue literals like `"/"`, `": "`, or a `DecimalFormat` pattern are fine; so is wrapping player-typed input, e.g. a renamed tool). Follow existing key families rather than inventing new ones: `item.forgeweave.*`/`block.forgeweave.*` (registered names, via `addItem`/`addBlock`), `material.forgeweave.*` (datapack material names), `trait.forgeweave.<id>.name`/`.description`, `tooltip.forgeweave.*` (item hover text), `gui.forgeweave.*` (station/screen labels), `jei.category.forgeweave.*`. `LocalizationAuditTest` scans `client/`, `menu/`, `item/`, and `jei/` for stray `Component.literal("...")` calls containing a letter and fails the build on new ones.
