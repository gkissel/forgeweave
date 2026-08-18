package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * {@link ToolItem#canPerformAction} per tool kind (parity audit 2026-08-18 T33, issue #464).
 *
 * <p>Upstream 1.12 declares a tool's kind with Forge's {@code setHarvestLevel(<tool class>, 0)}:
 * {@code Pickaxe} "pickaxe", {@code Shovel} "shovel", {@code Hatchet}/{@code LumberAxe}/
 * {@code BattleAxe} "axe", {@code Kama} "shears", and {@code Mattock} its own class plus a
 * {@code getHarvestLevel} override answering both "axe" and "shovel". 1.21's counterpart is the
 * {@link ItemAbility} set an item answers {@code canPerformAction} for, and Forgeweave derives that
 * from the {@code mineable/*} tags each tool is already registered with, so a tool's kind stays
 * declared in exactly one place.
 */
class ToolAbilityTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static boolean can(Item item, ItemAbility ability) {
        return new ItemStack(item).canPerformAction(ability);
    }

    @Test
    void pickaxeFamilyDigsLikeAPickaxeAndNothingElse() {
        for (Item pickaxe : new Item[] {ForgeweaveItems.TOOL_PICKAXE.get(), ForgeweaveItems.TOOL_HAMMER.get(),
                ForgeweaveItems.TOOL_VEIN_HAMMER.get()}) {
            assertTrue(can(pickaxe, ItemAbilities.PICKAXE_DIG), pickaxe + " must dig as a pickaxe");
            assertFalse(can(pickaxe, ItemAbilities.AXE_STRIP), pickaxe + " must not strip logs");
            assertFalse(can(pickaxe, ItemAbilities.SHOVEL_FLATTEN), pickaxe + " must not make paths");
        }
    }

    /** The audit row this ticket is named for: shovel and excavator make grass paths. */
    @Test
    void shovelFamilyFlattens() {
        for (Item shovel : new Item[] {ForgeweaveItems.TOOL_SHOVEL.get(), ForgeweaveItems.TOOL_EXCAVATOR.get()}) {
            assertTrue(can(shovel, ItemAbilities.SHOVEL_DIG), shovel + " must dig as a shovel");
            assertTrue(can(shovel, ItemAbilities.SHOVEL_FLATTEN), shovel + " must make grass paths");
            assertFalse(can(shovel, ItemAbilities.PICKAXE_DIG), shovel + " must not dig as a pickaxe");
        }
    }

    /**
     * The axe family digs as an axe and claims nothing else. Log stripping, copper scraping and wax
     * removal postdate 1.12 entirely and Forgeweave implements none of them yet -- upstream 1.20 keeps
     * them in a separate {@code stripping} trait, which issue #575 tracks porting.
     */
    @Test
    void axeFamilyDigsLikeAnAxeAndClaimsNothingItCannotDo() {
        for (Item axe : new Item[] {ForgeweaveItems.TOOL_HATCHET.get(), ForgeweaveItems.TOOL_LUMBERAXE.get(),
                ForgeweaveItems.TOOL_BATTLEAXE.get()}) {
            assertTrue(can(axe, ItemAbilities.AXE_DIG), axe + " must dig as an axe");
            assertFalse(can(axe, ItemAbilities.AXE_STRIP), axe + " must not claim stripping it cannot do");
            assertFalse(can(axe, ItemAbilities.SHOVEL_FLATTEN), axe + " must not make paths");
        }
    }

    /**
     * Upstream {@code Mattock#getHarvestLevel} answers both "axe" and "shovel" and its {@code onItemUse}
     * hoes; 1.20 spells the same tool {@code ToolActionsModule.of(AXE_DIG, SHOVEL_DIG)} plus the
     * {@code tilling} trait. No upstream mattock has ever made a grass path.
     */
    @Test
    void mattockIsAxeShovelAndHoeButNeverPaths() {
        Item mattock = ForgeweaveItems.TOOL_MATTOCK.get();
        assertTrue(can(mattock, ItemAbilities.AXE_DIG));
        assertTrue(can(mattock, ItemAbilities.SHOVEL_DIG));
        assertTrue(can(mattock, ItemAbilities.HOE_TILL));
        assertFalse(can(mattock, ItemAbilities.SHOVEL_FLATTEN), "a mattock must not make grass paths");
    }

    /** Upstream 1.20's kama is {@code ToolActionsModule.of(HOE_DIG)}; its shearing needs no ability. */
    @Test
    void hoeFamilyDigsLikeAHoeOnly() {
        for (Item hoe : new Item[] {ForgeweaveItems.TOOL_KAMA.get(), ForgeweaveItems.TOOL_SCYTHE.get()}) {
            assertTrue(can(hoe, ItemAbilities.HOE_DIG), hoe + " must dig as a hoe");
            assertFalse(can(hoe, ItemAbilities.HOE_TILL), hoe + " must not till");
            assertFalse(can(hoe, ItemAbilities.SHEARS_CARVE), hoe + " must not carve pumpkins");
        }
    }

    /**
     * Sword-family tools get every default sword ability but {@code SWORD_SWEEP}: upstream's
     * {@code SwordCore} extends {@code TinkerToolCore}, not vanilla's {@code ItemSword}, so 1.12's
     * automatic sweep never reached it -- and {@code BroadswordSweep} already ports the by-hand sweep
     * upstream wrote instead. Letting vanilla's gate through as well would sweep twice.
     */
    @Test
    void swordFamilyDigsLikeASwordButNeverSweepsTwice() {
        for (Item sword : new Item[] {ForgeweaveItems.TOOL_BROADSWORD.get(), ForgeweaveItems.TOOL_LONGSWORD.get(),
                ForgeweaveItems.TOOL_RAPIER.get(), ForgeweaveItems.TOOL_DAGGER.get(),
                ForgeweaveItems.TOOL_SCIMITAR.get(), ForgeweaveItems.TOOL_KATANA.get(),
                ForgeweaveItems.TOOL_CLEAVER.get()}) {
            assertTrue(can(sword, ItemAbilities.SWORD_DIG), sword + " must dig as a sword");
            assertFalse(can(sword, ItemAbilities.SWORD_SWEEP), sword + " must not use vanilla's sweep gate");
        }
    }

    /** Upstream gives the frying pan, battlesign and warmace no tool class at all. */
    @Test
    void bludgeonsHaveNoToolClass() {
        for (Item bludgeon : new Item[] {ForgeweaveItems.TOOL_FRYING_PAN.get(), ForgeweaveItems.TOOL_BATTLESIGN.get(),
                ForgeweaveItems.TOOL_WARMACE.get()}) {
            assertFalse(can(bludgeon, ItemAbilities.SWORD_DIG), bludgeon + " must not dig as a sword");
            assertFalse(can(bludgeon, ItemAbilities.AXE_DIG), bludgeon + " must not dig as an axe");
            assertFalse(can(bludgeon, ItemAbilities.SHOVEL_FLATTEN), bludgeon + " must not make paths");
        }
    }

    /** Upstream refuses every {@code onItemUse} on a Broken tool ({@code ToolHelper.isBroken} -> FAIL). */
    @Test
    void aBrokenToolPerformsNothing() {
        ItemStack shovel = new ItemStack(ForgeweaveItems.TOOL_SHOVEL.get());
        assertTrue(shovel.canPerformAction(ItemAbilities.SHOVEL_FLATTEN));
        shovel.set(ForgeweaveDataComponents.BROKEN.get(), true);
        assertFalse(shovel.canPerformAction(ItemAbilities.SHOVEL_FLATTEN));
        assertFalse(shovel.canPerformAction(ItemAbilities.SHOVEL_DIG));

        ItemStack mattock = new ItemStack(ForgeweaveItems.TOOL_MATTOCK.get());
        mattock.set(ForgeweaveDataComponents.BROKEN.get(), true);
        assertFalse(mattock.canPerformAction(ItemAbilities.HOE_TILL));
    }
}
