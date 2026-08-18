package dev.gkissel.forgeweave.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #465/T34's verification: the parity audit's per-tool knockback multiplier, re-verified
 * against the pinned 1.12 clone before this shipped -- {@code Hatchet#knockback() = 1.3f}, {@code
 * Mattock#knockback() = 1.1f}, {@code LumberAxe#knockback() = 1.5f}, {@code Rapier#knockback() =
 * 0.6f}, {@code ToolCore}'s own default {@code 1.0f} for every other tool.
 *
 * <p>What the multiplier actually scales turned out not to be what a first reading of upstream
 * suggests: not some special "attack knockback attribute" push, but the flat {@code 0.4f} knockback
 * {@code LivingEntity#hurt} applies to <em>every</em> successful hit from an attacking entity --
 * upstream's own {@code attackEntityFrom} carries the same unconditional per-hit push, and {@code
 * ToolHelper#attackEntity} scales exactly that delta (lines 737-740), never the separate
 * sprint/Knockback-enchant bonus {@code Player#attack} adds afterward, and never a trait/modifier's
 * own knockback (the "Knockback" combat modifier's {@code addVelocity}, upstream's own {@code
 * knockback} local at lines 743-752). {@link CombatSeam#knockback}'s javadoc has the mechanism.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class KnockbackMultiplierGameTests {

    /** The four per-tool multipliers, re-verified against the pinned 1.12 clone (issue body, T34). */
    @GameTest(template = "empty")
    public static void perToolMultipliersMatchUpstreamsFourOverrides(GameTestHelper helper) {
        CombatHit hit = trivialHit(helper);
        assertMultiplies(helper, ForgeweaveInnates.HATCHET_KNOCKBACK, hit, 1.3F, "hatchet");
        assertMultiplies(helper, ForgeweaveInnates.MATTOCK_KNOCKBACK, hit, 1.1F, "mattock");
        assertMultiplies(helper, ForgeweaveInnates.LUMBERAXE_KNOCKBACK, hit, 1.5F, "lumber axe");
        assertMultiplies(helper, ForgeweaveInnates.RAPIER_KNOCKBACK, hit, 0.6F, "rapier");
        helper.succeed();
    }

    private static void assertMultiplies(GameTestHelper helper, CombatSeam seam, CombatHit hit,
            float expectedMultiplier, String label) {
        float scaled = seam.knockback(hit, 1.0F);
        helper.assertTrue(Math.abs(scaled - expectedMultiplier) < 0.001F,
                "expected the " + label + " to multiply knockback by " + expectedMultiplier + ", got " + scaled);
    }

    private static CombatHit trivialHit(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        return new CombatHit(helper.getLevel(), new ItemStack(ForgeweaveItems.TOOL_HATCHET.get()), player, pig, source);
    }

    /**
     * The wiring, end to end: {@code ForgeweaveInnates#collect} attaches the hatchet's seam by item
     * identity, and {@code CombatSeams} reaches for it on the flat per-hit push every {@code
     * LivingEntity#hurt} call already produces -- a bare {@code pig.hurt(...)} is enough to observe it,
     * no simulated client swing needed, the same shortcut {@code CombatModifierGameTests#pushOnHit}
     * already established for the "Knockback" combat modifier's own push.
     */
    @GameTest(template = "empty")
    public static void hatchetsMultiplierScalesTheFlatKnockbackEveryHitAlreadyGets(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);

        double hatchetPush = CombatModifierGameTests.pushOnHit(helper, player, pos,
                new ItemStack(ForgeweaveItems.TOOL_HATCHET.get()));
        // The shovel: no per-tool knockback multiplier and no knockback-adjacent innate (flatten only
        // applies slowness), the same "changes nothing else" control CombatModifierGameTests uses.
        double controlPush = CombatModifierGameTests.pushOnHit(helper, player, pos,
                new ItemStack(ForgeweaveItems.TOOL_SHOVEL.get()));

        helper.assertTrue(controlPush > 0.0, "the control hit must produce some knockback, or this proves nothing");
        double ratio = hatchetPush / controlPush;
        helper.assertTrue(Math.abs(ratio - 1.3) < 0.01,
                "expected the hatchet's push to be 1.3x the control's (" + controlPush + "), got "
                        + hatchetPush + " (ratio " + ratio + ")");
        helper.succeed();
    }

    /**
     * The guard, precisely: a hatchet carrying the "Knockback" combat modifier produces exactly two
     * pushes on one hit -- the modifier's own ({@link dev.gkissel.forgeweave.combat.KnockbackOnHitSeam},
     * upstream {@code ModKnockback#calcKnockback}'s {@code 0.1f} per raw application unit, ten units
     * here is {@code 1.0f}), then vanilla's flat per-hit knockback ({@code 0.4f}). Captured in the
     * order they fire ({@link LivingKnockBackEvent} at {@link EventPriority#LOWEST}, after {@code
     * CombatSeams} has had its say), the first must land unscaled -- upstream's own separation between
     * {@code tool.knockback()} and a trait's {@code addVelocity} -- and only the second carries the
     * hatchet's 1.3x.
     */
    @GameTest(template = "empty")
    public static void hatchetsMultiplierScalesTheFlatPushButNotItsOwnKnockbackModifiersPush(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack hatchet = new ItemStack(ForgeweaveItems.TOOL_HATCHET.get());
        hatchet.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(CombatModifierGameTests.KNOCKBACK, 10)));
        player.setItemInHand(InteractionHand.MAIN_HAND, hatchet);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));

        List<Float> pushes = new ArrayList<>();
        Consumer<LivingKnockBackEvent> capture = event -> {
            if (event.getEntity() == pig) {
                pushes.add(event.getStrength());
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, LivingKnockBackEvent.class, capture);
        try {
            DamageSource source = helper.getLevel().damageSources().playerAttack(player);
            pig.hurt(source, 1.0F);
        } finally {
            NeoForge.EVENT_BUS.unregister(capture);
        }

        helper.assertTrue(pushes.size() == 2,
                "expected the Knockback modifier's own push plus vanilla's flat per-hit push, got "
                        + pushes.size() + ": " + pushes);
        helper.assertTrue(Math.abs(pushes.get(0) - 1.0F) < 0.001F,
                "the Knockback modifier's own push must land unscaled by the hatchet's per-tool multiplier, got "
                        + pushes.get(0));
        helper.assertTrue(Math.abs(pushes.get(1) - 0.52F) < 0.001F,
                "vanilla's flat 0.4 per-hit push must still be scaled by the hatchet's 1.3x multiplier, got "
                        + pushes.get(1));

        pig.discard();
        helper.succeed();
    }
}
