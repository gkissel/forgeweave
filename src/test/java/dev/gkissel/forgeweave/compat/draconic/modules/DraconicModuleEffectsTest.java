package dev.gkissel.forgeweave.compat.draconic.modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import com.brandon3055.brandonscore.api.TechLevel;
import com.brandon3055.draconicevolution.api.capability.ModuleHost;
import com.brandon3055.draconicevolution.api.config.ConfigProperty;
import com.brandon3055.draconicevolution.api.modules.Module;
import com.brandon3055.draconicevolution.api.modules.ModuleCategory;
import com.brandon3055.draconicevolution.api.modules.ModuleType;
import com.brandon3055.draconicevolution.api.modules.ModuleTypes;
import com.brandon3055.draconicevolution.api.modules.data.AOEData;
import com.brandon3055.draconicevolution.api.modules.data.DamageData;
import com.brandon3055.draconicevolution.api.modules.data.ModuleData;
import com.brandon3055.draconicevolution.api.modules.data.ProjectileData;
import com.brandon3055.draconicevolution.api.modules.data.SpeedData;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleContext;
import com.brandon3055.draconicevolution.api.modules.lib.ModuleEntity;
import com.brandon3055.draconicevolution.init.EquipCfg;

import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.trait.EnergyBuffer;

/**
 * Issue #956 phase 2: what each tool-active module effect works out, and what it works out for a
 * tool that is not a host at all.
 *
 * <p>Runs with Draconic Evolution and BrandonsCore on the test classpath only, same rows
 * {@code DraconicModuleHostTest} uses. A real host cannot be built here -- it loads and saves through
 * data component types that exist only on a running install -- so every arithmetic case drives
 * {@link DraconicModuleEffects}'s host-taking overloads through {@link StubHost}, which answers with
 * the module data records directly. Those records are plain and constructible; what is not
 * constructible is the module registry behind them.
 *
 * <p>Draconic Evolution's own {@code EquipCfg} energy costs are live in this JVM, so the setup below
 * zeroes all three -- every energy gate open -- and each test turns on only the one it is about.
 */
class DraconicModuleEffectsTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static int shippedEnergyHarvest;
    private static int shippedEnergyAttack;
    private static int shippedBowBaseEnergy;

    /**
     * Draconic Evolution's own config values are live in this JVM, so each test starts with the three
     * energy costs at zero -- every gate open -- and turns on only the one it is about. The shipped
     * numbers go back afterwards so nothing here leaks into another test class.
     */
    @BeforeEach
    void silenceDraconicEnergyCosts() {
        shippedEnergyHarvest = EquipCfg.energyHarvest;
        shippedEnergyAttack = EquipCfg.energyAttack;
        shippedBowBaseEnergy = EquipCfg.bowBaseEnergy;
        EquipCfg.energyHarvest = 0;
        EquipCfg.energyAttack = 0;
        EquipCfg.bowBaseEnergy = 0;
    }

    @AfterEach
    void restoreDraconicEnergyCosts() {
        EquipCfg.energyHarvest = shippedEnergyHarvest;
        EquipCfg.energyAttack = shippedEnergyAttack;
        EquipCfg.bowBaseEnergy = shippedBowBaseEnergy;
    }

    /** A stack that is not a host: no {@code evolved} trait, so no module effect may touch it. */
    private static ItemStack plainStack() {
        return new ItemStack(Items.DIAMOND_PICKAXE);
    }

    @Test
    void aToolWithNoHostGetsNoEffectAtAll() {
        ItemStack stack = plainStack();

        assertEquals(1.0F, DraconicModuleEffects.digSpeedMultiplier(stack));
        assertEquals(0, DraconicModuleEffects.miningAoe(stack));
        assertEquals(0, DraconicModuleEffects.miningEnergyCost(stack));
        assertEquals(0.0F, DraconicModuleEffects.attackDamageBonus(stack));
        assertSame(DraconicModules.Projectile.NONE, DraconicModuleEffects.projectile(stack));
        assertEquals(0, DraconicModuleEffects.shotEnergyCost(stack));
    }

    @Test
    void aHostWithNoModulesChangesNothing() {
        ItemStack stack = plainStack();
        StubHost host = new StubHost();

        assertEquals(1.0F, DraconicModuleEffects.digSpeedMultiplier(host, stack));
        assertEquals(0, DraconicModuleEffects.miningAoe(host));
        assertEquals(0, DraconicModuleEffects.miningEnergyCost(host));
        assertEquals(0.0F, DraconicModuleEffects.attackDamageBonus(host, stack));
        assertEquals(0.0, DraconicModuleEffects.meleeAoeRadius(host));
        assertSame(DraconicModules.Projectile.NONE, DraconicModuleEffects.projectile(host, stack));
        assertEquals(0, DraconicModuleEffects.shotEnergyCost(host));
    }

    /**
     * Draconic Evolution's own curve out of {@code IModularItem#handleTick}: the squared speed mapped
     * from {@code [1, 2]} onto {@code [1, 1.65]}. A speed module of 1 squares to 4, which is three
     * whole steps past 1, so 1 + 3 * 0.65.
     */
    @Test
    void aSpeedModuleMultipliesDigSpeedAlongDraconicsOwnCurve() {
        ItemStack stack = plainStack();
        StubHost host = new StubHost().with(ModuleTypes.SPEED, new SpeedData(1.0));

        assertEquals(2.95F, DraconicModuleEffects.digSpeedMultiplier(host, stack), 1.0E-5F);
    }

    /** The maintainer's rule stated as a clamp: a module never makes a Forgeweave tool worse. */
    @Test
    void digSpeedNeverFallsBelowTheToolsOwn() {
        ItemStack stack = plainStack();
        StubHost host = new StubHost().with(ModuleTypes.SPEED, new SpeedData(-0.9));

        assertEquals(1.0F, DraconicModuleEffects.digSpeedMultiplier(host, stack));
    }

    @Test
    void anEmptyBufferLeavesDigSpeedAlone() {
        ItemStack stack = plainStack();
        StubHost host = new StubHost().with(ModuleTypes.SPEED, new SpeedData(1.0));
        EquipCfg.energyHarvest = 32;

        assertEquals(1.0F, DraconicModuleEffects.digSpeedMultiplier(host, stack));

        EnergyBuffer.receive(stack, 1_000, 1_000, false);
        assertEquals(2.95F, DraconicModuleEffects.digSpeedMultiplier(host, stack), 1.0E-5F);
    }

    @Test
    void anAreaModuleIsARadiusAndCostsEnergyPerBlock() {
        StubHost host = new StubHost().with(ModuleTypes.AOE, new AOEData(2));
        EquipCfg.energyHarvest = 32;

        assertEquals(2, DraconicModuleEffects.miningAoe(host));
        assertEquals(32, DraconicModuleEffects.miningEnergyCost(host));
        // IModularMelee#onLeftClickEntity's own aoe * 1.5 radius, in blocks.
        assertEquals(3.0, DraconicModuleEffects.meleeAoeRadius(host));
    }

    @Test
    void aToolWithNoMiningModulePaysNothingToBreakABlock() {
        StubHost host = new StubHost().with(ModuleTypes.DAMAGE, new DamageData(4.0));
        EquipCfg.energyHarvest = 32;

        assertEquals(0, DraconicModuleEffects.miningEnergyCost(host));
    }

    @Test
    void aDamageModuleAddsItsOwnPoints() {
        ItemStack stack = plainStack();
        StubHost host = new StubHost().with(ModuleTypes.DAMAGE, new DamageData(4.0));

        assertEquals(4.0F, DraconicModuleEffects.attackDamageBonus(host, stack));
    }

    @Test
    void anEmptyBufferAddsNoAttackDamage() {
        ItemStack stack = plainStack();
        StubHost host = new StubHost().with(ModuleTypes.DAMAGE, new DamageData(4.0));
        EquipCfg.energyAttack = 100;

        assertEquals(0.0F, DraconicModuleEffects.attackDamageBonus(host, stack));

        // Draconic Evolution's gate is energyAttack per damage point, so 4 points want 400.
        EnergyBuffer.receive(stack, 400, 400, false);
        assertEquals(4.0F, DraconicModuleEffects.attackDamageBonus(host, stack));
    }

    @Test
    void aProjectileModuleCarriesItsThreeNumbersThrough() {
        ItemStack stack = plainStack();
        StubHost host = new StubHost()
                .with(ModuleTypes.PROJ_MODIFIER, new ProjectileData(0.5F, 0.25F, 1.0F, 0.0F, 2.0F));

        DraconicModules.Projectile projectile = DraconicModuleEffects.projectile(host, stack);

        assertEquals(0.5F, projectile.velocity());
        assertEquals(0.25F, projectile.accuracy());
        assertEquals(2.0F, projectile.damage());
    }

    /** {@code ModularBow#calculateShotEnergy}: {@code bowBaseEnergy * 2 * (1 + damage) * 3 * (1 + velocity)}. */
    @Test
    void aShotCostsDraconicsOwnPerShotEnergy() {
        StubHost host = new StubHost()
                .with(ModuleTypes.PROJ_MODIFIER, new ProjectileData(0.5F, 0.0F, 0.0F, 0.0F, 1.0F));
        EquipCfg.bowBaseEnergy = 100;

        // 2 * (1 + 1) * 3 * (1 + 0.5) = 18, times 100.
        assertEquals(1_800, DraconicModuleEffects.shotEnergyCost(host));
    }

    @Test
    void aBufferTooEmptyForTheShotLeavesTheBowAtItsOwnNumbers() {
        ItemStack stack = plainStack();
        StubHost host = new StubHost()
                .with(ModuleTypes.PROJ_MODIFIER, new ProjectileData(0.5F, 0.0F, 0.0F, 0.0F, 1.0F));
        EquipCfg.bowBaseEnergy = 100;

        assertSame(DraconicModules.Projectile.NONE, DraconicModuleEffects.projectile(host, stack));

        EnergyBuffer.receive(stack, 1_800, 1_800, false);
        assertEquals(0.5F, DraconicModuleEffects.projectile(host, stack).velocity());
    }

    @Test
    void anAllZeroProjectileModuleIsNotWorthApplying() {
        ItemStack stack = plainStack();
        StubHost host = new StubHost()
                .with(ModuleTypes.PROJ_MODIFIER, new ProjectileData(0.0F, 0.0F, 0.0F, 0.0F, 0.0F));

        assertSame(DraconicModules.Projectile.NONE, DraconicModuleEffects.projectile(host, stack));
        assertFalse(DraconicModules.Projectile.NONE.any());
    }

    /**
     * A {@link ModuleHost} that answers with canned module data and nothing else. Only
     * {@link #getModuleData(ModuleType)} and the three property probes are ever reached from
     * {@link DraconicModuleEffects}; the rest are here because the interface has them.
     */
    private static final class StubHost implements ModuleHost {

        private ModuleType<?> type;
        private ModuleData<?> data;

        StubHost with(ModuleType<?> moduleType, ModuleData<?> moduleData) {
            this.type = moduleType;
            this.data = moduleData;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T extends ModuleData<T>> T getModuleData(ModuleType<T> requested) {
            return requested == type ? (T) data : null;
        }

        @Override
        public Stream<Module<?>> getModules() {
            return Stream.of();
        }

        @Override
        public List<ModuleEntity<?>> getModuleEntities() {
            return List.of();
        }

        @Override
        public void addModule(ModuleEntity<?> entity, ModuleContext context) {}

        @Override
        public void removeModule(ModuleEntity<?> entity, ModuleContext context) {}

        @Override
        public Collection<ModuleCategory> getModuleCategories() {
            return List.of();
        }

        @Override
        public TechLevel getHostTechLevel() {
            return TechLevel.WYVERN;
        }

        @Override
        public int getGridWidth() {
            return 2;
        }

        @Override
        public int getGridHeight() {
            return 1;
        }

        @Override
        public boolean checkRemoveModule(ModuleEntity<?> entity, List<Component> reason) {
            return true;
        }

        @Override
        public void handleTick(ModuleContext context) {}

        @Override
        public String getProviderName() {
            return "stub";
        }

        @Override
        public Collection<ConfigProperty> getProperties() {
            return List.of();
        }

        @Nullable
        @Override
        public ConfigProperty getProperty(String name) {
            return null;
        }

        @Override
        public UUID getIdentity() {
            return UUID.nameUUIDFromBytes(new byte[] {0});
        }

        @Override
        public void regenIdentity() {}

        @Override
        public void markDirty() {}

        @Override
        public void close() {}
    }
}
