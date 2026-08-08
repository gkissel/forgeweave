package dev.gkissel.forgeweave.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;

import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ChestKind;

/** Menu types for Forgeweave's station GUIs. */
public final class ForgeweaveMenus {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, Forgeweave.MODID);

    // The client-side menu needs the side-inventory slot count from the open-menu packet (issue #40's
    // follow-up) so it builds the same number of Slots the server did -- see each menu's buf-reading
    // constructor, same shape as CraftingStationMenu below.
    public static final DeferredHolder<MenuType<?>, MenuType<PartBuilderMenu>> PART_BUILDER =
            MENUS.register("part_builder", () -> IMenuTypeExtension.create(PartBuilderMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ToolStationMenu>> TOOL_STATION =
            MENUS.register("tool_station", () -> IMenuTypeExtension.create(ToolStationMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<CraftingStationMenu>> CRAFTING_STATION =
            MENUS.register("crafting_station", () -> IMenuTypeExtension.create(CraftingStationMenu::new));

    // Same "no block position needs syncing" shape as PART_BUILDER/TOOL_STATION above; the selection
    // state (issue #44) rides the menu's own DataSlot, not the open-menu packet.
    public static final DeferredHolder<MenuType<?>, MenuType<StencilTableMenu>> STENCIL_TABLE =
            MENUS.register("stencil_table", () -> IMenuTypeExtension.create((windowId, inventory, buf) -> new StencilTableMenu(windowId, inventory)));

    // The Pattern Chest and Part Chest (docs/SCOPE.md M1 issue #66): same "no block position needs
    // syncing" shape as STENCIL_TABLE above -- each registration bakes in its ChestKind so the
    // client-side ChestMenu constructor doesn't need to read it from the open-menu packet.
    public static final DeferredHolder<MenuType<?>, MenuType<ChestMenu>> PATTERN_CHEST =
            MENUS.register("pattern_chest", () -> IMenuTypeExtension.create((windowId, inventory, buf) -> new ChestMenu(ChestKind.PATTERN, windowId, inventory)));

    public static final DeferredHolder<MenuType<?>, MenuType<ChestMenu>> PART_CHEST =
            MENUS.register("part_chest", () -> IMenuTypeExtension.create((windowId, inventory, buf) -> new ChestMenu(ChestKind.PART, windowId, inventory)));

    private ForgeweaveMenus() {}
}
