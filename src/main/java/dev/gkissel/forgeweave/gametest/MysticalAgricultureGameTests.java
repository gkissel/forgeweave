package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.CompatItems;
import dev.gkissel.forgeweave.compat.mysticalagriculture.EssenceTier;
import dev.gkissel.forgeweave.compat.mysticalagriculture.ForgeweaveCrop;
import dev.gkissel.forgeweave.compat.mysticalagriculture.MysticalAugments;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trackb.TrackBOre;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Issue #999 (D-M8-20, M8-15): Mystical Agriculture's crops, its metals as Track A presets, and the
 * augment seam. Mystical Agriculture is {@code compileOnly} and absent from this server (JC-B), so
 * everything here is the mod-free half of the integration:
 *
 * <ul>
 *   <li>the registration factory's absent branch, which is the one this server can observe directly,
 *       plus the flag that decides the other;
 *   <li>the per-material augment tier and slot count, which are plain functions of a stack;
 *   <li>the nine presets being absent without their own mods;
 *   <li>the toggle-off path, and a stack carrying a foreign component surviving it.
 * </ul>
 *
 * <p>What no GameTest here can reach, per the issue: a crop growing, a seed crafting, the Tinkering
 * Table opening on Forgeweave gear, and an augment installing and firing. All four need a live
 * Mystical Agriculture and are release-checklist lines on
 * <a href="https://github.com/gkissel/forgeweave/issues/975">#975</a>.
 *
 * <p>The foreign-component stand-in is {@code minecraft:custom_data} rather than
 * {@code mysticalagriculture:equipped_augments}: an unregistered component type cannot be put on a
 * stack at all, let alone decoded, so the real id is unreachable without the mod. What these tests
 * pin is the shape -- a foreign blob riding alongside Forgeweave's own components through a
 * round trip and a toggle flip -- which is the same substitution {@code m8_socketed.snbt} already
 * makes for Apotheosis gems.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class MysticalAgricultureGameTests {

    /** {@code forgeweave:reinforced}, standing in for "the tool carries a Forgeweave modifier too". */
    private static final ResourceLocation REINFORCED = id("reinforced");

    /** The nine presets #999 ships, all gated on a Mystical Agriculture or Agradditions item. */
    private static final String[] PRESET_MATERIALS = {
            "inferium", "prudentium", "tertium", "imperium", "supremium", "awakened_supremium",
            "prosperity", "soulium", "insanium",
    };

    /**
     * Both paths of the factory's branch. The absent path is asserted for real -- every tool and
     * armour item on this server is the plain class, and none of them is the compat subclass -- and
     * the flag that would have chosen otherwise is asserted to be the reason, so a factory that
     * silently stopped branching would fail here rather than pass by accident.
     */
    @GameTest(template = "empty")
    public static void theItemFactoryProducesPlainItemsWithoutMysticalAgriculture(GameTestHelper helper) {
        helper.assertTrue(!CompatItems.tinkerableItemsRegistered(),
                "Mystical Agriculture is compileOnly and must be absent from runGameTestServer");

        ToolItem pickaxe = ForgeweaveItems.TOOL_PICKAXE.get();
        helper.assertValueEqual(pickaxe.getClass(), ToolItem.class,
                "the pickaxe's class without Mystical Agriculture");
        ArmorPieceItem helmet = ForgeweaveItems.ARMOR_HELMET.get();
        helper.assertValueEqual(helmet.getClass(), ArmorPieceItem.class,
                "the helmet's class without Mystical Agriculture");
        helper.assertValueEqual(helmet.getType(), ArmorItem.Type.HELMET, "the helmet's armour slot");
        helper.succeed();
    }

    /**
     * The essence-tier map is total over the mining ladder and strictly non-decreasing, checked here
     * as well as in {@code EssenceTierTest} because the ladder is datapack-adjacent: a rung added in
     * a later milestone has to fail somewhere a full server run notices.
     */
    @GameTest(template = "empty")
    public static void theEssenceTierMapCoversEveryMiningRung(GameTestHelper helper) {
        EssenceTier previous = null;
        for (TrackBOre.Tier rung : TrackBOre.Tier.values()) {
            EssenceTier tier = EssenceTier.forOre(rung);
            helper.assertTrue(tier != null, rung + " has no essence tier");
            helper.assertTrue(previous == null || tier.ordinal() >= previous.ordinal(),
                    rung + " maps below the rung beneath it");
            previous = tier;
        }
        helper.assertValueEqual(ForgeweaveCrop.ALL.size(), 12, "the crop roster: eleven ores plus brimspar");
        helper.succeed();
    }

    /**
     * The per-material answers #999 specifies: the tier is the highest essence metal among the
     * stack's parts, the slot count is one, or two from awakened supremium up, and gear built from no
     * essence metal is not augmentable at all. These are the answers Mystical Agriculture's own
     * {@code ITinkerable} has no stack-aware method to receive -- see {@code MysticalItems} -- so
     * pinning them here is what keeps them correct for whatever seam carries them later.
     */
    @GameTest(template = "empty")
    public static void augmentTierAndSlotsComeFromTheMaterial(GameTestHelper helper) {
        helper.assertValueEqual(MysticalAugments.tierOf(tool("supremium")), Optional.of(EssenceTier.SUPREMIUM),
                "an all-supremium pickaxe's tier");
        helper.assertValueEqual(MysticalAugments.augmentSlots(tool("supremium")), 1, "supremium slots");

        helper.assertValueEqual(MysticalAugments.tierOf(tool("awakened_supremium")),
                Optional.of(EssenceTier.AWAKENED_SUPREMIUM), "an awakened pickaxe's tier");
        helper.assertValueEqual(MysticalAugments.augmentSlots(tool("awakened_supremium")), 2,
                "awakened supremium slots -- two, D-M8-20's own exception");
        helper.assertValueEqual(MysticalAugments.augmentSlots(tool("insanium")), 2,
                "insanium slots -- awakened's two continued, not a drop back to one");

        // One essence part among three is enough, and it is the part that sets the tier.
        ItemStack mixed = pickaxe();
        mixed.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(), materials("wood", "imperium", "wood"));
        helper.assertValueEqual(MysticalAugments.tierOf(mixed), Optional.of(EssenceTier.IMPERIUM),
                "one essence part is enough, and it sets the tier");

        helper.assertValueEqual(MysticalAugments.tierOf(tool("iron")), Optional.empty(),
                "an iron pickaxe is not augmentable");
        helper.assertValueEqual(MysticalAugments.augmentSlots(tool("iron")), 0, "iron slots");
        helper.succeed();
    }

    /** None of the nine presets exists without the mod that supplies its ingot. */
    @GameTest(template = "empty")
    public static void unsuppliedPresetsDoNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : PRESET_MATERIALS) {
            helper.assertTrue(materials.get(id(name)) == null,
                    "expected the " + name + " material to be absent without its own mod, found it registered");
        }
        helper.succeed();
    }

    /**
     * The toggle's off path, and D-M7-3's inert-never-destructive rule applied to it: with
     * {@code mysticalAgricultureAugments} off no stack is augmentable, and a stack carrying both its
     * own modifier components and a foreign augment blob comes through the flip with every one of
     * them intact. Nothing here writes or clears the foreign component, which is the whole point:
     * augment state is Mystical Agriculture's own and Forgeweave neither copies nor migrates it.
     */
    @GameTest(template = "empty")
    public static void theToggleOffLeavesEveryComponentAlone(GameTestHelper helper) {
        ItemStack tool = tool("awakened_supremium");
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(REINFORCED, 2)));
        CompoundTag augments = new CompoundTag();
        augments.putString("id", "mysticalagriculture:speed");
        augments.putInt("slot", 0);
        tool.set(DataComponents.CUSTOM_DATA, CustomData.of(augments));

        helper.assertValueEqual(MysticalAugments.augmentSlots(tool), 2, "slots with the toggle on");

        ForgeweaveConfig.MYSTICAL_AGRICULTURE_AUGMENTS.set(false);
        try {
            helper.assertValueEqual(MysticalAugments.augmentSlots(tool), 0, "slots with the toggle off");
            helper.assertValueEqual(MysticalAugments.tierOf(tool), Optional.empty(), "tier with the toggle off");
            assertComponentsIntact(helper, tool, "with the toggle off");
        } finally {
            ForgeweaveConfig.MYSTICAL_AGRICULTURE_AUGMENTS.set(true);
        }

        helper.assertValueEqual(MysticalAugments.augmentSlots(tool), 2, "back on, the slots return");
        assertComponentsIntact(helper, tool, "after the toggle came back");
        helper.succeed();
    }

    /**
     * The same coexistence, through an actual encode and decode rather than a config flip: the
     * foreign blob and Forgeweave's own modifier and material components round-trip together.
     * {@code m999_tool_augmented.snbt} pins the committed bytes of the same shape.
     */
    @GameTest(template = "empty")
    public static void foreignAugmentStateAndForgeweaveStateRoundTripTogether(GameTestHelper helper) {
        ItemStack tool = tool("supremium");
        tool.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(REINFORCED, 2)));
        CompoundTag augments = new CompoundTag();
        augments.putString("id", "mysticalagriculture:speed");
        augments.putInt("slot", 0);
        tool.set(DataComponents.CUSTOM_DATA, CustomData.of(augments));

        var registries = helper.getLevel().registryAccess();
        ItemStack decoded = ItemStack.parse(registries, tool.save(registries)).orElseThrow();

        helper.assertValueEqual(decoded.getItem(), tool.getItem(), "the decoded item");
        helper.assertValueEqual(ForgeweaveModifiers.entry(decoded, REINFORCED).level(), 2,
                "the decoded modifier level");
        helper.assertValueEqual(MysticalAugments.tierOf(decoded), Optional.of(EssenceTier.SUPREMIUM),
                "the decoded material tier");
        helper.assertValueEqual(decoded.get(DataComponents.CUSTOM_DATA), CustomData.of(augments),
                "the decoded foreign augment blob");
        helper.succeed();
    }

    private static void assertComponentsIntact(GameTestHelper helper, ItemStack tool, String when) {
        helper.assertValueEqual(ForgeweaveModifiers.entry(tool, REINFORCED).level(), 2,
                "the stored modifier level " + when);
        helper.assertTrue(tool.get(DataComponents.CUSTOM_DATA) != null,
                "the foreign augment blob must still be on the stack " + when);
        helper.assertTrue(tool.get(ForgeweaveDataComponents.TOOL_MATERIALS.get()) != null,
                "the tool's own materials must still be on the stack " + when);
    }

    private static ItemStack pickaxe() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), new ToolStats.Stats(250, 6.0F, 3.5F));
        return stack;
    }

    /** A pickaxe built entirely from {@code material}. */
    private static ItemStack tool(String material) {
        ItemStack stack = pickaxe();
        stack.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(), materials(material, material, material));
        return stack;
    }

    /** A head/handle/binding triple, built directly rather than through the part-slot roles. */
    private static ToolMaterials materials(String head, String handle, String binding) {
        return new ToolMaterials(id(head), Optional.of(id(binding)), Optional.of(id(handle)),
                List.of(id(head), id(handle), id(binding)));
    }

    private static ResourceLocation id(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private MysticalAgricultureGameTests() {}
}
