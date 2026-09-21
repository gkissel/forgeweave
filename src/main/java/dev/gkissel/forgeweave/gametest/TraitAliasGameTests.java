package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.SelfRepairWhen;
import dev.gkissel.forgeweave.trait.TraitFamilies;

/**
 * A tool built before issue #1103 still works (issue #1103, save compatibility).
 *
 * <p>Trait ids are stored, not re-derived: {@code ForgeweaveDataComponents.TRAITS} carries the
 * literal id strings a tool was assembled with, and nothing re-bakes them on load, on repair or on
 * world upgrade -- only a part swap or a rebuild at the station does. So a pickaxe sitting in a
 * chest in a 0.6.0-beta.3 save names {@code ironwood_grip}, {@code tinseeker}, {@code soulwick},
 * and none of those ids is registered any more. Without the alias map in {@link TraitFamilies} the
 * tool would silently lose the trait ({@code ForgeweaveTraits#of} logs an unknown id and drops it)
 * and show the raw lang key {@code trait.forgeweave.ironwood_grip.name} in its tooltip.
 *
 * <p>These tests stand in for that save: a tool is stamped with the retired ids a pre-merge save
 * would carry, and then asked whether the replacement behaviour is live on it. The trait list is
 * written directly rather than assembled, which is exactly what loading an old stack does.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class TraitAliasGameTests {

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    /** A pickaxe whose stored trait list is exactly {@code traits}, the way an old save's is. */
    private static ItemStack savedTool(String... traits) {
        ItemStack pickaxe = new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
        pickaxe.set(ForgeweaveDataComponents.TRAITS.get(), List.of(traits).stream()
                .map(TraitAliasGameTests::id).toList());
        return pickaxe;
    }

    /** Every retired id resolves to the behaviour of the rung that replaced it. */
    @GameTest(template = "empty")
    public static void everyRetiredTraitIdStillResolves(GameTestHelper helper) {
        for (Map.Entry<ResourceLocation, ResourceLocation> alias : TraitFamilies.aliases().entrySet()) {
            Trait retired = ForgeweaveTraits.lookup(alias.getKey());
            Trait replacement = ForgeweaveTraits.lookup(alias.getValue());
            helper.assertTrue(replacement != null,
                    alias.getValue() + " must be registered; " + alias.getKey() + " points at it");
            helper.assertTrue(retired == replacement,
                    alias.getKey() + " must resolve to " + alias.getValue() + " so a tool built before "
                            + "#1103 keeps its trait, got " + retired);
        }
        helper.assertTrue(TraitFamilies.aliases().size() > 100,
                "expected #1103's whole retired set, got " + TraitFamilies.aliases().size());
        helper.succeed();
    }

    /**
     * The behaviour is live on the stack, not merely resolvable: a saved tool naming the retired
     * {@code ironwood_grip} plants its wielder at Heft I's 0.25, and one naming {@code tinseeker}
     * mends at Ecological I's rate.
     */
    @GameTest(template = "empty")
    public static void aSavedToolsRetiredTraitsStillFire(GameTestHelper helper) {
        Player bare = helper.makeMockPlayer(GameType.SURVIVAL);
        bare.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get()));
        bare.tick();
        double none = bare.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, savedTool("ironwood_grip"));
        player.tick();
        double planted = player.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE);
        helper.assertTrue(Math.abs(planted - none - 0.25) < 1e-4,
                "a saved ironwood_grip tool must grant Heft I's 0.25 over " + none + ", got " + planted);

        List<Trait> mending = ForgeweaveTraits.of(savedTool("tinseeker"));
        helper.assertTrue(mending.size() == 1 && mending.get(0) instanceof SelfRepairWhen repair
                        && repair.ticksPerPoint() == 200,
                "a saved tinseeker tool must mend at Ecological I's 200 ticks per point, got " + mending);
        helper.succeed();
    }

    /**
     * Two retired names for one mechanic collapse to one trait on an old tool, rather than firing
     * twice. A pickaxe made of a dark-matter head and an unobtainium handle stored both
     * {@code voidward} and {@code unravelward}; both are 10% dodge, and both now mean Voidward I.
     */
    @GameTest(template = "empty")
    public static void twoRetiredNamesForOneMechanicFireOnce(GameTestHelper helper) {
        List<ResourceLocation> ids = ForgeweaveTraits.canonical(
                List.of(id("voidward"), id("unravelward")));
        helper.assertTrue(ids.equals(List.of(id("voidward"))),
                "both ids must fold onto one rung, got " + ids);

        List<Trait> traits = ForgeweaveTraits.of(savedTool("voidward", "unravelward"));
        helper.assertTrue(traits.size() == 1,
                "the dodge must not roll twice on one tool, got " + traits.size() + " traits");
        helper.succeed();
    }

    /**
     * The tooltip and the station panel read the family's own keys for a retired id, so an old tool
     * shows "Heft" rather than the literal string {@code trait.forgeweave.ironwood_grip.name} --
     * which is the "text errors instead of descriptions" symptom the same review reported.
     */
    @GameTest(template = "empty")
    public static void aRetiredIdRendersTheFamilysNameNotARawKey(GameTestHelper helper) {
        for (ResourceLocation retired : TraitFamilies.aliases().keySet()) {
            String base = TraitFamilies.langBase(ForgeweaveTraits.canonical(retired));
            helper.assertTrue(!base.endsWith("." + retired.getPath()),
                    retired + " would render its own retired key " + base + ", which has no lang entry");
        }
        helper.succeed();
    }
}
