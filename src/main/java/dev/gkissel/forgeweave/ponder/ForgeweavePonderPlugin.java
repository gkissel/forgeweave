package dev.gkissel.forgeweave.ponder;

import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Registers Forgeweave's Ponder scenes (docs/SCOPE.md M2 issue #110: "smeltery-assembly and casting
 * scenes play" when Ponder -- {@code https://github.com/Creators-of-Create/Ponder},
 * {@code net.createmod.ponder:ponder-neoforge}, {@code build.gradle}'s compileOnly dependency -- is
 * present).
 *
 * <p>Deliberately not {@code @EventBusSubscriber}-annotated: that annotation is scanned and the class
 * loaded on every install regardless of dist, which would touch {@code net.createmod.ponder} API
 * types (and so classload them) on installs that never asked for Ponder. Instead {@code
 * Forgeweave}'s constructor registers {@link #registerScenes} manually, and only after confirming
 * Ponder is on {@code ModList} -- see that call site's {@code // #110} comment -- so this class, and
 * Ponder's API, are only ever touched when the soft dependency is actually satisfied.
 *
 * <p><b>No storyboards are registered yet.</b> A Ponder scene needs a recorded schematic
 * ({@code src/main/resources/ponder/<category>/<scene>.nbt}, captured in-game) plus a storyboard
 * script (camera/text keyframes in Java); neither exists in this PR -- authoring them is content work
 * well beyond this issue's advancement-chain-plus-soft-dependency scope, and SCOPE.md itself treats
 * the scenes as aspirational ("when present") rather than required for M2. This class is the wiring
 * seam: once scene content exists, register it here, one call per scene, following Ponder's own
 * {@code PonderRegistry.addStoryBoard(ItemLike, String, PonderStoryBoardEntry)} API (item the scene is
 * pinned to, schematic path, storyboard method reference).
 */
public final class ForgeweavePonderPlugin {
    public static void registerScenes(FMLClientSetupEvent event) {
        // Seam only -- see class javadoc.
    }

    private ForgeweavePonderPlugin() {}
}
