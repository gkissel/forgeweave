package dev.gkissel.forgeweave.block;

import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

/**
 * Graveyard soil and consecrated soil (issue #429), two more states of the same upstream 1.12
 * {@code BlockSoil} that grout and the slimy muds already port (NOTICE.md) -- so they share
 * {@code ForgeweaveBlocks#soilProperties}. What makes them their own class rather than another
 * {@code registerSimpleBlock} is upstream's {@code onEntityWalk}: an undead mob standing on
 * graveyard soil is healed 1 health a tick, one standing on consecrated soil takes 1 magic damage
 * and is set alight for a second ({@code BlockSoil#processGraveyardSoil} /
 * {@code #processConsecratedSoil}).
 *
 * <p>Upstream keys both off {@code EnumCreatureAttribute.UNDEAD} and gates on
 * {@code entity instanceof EntityLiving}, i.e. mobs only, never the player -- ported here as
 * {@link Mob} plus {@link EntityTypeTags#UNDEAD}, the same reading of that enum
 * {@code combat.HitCondition#UNDEAD} already uses for smite.
 */
public class UndeadSoilBlock extends Block {

    private final boolean consecrated;

    public UndeadSoilBlock(Properties properties, boolean consecrated) {
        super(properties);
        this.consecrated = consecrated;
    }

    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (!(entity instanceof Mob mob) || !mob.getType().is(EntityTypeTags.UNDEAD)) {
            super.stepOn(level, pos, state, entity);
            return;
        }
        if (consecrated) {
            mob.hurt(level.damageSources().magic(), 1.0F);
            mob.igniteForSeconds(1.0F);
        } else {
            mob.heal(1.0F);
        }
    }
}
