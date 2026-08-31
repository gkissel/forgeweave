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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #829's verification: one test per new M6 utility/economy library instance ({@code sunmend},
 * {@code duskmend}, {@code cascading}, {@code fertilizing}). {@code writable}/{@code writable2}'s own
 * generalization pin lives with their existing coverage in {@link MiningTraitGameTests}; {@code
 * ecological}'s own regression lives in {@link TraitGameTests}. Like {@link MiningTraitGameTests},
 * tools here are assembled by hand ({@link #pickaxe}) -- the materials that will grant these traits
 * land in a later M6 roster issue, so there is nothing for a Tool Station to assemble from yet.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class UtilityTraitGameTests {

    /** {@code /time set noon} / {@code /time set midnight} -- deterministic day/night for {@link #daytime}. */
    private static final long NOON = 6000L;
    private static final long MIDNIGHT = 18000L;

    /**
     * Sunmend: heals only in direct sunlight, never at night -- {@link
     * dev.gkissel.forgeweave.trait.SelfRepairCondition#SUNLIT}'s gate is absolute (a miss on the
     * condition costs nothing, unlike the roll it also gates), so "never at night" is a certainty,
     * not a probability; "heals at noon" runs enough ticks that the chance of zero successes is
     * astronomically small (~2e-9 at the trait's own 400-ticks-per-point rate over 8000 ticks).
     */
    @GameTest(template = "empty")
    public static void sunmendHealsInSunlightNotAtNight(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("sunmend")), 1000);
        pickaxe.setDamageValue(500);

        daytime(helper, MIDNIGHT);
        tick(helper, player, pickaxe, 8000);
        helper.assertTrue(pickaxe.getDamageValue() == 500,
                "sunmend must not heal at night, damage moved to " + pickaxe.getDamageValue());

        daytime(helper, NOON);
        tick(helper, player, pickaxe, 8000);
        helper.assertTrue(pickaxe.getDamageValue() < 500,
                "sunmend should have healed over 8000 ticks of noon, still at " + pickaxe.getDamageValue());
        helper.succeed();
    }

    /** Duskmend: the mirror of {@link #sunmendHealsInSunlightNotAtNight} -- heals only at night. */
    @GameTest(template = "empty")
    public static void duskmendHealsAtNightNotInSunlight(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        ItemStack pickaxe = pickaxe(List.of(traitId("duskmend")), 1000);
        pickaxe.setDamageValue(500);

        daytime(helper, NOON);
        tick(helper, player, pickaxe, 8000);
        helper.assertTrue(pickaxe.getDamageValue() == 500,
                "duskmend must not heal in daylight, damage moved to " + pickaxe.getDamageValue());

        daytime(helper, MIDNIGHT);
        tick(helper, player, pickaxe, 8000);
        helper.assertTrue(pickaxe.getDamageValue() < 500,
                "duskmend should have healed over 8000 ticks of midnight, still at " + pickaxe.getDamageValue());
        helper.succeed();
    }

    /**
     * Cascading: breaking the bottom sand block of a stack takes the whole column above it in the
     * same swing, and stops at the first non-matching block above it.
     */
    @GameTest(template = "empty")
    public static void cascadingBreaksTheWholeColumnAtOnce(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("cascading")));
        BlockPos origin = new BlockPos(1, 1, 1);
        helper.setBlock(origin, Blocks.SAND);
        helper.setBlock(origin.above(), Blocks.SAND);
        helper.setBlock(origin.above(2), Blocks.GRAVEL);
        BlockPos capstone = origin.above(3);
        helper.setBlock(capstone, Blocks.STONE);

        player.gameMode.destroyBlock(helper.absolutePos(origin));

        helper.assertTrue(helper.getBlockState(origin).isAir(), "the origin block must be gone");
        helper.assertTrue(helper.getBlockState(origin.above()).isAir(), "the sand above it must cascade too");
        helper.assertTrue(helper.getBlockState(origin.above(2)).isAir(), "the gravel above that must cascade too");
        helper.assertBlockPresent(Blocks.STONE, capstone);
        helper.succeed();
    }

    /**
     * Fertilizing: right-click grows a young crop and costs durability, on a hit; a block with
     * nothing to fertilize (stone) is left untouched and costs nothing.
     */
    @GameTest(template = "empty")
    public static void fertilizingGrowsACropAtADurabilityCost(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("fertilizing")));
        ItemStack pickaxe = player.getMainHandItem();
        BlockPos cropPos = new BlockPos(1, 2, 1);
        helper.setBlock(cropPos.below(), Blocks.FARMLAND);
        helper.setBlock(cropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));

        BlockPos clicked = helper.absolutePos(cropPos);
        boolean grew = false;
        for (int i = 0; i < 100 && !grew; i++) {
            pickaxe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(clicked), Direction.UP, clicked, false)));
            grew = helper.getBlockState(cropPos).getValue(CropBlock.AGE) > 0;
        }
        // 100 rolls at chance 0.5: the odds of never once succeeding are ~8e-31.
        helper.assertTrue(grew, "fertilizing should have grown the crop within 100 right-clicks");
        helper.assertTrue(pickaxe.getDamageValue() > 0,
                "a successful fertilize must cost durability, still at " + pickaxe.getDamageValue());

        int damageBefore = pickaxe.getDamageValue();
        BlockPos stonePos = new BlockPos(3, 2, 3);
        helper.setBlock(stonePos, Blocks.STONE);
        BlockPos stoneClicked = helper.absolutePos(stonePos);
        pickaxe.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(stoneClicked), Direction.UP, stoneClicked, false)));
        helper.assertTrue(pickaxe.getDamageValue() == damageBefore,
                "a block with nothing to fertilize must cost nothing, moved to " + pickaxe.getDamageValue());
        helper.succeed();
    }

    /** Sets the level's day time and immediately recomputes {@code isDay}/{@code isNight}'s input. */
    private static void daytime(GameTestHelper helper, long time) {
        helper.getLevel().setDayTime(time);
        helper.getLevel().updateSkyBrightness();
    }

    /** As {@link TraitGameTests}' own helper: drives {@code inventoryTick} directly, many times over. */
    private static void tick(GameTestHelper helper, Player holder, ItemStack stack, int ticks) {
        for (int i = 0; i < ticks; i++) {
            stack.getItem().inventoryTick(stack, helper.getLevel(), holder, 0, false);
        }
    }

    /** A survival {@link ServerPlayer} holding a hand-built pickaxe carrying {@code traits}. */
    private static ServerPlayer holding(GameTestHelper helper, List<ResourceLocation> traits) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(traits, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        return player;
    }

    /** Builds a pickaxe {@code ItemStack} with the given traits directly (see class javadoc). */
    private static ItemStack pickaxe(List<ResourceLocation> traits, int durability) {
        ToolItem toolItem = ForgeweaveItems.TOOL_PICKAXE.get();
        ToolStats.Stats stats = new ToolStats.Stats(durability, 1.0F, 1.0F);
        Material head = new Material(
                new Material.Head(durability, 1.0F, 1.0F),
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
        stack.set(DataComponents.MAX_DAMAGE, durability);
        stack.set(DataComponents.DAMAGE, 0);
        return stack;
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
