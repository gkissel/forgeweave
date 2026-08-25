package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ForgeweaveCreativeTab;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ArmorStats;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Issue #721: the M4 armor pipeline through the <em>real</em> path a dedicated server runs -- a
 * {@link ServerPlayer} (not a mock {@code Player}) wearing a piece the Tool Station menu assembled,
 * modified at that same station, bitten by a real zombie ({@code Mob#doHurtTarget}) and dropped
 * from a height ({@code LivingEntity#causeFallDamage}). The #678/#680/#681 tests cover each seam
 * in isolation; these cover the seams meeting.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ArmorRealPathGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    /** Where the wearer stands, off the station and a block up so it never falls out of the template. */
    private static final BlockPos STAND = new BlockPos(3, 2, 3);
    /** {@code ServerPlayer#spawnInvulnerableTime}'s initial value, private there. */
    private static final int SPAWN_INVULNERABLE_TICKS = 60;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private static ItemStack assemble(GameTestHelper helper, Player player, ToolConstants.Entry piece, String plating,
            String maille) {
        return ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(piece), List.of(plating, maille));
    }

    /** {@code reagent} applied to {@code tool} through the station menu, output taken. */
    private static ItemStack modify(GameTestHelper helper, Player player, ItemStack tool, ItemStack reagent) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, ItemStack.EMPTY);
        }
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, reagent);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "the station must apply " + reagent
                + (menu.rejection() != null ? " (" + menu.rejection().message().getString() + ")" : ""));
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    /**
     * A real survival {@link ServerPlayer} in the level, wearing {@code piece} in {@code slot} and
     * ticked past its spawn invulnerability so its attribute modifiers are on and a blow lands.
     */
    private static ServerPlayer wearing(GameTestHelper helper, EquipmentSlot slot, ItemStack piece) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(STAND));
        player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        player.setGameMode(GameType.SURVIVAL);
        player.setItemSlot(slot, piece);
        // What a tick on a dedicated server does to a player, from both ends: the level's entity
        // tick (ServerPlayer#tick, which counts the 60 ticks of spawn invulnerability down) and the
        // connection's (ServerPlayer#doTick, the LivingEntity tick that applies the worn piece's
        // attribute modifiers -- LivingEntity#detectEquipmentUpdates). A mock server player's
        // connection is never ticked by the server, so the second half is driven by hand.
        for (int i = 0; i <= SPAWN_INVULNERABLE_TICKS; i++) {
            player.tick();
            player.doTick();
        }
        player.invulnerableTime = 0;
        return player;
    }

    private static float bitten(GameTestHelper helper, ServerPlayer player, Zombie zombie) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        float before = player.getHealth();
        helper.assertTrue(zombie.doHurtTarget(player), "the zombie must land its bite");
        return before - player.getHealth();
    }

    private static Zombie zombie(GameTestHelper helper) {
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 4));
        zombie.setNoAi(true);
        return zombie;
    }

    /** Diamond at the station grows an assembled piece's durability pool, as it grows a tool's (#106). */
    @GameTest(template = "empty")
    public static void diamondAtTheStationRaisesAnAssembledPieceDurability(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack piece = assemble(helper, player, ToolConstants.CHESTPLATE, "iron", "iron");
        int base = piece.getMaxDamage();
        ArmorStats stats = ArmorPieceItem.stats(piece);
        helper.assertTrue(stats != null && base == stats.durability(), "assembled max_damage is the plating's durability");
        ItemStack modified = modify(helper, player, piece, new ItemStack(Items.DIAMOND));
        helper.assertTrue(ForgeweaveModifiers.entry(modified, id("diamond")) != null, "diamond must be on the piece");
        helper.assertTrue(modified.getMaxDamage() > base,
                "diamond must raise max_damage above " + base + ", got " + modified.getMaxDamage());
        helper.assertTrue(modified.getDamageValue() == 0, "growing the pool costs no wear");
        helper.succeed();
    }

    /**
     * A real zombie bite on a real server player wearing a cobalt chestplate: the piece's armor is
     * on the player's attribute (what the vanilla HUD draws), the bite is cut by cobalt's ARMOR
     * trait (melee_protection 2: {@code 1 - 2/25}) on top of that, and the plating pays durability.
     */
    @GameTest(template = "empty")
    public static void realZombieBiteOnAServerPlayerRunsTheArmorPipeline(GameTestHelper helper) {
        ServerPlayer assembler = helper.makeMockServerPlayerInLevel();
        ItemStack piece = assemble(helper, assembler, ToolConstants.CHESTPLATE, "cobalt", "cobalt");
        ServerPlayer player = wearing(helper, EquipmentSlot.CHEST, piece);
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        ArmorStats stats = ArmorPieceItem.stats(worn);
        helper.assertTrue(stats != null, "the worn piece keeps its stats");
        helper.assertTrue(Math.abs(player.getAttributeValue(Attributes.ARMOR) - stats.armor()) < 1e-4,
                "the player's armor attribute must be the piece's " + stats.armor() + ", got "
                        + player.getAttributeValue(Attributes.ARMOR));
        helper.assertTrue(player.getArmorValue() == Math.round(stats.armor()),
                "the HUD's armor value must be the piece's, got " + player.getArmorValue());

        Zombie zombie = zombie(helper);
        float with = bitten(helper, player, zombie);
        helper.assertTrue(with > 0.0F, "the bite must hurt");
        helper.assertTrue(worn.getDamageValue() > 0, "the bite must wear the plating, damage " + worn.getDamageValue());

        List<ResourceLocation> traits = worn.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(traits != null && traits.contains(id("melee_protection")),
                "cobalt plating must carry melee_protection at real assembly, got " + traits);
        worn.remove(ForgeweaveDataComponents.TRAITS.get());
        float without = bitten(helper, player, zombie);
        worn.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        float expected = without * (1.0F - 2.0F / 25.0F);
        helper.assertTrue(Math.abs(with - expected) < 0.01F,
                "melee_protection must cut the bite to " + expected + " (from " + without + "), lost " + with);
        helper.succeed();
    }

    /**
     * Reinforced rolls its negation on armor as it does on a tool ({@code ToolItem#damageKeepingItem}):
     * at level 5's 100% a bitten piece never wears. Set directly rather than at the station -- five
     * levels cost five slots, more than a fresh piece has -- so this pins the damage seam only.
     */
    @GameTest(template = "empty")
    public static void reinforcedFiveKeepsARealBiteOffThePlating(GameTestHelper helper) {
        ServerPlayer assembler = helper.makeMockServerPlayerInLevel();
        ItemStack piece = assemble(helper, assembler, ToolConstants.CHESTPLATE, "iron", "iron");
        piece.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(id("reinforced"), 5)));
        ServerPlayer player = wearing(helper, EquipmentSlot.CHEST, piece);
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);
        Zombie zombie = zombie(helper);
        for (int i = 0; i < 8; i++) {
            bitten(helper, player, zombie);
        }
        helper.assertTrue(worn.getDamageValue() == 0, "reinforced V must negate every bite, wear " + worn.getDamageValue());
        helper.succeed();
    }

    /** Cactus maille's thorns bite the zombie back on a real server player. */
    @GameTest(template = "empty")
    public static void cactusThornsPrickARealZombie(GameTestHelper helper) {
        ServerPlayer assembler = helper.makeMockServerPlayerInLevel();
        ItemStack piece = assemble(helper, assembler, ToolConstants.LEGGINGS, "iron", "cactus");
        ServerPlayer player = wearing(helper, EquipmentSlot.LEGS, piece);
        Zombie zombie = zombie(helper);
        float before = zombie.getHealth();
        // The clone's thorns is a per-hit chance; enough bites make one prick certain.
        for (int i = 0; i < 64 && zombie.getHealth() >= before; i++) {
            zombie.invulnerableTime = 0;
            bitten(helper, player, zombie);
        }
        helper.assertTrue(zombie.getHealth() < before, "thorns must have hurt the zombie by now");
        helper.succeed();
    }

    /** Skyfall boots on a real server player: +1 safe fall distance shows in the fall it survives untouched. */
    @GameTest(template = "empty")
    public static void skyfallBootsSoftenARealFall(GameTestHelper helper) {
        ServerPlayer assembler = helper.makeMockServerPlayerInLevel();
        ItemStack piece = assemble(helper, assembler, ToolConstants.BOOTS, "iron", "slimevine_blue");
        ServerPlayer player = wearing(helper, EquipmentSlot.FEET, piece);
        helper.assertTrue(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE) > 3.0,
                "skyfall must raise the safe fall distance, got " + player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE));
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        player.causeFallDamage(4.0F, 1.0F, player.damageSources().fall());
        helper.assertTrue(player.getHealth() == player.getMaxHealth(),
                "a four-block fall is within skyfall's safe distance, lost " + (player.getMaxHealth() - player.getHealth()));
        player.invulnerableTime = 0;
        player.causeFallDamage(6.0F, 1.0F, player.damageSources().fall());
        helper.assertTrue(player.getHealth() < player.getMaxHealth(), "a six-block fall still hurts");
        helper.succeed();
    }

    /** The creative tab's chestplate is a real piece -- same stats, traits and durability as an assembled iron one. */
    @GameTest(template = "empty")
    public static void creativeTabPieceMatchesAnAssembledIronOne(GameTestHelper helper) {
        CreativeModeTab.ItemDisplayParameters parameters =
                new CreativeModeTab.ItemDisplayParameters(FeatureFlags.VANILLA_SET, true, helper.getLevel().registryAccess());
        List<ItemStack> displayed = new ArrayList<>();
        ForgeweaveCreativeTab.addToolItems(parameters, (stack, visibility) -> displayed.add(stack));
        ItemStack fromTab = displayed.stream().filter(stack -> stack.is(ForgeweaveItems.ARMOR_CHESTPLATE.get())).findFirst()
                .orElse(ItemStack.EMPTY);
        helper.assertFalse(fromTab.isEmpty(), "the tab lists the chestplate");
        ItemStack assembled = assemble(helper, helper.makeMockServerPlayerInLevel(), ToolConstants.CHESTPLATE, "iron", "iron");
        helper.assertTrue(ItemStack.isSameItemSameComponents(fromTab, assembled),
                "the tab's chestplate must be the assembled iron one: " + fromTab.getComponents() + " vs " + assembled.getComponents());
        helper.succeed();
    }
}
