package dev.gkissel.forgeweave.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import dev.gkissel.forgeweave.advancement.ForgeweaveCriteriaTriggers;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.modifier.Embossing;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.Fortification;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.menu.ToolStationTabs.Pos;
import dev.gkissel.forgeweave.menu.ToolStationTabs.Tab;

/**
 * The Tool Station's menu -- and the Tool Forge's, which is the same menu with {@link #isForge()}
 * set (issue #152): the tab-positioned input slots, an output slot, and the player's inventory, plus the
 * selected {@link ToolStationTabs.Tab} that decides where those input slots sit and what each one
 * accepts. Same "recompute output every broadcast, only consume on take" shape as
 * {@link dev.gkissel.forgeweave.menu.PartBuilderMenu}.
 *
 * <p>Assembly and repair (docs/SCOPE.md issues #10 and #11) share the same three input slots: which
 * recipe is running depends only on whether the first slot holds a head part or an assembled tool,
 * so the decision -- and the per-slot cost of taking the output -- lives entirely in
 * {@link ToolAssemblyRecipes}, unchanged by the tabs. All this class knows is "ask, show, and charge
 * what you're told".
 *
 * <h2>Tabs (issue #47)</h2>
 *
 * <p>The tab index is a synced {@link DataSlot} set from {@link #clickMenuButton} -- the vanilla
 * stonecutter/loom mechanism, the same one {@code StencilTableMenu} uses -- so the server is the
 * only writer and a dedicated server stays authoritative. Two things follow from it:
 *
 * <ul>
 *   <li><b>What each slot accepts.</b> {@link #accepts} reads the live tab, so the head slot takes
 *       only the selected tool's head part (or, on the repair tab, only an assembled tool) and shift-
 *       clicking obeys the same rule for free ({@code moveItemStackTo} consults {@code mayPlace}).
 *   <li><b>Where each slot is drawn.</b> {@link Slot#x}/{@link Slot#y} are final in modern
 *       Minecraft, so {@link #applyLayout} swaps the three input {@link Slot} objects for fresh ones
 *       at the tab's coordinates, keeping the same container, menu index and order. Positions are
 *       read only by the client's screen, but both sides rebuild identically -- the server from
 *       {@link #clickMenuButton}, the client from {@link #setData} when the data slot arrives -- so
 *       there is no third state to keep in sync.
 * </ul>
 *
 * <p>A tab switch deliberately leaves whatever is already in the slots alone: ejecting items on a
 * button press is how a menu loses a player's stack, and {@link ToolAssemblyRecipes} already refuses
 * to build anything from a mismatched set.
 *
 * <h2>Side inventory (issue #40's follow-up)</h2>
 *
 * <p>When the station has a qualifying neighbor ({@code ToolStationBlockEntity#findSideInventory}),
 * its {@link IItemHandler} is exposed as extra slots after the output slot via {@link
 * SideInventorySlots#create} -- same shape as {@link CraftingStationMenu}'s own side panel; see that
 * class's javadoc for the client/server slot-count handshake.
 */
public class ToolStationMenu extends StationMenu {
    public static final int CONTAINER_SLOTS = 7;
    public static final int HEAD_SLOT = 0;
    public static final int BINDING_SLOT = 1;
    public static final int HANDLE_SLOT = 2;
    /**
     * The extra free slots on the repair/modify tab ({@link ToolStationTabs.Tab#slots}); a build tab
     * needs at most four. Issue #154 added the first two, upstream's 4th and 5th repair positions;
     * issue #248 added the third, upstream's 6th, because the full-parity embossing cost is a donor
     * part plus <em>four</em> reagents (three slime crystals and a gold block), which is one more
     * free slot than the #154 pair could hold.
     */
    public static final int EXTRA_SLOT_1 = 3;
    public static final int EXTRA_SLOT_2 = 4;
    public static final int EXTRA_SLOT_3 = 5;
    public static final int OUTPUT_SLOT = 6;
    /**
     * How many tab-positioned input slots the container has; the output slot is fixed. How many of
     * them the <em>selected</em> tab uses is {@code tab().slots().size()}, upstream's
     * {@code activeSlots} -- the rest are hidden and refuse everything.
     */
    public static final int INPUT_SLOTS = 6;

    /** Upstream's own output-slot spot in {@code ContainerToolStation} ({@code SlotToolStationOut}). */
    private static final int OUTPUT_X = 124;
    private static final int OUTPUT_Y = 38;

