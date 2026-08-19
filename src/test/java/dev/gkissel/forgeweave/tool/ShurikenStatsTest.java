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
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

import dev.gkissel.forgeweave.material.Material;

/**
 * Pins {@link ToolConstants#SHURIKEN} against upstream 1.12's {@code tools/ranged/item/Shuriken.java}
 * (issue #448, parity audit T17 -- the first {@code ProjectileCore} consumer):
 *
 * <ul>
 *   <li>{@code buildTagData} feeds all four blades' HEAD blocks to {@code data.head(...)} <em>and</em>
 *       their EXTRA blocks to {@code data.extra(...)}, so durability is the head average plus the
 *       extra-durability average and attack/speed are plain four-way head averages -- that dual read
 *       is {@link ToolConstants.Role#SHURIKEN_BLADE};</li>
 *   <li>{@code data.attack += 1f} is the entry's flat attack bonus;</li>
 *   <li>{@code damagePotential() = 0.7f}, {@code ProjectileCore#attackSpeed() = 100};</li>
 *   <li>{@code getRepairParts() = {0, 1, 2, 3}} with no modifier override -- every blade repairs at 1.</li>
 * </ul>
 */
class ShurikenStatsTest {

    private static final float DELTA = 0.0001f;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final Material H1 = material(100, 4.0f, 6.0f, 20);
    private static final Material H2 = material(60, 2.0f, 2.0f, 30);

    @Test
    void fourBladesAverageHeadAndExtraBlocks() {
        // heads (100+100+60+60)/4 = 80, extra (20+20+30+30)/4 = 25 -> 105 durability;
        // attack (6+6+2+2)/4 + 1 = 5; mining speed (4+4+2+2)/4 = 3.
        ToolStats.Stats stats = ToolConstants.compute(ToolConstants.SHURIKEN, List.of(H1, H1, H2, H2));
        assertEquals(105, stats.durability());
        assertEquals(5.0f, stats.attackDamage(), DELTA);
        assertEquals(3.0f, stats.miningSpeed(), DELTA);
    }

    @Test
    void constantsMatchUpstream() {
        assertEquals(100.0f, ToolConstants.SHURIKEN.attackSpeed(), DELTA);
        assertEquals(0.7f, ToolConstants.SHURIKEN.damagePotential(), DELTA);
        assertEquals(1.0f, ToolConstants.SHURIKEN.flatAttackBonus(), DELTA);
        assertEquals(ToolConstants.DEFAULT_DAMAGE_CUTOFF, ToolConstants.SHURIKEN.damageCutoff(), DELTA);
        assertEquals(4, ToolConstants.SHURIKEN.parts().size());
        assertTrue(ToolConstants.SHURIKEN.parts().stream()
                .allMatch(slot -> slot.role() == ToolConstants.Role.SHURIKEN_BLADE
                        && slot.partId().equals("knife_blade")),
                "all four slots are knife blades");
    }

    /** Upstream {@code Shuriken#getRepairParts() = {0, 1, 2, 3}}, no repair-modifier override. */
    @Test
    void everyBladeRepairsAtFactorOne() {
        List<ToolConstants.RepairPart> slots = ToolConstants.SHURIKEN.repairSlots();
        assertEquals(4, slots.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(i, slots.get(i).slot());
            assertEquals(1.0f, slots.get(i).modifier(), DELTA);
        }
    }

    /**
     * The shuriken's four blades draw as four {@code head} layers -- upstream's
     * {@code shuriken.tcon.json} is layer0..layer3, one blade each. Upstream declares no
     * {@code broken<N>} key for it, but Forgeweave's #284 invariant (Broken shows on the model)
     * gives the first blade the same chipped break the other five art-less tools take -- see
     * {@code ToolArt#BROKEN_LAYERS} and {@code scripts/derive_broken_art.py}.
     */
    @Test
    void artIsFourHeadLayersBreakingTheFirstBlade() {
        assertEquals(List.of("head", "head2", "head3", "head4"),
                ToolArt.layers(ToolConstants.SHURIKEN.parts()));
        assertEquals(List.of(0, 1, 2, 3), ToolArt.layerSlots(ToolConstants.SHURIKEN.parts()));
        assertEquals("head", ToolArt.brokenLayer("shuriken"));
    }

    private static Material material(int durability, float speed, float attack, int extra) {
        return new Material(
                Optional.of(new Material.Head(durability, speed, attack)),
                Optional.of(new Material.Handle(1.0f, 0)),
                Optional.of(extra),
                TagKey.create(Registries.BLOCK,
                        ResourceLocation.withDefaultNamespace("incorrect_for_wooden_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(new Material.CraftingItem(Ingredient.of(Items.STICK), 144)),
                Ingredient.of(Items.STICK),
                TextColor.fromRgb(0xFFFFFF),
                Optional.empty(),
                Optional.empty());
    }
}
