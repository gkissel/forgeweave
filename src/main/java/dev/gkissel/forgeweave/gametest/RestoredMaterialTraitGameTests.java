package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #1092: the three trait ids seared stone and necrotic bone had named since #843 with nothing
 * behind them, each proved on real gear next to a control that carries no trait at all.
 *
 * <p>The 1.20 clone's {@code MaterialTraitsDataProvider} gives seared stone {@code searing} on every
 * part and {@code fire_protection} on its armor, and necrotic bone {@code necrotic} on every part.
 * #843 copied those rows into the two materials' JSON, but only the <em>modifiers</em> of those
 * names existed on this side; {@code ForgeweaveTraits#lookup} answered {@code null} for all three, so
 * {@code ForgeweaveTraits#of} dropped them with one line in the log and the station drew a raw lang
 * key where the trait's name belonged. The fourth row of the same clone batch, {@code restore} on
 * necrotic bone, did get a {@code Trait} and always worked.
 *
 * <p>Staging follows {@link MaterialTraitCorrectionGameTests}: gear is built from iron at a real
 * Tool Station (or through {@link CombatTraitGameTests#tool}) and its {@code forgeweave:traits}
 * component is overwritten with the shipped id, so what runs is the behaviour the material grants
 * rather than a hand-built stand-in.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class RestoredMaterialTraitGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    private static final float BLOW = 10.0F;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /** A survival mock player wearing an iron chestplate whose trait list is exactly {@code traits}. */
    private static Player wearing(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of("iron", "iron"));
        piece.set(ForgeweaveDataComponents.TRAITS.get(),
                List.of(traits).stream().map(RestoredMaterialTraitGameTests::id).toList());
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        return player;
    }

    /** One blow onto a healed, non-invulnerable player; what it cost them. */
    private static float lost(Player player, DamageSource source, float amount) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(source, amount);
        return before - player.getHealth();
    }

    /**
     * Seared stone's {@code searing}: what the tool mines comes out smelted, the same drop swap the
     * {@code searing} modifier and the {@code autosmelt} trait both make. The control is the same
     * pickaxe with no trait at all, which leaves the ore alone.
     */
    @GameTest(template = "empty")
    public static void searingSmeltsWhatASearedStoneToolMines(GameTestHelper helper) {
        ItemStack plain = MiningTraitGameTests.pickaxe(List.of(), 100, 1.0F, 1.0F);
        ItemEntity control = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.IRON_ORE, 2));
        ForgeweaveModifiers.onBlockDrops(MiningTraitGameTests.dropsEvent(helper, plain, null, control));
        helper.assertTrue(control.getItem().is(Items.IRON_ORE),
                "the control pickaxe must leave the ore alone, got " + control.getItem());

        ItemStack seared = MiningTraitGameTests.pickaxe(List.of(id("searing")), 100, 1.0F, 1.0F);
        ItemEntity drop = new ItemEntity(helper.getLevel(), 0, 0, 0, new ItemStack(Items.IRON_ORE, 2));
        ForgeweaveModifiers.onBlockDrops(MiningTraitGameTests.dropsEvent(helper, seared, null, drop));

        helper.assertTrue(drop.getItem().is(Items.IRON_INGOT) && drop.getItem().getCount() == 2,
                "searing must smelt 2 iron ore into 2 iron ingot, got " + drop.getItem());
        helper.succeed();
    }

    /**
     * Necrotic bone's {@code necrotic}: a landed hit heals the wielder for a tenth of the damage
     * dealt, the {@code necrotic} modifier's own level-1 fraction. The control is the same hatchet
     * with no trait, which heals nothing.
     */
    @GameTest(template = "empty")
    public static void necroticHealsTheWielderForATenthOfTheDamageDealt(GameTestHelper helper) {
        helper.assertTrue(Math.abs(ForgeweaveModifiers.necroticLifestealFraction(1) - 0.1F) < 1e-4,
                "the trait borrows the modifier's level-1 fraction, which must still be 0.1");

        Player control = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack plain = CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(), List.of(), 3.0F);
        Zombie first = zombie(helper, new BlockPos(2, 2, 2));
        control.setHealth(control.getMaxHealth() - 5.0F);
        float controlBefore = control.getHealth();
        CombatTraitGameTests.onHit(helper, control, plain, first);
        float controlHealed = control.getHealth() - controlBefore;
        helper.assertTrue(Math.abs(controlHealed) < 0.01F,
                "a hatchet with no trait must heal nothing, got " + controlHealed);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(),
                List.of(id("necrotic")), 3.0F);
        Zombie second = zombie(helper, new BlockPos(4, 2, 2));
        player.setHealth(player.getMaxHealth() - 5.0F);
        float before = player.getHealth();
        CombatTraitGameTests.onHit(helper, player, hatchet, second); // the helper deals 1.0 damage
        float healed = player.getHealth() - before;

        helper.assertTrue(Math.abs(healed - 0.1F) < 0.01F,
                "necrotic must heal a tenth of the 1.0 damage dealt, got " + healed);

        first.discard();
        second.discard();
        helper.succeed();
    }

    /**
     * Seared stone's {@code fire_protection} on a worn piece: a fire blow costs the wearer less than
     * the same blow costs a plain iron chestplate. Same shape as {@code blast_protection}, and the
     * same 2.5 per level the {@code fire_protection} modifier pays.
     */
    @GameTest(template = "empty")
    public static void fireProtectionSoftensAFireBlowOnAWornPiece(GameTestHelper helper) {
        DamageSource fire = helper.getLevel().damageSources().inFire();

        float plain = lost(wearing(helper), fire, BLOW);
        float protectedLoss = lost(wearing(helper, "fire_protection"), fire, BLOW);

        helper.assertTrue(plain > 0.0F, "the control piece must still take the fire blow, got " + plain);
        helper.assertTrue(protectedLoss < plain,
                "fire_protection must cost the wearer less than a plain piece, " + protectedLoss + " vs " + plain);
        helper.succeed();
    }

    /**
     * The blow {@code fire_protection} softens is a fire blow and no other: a plain generic hit is
     * untouched, which is what keeps the trait from reading as blanket protection.
     */
    @GameTest(template = "empty")
    public static void fireProtectionLeavesANonFireBlowAlone(GameTestHelper helper) {
        DamageSource generic = helper.getLevel().damageSources().generic();

        float plain = lost(wearing(helper), generic, BLOW);
        float withTrait = lost(wearing(helper, "fire_protection"), generic, BLOW);

        helper.assertTrue(Math.abs(withTrait - plain) < 0.01F,
                "fire_protection must not touch a non-fire blow, " + withTrait + " vs " + plain);
        helper.succeed();
    }

    /** All three ids resolve to a behaviour now; before #1092 every one of them answered null. */
    @GameTest(template = "empty")
    public static void allThreeRestoredIdsResolveToABehaviour(GameTestHelper helper) {
        for (String path : List.of("searing", "necrotic", "fire_protection")) {
            helper.assertTrue(ForgeweaveTraits.lookup(id(path)) != null,
                    "a material names forgeweave:" + path + " as a trait, so something has to implement it");
        }
        helper.succeed();
    }

    private static Zombie zombie(GameTestHelper helper, BlockPos pos) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, pos);
        zombie.setNoAi(true);
        return zombie;
    }
}
