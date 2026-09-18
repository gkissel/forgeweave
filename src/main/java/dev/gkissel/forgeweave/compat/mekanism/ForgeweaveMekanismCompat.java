package dev.gkissel.forgeweave.compat.mekanism;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.IEventBus;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.compat.mekanism.modules.MekanismModuleContainer;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * The Mekanism-free half of Forgeweave's module container compat (issue #993, docs/SCOPE.md M8,
 * D-M8-15): the metal that earns the container, the test for "is this stack one", and the single
 * entry point {@code Forgeweave}'s constructor calls from inside its {@code ModList} guard.
 *
 * <p>Same split {@code ForgeweaveDraconicCompat} uses. This class is classloaded on every install,
 * Mekanism or not, so it names no {@code mekanism} type: {@link MekanismModuleContainer} does, and is
 * only ever reached through one of the guards here. {@code MekanismSourceIsolationTest} keeps it that
 * way.
 *
 * <h2>What makes a stack a container</h2>
 *
 * <p>A tool with an {@code atomic_matter_alloy} part, and armour whose plating is that metal. Read
 * off {@code ForgeweaveDataComponents#TOOL_MATERIALS} rather than off the trait list the way
 * {@code ForgeweaveDraconicCompat#isWeldTool} does: the welds each grant their own {@code evolved}
 * marker trait, whereas D-M8-13 fixes this metal's trait list at {@code infused} alone, and other
 * materials may grant that too. Nothing here has to identify a stack whose parts have been stripped,
 * which is the case the trait-list read exists for.
 */
public final class ForgeweaveMekanismCompat {

    /** Mekanism's mod id -- the {@code ModList} guards, the IMC target and every recipe condition key on it. */
    public static final String MODID = "mekanism";

    /** The metal that earns the module container (D-M8-13). */
    public static final ResourceLocation ATOMIC_MATTER_ALLOY =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "atomic_matter_alloy");

    /** Mekanism's own atomic alloy, the nucleosynthesizing recipe's item input. */
    public static final String ATOMIC_ALLOY_ITEM = MODID + ":alloy_atomic";

    /** Mekanism's antimatter, the nucleosynthesizing recipe's chemical input. */
    public static final String ANTIMATTER_CHEMICAL = MODID + ":antimatter";

    /** {@link #nucleosynthesizingAlloyCount()}'s own default: one atomic alloy per ingot. */
    public static final int NUCLEOSYNTHESIZING_ALLOY_COUNT_DEFAULT = 1;

    /**
     * {@link #nucleosynthesizingAntimatterAmount()}'s own default, in mB. Mekanism's own hardest
     * nucleosynthesizing row spends 5, so this is above everything the machine is asked for by the mod
     * that ships it.
     */
    public static final int NUCLEOSYNTHESIZING_ANTIMATTER_DEFAULT = 8;

    /** {@link #nucleosynthesizingDuration()}'s own default, in ticks. Mekanism's own longest row is 1250. */
    public static final int NUCLEOSYNTHESIZING_DURATION_DEFAULT = 2000;

    /** How many of Mekanism's atomic alloys one {@code atomic_matter_alloy} ingot takes. */
    public static int nucleosynthesizingAlloyCount() {
        return ForgeweaveConfig.mekanismNucleosynthesizingAlloyCount();
    }

    /** How much antimatter, in mB, one {@code atomic_matter_alloy} ingot takes. */
    public static int nucleosynthesizingAntimatterAmount() {
        return ForgeweaveConfig.mekanismNucleosynthesizingAntimatter();
    }

    /** How long, in ticks, the nucleosynthesizer takes over one ingot. */
    public static int nucleosynthesizingDuration() {
        return ForgeweaveConfig.mekanismNucleosynthesizingDuration();
    }

    /**
     * Whether any part of {@code stack} is made of {@link #ATOMIC_MATTER_ALLOY} -- the tools and
     * armour pieces that host Mekanism modules. False for a stack with no Forgeweave materials at all,
     * which is what makes a vanilla pickaxe not a container rather than an empty one.
     */
    public static boolean isContainerStack(ItemStack stack) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        return materials != null && materials.all().contains(ATOMIC_MATTER_ALLOY);
    }

    /**
     * Installs the module container. Called from {@code Forgeweave}'s constructor, inside its
     * {@code ModList} guard; the class behind this call names {@code mekanism} types and cannot link
     * without the mod.
     */
    public static void register(IEventBus modEventBus) {
        MekanismModuleContainer.register(modEventBus);
    }

    private ForgeweaveMekanismCompat() {}
}
