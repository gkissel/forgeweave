package dev.gkissel.forgeweave.modifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * Upstream 1.12's modifier/trait/enchantment refusal layer (parity audit T23, issue #454):
 * {@code library/modifiers/Modifier#canApply} walks the tool's traits, then its modifiers, then its
 * enchantments, and throws a {@code TinkerGuiException} the moment either side's
 * {@code canApplyTogether} says no. The clone has exactly four overriders -- {@code ModSilktouch},
 * {@code ModLuck}, {@code TraitSqueaky}, {@code TraitAutosmelt} (plus the unported
 * {@code ModBlasting}) -- so the whole rule set is the two tables below rather than a per-modifier
 * hook: every check is by id, which is all a stored {@link ModifierEntry} or trait id carries anyway.
 *
 * <p>{@code ModExtraTrait#canApplyTogether} refuses an embossment whenever any donor trait would be
 * refused, so {@code Embossing} runs {@link #refusal} once per donor trait with the embossment's name.
 */
public final class ModifierCompatibility {

    private static final ResourceLocation SILKY = id("silky");
    private static final ResourceLocation LUCK = id("luck");
    private static final ResourceLocation SQUEAKY = id("squeaky");
    private static final ResourceLocation AUTOSMELT = id("autosmelt");

    /**
     * Symmetric, because upstream always checks both directions ({@code canApplyTogether(other) &&
     * other.canApplyTogether(this)}). {@code ModSilktouch}: not squeaky, not luck. {@code TraitSqueaky}:
     * not silktouch, not luck. {@code TraitAutosmelt}: not squeaky, not silktouch. Two traits can only
     * meet through embossing, and {@code ModExtraTrait#canApplyTogether} runs the same two-way check
     * per donor trait -- so autosmelt/squeaky is a pair even though only autosmelt's override names it.
     */
    private static final Map<ResourceLocation, Set<ResourceLocation>> INCOMPATIBLE = symmetric(List.of(
            List.of(SILKY, LUCK),
            List.of(SILKY, SQUEAKY),
            List.of(SILKY, AUTOSMELT),
            List.of(LUCK, SQUEAKY),
            List.of(AUTOSMELT, SQUEAKY)));

    /** Each overrider's {@code canApplyTogether(Enchantment)}. */
    private static final Map<ResourceLocation, Set<ResourceKey<Enchantment>>> EXCLUDED_ENCHANTMENTS = Map.of(
            SILKY, Set.of(Enchantments.LOOTING, Enchantments.FORTUNE),
            LUCK, Set.of(Enchantments.SILK_TOUCH),
            SQUEAKY, Set.of(Enchantments.LOOTING, Enchantments.FORTUNE),
            AUTOSMELT, Set.of(Enchantments.SILK_TOUCH));

    /**
     * Why {@code incoming} (a modifier id, or one embossing donor trait id) can't join {@code tool},
     * or empty when it can. Upstream's order and messages: traits first
     * ({@code gui.error.incompatible_trait}), then modifiers ({@code incompatible_modifiers}), then
     * every enchantment on the stack against every trait, modifier and the incoming id
     * ({@code incompatible_enchantments} -- upstream's {@code canApplyWithEnchantment} runs for the
     * existing ones too, not just the newcomer).
     *
     * @param incomingName what to call the newcomer in the message -- {@code ModifierApplication#name}
     *     for a modifier, the embossment's per-material name for a donor trait
     */
    public static Optional<Component> refusal(ItemStack tool, ResourceLocation incoming, Component incomingName) {
        List<ResourceLocation> traits = tool.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of());
        for (ResourceLocation trait : traits) {
            if (incompatible(incoming, trait)) {
                return Optional.of(Component.translatable("gui.forgeweave.modifier.incompatible_trait",
                        incomingName, traitName(trait)));
            }
        }
        List<ResourceLocation> modifiers = new ArrayList<>();
        for (ModifierEntry entry : ForgeweaveModifiers.of(tool)) {
            modifiers.add(entry.id());
            if (incompatible(incoming, entry.id())) {
                return Optional.of(Component.translatable("gui.forgeweave.modifier.incompatible_modifiers",
                        incomingName, ModifierApplication.name(entry.id())));
            }
        }
        ItemEnchantments enchantments = tool.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (enchantments.isEmpty()) {
            return Optional.empty();
        }
        List<ResourceLocation> all = new ArrayList<>(traits);
        all.addAll(modifiers);
        all.add(incoming);
        for (ResourceLocation id : all) {
            for (Holder<Enchantment> enchantment : enchantments.keySet()) {
                if (EXCLUDED_ENCHANTMENTS.getOrDefault(id, Set.of()).stream().anyMatch(enchantment::is)) {
                    Component name = id.equals(incoming) ? incomingName
                            : traits.contains(id) ? traitName(id) : ModifierApplication.name(id);
                    return Optional.of(Component.translatable("gui.forgeweave.modifier.incompatible_enchantment",
                            name, enchantment.value().description()));
                }
            }
        }
        return Optional.empty();
    }

    /** A trait's display key, {@code trait.<namespace>.<path>.name} -- {@code ToolTooltip#traitLine}'s. */
    private static Component traitName(ResourceLocation trait) {
        return Component.translatable("trait." + trait.getNamespace() + "." + trait.getPath() + ".name");
    }

    private static boolean incompatible(ResourceLocation a, ResourceLocation b) {
        return INCOMPATIBLE.getOrDefault(a, Set.of()).contains(b);
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> symmetric(List<List<ResourceLocation>> pairs) {
        Map<ResourceLocation, Set<ResourceLocation>> map = new HashMap<>();
        for (List<ResourceLocation> pair : pairs) {
            map.computeIfAbsent(pair.get(0), k -> new HashSet<>()).add(pair.get(1));
            map.computeIfAbsent(pair.get(1), k -> new HashSet<>()).add(pair.get(0));
        }
        return Map.copyOf(map);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ModifierCompatibility() {}
}
