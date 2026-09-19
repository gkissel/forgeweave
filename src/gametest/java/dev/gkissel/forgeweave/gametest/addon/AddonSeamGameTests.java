package dev.gkissel.forgeweave.gametest.addon;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.api.upgrade.UpgradeHosts;
import dev.gkissel.forgeweave.block.SmelteryScan;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierApplication;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #1083: one test per public seam {@link GameTestAddon} reaches, so the addon surface #1008
 * opened is proven from outside Forgeweave rather than from Forgeweave's own tests. The tool and
 * part half lives next door in {@code RegisteredToolGameTests}; everything else is here.
 *
 * <p>Every assertion runs against a real loaded server, so a datapack claim here means the addon's
 * own {@code data/gametest_addon/} folder was read the way a partner mod's jar would be read.
 * {@code docs/addons.md} quotes the fixtures these tests name.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class AddonSeamGameTests {

    private static final ResourceLocation MATERIAL = id("addon_alloy");
    private static final ResourceLocation PACK_TRAIT = id("addon_pack_trait");
    private static final ResourceLocation JAVA_TRAIT = id("addon_java_trait");
    private static final ResourceLocation PACK_MODIFIER = id("addon_pack_modifier");
    private static final ResourceLocation JAVA_MODIFIER = id("addon_java_modifier");

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(GameTestAddon.NAMESPACE, path);
    }

    /** A material an addon ships in its own data folder loads with the stats it wrote. */
    @GameTest(template = "empty")
    public static void anAddonMaterialLoadsWithItsStats(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        Material material = materials.get(MATERIAL);
        helper.assertTrue(material != null, "expected the addon's material in the registry, got nothing");
        helper.assertTrue(material.head().isPresent(), "expected the addon's material to carry head stats");
        helper.assertTrue(material.head().get().durability() == 320,
                "expected the durability the addon's JSON wrote, got " + material.head().get().durability());
        helper.assertTrue(material.traits().general().contains(PACK_TRAIT)
                        && material.traits().general().contains(JAVA_TRAIT),
                "expected the material to name both of the addon's traits, got " + material.traits().general());
        helper.succeed();
    }

    /** A trait written as parameters over a Forgeweave library behavior, with no addon Java at all. */
    @GameTest(template = "empty")
    public static void anAddonTraitDefinitionResolves(GameTestHelper helper) {
        Trait trait = ForgeweaveTraits.lookup(PACK_TRAIT);
        helper.assertTrue(trait != null, "expected the addon's trait_definition to resolve to a behavior");
        helper.assertTrue(trait.bonusSlots() == 2,
                "expected the count the addon's JSON wrote, got " + trait.bonusSlots());
        helper.succeed();
    }

    /** A trait over the addon's own behavior type: the type is Java, the parameters stay in data. */
    @GameTest(template = "empty")
    public static void anAddonTraitBehaviorTypeIsNamedFromADatapack(GameTestHelper helper) {
        Trait trait = ForgeweaveTraits.lookup(JAVA_TRAIT);
        helper.assertTrue(trait instanceof GameTestAddon.FlatAttackBonus,
                "expected the addon's own behavior type behind the definition, got " + trait);
        helper.assertTrue(trait.attackDamageBonus(ItemStack.EMPTY) == 2.5F,
                "expected the amount the addon's JSON wrote, got " + trait.attackDamageBonus(ItemStack.EMPTY));
        helper.succeed();
    }

    /** The modifier-side twin of {@link #anAddonTraitDefinitionResolves}. */
    @GameTest(template = "empty")
    public static void anAddonModifierDefinitionResolves(GameTestHelper helper) {
        Modifier modifier = ForgeweaveModifiers.get(PACK_MODIFIER);
        helper.assertTrue(modifier != null, "expected the addon's modifier_definition to resolve to a behavior");
        helper.assertTrue(modifier.bonusSlots(1) == 2,
                "expected the flat bonus the addon's JSON wrote, got " + modifier.bonusSlots(1));
        helper.succeed();
    }

    /** The modifier-side twin of {@link #anAddonTraitBehaviorTypeIsNamedFromADatapack}. */
    @GameTest(template = "empty")
    public static void anAddonModifierBehaviorTypeIsNamedFromADatapack(GameTestHelper helper) {
        Modifier modifier = ForgeweaveModifiers.get(JAVA_MODIFIER);
        helper.assertTrue(modifier instanceof GameTestAddon.FlatAttackModifier,
                "expected the addon's own behavior type behind the definition, got " + modifier);
        helper.assertTrue(modifier.attackDamage(1, 0.0F, 0.0F) == 4.0F,
                "expected the amount the addon's JSON wrote, got " + modifier.attackDamage(1, 0.0F, 0.0F));
        helper.succeed();
    }

    /**
     * A finished modifier registered from Java, reached the only way any modifier is reached: a
     * {@code modifier_recipe} naming its id, resolved against a real tool.
     */
    @GameTest(template = "empty")
    public static void anAddonModifierRegisteredFromJavaAppliesToATool(GameTestHelper helper) {
        helper.assertTrue(ForgeweaveModifiers.get(GameTestAddon.MODIFIER_ID) == GameTestAddon.WHETTED,
                "expected the Java-registered modifier behind its own id");

        ItemStack tool = new ItemStack(GameTestAddon.TOOL.get());
        ItemStack modified = ModifierApplication
                .resolve(helper.getLevel().registryAccess(), tool, new ItemStack(Items.GOAT_HORN), ItemStack.EMPTY)
                .map(ModifierApplication.Outcome::output)
                .orElse(ItemStack.EMPTY);
        helper.assertFalse(modified.isEmpty(),
                "expected the addon's recipe to apply " + GameTestAddon.MODIFIER_ID);
        helper.assertTrue(ForgeweaveModifiers.entry(modified, GameTestAddon.MODIFIER_ID) != null,
                "expected the addon's modifier on the tool after the station applied it");
        helper.succeed();
    }

    /** The upgrade host the addon registered is asked, and what it hands back is what the player gets. */
    @GameTest(template = "empty")
    public static void anAddonUpgradeHostIsAsked(GameTestHelper helper) {
        List<ItemStack> reclaimed = UpgradeHosts.reclaim(new ItemStack(GameTestAddon.TOOL.get()), ItemStack.EMPTY);
        helper.assertTrue(reclaimed.stream().anyMatch(stack -> stack.is(GameTestAddon.PATTERN.get())),
                "expected the addon's host to hand back its own item, got " + reclaimed);

        ItemStack sameTool = new ItemStack(GameTestAddon.TOOL.get());
        helper.assertTrue(UpgradeHosts.reclaim(sameTool, sameTool.copy()).isEmpty(),
                "a host that strips nothing returns nothing, so a real part swap stays inert");
        helper.succeed();
    }

    /** A block the addon owns is a smeltery wall because a tag says so, with no Forgeweave change. */
    @GameTest(template = "empty")
    public static void anAddonBlockIsASmelteryWallByTag(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, GameTestAddon.WALL_BLOCK.get());
        helper.assertTrue(helper.getBlockState(pos).is(SmelteryScan.WALL_ADDON),
                "expected the addon's own tag file to put its block in the wall extension tag");
        helper.assertTrue(helper.getBlockState(pos).is(SmelteryScan.WALL),
                "and so in the wall role the smeltery scan reads");
        helper.succeed();
    }
}
