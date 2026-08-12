package dev.gkissel.forgeweave.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

import dev.gkissel.forgeweave.material.Material;

/**
 * Pins {@link ToolConstants#compute} -- and each {@link ToolConstants.Entry}'s attack speed,
 * damage potential and mining speed modifier -- against the formulas in {@link ToolConstants}'s
 * class javadoc, for every one of the 18 M3 tools (issue #153's verification requirement).
 *
 * <p>Two fixture materials exercise the averaging/weighting math: {@link #H1} on every single-head
 * tool (and the "primary" head/handle of a multi-head one), {@link #H2} standing in for a second,
 * differently-statted part wherever a tool has one (a second head, the extra/binding slot, or a
 * second handle), so a bug that reads the wrong slot or averages instead of summing (or vice versa)
 * changes the expected number. Float comparisons use a small delta -- {@code preAttackMultiplier}/
 * {@code durabilityMultiplier} values like {@code 1.3f} aren't exactly representable, so asserting
 * exact equality against a hand-typed literal would be pinning a rounding accident, not the formula.
 */
class ToolConstantsTest {

    private static final float DELTA = 0.0001f;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Material H1 = material(100, 4.0f, 6.0f, 1.0f, 20, 10);
    private static final Material H2 = material(60, 2.0f, 2.0f, 1.5f, 0, 30);

    @Test
    void everyM3ToolIsListedExactlyOnce() {
        assertEquals(18, ToolConstants.ALL.size());
        Set<String> ids = ToolConstants.ALL.stream().map(ToolConstants.Entry::id).collect(Collectors.toSet());
        assertEquals(18, ids.size(), "tool ids must be unique: " + ids);
    }

    @Test
    void broadsword() {
        // handle H2, head H1, extra H2: (100 + 30) * 1.5 + 0 = 195, * 1.1 -> 214; attack 6*1+1=7
        assertStats(ToolConstants.BROADSWORD, List.of(H2, H1, H2), 214, 4.0f, 7.0f);
        assertConstants(ToolConstants.BROADSWORD, 1.6f, 1.0f, 1.0f);
    }

    @Test
    void longsword() {
        assertStats(ToolConstants.LONGSWORD, List.of(H2, H1, H2), 204, 4.0f, 6.5f);
        assertConstants(ToolConstants.LONGSWORD, 1.4f, 1.1f, 1.0f);
    }

    @Test
    void rapier() {
        assertStats(ToolConstants.RAPIER, List.of(H2, H1, H2), 156, 4.0f, 6.0f);
        assertConstants(ToolConstants.RAPIER, 3.0f, 0.55f, 1.0f);
    }

    @Test
    void battlesign() {
        // handle H1, head H1, no extra: durability = round(100*1.0) + 20 = 120
        assertStats(ToolConstants.BATTLESIGN, List.of(H1, H1), 120, 4.0f, 6.0f);
        assertConstants(ToolConstants.BATTLESIGN, 1.2f, 0.86f, 1.0f);
    }

    @Test
    void fryingPan() {
        assertStats(ToolConstants.FRYING_PAN, List.of(H1, H1), 120, 4.0f, 6.0f);
        assertConstants(ToolConstants.FRYING_PAN, 1.4f, 1.0f, 1.0f);
    }

    @Test
    void mattock() {
        // axe H1, shovel H2: head avg dur (100+60)/2=80, attack (6+2)/2=4, speed (4+2)/2=3
        // handle H1: round(80*1.0)+20 = 100; attack 4*1+3=7
        assertStats(ToolConstants.MATTOCK, List.of(H1, H1, H2), 100, 3.0f, 7.0f);
        assertConstants(ToolConstants.MATTOCK, 0.9f, 0.90f, 0.95f);
    }

    @Test
    void kama() {
        // same handle/head/extra shape as broadsword's pre-multiplier durability (195), * 1.0 -> 195
        assertStats(ToolConstants.KAMA, List.of(H2, H1, H2), 195, 4.0f, 6.0f);
        assertConstants(ToolConstants.KAMA, 1.3f, 1.0f, 1.0f);
    }

    @Test
    void dagger() {
        // head H1, handle H1, no extra: durability = round(100*1.0) + 20 = 120; attack 6*1+3=9
        assertStats(ToolConstants.DAGGER, List.of(H1, H1), 120, 4.0f, 9.0f);
        assertConstants(ToolConstants.DAGGER, 2.0f, 1.0f, 1.0f);
    }

    @Test
    void battleaxe() {
        // heads H1+H2 (avg, not weighted): dur avg (100+60)/2=80, attack SUM 6+2=8, speed avg 3
        // durability skips extra/handle: 80 * 1.10 -> 88; attack (6+2)*1.2 = 9.6
        assertStats(ToolConstants.BATTLEAXE, List.of(H1, H1, H2, H2), 88, 3.0f, 9.6f);
        assertConstants(ToolConstants.BATTLEAXE, 0.95f, 1.0f, 0.6f);
    }

    @Test
    void scimitar() {
        assertStats(ToolConstants.SCIMITAR, List.of(H2, H1, H2), 195, 4.0f, 8.5f);
        assertConstants(ToolConstants.SCIMITAR, 1.8f, 1.0f, 1.0f);
    }

