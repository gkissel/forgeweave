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

    private ForgeweaveMenus() {}
}
