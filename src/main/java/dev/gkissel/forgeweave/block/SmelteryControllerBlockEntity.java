package dev.gkissel.forgeweave.block;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

// #101: the smeltery GUI. The block entity is the menu's host, so the controller opens its own
// screen the same way every M1 station does.
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;

import dev.gkissel.forgeweave.menu.SmelteryMenu;

import dev.gkissel.forgeweave.advancement.ForgeweaveCriteriaTriggers;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;

/**
 * A smeltery core's structure state and molten-metal tank (docs/SCOPE.md M2 issue #95), ported from
 * upstream 1.12's {@code TileMultiblock}/{@code TileSmeltery} (NOTICE.md).
 *
 * <p><b>This block entity has no ticker at all</b> -- {@link SmelteryControllerBlock} never
 * registers one, so an idle smeltery costs literally nothing per tick, which is what the SCOPE.md M2
 * release gate ("spark profile confirms idle smeltery ~= zero tick") asks for. Upstream instead
 * ticks forever: once a second while unformed to look for a structure, and once every 15 seconds
 * plus a one-block-per-second interior sweep while formed. Forgeweave replaces the polling with:
 *
 * <ul>
 *   <li><b>Events for anything touching the core</b> -- placement, a neighbour changing, a player
 *       using it (see {@link SmelteryControllerBlock}).
 *   <li><b>Revalidation on read</b> for everything else. {@link #structure()} rescans when its answer
 *       is more than {@value #RESCAN_INTERVAL_TICKS} ticks old, so a wall broken on the far side of a
 *       9x9 is noticed the next time something asks -- and nothing asks while the smeltery is idle.
 * </ul>
 *
 * <p>Forgeweave has no equivalent of upstream's per-structure-block "servant" tile entities (issue
 * #93 ships the seared blocks as plain blocks), which is what lets upstream be notified of a distant
 * wall break directly. Revalidation-on-read costs one scan per second of active work instead, which
 * is still strictly less often than upstream's own 15-second full recheck.
 */
public class SmelteryControllerBlockEntity extends BlockEntity implements StationMenuHost {
    /**
     * Fluid capacity each interior block contributes, upstream's {@code CAPACITY_PER_BLOCK} of eight
     * ingots at 144 mB each.
     */
    public static final int CAPACITY_PER_BLOCK = 8 * 144;

    /**
     * How stale {@link #structure()} may be before it rescans.
     *
     * <p>ponytail: one second, matching upstream's unformed-poll rate and beating its 15-second
     * formed recheck. Drop it to 0 (rescan on every read) if a case turns up where a smeltery
     * working out of a broken structure for up to a second matters.
     */
    private static final int RESCAN_INTERVAL_TICKS = 20;

    private static final long NEVER_SCANNED = Long.MIN_VALUE;

    private static final String TAG_STRUCTURE = "structure";
    private static final String TAG_TANK = "tank";

    private final SmelteryCore core;
    /**
     * #101: was a single-fluid {@code FluidTank}. A smeltery holds a stack of different molten
     * metals whose order the player picks in the GUI; see {@link SmelteryTank}. Every content or
     * order change syncs to the client, which is where the GUI reads its fluid column from.
     */
    private final SmelteryTank tank = new SmelteryTank(CAPACITY_PER_BLOCK, this::onTankChanged);

    @Nullable
    private SmelteryStructure structure;
    private Component lastResult = Component.translatable(SmelteryScan.KEY_NOT_SCANNED);
    private long lastScanTick = NEVER_SCANNED;

    // #96 -- melting. One slot per interior block (upstream TileSmeltery's getUpdatedInventorySize),
    // one item per slot, each heating at its own pace towards its recipe's required temperature.
    private NonNullList<ItemStack> meltingItems = NonNullList.withSize(0, ItemStack.EMPTY);
    private int[] meltProgress = new int[0];
    private MeltingRecipe[] meltRecipes = new MeltingRecipe[0];
    @Nullable
    private BlockPos fuelTank;

    // #97 -- fuel. Upstream's own {@code fuel}/{@code temperature} fields on TileHeatingStructure:
    // how many melt ticks the current burn has left, and the temperature it is burning at.
    private int fuelBurnTicksRemaining;
    private int fuelTemperature;

