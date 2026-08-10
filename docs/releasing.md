# Releasing Forgeweave

Forgeweave publishes the same built JAR to GitHub Releases, Modrinth, and CurseForge through `.github/workflows/release.yml`.

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

## Publishing

From the branch or commit being released:

```bash
git tag -a mc1.21.1-v0.1.0 -m "Forgeweave 0.1.0 for Minecraft 1.21.1"
git push origin mc1.21.1-v0.1.0
```

Do not move or reuse a published tag. Publish a new patch version if a release needs correction.
