# Releasing Forgeweave

Forgeweave publishes the same built JAR to GitHub Releases, Modrinth, and CurseForge through `.github/workflows/release.yml`.
The workflow asks GitHub to generate release notes from the tag, verifies that the result is non-empty,
and sends that same Markdown changelog to all three platforms.

## Version branches

- `master` is the active development branch for the newest supported Minecraft version.
- Create `mc/<minecraft-version>` only when that version enters maintenance and development moves to a newer, source-incompatible Minecraft version.
- Backport shared fixes by cherry-picking focused commits from `master` into maintained version branches.
- Do not create a branch for every Forgeweave release; Git tags identify releases.

Example progression:

```text
master              newest supported Minecraft line
mc/1.21.1           maintained older line
mc/1.21.4           another maintained line, only if genuinely needed
```

## Release tags

Push a signed or annotated tag matching this format:

```text
mc<minecraft-version>-v<forgeweave-version>
```

Examples:

```text
mc1.21.1-v0.1.0-alpha.1
mc1.21.1-v0.1.0-beta.1
mc1.21.1-v0.1.0
```

Tags containing `alpha`, `beta`, or `rc` are published with the corresponding prerelease status. Other tags are stable releases.

The Gradle build must accept `-Pmod_version=<version>` and produce the distributable JAR under `build/libs/`. Source, Javadoc, and development JARs are excluded from publishing.

## GitHub configuration

Create a GitHub environment named `release`. Add these repository or environment settings after the Modrinth and CurseForge project pages exist:

| Kind | Name | Value |
| --- | --- | --- |
| Variable | `MODRINTH_PROJECT_ID` | Forgeweave's Modrinth project ID or slug |
| Variable | `CURSEFORGE_PROJECT_ID` | Forgeweave's numeric CurseForge project ID |
| Secret | `MODRINTH_TOKEN` | Modrinth token with version upload access |
| Secret | `CURSEFORGE_TOKEN` | CurseForge API token with file upload access |

The built-in `GITHUB_TOKEN` creates the matching GitHub Release. Protecting the `release` environment with required reviewers adds a final approval gate before any external publication.

Until the table above is configured, `release.yml` publishes to GitHub Releases alone: the workflow detects that Modrinth and CurseForge are unset and skips them rather than failing. A tag pushed with only some of the four values set still fails the workflow, since a half-configured platform is treated as a mistake, not an intentional skip.

## Release-checklist: screenshot review

Before tagging a release, run `scripts/screenshots.sh` (or `./gradlew runScreenshotHarness` directly)
to launch a real client, open every M1+ station screen in turn, and write a PNG of each to
`build/screenshots/`. Eyeball them for layout defects -- three shipped in M1 (issues #75, #85, #89)
past review that only ever looked at offline PNG compositing, never the actual running game. This is
a manual step, not a CI gate; it requires a display (or `xvfb` on a headless box -- the script detects
and uses `xvfb-run` automatically). New screens register themselves in
`dev.gkissel.forgeweave.client.ScreenshotHarness`.

Also review the worn armor frames (issues #679, #682): `armor_iron.png` and `armor_cobalt.png` (the
full plate set, third person from the front), `armor_obsidian_chestplate.png` (one obsidian-plated
piece over a bare body), and each one's `_firstperson` companion. Both layers must show -- the maille
(chain) visible in the gaps of the plating, both in the material's tint (applied at render time
over the four gray bases, #726 -- no per-material PNG to regenerate) -- with no purple
missing-texture patches and no layer bleeding through where the plating should cover it, and the
lone chestplate must not paint any other body part. `book_armor.png` and `book_armor_chestplate.png`
are the armor chapter's opening page and a piece page with its parts diagram.

Also review the Ponder frames (issues #664, #700, #891): `ponder_smeltery.png`, `ponder_smeltery_sizes.png`,
`ponder_casting.png`, `ponder_armor.png`, `ponder_seared_furnace.png`, `ponder_seared_reservoir.png` and
`ponder_core_tiers.png`, each scene captured on its finished frame from Ponder's
default camera. Every core, controller, drain and faucet must show its front (Ponder looks from the north-west,
so directional blocks sit in north and west walls -- #700's defect was a core showing its back), the
channel run must end over the table and the basin, the core tiers frame must end on a lit Deep Core
under its faucet, and no scene may be an empty plate (a schematic
that failed to load). New scenes register themselves in `PonderHarnessCaptures`.

## Release-checklist: manual dedicated-server pass (from M4)

Each release also gets a dated playtest checklist under `docs/playtest/` (pt-BR) run on a dedicated
server. From the M4 gate (SCOPE.md § "Milestone 4 — armors", D23) every release's checklist must
carry these lines, in addition to the milestone's own acceptance test:

- **JEI sanity** with JEI installed: plating/maille casting rows, armor assembly rows, and the armor
  modifier recipes all show with no JEI code changes; without JEI the game still loads.
- **Previous-release world load** (`mc1.21.1-v0.3.5-beta.1` or later) with a full worn armor set:
  every piece keeps parts, modifiers and durability; overslime survives, and an alpha.1 piece's legacy overshield charge no longer does anything but still loads (#728).
- **Spark profile** on the idle dedicated server: worn armor adds no per-tick cost beyond the
  existing formed-smeltery heartbeat; stations idle at 0.

## Release-checklist: tool leveling (from M7)

From the M7 gate (SCOPE.md § "Milestone 7 — tool leveling", "CI and release gates"), every release
from `0.5.0-beta.5` on carries these lines. The automated half is green before any of them is worth
running: the two save-compat fixtures (`m7_tool_level.snbt`, `m7_armor_level.snbt`) decode in
`SaveCompatCorpusTest`, `LocalizationAuditTest` passes (no level-up or ladder string is a
`Component.literal`), and `NOTICE.md` carries a row for every file derived from Tinkers' Tool
Leveling, the `sounds/chime.ogg` asset included.

- **Level a tool by hand** on the dedicated server: the chat line arrives, the chime plays, and the
  tooltip level colour actually rotates between levels. The hue is the one thing no automated test
  can see.
- **Check the level line at a wrapped and an easter-egg level**: set `tool_level` with `/data` on a
  held tool to 12 (`Like new+`) and to 42 (its own name), and read both off the tooltip.
- **JEI sanity**: no new categories appear, and the Tool Station panel shows the slot a level
  granted.
- **Previous-release world load** carrying both a pre-M7 tool (still level 0, unchanged, no level
  lines) and a leveled one (level, XP and earned slot all intact).
- **Load a world with leveled tools under `toolLeveling = false`**: nothing breaks, no earned slot is
  lost, and the modifiers spent into those slots still work.

## Publishing

From the branch or commit being released:

```bash
git tag -a mc1.21.1-v0.1.0 -m "Forgeweave 0.1.0 for Minecraft 1.21.1"
git push origin mc1.21.1-v0.1.0
```

Do not move or reuse a published tag. Publish a new patch version if a release needs correction.
