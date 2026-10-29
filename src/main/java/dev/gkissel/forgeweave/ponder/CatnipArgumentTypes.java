package dev.gkissel.forgeweave.ponder;

import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import net.createmod.catnip.impl.command.argument.ConfigIdArgument;
import net.createmod.catnip.impl.command.argument.ConfigPathArgument;
import net.createmod.catnip.impl.command.argument.ModIdArgument;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Registers the three argument types of Catnip's {@code /catnip config} command, which Catnip
 * 1.0.52 (the library Ponder split out on 26.1, embedded jar-in-jar alongside it) uses but never
 * registers itself.
 *
 * <p>Without them, building the command tree a joining player is sent fails with "Unrecognized
 * argument type ... ConfigIdArgument", because {@code /catnip} is open to every player. That kicks
 * every player off a server running Forgeweave, and fails every GameTest that logs in a mock
 * player. All three types are stateless, so a {@link SingletonArgumentInfo} serializes each one.
 * The ids live under Forgeweave's namespace so a later Catnip that registers its own cannot collide
 * with these.
 *
 * <p>Only reached behind {@code ModList.isLoaded("catnip")}, so an install without Catnip never
 * links these Catnip types.
 */
public final class CatnipArgumentTypes {
    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, Forgeweave.MODID);

    static {
        ARGUMENT_TYPES.register("catnip_mod_id", () -> ArgumentTypeInfos.registerByClass(
                ModIdArgument.class, SingletonArgumentInfo.contextFree(ModIdArgument::modId)));
        ARGUMENT_TYPES.register("catnip_config_id", () -> ArgumentTypeInfos.registerByClass(
                ConfigIdArgument.class, SingletonArgumentInfo.contextFree(ConfigIdArgument::configId)));
        ARGUMENT_TYPES.register("catnip_config_path", () -> ArgumentTypeInfos.registerByClass(
                ConfigPathArgument.class, SingletonArgumentInfo.contextFree(ConfigPathArgument::path)));
    }

    public static void register(IEventBus modEventBus) {
        ARGUMENT_TYPES.register(modEventBus);
    }

    private CatnipArgumentTypes() {}
}
