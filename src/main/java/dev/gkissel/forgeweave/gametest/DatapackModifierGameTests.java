package dev.gkissel.forgeweave.gametest;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.modifier.ModifierDefinition;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * Issue #973 (ADR-0004 item 3's modifier half): a modifier a datapack defines is applied at a real
 * Tool Station and fires. The gametest-only datapack (see src/gametest/resources/README.md) ships
 * {@code gametest_pack_chill} -- {@code effect_on_hit} leaving Blindness, which no shipped modifier
 * grants, so the effect on the target can only have come from the pack's own parameters -- with its
 * own {@code modifier_recipe} on a sea pickle, and {@code gametest_pack_absent}, the same definition
 * gated on {@code neoforge:mod_loaded} for a modid nothing supplies. The tool is assembled from real
 * materials and modified through {@link ModifierApplication}, so the whole
 * definition -&gt; recipe -&gt; entry -&gt; behavior path is the one a player's pack would take.
 *
 * <p>{@code DatapackTraitGameTests}' shape, one registry over.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class DatapackModifierGameTests {

    /** Exposed so {@code CompatToggleGameTests} can flip the toggle against the same definition. */
    public static final ResourceLocation PACK_CHILL = id("gametest_pack_chill");

    private static final ResourceLocation PACK_ABSENT = id("gametest_pack_absent");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    @GameTest(template = "empty")
    public static void aPackDefinedModifierIsAppliedAtTheStationAndFires(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack chilled = chill(helper, ToolAssembly.pickaxe(helper, player, new BlockPos(1, 1, 1),
                "iron", "wood", "wood"));
        helper.assertFalse(chilled.isEmpty(), "expected the Tool Station to apply " + PACK_CHILL);

        ModifierEntry entry = ForgeweaveModifiers.entry(chilled, PACK_CHILL);
        helper.assertTrue(entry != null && entry.level() == 1,
                "expected the pack-defined modifier on the tool at level 1, got " + entry);
        helper.assertTrue(ForgeweaveModifiers.get(PACK_CHILL) != null,
                "expected the datapack modifier definition to resolve to a behavior after data load");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(chilled) == ForgeweaveModifiers.DEFAULT_SLOTS - 1,
                "and to have spent one modifier slot, got " + ForgeweaveModifiers.freeSlots(chilled));

        player.setItemInHand(InteractionHand.MAIN_HAND, chilled);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(2, 2, 2));
        pig.setNoAi(true);
        pig.hurt(helper.getLevel().damageSources().playerAttack(player), 1.0F);

        MobEffectInstance blindness = pig.getEffect(MobEffects.BLINDNESS);
        helper.assertTrue(blindness != null,
                "a landed hit with the pack-defined modifier must leave Blindness on the target");
        helper.assertTrue(blindness.getDuration() <= 100 && blindness.getDuration() > 90,
                "expected the pack's 100-tick duration, got " + blindness.getDuration());

        pig.discard();
        helper.succeed();
    }

    /** The negative half: a definition whose {@code neoforge:conditions} fail is not registered anywhere. */
    @GameTest(template = "empty")
    public static void aDefinitionFailingItsConditionsNeverRegisters(GameTestHelper helper) {
        Registry<ModifierDefinition> definitions =
                helper.getLevel().registryAccess().registryOrThrow(ModifierDefinition.REGISTRY);
        helper.assertTrue(definitions.get(PACK_ABSENT) == null,
                "expected the mod_loaded-gated definition to be absent from the registry");
        helper.assertTrue(definitions.get(PACK_CHILL) != null,
                "expected the unconditioned definition to be present in the registry");
        helper.assertTrue(ForgeweaveModifiers.get(PACK_ABSENT) == null,
                "expected no behavior behind the mod_loaded-gated definition's id");
        helper.assertFalse(ForgeweaveModifiers.ids().contains(PACK_CHILL),
                "a datapack definition is never a built-in id, so it stays out of the lang-coverage roster");
        helper.succeed();
    }

    /**
     * Applies the pack's own recipe ({@code modifier_recipe/gametest_pack_chill.json}, a sea pickle)
     * and hands back what the station made of it, empty when it refused.
     */
    static ItemStack chill(GameTestHelper helper, ItemStack tool) {
        return ModifierApplication
                .resolve(helper.getLevel().registryAccess(), tool, new ItemStack(Items.SEA_PICKLE), ItemStack.EMPTY)
                .map(ModifierApplication.Outcome::output)
                .orElse(ItemStack.EMPTY);
    }

    private DatapackModifierGameTests() {}
}
