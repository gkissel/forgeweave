package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.TraitDefinition;

/**
 * Issue #832 (ADR-0004 item 3, traits only): a trait a datapack defines reaches a real tool and
 * fires. The gametest-only datapack (see src/gametest/resources/README.md) ships {@code
 * gametest_pack_poison} -- {@code effect_on_hit} at Poison <b>II</b>, one amplifier above the
 * built-in {@code poisonous}'s Poison I, so the effect on the target can only have come from the
 * pack's own parameters -- and {@code gametest_pack_absent}, the same shape gated on {@code
 * neoforge:mod_loaded} for a modid nothing supplies. {@code gametest_pack_material} names both, and
 * the tool is assembled at a real Tool Station from that material ({@link ToolAssembly}), so the
 * whole material -&gt; trait id -&gt; behaviour path is the one a player's pack would take.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class DatapackTraitGameTests {

    private static final ResourceLocation PACK_POISON = id("gametest_pack_poison");
    private static final ResourceLocation PACK_ABSENT = id("gametest_pack_absent");
    private static final String PACK_MATERIAL = "gametest_pack_material";

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    @GameTest(template = "empty")
    public static void aPackDefinedTraitReachesAnAssembledToolAndFires(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1),
                PACK_MATERIAL, PACK_MATERIAL, PACK_MATERIAL);
        helper.assertFalse(pickaxe.isEmpty(), "expected the Tool Station to assemble a pickaxe from " + PACK_MATERIAL);

        List<ResourceLocation> ids = pickaxe.get(ForgeweaveDataComponents.TRAITS.get());
        helper.assertTrue(ids != null && ids.contains(PACK_POISON),
                "expected the assembled tool's trait list to carry the pack-defined id, got " + ids);
        helper.assertTrue(ForgeweaveTraits.lookup(PACK_POISON) != null,
                "expected the datapack trait definition to resolve to a behaviour after data load");

        player.setItemInHand(InteractionHand.MAIN_HAND, pickaxe);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.setNoAi(true);
        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);

        MobEffectInstance poison = pig.getEffect(MobEffects.POISON);
        helper.assertTrue(poison != null && poison.getAmplifier() == 1,
                "a landed hit with the pack-defined trait must leave Poison II on the target, got " + poison);
        helper.assertTrue(poison.getDuration() <= 100 && poison.getDuration() > 90,
                "expected the pack's 100-tick duration, got " + poison.getDuration());

        pig.discard();
        helper.succeed();
    }

    /** The negative half: a definition whose {@code neoforge:conditions} fail is not registered anywhere. */
    @GameTest(template = "empty")
    public static void aDefinitionFailingItsConditionsNeverRegisters(GameTestHelper helper) {
        Registry<TraitDefinition> definitions = helper.getLevel().registryAccess().registryOrThrow(TraitDefinition.REGISTRY);
        helper.assertTrue(definitions.get(PACK_ABSENT) == null,
                "expected the mod_loaded-gated definition to be absent from the registry");
        helper.assertTrue(definitions.get(PACK_POISON) != null,
                "expected the unconditioned definition to be present in the registry");
        helper.assertTrue(ForgeweaveTraits.lookup(PACK_ABSENT) == null,
                "expected no behaviour behind the mod_loaded-gated definition's id");

        // The material still names the absent id; the tool carries it inertly, like any id no
        // version of Forgeweave implements (ForgeweaveTraits#of's unknown-id rule).
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1),
                PACK_MATERIAL, PACK_MATERIAL, PACK_MATERIAL);
        helper.assertTrue(ForgeweaveTraits.of(pickaxe).size() == 1,
                "expected exactly the one live pack trait on the tool, got " + ForgeweaveTraits.of(pickaxe));
        helper.succeed();
    }

    private DatapackTraitGameTests() {}
}
