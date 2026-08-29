# ADR-0003: Upstream derivation is allowed, tracked in NOTICE.md

Status: accepted (2026-08-07)

## Context

Parts of Forgeweave may derive from Tinkers' Construct 1.12.2 code and assets available under the MIT License (see README for the attribution position; do not restate it here). CONTRIBUTING.md obligates contributors to identify derived material but names no mechanism. The final roadmap milestone (M9) replaces derived assets with original ones, so the set of derived files must stay auditable throughout.

## Decision

- Deriving from upstream MIT-licensed code and assets is **permitted from milestone 1**.
- A root **`NOTICE.md`** carries the upstream MIT copyright notice once, plus one table row per derived file: Forgeweave path, upstream path, upstream commit hash, license.
- The table is maintained **in PR review** — a PR introducing derived material without matching rows is not mergeable. No tooling or automation.
- Per-file attribution headers are not used; binary assets cannot carry them and a second location would drift from the table.

## Consequences

- M9 (original-asset rewrite) is executable by walking the NOTICE.md table to empty.
- If the table grows unwieldy, that signals over-importing — the response is importing less, not building provenance tooling.

## Amendment (2026-08-28, issue #796)

M9's mechanism changed: rather than deleting derived files outright, each Forged sprite batch moves the file(s) it replaces into the built-in Legacy resource pack (`src/main/resources/resourcepacks/legacy/...`) and updates that file's `NOTICE.md` row to point there instead of deleting the row. The table does not walk to empty — it walks toward citing the Legacy pack tree instead of the default tree, one sprite batch at a time, until (if ever) the Legacy pack is all that is left citing upstream. The per-PR review mechanism above is unchanged: a PR moving a derived file's location still updates its row in the same PR, the same obligation as introducing one. See `docs/SCOPE.md`'s M9 row and `CLAUDE.md`'s "Derived texture organization" for the mechanics.
