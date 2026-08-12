package dev.gkissel.forgeweave.combat;

import java.util.Optional;
import java.util.function.Consumer;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveItems;

/**
 * The M1 tool innate retrofit (docs/SCOPE.md M3 issue #164, maintainer directive 2026-08-12: every
 * tool carries a combat innate) -- pickaxe pierce, shovel flatten, hatchet sunder. Attached the same
 * way materials' traits are ({@code ForgeweaveTraits#COMBAT_SEAM}): one {@link CombatSeams.Provider},
 * registered once in {@code Forgeweave}, keyed on which of the three M1 tool {@code Item}s the stack
 * actually is rather than on any data the stack carries -- these are fixed per-tool-type behavior, not
 * per-material like traits.
 *
 * <p>Each innate is a small parameterized behavior class (ADR-0004's M6 library candidates:
 * {@link FlatArmorPiercingDamage}, {@link PotionEffectOnHit}, {@link BonusDamageVsBlocking}), so a
 * future datapack-driven version of this retrofit needs only new JSON, no Java change to those three.
 *
 * <p>Sunder's shield-disable half is not a seam at all -- see {@code ToolItem#canDisableShield}'s
 * javadoc.
 */
public final class ForgeweaveInnates {

    /** Maintainer decision, issue #164 (2026-08-12): 1.0 flat armor-ignoring damage. */
    private static final float PIERCE_DAMAGE = 1.0F;
    /** Maintainer decision, issue #164 (2026-08-12): Slowness I for 1.5s (30 ticks). */
    private static final int FLATTEN_SLOWNESS_DURATION_TICKS = 30;
    /** Maintainer decision, issue #164 (2026-08-12): 20% bonus damage vs a blocking target. */
    private static final float SUNDER_BONUS_DAMAGE_FRACTION = 0.2F;

    /** Pickaxe. */
    public static final CombatSeam PIERCE = new FlatArmorPiercingDamage(PIERCE_DAMAGE);
    /** Shovel. */
    public static final CombatSeam FLATTEN =
            new PotionEffectOnHit(MobEffects.MOVEMENT_SLOWDOWN, 0, FLATTEN_SLOWNESS_DURATION_TICKS);
    /** Hatchet. Bonus damage only -- the shield-disable half lives on {@code ToolItem}. */
    public static final CombatSeam SUNDER = new BonusDamageVsBlocking(SUNDER_BONUS_DAMAGE_FRACTION);

    /** Registered once in {@code Forgeweave}, alongside materials' traits. */
    public static void collect(ItemStack weapon, Consumer<CombatSeam> out) {
        if (weapon.is(ForgeweaveItems.TOOL_PICKAXE.get())) {
            out.accept(PIERCE);
        } else if (weapon.is(ForgeweaveItems.TOOL_SHOVEL.get())) {
            out.accept(FLATTEN);
        } else if (weapon.is(ForgeweaveItems.TOOL_HATCHET.get())) {
            out.accept(SUNDER);
        }
    }

    /**
     * The innate id an assembled tool's tooltip should show ({@code ToolTooltip}), or empty for
     * anything that isn't one of the three M1 tools -- same ids {@link #collect} keys its seams by,
     * so the tooltip can never name an innate the stack doesn't actually carry.
     */
    public static Optional<ResourceLocation> innateId(ItemStack stack) {
        if (stack.is(ForgeweaveItems.TOOL_PICKAXE.get())) {
            return Optional.of(id("pierce"));
        }
        if (stack.is(ForgeweaveItems.TOOL_SHOVEL.get())) {
            return Optional.of(id("flatten"));
        }
        if (stack.is(ForgeweaveItems.TOOL_HATCHET.get())) {
            return Optional.of(id("sunder"));
        }
        return Optional.empty();
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ForgeweaveInnates() {}
}
