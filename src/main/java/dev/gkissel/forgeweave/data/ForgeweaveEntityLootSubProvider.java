package dev.gkissel.forgeweave.data;

import java.util.stream.Stream;

import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.MinMaxBounds;
import net.minecraft.advancements.critereon.SlimePredicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.EnchantedCountIncreaseFunction;
import net.minecraft.world.level.storage.loot.functions.SetItemCountFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemEntityPropertyCondition;
import net.minecraft.world.level.storage.loot.providers.number.UniformGenerator;

import net.minecraft.data.loot.EntityLootSubProvider;

import dev.gkissel.forgeweave.entity.ForgeweaveEntities;

/**
 * Loot for Forgeweave's own mobs. Only the blue slime has any (issue #451, parity audit T20); the
 * shuriken and the dropped-tool entity are {@code MobCategory.MISC} and so are not loot-bearing at
 * all, which is why {@link #getKnownEntityTypes} lists the blue slime alone -- the base provider
 * otherwise demands a table for every entity in the game.
 */
public class ForgeweaveEntityLootSubProvider extends EntityLootSubProvider {
    protected ForgeweaveEntityLootSubProvider(HolderLookup.Provider registries) {
        super(FeatureFlags.REGISTRY.allFlags(), registries);
    }

    /**
     * Upstream 1.12's {@code assets/tconstruct/loot_tables/entities/blueslime.json}: one pool, one
     * entry, {@code set_count} 0-2 and a 0-1 looting bonus -- gated (upstream:
     * {@code EntityBlueSlime#getLootTable} returning {@code LootTableList.EMPTY} above size 1) to
     * the smallest slime only, which on 1.21 is the same {@code slime}/{@code size} entity condition
     * vanilla's own slime table states the rule with.
     *
     * <p><b>Deviation, recorded:</b> upstream drops the <em>blue</em> slime ball
     * ({@code tconstruct:edible} metadata 1). Forgeweave has no coloured slime balls yet -- they are
     * parity audit T57's scope, together with the coloured slime fluids and the edible behaviour that
     * comes with them -- so the table names the vanilla slime ball, the same stand-in the Slimesling
     * recipe already makes for congealed slime (#453). Swapping the item is a one-line change to this
     * method when T57 lands.
     *
     * <p>Vanilla's second entry -- one guaranteed slime ball when a frog is the killer -- is vanilla's
     * own frog-eats-slime feature, not upstream's, and a frog cannot eat a blue slime (its
     * {@code frog_food} entity tag names {@code minecraft:slime} and {@code minecraft:magma_cube}
     * only), so it is deliberately absent rather than dropped.
     */
    @Override
    public void generate() {
        add(ForgeweaveEntities.BLUE_SLIME.get(), LootTable.lootTable()
                .withPool(LootPool.lootPool()
                        .add(LootItem.lootTableItem(Items.SLIME_BALL)
                                .apply(SetItemCountFunction.setCount(UniformGenerator.between(0.0F, 2.0F)))
                                .apply(EnchantedCountIncreaseFunction.lootingMultiplier(this.registries,
                                        UniformGenerator.between(0.0F, 1.0F))))
                        .when(LootItemEntityPropertyCondition.hasProperties(LootContext.EntityTarget.THIS,
                                EntityPredicate.Builder.entity()
                                        .subPredicate(SlimePredicate.sized(MinMaxBounds.Ints.exactly(1)))))));
    }

    @Override
    protected Stream<EntityType<?>> getKnownEntityTypes() {
        return Stream.of(ForgeweaveEntities.BLUE_SLIME.get());
    }
}
