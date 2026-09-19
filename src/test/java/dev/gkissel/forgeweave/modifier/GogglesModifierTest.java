package dev.gkissel.forgeweave.modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * Issue #1007 (docs/SCOPE.md M8, Create compat): the {@code goggles} modifier's slot-free flag and
 * helmet-only shape. {@code goggles.json}'s reagent is {@code create:goggles}, an item this project
 * never puts on any classpath (build.gradle: Create is compileOnly, main-source only) -- so unlike
 * every other modifier's own test, this one reads the shipped JSON as plain Gson rather than through
 * {@link ModifierRecipe#CODEC}, which would try to resolve that item against the live registry and
 * fail here the way it never has to at runtime (the {@code neoforge:conditions} gate skips the file
 * before decode ever runs there). The predicate half of the integration --
 * {@code ForgeweaveCreateCompat#isWearingGoggles} -- has its own test next to it in
 * {@code compat/create}, off the Create classpath the same way.
 */
class GogglesModifierTest {

    private static final ResourceLocation GOGGLES = ResourceLocation.fromNamespaceAndPath("forgeweave", "goggles");

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void isRegisteredArmorOnlyHelmetOnlyAndUtility() {
        assertEquals(ForgeweaveModifiers.GOGGLES, ForgeweaveModifiers.get(GOGGLES));
        assertEquals(ForgeweaveModifiers.GOGGLES_ID, GOGGLES);
        assertTrue(ForgeweaveModifiers.GOGGLES.armorOnly());
        assertTrue(ForgeweaveModifiers.GOGGLES.helmetOnly());
        assertTrue(ForgeweaveModifiers.GOGGLES.utility());
    }

    /** {@link Modifier#utility}'s single check, in {@link Modifier#occupiedSlots}'s default. */
    @Test
    void occupiesNoSlotAtAnyLevel() {
        assertEquals(0, ForgeweaveModifiers.GOGGLES.occupiedSlots(0));
        assertEquals(0, ForgeweaveModifiers.GOGGLES.occupiedSlots(1));
        assertEquals(0, ForgeweaveModifiers.GOGGLES.occupiedSlots(5), "utility() ignores level entirely");
    }

    /**
     * The actual proof the issue asks for: {@code freeSlots} is identical before and after applying
     * goggles to a helmet, while an ordinary (non-utility) modifier on the same slot still spends one
     * -- {@code haste}, one slot per level like every leveled modifier without its own override.
     */
    @Test
    void freeSlotsIsUnchangedByGogglesButStillSpentByAnOrdinaryModifier() {
        ItemStack helmet = new ItemStack(ForgeweaveItems.ARMOR_HELMET.get());
        int baseline = ForgeweaveModifiers.freeSlots(helmet);

        ItemStack withGoggles = helmet.copy();
        withGoggles.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(GOGGLES, 1)));
        assertEquals(baseline, ForgeweaveModifiers.freeSlots(withGoggles), "goggles must spend no slot");

        ResourceLocation haste = ResourceLocation.fromNamespaceAndPath("forgeweave", "haste");
        ItemStack withHaste = helmet.copy();
        withHaste.set(ForgeweaveDataComponents.MODIFIERS.get(), List.of(new ModifierEntry(haste, 1)));
        assertEquals(baseline - 1, ForgeweaveModifiers.freeSlots(withHaste),
                "an ordinary modifier must still occupy a slot, for contrast");
    }

    /** {@link Modifier#helmetOnly}: both helmet items accept it, heavy or light alike. */
    @Test
    void appliesToEitherHelmetButNoOtherArmorPieceOrTool() {
        assertTrue(ModifierApplication.acceptsToolShape(null, ForgeweaveModifiers.GOGGLES,
                new ItemStack(ForgeweaveItems.ARMOR_HELMET.get())), "light helmet");
        assertTrue(ModifierApplication.acceptsToolShape(null, ForgeweaveModifiers.GOGGLES,
                new ItemStack(ForgeweaveItems.ARMOR_HEAVY_HELMET.get())), "heavy helmet");
        assertFalse(ModifierApplication.acceptsToolShape(null, ForgeweaveModifiers.GOGGLES,
                new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get())), "chestplate must be refused");
        assertFalse(ModifierApplication.acceptsToolShape(null, ForgeweaveModifiers.GOGGLES,
                new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get())), "a harvest tool must be refused");
    }

    /**
     * The shipped recipe as plain JSON (see the class javadoc for why this doesn't go through
     * {@link ModifierRecipe#CODEC}): the modifier id, the {@code create:goggles} reagent, cost and
     * cap, and the {@code neoforge:mod_loaded} condition issue #1007 asks for.
     */
    @Test
    void theShippedRecipeIsOneGogglesGatedOnCreate() throws IOException {
        String path = "/data/forgeweave/forgeweave/modifier_recipe/goggles.json";
        JsonObject json;
        try (InputStream in = GogglesModifierTest.class.getResourceAsStream(path)) {
            assertTrue(in != null, "missing shipped modifier recipe: " + path);
            json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }

        assertEquals("forgeweave:goggles", json.get("modifier").getAsString());
        assertEquals("create:goggles", json.getAsJsonObject("reagent").get("item").getAsString());
        assertEquals(1, json.get("cost").getAsInt());
        assertEquals(1, json.get("max_level").getAsInt());

        JsonArray conditions = json.getAsJsonArray("neoforge:conditions");
        assertEquals(1, conditions.size());
        JsonObject condition = conditions.get(0).getAsJsonObject();
        assertEquals("neoforge:mod_loaded", condition.get("type").getAsString());
        assertEquals("create", condition.get("modid").getAsString());
    }
}