    /** Vanilla's rename cap, and the same order of magnitude as upstream's 40-character field. */
    private static final int MAX_NAME_LENGTH = 50;

    /**
     * The craft cue every take from the output slot plays (parity audit T50, issue #481). Upstream
     * {@code ContainerToolStation#playCraftSound} plays its own {@code Sounds.saw} ({@code
     * little_saw.ogg}) at volume {@value #CRAFT_VOLUME} and pitch {@code 0.8 + 0.4 * random}, and
     * {@code ContainerToolForge} overrides it with vanilla's {@code BLOCK_ANVIL_USE} at volume
     * {@value #FORGE_CRAFT_VOLUME} and pitch {@code 0.95 + 0.2 * random}. Neither asks which recipe
     * ran -- assembly, repair, modify and rename all get the same cue.
     *
     * <p>The Forge's is upstream's actual sound. The Station's is a stand-in: upstream's sound
     * assets are CC-BY/CC0 rather than the MIT its code carries, and its own
     * {@code sounds/Credits.txt} attributes no author to {@code little_saw}, so shipping the asset
     * is a maintainer call (CLAUDE.md's Spartan Weaponry precedent for non-MIT material) rather than
     * something the 1.12-parity default settles (issue #566 holds that decision) -- the same call
     * issue #415 made for shocking and issue #495 for squeaky. {@code UI_STONECUTTER_TAKE_RESULT} is vanilla's own "took the output
     * of a cutting station" rasp, which is what this cue is; the volume and pitch spread are
     * upstream's and stay put if the asset ever lands.
     */
    private static final SoundEvent CRAFT_SOUND = SoundEvents.UI_STONECUTTER_TAKE_RESULT;
    private static final float CRAFT_VOLUME = 0.8F;
    private static final float CRAFT_PITCH_BASE = 0.8F;
    private static final float CRAFT_PITCH_SPREAD = 0.4F;
    private static final SoundEvent FORGE_CRAFT_SOUND = SoundEvents.ANVIL_USE;
    private static final float FORGE_CRAFT_VOLUME = 0.9F;
    private static final float FORGE_CRAFT_PITCH_BASE = 0.95F;
    private static final float FORGE_CRAFT_PITCH_SPREAD = 0.2F;

    /**
     * The tool-tab column's own geometry, mirrored from {@code client.ToolStationScreen} rather than
     * referenced: the menu package cannot depend on the client package, the same trade the info
     * panel width below already makes. Upstream's {@code GuiSideButtons} grid -- 18px buttons, 4px
     * spacing, {@code buttons.yOffset = beamC.h + buttonDecorationTop.h} = 9.
     */
    private static final int TAB_BUTTON_SIZE = 18;
    private static final int TAB_BUTTON_SPACING = 4;
    private static final int TAB_BUTTON_COLUMNS = 5;
    private static final int TAB_BUTTONS_Y = 9;

    /** Where the tool-tab column ends, so the side panel can start below it. */
    private static final int TAB_COLUMN_BOTTOM = TAB_BUTTONS_Y
            + tabRows() * TAB_BUTTON_SIZE + (tabRows() - 1) * TAB_BUTTON_SPACING;

    /**
     * ponytail: counted over the whole tab roster, so the side panel below sits at one fixed y at
     * both blocks even though a Tool Station's sidebar is two rows shorter since issue #336. Slot
     * coordinates are part of the menu's client/server contract; deriving them from an item tag to
     * close a cosmetic gap under a shorter column would put datapack state in that contract. Make it
     * per-block only if the gap actually reads as a bug in playtest.
     */
    private static int tabRows() {
        return (ToolStationTabs.TABS.size() + TAB_BUTTON_COLUMNS - 1) / TAB_BUTTON_COLUMNS;
    }

