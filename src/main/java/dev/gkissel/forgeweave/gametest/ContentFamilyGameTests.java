package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ContentFamilies;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.SmelteryMenu;
import dev.gkissel.forgeweave.menu.StationMenu;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.menu.ToolStationTabs;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The content-family toggles ticket: each {@code content} config family switched off and back on,
 * proving the ticket's "unobtainable, never unregistered" contract at the surfaces a player touches
 * -- the Tool Station and Tool Forge, the Part Builder, the casting table, the smeltery and modifier
 * application -- plus the half that must <em>not</em> change: a tool of a switched-off family that
 * already exists still hits, and its repair tab is still there.
 *
 * <p>Every test here is synchronous for the reason {@code ClayCastGameTests} and
 * {@code StencilTableGameTests} already are: these mutate a global config value, and GameTests in
 * one batch tick concurrently, so each set/assert/restore has to complete inside a single method for
 * no other test to ever observe the flipped value.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ContentFamilyGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /**
     * Melee weapons off: the Tool Station builds no broadsword and says why, and turning the family
     * back on restores it. The parts are loaded once and the toggle is the only thing that moves,
     * so nothing but the config can explain the difference.
     */
    @GameTest(template = "empty")
    public static void meleeWeaponsOffRefusesAssemblyAtTheStation(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ToolStationMenu menu = loadBroadswordParts(helper, player);

        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().is(ForgeweaveItems.TOOL_BROADSWORD.get()),
                "with meleeWeapons on, the station must assemble a broadsword");

        ForgeweaveConfig.MELEE_WEAPONS.set(false);
        try {
            menu.broadcastChanges();
            helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                    "with meleeWeapons off, the station must assemble nothing, got "
                            + menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem());

            StationMenu.Rejection rejection = menu.rejection();
            helper.assertTrue(rejection != null && !rejection.warning(),
                    "a refused family must be explained as an error, got " + rejection);
        } finally {
            ForgeweaveConfig.MELEE_WEAPONS.set(true);
        }

        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().is(ForgeweaveItems.TOOL_BROADSWORD.get()),
                "turning meleeWeapons back on must restore the assembly with no reload");
        helper.succeed();
    }

    /**
     * Harvest tools off, at the other block: the Tool Forge -- which assembles everything the Tool
     * Station does and the large tier besides (issue #152) -- still refuses a hammer. The forge is
     * the harder case of the two, since its own roster gate is the one that would otherwise let this
     * through.
     */
    @GameTest(template = "empty")
    public static void harvestToolsOffRefusesAssemblyAtTheForge(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(POS, ForgeweaveBlocks.TOOL_FORGE.get());
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(POS);
        ToolAssemblyRecipes.Entry hammer = ToolAssembly.entryFor(ForgeweaveItems.TOOL_HAMMER.get());
        for (int i = 0; i < hammer.slotCount(); i++) {
            blockEntity.container().setItem(i, ToolAssembly.part(hammer.part(i), "iron"));
        }
        ToolStationMenu menu = ToolAssembly.menu(helper, player, POS, blockEntity);

        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().is(ForgeweaveItems.TOOL_HAMMER.get()),
                "with harvestTools on, the Tool Forge must assemble a hammer");

        ForgeweaveConfig.HARVEST_TOOLS.set(false);
        try {
            menu.broadcastChanges();
            helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                    "with harvestTools off, even the Tool Forge must assemble nothing");
            helper.assertTrue(menu.rejection() != null, "and it must say why");
        } finally {
            ForgeweaveConfig.HARVEST_TOOLS.set(true);
        }
        helper.succeed();
    }

    /**
     * The sidebar follows the same gate, and composes with issue #348's forge gate rather than
     * replacing it: with melee weapons off, every remaining tab at both blocks builds a harvest
     * tool, the repair tab survives at both, and the Tool Station is still missing the forge tier
     * the Tool Forge keeps.
     */
    @GameTest(template = "empty")
    public static void disabledFamilyTabsDisappearFromBothBlocks(GameTestHelper helper) {
        int stationOn = ToolStationTabs.visible(false).size();
        int forgeOn = ToolStationTabs.visible(true).size();

        ForgeweaveConfig.MELEE_WEAPONS.set(false);
        try {
            List<Integer> station = ToolStationTabs.visible(false);
            List<Integer> forge = ToolStationTabs.visible(true);

            helper.assertTrue(station.contains(ToolStationTabs.REPAIR) && forge.contains(ToolStationTabs.REPAIR),
                    "the repair tab is never a family's, so it must survive any toggle");
            helper.assertTrue(station.size() < stationOn && forge.size() < forgeOn,
                    "switching a family off must remove tabs: " + stationOn + " -> " + station.size()
                            + " (station), " + forgeOn + " -> " + forge.size() + " (forge)");
            helper.assertTrue(station.size() < forge.size(),
                    "the forge gate must still apply on top of the family one, got " + station.size()
                            + " vs " + forge.size());
            for (int index : forge) {
                ToolStationTabs.Tab tab = ToolStationTabs.get(index);
                helper.assertFalse(!tab.isRepair()
                                && tab.entry().constants().category() == ToolConstants.Category.MELEE,
                        "no melee tab may survive meleeWeapons=false, got " + tab.title().getString());
            }
        } finally {
            ForgeweaveConfig.MELEE_WEAPONS.set(true);
        }

        helper.assertTrue(ToolStationTabs.visible(false).size() == stationOn
                        && ToolStationTabs.visible(true).size() == forgeOn,
                "and turning it back on must restore every tab");
        helper.succeed();
    }

    /**
     * The ticket's exclusivity rule, as arithmetic on the derivation itself: a sword blade serves
     * only melee weapons and goes with them, a tool handle is shared by both families and survives,
     * and a sharpening kit belongs to no tool at all so no toggle can touch it. Derived by walking
     * {@link ToolAssemblyRecipes#ENTRIES}, which is why no test here lists a part by hand.
     */
    @GameTest(template = "empty")
    public static void onlyFamilyExclusivePartsGoWithTheirFamily(GameTestHelper helper) {
        ForgeweaveConfig.MELEE_WEAPONS.set(false);
        try {
            helper.assertFalse(ContentFamilies.itemEnabled(ForgeweaveItems.PART_SWORD_BLADE.get()),
                    "a sword blade serves only melee weapons");
            helper.assertTrue(ContentFamilies.itemEnabled(ForgeweaveItems.PART_TOOL_HANDLE.get()),
                    "a tool handle is shared with harvest tools and must survive");
            helper.assertTrue(ContentFamilies.itemEnabled(ForgeweaveItems.PART_SHARPENING_KIT.get()),
                    "the sharpening kit is in no tool's part list, so it is in no family");
            helper.assertFalse(ContentFamilies.itemEnabled(ForgeweaveItems.PATTERN_SWORD_BLADE.get()),
                    "a pattern follows its part");
            helper.assertFalse(ContentFamilies.itemEnabled(ForgeweaveItems.CAST_SWORD_BLADE.get()),
                    "and so does its gold cast");
            helper.assertFalse(ContentFamilies.itemEnabled(
                            ForgeweaveItems.CLAY_CASTS.get("cast_sword_blade").get()),
                    "and its clay cast");
            helper.assertTrue(ContentFamilies.itemEnabled(ForgeweaveItems.CAST_INGOT.get()),
                    "an ingot cast shapes no part, so no family owns it");
        } finally {
            ForgeweaveConfig.MELEE_WEAPONS.set(true);
        }
        helper.assertTrue(ContentFamilies.itemEnabled(ForgeweaveItems.PART_SWORD_BLADE.get()),
                "turning the family back on must restore its parts");
        helper.succeed();
    }

    /**
     * Same rule at the Part Builder: the melee-exclusive pattern stamps nothing and the station says
     * so, while the shared handle pattern keeps working through the same flip.
     */
    @GameTest(template = "empty")
    public static void familyExclusivePartsCannotBeStamped(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(POS, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(POS);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(POS)),
                blockEntity.findSideInventory());

        blockEntity.container().setItem(PartBuilderMenu.PATTERN_SLOT,
                new ItemStack(ForgeweaveItems.PATTERN_SWORD_BLADE.get()));
        blockEntity.container().setItem(PartBuilderMenu.MATERIAL_SLOT, new ItemStack(Items.IRON_INGOT, 8));
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem().is(ForgeweaveItems.PART_SWORD_BLADE.get()),
                "with meleeWeapons on, the Part Builder must stamp a sword blade");

        ForgeweaveConfig.MELEE_WEAPONS.set(false);
        try {
            menu.broadcastChanges();
            helper.assertTrue(menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem().isEmpty(),
                    "with meleeWeapons off, a sword blade cannot be stamped, got "
                            + menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem());

            blockEntity.container().setItem(PartBuilderMenu.PATTERN_SLOT,
                    new ItemStack(ForgeweaveItems.PATTERN_TOOL_HANDLE.get()));
            menu.broadcastChanges();
            helper.assertTrue(menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem()
                            .is(ForgeweaveItems.PART_TOOL_HANDLE.get()),
                    "a shared part must still be stampable while one of its families is off");
        } finally {
            ForgeweaveConfig.MELEE_WEAPONS.set(true);
        }
        helper.succeed();
    }

    /**
     * And at the casting table: neither moulding a melee-exclusive cast nor casting through one
     * resolves while the family is off, exactly the way {@code enableClayCasts} filters its two
     * halves ({@code CastingRecipe#matches}, issue #292). A harvest cast in the same registry is
     * untouched, which is what proves this is a family filter rather than casting being switched off
     * wholesale.
     */
    @GameTest(template = "empty")
    public static void familyExclusiveCastsCannotBeMouldedOrPouredThrough(GameTestHelper helper) {
        ItemStack swordCast = new ItemStack(ForgeweaveItems.CAST_SWORD_BLADE.get());
        ItemStack axeCast = new ItemStack(ForgeweaveItems.CAST_AXE_HEAD.get());

        helper.assertTrue(castingResolves(helper, swordCast), "a sword blade cast pours while melee weapons are on");

        ForgeweaveConfig.MELEE_WEAPONS.set(false);
        try {
            helper.assertFalse(castingResolves(helper, swordCast),
                    "no melee-exclusive cast may pour while meleeWeapons is off");
            helper.assertTrue(castingResolves(helper, axeCast),
                    "a harvest tool's cast is unaffected by the melee toggle");
        } finally {
            ForgeweaveConfig.MELEE_WEAPONS.set(true);
        }
        helper.succeed();
    }

    /**
     * The smeltery family: nothing melts and nothing casts while it is off, and the menu carries the
     * line the GUI draws over the melt grid. Asked of the menu rather than of the screen so it needs
     * no client -- {@link ForgeweaveConfig} is a {@code SERVER} spec, so both sides read this same
     * value.
     */
    @GameTest(template = "empty")
    public static void smelteryOffStopsMeltingAndCasting(GameTestHelper helper) {
        ItemStack ironIngot = new ItemStack(Items.IRON_INGOT);
        ItemStack axeCast = new ItemStack(ForgeweaveItems.CAST_AXE_HEAD.get());

        helper.assertTrue(MeltingRecipe.find(helper.getLevel().registryAccess(), ironIngot).isPresent(),
                "an iron ingot melts while the smeltery is on, or this test proves nothing");

        ForgeweaveConfig.SMELTERY.set(false);
        try {
            helper.assertTrue(MeltingRecipe.find(helper.getLevel().registryAccess(), ironIngot).isEmpty(),
                    "nothing may melt while the smeltery family is off");
            helper.assertFalse(castingResolves(helper, axeCast),
                    "and nothing may cast either -- casting is part of the smeltery family");
        } finally {
            ForgeweaveConfig.SMELTERY.set(true);
        }

        helper.assertTrue(MeltingRecipe.find(helper.getLevel().registryAccess(), ironIngot).isPresent(),
                "turning it back on must restore melting with no reload");
        helper.succeed();
    }

    /** The smeltery GUI's "why is this idle" line, present exactly while the family is off. */
    @GameTest(template = "empty")
    public static void smelteryMenuSaysWhenItIsDisabled(GameTestHelper helper) {
        SmelteryMenu menu = new SmelteryMenu(0, helper.makeMockPlayer(GameType.SURVIVAL).getInventory(), POS, 1);

        helper.assertTrue(menu.disabledNotice() == null, "a working smeltery says nothing");
        ForgeweaveConfig.SMELTERY.set(false);
        try {
            helper.assertTrue(menu.disabledNotice() != null, "a disabled smeltery must explain itself");
        } finally {
            ForgeweaveConfig.SMELTERY.set(true);
        }
        helper.succeed();
    }

    /**
     * Modifiers off: applying one is refused with a message, and the tool comes back out of the
     * station unchanged. Only the <em>application</em> -- see
     * {@link #anExistingToolOfADisabledFamilyStillHits} for the other half of the ticket's rule.
     */
    @GameTest(template = "empty")
    public static void modifiersOffRefusesApplication(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "stone", "wood", "wood");
        ItemStack redstone = new ItemStack(Items.REDSTONE, 1);

        helper.assertTrue(ModifierApplication.resolve(helper.getLevel().registryAccess(), pickaxe, redstone,
                        ItemStack.EMPTY)
                .filter(outcome -> !outcome.output().isEmpty()).isPresent(),
                "one redstone applies haste while modifiers are on, or this test proves nothing");

        ForgeweaveConfig.MODIFIERS.set(false);
        try {
            ModifierApplication.Outcome outcome = ModifierApplication
                    .resolve(helper.getLevel().registryAccess(), pickaxe, redstone, ItemStack.EMPTY)
                    .orElseThrow(() -> new AssertionError("a loaded reagent must still be recognized and refused"));
            helper.assertTrue(outcome.output().isEmpty(), "a refused application must produce no tool");
            helper.assertTrue(outcome.rejection() != null, "and must say why");
        } finally {
            ForgeweaveConfig.MODIFIERS.set(true);
        }
        helper.succeed();
    }

    /**
     * The other half of "unobtainable, never unregistered": a broadsword assembled while melee
     * weapons were on keeps hitting after they are switched off, and its repair tab is still there.
     * Nothing about an existing stack is allowed to change -- that is the whole reason these
     * toggles filter at lookup instead of touching the registry.
     */
    @GameTest(template = "empty")
    public static void anExistingToolOfADisabledFamilyStillHits(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack broadsword = ToolAssembly.assemble(helper, player, POS,
                ToolAssembly.entryFor(ForgeweaveItems.TOOL_BROADSWORD.get()), List.of("wood", "iron", "iron"));
        helper.assertTrue(broadsword.is(ForgeweaveItems.TOOL_BROADSWORD.get()),
                "the fixture must actually be a broadsword, got " + broadsword);

        ForgeweaveConfig.MELEE_WEAPONS.set(false);
        try {
            Pig target = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, broadsword);
            DamageSource source = helper.getLevel().damageSources().playerAttack(player);
            float before = target.getHealth();
            target.hurt(source, 3.0F);
            helper.assertTrue(target.getHealth() < before,
                    "a tool of a switched-off family must still damage what it hits");
            target.discard();

            helper.assertTrue(ToolStationTabs.visible(false).contains(ToolStationTabs.REPAIR),
                    "and must still have a repair tab to be maintained at");
        } finally {
            ForgeweaveConfig.MELEE_WEAPONS.set(true);
        }
        helper.succeed();
    }

    /** A Tool Station at {@link #POS} with an iron broadsword's three parts in its input slots. */
    private static ToolStationMenu loadBroadswordParts(GameTestHelper helper, Player player) {
        helper.setBlock(POS, ForgeweaveBlocks.TOOL_STATION.get());
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(POS);
        ToolAssemblyRecipes.Entry entry = ToolAssembly.entryFor(ForgeweaveItems.TOOL_BROADSWORD.get());
        for (int i = 0; i < entry.slotCount(); i++) {
            blockEntity.container().setItem(i, ToolAssembly.part(entry.part(i), "iron"));
        }
        return ToolAssembly.menu(helper, player, POS, blockEntity);
    }

    /** Whether molten iron poured over {@code held} at a casting table shapes anything. */
    private static boolean castingResolves(GameTestHelper helper, ItemStack held) {
        return CastingRecipe.find(helper.getLevel().registryAccess().registryOrThrow(CastingRecipe.REGISTRY),
                CastingRecipe.Station.TABLE, held, ForgeweaveFluids.IRON.still().get()) != null;
    }
}
