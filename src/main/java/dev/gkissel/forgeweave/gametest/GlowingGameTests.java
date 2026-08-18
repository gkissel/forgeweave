package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ToolStationBlockEntity;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.menu.ToolStationMenu;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Parity audit T25's verification (issue #456), against the 1.12 clone's {@code ModGlowing}: an ender
 * eye applies glowing once, and a held glowing tool standing anywhere darker than light 8 spends one
 * durability to drop a light next to itself -- once, not every tick, and never from a pocket, never
 * in the light.
 *
 * <p>The lit and the dark test pockets are sealed boxes rather than anything the world's own daylight
 * decides, so the test says the same thing whatever the GameTest level generator puts around the
 * structure; the {@code thenIdle} steps are there because {@code ServerLevel}'s light engine applies
 * its updates on the chunk source's tick, not inside {@code setBlock}.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class GlowingGameTests {

    private static final ResourceLocation GLOWING = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "glowing");

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    /** Sealed in stone: light 0, so a held glowing tool must light it. */
    private static final BlockPos DARK = new BlockPos(5, 2, 1);
    /** Sealed the same way but with one glowstone wall: light 14, so nothing must happen. */
    private static final BlockPos LIT = new BlockPos(9, 2, 1);
    /** As dark as {@link #DARK}, for the tool that is carried rather than held. */
    private static final BlockPos POCKETED = new BlockPos(13, 2, 1);

    /**
     * Upstream {@code TinkerModifiers:163} binds glowing to a single {@code ItemCombination}, i.e.
     * one application; the shipped recipe's {@code max_level: 1} is that, and it costs the one slot
     * upstream's {@code freeModifier} aspect charges.
     */
    @GameTest(template = "empty")
    public static void anEnderEyeAppliesGlowingOnce(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");

        ItemStack glowing = applyReagent(helper, player, pickaxe, new ItemStack(Items.ENDER_EYE, 1));

        ModifierEntry entry = ForgeweaveModifiers.entry(glowing, GLOWING);
        helper.assertTrue(entry != null && entry.level() == 1,
                "one ender eye must record glowing at level 1, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.freeSlots(glowing) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "glowing must occupy exactly one modifier slot, got "
                        + ForgeweaveModifiers.freeSlots(glowing) + " free");

        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        blockEntity.container().setItem(0, glowing);
        blockEntity.container().setItem(1, new ItemStack(Items.ENDER_EYE, 1));
        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().isEmpty(),
                "a second ender eye must be refused -- upstream's DataAspect applies once");
        helper.succeed();
    }

    /**
     * The whole of {@code ModGlowing#onUpdate}, in the four states that matter: dark and held (a
     * light, one durability), dark and already lit (nothing more, so the tool cannot bleed out
     * standing still), light (nothing), and carried rather than held (nothing).
     */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void aHeldGlowingToolLightsOnlyTheDark(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, STATION, "stone", "wood", "wood");
        pickaxe.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(GLOWING, 1)));

        seal(helper, DARK, Blocks.STONE);
        seal(helper, LIT, Blocks.GLOWSTONE);
        seal(helper, POCKETED, Blocks.STONE);

        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    stand(helper, player, DARK);
                    tick(helper, pickaxe, player, true);
                    helper.assertTrue(helper.getBlockState(DARK).is(Blocks.LIGHT),
                            "a held glowing tool in the dark must place a light, found "
                                    + helper.getBlockState(DARK));
                    helper.assertTrue(pickaxe.getDamageValue() == 1,
                            "the light must cost exactly one durability, got " + pickaxe.getDamageValue());
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    tick(helper, pickaxe, player, true);
                    helper.assertTrue(pickaxe.getDamageValue() == 1,
                            "a spot that is already lit must cost nothing more, got " + pickaxe.getDamageValue());

                    stand(helper, player, LIT);
                    tick(helper, pickaxe, player, true);
                    helper.assertFalse(helper.getBlockState(LIT).is(Blocks.LIGHT),
                            "nothing must be placed where the light is already above 8");

                    stand(helper, player, POCKETED);
                    tick(helper, pickaxe, player, false);
                    helper.assertFalse(helper.getBlockState(POCKETED).is(Blocks.LIGHT),
                            "a glowing tool that is merely carried must light nothing");
                    helper.assertTrue(pickaxe.getDamageValue() == 1,
                            "neither of those must cost durability, got " + pickaxe.getDamageValue());
                })
                .thenSucceed();
    }

    /** Walls {@code pocket} in on all six sides and empties it, so its light level is decided here. */
    private static void seal(GameTestHelper helper, BlockPos pocket, Block wall) {
        for (Direction face : Direction.values()) {
            helper.setBlock(pocket.relative(face), wall);
        }
        helper.setBlock(pocket, Blocks.AIR);
    }

    private static void stand(GameTestHelper helper, Player player, BlockPos pocket) {
        BlockPos absolute = helper.absolutePos(pocket);
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
    }

    private static void tick(GameTestHelper helper, ItemStack tool, Player player, boolean selected) {
        tool.getItem().inventoryTick(tool, helper.getLevel(), player, 0, selected);
    }

    private static ItemStack applyReagent(GameTestHelper helper, Player player, ItemStack tool, ItemStack reagent) {
        ToolStationBlockEntity blockEntity = helper.getBlockEntity(STATION);
        blockEntity.container().setItem(0, tool);
        blockEntity.container().setItem(1, reagent);
        blockEntity.container().setItem(2, ItemStack.EMPTY);

        ToolStationMenu menu = ToolAssembly.menu(helper, player, STATION, blockEntity);
        menu.broadcastChanges();
        ItemStack output = menu.getSlot(ToolStationMenu.OUTPUT_SLOT).getItem().copy();
        helper.assertFalse(output.isEmpty(), "expected the station to produce a modified tool");
        menu.getSlot(ToolStationMenu.OUTPUT_SLOT).onTake(player, output);
        return output;
    }
}
