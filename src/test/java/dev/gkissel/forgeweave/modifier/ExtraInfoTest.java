package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.trait.AlienProgress;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Parity audit T26 (issue #457): the extra-info lines upstream 1.12's {@code IModifier#getExtraInfo}
 * puts under a modifier or trait in the Tool Station's info panel, the leveled names
 * {@code Modifier#getLeveledTooltip} shows instead of {@code Name + numeral}, and the per-modifier
 * colour {@code ModifierNBT#getColorString} prefixes every modifier line with.
 *
 * <p>Every expected number is the clone's own formula at the pinned commit -- see each test.
 */
class ExtraInfoTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    /** A stack with no Forgeweave item behind it: only haste's line looks at the item at all. */
    private static ItemStack tool() {
        return new ItemStack(Items.IRON_PICKAXE);
    }

    private static List<Component> extra(String path, int level) {
        return ForgeweaveModifiers.extraInfo(tool(), new ModifierEntry(id(path), level));
    }

    private static Component line(String path, Object arg) {
        return Component.translatable("modifier.forgeweave." + path + ".extra", arg);
    }

    /** {@code ModAntiMonsterType#calcIncreasedDamage}: {@code current * 7 / 24}, smite and bane alike. */
    @Test
    void smiteAndBaneReportTheirBonusDamage() {
        assertEquals(List.of(line("smite", "7")), extra("smite", 24));
        assertEquals(List.of(line("smite", "3.5")), extra("smite", 12));
        assertEquals(List.of(line("bane_of_arthropods", "7")), extra("bane_of_arthropods", 24));
    }

    /** {@code ModFiery}: {@code current / 15} damage and {@code 1 + current / 8} seconds, two lines. */
    @Test
    void fieryReportsFireDamageAndBurnDuration() {
        assertEquals(List.of(
                line("fiery", "1.67"),
                Component.translatable("modifier.forgeweave.fiery.extra2", "4")),
                extra("fiery", 25));
    }

    /** {@code ModNecrotic#lifesteal}: {@code 0.10f * level}. */
    @Test
    void necroticReportsItsLifestealFraction() {
        assertEquals(List.of(line("necrotic", "30%")), extra("necrotic", 3));
    }

    /**
     * {@code ModReinforced#getExtraInfo}: the negation chance, replaced by the "Unbreakable" word
     * once it reaches 100% -- which is where the level-5 cap lands it (20% per level).
     */
    @Test
    void reinforcedReportsItsChanceAndThenReadsUnbreakable() {
        assertEquals(List.of(line("reinforced", Component.literal("40%"))), extra("reinforced", 2));
        assertEquals(List.of(line("reinforced",
                Component.translatable("modifier.forgeweave.reinforced.unbreakable"))),
                extra("reinforced", 5));
    }

    /** {@code ModShulking#getDuration}: {@code current / 2 + 10} ticks, shown in seconds. */
    @Test
    void shulkingReportsItsLevitationDurationInSeconds() {
        assertEquals(List.of(line("shulking", "1.75")), extra("shulking", 50));
    }

    /** {@code ModMendingMoss#getExtraInfo}: the XP banked on the stack, not a function of the level. */
    @Test
    void mendingMossReportsTheExperienceItHasBanked() {
        ItemStack stack = tool();
        stack.set(ForgeweaveDataComponents.MENDING_MOSS_XP.get(), 42);
        assertEquals(List.of(line("mending_moss", "42")),
                ForgeweaveModifiers.extraInfo(stack, new ModifierEntry(id("mending_moss"), 1)));
    }

    /** A modifier upstream gives no {@code getExtraInfo} contributes nothing, as its default does. */
    @Test
    void aModifierWithNoUpstreamExtraInfoContributesNothing() {
        assertEquals(List.of(), extra("soulbound", 1));
        assertEquals(List.of(), extra("diamond", 1));
    }

    /**
     * {@code Modifier#getLeveledTooltip}: the {@code modifier.<id>.nameN} ladder where upstream
     * defines one (haste and sharpness), the plain name plus a roman numeral where it does not, and
     * {@code ModReinforced#getTooltip}'s "Unbreakable" at the level whose chance reaches 100%.
     */
    @Test
    void leveledNamesReplaceTheRomanNumeralWhereUpstreamDefinesThem() {
        assertEquals(Component.translatable("modifier.forgeweave.haste.name"),
                ModifierApplication.displayName(id("haste"), 1));
        assertEquals(Component.translatable("modifier.forgeweave.haste.name2"),
                ModifierApplication.displayName(id("haste"), 2));
        assertEquals(Component.translatable("modifier.forgeweave.sharpness.name5"),
                ModifierApplication.displayName(id("sharpness"), 5));
        assertEquals(Component.translatable("modifier.forgeweave.reinforced.unbreakable"),
                ModifierApplication.displayName(id("reinforced"), 5));
        assertEquals(Component.translatable("modifier.forgeweave.reinforced.name")
                        .append(net.minecraft.network.chat.CommonComponents.SPACE)
                        .append(Component.translatable("enchantment.level.2")),
                ModifierApplication.displayName(id("reinforced"), 2));
        assertEquals(Component.translatable("modifier.forgeweave.knockback.name")
                        .append(net.minecraft.network.chat.CommonComponents.SPACE)
                        .append(Component.translatable("enchantment.level.3")),
                ModifierApplication.displayName(id("knockback"), 3),
                "a modifier with no leveled names keeps upstream's numeral fallback");
    }

    /** Upstream's per-modifier colours ({@code TinkerModifiers#registerModifiers}), with a fallback. */
    @Test
    void everyModifierCarriesItsOwnColour() {
        assertEquals(TextColor.fromRgb(0xE8D500), ForgeweaveModifiers.color(id("smite")));
        assertEquals(TextColor.fromRgb(0x502E83), ForgeweaveModifiers.color(id("reinforced")));
        assertEquals(TextColor.fromRgb(0x910000), ForgeweaveModifiers.color(id("haste")));
        assertEquals(StationText.MODIFIER_COLOR, ForgeweaveModifiers.color(id("embossment.iron")),
                "a generated embossment id has no colour of its own; upstream's material tint needs a "
                        + "registry the tooltip callers don't have");
    }

    // ---------------------------------------------------------------- traits

    private static Component traitLine(String path, Object arg) {
        return Component.translatable("trait.forgeweave." + path + ".extra", arg);
    }

    /** The five trait lines whose value is a constant ({@code TraitCrude}, {@code TraitHellish}, ...). */
    @Test
    void constantValuedTraitsReportTheirBonus() {
        ItemStack stack = tool();
        assertEquals(List.of(traitLine("crude", "5%")), ForgeweaveTraits.extraInfo(id("crude"), stack));
        assertEquals(List.of(traitLine("crude2", "10%")), ForgeweaveTraits.extraInfo(id("crude2"), stack));
        assertEquals(List.of(traitLine("hellish", "4")), ForgeweaveTraits.extraInfo(id("hellish"), stack));
        assertEquals(List.of(traitLine("holy", "5")), ForgeweaveTraits.extraInfo(id("holy"), stack));
        assertEquals(List.of(traitLine("lightweight", "10%")),
                ForgeweaveTraits.extraInfo(id("lightweight"), stack));
        assertEquals(List.of(traitLine("superheat", "35%")),
                ForgeweaveTraits.extraInfo(id("superheat"), stack));
    }

    /**
     * Jagged and stonebound share the "old tcon" wear curve, {@code log(lost / 72 + 1) * 2}: at 200
     * durability lost that is {@code ln(3.7778) * 2 = 2.658}, and at none it is zero.
     */
    @Test
    void theWearTraitsReportTheCurveAtTheToolsCurrentDamage() {
        ItemStack worn = tool();
        worn.setDamageValue(200);
        assertEquals(List.of(traitLine("jagged", "2.66")), ForgeweaveTraits.extraInfo(id("jagged"), worn));
        assertEquals(List.of(traitLine("stonebound", "2.66")),
                ForgeweaveTraits.extraInfo(id("stonebound"), worn));
        assertEquals(List.of(traitLine("jagged", "0")), ForgeweaveTraits.extraInfo(id("jagged"), tool()));
    }

    /** {@code TraitAlien#getExtraInfo}: the three stats it has distributed so far, as stat rows. */
    @Test
    void alienReportsTheStatsItHasHandedOut() {
        ItemStack stack = tool();
        stack.set(ForgeweaveDataComponents.ALIEN_PROGRESS.get(), new AlienProgress(
                new AlienProgress.Portion(100, 2.0F, 2.0F),
                new AlienProgress.Portion(12, 0.5F, 0.25F)));

        assertEquals(List.of(
                Component.translatable("gui.forgeweave.stat.durability", "12"),
                Component.translatable("gui.forgeweave.stat.mining_speed", "0.5"),
                Component.translatable("gui.forgeweave.stat.attack_damage", "0.25")),
                ForgeweaveTraits.extraInfo(id("alien"), stack));
    }

    /** A trait upstream gives no {@code getExtraInfo} contributes nothing. */
    @Test
    void aTraitWithNoUpstreamExtraInfoContributesNothing() {
        assertEquals(List.of(), ForgeweaveTraits.extraInfo(id("ecological"), tool()));
    }
}
