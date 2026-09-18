package dev.gkissel.forgeweave.compat.apotheosis;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import dev.shadowsoffire.apotheosis.loot.LootCategory;
import dev.shadowsoffire.apotheosis.socket.SocketedGems;
import dev.shadowsoffire.apotheosis.socket.gem.GemInstance;
import dev.shadowsoffire.apotheosis.socket.gem.UnsocketedGem;
import dev.shadowsoffire.apothic_attributes.modifiers.StackAttributeModifiers;
import dev.shadowsoffire.apothic_attributes.modifiers.StackAttributeModifiersEvent;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The Apotheosis-facing half of Forgeweave's gem-socket compat (issue #969): the one class here that
 * names a {@code dev.shadowsoffire} type, reached only through
 * {@link ApotheosisSockets#installBridge()} and so never classloaded on an install without the mod
 * ({@code ApotheosisSourceIsolationTest}).
 *
 * <h2>Why the socket list is rebuilt per query</h2>
 *
 * <p>Sockets are Forgeweave state (D-M8-1): the gems live in
 * {@code ForgeweaveDataComponents#SOCKETS}, not in Apotheosis' own {@code apotheosis:socketed_gems}
 * component, so Apotheosis' own {@code SocketHelper} cache never sees them and every query below
 * wraps Forgeweave's component in a fresh {@link SocketedGems} instead. That wrapper is what lets
 * Apotheosis' own summing and chaining rules -- protection sums, {@code onHurt} chains -- be used as
 * written rather than reimplemented.
 *
 * <p>ponytail: one wrapper per call, no cache. A stack with no sockets never gets here at all
 * ({@code ApotheosisSockets} checks the socket count first), and a socketed stack builds a handful of
 * records. If this ever shows up in a profile, Apotheosis' own {@code CachedObjectSource} is the
 * pattern to copy.
 *
 * <h2>Loot categories</h2>
 *
 * <p>Which bonus a gem grants depends on the item's Apotheosis {@code LootCategory}, and a Forgeweave
 * tool maps into one by predicate with no wiring of ours: its pickaxes match {@code breaker} through
 * {@code ItemAbilities.PICKAXE_DIG}, its swords match {@code melee_weapon}, its armour matches the
 * slot categories. A pack that wants a different mapping for a specific Forgeweave item has
 * Apotheosis' own {@code apotheosis:loot_category_overrides} data map, which is checked before the
 * predicate walk -- the intended hook, and the reason nothing here hardcodes a category.
 *
 * <p>Every read happens lazily, on a hit, a tooltip or a station craft. None happens at registration:
 * {@code LootCategory.forItem} throws before the category registry has baked, and the gem registry is
 * a datapack reload listener that is not populated until a world loads.
 */
final class ApotheosisGemBonuses implements ApotheosisSockets.Bridge {

    @Override
    public boolean canSeat(ItemStack target, ItemStack gem) {
        UnsocketedGem unsocketed = UnsocketedGem.of(gem);
        return unsocketed.isValid() && unsocketed.canApplyTo(target);
    }

    @Override
    public List<ApotheosisSockets.AttributeGrant> attributeGrants(ItemStack stack) {
        SocketedGems gems = wrap(stack);
        if (gems.isEmpty()) {
            return List.of();
        }
        // The only way to read what a gem's attribute bonus actually grants: AttributeBonus keeps its
        // attribute, operation and per-purity values protected and hands them out solely by filling
        // in this event. Building one with no defaults leaves it holding exactly the gems' own
        // modifiers, which is what gets harvested below.
        StackAttributeModifiersEvent event =
                new StackAttributeModifiersEvent(stack, StackAttributeModifiers.EMPTY);
        gems.addModifiers(event);
        List<ApotheosisSockets.AttributeGrant> grants = new ArrayList<>();
        for (StackAttributeModifiers.Entry entry : event.getModifiers()) {
            ResourceLocation attribute = entry.attribute().unwrapKey()
                    .map(ResourceKey::location)
                    .orElse(null);
            if (attribute != null) {
                grants.add(new ApotheosisSockets.AttributeGrant(attribute, entry.modifier()));
            }
        }
        return List.copyOf(grants);
    }

    @Override
    public float durabilityFraction(ItemStack stack) {
        return (float) wrap(stack).getDurabilityBonusPercentage().sum();
    }

    @Override
    public float protection(ItemStack stack, DamageSource source) {
        return wrap(stack).getDamageProtection(source);
    }

    @Override
    public float reduceDamage(ItemStack stack, DamageSource source, LivingEntity defender, float amount) {
        return wrap(stack).onHurt(source, defender, amount);
    }

    @Override
    public void afterAttack(ItemStack stack, LivingEntity attacker, @Nullable Entity target) {
        if (target != null) {
            // SocketedGems#doPostAttack zeroes and restores the target's invulnerable time around
            // each gem, so it needs a real target rather than tolerating a null one.
            wrap(stack).doPostAttack(attacker, target);
        }
    }

    @Override
    public void afterHurt(ItemStack stack, LivingEntity defender, DamageSource source) {
        wrap(stack).doPostHurt(defender, source);
    }

    /**
     * Forgeweave's socket component as a live {@link SocketedGems}: one entry per socket, in socket
     * order, with {@link GemInstance#EMPTY} standing in for an empty socket and for a gem stack whose
     * gem this pack no longer defines. Both read as invalid, and every {@code SocketedGems} method
     * skips invalid entries, which is what keeps a stale gem id inert rather than a crash.
     */
    private static SocketedGems wrap(ItemStack stack) {
        List<ItemStack> gems = ApotheosisSockets.gems(stack);
        if (gems.isEmpty()) {
            return SocketedGems.EMPTY;
        }
        LootCategory category = LootCategory.forItem(stack);
        List<GemInstance> instances = new ArrayList<>(gems.size());
        for (int socket = 0; socket < gems.size(); socket++) {
            ItemStack gem = gems.get(socket);
            instances.add(gem.isEmpty() ? GemInstance.EMPTY : GemInstance.socketed(category, gem, socket));
        }
        return new SocketedGems(instances);
    }
}
