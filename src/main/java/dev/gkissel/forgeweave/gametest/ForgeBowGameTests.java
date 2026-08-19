package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.CrossbowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.LauncherStats;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * M3.5 issue #395, the Tool Forge tier's two bows: the {@code #forgeweave:large_tools} gate, the two
 * stat chains {@link dev.gkissel.forgeweave.tool.ToolConstants#LONGBOW}/{@code #CROSSBOW} port from
 * upstream's own {@code buildTagData}s, and the crossbow's two-phase use -- load, keep the charge
 * across a hotbar swap and a save/reload, fire on the next click.
 *
 * <p>{@link BowGameTests} covers everything the two share with the shortbow (the draw/release cycle
 * itself, ammo lookup, pickup, Broken, creative); this class only tests what #395 adds.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ForgeBowGameTests {

    private static final double EPSILON = 1.0E-4;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private static ToolAssemblyRecipes.Entry entry(BowItem tool) {
        return ToolAssembly.entryFor(tool);
    }

    private static ItemStack longbow(GameTestHelper helper, Player player, BlockPos pos) {
        return ToolAssembly.assembleAtForge(helper, player, pos, entry(ForgeweaveItems.TOOL_LONGBOW.get()),
                List.of("iron", "bone", "iron", "string"));
    }

    private static ItemStack crossbow(GameTestHelper helper, Player player, BlockPos pos) {
        return ToolAssembly.assembleAtForge(helper, player, pos, entry(ForgeweaveItems.TOOL_CROSSBOW.get()),
                List.of("iron", "iron", "iron", "string"));
    }

    private static void giveArrows(Player player, int count) {
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, count));
    }

    private static int arrowsHeld(Player player) {
        return player.getInventory().countItem(Items.ARROW);
    }

    /**
     * {@code TinkerRangedWeapons#registerToolBuilding} registers the shortbow with {@code
     * registerToolCrafting} but both of these with {@code registerToolForgeCrafting} -- here the same
     * split is the {@code #forgeweave:large_tools} item tag (issue #348's single source), so this is
     * the one place it is checked against the real, datapack-bound tag.
     */
    @GameTest(template = "empty")
    public static void bothForgeBowsRefuseTheStationAndAssembleAtTheForge(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack stationLongbow = ToolAssembly.assemble(helper, player, new BlockPos(1, 1, 1),
                entry(ForgeweaveItems.TOOL_LONGBOW.get()), List.of("iron", "bone", "iron", "string"));
        helper.assertTrue(stationLongbow.isEmpty(),
                "the Tool Station must not assemble a longbow, got " + stationLongbow);
        ItemStack stationCrossbow = ToolAssembly.assemble(helper, player, new BlockPos(1, 1, 2),
                entry(ForgeweaveItems.TOOL_CROSSBOW.get()), List.of("iron", "iron", "iron", "string"));
        helper.assertTrue(stationCrossbow.isEmpty(),
                "the Tool Station must not assemble a crossbow, got " + stationCrossbow);

        helper.assertTrue(longbow(helper, player, new BlockPos(1, 1, 3)).is(ForgeweaveItems.TOOL_LONGBOW.get()),
                "the Tool Forge builds the longbow");
        helper.assertTrue(crossbow(helper, player, new BlockPos(1, 1, 4)).is(ForgeweaveItems.TOOL_CROSSBOW.get()),
                "the Tool Forge builds the crossbow");
        helper.succeed();
    }

    /**
     * The exact longbow {@code fixtures/save_compat/m3_5_tool_longbow.snbt} pins, built at a real
     * forge. {@code LongBow#buildTagData}: the shortbow's {@code head}/{@code limb} chain plus
     * {@code extra(grip)}, then {@code data.durability *= 1.4f} <em>after</em> {@code bowstring}.
     */
    @GameTest(template = "empty")
    public static void longbowStatsMatchUpstream(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = longbow(helper, player, new BlockPos(1, 1, 1));

        ToolStats.Stats stats = bow.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null && stats.durability() == 352
                        && Math.abs(stats.miningSpeed() - 5.545F) < EPSILON
                        && Math.abs(stats.attackDamage() - 3.25F) < EPSILON,
                "(204 + 200) / 2 = 202, + the plate's 50 = 252, x string 1.0, x 1.4 = 352; got " + stats);
        LauncherStats launcher = BowItem.launcherStats(bow);
        helper.assertTrue(launcher != null
                        && Math.abs(launcher.drawSpeed() - 0.725F) < EPSILON
                        && Math.abs(launcher.range() - 1.325F) < EPSILON
                        && Math.abs(launcher.bonusDamage() - 3.5F) < EPSILON,
                "the plain two-limb average, no bonus multiplier; got " + launcher);
        ToolMaterials materials = bow.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        helper.assertTrue(materials != null && materials.handle().isEmpty()
                        && materials.binding().equals(java.util.Optional.of(id("iron")))
                        && materials.head().equals(id("iron")),
                "a longbow has a grip but still no handle slot, got " + materials);
        helper.assertTrue(List.of(id("magnetic"), id("magnetic2"), id("fractured"), id("splintering"))
                        .equals(bow.get(ForgeweaveDataComponents.TRAITS.get())),
                "got " + bow.get(ForgeweaveDataComponents.TRAITS.get()));
        helper.succeed();
    }

    /**
     * {@code BowCore#getDrawbackProgress} with {@code LongBow#getDrawTime() = 30}: two and a half
     * times the shortbow's 12, so the same limbs take two and a half times as long to reach full
     * draw. Iron/bone draw at 0.725, so 30 / 0.725 = 41.4 ticks.
     */
    @GameTest(template = "empty")
    public static void longbowDrawsSlowerThanTheShortbow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BowItem item = ForgeweaveItems.TOOL_LONGBOW.get();
        ItemStack bow = longbow(helper, player, new BlockPos(1, 1, 1));

        helper.assertTrue(item.drawTime() == 30, "LongBow#getDrawTime() = 30, got " + item.drawTime());
        helper.assertTrue(Math.abs(item.drawbackProgress(bow, 30) - 0.725F) < EPSILON,
                "0.725 * 30 / 30, got " + item.drawbackProgress(bow, 30));
        helper.assertTrue(item.drawbackProgress(bow, 41) < 1.0F && item.drawbackProgress(bow, 42) == 1.0F,
                "full draw between 41 and 42 ticks (30 / 0.725 = 41.4)");
        helper.assertTrue(ForgeweaveItems.TOOL_SHORTBOW.get().drawbackProgress(bow, 17) == 1.0F,
                "the same limbs on the shortbow's drawTime 12 are full at 17 -- the difference is the constant");
        helper.succeed();
    }

    /**
     * The exact crossbow {@code fixtures/save_compat/m3_5_tool_crossbow_loaded.snbt} pins.
     * {@code CrossBow#buildTagData}: the rod body counted twice (its EXTRA averaged with the
     * binding's, its HANDLE applied on top), and {@code data.bonusDamage *= 1.5f}.
     */
    @GameTest(template = "empty")
    public static void crossbowStatsMatchUpstream(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = crossbow(helper, player, new BlockPos(1, 1, 1));

        ToolStats.Stats stats = bow.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null && stats.durability() == 276
                        && Math.abs(stats.miningSpeed() - 6.0F) < EPSILON
                        && Math.abs(stats.attackDamage() - 4.0F) < EPSILON,
                "204 + avg(binding 50, body 50) = 254, x the body's 0.85 = 216, + its 60 = 276; got " + stats);
        LauncherStats launcher = BowItem.launcherStats(bow);
        helper.assertTrue(launcher != null
                        && Math.abs(launcher.drawSpeed() - 0.5F) < EPSILON
                        && Math.abs(launcher.range() - 1.5F) < EPSILON
                        && Math.abs(launcher.bonusDamage() - 10.5F) < EPSILON,
                "one limb, and iron's 7.0 bonus x 1.5 = 10.5; got " + launcher);
        ToolMaterials materials = bow.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        helper.assertTrue(materials != null && materials.head().equals(id("iron"))
                        && materials.handle().equals(java.util.Optional.of(id("iron"))),
                "the body is the handle pick and the limb the head pick, got " + materials);
        helper.assertTrue(ForgeweaveItems.TOOL_CROSSBOW.get().drawTime() == 45, "CrossBow#getDrawTime() = 45");
        helper.assertTrue(!CrossbowItem.isLoaded(bow), "a freshly assembled crossbow is not loaded");
        helper.succeed();
    }

    /**
     * The whole two-phase cycle ({@code CrossBow#onPlayerStoppedUsing}/{@code #onItemRightClick}): a
     * full draw loads without firing and without spending ammo, the charge survives a hotbar swap and
     * a save/reload round trip, and the next right-click fires one arrow, spends the ammo and one
     * durability, and clears the flag.
     */
    @GameTest(template = "empty")
    public static void crossbowLoadsSurvivesAndFiresOnTheNextUse(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        ItemStack bow = crossbow(helper, player, new BlockPos(1, 1, 1));
        giveArrows(player, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);

        // Phase one: a full draw (0.5 draw speed on a 45-tick draw time, so 90 ticks) loads it.
        InteractionResult draw = bow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
        helper.assertTrue(draw == InteractionResult.CONSUME, "an unloaded crossbow starts a draw, got " + draw);
        bow.getItem().releaseUsing(bow, helper.getLevel(), player, bow.getUseDuration(player) - 90);
        player.stopUsingItem();
        helper.assertTrue(CrossbowItem.isLoaded(bow), "a full draw loads the crossbow");
        helper.assertTrue(arrowsHeld(player) == 5, "upstream stores no ammo when loading, got " + arrowsHeld(player));
        helper.assertTrue(bow.getDamageValue() == 0, "loading costs no durability");
        helper.assertTrue(arrowsAround(helper, player).isEmpty(), "loading fires nothing");

        // The charge is a data component, so it rides the stack: a hotbar swap and a full save/reload
        // round trip both come back loaded.
        player.getInventory().setItem(3, bow);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemStack swapped = player.getInventory().getItem(3);
        helper.assertTrue(CrossbowItem.isLoaded(swapped), "the charge survives a hotbar swap");
        RegistryAccess registries = helper.getLevel().registryAccess();
        ItemStack reloaded = ItemStack.parse(registries, (CompoundTag) swapped.save(registries)).orElseThrow();
        helper.assertTrue(CrossbowItem.isLoaded(reloaded), "the charge survives a save/reload");

        // Phase two: the next right-click fires at full power without a draw and clears the flag.
        player.setItemInHand(InteractionHand.MAIN_HAND, swapped);
        InteractionResult fire = swapped.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND)
                .getResult();
        helper.assertTrue(fire == InteractionResult.SUCCESS, "a loaded crossbow fires on use, got " + fire);
        helper.assertTrue(!CrossbowItem.isLoaded(swapped), "firing clears the charge");
        List<Arrow> arrows = arrowsAround(helper, player);
        helper.assertTrue(arrows.size() == 1, "one arrow per shot, got " + arrows.size());
        Arrow arrow = arrows.get(0);
        arrows.forEach(AbstractArrow::discard);
        // timeLeft 0 means the whole use duration counted, so progress is 1: getPowerForTime(20) = 1,
        // x 1 progress x baseProjectileSpeed 7 x range 1.5.
        double expectedSpeed = 7.0 * 1.5;
        helper.assertTrue(Math.abs(arrow.getDeltaMovement().length() - expectedSpeed) < 0.15,
                "expected launch speed ~" + expectedSpeed + ", got " + arrow.getDeltaMovement().length());
        helper.assertTrue(arrow.isCritArrow(), "a loaded shot is a full draw, so critical");
        helper.assertTrue(arrowsHeld(player) == 4, "the arrow is spent at fire time, got " + arrowsHeld(player));
        helper.assertTrue(swapped.getDamageValue() == 1, "one shot costs one durability");
        helper.succeed();
    }

    /** {@code CrossBow#onPlayerStoppedUsing} only loads at {@code >= 1f} progress; anything less does nothing. */
    @GameTest(template = "empty")
    public static void aPartialCrankDoesNotLoadTheCrossbow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        ItemStack bow = crossbow(helper, player, new BlockPos(1, 1, 1));
        giveArrows(player, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);

        bow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        // 89 ticks at 0.5 draw speed over drawTime 45 is 0.988 -- one tick short.
        bow.getItem().releaseUsing(bow, helper.getLevel(), player, bow.getUseDuration(player) - 89);
        player.stopUsingItem();

        helper.assertTrue(!CrossbowItem.isLoaded(bow), "an incomplete crank leaves the crossbow unloaded");
        helper.assertTrue(arrowsAround(helper, player).isEmpty(), "and fires nothing");
        helper.assertTrue(arrowsHeld(player) == 5, "and spends nothing");
        // An unloaded crossbow's right-click is an ordinary draw, not a shot.
        helper.assertTrue(bow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult()
                        == InteractionResult.CONSUME,
                "an unloaded crossbow still draws");
        player.stopUsingItem();
        helper.succeed();
    }

    /**
     * T52 (issue #483), {@code CrossBow#getAmmoToRender}'s one override: the crossbow shows a bolt
     * only while it holds a crank, however full the quiver is. A cranking crossbow shows none either
     * -- upstream's cranking overrides carry no {@code ammoPosition} at all
     * ({@code BowDrawArtTest}), and this is the other half of that: the stack itself is empty.
     */
    @GameTest(template = "empty")
    public static void onlyALoadedCrossbowShowsABolt(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        ItemStack bow = crossbow(helper, player, new BlockPos(1, 1, 1));
        CrossbowItem item = (CrossbowItem) bow.getItem();
        giveArrows(player, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);

        helper.assertTrue(item.ammoToRender(bow, player).isEmpty(),
                "an unloaded crossbow shows no bolt even with a full quiver");

        // Mid-crank is still unloaded, so still nothing.
        bow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(item.ammoToRender(bow, player).isEmpty(), "a crossbow being cranked shows no bolt");
        player.stopUsingItem();

        CrossbowItem.setLoaded(bow, true);
        helper.assertTrue(item.ammoToRender(bow, player).is(Items.ARROW),
                "a loaded crossbow shows the arrow its next click will spend");

        bow.set(ForgeweaveDataComponents.BROKEN.get(), true);
        helper.assertTrue(item.ammoToRender(bow, player).isEmpty(),
                "a Broken crossbow cannot fire, so it shows nothing -- BowCore's guard, inherited");
        helper.succeed();
    }

    private static List<Arrow> arrowsAround(GameTestHelper helper, Player player) {
        return helper.getLevel().getEntitiesOfClass(Arrow.class,
                new AABB(player.position(), player.position()).inflate(4.0));
    }

    /**
     * Issue #602: the arrow's yRot/xRot -- and the O-fields the client's very first render frame
     * lerps from ({@code ArrowRenderer#render}) -- must come from the exact vector that becomes its
     * deltaMovement, or that first frame renders the arrow at its pre-spawn default rotation, visible
     * as launching "backward" for an instant. Vanilla's own {@code CrossbowItem#shootProjectile} sets
     * both from one {@code Projectile#shoot(x, y, z, velocity, inaccuracy)} call. What the crossbow
     * inherited from {@link BowItem#createArrow} instead ({@code Projectile#shootFromRotation}, what a
     * bow's own vanilla {@code ItemBow#shootProjectile} uses) sets rotation from the shooter's aim
     * FIRST and only then mutates deltaMovement again by adding the shooter's own momentum, without
     * re-deriving rotation to match -- a real, measurable mismatch whenever the shooter is moving, not
     * just a cosmetic one-frame flicker. A player standing still hides it; this pins it with the
     * shooter moving briskly sideways at the moment of the shot.
     */
    @GameTest(template = "empty")
    public static void crossbowArrowRotationMatchesItsVelocityAtSpawn(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        player.setDeltaMovement(new Vec3(4.0, 0.0, 0.0)); // moving sideways as the shot fires
        ItemStack bow = crossbow(helper, player, new BlockPos(1, 1, 1));
        CrossbowItem.setLoaded(bow, true);
        giveArrows(player, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);

        bow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        List<Arrow> arrows = arrowsAround(helper, player);
        helper.assertTrue(arrows.size() == 1, "one arrow, got " + arrows.size());
        Arrow arrow = arrows.get(0);
        arrows.forEach(AbstractArrow::discard);

        assertRotationMatchesVelocity(helper, arrow);
        helper.succeed();
    }

    static void assertRotationMatchesVelocity(GameTestHelper helper, Arrow arrow) {
        Vec3 v = arrow.getDeltaMovement();
        double horizontal = Math.sqrt(v.x * v.x + v.z * v.z);
        float expectedYRot = (float) (Mth.atan2(v.x, v.z) * 180.0 / Math.PI);
        float expectedXRot = (float) (Mth.atan2(v.y, horizontal) * 180.0 / Math.PI);
        helper.assertTrue(Math.abs(Mth.wrapDegrees(arrow.getYRot() - expectedYRot)) < 1.0F,
                "yRot must track the actual launch velocity, expected ~" + expectedYRot + ", got " + arrow.getYRot());
        helper.assertTrue(Math.abs(Mth.wrapDegrees(arrow.getXRot() - expectedXRot)) < 1.0F,
                "xRot must track the actual launch velocity, expected ~" + expectedXRot + ", got " + arrow.getXRot());
        helper.assertTrue(arrow.yRotO == arrow.getYRot() && arrow.xRotO == arrow.getXRot(),
                "the O-fields the client's first frame lerps from must already match the current rotation, "
                        + "or that frame renders the arrow at its stale default (issue #602)");
    }
}
