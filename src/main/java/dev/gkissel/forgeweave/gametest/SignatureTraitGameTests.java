package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #1114's four signature behaviours, one test per behaviour: the effect a player is meant to
 * feel within a minute of use, proved on a real tool or a worn piece.
 *
 * <p>Staged the way {@link UtilityTraitGameTests} and {@link ArmorTraitLibraryGameTests} stage the
 * behaviour-library batches: the tool is hand-built with exactly the trait ids under test, and the
 * worn piece is assembled at a real Tool Station and then given the ids, so the numbers asserted
 * here are the shipped {@code trait_definition} files' own.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class SignatureTraitGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    private static final long NOON = 6000L;
    private static final long MIDNIGHT = 18000L;

    /**
     * {@code veinseeker}: one swing on a block of ore takes the run of connected ore with it, and
     * the tool pays a durability point per extra block on top of the origin's own cost.
     */
    @GameTest(template = "empty")
    public static void veinseekerTakesTheWholeVeinAtADurabilityPrice(GameTestHelper helper) {
        ServerPlayer player = holding(helper, "veinseeker");
        ItemStack pickaxe = player.getMainHandItem();
        BlockPos origin = new BlockPos(1, 1, 1);
        List<BlockPos> vein = List.of(origin, origin.above(), origin.above().east(), origin.east());
        for (BlockPos pos : vein) {
            helper.setBlock(pos, Blocks.IRON_ORE);
        }
        BlockPos stranger = origin.above(2);
        helper.setBlock(stranger, Blocks.STONE);
        int before = pickaxe.getDamageValue();

        player.gameMode.destroyBlock(helper.absolutePos(origin));

        for (BlockPos pos : vein) {
            helper.assertTrue(helper.getBlockState(pos).isAir(),
                    "every connected ore block must come out in the one swing, " + pos + " did not");
        }
        helper.assertBlockPresent(Blocks.STONE, stranger);
        helper.assertTrue(pickaxe.getDamageValue() >= before + vein.size(),
                "the three extra blocks must each cost a durability point on top of the origin's own, went from "
                        + before + " to " + pickaxe.getDamageValue());
        helper.succeed();
    }

    /** {@code veinseeker}: an ore block on its own is just an ore block, and costs nothing extra. */
    @GameTest(template = "empty")
    public static void veinseekerCostsNothingOnALoneBlock(GameTestHelper helper) {
        ServerPlayer player = holding(helper, "veinseeker");
        ItemStack pickaxe = player.getMainHandItem();
        BlockPos origin = new BlockPos(1, 1, 1);
        helper.setBlock(origin, Blocks.IRON_ORE);
        int before = pickaxe.getDamageValue();

        player.gameMode.destroyBlock(helper.absolutePos(origin));

        helper.assertTrue(helper.getBlockState(origin).isAir(), "the origin block must be gone");
        helper.assertTrue(pickaxe.getDamageValue() <= before + 1,
                "a lone block must cost only its own durability, went from " + before + " to "
                        + pickaxe.getDamageValue());
        helper.succeed();
    }

    /**
     * {@code warcharge}: a kill banks a charge worth 1.5 damage, the bank pays into the next blow,
     * and that blow clears it.
     */
    @GameTest(template = "empty")
    public static void warchargeBanksAKillAndSpendsItOnTheNextBlow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = pickaxe(List.of(id("warcharge")));
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);

        Pig first = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        first.setNoAi(true);
        Pig second = helper.spawn(EntityType.PIG, new BlockPos(3, 2, 2));
        second.setNoAi(true);

        helper.assertTrue(ForgeweaveTraits.bonusDamageAgainst(sword, second, 4.0F) == 0.0F,
                "an empty bank must add nothing");

        first.setHealth(0.0F);
        ForgeweaveTraits.afterHit(sword, level, player, first);
        helper.assertTrue(sword.get(ForgeweaveDataComponents.BANKED_CHARGES.get()) != null,
                "a kill must bank a charge");

        float banked = ForgeweaveTraits.bonusDamageAgainst(sword, second, 4.0F);
        helper.assertTrue(Math.abs(banked - 1.5F) < 0.001F,
                "one banked charge must be worth 1.5 damage, got " + banked);

        ForgeweaveTraits.afterHit(sword, level, player, second);
        helper.assertTrue(sword.get(ForgeweaveDataComponents.BANKED_CHARGES.get()) == null,
                "the blow that spent the bank must clear it");
        helper.assertTrue(ForgeweaveTraits.bonusDamageAgainst(sword, second, 4.0F) == 0.0F,
                "a spent bank must add nothing again");

        first.discard();
        second.discard();
        helper.succeed();
    }

    /**
     * {@code backlash}: a worn piece stores a quarter of every blow and, once 20 damage is stored,
     * throws the store at whatever is standing next to the wearer.
     */
    @GameTest(template = "empty")
    public static void backlashStoresBlowsThenEruptsOnWhatIsNearby(GameTestHelper helper) {
        Player player = wearing(helper, "backlash");
        Zombie attacker = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        attacker.setNoAi(true);
        attacker.setPos(player.getX() + 1.0, player.getY(), player.getZ());
        DamageSource source = helper.getLevel().damageSources().mobAttack(attacker);
        float attackerHealth = attacker.getHealth();

        // A quarter of 8 is 2 stored a blow, so the tenth blow is the one that crosses 20.
        for (int i = 0; i < 9; i++) {
            hit(player, source, 8.0F);
        }
        helper.assertTrue(attacker.getHealth() >= attackerHealth,
                "the store must not erupt before it reaches the threshold");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST)
                        .get(ForgeweaveDataComponents.STORED_BLOW.get()) != null,
                "a part-filled store has to be on the piece");

        hit(player, source, 8.0F);

        helper.assertTrue(attacker.getHealth() < attackerHealth,
                "the eruption must hurt whoever is standing next to the wearer");
        helper.assertTrue(player.getItemBySlot(EquipmentSlot.CHEST)
                        .get(ForgeweaveDataComponents.STORED_BLOW.get()) == null,
                "an eruption empties the store");

        attacker.discard();
        helper.succeed();
    }

    /**
     * {@code sunforged}: 40% faster under open daylight, 10% slower out of the sun. Both halves,
     * because the penalty is what makes it a choice rather than a stat.
     */
    @GameTest(template = "empty")
    public static void sunforgedIsFasterInTheSunAndSlowerOutOfIt(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe(List.of(id("sunforged"))));
        helper.assertTrue(!level.isRaining(), "test assumes a clear sky");

        level.setDayTime(NOON);
        level.updateSkyBrightness();
        helper.assertTrue(level.canSeeSky(player.blockPosition()), "test assumes open sky above the player");
        float sunlit = speed(helper, player, pos);
        helper.assertTrue(Math.abs(sunlit - 2.8F) < 0.001F, "expected 2 + 40% = 2.8 in the sun, got " + sunlit);

        level.setDayTime(MIDNIGHT);
        level.updateSkyBrightness();
        float dark = speed(helper, player, pos);
        helper.assertTrue(Math.abs(dark - 1.8F) < 0.001F, "expected 2 - 10% = 1.8 out of the sun, got " + dark);
        helper.succeed();
    }

    /**
     * {@code truestring} on a real station-assembled shortbow: a fully drawn shot strays 60% less,
     * and a shot short of full draw gets nothing. Read through the driver {@code BowItem#shoot}
     * multiplies into its spread, because the spread itself is consumed by
     * {@code Projectile#shoot}'s own jitter and is recorded nowhere on the arrow.
     */
    @GameTest(template = "empty")
    public static void truestringTightensAFullyDrawnShotOnly(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = ToolAssembly.assemble(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_SHORTBOW.get()), List.of("wood", "wood", "string"));
        List<ResourceLocation> traits = bow.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(id("truestring")),
                "a string bowstring must put truestring on the bow, got " + traits);

        float full = ForgeweaveTraits.shotInaccuracyFactor(bow, 1.0F);
        helper.assertTrue(Math.abs(full - 0.4F) < 0.001F,
                "a fully drawn shot must keep four tenths of the spread, got " + full);
        float partial = ForgeweaveTraits.shotInaccuracyFactor(bow, 0.9F);
        helper.assertTrue(Math.abs(partial - 1.0F) < 0.001F,
                "a shot short of full draw must get nothing, got " + partial);
        helper.succeed();
    }

    /**
     * {@code featherglide}: a feather fletching halves the arrow's gravity, which is what flattens a
     * long shot. The control is the same arrow fletched with a slime leaf, whose two traits touch
     * neither gravity nor flight.
     */
    @GameTest(template = "empty")
    public static void featherglideHalvesTheArrowsDrop(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack feathered = ToolAssembly.assemble(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_ARROW.get()), List.of("wood", "wood", "feather"));
        ItemStack plain = ToolAssembly.assemble(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_ARROW.get()), List.of("wood", "wood", "slimeleaf_blue"));

        float glide = ForgeweaveTraits.projectileGravityFactor(feathered);
        helper.assertTrue(Math.abs(glide - 0.5F) < 0.001F,
                "a feather fletching must halve the arrow's gravity, got " + glide);
        float control = ForgeweaveTraits.projectileGravityFactor(plain);
        helper.assertTrue(Math.abs(control - 1.0F) < 0.001F,
                "a slime-leaf fletching must leave gravity alone, got " + control);
        helper.succeed();
    }

    /**
     * {@code leafsprung}: one shot in four costs no arrow. Rolled 600 times, so a fair quarter
     * cannot miss the band (the chance of landing outside 18% to 32% is under one in a million) and
     * a wiring that answers always or never cannot land inside it.
     */
    @GameTest(template = "empty")
    public static void leafsprungSometimesCostsNoArrow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack leafFletched = ToolAssembly.assemble(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_ARROW.get()), List.of("wood", "wood", "leaf"));
        ItemStack feathered = ToolAssembly.assemble(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_ARROW.get()), List.of("wood", "wood", "feather"));

        int saved = 0;
        for (int i = 0; i < 600; i++) {
            if (ForgeweaveTraits.savesAmmo(leafFletched, player)) {
                saved++;
            }
        }
        helper.assertTrue(saved > 108 && saved < 192,
                "600 shots at one in four must save between 108 and 192 arrows, got " + saved);

        for (int i = 0; i < 100; i++) {
            helper.assertFalse(ForgeweaveTraits.savesAmmo(feathered, player),
                    "a feather fletching must never save an arrow");
        }
        helper.succeed();
    }

    // ------------------------------------------------------------------ staging

    private static float speed(GameTestHelper helper, Player player, BlockPos pos) {
        PlayerEvent.BreakSpeed event =
                new PlayerEvent.BreakSpeed(player, Blocks.STONE.defaultBlockState(), 2.0F, pos);
        ForgeweaveTraits.onBreakSpeed(event);
        return event.getNewSpeed();
    }

    /** One blow onto a healed, non-invulnerable player ({@link ArmorTraitLibraryGameTests}' own helper). */
    private static void hit(Player player, DamageSource source, float amount) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        player.hurt(source, amount);
    }

    /** A survival mock player wearing an iron chestplate whose trait list is exactly {@code traits}. */
    private static Player wearing(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of("iron", "iron"));
        piece.set(ForgeweaveDataComponents.TRAITS.get(),
                List.of(traits).stream().map(SignatureTraitGameTests::id).toList());
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        return player;
    }

    /** A survival {@link ServerPlayer} holding a hand-built pickaxe carrying {@code traits}. */
    private static ServerPlayer holding(GameTestHelper helper, String... traits) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setGameMode(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
                pickaxe(List.of(traits).stream().map(SignatureTraitGameTests::id).toList()));
        return player;
    }

    /** A pickaxe carrying exactly {@code traits}, diamond-tier so iron ore is a legal target. */
    private static ItemStack pickaxe(List<ResourceLocation> traits) {
        ToolItem toolItem = ForgeweaveItems.TOOL_PICKAXE.get();
        ToolStats.Stats stats = new ToolStats.Stats(1000, 2.0F, 1.0F);
        Material head = new Material(
                new Material.Head(1000, 2.0F, 1.0F),
                new Material.Handle(1.0F, 0),
                0,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_netherite_tool")),
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

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private SignatureTraitGameTests() {}
}
