package dev.gkissel.forgeweave.compat.mysticalagriculture;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.gamerules.GameRules;

import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.tool.ToolMaterials;

/**
 * The Mystical Agriculture-free half of the augment seam (issue #999, D-M8-20): which essence tier a
 * given stack was built from, how many augment slots that earns it, and whether the integration is
 * switched on at all. Kept clear of every {@code com.blakebr0} type on purpose, the same split
 * {@code CreateGoggles} has from {@code ForgeweaveCreateCompat}: {@code runGameTestServer} runs with
 * no Mystical Agriculture present, so the off path and the tier arithmetic can only be tested from a
 * class that loads without it.
 *
 * <p>{@link MysticalAgricultureCompat} is where these answers are handed to Mystical Agriculture's
 * own {@code ITinkerable}, and its javadoc records the one place that interface cannot take them.
 */
public final class MysticalAugments {

    /** Mystical Agriculture's mod id -- the {@code ModList} guard and the presets' existence gate. */
    public static final String MODID = "mysticalagriculture";

    /** Mystical Agradditions' mod id, which supplies insanium and nothing else Forgeweave uses. */
    public static final String AGRADDITIONS_MODID = "mysticalagradditions";

    /**
     * Forgeweave material id to essence tier, for the materials that make gear augmentable. These are
     * the essence metals of the ladder D-M8-20 names, and only those: {@code prosperity} and
     * {@code soulium} ship as presets too but are not essence metals -- prosperity is the resource the
     * ladder is bought with and soulium is the mob-crop metal -- so gear built from either is not
     * augmentable, which is also what Mystical Agriculture's own gear does with them.
     *
     * <p>{@code insanium} is Mystical Agradditions' rung above awakened supremium. D-M8-20's slot rule
     * is "one, or two for awakened"; insanium sits above awakened, so it continues that at two rather
     * than dropping back to one. It is the one row here that is a judgment call rather than a reading
     * of the issue, and it is called out in the PR.
     */
    private static final Map<String, EssenceTier> ESSENCE_METALS = Map.of(
            "inferium", EssenceTier.INFERIUM,
            "prudentium", EssenceTier.PRUDENTIUM,
            "tertium", EssenceTier.TERTIUM,
            "imperium", EssenceTier.IMPERIUM,
            "supremium", EssenceTier.SUPREMIUM,
            "awakened_supremium", EssenceTier.AWAKENED_SUPREMIUM,
            "insanium", EssenceTier.INSANIUM);

    /** The Mystical Agriculture materials that are not essence metals, which still earn Cognizant Dust. */
    private static final Set<String> OTHER_MYSTICAL_METALS = Set.of("prosperity", "soulium");

    private static final Identifier COGNIZANT_DUST =
            Identifier.fromNamespaceAndPath(MODID, "cognizant_dust");

    private static final ResourceKey<Enchantment> MYSTICAL_ENLIGHTENMENT = ResourceKey.create(Registries.ENCHANTMENT,
            Identifier.fromNamespaceAndPath(MODID, "mystical_enlightenment"));

    /** What Mystical Agriculture itself drops for Mystical Enlightenment I. */
    private static final int WITHER_DUST = 4;
    private static final int DRAGON_DUST = 6;

    private MysticalAugments() {}

    /**
     * How much Cognizant Dust killing {@code victim} with {@code weapon} is worth: the Wither and the
     * Ender Dragon pay out when any part of the weapon is a Mystical Agriculture or Agradditions
     * material, and nothing else does.
     */
    public static int cognizantDust(ItemStack weapon, Entity victim) {
        int dust = victim instanceof WitherBoss ? WITHER_DUST : victim instanceof EnderDragon ? DRAGON_DUST : 0;
        return dust > 0 && enabled() && hasMysticalPart(weapon) ? dust : 0;
    }

    private static boolean hasMysticalPart(ItemStack stack) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null) {
            return false;
        }
        for (Identifier id : materials.all()) {
            if (id.getNamespace().equals(Forgeweave.MODID)
                    && (ESSENCE_METALS.containsKey(id.getPath()) || OTHER_MYSTICAL_METALS.contains(id.getPath()))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Adds the dust to a boss's drops. A weapon with Mystical Enlightenment is left alone, because
     * Mystical Agriculture's own handler already pays that one out, scaled by level.
     *
     * <p>ponytail: this reads Forgeweave's toggle, not Mystical Agriculture's {@code witherDropsCognizant}
     * and {@code dragonDropsCognizant}, which live outside its API jar. Read them here if a pack turns
     * those off and expects this drop to stop as well.
     */
    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof Player player)
                || !level.getGameRules().get(GameRules.MOB_DROPS)) {
            return;
        }
        ItemStack weapon = player.getMainHandItem();
        int dust = cognizantDust(weapon, victim);
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(COGNIZANT_DUST);
        if (dust == 0 || item.isEmpty()) {
            return;
        }
        Optional<Holder.Reference<Enchantment>> enlightenment =
                level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).get(MYSTICAL_ENLIGHTENMENT);
        if (enlightenment.isPresent() && EnchantmentHelper.getTagEnchantmentLevel(enlightenment.get(), weapon) > 0) {
            return;
        }
        event.getDrops().add(new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(),
                new ItemStack(item.get(), dust)));
    }

    /**
     * Whether the augment integration answers at all ({@code compat.mysticalAgricultureAugments}).
     * Read here rather than at a registration site for every question but one -- see
     * {@link MysticalAgricultureCompat} for the registration read and why that half needs a restart.
     */
    public static boolean enabled() {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.MYSTICAL_AGRICULTURE_AUGMENTS);
    }

    /**
     * The essence tier {@code stack} was built from: the highest tier among its parts, so a tool with
     * one supremium part and three iron ones is a supremium tool. Empty when no part is an essence
     * metal, which is what makes gear built from anything else not augmentable, and empty whenever the
     * toggle is off.
     *
     * <p>Highest rather than the head's own tier because a part is a part: D-M8-20 says "a part made
     * of a Mystical Agriculture essence metal", without naming a slot.
     */
    public static Optional<EssenceTier> tierOf(ItemStack stack) {
        if (!enabled()) {
            return Optional.empty();
        }
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null) {
            return Optional.empty();
        }
        EssenceTier best = null;
        for (Identifier id : materials.all()) {
            EssenceTier tier = tierOf(id);
            if (tier != null && (best == null || tier.ordinal() > best.ordinal())) {
                best = tier;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * How many augment slots {@code stack} earns: {@link EssenceTier#augmentSlots()} for the tier it
     * was built from, and zero when it was built from no essence metal or the toggle is off. Computed
     * from the material rather than stored per item, which is what #999 asks for.
     */
    public static int augmentSlots(ItemStack stack) {
        return tierOf(stack).map(EssenceTier::augmentSlots).orElse(0);
    }

    /** The tier a single Forgeweave material id stands at, or null when it is not an essence metal. */
    static EssenceTier tierOf(Identifier materialId) {
        if (!materialId.getNamespace().equals(Forgeweave.MODID)) {
            return null;
        }
        return ESSENCE_METALS.get(materialId.getPath());
    }
}
