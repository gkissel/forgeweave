package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.RegistryFriendlyByteBuf;

import io.netty.buffer.Unpooled;

import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.entity.ArrowEntity;
import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.item.AmmoToolItem;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.MaterialArrowItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.tool.ProjectileStats;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #653 (parity audit T17) verification: the material arrow -- upstream
 * {@code tools/ranged/item/Arrow.java}. Assembles from shaft + head + fletching at the Tool
 * Station, is the ammo Forgeweave bows accept beside vanilla arrows ({@code BowItem#findAmmo}),
 * costs one ammo (ten durability) per shot, lands its launch-computed flat damage, and carries the
 * five ammo traits' entity-side behaviors (#626 registered them inert): splitting's double shot,
 * freezing's stacking Slowness, breakable's block-hit break, hovering's and endspeed's launch
 * halves.
 *
 * <p>Wood numbers, from {@code ToolConstants#ARROW} over wood shaft / wood head / feather
 * fletching: durability = head 35 x fletching 1.0 x shaft 1.0 + 0 = 35, so 3 ammo at 10 durability
 * each; stored attack = 2.0 + 2 = 4.0 at {@code damagePotential} 1.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArrowGameTests {

    private static ItemStack arrow(GameTestHelper helper, Player player, BlockPos pos, String shaft, String head,
            String fletching) {
        ItemStack arrow = ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_ARROW.get()), List.of(shaft, head, fletching));
        helper.assertTrue(arrow.is(ForgeweaveItems.TOOL_ARROW.get()),
                "expected the Tool Station to assemble an arrow, got " + arrow);
        return arrow;
    }

    private static ItemStack shortbow(GameTestHelper helper, Player player, BlockPos pos) {
        ItemStack bow = ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_SHORTBOW.get()), List.of("wood", "wood", "string"));
        helper.assertTrue(bow.is(ForgeweaveItems.TOOL_SHORTBOW.get()), "expected a shortbow, got " + bow);
        return bow;
    }

    /**
     * Draws the held shortbow for {@code ticks} with {@code ammo} in the offhand and lets go;
     * returns the {@link ArrowEntity}s the shot spawned (issue #643's {@code SpawnCapture} seam).
     */
    private static List<ArrowEntity> shoot(GameTestHelper helper, Player player, ItemStack bow, ItemStack ammo,
            int ticks) {
        player.moveTo(helper.absoluteVec(new Vec3(2.5, 2.0, 2.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        player.setItemInHand(InteractionHand.OFF_HAND, ammo);
        AABB near = new AABB(player.position(), player.position()).inflate(4.0);
        return SpawnCapture.spawnedDuring(helper, ArrowEntity.class, near, () -> {
            player.startUsingItem(InteractionHand.MAIN_HAND);
            int duration = bow.getUseDuration(player);
            bow.getItem().releaseUsing(bow, helper.getLevel(), player, duration - ticks);
            player.stopUsingItem();
        });
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /**
     * {@code TinkerRegistry.registerToolCrafting(arrow)}: a Tool Station tool. Stats are
     * {@code Arrow#buildTagData}'s chain with {@code attack += 2}; accuracy is the fletching's
     * ({@code ProjectileNBT}); ammo is durability over 10 ({@code ProjectileCore}); no melee
     * attributes at all. The arrow head's two-scope read (HEAD + PROJECTILE) keeps endstone's
     * {@code enderference} beside its head-scoped {@code alien} ({@code TinkerMaterials:262-264}).
     */
    @GameTest(template = "empty")
    public static void arrowAssemblesAtTheToolStationWithUpstreamStats(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        ItemStack arrow = arrow(helper, player, pos, "wood", "wood", "feather");
        ToolStats.Stats stats = arrow.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null && stats.durability() == 35 && stats.attackDamage() == 4.0F,
                "wood/wood/feather: durability 35, attack 2 + 2, got " + stats);
        helper.assertTrue(AmmoToolItem.maxAmmo(arrow) == 3 && AmmoToolItem.currentAmmo(arrow) == 3,
                "35 durability at 10 per ammo is 3 ammo, got " + AmmoToolItem.currentAmmo(arrow)
                        + "/" + AmmoToolItem.maxAmmo(arrow));
        ProjectileStats projectile = arrow.get(ForgeweaveDataComponents.PROJECTILE_STATS.get());
        helper.assertTrue(projectile != null && projectile.accuracy() == 1.0F,
                "a feather fletching is accuracy 1.0, got " + projectile);
        helper.assertTrue(((MaterialArrowItem) arrow.getItem()).getDefaultAttributeModifiers(arrow)
                        == ItemAttributeModifiers.EMPTY,
                "an arrow carries no melee attributes (ProjectileCore#getAttributeModifiers)");

        // The projectile trait scope (#653): endstone head = alien (HEAD) + enderference (PROJECTILE).
        ItemStack endstone = arrow(helper, player, pos, "wood", "endstone", "feather");
        List<ResourceLocation> traits = endstone.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(id("alien")) && traits.contains(id("enderference")),
                "an endstone arrow head grants both alien (HEAD) and enderference (PROJECTILE), got " + traits);
        helper.succeed();
    }

    /**
     * {@code AmmoHelper#findAmmoFromInventory} (issue #653): material arrows join the valid-ammo
     * set -- the offhand first, then slot order -- and an empty one is skipped exactly as upstream
     * skips an {@code IAmmo} at zero ammo.
     */
    @GameTest(template = "empty")
    public static void bowAmmoLookupTakesMaterialArrowsAndSkipsEmptyOnes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack arrow = arrow(helper, player, pos, "wood", "wood", "feather");

        player.setItemInHand(InteractionHand.OFF_HAND, arrow);
        player.getInventory().setItem(9, new ItemStack(Items.ARROW, 4));
        helper.assertTrue(BowItem.findAmmo(player) == arrow,
                "the offhand material arrow is the ammo, ahead of the vanilla arrows in the inventory");

        // Spend it empty: an ammo-less arrow is skipped and the vanilla arrows win.
        arrow.setDamageValue(arrow.getMaxDamage());
        helper.assertTrue(BowItem.findAmmo(player).is(Items.ARROW),
                "an empty material arrow is no ammo (IAmmo#getCurrentAmmo > 0), the vanilla stack is");
        helper.succeed();
    }

    /**
     * {@code BowCore#shootProjectile} + {@code Arrow#getProjectile}: one shot costs one ammo (ten
     * durability, {@code IAmmo#useAmmo}), the entity carries a one-ammo never-broken snapshot
     * ({@code ProjectileCore#getProjectileStack}), stays recoverable, and flies the way the player
     * faces. Spending the last ammo breaks the arrow stack ("Empty").
     */
    @GameTest(template = "empty")
    public static void shotCostsOneAmmoAndLastShotBreaks(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        ItemStack bow = shortbow(helper, player, pos);
        ItemStack arrow = arrow(helper, player, pos, "wood", "wood", "feather");

        List<ArrowEntity> shot = shoot(helper, player, bow, arrow, 12); // full draw
        helper.assertTrue(shot.size() == 1, "one arrow per shot, got " + shot.size());
        ArrowEntity entity = shot.get(0);
        helper.assertTrue(arrow.getDamageValue() == AmmoToolItem.DURABILITY_PER_AMMO,
                "one shot costs 10 durability, got " + arrow.getDamageValue());
        helper.assertTrue(entity.pickup == AbstractArrow.Pickup.ALLOWED,
                "a shot that cost ammo stays recoverable, got " + entity.pickup);
        helper.assertTrue(entity.getDeltaMovement().z > 0,
                "the arrow flies the way the player faces (+Z), got " + entity.getDeltaMovement());
        ItemStack carried = entity.getPickupItemStackOrigin();
        helper.assertTrue(carried.is(ForgeweaveItems.TOOL_ARROW.get())
                        && AmmoToolItem.currentAmmo(carried) == 1 && !ToolItem.isBroken(carried),
                "the entity carries a one-ammo, never-broken snapshot, got " + carried + " at "
                        + AmmoToolItem.currentAmmo(carried) + " ammo");
        entity.discard();

        arrow.setDamageValue(arrow.getMaxDamage() - AmmoToolItem.DURABILITY_PER_AMMO); // one ammo left
        shoot(helper, player, bow, arrow, 12).forEach(ArrowEntity::discard);
        helper.assertTrue(ToolItem.isBroken(arrow),
                "spending the last ammo breaks the arrow stack, got damage " + arrow.getDamageValue());

        // Upstream TinkerProjectileHandler#pickup: a picked-up arrow tops a matching stack back up
        // -- here across the Broken line, un-breaking the empty stack (ToolHelper#healTool).
        player.getInventory().setItem(9, arrow);
        helper.assertTrue(AmmoToolItem.restoreAmmo(player, carried),
                "a matching, non-full arrow stack absorbs the pickup");
        helper.assertTrue(!ToolItem.isBroken(arrow) && AmmoToolItem.currentAmmo(arrow) == 1,
                "one ammo restored un-breaks the stack, got " + AmmoToolItem.currentAmmo(arrow));
        helper.succeed();
    }

    /**
     * The flat-damage port ({@code ArrowEntity#onHitEntity}): at 11 of 12 draw ticks (progress
     * 11/12, power 0.8403 -- just under full, so no crit and no vanilla crit-bonus randomness, the
     * recorded {@code ArrowEntity} deviation) a wood arrow from a wood shortbow lands
     * {@code (4 attack + 0 base * power + 0 bonus) * 0.8 modifier * 0.8403 power = 2.6889}, which
     * vanilla's integer arrow pipeline ceils to 3 -- wherever along its flight it connects, since
     * the speed factor is cancelled out.
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void hitDealsLauncherFoldedFlatDamage(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(20.0F); // aim down from eye height onto the pig's hitbox three blocks out
        ItemStack bow = shortbow(helper, player, pos);
        ItemStack arrow = arrow(helper, player, pos, "wood", "wood", "feather");

        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 5));
        pig.setNoAi(true);
        pig.setNoGravity(true);
        float before = pig.getHealth();

        float power = (11.0F / 12.0F) * (11.0F / 12.0F);
        float expectedFlat = 4.0F * 0.8F * power; // 2.6889

        List<ArrowEntity> shot = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, pig))
                .thenExecute(() -> {
                    shot.addAll(shoot(helper, player, bow, arrow, 11)); // just under full draw: no crit
                    helper.assertTrue(shot.size() == 1, "one arrow, got " + shot.size());
                    helper.assertTrue(Math.abs(shot.get(0).flatDamage() - expectedFlat) < 1.0E-3,
                            "flat damage (4 + 0) * 0.8 * " + power + " = " + expectedFlat
                                    + ", got " + shot.get(0).flatDamage());
                })
                .thenWaitUntil(() -> helper.assertTrue(Math.abs((before - pig.getHealth()) - 3.0F) < 1.0E-4,
                        "expected ceil(" + expectedFlat + ") = 3 flat damage, got " + (before - pig.getHealth())))
                .thenSucceed();
    }

    /**
     * {@code TraitSplitting#onBowShooting} (bone shafts): 50% of shots fire two arrows for the one
     * ammo -- the second at +3 inaccuracy and, having consumed nothing,
     * {@code PickupStatus.DISALLOWED} ({@code BowCore#getProjectileEntity}'s {@code !usedAmmo}
     * rule). The stack is refilled between tries; 24 tries put a false negative at 2^-24.
     */
    @GameTest(template = "empty")
    public static void splittingFiresTwoArrowsForOneAmmo(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, pos);
        ItemStack arrow = arrow(helper, player, pos, "bone", "wood", "feather");
        List<ResourceLocation> traits = arrow.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(id("splitting")),
                "a bone shaft grants splitting (SHAFT-scoped, TinkerMaterials:272), got " + traits);

        for (int attempt = 0; attempt < 24; attempt++) {
            arrow.setDamageValue(0); // refill between tries; one try spends one ammo either way
            bow.setDamageValue(0); // the bow pays one durability per arrow, and 24 tries outspend wood's 35
            List<ArrowEntity> shot = shoot(helper, player, bow, arrow, 12);
            int spent = arrow.getDamageValue();
            shot.forEach(ArrowEntity::discard);
            helper.assertTrue(spent == AmmoToolItem.DURABILITY_PER_AMMO,
                    "a split shot still costs exactly one ammo, got " + spent + " durability");
            if (shot.size() == 2) {
                helper.assertTrue(shot.get(0).pickup == AbstractArrow.Pickup.ALLOWED,
                        "the arrow that cost the ammo stays recoverable, got " + shot.get(0).pickup);
                helper.assertTrue(shot.get(1).pickup == AbstractArrow.Pickup.DISALLOWED,
                        "the free extra arrow is never recoverable, got " + shot.get(1).pickup);
                helper.succeed();
                return;
            }
            helper.assertTrue(shot.size() == 1, "one or two arrows per shot, got " + shot.size());
        }
        helper.fail("no double shot in 24 tries of a 50% chance (odds 2^-24)");
    }

    /**
     * The commanded launch velocity of a full-drawn wood shortbow:
     * {@code getPowerForTime(20) = 1} x progress 1 x {@code baseProjectileSpeed} 3 x range 1.
     */
    private static final double FULL_DRAW_VELOCITY = 3.0;

    /**
     * Vanilla {@code Projectile#shoot} jitters each axis of the <em>normalized</em> direction by
     * {@code random.triangle(0, 0.0172275 * inaccuracy)} before scaling by the velocity, so a
     * shot's actual speed is {@code velocity * |unit + jitter|} -- off the commanded velocity by up
     * to {@code sqrt(3) * 0.0172275 * inaccuracy} relative (~3% at the shortbow's baseInaccuracy 1;
     * endspeed's 2/3-eased shot stays under the same bound). This is that bound with a little
     * float slack, and it is why the assertions below compare each shot against the commanded
     * velocity rather than against another (independently jittered) shot -- the flake CI caught.
     */
    private static final double SHOOT_JITTER_RELATIVE_BOUND = Math.sqrt(3.0) * 0.0172275 + 1.0E-3;

    /**
     * The launch halves of the flight traits ({@code AbstractProjectileTrait#onLaunch}): a blaze
     * shaft (hovering) launches at half speed; an end rod shaft (endspeed) at a tenth with gravity
     * off. Each speed is checked against the commanded full-draw velocity within vanilla's own
     * inaccuracy-jitter bound ({@link #SHOOT_JITTER_RELATIVE_BOUND}) -- the traits scale the
     * post-jitter vector, so the factor is exact per shot but the magnitude carries the jitter.
     */
    @GameTest(template = "empty")
    public static void hoveringAndEndspeedAdjustTheLaunch(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        ItemStack bow = shortbow(helper, player, pos);

        List<ArrowEntity> plain = shoot(helper, player, bow, arrow(helper, player, pos, "wood", "wood", "feather"), 12);
        List<ArrowEntity> hovering =
                shoot(helper, player, bow, arrow(helper, player, pos, "blaze", "wood", "feather"), 12);
        List<ArrowEntity> endspeed =
                shoot(helper, player, bow, arrow(helper, player, pos, "endrod", "wood", "feather"), 12);
        helper.assertTrue(plain.size() == 1 && hovering.size() == 1 && endspeed.size() == 1,
                "one arrow each, got " + plain.size() + "/" + hovering.size() + "/" + endspeed.size());

        assertLaunchSpeed(helper, plain.get(0), FULL_DRAW_VELOCITY,
                "a plain arrow launches at the commanded velocity");
        assertLaunchSpeed(helper, hovering.get(0), FULL_DRAW_VELOCITY / 2.0,
                "hovering launches at half speed (TraitHovering#onLaunch)");
        assertLaunchSpeed(helper, endspeed.get(0), FULL_DRAW_VELOCITY / 10.0,
                "endspeed launches at a tenth (TraitEndspeed#onLaunch)");
        helper.assertTrue(endspeed.get(0).isNoGravity(),
                "endspeed turns gravity off (TraitEndspeed#onLaunch setNoGravity)");
        plain.forEach(ArrowEntity::discard);
        hovering.forEach(ArrowEntity::discard);
        endspeed.forEach(ArrowEntity::discard);
        helper.succeed();
    }

    private static void assertLaunchSpeed(GameTestHelper helper, ArrowEntity arrow, double expected, String what) {
        double speed = arrow.getDeltaMovement().length();
        helper.assertTrue(Math.abs(speed - expected) <= expected * SHOOT_JITTER_RELATIVE_BOUND,
                what + ": expected " + expected + " within the " + SHOOT_JITTER_RELATIVE_BOUND
                        + " jitter bound, got " + speed);
    }

    /**
     * {@code TraitFreezing#onHit} (ice shafts): each landed hit stacks Slowness one amplifier
     * deeper, 30 ticks a hit -- the first hit applies Slowness I (amplifier 0), the second deepens
     * it to II (amplifier 1).
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void freezingStacksSlownessPerHit(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(20.0F);
        ItemStack bow = shortbow(helper, player, pos);
        ItemStack arrow = arrow(helper, player, pos, "ice", "wood", "feather");

        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 5));
        pig.setNoAi(true);
        pig.setNoGravity(true);

        helper.startSequence()
                .thenWaitUntil(() -> SpawnCapture.assertIndexServes(helper, pig))
                .thenExecute(() -> shoot(helper, player, bow, arrow, 11)) // just under full draw: no crit roll
                .thenWaitUntil(() -> {
                    MobEffectInstance slowness = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    helper.assertTrue(slowness != null && slowness.getAmplifier() == 0,
                            "the first hit applies Slowness I (30 ticks), got " + slowness);
                })
                // Timing budget: the pig blocks equal damage for ~10 ticks after a hit
                // (LivingEntity#hurt's invulnerableTime > 10 gate) and the first hit's Slowness
                // lasts 30. The wait above detects the effect at (or one tick after) the hit, so
                // idling 12 clears the invulnerability with 2 ticks to spare and leaves the second
                // arrow ~15 ticks of Slowness for its 1-2-tick flight -- the widest margin on both
                // sides at once. The aim itself is deterministic enough: shoot()'s jitter is at most
                // ~1.7 degrees (Projectile#shoot's 0.0172275-per-axis triangle), under 0.1 blocks
                // over this 3-block flight against a 0.9-wide hitbox.
                .thenIdle(12)
                .thenExecute(() -> shoot(helper, player, bow, arrow, 11))
                .thenWaitUntil(() -> {
                    MobEffectInstance slowness = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    helper.assertTrue(slowness != null && slowness.getAmplifier() == 1,
                            "the second hit deepens it to Slowness II, got " + slowness);
                })
                .thenSucceed();
    }

    /**
     * {@code TraitBreakable#onHitBlock} (reed shafts): half of all block hits break the arrow
     * outright. Shot straight into the floor; 24 tries put a false negative at 2^-24. A wood
     * arrow's control shot never breaks.
     */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void breakableBreaksOnBlockHitHalfTheTime(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setYRot(0.0F);
        player.setXRot(90.0F); // straight down into the floor
        ItemStack bow = shortbow(helper, player, pos);
        ItemStack reed = arrow(helper, player, pos, "reed", "wood", "feather");
        ItemStack wood = arrow(helper, player, pos, "wood", "wood", "feather");

        List<ArrowEntity> control = new ArrayList<>();
        List<ArrowEntity> candidates = new ArrayList<>();
        helper.startSequence()
                .thenExecute(() -> {
                    control.addAll(shoot(helper, player, bow, wood, 12));
                    for (int i = 0; i < 24; i++) {
                        reed.setDamageValue(0);
                        candidates.addAll(shoot(helper, player, bow, reed, 12));
                    }
                })
                // Give every arrow time to reach the floor a block below and roll its break.
                .thenIdle(20)
                .thenWaitUntil(() -> {
                    helper.assertTrue(control.stream().noneMatch(ArrowEntity::isRemoved),
                            "a wood arrow never breaks on a block hit");
                    helper.assertTrue(candidates.stream().anyMatch(ArrowEntity::isRemoved),
                            "no reed arrow broke in 24 block hits of a 50% chance (odds 2^-24)");
                })
                .thenExecute(() -> {
                    control.forEach(ArrowEntity::discard);
                    candidates.forEach(ArrowEntity::discard);
                })
                .thenSucceed();
    }

    /** Issue #697, exactly {@code ShurikenGameTests#spawnDataCarriesTheStackToTheClient} for the arrow. */
    @GameTest(template = "empty")
    public static void spawnDataCarriesTheStackToTheClient(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, pos);
        ItemStack arrow = arrow(helper, player, pos, "wood", "wood", "feather");
        List<ArrowEntity> shot = shoot(helper, player, bow, arrow, 12);
        helper.assertTrue(shot.size() == 1, "one arrow per shot, got " + shot.size());
        ArrowEntity entity = shot.get(0);

        helper.assertTrue(entity instanceof IEntityWithComplexSpawn,
                "the arrow entity must ship its stack in the spawn packet (#697)");
        RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                helper.getLevel().registryAccess());
        ((IEntityWithComplexSpawn) entity).writeSpawnData(buf);
        ArrowEntity clientSide = ForgeweaveEntities.ARROW.get().create(helper.getLevel());
        ((IEntityWithComplexSpawn) clientSide).readSpawnData(buf);
        helper.assertTrue(ItemStack.matches(clientSide.getPickupItemStackOrigin(), entity.getPickupItemStackOrigin()),
                "a client-constructed arrow carries the shot stack, got " + clientSide.getPickupItemStackOrigin());
        entity.discard();
        helper.succeed();
    }
}
