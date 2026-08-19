package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.entity.ShurikenEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ShurikenItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #448 (parity audit T17) verification: the shuriken -- the first {@code ProjectileCore}
 * consumer -- assembles from four knife blades only at the Tool Forge, throws on right-click at one
 * ammo (ten durability) per throw, breaks empty, deals its flat tool damage on impact, and a stuck
 * shuriken picked up tops a matching one in the inventory back up.
 *
 * <p>Wood numbers, from {@code ToolConstants#SHURIKEN} over four wood blades: durability = head avg
 * 35 + extra avg 15 = 50, so 5 ammo at 10 durability each; stored attack = 2.0 + 1 = 3.0, dealt at
 * {@code damagePotential 0.7} = 2.1 -- which vanilla's arrow pipeline (the recorded deviation in
 * {@code ShurikenEntity}) ceils to 3 on impact.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ShurikenGameTests {

    private static final List<String> WOOD_BLADES = List.of("wood", "wood", "wood", "wood");

    private static ItemStack shuriken(GameTestHelper helper, Player player, BlockPos pos) {
        ItemStack shuriken = ToolAssembly.assembleAtForge(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_SHURIKEN.get()), WOOD_BLADES);
        helper.assertTrue(shuriken.is(ForgeweaveItems.TOOL_SHURIKEN.get()),
                "expected the Tool Forge to assemble a shuriken, got " + shuriken);
        return shuriken;
    }

    /** Throws the held shuriken once and returns the projectiles that appeared near the player. */
    private static List<ShurikenEntity> throwOnce(GameTestHelper helper, Player player, ItemStack shuriken) {
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, shuriken);
        shuriken.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        return helper.getLevel().getEntitiesOfClass(ShurikenEntity.class,
                new AABB(player.position(), player.position()).inflate(4.0));
    }

    /**
     * {@code TinkerRegistry.registerToolForgeCrafting(shuriken)}: Tool Forge only, and the assembled
     * stats are upstream {@code Shuriken#buildTagData}'s head+extra dual read with {@code attack += 1}.
     * No melee attributes at all -- {@code ProjectileCore#getAttributeModifiers}.
     */
    @GameTest(template = "empty")
    public static void shurikenAssemblesOnlyAtTheToolForge(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack fromStation = ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_SHURIKEN.get()), WOOD_BLADES);
        helper.assertTrue(fromStation.isEmpty(),
                "the Tool Station must refuse a shuriken (a large tool), got " + fromStation);

        ItemStack shuriken = shuriken(helper, player, pos);
        ToolStats.Stats stats = shuriken.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null && stats.durability() == 50 && stats.attackDamage() == 3.0F,
                "four wood blades: durability 35 + 15, attack 2 + 1, got " + stats);
        helper.assertTrue(shuriken.getMaxDamage() == 50, "max damage mirrors durability, got "
                + shuriken.getMaxDamage());
        helper.assertTrue(ShurikenItem.maxAmmo(shuriken) == 5 && ShurikenItem.currentAmmo(shuriken) == 5,
                "50 durability at 10 per ammo is 5 ammo, got " + ShurikenItem.currentAmmo(shuriken)
                        + "/" + ShurikenItem.maxAmmo(shuriken));
        helper.assertTrue(((ShurikenItem) shuriken.getItem()).getDefaultAttributeModifiers(shuriken)
                        == ItemAttributeModifiers.EMPTY,
                "a shuriken carries no melee attributes (ProjectileCore#getAttributeModifiers)");
        helper.succeed();
    }

    /**
     * {@code Shuriken#onItemRightClick}: one throw costs one ammo (ten durability), spawns the
     * entity at speed 2.1 the way the player faces, carrying a one-ammo never-broken snapshot of the
     * tool ({@code ProjectileCore#getProjectileStack}), recoverable ({@code Pickup.ALLOWED}).
     */
    @GameTest(template = "empty")
    public static void throwCostsOneAmmoAndSpawnsTheProjectile(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        ItemStack shuriken = shuriken(helper, player, pos);

        List<ShurikenEntity> thrown = throwOnce(helper, player, shuriken);
        helper.assertTrue(thrown.size() == 1, "one projectile per throw, got " + thrown.size());
        ShurikenEntity projectile = thrown.get(0);

        helper.assertTrue(shuriken.getDamageValue() == ShurikenItem.DURABILITY_PER_AMMO,
                "one throw costs 10 durability, got " + shuriken.getDamageValue());
        helper.assertTrue(ShurikenItem.currentAmmo(shuriken) == 4, "4 of 5 ammo left, got "
                + ShurikenItem.currentAmmo(shuriken));

        double speed = projectile.getDeltaMovement().length();
        helper.assertTrue(Math.abs(speed - ShurikenItem.THROW_SPEED) < 0.01,
                "inaccuracy 0 launches at exactly 2.1, got " + speed);
        helper.assertTrue(projectile.getDeltaMovement().z > 0,
                "the shuriken flies the way the player faces (+Z), got " + projectile.getDeltaMovement());
        helper.assertTrue(projectile.pickup == AbstractArrow.Pickup.ALLOWED,
                "a throw that cost ammo stays recoverable, got " + projectile.pickup);

        ItemStack carried = projectile.getPickupItemStackOrigin();
        helper.assertTrue(carried.is(ForgeweaveItems.TOOL_SHURIKEN.get())
                        && ShurikenItem.currentAmmo(carried) == 1 && !ToolItem.isBroken(carried),
                "the entity carries a one-ammo, never-broken snapshot, got " + carried + " at "
                        + ShurikenItem.currentAmmo(carried) + " ammo");
        projectile.discard();
        helper.succeed();
    }

    /**
     * {@code ProjectileCore#useAmmo}'s endgame and {@code Shuriken#onItemRightClick}'s Broken gate:
     * spending the last ammo breaks the tool ("Empty"), and a broken shuriken refuses to throw.
     * Station repair is the reload -- that path is every tool's and already covered by the repair
     * tests; what is the shuriken's own here is that ammo 0 means Broken, not a live tool with
     * durability change left over.
     */
    @GameTest(template = "empty")
    public static void lastThrowBreaksAndBrokenRefusesToThrow(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack shuriken = shuriken(helper, player, pos);
        shuriken.setDamageValue(40); // one ammo left

        List<ShurikenEntity> thrown = throwOnce(helper, player, shuriken);
        helper.assertTrue(thrown.size() == 1, "the last ammo still throws, got " + thrown.size());
        thrown.forEach(ShurikenEntity::discard);
        helper.assertTrue(ToolItem.isBroken(shuriken),
                "spending the last ammo breaks the shuriken, got damage " + shuriken.getDamageValue());
        helper.assertTrue(ShurikenItem.currentAmmo(shuriken) == 0,
                "a broken shuriken reports 0 ammo, got " + ShurikenItem.currentAmmo(shuriken));

        InteractionResult broken = shuriken.getItem()
                .use(helper.getLevel(), player, InteractionHand.MAIN_HAND).getResult();
        helper.assertTrue(broken == InteractionResult.FAIL, "a broken shuriken refuses to throw, got " + broken);
        List<ShurikenEntity> after = helper.getLevel().getEntitiesOfClass(ShurikenEntity.class,
                new AABB(player.position(), player.position()).inflate(4.0));
        helper.assertTrue(after.isEmpty(), "no projectile from a broken shuriken, got " + after.size());
        helper.succeed();
    }

    /**
     * The flat-damage port ({@code ShurikenEntity#onHitEntity}): a wood shuriken's stored attack 3.0
     * lands at {@code damagePotential} 0.7 = 2.1, which vanilla's integer arrow pipeline ceils to 3
     * -- wherever along its flight it connects, since the speed factor is cancelled out.
     */
    @GameTest(template = "empty")
    public static void hitDealsFlatToolDamage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(20.0F); // aim down from eye height onto the pig's hitbox three blocks out
        ItemStack shuriken = shuriken(helper, player, pos);

        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 5));
        pig.setNoAi(true);
        float before = pig.getHealth();

        List<ShurikenEntity> thrown = throwOnce(helper, player, shuriken);
        helper.assertTrue(thrown.size() == 1, "one projectile, got " + thrown.size());

        helper.succeedWhen(() -> {
            helper.assertTrue(Math.abs((before - pig.getHealth()) - 3.0F) < 1.0E-4,
                    "expected ceil(3.0 * 0.7 attack) = 3 flat damage, got " + (before - pig.getHealth()));
        });
    }

    /**
     * {@code TinkerProjectileHandler#pickup}: a picked-up shuriken projectile tops up a matching
     * shuriken in the inventory by one ammo (un-breaking an empty one) instead of occupying a slot;
     * with no match it falls through to vanilla's add-the-item branch.
     */
    @GameTest(template = "empty")
    public static void pickupTopsUpAMatchingShurikenByOneAmmo(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack shuriken = shuriken(helper, player, pos);
        shuriken.setDamageValue(30); // 2 of 5 ammo
        player.getInventory().setItem(9, shuriken);

        ItemStack projectile = shuriken.copy();
        projectile.setDamageValue(40); // the one-ammo snapshot a thrown entity carries

        helper.assertTrue(ShurikenItem.restoreAmmo(player, projectile),
                "a matching, non-full shuriken absorbs the pickup");
        helper.assertTrue(shuriken.getDamageValue() == 20 && ShurikenItem.currentAmmo(shuriken) == 3,
                "one ammo (10 durability) restored, got damage " + shuriken.getDamageValue());

        shuriken.setDamageValue(0);
        helper.assertTrue(!ShurikenItem.restoreAmmo(player, projectile),
                "a full shuriken absorbs nothing; vanilla's own pickup branch takes over");
        helper.succeed();
    }
}
