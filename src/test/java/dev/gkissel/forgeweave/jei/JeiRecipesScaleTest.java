package dev.gkissel.forgeweave.jei;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.registration.IRecipeCategoryRegistration;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.common.conditions.ICondition;

import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.recipe.CoreTransformRecipe;
import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * Issue #846 (M6 UI/schema hardening), pressure point 5: one JEI subtype per material per part is
 * fixed cost at registration (it walks the ~36 registered part items and the tool roster, never the
 * material list -- see {@code ForgeweaveJeiPlugin#registerItemSubtypes}), but the three display-recipe
 * lists ({@code PartCraftingRecipes}, {@code AssemblyRecipes}, {@code RepairRecipes}) are each built
 * once per session directly off the material map, the same "one entry per material x eligible part/
 * tool" shape the creative tab and Part Builder measurements cover. This pins the real roster's
 * counts and confirms building all three together stays fast -- the epic's own guess was "the
 * likeliest of the five to be a non-issue."
 */
class JeiRecipesScaleTest {

    private static final long BUDGET_MILLIS = 250;

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static Map<Identifier, Material> shippedMaterials() throws Exception {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Path materialDir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/material");
        Map<Identifier, Material> materials = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(materialDir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                Material material = Material.CODEC.parse(ops, json).getOrThrow();
                materials.put(Identifier.fromNamespaceAndPath("forgeweave", name), material);
            }
        }
        return materials;
    }

    /**
     * Issue #890: the same shipped-datapack-directory read {@link #shippedMaterials} uses, generalised
     * to any of this project's registry codecs. Reading straight off {@code src/main/resources} (never
     * {@code src/gametest/resources}) is what keeps GameTest-only fixtures -- {@code
     * smeltery_fuel/gametest_super_fuel.json}, excluded from the published jar by {@code build.gradle}'s
     * {@code jar} task -- out of these counts without this test needing any id-based filter of its own.
     */
    private static <T> Map<Identifier, T> shippedRegistryEntries(String registryPath, com.mojang.serialization.Codec<T> codec) throws Exception {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        Path dir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/" + registryPath);
        Map<Identifier, T> entries = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                entries.put(Identifier.fromNamespaceAndPath("forgeweave", name), codec.parse(ops, json).getOrThrow());
            }
        }
        return entries;
    }

    private static Path projectRoot() {
        Path dir = Path.of("").toAbsolutePath();
        for (Path candidate = dir; candidate != null; candidate = candidate.getParent()) {
            if (Files.exists(candidate.resolve("settings.gradle"))) {
                return candidate;
            }
        }
        throw new AssertionError("could not locate project root (no settings.gradle found above " + dir + ")");
    }

    @Test
    void theThreeCategoriesBuildQuicklyAtTheFullRoster() throws Exception {
        Map<Identifier, Material> shipped = shippedMaterials();
        assertTrue(shipped.size() >= 100, "non-vacuity: expected the real M6 roster, saw only " + shipped.size());

        long start = System.nanoTime();
        List<PartCraftingRecipe> partCrafting = PartCraftingRecipes.build(shipped);
        List<AssemblyRecipe> assembly = AssemblyRecipes.build(shipped);
        List<RepairRecipe> repair = RepairRecipes.build(shipped);
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        System.out.println("[#846] JEI categories at " + shipped.size() + " materials: " + partCrafting.size()
                + " part-crafting, " + assembly.size() + " assembly, " + repair.size() + " repair recipes, "
                + "built in " + elapsedMillis + "ms");

        assertTrue(!partCrafting.isEmpty() && !assembly.isEmpty() && !repair.isEmpty(),
                "expected every category to enumerate at least one recipe from the real roster");
        assertTrue(elapsedMillis < BUDGET_MILLIS,
                "building all three JEI recipe lists for " + shipped.size() + " materials took "
                        + elapsedMillis + "ms, over the " + BUDGET_MILLIS + "ms budget");
    }

    /**
     * Issue #890: pins the real shipped counts for the two new categories the same way the test above
     * pins the original three -- lava and blazing blood ({@code smeltery_fuel}), end_core and
     * deep_core ({@code core_transform_recipe}, #845). Both registries are small and fixed by
     * design (docs/SCOPE.md M2/#845 never project either one growing the way the material roster did),
     * so this is a non-vacuity/regression pin rather than a scale budget -- if a pack or a future
     * milestone adds another fuel or transform, updating the expected count here is the intended
     * maintenance, not a failure of the test.
     *
     * <p>#894 added twinalloy, bringing the fuel count from 2 to 3; #897 added pyrealloy -- the fuel
     * ladder's 2100-degree top rung -- bringing it to 4; #903 added the ladder's two mined rungs,
     * molten magma (melted vanilla magma blocks, 1700) and molten brimspar (melted Nether crystals,
     * 1900), bringing it to 6. #910 merged twinalloy into brimspar and deleted its row, leaving 5.
     * The 2026-09-06 maintainer decision made the Nether Core a pour-to-transform tier too (blazing
     * blood over a Standard Core), bringing the transform count from 2 to 3.
     *
     * <p>The burn-rate assertions are #910's other half: every fuel drains 50 mB per 100-melt-tick
     * cycle (the clone's own lava numbers, {@code SmelteryFuelTest}) except pyrealloy, which drains
     * 100 mB and burns 500 ticks on it -- 2.5x the work per mB, the ladder's only long burn and the
     * reward for finishing the alloy chain. Pinned here, off the shipped JSON, because the shape of
     * the claim is "exactly one fuel is special"; {@code SmelteryFuelGameTests} proves the smeltery
     * actually honours those two numbers.
     */
    @Test
    void theTwoNewCategoriesEnumerateTheRealShippedRegistryContents() throws Exception {
        Map<Identifier, SmelteryFuel> fuels = shippedRegistryEntries("smeltery_fuel", SmelteryFuel.CODEC);
        Map<Identifier, CoreTransformRecipe> transforms =
                shippedRegistryEntries("core_transform_recipe", CoreTransformRecipe.CODEC);

        assertEquals(5, fuels.size(),
                "lava + blazing_blood + magma + brimspar + pyrealloy -- gametest_super_fuel ships outside src/main/resources");
        assertEquals(3, transforms.size(), "nether_core (blazing blood, 2026-09-06) + #845's end_core + deep_core rows");

        fuels.forEach((id, fuel) -> {
            boolean longBurn = id.getPath().equals("pyrealloy");
            assertEquals(longBurn ? 100 : 50, fuel.amount(), id + "'s mB drained per burn cycle");
            assertEquals(longBurn ? 500 : 100, fuel.duration(), id + "'s melt ticks per burn cycle");
        });

        List<SmelteryFuelDisplay> fuelDisplays = SmelteryFuelRecipes.build(fuels, false);
        List<CoreTransformRecipe> transformDisplays = CoreTransformRecipes.build(transforms);

        assertEquals(5, fuelDisplays.size());
        assertEquals(3, transformDisplays.size());

        // #972: the energized tank adds one synthetic row on top of the registry, the same shape the
        // entity-melting default row takes below. The registry itself is untouched -- the tank is a
        // block, not a burnable fluid -- so only the display count moves.
        List<SmelteryFuelDisplay> withTank = SmelteryFuelRecipes.build(fuels, true);
        assertEquals(6, withTank.size(), "one row per registry entry, plus the energized tank's own");
        assertEquals(1, withTank.stream().filter(SmelteryFuelDisplay::energizedTank).count(),
                "exactly one row must be the energized-tank row");
        assertTrue(withTank.stream().filter(SmelteryFuelDisplay::energizedTank)
                        .allMatch(row -> row.energizedCost() > 0),
                "and it must carry a real per-cycle cost, or the row teaches a player nothing");
    }

    /**
     * Issue #931: pins the real shipped {@code entity_melting_recipe} count the same way the test
     * above pins the other two smeltery registries -- blaze, emerald_mobs (villager + three illager
     * types, one file), iron_golem, large_overworld_mobs, small_overworld_mobs, snow_golem and warden,
     * seven files. {@link EntityMeltingRecipes#build} appends one more synthetic row for {@link
     * EntityMeltingRecipe#defaultResult}, so the JEI category shows eight rows for seven registry
     * entries.
     */
    @Test
    void theEntityMeltingCategoryEnumeratesTheRealShippedRegistryContentsPlusTheDefaultRow() throws Exception {
        Map<Identifier, EntityMeltingRecipe> recipes =
                shippedRegistryEntries("entity_melting_recipe", EntityMeltingRecipe.CODEC);

        assertEquals(7, recipes.size(),
                "blaze + emerald_mobs + iron_golem + large_overworld_mobs + small_overworld_mobs + snow_golem + warden");

        List<EntityMeltingDisplay> displays = EntityMeltingRecipes.build(recipes, List.of());
        assertEquals(8, displays.size(), "one row per recipe, plus EntityMeltingRecipe#defaultResult's own row");
        assertEquals(1, displays.stream().filter(EntityMeltingDisplay::defaultRow).count(),
                "exactly one row must be the default-rule row");
    }

    // ------------------------------------------------------------------ playtest checklist 1.0.0, JEI rows

    /** Copied from {@code ArmorModifiersTest#ARMOR_ONLY}: every shipped modifier whose {@code armorOnly()} is true. */
    private static final Set<Identifier> ARMOR_ONLY = Set.of(
            fw("fire_protection"), fw("blast_protection"), fw("magic_protection"),
            fw("melee_protection"), fw("projectile_protection"), fw("knockback_resistance"), fw("thorns"),
            fw("elytra_flight"), fw("creative_flight"), fw("goggles"), fw("rayward"));

    private static final List<String> ARMOR_CAST_PARTS =
            List.of("plating_helmet", "plating_chestplate", "plating_leggings", "plating_boots", "maille");

    private static Identifier fw(String path) {
        return Identifier.fromNamespaceAndPath("forgeweave", path);
    }

    private static Item item(String path) {
        return BuiltInRegistries.ITEM.getOptional(fw(path)).orElseThrow(() -> new AssertionError("no item " + path));
    }

    /**
     * {@link #shippedRegistryEntries} through {@code ConditionalOps}, the way the datapack registry
     * loader reads these files. A file whose {@code neoforge:conditions} fail is dropped, so the
     * result is what JEI gets in a pack with none of the optional partner mods.
     */
    private static <T> Map<Identifier, T> shippedConditionalEntries(String registryPath, Codec<T> codec) throws Exception {
        ConditionalOps<JsonElement> ops = new ConditionalOps<>(RegistryOps.create(JsonOps.INSTANCE,
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)), ICondition.IContext.EMPTY);
        Codec<Optional<T>> conditional = ConditionalOps.createConditionalCodec(codec);
        Path dir = projectRoot().resolve("src/main/resources/data/forgeweave/forgeweave/" + registryPath);
        Map<Identifier, T> entries = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String name = file.getFileName().toString().replace(".json", "");
                JsonElement json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
                conditional.parse(ops, json).getOrThrow().ifPresent(value -> entries.put(fw(name), value));
            }
        }
        return entries;
    }

    private static List<ToolConstants.Entry> allArmor() {
        return Stream.concat(ToolConstants.ARMOR.stream(), ToolConstants.HEAVY_ARMOR.stream()).toList();
    }

    private static Optional<AssemblyRecipe> rowFor(List<AssemblyRecipe> rows, ToolConstants.Entry armor) {
        return rows.stream()
                .filter(row -> ToolAssemblyRecipes.entryFor(row.result()).map(e -> e.constants() == armor).orElse(false))
                .findFirst();
    }

    /**
     * Playtest checklist 1.0.0, line 15 (armor assembly half). {@code JeiRecipesTest} drops the
     * armor rows on purpose, because its test materials have no plating stats. This test builds the
     * assembly rows from the shipped roster instead and pins one row per armor piece: 4 light pieces
     * from {@code ToolConstants.ARMOR} and 4 heavy pieces from {@code ToolConstants.HEAVY_ARMOR}, 8
     * in all. Each row has as many slots as the piece has parts, and every slot cycles at least one
     * material, so no slot draws empty in JEI.
     */
    @Test
    void theShippedRosterGivesEveryArmorPieceOneFullAssemblyRow() throws Exception {
        List<AssemblyRecipe> rows = AssemblyRecipes.build(shippedMaterials());

        List<ToolConstants.Entry> armor = allArmor();
        assertEquals(8, armor.size(), "4 light pieces and 4 heavy pieces");
        long armorRows = rows.stream()
                .filter(row -> ToolAssemblyRecipes.entryFor(row.result()).map(e -> armor.contains(e.constants())).orElse(false))
                .count();
        assertEquals(8, armorRows, "one assembly row per armor piece");

        for (ToolConstants.Entry piece : armor) {
            AssemblyRecipe row = rowFor(rows, piece).orElseThrow(() -> new AssertionError(piece.id() + " has no assembly row"));
            assertEquals(piece.parts().size(), row.parts().size(), piece.id() + " slot count");
            for (int slot = 0; slot < row.parts().size(); slot++) {
                assertFalse(row.parts().get(slot).isEmpty(), piece.id() + " slot " + slot + " cycles no material");
            }
        }
    }

    /**
     * Playtest checklist 1.0.0, line 32 (headless half). The tester saw 4 light pieces under
     * "Assembly" and 4 heavy pieces under "Assembly (Tool Forge only)". The plugin routes each row by
     * {@code AssemblyRecipes#isLarge}, which reads the {@code #forgeweave:large_tools} item tag. Plain
     * JUnit loads no datapack, so this binds that tag from the generated tag file the jar ships, then
     * checks the split: the light set is never large and the heavy set always is, 4 and 4. The
     * binding is cleared afterwards so later tests see unbound tags again, as they expect.
     */
    @Test
    void theArmorRowsSplitFourAndFourAcrossTheTwoAssemblyCategories() throws Exception {
        Path tagFile = projectRoot().resolve("src/generated/resources/data/forgeweave/tags/item/large_tools.json");
        JsonObject tag = JsonParser.parseString(Files.readString(tagFile, StandardCharsets.UTF_8)).getAsJsonObject();
        List<Holder<Item>> large = new ArrayList<>();
        for (JsonElement value : tag.getAsJsonArray("values")) {
            large.add(BuiltInRegistries.ITEM.getHolderOrThrow(
                    ResourceKey.create(Registries.ITEM, Identifier.parse(value.getAsString()))));
        }

        BuiltInRegistries.ITEM.bindTags(Map.of(ToolAssemblyRecipes.LARGE_TOOLS, large));
        try {
            List<AssemblyRecipe> rows = AssemblyRecipes.build(shippedMaterials());
            for (ToolConstants.Entry piece : ToolConstants.ARMOR) {
                assertFalse(AssemblyRecipes.isLarge(rowFor(rows, piece).orElseThrow()), piece.id() + " belongs under Assembly");
            }
            for (ToolConstants.Entry piece : ToolConstants.HEAVY_ARMOR) {
                assertTrue(AssemblyRecipes.isLarge(rowFor(rows, piece).orElseThrow()),
                        "heavy " + piece.id() + " belongs under Assembly (Tool Forge only)");
            }
            assertEquals(4, ToolConstants.ARMOR.size());
            assertEquals(4, ToolConstants.HEAVY_ARMOR.size());
        } finally {
            BuiltInRegistries.ITEM.bindTags(Map.of());
        }
    }

    private static boolean castIs(CastingRecipe recipe, Item cast) {
        return recipe.cast().map(ingredient -> ingredient.test(new ItemStack(cast))).orElse(false);
    }

    private static boolean fluidIs(CastingRecipe recipe, String fluid) {
        return recipe.fluid().map(BuiltInRegistries.FLUID::getKey).filter(fw(fluid)::equals).isPresent();
    }

    /**
     * Playtest checklist 1.0.0, line 15 (casting half). Reads every shipped {@code casting_recipe}
     * file and runs the table split JEI uses. For each of the 4 platings and maille it pins both cast
     * kinds the tester saw: a gold cast poured from 288 mB of molten gold over the part and a clay
     * cast from molten clay, both consuming the part, then part rows from each cast. The gold cast is
     * kept on every part row and the clay cast is used up, and both casts offer the same number of
     * materials, so neither cast kind loses a row.
     */
    @Test
    void platingAndMailleCastFromBothGoldAndClayCastsOnTheCastingTable() throws Exception {
        List<CastingRecipe> table = CastingRecipes.table(shippedConditionalEntries("casting_recipe", CastingRecipe.CODEC));

        for (String part : ARMOR_CAST_PARTS) {
            Item partItem = item(part);
            Item goldCast = item("cast_" + part);
            Item clayCast = item("clay_cast_" + part);

            assertTrue(table.stream().anyMatch(r -> r.result().is(goldCast) && castIs(r, partItem)
                    && fluidIs(r, "molten_gold") && r.amount() == 288 && r.consumesCast()), part + ": gold cast row");
            assertTrue(table.stream().anyMatch(r -> r.result().is(clayCast) && castIs(r, partItem)
                    && fluidIs(r, "molten_clay") && r.amount() == 288 && r.consumesCast()), part + ": clay cast row");

            List<CastingRecipe> fromGold = table.stream().filter(r -> r.result().is(partItem) && castIs(r, goldCast)).toList();
            List<CastingRecipe> fromClay = table.stream().filter(r -> r.result().is(partItem) && castIs(r, clayCast)).toList();
            assertFalse(fromGold.isEmpty(), part + " has no row from the gold cast");
            assertEquals(fromGold.size(), fromClay.size(), part + ": gold and clay casts offer the same materials");
            assertTrue(fromGold.stream().noneMatch(CastingRecipe::consumesCast), part + ": the gold cast is kept");
            assertTrue(fromClay.stream().allMatch(CastingRecipe::consumesCast), part + ": the clay cast is used up");
        }
    }

    /**
     * Playtest checklist 1.0.0, line 15 (armor modifier half). Builds the Modifier Application rows
     * from every shipped {@code modifier_recipe} file and pins that each armor-only modifier has at
     * least one row. Goggles is the exception: its only recipe takes Create's goggles and sits behind
     * a {@code neoforge:mod_loaded create} condition, so it has a row only when Create is installed.
     * The set of armor-only rows must equal the expected set exactly, so a new armor-only modifier
     * without a recipe, or one that loses its recipe, fails here.
     */
    @Test
    void everyArmorOnlyModifierHasAModifierApplicationRow() throws Exception {
        List<ModifierRecipe> rows = ModifierApplicationRecipes.build(shippedConditionalEntries("modifier_recipe", ModifierRecipe.CODEC));

        Set<Identifier> armorRows = rows.stream().map(ModifierRecipe::modifier)
                .filter(id -> {
                    Modifier modifier = ForgeweaveModifiers.get(id);
                    return modifier != null && modifier.armorOnly();
                })
                .collect(Collectors.toSet());

        Set<Identifier> expected = new HashSet<>(ARMOR_ONLY);
        if (!ModList.get().isLoaded("create")) {
            expected.remove(fw("goggles"));
        }
        assertEquals(expected, armorRows);
    }

    private static final List<String> SHIPPED_CATEGORY_UIDS = List.of(
            "forgeweave:part_crafting", "forgeweave:tool_assembly", "forgeweave:large_tool_assembly",
            "forgeweave:tool_repair", "forgeweave:modifier_worktable", "forgeweave:melting",
            "forgeweave:alloying", "forgeweave:casting_table", "forgeweave:casting_basin",
            "forgeweave:modifier_application", "forgeweave:embossing", "forgeweave:smeltery_fuel",
            "forgeweave:core_transform", "forgeweave:entity_melting");

    /**
     * A stand-in for any JEI interface: an interface-typed method returns another stand-in, a
     * primitive one returns its zero value, and anything else returns null. That is enough for every
     * category constructor, which only asks the gui helper for drawables and stores them.
     */
    private static Object fake(Class<?> type) {
        if (type == void.class) {
            return null;
        }
        if (type.isPrimitive()) {
            return Array.get(Array.newInstance(type, 1), 0);
        }
        if (!type.isInterface()) {
            return null;
        }
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type},
                (proxy, method, args) -> objectMethod(proxy, method, args).orElseGet(() -> fake(method.getReturnType())));
    }

    private static Optional<Object> objectMethod(Object proxy, Method method, Object[] args) {
        return switch (method.getName()) {
            case "equals" -> method.getParameterCount() == 1 ? Optional.of(proxy == args[0]) : Optional.empty();
            case "hashCode" -> method.getParameterCount() == 0 ? Optional.of(System.identityHashCode(proxy)) : Optional.empty();
            case "toString" -> method.getParameterCount() == 0 ? Optional.of("fake " + method.getDeclaringClass().getSimpleName()) : Optional.empty();
            default -> Optional.empty();
        };
    }

    /**
     * Playtest checklist 1.0.0, line 23 (category half). The tester looked for a new tool-leveling
     * category and found none. This pins the whole list instead: it runs the real {@code
     * ForgeweaveJeiPlugin#registerCategories} against a recording fake registration and checks the
     * 14 category ids, in registration order. A new category, a removed one, or a renamed id fails
     * here.
     */
    @Test
    void theJeiPluginRegistersExactlyTheFourteenShippedCategories() {
        List<IRecipeCategory<?>> recorded = new ArrayList<>();
        IRecipeCategoryRegistration registration = (IRecipeCategoryRegistration) Proxy.newProxyInstance(
                IRecipeCategoryRegistration.class.getClassLoader(), new Class<?>[] {IRecipeCategoryRegistration.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("addRecipeCategories")) {
                        for (Object category : (Object[]) args[0]) {
                            recorded.add((IRecipeCategory<?>) category);
                        }
                        return null;
                    }
                    return objectMethod(proxy, method, args).orElseGet(() -> fake(method.getReturnType()));
                });

        new ForgeweaveJeiPlugin().registerCategories(registration);

        assertEquals(SHIPPED_CATEGORY_UIDS,
                recorded.stream().map(category -> category.getRecipeType().getUid().toString()).toList());
    }
}
