package dev.gkissel.forgeweave.gametest;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Rotation;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforgespi.language.ModFileScanData;

import org.objectweb.asm.Type;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Registers every {@link GameTest} method of every {@link GameTestHolder} class (found through the mod
 * files' annotation scan, the way NeoForge found them on 1.21.1) as a vanilla test function plus a
 * {@link FunctionGameTestInstance}. Since 1.21.5 GameTests are registry entries rather than annotated
 * methods; this keeps the suite's own shape, and keeps each test's 1.21.1 name:
 * {@code forgeweave:<class>.<method>}, lower-cased.
 *
 * <p>Does nothing unless {@link GameTestHooks#isGametestEnabled()}, so a production install registers
 * no test functions at all.
 */
public final class ForgeweaveGameTestRegistrar {

    private static final Type HOLDER = Type.getType(GameTestHolder.class);

    /** One discovered test: its id, the method behind it, and the method's annotation. */
    private record Entry(Identifier id, Method method, GameTest spec, String namespace) {}

    private static List<Entry> entries = List.of();

    public static void register(IEventBus modEventBus) {
        if (!GameTestHooks.isGametestEnabled()) {
            return;
        }
        entries = discover();
        DeferredRegister<Consumer<GameTestHelper>> functions =
                DeferredRegister.create(Registries.TEST_FUNCTION, Forgeweave.MODID);
        for (Entry entry : entries) {
            Consumer<GameTestHelper> body = function(entry.method());
            functions.register(entry.id().getPath(), () -> body);
        }
        functions.register(modEventBus);
        modEventBus.addListener(ForgeweaveGameTestRegistrar::onRegisterGameTests);
    }

    private static void onRegisterGameTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment =
                event.registerEnvironment(Identifier.fromNamespaceAndPath(Forgeweave.MODID, "default"));
        for (Entry entry : entries) {
            GameTest spec = entry.spec();
            TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(
                    environment,
                    Identifier.fromNamespaceAndPath(entry.namespace(), spec.template()),
                    spec.timeoutTicks(),
                    spec.setupTicks(),
                    spec.required(),
                    Rotation.NONE);
            event.registerTest(entry.id(), new FunctionGameTestInstance(
                    ResourceKey.create(Registries.TEST_FUNCTION, entry.id()), data));
        }
    }

    private static List<Entry> discover() {
        List<Entry> found = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (ModFileScanData scan : ModList.get().getAllScanData()) {
            for (ModFileScanData.AnnotationData annotation : scan.getAnnotations()) {
                if (!HOLDER.equals(annotation.annotationType())
                        || !seen.add(annotation.clazz().getClassName())) {
                    continue;
                }
                Class<?> holder = load(annotation.clazz().getClassName());
                String namespace = holder.getAnnotation(GameTestHolder.class).value();
                String prefix = holder.getSimpleName().toLowerCase(Locale.ROOT) + ".";
                for (Method method : holder.getDeclaredMethods()) {
                    GameTest spec = method.getAnnotation(GameTest.class);
                    if (spec != null) {
                        Identifier id = Identifier.fromNamespaceAndPath(
                                namespace, prefix + method.getName().toLowerCase(Locale.ROOT));
                        found.add(new Entry(id, method, spec, namespace));
                    }
                }
            }
        }
        found.sort(Comparator.comparing(entry -> entry.id().toString()));
        return List.copyOf(found);
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name, true, ForgeweaveGameTestRegistrar.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("GameTest holder in the scan data cannot load: " + name, e);
        }
    }

    /** The test body: calls the method, rethrowing whatever it throws as-is so assertions report. */
    private static Consumer<GameTestHelper> function(Method method) {
        if (!Modifier.isStatic(method.getModifiers())) {
            throw new IllegalStateException("GameTest methods must be static: " + method);
        }
        MethodHandle handle;
        try {
            method.setAccessible(true);
            handle = MethodHandles.lookup().unreflect(method);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot access GameTest method " + method, e);
        }
        return helper -> {
            try {
                handle.invoke(helper);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        };
    }

    private ForgeweaveGameTestRegistrar() {}
}
