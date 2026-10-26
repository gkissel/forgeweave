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
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;

/**
 * Upstream 1.12's modifier/trait/enchantment refusal layer (parity audit T23, issue #454):
 * {@code library/modifiers/Modifier#canApply} walks the tool's traits, then its modifiers, then its
 * enchantments, and throws a {@code TinkerGuiException} the moment either side's
 * {@code canApplyTogether} says no. The clone has exactly five overriders -- {@code ModSilktouch},
 * {@code ModLuck}, {@code TraitSqueaky}, {@code TraitAutosmelt} and {@code ModBlasting} (added by
 * parity audit T24) -- so the whole rule set is the two tables below rather than a per-modifier hook:
 * every check is by id, which is all a stored {@link ModifierEntry} or trait id carries anyway.
 *
 * <p>{@code ModExtraTrait#canApplyTogether} refuses an embossment whenever any donor trait would be
 * refused, so {@code Embossing} runs {@link #refusal} once per donor trait with the embossment's name.
 */
public final class ModifierCompatibility {

    private static final Identifier SILKY = id("silky");
    private static final Identifier LUCK = id("luck");
    private static final Identifier SQUEAKY = id("squeaky");
    private static final Identifier AUTOSMELT = id("autosmelt");
    private static final Identifier BLASTING = id("blasting");

    /**
     * Symmetric, because upstream always checks both directions ({@code canApplyTogether(other) &&
     * other.canApplyTogether(this)}). {@code ModSilktouch}: not squeaky, not luck. {@code TraitSqueaky}:
     * not silktouch, not luck. {@code TraitAutosmelt}: not squeaky, not silktouch. Two traits can only
     * meet through embossing, and {@code ModExtraTrait#canApplyTogether} runs the same two-way check
     * per donor trait -- so autosmelt/squeaky is a pair even though only autosmelt's override names it.
     */
    private static final Map<Identifier, Set<Identifier>> INCOMPATIBLE = symmetric(List.of(
            List.of(SILKY, LUCK),
            List.of(SILKY, SQUEAKY),
            List.of(SILKY, AUTOSMELT),
            List.of(LUCK, SQUEAKY),
            List.of(AUTOSMELT, SQUEAKY),
            // T24: ModBlasting#canApplyTogether(IToolMod) -- not luck, not silktouch, not squeaky,
            // not autosmelt. Every drop it would multiply or transform, blasting may destroy.
            List.of(BLASTING, LUCK),
            List.of(BLASTING, SILKY),
            List.of(BLASTING, SQUEAKY),
            List.of(BLASTING, AUTOSMELT)));

    /** Each overrider's {@code canApplyTogether(Enchantment)}. */
    private static final Map<Identifier, Set<ResourceKey<Enchantment>>> EXCLUDED_ENCHANTMENTS = Map.of(
            SILKY, Set.of(Enchantments.LOOTING, Enchantments.FORTUNE),
            LUCK, Set.of(Enchantments.SILK_TOUCH),
            SQUEAKY, Set.of(Enchantments.LOOTING, Enchantments.FORTUNE),
            AUTOSMELT, Set.of(Enchantments.SILK_TOUCH),
            BLASTING, Set.of(Enchantments.SILK_TOUCH, Enchantments.LOOTING, Enchantments.FORTUNE));

    /**
     * The enchantments {@code id} refuses to sit beside -- its own {@code canApplyTogether(Enchantment)}.
     * Exposed so a unit test can pin the table without a live enchantment registry (1.21 makes them a
     * datapack registry); {@link #refusal} is the only production reader.
     */
    public static Set<ResourceKey<Enchantment>> excludedEnchantments(Identifier id) {
        return EXCLUDED_ENCHANTMENTS.getOrDefault(id, Set.of());
    }

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
    public static Optional<Component> refusal(ItemStack tool, Identifier incoming, Component incomingName) {
        List<Identifier> traits = tool.getOrDefault(ForgeweaveDataComponents.TRAITS.get(), List.of());
        for (Identifier trait : traits) {
            if (incompatible(incoming, trait)) {
                return Optional.of(Component.translatable("gui.forgeweave.modifier.incompatible_trait",
                        incomingName, traitName(trait)));
            }
        }
        List<Identifier> modifiers = new ArrayList<>();
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
        List<Identifier> all = new ArrayList<>(traits);
        all.addAll(modifiers);
        all.add(incoming);
        for (Identifier id : all) {
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
    private static Component traitName(Identifier trait) {
        return Component.translatable("trait." + trait.getNamespace() + "." + trait.getPath() + ".name");
    }

    private static boolean incompatible(Identifier a, Identifier b) {
        return INCOMPATIBLE.getOrDefault(a, Set.of()).contains(b);
    }

    private static Map<Identifier, Set<Identifier>> symmetric(List<List<Identifier>> pairs) {
        Map<Identifier, Set<Identifier>> map = new HashMap<>();
        for (List<Identifier> pair : pairs) {
            map.computeIfAbsent(pair.get(0), k -> new HashSet<>()).add(pair.get(1));
            map.computeIfAbsent(pair.get(1), k -> new HashSet<>()).add(pair.get(0));
        }
        return Map.copyOf(map);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private ModifierCompatibility() {}
}
