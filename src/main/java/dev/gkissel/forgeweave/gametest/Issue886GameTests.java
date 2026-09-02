package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.CapturedMob;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #886: murkiron's {@code dusksnare} capture half -- the gesture gate (sneak), the health gate
 * (15% of max), the boss exclusion, and the release. Same hand-built-tool pattern as
 * {@link Issue884GameTests}, whose {@code holding}/{@code pickaxe}/{@code traitId} helpers this class
 * copies rather than shares, for the same reason that class copied them.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class Issue886GameTests {

    /** A sneaking hit on a mob below the health gate snares it: the mob is gone, the cage is in hand. */
    @GameTest(template = "empty")
    public static void dusksnareCapturesAWoundedMobWhileSneaking(GameTestHelper helper) {
        ServerPlayer player = holding(helper);
        player.setShiftKeyDown(true);
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        target.setHealth(target.getMaxHealth() * 0.1F);

        ForgeweaveTraits.DUSKSNARE.afterHit(player.getMainHandItem(), helper.getLevel(), player, target);

        helper.assertTrue(target.isRemoved(), "the snared zombie must be taken out of the world");
        CapturedMob captured = cageIn(player);
        helper.assertTrue(captured != null, "the wielder must be holding a filled Dusk Cage");
        helper.assertTrue(captured.entityType().equals(ResourceLocation.withDefaultNamespace("zombie")),
                "the cage must name the captured type, got " + captured.entityType());
        helper.succeed();
    }

    /** The gesture gate: the same beaten mob, hit without sneaking, is not captured. */
    @GameTest(template = "empty")
    public static void dusksnareIgnoresAHitWithoutSneaking(GameTestHelper helper) {
        ServerPlayer player = holding(helper);
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        target.setHealth(target.getMaxHealth() * 0.1F);

        ForgeweaveTraits.DUSKSNARE.afterHit(player.getMainHandItem(), helper.getLevel(), player, target);

        helper.assertTrue(!target.isRemoved(), "a standing hit must leave the zombie in the world");
        helper.assertTrue(cageIn(player) == null, "a standing hit must not produce a cage");
        helper.succeed();
    }

    /** The health gate: 20% of max health is above the 15% the maintainer set, so nothing happens. */
    @GameTest(template = "empty")
    public static void dusksnareIgnoresAMobAboveTheHealthGate(GameTestHelper helper) {
        ServerPlayer player = holding(helper);
        player.setShiftKeyDown(true);
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        target.setHealth(target.getMaxHealth() * 0.2F);

        ForgeweaveTraits.DUSKSNARE.afterHit(player.getMainHandItem(), helper.getLevel(), player, target);

        helper.assertTrue(!target.isRemoved(), "a zombie above the health gate must stay in the world");
        helper.assertTrue(cageIn(player) == null, "a zombie above the health gate must not produce a cage");
        helper.succeed();
    }

    /** The boss exclusion: the wither is in {@code c:bosses}, so no health is low enough. */
    @GameTest(template = "empty")
    public static void dusksnareRefusesABoss(GameTestHelper helper) {
        ServerPlayer player = holding(helper);
        player.setShiftKeyDown(true);
        WitherBoss target = helper.spawn(EntityType.WITHER, new BlockPos(2, 2, 2));
        target.setHealth(1.0F);

        ForgeweaveTraits.DUSKSNARE.afterHit(player.getMainHandItem(), helper.getLevel(), player, target);

        helper.assertTrue(!target.isRemoved(), "a boss must never be snared");
        helper.assertTrue(cageIn(player) == null, "a boss must not produce a cage");
        helper.succeed();
    }

    /** Release: right-clicking a block puts the mob back at the health it was captured at. */
    @GameTest(template = "empty")
    public static void duskCageReleasesTheMobAtTheHealthItWasCapturedAt(GameTestHelper helper) {
        ServerPlayer player = holding(helper);
        player.setShiftKeyDown(true);
        Zombie target = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        float capturedHealth = 2.5F;
        target.setHealth(capturedHealth);

        ForgeweaveTraits.DUSKSNARE.afterHit(player.getMainHandItem(), helper.getLevel(), player, target);
        ItemStack cage = player.getInventory().getItem(findCageSlot(player));
        player.setItemInHand(InteractionHand.MAIN_HAND, cage);

        BlockPos floor = new BlockPos(4, 1, 4);
        helper.setBlock(floor, Blocks.STONE);
        BlockPos clicked = helper.absolutePos(floor);
        cage.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));

        List<Zombie> released = helper.getLevel().getEntitiesOfClass(Zombie.class,
                new AABB(clicked).inflate(3.0));
        helper.assertTrue(released.size() == 1, "expected exactly one released zombie, got " + released.size());
        float health = released.get(0).getHealth();
        helper.assertTrue(health == capturedHealth,
                "the released zombie must keep the health it was captured at (" + capturedHealth + "), got " + health);
        helper.assertTrue(cage.isEmpty(), "releasing must consume the cage, " + cage.getCount() + " left");
        helper.succeed();
    }

    private static CapturedMob cageIn(ServerPlayer player) {
        int slot = findCageSlot(player);
        return slot < 0 ? null : player.getInventory().getItem(slot).get(ForgeweaveDataComponents.CAPTURED_MOB.get());
    }

    private static int findCageSlot(ServerPlayer player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            if (player.getInventory().getItem(slot).is(ForgeweaveItems.DUSK_CAGE.get())) {
                return slot;
            }
        }
        return -1;
    }

    /** A survival {@link ServerPlayer} holding a hand-built pickaxe (the trait is called directly). */
    private static ServerPlayer holding(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe(List.of(traitId("dusksnare"))));
        return player;
    }

    /** Builds a pickaxe {@code ItemStack} with the given traits directly. */
    private static ItemStack pickaxe(List<ResourceLocation> traits) {
        ToolItem toolItem = ForgeweaveItems.TOOL_PICKAXE.get();
        ToolStats.Stats stats = new ToolStats.Stats(1000, 6.0F, 5.0F);
        Material head = new Material(
                new Material.Head(1000, 6.0F, 5.0F),
                new Material.Handle(1.0F, 0),
                0,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));

        ItemStack stack = new ItemStack(toolItem);
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        stack.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        stack.set(DataComponents.TOOL, toolItem.toolComponent(head, stats));
        stack.set(DataComponents.MAX_DAMAGE, 1000);
        stack.set(DataComponents.DAMAGE, 0);
        return stack;
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