    @Test
    void katana() {
        assertStats(ToolConstants.KATANA, List.of(H2, H1, H2), 195, 4.0f, 8.75f);
        assertConstants(ToolConstants.KATANA, 1.6f, 1.0f, 1.0f);
    }

    @Test
    void warmace() {
        assertStats(ToolConstants.WARMACE, List.of(H2, H1, H2), 195, 4.0f, 10.0f);
        assertConstants(ToolConstants.WARMACE, 0.8f, 1.0f, 1.0f);
    }

    @Test
    void hammer() {
        // head H1 weight 2, two large plates H2: dur (100*2+60+60)/4=80, attack (6*2+2+2)/4=4, speed (4*2+2+2)/4=3
        // no extra; handle H1: round(80*1.0)+20 = 100, * 2.5 -> 250; attack 4*1+0=4
        assertStats(ToolConstants.HAMMER, List.of(H1, H1, H2, H2), 250, 3.0f, 4.0f);
        assertConstants(ToolConstants.HAMMER, 0.8f, 1.2f, 0.4f);
    }

    @Test
    void excavator() {
        // heads H1+H2 avg: dur 80, attack 4, speed 3; extra H2: +30 -> 110; handle H1: round(110)+20=130, *1.75 -> 227
        assertStats(ToolConstants.EXCAVATOR, List.of(H1, H1, H2, H2), 227, 3.0f, 4.0f);
        assertConstants(ToolConstants.EXCAVATOR, 0.7f, 1.25f, 0.28f);
    }

    @Test
    void lumberaxe() {
        // same shape as excavator: durability 130 pre-multiplier, * 2.0 -> 260; attack 4+2=6
        assertStats(ToolConstants.LUMBERAXE, List.of(H1, H1, H2, H2), 260, 3.0f, 6.0f);
        assertConstants(ToolConstants.LUMBERAXE, 0.8f, 1.2f, 0.35f);
    }

    @Test
    void scythe() {
        // single head H1 (dur100/atk6/speed4); extra H2 (+30 -> 130); two handles H1+H2:
        // modifier avg (1.0+1.5)/2=1.25 -> round(130*1.25)=163, + round((20+0)/2)=10 -> 173, * 2.2 -> 380
        assertStats(ToolConstants.SCYTHE, List.of(H1, H1, H2, H2), 380, 4.0f, 6.0f);
        assertConstants(ToolConstants.SCYTHE, 0.9f, 0.75f, 1.0f);
    }

    @Test
    void cleaver() {
        // heads H1+H2 avg: dur 80, attack 4, speed 3; extra H2: +30 -> 110; handle H1: round(110)+20=130, *2.0 -> 260
        // attack: 4*1.3+3 = 8.2
        assertStats(ToolConstants.CLEAVER, List.of(H1, H1, H2, H2), 260, 3.0f, 4.0f * 1.3f + 3.0f);
        assertConstants(ToolConstants.CLEAVER, 0.7f, 1.2f, 1.0f);
    }

    @Test
    void veinHammer() {
        // head H1 weight .75 + head H2 weight .25: dur (100*.75+60*.25)/1=90, attack (6*.75+2*.25)/1=5, speed (4*.75+2*.25)/1=3.5
        // extra H2: +30 -> 120; handle H1: round(120)+20=140, *5.0 -> 700; attack 5*1+3=8
        assertStats(ToolConstants.VEIN_HAMMER, List.of(H1, H1, H2, H2), 700, 3.5f, 8.0f);
        assertConstants(ToolConstants.VEIN_HAMMER, 0.85f, 1.25f, 0.3f);
    }

    private static void assertStats(ToolConstants.Entry entry, List<Material> materials, int expectedDurability,
            float expectedMiningSpeed, float expectedAttack) {
        ToolStats.Stats stats = ToolConstants.compute(entry, materials);
        assertEquals(expectedDurability, stats.durability(), entry.id() + " durability");
        assertEquals(expectedMiningSpeed, stats.miningSpeed(), DELTA, entry.id() + " mining speed");
        assertEquals(expectedAttack, stats.attackDamage(), DELTA, entry.id() + " attack damage");
    }

    private static void assertConstants(ToolConstants.Entry entry, float attackSpeed, float damagePotential,
            float miningSpeedModifier) {
        assertEquals(attackSpeed, entry.attackSpeed(), DELTA, entry.id() + " attack speed");
        assertEquals(damagePotential, entry.damagePotential(), DELTA, entry.id() + " damage potential");
        assertEquals(miningSpeedModifier, entry.miningSpeedModifier(), DELTA, entry.id() + " mining speed modifier");
    }

    private static Material material(int headDurability, float miningSpeed, float attackDamage,
            float handleDurabilityModifier, int handleDurability, int extraDurability) {
        return new Material(
                new Material.Head(headDurability, miningSpeed, attackDamage),
                new Material.Handle(handleDurabilityModifier, handleDurability),
                extraDurability,
                TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                Material.Traits.general(ResourceLocation.fromNamespaceAndPath("forgeweave", "test")),
                List.of(new Material.CraftingItem(Ingredient.of(Items.STICK), 1)),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF));
    }
}
