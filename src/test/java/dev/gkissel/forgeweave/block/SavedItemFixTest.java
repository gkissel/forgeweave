package dev.gkissel.forgeweave.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

import dev.gkissel.forgeweave.TestRegistries;

/**
 * Items saved inside a Forgeweave block entity on 1.21.1 get vanilla's item data fixes on load
 * ({@link SavedItemFix}). Vanilla's chunk fixer skips block entity ids it does not know, so before
 * the fix an enchanted pickaxe left in a tool station came back with no enchantments and a custom
 * name that read as raw JSON. Found by the 1.21.1 world-upgrade check on 2026-10-28.
 */
class SavedItemFixTest {

    /** Copied from a tool station a Forgeweave 1.0.0 server on 1.21.1 saved, via `data get block`. */
    private static final String STATION_1_21_1 = """
            {id: "forgeweave:tool_station", texture: "minecraft:oak_planks", x: 0, y: 0, z: 0, inventory: [
              {count: 1, id: "forgeweave:pickaxe", components: {
                "forgeweave:traits": ["forgeweave:crude", "forgeweave:ecological", "forgeweave:cheap"],
                "forgeweave:tool_stats": {mining_speed: 5.0f, durability: 33, attack_damage: 2.9f},
                "forgeweave:tool_materials": {head: "forgeweave:flint", parts: ["forgeweave:flint", "forgeweave:wood", "forgeweave:stone"], binding: "forgeweave:wood", handle: "forgeweave:stone"},
                "minecraft:enchantments": {levels: {"minecraft:unbreaking": 3}},
                "minecraft:custom_name": '"Old Pick"', "minecraft:damage": 0, "minecraft:max_damage": 33}},
              {count: 1, id: "minecraft:diamond_sword", components: {
                "minecraft:enchantments": {show_in_tooltip: 0b, levels: {"minecraft:sharpness": 5}},
                "minecraft:custom_name": '{"color":"red","text":"Blade"}', "minecraft:lore": ['"old lore"']}}]}""";

    /** The ContainerHelper path: a formed 1x1x1 seared furnace with an enchanted sword in slot 0. */
    private static final String FURNACE_1_21_1 = """
            {id: "forgeweave:seared_furnace_controller", x: 0, y: 0, z: 0,
             structure: {interior_max: [I; 1, 64, 1], interior_min: [I; 1, 64, 1]}, tanks: [[I; 0, 65, 0]],
             items: {Items: [{Slot: 0b, count: 1, id: "minecraft:iron_sword",
               components: {"minecraft:enchantments": {levels: {"minecraft:sharpness": 2}}}}]}}""";

    private static HolderLookup.Provider registries;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        registries = TestRegistries.vanilla();
    }

    @Test
    void anEnchantedNamedToolSavedInAStationOn1_21_1KeepsItsEnchantmentsAndName() throws Exception {
        CompoundTag saved = loadAndSave("forgeweave:tool_station", TagParser.parseCompoundFully(STATION_1_21_1));

        CompoundTag pickaxe = saved.getListOrEmpty("inventory").getCompoundOrEmpty(0).getCompoundOrEmpty("components");
        assertEquals(TagParser.parseCompoundFully("{\"minecraft:unbreaking\": 3}"), pickaxe.get("minecraft:enchantments"));
        assertEquals(StringTag.valueOf("Old Pick"), pickaxe.get("minecraft:custom_name"), "a plain name, not its JSON");

        CompoundTag sword = saved.getListOrEmpty("inventory").getCompoundOrEmpty(1).getCompoundOrEmpty("components");
        assertEquals(TagParser.parseCompoundFully("{\"minecraft:sharpness\": 5}"), sword.get("minecraft:enchantments"));
        assertEquals(TagParser.parseCompoundFully("{color: \"red\", text: \"Blade\"}"), sword.get("minecraft:custom_name"));
        assertEquals(TagParser.parseCompoundFully("{hidden_components: [\"minecraft:enchantments\"]}"),
                sword.get("minecraft:tooltip_display"), "show_in_tooltip: false became a hidden tooltip");
    }

    @Test
    void anEnchantedItemSavedInASearedFurnaceOn1_21_1KeepsItsEnchantments() throws Exception {
        CompoundTag saved = loadAndSave("forgeweave:seared_furnace_controller", TagParser.parseCompoundFully(FURNACE_1_21_1));

        CompoundTag sword = saved.getCompoundOrEmpty("items").getListOrEmpty("Items").getCompoundOrEmpty(0);
        assertEquals("minecraft:iron_sword", sword.getStringOr("id", ""));
        assertEquals(TagParser.parseCompoundFully("{\"minecraft:sharpness\": 2}"),
                sword.getCompoundOrEmpty("components").get("minecraft:enchantments"));
    }

    /** A save made by this version carries its data version, so loading it again fixes nothing twice. */
    @Test
    void aStationSavedByThisVersionReloadsUnchanged() throws Exception {
        CompoundTag once = loadAndSave("forgeweave:tool_station", TagParser.parseCompoundFully(STATION_1_21_1));
        assertTrue(once.contains(SavedItemFix.VERSION_KEY), "every save writes " + SavedItemFix.VERSION_KEY);

        assertEquals(once, loadAndSave("forgeweave:tool_station", once));
    }

    /** Loads {@code tag} the way a chunk load does, fails on anything the load reports, and saves it again. */
    private static CompoundTag loadAndSave(String block, CompoundTag tag) {
        BlockEntity be = BuiltInRegistries.BLOCK_ENTITY_TYPE.getValue(Identifier.parse(tag.getStringOr("id", "")))
                .create(BlockPos.ZERO, BuiltInRegistries.BLOCK.getValue(Identifier.parse(block)).defaultBlockState());
        ProblemReporter.Collector problems = new ProblemReporter.Collector();
        be.loadWithComponents(TagValueInput.create(problems, registries, tag));
        assertTrue(problems.isEmpty(), () -> "the load dropped data: " + problems.getReport());
        TagValueOutput out = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        be.saveWithId(out);
        return out.buildResult();
    }
}
