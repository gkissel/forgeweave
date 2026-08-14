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
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
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

    /**
     * Issue #379: a part with no material component at all says so rather than hovering blank --
     * upstream's {@code ToolPart#checkMissingMaterialTooltip} {@code missing_info} branch.
     */
    @Test
    void tooltipReportsMissingDataWhenNoMaterialComponentIsSet() {
        ItemStack stack = new ItemStack(pickaxeHead());

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        assertEquals(List.of(Component.translatable("tooltip.forgeweave.part.missing_info")), tooltip);
    }

    /**
     * The other half of that upstream branch: the component names a material this world's datapack
     * does not define. The error fires <em>instead of</em> the trait block, as upstream's own early
     * return does.
     */
    @Test
    void tooltipReportsAMaterialTheRegistryDoesNotDefine() {
        ResourceLocation missing = ResourceLocation.fromNamespaceAndPath("forgeweave", "unobtainium");
        ItemStack stack = new ItemStack(pickaxeHead());
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), missing);

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().append(stack, registriesWithStone(), false, tooltip);

        assertEquals(List.of(
                Component.translatable("material.forgeweave.unobtainium"),
                Component.translatable("tooltip.forgeweave.part.missing_material", missing.toString())),
                tooltip);
    }

    /** Without registries nothing can resolve, so the plain name line stays the whole tooltip. */
    @Test
    void tooltipStaysAtThePlainNameLineWithoutRegistries() {
        ItemStack stack = new ItemStack(pickaxeHead());
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), STONE_ID);

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().append(stack, null, true, tooltip);

        assertEquals(List.of(Component.translatable("material.forgeweave.stone")), tooltip);
    }

    @Test
    void compactTooltipShowsTheMaterialAndItsTraitInTheMaterialColor() {
        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().append(partOf(pickaxeHead()), registriesWithStone(), false, tooltip);

        assertEquals(List.of(
                Component.translatable("material.forgeweave.stone").withStyle(Style.EMPTY.withColor(STONE_COLOR)),
                Component.empty(),
                traitLine("cheap", STONE_COLOR)),
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
                statTypeHeading("head"),
                statLine("durability", "120", DURABILITY_COLOR),
                statLine("mining_speed", "4", SPEED_COLOR),
                statLine("attack_damage", "3", ATTACK_COLOR),
                tierLine("stone"),
                Component.empty(),
                traitLine("cheap", STONE_COLOR)),
                tooltip);
    }

    /**
     * Issue #254: head parts decide a tool's mining capability, so their detailed tooltip shows the
     * tool tier the material's {@code incorrect_for_tool} tag grants. Knightslime ships
     * {@code incorrect_for_netherite_tool}, i.e. the Netherite tier.
     */
    @Test
    void knightslimeHeadShowsNetheriteToolTierWhenDetailed() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("forgeweave", "knightslime");
        ItemStack stack = new ItemStack(pickaxeHead());
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), id);

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().append(stack, registriesWith(id, materialWithTier("netherite")), true, tooltip);

        assertTrue(tooltip.contains(tierLine("netherite")), "expected a Netherite tier line in " + tooltip);
    }

    /**
     * The shipped wood material's {@code incorrect_for_tool} is {@code incorrect_for_stone_tool}
     * (upstream 1.12's wood sits at the STONE harvest level, issue #79), so a wood head reads
     * {@code Tool Tier: Stone}.
     */
    @Test
    void woodHeadShowsStoneToolTierWhenDetailed() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("forgeweave", "wood");
        ItemStack stack = new ItemStack(pickaxeHead());
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), id);

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().append(stack, registriesWith(id, materialWithTier("stone")), true, tooltip);

        assertTrue(tooltip.contains(tierLine("stone")), "expected a Stone tier line in " + tooltip);
    }

    /** A tag off the vanilla ladder (gold, modded) grants no tier this ladder knows: no line, no guess. */
    @Test
    void headWithOffLadderTierTagShowsNoTierLine() {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("forgeweave", "wood");
        ItemStack stack = new ItemStack(pickaxeHead());
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), id);

        List<Component> tooltip = new ArrayList<>();
        pickaxeHead().append(stack, registriesWith(id, materialWithTier("gold")), true, tooltip);

        assertTrue(tooltip.stream().noneMatch(line -> line.toString().contains("tool_tier")),
                "expected no tier line in " + tooltip);
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
                statTypeHeading("handle"),
                statLine("handle_modifier", "0.5", MODIFIER_COLOR),
                statLine("handle_durability", "-50", DURABILITY_COLOR),
                Component.empty(),
                traitLine("cheap", STONE_COLOR)),
                tooltip);
    }

    @Test
    void bindingPartShowsOnlyItsExtraDurability() {
        PartItem binding = (PartItem) ForgeweaveItems.PART_TOOL_BINDING.get();

        List<Component> tooltip = new ArrayList<>();
        binding.append(partOf(binding), registriesWithStone(), true, tooltip);

        assertEquals(statTypeHeading("extra"), tooltip.get(2));
        assertEquals(statLine("extra_durability", "20", DURABILITY_COLOR), tooltip.get(3));
        assertEquals(6, tooltip.size(),
                "issue #379's heading and one stat line, framed by the material name and the single "
                        + "trait row issue #376 left");
    }

    private static ItemStack partOf(PartItem part) {
        ItemStack stack = new ItemStack(part);
        stack.set(ForgeweaveDataComponents.MATERIAL.get(), STONE_ID);
        return stack;
    }

    /** Issue #379: upstream's white underlined {@code stat.<type>.name} heading over the stat block. */
    private static Component statTypeHeading(String kind) {
        return Component.translatable("tooltip.forgeweave.stat_type." + kind)
                .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE);
    }

    /**
     * A stat row as {@code StationText} builds it: label plus coloured value, with the row's
     * {@code .desc} key hung on it as hover text (issue #376).
     */
    private static Component statLine(String key, String value, TextColor color) {
        return withHover(Component.translatable("gui.forgeweave.stat." + key,
                Component.literal(value).withStyle(Style.EMPTY.withColor(color))),
                Component.translatable("gui.forgeweave.stat." + key + ".desc"));
    }

    /** One line per trait since issue #376, its description carried as hover text rather than a second line. */
    private static Component traitLine(String trait, TextColor color) {
        return withHover(
                Component.translatable("trait.forgeweave." + trait + ".name")
                        .withStyle(Style.EMPTY.withColor(color)),
                Component.translatable("trait.forgeweave." + trait + ".description"));
    }

    private static Component withHover(MutableComponent line, Component description) {
        Component hover = line.copy().append("\n").append(description.copy().withStyle(ChatFormatting.GRAY));
        return line.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
    }

    private static Component tierLine(String tier) {
        return Component.translatable("tooltip.forgeweave.tool_tier")
                .append(": ")
                .append(Component.translatable("tooltip.forgeweave.tier." + tier));
    }

    private static HolderLookup.Provider registriesWithStone() {
        return registriesWith(STONE_ID, stone());
    }

    private static HolderLookup.Provider registriesWith(ResourceLocation id, Material material) {
        MappedRegistry<Material> registry = new MappedRegistry<>(Material.REGISTRY, Lifecycle.stable());
        registry.register(ResourceKey.create(Material.REGISTRY, id), material, RegistrationInfo.BUILT_IN);
        return HolderLookup.Provider.create(Stream.of(registry.asLookup()));
    }

    /** {@link #stone()}'s stats under a different {@code incorrect_for_tool} tag -- only the tier line changes. */
    private static Material materialWithTier(String tier) {
        Material stone = stone();
        return new Material(stone.head(), stone.handle(), stone.extraDurability(),
                TagKey.create(Registries.BLOCK,
                        ResourceLocation.withDefaultNamespace("incorrect_for_" + tier + "_tool")),
                stone.traits(), stone.craftingItems(), stone.repairItem(), stone.color());
    }

    private static Material stone() {
        return new Material(
                new Material.Head(120, 4.0F, 3.0F),
                new Material.Handle(0.5F, -50),
                20,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                Material.Traits.general(CHEAP_TRAIT),
                List.of(),
                Ingredient.of(Items.COBBLESTONE),
                STONE_COLOR);
    }
}
