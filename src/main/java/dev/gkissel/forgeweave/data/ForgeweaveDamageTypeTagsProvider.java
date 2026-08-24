package dev.gkissel.forgeweave.data;

import java.util.concurrent.CompletableFuture;

import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.DamageTypeTagsProvider;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;

import net.neoforged.neoforge.common.data.ExistingFileHelper;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.Protection;

/**
 * The five protection damage-type tags (issue #680, M4-5) -- the 1.20 clone's
 * {@code DamageTypeTagProvider} rows for {@code TinkerTags.DamageTypes}, vanilla members only (the
 * clone's own fluid/shock damage types and its Twilight Forest optionals have no Forgeweave
 * counterpart). Read by {@link Protection}.
 */
public class ForgeweaveDamageTypeTagsProvider extends DamageTypeTagsProvider {
    public ForgeweaveDamageTypeTagsProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider,
            ExistingFileHelper existingFileHelper) {
        super(output, lookupProvider, Forgeweave.MODID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        tag(Protection.MELEE_PROTECTION).add(DamageTypes.PLAYER_ATTACK, DamageTypes.MOB_ATTACK,
                DamageTypes.MOB_ATTACK_NO_AGGRO, DamageTypes.CRAMMING, DamageTypes.STING);
        tag(Protection.PROJECTILE_PROTECTION).addTag(DamageTypeTags.IS_PROJECTILE)
                .add(DamageTypes.FALLING_ANVIL, DamageTypes.FALLING_BLOCK, DamageTypes.FALLING_STALACTITE);
        tag(Protection.FIRE_PROTECTION).addTags(DamageTypeTags.IS_FIRE, DamageTypeTags.IS_LIGHTNING);
        tag(Protection.BLAST_PROTECTION).addTag(DamageTypeTags.IS_EXPLOSION);
        tag(Protection.MAGIC_PROTECTION).addTag(DamageTypeTags.WITCH_RESISTANT_TO)
                .add(DamageTypes.WITHER, DamageTypes.WITHER_SKULL, DamageTypes.DRAGON_BREATH);
    }
}
