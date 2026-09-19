#!/usr/bin/env python3
"""Generates issue #1046's Better Combat and Epic Fight datapack files from one mapping table,
sibling to scripts/generate_compat_processing.py and scripts/generate_compat_smeltery.py.

Both mods are data-only integrations, unlike every `compat` entry D-M8-5 already toggles: neither
reads a static number from these files for attack damage, speed or range. Better Combat multiplies
whatever the stack's live `minecraft:attack_damage`/`attack_speed` attributes already are (a temporary
`ADD_MULTIPLIED_BASE` modifier, applied then removed around the vanilla attack call --
`net.bettercombat.network.ServerNetwork`, version 2.3.1+1.21.1-neoforge). Epic Fight reads
`itemstack.getAttributeModifiers()` directly and layers its own impact/armor-negation stats on top
(`yesman.epicfight.world.capabilities.item.CapabilityItem#getAttributeModifiersAsWeapon`, version
21.15.6-mc1.21.1-neoforge). Forgeweave already computes attack damage and speed per stack
(`ToolItem#getDefaultAttributeModifiers`), so both mods see the real numbers with no Forgeweave-side
Java seam -- no `compat/<mod>/` package, no config toggle. This mirrors D-M8-5's own carve-out and the
Track A material preset precedent (docs/SCOPE.md: "existence gating already does the job a toggle
would"): these files are existence-gated by construction. Both mods' own loaders silently skip a file
whose target item id is not currently registered, and neither even scans its data folder unless the
mod itself is present (`net.bettercombat.logic.WeaponRegistry#loadAttributes`,
`yesman.epicfight.api.data.reloader.ItemCapabilityReloadListener`) -- so no `neoforge:conditions` gate
is written or needed.

Presets/types are drawn only from ids independently confirmed against each mod's real source (see the
PR body for the full research citations):
  - Better Combat 33 built-in `weapon_attributes` presets (directory listing, commit `616829c5`).
  - Epic Fight's 17 built-in weapon "type" ids (`EpicFightItemCapabilityPresets`, branch `1.21.1`).

Usage: python3 scripts/generate_combat_movesets.py
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BC_DIR = ROOT / "src/main/resources/data/forgeweave/weapon_attributes"
EF_WEAPON_DIR = ROOT / "src/main/resources/data/forgeweave/capabilities/weapons"
EF_ARMOR_DIR = ROOT / "src/main/resources/data/forgeweave/capabilities/armors"

# The 33 confirmed Better Combat presets (data/bettercombat/weapon_attributes/*.json, commit
# 616829c5509712024b14ebd8f26db6f0ba0e5138) -- for this script's own sanity check, not shipped.
BC_PRESETS = {
    "anchor", "axe", "battlestaff", "bow_two_handed_heavy", "bow_two_handed_light", "claw",
    "claymore", "coral_blade", "crossbow_two_handed_heavy", "crossbow_two_handed_light", "cutlass",
    "dagger", "double_axe", "fist", "glaive", "halberd", "hammer", "heavy_axe", "katana", "lance",
    "mace", "pickaxe", "rapier", "scythe", "sickle", "soul_knife", "spear", "staff", "sword",
    "trident", "twin_blade", "vanilla_mace", "wand",
}

# The 17 confirmed Epic Fight built-in weapon types (EpicFightItemCapabilityPresets, branch 1.21.1).
EF_TYPES = {
    "axe", "sword", "greatsword", "longsword", "uchigatana", "dagger", "trident", "shield",
    "pickaxe", "shovel", "hoe", "bokken", "spear", "tachi", "fist", "bow", "crossbow",
}

# Every Forgeweave weapon and tool (dev.gkissel.forgeweave.menu.ToolAssemblyRecipes#ENTRIES minus
# armor and the two AmmoToolItems -- see this issue's own GeneratedCombatMovesetTest, which re-derives
# this exact set from the registry rather than trusting this list). One row per item:
# (item_id, better_combat_preset, epic_fight_type, one-line reasoning for the PR/SCOPE.md table).
#
# shuriken and arrow are deliberately absent: both are ToolConstants.SHURIKEN/ARROW's own thrown/fired
# ammo, and ShurikenItem carries no melee attack attributes at all (ToolConstants#SHURIKEN's javadoc:
# "inert here because ShurikenItem carries no melee attributes at all") -- there is no swing for either
# mod's weapon system to animate.
WEAPON_MOVESETS = [
    # -- Sword family and the other Tool Station/Forge melee weapons --
    ("broadsword", "sword", "sword",
     "baseline one-handed blade with a wide guard; the plain sword preset/type on both mods"),
    ("longsword", "sword", "longsword",
     "Better Combat has no distinct longsword preset so it shares the plain sword moveset; Epic Fight ships a longsword type by that exact name"),
    ("rapier", "rapier", "dagger",
     "Better Combat names a rapier preset outright; Epic Fight has none, so its fast, low-damage-cutoff profile (13, lowest of the sword family) fits the low-impact dagger type best"),
    ("dagger", "dagger", "dagger", "exact name and shape match on both mods"),
    ("scimitar", "cutlass", "tachi",
     "a curved single-edge blade: Better Combat's cutlass is a curved-blade preset by design, Epic Fight's tachi is its curved-saber type"),
    ("katana", "katana", "uchigatana",
     "exact match: Better Combat names the preset katana outright, Epic Fight's uchigatana is literally its katana-style type"),
    ("cleaver", "claymore", "greatsword",
     "a big blade with an extra plate riveted on and a tough handle reads as a heavy two-handed sword on both mods"),
    ("warmace", "vanilla_mace", "axe",
     "WarmaceItem's hurtEnemy/postHurtEnemy delegate straight to vanilla's MaceItem, so Better Combat's vanilla_mace preset (built for that exact vanilla item) is an exact behavioral match. Epic Fight ships no capability for vanilla's own mace at all (no weapons/mace.json, no mace item_keyword regex, no MaceItem case in CommonItemCapabilityProvider#registerWeaponTypesByClass) -- confirmed from its real source, not assumed -- and its fist type is bare-knuckle punching (EpicFightMovesets#GLOVE), not a held-weapon swing, so it is the wrong animation for a two-handed smash weapon regardless of the 'blunt impact' theme. axe is the closest built-in animation match: its AXE_1H moveset is a real one-handed overhead/chopping combo (AXE_AUTO1/2, AXE_DASH, AXE_AIRSLASH), the same moveset Epic Fight's own pickaxe and shovel types already borrow by parenting axe"),
    ("battlesign", "staff", "axe",
     "a flat implement on a handle whose innate blocks and reflects. Epic Fight's shield type looked like a thematic match, but its own SHIELD moveset carries no addComboAttacks at all (only BLOCK/BLOCK_SHIELD pose modifiers), confirmed from EpicFightMovesets.java -- a main-hand item given that type could not attack. axe's real one-handed swing is the closest held-weapon animation available; the blocking/reflecting behavior stays Forgeweave's own DEFLECT innate through CombatSeams, off Epic Fight entirely. Better Combat has no shield-shaped preset either, so staff (its closest pole-mounted guard implement) stands in there"),
    ("frying_pan", "hammer", "axe",
     "a single blunt head on a handle swings the same short crushing arc as Better Combat's blunt-impact hammer preset. Epic Fight's fist type is bare-knuckle punching (EpicFightMovesets#GLOVE), not a held swing, so it is wrong here for the same reason it is wrong for the warmace; axe's real one-handed swinging combo is the closest built-in animation match for a blunt head on a handle"),
    # -- Axe family --
    ("hatchet", "axe", "axe", "exact match: single axe head, mines the axe tag"),
    ("battleaxe", "double_axe", "axe",
     "two broad_axe_head parts on a tough rod is literally a double-headed axe on Better Combat's own preset by that name; Epic Fight has one axe type for the whole family"),
    ("lumberaxe", "heavy_axe", "axe",
     "a broad axe head plus a large plate on a tough rod is bigger than the hatchet's single head, matching Better Combat's heavy_axe over its plain axe; still Epic Fight's one axe type"),
    ("mattock", "axe", "axe",
     "an axe head leads its part list (axe_head before shovel_head) and mines the axe tag first of its two; no dual-tool preset exists on either mod"),
    # -- Harvest tools whose Epic Fight type follows their own mineable/* tag exactly --
    ("kama", "sickle", "hoe",
     "Better Combat names a sickle preset outright, matching the kama's shape; Epic Fight has no sickle type, so hoe -- the exact tag the kama itself mines -- is the strongest available match"),
    ("scythe", "scythe", "hoe",
     "exact name match on Better Combat; Epic Fight has no scythe type, so hoe -- the exact tag the scythe mines -- is the strongest available match"),
    ("hammer", "hammer", "pickaxe",
     "exact name match on Better Combat; Epic Fight has no hammer type, so pickaxe -- the exact tag the hammer mines -- is the strongest available match"),
    ("vein_hammer", "hammer", "pickaxe",
     "same reasoning as the hammer: no Better Combat preset distinguishes a mining hammer from a war hammer, and it mines the same pickaxe tag"),
    ("excavator", "pickaxe", "shovel",
     "Better Combat ships no shovel preset at all, so pickaxe (its only generic mining-tool moveset) is the closest available; Epic Fight's shovel type matches the excavator's own mined tag exactly"),
    ("pickaxe", "pickaxe", "pickaxe", "exact match on both"),
    ("shovel", "pickaxe", "shovel",
     "Better Combat ships no shovel preset, so its generic pickaxe moveset is the closest stand-in; Epic Fight's shovel type is an exact match"),
    # -- Bows and the crossbow --
    ("shortbow", "bow_two_handed_light", "bow",
     "the fastest-drawing, most mobile of the three launchers (12-tick draw) fits Better Combat's light bow preset; Epic Fight has one bow type for the whole family"),
    ("longbow", "bow_two_handed_heavy", "bow",
     "the slowest-drawing bow (30-tick draw, no speed-up while charging) fits Better Combat's heavy preset; same single Epic Fight bow type"),
    ("crossbow", "crossbow_two_handed_heavy", "crossbow",
     "the slowest draw of all three launchers (45 ticks) fits Better Combat's heavy crossbow preset; Epic Fight's one crossbow type is an exact match"),
]

# The four light and four heavy armor pieces (Epic Fight only -- Better Combat has no armor schema at
# all, confirmed from its source: `weapon_attributes` is the entirety of its datapack surface).
# Weight and stun_armor are Forgeweave's own numbers (Epic Fight's own README: weight shortens stun
# time, raises skill stamina cost and lowers attack speed the more of it a piece carries). The heavy
# set reuses ToolConstants#HEAVY_ARMOR_FACTOR (1.4, the same multiplier the heavy set's own defense
# already applies) rather than a second, disconnected heaviness number.
EF_LIGHT_ARMOR_WEIGHT = 0.5
EF_LIGHT_ARMOR_STUN = 0.15
HEAVY_ARMOR_FACTOR = 1.4  # ToolConstants.HEAVY_ARMOR_FACTOR

ARMOR_PIECES = ["helmet", "chestplate", "leggings", "boots"]


def write_json(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2) + "\n")


def weapon_files() -> int:
    count = 0
    for item_id, bc_preset, ef_type, _reasoning in WEAPON_MOVESETS:
        assert bc_preset in BC_PRESETS, f"{item_id}: {bc_preset!r} is not a confirmed Better Combat preset"
        assert ef_type in EF_TYPES, f"{item_id}: {ef_type!r} is not a confirmed Epic Fight weapon type"
        write_json(BC_DIR / f"{item_id}.json", {"parent": f"bettercombat:{bc_preset}"})
        write_json(EF_WEAPON_DIR / f"{item_id}.json", {"type": f"epicfight:{ef_type}"})
        count += 2
    return count


def armor_files() -> int:
    count = 0
    for piece in ARMOR_PIECES:
        write_json(EF_ARMOR_DIR / f"{piece}.json", {
            "attributes": {"weight": EF_LIGHT_ARMOR_WEIGHT, "stun_armor": EF_LIGHT_ARMOR_STUN},
        })
        write_json(EF_ARMOR_DIR / f"heavy_{piece}.json", {
            "attributes": {
                "weight": round(EF_LIGHT_ARMOR_WEIGHT * HEAVY_ARMOR_FACTOR, 2),
                "stun_armor": round(EF_LIGHT_ARMOR_STUN * HEAVY_ARMOR_FACTOR, 2),
            },
        })
        count += 2
    return count


def main() -> None:
    written = weapon_files() + armor_files()
    print(f"wrote {written} files "
          f"({len(WEAPON_MOVESETS)} weapons x 2 mods + {len(ARMOR_PIECES)} armor pieces x 2 sets)")


if __name__ == "__main__":
    main()
