package dev.gkissel.forgeweave.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.entity.IEntityWithComplexSpawn;

import dev.gkissel.forgeweave.item.AmmoToolItem;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * A fired material arrow (issue #653, parity audit T17): upstream 1.12's
 * {@code tools/common/entity/EntityArrow} on {@code EntityProjectileBase}, re-based on vanilla's
 * {@link net.minecraft.world.entity.projectile.AbstractArrow} exactly as {@code ShurikenEntity} is
 * (see that class's javadoc for the shared reasoning -- upstream itself bases its projectile on
 * {@code EntityArrow} "otherwise minecraft does derp things").
 *
 * <h2>What is upstream's</h2>
 *
 * <ul>
 *   <li><b>Flat damage.</b> {@link #flatDamage} is computed at launch
 *       ({@code MaterialArrowItem#createProjectile}): the arrow tool's attack folded with the
 *       launcher's base/modifier damage and the draw power, upstream
 *       {@code EntityProjectileBase#onHitEntity}'s attribute stack --
 *       {@code (attack + baseProjectileDamage * power + launcherBonus) * damageModifier * power}.
 *       {@link #onHitEntity} sets {@code baseDamage = flatDamage / speed} just before handing over,
 *       the same speed-cancelling trick {@code ShurikenEntity} documents.</li>
 *   <li><b>The five ammo traits' flight halves</b> ({@code TraitHovering}, {@code TraitEndspeed},
 *       {@code TraitBreakable} -- {@code TinkerTraits:106-110}, issue #626's inert registrations):
 *       hovering keeps 5% gravity ({@code onMovement}'s {@code += gravity * 95/100}) and trails
 *       flame particles; endspeed launches at a tenth speed with no gravity, cancels air drag
 *       ({@code onMovement}'s {@code *= 1/slowdown}) and fast-forwards up to 40 blocks^2 of flight
 *       per tick ({@code onProjectileUpdate}'s {@code updateInAir} loop); breakable breaks the
 *       arrow half the time it hits a block ({@code ProjectileEvent.OnHitBlock}). Freezing and
 *       splitting are not entity concerns: freezing rides the combat seams
 *       ({@code ForgeweaveTraits#FREEZING}) and splitting the shot itself
 *       ({@code BowItem#shoot}).</li>
 *   <li><b>Fins</b> ({@code ModFins#onMovement}, issue #653): in water the arrow keeps nearly all
 *       its speed -- the water slowdown is cancelled and a slightly-eased air drag applied instead
 *       ({@code 1 - 0.01 * 0.8}); {@link #getWaterInertia} is where vanilla lets that be said.</li>
 *   <li><b>Pickup restores ammo</b> ({@code TinkerProjectileHandler#pickup}): {@link #tryPickup},
 *       exactly as the shuriken's.</li>
 *   <li><b>Roll animation</b> ({@code EntityArrow#readSpawnData}): the arrow spins around its
 *       flight axis at {@code speed * 80 / 3} degrees per tick, direction random per arrow --
 *       {@link #roll}, derived client-side with the entity id as the coin flip so it needs no
 *       extra sync.</li>
 * </ul>
 *
 * <h2>Recorded deviations (issue #653 PR)</h2>
 *
 * <ul>
 *   <li>Air drag is vanilla's hardcoded 0.99/tick against upstream's 0.99
 *       ({@code EntityProjectileBase#getSlowdown} = 0.01) -- identical, so unlike the shuriken
 *       there is nothing to compensate outside endspeed's cancellation.</li>
 *   <li>Endspeed's fast-forward runs server-side only; upstream ran its {@code updateInAir} loop on
 *       both sides. The client sees the arrow through the ordinary movement sync, which for a
 *       near-instant flight means it effectively teleports to the target -- visually equivalent at
 *       upstream's speeds. Its trail is vanilla's {@code END_ROD} particle where upstream draws its
 *       own {@code Particles.ENDSPEED}; no custom particle is ported for a trail.</li>
 *   <li>Vanilla's crit roll (a full-draw arrow adds a small random bonus in
 *       {@code AbstractArrow#onHitEntity}) stays, where upstream's flat pipeline ignored crit
 *       beyond particles -- same posture as the shuriken's recorded vanilla-plumbing deviations.</li>
 * </ul>
 */
public class ArrowEntity extends net.minecraft.world.entity.projectile.AbstractArrow
        implements IEntityWithComplexSpawn {

    /** Upstream {@code EntityProjectileBase#getGravity} = 0.05, vanilla's own arrow gravity too. */
    private static final double GRAVITY = 0.05;
    /** {@code TraitHovering#onMovement}: {@code motionY += gravity * 95 / 100} -- net 5% gravity. */
    private static final double HOVERING_GRAVITY = GRAVITY * 0.05;
    /** {@code TraitEndspeed#onMovement}: {@code motionY -= gravity / 250} while gravity is off. */
    private static final double ENDSPEED_DROOP = GRAVITY / 250.0;
    /** {@code TraitEndspeed#onProjectileUpdate}: fast-forward until this much squared distance per tick. */
    private static final double ENDSPEED_DISTANCE_SQR_PER_TICK = 40.0;
    /** {@code TraitBreakable#BREAKCHANCE}. */
    private static final float BREAK_CHANCE = 0.5F;
    /** {@code ModFins#onMovement}: water drag eased to {@code 1 - getSlowdown() * 0.8}. */
    private static final float FINS_WATER_INERTIA = 1.0F - 0.01F * 0.8F;
    /** {@code TraitHovering#onMovement} compensates water back to plain air drag. */
    private static final float HOVERING_WATER_INERTIA = 0.99F;

    /** See the class javadoc: the launch-computed flat damage this arrow lands. */
    private float flatDamage;

    /** The renderer's roll clock in ticks, frozen once stuck (upstream {@code EntityArrow#roll}). */
    private int rollTicks;

    /** Guards the endspeed fast-forward against re-entering itself through {@code super.tick()}. */
    private boolean fastForwarding;

    public ArrowEntity(EntityType<? extends ArrowEntity> type, Level level) {
        super(type, level);
    }

    /**
     * @param stack the single-ammo snapshot the entity carries and hands back on pickup
     *     ({@code ProjectileCore#getProjectileStack}); also the weapon whose traits and modifiers
     *     ride the hit ({@code AbstractArrow#firedFromWeapon} -- upstream's ammo-side trait branch,
     *     {@code EntityProjectileBase} reads its capability's item stack the same way)
     */
    public ArrowEntity(EntityType<? extends ArrowEntity> type, Level level, LivingEntity owner, ItemStack stack) {
        super(type, owner, level, stack, stack);
    }

    /** See the class javadoc. Set once at launch by {@code MaterialArrowItem#createProjectile}. */
    public void setFlatDamage(float flatDamage) {
        this.flatDamage = flatDamage;
    }

    public float flatDamage() {
        return flatDamage;
    }

    private boolean hasTrait(dev.gkissel.forgeweave.trait.Trait trait) {
        return ForgeweaveTraits.has(getPickupItemStackOrigin(), trait);
    }

    /** {@code TraitHovering#onMovement}: 5% of gravity; everything else keeps the arrow's 0.05. */
    @Override
    protected double getDefaultGravity() {
        return hasTrait(ForgeweaveTraits.HOVERING) ? HOVERING_GRAVITY : GRAVITY;
    }

    /** Fins beats water; hovering compensates water back to air drag. See the class javadoc. */
    @Override
    protected float getWaterInertia() {
        if (ForgeweaveModifiers.hasFins(getPickupItemStackOrigin())) {
            return FINS_WATER_INERTIA;
        }
        if (hasTrait(ForgeweaveTraits.HOVERING)) {
            return HOVERING_WATER_INERTIA;
        }
        return super.getWaterInertia();
    }

    @Override
    public void tick() {
        if (!this.inGround) {
            this.rollTicks++;
        }
        super.tick();
        if (this.fastForwarding || this.isRemoved()) {
            return;
        }
        boolean endspeed = hasTrait(ForgeweaveTraits.ENDSPEED);
        if (endspeed && !this.inGround) {
            // TraitEndspeed#onMovement: revert the slowdown so we don't get stuck midair, and droop
            // a whisker (gravity itself is off, MaterialArrowItem#createProjectile).
            setDeltaMovement(getDeltaMovement().scale(1.0 / 0.99).subtract(0.0, ENDSPEED_DROOP, 0.0));
        }
        if (level().isClientSide) {
            // TraitHovering#onMovement's flame trail: one flame every other tick on average.
            if (!this.inGround && hasTrait(ForgeweaveTraits.HOVERING) && this.random.nextInt(2) == 0) {
                level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(),
                        (this.random.nextFloat() - 0.5F) / 15.0F, this.random.nextFloat() / 15.0F,
                        (this.random.nextFloat() - 0.5F) / 15.0F);
            }
            return;
        }
        if (endspeed && !this.inGround && this.tickCount > 1) {
            fastForward();
        }
    }

    /**
     * {@code TraitEndspeed#onProjectileUpdate}: re-run the flight update until the arrow has covered
     * up to {@value #ENDSPEED_DISTANCE_SQR_PER_TICK} blocks^2 this tick, hit something, or stopped
     * moving -- a near-instant, still fully collision-checked flight.
     */
    private void fastForward() {
        this.fastForwarding = true;
        try {
            double traveledSqr = 0.0;
            double lastParticle = 0.0;
            while (!this.inGround && !this.isRemoved() && traveledSqr < ENDSPEED_DISTANCE_SQR_PER_TICK) {
                Vec3 before = position();
                super.tick();
                setDeltaMovement(getDeltaMovement().scale(1.0 / 0.99).subtract(0.0, ENDSPEED_DROOP, 0.0));
                double step = position().distanceToSqr(before);
                traveledSqr += step;
                if (step < 0.001) {
                    break;
                }
                lastParticle += step;
                if (lastParticle > 0.3 && level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
                    lastParticle = 0.0;
                }
            }
        } finally {
            this.fastForwarding = false;
        }
    }

    /** See the class javadoc: flat launch-computed damage through vanilla's speed-scaled formula. */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        float speed = (float) getDeltaMovement().length();
        if (speed > 1.0E-5F && flatDamage > 0.0F) {
            setBaseDamage(flatDamage / speed);
        }
        super.onHitEntity(result);
    }

    /** {@code TraitBreakable#onHitBlock}: half of all block hits break the arrow outright. */
    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!level().isClientSide && hasTrait(ForgeweaveTraits.BREAKABLE)
                && this.random.nextFloat() < BREAK_CHANCE) {
            discard();
        }
    }

    /** Upstream {@code TinkerProjectileHandler#pickup}, exactly as {@code ShurikenEntity#tryPickup}. */
    @Override
    protected boolean tryPickup(Player player) {
        if (this.pickup == Pickup.ALLOWED && AmmoToolItem.restoreAmmo(player, getPickupItemStackOrigin())) {
            return true;
        }
        return super.tryPickup(player);
    }

    /** Never reached with a real arrow aboard; vanilla requires a non-null default. */
    @Override
    protected ItemStack getDefaultPickupItem() {
        return new ItemStack(Items.AIR);
    }

    /**
     * The renderer's roll angle in degrees at {@code partialTicks}: upstream {@code RenderArrow}
     * advances {@code entity.roll += entity.rollSpeed * partialTicks} every frame while airborne
     * and stops once stuck; {@code rollSpeed = (speed * 80 / 3) * (rand direction)}
     * ({@code EntityArrow#readSpawnData}). The direction coin flip is the entity id's parity so it
     * needs no extra sync, and the speed sampled is the current one -- close enough to upstream's
     * launch-time sample for a projectile whose drag is 1%/tick.
     */
    public float roll(float partialTicks) {
        float rollSpeed = (int) ((getDeltaMovement().length() * 80.0) / 3.0)
                * (Math.floorMod(getId(), 2) == 0 ? 1 : -1);
        return (this.rollTicks + (this.inGround ? 0.0F : partialTicks)) * rollSpeed;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("forgeweave:flat_damage", this.flatDamage);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        this.flatDamage = tag.getFloat("forgeweave:flat_damage");
    }

    /**
     * Issue #697, exactly {@code ShurikenEntity#writeSpawnData}: the carried stack reaches the
     * client only through spawn data, and both the renderer and the client-side hovering flame
     * trail ({@link #hasTrait}) read it there.
     */
    @Override
    public void writeSpawnData(RegistryFriendlyByteBuf buffer) {
        ItemStack.OPTIONAL_STREAM_CODEC.encode(buffer, getPickupItemStackOrigin());
    }

    @Override
    public void readSpawnData(RegistryFriendlyByteBuf buffer) {
        setPickupItemStack(ItemStack.OPTIONAL_STREAM_CODEC.decode(buffer));
    }
}
