package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
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
import dev.gkissel.forgeweave.block.SlimeColour;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.combat.CombatDefense;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.combat.DefendedBlow;
import dev.gkissel.forgeweave.combat.Protection;
import dev.gkissel.forgeweave.combat.ThornsCounterSeam;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

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
        return chestplate(helper, player, "iron");
    }

    private static ItemStack chestplate(GameTestHelper helper, Player player, String material) {
        return ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of(material, material));
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

    /** Issue #780: the station loaded with {@code tool} and two reagent stacks in two free slots. */
    private static ToolStationMenu load(GameTestHelper helper, Player player, ItemStack tool,
            ItemStack first, ItemStack second) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, ItemStack.EMPTY);
        }
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, first);
        blockEntity.container().setItem(2, second);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    /** Issue #780: two-reagent counterpart to {@link #apply(GameTestHelper, Player, ItemStack, ItemStack)}. */
    private static ItemStack apply(GameTestHelper helper, Player player, ItemStack tool, ItemStack first, ItemStack second) {
        ToolStationMenu menu = load(helper, player, tool, first, second);
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "the station must apply " + first + " + " + second
                + (menu.rejection() != null ? " (" + menu.rejection().message().getString() + ")" : ""));
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    private static void assertRefused(GameTestHelper helper, ToolStationMenu menu, String why) {
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(), why);
        helper.assertTrue(menu.rejection() != null, "and the station must say why");
    }

    /** Five of a protection's reagent buy level 1 and one slot; the sixth starts a second slot. */
    private static void assertProtection(GameTestHelper helper, String name, float perLevel, ItemStack reagent) {
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
        // #680's shared Protection seam, at this modifier's effective level.
        helper.assertTrue(CombatSeams.seams(one).stream().anyMatch(seam -> seam instanceof Protection protection
                && Math.abs(protection.value() - perLevel) < 1e-6F), name + " I resolves to " + perLevel + " protection, got " + CombatSeams.seams(one));
        ItemStack two = apply(helper, player, one, reagent.copyWithCount(1));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(two) == ForgeweaveModifiers.DEFAULT_SLOTS - 2,
                name + ": the sixth unit opens level 2 and charges a second slot");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fiveSearedBricksBuyFireProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "fire_protection", 2.5F, new ItemStack(ForgeweaveItems.SEARED_BRICK.get()));
    }

    @GameTest(template = "empty")
    public static void fiveCryingObsidianBuyBlastProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "blast_protection", 2.5F, new ItemStack(Items.CRYING_OBSIDIAN));
    }

    @GameTest(template = "empty")
    public static void fiveGoldIngotsBuyMagicProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "magic_protection", 2.5F, new ItemStack(Items.GOLD_INGOT));
    }

    @GameTest(template = "empty")
    public static void fiveCobaltIngotsBuyMeleeProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "melee_protection", 2.0F, new ItemStack(ForgeweaveItems.INGOT_COBALT.get()));
    }

    @GameTest(template = "empty")
    public static void fiveIronIngotsBuyProjectileProtectionOne(GameTestHelper helper) {
        assertProtection(helper, "projectile_protection", 2.0F, new ItemStack(Items.IRON_INGOT));
    }

    /**
     * {@code defense/knockback_resistance.json}: one anvil, one level, and the worn piece adds
     * {@code +0.1} to the wearer's knockback resistance (clone {@code StatBoostModule} 0.1 per level
     * on {@code ToolStats.KNOCKBACK_RESISTANCE}, read into the attribute by the armor item).
     */
    @GameTest(template = "empty")
    public static void anAnvilBuysKnockbackResistanceWornAsAnAttribute(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack plain = chestplate(helper, player);
        player.setItemSlot(EquipmentSlot.CHEST, plain);
        player.tick();
        double base = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE); // the plating's own (#680: iron's trait adds some)
        ItemStack piece = apply(helper, player, plain, new ItemStack(Items.ANVIL));
        ModifierEntry entry = ForgeweaveModifiers.entry(piece, id("knockback_resistance"));
        helper.assertTrue(entry != null && entry.level() == 1, "an anvil records level 1, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(piece) == ForgeweaveModifiers.DEFAULT_SLOTS - 1, "and takes one slot");
        assertRefused(helper, load(helper, player, piece, new ItemStack(Items.ANVIL)), "a second anvil is past the one-level cap");

        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        double resistance = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(Math.abs(resistance - base - 0.1) < 1e-6, "worn, the modifier adds 0.1 knockback resistance over " + base + ", got " + resistance);
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
        // #767: a vanilla zombie's own base armor (2.0) shaves a hair off a minimum 1.0 thorns
        // roll via CombatRules#getDamageAfterAbsorb, flaking the >= 1.0F assertion below. Strip it
        // so the target takes the seam's damage unmitigated -- thorns behaviour is unchanged.
        zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0);
        float before = zombie.getHealth();
        CombatDefense defense = new CombatDefense(helper.getLevel(), worn, player, zombie,
                helper.getLevel().damageSources().mobAttack(zombie), false, false);
        DefendedBlow blow = new DefendedBlow(4.0F);
        new ThornsCounterSeam(1.0F, 1.0F, 3.0F).onDefend(defense, blow);
        helper.assertTrue(blow.damage() == 4.0F && blow.protection() == 0.0F, "thorns never changes the incoming blow");
        float dealt = before - zombie.getHealth();
        helper.assertTrue(dealt >= 1.0F && dealt <= 4.0F, "a guaranteed roll deals 1 to 4 back, dealt " + dealt);
        helper.assertTrue(worn.getDamageValue() == 1, "the counter costs the piece one durability, got " + worn.getDamageValue());

        // An indirect blow (an arrow) never triggers it.
        CombatDefense arrow = new CombatDefense(helper.getLevel(), worn, player, zombie,
                helper.getLevel().damageSources().arrow(null, zombie), false, false);
        float health = zombie.getHealth();
        new ThornsCounterSeam(1.0F, 1.0F, 3.0F).onDefend(arrow, new DefendedBlow(4.0F));
        helper.assertTrue(zombie.getHealth() == health, "an indirect blow is not countered");
        helper.succeed();
    }

    /** Vanilla {@code CombatRules#getDamageAfterAbsorb}: 10 damage against the iron chestplate's 5 armor, 0 toughness. */
    private static final float BLOW = 10.0F;
    private static final float ABSORBED_BLOW = BLOW * (1.0F - Math.max(5.0F - BLOW / 2.0F, 5.0F * 0.2F) / 25.0F);

    /**
     * The blow-level effect through #680's real armor pass and damage-type tags: fire protection
     * III (15 seared bricks, all three slots) is 7.5 protection, i.e. the post-armor fire damage
     * times {@code 1 - 7.5 / 25} (clone {@code ArmorUtil#getDamageForEvent}).
     */
    @GameTest(template = "empty")
    public static void fireProtectionAttenuatesFireDamageAfterArmor(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = apply(helper, player, chestplate(helper, player), new ItemStack(ForgeweaveItems.SEARED_BRICK.get(), 15));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(piece) == 0, "three levels fill the three slots");
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        float expected = ABSORBED_BLOW * (1.0F - 7.5F / 25.0F);
        float unrelated = ABSORBED_BLOW;

        float before = player.getHealth();
        player.hurt(helper.getLevel().damageSources().inFire(), BLOW);
        float lost = before - player.getHealth();
        helper.assertTrue(Math.abs(lost - expected) < 0.01F, "fire III must cut a " + BLOW + " fire blow to " + expected + ", lost " + lost);

        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        before = player.getHealth();
        player.hurt(helper.getLevel().damageSources().explosion(null, null), BLOW);
        lost = before - player.getHealth();
        helper.assertTrue(Math.abs(lost - unrelated) < 0.01F, "and leave an explosion at the plain armor value " + unrelated + ", lost " + lost);
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

    // ---------------------------------------------------------------- #736: netherite, slotless

    /** The chestplate with its three slots full (fire protection III). */
    private static ItemStack fullChestplate(GameTestHelper helper, Player player) {
        ItemStack piece = apply(helper, player, chestplate(helper, player), new ItemStack(ForgeweaveItems.SEARED_BRICK.get(), 15));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(piece) == 0, "three levels fill the three slots");
        return piece;
    }

    /**
     * Issue #736/#780: the clone's {@code upgrade/netherite.json} (netherite upgrade smithing
     * template <em>and</em> a netherite ingot, max 1) applied <em>slotless</em> (maintainer decision;
     * the clone charges one upgrade slot) -- it lands on a piece with no free slot, grows the pool by
     * 20% of the plating's base, and the worn piece adds {@code +1} toughness and {@code +0.05}
     * knockback resistance. Issue #780 restored the ingot half of upstream's combo that PR #744 had
     * dropped; issue #776's specificity rule is what lets the ingot stay shared with
     * {@code modifier_recipe/extra_slot_netherite.json} (issue #107/#135) without an unreachable
     * recipe (see {@code NetheriteModifierTest#theShippedRecipe}).
     */
    @GameTest(template = "empty")
    public static void aTemplateAndIngotApplyNetheriteOnAFullChestplate(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack full = fullChestplate(helper, player);
        int baseDurability = full.getMaxDamage();
        player.setItemSlot(EquipmentSlot.CHEST, full);
        player.tick();
        double toughness = player.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        double knockback = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);

        ItemStack piece = apply(helper, player, full,
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.NETHERITE_INGOT));
        ModifierEntry entry = ForgeweaveModifiers.entry(piece, id("netherite"));
        helper.assertTrue(entry != null && entry.level() == 1, "a template and an ingot record level 1, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(piece) == 0, "and occupies no slot");
        helper.assertTrue(piece.getMaxDamage() == baseDurability + baseDurability / 5,
                "+20% of the base durability, got " + piece.getMaxDamage() + " over " + baseDurability);
        helper.assertTrue(piece.has(DataComponents.FIRE_RESISTANT), "the dropped piece survives fire");

        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ARMOR_TOUGHNESS) - toughness - 1.0) < 1e-6,
                "worn, +1 toughness over " + toughness + ", got " + player.getAttributeValue(Attributes.ARMOR_TOUGHNESS));
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) - knockback - 0.05) < 1e-6,
                "worn, +0.05 knockback resistance over " + knockback);
        helper.succeed();
    }

    /**
     * Issue #780's specificity test: a lone netherite ingot, with no template, must still fall
     * through to {@code extra_slot} exactly as before this ticket -- the two recipes share the
     * ingot, and only the template's presence tips the match to netherite.
     */
    @GameTest(template = "empty")
    public static void aLoneNetheriteIngotStillGivesExtraSlot(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = apply(helper, player, chestplate(helper, player), new ItemStack(Items.NETHERITE_INGOT));
        helper.assertTrue(ForgeweaveModifiers.entry(piece, id("netherite")) == null, "no template means no netherite");
        ModifierEntry extraSlot = ForgeweaveModifiers.entry(piece, id("extra_slot"));
        helper.assertTrue(extraSlot != null && extraSlot.level() == 1,
                "a lone ingot must still buy extra_slot, got " + extraSlot);
        helper.succeed();
    }

    /**
     * Issue #780's specificity test, the other half: a lone template, with no ingot, satisfies no
     * recipe at all -- {@code extra_slot}'s own recipe is the bare ingot, not the template -- so the
     * station stays silent.
     */
    @GameTest(template = "empty")
    public static void aLoneNetheriteTemplateAppliesNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolStationMenu menu = load(helper, player, chestplate(helper, player),
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a lone template must not apply netherite");
        helper.assertTrue(menu.rejection() == null,
                "no recipe is satisfied at all by a lone template, so the station has nothing to say");
        helper.succeed();
    }

    /** The clone's {@code setMaxLevel(1)}: a second template-and-ingot pair is refused. */
    @GameTest(template = "empty")
    public static void aSecondNetheriteIngotIsRefusedPastTheCap(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = apply(helper, player, fullChestplate(helper, player),
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.NETHERITE_INGOT));
        assertRefused(helper, load(helper, player, piece,
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.NETHERITE_INGOT)),
                "netherite is max level 1");
        helper.succeed();
    }

    /** Slotless is per-modifier: the same full piece still refuses a slotted one (an anvil). */
    @GameTest(template = "empty")
    public static void aSlottedModifierIsStillRefusedOnTheFullPiece(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = apply(helper, player, fullChestplate(helper, player),
                new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE), new ItemStack(Items.NETHERITE_INGOT));
        assertRefused(helper, load(helper, player, piece, new ItemStack(Items.ANVIL)), "knockback resistance needs a slot the piece no longer has");
        helper.succeed();
    }

    /**
     * #728, the clone's overslime refill recipes: a slime ball restores its colour's amount (green
     * 20, blue 50), as many are spent as the missing overslime needs (overshoot wasted), a full
     * piece refuses, and a piece without the trait refuses.
     */
    @GameTest(template = "empty")
    public static void slimeBallsRefillOverslimeAtTheStation(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = chestplate(helper, player, "knightslime");
        ItemStack two = apply(helper, player, piece, new ItemStack(Items.SLIME_BALL, 2));
        helper.assertTrue(ForgeweaveTraits.overslime(two) == 40, "two green balls are 40, got " + ForgeweaveTraits.overslime(two));
        helper.assertTrue(ForgeweaveModifiers.of(two).isEmpty(), "overslime is no modifier entry, got " + ForgeweaveModifiers.of(two));
        ToolStationBlockEntity station = helper.getBlockEntity(STATION);
        helper.assertTrue(station.container().getItem(1).isEmpty(), "both balls spent");

        ItemStack blue = ForgeweaveItems.slimeBallItem(SlimeColour.BLUE).toStack(3);
        ItemStack full = apply(helper, player, two, blue);
        helper.assertTrue(ForgeweaveTraits.overslime(full) == 50, "capped at 50, got " + ForgeweaveTraits.overslime(full));
        helper.assertTrue(station.container().getItem(1).getCount() == 2, "one blue ball covers the missing 10");

        assertRefused(helper, load(helper, player, full, new ItemStack(Items.SLIME_BALL)), "a full piece takes no more");
        assertRefused(helper, load(helper, player, chestplate(helper, player), new ItemStack(Items.SLIME_BALL)),
                "a piece without the overslime trait takes none");
        helper.succeed();
    }

    /**
     * Issue #729: the clone's defense recipes list {@code #tconstruct:modifiable/held} next to
     * {@code modifiable/armor} for the five protections only -- a broadsword takes cobalt, and is
     * still refused an anvil (knockback resistance, armor tag alone) and cactus (thorns, likewise).
     */
    @GameTest(template = "empty")
    public static void aBroadswordTakesAProtectionButNotKnockbackResistanceOrThorns(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = ToolAssembly.assemble(helper, player, STATION, ToolAssembly.entryOf(ToolConstants.BROADSWORD),
                List.of("wood", "iron", "iron"));
        // Checked before the apply() below: the station's head slot holds `sword` by reference and
        // a successful application consumes it in place, so a refusal check reusing the same
        // ItemStack afterwards would see an already-emptied stack rather than the assembled sword.
        assertRefused(helper, load(helper, player, sword, new ItemStack(Items.ANVIL)),
                "knockback resistance stays armor-only");
        assertRefused(helper, load(helper, player, sword, new ItemStack(Items.CACTUS, 25)),
                "thorns stays armor-only");
        ItemStack guarded = apply(helper, player, sword, new ItemStack(ForgeweaveItems.INGOT_COBALT.get(), 5));
        ModifierEntry entry = ForgeweaveModifiers.entry(guarded, id("melee_protection"));
        helper.assertTrue(entry != null && entry.level() == 5, "melee protection I on a held sword, got " + entry);
        helper.succeed();
    }

    /** Issue #729, maintainer decision: held means melee here -- a shortbow is still refused a protection. */
    @GameTest(template = "empty")
    public static void aShortbowRefusesAProtection(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack bow = ToolAssembly.assemble(helper, player, STATION,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_SHORTBOW.get()), List.of("wood", "wood", "string"));
        helper.assertTrue(bow.is(ForgeweaveItems.TOOL_SHORTBOW.get()), "expected a shortbow, got " + bow);
        assertRefused(helper, load(helper, player, bow, new ItemStack(ForgeweaveItems.SEARED_BRICK.get(), 5)),
                "fire protection is armor-or-melee, so a bow must produce nothing");
        helper.succeed();
    }
}
