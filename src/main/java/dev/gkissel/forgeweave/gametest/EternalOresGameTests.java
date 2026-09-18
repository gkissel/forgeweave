package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.material.Material;

/**
 * Issue #1031 (D-M8-21, M8-16): the Eternal Ores dedupe. Eternal Ores (all rights reserved, code
 * and assets both -- {@code Catalyst-Studios/Eternal-Ores}'s own {@code LICENCE.txt} -- so nothing
 * here derives from it, only its public item/tag ids) supplies real-world metals Forgeweave already
 * had Track A materials for, so this batch ships no new material: it widens 19 existing materials'
 * {@code neoforge:conditions} (and every melting/casting row that mirrors it) with an {@code
 * eternalores:<id>[_ingot]} branch, following the exact {@code neoforge:or} shape {@code lead} and
 * {@code uranium} already used for their own multiple providers -- see {@code
 * MaterialTest#eternalOresDedupeMaterialsCarryTheEternalOresBranch} for the per-material unit
 * coverage. {@code eternalores} is not a build/test dependency, so every one of those materials'
 * conditions still fails here exactly as it would in a Forgeweave-only install: none of them gains
 * a second registration path this GameTest server can see, only a provider this server does not
 * have either.
 *
 * <p>{@code quartz_enriched_iron} and {@code silicon} additionally needed their own tag-shape fix
 * (not just a new condition branch): the former keyed its Part Builder/repair ingredient on
 * RefinedStorage's bare concrete item id (RefinedStorage ships no {@code c:} tag for it at all), the
 * latter on the flat {@code c:silicon} tag rather than the nested {@code c:ingots/silicon} family
 * Eternal Ores actually populates. Both now list Eternal Ores' matching tag as an additional {@code
 * crafting_items} row alongside the original entry, rather than replacing it -- {@code repair_item}
 * stays on the original entry only, since {@link
 * dev.gkissel.forgeweave.material.Material#CODEC}'s lenient item codec (issue #872) only tolerates
 * an unregistered id as a single, standalone value, not as one element of a mixed item/tag array.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EternalOresGameTests {

    /** The 19 materials this batch widened -- see the class javadoc and {@code MaterialTest}. */
    private static final String[] DEDUPE_MATERIALS = {
            "aluminium", "bronze", "constantan", "electrum", "graphite", "invar", "iridium", "lead",
            "nickel", "osmium", "platinum", "silver", "tin", "titanium", "tungsten", "uranium",
            "uraninite", "quartz_enriched_iron", "silicon",
    };

    @GameTest(template = "empty")
    public static void unsuppliedDedupeMaterialsStillDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : DEDUPE_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without any of its supplying mods "
                            + "(including Eternal Ores), found it registered");
        }
        helper.succeed();
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private EternalOresGameTests() {}
}
