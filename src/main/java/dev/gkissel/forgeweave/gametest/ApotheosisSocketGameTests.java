package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.apotheosis.ApotheosisSockets;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.tool.ToolLevel;

/**
 * Issue #969 (docs/SCOPE.md M8, D-M8-1), the Forgeweave side: that {@code socketed} applies at the
 * Tool Station and spends a slot per socket, that a slot earned by levelling up under M7 buys one,
 * that the station seats a gem into the socket component, and that with the integration switched off
 * a stack that already carries sockets keeps its component and works again when it comes back.
 *
 * <p>Apotheosis is not on the gametest classpath (build.gradle, JC-B), so two stand-ins are in play.
 * The shipped recipes name Apotheosis items and gate themselves on that mod, so the station applies
 * {@code gametest_socketed.json} and {@code gametest_socket_gem.json} instead -- same modifier, same
 * cap, vanilla reagents (see {@code src/gametest/resources/README.md}). And the gem is stood in for
 * by a fake {@link ApotheosisSockets.Bridge}, the split {@code DraconicModuleEffectGameTests} already
 * makes: what a real gem's bonus works out to is a manual release-checklist line, and what these
 * tests cover is Forgeweave's own plumbing.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ApotheosisSocketGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /** {@code gametest_socketed.json}'s reagent -- one socket per item. */
    private static ItemStack sigil(int count) {
        return new ItemStack(Items.HEART_OF_THE_SEA, count);
    }

    /** {@code gametest_socket_gem.json}'s reagent, standing in for an Apotheosis gem. */
    private static ItemStack gem() {
        return new ItemStack(Items.NAUTILUS_SHELL);
    }

    /**
     * Acceptance test 1: applying {@code socketed} costs a modifier slot per level, the tool reports
     * that many sockets, and its free-slot count is exactly what
     * {@link ForgeweaveModifiers#freeSlots} says it should be.
     */
    @GameTest(template = "empty")
    public static void socketedCostsOneModifierSlotPerSocket(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "iron", "wood", "wood");

        int before = ForgeweaveModifiers.freeSlots(pickaxe);
        helper.assertTrue(before == ForgeweaveModifiers.DEFAULT_SLOTS,
                "setup: a fresh pickaxe starts with " + ForgeweaveModifiers.DEFAULT_SLOTS
                        + " slots, got " + before);

        ItemStack socketed = apply(helper, pickaxe, sigil(1));
        helper.assertTrue(ApotheosisSockets.socketCount(socketed) == 1,
                "one sigil must buy one socket, got " + ApotheosisSockets.socketCount(socketed));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(socketed) == before - 1,
                "and cost exactly one slot: expected " + (before - 1) + ", got "
                        + ForgeweaveModifiers.freeSlots(socketed));

        ItemStack twice = apply(helper, socketed, sigil(1));
        helper.assertTrue(ApotheosisSockets.socketCount(twice) == 2,
                "a second sigil buys a second socket, got " + ApotheosisSockets.socketCount(twice));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(twice) == before - 2,
                "for a second slot: expected " + (before - 2) + ", got "
                        + ForgeweaveModifiers.freeSlots(twice));
        helper.assertTrue(ApotheosisSockets.gems(twice).stream().allMatch(ItemStack::isEmpty),
                "both sockets start empty");
        helper.succeed();
    }

    /**
     * Acceptance test 3, the slot half: a tool that has spent its three starting slots takes no
     * fourth socket, and one M7 level -- which grants exactly one slot -- is enough to buy it. That
     * is D-M8-1's specified interaction, not an accident of the arithmetic.
     */
    @GameTest(template = "empty")
    public static void aLevelEarnedUnderM7BuysAnotherSocket(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "iron", "wood", "wood");

        // Two sockets plus one unrelated modifier spends all three starting slots.
        ItemStack spent = apply(helper, apply(helper, pickaxe, sigil(2)),
                new ItemStack(Items.OBSIDIAN, 1));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(spent) == 0,
                "setup: the three starting slots must all be spent, " + ForgeweaveModifiers.freeSlots(spent)
                        + " left");

        ModifierApplication.Outcome refused = ModifierApplication
                .resolve(helper.getLevel().registryAccess(), spent, sigil(1), ItemStack.EMPTY)
                .orElseThrow(() -> new AssertionError("a loaded sigil must be recognised"));
        helper.assertTrue(refused.output().isEmpty(),
                "a third socket must be refused with no slot left to pay for it");
        helper.assertTrue(refused.rejection() != null, "and must say why");

        // One M7 level grants one slot (D-M7-1), which the third socket then spends.
        ItemStack levelled = spent.copy();
        levelled.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(1, 0, 1));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(levelled) == 1,
                "setup: one level is one slot, got " + ForgeweaveModifiers.freeSlots(levelled));

        ItemStack third = apply(helper, levelled, sigil(1));
        helper.assertTrue(ApotheosisSockets.socketCount(third) == 3,
                "the earned slot must buy the third socket, got " + ApotheosisSockets.socketCount(third));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(third) == 0,
                "and spend it: " + ForgeweaveModifiers.freeSlots(third) + " left");
        helper.succeed();
    }

    /**
     * Acceptance test 2, the Forgeweave half: the station's gem recipe reaches the seating action,
     * the gem lands in the socket component, and one gem is spent. What the gem then <em>grants</em>
     * needs a live Apotheosis and is a checklist line.
     */
    @GameTest(template = "empty")
    public static void theStationSeatsAGemIntoTheSocketComponent(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack socketed = apply(helper,
                ToolAssembly.pickaxe(helper, player, POS, "iron", "wood", "wood"), sigil(2));

        withBridge(new FakeBridge(), () -> {
            ModifierApplication.Outcome outcome = ModifierApplication
                    .resolve(helper.getLevel().registryAccess(), socketed, gem(), ItemStack.EMPTY)
                    .orElseThrow(() -> new AssertionError("a gem beside a socketed tool must be recognised"));
            helper.assertFalse(outcome.output().isEmpty(),
                    "the station must seat it: " + outcome.rejection());
            helper.assertTrue(outcome.firstUsed() == 1,
                    "exactly one gem is spent, got " + outcome.firstUsed());

            List<ItemStack> gems = ApotheosisSockets.gems(outcome.output());
            helper.assertTrue(gems.get(0).is(Items.NAUTILUS_SHELL),
                    "the first socket holds the gem, got " + gems.get(0));
            helper.assertTrue(gems.get(1).isEmpty(), "the second socket is untouched");
            helper.assertTrue(ForgeweaveModifiers.freeSlots(outcome.output())
                            == ForgeweaveModifiers.freeSlots(socketed),
                    "seating a gem costs no further slot -- the socket already paid for it");
        });
        helper.succeed();
    }

    /**
     * Acceptance test 9 for this integration (D-M8-5's off path, JC-C): with the toggle off the
     * station refuses both recipes, and a stack that already carries sockets and gems keeps its
     * component untouched and inert -- then works again when the toggle comes back, with nothing
     * lost. The toggle itself is issue #968's; what this pins is
     * {@link ApotheosisSockets#enabled()}'s two consequences, which is the half that lives here.
     */
    @GameTest(template = "empty")
    public static void withNoBridgeASocketedStackKeepsItsComponentInert(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack socketed = apply(helper,
                ToolAssembly.pickaxe(helper, player, POS, "iron", "wood", "wood"), sigil(1));

        ItemStack seated = withBridgeGet(new FakeBridge(), () -> ModifierApplication
                .resolve(helper.getLevel().registryAccess(), socketed, gem(), ItemStack.EMPTY)
                .map(ModifierApplication.Outcome::output)
                .orElse(ItemStack.EMPTY));
        helper.assertFalse(seated.isEmpty(), "setup: the gem must seat while the bridge is installed");

        ItemContainerContents stored = seated.get(ForgeweaveDataComponents.SOCKETS.get());
        helper.assertTrue(stored != null, "setup: the socket component must be there");

        // No bridge is what an install with the integration off -- or with Apotheosis absent -- has.
        helper.assertFalse(ApotheosisSockets.active(), "no bridge must be installed by default");
        helper.assertTrue(stored.equals(seated.get(ForgeweaveDataComponents.SOCKETS.get())),
                "the component is untouched with the integration off");
        helper.assertTrue(ApotheosisSockets.socketCount(seated) == 1,
                "and the socket is still counted, so its modifier slot stays spent");
        helper.assertTrue(ApotheosisSockets.attackDamageBonus(seated) == 0.0F,
                "but the gem grants nothing: " + ApotheosisSockets.attackDamageBonus(seated));
        helper.assertTrue(ModifierApplication
                        .resolve(helper.getLevel().registryAccess(), seated, gem(), ItemStack.EMPTY)
                        .map(outcome -> outcome.output().isEmpty())
                        .orElse(true),
                "and no further gem seats while the integration is off");

        withBridge(new FakeBridge(), () -> helper.assertTrue(
                ApotheosisSockets.attackDamageBonus(seated) == 2.0F,
                "turning it back on must restore the bonus with nothing lost, got "
                        + ApotheosisSockets.attackDamageBonus(seated)));
        helper.succeed();
    }

    // ---------------------------------------------------------------- helpers

    /** {@code reagent} applied to {@code tool} at the station, asserting it actually landed. */
    private static ItemStack apply(GameTestHelper helper, ItemStack tool, ItemStack reagent) {
        ModifierApplication.Outcome outcome = ModifierApplication
                .resolve(helper.getLevel().registryAccess(), tool, reagent, ItemStack.EMPTY)
                .orElseThrow(() -> new AssertionError("the station did not recognise " + reagent));
        if (outcome.output().isEmpty()) {
            throw new AssertionError("the station refused " + reagent + ": " + outcome.rejection());
        }
        return outcome.output();
    }

    /** See {@code DraconicModuleEffectGameTests#withBridge}: one static field for the whole game. */
    private static void withBridge(ApotheosisSockets.Bridge bridge, Runnable body) {
        ApotheosisSockets.install(bridge);
        try {
            body.run();
        } finally {
            ApotheosisSockets.install(null);
        }
    }

    private static ItemStack withBridgeGet(ApotheosisSockets.Bridge bridge, Supplier<ItemStack> body) {
        ApotheosisSockets.install(bridge);
        try {
            return body.get();
        } finally {
            ApotheosisSockets.install(null);
        }
    }

    /** Seats anything and grants a flat +2 attack damage, so "the bonus is back" is observable. */
    private static final class FakeBridge implements ApotheosisSockets.Bridge {

        @Override
        public boolean canSeat(ItemStack target, ItemStack gem) {
            return true;
        }

        @Override
        public List<ApotheosisSockets.AttributeGrant> attributeGrants(ItemStack stack) {
            return List.of(new ApotheosisSockets.AttributeGrant(
                    ResourceLocation.withDefaultNamespace("generic.attack_damage"),
                    new AttributeModifier(
                            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "gametest_gem"),
                            2.0, AttributeModifier.Operation.ADD_VALUE)));
        }

        @Override
        public float durabilityFraction(ItemStack stack) {
            return 0.0F;
        }

        @Override
        public float protection(ItemStack stack, DamageSource source) {
            return 0.0F;
        }

        @Override
        public float reduceDamage(ItemStack stack, DamageSource source, LivingEntity defender, float amount) {
            return amount;
        }

        @Override
        public void afterAttack(ItemStack stack, LivingEntity attacker, @Nullable Entity target) {}

        @Override
        public void afterHurt(ItemStack stack, LivingEntity defender, DamageSource source) {}
    }

    private ApotheosisSocketGameTests() {}
}
