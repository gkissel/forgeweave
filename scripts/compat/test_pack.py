"""Self-check for pack.py's log checks: python3 -I scripts/compat/test_pack.py (no server needed)."""
import os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
OK_LOG = '''[t] [Server thread/INFO] [dev.gkissel.forgeweave.Forgeweave/]: Loaded 2 materials: [forgeweave:iron, forgeweave:tin]
[t] [Server thread/INFO] [dev.gkissel.forgeweave.Forgeweave/]: Material registry sync payload: 2 of 2 materials encode to 9 bytes
[t] [main/INFO] [net.minecraft.world.item.crafting.RecipeManager/]: Loaded 10 recipes
'''
# Mekanism logged this for every Forgeweave item until each one got its own imcMethod (#1138). It is a
# Forgeweave defect, so it is not allowlisted and must fail.
IMC_ERROR = ("[t] [modloading-worker-0/ERROR] [mekanism.common.Mekanism/]: Received IMC message for "
             "'add_module_container' from mod 'forgeweave' for an item 'forgeweave:pickaxe' with an imcMethod "
             "'add_meka_tool_modules' that that has already been registered.\n")


def check(pack, log, phase='on', *extra):
    with tempfile.TemporaryDirectory() as d:
        p, l = os.path.join(d, 'p.txt'), os.path.join(d, 'latest.log')
        open(p, 'w').write(pack)
        open(l, 'w').write(log)
        return subprocess.run([sys.executable, '-I', os.path.join(HERE, 'pack.py'), 'check', p, l, phase, *extra],
                              capture_output=True, text=True).returncode


def corrected(before, after, *, give_before=True):
    """Check a log whose only noise is NeoForge correcting compat-server.toml from `before` to `after`."""
    with tempfile.TemporaryDirectory() as d:
        os.makedirs(os.path.join(d, 'before'))
        os.makedirs(os.path.join(d, 'config', 'forgeweave'))
        open(os.path.join(d, 'before', 'compat-server.toml'), 'w').write(before)
        live = os.path.join(d, 'config', 'forgeweave', 'compat-server.toml')
        open(live, 'w').write(after)
        line = (f'[t] [Server thread/WARN] [net.neoforged.fml.config.ConfigTracker/CONFIG]: Configuration file '
                f'{live} is not correct. Correcting\n')
        extra = [os.path.join(d, 'before')] if give_before else []
        return check('', OK_LOG + line, 'on', *extra)


MISSING_PACK = '[t] [main/WARN] [net.minecraft.server.MinecraftServer/]: Missing data pack mod/forgeweave:datapacks/apotheosis\n'
assert check('', OK_LOG + MISSING_PACK, 'without') == 0, "the without pass allows the removed mod's built-in pack"
assert check('', OK_LOG + MISSING_PACK) == 1, 'any other pass fails on a missing Forgeweave data pack'

OLD_TOML ='[compat]\n\tcreateRecipes = false\n\toverlays = true\n'
assert corrected(OLD_TOML, OLD_TOML + '\temiPlugin = true\n') == 0, 'a correction that only adds a key passes'
assert corrected(OLD_TOML, OLD_TOML.replace('[compat]\n', '[compat]\n\t#Reworded.\n')) == 0, \
    'a correction that only refreshes a comment passes'
assert corrected(OLD_TOML, OLD_TOML + '\temiPlugin = true\n', give_before=False) == 1, \
    'without the pre-boot config the correction WARN fails'
assert corrected(OLD_TOML, OLD_TOML.replace('false', 'true') + '\temiPlugin = true\n') == 1, \
    'a correction that also resets a value fails'
assert corrected(OLD_TOML, '[compat]\n\toverlays = true\n\temiPlugin = true\n') == 1, 'a correction that drops a key fails'
assert corrected(OLD_TOML, OLD_TOML.replace('false', 'true')) == 1, 'a correction that only changes a value fails'


PACK = 'recipes 10\nmaterials 2\nmaterial forgeweave:tin\nno-material forgeweave:zinc\nmaterials-off 1\n'
assert check(PACK, OK_LOG) == 0, 'a clean log with matching counts passes'
assert check(PACK, OK_LOG, 'off') == 1, 'materials-off 1 does not match 2'
assert check(PACK, OK_LOG, 'without') == 0, 'the without phase checks only the log'
assert check('recipes 11\n', OK_LOG) == 1, 'a recipe count mismatch fails'
assert check('material forgeweave:zinc\n', OK_LOG) == 1, 'a missing material fails'
assert check('no-material forgeweave:tin\n', OK_LOG) == 1, 'a present no-material fails'
assert check('', OK_LOG + '[t] [Server thread/WARN] [dev.gkissel.forgeweave.X/]: oops\n') == 1, 'a forgeweave WARN fails'
assert check('', OK_LOG + IMC_ERROR) == 1, "another mod's ERROR naming forgeweave fails"
assert check('', OK_LOG + 'java.lang.NullPointerException\n\tat dev.gkissel.forgeweave.Foo.bar(Foo.java:1)\n') == 1, \
    'a stack frame in forgeweave fails'
assert check('', OK_LOG.replace('2 of 2', '1 of 2')) == 1, 'a partial material sync fails'
print('pack.py checks: ok')
