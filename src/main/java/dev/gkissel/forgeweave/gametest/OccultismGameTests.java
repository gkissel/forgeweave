package dev.gkissel.forgeweave.gametest;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.occultism.ForgeweaveOccultismCompat;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.trackb.TrackBOre;

/**
 * Issue #997 (D-M8-18, M8-13): Occultism's spirit binding rituals, its crushing and miner rows, and
 * the spirit attuned gem preset, exercised on a server with no Occultism installed -- which is every
 * server this repo runs (JC-B, and see build.gradle's comment on the dependency: Occultism will not
 * even load on the unit-test classpath, let alone this one).
 *
 * <p>So what is covered here is deliberately the half a Forgeweave-only runtime can answer, which
 * turns out to be most of it: the binding decision itself
 * ({@link ForgeweaveOccultismCompat#bind}, on the Occultism-free side of the compat package
 * precisely so this file can reach it), the slot it charges, the toggle-off path, and the fact that
 * every Occultism-typed row and the gem preset are absent rather than a load error.
 *
 * <p>What no GameTest here can cover: an actual Occultism ritual completing on a Golden Sacrificial
 * Bowl, a crusher spirit actually crushing, and a mining spirit actually returning a Track B ore.
 * Those are release-checklist lines on #975.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class OccultismGameTests {

    private static ItemStack pickaxe() {
        return new ItemStack(ForgeweaveItems.TOOL_PICKAXE.get());
    }

    private static Optional<ItemStack> bind(GameTestHelper helper, ForgeweaveOccultismCompat.Ritual ritual,
            ItemStack tool) {
        return ForgeweaveOccultismCompat.bind(helper.getLevel().registryAccess(), tool,
                ResourceLocation.parse(ritual.modifier()), ritual.level());
    }

    // ------------------------------------------------------------ absent without Occultism

    @GameTest(template = "empty")
    public static void theSpiritAttunedGemPresetDoesNotExistAtAll(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        helper.assertTrue(
                materials.get(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "spirit_attuned_gem")) == null,
                "expected the spirit_attuned_gem material to be absent without Occultism, found it registered");
        helper.succeed();
    }

    /**
     * The gate that matters most: Occultism's three recipe types are not registered on this server,
     * so a crushing, miner or ritual row that reached the recipe manager would be a load error rather
     * than a skipped row. A booting server is the assertion -- if any of the 37 rows had lost its
     * {@code neoforge:conditions}, nothing in this class would run at all.
     */
    @GameTest(template = "empty")
    public static void noOccultismTypedRecipeLoadedOnAServerWithoutIt(GameTestHelper helper) {
        for (ResourceLocation id : List.of(
                ResourceLocation.fromNamespaceAndPath(ForgeweaveOccultismCompat.MODID, "crushing"),
                ResourceLocation.fromNamespaceAndPath(ForgeweaveOccultismCompat.MODID, "miner"),
                ResourceLocation.fromNamespaceAndPath(ForgeweaveOccultismCompat.MODID, "ritual"))) {
            helper.assertTrue(net.minecraft.core.registries.BuiltInRegistries.RECIPE_TYPE.get(id) == null,
                    "Occultism is absent, so " + id + " must not be a registered recipe type -- if it is,"
                            + " this test is no longer proving anything");
        }
        helper.succeed();
    }

    /** The ritual factories are registered inside a ModList guard, so none exists here either. */
    @GameTest(template = "empty")
    public static void noRitualFactoryIsRegisteredWithoutOccultism(GameTestHelper helper) {
        helper.assertTrue(helper.getLevel().registryAccess()
                        .registry(net.minecraft.resources.ResourceKey.createRegistryKey(
                                ResourceLocation.fromNamespaceAndPath(
                                        ForgeweaveOccultismCompat.MODID, "ritual_factories")))
                        .isEmpty(),
                "occultism:ritual_factories must not exist without Occultism; ForgeweaveOccultismCompat"
                        + " creates its DeferredRegister inside the guard for exactly this reason");
        helper.succeed();
    }

    // ------------------------------------------------------------ the binding

    /**
     * Every ritual in the roster binds its modifier at its level, and the tool's free slot count
     * drops by exactly what the entry occupies. The half that distinguishes a binding from a Draconic
     * fusion upgrade, which hands the slots back.
     */
    @GameTest(template = "empty")
    public static void everyRitualBindsItsModifierAndSpendsItsSlots(GameTestHelper helper) {
        for (ForgeweaveOccultismCompat.Ritual ritual : ForgeweaveOccultismCompat.RITUALS) {
            ResourceLocation modifier = ResourceLocation.parse(ritual.modifier());
            ItemStack tool = pickaxe();
            int before = ForgeweaveModifiers.freeSlots(tool);

            ItemStack bound = bind(helper, ritual, tool).orElseThrow(() -> new AssertionError(
                    ritual.name() + " refused a freshly assembled pickaxe; every roster level is picked"
                            + " to fit a fresh tool's slot budget"));

            ModifierEntry entry = ForgeweaveModifiers.entry(bound, modifier);
            helper.assertTrue(entry != null, ritual.name() + " wrote no modifier entry at all");
            helper.assertValueEqual(entry.level(), ritual.level(), ritual.name() + " level");
            helper.assertValueEqual(before - ForgeweaveModifiers.freeSlots(bound),
                    ForgeweaveModifiers.occupiedSlots(modifier, ritual.level()),
                    ritual.name() + " slots spent");
            helper.assertTrue(ForgeweaveModifiers.of(tool).isEmpty(),
                    ritual.name() + " must not have mutated the tool it was given");
        }
        helper.succeed();
    }

    /** A tool already at the ritual's level, and a stack that is not a Forgeweave tool, are refused. */
    @GameTest(template = "empty")
    public static void aBindingWithNothingToGiveIsRefused(GameTestHelper helper) {
        ForgeweaveOccultismCompat.Ritual ritual = ForgeweaveOccultismCompat.RITUALS.getFirst();

        ItemStack bound = bind(helper, ritual, pickaxe()).orElseThrow();
        helper.assertTrue(bind(helper, ritual, bound).isEmpty(),
                "a second binding of the same ritual must be refused rather than burning the ingredients");
        helper.assertTrue(bind(helper, ritual, new ItemStack(Items.DIAMOND_PICKAXE)).isEmpty(),
                "a vanilla pickaxe has no Forgeweave modifier list to bind into");
        helper.assertTrue(bind(helper, ritual, ItemStack.EMPTY).isEmpty(),
                "and an empty stack is not a tool either");
        helper.succeed();
    }

    /**
     * A tool with no slots left is refused rather than upgraded for free. Reachable because the
     * roster's own costs already add up past a fresh tool's budget -- see
     * {@link ForgeweaveOccultismCompat#RITUALS}.
     */
    @GameTest(template = "empty")
    public static void aToolWithNoSlotsLeftIsRefused(GameTestHelper helper) {
        ItemStack tool = pickaxe();
        // mending_moss at 2 plus necrotic at 3 is 5 slots against a budget of 3, so the second of the
        // two has to be refused somewhere -- which is the whole point of charging for a binding.
        int spent = 0;
        for (ForgeweaveOccultismCompat.Ritual ritual : ForgeweaveOccultismCompat.RITUALS) {
            Optional<ItemStack> bound = bind(helper, ritual, tool);
            if (bound.isPresent()) {
                tool = bound.get();
                spent++;
            }
        }
        helper.assertTrue(spent < ForgeweaveOccultismCompat.RITUALS.size(),
                "all four rituals fit on one fresh tool, which means the slot charge is not being applied");
        helper.assertTrue(ForgeweaveModifiers.freeSlots(tool) >= 0, "slot accounting went negative");
        helper.succeed();
    }

    // ------------------------------------------------------------ the toggle

    /**
     * {@code occultismRituals = false}: no new binding is possible, and a tool that already carries
     * one keeps both the stored id and level <em>and</em> the modifier's effect. Every modifier the
     * ladder offers is reachable at the Tool Station too, so none of them is the inert case -- which
     * is D-M7-3's rule applied to a compat path that grants a core modifier rather than its own.
     */
    @GameTest(template = "empty")
    public static void occultismRitualsOffRefusesNewBindingsAndLeavesOldOnesWorking(GameTestHelper helper) {
        ForgeweaveOccultismCompat.Ritual ritual = ForgeweaveOccultismCompat.RITUALS.getFirst();
        ResourceLocation modifier = ResourceLocation.parse(ritual.modifier());
        ItemStack bound = bind(helper, ritual, pickaxe()).orElseThrow();
        int slotsWhileOn = ForgeweaveModifiers.freeSlots(bound);

        ForgeweaveConfig.OCCULTISM_RITUALS.set(false);
        try {
            helper.assertTrue(ForgeweaveOccultismCompat.bind(helper.getLevel().registryAccess(), pickaxe(),
                            modifier, ritual.level()).isEmpty(),
                    "with the toggle off no tool may take a new binding");
            helper.assertTrue(!ForgeweaveOccultismCompat.acceptsRitualTool(pickaxe()),
                    "and the gate itself must answer no, which is what keeps the recipe from matching");

            helper.assertValueEqual(ForgeweaveModifiers.entry(bound, modifier).level(), ritual.level(),
                    "the stored level must survive the toggle -- D-M7-3's rule, not just its effect");
            helper.assertValueEqual(ForgeweaveModifiers.freeSlots(bound), slotsWhileOn,
                    "and the slots it spent stay spent; off is inert, never a refund");
        } finally {
            ForgeweaveConfig.OCCULTISM_RITUALS.set(true);
        }

        helper.assertTrue(ForgeweaveOccultismCompat.acceptsRitualTool(pickaxe()),
                "back on, a fresh tool is accepted again");
        helper.succeed();
    }

    // ------------------------------------------------------------ the tier tables

    /**
     * The two tables the crushing and miner rows are generated from, read on a running server rather
     * than off the JSON ({@code OccultismRecipeTest} does that half). What this pins is that both
     * answer for every Track B ore's own tier and stay monotone, which is the property the whole
     * Track B ladder depends on.
     */
    @GameTest(template = "empty")
    public static void theCrusherAndMinerTablesAreMonotoneOverEveryTrackBOre(GameTestHelper helper) {
        TrackBOre.Tier[] rungs = TrackBOre.Tier.values();
        for (int i = 1; i < rungs.length; i++) {
            helper.assertTrue(ForgeweaveOccultismCompat.minerWeight(rungs[i])
                            < ForgeweaveOccultismCompat.minerWeight(rungs[i - 1]),
                    "a mining spirit must never return " + rungs[i] + " as often as " + rungs[i - 1]);
            helper.assertTrue(ForgeweaveOccultismCompat.crusherTier(rungs[i])
                            >= ForgeweaveOccultismCompat.crusherTier(rungs[i - 1]),
                    rungs[i] + " must not be crushable by a weaker spirit than " + rungs[i - 1]);
        }
        for (TrackBOre ore : TrackBOre.ALL) {
            int tier = ForgeweaveOccultismCompat.crusherTier(ore.tier());
            helper.assertTrue(tier >= 1 && tier <= 4,
                    ore.id() + " maps outside Occultism's foliot-to-marid range of 1 to 4: " + tier);
            helper.assertTrue(ForgeweaveOccultismCompat.minerWeight(ore.tier()) > 0,
                    ore.id() + " must stay reachable by a mining spirit at all");
        }
        helper.succeed();
    }
}
