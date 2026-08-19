package dev.gkissel.forgeweave.block;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import dev.gkissel.forgeweave.advancement.ForgeweaveCriteriaTriggers;
import dev.gkissel.forgeweave.casting.CastingRecipe;

/**
 * The Casting Table and Casting Basin (docs/SCOPE.md M2 issue #100), ported from upstream 1.12's
 * {@code TileCasting}/{@code TileCastingTable}/{@code TileCastingBasin} and {@code
 * FluidHandlerCasting} (NOTICE.md). One class parameterized by {@link CastingRecipe.Station}, the
 * same shape {@link SmelteryControllerBlockEntity} uses for its two core tiers -- upstream's two
 * subclasses differ only in which recipe list they search and how they draw the item on top.
 *
 * <p>Two item slots, upstream's: slot 0 is the input (a cast, or the crafted part a gold pour is
 * moulded around), slot 1 the finished result. A faucet fills the block through its fluid handler,
 * which only accepts a fluid some recipe matches and only up to that recipe's exact amount -- so a
 * pour into a table with the wrong cast is refused at the faucet rather than swallowed. An item
 * handler exposes the same two slots to automation on upstream's terms (#207, {@link
 * CastingItemHandler}): a hopper below takes the finished result and leaves the cast.
 *
 * <p><b>No block-entity ticker.</b> Upstream ticks every casting block every tick and returns
 * immediately unless the tank is full, which docs/SCOPE.md's "block entities tick only while doing
 * work" budget rules out. Cooling instead runs off a vanilla scheduled block tick, booked for
 * exactly {@link CastingRecipe#cooldownTicks(net.minecraft.world.level.material.Fluid)} ticks when the last drop lands (see {@link
 * CastingBlock#tick}); scheduled ticks persist across save/load for free, and a stale one that fires
 * after the fluid was drained back out simply finds nothing to do.
 */
public class CastingBlockEntity extends BlockEntity {
    private static final String TAG_INPUT = "input";
    private static final String TAG_OUTPUT = "output";
    private static final String TAG_TANK = "tank";

    /** Upstream's slot 0: the cast, or the crafted part a gold pour is moulded around. */
    private static final int SLOT_INPUT = 0;
    /** Upstream's slot 1: the finished, cooled result. */
    private static final int SLOT_OUTPUT = 1;

    private final CastingRecipe.Station station;
    /** Capacity is whatever the in-progress recipe needs; 0 (and empty) means "nothing being poured". */
    private final FluidTank tank = new FluidTank(0);
    private final IFluidHandler fluidHandler = new CastingFluidHandler();
    private final IItemHandler itemHandler = new CastingItemHandler();

    private ItemStack input = ItemStack.EMPTY;
    private ItemStack output = ItemStack.EMPTY;

    public CastingBlockEntity(BlockPos pos, BlockState state, CastingRecipe.Station station) {
        super(station == CastingRecipe.Station.TABLE
                ? ForgeweaveBlockEntities.CASTING_TABLE.get()
                : ForgeweaveBlockEntities.CASTING_BASIN.get(), pos, state);
        this.station = station;
    }

    public ItemStack input() {
        return input;
    }

    public ItemStack output() {
        return output;
    }

    /** The fluid poured in so far, for renderers and tests. */
    public FluidTank tank() {
        return tank;
    }

    /**
     * Upstream {@code TileCasting#update}'s cooldown condition ({@code tank.getFluidAmount() ==
     * tank.getCapacity()}): a pour is mid-cooldown, waiting on the scheduled tick {@link
     * CastingBlock#tick} will finish, rather than empty or still being poured into. {@code capacity}
     * is 0 (see {@link #tank}'s field doc) whenever nothing is in progress, which upstream's fixed
     * per-block tank size never is -- checked explicitly here so an idle block (0 == 0) doesn't
     * read as cooling.
     */
    static boolean isCooling(int fluidAmount, int capacity) {
        return capacity > 0 && fluidAmount == capacity;
    }

    /** {@link #isCooling(int, int)} against this block entity's own tank. */
    boolean isCooling() {
        return isCooling(tank.getFluidAmount(), tank.getCapacity());
    }

    /**
     * Upstream's {@code TileCasting#interact}: while fluid is in the block nothing can be added or
     * taken; otherwise an empty block takes one item from the player's hand, and a non-empty one
     * gives back its result (or, with no result yet, its input).
     *
     * @return whether the click did anything
     */
    public boolean interact(Player player, InteractionHand hand) {
        if (level == null || level.isClientSide || !tank.isEmpty()) {
            return false;
        }
        if (input.isEmpty() && output.isEmpty()) {
            ItemStack held = player.getItemInHand(hand);
            if (held.isEmpty()) {
                return false;
            }
            input = held.split(1);
        } else {
            boolean fromOutput = !output.isEmpty();
            ItemHandlerHelper.giveItemToPlayer(player, fromOutput ? output : input);
            if (fromOutput) {
                output = ItemStack.EMPTY;
                // #110 -- "first cast" advancement (docs/SCOPE.md M2 issue #110): fires whenever a
                // player collects a finished casting result, cast creation and part casting alike --
                // "casting table produces output" is the issue's own wording, and the chain doesn't
                // distinguish the two.
                if (player instanceof ServerPlayer serverPlayer) {
                    ForgeweaveCriteriaTriggers.FIRST_CAST.get().trigger(serverPlayer);
                }
            } else {
                input = ItemStack.EMPTY;
            }
        }
        contentsChanged();
        return true;
    }

