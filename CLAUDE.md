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

Rules (see ADR-0003 and CONTEXT.md):

- Forgeweave is a **new independent implementation** whose feature set stays close to the 1.12.2 generation. The 1.20 branch never sets feature scope.
- Reading upstream code is always fine. Any file **derived** from either branch (code or asset) gets a `NOTICE.md` row — Forgeweave path, upstream path, pinned commit above, MIT — in the same PR.
- All identifiers, file names, and player-facing text use Forgeweave vocabulary; never "tinker"/"tic" (CONTEXT.md avoided terminology).
- If the clones are missing, re-create them: `git clone --depth 1 --branch <branch> https://github.com/SlimeKnights/TinkersConstruct.git <path>` and checkout the pinned commit.

## Derived texture organization

All upstream-derived art lives under `assets/forgeweave/textures/derived/` (`derived/item/`, `derived/tools/`, `derived/block/`, `derived/gui/`), one `NOTICE.md` row per file. Freshly-authored/original art lives in the standard `textures/item|block|gui` folders alongside it. M9 executes by replacing/emptying the `derived/` tree.
