package dev.gkissel.forgeweave.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolStats;

/**
 * A compat material matches or beats its home mod's own gear (D-M8-19). Every row of
 * {@code mod_gear_floors.tsv} is checked against the shipped material JSON: damage through a blow's
 * full path (player base, attribute cutoff, then the second cutoff on the hit), so the number holds
 * for a real swing and for the tooltip alike.
 */
class ModGearFloorsTest {
    private static final List<String> SLOTS = List.of("helmet", "chestplate", "leggings", "boots");
    private static final float CUTOFF = ToolConstants.BROADSWORD.damageCutoff();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyMaterialReachesItsHomeModsGear() throws IOException {
        List<String> rows = resource("/mod_gear_floors.tsv").lines()
                .filter(line -> !line.startsWith("#") && !line.isBlank())
                .toList();
        assertTrue(rows.size() >= 50, "non-vacuity: only " + rows.size() + " rows");
        for (String row : rows) {
            String[] cells = row.split("\t");
            String id = cells[0];
            JsonObject material = JsonParser.parseString(resource("/data/forgeweave/forgeweave/material/" + id + ".json")).getAsJsonObject();
            if (material.has("head")) {
                checkHead(id, material.getAsJsonObject("head"), cells);
            }
            if (material.has("plating")) {
                checkPlating(id, material.getAsJsonObject("plating"), cells);
            }
        }
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = ModGearFloorsTest.class.getResourceAsStream(path)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void checkHead(String id, JsonObject head, String[] cells) {
        float attribute = ToolItem.calcCutoffDamage(head.get("attack_damage").getAsFloat(), CUTOFF);
        float blow = ToolItem.calcCutoffDamage(1.0F + attribute, CUTOFF);
        assertTrue(blow >= Float.parseFloat(cells[1]) - 0.01F, id + " hits for " + blow + ", its home mod for " + cells[1]);
        float bonus = head.has("attack_speed") ? head.get("attack_speed").getAsFloat() : 0.0F;
        assertTrue(ToolConstants.BROADSWORD.attackSpeed() + bonus >= Float.parseFloat(cells[2]) - 0.01F,
                id + " swings at " + (ToolConstants.BROADSWORD.attackSpeed() + bonus) + ", its home mod at " + cells[2]);
        assertTrue(head.get("durability").getAsInt() >= Float.parseFloat(cells[3]), id + " durability");
        assertTrue(head.get("mining_speed").getAsFloat() >= Float.parseFloat(cells[4]), id + " mining speed");
    }

    private static void checkPlating(String id, JsonObject plating, String[] cells) {
        for (int i = 0; i < SLOTS.size(); i++) {
            JsonObject piece = plating.getAsJsonObject(SLOTS.get(i));
            String where = id + " " + SLOTS.get(i);
            assertTrue(number(piece, "armor") >= Float.parseFloat(cells[5 + i]), where + " armor");
            assertTrue(number(piece, "toughness") >= Float.parseFloat(cells[9]), where + " toughness");
            assertTrue(number(piece, "knockback_resistance") >= Float.parseFloat(cells[10]), where + " knockback");
            assertTrue(number(piece, "durability") >= Float.parseFloat(cells[11 + i]), where + " durability");
        }
    }

    private static float number(JsonObject object, String key) {
        return object.has(key) ? object.get(key).getAsFloat() : 0.0F;
    }

    @Test
    void aHeadsAttackSpeedReachesTheAttackSpeedAttribute() {
        ItemStack sword = new ItemStack(ForgeweaveItems.TOOL_BROADSWORD.get());
        sword.set(ForgeweaveDataComponents.TOOL_STATS.get(), new ToolStats.Stats(100, 4.0F, 5.0F, 2.8F));

        double[] speed = {4.0};
        sword.getItem().getDefaultAttributeModifiers(sword).forEach(EquipmentSlotGroup.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_SPEED)) {
                speed[0] += modifier.amount();
            }
        });

        assertEquals(4.4, speed[0], 1.0e-4, "the broadsword's 1.6 plus unobtainium's 2.8");
    }

    @Test
    void attackSpeedIsOptionalInMaterialsAndSavedTools() {
        JsonObject head = JsonParser.parseString("{\"durability\":1,\"mining_speed\":1,\"attack_damage\":1}").getAsJsonObject();
        assertEquals(0.0F, Material.Head.CODEC.parse(JsonOps.INSTANCE, head).getOrThrow().attackSpeed());

        JsonObject saved = JsonParser.parseString("{\"durability\":1,\"mining_speed\":1,\"attack_damage\":1}").getAsJsonObject();
        assertEquals(0.0F, ToolStats.Stats.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow().attackSpeed());
        assertEquals(saved, ToolStats.Stats.CODEC.encodeStart(JsonOps.INSTANCE, new ToolStats.Stats(1, 1.0F, 1.0F)).getOrThrow());
    }
}
