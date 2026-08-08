package dev.gkissel.forgeweave.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/** Menu types for Forgeweave's station GUIs. */
public final class ForgeweaveMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Forgeweave.MODID);

    // No block position needs syncing to the client: the client-side menu only exists to hold
    // slots whose contents the server pushes down via the normal container sync packets.
    public static final DeferredHolder<MenuType<?>, MenuType<PartBuilderMenu>> PART_BUILDER =
            MENUS.register("part_builder", () -> IMenuTypeExtension.create((windowId, inventory, buf) -> new PartBuilderMenu(windowId, inventory)));

    public static final DeferredHolder<MenuType<?>, MenuType<ToolStationMenu>> TOOL_STATION =
            MENUS.register("tool_station", () -> IMenuTypeExtension.create((windowId, inventory, buf) -> new ToolStationMenu(windowId, inventory)));

    // Unlike the two above, the Crafting Station's client-side menu does need data from the open-menu
    // packet: the side-inventory slot count (issue #40), so it builds the same number of Slots the
    // server did -- see CraftingStationMenu's buf-reading constructor.
    public static final DeferredHolder<MenuType<?>, MenuType<CraftingStationMenu>> CRAFTING_STATION =
            MENUS.register("crafting_station", () -> IMenuTypeExtension.create(CraftingStationMenu::new));

    private ForgeweaveMenus() {}
}
