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
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;

import dev.gkissel.forgeweave.recipe.MeltingRecipe;

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
public class SmelteryControllerBlockEntity extends BlockEntity {
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
    private final FluidTank tank = new FluidTank(CAPACITY_PER_BLOCK);

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

    public SmelteryControllerBlockEntity(BlockPos pos, BlockState state, SmelteryCore core) {
        super(core.blockEntityType().get(), pos, state);
        this.core = core;
    }

    /** Which core tier this structure has; {@link #finishMelting} multiplies ore-class yields by {@link SmelteryCore#yieldMultiplier()} (#99). */
    public SmelteryCore core() {
        return core;
    }

    /** The molten-metal tank. Its capacity tracks the interior size; it is filled by melting (#96) and drained through a {@link SearedDrainBlock}. */
    public FluidTank tank() {
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
            // #96: a scan is the one moment the core is guaranteed to hear about the world changing
            // around it, so it is also where melting that stopped for want of heat picks back up.
            armMeltTick();
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

    /** Capacity follows the interior size; an interior that shrank spills nothing but caps what is held. */
    private void resizeTank() {
        tank.setCapacity(structure == null ? CAPACITY_PER_BLOCK : structure.interiorVolume() * CAPACITY_PER_BLOCK);
        if (tank.getFluidAmount() > tank.getCapacity()) {
            tank.getFluid().setAmount(tank.getCapacity());
        }
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
     */
    public ItemStack insertForMelting(ItemStack stack) {
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
        return remaining;
    }

    /**
     * The smeltery's working heat, on the same scale a melting recipe's {@code temperature} uses.
     *
     * <p>ponytail: this is the entire heat model for now -- lava anywhere in the structure's seared
     * tanks means the smeltery runs at lava's own fluid temperature, and nothing is consumed. Issue
     * #97 replaces the body with the real fuel system (datapack fuels carrying a temperature and a
     * burn duration, drained from the tank as they burn); every consumer already goes through this
     * one method, and the only other thing #97 needs is to re-arm the melt tick when fuel arrives.
     */
    public int currentTemperature() {
        if (level == null || level.isClientSide || structure == null) {
            return 0;
        }
        if (fuelTank == null || !holdsLava(fuelTank)) {
            fuelTank = findFuelTank();
        }
        return fuelTank == null ? 0 : Fluids.LAVA.getFluidType().getTemperature();
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
            setChanged();
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
     */
    private void armMeltTick() {
        if (level == null || level.isClientSide || !hasMeltableItem()) {
            return;
        }
        Block block = getBlockState().getBlock();
        if (!level.getBlockTicks().hasScheduledTick(worldPosition, block)) {
            level.scheduleTick(worldPosition, block, MELT_INTERVAL_TICKS);
        }
    }

    private boolean hasMeltableItem() {
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
                    if (holdsLava(pos)) {
                        return pos;
                    }
                }
            }
            for (int z = min.getZ(); z <= max.getZ(); z++) {
                for (int x : new int[] {min.getX() - 1, max.getX() + 1}) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (holdsLava(pos)) {
                        return pos;
                    }
                }
            }
        }
        return null;
    }

    private boolean holdsLava(BlockPos pos) {
        return level != null && level.getBlockEntity(pos) instanceof SearedTankBlockEntity tank
                && tank.tank().getFluidAmount() > 0 && tank.tank().getFluid().getFluid() == Fluids.LAVA;
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
}
