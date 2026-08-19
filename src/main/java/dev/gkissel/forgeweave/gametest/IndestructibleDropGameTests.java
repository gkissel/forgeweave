package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.entity.ForgeweaveEntities;
import dev.gkissel.forgeweave.entity.IndestructibleItemEntity;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;

/**
 * Issue #447 (parity audit T16): upstream 1.12 {@code TinkersItem#hasCustomEntity} makes <b>every</b>
 * dropped tool spawn an {@code IndestructibleEntityItem} instead of a vanilla item entity -- immune to
 * everything but the void, and never despawning. These tests pin that behavior, the void escape hatch,
 * and the save round-trip of the new entity.
 *
 * <h2>Issue #599: parts are indestructible too, by maintainer decision</h2>
 *
 * <p>#518 verified upstream 1.12's actual class hierarchy -- {@code ToolPart extends MaterialItem},
 * not {@code TinkersItem} -- and correctly concluded upstream never makes a dropped tool part
 * indestructible. This re-verifies the same hierarchy (unchanged in the pinned clone) and reaches the
 * same conclusion: {@code TinkersItem}, {@code MaterialItem} and {@code ToolPart} do not declare
 * {@code hasCustomEntity}/{@code createEntity}, and neither of {@code ToolPart}'s two upstream
 * subclasses ({@code Shard}, {@code SharpeningKit}) adds them. So this <em>is</em> a deviation from
 * 1.12 parity, not a parity fix -- recorded here because the playtest checklist (0.3.5-alpha.3, item
 * 8.a) promised parts survive lava/fire/explosions the same way tools do, and a player's hours of
 * material investment sit in the parts just as much as the assembled tool. {@link PartItem}'s
 * {@code hasCustomEntity}/{@code createEntity} pair mirrors {@link
 * dev.gkissel.forgeweave.item.ToolItem}'s exactly, so every {@code PartItem} instance -- pickaxe
 * heads, rods, bindings, bow limbs/strings, shards, and the sharpening kit (itself a plain
 * {@code PartItem}, not a subclass, in Forgeweave) -- is covered by the one override.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class IndestructibleDropGameTests {

    /** Where the tests drop things: inside the 1x1 "empty" template's bounds so cleanup collects them. */
    private static final BlockPos DROP = new BlockPos(1, 2, 1);

    /**
     * The swap itself. NeoForge's {@code hasCustomEntity} hook runs on {@code EntityJoinLevelEvent} and
     * queues the replacement as a tick task, so the assertion waits a tick: what ends up in the level is
     * an {@link IndestructibleItemEntity} carrying the same stack, and the vanilla entity is gone.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aDroppedToolBecomesAnIndestructibleEntity(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "iron", "iron");

        ItemEntity vanilla = drop(helper, pickaxe);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(vanilla.isRemoved(), "the vanilla item entity must have been replaced");
            IndestructibleItemEntity replacement = onlyIndestructible(helper);
            helper.assertTrue(ItemStack.isSameItemSameComponents(replacement.getItem(), pickaxe),
                    "the replacement must carry the very stack that was dropped");
            helper.assertTrue(replacement.getType() == ForgeweaveEntities.INDESTRUCTIBLE_ITEM.get(),
                    "the replacement must be this mod's own registered entity type");
            helper.succeed();
        });
    }

    /**
     * Upstream's whole point: fire, lava and explosions all leave the dropped tool alone. 1.12's
     * {@code IndestructibleEntityItem#attackEntityFrom} refuses every source; this asserts the three a
     * player actually loses tools to.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anIndestructibleDropSurvivesFireLavaAndExplosions(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "iron", "iron");
        drop(helper, pickaxe);

        helper.runAfterDelay(2, () -> {
            IndestructibleItemEntity dropped = onlyIndestructible(helper);
            helper.assertFalse(dropped.hurt(level.damageSources().inFire(), 4.0F),
                    "a dropped tool must be immune to fire");
            helper.assertFalse(dropped.hurt(level.damageSources().lava(), 4.0F),
                    "a dropped tool must be immune to lava");
            helper.assertFalse(dropped.hurt(level.damageSources().explosion(null), 20.0F),
                    "a dropped tool must be immune to explosions");
            helper.assertFalse(dropped.isRemoved(), "none of those hits may destroy the dropped tool");
            helper.assertTrue(dropped.fireImmune(), "a dropped tool must not catch fire either");
            helper.succeed();
        });
    }

    /**
     * The one escape hatch, upstream's {@code DamageSource.OUT_OF_WORLD} clause: a tool that falls into
     * the void is still destroyed, otherwise it would live forever under the world.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anIndestructibleDropStillDiesToTheVoid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "iron", "iron");
        drop(helper, pickaxe);

        helper.runAfterDelay(2, () -> {
            IndestructibleItemEntity dropped = onlyIndestructible(helper);
            // Vanilla's own void tick: 4 damage against an item entity's 5 health, so it takes two.
            helper.assertTrue(dropped.hurt(level.damageSources().fellOutOfWorld(), 4.0F),
                    "the void must still be able to hurt a dropped tool");
            helper.assertFalse(dropped.isRemoved(), "one void tick is not yet fatal, as for any item");
            helper.assertTrue(dropped.hurt(level.damageSources().fellOutOfWorld(), 4.0F),
                    "the second void tick must land too");
            helper.assertTrue(dropped.isRemoved(), "a tool worn down by the void must be gone");
            helper.succeed();
        });
    }

    /**
     * "No despawn", the other half of upstream's indestructibility: vanilla expires an item entity once
     * its age reaches {@code lifespan} (6000 ticks, five minutes), and this one never gets there.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anIndestructibleDropNeverDespawns(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "iron", "iron");
        drop(helper, pickaxe);

        helper.runAfterDelay(2, () -> {
            IndestructibleItemEntity dropped = onlyIndestructible(helper);
            helper.assertTrue(dropped.lifespan == Integer.MAX_VALUE,
                    "expected an unreachable lifespan, got " + dropped.lifespan);
            helper.succeed();
        });
    }

    /**
     * Save compatibility for the one new serialized format this issue ships -- the entity itself. The
     * corpus in {@code SaveCompatCorpusTest} decodes item stacks, block entities and materials, none of
     * which needs a level; an entity does, so its round-trip is pinned here instead: what
     * {@code saveWithoutId} writes decodes back into the same type, stack and lifespan.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void anIndestructibleDropSurvivesASaveRoundTrip(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1), "iron", "iron", "iron");
        drop(helper, pickaxe);

        helper.runAfterDelay(2, () -> {
            IndestructibleItemEntity dropped = onlyIndestructible(helper);
            CompoundTag tag = new CompoundTag();
            helper.assertTrue(dropped.save(tag), "a dropped tool must be saved to the region file");
            helper.assertTrue(EntityType.by(tag).orElse(null) == ForgeweaveEntities.INDESTRUCTIBLE_ITEM.get(),
                    "the saved tag must name this mod's entity type, not minecraft:item");

            Entity reloaded = EntityType.loadEntityRecursive(tag, level, e -> e);
            helper.assertTrue(reloaded instanceof IndestructibleItemEntity,
                    "a saved indestructible drop must reload as one");
            IndestructibleItemEntity reloadedDrop = (IndestructibleItemEntity) reloaded;
            helper.assertTrue(ItemStack.isSameItemSameComponents(reloadedDrop.getItem(), pickaxe),
                    "the reloaded drop must still carry the tool");
            helper.assertTrue(reloadedDrop.lifespan == Integer.MAX_VALUE,
                    "the reloaded drop must still never despawn, got " + reloadedDrop.lifespan);
            reloadedDrop.discard();
            helper.succeed();
        });
    }

    /**
     * Issue #599: a dropped tool part becomes an indestructible entity too, same as a dropped tool
     * (see the class javadoc for why this is a deliberate deviation from 1.12, not a parity fix).
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aDroppedPartBecomesAnIndestructibleEntity(GameTestHelper helper) {
        ItemStack head = new ItemStack(ForgeweaveItems.PART_PICKAXE_HEAD.get());
        helper.assertTrue(head.getItem() instanceof PartItem, "the fixture must actually be a tool part");
        ItemEntity vanilla = drop(helper, head);

        helper.runAfterDelay(2, () -> {
            helper.assertTrue(vanilla.isRemoved(), "the vanilla item entity must have been replaced");
            IndestructibleItemEntity replacement = onlyIndestructible(helper);
            helper.assertTrue(ItemStack.isSameItemSameComponents(replacement.getItem(), head),
                    "the replacement must carry the very stack that was dropped");
            helper.assertTrue(replacement.getType() == ForgeweaveEntities.INDESTRUCTIBLE_ITEM.get(),
                    "the replacement must be this mod's own registered entity type");
            helper.succeed();
        });
    }

    /**
     * A dropped sharpening kit is also a {@code PartItem} (Forgeweave has no separate class for it, per
     * the class javadoc), so it must survive lava the same way any other dropped part does.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void aDroppedSharpeningKitSurvivesLava(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ItemStack kit = new ItemStack(ForgeweaveItems.PART_SHARPENING_KIT.get());
        drop(helper, kit);

        helper.runAfterDelay(2, () -> {
            IndestructibleItemEntity dropped = onlyIndestructible(helper);
            helper.assertFalse(dropped.hurt(level.damageSources().lava(), 4.0F),
                    "a dropped sharpening kit must be immune to lava");
            helper.assertFalse(dropped.isRemoved(), "lava must not destroy the dropped sharpening kit");
            helper.succeed();
        });
    }

    private static ItemEntity drop(GameTestHelper helper, ItemStack stack) {
        BlockPos pos = helper.absolutePos(DROP);
        ItemEntity entity = new ItemEntity(helper.getLevel(), pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, stack.copy());
        entity.setDeltaMovement(0.0, 0.0, 0.0);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }

    private static List<IndestructibleItemEntity> indestructibles(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(DROP);
        AABB box = new AABB(pos).inflate(4.0);
        return helper.getLevel().getEntitiesOfClass(IndestructibleItemEntity.class, box);
    }

    private static IndestructibleItemEntity onlyIndestructible(GameTestHelper helper) {
        List<IndestructibleItemEntity> found = indestructibles(helper);
        helper.assertTrue(found.size() == 1, "expected exactly one indestructible drop, found " + found.size());
        return found.get(0);
    }
}
