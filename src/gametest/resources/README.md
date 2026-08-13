# GameTest-only datapack

Fixtures that the GameTests need and that must never reach a player's world:

| File | Why |
| --- | --- |
| `data/c/tags/item/ingots/copper.json` | Plants `minecraft:brick` in `c:ingots/copper` as a stand-in for a modded ingot, so the GameTest can prove docs/SCOPE.md's M2 ladder promise — an item Forgeweave has never heard of melts because a *tag* names it, with no Forgeweave code and no Forgeweave recipe of its own. |
| `data/c/tags/item/ingots/bronze.json` | Plants `minecraft:nether_brick` in `c:ingots/bronze` (a different stand-in than copper's, so no item matches two materials at once), so `SteelAndTagGatedGameTests` can prove issue #234's tag-gating both ways: with the tag supplied the Part Builder crafts bronze parts, and lead/silver/electrum — whose tags nothing supplies — stay unobtainable. |
| `data/forgeweave/forgeweave/melting_recipe/gametest_above_lava.json` | A recipe at 1400, hotter than lava's 1300, so the GameTest can prove temperature gating. |
| `data/forgeweave/forgeweave/smeltery_fuel/gametest_super_fuel.json` | A fuel riding `minecraft:water` (inert, never a real fuel) at 5000 degrees, so `SmelteryFuelGameTests` can melt `gametest_above_lava`'s 1400-degree recipe — lava alone cannot — without waiting through the real mB/duration math a shipped superfuel would use (issue #97). |

`build.gradle` puts this directory on the run classpath (`sourceSets.main.resources`) and excludes
exactly these paths from the published `jar`. Adding a file here means adding its exclude there.
