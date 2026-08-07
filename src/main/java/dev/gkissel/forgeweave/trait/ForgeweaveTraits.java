package dev.gkissel.forgeweave.trait;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.material.Material;

/**
 * The trait ids Forgeweave ships and the behavior behind each, plus the aggregation the seams in
 * {@code ToolItem} and {@code ToolAssemblyRecipes} call. Ported from upstream 1.12's
 * {@code tools/traits/} (pinned commit in NOTICE.md); each constant's javadoc cites its class.
 *
 * <h2>Registry</h2>
 *
 * <p>A plain immutable map, not a Minecraft registry: trait behavior is Java and only ever added by
 * a mod update (ADR-0002), so there is nothing for a registry event to contribute. A material naming
 * an id that isn't here logs once and does nothing, which is what lets a datapack ship a material
 * before the Java that gives it a trait.
 *
 * <h2>Stacking</h2>
 *
 * <p>A tool has three materials, so up to three trait ids -- but the <b>same id applies once</b>, no
 * matter how many parts grant it. That is upstream's rule too: {@code TraitBonusDamage#applyEffect}
 * guards on {@code !TinkerUtil.hasTrait(...)} and {@code AbstractTraitLeveled#applyEffect} on a
 * per-identifier {@code boolean} tag, so a second application of the same trait is a no-op.
 * {@link #resolve} therefore de-duplicates while keeping head/binding/handle order, and the result
 * is stored on the tool as {@code forgeweave:traits} at assembly -- reading it back needs no
 * registry access, which is what lets {@code ToolItem#getDefaultAttributeModifiers} (which gets a
 * bare {@code ItemStack} and nothing else) see traits at all.
 */
public final class ForgeweaveTraits {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Ids already reported as unknown, so a bad material JSON logs once rather than every tick. */
    private static final Set<ResourceLocation> WARNED_UNKNOWN = ConcurrentHashMap.newKeySet();

    /**
     * Wood. Upstream {@code TraitEcological}: server side, a 1-in-{@code 20 * 40} chance per tick to
     * regenerate one durability, skipped while the holder is actively using the tool. Upstream
     * routes this through {@code ToolHelper#healTool} -&gt; {@code #damageTool}, which returns early on
     * a broken tool; the {@code ToolItem} seam applies that same guard to every trait.
     */
    public static final Trait ECOLOGICAL = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (holder.getUseItem() == stack || stack.getDamageValue() == 0) {
                return;
            }
            if (level.getRandom().nextInt(20 * ECOLOGICAL_PERIOD_SECONDS) == 0) {
                stack.setDamageValue(stack.getDamageValue() - 1);
            }
        }
    };

    /** Upstream {@code TraitEcological#chance}: one durability roughly every 40 seconds. */
    private static final int ECOLOGICAL_PERIOD_SECONDS = 40;

    /**
     * Stone. Upstream {@code TraitCheap#onToolHeal}: {@code newAmount + amount * 5 / 100}, i.e. 5%
     * more durability per repair, integer-truncated exactly as upstream truncates it.
     */
    public static final Trait CHEAP = new Trait() {
        @Override
        public int repairBonus(int amount) {
            return amount * 5 / 100;
        }
    };

    /**
     * Flint. Upstream {@code TraitCrude#damage}: {@code newDamage += damage * 0.05f * level} when
     * {@code target.getTotalArmorValue() == 0}. Forgeweave materials carry one trait id each
     * (ADR-0002), so flint grants level 1 -- see the PR/NOTICE note on upstream's {@code crude2}.
     */
    public static final Trait CRUDE = new Trait() {
        @Override
        public float bonusDamageAgainst(LivingEntity target, float damage) {
            return target.getArmorValue() > 0 ? 0.0F : damage * 0.05F;
        }
    };

    /**
     * Bone. Upstream registers it as {@code new TraitBonusDamage("fractured", 1.5f)}, whose
     * {@code applyEffect} does {@code data.attack += damage} once at build time -- a flat +1.5 on the
     * tool's own attack stat, not a conditional hit-time bonus, so it belongs on the attribute
     * modifier and shows up in the tool's stats like upstream's does.
     */
    public static final Trait FRACTURED = new Trait() {
        @Override
        public float attackDamageBonus() {
            return 1.5F;
        }
    };

    private static final Map<ResourceLocation, Trait> REGISTRY = Map.of(
            id("ecological"), ECOLOGICAL,
            id("cheap"), CHEAP,
            id("crude"), CRUDE,
            id("fractured"), FRACTURED);

    /** The trait ids an assembled tool gets from its three materials, de-duplicated (see class javadoc). */
    public static List<ResourceLocation> resolve(Material head, Material binding, Material handle) {
        return List.of(head.trait(), binding.trait(), handle.trait()).stream().distinct().toList();
    }

    /** The traits of an assembled tool, in the order {@link #resolve} stored them. */
    public static List<Trait> of(ItemStack stack) {
        List<ResourceLocation> ids = stack.get(ForgeweaveDataComponents.TRAITS.get());
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Trait> traits = new ArrayList<>(ids.size());
        for (ResourceLocation id : ids) {
            Trait trait = REGISTRY.get(id);
            if (trait != null) {
                traits.add(trait);
            } else if (WARNED_UNKNOWN.add(id)) {
                LOGGER.warn("Unknown trait '{}' on {}; ignoring it. Materials may only name traits this "
                        + "version of Forgeweave implements (ADR-0002).", id, stack.getItem());
            }
        }
        return traits;
    }

    /** Flat attack damage the tool's traits add, folded into its one attack damage modifier. */
    public static float attackDamageBonus(ItemStack stack) {
        float bonus = 0.0F;
        for (Trait trait : of(stack)) {
            bonus += trait.attackDamageBonus();
        }
        return bonus;
    }

    /** Extra durability the tool's traits add to one repair item's worth of repair. */
    public static int repairBonus(ItemStack stack, int amount) {
        int bonus = 0;
        for (Trait trait : of(stack)) {
            bonus += trait.repairBonus(amount);
        }
        return bonus;
    }

    /** Called from {@code ToolItem#inventoryTick}, which has already ruled out clients and Broken tools. */
    public static void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
        for (Trait trait : of(stack)) {
            trait.inventoryTick(stack, level, holder);
        }
    }

    /**
     * Applies target-dependent damage traits to an attack made with a Forgeweave tool. Upstream runs
     * its {@code ITrait#damage} hook inside {@code ToolHelper#attackEntity} before armor is applied
     * and feeds each trait the untouched original damage; {@link LivingIncomingDamageEvent} is the
     * 1.21 equivalent point ("after invulnerability checks but before any damage
     * processing/mitigation"), and {@link LivingIncomingDamageEvent#getOriginalAmount()} is that same
     * untouched value. Registered on the game event bus in {@code Forgeweave}.
     */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        ItemStack weapon = event.getSource().getWeaponItem();
        if (weapon == null || !(weapon.getItem() instanceof ToolItem) || ToolItem.isBroken(weapon)) {
            return;
        }
        float bonus = 0.0F;
        for (Trait trait : of(weapon)) {
            bonus += trait.bonusDamageAgainst(event.getEntity(), event.getOriginalAmount());
        }
        if (bonus != 0.0F) {
            event.setAmount(event.getAmount() + bonus);
        }
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ForgeweaveTraits() {}
}
