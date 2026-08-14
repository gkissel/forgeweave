package dev.gkissel.forgeweave.jei;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * The subtype key each JEI-registered item family is distinguished by (issue #307). A bare
 * {@code ItemStack} carries no material info in its {@code Item} identity -- it is all in data
 * components -- so with no interpreter every material variant of a part or tool collapsed into one
 * JEI entry.
 *
 * <p>Kept free of any JEI import (JEI is {@code compileOnly}, see build.gradle's dependency comment)
 * so {@code SubtypeKeysTest} can pin these methods against a plain {@code ItemStack} without JEI on
 * the test classpath; {@link ForgeweaveJeiPlugin#registerItemSubtypes} is the only caller, wrapping
 * each method as a JEI {@code IIngredientSubtypeInterpreter} and mapping a {@code null} result to
 * that API's {@code NONE} sentinel.
 *
 * <p>Mirrors upstream 1.12's {@code jei.interpreter} package -- three of its four interpreters
 * ({@code ToolPartSubtypeInterpreter}, {@code ToolSubtypeInterpreter}, {@code TableSubtypeInterpreter};
 * Forgeweave's patterns are one item per part type already, with no per-material component, so there
 * is no Forgeweave equivalent of upstream's fourth, {@code PatternSubtypeInterpreter}) -- adapted to
 * the plain {@code String} shape modern JEI's {@code IIngredientSubtypeInterpreter} already returns,
 * so no {@code ItemDamage} meta prefix (1.12's own subtype key ingredient, replaced here by data
 * components) is needed.
 */
public final class SubtypeKeys {

    /** {@code PartItem}s (upstream {@code ToolPartSubtypeInterpreter}): keyed on the material id. */
    public static String part(ItemStack stack) {
        ResourceLocation materialId = stack.get(ForgeweaveDataComponents.MATERIAL.get());
        return materialId == null ? null : materialId.toString();
    }

    /**
     * {@code ToolItem}s (upstream {@code ToolSubtypeInterpreter}): keyed on every part's material,
     * in slot order, so two tools built from the same head but a different handle are still distinct.
     */
    public static String tool(ItemStack stack) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null || materials.parts().isEmpty()) {
            return null;
        }
        StringBuilder key = new StringBuilder();
        for (ResourceLocation part : materials.parts()) {
            if (!key.isEmpty()) {
                key.append(',');
            }
            key.append(part);
        }
        return key.toString();
    }

    /**
     * Texture-bearing station items (upstream {@code TableSubtypeInterpreter}): keyed on the wood
     * block id issue #43's retexturing stores in {@link ForgeweaveDataComponents#TEXTURE}.
     */
    public static String texture(ItemStack stack) {
        ResourceLocation textureId = stack.get(ForgeweaveDataComponents.TEXTURE.get());
        return textureId == null ? null : textureId.toString();
    }

    private SubtypeKeys() {}
}