    /** Drops both slots when the block is broken. */
    public void dropContents() {
        if (level == null) {
            return;
        }
        Block.popResource(level, worldPosition, input);
        Block.popResource(level, worldPosition, output);
        input = ItemStack.EMPTY;
        output = ItemStack.EMPTY;
    }

    /** Upstream's comparator output: full signal once a result is waiting to be picked up. */
    public int comparatorStrength() {
        return output.isEmpty() ? 0 : 15;
    }

    /**
     * Finishes a pour whose cooling time has elapsed. Called from the scheduled block tick; a no-op
     * unless the tank is still full of a fluid that still matches a recipe, which is what makes a
     * stale tick (fluid drained back out, cast taken) harmless.
     */
    void finishCasting() {
        if (level == null || level.isClientSide || tank.isEmpty()) {
            return;
        }
        CastingRecipe recipe = currentRecipe();
        if (recipe == null || tank.getFluidAmount() < recipe.amount()) {
            return;
        }

        ItemStack result = recipe.resultFor(tank.getFluid().getFluid());
        if (recipe.consumesCast()) {
            input = ItemStack.EMPTY;
        }
        if (recipe.resultInInput()) {
            // Upstream's switchOutputs: cast creation leaves the fresh cast where the next pour needs
            // it, and whatever survived in the input (nothing, when the cast was consumed) moves out.
            output = input;
            input = result;
        } else {
            output = result;
        }

        level.playSound(null, worldPosition, SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.07f, 4f);
        tank.setFluid(FluidStack.EMPTY);
        tank.setCapacity(0);
        contentsChanged();
    }

    /** The recipe the fluid currently in the tank is being poured for, or {@code null}. */
    @Nullable
    private CastingRecipe currentRecipe() {
        return tank.isEmpty() ? null : findRecipe(tank.getFluid().getFluid());
    }

    @Nullable
    private CastingRecipe findRecipe(Fluid fluid) {
        if (level == null || fluid == Fluids.EMPTY) {
            return null;
        }
        Registry<CastingRecipe> recipes = level.registryAccess().registryOrThrow(CastingRecipe.REGISTRY);
        return CastingRecipe.find(recipes, station, input, fluid);
    }

