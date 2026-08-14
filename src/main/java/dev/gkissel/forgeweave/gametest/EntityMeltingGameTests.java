package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryTank;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe;

/**
 * Issue #270's verification: an entity standing in a formed smeltery that already holds molten
 * content is damaged on upstream's own cadence and bleeds its fluid into the tank.
 *
 * <p>Every number here is read off upstream 1.12's {@code TileSmeltery#interactWithEntitiesInside}
 * (pinned {@code c01173c}), which is the whole mechanic in twenty lines: the sweep runs on
 * {@code tick == 0} of a {@code % 20} counter, the hit is a flat {@code 2f}, the tank is filled only
 * when {@code attackEntityFrom} returned true, the living-entity branch is gated on
 * {@code liquids.getFluidAmount() > 0}, and anything living with no registration of its own is worth
 * {@code new FluidStack(TinkerFluids.blood, 20)}.
 *
 * <p>These drive {@link SmelteryControllerBlockEntity#sweepInterior()} directly rather than waiting on
 * the block's scheduler, the same way {@link SmelteryMeltingGameTests}'s finishing-tick assertions
 * drive {@code meltTick()} -- the assertion then sits on an exact sweep instead of a timing-dependent
 * sample. {@link #aSweepHitsAtMostOncePerSecondNoMatterHowOftenItIsCalled} is the one that pins the
 * cadence itself, so the rest are free to call it as often as they like.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EntityMeltingGameTests {

    /**
     * The default-blood fallback, upstream's own {@code fluid = new FluidStack(TinkerFluids.blood, 20)}
     * for a living entity with no registration. A cow has no entity-melting recipe in this repo, so one
     * sweep costs it {@link EntityMeltingRecipe#DAMAGE} and pours exactly
     * {@link EntityMeltingRecipe#DEFAULT_AMOUNT} of blood on top of the primer.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aMobWithNoRecipeBleedsTheDefaultBlood(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = primedSmeltery(helper);
        Cow cow = spawnInside(helper, EntityType.COW);
        float before = cow.getHealth();

        core.sweepInterior();

        helper.assertValueEqual(cow.getHealth(), before - EntityMeltingRecipe.DAMAGE, "cow health after one sweep");
        assertTankGained(helper, core, ForgeweaveFluids.BLOOD.still().get(), EntityMeltingRecipe.DEFAULT_AMOUNT);
        helper.succeed();
    }

    /**
     * The blaze row of #270's table, and the one row that proves the damage type had to be split. A
     * blaze is fire-immune, so upstream 1.12's single fire-flavoured {@code smeltery} damage source
     * could never land on it -- the tank would stay at its primer forever. See
     * {@code SmelteryControllerBlockEntity#meltEntity}.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aFireImmuneBlazeStillMeltsIntoBlazingBlood(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = primedSmeltery(helper);
        Blaze blaze = spawnInside(helper, EntityType.BLAZE);
        float before = blaze.getHealth();

        core.sweepInterior();

        helper.assertTrue(blaze.getHealth() < before, "expected a fire-immune blaze to still take smeltery damage");
        assertTankGained(helper, core, ForgeweaveFluids.BLAZING_BLOOD.still().get(), 20);
        helper.succeed();
    }

    /**
     * The illager half of the 1.12 parity set: {@code entity_melting_recipe/emerald_mobs.json} names
     * four entity types in one file, so this proves the list form of the {@code entities} field
     * actually matches -- a villager is not the first id in it.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aVindicatorMeltsIntoEmeraldOffAMultiEntityRecipe(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = primedSmeltery(helper);
        spawnInside(helper, EntityType.VINDICATOR);

        core.sweepInterior();

        assertTankGained(helper, core, ForgeweaveFluids.EMERALD.still().get(), 6);
        helper.succeed();
    }

    /**
     * Upstream's iron golem row, at its own 18 mB rather than the 20 the default would have given --
     * so this fails if {@link EntityMeltingRecipe#find} silently misses and the fallback takes over.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void anIronGolemMeltsIntoIronAtUpstreamsOwnAmount(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = primedSmeltery(helper);
        spawnInside(helper, EntityType.IRON_GOLEM);

        core.sweepInterior();

        assertTankGained(helper, core, ForgeweaveFluids.IRON.still().get(), 18);
        helper.succeed();
    }

    /**
     * Upstream's damage cadence: {@code interactWithEntitiesInside} fires on one tick in twenty, so a
     * mob standing in a smeltery takes one hit a second no matter how often the block itself ticks --
     * and the block does tick more often than that, every
     * {@value SmelteryControllerBlockEntity#MELT_INTERVAL_TICKS} ticks while melting. Three calls
     * inside the same second must therefore land exactly one hit.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aSweepHitsAtMostOncePerSecondNoMatterHowOftenItIsCalled(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = primedSmeltery(helper);
        Cow cow = spawnInside(helper, EntityType.COW);
        float before = cow.getHealth();

        helper.assertTrue(core.sweepInterior(), "expected the first sweep to run");
        helper.assertTrue(!core.sweepInterior(), "expected a second sweep in the same tick to be throttled");
        helper.assertTrue(!core.sweepInterior(), "expected a third sweep in the same tick to be throttled");

        helper.assertValueEqual(cow.getHealth(), before - EntityMeltingRecipe.DAMAGE, "cow health after three calls in one tick");
        assertTankGained(helper, core, ForgeweaveFluids.BLOOD.still().get(), EntityMeltingRecipe.DEFAULT_AMOUNT);
        helper.succeed();
    }

    /**
     * Upstream's "we only melt living entities if we have something in the smeltery" gate
     * ({@code liquids.getFluidAmount() > 0}): an empty smeltery leaves whatever wandered into it
     * completely alone. Note the item half of the same sweep has no such gate, which is why the gate
     * is read per sweep rather than folded into the sweep's own guard.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void anEmptySmelteryDoesNotTouchTheMobStandingInIt(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelled(helper);
        helper.assertValueEqual(core.tank().getFluidAmount(), 0, "an unprimed smeltery's tank");
        Cow cow = spawnInside(helper, EntityType.COW);
        float before = cow.getHealth();

        core.sweepInterior();

        helper.assertValueEqual(cow.getHealth(), before, "cow health after a sweep of an empty smeltery");
        helper.assertValueEqual(core.tank().getFluidAmount(), 0, "an empty smeltery's tank after the sweep");
        helper.succeed();
    }

    /**
     * A dead or removed entity stops yielding. Upstream's own two-part guard is
     * {@code entity.isEntityAlive() && !entity.isDead} on the fallback branch plus
     * {@code attackEntityFrom} returning false for anything already gone; both collapse into
     * {@link net.minecraft.world.entity.Entity#isAlive()} here. Checked with a mob that <em>does</em>
     * have a recipe, because the recipe branch is the one upstream's alive-check does not cover -- a
     * registered entity that has been discarded must not keep pouring.
     */
    @GameTest(template = "smeltery", timeoutTicks = 200)
    public static void aRemovedEntityStopsYielding(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = primedSmeltery(helper);
        LivingEntity golem = spawnInside(helper, EntityType.IRON_GOLEM);
        int primer = core.tank().getFluidAmount();

        golem.discard();
        core.sweepInterior();

        helper.assertValueEqual(core.tank().getFluidAmount(), primer, "tank contents after sweeping a removed entity");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** {@link SmelteryMeltingGameTests}'s 1x1x2 lava-fuelled smeltery, still with an empty tank. */
    private static SmelteryControllerBlockEntity lavaFuelled(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());

        SearedTankBlockEntity wallTank = helper.getBlockEntity(SmelteryGameTests.TANK_POS);
        wallTank.tank().fill(new FluidStack(Fluids.LAVA, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);

        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /**
     * The same smeltery with a token 100 mB of molten gold already in it, because the living-entity
     * branch only runs at all once the tank holds something. Every {@code assertTankGained} assertion
     * is stated against this primer rather than against zero.
     *
     * <p>Gold specifically, for two reasons {@link SmelteryTank} imposes: it merges same-fluid stacks
     * (so priming with iron would hide the iron golem's own 18 mB inside a 118 mB stack) and it alloys
     * whatever it holds, and gold's only two alloy partners -- molten copper and molten netherite scrap
     * -- appear nowhere in the #270 entity table.
     */
    private static SmelteryControllerBlockEntity primedSmeltery(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = lavaFuelled(helper);
        core.tank().fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), PRIMER), IFluidHandler.FluidAction.EXECUTE);
        return core;
    }

    /** How much molten gold {@link #primedSmeltery} puts in to open the living-entity branch. */
    private static final int PRIMER = 100;

    /**
     * Spawns {@code type} in the middle of {@link SmelteryGameTests#buildWalls}'s 1x1x2 interior, the
     * same relative (1, 2, 1) {@code SmelteryMeltingGameTests#dropInsideSmeltery} uses. Mobs larger
     * than the interior (the iron golem) still count as inside: upstream tests the interior AABB
     * against the entity's own bounding box, and so does {@code Level#getEntitiesOfClass}.
     */
    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity> T spawnInside(GameTestHelper helper, EntityType<T> type) {
        Vec3 pos = helper.absoluteVec(new BlockPos(1, 2, 1).getCenter());
        T entity = type.create(helper.getLevel());
        helper.assertTrue(entity != null, "expected " + type + " to be creatable");
        entity.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
        // No AI: a mob left to its own devices walks out of a two-block box mid-test.
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
        helper.getLevel().addFreshEntity(entity);
        return (T) entity;
    }

    private static void assertTankGained(GameTestHelper helper, SmelteryControllerBlockEntity core, Fluid fluid, int amount) {
        helper.assertValueEqual(core.tank().getFluidAmount(), PRIMER + amount, "total tank contents after the sweep");
        helper.assertTrue(core.tank().fluids().stream().anyMatch(stack -> stack.getFluid() == fluid && stack.getAmount() == amount),
                "expected " + amount + " mB of " + fluid + " in the tank, which holds " + core.tank().fluids());
    }
}
