package dev.gkissel.forgeweave.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The item entity every dropped Forgeweave tool spawns as (issue #447, parity audit T16): immune to
 * everything but the void, and it never despawns. Upstream 1.12 has exactly this
 * ({@code library/tinkering/IndestructibleEntityItem}, spawned from
 * {@code TinkersItem#hasCustomEntity}/{@code #createEntity}, which every {@code ToolCore} inherits),
 * and it is not optional there -- a tool represents hours of material and modifier investment, so
 * losing one to a stray creeper or a five-minute despawn timer is the failure mode the class exists
 * to prevent. {@link dev.gkissel.forgeweave.item.ToolItem} is the Forgeweave counterpart of
 * {@code TinkersItem} and hooks it the same way.
 *
 * <h2>What "indestructible" means</h2>
 *
 * <ul>
 *   <li>{@link #isInvulnerableTo} refuses every damage source that is not tagged
 *       {@code bypasses_invulnerability} -- fire, lava, explosions, cacti, a player's attack. 1.12
 *       spells the same rule out as "refuse everything except {@code OUT_OF_WORLD}"; the tag is the
 *       modern name for that one exception, and keeping it is what stops a tool that fell into the
 *       void from living forever underneath the world.
 *   <li>The entity type is built {@code fireImmune} on top of that ({@link ForgeweaveEntities}), so
 *       the entity does not visually burn while sitting in lava either -- vanilla's fire tick and the
 *       flame overlay read {@code Entity#fireImmune}, not the damage refusal.
 *   <li>{@code lifespan} is set past any reachable age, vanilla's expiry check being
 *       {@code age >= lifespan}. 1.12 gets the same result by cancelling {@code ItemExpireEvent}.
 * </ul>
 *
 * <p>Being a distinct registered entity type rather than a flag on a vanilla one is load-bearing:
 * the type id is what the region file stores, so a tool dropped before a restart is still
 * indestructible after it. Upstream 1.20 keeps the same shape for the same reason
 * ({@code library/tools/IndestructibleItemEntity}), which is where the modern-API details here come
 * from.
 */
public class IndestructibleItemEntity extends ItemEntity {

    /**
     * Past any age vanilla can reach: {@code ItemEntity#tick} expires the entity on
     * {@code age >= lifespan} and {@code age} is a tick counter that starts at zero.
     */
    private static final int NEVER = Integer.MAX_VALUE;

    /** The registry/deserialization constructor -- also the one the client builds the entity with. */
    public IndestructibleItemEntity(EntityType<? extends IndestructibleItemEntity> type, Level level) {
        super(type, level);
        this.lifespan = NEVER;
    }

    /** The drop constructor: {@code ToolItem#createEntity} replacing a vanilla item entity. */
    public IndestructibleItemEntity(Level level, double x, double y, double z, ItemStack stack) {
        this(ForgeweaveEntities.INDESTRUCTIBLE_ITEM.get(), level);
        this.setPos(x, y, z);
        this.setItem(stack);
    }

    /**
     * Carries over the two things the replaced entity already decided: how it was thrown, and how long
     * before it can be picked up (a player who dropped a tool by hand must not instantly re-collect it).
     * {@code pickupDelay} has no accessor, so this reads it back out of the entity's own saved tag --
     * upstream 1.12 does the same thing, and for the same reason.
     */
    public void copyThrowFrom(Entity original) {
        if (original instanceof ItemEntity) {
            CompoundTag tag = new CompoundTag();
            original.saveWithoutId(tag);
            this.setPickUpDelay(tag.getShort("PickupDelay"));
        }
        this.setDeltaMovement(original.getDeltaMovement());
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }
}
