package dev.gkissel.forgeweave.gametest.addon;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.api.tool.ForgeweaveTools;
import dev.gkissel.forgeweave.api.tool.PartDefinition;
import dev.gkissel.forgeweave.api.tool.PartKind;
import dev.gkissel.forgeweave.api.tool.PartRole;
import dev.gkissel.forgeweave.api.tool.ToolDefinition;
import dev.gkissel.forgeweave.api.tool.ToolFamily;

/**
 * A stand-in for another mod's tool addon, and the proof that issue #1066's public entry point is
 * one another mod can actually build against. It registers one part and one tool through
 * {@link ForgeweaveTools} and nothing else: every other Forgeweave class it could import is off
 * limits to it on purpose, so a table it reaches has to have been opened rather than merely
 * package-visible.
 *
 * <p>It lives in the GameTest-only {@code gametest} source set, which is folded into the mod's file
 * list for the {@code gameTestServer} run alone (see build.gradle and
 * {@code src/gametest/resources/README.md}), so nothing here reaches a dev run, a data run or the
 * published jar. {@code RegisteredToolGameTests} is what drives it.
 *
 * <p>A second {@code @Mod} entry point under Forgeweave's own id rather than a mod id of its own:
 * that is all a separate {@code neoforge.mods.toml} would buy, and it would buy it by making the
 * GameTest run load a second mod. Its items are registered in the {@code gametest_addon} namespace
 * anyway, which is the part that matters -- every id the stations carry here is foreign.
 */
@Mod(Forgeweave.MODID)
public final class GameTestAddon {

    /** The namespace the addon's items live in, which is deliberately not Forgeweave's. */
    public static final String NAMESPACE = "gametest_addon";

    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NAMESPACE);

    /** The part's own id, which is what {@link #TOOL_DEFINITION}'s head slot names. */
    public static final ResourceLocation BLADE_ID = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "test_blade");

    /** The tool's id, and so the base of the texture and lang keys an addon would ship art under. */
    public static final ResourceLocation TOOL_ID = ResourceLocation.fromNamespaceAndPath(NAMESPACE, "test_dirk");

    /** The pattern that stamps {@link #BLADE} at the Part Builder and shows at the Stencil Table. */
    public static final DeferredItem<Item> PATTERN = ITEMS.registerItem("pattern_test_blade", Item::new);

    public static final DeferredItem<Item> BLADE =
            ITEMS.registerItem("test_blade", properties -> ForgeweaveTools.partItem(PartKind.HEAD, properties));

    /** Two slots, one part foreign and one of Forgeweave's own, so the mix is covered too. */
    public static final ToolDefinition TOOL_DEFINITION = ToolDefinition.builder(TOOL_ID, ToolFamily.MELEE)
            .part(PartRole.HEAD, BLADE_ID)
            .part(PartRole.HANDLE, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "tool_handle"))
            .weapon()
            .attackSpeed(1.6f)
            .flatAttackBonus(2.0f)
            .build();

    public static final DeferredItem<Item> TOOL =
            ITEMS.registerItem("test_dirk", properties -> ForgeweaveTools.toolItem(TOOL_DEFINITION, properties));

    /** What one blade costs, in the units {@link ForgeweaveTools#INGOT_VALUE} denominates. */
    public static final int BLADE_COST = 2 * ForgeweaveTools.INGOT_VALUE;

    public GameTestAddon(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        ForgeweaveTools.registerPart(PartDefinition.stamped(BLADE_ID, PartKind.HEAD, BLADE, PATTERN, BLADE_COST));
        ForgeweaveTools.registerTool(TOOL_DEFINITION, TOOL);
    }
}
