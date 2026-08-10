package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.material.Fluids;

import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Save compatibility for the smeltery's molten contents across the issue #101 tank change.
 *
 * <p>Issue #95 stored the tank as a single-fluid NeoForge {@code FluidTank}, which writes one
 * {@code Fluid} compound. {@link SmelteryTank} stores an ordered {@code fluids} list instead. Every
 * world saved on master before #101 has the old shape, and a load path that only understood the new
 * one would empty those smelteries silently -- no crash, no log line, just missing metal. That is
 * the failure this pins.
 *
 * <p>The committed fixtures themselves are covered by {@code SaveCompatCorpusTest}, which decodes
 * every one of them through the real save path; this class covers the cases a fixture cannot -- an
 * empty legacy tag, and the one-way rewrite into the new shape.
 */
class SmelteryTankSaveCompatTest {

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
    }

    private static SmelteryTank tank() {
        return new SmelteryTank(8 * 144, () -> {});
    }

    /** The shape issue #95's {@code FluidTank} wrote: one fluid, under {@code Fluid}. */
    @Test
    void aPreIssue101SingleFluidTankStillLoads() {
        CompoundTag legacy = new CompoundTag();
        legacy.put("Fluid", new FluidStack(Fluids.LAVA, 777).save(registries));

        SmelteryTank tank = tank();
        tank.readFromNBT(registries, legacy);

        assertEquals(1, tank.fluids().size(), "a legacy tank holds exactly the one fluid it saved");
        assertEquals(777, tank.getFluidAmount(), "the legacy amount must survive the upgrade");
        assertTrue(tank.getFluid().is(Fluids.LAVA), "and so must which fluid it was");
    }

    /** An empty legacy tag wrote no {@code Fluid} key at all; that must read as empty, not crash. */
    @Test
    void anEmptyLegacyTankLoadsAsEmpty() {
        SmelteryTank tank = tank();
        tank.readFromNBT(registries, new CompoundTag());
        assertTrue(tank.fluids().isEmpty(), "a tank that saved nothing must load as empty");
    }

    /** Once loaded, the next save is the new shape -- the conversion is one-way and happens on load. */
    @Test
    void aLegacyTankIsRewrittenInTheListShape() {
        CompoundTag legacy = new CompoundTag();
        legacy.put("Fluid", new FluidStack(Fluids.LAVA, 500).save(registries));

        SmelteryTank tank = tank();
        tank.readFromNBT(registries, legacy);
        CompoundTag rewritten = tank.writeToNBT(registries, new CompoundTag());

        assertTrue(rewritten.contains("fluids"), "a re-saved tank uses the list shape");
        SmelteryTank reloaded = tank();
        reloaded.readFromNBT(registries, rewritten);
        assertEquals(500, reloaded.getFluidAmount(), "and round-trips through it unchanged");
    }

}