    /**
     * Side-panel layout (issue #40's follow-up, re-placed by issue #88).
     *
     * <p>This side inventory is a Forgeweave addition -- upstream's {@code GuiToolStation} has no
     * {@code GuiSideInventory} at all, so there is no upstream placement to copy. Issue #79 put it
     * on the right, past both info panels, because the station's left edge is taken by its tool-tab
     * column. That was sound in isolation and wrong in practice: it pushed the station's total
     * chrome to 536px against a window that vanilla only guarantees to be 320 wide, so the panel
     * was still 124px off the right edge at a 427px window and 18px off at 640.
     *
     * <p>It now goes where the Crafting Station's and Part Builder's do -- upstream's left-hand
     * {@code rightSide = false} placement -- but <em>below</em> the tab column rather than beside
     * it, which is what the collision actually required. That column is one row of buttons ending
     * at y {@value #TAB_COLUMN_BOTTOM}; the panel starts a {@link #SIDE_PANEL_GAP}px gap under it,
     * stacked on the same edge the way upstream's own modules stack via {@code yOffset}.
     *
     * <p>The move costs nothing horizontally, which is what makes it the right answer rather than a
     * trade: the tab column already reaches x -110, and the panel only needs -122, so total chrome
     * goes 536 -> 428px -- within 12px of the 416px the station occupies with no side panel at all.
     * It fits a 480px window outright, where the old placement needed 640 and still overflowed.
     *
     * <p>Vertically the panel now has less room than a full-height one, so tall neighbours shed rows
     * ({@code client.SideInventoryPanel#visibleRows}) instead of drawing out through the bottom.
     */
    private static final int SIDE_PANEL_GAP = 4;
    public static final int SIDE_PANEL_X = SideInventorySlots.LEFT_SLOT_X;
    public static final int SIDE_PANEL_Y = TAB_COLUMN_BOTTOM + SIDE_PANEL_GAP + SideInventorySlots.SLOT_INSET;

    private final Container container;
    private final ContainerLevelAccess access;
    private final HolderLookup.Provider registries;
    private final DataSlot selectedTab = DataSlot.standalone();
    /**
     * Whether this menu belongs to a Tool Forge rather than a Tool Station (issue #152). Fixed for
     * the life of the menu -- it is a property of the block that opened it -- so it rides the
     * open-menu payload rather than a {@link DataSlot}.
     */
    private final boolean forge;
    public final int sideInventorySlotCount;
    /** The side panel's own slots, kept so the client-side panel can lay them out and scroll them (issue #68). */
    public final List<SideInventorySlots.SideSlot> sideSlots;
    /**
     * Whose menu this is, so {@link #pushToolName} can send the rename field's text down to the
     * players standing at the same station -- upstream {@code ContainerToolStation} keeps the same
     * field for the same reason.
     */
    private final Player owner;
    private String toolName = "";
    /** What {@link #pushToolName} last sent {@link #owner}, so an unchanged name sends nothing. */
    private String pushedToolName = "";

    /**
     * Client-side: constructed from the open-menu packet, which carries the side-inventory slot
     * count, the tab row, and the Tool Station/Tool Forge flag (issue #152).
     */
    public ToolStationMenu(int containerId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        this(containerId, playerInventory, new SimpleContainer(CONTAINER_SLOTS), ContainerLevelAccess.NULL, null,
                buf.readVarInt(), StationGroup.STREAM_CODEC.decode(buf), buf.readBoolean());
    }

