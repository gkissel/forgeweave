package dev.gkissel.forgeweave.tool;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.combat.CombatHit;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * The multi-hit damage ledger melee XP is paid out of (docs/SCOPE.md M7, D-M7-4; issue #919): for
 * one damaged entity, how much damage each of a player's tools has dealt it so far. Ported from
 * Tinkers' Tool Leveling's {@code CapabilityDamageXp} / {@code DamageXpHandler} / {@code IDamageXp}
 * trio and {@code EntityXpHandler} (MIT), collapsed into one NeoForge data attachment -- 1.21.1 has
 * no capability to hang the map off and needs no interface for a single implementation.
 *
 * <p><b>Melee XP is paid on the kill, never on the hit.</b> A blow that leaves the target standing
 * banks its damage here; every tool in the ledger is paid its own accumulated total the moment the
 * target dies, whatever finally killed it. That is upstream's headline behavior, and the reason the
 * ledger has to live on the <em>target</em> and be serialized with it: the mob may wander off, the
 * chunk may unload, and the player may pocket the sword before it bleeds out.
 *
 * <p><b>Tool identity.</b> Upstream keyed its map by the {@code ItemStack} itself and matched with
 * {@code ToolCore#isEqualTinkersItem} (same item, same parts -- durability ignored), falling back to
 * that from instance equality. Neither works on 1.21.1: a stack's components change on every hit as
 * durability and banked XP move, so nothing derived from the stack stays stable between the hit and
 * the death. Forgeweave keys the ledger by a per-tool {@code forgeweave:tool_id} UUID instead,
 * minted lazily by {@link #toolId} the first time a tool banks damage. One scan of the player's
 * inventory then does the job of upstream's two: the id follows the exact tool the blow was struck
 * with wherever in the inventory it has since been moved, and never matches a second, identical tool
 * the way {@code isEqualTinkersItem} could.
 */
public final class DamageXpLedger {

    /** {@code player UUID -> tool UUID -> damage that tool has dealt this entity}. */
    private final Map<UUID, Map<UUID, Float>> byPlayer;

    public static final Codec<DamageXpLedger> CODEC = Codec
            .unboundedMap(UUIDUtil.STRING_CODEC, Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.FLOAT))
            .xmap(DamageXpLedger::new, ledger -> ledger.byPlayer);

    /** An empty ledger -- what an entity that has never been hit with a Forgeweave tool has. */
    public DamageXpLedger() {
        this(Map.of());
    }

    private DamageXpLedger(Map<UUID, Map<UUID, Float>> byPlayer) {
        this.byPlayer = new LinkedHashMap<>();
        byPlayer.forEach((player, tools) -> this.byPlayer.put(player, new LinkedHashMap<>(tools)));
    }

    public boolean isEmpty() {
        return byPlayer.isEmpty();
    }

    /** Damage {@code tool} has banked against this entity for {@code player}, {@code 0} if none. */
    public float damage(UUID player, UUID tool) {
        return byPlayer.getOrDefault(player, Map.of()).getOrDefault(tool, 0.0F);
    }

    /** Upstream's {@code DamageXpHandler#addDamageFromTool}: accumulate, never overwrite. */
    public void add(UUID player, UUID tool, float damage) {
        byPlayer.computeIfAbsent(player, ignored -> new LinkedHashMap<>()).merge(tool, damage, Float::sum);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof DamageXpLedger ledger && byPlayer.equals(ledger.byPlayer);
    }

    @Override
    public int hashCode() {
        return byPlayer.hashCode();
    }

    /* The two grant sites */

    /**
     * Upstream's {@code ModToolLeveling#afterHit}, on Forgeweave's on-hit seam: a killing blow pays
     * {@code round(damageDealt)} to the weapon that landed it straight away, anything the target
     * survives is banked for {@link #payOut}.
     *
     * <p>Upstream reads "did I kill it" as {@code !target.isEntityAlive()} because its hook runs
     * <em>after</em> the death event. Ours runs before it -- {@code LivingDamageEvent.Post} fires
     * inside {@code actuallyHurt}, once health has been taken off and before {@code hurt} gets round
     * to calling {@code die} -- so the same question is asked of {@link LivingEntity#isDeadOrDying}
     * and gets the same answer. The net grant per tool is unchanged either way: the killing blow is
     * never in the ledger, so {@link #payOut} pays exactly what upstream's ledger paid.
     *
     * <p>Projectile hits are excluded: upstream grants those off draw time from its own separate
     * {@code afterHit} overload (docs/SCOPE.md D-M7-6, issue #920), not off damage dealt.
     */
    public static void afterMeleeHit(CombatHit hit, float damage) {
        if (hit.isProjectile() || damage <= 0.0F || !ForgeweaveConfig.enabled(ForgeweaveConfig.TOOL_LEVELING)) {
            return;
        }
        if (!(hit.attacker() instanceof Player player)) {
            return; // upstream's `player instanceof EntityPlayer`: a mob's swing earns nothing
        }
        if (hit.target().isDeadOrDying()) {
            ToolLeveling.addXp(hit.weapon(), Math.round(damage), player instanceof ServerPlayer server ? server : null);
            return;
        }
        hit.target().getData(ForgeweaveAttachments.DAMAGE_XP)
                .add(player.getUUID(), toolId(hit.weapon()), damage);
    }

    /**
     * Upstream's {@code EntityXpHandler#onDeath} into {@code DamageXpHandler#distributeXpToTools}:
     * every tool that damaged this entity and survives in its owner's inventory is paid its own
     * accumulated total, and the ledger is cleared so a resurrected entity cannot pay twice.
     *
     * <p>Called from {@code CombatSeams#onDeath} before its own Forgeweave-tool gate, because what
     * finally killed the mob is none of the ledger's business -- it may have burned to death an hour
     * after the sword that hurt it was put in a chest.
     */
    public static void payOut(LivingEntity dead) {
        if (!(dead.level() instanceof ServerLevel level) || !dead.hasData(ForgeweaveAttachments.DAMAGE_XP)) {
            return;
        }
        DamageXpLedger ledger = dead.getData(ForgeweaveAttachments.DAMAGE_XP);
        if (ledger.isEmpty()) {
            return;
        }
        Map<UUID, Map<UUID, Float>> owed = new HashMap<>(ledger.byPlayer);
        ledger.byPlayer.clear();
        owed.forEach((playerId, tools) -> {
            if (level.getPlayerByUUID(playerId) instanceof ServerPlayer player) {
                tools.forEach((toolId, damage) -> pay(player, toolId, damage));
            }
        });
    }

    /**
     * Upstream's {@code distributeXpToPlayerForTool}, one scan instead of two -- see the class
     * javadoc on tool identity. The inventory's container view spans the main slots, the armor slots
     * and the offhand, so a tool that has been swapped anywhere the player still carries it is
     * found; one they have dropped or stored is not, exactly as upstream.
     */
    private static void pay(ServerPlayer player, UUID toolId, float damage) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (toolId.equals(stack.get(ForgeweaveDataComponents.TOOL_ID.get()))) {
                ToolLeveling.addXp(stack, Math.round(damage), player);
                return;
            }
        }
    }

    /** This tool's ledger id, minted and stored on the stack the first time it banks damage. */
    private static UUID toolId(ItemStack tool) {
        UUID id = tool.get(ForgeweaveDataComponents.TOOL_ID.get());
        if (id == null) {
            id = UUID.randomUUID();
            tool.set(ForgeweaveDataComponents.TOOL_ID.get(), id);
        }
        return id;
    }
}
