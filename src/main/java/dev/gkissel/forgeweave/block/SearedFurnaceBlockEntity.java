package dev.gkissel.forgeweave.block;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.menu.SearedFurnaceMenu;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * The seared furnace controller's block entity (issue #442), ported from upstream 1.12's {@code
 * TileSearedFurnace} over {@code TileHeatingStructure}/{@code TileHeatingStructureFuelTank}
 * (NOTICE.md): a furnace-recipe cooker whose inventory grows with its structure and whose heat
 * comes from lava in the structure's seared tanks.
 *
 * <ul>
 *   <li><b>Inventory:</b> {@code 9 + 3 * width * height * depth} slots of up to {@value
 *       #MAX_STACK} items each ({@code getUpdatedInventorySize}, {@code super(name, 0, 16)}). Any
 *       item may be put in; only what a vanilla furnace recipe accepts is ever heated.
 *   <li><b>Heat cost:</b> {@code 200 * count / 4}, times {@code 0.8} for food ({@code
 *       getHeatForStack}) -- a quarter of vanilla's per-item time, per stack, so a full 16-stack
 *       cooks in 800 heat where a vanilla furnace would spend 3200 ticks on it. A stack whose result
 *       would exceed the item's own max stack size or {@value #MAX_STACK} is marked as having no
 *       space ({@code itemTemperatures = -1}) and never heats.
 *   <li><b>Heating:</b> every {@value #HEAT_INTERVAL_TICKS} ticks, each slot that has fuel and enough
 *       heat gains {@code temperature / 100} ({@code heatSlot}) towards its cost times {@value
 *       #TIME_FACTOR}; a step that heated anything costs one burn tick ({@code fuel--}).
 *   <li><b>Fuel:</b> the structure's tanks, preferring the tank and fuel last burned ({@code
 *       searchForFuel}); one {@link SmelteryFuel#amount()} buys {@link SmelteryFuel#duration()} burn
 *       ticks at the fuel's temperature less 300 ({@code addFuel}, upstream's "convert to degree
 *       celcius").
 *   <li><b>Interior:</b> once a second, hostile mobs standing inside are killed outright ({@code
 *       interactWithEntitiesInside}, {@code entity.setDead()}).
 * </ul>
 *
 * <p>Ticking follows {@link SmelteryControllerBlockEntity} rather than upstream: no {@code
 * BlockEntityTicker}, a scheduled block tick every {@value #HEAT_INTERVAL_TICKS} ticks while
 * something is heating and every {@value #SWEEP_INTERVAL_TICKS} while merely formed, and
 * revalidation-on-read for the structure ({@link #structure()}).
 */
public class SearedFurnaceBlockEntity extends BlockEntity implements StationMenuHost, TankOwner {
    /** Upstream's {@code TileHeatingStructure.TIME_FACTOR}: heat costs are stored at this resolution. */
    public static final int TIME_FACTOR = 8;
    /** Upstream {@code TileSearedFurnace}'s {@code maxStackSize} of 16. */
    public static final int MAX_STACK = 16;
    /** Upstream {@code getHeatForStack}'s base of 200 (vanilla's cook time), spread over a stack of four. */
    static final int BASE_HEAT = 200;
    /** Upstream's {@code tick % 4 == 0} heating cadence. */
    public static final int HEAT_INTERVAL_TICKS = 4;
    /** Upstream's once-a-second {@code interactWithEntitiesInside} (and unformed structure re-check). */
    public static final int SWEEP_INTERVAL_TICKS = 20;

    /**
     * How stale {@link #structure()} may be before it rescans.
     *
     * <p>Package-private rather than {@code private}: {@link SearedFurnaceControllerBlock#tick}
     * reuses it as the settle-window poll cadence (#772, mirroring {@link
     * SmelteryControllerBlockEntity#RESCAN_INTERVAL_TICKS}), so a rescan is always at least this
     * stale by the time that tick fires and {@link #structure()}'s own staleness check does the
     * actual work.
     */
    static final int RESCAN_INTERVAL_TICKS = 20;

    /**
     * How long an unformed furnace keeps rechecking itself after a placement or a neighbour change
     * touches it directly, in ticks (#772 -- the same visual-sync gap #757 fixed for the smeltery
     * core: {@link SearedFurnaceControllerBlock#onPlace}/{@code neighborChanged} only reach a block
     * adjacent to the controller itself, so finishing a wall a few blocks further out never notifies
     * it directly). See {@link #armSettleWindow()}; same magnitude as {@link
     * SmelteryControllerBlockEntity#SETTLE_WINDOW_TICKS}.
     */
    private static final int SETTLE_WINDOW_TICKS = 100;

    private static final long NEVER = Long.MIN_VALUE;

    private static final String TAG_STRUCTURE = "structure";
    private static final String TAG_TANKS = "tanks";
    private static final String TAG_ITEMS = "items";
    private static final String TAG_ITEM_TEMPERATURES = "item_temperatures";
    private static final String TAG_ITEM_TEMP_REQUIRED = "item_temp_required";
    private static final String TAG_FUEL = "fuel";
    private static final String TAG_FUEL_QUALITY = "fuel_quality";
    private static final String TAG_TEMPERATURE = "temperature";
    private static final String TAG_CURRENT_TANK = "current_tank";
    private static final String TAG_CURRENT_FUEL = "current_fuel";
    private static final String TAG_FUEL_DISPLAY = "fuel_display";

    /** Why a slot's bar looks the way it does -- upstream {@code getHeatingProgress}'s float encoding, spelled out. */
    public enum Progress {
        /** Empty slot: no bar. */
        NONE,
        /** {@code NaN}: the item has no furnace recipe. */
        NO_RECIPE,
        /** {@code +Infinity}: the item finished cooking. */
        COMPLETE,
        /** {@code -Infinity}: the result would not fit the slot. */
        NO_SPACE,
        /** {@code getFuel() == 0}: nothing is burning. */
        NO_FUEL,
        /** {@code -1}: the furnace is not hot enough for this stack. */
        NO_HEAT,
        /** {@code 0..1}: cooking normally. */
        HEATING
    }

    @Nullable
    private SmelteryStructure structure;
    private Component lastResult = Component.translatable(SearedFurnaceScan.KEY_NOT_SCANNED);
    private long lastScanTick = NEVER;
    /** #772: end of the current settle window, or {@link #NEVER} if none is open. See {@link #armSettleWindow()}. */
    private long settleUntilTick = NEVER;
    private List<BlockPos> tanks = new ArrayList<>();

    private NonNullList<ItemStack> items = NonNullList.withSize(0, ItemStack.EMPTY);
    /** Current heat of each slot; {@code -1} is upstream's "result would not fit" marker. */
    private int[] itemTemperatures = new int[0];
    /** Heat each slot needs, already times {@link #TIME_FACTOR}; {@code 0} when nothing to cook. */
    private int[] itemTempRequired = new int[0];
    /**
     * What each slot held when its heat cost was last worked out. Vanilla's shift-click merge grows
     * a slot's stack in place and only calls {@code Slot#setChanged}, never {@code setItem}, so
     * {@link FurnaceContainer#setChanged} diffs against this to find the slot upstream's
     * {@code onSlotChanged} would have re-evaluated.
     */
    private ItemStack[] evaluated = new ItemStack[0];

    /** Burn ticks left, upstream {@code fuel}. */
    private int fuel;
    /** Burn ticks the last consumed fuel bought, upstream {@code fuelQuality}; the flame gauge is {@code fuel / fuelQuality}. */
    private int fuelQuality;
    /** The last burned fuel's own temperature (the {@link SmelteryFuel} scale, 300 = ambient); the working heat is this less 300. */
    private int fuelTemperature;
    @Nullable
    private BlockPos currentTank;
    private FluidStack currentFuel = FluidStack.EMPTY;
    private FluidStack fuelDisplayFluid = FluidStack.EMPTY;

    private long lastSweepTick = NEVER;

    public SearedFurnaceBlockEntity(BlockPos pos, BlockState state) {
        super(ForgeweaveBlockEntities.SEARED_FURNACE.get(), pos, state);
    }

    // ------------------------------------------------------------------ structure

    public Component lastResult() {
        return lastResult;
    }

    /** The formed structure, or {@code null}; rescans a stale answer on the server (see {@link SmelteryControllerBlockEntity#structure()}). */
    @Nullable
    public SmelteryStructure structure() {
        if (level != null && !level.isClientSide
                && (lastScanTick == NEVER || level.getGameTime() - lastScanTick >= RESCAN_INTERVAL_TICKS)) {
            updateStructure();
        }
        return structure;
    }

    @Override
    public boolean isFormed() {
        return structure() != null;
    }

    /** Rescans now. Server-side only. */
    public void updateStructure() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SearedFurnaceControllerBlock)) {
            return;
        }
        lastScanTick = level.getGameTime();
        // #288 / upstream checkIfMultiblockCanBeRechecked: never fail a formed structure over unloaded chunks.
        SmelteryStructure current = structure;
        if (current != null && !level.hasChunksAt(current.interiorMin().offset(-1, -1, -1), current.interiorMax().offset(1, 1, 1))) {
            return;
        }

        SearedFurnaceScan.Result result = SearedFurnaceScan.scan(level, worldPosition, state.getValue(SearedFurnaceControllerBlock.FACING));
        lastResult = result.message();
        SmelteryStructure found = result.structure();
        if (!Objects.equals(found, structure)) {
            structure = found;
            resizeInventory(found == null ? 0 : 9 + 3 * found.interiorVolume());
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        if (found != null) {
            tanks = new ArrayList<>(result.tanks());
            for (BlockPos pos : tanks) {
                if (level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank) {
                    tank.setCore(worldPosition);
                }
            }
            if (currentTank != null && !tanks.contains(currentTank)) {
                currentTank = null;
            }
            refreshFuelDisplay();
            armMeltTick();
        }
        if (state.getValue(SearedFurnaceControllerBlock.ACTIVE) != (found != null)) {
            level.setBlock(worldPosition, state.setValue(SearedFurnaceControllerBlock.ACTIVE, found != null), Block.UPDATE_ALL);
        }
    }

    /**
     * #772: opens (or extends) a bounded recheck window for an unformed furnace, and makes sure a
     * tick is actually scheduled to use it. Called after every {@link #updateStructure()} that
     * {@link SearedFurnaceControllerBlock#onPlace}/{@code neighborChanged} triggers -- those only
     * reach a block adjacent to the controller itself, so completing the structure a few blocks
     * further out would otherwise sit unnoticed until a player clicks it, exactly {@link
     * SmelteryControllerBlockEntity#armSettleWindow()}'s #757 gap. A no-op once formed: {@link
     * #armMeltTick} already keeps a formed furnace's own heartbeat alive.
     */
    void armSettleWindow() {
        if (level == null || level.isClientSide || isFormed()) {
            return;
        }
        settleUntilTick = level.getGameTime() + SETTLE_WINDOW_TICKS;
        Block block = getBlockState().getBlock();
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            level.scheduleTick(worldPosition, block, RESCAN_INTERVAL_TICKS);
        }
    }

    /** Whether {@link #armSettleWindow()}'s recheck window is still open. */
    boolean settling() {
        return level != null && settleUntilTick != NEVER && level.getGameTime() < settleUntilTick;
    }

    /** Upstream {@code updateStructureInfo}: resize, dropping what no longer fits out of the front. */
    private void resizeInventory(int size) {
        if (size == items.size()) {
            return;
        }
        NonNullList<ItemStack> resized = NonNullList.withSize(size, ItemStack.EMPTY);
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (slot < size) {
                resized.set(slot, stack);
            } else if (level != null && !level.isClientSide) {
                BlockPos drop = worldPosition.relative(getBlockState().getValue(SearedFurnaceControllerBlock.FACING));
                Containers.dropItemStack(level, drop.getX(), drop.getY(), drop.getZ(), stack);
            }
        }
        items = resized;
        itemTemperatures = Arrays.copyOf(itemTemperatures, size);
        itemTempRequired = Arrays.copyOf(itemTempRequired, size);
        evaluated = new ItemStack[size];
        for (int slot = 0; slot < size; slot++) {
            evaluated[slot] = items.get(slot).copy();
        }
    }

    // ------------------------------------------------------------------ heating

    /** Upstream {@code getHeatForStack}: {@code 200 * count / 4}, times 0.8 for food, before {@link #TIME_FACTOR}. */
    static int heatFor(int count, boolean food) {
        float heat = BASE_HEAT * count / 4f;
        if (food) {
            heat *= 0.8f;
        }
        return (int) heat;
    }

    /** Whether a result of {@code resultCount} fits, upstream's {@code newSize <= stack.getMaxStackSize() && newSize <= 16}. */
    static boolean resultFits(int inputCount, int resultCount, int inputMaxStackSize) {
        int newSize = inputCount * resultCount;
        return newSize <= inputMaxStackSize && newSize <= MAX_STACK;
    }

    /** The vanilla furnace result for {@code stack}, or empty. Empty too while the smeltery family is switched off. */
    private ItemStack smeltingResult(ItemStack stack) {
        if (level == null || stack.isEmpty() || !ForgeweaveConfig.enabled(ForgeweaveConfig.SMELTERY)) {
            return ItemStack.EMPTY;
        }
        return level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)
                .map(holder -> holder.value().getResultItem(level.registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    /** Upstream {@code updateHeatRequired}. */
    private void updateHeatRequired(int slot) {
        ItemStack stack = items.get(slot);
        evaluated[slot] = stack.copy();
        ItemStack result = smeltingResult(stack);
        if (!result.isEmpty()) {
            if (resultFits(stack.getCount(), result.getCount(), stack.getMaxStackSize())) {
                itemTempRequired[slot] = heatFor(stack.getCount(), result.has(DataComponents.FOOD)) * TIME_FACTOR;
            } else {
                itemTempRequired[slot] = 0;
                itemTemperatures[slot] = -1;
            }
            if (fuel <= 0) {
                consumeFuel();
            }
            return;
        }
        itemTempRequired[slot] = 0;
    }

    /** Upstream {@code canHeat}: the working heat reaches the slot's cost. */
    private boolean canHeat(int slot) {
        return workingHeat() >= itemTempRequired[slot] / TIME_FACTOR;
    }

    /** Upstream's {@code temperature}: the burning fuel's temperature less 300. */
    private int workingHeat() {
        return Math.max(0, fuelTemperature - MeltingRecipe.AMBIENT_TEMPERATURE);
    }

    private boolean hasHeatWork() {
        for (int slot = 0; slot < items.size(); slot++) {
            if (!items.get(slot).isEmpty() && itemTempRequired[slot] > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * One heat step, upstream {@code heatItems} -- called every {@value #HEAT_INTERVAL_TICKS} ticks
     * by {@link SearedFurnaceControllerBlock#tick}. Returns whether anything heated, i.e. whether
     * the caller should come back at the heat cadence rather than the sweep cadence.
     */
    public boolean heatTick() {
        if (level == null || level.isClientSide || structure() == null) {
            return false;
        }
        if (fuel <= 0 && hasHeatWork()) {
            consumeFuel();
        }
        if (fuel <= 0) {
            return false;
        }
        boolean heated = false;
        int step = workingHeat() / 100;
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (stack.isEmpty()) {
                itemTemperatures[slot] = 0;
                continue;
            }
            if (itemTempRequired[slot] <= 0 || !canHeat(slot)) {
                continue;
            }
            if (itemTemperatures[slot] >= itemTempRequired[slot]) {
                finishHeating(slot, stack);
            } else {
                itemTemperatures[slot] += step;
                heated = true;
            }
        }
        if (heated) {
            fuel--;
        }
        syncToClients();
        return heated;
    }

    /** Upstream {@code onItemFinishedHeating}: the whole stack becomes its results, marked complete. */
    private void finishHeating(int slot, ItemStack stack) {
        ItemStack result = smeltingResult(stack);
        if (result.isEmpty()) {
            return;
        }
        result = result.copy();
        result.setCount(result.getCount() * stack.getCount());
        items.set(slot, result);
        evaluated[slot] = result.copy();
        itemTemperatures[slot] = 1;
        itemTempRequired[slot] = 0;
        setChanged();
    }

    /** Upstream {@code interactWithEntitiesInside}: hostile mobs inside die, once a second. */
    public void sweepInterior() {
        if (level == null || level.isClientSide || structure == null) {
            return;
        }
        long now = level.getGameTime();
        if (lastSweepTick != NEVER && now - lastSweepTick < SWEEP_INTERVAL_TICKS) {
            return;
        }
        lastSweepTick = now;
        for (Monster monster : level.getEntitiesOfClass(Monster.class, structure.interiorBounds())) {
            if (monster.isAlive()) {
                monster.discard();
            }
        }
    }

    /** Puts this furnace on the block-tick list if it is not already: the heat cadence while cooking, the sweep cadence while formed. */
    @Override
    public void armMeltTick() {
        if (level == null || level.isClientSide) {
            return;
        }
        boolean heating = hasHeatWork();
        if (!heating && structure == null) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            level.scheduleTick(worldPosition, block, heating ? HEAT_INTERVAL_TICKS : SWEEP_INTERVAL_TICKS);
        }
    }

    // ------------------------------------------------------------------ fuel

    /** Upstream {@code getTankAt}: the tank block entity at {@code pos}, if it is one. */
    @Nullable
    private SearedTankBlockEntity tankAt(@Nullable BlockPos pos) {
        return pos != null && level != null && level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank ? tank : null;
    }

    private Optional<SmelteryFuel> fuelIn(@Nullable SearedTankBlockEntity tank) {
        if (tank == null || level == null || tank.tank().getFluidAmount() <= 0) {
            return Optional.empty();
        }
        return SmelteryFuel.find(level.registryAccess(), tank.tank().getFluid().getFluid());
    }

    /** Upstream {@code hasTankWithFuel}: fuel in the tank at {@code pos}, matching {@code preference} if one is given. */
    private boolean hasTankWithFuel(BlockPos pos, FluidStack preference) {
        SearedTankBlockEntity tank = tankAt(pos);
        if (tank == null || fuelIn(tank).isEmpty()) {
            return false;
        }
        return preference.isEmpty() || FluidStack.isSameFluidSameComponents(tank.tank().getFluid(), preference);
    }

    /** Upstream {@code searchForFuel}: keep the current tank while it has the current fuel, else the same fuel elsewhere, else any fuel. */
    private void searchForFuel() {
        if (currentTank != null && hasTankWithFuel(currentTank, currentFuel)) {
            return;
        }
        for (BlockPos pos : tanks) {
            if (hasTankWithFuel(pos, currentFuel)) {
                currentTank = pos;
                return;
            }
        }
        for (BlockPos pos : tanks) {
            if (hasTankWithFuel(pos, FluidStack.EMPTY)) {
                currentTank = pos;
                return;
            }
        }
        currentTank = null;
    }

    /** Upstream {@code consumeFuel}: one fuel unit out of the current tank buys its duration at its temperature. */
    private void consumeFuel() {
        if (fuel > 0 || level == null) {
            return;
        }
        searchForFuel();
        SearedTankBlockEntity tank = tankAt(currentTank);
        SmelteryFuel found = fuelIn(tank).orElse(null);
        if (tank == null || found == null) {
            fuelQuality = 0;
            return;
        }
        FluidStack drained = tank.tank().drain(found.amount(), IFluidHandler.FluidAction.SIMULATE);
        if (drained.getAmount() != found.amount()) {
            fuelQuality = 0;
            return;
        }
        tank.tank().drain(found.amount(), IFluidHandler.FluidAction.EXECUTE);
        currentFuel = drained.copy();
        fuelQuality = found.duration();
        fuel += found.duration();
        fuelTemperature = found.temperature();
        refreshFuelDisplay();
    }

    /** What the fuel gauge shows: the current tank's live contents, else whatever any tank holds -- same as the smeltery's gauge (#377). */
    private void refreshFuelDisplay() {
        FluidStack live = FluidStack.EMPTY;
        SearedTankBlockEntity current = tankAt(currentTank);
        if (current != null && current.tank().getFluidAmount() > 0) {
            live = current.tank().getFluid();
        } else {
            for (BlockPos pos : tanks) {
                SearedTankBlockEntity tank = tankAt(pos);
                if (tank != null && tank.tank().getFluidAmount() > 0) {
                    live = tank.tank().getFluid();
                    break;
                }
            }
        }
        if (!FluidStack.matches(live, fuelDisplayFluid)) {
            fuelDisplayFluid = live.copy();
            syncToClients();
        }
    }

    // ------------------------------------------------------------------ what the screen reads (client-safe)

    public int fuel() {
        return fuel;
    }

    public int fuelQuality() {
        return fuelQuality;
    }

    /** The burning fuel's temperature on the {@link SmelteryFuel} scale, or 0 while nothing burns. */
    public int fuelTemperatureForDisplay() {
        return fuel > 0 ? fuelTemperature : 0;
    }

    public FluidStack fuelDisplayFluid() {
        return fuelDisplayFluid;
    }

    /** Upstream {@code getFuelPercentage}, clamped: the flame's fill. */
    public float fuelPercentage() {
        return fuelQuality <= 0 ? 0f : Math.clamp((float) fuel / (float) fuelQuality, 0f, 1f);
    }

    /** Upstream {@code getHeatingProgress} decoded, see {@link Progress}. */
    public Progress progressState(int slot) {
        if (slot < 0 || slot >= items.size() || items.get(slot).isEmpty()) {
            return Progress.NONE;
        }
        if (!canHeat(slot)) {
            return fuel <= 0 ? Progress.NO_FUEL : Progress.NO_HEAT;
        }
        if (itemTempRequired[slot] == 0) {
            int temperature = itemTemperatures[slot];
            return temperature == 0 ? Progress.NO_RECIPE : temperature > 0 ? Progress.COMPLETE : Progress.NO_SPACE;
        }
        return fuel <= 0 ? Progress.NO_FUEL : Progress.HEATING;
    }

    /** How far along {@code slot} is, 0..1; full for the terminal states. */
    public float progress(int slot) {
        Progress state = progressState(slot);
        return switch (state) {
            case NONE -> 0f;
            case HEATING, NO_FUEL -> Math.clamp((float) itemTemperatures[slot] / (float) itemTempRequired[slot], 0f, 1f);
            default -> 1f;
        };
    }

    // ------------------------------------------------------------------ inventory

    /** The furnace's inventory as a vanilla {@link Container}, so {@link SearedFurnaceMenu} can put real slots over it. */
    public Container container() {
        return new FurnaceContainer();
    }

    public List<ItemStack> items() {
        return List.copyOf(items);
    }

    /** Upstream {@code setInventorySlotContents} + {@code SearedFurnaceSlot#onSlotChanged}. */
    private void setItem(int slot, ItemStack stack) {
        if (stack.isEmpty() || (!items.get(slot).isEmpty() && !ItemStack.matches(stack, items.get(slot)))) {
            itemTemperatures[slot] = 0;
        }
        items.set(slot, stack);
        updateHeatRequired(slot);
        syncToClients();
        armMeltTick();
    }

    private final class FurnaceContainer implements Container {
        @Override
        public int getContainerSize() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            return items.stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack removed = ContainerHelper.removeItem(items, slot, count);
            if (!removed.isEmpty()) {
                setItem(slot, items.get(slot));
            }
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack removed = getItem(slot);
            setItem(slot, ItemStack.EMPTY);
            return removed;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            SearedFurnaceBlockEntity.this.setItem(slot, stack);
        }

        @Override
        public int getMaxStackSize() {
            return MAX_STACK;
        }

        /** Re-evaluates any slot whose stack moved without a {@link #setItem} call (see {@link #evaluated}). */
        @Override
        public void setChanged() {
            boolean reevaluated = false;
            for (int slot = 0; slot < items.size(); slot++) {
                if (!ItemStack.matches(items.get(slot), evaluated[slot])) {
                    SearedFurnaceBlockEntity.this.setItem(slot, items.get(slot));
                    reevaluated = true;
                }
            }
            if (!reevaluated) {
                SearedFurnaceBlockEntity.this.setChanged();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return !isRemoved();
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < items.size(); slot++) {
                setItem(slot, ItemStack.EMPTY);
            }
        }
    }

    // ------------------------------------------------------------------ menu

    @Override
    public Component getDisplayName() {
        return Component.translatable("gui.forgeweave.seared_furnace.name");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SearedFurnaceMenu(containerId, playerInventory,
                ContainerLevelAccess.create(level, worldPosition), worldPosition, container());
    }

    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeVarInt(items.size());
    }

    // ------------------------------------------------------------------ persistence + sync

    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void onLoad() {
        super.onLoad();
        armMeltTick();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (structure != null) {
            SmelteryStructure.CODEC.encodeStart(NbtOps.INSTANCE, structure)
                    .resultOrPartial(error -> {})
                    .ifPresent(encoded -> tag.put(TAG_STRUCTURE, encoded));
        }
        BlockPos.CODEC.listOf().encodeStart(NbtOps.INSTANCE, tanks)
                .resultOrPartial(error -> {})
                .ifPresent(encoded -> tag.put(TAG_TANKS, encoded));
        tag.put(TAG_ITEMS, ContainerHelper.saveAllItems(new CompoundTag(), items, true, registries));
        tag.putIntArray(TAG_ITEM_TEMPERATURES, itemTemperatures);
        tag.putIntArray(TAG_ITEM_TEMP_REQUIRED, itemTempRequired);
        tag.putInt(TAG_FUEL, fuel);
        tag.putInt(TAG_FUEL_QUALITY, fuelQuality);
        tag.putInt(TAG_TEMPERATURE, fuelTemperature);
        if (currentTank != null) {
            tag.put(TAG_CURRENT_TANK, NbtUtils.writeBlockPos(currentTank));
        }
        tag.put(TAG_CURRENT_FUEL, currentFuel.saveOptional(registries));
        tag.put(TAG_FUEL_DISPLAY, fuelDisplayFluid.saveOptional(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        structure = tag.contains(TAG_STRUCTURE)
                ? SmelteryStructure.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_STRUCTURE)).resultOrPartial(error -> {}).orElse(null)
                : null;
        tanks = new ArrayList<>(tag.contains(TAG_TANKS)
                ? BlockPos.CODEC.listOf().parse(NbtOps.INSTANCE, tag.get(TAG_TANKS)).resultOrPartial(error -> {}).orElse(List.of())
                : List.of());
        resizeInventory(structure == null ? 0 : 9 + 3 * structure.interiorVolume());
        ContainerHelper.loadAllItems(tag.getCompound(TAG_ITEMS), items, registries);
        int[] temperatures = tag.getIntArray(TAG_ITEM_TEMPERATURES);
        System.arraycopy(temperatures, 0, itemTemperatures, 0, Math.min(temperatures.length, itemTemperatures.length));
        int[] required = tag.getIntArray(TAG_ITEM_TEMP_REQUIRED);
        System.arraycopy(required, 0, itemTempRequired, 0, Math.min(required.length, itemTempRequired.length));
        fuel = tag.getInt(TAG_FUEL);
        fuelQuality = tag.getInt(TAG_FUEL_QUALITY);
        fuelTemperature = tag.getInt(TAG_TEMPERATURE);
        currentTank = tag.contains(TAG_CURRENT_TANK) ? NbtUtils.readBlockPos(tag, TAG_CURRENT_TANK).orElse(null) : null;
        currentFuel = FluidStack.parseOptional(registries, tag.getCompound(TAG_CURRENT_FUEL));
        fuelDisplayFluid = FluidStack.parseOptional(registries, tag.getCompound(TAG_FUEL_DISPLAY));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
