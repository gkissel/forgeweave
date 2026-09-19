package dev.gkissel.forgeweave.api.tool;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * The one entry point another mod registers a tool or a tool part through (issue #1066, part of
 * #1008). A tool from outside is registered in <b>Java</b>: it needs an {@link Item}, and a model
 * with one tinted layer per part, neither of which a datapack can produce. A material, a trait or a
 * modifier is the opposite -- those are datapack JSON and need no code at all.
 *
 * <h2>What registering buys</h2>
 *
 * <p>A registered tool gets a Tool Station tab with slots laid out from its part count, assembles
 * and repairs there, shows in the creative tab, renders its layers tinted by the materials it was
 * built from, and answers the same modifier, trait and leveling machinery every built-in tool does.
 * A registered part is offered at the Stencil Table and stamped at the Part Builder.
 *
 * <h2>The window</h2>
 *
 * <p>Register during your mod's construction, the phase in which a mod that depends on Forgeweave
 * runs after Forgeweave itself. The tables freeze the first time Forgeweave reads them, which is
 * during the registry events that follow; registering after that throws rather than being silently
 * dropped, and so does registering the same id twice.
 *
 * <h2>Art</h2>
 *
 * <p>Ship one texture per layer at {@code <your namespace>:textures/item/<tool>_<layer>.png}, plus a
 * {@code _broken} variant of the layer that shows damage. Layer names come from the part roles, in
 * Forgeweave's own back-to-front drawing order, with a number from the second occurrence of a role
 * on: a handle-head-binding tool is {@code handle}, {@code head}, {@code binding}, and a two-headed
 * one is {@code handle}, {@code head}, {@code head2}. Nothing has to be added to a Forgeweave table
 * for those paths to resolve.
 *
 * <h2>Removal</h2>
 *
 * <p>A player who removes your mod loses its tools, because the item id itself is gone. That is
 * outside Forgeweave's control and worth saying in your own documentation. Materials, traits and
 * modifiers degrade instead of disappearing.
 */
public final class ForgeweaveTools {

    /**
     * One ingot in the material-value units {@link PartDefinition#cost} is denominated in, so a part
     * priced at two ingots costs {@code 2 * INGOT_VALUE}. Forgeweave's own head parts cost two
     * ingots, its rods and bindings one, and its large parts eight.
     */
    public static final int INGOT_VALUE = 144;

    /**
     * A tool as registered: the definition, and the item the station hands the player. Forgeweave
     * reads this; an addon only ever produces one by calling {@link #registerTool}.
     */
    public record RegisteredTool(ToolDefinition definition, Supplier<? extends Item> tool) {}

    /**
     * How Forgeweave builds the item classes an addon's tools and parts have to be, without an addon
     * having to reach into Forgeweave's internals for them. Forgeweave installs the one
     * implementation while it constructs itself; nothing outside Forgeweave calls
     * {@link #installItemFactory}.
     */
    public interface ItemFactory {
        Item tool(ToolDefinition definition, Item.Properties properties);

        Item part(PartKind kind, Item.Properties properties);
    }

    private static final List<RegisteredTool> TOOLS = new ArrayList<>();
    private static final List<PartDefinition> PARTS = new ArrayList<>();
    private static final Set<ResourceLocation> IDS = new HashSet<>();
    private static ItemFactory itemFactory;
    private static boolean frozen;

    /**
     * The tool item for {@code definition}, ready to register with your own {@code DeferredRegister}.
     * Registering the item is yours to do; this only builds it, so that the stats the station's
     * formula uses and the ones the item's attributes use are the same numbers.
     */
    public static Item toolItem(ToolDefinition definition, Item.Properties properties) {
        return factory().tool(definition, properties);
    }

    /** The part item for a part of {@code kind}, ready to register with your own {@code DeferredRegister}. */
    public static Item partItem(PartKind kind, Item.Properties properties) {
        return factory().part(kind, properties);
    }

    /**
     * Adds {@code definition} to the Tool Station's table, in registration order after every
     * built-in tool.
     *
     * @throws IllegalStateException if the window has closed
     * @throws IllegalArgumentException if this id is already registered
     */
    public static synchronized void registerTool(ToolDefinition definition, Supplier<? extends Item> tool) {
        claim(definition.id(), "tool");
        TOOLS.add(new RegisteredTool(definition, tool));
    }

    /**
     * Adds {@code definition} to the Part Builder's table and, unless it is cast only, to the
     * Stencil Table's pattern grid -- both in registration order after every built-in part.
     *
     * @throws IllegalStateException if the window has closed
     * @throws IllegalArgumentException if this id is already registered
     */
    public static synchronized void registerPart(PartDefinition definition) {
        claim(definition.partId(), "part");
        PARTS.add(definition);
    }

    private static void claim(ResourceLocation id, String what) {
        if (frozen) {
            throw new IllegalStateException(id + ": too late to register a " + what
                    + " with Forgeweave. Register during your mod's construction, before Forgeweave"
                    + " first reads its tables.");
        }
        if (!IDS.add(id)) {
            throw new IllegalArgumentException(id + ": a " + what + " is already registered under this id");
        }
    }

    /**
     * Every registered tool, in registration order, and the point at which the window closes.
     * Forgeweave-internal.
     */
    public static synchronized List<RegisteredTool> tools() {
        frozen = true;
        return List.copyOf(TOOLS);
    }

    /** Every registered part, in registration order; closes the window too. Forgeweave-internal. */
    public static synchronized List<PartDefinition> parts() {
        frozen = true;
        return List.copyOf(PARTS);
    }

    /** Whether the window has closed. Forgeweave-internal. */
    public static synchronized boolean isFrozen() {
        return frozen;
    }

    /** Forgeweave-internal: installs the one item factory, once, while Forgeweave constructs itself. */
    public static synchronized void installItemFactory(ItemFactory factory) {
        itemFactory = factory;
    }

    private static synchronized ItemFactory factory() {
        if (itemFactory == null) {
            throw new IllegalStateException("Forgeweave has not finished loading yet. Declare Forgeweave as a"
                    + " dependency so your mod is constructed after it.");
        }
        return itemFactory;
    }

    private ForgeweaveTools() {}
}
