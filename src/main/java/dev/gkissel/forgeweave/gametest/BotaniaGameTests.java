package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.material.CompatMaterialAvailability;
import dev.gkissel.forgeweave.material.Material;

/**
 * Issue #857: Botania's ten Track A presets, the Tinkers' Evolution 1.12 roster. On 1.21.1 the
 * provider is Botania Revived (mod id {@code botania}), an unofficial NeoForge port. It is not a
 * build or test dependency, so every {@code neoforge:conditions} check fails here exactly as it
 * would in a Forgeweave-only install. The positive path is the generic mechanism
 * {@code ConditionalMaterialGameTests} already covers.
 *
 * <p>Item ids were read from the {@code Botania-Revived-1.21.1-1.0.6-NeoForge.jar} release. Its
 * license is the Botania License, so this batch names items by id and tag only and derives nothing.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class BotaniaGameTests {

    private static final String[] BOTANIA_MATERIALS = {
            "manasteel", "elementium", "terrasteel", "mana_diamond", "mana_pearl", "dragonstone",
            "mana_string", "livingrock", "livingwood", "dreamwood",
    };

    @GameTest(template = "empty")
    public static void unsuppliedBotaniaMaterialsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : BOTANIA_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without Botania, found it registered");
            helper.assertFalse(CompatMaterialAvailability.isAvailable(name),
                    "expected " + name + " to report unavailable without Botania");
        }
        for (String metal : new String[] {"manasteel", "elementium", "terrasteel"}) {
            helper.assertTrue(ForgeweaveFluids.compatMetalFluid(metal) != null,
                    "expected molten " + metal + " to be registered, hidden rather than missing");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private BotaniaGameTests() {}
}
