package dev.gkissel.forgeweave.loot;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.LootItemConditionalFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunctionType;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * {@code forgeweave:assemble_tool}, the loot function that turns a bare Forgeweave tool or armor item
 * into an assembled one (issue #970, docs/SCOPE.md M8, D-M8-1).
 *
 * <h2>Why this exists</h2>
 *
 * <p>A Forgeweave tool is only a tool once it carries parts: a bare {@code forgeweave:pickaxe} has no
 * materials, no stats and no durability, which is why the creative tab hands out an assembled armor
 * piece rather than the registered item ({@code ForgeweaveCreativeTab#addToolItems}). Nothing outside
 * the Tool Station could build one, so nothing could put one in a loot table -- and D-M8-1 needs
 * exactly that, because Apotheosis affixes whatever a loot table produced rather than generating the
 * base item itself. This is the seam: a loot table entry names the tool item, this function gives it
 * parts, and Apotheosis' own loot handling then affixes the result like any other item.
 *
 * <h2>The decided answer to "where do a loot tool's parts come from"</h2>
 *
 * <p>Issue #970 offers three options -- a fixed material per loot table, a random pick from a tag, or
 * a Forgeweave-side loot function -- and this is the third, because the first two cannot be
 * implemented without it: there is no vanilla function that writes Forgeweave's material components.
 * The one knob is {@code materials}, a list of material ids; one is picked uniformly at random per
 * roll with the loot context's own {@link net.minecraft.util.RandomSource} and used for every part
 * slot, so the tool comes out of a chest with uniform materials the way the creative tab's and the
 * screenshot harness's do. A single-element list is therefore the "fixed material" option and a longer
 * one the "random pick", both from the same field; a material tag would need a material-tag registry
 * Forgeweave does not have, and buys nothing a list does not.
 *
 * <p>Forgeweave ships no loot table using this. Which chests hold Forgeweave gear is a pack's
 * decision, not the mod's, and a mod that injected its tools into vanilla chests uninvited would be
 * the opposite of M8's "runs identically with none of them installed" rule. The function is the
 * documented seam a pack or Apotheosis' own affix loot writes against.
 *
 * <h2>Foreign components survive</h2>
 *
 * <p>{@link #run} applies the assembled stack's components <em>onto</em> the incoming stack rather
 * than returning a fresh one, so anything already on it -- a name from {@code set_name}, an Apotheosis
 * affix component from a function that ran first -- is still there afterwards. That is JC-D's rule
 * (affix state is Apotheosis', untouched and unmirrored) landing on the one Forgeweave path that
 * builds a tool from nothing.
 */
public class AssembleTool extends LootItemConditionalFunction {

    public static final DeferredRegister<LootItemFunctionType<?>> LOOT_FUNCTIONS =
            DeferredRegister.create(Registries.LOOT_FUNCTION_TYPE, Forgeweave.MODID);

    public static final MapCodec<AssembleTool> CODEC = RecordCodecBuilder.mapCodec(instance ->
            commonFields(instance)
                    .and(ResourceLocation.CODEC.listOf().fieldOf("materials")
                            .forGetter(function -> function.materials))
                    .apply(instance, AssembleTool::new));

    public static final DeferredHolder<LootItemFunctionType<?>, LootItemFunctionType<AssembleTool>> TYPE =
            LOOT_FUNCTIONS.register("assemble_tool", () -> new LootItemFunctionType<>(CODEC));

    private final List<ResourceLocation> materials;

    protected AssembleTool(List<LootItemCondition> predicates, List<ResourceLocation> materials) {
        super(predicates);
        this.materials = List.copyOf(materials);
    }

    /** The candidate materials, in the order the JSON lists them. */
    public List<ResourceLocation> materials() {
        return materials;
    }

    @Override
    public LootItemFunctionType<AssembleTool> getType() {
        return TYPE.get();
    }

    /**
     * Assembles {@code stack} from one randomly picked material, or leaves it exactly as it was when
     * it is not an assemblable Forgeweave item, when {@code materials} is empty, or when the picked
     * material id does not resolve in the loaded datapack. Leaving the stack alone rather than
     * throwing is deliberate: a loot roll is not a place to crash a server over a typo in a pack, and
     * an unassembled tool in a chest is visibly wrong in a way a log line is not.
     */
    @Override
    protected ItemStack run(ItemStack stack, LootContext context) {
        Optional<ToolAssemblyRecipes.Entry> entry = ToolAssemblyRecipes.entryFor(stack);
        if (entry.isEmpty() || materials.isEmpty()) {
            return stack;
        }
        ResourceLocation material = materials.get(context.getRandom().nextInt(materials.size()));
        HolderLookup.Provider registries = context.getLevel().registryAccess();
        return ToolAssemblyRecipes
                .assemble(registries, entry.get(),
                        Collections.nCopies(entry.get().slotCount(), material))
                .map(assembled -> {
                    stack.applyComponents(assembled.getComponentsPatch());
                    return stack;
                })
                .orElse(stack);
    }
}
