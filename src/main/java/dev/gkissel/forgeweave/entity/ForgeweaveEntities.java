package dev.gkissel.forgeweave.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;

/**
 * Forgeweave's entity types: the dropped tool (issue #447) and the thrown shuriken (issue #448).
 * Deliberately just the registry plumbing; see {@link IndestructibleItemEntity} and
 * {@link ShurikenEntity} for what each entity does.
 */
public final class ForgeweaveEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Forgeweave.MODID);

    /**
     * Every dropped Forgeweave tool. The dimensions, tracking range and update interval are vanilla
     * {@code EntityType#ITEM}'s to the number -- this is an item entity in every respect except that
     * it refuses to die, so anything that differed would be a rendering or sync bug rather than a
     * design choice. {@code fireImmune} is upstream 1.20's own builder flag: it is what
     * {@code Entity#fireImmune} reads, so the entity does not visually burn in lava either.
     */
    /**
     * The thrown shuriken (issue #448). Upstream 1.12 registers it 64-block tracked at a 1-tick
     * update interval ({@code TinkerRangedWeapons#registerEntities}) and {@code EntityShuriken#init}
     * sizes it 0.3 x 0.1; the tracking numbers here are vanilla's own for every {@code AbstractArrow}
     * (range 4 chunks = upstream's 64 blocks, interval 20), which is what the base class's movement
     * sync is tuned for.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<ShurikenEntity>> SHURIKEN =
            ENTITY_TYPES.register("shuriken",
                    () -> EntityType.Builder.<ShurikenEntity>of(ShurikenEntity::new, MobCategory.MISC)
                            .sized(0.3F, 0.1F)
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("shuriken"));

    public static final DeferredHolder<EntityType<?>, EntityType<IndestructibleItemEntity>> INDESTRUCTIBLE_ITEM =
            ENTITY_TYPES.register("indestructible_item",
                    () -> EntityType.Builder.<IndestructibleItemEntity>of(IndestructibleItemEntity::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .fireImmune()
                            .eyeHeight(ItemEntity.EYE_HEIGHT)
                            .clientTrackingRange(6)
                            .updateInterval(20)
                            .build("indestructible_item"));

    private ForgeweaveEntities() {}
}
