package dev.gkissel.forgeweave.menu;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;

import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.item.ToolItem;

/**
 * The Tool Station's selectable tabs and the slot arrangement each one puts the three input slots
 * in -- upstream 1.12's {@code ToolBuildGuiInfo} plus the per-tool tables its client proxies
 * register ({@code tools/harvest/HarvestClientProxy#registerToolBuildInfo}) and the repair layout in
 * {@code GuiButtonRepair}'s static initializer (NOTICE.md). Coordinates are upstream's verbatim,
 * expressed there as offsets from the station's build-area origin {@code (33, 42)}:
 *
 * <pre>
 *   pickaxe  handle (33-18, 42+18)  head (33+20, 42-20)  binding (33, 42)
 *   shovel   handle (33, 42)        head (33+18, 42-18)  binding (33-20, 42+20)
 *   hatchet  handle (33-11, 42+11)  head (33-2,  42-20)  binding (33+18, 42-8)
 *   repair   tool   (33, 42)        item (33-18, 42+20)  item (33-22, 42-5)
 * </pre>
 *
 * <p>Upstream's layouts are a client-only registry, because there the layout is only ever pixel
 * positions; the same table lives here in {@code menu} because Forgeweave's tabs also decide what
 * each slot accepts ({@link ToolStationMenu#accepts}), which is a server-authoritative question.
 * Which tab is selected travels as a {@code DataSlot} set from
 * {@link ToolStationMenu#clickMenuButton} -- the vanilla stonecutter/loom mechanism the repository
 * already uses for the Stencil Table -- rather than upstream's bespoke
 * {@code ToolStationSelectionPacket}.
 *
 * <p>Every Forgeweave tool is a three-part tool and repair uses the same three slots (tool + up to
 * two repair items), so unlike upstream there is no per-tab slot <em>count</em>, only per-tab
 * positions and filters. That is why nothing here has to activate or deactivate slots.
 */
public final class ToolStationTabs {

    /** A slot's position inside the station's build area, in GUI pixels from the panel's top-left. */
    public record Pos(int x, int y) {}

    /**
     * One entry in the selection sidebar.
     *
     * @param tool the tool this tab builds, or {@code null} for the repair tab
     * @param headPart the head part the first slot accepts, or {@code null} for the repair tab
     *     (which accepts an assembled tool there instead)
     * @param slots where the head, binding and handle slots sit, in that order
     */
    public record Tab(@Nullable Supplier<? extends ToolItem> tool,
                      @Nullable Supplier<? extends PartItem> headPart,
                      List<Pos> slots) {

        public boolean isRepair() {
            return tool == null;
        }

        /** The caption the info panel and the button tooltip show for this tab. */
        public Component title() {
            return isRepair()
                    ? Component.translatable("gui.forgeweave.tool_station.repair")
                    : Component.translatable(tool.get().getDescriptionId());
        }

        /** The lang key of this tab's one-paragraph description, shown while nothing is loaded. */
        public String descriptionKey() {
            return isRepair()
                    ? "gui.forgeweave.tool_station.repair.description"
                    : tool.get().getDescriptionId() + ".description";
        }
    }

    private static final int ORIGIN_X = 33;
    private static final int ORIGIN_Y = 42;

    private static Pos at(int dx, int dy) {
        return new Pos(ORIGIN_X + dx, ORIGIN_Y + dy);
    }

    public static final List<Tab> TABS = List.of(
            new Tab(null, null, List.of(at(0, 0), at(-18, 20), at(-22, -5))),
            new Tab(ForgeweaveItems.TOOL_PICKAXE, ForgeweaveItems.PART_PICKAXE_HEAD,
                    List.of(at(20, -20), at(0, 0), at(-18, 18))),
            new Tab(ForgeweaveItems.TOOL_SHOVEL, ForgeweaveItems.PART_SHOVEL_HEAD,
                    List.of(at(18, -18), at(-20, 20), at(0, 0))),
            new Tab(ForgeweaveItems.TOOL_HATCHET, ForgeweaveItems.PART_AXE_HEAD,
                    List.of(at(-2, -20), at(18, -8), at(-11, 11))));

    /** The repair tab, which is what a freshly opened station shows (as upstream's does). */
    public static final int REPAIR = 0;

    public static Tab get(int index) {
        return TABS.get(Math.floorMod(index, TABS.size()));
    }

    private ToolStationTabs() {}
}
