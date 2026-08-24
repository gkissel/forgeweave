package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.ThornsCounterSeam;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * M4-6 (issue #681; SCOPE.md D15/D16): the seven armor modifiers through the real Tool Station
 * flow -- what a reagent stack buys, what it costs in slots, and the {@code armorOnly()} gate in
 * both directions. The pure arithmetic is {@code modifier.ArmorModifiersTest}.
 *
 * <p>Clone pinned at {@code de26560d}: {@code recipes/tools/modifiers/defense/*.json},
 * {@code .../upgrade/thorns.json}, {@code tools/data/ModifierProvider.java}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArmorModifierGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private static ItemStack chestplate(GameTestHelper helper, Player player) {
        return ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of("iron", "iron"));
    }

    /** The station loaded with {@code tool} and one reagent stack, output untaken. */
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

    private static void assertRefused(GameTestHelper helper, ToolStationMenu menu, String why) {
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(), why);
        helper.assertTrue(menu.rejection() != null, "and the station must say why");
    }

    /** Five of a protection's reagent buy level 1 and one slot; the sixth starts a second slot. */
    private static void assertProtection(GameTestHelper helper, String name, ItemStack reagent) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = chestplate(helper, player);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(piece) == ForgeweaveModifiers.DEFAULT_SLOTS,
                "a fresh piece has the default three slots");
        ItemStack one = apply(helper, player, piece, reagent.copyWithCount(5));
        ModifierEntry entry = ForgeweaveModifiers.entry(one, id(name));
        helper.assertTrue(entry != null && entry.level() == 5, name + ": five reagents are 5 units (level 1), got " + entry);
        helper.assertTrue(ForgeweaveModifiers.displayLevel(id(name), 5) == 1, name + " shows level I");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(one) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                name + ": level 1 costs one slot, free " + ForgeweaveModifiers.freeSlots(one));
        ItemStack two = apply(helper, player, one, reagent.copyWithCount(1));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(two) == ForgeweaveModifiers.DEFAULT_SLOTS - 2,
                name + ": the sixth unit opens level 2 and charges a second slot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fiveSearedBricksBuyFireProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "fire_protection", new ItemStack(ForgeweaveItems.SEARED_BRICK.get()));
    }

    @GameTest(template = "empty")
    public static void fiveCryingObsidianBuyBlastProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "blast_protection", new ItemStack(Items.CRYING_OBSIDIAN));
    }

    @GameTest(template = "empty")
    public static void fiveGoldIngotsBuyMagicProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "magic_protection", new ItemStack(Items.GOLD_INGOT));
    }

    @GameTest(template = "empty")
    public static void fiveCobaltIngotsBuyMeleeProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "melee_protection", new ItemStack(ForgeweaveItems.INGOT_COBALT.get()));
    }

    @GameTest(template = "empty")
    public static void fiveIronIngotsBuyProjectileProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "projectile_protection", new ItemStack(Items.IRON_INGOT));
    }

    /**
     * {@code defense/knockback_resistance.json}: one anvil, one level, and the worn piece adds
     * {@code +0.1} to the wearer's knockback resistance (clone {@code StatBoostModule} 0.1 per level
     * on {@code ToolStats.KNOCKBACK_RESISTANCE}, read into the attribute by the armor item).
     */
    @GameTest(template = "empty")
    public static void anAnvilBuysKnockbackResistanceWornAsAnAttribute(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = apply(helper, player, chestplate(helper, player), new ItemStack(Items.ANVIL));
        ModifierEntry entry = ForgeweaveModifiers.entry(piece, id("knockback_resistance"));
        helper.assertTrue(entry != null && entry.level() == 1, "an anvil records level 1, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(piece) == ForgeweaveModifiers.DEFAULT_SLOTS - 1, "and takes one slot");
        assertRefused(helper, load(helper, player, piece, new ItemStack(Items.ANVIL)), "a second anvil is past the one-level cap");

        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        double resistance = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(Math.abs(resistance - 0.1) < 1e-6, "worn, the piece grants 0.1 knockback resistance, got " + resistance);
        helper.succeed();
    }

    /**
     * {@code upgrade/thorns.json} (25 cactus per level, 3 levels) and {@code ThornsModule}: the worn
     * piece resolves to a {@link ThornsCounterSeam} at 15% per level, and a guaranteed roll deals
     * 1-4 thorns damage to a direct attacker while costing the piece one durability.
     */
    @GameTest(template = "empty")
    public static void cactusBuysThornsThatCounterADirectBlow(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = apply(helper, player, chestplate(helper, player), new ItemStack(Items.CACTUS, 25));
        ModifierEntry entry = ForgeweaveModifiers.entry(piece, id("thorns"));
        helper.assertTrue(entry != null && entry.level() == 25, "25 cactus are level 1, got " + entry);
        List<CombatSeam> seams = CombatSeams.seams(piece);
        helper.assertTrue(seams.stream().anyMatch(seam -> seam instanceof ThornsCounterSeam thorns
                && Math.abs(thorns.chance() - 0.15F) < 1e-6F), "thorns I resolves to a 15% counter seam, got " + seams);

        player.setItemSlot(EquipmentSlot.CHEST, piece);
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
        float before = zombie.getHealth();
        CombatDefense defense = new CombatDefense(helper.getLevel(), worn, player, zombie,
                helper.getLevel().damageSources().mobAttack(zombie), false, false);
        float damage = new ThornsCounterSeam(1.0F, 1.0F, 3.0F).incomingHit(defense, 4.0F, 4.0F);
        helper.assertTrue(damage == 4.0F, "thorns never changes the incoming blow");
        float dealt = before - zombie.getHealth();
        helper.assertTrue(dealt >= 1.0F && dealt <= 4.0F, "a guaranteed roll deals 1 to 4 back, dealt " + dealt);
        helper.assertTrue(worn.getDamageValue() == 1, "the counter costs the piece one durability, got " + worn.getDamageValue());

        // An indirect blow (an arrow) never triggers it.
        CombatDefense arrow = new CombatDefense(helper.getLevel(), worn, player, zombie,
                helper.getLevel().damageSources().arrow(null, zombie), false, false);
        float health = zombie.getHealth();
        new ThornsCounterSeam(1.0F, 1.0F, 3.0F).incomingHit(arrow, 4.0F, 4.0F);
        helper.assertTrue(zombie.getHealth() == health, "an indirect blow is not countered");
        helper.succeed();
    }

    /** D15's {@code armorOnly()}: a seared brick on a pickaxe is refused with the category message. */
    @GameTest(template = "empty")
    public static void aPickaxeRefusesAnArmorOnlyModifier(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");
        assertRefused(helper, load(helper, player, pickaxe, new ItemStack(ForgeweaveItems.SEARED_BRICK.get(), 5)),
                "fire protection is armor-only, so a pickaxe must produce nothing");
        assertRefused(helper, load(helper, player, pickaxe, new ItemStack(Items.ANVIL)),
                "knockback resistance is armor-only too");
        helper.succeed();
    }

    /** The other direction: blasting's {@code harvestOnly()} refuses a chestplate; a generic modifier does not. */
    @GameTest(template = "empty")
    public static void aChestplateRefusesAHarvestOnlyModifierButTakesAGenericOne(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = chestplate(helper, player);
        assertRefused(helper, load(helper, player, piece, new ItemStack(Items.TNT, 3)),
                "blasting is harvest-only, so a chestplate must produce nothing");
        assertRefused(helper, load(helper, player, piece, new ItemStack(Items.COD, 2)),
                "fins is projectile-only");
        ItemStack reinforced = apply(helper, player, piece, new ItemStack(ForgeweaveItems.REINFORCED_PLATE.get()));
        helper.assertTrue(ForgeweaveModifiers.entry(reinforced, id("reinforced")) != null, "reinforced applies to armor");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(reinforced) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "at the same one-slot-per-level charge as on a tool");
        helper.succeed();
    }
}