    private void contentsChanged() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!input.isEmpty()) {
            tag.put(TAG_INPUT, input.save(registries));
        }
        if (!output.isEmpty()) {
            tag.put(TAG_OUTPUT, output.save(registries));
        }
        tag.put(TAG_TANK, tank.writeToNBT(registries, new CompoundTag()));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        input = tag.contains(TAG_INPUT) ? ItemStack.parseOptional(registries, tag.getCompound(TAG_INPUT)) : ItemStack.EMPTY;
        output = tag.contains(TAG_OUTPUT) ? ItemStack.parseOptional(registries, tag.getCompound(TAG_OUTPUT)) : ItemStack.EMPTY;
        tank.readFromNBT(registries, tag.getCompound(TAG_TANK));
        // FluidTank's own NBT carries no capacity, so the capacity the renderer draws its fill
        // fraction against is re-derived here, from the recipe the fluid in the tank is being poured
        // for. #204: doing it in onLoad alone was not enough. A block entity read from disk has no
        // level yet and so no recipe registry (see the fill comment below), which is why onLoad
        // exists -- but NeoForge defers onLoad to the end of the tick the block entity appeared in
        // and never calls it again, while a client gets one of these loads per drop of an
        // in-progress pour. Every drop after the first therefore left capacity pinned at the amount
        // poured so far, i.e. a fill fraction of exactly 1: a mid-pour block rendered full.
        tank.setCapacity(level == null ? tank.getFluidAmount() : recipeAmountFor(tank.getFluid().getFluid()));
    }

    /**
     * Restores the capacity for the one load that could not derive it: a block entity read from disk
     * is handed its level only after {@link #loadAdditional} has run. Casting logic itself no longer
     * reads this value.
     */
    @Override
    public void onLoad() {
        super.onLoad();
        if (!tank.isEmpty()) {
            tank.setCapacity(recipeAmountFor(tank.getFluid().getFluid()));
        }
    }

    private int recipeAmountFor(Fluid fluid) {
        CastingRecipe recipe = findRecipe(fluid);
        return recipe == null ? tank.getFluidAmount() : recipe.amount();
    }

    /** Both slots and the tank are what a renderer needs, so the whole block entity syncs. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /**
     * Upstream's {@code FluidHandlerCasting}: an empty block accepts only a fluid some recipe wants
     * for whatever is sitting in it, and only that recipe's exact amount; a block already holding a
     * result accepts nothing.
     */
    private class CastingFluidHandler implements IFluidHandler {
        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public FluidStack getFluidInTank(int slot) {
            return tank.getFluid();
        }

        @Override
        public int getTankCapacity(int slot) {
            return tank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int slot, FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !output.isEmpty()) {
                return 0;
            }
            if (!tank.isEmpty() && !FluidStack.isSameFluidSameComponents(tank.getFluid(), resource)) {
                return 0;
            }
            // #183/#186 -- the recipe is asked how much fits on every drop, not just on the one that
            // starts the pour. A block entity reloaded from disk has no level while its NBT is read
            // (LevelChunk#promotePendingBlockEntity hands it one only afterwards), so it cannot
            // resolve a recipe there and cannot know its own capacity; a half-poured block used to
            // come back capped at whatever it was holding, refuse every further drop -- basin output
            // never arrived -- and, since nothing can be taken out of a block with fluid in it, seal
            // its own cast in with it.
            CastingRecipe recipe = findRecipe(resource.getFluid());
            if (recipe == null) {
                return 0;
            }
            int room = recipe.amount() - tank.getFluidAmount();
            if (room <= 0) {
                return 0;
            }
            if (action.simulate()) {
                return Math.min(resource.getAmount(), room);
            }
            tank.setCapacity(recipe.amount());
            int filled = tank.fill(resource, action);
            if (filled > 0) {
                contentsChanged();
                if (tank.getFluidAmount() >= recipe.amount() && level != null && !level.isClientSide) {
                    level.scheduleTick(worldPosition, getBlockState().getBlock(), recipe.cooldownTicks(resource.getFluid()));
                }
            }
            return filled;
        }

        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return resource.isEmpty() || !FluidStack.isSameFluidSameComponents(resource, tank.getFluid())
                    ? FluidStack.EMPTY
                    : drain(resource.getAmount(), action);
        }

        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = tank.drain(maxDrain, action);
            if (!drained.isEmpty() && action.execute()) {
                if (tank.isEmpty()) {
                    tank.setCapacity(0);
                }
                contentsChanged();
            }
            return drained;
        }
    }

    /**
     * Upstream's {@code SidedInvWrapper} over {@code TileCasting}'s {@code canInsertItem}/{@code
     * canExtractItem} (#207): automation may put a cast into the input slot and may take the
     * finished result out of the output slot, and that is all. The cast in use is never extractable
     * -- a hopper under a table empties its output and leaves the reusable cast where the next pour
     * needs it -- and neither slot moves at all while there is fluid in the block, which is
     * upstream's own {@code tank.isEmpty()} guard (1.20's {@code canTakeItemThroughFace}) and the
     * same rule {@link #interact} applies to a player's hands.
     */
    private class CastingItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == SLOT_OUTPUT ? output : input;
        }

        /** One item, upstream's {@code stackSizeLimit}; a recipe result may still stack higher. */
        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_INPUT ? 1 : Item.ABSOLUTE_MAX_STACK_SIZE;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == SLOT_INPUT && !stack.isEmpty() && tank.isEmpty() && input.isEmpty() && output.isEmpty();
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!isItemValid(slot, stack)) {
                return stack;
            }
            if (!simulate) {
                input = stack.copyWithCount(1);
                contentsChanged();
            }
            return stack.copyWithCount(stack.getCount() - 1);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != SLOT_OUTPUT || amount <= 0 || output.isEmpty() || !tank.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack extracted = output.copyWithCount(Math.min(amount, output.getCount()));
            if (!simulate) {
                output = output.copyWithCount(output.getCount() - extracted.getCount());
                contentsChanged();
            }
            return extracted;
        }
    }

    /** Wires the fluid- and item-handler capabilities for both casting blocks; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ForgeweaveBlockEntities.CASTING_TABLE.get(),
                (blockEntity, side) -> blockEntity.fluidHandler);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ForgeweaveBlockEntities.CASTING_BASIN.get(),
                (blockEntity, side) -> blockEntity.fluidHandler);
        // Every side, as upstream does: it builds one SidedInvWrapper for DOWN and hands it back for
        // any facing, and its own canInsert/canExtract ignore the side entirely.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ForgeweaveBlockEntities.CASTING_TABLE.get(),
                (blockEntity, side) -> blockEntity.itemHandler);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ForgeweaveBlockEntities.CASTING_BASIN.get(),
                (blockEntity, side) -> blockEntity.itemHandler);
    }
}
