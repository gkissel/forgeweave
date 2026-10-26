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
| `upgrade` | Only with `upgrade-from`: a second server places the corpus under the previous release, then boots this jar on that world. The two dumps match. NeoForge's "is not correct. Correcting" WARN for a Forgeweave config is allowed here only when the correction kept every old key and value: it may add keys the previous release didn't have (beta.12 has no `emiPlugin`) and refresh reworded comments, but a changed, reset or dropped value still fails. | 17, 18 |

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
copy the numbers in. The jar hashes come from `fw-release-1.0.0/partner-mods/MANIFEST.md`'s
Modrinth-verified files; Just Dire Things and Twilight Forest are local-only. Four packs stay a SKIP
until someone stages a jar:

- `g10.txt`: Elementarium 1.2.0 requires `navaronee_corelib`, which Modrinth doesn't carry.
  `g10-allthemodium.txt` runs group 10's Allthemodium half meanwhile.
- `g11.txt` and `g12.txt`: they need Catalyst-Studios' Eternal Ores 2.1. The Modrinth `eternal-ores`
  1.0.32 jar in `partner-mods/` has the same mod id but is a different mod, with none of the metals
  Forgeweave dedupes. `g11-justdirethings.txt` runs group 11's Just Dire Things half meanwhile.
- `g13-iceandfire.txt`: no Ice and Fire jar is staged.

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
| `FW_COMPAT_JAR_DIRS` | none | Colon-separated jar directories, searched by file name and hash before any download. |
| `FW_COMPAT_CACHE` | `~/.cache/forgeweave-compat` | Downloaded jars, and the NeoForge server install. |
| `FW_SERVER_BASE` | `$FW_COMPAT_CACHE/neoforge-<neo_version>` | A NeoForge server install (`run.sh`, `libraries/`). Installed from maven.neoforged.net when missing. |
| `FW_COMPAT_OUT` | `build/compat` | Output directory. |
| `FW_COMPAT_FIXTURES` | `src/test/resources/fixtures/save_compat` | The corpus. |
| `FW_SERVER_XMX`, `FW_SERVER_PORT` | `6G`, `25599` | Server heap and port. |
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
- `test_pack.py` checks `pack.py`'s log checks against small made-up logs, with no server.

## Gaps

- No Draconic Evolution fixture: its module state lives on its own components, so line 129's module and
  fusion state on DE gear is still a manual check.
- Lines 194 (per-mod processing toggles with `/reload`) and 220 (ore generation in Allthemodium's
  mining dimension) are not automated yet.
- On a dedicated server with no player, worn armor never ticks, so the spark pass measures the smeltery
  and the idle server only, not line 20's worn-armor half.
