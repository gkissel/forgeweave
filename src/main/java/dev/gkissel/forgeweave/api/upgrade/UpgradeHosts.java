package dev.gkissel.forgeweave.api.upgrade;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.world.item.ItemStack;

/**
 * The seam that keeps a partner mod's upgrade from being lost when a part swap makes it invalid
 * (maintainer rule, 2026-09-18): an upgrade is never silently dropped and never left stranded on gear
 * that cannot use it. If it no longer fits, it turns back into items and the player gets them.
 *
 * <p>Forgeweave core owns the question; each {@code compat/<mod>/} package owns its answer and
 * registers a {@link Host} from inside its own {@code ModList.isLoaded} guard. Nothing here names a
 * partner mod's type, so the class is safe to classload on any install and the per-integration source
 * isolation tests have nothing to complain about.
 *
 * <h2>Where it is asked</h2>
 *
 * <p>One place: {@code ToolAssemblyRecipes#resolveExchange}, the single point a part swap is resolved
 * at, for both stations. What the hosts hand back joins the {@code displacedParts} the swap already
 * returns (issue #813), so the station's existing "into the inventory, dropped at the player if it is
 * full" path carries it with no second mechanism.
 *
 * <h2>The contract a host signs</h2>
 *
 * <ul>
 *   <li>{@code reclaim} is called with the tool as it was and the freshly built replacement. The
 *       replacement is a copy nothing else holds yet, so a host mutates <em>that</em> and never the
 *       original.
 *   <li>A host strips only what the replacement genuinely cannot use. An upgrade that still fits stays
 *       exactly where it was, untouched.
 *   <li>What it returns is what the player gets. An empty list means nothing changed.
 *   <li>A host whose own config toggle is off returns nothing and strips nothing. Off is inert, not
 *       destructive.
 * </ul>
 */
public final class UpgradeHosts {

    /** One integration's answer to "what no longer fits, and what does it turn back into". */
    public interface Host {

        /**
         * Strips whatever {@code replacement} cannot carry and returns the items it turns back into.
         *
         * @param original the tool as it was before the swap, never mutated
         * @param replacement the tool as it will be, mutated in place when something has to come off
         * @return the items to hand the player, empty when nothing changed
         */
        List<ItemStack> reclaim(ItemStack original, ItemStack replacement);
    }

    /**
     * Copy-on-write because hosts are registered once during mod construction and read on every part
     * swap resolve, which runs on both the server thread and the client's menu mirror.
     */
    private static final List<Host> HOSTS = new CopyOnWriteArrayList<>();

    /** Registered once, from inside an integration's own {@code ModList} guard. */
    public static void register(Host host) {
        HOSTS.add(host);
    }

    /**
     * Drops the registered hosts' now-invalid upgrades off {@code replacement} and collects the items
     * they turn back into. Empty on a Forgeweave-only install, where no host is registered at all, and
     * empty for a swap every host is happy with.
     */
    public static List<ItemStack> reclaim(ItemStack original, ItemStack replacement) {
        if (HOSTS.isEmpty()) {
            return List.of();
        }
        List<ItemStack> reclaimed = new ArrayList<>();
        for (Host host : HOSTS) {
            reclaimed.addAll(host.reclaim(original, replacement));
        }
        return List.copyOf(reclaimed);
    }

    /** Puts the registry back where an install with no integrations has it. For tests. */
    public static void clear() {
        HOSTS.clear();
    }

    private UpgradeHosts() {}
}
