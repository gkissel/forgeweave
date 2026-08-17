package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;

/**
 * M3.5 issue #395: the two Tool Forge-tier bows' stat math against upstream's own
 * {@code buildTagData}s, which are the shortbow's chain plus one step each.
 *
 * <p>{@code LongBow#buildTagData}: {@code head(limb1, limb2)}, {@code limb(limb1, limb2)},
 * {@code extra(grip)}, {@code bowstring(string)}, then {@code data.durability *= 1.4f}.
 *
 * <p>{@code CrossBow#buildTagData}: {@code head(head)} and {@code limb(limb)} off the one limb,
 * {@code extra(binding, bodyExtra)} -- <em>two</em> extras, the binding's and the body rod's --
 * {@code handle(body)}, {@code bowstring(string)}, then {@code data.bonusDamage *= 1.5f}.
 */
class ForgeBowStatsTest {

    private static final float DELTA = 1.0e-5f;

    /** {@code TinkerMaterials}: iron head 204/6.0/4.0, handle 0.85/60, extra 50, bow 0.5/1.5/7.0. */
    private static final Material IRON = full(204, 6.0f, 4.0f, 0.85f, 60, 50, 0.5f, 1.5f, 7.0f);
    /** bone head 200/5.09/2.5, handle 1.1/50, extra 65, bow 0.95/1.15/0.0. */
    private static final Material BONE = full(200, 5.09f, 2.5f, 1.1f, 50, 65, 0.95f, 1.15f, 0.0f);
    private static final Material STRING = string(1.0f);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * The longbow adds a grip to the shortbow's chain and closes with
     * {@code LongBow#DURABILITY_MODIFIER}, which runs <em>after</em> the bowstring step -- upstream
     * applies it as the last line of {@code buildTagData}, past {@code data.bowstring(...)}.
     */
    @Test
    void longbowAddsTheGripThenItsOwnDurabilityMultiplier() {
        ToolStats.Stats stats = ToolConstants.compute(ToolConstants.LONGBOW, List.of(IRON, BONE, IRON, STRING));
        // head avg (204 + 200) / 2 = 202, + the plate's 50 = 252, x string 1.0, x 1.4 = 352.
        assertEquals(352, stats.durability());
        assertEquals(5.545f, stats.miningSpeed(), DELTA, "(6.0 + 5.09) / 2 -- the melee half is the shortbow's");
        assertEquals(3.25f, stats.attackDamage(), DELTA, "(4.0 + 2.5) / 2");

        // The bowstring modifier lands before the 1.4, not after: a 0.5 string halves 252 to 126 and
        // 1.4 takes that to 176, where the other order would give 176.4 -> 176 too, so use a string
        // whose two orders differ (0.3: 252*0.3=76 -> 106; 252*1.4=352 -> 105).
        assertEquals(106, ToolConstants.compute(ToolConstants.LONGBOW, List.of(IRON, BONE, IRON, string(0.3f)))
                .durability(), "round(252 * 0.3) = 76, then x1.4 = 106");
    }

    /** {@code ProjectileLauncherNBT#limb} over two limbs; the longbow has no bonus multiplier. */
    @Test
    void longbowLauncherStatsAreThePlainLimbAverage() {
        LauncherStats stats = LauncherStats.of(ToolConstants.LONGBOW, List.of(IRON, BONE, IRON, STRING)).orElseThrow();
        assertEquals(0.725f, stats.drawSpeed(), DELTA, "(0.5 + 0.95) / 2");
        assertEquals(1.325f, stats.range(), DELTA, "(1.5 + 1.15) / 2");
        assertEquals(3.5f, stats.bonusDamage(), DELTA, "(7.0 + 0.0) / 2, no multiplier");
    }

    /**
     * The crossbow body's double duty: {@code PartMaterialType.crossbow} names HANDLE and EXTRA, and
     * {@code buildTagData} spends both -- the rod's extra durability averages in with the binding's,
     * and its handle modifier and durability apply on top.
     */
    @Test
    void crossbowBodyCountsAsBothAnExtraAndAHandle() {
        ToolStats.Stats stats = ToolConstants.compute(ToolConstants.CROSSBOW, List.of(IRON, IRON, IRON, STRING));
        // head 204 (one limb) + avg(binding 50, body 50) = 254; x the body's 0.85 = 216; + its 60 = 276.
        assertEquals(276, stats.durability());
        assertEquals(6.0f, stats.miningSpeed(), DELTA, "the single limb's HEAD block, unaveraged");
        assertEquals(4.0f, stats.attackDamage(), DELTA);

        // A bone body proves both halves move: extras avg(50 iron binding, 65 bone body) = 58 (round
        // 57.5), so 204 + 58 = 262; x bone's 1.1 = 288; + bone's 50 = 338.
        assertEquals(338, ToolConstants.compute(ToolConstants.CROSSBOW, List.of(BONE, IRON, IRON, STRING))
                .durability());
    }

    /** {@code CrossBow#buildTagData}'s closing {@code data.bonusDamage *= 1.5f}, and nothing else scaled. */
    @Test
    void crossbowMultipliesOnlyItsBonusDamage() {
        LauncherStats stats = LauncherStats.of(ToolConstants.CROSSBOW, List.of(IRON, IRON, IRON, STRING)).orElseThrow();
        assertEquals(0.5f, stats.drawSpeed(), DELTA, "one limb, so iron's own draw speed");
        assertEquals(1.5f, stats.range(), DELTA, "unscaled");
        assertEquals(10.5f, stats.bonusDamage(), DELTA, "7.0 * 1.5");

        assertEquals(1.5f, ToolConstants.CROSSBOW.bonusDamageMultiplier(), DELTA);
        assertEquals(1.0f, ToolConstants.LONGBOW.bonusDamageMultiplier(), DELTA);
        assertEquals(1.0f, ToolConstants.SHORTBOW.bonusDamageMultiplier(), DELTA);
    }

    /**
     * {@code getDrawTime()}/{@code baseProjectileSpeed()}/{@code baseInaccuracy()}: the longbow
     * overrides all three (30 / 5.5 / 1.2); the crossbow overrides the first two (45 / 7) and,
     * uniquely, does <em>not</em> override inaccuracy, so it keeps {@code BowCore}'s own 0f -- it
     * extends {@code BowCore} directly, not {@code ShortBow}.
     */
    @Test
    void drawTimesAreUpstreamsOwn() {
        BowItem longbow = ForgeweaveItems.TOOL_LONGBOW.get();
        BowItem crossbow = ForgeweaveItems.TOOL_CROSSBOW.get();
        assertEquals(30, longbow.drawTime());
        assertEquals(45, crossbow.drawTime());
        assertEquals(12, ForgeweaveItems.TOOL_SHORTBOW.get().drawTime(), "unchanged by #395");
    }

    private static Material full(int durability, float miningSpeed, float attack, float handleModifier,
            int handleDurability, int extraDurability, float drawspeed, float range, float bonusDamage) {
        return new Material(Optional.of(new Material.Head(durability, miningSpeed, attack)),
                Optional.of(new Material.Handle(handleModifier, handleDurability)), Optional.of(extraDurability),
                incorrectForStone(), new Material.Traits(List.of(), List.of()), List.of(),
                Ingredient.of(Items.STICK), TextColor.fromRgb(0xFFFFFF),
                Optional.of(new Material.Bow(drawspeed, range, bonusDamage)), Optional.empty());
    }

    private static Material string(float modifier) {
        return new Material(Optional.empty(), Optional.empty(), Optional.empty(), incorrectForStone(),
                new Material.Traits(List.of(), List.of()), List.of(), Ingredient.of(Items.STRING),
                TextColor.fromRgb(0xFFFFFF), Optional.empty(), Optional.of(new Material.Bowstring(modifier)));
    }

    private static TagKey<net.minecraft.world.level.block.Block> incorrectForStone() {
        return TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool"));
    }
}
