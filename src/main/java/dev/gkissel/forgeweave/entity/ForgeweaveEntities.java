package dev.gkissel.forgeweave.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.level.ServerLevelAccessor;

import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.levelgen.Heightmap;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;

/**
 * Forgeweave's entity types: the dropped tool (issue #447), the thrown shuriken (issue #448) and
 * the blue slime (issue #451). Deliberately just the registry plumbing; see
 * {@link IndestructibleItemEntity} and {@link ShurikenEntity} for what each entity does.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD)
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

    /**
     * The blue slime (issue #451, parity audit T20), upstream 1.12's {@code EntityBlueSlime}: the
     * island mob, a slime in every respect but its colour, its drop and where it may spawn.
     *
     * <p><b>No entity class of its own.</b> Upstream 1.12 needs one for four things and modern
     * Minecraft answers all four somewhere else:
     * <ul>
     *   <li>{@code createInstance} -- what a split slime's halves are. 1.21's {@code Slime#remove}
     *       calls {@code this.getType().create(level)}, so the halves are already blue slimes.</li>
     *   <li>{@code getLootTable} returning {@code EMPTY} above size 1 -- now the
     *       {@code minecraft:entity_properties} size condition on the generated loot table, which is
     *       how vanilla's own slime table states the same rule.</li>
     *   <li>{@code dropItemWithOffset} forcing the blue slime ball -- the loot table names the item
     *       directly (see {@code ForgeweaveEntityLootSubProvider} for the stand-in it names until
     *       T57 ships the coloured slime balls).</li>
     *   <li>{@code getCanSpawnHere} -- {@link #canSpawnHere} below, registered as this type's
     *       {@code SpawnPlacements} predicate, which is exactly what upstream itself moved that
     *       method to on modern Minecraft ({@code SlimePlacementPredicate} on the 1.20 branch).</li>
     * </ul>
     * The dimensions, eye height, spawn scale and tracking range are vanilla {@code EntityType#SLIME}'s
     * to the number: a blue slime is the same body, so anything that differed would be a hitbox bug
     * rather than a design choice.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<Slime>> BLUE_SLIME =
            ENTITY_TYPES.register("blue_slime",
                    () -> EntityType.Builder.<Slime>of(Slime::new, MobCategory.MONSTER)
                            .sized(0.52F, 0.52F)
                            .eyeHeight(0.325F)
                            .spawnDimensionsScale(4.0F)
                            .clientTrackingRange(10)
                            .build("blue_slime"));

    /**
     * Upstream {@code EntityBlueSlime#getCanSpawnHere}: a blue slime may only appear standing on
     * slime grass -- which, since slime grass only exists on a slime island, is upstream's "spawn on
     * the islands" rule stated as a block check rather than a world query.
     *
     * <p>Upstream 1.12 also allows liquid slime (the island lake). Forgeweave has no slime fluid
     * block yet (parity audit T57 -- the same gap that leaves {@code SlimeIslandFeature} without its
     * lake), so that branch has nothing to test against and is left out rather than approximated.
     *
     * <p>The peaceful and spawner clauses are not upstream 1.12 additions: they come from
     * {@code Mob#checkMobSpawnRules}, which upstream's full {@code getCanSpawnHere} override
     * replaced wholesale in 1.12 but which a {@code SpawnPlacements} predicate replaces the same way
     * on 1.21 -- so they are restated here exactly as upstream's own 1.20 branch restates them in
     * {@code SlimePlacementPredicate}. Note what is deliberately *not* here: a light-level gate.
     * Upstream's islands float in daylight and its slimes spawn on them regardless.
     */
    public static boolean canSpawnHere(EntityType<Slime> type, ServerLevelAccessor level,
            MobSpawnType reason, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) {
            return false;
        }
        if (reason == MobSpawnType.SPAWNER) {
            return true;
        }
        return ForgeweaveBlocks.isSlimeGrass(level.getBlockState(pos.below()).getBlock());
    }

    /**
     * The blue slime's attributes. Vanilla registers {@code EntityType#SLIME} with exactly these
     * ({@code DefaultAttributes}), and so does upstream's own 1.20 branch for its slimes.
     */
    @SubscribeEvent
    static void entityAttributes(EntityAttributeCreationEvent event) {
        event.put(BLUE_SLIME.get(), Monster.createMonsterAttributes().build());
    }

    /**
     * Where a blue slime may appear. This is one half of upstream 1.12's
     * {@code WorldEvents#extraSlimeSpawn}; the other half is the slime island structure's
     * {@code spawn_overrides} (data/forgeweave/worldgen/structure/slime_island.json), which is
     * upstream's {@code getList().clear()} plus its one weight-15/2-4 entry. Upstream asks both
     * questions too -- its spawn entry only ever reaches a slime island, and
     * {@code EntityBlueSlime#getCanSpawnHere} still has to say yes to the exact block.
     */
    @SubscribeEvent
    static void registerSpawnPlacements(RegisterSpawnPlacementsEvent event) {
        event.register(BLUE_SLIME.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ForgeweaveEntities::canSpawnHere,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    private ForgeweaveEntities() {}
}
