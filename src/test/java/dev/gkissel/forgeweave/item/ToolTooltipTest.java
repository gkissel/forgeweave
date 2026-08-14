package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.mojang.serialization.Lifecycle;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Pins {@link ToolTooltip#append}'s compact-vs-Shift line assembly (issue #54; structure ported
 * from upstream 1.12, see {@link ToolTooltip}'s javadoc and NOTICE.md). Materials/stats mirror the
 * shipped stone-head/wood-binding/wood-handle pickaxe {@code ToolStationGameTests} builds
 * (durability 160, mining speed 4.0, attack damage 3.0).
 */
class ToolTooltipTest {

    private static final ResourceLocation STONE_ID = ResourceLocation.fromNamespaceAndPath("forgeweave", "stone");
    private static final ResourceLocation WOOD_ID = ResourceLocation.fromNamespaceAndPath("forgeweave", "wood");
    private static final ResourceLocation CHEAP_TRAIT = ResourceLocation.fromNamespaceAndPath("forgeweave", "cheap");
    private static final ResourceLocation ECOLOGICAL_TRAIT =
            ResourceLocation.fromNamespaceAndPath("forgeweave", "ecological");

    private static final ToolStats.Stats PICKAXE_STATS = new ToolStats.Stats(160, 4.0F, 3.0F);
    private static final TextColor STONE_COLOR = TextColor.fromRgb(0x999999);
    private static final TextColor WOOD_COLOR = TextColor.fromRgb(0x8E661B);
    private static final TextColor ATTACK_COLOR = TextColor.fromRgb(0xD76464);
    private static final TextColor SPEED_COLOR = TextColor.fromRgb(0x78A0CD);
    private static final TextColor MODIFIER_COLOR = TextColor.fromRgb(0xB9B95A);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void appendDoesNothingForAnUnassembledStack() {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());

        List<Component> tooltip = new ArrayList<>();
        ToolTooltip.append(stack, null, true, 3.0F, tooltip);

        assertEquals(List.of(), tooltip);
    }

    @Test
    void compactTooltipShowsDurabilityAttackDamageAndFreeModifierSlots() {
        ItemStack stack = assembledPickaxe(40, List.of());

        List<Component> tooltip = new ArrayList<>();
        ToolTooltip.append(stack, null, false, 3.0F, tooltip);

        assertEquals(List.of(durabilityLine(120, 160), attackLine(3.0F), pierceLine(), slotsLine(3)), tooltip);
    }

    /**
     * Issue #303: the warmace's innate is vanilla's own mace ({@code WarmaceItem}), never a {@code
     * ForgeweaveInnates.Innate} seam, so without {@code innateId}'s carve-out it showed no line at all
     * -- unlike every other weapon. Same shape as the pickaxe's pierce line above.
     */
    @Test
    void compactTooltipShowsTheWarmaceSmashInnateLine() {
        ItemStack stack = assembledWarmace(40, List.of());

        List<Component> tooltip = new ArrayList<>();
        ToolTooltip.append(stack, null, false, 7.0F, tooltip);

        assertEquals(List.of(durabilityLine(120, 160), attackLine(7.0F), smashLine(), slotsLine(3)), tooltip);
    }

    /**
     * Issue #105: an applied Modifier shows its name and level, and the free-slot count drops by the
     * one it occupies. Haste's level is in redstone, so 51 of them read as level 2 with the current
     * level 51/100 full -- upstream's {@code ModifierNBT.IntegerNBT#extraInfo}.
     */
    @Test
    void compactTooltipListsModifiersWithTheirLevelAndProgress() {
        ItemStack stack = assembledPickaxe(40, List.of());
        stack.set(ForgeweaveDataComponents.MODIFIERS.get(),
                List.of(new ModifierEntry(ResourceLocation.fromNamespaceAndPath("forgeweave", "haste"), 51)));

        List<Component> tooltip = new ArrayList<>();
        ToolTooltip.append(stack, null, false, 3.0F, tooltip);

        assertEquals(List.of(
                durabilityLine(120, 160),
                attackLine(3.0F),
                pierceLine(),
                Component.translatable("modifier.forgeweave.haste.name")
                        .withStyle(Style.EMPTY.withColor(MODIFIER_COLOR))
                        .append(CommonComponents.SPACE)
                        .append(Component.translatable("enchantment.level.2"))
                        .append(Component.literal(" (51/100)").withStyle(ChatFormatting.GRAY)),
                slotsLine(2)),
                tooltip);
    }

    @Test
    void brokenToolShowsOneBrokenLineInsteadOfDurability() {
        ItemStack stack = assembledPickaxe(159, List.of());
        stack.set(ForgeweaveDataComponents.BROKEN.get(), true);

        List<Component> tooltip = new ArrayList<>();
        ToolTooltip.append(stack, null, false, 0.0F, tooltip);

        assertEquals(List.of(
                Component.translatable("tooltip.forgeweave.broken").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                attackLine(0.0F),
                pierceLine(),
                slotsLine(3)),
                tooltip);
    }

    @Test
    void detailedTooltipAddsMiningSpeedTierPartsAndTraits() {
        ItemStack stack = assembledPickaxe(40, List.of(CHEAP_TRAIT, ECOLOGICAL_TRAIT));
        HolderLookup.Provider registries = registriesWithStoneAndWood();

        List<Component> tooltip = new ArrayList<>();
        ToolTooltip.append(stack, registries, true, 3.0F, tooltip);

        assertEquals(List.of(
                durabilityLine(120, 160),
                attackLine(3.0F),
                pierceLine(),
                slotsLine(3),
                statLine("tooltip.forgeweave.mining_speed", "4", SPEED_COLOR),
                Component.translatable("tooltip.forgeweave.tool_tier").append(": ")
                        .append(Component.translatable("tooltip.forgeweave.tier.stone")),
                Component.empty(),
                Component.translatable("material.forgeweave.stone").withStyle(Style.EMPTY.withColor(STONE_COLOR)),
                Component.translatable("material.forgeweave.wood").withStyle(Style.EMPTY.withColor(WOOD_COLOR)),
                Component.translatable("material.forgeweave.wood").withStyle(Style.EMPTY.withColor(WOOD_COLOR)),
                Component.empty(),
                traitLine("cheap", STONE_COLOR),
                traitLine("ecological", WOOD_COLOR)),
                tooltip);
    }

    @Test
    void detailedTooltipDegradesGracefullyWithoutRegistries() {
        ItemStack stack = assembledPickaxe(40, List.of(CHEAP_TRAIT));

        List<Component> tooltip = new ArrayList<>();
        ToolTooltip.append(stack, null, true, 3.0F, tooltip);

        // Parts and the trait name fall back to plain translatable components (PartItem's pattern);
        // no color can be resolved without registries, and no material means no granting-color match
        // for the trait either. Tool tier needs the head Material record too, so it's skipped
        // entirely rather than shown without a value.
        assertEquals(List.of(
                durabilityLine(120, 160),
                attackLine(3.0F),
                pierceLine(),
                slotsLine(3),
                statLine("tooltip.forgeweave.mining_speed", "4", SPEED_COLOR),
                Component.empty(),
                Component.translatable("material.forgeweave.stone"),
                Component.translatable("material.forgeweave.wood"),
                Component.translatable("material.forgeweave.wood"),
                Component.empty(),
                Component.translatable("trait.forgeweave.cheap.name")
                        .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                        .append(Component.translatable("trait.forgeweave.cheap.description").withStyle(ChatFormatting.GRAY))),
                tooltip);
    }

    /** Issue #254: the bare-tag tier mapping head-part tooltips use covers the whole vanilla ladder. */
    @Test
    void tierLineMapsEveryVanillaLadderTag() {
        for (String tier : List.of("wooden", "stone", "iron", "diamond", "netherite")) {
            assertEquals(
                    Optional.of(Component.translatable("tooltip.forgeweave.tool_tier").append(": ")
                            .append(Component.translatable("tooltip.forgeweave.tier." + tier))),
                    ToolTooltip.tierLine(incorrectForTool(tier)),
                    tier);
        }
    }

    /** Gold and modded tags are off the ladder: no line rather than a guessed tier (#254). */
    @Test
    void tierLineIsEmptyForGoldAndUnknownTags() {
        assertEquals(Optional.empty(), ToolTooltip.tierLine(incorrectForTool("gold")));
        assertEquals(Optional.empty(), ToolTooltip.tierLine(TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("somemod", "incorrect_for_osmium_tool"))));
    }

    @Test
    void toolItemAppendHoverTextWiresTheCompactTooltipByDefault() {
        ItemStack stack = assembledPickaxe(40, List.of());

        List<Component> tooltip = new ArrayList<>();
        ForgeweaveItems.TOOL_PICKAXE.get().appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        // TooltipFlag.NORMAL.hasShiftDown() is NeoForge's TooltipFlagExtension default (false off-client).
        assertEquals(List.of(durabilityLine(120, 160), attackLine(3.0F), pierceLine(), slotsLine(3)), tooltip);
    }

    private static ItemStack assembledPickaxe(int damage, List<ResourceLocation> traits) {
        return assembledTool(ForgeweaveItems.TOOL_PICKAXE.get(), damage, traits);
    }

    /** Issue #303: the warmace, same fixture shape -- {@link ToolTooltip} doesn't care which tool it is. */
    private static ItemStack assembledWarmace(int damage, List<ResourceLocation> traits) {
        return assembledTool(ForgeweaveItems.TOOL_WARMACE.get(), damage, traits);
    }

    private static ItemStack assembledTool(Item item, int damage, List<ResourceLocation> traits) {
        ItemStack stack = new ItemStack(item);
        stack.set(ForgeweaveDataComponents.TOOL_MATERIALS.get(), new ToolMaterials(STONE_ID, java.util.Optional.of(WOOD_ID), WOOD_ID,
                java.util.List.of(STONE_ID, WOOD_ID, WOOD_ID)));
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), PICKAXE_STATS);
        stack.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        stack.set(DataComponents.MAX_DAMAGE, PICKAXE_STATS.durability());
        stack.set(DataComponents.DAMAGE, damage);
        return stack;
    }

    private static HolderLookup.Provider registriesWithStoneAndWood() {
        MappedRegistry<Material> registry = new MappedRegistry<>(Material.REGISTRY, Lifecycle.stable());
        registry.register(ResourceKey.create(Material.REGISTRY, STONE_ID), stone(), RegistrationInfo.BUILT_IN);
        registry.register(ResourceKey.create(Material.REGISTRY, WOOD_ID), wood(), RegistrationInfo.BUILT_IN);
        return HolderLookup.Provider.create(Stream.of(registry.asLookup()));
    }

    private static Material stone() {
        return new Material(
                new Material.Head(120, 4.0F, 3.0F),
                new Material.Handle(0.5F, -50),
                20,
                incorrectForTool("stone"),
                Material.Traits.general(CHEAP_TRAIT),
                List.of(),
                Ingredient.of(Items.COBBLESTONE),
                STONE_COLOR);
    }

    private static Material wood() {
        return new Material(
                new Material.Head(35, 2.0F, 2.0F),
                new Material.Handle(1.0F, 25),
                15,
                incorrectForTool("wooden"),
                Material.Traits.general(ECOLOGICAL_TRAIT),
                List.of(),
                Ingredient.of(Items.STICK),
                WOOD_COLOR);
    }

    private static TagKey<Block> incorrectForTool(String tier) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_" + tier + "_tool"));
    }

    private static Component durabilityLine(int current, int max) {
        return Component.translatable("tooltip.forgeweave.durability")
                .append(": ")
                .append(Component.literal(Integer.toString(current))
                        .withStyle(Style.EMPTY.withColor(durabilityColor((float) current / max))))
                .append(Component.literal("/").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(Integer.toString(max)).withStyle(Style.EMPTY.withColor(durabilityColor(1.0F))));
    }

    /** Upstream 1.12's "Modifiers: %d" line, shown while the tool still has slots free. */
    private static Component slotsLine(int free) {
        return Component.translatable("tooltip.forgeweave.modifier_slots", free)
                .withStyle(Style.EMPTY.withColor(MODIFIER_COLOR));
    }

    private static Component attackLine(float value) {
        return statLine("tooltip.forgeweave.attack_damage", formatNumber(value), ATTACK_COLOR);
    }

    private static Component statLine(String key, String value, TextColor color) {
        return Component.translatable(key).append(": ").append(Component.literal(value).withStyle(Style.EMPTY.withColor(color)));
    }

    /**
     * The M1 pickaxe innate line every {@code assembledPickaxe} stack now carries (issue #164):
     * same name-colon-description shape as {@link #traitLine}, but gold and not keyed by material.
     */
    private static Component pierceLine() {
        return Component.translatable("tooltip.forgeweave.innate.pierce.name")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("tooltip.forgeweave.innate.pierce.description").withStyle(ChatFormatting.GRAY));
    }

    /** Issue #303's warmace innate line, same shape as {@link #pierceLine()}. */
    private static Component smashLine() {
        return Component.translatable("tooltip.forgeweave.innate.smash.name")
                .withStyle(ChatFormatting.GOLD)
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("tooltip.forgeweave.innate.smash.description").withStyle(ChatFormatting.GRAY));
    }

    private static Component traitLine(String path, TextColor color) {
        return Component.translatable("trait.forgeweave." + path + ".name")
                .withStyle(Style.EMPTY.withColor(color))
                .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                .append(Component.translatable("trait.forgeweave." + path + ".description").withStyle(ChatFormatting.GRAY));
    }

    /** Mirrors {@code ToolTooltip#durabilityColor}: upstream's {@code CustomFontColor#valueToColorCode}. */
    private static TextColor durabilityColor(float ratio) {
        float hue = Mth.clamp(ratio / 3f, 0.01f, 0.5f);
        return TextColor.fromRgb(Color.HSBtoRGB(hue, 0.65f, 0.8f) & 0xFFFFFF);
    }

    private static String formatNumber(float value) {
        return value == Math.rint(value) ? Integer.toString((int) value) : String.valueOf(value);
    }
}
