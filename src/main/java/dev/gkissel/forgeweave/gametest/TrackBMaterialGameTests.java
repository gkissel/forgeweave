package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trackb.TrackBAlloy;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Issue #841 (epic #824's Track B, closing the M6 roster): the self-contained tool material roster
 * and its traits -- 12 ore-sourced metals ({@link TrackBOre}) and 18 alloy metals
 * ({@link TrackBAlloy}), all carrying a {@code Material} JSON for the first time (#839/#840 gave them
 * ore blocks, fluids and casting but no tool stats). Unlike every Track A preset batch, these
 * materials carry <b>no</b> {@code neoforge:conditions} -- they always exist, so the positive path is
 * the direct one, not a mechanism proof (contrast {@link TechMetalGameTests}, {@link
 * DraconicEvolutionGameTests}, {@link PresetBatch5GameTests}, all of which can only prove the
 * negative-existence half of a conditional material).
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TrackBMaterialGameTests {

    /** The full 30-material roster this issue ships, ore-sourced first then alloy, matching the PR's stat table. */
    private static final List<String> ROSTER = List.of(
            "cinderstone", "fulmenite", "duskspar", "voltcinder", "murkiron", "hardcinder", "nightshale",
            "warspar", "hollowstone", "resonite", "starfall_stone", "voidglass",
            "ironbrand", "quakestone", "shardline", "embercast", "riftalloy", "tideiron", "cinderforge",
            "dreadalloy", "sunsteel", "hollowsteel", "truesteel", "stormalloy", "glowveil", "daybrass",
            "faultsteel", "skipalloy", "mendalloy", "mendstone");

    /**
     * id -&gt; its assigned general trait, the PR body's stat/trait table in code form.
     *
     * <p>Issue #876's M6 dedupe batch moved 20 of these off the id this table originally shipped
     * (several Track B materials shared a trait id with another Track B material or with a base/Track
     * A material) -- see that issue's PR body for the full before/after roster; each row below still
     * documents its own material's identity, just under its new, unshared id.
     */
    private static final Map<String, String> TRAITS = Map.ofEntries(
            Map.entry("cinderstone", "wellspring"),
            Map.entry("fulmenite", "unstable_core"),
            Map.entry("duskspar", "duskmend"),
            Map.entry("voltcinder", "overburdened"),
            Map.entry("murkiron", "blighted"),
            Map.entry("hardcinder", "emberwake"),
            Map.entry("nightshale", "nocturnal_edge"),
            Map.entry("warspar", "warbond"),
            Map.entry("hollowstone", "fertilizing"),
            Map.entry("resonite", "dominant"),
            Map.entry("starfall_stone", "obliterate"),
            Map.entry("voidglass", "unraveling2"),
            Map.entry("ironbrand", "smokehouse"),
            Map.entry("quakestone", "stonewake"),
            Map.entry("shardline", "keenedge"),
            Map.entry("embercast", "ashenbond"),
            Map.entry("riftalloy", "unraveling3"),
            Map.entry("tideiron", "tidebreaker"),
            Map.entry("cinderforge", "magmaforge"),
            Map.entry("dreadalloy", "obsidian_heart"),
            Map.entry("sunsteel", "avalanche"),
            Map.entry("hollowsteel", "ruthless"),
            Map.entry("truesteel", "berserker_stance"),
            Map.entry("stormalloy", "unraveling"),
            Map.entry("glowveil", "fallout"),
            Map.entry("daybrass", "daybound"),
            Map.entry("faultsteel", "cascading"),
            Map.entry("skipalloy", "quickstep"),
            Map.entry("mendalloy", "merciful"),
            Map.entry("mendstone", "tinseeker"));

    /** Every Track B material is registered with no supplying mod required -- the Track A contrast. */
    @GameTest(template = "empty")
    public static void everyTrackBMaterialExistsUnconditionally(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : ROSTER) {
            helper.assertTrue(materials.get(materialId(name)) != null,
                    "expected " + name + " to be registered unconditionally (issue #841, Track B always exists)");
        }
        helper.succeed();
    }

    /** Every Track B material has head, handle, bow and armor stat blocks -- a full tool+armor metal. */
    @GameTest(template = "empty")
    public static void everyTrackBMaterialHasFullToolAndArmorStats(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        for (String name : ROSTER) {
            Material material = materials.get(materialId(name));
            helper.assertTrue(material != null, name + " must be registered");
            helper.assertTrue(material.head().isPresent(), name + " must carry head stats");
            helper.assertTrue(material.handle().isPresent(), name + " must carry handle stats");
            helper.assertTrue(material.bow().isPresent(), name + " must carry bow stats");
            helper.assertTrue(material.plating().isPresent(), name + " must carry plating stats");
            helper.assertTrue(material.maille(), name + " must be maille-capable");
            helper.assertTrue(material.castOnly(), name + " must be cast-only (#840 gives full smeltery integration, no Part Builder path)");
        }
        helper.succeed();
    }

    /** Every Track B material carries the general trait this issue assigns it (the PR body's table). */
    @GameTest(template = "empty")
    public static void everyTrackBMaterialCarriesItsAssignedTrait(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        TRAITS.forEach((name, trait) -> {
            Material material = materials.get(materialId(name));
            helper.assertTrue(material != null, name + " must be registered");
            ResourceLocation traitId = traitId(trait);
            helper.assertTrue(material.traits().general().contains(traitId),
                    name + " must carry forgeweave:" + trait + ", got " + material.traits().general());
        });
        helper.succeed();
    }

    /** murkiron's second, head-scoped half of its two-part reference idea (darkness/dread flavor). */
    @GameTest(template = "empty")
    public static void murkironCarriesASecondHeadScopedTrait(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        Material murkiron = materials.get(materialId("murkiron"));
        helper.assertTrue(murkiron != null, "murkiron must be registered");
        helper.assertTrue(murkiron.traits().head().contains(traitId("harrying")),
                "murkiron's head part must carry forgeweave:harrying, got " + murkiron.traits().head());
        helper.succeed();
    }

    /**
     * The top of the ladder: an assembled truesteel pickaxe carries exactly its JSON's stats and
     * mines a diamond-tier block (ancient debris) the way every other netherite-rung Forgeweave
     * material already does (JC10: no tags above netherite, so "whatever #838 decided it should
     * mine" is exactly cobalt/manyullyn/netherite's own rung).
     */
    @GameTest(template = "empty")
    public static void truesteelPickaxeMatchesItsStatsAndMinesAncientDebris(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = ToolAssembly.pickaxe(helper, player, pos, "truesteel", "truesteel", "truesteel");

        ToolStats.Stats stats = pickaxe.get(ForgeweaveDataComponents.TOOL_STATS.get());
        helper.assertTrue(stats != null, "an assembled truesteel pickaxe must carry tool stats");
        helper.assertTrue(pickaxe.getMaxDamage() > 1000,
                "truesteel's pooled durability should be well above 1000, got " + pickaxe.getMaxDamage());
        helper.assertTrue(stats.miningSpeed() >= 8.0F,
                "truesteel's mining speed should match its JSON (10.0 base), got " + stats.miningSpeed());
        helper.assertTrue(stats.attackDamage() >= 5.0F,
                "truesteel's attack damage should match its JSON (9.0 base + binding), got " + stats.attackDamage());

        Tool tool = pickaxe.get(DataComponents.TOOL);
        helper.assertTrue(tool != null, "an assembled truesteel pickaxe must carry a tool component");
        BlockState ancientDebris = Blocks.ANCIENT_DEBRIS.defaultBlockState();
        helper.assertTrue(tool.isCorrectForDrops(ancientDebris),
                "truesteel, on Forgeweave's shared top mining rung, must be correct-for-drops on ancient debris");
        helper.succeed();
    }

    /** truesteel's ingot repairs an assembled tool -- the repair_item wiring actually works. */
    @GameTest(template = "empty")
    public static void truesteelIngotRepairsTheTool(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        Material truesteel = materials.get(materialId("truesteel"));
        helper.assertTrue(truesteel != null, "truesteel must be registered");
        Ingredient repair = truesteel.repairItem();
        helper.assertTrue(repair.test(new ItemStack(ForgeweaveItems.trackBAlloyIngot("truesteel").get())),
                "truesteel's repair_item must accept its own ingot");
        helper.succeed();
    }

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private TrackBMaterialGameTests() {}
}
