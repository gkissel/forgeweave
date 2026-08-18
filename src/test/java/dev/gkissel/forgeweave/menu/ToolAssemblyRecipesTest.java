package dev.gkissel.forgeweave.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

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
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Pins the hatchet's flat attack bonus (parity audit 2026-08-18 T65, issue #496) against upstream
 * 1.12's {@code Hatchet#buildTagData}:
 *
 * <pre>
 * ToolNBT data = buildDefaultTag(materials);
 * data.attack += 0.5f;
 * </pre>
 *
 * <p>{@link ToolAssemblyRecipes#HATCHET} is the exact entry the Tool Station assembles a hatchet
 * from ({@code ToolAssemblyRecipes#ENTRIES}), so this exercises the real assembly formula rather
 * than a copy of it. {@link ToolStats#compute} is upstream's {@code buildDefaultTag} half, with no
 * flat bonus of its own -- the delta between the two pins the +0.5.
 */
class ToolAssemblyRecipesTest {

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void assembledAttackIsHalfAPointAboveThePlainHeadAverage() {
        Material axeHead = material(120, 4.0f, 3.0f, 0.5f, -50, 20);
        Material binding = material(35, 2.0f, 2.0f, 1.0f, 25, 15);
        Material handle = binding;

        ToolStats.Stats plain = ToolStats.compute(axeHead, binding, handle);
        ToolStats.Stats assembled =
                ToolConstants.compute(ToolAssemblyRecipes.HATCHET, List.of(axeHead, binding, handle));

        assertEquals(plain.attackDamage() + 0.5f, assembled.attackDamage(), 1.0e-5f,
                "upstream Hatchet#buildTagData adds a flat 0.5 attack on top of the plain head average");
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
