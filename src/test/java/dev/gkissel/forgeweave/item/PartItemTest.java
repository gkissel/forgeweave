package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.Lifecycle;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.material.Material;

/**
 * Pins the one piece of real logic in the item slice: a part's tooltip reflects whatever material
 * id is (or isn't) stored in its {@link ForgeweaveDataComponents#MATERIAL} component, and -- once
 * the material resolves -- the stats that material contributes <em>through that kind of part</em>
 * plus its trait, coloured per upstream (issue #64; see {@link PartItem}'s javadoc and NOTICE.md).
 * Stat values mirror the shipped stone material.
 */
class PartItemTest {

    private static final ResourceLocation STONE_ID = ResourceLocation.fromNamespaceAndPath("forgeweave", "stone");
    private static final ResourceLocation CHEAP_TRAIT = ResourceLocation.fromNamespaceAndPath("forgeweave", "cheap");
    private static final TextColor STONE_COLOR = TextColor.fromRgb(0x999999);

    /** Upstream's {@code COLOR_Durability}, i.e. the top of {@code valueToColorCode}'s ramp. */
    private static final TextColor DURABILITY_COLOR = TextColor.fromRgb(0x47CC47);
    private static final TextColor SPEED_COLOR = TextColor.fromRgb(0x78A0CD);
    private static final TextColor ATTACK_COLOR = TextColor.fromRgb(0xD76464);
    private static final TextColor MODIFIER_COLOR = TextColor.fromRgb(0xB9B95A);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static PartItem pickaxeHead() {
        return (PartItem) ForgeweaveItems.PART_PICKAXE_HEAD.get();
    }

    @Test
    void tooltipShowsTranslatableMaterialNameWhenComponentIsSet() {
        ItemStack stack = new ItemStack(pickaxeHead());
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), ResourceLocation.fromNamespaceAndPath("forgeweave", "wood"));

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        assertEquals(List.of(Component.translatable("material.forgeweave.wood")), tooltip);
    }

    @Test
    void tooltipStaysEmptyWhenNoMaterialComponentIsSet() {
        ItemStack stack = new ItemStack(pickaxeHead());

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        assertTrue(tooltip.isEmpty());
    }

    @Test
    void compactTooltipShowsTheMaterialAndItsTraitInTheMaterialColor() {
        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().append(partOf(pickaxeHead()), registriesWithStone(), false, tooltip);

        assertEquals(List.of(
                Component.translatable("material.forgeweave.stone").withStyle(Style.EMPTY.withColor(STONE_COLOR)),
                Component.empty(),
                Component.translatable("trait.forgeweave.cheap.name").withStyle(Style.EMPTY.withColor(STONE_COLOR)),
                Component.translatable("trait.forgeweave.cheap.description").withStyle(ChatFormatting.GRAY)),
                tooltip);
    }

    @Test
    void headPartShowsDurabilityMiningSpeedAndAttackWhenDetailed() {
        PartItem head = pickaxeHead();

        List<Component> tooltip = new ArrayList<>();
        head.append(partOf(head), registriesWithStone(), true, tooltip);

        assertEquals(List.of(
                Component.translatable("material.forgeweave.stone").withStyle(Style.EMPTY.withColor(STONE_COLOR)),
                Component.empty(),
                statLine("durability", "120", DURABILITY_COLOR),
                statLine("mining_speed", "4", SPEED_COLOR),
                statLine("attack_damage", "3", ATTACK_COLOR),
                Component.empty(),
                Component.translatable("trait.forgeweave.cheap.name").withStyle(Style.EMPTY.withColor(STONE_COLOR)),
                Component.translatable("trait.forgeweave.cheap.description").withStyle(ChatFormatting.GRAY)),
                tooltip);
    }

    @Test
    void handlePartShowsItsModifierAndDurabilityBonusInsteadOfHeadStats() {
        PartItem handle = (PartItem) ForgeweaveItems.PART_TOOL_HANDLE.get();

        List<Component> tooltip = new ArrayList<>();
        handle.append(partOf(handle), registriesWithStone(), true, tooltip);

        // Negative bonuses keep their sign and no "+" is added, as upstream's Util.df does.
        assertEquals(List.of(
                Component.translatable("material.forgeweave.stone").withStyle(Style.EMPTY.withColor(STONE_COLOR)),
                Component.empty(),
                statLine("handle_modifier", "0.5", MODIFIER_COLOR),
                statLine("handle_durability", "-50", DURABILITY_COLOR),
                Component.empty(),
                Component.translatable("trait.forgeweave.cheap.name").withStyle(Style.EMPTY.withColor(STONE_COLOR)),
                Component.translatable("trait.forgeweave.cheap.description").withStyle(ChatFormatting.GRAY)),
                tooltip);
    }

    @Test
    void bindingPartShowsOnlyItsExtraDurability() {
        PartItem binding = (PartItem) ForgeweaveItems.PART_TOOL_BINDING.get();

        List<Component> tooltip = new ArrayList<>();
        binding.append(partOf(binding), registriesWithStone(), true, tooltip);

        assertEquals(statLine("extra_durability", "20", DURABILITY_COLOR), tooltip.get(2));
        assertEquals(6, tooltip.size(), "one stat line, framed by the material name and the trait pair");
    }

    private static ItemStack partOf(PartItem part) {
        ItemStack stack = new ItemStack(part);
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), STONE_ID);
        return stack;
    }

    private static Component statLine(String key, String value, TextColor color) {
        return Component.translatable("gui.forgeweave.stat." + key,
                Component.literal(value).withStyle(Style.EMPTY.withColor(color)));
    }

    private static HolderLookup.Provider registriesWithStone() {
        MappedRegistry<Material> registry = new MappedRegistry<>(Material.REGISTRY, Lifecycle.stable());
        registry.register(ResourceKey.create(Material.REGISTRY, STONE_ID), stone(), RegistrationInfo.BUILT_IN);
        return HolderLookup.Provider.create(Stream.of(registry.asLookup()));
    }

    private static Material stone() {
        return new Material(
                new Material.Head(120, 4.0F, 3.0F),
                new Material.Handle(0.5F, -50),
                20,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                CHEAP_TRAIT,
                List.of(),
                Ingredient.of(Items.COBBLESTONE),
                STONE_COLOR);
    }
}
