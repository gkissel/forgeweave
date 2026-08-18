package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Parity audit T50 (issue #481) -- the craft and hit cues upstream plays and Forgeweave played
 * nowhere: {@code ContainerToolStation#playCraftSound} on every take from a Tool Station's output,
 * {@code ContainerToolForge}'s override of it at a Tool Forge, and {@code FryPan}'s boing on both
 * halves of the pan (its {@code dealDamage} on a landed blow, and its {@code onPlayerStoppedUsing}
 * on a charged launch).
 *
 * <p>Two of the three sounds are custom assets upstream ships under CC-BY/CC0 rather than the MIT
 * its code carries, and its own {@code sounds/Credits.txt} names an author for neither
 * {@code little_saw} nor {@code frypan_hit}. Adding non-MIT third-party material needs an explicit
 * maintainer decision here (CLAUDE.md, the Spartan Weaponry precedent; that decision is issue #566),
 * so these ship as vanilla
 * stand-ins at upstream's own volumes and pitch spreads -- the same call issue #415 made for
 * shocking and issue #495 for squeaky. Only the Tool Forge's anvil is upstream's actual sound,
 * because upstream itself uses vanilla's there. Swapping a stand-in for a derived asset later is a
 * one-constant change per site; the volumes, spreads and call sites asserted below are the parity
 * that matters and do not move with it.
 *
 * <p>{@link SoundCapture} observes the {@code PlayLevelSoundEvent.AtPosition} NeoForge fires from
 * inside {@code Level#playSound}, since a mock player receives no sound packet.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class CraftAndHitSoundGameTests {

    private static final String HEAD = "stone";
    private static final String OTHER = "wood";

    /**
     * Upstream {@code ContainerToolStation#playCraftSound}: {@code Sounds.saw} at volume 0.8 and
     * pitch {@code 0.8 + 0.4 * random}, on every take from the output slot -- assembly, repair,
     * modify and rename alike, since upstream plays it at the end of {@code onTakeOutput} without
     * asking which recipe ran.
     */
    @GameTest(template = "empty")
    public static void takingFromTheToolStationPlaysTheSawCue(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        standInside(helper, player, pos);

        List<SoundCapture.Played> cues = cuesOf(SoundEvents.UI_STONECUTTER_TAKE_RESULT,
                SoundCapture.playedDuring(helper, () -> ToolAssembly.assemble(helper, player, pos,
                        entry(ForgeweaveItems.TOOL_PICKAXE.get()), materials(ForgeweaveItems.TOOL_PICKAXE.get()))));

        helper.assertTrue(cues.size() == 1, "expected exactly one craft cue, got " + cues);
        SoundCapture.Played cue = cues.get(0);
        helper.assertTrue(cue.volume() == 0.8F, "upstream plays the saw at volume 0.8, got " + cue.volume());
        helper.assertTrue(cue.pitch() >= 0.8F && cue.pitch() <= 1.2F,
                "upstream's saw pitch spread is 0.8-1.2, got " + cue.pitch());
        helper.succeed();
    }

    /**
     * Upstream {@code ContainerToolForge#playCraftSound} overrides the saw with vanilla's own
     * {@code BLOCK_ANVIL_USE} at volume 0.9 and pitch {@code 0.95 + 0.2 * random} -- the one cue in
     * this ticket that is not a custom asset, so this is upstream's sound and not a stand-in.
     */
    @GameTest(template = "empty")
    public static void takingFromTheToolForgePlaysTheAnvilCue(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        standInside(helper, player, pos);

        List<SoundCapture.Played> played = SoundCapture.playedDuring(helper,
                () -> ToolAssembly.assembleAtForge(helper, player, pos,
                        entry(ForgeweaveItems.TOOL_PICKAXE.get()), materials(ForgeweaveItems.TOOL_PICKAXE.get())));

        List<SoundCapture.Played> cues = cuesOf(SoundEvents.ANVIL_USE, played);
        helper.assertTrue(cues.size() == 1, "expected exactly one forge craft cue, got " + cues);
        SoundCapture.Played cue = cues.get(0);
        helper.assertTrue(cue.volume() == 0.9F, "upstream plays the anvil at volume 0.9, got " + cue.volume());
        helper.assertTrue(cue.pitch() >= 0.95F && cue.pitch() <= 1.15F,
                "upstream's anvil pitch spread is 0.95-1.15, got " + cue.pitch());
        helper.assertTrue(cuesOf(SoundEvents.UI_STONECUTTER_TAKE_RESULT, played).isEmpty(),
                "the Tool Forge overrides the saw rather than adding to it, got " + played);
        helper.succeed();
    }

    /**
     * Upstream {@code FryPan#dealDamage}: the pan's boing on every landed blow, volume 2.0, pitch
     * 1.0 flat -- no spread on this one, unlike the launch's.
     */
    @GameTest(template = "empty")
    public static void theFryingPanBoingsOnALandedBlow(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pan = pan(helper, player, pos);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        player.setPos(pig.getX(), pig.getY(), pig.getZ() - 1.0);
        player.setItemInHand(InteractionHand.MAIN_HAND, pan);

        List<SoundCapture.Played> cues = cuesOf(SoundEvents.ANVIL_PLACE, SoundCapture.playedDuring(helper,
                () -> pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F)));

        helper.assertTrue(cues.size() == 1, "expected exactly one boing on a landed blow, got " + cues);
        SoundCapture.Played cue = cues.get(0);
        helper.assertTrue(cue.volume() == 2.0F, "upstream plays the boing at volume 2.0, got " + cue.volume());
        helper.assertTrue(cue.pitch() == 1.0F, "upstream's on-hit boing has no pitch spread, got " + cue.pitch());
        pig.discard();
        helper.succeed();
    }

    /**
     * Upstream {@code FryPan#onPlayerStoppedUsing} plays a second boing on the launch itself, volume
     * 1.5 and pitch {@code 0.6 + 0.2 * random} -- on top of the one the blow it lands already played
     * through {@code dealDamage}, so a charged launch is two cues, not one.
     */
    @GameTest(template = "empty")
    public static void aChargedLaunchAddsASecondLowerBoing(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pan = pan(helper, player, pos);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.setDeltaMovement(Vec3.ZERO);
        player.setPos(pig.getX(), pig.getY() - 1.2, pig.getZ() - 2.0);
        player.setYRot(0.0F);
        player.setXRot(0.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, pan);
        ToolItem tool = (ToolItem) pan.getItem();

        List<SoundCapture.Played> cues = cuesOf(SoundEvents.ANVIL_PLACE, SoundCapture.playedDuring(helper,
                () -> tool.releaseUsing(pan, helper.getLevel(), player, pan.getUseDuration(player) - 40)));

        helper.assertTrue(cues.size() == 2, "a charged launch is the blow's boing plus the launch's, got " + cues);
        List<SoundCapture.Played> launch = cues.stream().filter(cue -> cue.volume() == 1.5F).toList();
        helper.assertTrue(launch.size() == 1, "expected one launch cue at volume 1.5, got " + cues);
        float pitch = launch.get(0).pitch();
        helper.assertTrue(pitch >= 0.6F && pitch <= 0.8F,
                "upstream's launch pitch spread is 0.6-0.8, got " + pitch);
        pig.discard();
        helper.succeed();
    }

    private static List<SoundCapture.Played> cuesOf(SoundEvent sound, List<SoundCapture.Played> played) {
        return played.stream().filter(cue -> cue.sound().value() == sound).toList();
    }

    private static ItemStack pan(GameTestHelper helper, Player player, BlockPos pos) {
        standInside(helper, player, pos);
        return ToolAssembly.assemble(helper, player, pos, entry(ForgeweaveItems.TOOL_FRYING_PAN.get()),
                materials(ForgeweaveItems.TOOL_FRYING_PAN.get()));
    }

    /** {@link SoundCapture} only sees cues inside the test structure, and a mock player starts outside it. */
    private static void standInside(GameTestHelper helper, Player player, BlockPos pos) {
        BlockPos absolute = helper.absolutePos(pos);
        player.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
    }

    private static ToolAssemblyRecipes.Entry entry(ToolItem tool) {
        return ToolAssembly.entryFor(tool);
    }

    private static List<String> materials(ToolItem tool) {
        return ToolAssembly.entryFor(tool).constants().parts().stream()
                .map(slot -> slot.role() == dev.gkissel.forgeweave.tool.ToolConstants.Role.HEAD ? HEAD : OTHER)
                .toList();
    }
}
