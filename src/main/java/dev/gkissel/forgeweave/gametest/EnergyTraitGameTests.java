package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.EnergyBuffer;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #830's verification: one test per registered trait id (energized, solar_recharge,
 * kinetic_charge) plus the item capability's own round trip, per the issue's test strategy list.
 * Like {@link MiningTraitGameTests}/{@link CombatTraitGameTests}, tools are assembled by hand
 * ({@link #pickaxe}/{@link #hatchet}) with the trait ids set directly -- material wiring is a later
 * M6 issue. Magnitudes asserted here (32,000 FE capacity, 40 FE/durability point, 2 FE/tick solar,
 * 5 FE/damage kinetic) are {@code ForgeweaveTraits}' proposed issue #830 numbers; a maintainer
 * retune updates both places together, same as every other M6 batch's tests.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EnergyTraitGameTests {

    /** {@code forgeweave:energized}: spends the buffer before durability, then falls back once empty. */
    @GameTest(template = "empty")
    public static void energizedSpendsEnergyBeforeDurabilityThenFallsBackOnceEmpty(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("energized")), 1000);
        pickaxe.set(ForgeweaveDataComponents.ENERGY.get(), 400); // 400 FE / 40 per point = 10 points covered
        RandomSource random = helper.getLevel().getRandom();

        int fullyCovered = ForgeweaveTraits.durabilityDamage(pickaxe, random, 6);
        helper.assertTrue(fullyCovered == 0,
                "a full-enough buffer must take no durability damage, got " + fullyCovered);
        helper.assertTrue(EnergyBuffer.stored(pickaxe) == 160,
                "expected 400 - 6*40 = 160 FE left, got " + EnergyBuffer.stored(pickaxe));

        int partiallyCovered = ForgeweaveTraits.durabilityDamage(pickaxe, random, 6);
        helper.assertTrue(partiallyCovered == 2,
                "the remaining 160 FE covers only 4 of the 6 points, expected 2 left over, got " + partiallyCovered);
        helper.assertTrue(EnergyBuffer.stored(pickaxe) == 0, "the buffer must be drained to zero");

        int emptyBuffer = ForgeweaveTraits.durabilityDamage(pickaxe, random, 5);
        helper.assertTrue(emptyBuffer == 5, "an empty buffer must pay the full durability cost, got " + emptyBuffer);

        helper.succeed();
    }

    /**
     * The item capability's own round trip: {@code Capabilities.EnergyStorage.ITEM}'s per-stack
     * provider ({@link EnergyBuffer#capability}) reports the trait's capacity, accepts energy up to
     * it, respects {@code simulate}, and extracts.
     */
    @GameTest(template = "empty")
    public static void theCapabilityReportsCapacityAndRespectsSimulateOnInsertAndExtract(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("energized")), 1000);
        IEnergyStorage capability = EnergyBuffer.capability(pickaxe);
        helper.assertTrue(capability != null, "a tool with the energized trait must expose the capability");
        helper.assertTrue(capability.getMaxEnergyStored() == 32000,
                "expected the proposed 32,000 FE capacity, got " + capability.getMaxEnergyStored());

        int simulated = capability.receiveEnergy(50000, true);
        helper.assertTrue(simulated == 32000, "a simulated insert must clamp to capacity, got " + simulated);
        helper.assertTrue(capability.getEnergyStored() == 0, "a simulated insert must not mutate the stack");

        int accepted = capability.receiveEnergy(20000, false);
        helper.assertTrue(accepted == 20000, "expected the full 20,000 FE accepted, got " + accepted);
        int topUp = capability.receiveEnergy(20000, false);
        helper.assertTrue(topUp == 12000, "expected only the remaining 12,000 FE accepted, got " + topUp);
        helper.assertTrue(capability.getEnergyStored() == 32000, "the buffer must now read full");

        int extracted = capability.extractEnergy(5000, false);
        helper.assertTrue(extracted == 5000, "expected 5,000 FE extracted, got " + extracted);
        helper.assertTrue(capability.getEnergyStored() == 27000,
                "expected 27,000 FE left after the extraction, got " + capability.getEnergyStored());

        helper.succeed();
    }

    /** Round trip: energy inserted through the capability is what {@code durabilityDamage} then spends. */
    @GameTest(template = "empty")
    public static void energyInsertedThroughTheCapabilityIsConsumedByDurabilityDamage(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(traitId("energized")), 1000);
        IEnergyStorage capability = EnergyBuffer.capability(pickaxe);
        helper.assertTrue(capability != null, "setup: the capability must be present");
        capability.receiveEnergy(400, false);

        RandomSource random = helper.getLevel().getRandom();
        int covered = ForgeweaveTraits.durabilityDamage(pickaxe, random, 10);
        helper.assertTrue(covered == 0,
                "the 400 FE charged through the capability must cover all 10 durability points, got " + covered);
        helper.assertTrue(EnergyBuffer.stored(pickaxe) == 0, "the charge must be fully spent");

        int afterDrained = ForgeweaveTraits.durabilityDamage(pickaxe, random, 3);
        helper.assertTrue(afterDrained == 3, "with the charge spent, durability must pay the full cost again");

        helper.succeed();
    }

    /** A tool with no energy trait exposes no capability and sums to zero capacity. */
    @GameTest(template = "empty")
    public static void aToolWithNoEnergyTraitExposesNoCapability(GameTestHelper helper) {
        ItemStack pickaxe = pickaxe(List.of(), 1000);
        helper.assertTrue(ForgeweaveTraits.energyCapacity(pickaxe) == 0, "expected zero summed capacity");
        helper.assertTrue(EnergyBuffer.capability(pickaxe) == null,
                "a tool with no energy trait must expose no energy capability");
        helper.succeed();
    }

    /** {@code forgeweave:solar_recharge}: fills at the stated rate in daylight, not underground or at night. */
    @GameTest(template = "empty")
    public static void solarRechargeFillsInDaylightNotUndergroundOrAtNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(2, 2, 2));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("energized"), traitId("solar_recharge")), 1000);

        level.setDayTime(6000); // noon
        helper.runAfterDelay(5, () -> {
            pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
            int afterDaylight = EnergyBuffer.stored(pickaxe);
            helper.assertTrue(afterDaylight == 2,
                    "expected 2 FE gained on one daylight tick under open sky, got " + afterDaylight);

            helper.setBlock(new BlockPos(2, 6, 2), Blocks.STONE);
            helper.runAfterDelay(5, () -> {
                pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
                int underRoof = EnergyBuffer.stored(pickaxe);
                helper.assertTrue(underRoof == afterDaylight,
                        "must not recharge without open sky even at noon, got " + underRoof);

                helper.setBlock(new BlockPos(2, 6, 2), Blocks.AIR);
                level.setDayTime(18000); // midnight
                helper.runAfterDelay(5, () -> {
                    pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
                    int atNight = EnergyBuffer.stored(pickaxe);
                    helper.assertTrue(atNight == afterDaylight, "must not recharge at night, got " + atNight);
                    helper.succeed();
                });
            });
        });
    }

    /** {@code forgeweave:kinetic_charge}: converts damage dealt into stored energy. */
    @GameTest(template = "empty")
    public static void kineticChargeAddsEnergyProportionalToDamageDealt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = hatchet(List.of(traitId("energized"), traitId("kinetic_charge")), 1000);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        CombatHit hit = new CombatHit(helper.getLevel(), hatchet, player, target,
                helper.getLevel().damageSources().playerAttack(player));
        for (CombatSeam seam : CombatSeams.seams(hatchet)) {
            seam.onHit(hit, 4.0F);
        }

        int gained = EnergyBuffer.stored(hatchet);
        helper.assertTrue(gained == 20, "expected 4.0 damage * 5.0 FE/damage = 20 FE gained, got " + gained);

        target.discard();
        helper.succeed();
    }

    // ------------------------------------------------------------------ plumbing

    private static ItemStack pickaxe(List<ResourceLocation> traits, int durability) {
        return tool(ForgeweaveItems.TOOL_PICKAXE.get(), traits, durability, 1.0F, 1.0F);
    }

    private static ItemStack hatchet(List<ResourceLocation> traits, int durability) {
        return tool(ForgeweaveItems.TOOL_HATCHET.get(), traits, durability, 1.0F, 3.0F);
    }

    /** Builds a tool {@code ItemStack} with the given traits/stats directly (see class javadoc). */
    private static ItemStack tool(ToolItem toolItem, List<ResourceLocation> traits, int durability,
            float miningSpeed, float attackDamage) {
        ToolStats.Stats stats = new ToolStats.Stats(durability, miningSpeed, attackDamage);
        Material head = new Material(
                new Material.Head(durability, miningSpeed, attackDamage),
                new Material.Handle(1.0F, 0),
                0,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));

        ItemStack stack = new ItemStack(toolItem);
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        stack.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        stack.set(DataComponents.TOOL, toolItem.toolComponent(head, stats));
        stack.set(DataComponents.MAX_DAMAGE, durability);
        stack.set(DataComponents.DAMAGE, 0);
        return stack;
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
