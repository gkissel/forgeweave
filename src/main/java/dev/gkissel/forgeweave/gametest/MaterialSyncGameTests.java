package dev.gkissel.forgeweave.gametest;

import dev.gkissel.forgeweave.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

import dev.gkissel.forgeweave.gametest.GameTestHolder;
import dev.gkissel.forgeweave.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.material.Material;

/**
 * The material registry sync that the server reports at startup (issue #1065). {@code
 * MaterialSyncSizeTest} budgets the shipped roster off the data folder; this measures what a running
 * server would really send.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MaterialSyncGameTests {

    /**
     * Playtest checklist 1.0.0, line 62: the server startup log carries the real material sync total,
     * "Material registry sync payload: N of N materials encode to B bytes". This asserts the numbers
     * that line prints rather than grepping a log: every loaded material encodes, the total is the
     * live registry's size, and the payload is not empty.
     */
    @GameTest(template = "empty")
    public static void everyLoadedMaterialEncodesIntoTheSyncPayload(GameTestHelper helper) {
        Forgeweave.MaterialSyncSize sync = Forgeweave.materialSyncSize(helper.getLevel().registryAccess());
        int registered = helper.getLevel().registryAccess().lookupOrThrow(Material.REGISTRY).size();

        helper.assertTrue(registered > 50, "expected the shipped roster on the server, got " + registered);
        helper.assertValueEqual(sync.total(), registered, "materials the log line counts");
        helper.assertValueEqual(sync.encoded(), sync.total(), "materials that encode for the client");
        helper.assertTrue(sync.bytes() > sync.total(), "expected a real payload, got " + sync.bytes() + " bytes");
        helper.succeed();
    }

    private MaterialSyncGameTests() {}
}
