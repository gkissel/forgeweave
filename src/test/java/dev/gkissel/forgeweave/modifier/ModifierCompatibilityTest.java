package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;

import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * Parity audit 2026-08-18 T23 (issue #454): upstream 1.12's {@code Modifier#canApply} refusal layer
 * -- every {@code canApplyTogether(IToolMod)} override in the clone ({@code ModSilktouch},
 * {@code TraitSqueaky}, {@code TraitAutosmelt}) -- verified against tinkers-1.12 @ {@code c01173c0}.
 */
class ModifierCompatibilityTest {

    private static final ToolStats.Stats PICKAXE_STATS = new ToolStats.Stats(160, 4.0F, 3.0F);
    private static final ResourceLocation SILKY = id("silky");
    private static final ResourceLocation LUCK = id("luck");
    private static final ResourceLocation SQUEAKY = id("squeaky");
    private static final ResourceLocation AUTOSMELT = id("autosmelt");
    private static RegistryOps<JsonElement> ops;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        ops = RegistryOps.create(JsonOps.INSTANCE, RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
    }

    /** {@code ModSilktouch#canApplyTogether(IToolMod)}: not with {@code modLuck}. */
    @Test
    void silkyIsRefusedOnALuckTool() {
        ItemStack tool = pickaxe(List.of(), List.of(new ModifierEntry(LUCK, 60)));
        ModifierApplication.Outcome outcome = ModifierApplication.apply(recipe("silky.json"), tool, 1, 0);
        assertRefused(outcome, "gui.forgeweave.modifier.incompatible_modifiers");
    }

    /** The reverse direction: {@code Modifier#canApply} checks {@code mod.canApplyTogether(this)} too. */
    @Test
    void luckIsRefusedOnASilkyTool() {
        ItemStack tool = pickaxe(List.of(), List.of(new ModifierEntry(SILKY, 1)));
        ModifierApplication.Outcome outcome = ModifierApplication.apply(recipe("luck.json"), tool, 60, 0);
        assertRefused(outcome, "gui.forgeweave.modifier.incompatible_modifiers");
    }

    /** {@code TraitSqueaky#canApplyTogether(IToolMod)}: not with {@code modLuck} or {@code modSilktouch}. */
    @Test
    void luckAndSilkyAreRefusedOnASqueakyTool() {
        ItemStack tool = pickaxe(List.of(SQUEAKY), List.of());
        assertRefused(ModifierApplication.apply(recipe("luck.json"), tool, 60, 0),
                "gui.forgeweave.modifier.incompatible_trait");
        assertRefused(ModifierApplication.apply(recipe("silky.json"), tool, 1, 0),
                "gui.forgeweave.modifier.incompatible_trait");
    }

    /** {@code TraitAutosmelt#canApplyTogether(IToolMod)}: not with {@code modSilktouch} -- but luck is fine. */
    @Test
    void silkyIsRefusedOnAnAutosmeltToolButLuckIsNot() {
        ItemStack tool = pickaxe(List.of(AUTOSMELT), List.of());
        assertRefused(ModifierApplication.apply(recipe("silky.json"), tool, 1, 0),
                "gui.forgeweave.modifier.incompatible_trait");
        assertFalse(ModifierApplication.apply(recipe("luck.json"), tool, 60, 0).output().isEmpty(),
                "upstream TraitAutosmelt only excludes squeaky and silktouch; luck applies");
    }

    /** Haste has no {@code canApplyTogether} override upstream, so it lands on anything. */
    @Test
    void anUnrelatedModifierStillAppliesToASqueakyTool() {
        ItemStack tool = pickaxe(List.of(SQUEAKY), List.of(new ModifierEntry(LUCK, 60)));
        assertFalse(ModifierApplication.apply(recipe("haste.json"), tool, 1, 0).output().isEmpty());
    }

    /**
     * {@code ModExtraTrait#canApplyTogether}: an embossment is refused whenever any donor trait is --
     * embossing firewood (autosmelt) onto a silky tool, sponge (squeaky) onto a luck tool, or either
     * onto a tool carrying the other.
     */
    @Test
    void embossingADonorTraitIncompatibleWithTheToolIsRefused() {
        Component name = Component.literal("embossment");
        assertTrue(ModifierCompatibility.refusal(pickaxe(List.of(), List.of(new ModifierEntry(SILKY, 1))),
                AUTOSMELT, name).isPresent());
        assertTrue(ModifierCompatibility.refusal(pickaxe(List.of(), List.of(new ModifierEntry(LUCK, 1))),
                SQUEAKY, name).isPresent());
        // TraitAutosmelt#canApplyTogether names squeaky, and ModExtraTrait checks both directions.
        assertTrue(ModifierCompatibility.refusal(pickaxe(List.of(SQUEAKY), List.of()),
                AUTOSMELT, name).isPresent());
        assertTrue(ModifierCompatibility.refusal(pickaxe(List.of(AUTOSMELT), List.of()),
                SQUEAKY, name).isPresent());
        assertTrue(ModifierCompatibility.refusal(pickaxe(List.of(AUTOSMELT), List.of()),
                id("magnetic2"), name).isEmpty(), "an unrelated donor trait embosses fine");
    }

    // ------------------------------------------------------------------ helpers

    private static void assertRefused(ModifierApplication.Outcome outcome, String key) {
        assertTrue(outcome.output().isEmpty(), "expected a refusal, got " + outcome.output());
        Optional<String> actual = Optional.ofNullable(outcome.rejection())
                .map(component -> ((TranslatableContents) component.getContents()).getKey());
        assertEquals(Optional.of(key), actual);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("forgeweave", path);
    }

    private static ItemStack pickaxe(List<ResourceLocation> traits, List<ModifierEntry> modifiers) {
        ItemStack stack = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), PICKAXE_STATS);
        stack.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        stack.set(ForgeweaveDataComponents.MODIFIERS.get(), modifiers);
        stack.set(DataComponents.MAX_DAMAGE, PICKAXE_STATS.durability());
        stack.set(DataComponents.DAMAGE, 0);
        stack.set(DataComponents.TOOL, new Tool(
                List.of(Tool.Rule.deniesDrops(BlockTags.INCORRECT_FOR_WOODEN_TOOL),
                        Tool.Rule.minesAndDrops(BlockTags.MINEABLE_WITH_PICKAXE, PICKAXE_STATS.miningSpeed())),
                1.0F, 1));
        return stack;
    }

    private static ModifierRecipe recipe(String fileName) {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/" + fileName;
        JsonElement json;
        try (InputStream in = ModifierCompatibilityTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new AssertionError("missing shipped modifier recipe: " + path);
            }
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
        return ModifierRecipe.CODEC.parse(ops, json).getOrThrow();
    }
}
