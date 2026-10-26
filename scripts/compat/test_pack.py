"""Self-check for pack.py's log checks: python3 -I scripts/compat/test_pack.py (no server needed)."""
import os, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
OK_LOG = '''[t] [Server thread/INFO] [dev.gkissel.forgeweave.Forgeweave/]: Loaded 2 materials: [forgeweave:iron, forgeweave:tin]
[t] [Server thread/INFO] [dev.gkissel.forgeweave.Forgeweave/]: Material registry sync payload: 2 of 2 materials encode to 9 bytes
[t] [main/INFO] [net.minecraft.world.item.crafting.RecipeManager/]: Loaded 10 recipes
[t] [modloading-worker-0/ERROR] [mekanism.common.Mekanism/]: Received IMC message for 'add_module_container' from mod 'forgeweave' for an item 'forgeweave:pickaxe' with an imcMethod 'add_meka_tool_modules' that that has already been registered.
'''


def check(pack, log, phase='on'):
    with tempfile.TemporaryDirectory() as d:
        p, l = os.path.join(d, 'p.txt'), os.path.join(d, 'latest.log')
        open(p, 'w').write(pack)
        open(l, 'w').write(log)
        return subprocess.run([sys.executable, '-I', os.path.join(HERE, 'pack.py'), 'check', p, l, phase],
                              capture_output=True, text=True).returncode


PACK = 'recipes 10\nmaterials 2\nmaterial forgeweave:tin\nno-material forgeweave:zinc\nmaterials-off 1\n'
assert check(PACK, OK_LOG) == 0, 'a clean log with matching counts passes; allowlisted noise is ignored'
assert check(PACK, OK_LOG, 'off') == 1, 'materials-off 1 does not match 2'
assert check(PACK, OK_LOG, 'without') == 0, 'the without phase checks only the log'
assert check('recipes 11\n', OK_LOG) == 1, 'a recipe count mismatch fails'
assert check('material forgeweave:zinc\n', OK_LOG) == 1, 'a missing material fails'
assert check('no-material forgeweave:tin\n', OK_LOG) == 1, 'a present no-material fails'
assert check('', OK_LOG + '[t] [Server thread/WARN] [dev.gkissel.forgeweave.X/]: oops\n') == 1, 'a forgeweave WARN fails'
assert check('', OK_LOG + 'java.lang.NullPointerException\n\tat dev.gkissel.forgeweave.Foo.bar(Foo.java:1)\n') == 1, \
    'a stack frame in forgeweave fails'
assert check('', OK_LOG.replace('2 of 2', '1 of 2')) == 1, 'a partial material sync fails'
print('pack.py checks: ok')
