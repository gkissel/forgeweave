package dev.gkissel.forgeweave.trait;

import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.energy.IEnergyStorage;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * {@code energized(capacity, perDurabilityPoint)}: a Forge-Energy buffer on the tool that spends
 * before durability (issue #830, M6's energy trait batch, ADR-0004). Design pool:
 * docs/research/m6-material-expansion-references.md §3 (Moar Tinkers' RF-repair/RF-eater family)
 * and §6.5 ("Energized I-II") -- our own shape and numbers, not ported; every reference clone here
 * is inspiration-only (CLAUDE.md).
 *
 * <h2>Why only the amount is persisted (issue #830 deliverable 1)</h2>
 *
 * <p>{@link ForgeweaveDataComponents#ENERGY} carries the <em>current</em> stored amount and nothing
 * else; capacity is this trait's own constructor parameter, re-summed on demand by {@link
 * ForgeweaveTraits#energyCapacity} across every trait on the tool -- the same shape {@code
 * OVERSLIME}'s 50-per-trait capacity takes, never stored, so retuning the number here later needs
 * no save-compat migration. Absent means zero: a tool that has never held any energy writes nothing
 * extra to its stack, and a tool with no {@code energized} trait sums to zero capacity, which is
 * what keeps {@link #capability} returning {@code null} for it -- no component write, no capability
 * query cost, "costs nothing" per the issue's own wording.
 *
 * <h2>Spending before durability (deliverable 2)</h2>
 *
 * <p>Rides {@link Trait#durabilityDamage}, the single choke point every durability loss in the game
 * routes through ({@code ToolItem}'s class javadoc) -- mining, attacking and any third-party {@code
 * hurtAndBreak} alike, so "spend energy before durability" needs no combat-only seam.
 * {@code energyPerDurabilityPoint} FE buys back one point of durability loss; a hit the buffer can
 * only partly cover drains it to zero and pays the remainder in durability, in that order, never
 * the reverse.
 *
 * <h2>The item capability (deliverable 1)</h2>
 *
 * <p>{@link #capability} is what {@code ToolItem#registerCapabilities} hands {@code
 * Capabilities.EnergyStorage.ITEM} as its per-stack provider: a completely ordinary {@link
 * IEnergyStorage} view over the stack's own component for a tool that actually carries the trait,
 * {@code null} otherwise. It reads and writes {@code stack} directly rather than caching a snapshot,
 * so an external charger's {@code receiveEnergy} call is visible to the very next capability query
 * on the same stack.
 */
public record EnergyBuffer(int capacity, float energyPerDurabilityPoint) implements Trait {

    /** The buffer's current amount, {@code 0} if the component is absent. */
    public static int stored(ItemStack stack) {
        return stack.getOrDefault(ForgeweaveDataComponents.ENERGY.get(), 0);
    }

    private static void setStored(ItemStack stack, int amount) {
        if (amount <= 0) {
            stack.remove(ForgeweaveDataComponents.ENERGY.get());
        } else {
            stack.set(ForgeweaveDataComponents.ENERGY.get(), amount);
        }
    }

    /**
     * Adds up to {@code toReceive} FE, clamped so the buffer never exceeds {@code capacity} --
     * {@link IEnergyStorage#receiveEnergy}'s own "amount accepted" contract, shared by the item
     * capability, {@link SolarRecharge} and {@link KineticCharge} so all three fill the same way.
     */
    public static int receive(ItemStack stack, int capacity, int toReceive, boolean simulate) {
        if (capacity <= 0 || toReceive <= 0) {
            return 0;
        }
        int stored = stored(stack);
        int accepted = Math.min(toReceive, capacity - stored);
        if (accepted <= 0) {
            return 0;
        }
        if (!simulate) {
            setStored(stack, stored + accepted);
        }
        return accepted;
    }

    /** Removes up to {@code toExtract} FE -- {@link IEnergyStorage#extractEnergy}'s contract. */
    public static int extract(ItemStack stack, int toExtract, boolean simulate) {
        if (toExtract <= 0) {
            return 0;
        }
        int stored = stored(stack);
        int removed = Math.min(toExtract, stored);
        if (removed <= 0) {
            return 0;
        }
        if (!simulate) {
            setStored(stack, stored - removed);
        }
        return removed;
    }

    /** See the class javadoc: {@code null} for a tool whose traits sum to zero energy capacity. */
    public static IEnergyStorage capability(ItemStack stack) {
        int capacity = ForgeweaveTraits.energyCapacity(stack);
        if (capacity <= 0) {
            return null;
        }
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int toReceive, boolean simulate) {
                return EnergyBuffer.receive(stack, capacity, toReceive, simulate);
            }

            @Override
            public int extractEnergy(int toExtract, boolean simulate) {
                return EnergyBuffer.extract(stack, toExtract, simulate);
            }

            @Override
            public int getEnergyStored() {
                return EnergyBuffer.stored(stack);
            }

            @Override
            public int getMaxEnergyStored() {
                return capacity;
            }

            @Override
            public boolean canExtract() {
                return true;
            }

            @Override
            public boolean canReceive() {
                return true;
            }
        };
    }

    @Override
    public int energyCapacity() {
        return capacity;
    }

    @Override
    public int durabilityDamage(ItemStack stack, RandomSource random, int originalAmount, int amount) {
        if (amount <= 0 || energyPerDurabilityPoint <= 0.0F) {
            return amount;
        }
        int stored = stored(stack);
        if (stored <= 0) {
            return amount;
        }
        int coverable = Math.min(amount, (int) (stored / energyPerDurabilityPoint));
        if (coverable <= 0) {
            return amount;
        }
        setStored(stack, stored - Math.round(coverable * energyPerDurabilityPoint));
        return amount - coverable;
    }
}