    /**
     * #97 follow-up (PR #126's flagged gap): a synced snapshot of what the backing wall tank
     * currently holds, so the smeltery screen's fuel gauge has an actual fluid to draw rather than
     * always-empty. See {@link #refreshFuelDisplay()}.
     */
    private FluidStack fuelDisplayFluid = FluidStack.EMPTY;

    public SmelteryControllerBlockEntity(BlockPos pos, BlockState state, SmelteryCore core) {
        super(core.blockEntityType().get(), pos, state);
        this.core = core;
    }

    /** Which core tier this structure has; {@link #finishMelting} multiplies ore-class yields by {@link SmelteryCore#yieldMultiplier()} (#99). */
    public SmelteryCore core() {
        return core;
    }

    /** The molten-metal tank. Its capacity tracks the interior size; it is filled by melting (#96) and drained through a {@link SearedDrainBlock}. */
    public SmelteryTank tank() {
        return tank;
    }

    /** Why the last scan formed or failed, as a player-facing message. */
    public Component lastResult() {
        return lastResult;
    }

    /**
     * The formed structure, or {@code null} if this core has none. Rescans first when the cached
     * answer is stale (see the class javadoc); on the client this returns the last synced value
     * without scanning.
     */
    @Nullable
    public SmelteryStructure structure() {
        if (level != null && !level.isClientSide
                && (lastScanTick == NEVER_SCANNED || level.getGameTime() - lastScanTick >= RESCAN_INTERVAL_TICKS)) {
            updateStructure();
        }
        return structure;
    }

    public boolean isFormed() {
        return structure() != null;
    }

