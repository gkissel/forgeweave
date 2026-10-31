package dev.gkissel.forgeweave.compat;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Built-in data packs for other mods' data maps, one per mod, added only while that mod is loaded.
 *
 * <p>NeoForge's {@code DataMapLoader} warns about a data map file whose type is not registered as
 * soon as it lists the file, before it reads any {@code neoforge:conditions} block inside. So a
 * Powah or Apotheosis data map in the mod's own {@code data/} folder logged a WARN on every load
 * and every {@code /reload} without that mod (playtest 1.0.0 line 8). The files live under
 * {@code datapacks/<mod>/} in the jar instead, and the pack only exists when the mod does.
 * Always active, so a world never has to turn it on. The entries keep their own compat toggle
 * conditions.
 */
public final class CompatDataPacks {

    /** Every mod with a pack under {@code datapacks/<mod>/}. */
    public static final List<String> MODS = List.of("apotheosis", "powah");

    private CompatDataPacks() {}

    public static void addPackFinders(AddPackFindersEvent event) {
        for (String mod : MODS) {
            if (!ModList.get().isLoaded(mod)) {
                continue;
            }
            event.addPackFinders(
                    Identifier.fromNamespaceAndPath(Forgeweave.MODID, "datapacks/" + mod),
                    PackType.SERVER_DATA,
                    Component.translatable("datapack.forgeweave." + mod),
                    PackSource.BUILT_IN,
                    true,
                    Pack.Position.TOP);
        }
    }
}
