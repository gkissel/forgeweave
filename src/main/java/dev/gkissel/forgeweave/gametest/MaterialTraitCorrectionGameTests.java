package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.TraitStacks;

/**
 * Issue #1091: the four material traits that promised one thing and did another, each proved on a
 * real piece of gear rather than on the JSON that describes it.
 *
 * <p>Three of them ({@code heft3}, {@code bracingplate},
 * {@code heft}) shipped over {@code damage_floor}, which is the armor library's
 * drawback: it raises a blow back toward its original damage, so on armor it undoes other traits'
 * reductions and on a tool it does nothing at all. The fourth ({@code surgeward}) asked
 * for an 8-tick invulnerability window, below vanilla's own 20, so it shortened the wearer's
 * recovery instead of lengthening it -- {@code InvulnerabilityWindow}'s own javadoc warns about
 * exactly that threshold.
 *
 * <p>Staging follows {@link ArmorTraitLibraryGameTests}: the three materials are existence-gated on
 * mods no GameTest server has ({@code actuallyadditions}, {@code twilightforest},
 * {@code pneumaticcraft}), so a piece is assembled from iron at a real Tool Station and its
 * {@code forgeweave:traits} component is overwritten with the real shipped trait ids. The
 * {@code trait_definition} files under test are unconditioned, so what runs here is the shipped
 * JSON, parameters and all.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MaterialTraitCorrectionGameTests {

    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    private static final float BLOW = 10.0F;

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /** A survival mock player holding a hatchet whose trait list is exactly {@code traits}. */
    private static Player holding(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = CombatTraitGameTests.tool(ForgeweaveItems.TOOL_HATCHET.get(),
                List.of(traits).stream().map(MaterialTraitCorrectionGameTests::id).toList(), 3.0F);
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        player.tick();
        return player;
    }

    /** A survival mock player wearing an iron chestplate whose trait list is exactly {@code traits}. */
    private static Player wearing(GameTestHelper helper, String... traits) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.TOOL_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of("iron", "iron"));
        piece.set(ForgeweaveDataComponents.TRAITS.get(),
                List.of(traits).stream().map(MaterialTraitCorrectionGameTests::id).toList());
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        return player;
    }

    private static ItemStack worn(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST);
    }

    /** One blow onto a healed, non-invulnerable player; what it cost them. */
    private static float lost(Player player, DamageSource source, float amount) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(source, amount);
        return before - player.getHealth();
    }

    private static double knockbackResistance(Player player) {
        return player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
    }

    /**
     * Heft II and Heft III, the rungs #1103 folded the twelve knockback ids into: a held tool plants
     * its wielder, and the deeper rung plants them harder. Before #1091 the empowered crystal
     * granted no knockback resistance at all.
     */
    @GameTest(template = "empty")
    public static void theEmpoweredCrystalResistsKnockbackMoreThanThePlainOne(GameTestHelper helper) {
        double bare = knockbackResistance(holding(helper));
        double plain = knockbackResistance(holding(helper, "heft2"));
        double empowered = knockbackResistance(holding(helper, "heft3"));

        helper.assertTrue(Math.abs(plain - bare - 0.4) < 1e-4,
                "Heft II must grant 0.4 over " + bare + ", got " + plain);
        helper.assertTrue(Math.abs(empowered - bare - 0.55) < 1e-4,
                "Heft III must grant 0.55 over " + bare + ", got " + empowered);
        helper.assertTrue(empowered > plain,
                "the empowered crystal must resist knockback more than the plain one, "
                        + empowered + " vs " + plain);
        helper.succeed();
    }

    /**
     * Heft I is the weight its name claims: #1103's floor puts the bottom rung at 0.25, a quarter of
     * full immunity, because below that the shove still moves the wielder the same visual distance.
     * Before #1091 it granted nothing on a tool and cost the wearer damage on armour.
     */
    @GameTest(template = "empty")
    public static void compressedIronHeftPlantsTheWielderWhileHeld(GameTestHelper helper) {
        double bare = knockbackResistance(holding(helper));
        double heft = knockbackResistance(holding(helper, "heft"));

        helper.assertTrue(Math.abs(heft - bare - 0.25) < 1e-4,
                "Heft I must grant 0.25 over " + bare + ", got " + heft);
        helper.assertTrue(heft > bare, "and must be strictly more than an empty hand");
        helper.succeed();
    }

    /**
     * {@code bracingplate} protects, and protects more the longer the fight runs: the fifth blow costs
     * less than the first and the stacks stand at the cap on the piece. Before #1091 repeated blows
     * built nothing.
     */
    @GameTest(template = "empty")
    public static void nagaWardHardensAsBlowsKeepLanding(GameTestHelper helper) {
        Player player = wearing(helper, "bracingplate");
        DamageSource source = helper.getLevel().damageSources().generic();

        float first = lost(player, source, BLOW);
        float last = first;
        for (int i = 0; i < 4; i++) {
            last = lost(player, source, BLOW);
        }

        helper.assertTrue(last < first, "the fifth blow must cost less than the first, " + last + " vs " + first);
        TraitStacks stacks = worn(player).get(ForgeweaveDataComponents.RESISTANCE_STACKS.get());
        helper.assertTrue(stacks != null && stacks.level() == 4,
                "five blows must leave the stacks at Bracing Plate I's cap of 4, got " + stacks);
        helper.succeed();
    }

    /**
     * Issue #1097's raised {@code stacking_resistance} preset, now Bracing Plate I after #1103
     * merged the five ids that shared the mechanic. The control is the same iron chestplate carrying
     * no trait at all, whose blows never get cheaper however many land.
     */
    @GameTest(template = "empty")
    public static void arcticInsulationReachesItsTiersProtection(GameTestHelper helper) {
        DamageSource source = helper.getLevel().damageSources().generic();

        Player control = wearing(helper);
        float controlFirst = lost(control, source, BLOW);
        float controlLast = controlFirst;
        for (int i = 0; i < 3; i++) {
            controlLast = lost(control, source, BLOW);
        }
        helper.assertTrue(Math.abs(controlLast - controlFirst) < 1e-4,
                "an untraited piece must cost the same every blow, " + controlLast + " against " + controlFirst);

        Player player = wearing(helper, "bracingplate");
        float first = lost(player, source, BLOW);
        float capped = first;
        for (int i = 0; i < 3; i++) {
            capped = lost(player, source, BLOW);
        }
        TraitStacks stacks = worn(player).get(ForgeweaveDataComponents.RESISTANCE_STACKS.get());
        helper.assertTrue(stacks != null && stacks.level() == 4,
                "four blows must leave the stacks at Bracing Plate I's cap of 4, got " + stacks);
        // Four stacks at 1.0 protection each is 4/25 of the blow that arrives with them standing.
        helper.assertTrue(capped < first && Math.abs(capped - first * 0.84F) < 0.2F,
                "at the cap the blow must cost 16% less, " + capped + " against " + first);
        helper.succeed();
    }

    /**
     * The negative that started #1091. {@code stormward} cancels a lightning blow outright; none of
     * the three corrected traits may put any of it back. Worn beside {@code damage_floor} -- which
     * is what all three carried in 0.6.0-beta.2 -- each of them did.
     */
    @GameTest(template = "empty")
    public static void noneOfTheThreeTurnsACancelledBlowBackIntoDamage(GameTestHelper helper) {
        DamageSource lightning = helper.getLevel().damageSources().lightningBolt();
        helper.assertTrue(lost(wearing(helper, "stormward"), lightning, BLOW) == 0.0F,
                "stormward alone must cancel the blow outright");

        for (String trait : List.of("heft3", "bracingplate", "heft")) {
            float lost = lost(wearing(helper, "stormward", trait), lightning, BLOW);
            helper.assertTrue(lost == 0.0F,
                    trait + " must not put a cancelled blow back; it cost the wearer " + lost);
        }
        helper.succeed();
    }

    /**
     * {@code surgeward} lengthens the wearer's recovery window past vanilla's 20 ticks.
     * Before #1091 it asked for 8, which vanilla honours literally, so the trait left its wearer
     * open sooner than wearing nothing would have.
     */
    @GameTest(template = "empty")
    public static void alphaYetiResilienceLengthensTheWindowRatherThanShorteningIt(GameTestHelper helper) {
        Player plain = wearing(helper);
        lost(plain, helper.getLevel().damageSources().generic(), BLOW);
        helper.assertTrue(plain.invulnerableTime == 20, "vanilla's own window is 20, got " + plain.invulnerableTime);

        Player player = wearing(helper, "surgeward");
        lost(player, helper.getLevel().damageSources().generic(), BLOW);
        helper.assertTrue(player.invulnerableTime > 20,
                "the trait must lengthen the window, not shorten it, got " + player.invulnerableTime);
        helper.assertTrue(player.invulnerableTime == 40,
                "and the shipped definition asks for 40 ticks, got " + player.invulnerableTime);
        helper.succeed();
    }

    /** Every trait id under test resolves to a behaviour: a renamed or dropped definition fails here. */
    @GameTest(template = "empty")
    public static void everyCorrectedTraitIdStillResolves(GameTestHelper helper) {
        for (String trait : List.of("heft3", "bracingplate", "heft",
                "surgeward")) {
            helper.assertTrue(ForgeweaveTraits.lookup(id(trait)) != null,
                    "expected " + trait + " to still resolve, so tools built before #1091 keep their trait");
        }
        helper.succeed();
    }

    private MaterialTraitCorrectionGameTests() {}
}
