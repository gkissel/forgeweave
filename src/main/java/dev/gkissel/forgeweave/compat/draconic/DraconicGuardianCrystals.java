package dev.gkissel.forgeweave.compat.draconic;

import com.brandon3055.draconicevolution.entity.GuardianCrystalEntity;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * Maintainer decision 2026-09-06: everything chaotic hurts the Chaos Guardian's crystals. Draconic
 * Evolution's guardian crystals shrug off any hit whose damage source is not chaotic-tier -- its
 * {@code GuardianFightManager#getCrystalDamageModifier} reads the source's tech level off the
 * {@code draconicevolution:chaotic} damage type tag (or off one of DE's own melee items) and answers
 * 0 for anything else. A Forgeweave tool at the chaotic tier ({@link ForgeweaveDraconicCompat#evolvedLevel}
 * 4: voidweld or a chaotic core) therefore hits a crystal with {@link #CHAOTIC_STRIKE}, a Forgeweave
 * damage type that {@code data/draconicevolution/tags/damage_type/chaotic.json} puts in that tag,
 * for the wielder's full attack damage, in place of the vanilla player attack the crystal ignores.
 *
 * <p>Only crystals are handled here; every other target keeps the vanilla attack and Forgeweave's
 * own combat seams. Nothing in this class is derived from Draconic Evolution (ADR-0003).
 */
public final class DraconicGuardianCrystals {
    public static final ResourceKey<DamageType> CHAOTIC_STRIKE =
            ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "chaotic_strike"));

    private DraconicGuardianCrystals() {}

    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getTarget() instanceof GuardianCrystalEntity crystal) || crystal.level().isClientSide) {
            return;
        }
        Player player = event.getEntity();
        ItemStack held = player.getMainHandItem();
        if (!ToolAssemblyRecipes.isAssembled(held) || ForgeweaveDraconicCompat.evolvedLevel(held) < 4) {
            return;
        }
        DamageSource source = new DamageSource(
                crystal.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CHAOTIC_STRIKE),
                player);
        crystal.hurt(source, (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE));
        event.setCanceled(true);
    }
}
