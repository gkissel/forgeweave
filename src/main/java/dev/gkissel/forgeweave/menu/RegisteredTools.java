package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.item.Item;

import dev.gkissel.forgeweave.api.tool.ForgeweaveTools;
import dev.gkissel.forgeweave.api.tool.PartDefinition;
import dev.gkissel.forgeweave.api.tool.PartKind;
import dev.gkissel.forgeweave.api.tool.ToolDefinition;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The seam between {@code api.tool}, which another mod compiles against, and the four tables the
 * stations actually read (issue #1066): {@link ToolAssemblyRecipes#ENTRIES},
 * {@link PartBuilderRecipes}, {@link StencilTableMenu#PATTERNS} and {@link ToolStationTabs#TABS}.
 * Each of those is now its shipped list plus whatever was registered, in that order, so nothing a
 * player sees moves.
 *
 * <p>Translation, not storage. The api package owns the registrations and the window; this class
 * converts one {@link ToolDefinition} into the {@link ToolConstants.Entry} the stat formula takes
 * and the {@link ToolAssemblyRecipes.Entry} the station matches slots against. The conversion is
 * cached because two tables have to agree on the identity of the entries they hold: a tab points at
 * the row the station assembles from, not at a copy of it.
 *
 * <p>The api's enums are name-for-name mirrors of the internal ones ({@code PartRoleMirrorTest}
 * fails the build if they drift), which is what lets the mapping here be {@code valueOf} rather than
 * a switch that a new role could be left out of.
 */
public final class RegisteredTools {

    private static List<ToolAssemblyRecipes.Entry> assemblies;

    /**
     * Installs the factory that turns a registered definition into the item classes it has to be.
     * Called once while Forgeweave constructs itself, so an addon can build a tool item in its own
     * constructor without importing anything below {@code api}.
     */
    public static void installItemFactory() {
        ForgeweaveTools.installItemFactory(new ForgeweaveTools.ItemFactory() {
            @Override
            public Item tool(ToolDefinition definition, Item.Properties properties) {
                return new ToolItem(properties, constants(definition), definition.mineableBlocks(),
                        definition.weapon(), null);
            }

            @Override
            public Item part(PartKind kind, Item.Properties properties) {
                return new PartItem(properties, PartItem.Kind.valueOf(kind.name()));
            }
        });
    }

    /**
     * {@code definition} as the record every Forgeweave stat and art path already speaks. The id
     * keeps its namespace, so two addons can each register a {@code hammer} without their tools
     * sharing art or error messages.
     */
    public static ToolConstants.Entry constants(ToolDefinition definition) {
        List<ToolConstants.PartSlot> parts = new ArrayList<>(definition.parts().size());
        for (ToolDefinition.PartSlot slot : definition.parts()) {
            parts.add(new ToolConstants.PartSlot(ToolConstants.Role.valueOf(slot.role().name()),
                    slot.partId(), slot.weight()));
        }
        return new ToolConstants.Entry(definition.id().toString(),
                ToolConstants.Category.valueOf(definition.family().name()), List.copyOf(parts),
                definition.attackSpeed(), definition.damagePotential(), 1.0f, definition.flatAttackBonus(),
                definition.durabilityMultiplier(), definition.miningSpeedModifier(), false, false);
    }

    /** Every registered tool as a station row, in registration order. */
    public static synchronized List<ToolAssemblyRecipes.Entry> assemblies() {
        if (assemblies == null) {
            List<ToolAssemblyRecipes.Entry> rows = new ArrayList<>();
            for (ForgeweaveTools.RegisteredTool registered : ForgeweaveTools.tools()) {
                rows.add(new ToolAssemblyRecipes.Entry(constants(registered.definition()), registered.tool()));
            }
            assemblies = List.copyOf(rows);
        }
        return assemblies;
    }

    /** {@code builtIn} with every registered tool appended, which is how the station's table is built. */
    public static List<ToolAssemblyRecipes.Entry> appendAssemblies(List<ToolAssemblyRecipes.Entry> builtIn) {
        List<ToolAssemblyRecipes.Entry> rows = new ArrayList<>(builtIn);
        rows.addAll(assemblies());
        return List.copyOf(rows);
    }

    /** Every registered part, in registration order. */
    public static List<PartDefinition> parts() {
        return ForgeweaveTools.parts();
    }

    /**
     * A registered part's item, checked. The api hands over a plain {@link Item} supplier, so this
     * is where an addon that registered something other than a Forgeweave part item finds out,
     * naming the part rather than throwing a bare cast failure out of the Part Builder.
     */
    public static PartItem partItem(PartDefinition definition) {
        Item item = definition.part().get();
        if (!(item instanceof PartItem partItem)) {
            throw new IllegalStateException(definition.partId()
                    + " was registered as a tool part, but its item was not built by"
                    + " ForgeweaveTools#partItem");
        }
        return partItem;
    }

    private RegisteredTools() {}
}
