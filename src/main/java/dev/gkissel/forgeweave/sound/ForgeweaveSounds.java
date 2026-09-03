package dev.gkissel.forgeweave.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Forgeweave's own sound events. The Slimesling's was the first (parity audit T22, issue #453):
 * upstream 1.12's {@code common/Sounds#slimesling}, whose {@code slimesling.ogg} ships here as
 * {@code sounds/slime_sling.ogg} (NOTICE.md). The level-up chime is the second (docs/SCOPE.md M7,
 * D-M7-8; issue #922), ported from Tinkers' Tool Leveling's {@code CommonProxy#SOUND_LEVELUP}, whose
 * {@code chime.ogg} ships here as {@code sounds/tool_level_up.ogg} (NOTICE.md). {@code
 * assets/forgeweave/sounds.json} names the file for each event -- it is hand-written rather than
 * generated, unlike the model/lang/recipe output (docs/adr/0002), because two entries are not worth
 * a datagen provider.
 */
public final class ForgeweaveSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Forgeweave.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> SLIME_SLING = register("slime_sling");
    public static final DeferredHolder<SoundEvent, SoundEvent> TOOL_LEVEL_UP = register("tool_level_up");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name)));
    }

    private ForgeweaveSounds() {}
}
