package dev.gkissel.forgeweave.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import net.neoforged.neoforge.event.AddPackFindersEvent;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Registers Forgeweave's built-in Legacy resource pack (issue #796). Forgeweave's default look is
 * now the Forged art set (original, no NOTICE.md rows), and the art it shipped before that rewrite
 * -- mostly derived from the 1.12 clone, some from Spartan Weaponry -- lives on as a resource pack a
 * player can enable from Options {@literal >} Resource Packs to get the old look back.
 *
 * <p>The pack's files ship under {@code resourcepacks/legacy/assets/forgeweave/...} in the mod jar
 * (see {@code src/main/resources}), mirroring {@code assets/forgeweave/...}'s own paths exactly --
 * Minecraft resource packs override by path, so as long as both trees use the same relative paths
 * for the same logical texture, enabling the pack is enough to swap the look. The pack only needs to
 * carry the files that actually differ from the Forged default; anything it does not ship falls
 * through to the mod's own (Forged) copy. See {@code scripts/sprite_sets.py}'s module docstring for
 * how the art generator scripts keep that true as new Forged sprites arrive, and
 * {@code LegacyResourcePackTest} for the audit that the pack never drifts (missing override, or an
 * orphan file that overrides nothing real).
 *
 * <p>{@code alwaysActive = false}: unlike the mod's own assets, the Legacy pack is opt-in, disabled
 * by default like any other resource pack -- Forged is the default look (maintainer decision on
 * #796). {@code Pack.Position.TOP}: resource packs apply bottom-to-top, so this only matters if a
 * player also has another pack touching the same paths above Forgeweave's own assets; TOP keeps the
 * Legacy pack itself never accidentally shadowed by a lower entry in that stack.
 */
public final class ForgeweaveResourcePacks {
    private ForgeweaveResourcePacks() {}

    public static void addPackFinders(AddPackFindersEvent event) {
        event.addPackFinders(
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "resourcepacks/legacy"),
                PackType.CLIENT_RESOURCES,
                Component.translatable("resourcepack.forgeweave.legacy"),
                PackSource.BUILT_IN,
                false,
                Pack.Position.TOP);
    }
}
