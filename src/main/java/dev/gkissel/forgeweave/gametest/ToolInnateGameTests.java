package dev.gkissel.forgeweave.gametest;

import com.mojang.authlib.GameProfile;

import java.util.UUID;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * docs/SCOPE.md M3 issue #164's verification: one GameTest per M1 tool innate (pickaxe pierce, shovel
 * flatten, hatchet sunder), each assembled through a real Tool Station ({@link ToolAssembly}) so the
 * innate's attachment through {@code ForgeweaveInnates#collect} (registered as a
 * {@code CombatSeams.Provider} in {@code Forgeweave}, same idiom as materials' traits) is part of what
 * is under test, not just the behavior class in isolation.
 *
 * <p>Magnitudes are the maintainer decision recorded on the issue (2026-08-12): pierce 1.0 flat
 * armor-ignoring damage, flatten Slowness I for 1.5s (30 ticks), sunder 20% bonus damage vs a blocking
 * target plus the vanilla-axe shield disable.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ToolInnateGameTests {

    /**
     * Pierce: a stone-headed pickaxe and a stone-headed shovel deal identical mitigated damage to a
     * heavily armored (netherite-set-equivalent: 20 armor, 12 toughness -- set on the attribute rather
     * than by equipping a chestplate, since equipment only reaches the attribute on the entity's next
     * tick, matching {@code TraitGameTests#hit}) target except for pierce's flat, unmitigated 1.0 --
     * isolating the innate the same way {@code TraitGameTests#crudeAddsDamageOnlyAgainstUnarmoredTargets}
     * isolates a trait: same materials (stone head, wood binding/handle -- neither trait touches
     * damage), same fixed pre-mitigation hit, only the tool type differs.
     */
    @GameTest(template = "empty")
    public static void pierceDealsFlatDamageThroughHeavyArmor(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "stone", "wood", "wood");
        ItemStack shovel = ToolAssembly.tool(helper, player, pos, ForgeweaveItems.PART_SHOVEL_HEAD.get(),
                "stone", "wood", "wood");

        float withPierce = hitHeavilyArmoredPig(helper, player, pickaxe);
        float withoutPierce = hitHeavilyArmoredPig(helper, player, shovel);

        helper.assertTrue(Math.abs(withPierce - (withoutPierce + 1.0F)) < 0.01F,
                "expected pierce to add exactly 1.0 armor-ignoring damage on top of the mitigated hit: "
                        + withPierce + " with it vs " + withoutPierce + " without");
        helper.succeed();
    }

    /** Hits a heavily armored pig with {@code tool} and returns how much health it lost. */
    private static float hitHeavilyArmoredPig(GameTestHelper helper, Player player, ItemStack tool) {
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.getAttribute(Attributes.ARMOR).setBaseValue(20.0);
        pig.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(12.0);
        player.setItemInHand(InteractionHand.MAIN_HAND, tool);

        float before = pig.getHealth();
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        helper.assertTrue(source.getWeaponItem() == tool, "the attack must be attributed to the tool being tested");
        pig.hurt(source, 4.0F);
        float lost = before - pig.getHealth();

        pig.discard();
        return lost;
    }

    /** Flatten: a hit from the shovel applies Slowness I for exactly the 30 ticks (1.5s) decided on the issue. */
    @GameTest(template = "empty")
    public static void flattenAppliesSlownessOnHit(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack shovel = ToolAssembly.tool(helper, player, pos, ForgeweaveItems.PART_SHOVEL_HEAD.get(),
                "stone", "wood", "wood");
        player.setItemInHand(InteractionHand.MAIN_HAND, shovel);

        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        helper.assertTrue(!pig.hasEffect(MobEffects.MOVEMENT_SLOWDOWN),
                "the pig must start without slowness, or this proves nothing");

        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        helper.assertTrue(source.getWeaponItem() == shovel, "the attack must be attributed to the shovel");
        pig.hurt(source, 1.0F);

        MobEffectInstance slow = pig.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        helper.assertTrue(slow != null, "flatten should apply Slowness on hit");
        helper.assertTrue(slow.getAmplifier() == 0, "expected Slowness I (amplifier 0), got " + slow.getAmplifier());
        helper.assertTrue(slow.getDuration() == 30, "expected 30 ticks (1.5s) of slowness, got " + slow.getDuration());

        pig.discard();
        helper.succeed();
    }

    /**
     * Sunder, both halves:
     *
     * <ul>
     *   <li>bonus damage vs blocking -- exercised directly on {@link ForgeweaveInnates#SUNDER}
     *       ({@code CombatSeams.seams} is public for exactly this kind of assertion, per its javadoc),
     *       toggling the same target's blocking state, since a real blow against an actively blocking
     *       target has its damage absorbed by the shield block itself before it ever reaches the
     *       target's health -- the bonus has to be read off the seam's own adjustment, not off health
     *       lost.
     *   <li>shield disable -- a real blow against an actively blocking {@link Player} (the vanilla-axe
     *       mechanic is {@code Player}-specific: {@code Player#blockUsingShield} is what consults
     *       {@code canDisableShield}; a generic {@code LivingEntity}/{@code Mob} blocker never does),
     *       facing the attacker so vanilla's own blocked-direction check passes, asserting the shield
     *       stops being used and lands on vanilla's 100-tick cooldown.
     * </ul>
     *
     * <p>The blocker is built inline (rather than through a helper returning {@code Player}) so its one
     * extra method stays reachable: {@code var} keeps the anonymous subclass's own type at the call
     * site, which a {@code Player}-typed return value would erase.
     */
    @GameTest(template = "empty")
    public static void sunderAddsBonusDamageAndDisablesAnActiveShield(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player attacker = helper.makeMockPlayer(GameType.SURVIVAL);
        // A Tool Station, not a Tool Forge: issue #157 replaced #152's synthetic "the hatchet is a
        // large tool" fixture with the five real large tools, so the hatchet builds at the station again.
        ItemStack hatchet = ToolAssembly.tool(helper, attacker, pos, ForgeweaveItems.PART_AXE_HEAD.get(),
                "stone", "wood", "wood");
        attacker.setItemInHand(InteractionHand.MAIN_HAND, hatchet);

        // A mock Player, same idiom as GameTestHelper#makeMockPlayer -- but built here, not through it,
        // so isBlocking()'s "held long enough" check can be forced without waiting on real server ticks
        // (a mock player is never added to the level, so nothing ticks it): LivingEntity#isBlocking()
        // needs getUseDuration() - useItemRemaining >= 5, and useItemRemaining is a protected field only
        // code inside a Player subclass may touch -- hence the extra method living on the subclass
        // itself.
        var blocker = new Player(helper.getLevel(), BlockPos.ZERO, 0.0F,
                new GameProfile(UUID.randomUUID(), "forgeweave-sunder-test-blocker")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return false;
            }

            @Override
            public boolean isLocalPlayer() {
                return true;
            }

            void forceHeldLongEnoughToBlock() {
                this.useItemRemaining -= 10;
            }
        };
        blocker.setPos(4.5, 2.0, 4.5);
        blocker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));

        DamageSource source = helper.getLevel().damageSources().playerAttack(attacker);
        CombatHit hit = new CombatHit(helper.getLevel(), hatchet, attacker, blocker, source);

        float notBlocking = ForgeweaveInnates.SUNDER.preHit(hit, 4.0F, 4.0F);
        helper.assertTrue(Math.abs(notBlocking - 4.0F) < 0.001F,
                "no bonus should apply while not blocking, got " + notBlocking);

        blocker.startUsingItem(InteractionHand.MAIN_HAND);
        blocker.forceHeldLongEnoughToBlock();
        helper.assertTrue(blocker.isBlocking(), "test setup should have produced an actively blocking target");
        float blocking = ForgeweaveInnates.SUNDER.preHit(hit, 4.0F, 4.0F);
        helper.assertTrue(Math.abs(blocking - 4.8F) < 0.001F,
                "sunder should add 20% bonus damage vs a blocking target, got " + blocking);

        blocker.lookAt(EntityAnchorArgument.Anchor.EYES, attacker.position());
        blocker.hurt(source, 4.0F);
        helper.assertTrue(!blocker.isUsingItem(), "an axe/hatchet hit should force the shield down");
        helper.assertTrue(blocker.getCooldowns().isOnCooldown(Items.SHIELD),
                "expected the vanilla-axe shield-disable cooldown to be applied");

        helper.succeed();
    }
}
