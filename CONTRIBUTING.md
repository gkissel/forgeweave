# Contributing to Forgeweave

Thank you for helping build Forgeweave. The project is an independent, unofficial NeoForge mod inspired by Tinkers' Construct 2.

## Before starting

- Search existing issues before opening a new one.
- Use a feature request for behavior changes and a bug report for reproducible defects.
- Discuss large gameplay, compatibility, or architecture changes in an issue before implementing them.
- Do not contact the Tinkers' Construct or SlimeKnights teams for Forgeweave support.

## Development workflow

1. Create a branch from the appropriate Minecraft version line.
2. Keep commits focused and use descriptive messages.
3. Run `./gradlew build` once the NeoForge project scaffold is present.
4. Open a pull request against `master` for the newest supported Minecraft line, or against the matching `mc/<version>` maintenance branch.
5. Resolve review conversations and keep the branch current before merging.

Direct pushes to protected branches are not allowed. Pull requests are squash-merged so each merged change becomes one focused commit.

## Licensing and provenance

By contributing, you agree that your original contributions are licensed under Forgeweave's MIT License.

Clearly identify code or assets derived from another project. Preserve all applicable copyright and license notices, and do not submit material whose license is incompatible with this repository.
