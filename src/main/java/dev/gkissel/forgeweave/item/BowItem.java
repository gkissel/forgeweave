package dev.gkissel.forgeweave.item;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import net.neoforged.neoforge.event.EventHooks;

import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.LauncherStats;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * A bow (M3.5 issue #394): upstream 1.12's {@code library/tools/ranged/BowCore.java} draw/release
 * cycle on top of {@link ToolItem}'s durability, Broken and melee behavior -- the same "a bow is a
 * tool that also launches" relationship {@code BowCore extends ProjectileLauncherCore extends
 * TinkerToolCore} has upstream. Ported at the semantic level; the vanilla side is 1.21's
 * {@code BowItem}/{@code ProjectileWeaponItem}, called through where they carry the same rule
 * ({@code getPowerForTime} is 1.12's {@code ItemBow#getArrowVelocity}, {@code ARROW_ONLY} is
 * "vanilla arrows only, tipped and spectral included").
 *
 * <h2>Draw</h2>
 *
 * <p>Right-click starts a 72000-tick use ({@code BowCore#getMaxItemUseDuration}) if the tool is not
 * Broken and the player has ammo or infinite materials, after NeoForge's {@code ArrowNockEvent}.
 * Draw progress is {@link #drawbackProgress}: {@code drawSpeed * ticks / drawTime}, capped at 1
 * ({@code BowCore.java:112-114}) -- {@code drawSpeed} is the assembled {@link LauncherStats} and
 * {@code drawTime} the per-bow constant ({@code ShortBow#getDrawTime() = 12}, not {@code BowCore}'s
 * 20 default). {@link #drawSpeed} is the one seam a draw-speed modifier or trait multiplies (haste
 * and lightweight on launchers, M3.5 issue #396).
 *
 * <h2>Release</h2>
 *
 * <p>{@code BowCore#onPlayerStoppedUsing}/{@code #shootProjectile}: under 5 ticks fires nothing;
 * otherwise power is {@code getPowerForTime(progress * 20) * progress * baseProjectileSpeed *
 * range}, one arrow entity is created from the ammo item, aimed with {@code baseInaccuracy}, made
 * critical at full draw, and the bow takes one durability (never in creative). Ammo is looked up
 * held-hands-first then inventory-in-slot-order ({@code AmmoHelper#findAmmoFromInventory}: the
 * offhand equipment first, then hotbar, then the rest) and one is consumed unless the player has
 * infinite materials, in which case a plain arrow is conjured ({@code getCreativeProjectileStack}).
 *
 * <p>Pickup follows {@code BowCore#getProjectileEntity} exactly: creative arrows are
 * {@code CREATIVE_ONLY}, an arrow that cost no ammo is {@code DISALLOWED}, and an arrow that did cost
 * ammo keeps the entity's own default -- which for a player-shot arrow is {@code ALLOWED}, in 1.12
 * ({@code EntityArrow}'s shooter constructor) and 1.21 ({@code AbstractArrow#setOwner}) alike.
 *
 * <p>Deviation from 1.12, recorded here and in the PR: upstream applies a launcher's
 * {@code bonusDamage} only through {@code ILauncher#modifyProjectileAttributes}, which only its own
 * projectile entities call -- a vanilla arrow fired from a 1.12 bow deals vanilla damage and the
 * BOW stat is inert. docs/SCOPE.md M3.5 gates "arrow entity damage carries bonusDamage" and defers
 * material arrows, so here the bonus rides the vanilla arrow: added to its base damage scaled by
 * {@code 1 / launch velocity}, since vanilla multiplies base damage by the arrow's speed at impact
 * -- a hit at launch speed lands {@code bonusDamage} flat on top of vanilla's number.
 * ponytail: not scaled by draw progress the way upstream's own arrows are ({@code power - 1}
 * multiplier); add that if playtest wants weak draws to carry less bonus.
 *
 * <p>Not ported (deliberately, see the PR): per-stage draw art and the crosshair (M3.5-6),
 * {@code preventSlowDown} (a client movement-input hack), and the multi-projectile
 * {@code TinkerToolEvent.OnBowShoot} hook -- {@link #shoot} fires exactly one arrow. M3.5-5 (issue
 * #396) confirmed against the clone that nothing on a <em>launcher</em> ever raises that count: its
 * only listeners ({@code TraitSplitting}, {@code TraitEndspeed}) key on the <em>ammo's</em> traits,
 * which are Forgeweave's deferred material arrows.
 *
 * <h2>What rides the arrow</h2>
 *
 * <p>1.21's {@code AbstractArrow} remembers the stack that fired it ({@code firedFromWeapon}, set by
 * {@code ArrowItem#createArrow(level, ammo, shooter, weapon)} in {@link #createArrow}) and hands it
 * back through {@code DamageSource#getWeaponItem}, so {@code CombatSeams}' per-hit pipeline sees the
 * bow's hit-effect modifiers and traits (fiery, smite, necrotic, knockback, beheading, holy, ...) on
 * the arrow's impact exactly as it sees them on a melee blow. That is beyond 1.12, where a launcher's
 * own modifiers reach only its melee swings (M3.5-5's PR body records the maintainer's decision); the
 * one exemption is squeaky's zero-attack, a melee-stat rule ({@code ForgeweaveTraits#COMBAT_SEAM}).
 */
public class BowItem extends ToolItem {

    /** {@code BowCore#getMaxItemUseDuration}: as long as vanilla's own bow. */
    private static final int MAX_USE_DURATION = 72000;
    /** {@code BowCore#onPlayerStoppedUsing}: {@code if(useTime < 5) return;}. */
    private static final int MIN_DRAW_TICKS = 5;

    private final int drawTime;
    private final float baseProjectileSpeed;
    private final float baseInaccuracy;

    /**
     * @param constants the bow's {@link ToolConstants} entry (attack speed, damage potential, parts)
     * @param drawTime {@code BowCore#getDrawTime()}: ticks a {@code drawSpeed = 1} bow takes to draw
     * @param baseProjectileSpeed {@code BowCore#baseProjectileSpeed()}: launch velocity at full draw
     *     before the {@code range} multiplier ({@code 3f} for every bow but the longbow)
     * @param baseInaccuracy {@code BowCore#baseInaccuracy()}, the spread passed to
     *     {@code shootFromRotation}
     */
    public BowItem(Properties properties, ToolConstants.Entry constants, int drawTime, float baseProjectileSpeed,
            float baseInaccuracy) {
        // No mineable tag: a bow digs nothing. weapon = false: BowCore adds Category.LAUNCHER, never
        // Category.WEAPON, so a melee hit with it costs full durability and haste's attack-speed
        // half does not apply (its draw-speed half is M3.5-5's).
        super(properties, constants, List.of(), false, null);
        this.drawTime = drawTime;
        this.baseProjectileSpeed = baseProjectileSpeed;
        this.baseInaccuracy = baseInaccuracy;
    }

    /** {@code BowCore#getDrawTime()}. */
    public int drawTime() {
        return drawTime;
    }

    /** The assembled bow's ranged stats, or {@code null} for an unassembled (creative-tab) stack. */
    @Nullable
    public static LauncherStats launcherStats(ItemStack stack) {
        return stack.get(ForgeweaveDataComponents.LAUNCHER_STATS.get());
    }

    /**
     * How fast this stack draws: {@link LauncherStats#drawSpeed()} ({@code 1} for an unassembled
     * stack) times what its modifiers and traits do to it (M3.5 issue #396) -- upstream
     * {@code ModHaste#applyEffect}'s and {@code TraitLightweight#applyEffect}'s
     * {@code Category.LAUNCHER} branches, the only two in the 1.12 clone that touch
     * {@code ProjectileLauncherNBT#drawSpeed}. Upstream scales the stored tag at apply time; here the
     * stored stat stays the materials' own and the scaling is computed on read, as every other
     * Forgeweave modifier is (a pure function of the tool's components, ADR-0004). Nothing else reads
     * the raw stat.
     */
    public float drawSpeed(ItemStack stack) {
        LauncherStats stats = launcherStats(stack);
        float base = stats == null ? 1.0F : stats.drawSpeed();
        return base * ForgeweaveModifiers.drawSpeedMultiplier(stack) * (1.0F + ForgeweaveTraits.drawSpeedBonus(stack));
    }

    /**
     * {@code BowCore#getDrawbackProgress(ItemStack, int)}: {@code min(1, drawSpeed * ticksDrawn /
     * drawTime)}. Pure, so a unit test can pin the formula against upstream's numbers.
     */
    public float drawbackProgress(ItemStack stack, int ticksDrawn) {
        return Math.min(1.0F, drawSpeed(stack) * ticksDrawn / (float) drawTime);
    }

    /**
     * {@code BowCore#getDrawbackProgress(ItemStack, EntityLivingBase)}: the progress of the draw
     * {@code holder} currently has going on {@code stack}, or 0 if it is not drawing this stack.
     * What M3.5-6's {@code pull} item property reads.
     */
    public float drawbackProgress(ItemStack stack, LivingEntity holder) {
        return holder.isUsingItem() && holder.getUseItem() == stack
                ? drawbackProgress(stack, holder.getTicksUsingItem())
                : 0.0F;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return MAX_USE_DURATION;
    }

    /** {@code BowCore#onItemRightClick}. */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isBroken(stack)) {
            return InteractionResultHolder.fail(stack);
        }
        boolean hasAmmo = !findAmmo(player).isEmpty();
        InteractionResultHolder<ItemStack> nock = EventHooks.onArrowNock(stack, level, player, hand, hasAmmo);
        if (nock != null) {
            return nock;
        }
        if (player.hasInfiniteMaterials() || hasAmmo) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    /** {@code BowCore#onPlayerStoppedUsing}. */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        if (isBroken(stack) || !(user instanceof Player player)) {
            return;
        }
        ItemStack ammo = findAmmo(player);
        if (ammo.isEmpty() && !player.hasInfiniteMaterials()) {
            return;
        }
        int useTime = getUseDuration(stack, user) - timeLeft;
        useTime = EventHooks.onArrowLoose(stack, level, player, useTime, !ammo.isEmpty());
        if (useTime < MIN_DRAW_TICKS) {
            return; // also the event's -1 for "cancelled"
        }
        if (ammo.isEmpty()) {
            ammo = new ItemStack(Items.ARROW); // BowCore#getCreativeProjectileStack
        }
        shoot(ammo, stack, level, player, useTime);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    /**
     * {@code BowCore#shootProjectile}, for one projectile. {@code ammo} is the inventory stack the
     * arrow is drawn from (or a conjured creative arrow); it is consumed here, and the arrow is built
     * from a copy taken first so a last arrow still fires.
     */
    protected void shoot(ItemStack ammo, ItemStack bow, Level level, Player player, int useTime) {
        float progress = drawbackProgress(bow, useTime);
        LauncherStats stats = launcherStats(bow);
        float range = stats == null ? 1.0F : stats.range();
        // ItemBow.getArrowVelocity((int)(progress * 20f)) * progress * baseProjectileSpeed() * range
        float velocity = net.minecraft.world.item.BowItem.getPowerForTime((int) (progress * 20.0F))
                * progress * baseProjectileSpeed * range;

        if (level instanceof ServerLevel) {
            ItemStack toShoot = ammo.copy();
            boolean usedAmmo = consumeAmmo(ammo, player);
            AbstractArrow arrow = createArrow(toShoot, bow, level, player, velocity, baseInaccuracy, usedAmmo);
            if (progress >= 1.0F) {
                arrow.setCritArrow(true);
            }
            if (!player.hasInfiniteMaterials()) {
                // ToolHelper.damageTool(bow, 1, player); the slot only matters for the break event.
                bow.hurtAndBreak(1, player, player.getUsedItemHand() == InteractionHand.OFF_HAND
                        ? EquipmentSlot.OFFHAND : EquipmentSlot.MAINHAND);
            }
            level.addFreshEntity(arrow);
        }
        playShootSound(level, player, velocity);
    }

    /**
     * {@code BowCore#playShootSound}, verbatim pitch formula. Its own method because the crossbow
     * overrides exactly this ({@code CrossBow#playShootSound}, M3.5 issue #395).
     */
    protected void playShootSound(Level level, Player player, float velocity) {
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT,
                SoundSource.NEUTRAL, 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + velocity * 0.5F);
    }

    /** {@code BowCore#getProjectileEntity}'s {@code ItemArrow} branch -- see the class javadoc. */
    protected AbstractArrow createArrow(ItemStack ammo, ItemStack bow, Level level, Player player, float velocity,
            float inaccuracy, boolean usedAmmo) {
        ArrowItem arrowItem = ammo.getItem() instanceof ArrowItem item ? item : (ArrowItem) Items.ARROW;
        AbstractArrow arrow = arrowItem.createArrow(level, ammo, player, bow);
        arrow.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, velocity, inaccuracy);
        LauncherStats stats = launcherStats(bow);
        if (stats != null && velocity > 0.0F) {
            arrow.setBaseDamage(arrow.getBaseDamage() + stats.bonusDamage() / velocity);
        }
        if (player.hasInfiniteMaterials()) {
            arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        } else if (!usedAmmo) {
            arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        }
        return arrow;
    }

    /** {@code BowCore#consumeAmmo}: one off the stack, gone from the inventory when that empties it. */
    protected boolean consumeAmmo(ItemStack ammo, Player player) {
        if (player.hasInfiniteMaterials()) {
            return false;
        }
        ammo.shrink(1);
        if (ammo.isEmpty()) {
            player.getInventory().removeItem(ammo);
        }
        return true;
    }

    /**
     * {@code BowCore#findAmmo} through {@code AmmoHelper#findAmmoFromInventory}: the held hands first
     * (offhand before main hand, the equipment handler upstream reads first), then the inventory in
     * slot order -- hotbar, then the rest, exactly the order upstream walks it in.
     */
    public static ItemStack findAmmo(Player player) {
        ItemStack held = ProjectileWeaponItem.getHeldProjectile(player, ProjectileWeaponItem.ARROW_ONLY);
        if (!held.isEmpty()) {
            return held;
        }
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack candidate = player.getInventory().getItem(slot);
            if (ProjectileWeaponItem.ARROW_ONLY.test(candidate)) {
                return candidate;
            }
        }
        return ItemStack.EMPTY;
    }
}
