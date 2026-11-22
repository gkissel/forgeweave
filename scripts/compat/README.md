# Compat server matrix

`run_matrix.sh` boots a real dedicated server with a release jar and the partner mods of each playtest
group, and checks the parts of the release checklist that a server console, its log and a block-data
dump can answer. It never starts a client.

```bash
./gradlew build -Pmod_version=1.0.0
FW_COMPAT_JAR_DIRS=~/development/minecraft/fw-release-1.0.0/partner-mods:~/development/minecraft/fw-release-1.0.0/partner-mods/extras:~/development/minecraft/tinker-legacy/run/mods \
  scripts/compat/run_matrix.sh build/libs/forgeweave-1.0.0.jar            # every pack
scripts/compat/run_matrix.sh build/libs/forgeweave-1.0.0.jar scripts/compat/packs/g1.txt
```

It writes `build/compat/summary.md` (one row per pack and pass) and exits 1 if any row is a FAIL. Each
pack's directory under `build/compat/` keeps the server, every boot's log, dump and check output.

## Passes

Every pack goes through these, in order, on one world:

| Pass | What it checks | Checklist lines |
| --- | --- | --- |
| `boot` | Places the SaveCompat corpus (`src/test/resources/fixtures/save_compat/`) and the pack's own fixtures in chests and as block entities, then dumps them. Every corpus item must be placed. | 129 |
| `restart` | The dump after a restart matches the first one byte for byte. | 17, 18, 129 |
| `toggles-off` | All boolean keys in `compat-server.toml` set to false (there must be 21: the checklist's 20 plus `emiPlugin`) and a restart: the dump still matches. | 128, 139, 140 |
| `toggles-on` | All 21 back on and a restart: the dump still matches. | 141 |
| `tool-leveling-off` | `toolLeveling = false` and a restart: the dump still matches. | 24 |
| `without` | Only with a `without` directive: those jars removed and a restart. No item stack vanishes (components owned by the removed mod may go). Vanilla's "Missing data pack mod/forgeweave:datapacks/<mod>" WARN is allowed in this pass only: the removed mod's built-in pack goes with it and comes back with it. | 215 |
| `spark` | Only with `spark`: a formed, fuelled, melting smeltery, a 60 s profile. TPS over the last minute is at least `FW_SPARK_MIN_TPS` (19.5) and Forgeweave's share of the server thread at most `FW_SPARK_MAX_SHARE` percent (2). | 20 |
| `upgrade` | Only with `upgrade-from`: a second server places the corpus under the previous release, then boots this jar on that world. The two dumps match, and the log check follows the config rule below. | 17, 18 |

### Config changes on upgrade

Before the new jar boots, the pass copies the previous release's `config/forgeweave/` files. When
NeoForge logs "Configuration file ... is not correct. Correcting" for one of them, `pack.py` compares
that file's values before and after, and the WARN passes only if every key the old file had is still
there with the same value. The new release may add keys the old one lacked (beta.12 has no
`emiPlugin`) and reword comments, which NeoForge rewrites but `pack.py` doesn't compare. A changed,
reset or dropped value fails, and so does a renamed key, since the old name is gone. The allowance
holds in this pass only. A changed default isn't caught, because NeoForge keeps an existing file's
value. `ForgeweaveConfig`'s javadoc states the same rule for whoever edits the config spec.

Every boot's log is also checked against the pack:

- no WARN, ERROR or FATAL line that mentions forgeweave, and no stack frame in `dev.gkissel.forgeweave`,
  other than known third-party noise in `log-allowlist.txt` (lines 8, 226);
- the `Material registry sync payload: N of N` line, with all N materials encoded (line 62);
- the pack's recipe and material counts, and the material ids it lists as present or absent.

The `without` pass checks only the log hygiene, since removing a mod changes every count.

## Packs

`packs/g<N>.txt` is checklist group N. `base.txt` is the `run/mods` base pack, included by g8 and g9.
One directive per line; `#` starts a comment.

| Directive | Meaning |
| --- | --- |
| `include <pack>` | Read another pack file here, relative to this one. |
| `mod modrinth <slug> <version> <sha512> <file name>` | A Modrinth jar, fetched by its sha512 and checked against it. |
| `mod local <sha512 or -> <file name or glob>` | A jar only a `FW_COMPAT_JAR_DIRS` directory can supply (CurseForge-only mods). With `-` any matching file is taken. A pack with a missing local jar is a SKIP. |
| `recipes <n>`, `materials <n>` | Expected `Loaded N recipes` and `Loaded N materials` counts. |
| `recipes-off <n>`, `materials-off <n>` | The same, for the toggles-off pass. |
| `material <id>...`, `no-material <id>...` | Ids that must be in, or missing from, the material registry. Add `-off` for the toggles-off pass. |
| `fixture <file>` | Extra console commands from `fixtures/`, placed with the corpus and dumped with it. |
| `without <glob>...` | Run the `without` pass with these jar names removed. |
| `upgrade-from modrinth ...` | Run the `upgrade` pass from this earlier Forgeweave jar. |
| `spark` | Run the `spark` pass (the pack must list the spark jar). |

The runner refuses a pack that loads JEI with EMI, Jade with WTHIT, or Better Combat with Epic Fight. A
count the pack doesn't pin still shows in the summary, which is how to pin a new pack: run it once and
copy the numbers in.

A pack file with a `# SKIP on <version>: <reason>` line is a SKIP with that reason, without building a
server. A pack with a missing `mod local` jar is a SKIP too.

### On Minecraft 26.1.2

The packs target Minecraft 26.1.2 and NeoForge 26.1.2 (checked 2026-10-26). Every Modrinth pin is the
newest NeoForge 26.1.2 release of its project, or the newest beta, or the newest alpha where that is all
there is (Powah, EnderIO). The 1.21.1 lines stay in each file as comments. The recipe and material
counts are commented out until the first 26.1.2 run re-pins them, and so is every `upgrade-from`: no
earlier Forgeweave release targets 26.1.2, so the 1.21.1 to 26.1.2 world upgrade is a manual check.

- SKIP: `g3` (EMI), `g7` (Epic Fight), `g10` (Elementarium), `g11` (Eternal Ores), `g12` (Mekanism,
  Eternal Ores) and `g13-iceandfire` (Ice and Fire). None of those mods has a 26.1.2 NeoForge build.
- Reduced: `base.txt` has no Create, Mekanism or Immersive Engineering, so `g8` and `g9` lose those
  mods' materials and toggles. `g8` also has no EMI, Mekanism Generators or Draconic Evolution. `g13`
  has no PneumaticCraft, The Aether, L_Ender's Cataclysm, Actually Additions or Extreme Reactors.
  `partner-gear.cmds` drops its Mekanism pickaxe. Each file's header lists what it loses.
- CurseForge-only jars to stage in a `FW_COMPAT_JAR_DIRS` directory: `justdirethings-1.6.11.jar`
  (`g11-justdirethings`), `twilightforest-26.1.2-4.9.3817-universal.jar` (`g13`), and
  `allthemodium-26.1.2-4.2.0.jar` with `alltheores-4.0.4.jar` (`g10-allthemodium`). Modrinth's
  Allthemodium 3.0.1 is the 1.21.1 jar with 26.1.x tags, so it isn't used.

## The previous-release world

The upgrade pass doesn't use a saved world. It builds one: the beta.12 jar (pinned by sha512 on
Modrinth) boots with the pack, the corpus goes into chests, the server saves and stops, and the release
jar then opens that world. The world from the manual 1.0.0 playtest
(`fw-release-1.0.0/server/run4-prevworld/world`, 3.6 MB) has since been saved by 1.0.0 itself, as its
`level.dat` records, so it is no longer a beta.12 world. A world built fresh each run is the closest
reproducible equivalent, and CI can build it too.

