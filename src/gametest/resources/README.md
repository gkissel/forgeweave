# GameTest-only datapack

Two fixtures that `SmelteryMeltingGameTests` needs and that must never reach a player's world:

| File | Why |
| --- | --- |
| `data/c/tags/item/ingots/copper.json` | Plants `minecraft:brick` in `c:ingots/copper` as a stand-in for a modded ingot, so the GameTest can prove docs/SCOPE.md's M2 ladder promise — an item Forgeweave has never heard of melts because a *tag* names it, with no Forgeweave code and no Forgeweave recipe of its own. |
| `data/forgeweave/forgeweave/melting_recipe/gametest_above_lava.json` | A recipe at 1400, hotter than lava's 1300, so the GameTest can prove temperature gating. Every shipped recipe melts under lava, so there is nothing real to test that with until issue #97 brings hotter fuels and the metals that need them. |

`build.gradle` puts this directory on the run classpath (`sourceSets.main.resources`) and excludes
exactly these two paths from the published `jar`. Adding a file here means adding its exclude there.
