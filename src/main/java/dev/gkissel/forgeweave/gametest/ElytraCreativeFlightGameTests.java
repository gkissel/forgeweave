package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.CreativeFlightHandler;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Issue #737 (epic #730 slice 2), blocked by #735: elytra flight (an elytra teaches the worn
 * chestplate to glide, via the item hooks in {@code ArmorPieceItem}) and creative flight (issue
 * #776's end-crystal-and-nether-star combo grants creative-style flight while a full set of armor is
 * worn, gated behind elytra flight already being on the same chestplate -- {@code
 * CreativeFlightHandler}'s per-tick grant/revoke). Both are {@code Modifier#armorOnly}/{@code
 * #chestplateOnly}: refused on every non-chestplate slot and on every tool. Issue #1005 dropped the
 * heavy-only half of {@code #chestplateOnly} (formerly {@code heavyChestplateOnly}), so the plate
 * chestplate (#678's plain piece) now works exactly like the heavy one, and
 * {@code CreativeFlightHandler}'s full-set check no longer cares about weight either.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ElytraCreativeFlightGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    // Issue #1006 (retiring #782's Armor Station): the heavy set needs a Tool Forge.
    private static ItemStack heavyPiece(GameTestHelper helper, Player player, ToolConstants.Entry entry) {
        return ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_FORGE.get(),
                ToolAssembly.entryOf(entry), List.of("iron", "iron", "iron"));
    }

    private static ItemStack heavyChestplate(GameTestHelper helper, Player player) {
        return heavyPiece(helper, player, ToolConstants.HEAVY_CHESTPLATE);
    }

    private static ItemStack plateChestplate(GameTestHelper helper, Player player) {
        return lightPiece(helper, player, ToolConstants.CHESTPLATE);
    }

    /** Issue #1005: the light counterpart of {@link #heavyPiece}, two parts instead of three. */
    private static ItemStack lightPiece(GameTestHelper helper, Player player, ToolConstants.Entry entry) {
        return ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(entry), List.of("iron", "iron"));
    }

    /** The station loaded with {@code tool} and one reagent stack, output untaken. */
    private static ToolStationMenu load(GameTestHelper helper, Player player, ItemStack tool, ItemStack reagent) {
        return load(helper, player, tool, reagent, ItemStack.EMPTY);
    }

    /**
     * The station loaded with {@code tool} and two reagent stacks in two different free slots (issue
     * #776: creative flight's end-crystal-and-nether-star combo needs a slot each), output untaken.
     */
    private static ToolStationMenu load(GameTestHelper helper, Player player, ItemStack tool,
            ItemStack firstReagent, ItemStack secondReagent) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        for (int i = 0; i < ToolStationMenu.INPUT_SLOTS; i++) {
            blockEntity.container().setItem(i, ItemStack.EMPTY);
        }
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, firstReagent);
        blockEntity.container().setItem(2, secondReagent);
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    private static ItemStack apply(GameTestHelper helper, Player player, ItemStack tool, ItemStack reagent) {
        return apply(helper, player, tool, reagent, ItemStack.EMPTY);
    }

    private static ItemStack apply(GameTestHelper helper, Player player, ItemStack tool,
            ItemStack firstReagent, ItemStack secondReagent) {
        ToolStationMenu menu = load(helper, player, tool, firstReagent, secondReagent);
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "the station must apply " + firstReagent + " + " + secondReagent
                + (menu.rejection() != null ? " (" + menu.rejection().message().getString() + ")" : ""));
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    private static void assertRefused(GameTestHelper helper, ToolStationMenu menu, String why) {
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(), why);
        helper.assertTrue(menu.rejection() != null, "and the station must say why");
    }

    // ---------------------------------------------------------------- elytra flight

    /** An elytra grants gliding through {@code canElytraFly}; a plain (unmodified) piece never does. */
    @GameTest(template = "empty")
    public static void anElytraGrantsGlidingToTheHeavyChestplate(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack plain = heavyChestplate(helper, player);
        helper.assertTrue(!plain.canElytraFly(player), "an unmodified heavy chestplate cannot glide");

        ItemStack flying = apply(helper, player, plain, new ItemStack(Items.ELYTRA));
        ModifierEntry entry = ForgeweaveModifiers.entry(flying, id("elytra_flight"));
        helper.assertTrue(entry != null && entry.level() == 1, "an elytra records level 1, got " + entry);
        helper.assertTrue(flying.canElytraFly(player), "elytra flight grants gliding");
        helper.assertTrue(flying.elytraFlightTick(player, 19), "flight continues past a non-multiple-of-10 tick");
        helper.succeed();
    }

    /** A Broken piece never glides, even carrying the modifier -- revoked the moment it clamps. */
    @GameTest(template = "empty")
    public static void elytraFlightIsRevokedOnceThePieceIsBroken(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack flying = apply(helper, player, heavyChestplate(helper, player), new ItemStack(Items.ELYTRA));
        helper.assertTrue(flying.canElytraFly(player), "sanity: flies before breaking");
        flying.set(ForgeweaveDataComponents.BROKEN.get(), true);
        helper.assertTrue(!flying.canElytraFly(player), "a Broken piece cannot glide even carrying the modifier");
        helper.succeed();
    }

    /** Issue #1005: {@code chestplateOnly()} now takes the plate chestplate (#678) too, but still refuses every tool. */
    @GameTest(template = "empty")
    public static void elytraFlightAppliesToThePlateChestplateTooButNotToTools(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack flying = apply(helper, player, plateChestplate(helper, player), new ItemStack(Items.ELYTRA));
        ModifierEntry entry = ForgeweaveModifiers.entry(flying, id("elytra_flight"));
        helper.assertTrue(entry != null && entry.level() == 1, "the plain plate chestplate takes elytra flight too, got " + entry);
        helper.assertTrue(flying.canElytraFly(player), "and it glides exactly like the heavy chestplate does");

        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");
        assertRefused(helper, load(helper, player, pickaxe, new ItemStack(Items.ELYTRA)),
                "elytra flight is still armor-only");
        helper.succeed();
    }

    // ---------------------------------------------------------------- creative flight: the station recipe (#776)

    /**
     * Issue #776 (maintainer decision, supersedes #751's recipeless state): an end crystal and a
     * nether star together apply creative flight on a heavy chestplate that already has elytra
     * flight (the proposed balance from #737/#749).
     */
    @GameTest(template = "empty")
    public static void endCrystalAndNetherStarGrantCreativeFlight(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack flying = apply(helper, player, heavyChestplate(helper, player), new ItemStack(Items.ELYTRA));

        ItemStack output = apply(helper, player, flying, new ItemStack(Items.END_CRYSTAL), new ItemStack(Items.NETHER_STAR));

        ModifierEntry entry = ForgeweaveModifiers.entry(output, id("creative_flight"));
        helper.assertTrue(entry != null && entry.level() == 1, "the combo grants creative flight level 1, got " + entry);
        helper.succeed();
    }

    /**
     * Issue #776's specificity test: a nether star ships as soulbound's reagent too, so on its own it
     * must still resolve to soulbound -- only the full end-crystal-and-nether-star combo is specific
     * enough to win creative flight.
     */
    @GameTest(template = "empty")
    public static void aLoneNetherStarStillGrantsSoulboundNotCreativeFlight(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack flying = apply(helper, player, heavyChestplate(helper, player), new ItemStack(Items.ELYTRA));

        ItemStack output = apply(helper, player, flying, new ItemStack(Items.NETHER_STAR));

        helper.assertTrue(ForgeweaveModifiers.entry(output, id("creative_flight")) == null,
                "a lone nether star must not apply creative flight -- it is not the specific combo");
        helper.assertTrue(ForgeweaveModifiers.entry(output, id("soulbound")) != null,
                "it applies soulbound instead, same as before the combo recipe existed");
        helper.succeed();
    }

    /** {@code requiresElytraFlightFirst()}: the combo is still refused without elytra flight already on the piece. */
    @GameTest(template = "empty")
    public static void creativeFlightComboStillRequiresElytraFlightFirst(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack plain = heavyChestplate(helper, player);

        assertRefused(helper,
                load(helper, player, plain, new ItemStack(Items.END_CRYSTAL), new ItemStack(Items.NETHER_STAR)),
                "the combo must not apply before elytra flight is on the same chestplate");
        helper.succeed();
    }

    /** Issue #1005: {@code chestplateOnly()} now takes the plate chestplate (#678) too, but still refuses every tool. */
    @GameTest(template = "empty")
    public static void creativeFlightComboAppliesToThePlateChestplateTooButNotToTools(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack flying = apply(helper, player, plateChestplate(helper, player), new ItemStack(Items.ELYTRA));
        ItemStack output = apply(helper, player, flying, new ItemStack(Items.END_CRYSTAL), new ItemStack(Items.NETHER_STAR));
        ModifierEntry entry = ForgeweaveModifiers.entry(output, id("creative_flight"));
        helper.assertTrue(entry != null && entry.level() == 1,
                "the plain plate chestplate takes the creative flight combo too, got " + entry);

        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");
        assertRefused(helper,
                load(helper, player, pickaxe, new ItemStack(Items.END_CRYSTAL), new ItemStack(Items.NETHER_STAR)),
                "creative flight is still armor-only");
        helper.succeed();
    }

    // ---------------------------------------------------------------- creative flight: the worn grant/revoke

    /**
     * A chestplate carrying both modifiers, ready to wear. Applied directly to the data component
     * (rather than through the two-station-trip combo recipe the tests above exercise) to keep these
     * {@code CreativeFlightHandler} tests -- about the worn-set grant/revoke, not the recipe -- short,
     * the same pattern every other GameTest needing a modifier with no recipe convenient to hand uses
     * (e.g. {@code BeheadingGameTests}, {@code ArmorRealPathGameTests}).
     */
    private static ItemStack flightChestplate(GameTestHelper helper, Player player) {
        return flightChestplate(helper, player, heavyChestplate(helper, player));
    }

    /** Issue #1005: same recipe as {@link #flightChestplate(GameTestHelper, Player)}, any starting piece. */
    private static ItemStack flightChestplate(GameTestHelper helper, Player player, ItemStack plainChestplate) {
        ItemStack flying = apply(helper, player, plainChestplate, new ItemStack(Items.ELYTRA));
        ItemStack both = flying.copy();
        List<ModifierEntry> modifiers = new ArrayList<>(
                both.getOrDefault(ForgeweaveDataComponents.MODIFIERS.get(), List.of()));
        modifiers.add(new ModifierEntry(id("creative_flight"), 1));
        both.set(ForgeweaveDataComponents.MODIFIERS.get(), List.copyOf(modifiers));
        return both;
    }

    private static void tick(Player player) {
        CreativeFlightHandler.onPlayerTickPost(new PlayerTickEvent.Post(player));
    }

    /** The full set requirement: three of four heavy pieces (missing boots) never grants flight. */
    @GameTest(template = "empty")
    public static void creativeFlightNeedsTheFullHeavySetWorn(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, heavyPiece(helper, player, ToolConstants.HEAVY_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, flightChestplate(helper, player));
        player.setItemSlot(EquipmentSlot.LEGS, heavyPiece(helper, player, ToolConstants.HEAVY_LEGGINGS));
        tick(player);
        helper.assertTrue(!CreativeFlightHandler.isGranted(player), "missing the boots must refuse flight");
        helper.assertTrue(!player.getAbilities().mayfly, "and never touch mayfly");

        player.setItemSlot(EquipmentSlot.FEET, heavyPiece(helper, player, ToolConstants.HEAVY_BOOTS));
        tick(player);
        helper.assertTrue(CreativeFlightHandler.isGranted(player), "the full set must grant flight");
        helper.assertTrue(player.getAbilities().mayfly, "and mayfly must follow");
        helper.succeed();
    }

    /**
     * Issue #1005: {@code wearsFullCreativeFlightSet} dropped its {@code isHeavy()} check, so a set
     * mixing light and heavy pieces -- not just an all-heavy or all-light one -- grants flight too.
     */
    @GameTest(template = "empty")
    public static void creativeFlightNeedsTheFullSetWornRegardlessOfWeight(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, lightPiece(helper, player, ToolConstants.HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, flightChestplate(helper, player, plateChestplate(helper, player)));
        player.setItemSlot(EquipmentSlot.LEGS, heavyPiece(helper, player, ToolConstants.HEAVY_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, lightPiece(helper, player, ToolConstants.BOOTS));
        tick(player);
        helper.assertTrue(CreativeFlightHandler.isGranted(player), "a mixed light/heavy full set must grant flight too");
        helper.assertTrue(player.getAbilities().mayfly, "and mayfly must follow");
        helper.succeed();
    }

    /** A chestplate without the modifier never grants flight, even under a full heavy set. */
    @GameTest(template = "empty")
    public static void aPlainHeavySetNeverGrantsCreativeFlight(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (ToolConstants.Entry entry : ToolConstants.HEAVY_ARMOR) {
            ItemStack stack = heavyPiece(helper, player, entry);
            player.setItemSlot(((ArmorPieceItem) stack.getItem()).getEquipmentSlot(), stack);
        }
        tick(player);
        helper.assertTrue(!CreativeFlightHandler.isGranted(player), "no piece carries creative flight");
        helper.succeed();
    }

    /** Revoked the tick after a piece is removed, and again the tick after a worn piece breaks. */
    @GameTest(template = "empty")
    public static void creativeFlightIsRevokedWhenAPieceIsRemovedOrBreaks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemSlot(EquipmentSlot.HEAD, heavyPiece(helper, player, ToolConstants.HEAVY_HELMET));
        player.setItemSlot(EquipmentSlot.CHEST, flightChestplate(helper, player));
        player.setItemSlot(EquipmentSlot.LEGS, heavyPiece(helper, player, ToolConstants.HEAVY_LEGGINGS));
        player.setItemSlot(EquipmentSlot.FEET, heavyPiece(helper, player, ToolConstants.HEAVY_BOOTS));
        tick(player);
        helper.assertTrue(CreativeFlightHandler.isGranted(player), "sanity: the full set grants flight");

        player.setItemSlot(EquipmentSlot.FEET, ItemStack.EMPTY);
        tick(player);
        helper.assertTrue(!CreativeFlightHandler.isGranted(player), "removing one piece must revoke flight");
        helper.assertTrue(!player.getAbilities().mayfly && !player.getAbilities().flying,
                "and turn mayfly/flying back off");

        ItemStack boots = heavyPiece(helper, player, ToolConstants.HEAVY_BOOTS);
        player.setItemSlot(EquipmentSlot.FEET, boots);
        tick(player);
        helper.assertTrue(CreativeFlightHandler.isGranted(player), "re-equipping must grant flight again");

        boots.set(ForgeweaveDataComponents.BROKEN.get(), true);
        tick(player);
        helper.assertTrue(!CreativeFlightHandler.isGranted(player), "a broken worn piece must revoke flight too");
        helper.succeed();
    }

    /** Never touches a creative or spectator player's own flight. */
    @GameTest(template = "empty")
    public static void creativeModePlayersAreNeverManagedByTheHandler(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.CREATIVE);
        player.getAbilities().mayfly = true;
        tick(player);
        helper.assertTrue(!CreativeFlightHandler.isGranted(player), "a creative player is never tracked");
        helper.assertTrue(player.getAbilities().mayfly, "and their own mayfly is left alone");
        helper.succeed();
    }
}
