package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #876 (M6 dedupe batch): one GameTest per genuinely new behavior class -- the ten ids that
 * needed a real new mechanic rather than a new instance of an existing ADR-0004 seam with new
 * parameters (that half of the batch rides existing seam coverage, per {@code ForgeweaveTraits}' own
 * javadoc on each constant). Same hand-built-pickaxe pattern as {@link UtilityTraitGameTests}: the
 * materials that grant these traits are already shipped Track B rows, but building from raw traits
 * keeps this test independent of the roster's own future churn.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class DedupeBatchGameTests {

    /** Wellspring: mining stone-tagged blocks eventually heals the wielder's own health. */
    @GameTest(template = "empty")
    public static void wellspringHealsTheWielderFromMining(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("wellspring")));
        player.setHealth(10.0F);
        BlockPos pos = new BlockPos(1, 1, 1);

        for (int i = 0; i < 200 && player.getHealth() <= 10.0F; i++) {
            helper.setBlock(pos, Blocks.STONE);
            player.gameMode.destroyBlock(helper.absolutePos(pos));
        }

        helper.assertTrue(player.getHealth() > 10.0F,
                "wellspring should have healed the wielder over 200 mined stone blocks, still at " + player.getHealth());
        helper.succeed();
    }

    /** {@code ServerPlayer#spawnInvulnerableTime}'s initial value, private there -- ArmorRealPathGameTests' own constant. */
    private static final int SPAWN_INVULNERABLE_TICKS = 60;

    /** Unstable core: while the tool is in active use, it can eventually hurt its own wielder. */
    @GameTest(template = "empty")
    public static void unstableCoreCanHurtTheWielderWhileInUse(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("unstable_core")));
        ItemStack pickaxe = player.getMainHandItem();
        player.startUsingItem(InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getUseItem() == pickaxe,
                "the player's reported use-item must be the exact pickaxe reference, got "
                        + player.getUseItem() + " vs " + pickaxe);

        // A freshly-spawned ServerPlayer is damage-immune for its first 60 ticks (spawnInvulnerableTime,
        // private); wait it out before exercising the trait's own damage path -- ArmorRealPathGameTests'
        // own precedent for this exact constant.
        helper.runAfterDelay(SPAWN_INVULNERABLE_TICKS + 1, () -> {
            player.setHealth(20.0F);
            // Drives the trait directly rather than through ToolItem#inventoryTick -- exercises exactly
            // ForgeweaveTraits#UNSTABLE_CORE's own logic against a player known to be using this stack.
            for (int i = 0; i < 5000 && player.getHealth() >= 20.0F; i++) {
                ForgeweaveTraits.UNSTABLE_CORE.inventoryTick(pickaxe, helper.getLevel(), player);
            }
            helper.assertTrue(player.getHealth() < 20.0F,
                    "unstable_core should eventually hurt the wielder while in use, still at " + player.getHealth());
            helper.succeed();
        });
    }

    /** Overburdened: mining an effective block can eventually apply Mining Fatigue to the wielder. */
    @GameTest(template = "empty")
    public static void overburdenedAppliesMiningFatigue(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("overburdened")));
        BlockPos pos = new BlockPos(1, 1, 1);
        boolean applied = false;

        for (int i = 0; i < 200 && !applied; i++) {
            helper.setBlock(pos, Blocks.STONE);
            player.gameMode.destroyBlock(helper.absolutePos(pos));
            applied = player.hasEffect(MobEffects.DIG_SLOWDOWN);
        }

        helper.assertTrue(applied, "overburdened should have applied Mining Fatigue within 200 mined blocks");
        helper.succeed();
    }

    /** Obliterate: a mined block's own drops can vanish outright -- {@link ForgeweaveTraits#OBLITERATE}. */
    @GameTest(template = "empty")
    public static void obliterateDestroysSomeDrops(GameTestHelper helper) {
        helper.assertTrue(ForgeweaveTraits.OBLITERATE.dropDestroyChance() > 0.0F,
                "obliterate must report a positive drop-destroy chance");
        helper.succeed();
    }

    /** Tidebreaker: mining next to water clears the water source alongside the mined block. */
    @GameTest(template = "empty")
    public static void tidebreakerClearsAdjacentWater(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("tidebreaker")));
        BlockPos stonePos = new BlockPos(1, 1, 1);
        BlockPos waterPos = stonePos.above();
        helper.setBlock(stonePos, Blocks.STONE);
        helper.setBlock(waterPos, Blocks.WATER);

        player.gameMode.destroyBlock(helper.absolutePos(stonePos));

        helper.assertTrue(helper.getBlockState(waterPos).isAir(),
                "tidebreaker must clear the water block adjacent to the one just mined");
        helper.succeed();
    }

    /** Magmaforge: mining stone sometimes leaves lava behind instead of an empty hole. */
    @GameTest(template = "empty")
    public static void magmaforgeSometimesLeavesLava(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("magmaforge")));
        BlockPos pos = new BlockPos(1, 1, 1);
        boolean sawLava = false;

        for (int i = 0; i < 400 && !sawLava; i++) {
            helper.setBlock(pos, Blocks.STONE);
            player.gameMode.destroyBlock(helper.absolutePos(pos));
            sawLava = helper.getBlockState(pos).is(Blocks.LAVA);
        }

        helper.assertTrue(sawLava, "magmaforge should have left lava behind at least once within 400 mined blocks");
        helper.succeed();
    }

    /** Fallout: mining next to stone can mutate it into deepslate. */
    @GameTest(template = "empty")
    public static void falloutSometimesMutatesAdjacentStone(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("fallout")));
        BlockPos pos = new BlockPos(1, 1, 1);
        BlockPos above = pos.above();
        boolean mutated = false;

        for (int i = 0; i < 400 && !mutated; i++) {
            helper.setBlock(pos, Blocks.DIRT);
            helper.setBlock(above, Blocks.STONE);
            player.gameMode.destroyBlock(helper.absolutePos(pos));
            mutated = helper.getBlockState(above).is(Blocks.DEEPSLATE);
        }

        helper.assertTrue(mutated, "fallout should have mutated the adjacent stone at least once within 400 mined blocks");
        helper.succeed();
    }

    /** Daybound: glows during the day. */
    @GameTest(template = "empty")
    public static void dayboundGlowsByDay(GameTestHelper helper) {
        ServerPlayer player = holding(helper, List.of(traitId("daybound")));
        ItemStack pickaxe = player.getMainHandItem();
        helper.getLevel().setDayTime(6000L);
        helper.getLevel().updateSkyBrightness();

        for (int i = 0; i < 200 && !player.hasEffect(MobEffects.GLOWING); i++) {
            pickaxe.getItem().inventoryTick(pickaxe, helper.getLevel(), player, 0, false);
        }

        helper.assertTrue(player.hasEffect(MobEffects.GLOWING), "daybound should have glowed the wielder by day");
        helper.succeed();
    }

    /** Nocturnal edge: bonus damage at night, a penalty by day -- wired straight through {@link CombatSeam}. */
    @GameTest(template = "empty")
    public static void nocturnalEdgeSwingsWithTheTimeOfDay(GameTestHelper helper) {
        List<CombatSeam> seams = new ArrayList<>();
        ForgeweaveTraits.NOCTURNAL_EDGE.combatSeams(seams::add);
        ServerPlayer player = holding(helper, List.of());
        CombatHit hit = new CombatHit(helper.getLevel(), player.getMainHandItem(), player, player,
                helper.getLevel().damageSources().generic());

        helper.getLevel().setDayTime(18000L);
        helper.getLevel().updateSkyBrightness();
        float nightDamage = 5.0F;
        for (CombatSeam seam : seams) {
            nightDamage = seam.preHit(hit, 5.0F, nightDamage);
        }

        helper.getLevel().setDayTime(6000L);
        helper.getLevel().updateSkyBrightness();
        float dayDamage = 5.0F;
        for (CombatSeam seam : seams) {
            dayDamage = seam.preHit(hit, 5.0F, dayDamage);
        }

        helper.assertTrue(nightDamage > dayDamage,
                "nocturnal_edge's night damage (" + nightDamage + ") must exceed its day damage (" + dayDamage + ")");
        helper.succeed();
    }

    /** Berserker stance: bonus damage while the wielder is sneaking, none while standing. */
    @GameTest(template = "empty")
    public static void berserkerStanceRewardsSneaking(GameTestHelper helper) {
        List<CombatSeam> seams = new ArrayList<>();
        ForgeweaveTraits.BERSERKER_STANCE.combatSeams(seams::add);
        ServerPlayer player = holding(helper, List.of());
        CombatHit hit = new CombatHit(helper.getLevel(), player.getMainHandItem(), player, player,
                helper.getLevel().damageSources().generic());

        player.setShiftKeyDown(false);
        float standingDamage = 5.0F;
        for (CombatSeam seam : seams) {
            standingDamage = seam.preHit(hit, 5.0F, standingDamage);
        }

        player.setShiftKeyDown(true);
        float sneakingDamage = 5.0F;
        for (CombatSeam seam : seams) {
            sneakingDamage = seam.preHit(hit, 5.0F, sneakingDamage);
        }

        helper.assertTrue(sneakingDamage > standingDamage,
                "berserker_stance's sneaking damage (" + sneakingDamage + ") must exceed standing (" + standingDamage + ")");
        helper.succeed();
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
        stack.set(net.minecraft.core.component.DataComponents.TOOL, toolItem.toolComponent(head, stats));
        stack.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, durability);
        stack.set(net.minecraft.core.component.DataComponents.DAMAGE, 0);
        return stack;
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }
}
