package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.LauncherStats;
import dev.gkissel.forgeweave.trait.ShockingCharge;

/**
 * M3.5 issue #396: the launcher branches upstream 1.12 gives its modifiers and traits, on a real
 * bow -- {@code ModHaste}'s and {@code TraitLightweight}'s draw-speed bonuses, {@code ModLuck}'s
 * and {@code ModFortify}'s category refusals surfacing at the station -- plus the one thing 1.21
 * gives a vanilla arrow that 1.12 did not: it carries the stack that fired it
 * ({@code AbstractArrow#firedFromWeapon}, read back through {@code DamageSource#getWeaponItem}), so
 * the shared per-hit pipeline ({@code CombatSeams}) sees a bow's hit-effect modifiers and traits on
 * the arrow's impact exactly as it sees them on a melee blow. The arrow tests below stage that impact
 * directly -- an arrow entity built the way {@link BowItem#createArrow} builds it, and the damage
 * source vanilla's {@code AbstractArrow#onHitEntity} would use -- rather than flying one across the
 * structure, so they are deterministic.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class LauncherBranchGameTests {

    private static final double EPSILON = 1.0E-4;

    private static final ResourceLocation HASTE = id("haste");
    private static final ResourceLocation FIERY = id("fiery");
    private static final ResourceLocation SMITE = id("smite");
    private static final ResourceLocation LIGHTWEIGHT = id("lightweight");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private static ItemStack shortbow(GameTestHelper helper, Player player, BlockPos pos, String limb1, String limb2) {
        ItemStack bow = ToolAssembly.assemble(helper, player, pos,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_SHORTBOW.get()), List.of(limb1, limb2, "string"));
        helper.assertTrue(bow.is(ForgeweaveItems.TOOL_SHORTBOW.get()), "expected a shortbow, got " + bow);
        return bow;
    }

    private static ItemStack withModifier(ItemStack bow, ResourceLocation modifier, int units) {
        bow.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(modifier, units)));
        return bow;
    }

    /** {@link BowItem#createArrow}'s entity, and the damage source vanilla fires it with. */
    private static DamageSource arrowFrom(GameTestHelper helper, Player player, ItemStack bow) {
        AbstractArrow arrow = ((ArrowItem) Items.ARROW).createArrow(helper.getLevel(), new ItemStack(Items.ARROW),
                player, bow);
        return helper.getLevel().damageSources().arrow(arrow, player);
    }

    private static String key(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
    }

    // ---------------------------------------------------------------- draw speed

    /**
     * {@code ModHaste#applyEffect}, launcher branch: {@code drawSpeed += drawSpeed * 0.1f * current /
     * 50}. Wood limbs draw at 1.0; one full level of haste (50 redstone) makes that 1.1, so the
     * shortbow's 12-tick draw is full after 11 ticks instead of 12 -- and the stored stat itself is
     * untouched, the way every Forgeweave modifier stays a pure function of the tool's components.
     */
    @GameTest(template = "empty")
    public static void hasteSpeedsTheDraw(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, new BlockPos(1, 1, 1), "wood", "wood");
        BowItem item = (BowItem) bow.getItem();

        helper.assertTrue(item.drawbackProgress(bow, 11) < 1.0F, "a plain wood shortbow is not full at 11 ticks");
        helper.assertTrue(item.drawbackProgress(bow, 12) >= 1.0F, "a plain wood shortbow is full at 12 ticks");

        withModifier(bow, HASTE, 50);

        helper.assertTrue(Math.abs(item.drawSpeed(bow) - 1.1F) < EPSILON,
                "haste I on wood limbs must draw at 1.1, got " + item.drawSpeed(bow));
        helper.assertTrue(item.drawbackProgress(bow, 11) >= 1.0F, "haste I makes the 12-tick draw full at 11");
        helper.assertTrue(item.drawbackProgress(bow, 10) < 1.0F, "but not at 10");
        LauncherStats stored = BowItem.launcherStats(bow);
        helper.assertTrue(stored != null && Math.abs(stored.drawSpeed() - 1.0F) < EPSILON,
                "the stored launcher stat must stay the materials' own, got " + stored);
        helper.succeed();
    }

    /**
     * {@code TraitLightweight#applyEffect}: {@code if(hasCategory(LAUNCHER)) drawSpeed += drawSpeed *
     * 0.1f}. Cobalt limbs are lightweight (general scope) at 0.75 draw speed, so the bow draws at
     * 0.825 -- and with haste I on top the two multiply, as upstream's two {@code +=} on the same tag
     * do, to 0.9075.
     */
    @GameTest(template = "empty")
    public static void lightweightLimbsSpeedTheDrawAndStackWithHaste(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, new BlockPos(1, 1, 1), "cobalt", "cobalt");
        BowItem item = (BowItem) bow.getItem();

        List<ResourceLocation> traits = bow.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(LIGHTWEIGHT), "cobalt limbs carry lightweight, got " + traits);
        LauncherStats stored = BowItem.launcherStats(bow);
        helper.assertTrue(stored != null && Math.abs(stored.drawSpeed() - 0.75F) < EPSILON,
                "two cobalt limbs store draw speed 0.75, got " + stored);
        helper.assertTrue(Math.abs(item.drawSpeed(bow) - 0.825F) < EPSILON,
                "lightweight makes that 0.825, got " + item.drawSpeed(bow));

        withModifier(bow, HASTE, 50);
        helper.assertTrue(Math.abs(item.drawSpeed(bow) - 0.9075F) < EPSILON,
                "haste I on top multiplies to 0.9075, got " + item.drawSpeed(bow));
        helper.succeed();
    }

    // ---------------------------------------------------------------- station refusals

    /**
     * {@code ModLuck}'s aspects are {@code CategoryAnyAspect(HARVEST, WEAPON, PROJECTILE)}; a bow is
     * {@code TOOL + LAUNCHER} only, so upstream's station silently declines lapis on it. Forgeweave
     * refuses with a reason like every other declined application does
     * ({@code ModifierApplication}'s standing deviation from upstream's silent {@code EMPTY}).
     */
    @GameTest(template = "empty")
    public static void luckIsRefusedOnALauncher(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, pos, "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, bow);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT, new ItemStack(Items.LAPIS_LAZULI, 60));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "lapis on a bow must produce nothing, got " + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());
        helper.assertTrue(menu.rejection() != null && key(menu.rejection().message()).equals("gui.forgeweave.modifier.unsupported_tool"),
                "and the station must say the modifier does not fit this tool, got " + menu.rejection());

        // The same lapis on a haste-able bow slot: redstone still lands, so the refusal is luck's alone.
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT, new ItemStack(Items.REDSTONE, 50));
        menu.broadcastChanges();
        ItemStack hasted = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(!hasted.isEmpty() && new ModifierEntry(HASTE, 50).equals(hasted.get(ForgeweaveDataComponents.MODIFIERS.get()).get(0)),
                "redstone on the same bow must apply haste, got " + hasted);
        helper.succeed();
    }

    /**
     * {@code ModFortify}'s aspects include {@code harvestOnly}; a bow has no HARVEST category, so a
     * sharpening kit and flint on it fortify nothing -- refused with a reason, like luck above.
     */
    @GameTest(template = "empty")
    public static void fortifyIsRefusedOnALauncher(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, pos, "wood", "wood");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, bow);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT,
                ToolAssembly.part(ForgeweaveItems.PART_SHARPENING_KIT.get(), "cobalt"));
        blockEntity.container().setItem(ToolStationMenu.HANDLE_SLOT, new ItemStack(Items.FLINT));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a kit and flint on a bow must fortify nothing, got " + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());
        helper.assertTrue(menu.rejection() != null && key(menu.rejection().message()).equals("gui.forgeweave.fortification.not_harvest"),
                "and the station must say a launcher has no tier to set, got " + menu.rejection());
        helper.succeed();
    }

    // ---------------------------------------------------------------- hit effects ride the arrow

    /**
     * Fiery on the bow (15 raw units: 1.0 true fire damage, ignite for 2 s) lands on the arrow's
     * impact through {@code CombatSeams#onDamageDealt}, because the arrow's damage source hands back
     * the bow as its weapon.
     */
    @GameTest(template = "empty")
    public static void fieryOnTheBowRidesTheArrow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = withModifier(shortbow(helper, player, new BlockPos(1, 1, 1), "wood", "wood"), FIERY, 15);
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        target.hurt(arrowFrom(helper, player, bow), 1.0F);

        helper.assertTrue(target.getRemainingFireTicks() > 0, "fiery on the bow must ignite the arrow's target");
        helper.assertTrue(target.getHealth() <= target.getMaxHealth() - 2.0F,
                "1 arrow damage plus fiery's 1.0 true fire damage must take at least 2 health, left "
                        + target.getHealth() + "/" + target.getMaxHealth());
        target.discard();
        helper.succeed();
    }

    /** Smite on the bow (+7 vs undead at one level) adds to the arrow's damage before mitigation. */
    @GameTest(template = "empty")
    public static void smiteOnTheBowRidesTheArrow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = withModifier(shortbow(helper, player, new BlockPos(1, 1, 1), "wood", "wood"), SMITE, 24);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));

        zombie.hurt(arrowFrom(helper, player, bow), 1.0F);

        helper.assertTrue(zombie.getHealth() <= zombie.getMaxHealth() - 5.0F,
                "1 arrow damage + smite's +7 must take well over 1 health off an undead target, left "
                        + zombie.getHealth() + "/" + zombie.getMaxHealth());
        zombie.discard();
        helper.succeed();
    }

    /**
     * Squeaky's {@code damage()} returns 0 unconditionally upstream -- for a <em>melee</em> blow, the
     * only kind a 1.12 launcher's traits ever see. The arrow's own damage is not the bow's attack stat,
     * so it is left alone: a sponge-limbed bow still shoots arrows that hurt.
     */
    @GameTest(template = "empty")
    public static void squeakyLimbsDoNotZeroTheArrow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, new BlockPos(1, 1, 1), "sponge", "sponge");
        List<ResourceLocation> traits = bow.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(id("squeaky")), "sponge limbs carry squeaky, got " + traits);

        Pig meleeTarget = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);
        meleeTarget.hurt(helper.getLevel().damageSources().playerAttack(player), 4.0F);
        helper.assertTrue(meleeTarget.getHealth() >= meleeTarget.getMaxHealth() - EPSILON,
                "a melee blow with a squeaky bow deals nothing, left " + meleeTarget.getHealth());

        Pig arrowTarget = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 2));
        arrowTarget.hurt(arrowFrom(helper, player, bow), 4.0F);
        helper.assertTrue(arrowTarget.getHealth() <= arrowTarget.getMaxHealth() - 3.0F,
                "an arrow from the same bow must still hurt, left " + arrowTarget.getHealth());

        meleeTarget.discard();
        arrowTarget.discard();
        helper.succeed();
    }

    /**
     * {@code CombatHit#attackStrengthScale} is captured from the shooter's last melee swing; a
     * projectile hit is not a swing (upstream {@code ToolHelper#attackEntity} runs projectile hits with
     * {@code applyCooldown = false}), so it reports full charge regardless of what the last swing was.
     */
    @GameTest(template = "empty")
    public static void arrowHitsAreAlwaysFullCharge(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, new BlockPos(1, 1, 1), "wood", "wood");
        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        player.setItemInHand(InteractionHand.MAIN_HAND, bow);

        CAPTURE.arm();
        // A fresh mock player's attack ticker is 0, so its swing charge is far from full; record it the
        // way CombatSeams does, then land an arrow hit from the same player.
        CombatSeams.onPlayerAttack(new AttackEntityEvent(player, target));
        helper.assertTrue(player.getAttackStrengthScale(0.5F) < CombatHit.FULL_CHARGE,
                "the fixture needs a weak swing on record, got " + player.getAttackStrengthScale(0.5F));
        target.hurt(arrowFrom(helper, player, bow), 1.0F);
        CAPTURE.disarm();

        helper.assertTrue(CAPTURE.last != null, "the arrow hit must reach the pipeline");
        helper.assertTrue(CAPTURE.last.isProjectile(), "and be recorded as a projectile hit");
        helper.assertTrue(CAPTURE.last.isFullCharge(),
                "a projectile hit is full charge, got scale " + CAPTURE.last.attackStrengthScale());
        target.discard();
        helper.succeed();
    }

    /** The bow's shocking charge, 0 when it has never carried one. */
    private static float charge(ItemStack bow) {
        ShockingCharge charge = bow.get(ForgeweaveDataComponents.SHOCKING_CHARGE.get());
        return charge == null ? 0.0F : charge.charge();
    }

    /** A full charge, glint and all, the way {@code ForgeweaveTraits} writes one. */
    private static ItemStack fullyCharged(ItemStack bow) {
        bow.set(ForgeweaveDataComponents.SHOCKING_CHARGE.get(), new ShockingCharge(ShockingCharge.FULL, 0, 0, 0));
        bow.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        return bow;
    }

    /**
     * Issue #416. An arrow carries a <em>copy</em> of the bow that fired it
     * ({@code AbstractArrow#firedFromWeapon}), so a trait that writes tool state on hit used to write
     * to that copy and leave the real bow untouched -- an electrum bow at full charge discharged Speed
     * VI on every arrow hit, forever. {@code CombatSeams#hitOf} now resolves the live launcher out of
     * the shooter's hands for a projectile hit, so the state-writing half lands on the bow the player
     * is actually holding.
     */
    @GameTest(template = "empty")
    public static void arrowHitDischargesTheLiveBowNotTheArrowsCopy(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = shortbow(helper, player, new BlockPos(1, 1, 1), "electrum", "electrum");
        List<ResourceLocation> traits = bow.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(id("shocking")), "electrum limbs carry shocking, got " + traits);
        player.setItemInHand(InteractionHand.MAIN_HAND, fullyCharged(bow));

        Pig first = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        first.hurt(arrowFrom(helper, player, bow), 1.0F);

        helper.assertTrue(charge(bow) == 0.0F,
                "the arrow hit must discharge the bow the shooter is holding, charge left " + charge(bow));
        helper.assertTrue(bow.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == null,
                "and clear its full-charge glint");
        helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED),
                "the discharge itself still rides the arrow (M3.5-5's hit effects on projectiles)");

        // A second arrow finds a discharged bow: no Speed, and no charge either -- an arrow hit does
        // not build charge (see the class javadoc's #416 note).
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        Pig second = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 2));
        second.hurt(arrowFrom(helper, player, bow), 1.0F);

        helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SPEED),
                "a second arrow from a discharged bow must grant nothing");
        helper.assertTrue(charge(bow) == 0.0F,
                "and an arrow hit builds no charge of its own, got " + charge(bow));

        // The melee path is untouched: a swing with the same bow still discharges it.
        fullyCharged(bow);
        Pig melee = helper.spawn(EntityType.PIG, new BlockPos(4, 2, 2));
        melee.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);
        helper.assertTrue(charge(bow) == 0.0F, "a melee blow still discharges, charge left " + charge(bow));

        first.discard();
        second.discard();
        melee.discard();
        helper.succeed();
    }

    /**
     * Issue #416, the other half: when the shooter no longer holds the launcher there is no live stack
     * to resolve, so the seams run off the arrow's snapshot -- read-only hit effects keep working (see
     * {@link #fieryOnTheBowRidesTheArrow}, whose shooter holds nothing) and the state-writing half
     * writes to the snapshot, where it dies with the arrow. The stowed bow is left exactly as it was.
     */
    @GameTest(template = "empty")
    public static void anArrowFromAStowedBowLeavesItAlone(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = fullyCharged(shortbow(helper, player, new BlockPos(1, 1, 1), "electrum", "electrum"));
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);

        Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        target.hurt(arrowFrom(helper, player, bow), 1.0F);

        helper.assertTrue(charge(bow) == ShockingCharge.FULL,
                "a bow the shooter is not holding must not be written to, charge " + charge(bow));
        helper.assertTrue(bow.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE) == Boolean.TRUE,
                "and it keeps its glint");
        target.discard();
        helper.succeed();
    }

    /** Same arm/disarm shape as {@code CombatGameTests}' counting seam: registered once, active per test. */
    private static final class CapturingSeam implements CombatSeam {
        private boolean registered;
        private boolean armed;
        CombatHit last;

        void arm() {
            if (!registered) {
                registered = true;
                CombatSeams.register((weapon, out) -> {
                    if (armed) {
                        out.accept(this);
                    }
                });
            }
            armed = true;
            last = null;
        }

        void disarm() {
            armed = false;
        }

        @Override
        public float preHit(CombatHit hit, float originalDamage, float damage) {
            last = hit;
            return damage;
        }
    }

    private static final CapturingSeam CAPTURE = new CapturingSeam();
}
