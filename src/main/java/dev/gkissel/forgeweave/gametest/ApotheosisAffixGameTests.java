package dev.gkissel.forgeweave.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.apotheosis.ApotheosisAffixes;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.tool.ToolLevel;

/**
 * Issue #970 (docs/SCOPE.md M8, D-M8-1 and D-M8-5), the Forgeweave side: the two compat toggles and
 * their off paths, and what an enchantment does and does not cost a tool that carries one.
 *
 * <p>Neither Apotheosis nor Apothic Enchanting is on the gametest classpath (build.gradle, JC-B),
 * and that is not a gap these tests paper over -- it is half of what they check. Both toggles sit
 * behind a mod-absence guard on purpose, so that a toggle for a bridge nobody installed never moves
 * base gameplay, and here that guard is live. So the pair is tested as a pair:
 * {@code affixesConfigured}/{@code enchantingConfigured} read the toggle and are driven directly,
 * while {@code affixesEnabled}/{@code enchantingEnabled} are asserted to stay on regardless, which is
 * the guard. What a real affix rolling on a real loot chest does is a manual release-checklist line.
 *
 * <p>Foreign affix state is stood in for by {@code minecraft:custom_data}, the same call
 * {@code m8_affixed.snbt} makes and for the same reason: Apotheosis' own component types are not in
 * any registry this classpath can reach. What the round trip pins is Forgeweave's rule, that it owns
 * its components and carries everything else through untouched (JC-D), not Apotheosis' serialization.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class ApotheosisAffixGameTests {

    private static final BlockPos POS = new BlockPos(1, 1, 1);

    /** Affix-shaped foreign state: three keys under one component Forgeweave never reads or writes. */
    private static CustomData foreignAffixes() {
        CompoundTag tag = new CompoundTag();
        CompoundTag affixes = new CompoundTag();
        affixes.putFloat("apotheosis:cleaving", 0.62F);
        affixes.putFloat("apotheosis:sundering", 0.31F);
        tag.put("apotheosis:affixes", affixes);
        tag.putString("apotheosis:rarity", "apotheosis:rare");
        tag.putString("apotheosis:affix_name", "Cleaving Pickaxe of Sundering");
        return CustomData.of(tag);
    }

    /**
     * {@code apotheosisAffixes} defaults on, reads back off, and stays inert without Apotheosis. The
     * post-restore beat is D-M8-5's own contract: the toggle comes back with no reload.
     */
    @GameTest(template = "empty")
    public static void theAffixToggleReadsBackAndStaysInertWithoutApotheosis(GameTestHelper helper) {
        helper.assertTrue(ApotheosisAffixes.affixesConfigured(),
                "apotheosisAffixes must default to on");

        ForgeweaveConfig.APOTHEOSIS_AFFIXES.set(false);
        try {
            helper.assertTrue(!ApotheosisAffixes.affixesConfigured(),
                    "the toggle must read back off once set");
            helper.assertTrue(ApotheosisAffixes.affixesEnabled(),
                    "and must still change nothing with Apotheosis absent, because a toggle for a "
                            + "bridge nobody installed has no business moving a Forgeweave-only pack's loot");
        } finally {
            ForgeweaveConfig.APOTHEOSIS_AFFIXES.set(true);
        }

        helper.assertTrue(ApotheosisAffixes.affixesConfigured() && ApotheosisAffixes.affixesEnabled(),
                "turning it back on must restore it with no reload");
        helper.succeed();
    }

    /**
     * The same for {@code apotheosisEnchanting}, plus the consequence that matters: with the toggle
     * off and {@code allowVanillaEnchanting} on, a Forgeweave tool is still enchantable here, because
     * the mod that owns the table is absent. That is the whole reason the guard exists.
     */
    @GameTest(template = "empty")
    public static void theEnchantingToggleReadsBackAndStaysInertWithoutApothicEnchanting(GameTestHelper helper) {
        helper.assertTrue(ApotheosisAffixes.enchantingConfigured(),
                "apotheosisEnchanting must default to on");

        ItemStack pickaxe = ForgeweaveItems.TOOL_PICKAXE.get().getDefaultInstance();
        ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(true);
        ForgeweaveConfig.APOTHEOSIS_ENCHANTING.set(false);
        try {
            helper.assertTrue(!ApotheosisAffixes.enchantingConfigured(),
                    "the toggle must read back off once set");
            helper.assertTrue(ApotheosisAffixes.enchantingEnabled(),
                    "and stay inert with Apothic Enchanting absent: there is no Apotheosis table here "
                            + "to refuse the tool at");
            helper.assertTrue(pickaxe.isEnchantable(),
                    "so allowVanillaEnchanting alone still decides the vanilla table, unchanged");
        } finally {
            ForgeweaveConfig.APOTHEOSIS_ENCHANTING.set(true);
            ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.set(false);
        }

        helper.assertTrue(ApotheosisAffixes.enchantingConfigured(),
                "turning it back on must restore it with no reload");
        helper.assertTrue(!pickaxe.isEnchantable(),
                "and the gameplay flag must be back at its own default of off");
        helper.succeed();
    }

    /**
     * An enchanted tool still takes a modifier and still levels, and the enchantment costs no
     * modifier slot. D-M8-1 states this rather than leaving it to be discovered: an enchantment and a
     * modifier are different currencies and never compete.
     */
    @GameTest(template = "empty")
    public static void anEnchantmentCostsNoModifierSlotAndBlocksNothing(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "iron", "wood", "wood");

        int before = ForgeweaveModifiers.freeSlots(pickaxe);
        helper.assertTrue(before == ForgeweaveModifiers.DEFAULT_SLOTS,
                "setup: a fresh pickaxe starts with " + ForgeweaveModifiers.DEFAULT_SLOTS
                        + " slots, got " + before);

        ItemStack enchanted = pickaxe.copy();
        enchanted.enchant(helper.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY), 3);

        helper.assertTrue(ForgeweaveModifiers.freeSlots(enchanted) == before,
                "an enchantment must cost no modifier slot: expected " + before + ", got "
                        + ForgeweaveModifiers.freeSlots(enchanted));

        ModifierApplication.Outcome outcome = ModifierApplication
                .resolve(helper.getLevel().registryAccess(), enchanted, new ItemStack(Items.OBSIDIAN, 1),
                        ItemStack.EMPTY)
                .orElseThrow(() -> new AssertionError("the station must still recognise obsidian"));
        helper.assertTrue(!outcome.output().isEmpty(),
                "an enchanted tool must still take a modifier: " + outcome.rejection());

        ItemStack modified = outcome.output();
        helper.assertTrue(!modified.getEnchantments().isEmpty(),
                "and must keep its enchantment through the station");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(modified) == before - 1,
                "the modifier spends its own slot and only its own: expected " + (before - 1)
                        + ", got " + ForgeweaveModifiers.freeSlots(modified));

        // Still levels: one M7 level grants one slot on top of whatever the enchantment did not take.
        ItemStack levelled = modified.copy();
        levelled.set(ForgeweaveDataComponents.TOOL_LEVEL.get(), new ToolLevel(1, 0, 1));
        helper.assertTrue(ForgeweaveModifiers.freeSlots(levelled) == before,
                "and an enchanted tool still levels into a fresh slot: expected " + before + ", got "
                        + ForgeweaveModifiers.freeSlots(levelled));
        helper.succeed();
    }

    /**
     * The stateful half of D-M8-5 for both toggles at once: a tool carrying foreign affix-shaped state
     * keeps every component with both toggles off, still takes a modifier, and comes back unchanged.
     * Forgeweave never reads or writes that state (JC-D), so this is the property that has to hold.
     */
    @GameTest(template = "empty")
    public static void aToolCarryingForeignAffixStateSurvivesBothTogglesOff(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, POS, "iron", "wood", "wood");
        pickaxe.set(DataComponents.CUSTOM_DATA, foreignAffixes());

        CustomData expected = foreignAffixes();
        ForgeweaveConfig.APOTHEOSIS_AFFIXES.set(false);
        ForgeweaveConfig.APOTHEOSIS_ENCHANTING.set(false);
        try {
            helper.assertTrue(expected.equals(pickaxe.get(DataComponents.CUSTOM_DATA)),
                    "foreign affix state must be untouched by either toggle");
            helper.assertTrue(pickaxe.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()) != null
                            && pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get()) != null,
                    "and must not have displaced Forgeweave's own components");

            ModifierApplication.Outcome outcome = ModifierApplication
                    .resolve(helper.getLevel().registryAccess(), pickaxe, new ItemStack(Items.OBSIDIAN, 1),
                            ItemStack.EMPTY)
                    .orElseThrow(() -> new AssertionError("the station must still recognise obsidian"));
            helper.assertTrue(!outcome.output().isEmpty(),
                    "an affixed tool must still take a modifier with the bridges off: " + outcome.rejection());
            helper.assertTrue(expected.equals(outcome.output().get(DataComponents.CUSTOM_DATA)),
                    "and the station must carry the foreign state across the rebuild");
        } finally {
            ForgeweaveConfig.APOTHEOSIS_AFFIXES.set(true);
            ForgeweaveConfig.APOTHEOSIS_ENCHANTING.set(true);
        }

        helper.assertTrue(expected.equals(pickaxe.get(DataComponents.CUSTOM_DATA)),
                "and it is all still there when the toggles come back");
        helper.succeed();
    }

    // Why the ranged family needs the loot-category override, and why no test here asserts it:
    // javac already does. Apotheosis' bow category tests `instanceof` vanilla's BowItem and
    // CrossbowItem, and an `instanceof` against either one from a Forgeweave bow is a compile error
    // ("incompatible types: dev.gkissel.forgeweave.item.BowItem cannot be converted to
    // net.minecraft.world.item.BowItem"), because the two hierarchies are disjoint. That is a
    // stronger guarantee than a runtime assertion, so the only thing left worth pinning is that the
    // override file still names exactly those three items, which ApotheosisAffixTest does.
}