    /** Rescans now, regardless of how fresh the cached answer is. Server-side only. */
    public void updateStructure() {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SmelteryControllerBlock)) {
            return;
        }

        lastScanTick = level.getGameTime();
        SmelteryScan.Result result = SmelteryScan.scan(level, worldPosition, state.getValue(SmelteryControllerBlock.FACING));
        lastResult = result.message();

        SmelteryStructure found = result.structure();
        if (!Objects.equals(found, structure)) {
            structure = found;
            resizeTank();
            setChanged();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
        if (found != null) {
            assignDrains(result.drains());
            assignTanks(result.tanks());
            // #96: a scan is the one moment the core is guaranteed to hear about the world changing
            // around it, so it is also where melting that stopped for want of heat picks back up.
            armMeltTick();
            // #97 follow-up: a scan is also the ~once-a-second poll a menu's stillValid keeps alive
            // while a player has the screen open (see structure()'s rescan-on-read), so this is what
            // keeps the fuel gauge live even while the smeltery is idle and nothing else is ticking.
            refreshFuelDisplay();
        }
        if (state.getValue(SmelteryControllerBlock.ACTIVE) != (found != null)) {
            level.setBlock(worldPosition, state.setValue(SmelteryControllerBlock.ACTIVE, found != null), Block.UPDATE_ALL);
        }
    }

    /** Points every drain in the walls back at this core so it can serve the smeltery's fluids. */
    private void assignDrains(List<BlockPos> drains) {
        for (BlockPos pos : drains) {
            if (level != null && level.getBlockEntity(pos) instanceof SearedDrainBlockEntity drain) {
                drain.setCore(worldPosition);
            }
        }
    }

    /** Points every wall tank back at this core so a fuel pour can wake a melt stuck for want of heat (#97). */
    private void assignTanks(List<BlockPos> tanks) {
        for (BlockPos pos : tanks) {
            if (level != null && level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank) {
                tank.setCore(worldPosition);
            }
        }
    }

    /** Capacity follows the interior size; an interior that shrank spills nothing but caps what is held. */
    private void resizeTank() {
        // #101: no overflow trim here any more -- SmelteryTank#setCapacity does it internally, and
        // does it from the *top* of the melt, so the bottom (drain) fluid the player selected is the
        // last thing lost rather than the first thing truncated.
        tank.setCapacity(structure == null ? CAPACITY_PER_BLOCK : structure.interiorVolume() * CAPACITY_PER_BLOCK);
        // #96: the melting inventory tracks the same interior, and the cached fuel tank may no
        // longer be part of it.
        fuelTank = null;
        resizeMeltingInventory(structure == null ? 0 : structure.interiorVolume());
    }

    // ------------------------------------------------------------------ #96: melting

    /**
     * How often a melt tick runs, upstream's {@code tick % 4 == 0} in {@code TileSmeltery.update}.
     *
     * <p>Where upstream ticks the block entity forever and does nothing on 3 ticks out of 4,
     * Forgeweave schedules a block tick this far ahead only while there is something to melt (see
     * {@link #armMeltTick()}), so the SCOPE.md M2 budget of "idle smeltery ~= zero tick" holds
     * literally: an idle core is not on any tick list at all.
     */
    public static final int MELT_INTERVAL_TICKS = 4;

    private static final String TAG_MELTING = "melting";
    private static final String TAG_MELT_PROGRESS = "melt_progress";
    private static final String TAG_FUEL_BURN_TICKS = "fuel_burn_ticks";
    private static final String TAG_FUEL_TEMPERATURE = "fuel_temperature";
    private static final String TAG_FUEL_DISPLAY = "fuel_display";

    /**
     * The items currently melting, one per interior block, in slot order. Read-only; insert through
     * {@link #insertForMelting(ItemStack)}. This plus {@link #meltProgress(int)} is what the smeltery
     * screen (issue #101) draws.
     */
    public List<ItemStack> meltingItems() {
        return List.copyOf(meltingItems);
    }

    /** How far along the item in {@code slot} is, 0 to 1 (upstream {@code TileHeatingStructure#getProgress}). */
    public float meltProgress(int slot) {
        MeltingRecipe recipe = slot < 0 || slot >= meltingItems.size() ? null : recipeFor(slot);
        return recipe == null ? 0f : Math.min(1f, (float) meltProgress[slot] / (float) recipe.heatRequired());
    }

    /**
     * Puts {@code stack} into the melting inventory, one item per free slot, and returns whatever did
     * not fit. Server-side; items with no melting recipe are refused outright, the way upstream's
     * {@code interactWithEntitiesInside} only picks up what {@code getMelting} recognises.
     *
     * <p>#110: equivalent to {@link #insertForMelting(ItemStack, ServerPlayer)} with a {@code null}
     * player -- automation (a hopper feeding a smeltery, once that exists) has no player to attribute
     * the "first melt" advancement to.
     */
    public ItemStack insertForMelting(ItemStack stack) {
        return insertForMelting(stack, null);
    }

    /**
     * #110: player-aware overload backing the M2 advancement chain's {@code first_melt} step. Fires
     * {@link ForgeweaveCriteriaTriggers#FIRST_MELT} when {@code player} is non-null, at least one item
     * actually found a free slot, and the smeltery is hot ({@link #currentTemperature()} {@code > 0})
     * at the moment of insertion -- "a player inserts an item with a valid melting recipe into a
     * formed, hot smeltery" is this method's own chosen reading of "first melt", picked because it's
     * the one moment in the melting flow that already has both a real player and a completed action to
     * hang the criterion on. {@link #finishMelting} (when an item actually finishes) has neither: it
     * runs off {@link #meltTick}'s scheduled block tick, with no player anywhere in that call chain,
     * and attributing it correctly would mean tracking which player inserted each slot's item across
     * however many ticks it takes to finish -- more bookkeeping than a milestone gate needs.
     *
     * <p>Known gap from that choice: inserting into a <em>cold</em> smeltery (no lava yet) and having
     * it start melting once fuel later arrives does not retroactively grant the advancement. Acceptable
     * for M2's "the chain completes" bar. Until #101's GUI lands as the real caller (always with a
     * player) this overload has no production caller either -- see {@code AdvancementGameTests} for
     * the one exercising it today.
     */
    public ItemStack insertForMelting(ItemStack stack, @Nullable ServerPlayer player) {
        if (level == null || level.isClientSide || stack.isEmpty() || !isFormed()
                || MeltingRecipe.find(level.registryAccess(), stack).isEmpty()) {
            return stack;
        }
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < meltingItems.size() && !remaining.isEmpty(); slot++) {
            if (meltingItems.get(slot).isEmpty()) {
                setMeltingItem(slot, remaining.split(1));
            }
        }
        if (player != null && remaining.getCount() < stack.getCount() && currentTemperature() > 0) {
            ForgeweaveCriteriaTriggers.FIRST_MELT.get().trigger(player);
        }
        return remaining;
    }

    /**
     * How many melt ticks remain in the current fuel burn, {@code 0} if nothing is currently burning
     * (which does not by itself mean no fuel is available -- see {@link #currentTemperature()}).
     * Exposed alongside it for the smeltery GUI's fuel gauge (issue #101); no {@code SmelteryMenu}
     * exists in this tree yet to wire it into, so this is the seam #101 reads from directly.
     */
    public int fuelBurnTicksRemaining() {
        return fuelBurnTicksRemaining;
    }

    /**
     * #101: the temperature the GUI's fuel tooltip shows, readable on the client.
     *
     * <p>{@link #currentTemperature()} is server-only -- it peeks into a wall tank, which the client
     * has no business walking -- but the temperature of an <em>in-progress burn</em> is synced with
     * the rest of this block entity, so the screen can show it. Zero means "not currently burning",
     * which on the client is all that can honestly be said.
     */
    public int fuelTemperatureForDisplay() {
        return fuelBurnTicksRemaining > 0 ? fuelTemperature : 0;
    }

    /**
     * The fluid currently sitting in the wall tank feeding this smeltery, synced for the fuel gauge
     * (issue #97 follow-up). Unlike {@link #fuelTemperatureForDisplay()} this reads the tank's real,
     * live contents rather than the locked-in state of an in-progress burn -- {@code
     * SmelteryMenu#fuel}'s javadoc has the full story on why.
     */
    public FluidStack fuelDisplayFluid() {
        return fuelDisplayFluid;
    }

    /**
     * The smeltery's working heat, on the same scale a melting recipe's {@code temperature} uses.
     *
     * <p>Ported from upstream's {@code TileHeatingStructureFuelTank#getFuelDisplay}/{@code
     * TileHeatingStructure#getTemperature}: while a burn is under way this is the temperature that
     * burn locked in ({@link #fuelTemperature}); otherwise it is a <b>peek</b> at whatever registered
     * fuel currently sits in a wall tank, so temperature gating and the GUI see heat is available
     * before the next {@link #meltTick()} actually drains it (issue #97). A peek never consumes fuel;
     * only {@link #consumeFuel()} does, and only from inside a melt tick.
     */
    public int currentTemperature() {
        if (level == null || level.isClientSide || structure == null) {
            return 0;
        }
        refreshFuelDisplay();
        if (fuelBurnTicksRemaining > 0) {
            return fuelTemperature;
        }
        SmelteryFuel fuel = peekFuel();
        return fuel == null ? 0 : fuel.temperature();
    }

    /**
     * One melt step, driven by the scheduled block tick in {@link SmelteryControllerBlock#tick}.
     * Returns whether there is still work, i.e. whether the caller should schedule another.
     *
     * <p>Ported from upstream's {@code TileHeatingStructure#heatItems}: each slot accumulates
     * hundredths of the smeltery's working heat until it reaches its recipe's
     * {@link MeltingRecipe#heatRequired()}, and a slot whose recipe wants more heat than the
     * smeltery has simply never progresses.
     */
    public boolean meltTick() {
        if (level == null || level.isClientSide || structure() == null) {
            return false;
        }
        // #97: refuel before heating, upstream's own needsFuel-driven consumeFuel call. This method
        // only ever runs while armed (see armMeltTick), i.e. while there is melting work queued, so a
        // burn never starts for an idle smeltery.
        if (fuelBurnTicksRemaining <= 0 && hasMeltableItem()) {
            consumeFuel();
        }
        // Upstream converts the fuel's temperature to its own zero-is-300 scale the moment it burns it.
        int heat = currentTemperature() - MeltingRecipe.AMBIENT_TEMPERATURE;
        if (heat <= 0) {
            return false;
        }
        // Upstream heatSlot: "if your smeltery has <100 heat then it deserves to not create any heat".
        int step = heat / 100;
        boolean working = false;
        for (int slot = 0; slot < meltingItems.size(); slot++) {
            MeltingRecipe recipe = recipeFor(slot);
            if (recipe == null || heat < recipe.heatRequired() / MeltingRecipe.TIME_FACTOR) {
                continue;
            }
            working = true;
            if (meltProgress[slot] >= recipe.heatRequired()) {
                finishMelting(slot, recipe);
            } else {
                meltProgress[slot] += step;
            }
        }
        if (working) {
            // Upstream's fuel-- inside heatItems: the burn only counts down on a tick that actually
            // heated something.
            if (fuelBurnTicksRemaining > 0) {
                fuelBurnTicksRemaining--;
            }
            // #101: was setChanged(). The GUI's heat bars and fuel gauge read melt progress and the
            // remaining burn off the client-side copy of this block entity, so a melt that never
            // syncs renders as a grid of frozen bars over a full fuel gauge. Coalesced here rather
            // than in setMeltingItem so it costs one packet per melt tick (every
            // MELT_INTERVAL_TICKS) regardless of how many of a 9x9x9's slots advanced.
            syncToClients();
        }
        return working;
    }

    /**
     * Fills the tank and empties the slot. Ore-class recipes ({@link MeltingRecipe#ore()}) are scaled
     * by {@link SmelteryCore#yieldMultiplier()} -- docs/SCOPE.md M2: "melting recipes hold base
     * amounts, the core multiplies" and "core tier is the ONLY yield axis; ingot re-melts 1:1", so an
     * ingot/nugget/block re-melt ({@code ore == false}) is untouched. #99.
     *
     * <p>Upstream refuses a partial melt when the tank is nearly full and re-heats the item instead;
     * leaving the progress where it is has the same effect -- the slot retries every melt tick until
     * a drain makes room.
     */
    private void finishMelting(int slot, MeltingRecipe recipe) {
        // #99: floor a fractional multiplier x base rather than round, matching an ordinary int cast.
        // None of the shipped ore-class amounts land fractional at 1.5x/2x (see the PR's amounts
        // table), so this is currently unexercised by any shipped recipe.
        int amount = recipe.ore() ? (int) (recipe.amount() * core.yieldMultiplier()) : recipe.amount();
        var result = new FluidStack(recipe.fluid(), amount);
        if (tank.fill(result, IFluidHandler.FluidAction.SIMULATE) != result.getAmount()) {
            return;
        }
        tank.fill(result, IFluidHandler.FluidAction.EXECUTE);
        setMeltingItem(slot, ItemStack.EMPTY);
    }

    /**
     * Schedules the next melt tick unless one is already pending or there is nothing to melt. Every
     * path that can create work calls this; nothing polls.
     *
     * <p>Package-private rather than {@code private}: {@link SearedTankBlockEntity} calls this too
     * (#97), to wake a melt that stopped for want of heat when a wall tank gets refilled -- the only
     * other re-arm points are insertion, a structure scan, and load.
     */
    void armMeltTick() {
        if (level == null || level.isClientSide || !hasMeltableItem()) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            level.scheduleTick(worldPosition, block, MELT_INTERVAL_TICKS);
        }
    }

    /**
     * Whether any slot holds something this smeltery can actually melt.
     *
     * <p>#101: this used to answer "any slot holds anything", which was safe only while {@link
     * #insertForMelting} -- which rejects items with no recipe -- was the sole way in. It is not any
     * more: #97 made this gate {@link #consumeFuel()}, and the GUI's melt slots are a second entry
     * path. An unmeltable item sitting in the grid therefore armed a melt tick, burned a whole fuel
     * charge, found nothing to heat, and returned without decrementing it -- so every poke of the
     * controller drained the tank another charge for nothing.
     *
     * <p>{@link #recipeFor} is cached per slot and this short-circuits on the first hit, so the
     * normal case is one lookup; the full scan only happens when there is nothing meltable at all,
     * which is exactly the case that must stop ticking.
     *
     * <p><b>Known consequence, not a bug:</b> because {@link #armMeltTick()} now needs a recipe
     * too, an item already sitting in the grid that <em>gains</em> one via a datapack {@code
     * /reload} does not re-arm by itself -- it waits for the next insert, structure scan
     * (right-click or neighbour change) or chunk load. Same shape as fuel arriving in a distant tank
     * after the items went in, and it wants the same fix: whatever ends up re-arming on fuel arrival
     * should re-arm on reload too. ponytail: not worth its own hook until one of the two actually
     * bites in a playtest.
     */
    private boolean hasMeltableItem() {
        for (int slot = 0; slot < meltingItems.size(); slot++) {
            if (recipeFor(slot) != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether any slot holds anything at all, meltable or not -- what {@link Container#isEmpty}
     * means. Deliberately <em>not</em> {@link #hasMeltableItem()}: a grid holding one unmeltable
     * item is not an empty container, and reporting it as one would mislead every vanilla consumer
     * of the container (hoppers, comparators).
     */
    private boolean hasAnyItem() {
        for (ItemStack stack : meltingItems) {
            if (!stack.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void setMeltingItem(int slot, ItemStack stack) {
        meltingItems.set(slot, stack);
        meltProgress[slot] = 0;
        meltRecipes[slot] = null;
        setChanged();
        armMeltTick();
    }

    /**
     * The recipe for the item in {@code slot}, cached because the alternative is a registry scan per
     * slot per tick. The cache re-tests the ingredient rather than being invalidated on {@code
     * /reload}, which also makes it self-healing after a load (where no level exists yet to resolve
     * recipes against).
     */
    @Nullable
    private MeltingRecipe recipeFor(int slot) {
        ItemStack stack = meltingItems.get(slot);
        if (stack.isEmpty() || level == null) {
            return null;
        }
        MeltingRecipe cached = meltRecipes[slot];
        if (cached == null || !cached.input().test(stack)) {
            cached = MeltingRecipe.find(level.registryAccess(), stack).orElse(null);
            meltRecipes[slot] = cached;
        }
        return cached;
    }

    /** Upstream's {@code updateStructureInfo} resize, including dropping what no longer fits out of the front. */
    private void resizeMeltingInventory(int slots) {
        if (slots == meltingItems.size()) {
            return;
        }
        NonNullList<ItemStack> resized = NonNullList.withSize(slots, ItemStack.EMPTY);
        for (int slot = 0; slot < meltingItems.size(); slot++) {
            ItemStack stack = meltingItems.get(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (slot < slots) {
                resized.set(slot, stack);
            } else if (level != null && !level.isClientSide) {
                BlockPos drop = worldPosition.relative(getBlockState().getValue(SmelteryControllerBlock.FACING));
                Containers.dropItemStack(level, drop.getX(), drop.getY(), drop.getZ(), stack);
            }
        }
        meltProgress = Arrays.copyOf(meltProgress, slots);
        meltingItems = resized;
        meltRecipes = new MeltingRecipe[slots];
    }

    /** Upstream keeps a list of every tank in the walls; Forgeweave walks the shell once and remembers the hit. */
    @Nullable
    private BlockPos findFuelTank() {
        if (structure == null) {
            return null;
        }
        BlockPos min = structure.interiorMin();
        BlockPos max = structure.interiorMax();
        for (int y = min.getY(); y <= max.getY(); y++) {
            for (int x = min.getX(); x <= max.getX(); x++) {
                for (int z : new int[] {min.getZ() - 1, max.getZ() + 1}) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (holdsFuel(pos)) {
                        return pos;
                    }
                }
            }
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x : new int[] {min.getX() - 1, max.getX() + 1}) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (holdsFuel(pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    /** Whether the wall tank at {@code pos} holds a registered {@link SmelteryFuel} (#97), upstream's {@code hasTankWithFuel}. */
    private boolean holdsFuel(BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank
                && tank.tank().getFluidAmount() > 0
                && SmelteryFuel.find(level.registryAccess(), tank.tank().getFluid().getFluid()).isPresent();
    }

    /** The registered fuel available right now, without consuming it, or {@code null} if none is. */
    @Nullable
    private SmelteryFuel peekFuel() {
        if (fuelTank == null || !holdsFuel(fuelTank)) {
            fuelTank = findFuelTank();
        }
        if (fuelTank == null || !(level.getBlockEntity(fuelTank) instanceof SearedTankBlockEntity tank)) {
            return null;
        }
        return SmelteryFuel.find(level.registryAccess(), tank.tank().getFluid().getFluid()).orElse(null);
    }

    /**
     * Refreshes {@link #fuelDisplayFluid} from whatever the wall tank feeding this smeltery actually
     * holds right now, and syncs to clients when it changed -- issue #97's follow-up fixing the fuel
     * gauge. Deliberately the tank's real, live contents rather than upstream's own burn-progress
     * display (which zeroes out for the whole burn, see {@code SmelteryMenu#fuel}): this is a
     * conscious parity deviation, recorded in the PR, because upstream's own behaviour renders an
     * empty gauge for nearly the entire time a smeltery is actively melting.
     *
     * <p>Cheap to call opportunistically: {@link #peekFuel()} finds the tank at most once per call
     * (cached in {@link #fuelTank}), and the sync only fires when the fluid or its amount actually
     * moved.
     */
    private void refreshFuelDisplay() {
        peekFuel();
        FluidStack live = fuelTank != null && level != null && level.getBlockEntity(fuelTank) instanceof SearedTankBlockEntity tank
                ? tank.tank().getFluid()
                : FluidStack.EMPTY;
        if (live.getFluid() != fuelDisplayFluid.getFluid() || live.getAmount() != fuelDisplayFluid.getAmount()) {
            fuelDisplayFluid = live.copy();
            syncToClients();
        }
    }

    /**
     * Drains one burn cycle's worth of fuel from the wall tank holding it and locks in
     * {@link #fuelBurnTicksRemaining}/{@link #fuelTemperature} for that burn -- upstream's {@code
     * consumeFuel}/{@code searchForFuel}. Called only from {@link #meltTick()}, itself only scheduled
     * while there is melting work (see {@link #armMeltTick()}), so an idle or unfuelled smeltery never
     * touches a tank. A tank with less than a full {@link SmelteryFuel#amount()} simply is not burned
     * yet -- ponytail: no partial-fuel accounting, matching how upstream itself only bothers with it
     * once a tank is close to fully drained; add it if a playtest tank ever runs that dry mid-melt.
     */
    private void consumeFuel() {
        SmelteryFuel fuel = peekFuel();
        if (fuel == null || !(level.getBlockEntity(fuelTank) instanceof SearedTankBlockEntity tank)) {
            return;
        }
        if (tank.tank().drain(fuel.amount(), IFluidHandler.FluidAction.SIMULATE).getAmount() != fuel.amount()) {
            return;
        }
        tank.tank().drain(fuel.amount(), IFluidHandler.FluidAction.EXECUTE);
        fuelBurnTicksRemaining = fuel.duration();
        fuelTemperature = fuel.temperature();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Scheduled ticks ride along with the chunk, so this is belt-and-braces for a smeltery that
        // was saved mid-melt by something that dropped its tick (a /setblock, an older world).
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
        tag.put(TAG_TANK, tank.writeToNBT(registries, new CompoundTag()));
        // #96: what is melting and how far along it is (SCOPE.md M2 save-compat fixture list).
        tag.put(TAG_MELTING, ContainerHelper.saveAllItems(new CompoundTag(), meltingItems, true, registries));
        tag.putIntArray(TAG_MELT_PROGRESS, meltProgress);
        // #97: the in-progress burn -- fuel state (SCOPE.md M2 save-compat fixture list).
        tag.putInt(TAG_FUEL_BURN_TICKS, fuelBurnTicksRemaining);
        tag.putInt(TAG_FUEL_TEMPERATURE, fuelTemperature);
        // #97 follow-up: the fuel gauge's live snapshot, so it has something to show the instant a
        // reopened screen loads rather than waiting for the next refresh.
        tag.put(TAG_FUEL_DISPLAY, fuelDisplayFluid.saveOptional(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        structure = tag.contains(TAG_STRUCTURE)
                ? SmelteryStructure.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_STRUCTURE))
                        .resultOrPartial(error -> {})
                        .orElse(null)
                : null;
        tank.readFromNBT(registries, tag.getCompound(TAG_TANK));
        // #96: resizeTank sizes the melting inventory to the interior it just read, so the saved
        // items and their progress go in afterwards.
        resizeTank();
        ContainerHelper.loadAllItems(tag.getCompound(TAG_MELTING), meltingItems, registries);
        int[] progress = tag.getIntArray(TAG_MELT_PROGRESS);
        System.arraycopy(progress, 0, meltProgress, 0, Math.min(progress.length, meltProgress.length));
        // #97: the in-progress burn, if any -- fuelTank itself is not saved, it is re-found on demand.
        fuelBurnTicksRemaining = tag.getInt(TAG_FUEL_BURN_TICKS);
        fuelTemperature = tag.getInt(TAG_FUEL_TEMPERATURE);
        fuelDisplayFluid = FluidStack.parseOptional(registries, tag.getCompound(TAG_FUEL_DISPLAY));
    }

    /** Structure bounds and tank contents are what the client needs for the smeltery GUI and fluid rendering (#101). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ---------------------------------------------------------------- #101: the smeltery GUI

    /**
     * Pushes a tank change to every client watching this core, which is how the GUI's fluid column
     * stays live -- {@link #getUpdateTag} already carries the tank, so this is the only trigger it
     * was missing.
     *
     * <p>ponytail: one block-update packet per tank change, no throttling. That is fine while the
     * things that change a tank are a completed melt (#96), a drain pour and a GUI click; if #97's
     * fuel loop ever fills per tick, throttle here rather than at every call site.
     */
    private void onTankChanged() {
        syncToClients();
    }

    /**
     * Marks dirty and pushes this block entity's state to every client watching it. {@link
     * #getUpdateTag} already carries the tank, the structure and (since #96) the melting inventory
     * and its progress, so this is the one trigger all of the GUI's live state rides on.
     */
    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Moves the fluid the player clicked in the GUI to the bottom of the melt, making it the fluid a
     * drain pours (upstream's {@code SmelteryFluidClicked} packet handler).
     *
     * <p>Server-side only, reached from {@link SmelteryMenu#clickMenuButton} -- the index is
     * untrusted and {@link SmelteryTank#moveToBottom} range-checks it.
     */
    public void selectDrainFluid(int index) {
        if (level != null && !level.isClientSide) {
            tank.moveToBottom(index);
        }
    }

    @Override
    public Component getDisplayName() {
        return getBlockState().getBlock().getName();
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new SmelteryMenu(containerId, playerInventory,
                ContainerLevelAccess.create(level, worldPosition), worldPosition, meltingContainer());
    }

    @Override
    public void writeMenuData(RegistryFriendlyByteBuf buf) {
        buf.writeBlockPos(worldPosition);
        buf.writeVarInt(meltingItems.size());
    }

    /**
     * The melting inventory as a vanilla {@link Container}, so {@link SmelteryMenu} can put real
     * {@link net.minecraft.world.inventory.Slot}s over it.
     *
     * <p>Real slots rather than a bespoke click packet: vanilla's own container click handling is
     * already server-authoritative, already handles pick-up/place/split/shift-click, and already
     * validates against the server's copy of the inventory. {@link #insertForMelting} stays as the
     * non-GUI path (hoppers, dropped items) -- this is the same inventory seen through the interface
     * slots need.
     *
     * <p>Reads {@link #meltingItems} live rather than capturing it, because {@link
     * #resizeMeltingInventory} <em>replaces</em> the list when the interior changes. A menu open
     * across such a resize closes on its next {@code stillValid} ({@link SmelteryMenu}), which is
     * upstream's {@code GuiSmeltery#updateScreen} behaviour.
     */
    public Container meltingContainer() {
        return new MeltingContainer();
    }

    /** @see #meltingContainer() */
    private final class MeltingContainer implements Container {
        @Override
        public int getContainerSize() {
            return meltingItems.size();
        }

        @Override
        public boolean isEmpty() {
            return !hasAnyItem();
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < meltingItems.size() ? meltingItems.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            ItemStack removed = ContainerHelper.removeItem(meltingItems, slot, count);
            if (!removed.isEmpty()) {
                // Progress belongs to the stack that earned it, so taking the item back resets it.
                setMeltingItem(slot, meltingItems.get(slot));
            }
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack removed = getItem(slot);
            setMeltingItem(slot, ItemStack.EMPTY);
            return removed;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            setMeltingItem(slot, stack);
        }

        /**
         * Only what the smeltery can actually melt, which is what keeps the GUI honest about the
         * grid being a melting inventory and not general storage. Checked on both sides: the client
         * greys the placement, the server refuses it.
         */
        @Override
        public boolean canPlaceItem(int slot, ItemStack stack) {
            return level != null && MeltingRecipe.find(level.registryAccess(), stack).isPresent();
        }

        @Override
        public int getMaxStackSize() {
            return 1; // Upstream melts one item per interior block; a slot never holds a stack.
        }

        @Override
        public void setChanged() {
            SmelteryControllerBlockEntity.this.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return !isRemoved();
        }

        @Override
        public void clearContent() {
            for (int slot = 0; slot < meltingItems.size(); slot++) {
                setMeltingItem(slot, ItemStack.EMPTY);
            }
        }
    }
}
