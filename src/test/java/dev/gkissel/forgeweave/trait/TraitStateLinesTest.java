package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * {@link Trait#stateLines}'s three shipped implementations (issue #955): bloodtally's kill count and
 * bonus, warmemory's top per-type entries, and evolved's Draconic-upgrade count with highest-level-
 * wins across a tool's parts. {@code ToolTooltip}'s wiring of these lines into the actual tooltip is
 * covered separately in {@code ToolTooltipTest}; this pins the exact line text and numbers each
 * implementation produces off a bare {@link ItemStack} and its data components.
 */
class TraitStateLinesTest {

    private static final Identifier ZOMBIE = Identifier.withDefaultNamespace("zombie");
    private static final Identifier SKELETON = Identifier.withDefaultNamespace("skeleton");
    private static final Identifier SPIDER = Identifier.withDefaultNamespace("spider");
    private static final Identifier CREEPER = Identifier.withDefaultNamespace("creeper");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static ItemStack blankStack() {
        return new ItemStack(Items.STICK);
    }

    private static List<Component> stateLines(Trait trait, ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        trait.stateLines(stack, lines::add);
        return lines;
    }

    // ---------------------------------------------------------------- bloodtally

    /** 37 kills: bonus is 37 * 0.03 = 1.11, under the 200-kill/+6 cap. */
    @Test
    void bloodtallyBelowCapShowsTheCurrentBonusAndTheCap() {
        ItemStack stack = blankStack();
        stack.set(ForgeweaveDataComponents.KILL_TALLY.get(), 37);

        assertEquals(List.of(Component.translatable("tooltip.forgeweave.trait.bloodtally", "1.11", 37, "6")
                        .withStyle(ChatFormatting.GRAY)),
                stateLines(ForgeweaveTraits.BLOODTALLY, stack));
    }

    /** 200 kills is the cap itself: bonus reads the capped +6, not a projection past it. */
    @Test
    void bloodtallyAtCapShowsTheCappedBonus() {
        ItemStack stack = blankStack();
        stack.set(ForgeweaveDataComponents.KILL_TALLY.get(), 200);

        assertEquals(List.of(Component.translatable("tooltip.forgeweave.trait.bloodtally", "6", 200, "6")
                        .withStyle(ChatFormatting.GRAY)),
                stateLines(ForgeweaveTraits.BLOODTALLY, stack));
    }

    /** No {@code kill_tally} component at all -- a tool that has never scored a kill -- shows nothing. */
    @Test
    void bloodtallyWithAnEmptyTallyShowsNothing() {
        assertEquals(List.of(), stateLines(ForgeweaveTraits.BLOODTALLY, blankStack()));
    }

    // ---------------------------------------------------------------- warmemory

    /**
     * Four entity types fought; only the top three by bonus show, highest first, followed by the
     * shared per-type cap line. Spider is already at the 20-fight cap (bonus +3, not more).
     */
    @Test
    void warmemoryShowsTheTopThreeEntriesByBonusThenTheCapLine() {
        ItemStack stack = blankStack();
        stack.set(ForgeweaveDataComponents.WAR_MEMORY.get(), WarMemory.EMPTY
                .with(ZOMBIE, 12)
                .with(SKELETON, 5)
                .with(SPIDER, 20)
                .with(CREEPER, 1));

        assertEquals(List.of(
                        warMemoryEntry(EntityType.SPIDER, "3", 20),
                        warMemoryEntry(EntityType.ZOMBIE, "1.8", 12),
                        warMemoryEntry(EntityType.SKELETON, "0.75", 5),
                        Component.translatable("tooltip.forgeweave.trait.warmemory.cap", "3", 20)
                                .withStyle(ChatFormatting.GRAY)),
                stateLines(ForgeweaveTraits.WARMEMORY, stack));
    }

    /** No fights recorded at all shows nothing -- not even the cap line. */
    @Test
    void warmemoryWithAnEmptyTallyShowsNothing() {
        assertEquals(List.of(), stateLines(ForgeweaveTraits.WARMEMORY, blankStack()));
    }

    private static Component warMemoryEntry(EntityType<?> type, String bonus, int count) {
        return Component.translatable("tooltip.forgeweave.trait.warmemory.entry", type.getDescription(), bonus, count)
                .withStyle(ChatFormatting.GRAY);
    }

    // ---------------------------------------------------------------- evolved

    /**
     * A head-and-handle mix of the wyvern and draconic markers: the tool's overall level is the
     * higher one (issue #955's "highest wins"), so only {@code EVOLVED2} emits a line and
     * {@code EVOLVED} stays silent rather than showing a second, contradicting one.
     *
     * <p>The allowance is issue #965's grid table, so the draconic tier reads 20 rather than #955's
     * original 4. Both parts are welds (soul rend markers), so the line is the module line
     * (maintainer decision 2026-09-06: welds host modules, cores take fusion upgrades).
     */
    @Test
    void evolvedTheHighestLevelAcrossPartsIsTheOnlyOneThatSpeaks() {
        ItemStack stack = blankStack();
        stack.set(ForgeweaveDataComponents.TRAITS.get(),
                List.of(id("evolved"), id("soulrend"), id("evolved2"), id("soulrend2")));

        assertEquals(List.of(), stateLines(ForgeweaveTraits.EVOLVED, stack),
                "the wyvern marker is outranked by the draconic one on another part");
        assertEquals(List.of(Component.translatable("tooltip.forgeweave.trait.evolved", 0, 20)
                        .withStyle(ChatFormatting.GRAY)),
                stateLines(ForgeweaveTraits.EVOLVED2, stack));
    }

    /**
     * Every tier's allowance is the cell count of its own grid in
     * {@code compat.draconic.modules.DraconicModules} -- 6 / 12 / 20 / 36 since issue #965 -- rather
     * than a second table beside it.
     */
    @Test
    void evolvedTheAllowanceIsTheModuleGridsOwnCellCount() {
        List<Integer> allowances = List.of(6, 12, 20, 36);
        List<String> markers = List.of("evolving", "evolved", "evolved2", "evolved3");
        List<String> welds = List.of("soulrend", "soulrend2", "soulrend3", "soulrend4");
        List<Trait> traits = List.of(ForgeweaveTraits.EVOLVING, ForgeweaveTraits.EVOLVED,
                ForgeweaveTraits.EVOLVED2, ForgeweaveTraits.EVOLVED3);

        for (int level = 1; level <= markers.size(); level++) {
            ItemStack stack = blankStack();
            stack.set(ForgeweaveDataComponents.TRAITS.get(), List.of(id(markers.get(level - 1)), id(welds.get(level - 1))));
            assertEquals(List.of(Component
                            .translatable("tooltip.forgeweave.trait.evolved", 0, allowances.get(level - 1))
                            .withStyle(ChatFormatting.GRAY)),
                    stateLines(traits.get(level - 1), stack),
                    markers.get(level - 1) + " must read its own grid's cell count");
        }
    }

    /**
     * A chaotic-core tool (maintainer decision 2026-09-06: cores take fusion upgrades) reads the
     * fusion line: two fusion-upgrade modifiers already applied (haste and sharpness, both in
     * {@code ForgeweaveDraconicCompat.UPGRADE_LINES}) count as two used upgrades of the eight lines;
     * a non-fusion modifier (soulbound) does not count. No module line, since a core hosts none.
     */
    @Test
    void evolvedCountsOnlyModifiersInTheFusionRoster() {
        ItemStack stack = blankStack();
        stack.set(ForgeweaveDataComponents.TRAITS.get(), List.of(id("evolved3"), id("chaosmark")));
        stack.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(id("haste"), 100),
                new ModifierEntry(id("sharpness"), 144),
                new ModifierEntry(id("soulbound"), 1)));

        assertEquals(List.of(Component.translatable("tooltip.forgeweave.trait.evolved.fusion", 2, 8)
                        .withStyle(ChatFormatting.GRAY)),
                stateLines(ForgeweaveTraits.EVOLVED3, stack));
    }

    /** A head-and-handle mix of a core and a weld reads both lines, in that order: modules first. */
    @Test
    void evolvedAMixedToolReadsBothLines() {
        ItemStack stack = blankStack();
        stack.set(ForgeweaveDataComponents.TRAITS.get(), List.of(id("evolved"), id("stonewake"), id("soulrend")));

        assertEquals(List.of(
                        Component.translatable("tooltip.forgeweave.trait.evolved", 0, 12).withStyle(ChatFormatting.GRAY),
                        Component.translatable("tooltip.forgeweave.trait.evolved.fusion", 0, 8).withStyle(ChatFormatting.GRAY)),
                stateLines(ForgeweaveTraits.EVOLVED, stack));
    }

    /** A tool with no tier marker at all: every level stays silent, none of them are "the" level. */
    @Test
    void evolvedWithNoTraitAtAllShowsNothingFromAnyLevel() {
        ItemStack stack = blankStack();

        assertEquals(List.of(), stateLines(ForgeweaveTraits.EVOLVING, stack));
        assertEquals(List.of(), stateLines(ForgeweaveTraits.EVOLVED, stack));
        assertEquals(List.of(), stateLines(ForgeweaveTraits.EVOLVED2, stack));
        assertEquals(List.of(), stateLines(ForgeweaveTraits.EVOLVED3, stack));
    }

    /**
     * Checklist line 90: bracing plate's line grows with each blow taken, 1 protection point (4% of the
     * post-armor blow) a stack up to its 3, and clears once the stacks lapse.
     */
    @Test
    void bracingplateReadsHowBracedThePieceIsThenClearsWhenTheStacksLapse() {
        ItemStack stack = blankStack();
        for (int stacks = 1; stacks <= 3; stacks++) {
            stack.set(ForgeweaveDataComponents.RESISTANCE_STACKS.get(), new TraitStacks(stacks, 120));
            String percent = Integer.toString(4 * stacks);
            assertEquals(List.of(Component.translatable("tooltip.forgeweave.trait.resistance_stacks", percent, stacks, 3)
                            .withStyle(ChatFormatting.GRAY)),
                    stateLines(ForgeweaveTraits.BRACINGPLATE, stack));
        }
        assertEquals("Braced: 12% off a blow, 3 of 3 stacks",
                ShippedLanguage.render(stateLines(ForgeweaveTraits.BRACINGPLATE, stack).get(0)));

        stack.set(ForgeweaveDataComponents.RESISTANCE_STACKS.get(), new TraitStacks(3, 1));
        ForgeweaveTraits.decayStack(stack, ForgeweaveDataComponents.RESISTANCE_STACKS.get());
        assertEquals(List.of(), stateLines(ForgeweaveTraits.BRACINGPLATE, stack), "lapsed stacks show nothing");
    }

    /**
     * Checklist line 90: last breath's cooldown counts down in seconds, never ticks, and the line goes
     * once the piece can save again.
     */
    @Test
    void lastbreathCountsTheCooldownDownInSecondsThenClears() {
        ItemStack stack = blankStack();
        assertEquals(List.of(), stateLines(ForgeweaveTraits.LASTBREATH, stack), "a ready piece shows nothing");

        stack.set(ForgeweaveDataComponents.DEATH_SAVE_COOLDOWN.get(), new TraitStacks(1, 6000));
        assertEquals(List.of(Component.translatable("tooltip.forgeweave.trait.death_save.cooldown", "300")
                        .withStyle(ChatFormatting.GRAY)),
                stateLines(ForgeweaveTraits.LASTBREATH, stack));
        assertEquals("Can save again in 300s",
                ShippedLanguage.render(stateLines(ForgeweaveTraits.LASTBREATH, stack).get(0)));

        stack.set(ForgeweaveDataComponents.DEATH_SAVE_COOLDOWN.get(), new TraitStacks(1, 40));
        assertEquals("Can save again in 2s",
                ShippedLanguage.render(stateLines(ForgeweaveTraits.LASTBREATH, stack).get(0)));

        stack.set(ForgeweaveDataComponents.DEATH_SAVE_COOLDOWN.get(), new TraitStacks(1, 1));
        ForgeweaveTraits.decayStack(stack, ForgeweaveDataComponents.DEATH_SAVE_COOLDOWN.get());
        assertEquals(List.of(), stateLines(ForgeweaveTraits.LASTBREATH, stack), "a spent cooldown shows nothing");
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("forgeweave", path);
    }
}
