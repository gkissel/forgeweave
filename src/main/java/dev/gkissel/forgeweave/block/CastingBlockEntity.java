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
 * pour into a table with the wrong cast is refused at the faucet rather than swallowed.
 *
 * <p><b>No block-entity ticker.</b> Upstream ticks every casting block every tick and returns
 * immediately unless the tank is full, which docs/SCOPE.md's "block entities tick only while doing
 * work" budget rules out. Cooling instead runs off a vanilla scheduled block tick, booked for
 * exactly {@link CastingRecipe#cooldownTicks()} ticks when the last drop lands (see {@link
 * CastingBlock#tick}); scheduled ticks persist across save/load for free, and a stale one that fires
 * after the fluid was drained back out simply finds nothing to do.
 */
public class CastingBlockEntity extends BlockEntity {
    private static final String TAG_INPUT = "input";
    private static final String TAG_OUTPUT = "output";
    private static final String TAG_TANK = "tank";

    private final CastingRecipe.Station station;
    /** Capacity is whatever the in-progress recipe needs; 0 (and empty) means "nothing being poured". */
    private final FluidTank tank = new FluidTank(0);
    private final IFluidHandler fluidHandler = new CastingFluidHandler();

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
        if (level == null || level.isClientSide || tank.getFluidAmount() < tank.getCapacity() || tank.isEmpty()) {
            return;
        }
        CastingRecipe recipe = currentRecipe();
        if (recipe == null) {
            return;
        }

        ItemStack result = recipe.result().copy();
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
        // FluidTank's own NBT carries no capacity; a half-poured block reloads with the capacity its
        // recipe asks for, which is also what stops a reload from counting as "full" too early.
        tank.setCapacity(tank.isEmpty() ? 0 : recipeAmountFor(tank.getFluid().getFluid()));
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
            if (tank.isEmpty()) {
                CastingRecipe recipe = findRecipe(resource.getFluid());
                if (recipe == null) {
                    return 0;
                }
                if (action.simulate()) {
                    return Math.min(resource.getAmount(), recipe.amount());
                }
                tank.setCapacity(recipe.amount());
            }
            int filled = tank.fill(resource, action);
            if (filled > 0 && action.execute()) {
                contentsChanged();
                if (tank.getFluidAmount() >= tank.getCapacity() && level != null && !level.isClientSide) {
                    CastingRecipe recipe = currentRecipe();
                    if (recipe != null) {
                        level.scheduleTick(worldPosition, getBlockState().getBlock(), recipe.cooldownTicks());
                    }
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

    /** Wires the fluid-handler capability for both casting blocks; called from {@code Forgeweave}'s constructor. */
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ForgeweaveBlockEntities.CASTING_TABLE.get(),
                (blockEntity, side) -> blockEntity.fluidHandler);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ForgeweaveBlockEntities.CASTING_BASIN.get(),
                (blockEntity, side) -> blockEntity.fluidHandler);
    }
}
