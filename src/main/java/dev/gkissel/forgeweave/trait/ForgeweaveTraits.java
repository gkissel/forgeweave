package dev.gkissel.forgeweave.trait;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.PartItem;
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
 * <p>A tool has three materials, each contributing the traits it scopes to that part -- but the
 * <b>same id applies once</b>, no
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
     *
     * <p>Healing an already-undamaged tool needs no guard of its own: 1.21's
     * {@code ItemStack#setDamageValue} clamps to {@code [0, maxDamage]}, exactly as 1.12's
     * {@code Item#setDamage} clamped at 0 before upstream's own heal path could go negative.
     */
    public static final Trait ECOLOGICAL = new Trait() {
        @Override
        public void inventoryTick(ItemStack stack, ServerLevel level, LivingEntity holder) {
            if (holder.getUseItem() == stack) {
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
     * Stone. Two upstream traits in one id, because stone grants both and M1's material schema gave a
     * material exactly one. Issue #94 lifted that limit; splitting this back into upstream's separate
     * {@code cheap} + head-scoped {@code cheapskate} ids is a trait change (new id, new lang keys),
     * not a schema one, so it waits for the milestone that revisits stone's traits:
     *
     * <ul>
     *   <li>{@code TraitCheap#onToolHeal}: {@code newAmount + amount * 5 / 100}, i.e. 5% more
     *       durability per repair, integer-truncated exactly as upstream truncates it.
     *   <li>{@code TraitCheapskate#onToolBuilding} (upstream assigns it to the head part only:
     *       {@code stone.addTrait(cheapskate, HEAD)}): {@code max(1, durability * 80 / 100)} on the
     *       assembled tool, i.e. a 20% durability penalty. Head-only upstream, head-only here --
     *       hence {@link Trait#headDurability} rather than a hook every part could trigger.
     * </ul>
     */
    public static final Trait CHEAP = new Trait() {
        @Override
        public int repairBonus(int amount) {
            return amount * 5 / 100;
        }

        @Override
        public int headDurability(int durability) {
            return Math.max(1, durability * 80 / 100);
        }
    };

    /**
     * Flint. Upstream {@code TraitCrude#damage}: {@code newDamage += damage * 0.05f * level} when
     * {@code target.getTotalArmorValue() == 0}. Flint grants level 1 only -- upstream's head-scoped
     * {@code crude2} has no Forgeweave id yet, which issue #94's schema now leaves room for; see the
     * PR/NOTICE note.
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

    /**
     * The assembled tool's durability after the <b>head</b> material's head-scoped traits adjusted it
     * ({@link Trait#headDurability}), applied in order. Called from {@code ToolStats#compute} with
     * {@code head.traits().forPart(HEAD)}; an id no version of Forgeweave implements simply leaves the
     * durability alone, same as every other hook.
     */
    public static int headDurability(List<ResourceLocation> traitIds, int durability) {
        for (ResourceLocation id : traitIds) {
            Trait trait = REGISTRY.get(id);
            if (trait != null) {
                durability = trait.headDurability(durability);
            }
        }
        return durability;
    }

    /**
     * The trait ids an assembled tool gets from its three materials, each material contributing the
     * traits it scopes to the part it was used as ({@link Material.Traits#forPart}), de-duplicated
     * head-first (see class javadoc).
     */
    public static List<ResourceLocation> resolve(Material head, Material binding, Material handle) {
        return Stream.of(
                head.traits().forPart(PartItem.Kind.HEAD),
                binding.traits().forPart(PartItem.Kind.EXTRA),
                handle.traits().forPart(PartItem.Kind.HANDLE))
                .flatMap(List::stream)
                .distinct()
                .toList();
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
