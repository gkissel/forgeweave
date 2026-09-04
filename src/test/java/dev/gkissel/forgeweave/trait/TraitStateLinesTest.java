package dev.gkissel.forgeweave.trait;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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

    private static final ResourceLocation ZOMBIE = ResourceLocation.withDefaultNamespace("zombie");
    private static final ResourceLocation SKELETON = ResourceLocation.withDefaultNamespace("skeleton");
    private static final ResourceLocation SPIDER = ResourceLocation.withDefaultNamespace("spider");
    private static final ResourceLocation CREEPER = ResourceLocation.withDefaultNamespace("creeper");

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
     * A head-and-handle mix of evolved I and evolved II: the tool's overall level is the higher one
     * (issue #955's "highest wins"), so only {@code EVOLVED2} emits a line and {@code EVOLVED} stays
     * silent rather than showing a second, contradicting one.
     */
    @Test
    void evolvedTheHighestLevelAcrossPartsIsTheOnlyOneThatSpeaks() {
        ItemStack stack = blankStack();
        stack.set(ForgeweaveDataComponents.TRAITS.get(),
                List.of(id("evolved"), id("evolved2")));

        assertEquals(List.of(), stateLines(ForgeweaveTraits.EVOLVED, stack),
                "evolved I is outranked by evolved II on another part");
        assertEquals(List.of(Component.translatable("tooltip.forgeweave.trait.evolved", 0, 4)
                        .withStyle(ChatFormatting.GRAY)),
                stateLines(ForgeweaveTraits.EVOLVED2, stack));
    }

    /**
     * Two fusion-upgrade modifiers already applied (haste and sharpness, both in
     * {@code ForgeweaveDraconicCompat.UPGRADE_LINES}) count as two used upgrades against evolved III's
     * allowance of 8; a non-fusion modifier (soulbound) does not count.
     */
    @Test
    void evolvedCountsOnlyModifiersInTheFusionRoster() {
        ItemStack stack = blankStack();
        stack.set(ForgeweaveDataComponents.TRAITS.get(), List.of(id("evolved3")));
        stack.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(
                new ModifierEntry(id("haste"), 100),
                new ModifierEntry(id("sharpness"), 144),
                new ModifierEntry(id("soulbound"), 1)));

        assertEquals(List.of(Component.translatable("tooltip.forgeweave.trait.evolved", 2, 8)
                        .withStyle(ChatFormatting.GRAY)),
                stateLines(ForgeweaveTraits.EVOLVED3, stack));
    }

    /** A tool with no {@code evolved} trait at all: every level stays silent, none of them are "the" level. */
    @Test
    void evolvedWithNoTraitAtAllShowsNothingFromAnyLevel() {
        ItemStack stack = blankStack();

        assertEquals(List.of(), stateLines(ForgeweaveTraits.EVOLVED, stack));
        assertEquals(List.of(), stateLines(ForgeweaveTraits.EVOLVED2, stack));
        assertEquals(List.of(), stateLines(ForgeweaveTraits.EVOLVED3, stack));
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }
}
