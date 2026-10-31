package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import dev.gkissel.forgeweave.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import dev.gkissel.forgeweave.gametest.GameTestHolder;
import dev.gkissel.forgeweave.gametest.PrefixGameTestTemplate;

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
        // Checklist line 77: every block mined pays its own point, and each of the 3 extra blocks pays
        // the shipped durability_per_block of 4 on top (VeinBreak's "on top of its own"), so 1 + 3 x 5.
        helper.assertTrue(pickaxe.getDamageValue() == before + 1 + 3 * (1 + 4),
                "each block must cost its own 1 and each of the three extra blocks 4 more, so " + (before + 16)
                        + ", went from " + before + " to " + pickaxe.getDamageValue());
        helper.succeed();
    }

    /**
     * Checklist line 77: a 20-block vein comes out 12 blocks past the origin and no further (the
     * shipped {@code max_blocks}, which does not count the origin), each extra block at its own point
     * plus the shipped 4.
     */
    @GameTest(template = "empty")
    public static void veinseekerStopsTwelveBlocksIntoALongerVein(GameTestHelper helper) {
        ServerPlayer player = holding(helper, "veinseeker");
        ItemStack pickaxe = player.getMainHandItem();
        List<BlockPos> vein = new java.util.ArrayList<>();
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 4; z++) {
                vein.add(new BlockPos(1 + x, 1, 1 + z));
            }
        }
        for (BlockPos pos : vein) {
            helper.setBlock(pos, Blocks.IRON_ORE);
        }
        int before = pickaxe.getDamageValue();

        player.gameMode.destroyBlock(helper.absolutePos(vein.get(0)));

        long taken = vein.stream().filter(pos -> helper.getBlockState(pos).isAir()).count();
        helper.assertTrue(taken == 1 + 12, "the origin and 12 more must come out of a 20-block vein, got " + taken);
        helper.assertTrue(pickaxe.getDamageValue() == before + 1 + 12 * (1 + 4),
                "each block must cost its own 1 and each of the 12 extra blocks 4 more, so " + (before + 61)
                        + ", went from " + before + " to " + pickaxe.getDamageValue());
        helper.succeed();
    }

    /** Checklist lines 77 and 79: hollowstone tools carry veinseeker; faultsteel armor carries backlash. */
    @GameTest(template = "empty")
    public static void hollowstoneCarriesVeinseekerAndFaultsteelBacklash(GameTestHelper helper) {
        var materials = helper.getLevel().registryAccess().lookupOrThrow(Material.REGISTRY);
        Material hollowstone = materials.getValueOrThrow(net.minecraft.resources.ResourceKey.create(Material.REGISTRY,
                id("hollowstone")));
        Material faultsteel = materials.getValueOrThrow(net.minecraft.resources.ResourceKey.create(Material.REGISTRY,
                id("faultsteel")));
        helper.assertTrue(hollowstone.traits().general().contains(id("veinseeker")),
                "hollowstone must carry veinseeker, got " + hollowstone.traits().general());
        helper.assertTrue(faultsteel.traits().armor().contains(id("backlash")),
                "faultsteel armor must carry backlash, got " + faultsteel.traits().armor());
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

        Pig first = helper.spawn(EntityTypes.PIG, new BlockPos(2, 2, 2));
        first.setNoAi(true);
        Pig second = helper.spawn(EntityTypes.PIG, new BlockPos(3, 2, 2));
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
     * Checklist line 78: three kills in a row bank three charges, the tooltip says so, and the next
     * blow is worth 3 x 1.5 = 4.5 more. Each kill is a blow of this sword, the way a player banks.
     */
    @GameTest(template = "empty")
    public static void warchargeBanksThreeKillsAndTheTooltipSaysSo(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = pickaxe(List.of(id("warcharge")));
        player.setItemInHand(InteractionHand.MAIN_HAND, sword);
        Pig target = helper.spawn(EntityTypes.PIG, new BlockPos(3, 2, 2));
        target.setNoAi(true);

        for (int kill = 0; kill < 3; kill++) {
            Pig victim = helper.spawn(EntityTypes.PIG, new BlockPos(2, 2, 2));
            victim.setNoAi(true);
            ForgeweaveTraits.bonusDamageAgainst(sword, victim, 4.0F);
            victim.setHealth(0.0F);
            ForgeweaveTraits.afterHit(sword, level, player, victim);
            victim.discard();
        }

        float banked = ForgeweaveTraits.bonusDamageAgainst(sword, target, 4.0F);
        helper.assertTrue(Math.abs(banked - 4.5F) < 0.001F, "three banked charges must be worth 4.5 damage, got "
                + banked);
        List<net.minecraft.network.chat.Component> lines = new java.util.ArrayList<>();
        ForgeweaveTraits.lookup(id("warcharge")).stateLines(sword, lines::add);
        helper.assertValueEqual(lines, List.of(net.minecraft.network.chat.Component
                        .translatable("tooltip.forgeweave.trait.banked_strike", 3, "4.5")
                        .withStyle(net.minecraft.ChatFormatting.GRAY)),
                "the tooltip's banked line");

        for (int kill = 0; kill < 2; kill++) {
            Pig victim = helper.spawn(EntityTypes.PIG, new BlockPos(2, 2, 2));
            victim.setNoAi(true);
            victim.setHealth(0.0F);
            ForgeweaveTraits.afterHit(sword, level, player, victim);
            victim.discard();
        }
        float capped = ForgeweaveTraits.bonusDamageAgainst(sword, target, 4.0F);
        helper.assertTrue(Math.abs(capped - 6.0F) < 0.001F, "the bank must stop at its cap of 4, worth 6 damage, got "
                + capped);
        target.discard();
        helper.succeed();
    }

    /**
     * Checklist line 79: set upon by a group, the piece erupts on everything within its 4 blocks.
     * Two attackers take turns; a bystander 3.5 blocks off is caught and one 5 blocks off is not.
     */
    @GameTest(template = "empty")
    public static void backlashReachesFourBlocksAndNoFurther(GameTestHelper helper) {
        Player player = wearing(helper, "backlash");
        Zombie east = bystander(helper, player, 1.0, 0.0);
        Zombie west = bystander(helper, player, -1.0, 0.0);
        Zombie near = bystander(helper, player, 0.0, 3.5);
        Zombie far = bystander(helper, player, 0.0, 5.0);
        float nearHealth = near.getHealth();
        float farHealth = far.getHealth();

        for (int i = 0; i < 10; i++) {
            Zombie attacker = i % 2 == 0 ? east : west;
            hit(player, helper.getLevel().damageSources().mobAttack(attacker), 8.0F);
        }

        helper.assertTrue(near.getHealth() < nearHealth, "the eruption must reach a bystander 3.5 blocks off");
        helper.assertTrue(far.getHealth() == farHealth, "the eruption must not reach a bystander 5 blocks off");
        for (Zombie zombie : List.of(east, west, near, far)) {
            zombie.discard();
        }
        helper.succeed();
    }

    private static Zombie bystander(GameTestHelper helper, Player player, double dx, double dz) {
        Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
        zombie.setNoAi(true);
        zombie.setPos(player.getX() + dx, player.getY(), player.getZ() + dz);
        return zombie;
    }

    /**
     * Checklist line 82: the bowstring decides nothing about draw speed. A string and a vine
     * bowstring on the same wooden limbs draw at the limbs' own 1.0, truestring or not.
     */
    @GameTest(template = "empty")
    public static void aBowstringLeavesTheDrawSpeedAlone(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var shortbow = ForgeweaveItems.TOOL_SHORTBOW.get();
        ItemStack string = ToolAssembly.assemble(helper, player, STATION, ToolAssembly.entryFor(shortbow),
                List.of("wood", "wood", "string"));
        ItemStack vine = ToolAssembly.assemble(helper, player, STATION, ToolAssembly.entryFor(shortbow),
                List.of("wood", "wood", "vine"));
        helper.assertTrue(shortbow.drawSpeed(string) == 1.0F && shortbow.drawSpeed(vine) == 1.0F,
                "both bowstrings must draw at the limbs' 1.0, got " + shortbow.drawSpeed(string) + " and "
                        + shortbow.drawSpeed(vine));
        helper.succeed();
    }

    /**
     * Checklist line 86, the bow half, on the shipped numbers: a truesteel longbow hits harder than one
     * of sunsteel or hollowsteel, the two metals it is alloyed from. It does not draw faster: truesteel's
     * draw speed is 0.59 against sunsteel's 0.60 and hollowsteel's 0.66, which is drift in the checklist.
     */
    @GameTest(template = "empty")
    public static void aTruesteelLongbowHitsHarderThanItsIngredients(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack truesteel = longbow(helper, player, "truesteel");
        ItemStack sunsteel = longbow(helper, player, "sunsteel");
        ItemStack hollowsteel = longbow(helper, player, "hollowsteel");
        var longbow = ForgeweaveItems.TOOL_LONGBOW.get();
        float damage = dev.gkissel.forgeweave.item.BowItem.launcherStats(truesteel).bonusDamage();

        helper.assertTrue(damage > dev.gkissel.forgeweave.item.BowItem.launcherStats(sunsteel).bonusDamage()
                        && damage > dev.gkissel.forgeweave.item.BowItem.launcherStats(hollowsteel).bonusDamage(),
                "truesteel limbs must out-hit both their ingredients, got " + damage);
        helper.assertTrue(Math.abs(longbow.drawSpeed(truesteel) - 0.59F) < 0.001F
                        && Math.abs(longbow.drawSpeed(sunsteel) - 0.60F) < 0.001F
                        && Math.abs(longbow.drawSpeed(hollowsteel) - 0.66F) < 0.001F,
                "draw speeds must be the shipped 0.59, 0.60 and 0.66, got " + longbow.drawSpeed(truesteel) + ", "
                        + longbow.drawSpeed(sunsteel) + " and " + longbow.drawSpeed(hollowsteel));
        helper.succeed();
    }

    private static ItemStack longbow(GameTestHelper helper, Player player, String limbs) {
        return ToolAssembly.assembleAtForge(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_LONGBOW.get()), List.of(limbs, limbs, "iron", "string"));
    }

    /**
     * {@code backlash}: a worn piece stores a quarter of every blow and, once 20 damage is stored,
     * throws the store at whatever is standing next to the wearer.
     */
    @GameTest(template = "empty")
    public static void backlashStoresBlowsThenEruptsOnWhatIsNearby(GameTestHelper helper) {
        Player player = wearing(helper, "backlash");
        Zombie attacker = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(2, 2, 2));
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

        GameTestCompat.setDayTime(level, NOON);
        level.updateSkyBrightness();
        helper.assertTrue(level.canSeeSky(player.blockPosition()), "test assumes open sky above the player");
        float sunlit = speed(helper, player, pos);
        helper.assertTrue(Math.abs(sunlit - 2.8F) < 0.001F, "expected 2 + 40% = 2.8 in the sun, got " + sunlit);

        GameTestCompat.setDayTime(level, MIDNIGHT);
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
        List<Identifier> traits = bow.get(ForgeweaveDataComponents.TRAITS.get());
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

    /**
     * A survival mock player wearing an iron chestplate whose trait list is exactly {@code traits}.
     * Stood inside the test: the mock player starts at the world origin, which 26.1 runs its tests
     * nowhere near, so the zombies {@link #bystander} places beside it would sit in unloaded chunks
     * no entity search reaches.
     */
    private static Player wearing(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Vec3 stand = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
        player.setPos(stand.x, stand.y, stand.z);
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
    private static ItemStack pickaxe(List<Identifier> traits) {
        ToolItem toolItem = ForgeweaveItems.TOOL_PICKAXE.get();
        ToolStats.Stats stats = new ToolStats.Stats(1000, 2.0F, 1.0F);
        Material head = new Material(
                new Material.Head(1000, 2.0F, 1.0F),
                new Material.Handle(1.0F, 0),
                0,
                TagKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("incorrect_for_netherite_tool")),
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

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private SignatureTraitGameTests() {}
}
