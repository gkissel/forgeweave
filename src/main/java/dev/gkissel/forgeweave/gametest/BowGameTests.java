package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
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
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.LauncherStats;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * M3.5 issue #394, the shortbow: assembly at a real Tool Station, the stat math into both
 * components against upstream {@code ShortBow#buildTagData}, and {@code BowCore}'s draw/release
 * cycle -- launch velocity, bonus damage, crit, ammo cost, pickup, the 5-tick floor, no ammo, Broken.
 * The mock player is not ticked by the level, so a draw is a {@code startUsingItem} followed by a
 * {@code releaseUsing} at the tick count under test, the way {@link StationWeaponGameTests} drives
 * the longsword's charged leap.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class BowGameTests {

    private static final double EPSILON = 1.0E-4;

    private static ToolAssemblyRecipes.Entry shortbowEntry() {
        return ToolAssembly.entryFor(ForgeweaveItems.TOOL_SHORTBOW.get());
    }

    private static ItemStack shortbow(GameTestHelper helper, Player player, String limb1, String limb2, String string) {
        ItemStack bow = ToolAssembly.assemble(helper, player, new BlockPos(1, 1, 1), shortbowEntry(),
                List.of(limb1, limb2, string));
        helper.assertTrue(bow.is(ForgeweaveItems.TOOL_SHORTBOW.get()), "expected a shortbow, got " + bow);
        return bow;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /**
     * Draws {@code bow} for {@code ticks} and lets go; returns the arrows that appeared near the
     * player. The player is first moved into this test's own structure -- mock players all spawn at
     * the world origin, and tests in one class run side by side, so a shared origin would let one
     * test's arrows show up in another's scan.
     */
    private static List<Arrow> draw(GameTestHelper helper, Player player, ItemStack bow, int ticks) {
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        int duration = bow.getUseDuration(player);
        bow.getItem().releaseUsing(bow, helper.getLevel(), player, duration - ticks);
        player.stopUsingItem();
        return helper.getLevel().getEntitiesOfClass(Arrow.class, new AABB(player.position(), player.position()).inflate(4.0));
    }

    private static void giveArrows(Player player, int count) {
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, count)); // main inventory, off the hotbar
    }

    private static int arrowsHeld(Player player) {
        return player.getInventory().countItem(Items.ARROW);
    }

    /** {@code TinkerRegistry.registerToolCrafting(shortBow)}: a Tool Station tool, from limb + limb + string. */
    @GameTest(template = "empty")
    public static void shortbowAssemblesAtTheToolStation(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, "wood", "wood", "string");

        ToolMaterials materials = bow.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        helper.assertTrue(materials != null && materials.parts().equals(List.of(id("wood"), id("wood"), id("string"))),
                "expected the three part materials in slot order, got " + materials);
        helper.assertTrue(materials.head().equals(id("wood")) && materials.handle().isEmpty()
                        && materials.binding().isEmpty(),
                "a bow's head is its first limb and it has no handle or binding, got " + materials);
        ToolStats.Stats stats = bow.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null && stats.durability() == 35 && stats.attackDamage() == 2.0F,
                "wood/wood/string: durability 35 (head avg x string 1.0), attack 2.0, got " + stats);
        LauncherStats launcher = BowItem.launcherStats(bow);
        helper.assertTrue(launcher != null && launcher.equals(new LauncherStats(1.0F, 1.0F, 0.0F)),
                "wood limbs: draw speed 1, range 1, bonus 0, got " + launcher);
        helper.assertTrue(bow.getMaxDamage() == 35, "max damage mirrors durability, got " + bow.getMaxDamage());
        helper.succeed();
    }

    /**
     * The exact bow {@code fixtures/save_compat/m3_5_tool_shortbow.snbt} pins, built at a real
     * station: {@code data.head(iron, bone)} averages the HEAD blocks, {@code data.limb(iron, bone)}
     * averages the BOW blocks ({@code TinkerMaterials}: iron 0.5/1.5/7.0, bone 0.95/1.15/0.0), and a
     * limb collects both its BOW-scoped (general) and HEAD-scoped traits, as {@code
     * PartMaterialType.bow}'s two stat types do upstream.
     */
    @GameTest(template = "empty")
    public static void ironBoneStringShortbowStatsMatchUpstream(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, "iron", "bone", "string");

        ToolStats.Stats stats = bow.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null && stats.durability() == 202
                        && Math.abs(stats.miningSpeed() - 5.545F) < EPSILON
                        && Math.abs(stats.attackDamage() - 3.25F) < EPSILON,
                "expected durability 202, mining 5.545, attack 3.25, got " + stats);
        LauncherStats launcher = BowItem.launcherStats(bow);
        helper.assertTrue(launcher != null
                        && Math.abs(launcher.drawSpeed() - 0.725F) < EPSILON
                        && Math.abs(launcher.range() - 1.325F) < EPSILON
                        && Math.abs(launcher.bonusDamage() - 3.5F) < EPSILON,
                "expected draw speed 0.725, range 1.325, bonus 3.5, got " + launcher);
        List<ResourceLocation> traits = bow.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(List.of(id("magnetic"), id("magnetic2"), id("fractured"), id("splintering")).equals(traits),
                "a limb grants its material's general (BOW-scoped) and head-scoped traits, got " + traits);
        helper.succeed();
    }

    /** {@code BowCore#getDrawbackProgress}: {@code drawSpeed * ticks / drawTime}, capped at 1, with drawTime 12. */
    @GameTest(template = "empty")
    public static void drawProgressIsDrawSpeedTimesTicksOverDrawTime(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BowItem item = ForgeweaveItems.TOOL_SHORTBOW.get();
        ItemStack wood = shortbow(helper, player, "wood", "wood", "string");
        ItemStack ironBone = shortbow(helper, player, "iron", "bone", "string");

        helper.assertTrue(Math.abs(item.drawbackProgress(wood, 6) - 0.5F) < EPSILON, "wood, 6 ticks: 1.0 * 6 / 12");
        helper.assertTrue(item.drawbackProgress(wood, 12) == 1.0F, "wood, 12 ticks: full");
        helper.assertTrue(item.drawbackProgress(wood, 100) == 1.0F, "never past 1");
        helper.assertTrue(Math.abs(item.drawbackProgress(ironBone, 12) - 0.725F) < EPSILON,
                "iron/bone, 12 ticks: 0.725 * 12 / 12");
        helper.assertTrue(item.drawbackProgress(ironBone, 16) < 1.0F && item.drawbackProgress(ironBone, 17) == 1.0F,
                "iron/bone reaches full draw between 16 and 17 ticks (12 / 0.725 = 16.55)");
        // Not drawing this stack: 0.
        helper.assertTrue(item.drawbackProgress(wood, player) == 0.0F, "a holder not drawing reports 0");
        helper.succeed();
    }

    /**
     * {@code BowCore#shootProjectile} for a full draw: launch speed {@code getArrowVelocity(20) * 1 *
     * 3 * range}, a critical arrow, one arrow spent from the inventory, one durability off the bow,
     * and (the deviation the class javadoc records) the bow's bonus damage on the arrow's base damage
     * scaled so a hit at launch speed lands it flat.
     */
    @GameTest(template = "empty")
    public static void fullDrawFiresACriticalArrowCarryingRangeAndBonusDamage(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        ItemStack bow = shortbow(helper, player, "iron", "bone", "string");
        giveArrows(player, 5);

        List<Arrow> arrows = draw(helper, player, bow, 40);
        helper.assertTrue(arrows.size() == 1, "one arrow per release, got " + arrows.size());
        Arrow arrow = arrows.get(0);
        arrows.forEach(AbstractArrow::discard);

        double expectedSpeed = 3.0 * 1.325; // getPowerForTime(20) = 1, progress 1, base 3, range 1.325
        double speed = arrow.getDeltaMovement().length();
        helper.assertTrue(Math.abs(speed - expectedSpeed) < 0.15,
                "expected launch speed ~" + expectedSpeed + " (inaccuracy 1.0 jitters it slightly), got " + speed);
        helper.assertTrue(arrow.getDeltaMovement().z > 0 && Math.abs(arrow.getDeltaMovement().x) < 0.5,
                "the arrow flies the way the player faces (+Z), got " + arrow.getDeltaMovement());
        helper.assertTrue(arrow.isCritArrow(), "a full draw is critical");
        double expectedBase = 2.0 + 3.5 / expectedSpeed;
        helper.assertTrue(Math.abs(arrow.getBaseDamage() - expectedBase) < 0.05,
                "expected base damage 2 + bonus / speed = " + expectedBase + ", got " + arrow.getBaseDamage());
        helper.assertTrue(arrow.pickup == AbstractArrow.Pickup.ALLOWED,
                "an arrow that cost ammo keeps the player-shot default (BowCore only forbids pickup on a free arrow), got "
                        + arrow.pickup);
        helper.assertTrue(arrowsHeld(player) == 4, "one arrow consumed, got " + arrowsHeld(player));
        helper.assertTrue(bow.getDamageValue() == 1, "one shot costs one durability, got " + bow.getDamageValue());
        helper.succeed();
    }

    /** A weaker draw flies slower and is not critical; a wood bow's zero bonus leaves vanilla's 2.0 base. */
    @GameTest(template = "empty")
    public static void partialDrawFliesSlowerAndIsNotCritical(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        ItemStack bow = shortbow(helper, player, "wood", "wood", "string");
        giveArrows(player, 5);

        List<Arrow> arrows = draw(helper, player, bow, 6); // progress 0.5
        helper.assertTrue(arrows.size() == 1, "one arrow, got " + arrows.size());
        Arrow arrow = arrows.get(0);
        arrows.forEach(AbstractArrow::discard);

        // getPowerForTime(10) = (0.25 + 1) / 3 = 0.4167; * 0.5 progress * 3 * range 1 = 0.625
        double expectedSpeed = ((0.5 * 0.5 + 0.5 * 2.0) / 3.0) * 0.5 * 3.0;
        double speed = arrow.getDeltaMovement().length();
        helper.assertTrue(Math.abs(speed - expectedSpeed) < 0.15, "expected ~" + expectedSpeed + ", got " + speed);
        helper.assertTrue(!arrow.isCritArrow(), "a half draw is not critical");
        helper.assertTrue(Math.abs(arrow.getBaseDamage() - 2.0) < EPSILON,
                "zero bonus damage leaves vanilla's base, got " + arrow.getBaseDamage());
        helper.succeed();
    }

    /**
     * {@code BowCore#onPlayerStoppedUsing}: nothing under 5 ticks; nothing with no ammo (and no use
     * begins -- {@code onItemRightClick} fails); nothing from a Broken bow.
     */
    @GameTest(template = "empty")
    public static void tooShortNoAmmoAndBrokenAllFireNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, "wood", "wood", "string");

        // No ammo: the click fails and a release fires nothing.
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        InteractionResult noAmmo = bow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
        helper.assertTrue(noAmmo == InteractionResult.FAIL, "no ammo must refuse the draw, got " + noAmmo);
        helper.assertTrue(draw(helper, player, bow, 40).isEmpty(), "no ammo, no arrow");

        giveArrows(player, 3);
        InteractionResult withAmmo = bow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
        helper.assertTrue(withAmmo == InteractionResult.CONSUME, "with ammo the draw starts, got " + withAmmo);
        player.stopUsingItem();

        // Under the 5-tick floor: nothing, and the arrow stays in the quiver.
        helper.assertTrue(draw(helper, player, bow, 4).isEmpty(), "a 4-tick draw fires nothing");
        helper.assertTrue(arrowsHeld(player) == 3, "a refused shot costs no ammo, got " + arrowsHeld(player));
        helper.assertTrue(bow.getDamageValue() == 0, "a refused shot costs no durability");

        // Broken: no draw, no shot.
        bow.set(ForgeweaveDataComponents.BROKEN.get(), true);
        InteractionResult broken = bow.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
        helper.assertTrue(broken == InteractionResult.FAIL, "a Broken bow refuses to draw, got " + broken);
        helper.assertTrue(draw(helper, player, bow, 40).isEmpty(), "a Broken bow fires nothing");
        helper.assertTrue(arrowsHeld(player) == 3, "a Broken bow spends no ammo");
        helper.succeed();
    }

    /**
     * Creative ({@code BowCore#getCreativeProjectileStack}/{@code #consumeAmmo}/{@code
     * #getProjectileEntity}): fires with an empty quiver, spends nothing, and the arrow is
     * {@code CREATIVE_ONLY} pickup. Also: ammo in the offhand wins over the inventory
     * ({@code AmmoHelper}: the equipment handler first), and a tipped arrow fires as itself.
     */
    @GameTest(template = "empty")
    public static void creativeConjuresAnArrowAndAmmoLookupPrefersTheOffhand(GameTestHelper helper) {
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        ItemStack bow = shortbow(helper, creative, "wood", "wood", "string");

        List<Arrow> arrows = draw(helper, creative, bow, 40);
        helper.assertTrue(arrows.size() == 1, "creative fires with no ammo, got " + arrows.size());
        helper.assertTrue(arrows.get(0).pickup == AbstractArrow.Pickup.CREATIVE_ONLY,
                "a creative arrow is CREATIVE_ONLY pickup, got " + arrows.get(0).pickup);
        arrows.forEach(AbstractArrow::discard);
        helper.assertTrue(bow.getDamageValue() == 0, "creative costs no durability");

        Player survival = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack survivalBow = shortbow(helper, survival, "wood", "wood", "string");
        giveArrows(survival, 2);
        survival.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SPECTRAL_ARROW, 2));
        helper.assertTrue(BowItem.findAmmo(survival).is(Items.SPECTRAL_ARROW), "the offhand stack is the ammo");
        List<Arrow> shot = draw(helper, survival, survivalBow, 40);
        // A spectral arrow is a SpectralArrow, not an Arrow -- so the Arrow scan finds none, and the
        // offhand stack is what shrank.
        helper.assertTrue(shot.isEmpty(), "a spectral arrow is not a plain Arrow entity, got " + shot);
        helper.assertTrue(survival.getItemInHand(InteractionHand.OFF_HAND).getCount() == 1,
                "the offhand spectral arrow was spent, got " + survival.getItemInHand(InteractionHand.OFF_HAND));
        helper.assertTrue(arrowsHeld(survival) == 2, "the inventory arrows were not, got " + arrowsHeld(survival));
        helper.getLevel().getEntitiesOfClass(AbstractArrow.class,
                new AABB(survival.position(), survival.position()).inflate(4.0)).forEach(AbstractArrow::discard);
        helper.succeed();
    }

    /** The content-family toggles ticket's gate, wired to {@code rangedWeapons} here: off, no shortbow. */
    @GameTest(template = "empty")
    public static void rangedWeaponsOffRefusesTheShortbow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ForgeweaveConfig.RANGED_WEAPONS.set(false);
        try {
            ItemStack refused = ToolAssembly.assemble(helper, player, new BlockPos(1, 1, 1), shortbowEntry(),
                    List.of("wood", "wood", "string"));
            helper.assertTrue(refused.isEmpty(), "with rangedWeapons off the station builds no shortbow, got " + refused);
        } finally {
            ForgeweaveConfig.RANGED_WEAPONS.set(true);
        }
        ItemStack allowed = ToolAssembly.assemble(helper, player, new BlockPos(1, 1, 3), shortbowEntry(),
                List.of("wood", "wood", "string"));
        helper.assertTrue(allowed.is(ForgeweaveItems.TOOL_SHORTBOW.get()), "back on, it builds again");
        // A ToolItem in every other respect: melee attack speed/damage come off ToolConstants#SHORTBOW.
        helper.assertTrue(allowed.getItem() instanceof ToolItem, "a bow is a ToolItem");
        helper.succeed();
    }

    /**
     * T52 (issue #483), {@code BowCore#getAmmoToRender}: the stack the bow would fire is the one its
     * model draws nocked -- the same {@code findAmmo} lookup, so the offhand wins over the inventory
     * and the arrow on show is the arrow that leaves the bow. Two guards of upstream's own: a Broken
     * bow renders no ammo (it cannot fire), and neither does one nobody is holding.
     *
     * <p>It is not gated on the draw: upstream's undrawn bows carry a root {@code ammoPosition} too,
     * so a bow held by a player with arrows shows one before the draw starts.
     */
    @GameTest(template = "empty")
    public static void theNockedArrowIsTheArrowTheBowWouldFire(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, "wood", "wood", "string");
        BowItem item = (BowItem) bow.getItem();
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);

        helper.assertTrue(item.ammoToRender(bow, player).isEmpty(), "an empty quiver nocks nothing");

        giveArrows(player, 3);
        ItemStack nocked = item.ammoToRender(bow, player);
        helper.assertTrue(nocked.is(Items.ARROW) && nocked.getCount() == 3,
                "the inventory arrows are what shows, got " + nocked);

        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SPECTRAL_ARROW, 2));
        helper.assertTrue(item.ammoToRender(bow, player).is(Items.SPECTRAL_ARROW),
                "findAmmo's order holds: the offhand stack is what a shot would spend, so it is what shows");

        helper.assertTrue(item.ammoToRender(bow, null).isEmpty(), "an unheld bow has no inventory to look in");

        bow.set(ForgeweaveDataComponents.BROKEN.get(), true);
        helper.assertTrue(item.ammoToRender(bow, player).isEmpty(), "a Broken bow cannot fire, so it nocks nothing");
        helper.succeed();
    }

    /**
     * Issue #602, the shortbow's half of {@link ForgeBowGameTests#crossbowArrowRotationMatchesItsVelocityAtSpawn}:
     * {@link BowItem#createArrow} is shared by every bow, so a plain bow shot has the exact same
     * rotation-vs-deltaMovement mismatch the crossbow does -- the issue's own note that it is "less
     * visible due to draw pose" rather than absent.
     */
    @GameTest(template = "empty")
    public static void shortbowArrowRotationMatchesItsVelocityAtSpawn(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        player.setDeltaMovement(new Vec3(4.0, 0.0, 0.0)); // moving sideways as the shot fires
        ItemStack bow = shortbow(helper, player, "iron", "bone", "string");
        giveArrows(player, 5);
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.startUsingItem(InteractionHand.MAIN_HAND);
        bow.getItem().releaseUsing(bow, helper.getLevel(), player, bow.getUseDuration(player) - 40);
        player.stopUsingItem();

        List<Arrow> arrows = helper.getLevel().getEntitiesOfClass(Arrow.class,
                new AABB(player.position(), player.position()).inflate(4.0));
        helper.assertTrue(arrows.size() == 1, "one arrow, got " + arrows.size());
        Arrow arrow = arrows.get(0);
        arrows.forEach(AbstractArrow::discard);

        ForgeBowGameTests.assertRotationMatchesVelocity(helper, arrow);
        helper.succeed();
    }
}