    /** Server-side: constructed by {@code ToolStationBlockEntity} with the block's real inventory and detected neighbor. */
    public ToolStationMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory, boolean forge) {
        this(containerId, playerInventory, container, access, sideInventory,
                sideInventory == null ? 0 : sideInventory.getSlots(), groupAt(access), forge);
    }

    private ToolStationMenu(int containerId, Inventory playerInventory, Container container, ContainerLevelAccess access,
            @Nullable IItemHandler sideInventory, int sideInventorySlotCount, StationGroup stationGroup, boolean forge) {
        super(ForgeweaveMenus.TOOL_STATION.get(), containerId, stationGroup);
        checkContainerSize(container, CONTAINER_SLOTS);
        this.container = container;
        this.access = access;
        this.forge = forge;
        this.registries = playerInventory.player.level().registryAccess();
        this.sideInventorySlotCount = sideInventorySlotCount;
        this.owner = playerInventory.player;
        container.startOpen(playerInventory.player);

        addDataSlot(selectedTab);
        selectedTab.set(ToolStationTabs.REPAIR);
        // Upstream ContainerToolStation#syncWithOtherContainer: a station someone else already has
        // open hands the newcomer its typed name and tool selection, so both players work the one
        // shared output slot from the same state.
        peers().stream().findFirst().ifPresent(peer -> {
            this.toolName = peer.toolName;
            selectedTab.set(peer.selectedTab.get());
        });

        for (int i = 0; i < INPUT_SLOTS; i++) {
            Pos pos = position(tab(), i);
            addSlot(inputSlot(i, pos.x(), pos.y()));
        }
        addSlot(new OutputSlot(container, OUTPUT_SLOT, OUTPUT_X, OUTPUT_Y));

        this.sideSlots = SideInventorySlots.create(sideInventory, sideInventorySlotCount, SIDE_PANEL_X, SIDE_PANEL_Y);
        this.sideSlots.forEach(this::addSlot);
        layoutPlayerInventorySlots(playerInventory);
    }

    /**
     * The player's inventory sits 8px lower than a stock 166px-tall GUI's, because the Tool Station
     * panel is upstream's full 176x174 one (issue #47 restored the whole panel, chrome included,
     * where issue #43 had used a 176x166 crop of it).
     */
    private void layoutPlayerInventorySlots(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 92 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 150));
        }
    }

    private Slot inputSlot(int index, int x, int y) {
        return new Slot(container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return accepts(index, stack);
            }

            /**
             * Upstream's {@code activeSlots}: a build tab has no use for the repair tab's extra
             * reagent slots, so they are neither drawn nor clickable while one is selected. Nothing
             * can be stranded behind this -- {@link #returnUnusableInputs} hands back whatever a
             * slot held before the tab that hides it was selected.
             */
            @Override
            public boolean isActive() {
                return index < tab().slots().size();
            }
        };
    }

    /**
     * Where slot {@code index} sits on {@code tab}. Slots the tab doesn't use borrow its last
     * position; they are never drawn ({@code Slot#isActive}), so the value only has to be sane.
     */
    private static Pos position(Tab tab, int index) {
        List<Pos> positions = tab.slots();
        return positions.get(Math.min(index, positions.size() - 1));
    }

    /** What the currently selected tab lets the player put in slot {@code index}. */
    private boolean accepts(int index, ItemStack stack) {
        Tab tab = tab();
        if (tab.isRepair()) {
            // The repair tab is also the modify and emboss tab (issues #105, #154): its free slots
            // take the loaded tool's repair item, any modifier recipe's reagent, or an embossment's
            // donor part and reagents.
            return index == HEAD_SLOT
                    ? stack.getItem() instanceof ToolItem
                    : ToolAssemblyRecipes.isRepairItemFor(registries, container.getItem(HEAD_SLOT), stack)
                            || ModifierApplication.isReagent(registries, stack)
                            || Embossing.isReagent(registries, stack)
                            // #271: the sharpening kit and its flint. The kit is no longer an
                            // embossing donor (Embossing#isDonorPart -- it belongs to no tool), and
                            // the flint's recipe is skipped by ModifierApplication#recipeFor, so
                            // neither of the two clauses above would let it in.
                            || Fortification.isReagent(registries, stack);
        }
        // Since issue #155 the per-slot filter is the tab's own entry: M3's swords each take a
        // different guard as their extra part, and three of its weapons have no extra part at all,
        // in which case the surplus slots are inactive and accept nothing.
        return index < tab.slots().size() && stack.is(tab.part(index));
    }

    /** Whether this menu belongs to a Tool Forge; the screen reads it to pick its metal styling. */
    public boolean isForge() {
        return forge;
    }

    /**
     * The tab indices this block offers (issue #336) -- {@link ToolStationTabs#visible} for this
     * menu's block. The screen draws one sidebar button per entry, and {@link #clickMenuButton}
     * accepts nothing outside it.
     */
    public List<Integer> visibleTabs() {
        return ToolStationTabs.visible(forge);
    }

    /**
     * Why the loaded slots produce nothing, or {@code null} when there is nothing to say. Read by
     * the screen for the info panel: both answers below are resolved from synced data (the modifier
     * recipes are a datapack registry, the large-tool classification an item tag), so the client
     * reaches the same conclusion the server did and no extra payload is needed. The server stays
     * authoritative regardless -- it simply produces no output.
     *
     * <p>The large-tool refusal comes first because it is the harder stop: a large tool cannot be
     * built here at all, whereas a modifier rejection is about the particular reagents loaded.
     *
     * <p>Every answer below is upstream's <em>error</em> class ({@link Rejection#error}): each one
     * is a craft that was attempted and refused, which is what upstream throws as a
     * {@code TinkerGuiException} and {@code ContainerToolStation} hands to {@code error(...)}. The
     * one warning is {@link #wrongMaterialPart}, derived from the slots rather than from a failed
     * attempt -- see {@link Rejection}.
     */
    @Nullable
    public Rejection rejection() {
        ItemStack tool = slots.get(HEAD_SLOT).getItem();
        if (!forge && ToolAssemblyRecipes.isLargeToolHead(inputSlots())) {
            return Rejection.error(Component.translatable("gui.forgeweave.tool_station.needs_forge"));
        }
        // Content-family toggles ticket: a part that serves only families the server has switched
        // off. Sits with the large-tool refusal because it is the same kind of hard stop -- nothing
        // loaded into these slots can ever assemble here. Build loadouts only: an assembled tool in
        // the head slot is being repaired, modified or embossed, and an existing tool of an off
        // family keeps every one of those.
        if (!(tool.getItem() instanceof ToolItem)
                && inputSlots().stream().anyMatch(stack -> !ContentFamilies.itemEnabled(stack))) {
            return Rejection.error(ContentFamilies.disabledMessage());
        }
        // #378, upstream GuiToolStation:296-301: a part of the right shape whose material this world
        // has no definition for. Sits here because it is the same kind of hard stop as the one above
        // -- ToolAssemblyRecipes#assemble bails on exactly this and says nothing, so before #378 the
        // output slot simply stayed empty with the components panel showing every part satisfied.
        Component wrongMaterial = wrongMaterialPart();
        if (wrongMaterial != null) {
            return Rejection.warning(wrongMaterial);
        }
        // #264: a part exchange refused for shape, material or durability explains itself. Checked
        // before embossing because an exchange loadout (tool + parts only) is never an embossing one.
        Component exchange = ToolAssemblyRecipes.resolveExchange(registries, tool, freeSlotContents(), forge)
                .map(ToolAssemblyRecipes.Exchange::rejection)
                .orElse(null);
        if (exchange != null) {
            return Rejection.error(exchange);
        }
        Component embossing = Embossing.resolve(registries, tool, freeSlotContents())
                .map(Embossing.Outcome::rejection)
                .orElse(null);
        if (embossing != null) {
            // #154: "already embossed" outranks anything the reagents also mean.
            return Rejection.error(embossing);
        }
        // #271: same position and same reason as embossing above -- a fortification loadout is never
        // a generic modifier one, and "already fortified with this material" outranks anything the
        // flint would otherwise be read as. Mirrors resolve()'s own ordering, which is what keeps the
        // message and the missing output explaining the same thing.
        Component fortification = Fortification.resolve(registries, tool, freeSlotContents())
                .map(Fortification.Outcome::rejection)
                .orElse(null);
        if (fortification != null) {
            return Rejection.error(fortification);
        }
        return ModifierApplication.resolve(registries, tool, freeSlotContents())
                .map(ModifierApplication.Outcome::rejection)
                .map(Rejection::error)
                .orElse(null);
    }

    /**
     * Upstream {@code GuiToolStation:296-301}: on a build tab, a slot holding the part that slot
     * wants but made of a material no loaded datapack defines. That is precisely the case
     * {@code ToolAssemblyRecipes#assemble} gives up on (its {@code lookupMaterial} comes back empty,
     * as does {@code resolveAssembly} for a part carrying no material component at all), so without
     * this the station refuses silently.
     *
     * <p>Only build tabs, as upstream: the repair tab's free slots take reagents and embossing
     * donors, whose own resolvers already explain themselves.
     */
    @Nullable
    private Component wrongMaterialPart() {
        Tab tab = tab();
        if (tab.isRepair()) {
            return null;
        }
        for (int i = 0; i < tab.slots().size(); i++) {
            ItemStack stack = slots.get(i).getItem();
            // "Right part, wrong material" is upstream's exact shape (`pmt.isValidItem` passing while
            // `pmt.isValid` fails); anything else in the slot is the components list's business.
            if (stack.is(tab.part(i)) && PartItem.hasUnusableMaterial(registries, stack)) {
                return Component.translatable("gui.forgeweave.tool_station.wrong_material_part");
            }
        }
        return null;
    }

    /** Every input slot, in slot order -- what the large-tool refusal looks at (issue #157). */
    private List<ItemStack> inputSlots() {
        List<ItemStack> stacks = new ArrayList<>(INPUT_SLOTS);
        for (int i = 0; i < INPUT_SLOTS; i++) {
            stacks.add(slots.get(i).getItem());
        }
        return stacks;
    }

    /** Every input slot except the tool's, in slot order -- what an embossment is matched against. */
    private List<ItemStack> freeSlotContents() {
        List<ItemStack> stacks = new ArrayList<>(INPUT_SLOTS - 1);
        for (int i = HEAD_SLOT + 1; i < INPUT_SLOTS; i++) {
            stacks.add(slots.get(i).getItem());
        }
        return stacks;
    }

    /** The selected tab; the screen reads it to pick the sidebar highlight, icons and info text. */
    public Tab tab() {
        return ToolStationTabs.get(selectedTab.get());
    }

    public int getSelectedTab() {
        return selectedTab.get();
    }

    /** The current contents of the rename field, so the screen can restore it on reopen. */
    public String getToolName() {
        return toolName;
    }

    /**
     * Renames the assembled output, like a vanilla anvil: the name is applied to the freshly built
     * stack in {@link #updateResult}, so it travels to the client on the ordinary slot-sync packet
     * and there is nothing extra to keep consistent. Reached from the screen's text field over
     * {@link RenameStationItemPayload}, and validated here because that payload is player input.
     */
    public void setToolName(String name) {
        String filtered = StringUtil.filterText(name);
        String capped = filtered.length() > MAX_NAME_LENGTH ? filtered.substring(0, MAX_NAME_LENGTH) : filtered;
        if (capped.equals(toolName)) {
            return;
        }
        this.toolName = capped;
        // No echo back to whoever typed it: their field already reads this, and a round trip would
        // fight a fast typist's cursor. Upstream ToolStationTextPacket does echo to the sender.
        this.pushedToolName = capped;
        broadcastChanges();
        // Upstream ToolStationTextPacket#handleServerSafe: the typed text goes back out to everyone
        // else at this station. Their own broadcast pushes it on, seeing the name it last sent go
        // stale.
        for (ToolStationMenu peer : peers()) {
            peer.toolName = capped;
            peer.broadcastChanges();
        }
    }

    /**
     * Sends the rename field's text down to this menu's own player when it has changed behind their
     * back -- a peer typing ({@link #setToolName}), or this menu seeding itself from a peer when it
     * opened. Everything else about a rename already rides the shared output slot.
     *
     * <p>The channel check is {@code StationMenuHost#open}'s, for its reason (issue #101): a
     * GameTest's mock {@code ServerPlayer} has a connection that never negotiated the mod's payload
     * channel, and sending down it throws rather than degrading.
     */
    private void pushToolName() {
        if (pushedToolName.equals(toolName)) {
            return;
        }
        pushedToolName = toolName;
        if (owner instanceof ServerPlayer serverPlayer
                && NetworkRegistry.hasChannel(serverPlayer.connection, RenameStationItemPayload.TYPE.id())) {
            PacketDistributor.sendToPlayer(serverPlayer, new RenameStationItemPayload(containerId, toolName));
        }
    }

    /**
     * Every other menu open on this same station -- upstream's {@code BaseContainer#sameGui}, which
     * compares the tile. Comparing the {@link Container} instance is the same test: a station's menus
     * are all built over the one inventory its block entity owns, and it is the only thing the client
     * mirror does <em>not</em> share, so this is empty client-side and no side check is needed.
     */
    private List<ToolStationMenu> peers() {
        return access.evaluate((level, pos) -> level.players().stream()
                        .map(player -> player.containerMenu)
                        .filter(menu -> menu != this && menu instanceof ToolStationMenu peer && peer.container == container)
                        .map(ToolStationMenu.class::cast)
                        .toList())
                .orElse(List.of());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (super.clickMenuButton(player, id)) {
            return true; // a station-group tab (issue #78), handled by StationMenu
        }
        // #336: a Tool Station refuses the Tool Forge tier's tabs outright, not just by not drawing
        // their buttons -- the id arrives over the wire, so the sidebar is not the gate.
        if (!visibleTabs().contains(id)) {
            return false;
        }
        selectedTab.set(id);
        returnUnusableInputs(player);
        applyLayout();
        updateResult();
        // Upstream ToolStationSelectionPacket / ContainerToolStation#syncWithOtherContainer: the tool
        // selection is station state, not per-player -- everyone standing here works one set of
        // slots, so they have to be looking at the same layout. The peers' own DataSlot update rides
        // their next broadcast; their inputs are the same container, already returned above.
        for (ToolStationMenu peer : peers()) {
            peer.selectedTab.set(id);
            peer.applyLayout();
        }
        return true;
    }

    /**
     * Hands back whatever sits in a slot the newly selected tab doesn't have (issue #154, which gave
     * the repair tab two slots a build tab lacks). A tab switch still leaves the slots both tabs
     * share alone -- ejecting those on a button press is how a menu loses a player's stack -- but a
     * slot that is about to become invisible has to give its contents back or they are gone.
     */
    private void returnUnusableInputs(Player player) {
        if (access == ContainerLevelAccess.NULL) {
            return; // client mirror: the server moves the items and the result syncs back down.
        }
        for (int i = tab().slots().size(); i < INPUT_SLOTS; i++) {
            ItemStack stranded = container.getItem(i);
            if (stranded.isEmpty()) {
                continue;
            }
            container.setItem(i, ItemStack.EMPTY);
            if (!player.getInventory().add(stranded)) {
                player.drop(stranded, false);
            }
        }
    }

    /** Client side: the tab arrives as a data-slot update, and the layout follows it. */
    @Override
    public void setData(int id, int data) {
        super.setData(id, data);
        applyLayout();
    }

    /** Rebuilds the input slots at the selected tab's coordinates (see the class javadoc). */
    private void applyLayout() {
        Tab tab = tab();
        for (int i = 0; i < INPUT_SLOTS; i++) {
            Pos pos = position(tab, i);
            Slot slot = inputSlot(i, pos.x(), pos.y());
            slot.index = i;
            slots.set(i, slot);
        }
    }

    @Override
    public void broadcastChanges() {
        updateResult();
        super.broadcastChanges();
        pushToolName();
    }

    private void updateResult() {
        if (access == ContainerLevelAccess.NULL) {
            return; // client: the server pushes slot contents down instead of computing locally.
        }
        ItemStack output = resolve().map(ToolAssemblyRecipes.Result::output).orElse(ItemStack.EMPTY);
        if (!output.isEmpty() && !toolName.isBlank()) {
            output.set(DataComponents.CUSTOM_NAME, Component.literal(toolName));
        }
        slots.get(OUTPUT_SLOT).set(output);
    }

    private Optional<ToolAssemblyRecipes.Result> resolve() {
        ItemStack head = slots.get(HEAD_SLOT).getItem();
        return ToolAssemblyRecipes.resolve(registries, head, freeSlotContents(), forge)
                .or(() -> renameOnly(head));
    }

    /**
     * Upstream {@code ContainerToolStation#renameTool}: renaming is a recipe in its own right, last
     * in the chain before assembly, so a tool sitting alone in the head slot with a name typed in the
     * field produces a renamed copy at the cost of that one tool. {@link #updateResult} stamps the
     * name on, exactly as it does for every other recipe's output.
     *
     * <p>Lives here rather than in {@link ToolAssemblyRecipes} because the typed name is menu state,
     * not slot state -- the only input of any recipe here that does not come out of the container.
     *
     * <p>Two guards, both upstream's. The name must differ from what the tool already shows, or
     * every loaded tool would sit behind a pointless output; and nothing may have been refused,
     * because upstream never reaches {@code renameTool} when {@code modifyTool} and friends threw --
     * without this a rejected modifier loadout would quietly hand back a rename instead of the
     * refusal {@link #rejection} is showing.
     */
    private Optional<ToolAssemblyRecipes.Result> renameOnly(ItemStack head) {
        if (toolName.isBlank()
                || !(head.getItem() instanceof ToolItem)
                || head.getHoverName().getString().equals(toolName)
                || rejection() != null) {
            return Optional.empty();
        }
        return Optional.of(ToolAssemblyRecipes.Result.of(head.copy(), 1));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stackInSlot = slot.getItem();
        ItemStack result = stackInSlot.copy();
        int sideEnd = CONTAINER_SLOTS + sideInventorySlotCount;
        int playerInvEnd = sideEnd + 36;

        if (index < CONTAINER_SLOTS) { // head/binding/handle/output -> player inventory
            if (!moveItemStackTo(stackInSlot, CONTAINER_SLOTS, playerInvEnd, true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < sideEnd) { // side inventory -> player inventory
            if (!moveItemStackTo(stackInSlot, sideEnd, playerInvEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stackInSlot, HEAD_SLOT, INPUT_SLOTS, false)) {
            // The per-slot filters live in mayPlace, which moveItemStackTo already consults, so the
            // selected tab decides where (and whether) a shift-clicked stack lands.
            return ItemStack.EMPTY;
        }

        if (stackInSlot.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        if (stackInSlot.getCount() == result.getCount()) {
            return ItemStack.EMPTY;
        }
        slot.onTake(player, stackInSlot);
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        // The flag came from the block entity at exactly this position, so naming one block is enough
        // -- there is no state in which a forge menu is open over a station block or vice versa.
        return stillValid(access, player,
                forge ? ForgeweaveBlocks.TOOL_FORGE.get() : ForgeweaveBlocks.TOOL_STATION.get());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private final class OutputSlot extends Slot {
        OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            // Recomputed rather than remembered from updateResult(): the inputs haven't changed
            // since the output was built, and a stateless read can't go stale.
            if (player instanceof ServerPlayer serverPlayer) {
                grantAdvancements(serverPlayer);
            }
            resolve().ifPresent(result -> {
                List<Integer> used = result.slotsUsed();
                for (int i = 0; i < used.size(); i++) {
                    consume(i, used.get(i));
                }
            });
            playCraftSound(player);
            super.onTake(player, stack);
        }

        /**
         * Upstream's {@code playCraftSound}, at the same point in {@code onTakeOutput}: after the
         * inputs are consumed, once per take. Server side only -- {@code Level#playSound} with a null
         * excluded player broadcasts to everyone in range, so letting the client mirror run it too
         * would double the cue for whoever took the tool. See {@link #CRAFT_SOUND}.
         */
        private void playCraftSound(Player player) {
            if (player.level().isClientSide) {
                return;
            }
            RandomSource random = player.level().getRandom();
            player.level().playSound(null, player.blockPosition(),
                    forge ? FORGE_CRAFT_SOUND : CRAFT_SOUND, SoundSource.PLAYERS,
                    forge ? FORGE_CRAFT_VOLUME : CRAFT_VOLUME,
                    forge ? FORGE_CRAFT_PITCH_BASE + FORGE_CRAFT_PITCH_SPREAD * random.nextFloat()
                            : CRAFT_PITCH_BASE + CRAFT_PITCH_SPREAD * random.nextFloat());
        }

        /**
         * The M2/M3 advancement chain's four hooks that fire from this station (docs/SCOPE.md issues
         * #110, #166) -- forge and large tool are the chain's other two steps, but "forge" needs no
         * hook (owning a Tool Forge, same {@code hasItems} idiom {@code ForgeweaveAdvancementProvider}'s
         * root uses) and "large tool" is the assembly branch below. Every check here re-resolves from
         * the current slots rather than remembering a result, same reasoning as this class's other
         * rejection/result reads.
         */
        private void grantAdvancements(ServerPlayer player) {
            ItemStack head = slots.get(HEAD_SLOT).getItem();

            // #110 -- "first modifier"; #166 -- "combat modifier", alongside it. Only when
            // ModifierApplication actually resolves an application from the current slots, the same
            // check rejection() uses to decide whether there's a rejection to report at all.
            if (ModifierApplication.resolve(registries, head, freeSlotContents())
                    .map(ModifierApplication.Outcome::output)
                    .filter(output -> !output.isEmpty())
                    .isPresent()) {
                ForgeweaveCriteriaTriggers.FIRST_MODIFIER.get().trigger(player);
                // Every free slot, not first-match: since issue #340 a craft can land several
                // modifiers at once, and a combat one in any slot still counts.
                if (freeSlotContents().stream()
                        .flatMap(stack -> ModifierApplication.recipeFor(registries, stack).stream())
                        .map(ModifierRecipe::modifier)
                        .anyMatch(ForgeweaveModifiers::isCombatModifier)) {
                    ForgeweaveCriteriaTriggers.COMBAT_MODIFIER_APPLIED.get().trigger(player);
                }
            }

            // #166 -- "emboss": Embossing#resolve is the same check rejection() uses.
            if (Embossing.resolve(registries, head, freeSlotContents())
                    .map(Embossing.Outcome::output)
                    .filter(output -> !output.isEmpty())
                    .isPresent()) {
                ForgeweaveCriteriaTriggers.FIRST_EMBOSSMENT.get().trigger(player);
            }

            // #166 -- "large tool": only on a fresh assembly -- the head slot holding a part rather
            // than an already-assembled tool, ToolAssemblyRecipes#resolve's own assembly-vs-repair
            // discriminator -- and only when the resolved tool is one ToolAssemblyRecipes#LARGE_TOOLS
            // gates to the Tool Forge.
            if (!(head.getItem() instanceof ToolItem)) {
                resolve().map(ToolAssemblyRecipes.Result::output)
                        .flatMap(ToolAssemblyRecipes::entryFor)
                        .filter(ToolAssemblyRecipes::isLargeTool)
                        .ifPresent(entry -> ForgeweaveCriteriaTriggers.LARGE_TOOL_ASSEMBLED.get().trigger(player));
            }
        }

        private void consume(int slotIndex, int count) {
            if (count > 0) {
                slots.get(slotIndex).remove(count);
                slots.get(slotIndex).setChanged();
            }
        }
    }
}