## Environment

| Variable | Default | Use |
| --- | --- | --- |
| `FW_JAVA` | `$JAVA_HOME/bin/java`, else `java` | The java for the NeoForge installer and the server (`mk.sh` writes it into each server's `run.sh`). NeoForge 26.1.2 needs Java 25 or newer; anything older stops the run. A JDK 25 Gradle provisioned works, e.g. `~/.gradle/jdks/eclipse_adoptium-25-amd64-linux.2/bin/java`. |
| `FW_COMPAT_JAR_DIRS` | none | Colon-separated jar directories, searched by file name and hash before any download. |
| `FW_COMPAT_CACHE` | `~/.cache/forgeweave-compat` | Downloaded jars, and the NeoForge server install. |
| `FW_SERVER_BASE` | `$FW_COMPAT_CACHE/neoforge-<neo_version>` | A NeoForge server install (`run.sh`, `libraries/`). Installed from maven.neoforged.net when missing. |
| `FW_COMPAT_OUT` | `build/compat` | Output directory. |
| `FW_COMPAT_FIXTURES` | `src/test/resources/fixtures/save_compat` | The corpus. |
| `FW_SERVER_XMX` | `6G` | Server heap. |
| `FW_SERVER_PORT` | a free port | The server's port. Unset, `mk.sh` asks the kernel for a free one each time it builds a server, so two runs on one machine don't collide, and prints it (the runner keeps that in `mk.txt`). Rcon and query stay off; the runner talks to the server through the console FIFO and the log only. |
| `FW_BOOT_TIMEOUT` | `900` | Seconds to wait for `Done (`. |

## The other scripts

They work on their own too, against any server directory `mk.sh` built:

- `mk.sh <dir> <jar> <pack>` builds a server; `start.sh <dir>` boots it with a FIFO console;
  `cmd.sh <dir> '<command>' [seconds]` sends a command and prints what it logged; `stop.sh <dir>` stops it.
- `fixtures_to_cmds.py <fixture-dir> <out.cmds> <out.idx>` turns the corpus into `setblock` commands;
  `dump.sh <dir> <out> <cmds>...` reads back every position those commands placed.
- `check_items.py <idx> <dump> [<fixture-dir>]` lists each fixture item as loaded, and with the fixture
  dir the components that differ from what was written (informational: a load-time migration shows up
  there). `vanished.py <before> <after>` lists item stacks that disappeared.
- `spark_fw.py <profile>` totals each thread and Forgeweave's share of a `.sparkprofile`;
  `spark_tree.py <profile> <thread> <Class#method>...` prints the call tree under matching frames.
- `snbt.py` is the SNBT parser the Python scripts share.
- `java.sh` picks the java (`FW_JAVA`) and checks it is 25 or newer; `run_matrix.sh` and `mk.sh` source it.
- `test_pack.py` checks `pack.py`'s log checks against small made-up logs, with no server.

## Gaps

These checklist lines stay manual. `docs/releasing.md` lists them as manual steps.

- Line 129, Draconic Evolution module and fusion state. g8 loads Draconic Evolution, but no fixture
  holds a Forgeweave tool or armor piece with DE modules installed or a fusion upgrade, so `boot`,
  `restart` and `without` never see that state. DE keeps it on its own data components, not Forgeweave's.
  The fixture needs that item's exact SNBT: build the item in game on a server with g8's jars, read its
  chest back with `data get block`, and save the result as `fixtures/draconic-gear.cmds`, the way
  `partner-gear.cmds` was made. SNBT written by hand, with no live DE to check it against, either fails to
  place or loads with an empty module grid, and the restart pass would then compare two empty grids and
  pass. On 26.1.2 g8 has no Draconic Evolution at all (no 26.1.2 build, see g8.txt), so this half waits
  for one.
- Line 194, per-mod processing toggles with `/reload`. The runner only changes a toggle while the server
  is stopped, then restarts it. Line 194 needs the server up: set `createRecipes` (then
  `immersiveEngineeringRecipes`, then `enderIoRecipes`) to false in the running server's
  `compat-server.toml`, send `/reload`, and expect the next `Loaded N recipes` line below the boot count;
  set it back, `/reload` again, and expect the boot count. On 1.21.1, g8 and g9 load all three mods
  through `base.txt`. On 26.1.2 `base.txt` has only EnderIO (Create and Immersive Engineering have no
  26.1.2 build), so only `enderIoRecipes` can be checked. It isn't automated because it relies on NeoForge's config watcher reading an edit made
  outside the running server before `/reload` runs, and no one has checked on a dedicated server that it
  does, or how long it takes. A count also shows only that some recipes went away, not that only the
  toggled mod's rows did: the log doesn't name recipe ids. `ProcessingCompatToggleGameTests` covers the
  recipe condition reading the live toggle value.
- Line 220, Track B ore in Allthemodium's mining dimension. With no player on the server,
  `allthemodium:mining` never loads a chunk, so nothing generates there. A pass for
  `g10-allthemodium.txt` would run `execute in allthemodium:mining run forceload add ...` over a few
  chunks, then count each Track B ore block with `execute in allthemodium:mining run fill ... air replace
  <ore>` and read the `Successfully filled N block(s)` lines (by default `fill` changes at most 32,768
  blocks per command, so one chunk takes several). It isn't automated because the dimension's terrain is
  flat and layered: an ore's host block may never fall inside that ore's height band, as the checklist
  line itself warns, so a zero for one ore is not a failure, and the counts to expect need a real run to
  pin. `AllthemodiumMiningWorldgenTest` checks that the `allthemodium_mining_ores` biome modifier lists
  every Track B ore.
- On a dedicated server with no player, worn armor never ticks, so the spark pass measures the smeltery
  and the idle server only, not line 20's worn-armor half.
