package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;

/**
 * M3.5 issue #394: the shortbow's stat math against upstream {@code ShortBow#buildTagData} --
 * {@code data.head(limb1, limb2)}, {@code data.limb(limb1, limb2)}, {@code data.bowstring(string)}
 * -- and {@code BowCore#getDrawbackProgress}'s formula, with the shipped iron/bone/string values
 * ({@code TinkerMaterials#registerBowMaterialStats}, ported at #392).
 */
class ShortbowStatsTest {

    private static final float DELTA = 1.0e-5f;

    /** {@code TinkerMaterials}: iron head 204/6.0/4.0, bow 0.5/1.5/7.0. */
    private static final Material IRON = limb(204, 6.0f, 4.0f, 0.5f, 1.5f, 7.0f);
    /** bone head 200/5.09/2.5, bow 0.95/1.15/0.0. */
    private static final Material BONE = limb(200, 5.09f, 2.5f, 0.95f, 1.15f, 0.0f);
    private static final Material STRING = string(1.0f);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /** {@code ToolNBT#head}: plain average of the two limbs' HEAD blocks; string x1.0 leaves it. */
    @Test
    void meleeHalfIsTheTwoLimbHeadAverageTimesTheStringModifier() {
        ToolStats.Stats stats = ToolConstants.compute(ToolConstants.SHORTBOW, List.of(IRON, BONE, STRING));
        assertEquals(202, stats.durability(), "(204 + 200) / 2");
        assertEquals(5.545f, stats.miningSpeed(), DELTA, "(6.0 + 5.09) / 2");
        assertEquals(3.25f, stats.attackDamage(), DELTA, "(4.0 + 2.5) / 2");

        // ProjectileLauncherNBT#bowstring: durability = round(durability * modifier), floor 1.
        ToolStats.Stats strong = ToolConstants.compute(ToolConstants.SHORTBOW, List.of(IRON, BONE, string(1.5f)));
        assertEquals(303, strong.durability(), "round(202 * 1.5)");
        ToolStats.Stats frayed = ToolConstants.compute(ToolConstants.SHORTBOW, List.of(IRON, BONE, string(0.001f)));
        assertEquals(1, frayed.durability(), "floor 1");
    }

    /** {@code ProjectileLauncherNBT#limb}: field-by-field average over the limbs, floors on speed and range. */
    @Test
    void launcherHalfAveragesTheLimbsBowBlocks() {
        LauncherStats stats = LauncherStats.of(ToolConstants.SHORTBOW, List.of(IRON, BONE, STRING)).orElseThrow();
        assertEquals(0.725f, stats.drawSpeed(), DELTA, "(0.5 + 0.95) / 2");
        assertEquals(1.325f, stats.range(), DELTA, "(1.5 + 1.15) / 2");
        assertEquals(3.5f, stats.bonusDamage(), DELTA, "(7.0 + 0.0) / 2");

        // A negative bonus (paper's -2.0) averages in as-is; only draw speed and range are floored.
        LauncherStats paper = LauncherStats.of(ToolConstants.SHORTBOW,
                List.of(limb(1, 1.0f, 0.0f, 1.5f, 0.4f, -2.0f), limb(1, 1.0f, 0.0f, 0.0f, 0.0f, -2.0f), STRING)).orElseThrow();
        assertEquals(0.75f, paper.drawSpeed(), DELTA);
        assertEquals(0.2f, paper.range(), DELTA);
        assertEquals(-2.0f, paper.bonusDamage(), DELTA);
        LauncherStats floored = LauncherStats.of(ToolConstants.SHORTBOW,
                List.of(limb(1, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f), limb(1, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f), STRING)).orElseThrow();
        assertEquals(0.001f, floored.drawSpeed(), DELTA);
        assertEquals(0.001f, floored.range(), DELTA);
    }

    /** No LIMB slot, no launcher stats: every non-bow's component set is untouched. */
    @Test
    void nonBowsHaveNoLauncherStats() {
        Material plain = new Material(new Material.Head(100, 4.0f, 6.0f), new Material.Handle(1.0f, 20), 10,
                incorrectForStone(), new Material.Traits(List.of(), List.of()), List.of(), Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));
        assertEquals(Optional.empty(), LauncherStats.of(ToolConstants.BROADSWORD, List.of(plain, plain, plain)));
    }

    /**
     * {@code BowCore#getDrawbackProgress(ItemStack, int)}: {@code min(1, drawSpeed * ticks /
     * drawTime)}, with the shortbow's {@code getDrawTime() = 12}. Wood (1.0) is fully drawn at 12
     * ticks; a 0.725 iron/bone bow at ~17; a paper 1.5 bow at 8; and progress never exceeds 1.
     */
    @Test
    void drawProgressIsDrawSpeedTimesTicksOverDrawTime() {
        BowItem bow = ForgeweaveItems.TOOL_SHORTBOW.get();
        assertEquals(12, bow.drawTime());

        assertEquals(0.5f, bow.drawbackProgress(withDrawSpeed(1.0f), 6), DELTA);
        assertEquals(1.0f, bow.drawbackProgress(withDrawSpeed(1.0f), 12), DELTA);
        assertEquals(1.0f, bow.drawbackProgress(withDrawSpeed(1.0f), 40), DELTA, "capped at 1");
        assertEquals(0.725f * 10 / 12, bow.drawbackProgress(withDrawSpeed(0.725f), 10), DELTA);
        assertTrue(bow.drawbackProgress(withDrawSpeed(0.725f), 16) < 1.0f);
        assertEquals(1.0f, bow.drawbackProgress(withDrawSpeed(0.725f), 17), DELTA);
        assertEquals(1.0f, bow.drawbackProgress(withDrawSpeed(1.5f), 8), DELTA);
        // An unassembled stack (creative tab) draws at speed 1.
        assertEquals(0.5f, bow.drawbackProgress(new ItemStack(bow), 6), DELTA);
    }

    private static ItemStack withDrawSpeed(float drawSpeed) {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_SHORTBOW.get());
        stack.set(ForgeweaveDataComponents.LAUNCHER_STATS.get(), new LauncherStats(drawSpeed, 1.0f, 0.0f));
        return stack;
    }

    private static Material limb(int durability, float miningSpeed, float attack, float drawspeed, float range,
            float bonusDamage) {
        return new Material(Optional.of(new Material.Head(durability, miningSpeed, attack)), Optional.empty(),
                Optional.empty(), incorrectForStone(), new Material.Traits(List.of(), List.of()), List.of(),
                Ingredient.of(Items.STICK), TextColor.fromRgb(0xFFFFFF),
                Optional.of(new Material.Bow(drawspeed, range, bonusDamage)), Optional.empty());
    }

    private static Material string(float modifier) {
        return new Material(Optional.empty(), Optional.empty(), Optional.empty(), incorrectForStone(),
                new Material.Traits(List.of(), List.of()), List.of(), Ingredient.of(Items.STRING), TextColor.fromRgb(0xFFFFFF),
                Optional.empty(), Optional.of(new Material.Bowstring(modifier)));
    }

    private static TagKey<net.minecraft.world.level.block.Block> incorrectForStone() {
        return TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool"));
    }
}
