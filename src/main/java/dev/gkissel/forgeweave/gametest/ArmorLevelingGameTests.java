package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolLevel;
import dev.gkissel.forgeweave.tool.ToolLeveling;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * M7-6, armor leveling (issue #923; SCOPE.md D-M7-2) -- <b>original Forgeweave design</b>. Tinkers'
 * Tool Leveling has no armor code at all, so nothing here is derived from it and no
 * {@code NOTICE.md} row exists for any of it.
 *
 * <p>The rule under test: every worn, unbroken piece the defensive walk reaches earns
 * {@code max(1, round(what that piece mitigated))} once the blow has actually dealt damage, where a
 * piece's mitigation is what it took off the blow itself plus its share of the protection and flat
 * reduction the blow settled at. Same curve, same slot per level and same feedback as a tool's,
 * because it all runs through the same {@link ToolLeveling#addXp}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArmorLevelingGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    /** Big enough that the chestplate's share rounds past the floor, small enough to survive in iron. */
    private static final float BLOW = 16.0F;

    private static final EquipmentSlot[] SLOTS =
            {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static ItemStack piece(GameTestHelper helper, Player player, ToolConstants.Entry entry, String material) {
        // Issue #782: armor assembles at the Armor Station.
        return ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.ARMOR_STATION.get(),
                ToolAssembly.entryOf(entry), List.of(material, material));
    }

    /** A survival mock player in a full set of {@code material}, ticked once so the attributes apply. */
    private static Player suitedUp(GameTestHelper helper, String material) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, piece(helper, player, ToolConstants.HELMET, material));
        player.setItemSlot(EquipmentSlot.CHEST, piece(helper, player, ToolConstants.CHESTPLATE, material));
        player.setItemSlot(EquipmentSlot.LEGS, piece(helper, player, ToolConstants.LEGGINGS, material));
        player.setItemSlot(EquipmentSlot.FEET, piece(helper, player, ToolConstants.BOOTS, material));
        player.tick();
        return player;
    }

    private static int xp(Player player, EquipmentSlot slot) {
        return ToolLevel.of(player.getItemBySlot(slot)).xp();
    }

    /** One blow onto a healed, non-invulnerable player; what it cost them. */
    private static float lost(Player player, DamageSource source, float amount) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(source, amount);
        return before - player.getHealth();
    }

    /** The Armor Station loaded with {@code tool} and one reagent stack, output untaken. */
    private static ToolStationMenu load(GameTestHelper helper, Player player, ItemStack tool, ItemStack reagent) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, ItemStack.EMPTY);
        }
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, reagent);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    private static ItemStack apply(GameTestHelper helper, Player player, ItemStack tool, ItemStack reagent) {
        ToolStationMenu menu = load(helper, player, tool, reagent);
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "the station must apply " + reagent
                + (menu.rejection() != null ? " (" + menu.rejection().message().getString() + ")" : ""));
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    /**
     * The headline of the rule: one hit pays every worn piece, and pays them for what each of them
     * mitigated rather than in equal shares. Fire protection III (7.5 protection) on the chestplate
     * alone is the whole blow's protection -- iron's own {@code projectile_protection} does nothing
     * against fire -- so the chestplate takes all of the settled protection mitigation and the other
     * three ride the floor of 1.
     */
    @GameTest(template = "empty")
    public static void aHitPaysEveryWornPieceForWhatItMitigated(GameTestHelper helper) {
        Player player = suitedUp(helper, "iron");
        ItemStack chest = apply(helper, player, player.getItemBySlot(EquipmentSlot.CHEST),
                new ItemStack(ForgeweaveItems.SEARED_BRICK.get(), 15));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(chest) == 0, "fire protection III fills the three slots");
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.tick();

        float lost = lost(player, helper.getLevel().damageSources().inFire(), BLOW);
        helper.assertTrue(lost > 0.0F && player.isAlive(), "the blow must land and be survivable, lost " + lost);

        int total = 0;
        for (EquipmentSlot slot : SLOTS) {
            helper.assertTrue(xp(player, slot) >= 1,
                    slot + " was worn and unbroken, so it earns at least the floor of 1, got " + xp(player, slot));
            total += xp(player, slot);
        }
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            helper.assertTrue(xp(player, slot) == 1,
                    slot + " added no protection to this blow, so it earns exactly the floor, got " + xp(player, slot));
        }
        helper.assertTrue(xp(player, EquipmentSlot.CHEST) > xp(player, EquipmentSlot.FEET),
                "the chestplate stopped the fire, so it must out-earn the boots: "
                        + xp(player, EquipmentSlot.CHEST) + " vs " + xp(player, EquipmentSlot.FEET));
        // No piece is paid for damage the blow never lost; the four floors are the only slack.
        helper.assertTrue(total <= Math.round(BLOW - lost) + SLOTS.length,
                "the set earned " + total + " for a blow that only lost " + (BLOW - lost) + " damage");
        helper.succeed();
    }

    /**
     * Knightslime's overshield spends overslime inside its own {@code onDefend} and hands the blow
     * protection in exchange, so the absorbing piece is paid for the absorb by the same protection
     * share every other piece is paid by -- there is no separate overslime bookkeeping. The three
     * overslime and the 4-damage explosion are {@code ArmorTraitGameTests}'
     * {@code overshieldSpendsOverslimeBeforeItShieldsWear}: two shield the blow, the third pays the
     * wear. A 1.25 protection share of a 4-damage explosion rounds below 1, so the piece lands on
     * the floor here; the share arithmetic itself is what the test above pins.
     */
    @GameTest(template = "empty")
    public static void anOverslimeAbsorbPaysTheAbsorbingPiece(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack chest = piece(helper, player, ToolConstants.CHESTPLATE, "knightslime");
        player.setItemSlot(EquipmentSlot.CHEST, chest);
        player.tick();
        ForgeweaveTraits.setOverslime(chest, 3);

        lost(player, helper.getLevel().damageSources().explosion(null, null), 4.0F);
        helper.assertTrue(ForgeweaveTraits.overslime(chest) == 0,
                "two overslime shield and the third pays the wear, left " + ForgeweaveTraits.overslime(chest));
        helper.assertTrue(ToolLevel.of(chest).xp() >= 1, "and the piece that spent them is paid for the absorb");
        helper.succeed();
    }

    /** A Broken piece protects nothing and the walk skips it, so it earns nothing; the rest still do. */
    @GameTest(template = "empty")
    public static void aBrokenPieceEarnsNothing(GameTestHelper helper) {
        Player player = suitedUp(helper, "iron");
        player.getItemBySlot(EquipmentSlot.FEET).set(ForgeweaveDataComponents.BROKEN.get(), true);

        lost(player, helper.getLevel().damageSources().inFire(), BLOW);
        helper.assertTrue(xp(player, EquipmentSlot.FEET) == 0,
                "a Broken piece earns nothing, got " + xp(player, EquipmentSlot.FEET));
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS}) {
            helper.assertTrue(xp(player, slot) >= 1, slot + " is unbroken and must still earn");
        }
        helper.succeed();
    }

    /** Nothing worn: no piece is reached, and a piece merely carried is not worn. */
    @GameTest(template = "empty")
    public static void anUnarmoredHitEarnsNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack carried = piece(helper, player, ToolConstants.CHESTPLATE, "iron");

        float lost = lost(player, helper.getLevel().damageSources().inFire(), BLOW);
        helper.assertTrue(Math.abs(lost - BLOW) < 0.001F, "nothing worn: the blow lands whole, lost " + lost);
        helper.assertTrue(ToolLevel.of(carried).equals(ToolLevel.NONE), "and a carried piece earns nothing");
        helper.succeed();
    }

    /**
     * The level's whole payoff: one more modifier slot on that piece, spendable at the Armor
     * Station. Granted here through {@link ToolLeveling#addXp} -- the same call the defensive pass
     * makes, with the hundreds of hits a real level costs skipped.
     */
    @GameTest(template = "empty")
    public static void aLevelOpensASlotAtTheArmorStation(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack chest = apply(helper, player, piece(helper, player, ToolConstants.CHESTPLATE, "iron"),
                new ItemStack(ForgeweaveItems.SEARED_BRICK.get(), 15));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(chest) == 0, "fire protection III fills the three slots");
        helper.assertTrue(load(helper, player, chest.copy(), new ItemStack(ForgeweaveItems.SEARED_BRICK.get(), 5))
                .getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(), "so a fourth level has nowhere to go");

        helper.assertTrue(ToolLeveling.addXp(chest, ToolLeveling.baseXp(chest), null), "one base-XP grant is one level");
        helper.assertTrue(ToolLevel.of(chest).level() == 1 && ToolLevel.of(chest).bonusSlots() == 1,
                "level 1 grants exactly one slot, got " + ToolLevel.of(chest));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(chest) == 1, "which shows up as one free slot");

        ItemStack four = apply(helper, player, chest, new ItemStack(ForgeweaveItems.SEARED_BRICK.get(), 5));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(four) == 0,
                "and the earned slot takes fire protection IV, free " + ForgeweaveModifiers.freeSlots(four));
        helper.assertTrue(ToolLevel.of(four).bonusSlots() == 1, "the level survives the station round trip");
        helper.succeed();
    }

    /** D-M7-3: with {@code toolLeveling} off the mechanic is inert, armor included. */
    @GameTest(template = "empty")
    public static void nothingAccruesWithTheConfigOff(GameTestHelper helper) {
        ForgeweaveConfig.TOOL_LEVELING.set(false);
        try {
            Player player = suitedUp(helper, "iron");
            lost(player, helper.getLevel().damageSources().inFire(), BLOW);
            for (EquipmentSlot slot : SLOTS) {
                helper.assertTrue(ToolLevel.of(player.getItemBySlot(slot)).equals(ToolLevel.NONE),
                        slot + " must be untouched with leveling off, got " + ToolLevel.of(player.getItemBySlot(slot)));
            }
        } finally {
            ForgeweaveConfig.TOOL_LEVELING.set(true);
        }
        helper.succeed();
    }
}
