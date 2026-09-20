package dev.gkissel.forgeweave.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.registry.ServerRegistryRegistry;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptType;
import net.minecraft.resources.ResourceLocation;

import dev.gkissel.forgeweave.api.trait.Trait;
import dev.gkissel.forgeweave.casting.CastingRecipe;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.EmbossingRecipe;
import dev.gkissel.forgeweave.modifier.ModifierDefinition;
import dev.gkissel.forgeweave.modifier.ModifierRecipe;
import dev.gkissel.forgeweave.modifier.WorktableRecipe;
import dev.gkissel.forgeweave.recipe.AlloyRecipe;
import dev.gkissel.forgeweave.recipe.CoreTransformRecipe;
import dev.gkissel.forgeweave.recipe.EntityMeltingRecipe;
import dev.gkissel.forgeweave.recipe.MeltingRecipe;
import dev.gkissel.forgeweave.recipe.SmelteryFuel;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;
import dev.gkissel.forgeweave.trait.ScriptTrait;
import dev.gkissel.forgeweave.trait.TraitDefinition;

/**
 * Forgeweave's KubeJS binding (issue #832, maintainer decision 2026-09-02): traits whose runtime
 * logic a {@link TraitDefinition}'s JSON parameters cannot express are written as startup-script
 * callbacks instead. One event, one builder:
 *
 * <pre>{@code
 * // kubejs/startup_scripts/forgeweave_traits.js
 * ForgeweaveEvents.traits(event => {
 *     event.register('mypack:frosty')
 *         .onAfterHit((stack, level, attacker, target) => target.potionEffects.add('minecraft:slowness', 60))
 *         .onMiningSpeed((stack, effective, originalSpeed, speed) => speed * 1.25)
 *         .bonusSlots(1)
 * })
 * }</pre>
 *
 * The id is then named from material JSON like any other trait, and the pack supplies its {@code
 * trait.mypack.frosty.name} / {@code .description} lang keys. Every callback matches a {@link
 * dev.gkissel.forgeweave.api.trait.Trait} hook by name -- see {@link ScriptTrait} for the full list.
 *
 * <p><b>Isolation.</b> Nothing in the mod references this class: KubeJS itself instantiates it from
 * {@code kubejs.plugins.txt} when KubeJS is installed, and without KubeJS the class is never
 * classloaded (the {@code jei}/{@code ponder} soft-dependency idiom -- build.gradle's
 * {@code compileOnly} + {@code localRuntime} split, an {@code optional} entry in
 * {@code neoforge.mods.toml}). The trait system's own half ({@link ScriptTrait},
 * {@link ForgeweaveTraits#registerScripted}) is plain Java with no KubeJS import.
 *
 * <p>Posted from {@link #afterScriptsLoaded} for the startup script type, i.e. once the scripts
 * have attached their listeners -- which also covers {@code /kubejs reload_startup_scripts},
 * since a re-registered id simply replaces the previous {@link ScriptTrait}.
 */
public final class ForgeweaveKubeJSPlugin implements KubeJSPlugin {

    public static final EventGroup GROUP = EventGroup.of("ForgeweaveEvents");

    /** {@code ForgeweaveEvents.traits(event => ...)}, startup scripts only. */
    public static final EventHandler TRAITS = GROUP.startup("traits", () -> TraitsKubeEvent.class);

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        registry.register(GROUP);
    }

    /**
     * Hands KubeJS every datapack registry Forgeweave owns (issue #1083), so a script can write a
     * melting, casting, alloy, fuel, entity-melting, core-transform, modifier, embossing or
     * worktable entry, a material, or a trait or modifier definition:
     *
     * <pre>{@code
     * // kubejs/server_scripts/forgeweave_recipes.js
     * ServerEvents.registry('forgeweave:melting_recipe', event => {
     *     event.createFromJson('mypack:molten_mythril', {
     *         input: { tag: 'c:ingots/mythril' },
     *         fluid: 'forgeweave:molten_iron',
     *         amount: 144
     *     })
     * })
     * }</pre>
     *
     * <p><b>Why this and not {@code event.recipes.forgeweave.melting(...)}.</b> KubeJS's recipe
     * schema API edits the vanilla recipe manager, whose entries live under {@code data/<ns>/recipe/}
     * and carry a {@code type} field. None of Forgeweave's eight recipe types is one of those: each
     * is a datapack <em>registry</em> loaded by {@code RegistryDataLoader}, which the recipe manager
     * never sees, so a {@code RecipeSchema} for them would describe something KubeJS could not
     * write. {@code ServerEvents.registry} is the seam that does reach a datapack registry, and it
     * reads the JSON through Forgeweave's own codec, so a bad field fails the same way a file in a
     * pack's {@code data/} folder does. {@code docs/addons.md} says the same thing for pack authors.
     */
    @Override
    public void registerServerRegistries(ServerRegistryRegistry registry) {
        registry.register(Material.REGISTRY, Material.CODEC, Material.class);
        registry.register(TraitDefinition.REGISTRY, TraitDefinition.CODEC, TraitDefinition.class);
        registry.register(ModifierDefinition.REGISTRY, ModifierDefinition.CODEC, ModifierDefinition.class);
        registry.register(MeltingRecipe.REGISTRY, MeltingRecipe.CODEC, MeltingRecipe.class);
        registry.register(CastingRecipe.REGISTRY, CastingRecipe.CODEC, CastingRecipe.class);
        registry.register(AlloyRecipe.REGISTRY, AlloyRecipe.CODEC, AlloyRecipe.class);
        registry.register(SmelteryFuel.REGISTRY, SmelteryFuel.CODEC, SmelteryFuel.class);
        registry.register(EntityMeltingRecipe.REGISTRY, EntityMeltingRecipe.CODEC, EntityMeltingRecipe.class);
        registry.register(CoreTransformRecipe.REGISTRY, CoreTransformRecipe.CODEC, CoreTransformRecipe.class);
        registry.register(ModifierRecipe.REGISTRY, ModifierRecipe.CODEC, ModifierRecipe.class);
        registry.register(EmbossingRecipe.REGISTRY, EmbossingRecipe.CODEC, EmbossingRecipe.class);
        registry.register(WorktableRecipe.REGISTRY, WorktableRecipe.CODEC, WorktableRecipe.class);
    }

    @Override
    public void afterScriptsLoaded(ScriptManager manager) {
        if (manager.scriptType == ScriptType.STARTUP) {
            TRAITS.post(ScriptType.STARTUP, new TraitsKubeEvent());
        }
    }

    /** The one event: {@link #register} hands back the builder for a new script-defined trait id. */
    public static final class TraitsKubeEvent implements KubeEvent {

        /**
         * @param id the trait id material JSON will name; rejected if a built-in Java trait owns it
         *     ({@link ForgeweaveTraits#registerScripted})
         */
        public ScriptTrait register(ResourceLocation id) {
            ScriptTrait trait = new ScriptTrait();
            ForgeweaveTraits.registerScripted(id, trait);
            return trait;
        }
    }
}
