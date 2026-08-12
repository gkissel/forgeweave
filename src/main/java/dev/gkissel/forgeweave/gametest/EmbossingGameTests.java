package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.Embossing;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Issue #154's verification: embossing driven through the real Tool Station menu against the shipped
 * {@code forgeweave:embossment} recipe JSON (slime block + magma block + gold block, plus the donor
 * part), covering the four things docs/SCOPE.md's M3 gate names -- donor traits present, stats
 * untouched, a second embossment refused, and the modifier slot count unaffected.
 *
 * <p>The donor throughout is an <b>iron pickaxe head</b>, whose material scopes {@code magnetic2} to
 * heads and {@code magnetic} to everything else. Embossing it onto a stone/wood/wood pickaxe is
 * therefore an assertion with no false positive available: neither trait can be on that tool for any
 * other reason.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class EmbossingGameTests {

    private static final ResourceLocation IRON =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron");
    private static final ResourceLocation MAGNETIC2 =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "magnetic2");

    /**
     * The mechanic itself: the donor material's traits land on the tool, the embossment serializes as
     * {@code embossment.<material>} at level 1 (ADR-0004's {@code id + level} rule, upstream's
     * generated identifier), and taking the output spends one of every reagent slot.
     */
    @GameTest(template = "empty")
    public static void embossingAddsTheDonorMaterialsTraits(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        helper.assertFalse(traits(pickaxe).contains(MAGNETIC2),
                "the base tool must not already carry the donor's trait, or this test proves nothing");

        ToolStationMenu menu = load(helper, player, pos, pickaxe, ironPickaxeHead());
        ItemStack embossed = take(helper, player, menu);

        helper.assertTrue(traits(embossed).contains(MAGNETIC2),
                "an iron head embossment must add iron's head-scoped trait, got " + traits(embossed));
        ModifierEntry entry = ForgeweaveModifiers.entry(embossed, Embossing.idFor(IRON));
        helper.assertTrue(entry != null && entry.level() == 1,
                "the embossment must serialize as forgeweave:embossment.iron at level 1, got " + entry);
        for (int slot = ToolStationMenu.HEAD_SLOT; slot < ToolStationMenu.INPUT_SLOTS; slot++) {
            helper.assertTrue(menu.getSlot(slot).getItem().isEmpty(),
                    "taking the embossed tool must spend one of every loaded slot; slot " + slot
                            + " still holds " + menu.getSlot(slot).getItem());
        }
        helper.succeed();
    }

    /**
     * The other half of upstream {@code ModExtraTrait#applyEffect}, which adds traits and touches no
     * stat block at all: durability, mining speed, attack damage and the vanilla {@code tool}
     * component all come out of an embossment exactly as they went in.
     */
    @GameTest(template = "empty")
    public static void embossingLeavesTheToolsStatsAlone(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        // Snapshotted before the station spends the tool slot: taking the output shrinks that stack
        // to nothing, and an empty ItemStack answers null to every component read.
        ToolStats.Stats effectiveBefore = ForgeweaveModifiers.effectiveStats(pickaxe);
        ToolStats.Stats baseBefore = pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get());
        Tool toolBefore = pickaxe.get(DataComponents.TOOL);
        int durabilityBefore = pickaxe.getMaxDamage();

        ItemStack embossed = take(helper, player, load(helper, player, pos, pickaxe, ironPickaxeHead()));

        helper.assertTrue(effectiveBefore.equals(ForgeweaveModifiers.effectiveStats(embossed)),
                "embossing must not change the tool's stats: " + effectiveBefore + " -> "
                        + ForgeweaveModifiers.effectiveStats(embossed));
        helper.assertTrue(baseBefore.equals(embossed.get(ForgeweaveDataComponents.TOOL_STATS.get())),
                "the stored base stat block must be untouched too");
        helper.assertTrue(toolBefore.equals(embossed.get(DataComponents.TOOL)),
                "the vanilla tool component -- mining speed and tier -- must be untouched");
        helper.assertTrue(durabilityBefore == embossed.getMaxDamage(),
                "the durability pool must be untouched: " + durabilityBefore + " -> "
                        + embossed.getMaxDamage());
        helper.succeed();
    }

    /** Upstream's {@code ExtraTraitAspect#canApply}: exactly one embossment per tool, ever. */
    @GameTest(template = "empty")
    public static void aSecondEmbossmentIsRejected(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        ItemStack embossed = take(helper, player, load(helper, player, pos, pickaxe, ironPickaxeHead()));

        // A different material's part, so nothing but the one-per-tool rule can be what refuses it.
        ToolStationMenu menu = load(helper, player, pos, embossed,
                ToolAssembly.part(ForgeweaveItems.PART_TOOL_HANDLE.get(), "bone"));

        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a second embossment must produce no output");
        helper.assertTrue(menu.rejection() != null,
                "a refused second embossment must tell the player why");
        helper.succeed();
    }

    /**
     * Parity with upstream, where {@code ModExtraTrait} carries no {@code FreeModifierAspect}: the
     * embossment entry sits on the tool without spending one of the three modifier slots, so all
     * three are still there to spend on real modifiers afterwards.
     */
    @GameTest(template = "empty")
    public static void anEmbossmentCostsNoModifierSlot(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        int before = ForgeweaveModifiers.freeSlots(pickaxe);

        ItemStack embossed = take(helper, player, load(helper, player, pos, pickaxe, ironPickaxeHead()));

        helper.assertTrue(ForgeweaveModifiers.freeSlots(embossed) == before,
                "an embossment must cost no modifier slot: " + before + " -> "
                        + ForgeweaveModifiers.freeSlots(embossed));
        helper.assertTrue(before == ForgeweaveModifiers.DEFAULT_SLOTS,
                "and those slots are still the three every tool starts with");

        // Still spendable: a real modifier applied afterwards takes one of the three as usual.
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, embossed);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT, new ItemStack(Items.REDSTONE, 1));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        ItemStack hasted = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem();

        helper.assertFalse(hasted.isEmpty(), "an embossed tool must still accept a modifier");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(hasted) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "the modifier applied after an embossment takes one of the three slots, got "
                        + ForgeweaveModifiers.freeSlots(hasted) + " free");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    /** A station loaded with the tool, a donor part and one of each shipped reagent, ready to take. */
    private static ToolStationMenu load(GameTestHelper helper, Player player, BlockPos pos, ItemStack tool,
            ItemStack donor) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(pos);
        blockEntity.container().clearContent();
        blockEntity.container().setItem(ToolStationMenu.HEAD_SLOT, tool);
        blockEntity.container().setItem(ToolStationMenu.BINDING_SLOT, donor);
        blockEntity.container().setItem(ToolStationMenu.HANDLE_SLOT, new ItemStack(Items.SLIME_BLOCK));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_1, new ItemStack(Items.MAGMA_BLOCK));
        blockEntity.container().setItem(ToolStationMenu.EXTRA_SLOT_2, new ItemStack(Items.GOLD_BLOCK));

        ToolStationMenu menu = ToolAssembly.menu(helper, player, pos, blockEntity);
        menu.broadcastChanges();
        return menu;
    }

    private static ItemStack take(GameTestHelper helper, Player player, ToolStationMenu menu) {
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "expected the station to produce an embossed tool");
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }

    private static ItemStack ironPickaxeHead() {
        return ToolAssembly.part(ForgeweaveItems.PART_PICKAXE_HEAD.get(), "iron");
    }

    private static List<ResourceLocation> traits(ItemStack stack) {
        return stack.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of());
    }
}
