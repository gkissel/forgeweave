package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.AttackSlash;
import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #584 (parity audit T51): the seven weapons upstream 1.12 spawns a full-charge attack slash
 * from each reach {@code AttackSlash} through the real {@code CombatSeams} pipeline, and the arc a
 * landed full-charge blow draws actually spawns.
 *
 * <p>The particle itself is unobservable server side -- NeoForge fires no event for
 * {@code ServerLevel#sendParticles}, the precedent {@code SoundCapture}'s javadoc already records for
 * issue #415 -- so what is pinned here is everything around it that can silently rot: which weapons
 * carry the seam (a weapon left off {@code AttackSlash#collect} is a slash that simply never
 * appears), which arc and height factor each one carries, and that firing the seam resolves its
 * {@code DeferredHolder} rather than throwing on an unbound one, which is the crash a registration
 * mistake would produce at the first charged hit rather than at startup.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class AttackSlashGameTests {

    /** Upstream's eight {@code spawnAttackParticle} sites minus the pan's launch, weapon by weapon. */
    private static Map<Item, AttackSlash> upstreamSites() {
        return Map.of(
                ForgeweaveItems.TOOL_CLEAVER.get(), AttackSlash.CLEAVER,
                ForgeweaveItems.TOOL_LONGSWORD.get(), AttackSlash.LONGSWORD,
                ForgeweaveItems.TOOL_RAPIER.get(), AttackSlash.RAPIER,
                ForgeweaveItems.TOOL_FRYING_PAN.get(), AttackSlash.FRYING_PAN,
                ForgeweaveItems.TOOL_HAMMER.get(), AttackSlash.HAMMER,
                ForgeweaveItems.TOOL_HATCHET.get(), AttackSlash.HATCHET,
                ForgeweaveItems.TOOL_LUMBERAXE.get(), AttackSlash.LUMBERAXE);
    }

    /** Weapons upstream draws no slash for -- the audit's own count of seven, from the other side. */
    private static List<Item> weaponsWithoutASlash() {
        return List.of(ForgeweaveItems.TOOL_BROADSWORD.get(), ForgeweaveItems.TOOL_BATTLEAXE.get(),
                ForgeweaveItems.TOOL_KATANA.get(), ForgeweaveItems.TOOL_DAGGER.get(),
                ForgeweaveItems.TOOL_BATTLESIGN.get(), ForgeweaveItems.TOOL_SCIMITAR.get(),
                ForgeweaveItems.TOOL_EXCAVATOR.get(), ForgeweaveItems.TOOL_PICKAXE.get());
    }

    @GameTest(template = "empty")
    public static void everyUpstreamWeaponResolvesItsOwnSlashThroughThePipeline(GameTestHelper helper) {
        upstreamSites().forEach((item, expected) -> {
            List<AttackSlash> resolved = slashesOf(new ItemStack(item));
            helper.assertTrue(resolved.equals(List.of(expected)),
                    "expected " + item + " to carry exactly " + expected + " through CombatSeams, got "
                            + resolved);
        });
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void weaponsUpstreamDrawsNoSlashForCarryNone(GameTestHelper helper) {
        for (Item item : weaponsWithoutASlash()) {
            List<AttackSlash> resolved = slashesOf(new ItemStack(item));
            helper.assertTrue(resolved.isEmpty(),
                    "upstream draws no attack slash for " + item + ", but it carries " + resolved);
        }
        helper.succeed();
    }

    /**
     * The gate and the spawn together: a full-charge blow from a player fires the seam and its
     * particle type resolves; a half-charge blow and a non-player swinger are both refused, which is
     * upstream's {@code readyForSpecialAttack} ({@code player instanceof EntityPlayer} <em>and</em>
     * {@code getCooledAttackStrength(0.5f) > 0.9f}) field for field.
     */
    @GameTest(template = "empty")
    public static void onlyAFullChargePlayerSwingDrawsTheArc(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        DamageSource source = helper.getLevel().damageSources().playerAttack(player);
        ItemStack cleaver = new ItemStack(ForgeweaveItems.TOOL_CLEAVER.get());

        // Full charge, player swinger: spawns. A missing particle registration throws here.
        AttackSlash.CLEAVER.onHit(
                new CombatHit(helper.getLevel(), cleaver, player, pig, source, 1.0F), 1.0F);

        // Half charge, and a swing with no living attacker at all: both refused, so neither can throw
        // even if the type were unbound. Asserting "no exception" is all a server can see of either.
        AttackSlash.CLEAVER.onHit(
                new CombatHit(helper.getLevel(), cleaver, player, pig, source, 0.5F), 1.0F);
        AttackSlash.CLEAVER.onHit(
                new CombatHit(helper.getLevel(), cleaver, null, pig, source, 1.0F), 1.0F);

        helper.succeed();
    }

    private static List<AttackSlash> slashesOf(ItemStack weapon) {
        return CombatSeams.seams(weapon).stream()
                .filter(AttackSlash.class::isInstance)
                .map(AttackSlash.class::cast)
                .toList();
    }
}
