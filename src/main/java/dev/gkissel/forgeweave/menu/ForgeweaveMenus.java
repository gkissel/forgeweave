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

    // These two have no side inventory, so the station-group tab row (issue #78) is the whole
    // open-menu payload; the pattern selection (issue #44) still rides the menu's own DataSlot.
    public static final DeferredHolder<MenuType<?>, MenuType<StencilTableMenu>> STENCIL_TABLE =
            MENUS.register("stencil_table", () -> IMenuTypeExtension.create(StencilTableMenu::new));

    // The Pattern Chest and Part Chest (docs/SCOPE.md M1 issue #66): each registration bakes in its
    // ChestKind so the client-side ChestMenu constructor doesn't need to read it from the packet.
    public static final DeferredHolder<MenuType<?>, MenuType<ChestMenu>> PATTERN_CHEST =
            MENUS.register("pattern_chest", () -> IMenuTypeExtension.create(
                    (windowId, inventory, buf) -> new ChestMenu(ChestKind.PATTERN, windowId, inventory, StationGroup.STREAM_CODEC.decode(buf))));

    public static final DeferredHolder<MenuType<?>, MenuType<ChestMenu>> PART_CHEST =
            MENUS.register("part_chest", () -> IMenuTypeExtension.create(
                    (windowId, inventory, buf) -> new ChestMenu(ChestKind.PART, windowId, inventory, StationGroup.STREAM_CODEC.decode(buf))));

    // #101: the smeltery controller (docs/SCOPE.md M2). Its whole payload is the controller's
    // position -- the tank contents the screen draws ride the block entity's own sync, not this
    // menu; see SmelteryMenu.
    public static final DeferredHolder<MenuType<?>, MenuType<SmelteryMenu>> SMELTERY =
            MENUS.register("smeltery", () -> IMenuTypeExtension.create(
                    (windowId, inventory, buf) -> new SmelteryMenu(
                            windowId, inventory, buf.readBlockPos(), buf.readVarInt())));

    // #442: the seared furnace, same payload shape as the smeltery.
    public static final DeferredHolder<MenuType<?>, MenuType<SearedFurnaceMenu>> SEARED_FURNACE =
            MENUS.register("seared_furnace", () -> IMenuTypeExtension.create(
                    (windowId, inventory, buf) -> new SearedFurnaceMenu(
                            windowId, inventory, buf.readBlockPos(), buf.readVarInt())));

    // T44/#475: the seared reservoir. Its payload is the controller's position and nothing else --
    // the menu has no slots at all, and the fluid column rides the block entity's own sync.
    public static final DeferredHolder<MenuType<?>, MenuType<SearedReservoirMenu>> SEARED_RESERVOIR =
            MENUS.register("seared_reservoir", () -> IMenuTypeExtension.create(
                    (windowId, inventory, buf) -> new SearedReservoirMenu(windowId, inventory, buf.readBlockPos())));

    // #277: the seared duct's one-slot filter GUI (docs/SCOPE.md M3.4). No payload at all -- the
    // filter slot's contents ride vanilla's own slot sync, and nothing else on the screen is dynamic.
    public static final DeferredHolder<MenuType<?>, MenuType<SearedDuctMenu>> SEARED_DUCT =
            MENUS.register("seared_duct", () -> IMenuTypeExtension.create(
                    (windowId, inventory, buf) -> new SearedDuctMenu(windowId, inventory)));

    private ForgeweaveMenus() {}
}
