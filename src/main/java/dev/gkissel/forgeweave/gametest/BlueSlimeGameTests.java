package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSpawnOverride;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #451 (parity audit T20), the blue slime. Upstream 1.12 is three small pieces --
 * {@code EntityBlueSlime}, {@code assets/tconstruct/loot_tables/entities/blueslime.json} and the
 * blue-slime half of {@code WorldEvents#extraSlimeSpawn} -- and this covers all three: where it may
 * stand, what it drops (and at which size), that a split keeps the type, and that an island really
 * replaces its monster spawns with upstream's weight-15/2-4 blue slime entry.
 *
 * <p>The renderer is client-only and has no server seam to test; its tint and texture are asserted
 * by review against upstream's {@code RenderTinkerSlime#FACTORY_BlueSlime}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class BlueSlimeGameTests {

    /** Rolls per loot assertion -- 0-2 slime balls means a single roll can legitimately be empty. */
    private static final int DROP_ROLLS = 200;

    /**
     * Upstream {@code EntityBlueSlime#getCanSpawnHere}: slime grass below, nothing else. The
     * island's other surfaces -- its dirt, its congealed slime -- and plain overworld ground all
     * refuse, which is what keeps the mob on the islands.
     */
    @GameTest(template = "empty")
    public static void aBlueSlimeMaySpawnOnlyOnSlimeGrass(GameTestHelper helper) {
        BlockPos floor = new BlockPos(1, 1, 1);
        BlockPos above = floor.above();

        helper.setBlock(floor, ForgeweaveBlocks.BLUE_SLIME_SOIL.grass().get());
        helper.assertTrue(maySpawnAt(helper, above), "a blue slime must spawn on slime grass");

        helper.setBlock(floor, ForgeweaveBlocks.BLUE_SLIME_SOIL.dirt().get());
        helper.assertFalse(maySpawnAt(helper, above), "slime dirt is not slime grass -- upstream checks grass only");

        helper.setBlock(floor, ForgeweaveBlocks.GREEN_CONGEALED_SLIME.get());
        helper.assertFalse(maySpawnAt(helper, above), "congealed slime must not carry a blue slime spawn");

        helper.setBlock(floor, Blocks.GRASS_BLOCK);
        helper.assertFalse(maySpawnAt(helper, above), "plain overworld ground must not carry a blue slime spawn");

        helper.succeed();
    }

    /**
     * The predicate is registered against the entity type through {@code SpawnPlacements}, with
     * upstream's own 1.20 placement/heightmap pair -- so vanilla's spawner asks it, not just this
     * test. A wrong heightmap would put island slimes underground.
     */
    @GameTest(template = "empty")
    public static void theBlueSlimeIsRegisteredWithAnOnGroundSurfacePlacement(GameTestHelper helper) {
        EntityType<Slime> type = ForgeweaveEntities.BLUE_SLIME.get();
        helper.assertTrue(SpawnPlacements.getPlacementType(type) == SpawnPlacementTypes.ON_GROUND,
                "expected an ON_GROUND placement, got " + SpawnPlacements.getPlacementType(type));
        helper.assertTrue(SpawnPlacements.getHeightmapType(type) == Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                "expected the surface heightmap, got " + SpawnPlacements.getHeightmapType(type));
        helper.succeed();
    }

    /**
     * Upstream {@code EntityBlueSlime#getLootTable}: the drop is gated to the smallest slime, every
     * larger one returning {@code LootTableList.EMPTY}. Ported as the size condition on the generated
     * table, so this rolls the real table both ways.
     */
    @GameTest(template = "empty")
    public static void onlyTheSmallestBlueSlimeDropsSlimeBalls(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Slime tiny = helper.spawn(ForgeweaveEntities.BLUE_SLIME.get(), new BlockPos(1, 2, 1));
        tiny.setSize(1, true);
        Slime big = helper.spawn(ForgeweaveEntities.BLUE_SLIME.get(), new BlockPos(3, 2, 3));
        big.setSize(2, true);

        int fromTiny = rollSlimeBalls(level, tiny, player);
        int fromBig = rollSlimeBalls(level, big, player);

        helper.assertTrue(fromTiny > 0,
                "expected a size-1 blue slime to drop slime balls over " + DROP_ROLLS + " rolls");
        helper.assertTrue(fromBig == 0,
                "upstream drops nothing above size 1, got " + fromBig);

        tiny.discard();
        big.discard();
        helper.succeed();
    }

    /**
     * Upstream {@code EntityBlueSlime#createInstance}: a split blue slime yields blue slimes. On 1.21
     * {@code Slime#remove} does that off {@code getType()} for free, which is the whole reason this
     * port needs no entity class -- so it gets a test rather than a comment.
     */
    @GameTest(template = "empty")
    public static void aSplitBlueSlimeYieldsBlueSlimes(GameTestHelper helper) {
        Slime parent = helper.spawn(ForgeweaveEntities.BLUE_SLIME.get(), new BlockPos(2, 2, 2));
        parent.setSize(2, true);

        // Straight to the seam: Slime#remove is where upstream's createInstance moved to, and it
        // fires on the removal, not on the twenty-tick death animation a plain kill() would start.
        List<Slime> children = SpawnCapture.spawnedDuring(helper, Slime.class, () -> {
            parent.setHealth(0.0F);
            parent.remove(Entity.RemovalReason.KILLED);
        });

        helper.assertTrue(!children.isEmpty(), "a size-2 slime must split when it dies");
        for (Slime child : children) {
            helper.assertTrue(child.getType() == ForgeweaveEntities.BLUE_SLIME.get(),
                    "a blue slime's halves must be blue slimes, got " + child.getType());
            helper.assertTrue(child.getSize() == 1, "expected size-1 halves, got " + child.getSize());
        }

        children.forEach(Entity::discard);
        helper.succeed();
    }

    /**
     * Upstream {@code WorldEvents#extraSlimeSpawn} in full: inside a slime island the monster spawn
     * list is <em>cleared</em> and replaced by one entry,
     * {@code new Biome.SpawnListEntry(EntityBlueSlime.class, 15, 2, 4)}. Ported as the slime island
     * structure's {@code spawn_overrides} (#629 made the island a structure, which is what makes the
     * override reachable) -- a structure spawn override replaces the biome's list for the given
     * category inside the piece's bounding box, which is upstream's clear-then-add exactly.
     */
    @GameTest(template = "empty")
    public static void theSlimeIslandStructureReplacesItsMonsterSpawnsWithBlueSlimes(GameTestHelper helper) {
        ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE,
                ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "slime_island"));
        Structure island = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        helper.assertTrue(island != null, "expected a structure registered as " + key.location());

        StructureSpawnOverride override = island.spawnOverrides().get(MobCategory.MONSTER);
        helper.assertTrue(override != null, "an island must override its monster spawns, upstream clears the list");
        helper.assertTrue(override.boundingBox() == StructureSpawnOverride.BoundingBoxType.PIECE,
                "the override must apply over the island itself, got " + override.boundingBox());

        List<MobSpawnSettings.SpawnerData> spawns = override.spawns().unwrap();
        helper.assertTrue(spawns.size() == 1,
                "upstream leaves exactly one entry after its clear, got " + spawns.size());
        MobSpawnSettings.SpawnerData entry = spawns.get(0);
        helper.assertTrue(entry.type == ForgeweaveEntities.BLUE_SLIME.get(),
                "that one entry must be the blue slime, got " + entry.type);
        helper.assertTrue(entry.getWeight().asInt() == 15,
                "upstream weights the blue slime 15, got " + entry.getWeight().asInt());
        helper.assertTrue(entry.minCount == 2 && entry.maxCount == 4,
                "upstream spawns 2-4 at a time, got " + entry.minCount + "-" + entry.maxCount);
        helper.succeed();
    }

    /** The spawn egg upstream's {@code registerModEntity(..., hasEgg = true)} asks for. */
    @GameTest(template = "empty")
    public static void theSpawnEggMakesABlueSlime(GameTestHelper helper) {
        ItemStack egg = new ItemStack(ForgeweaveItems.BLUE_SLIME_SPAWN_EGG.get());
        SpawnEggItem item = (SpawnEggItem) egg.getItem();
        helper.assertTrue(item.getType(egg) == ForgeweaveEntities.BLUE_SLIME.get(),
                "the spawn egg must make a blue slime, got " + item.getType(egg));
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static boolean maySpawnAt(GameTestHelper helper, BlockPos relative) {
        return ForgeweaveEntities.canSpawnHere(ForgeweaveEntities.BLUE_SLIME.get(), helper.getLevel(),
                MobSpawnType.NATURAL, helper.absolutePos(relative), helper.getLevel().getRandom());
    }

    private static int rollSlimeBalls(ServerLevel level, Slime victim, Player killer) {
        LootTable table = level.getServer().reloadableRegistries()
                .getLootTable(ForgeweaveEntities.BLUE_SLIME.get().getDefaultLootTable());
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.THIS_ENTITY, victim)
                .withParameter(LootContextParams.ORIGIN, victim.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, level.damageSources().playerAttack(killer))
                .withParameter(LootContextParams.ATTACKING_ENTITY, killer)
                .withParameter(LootContextParams.LAST_DAMAGE_PLAYER, killer)
                .create(LootContextParamSets.ENTITY);
        int balls = 0;
        for (int i = 0; i < DROP_ROLLS; i++) {
            for (ItemStack stack : table.getRandomItems(params)) {
                if (stack.is(Items.SLIME_BALL)) {
                    balls += stack.getCount();
                }
            }
        }
        return balls;
    }
}
