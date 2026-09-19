package dev.gkissel.forgeweave.compat.apotheosis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.api.combat.CombatDefense;
import dev.gkissel.forgeweave.api.combat.CombatHit;
import dev.gkissel.forgeweave.api.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.CombatSeams;
import dev.gkissel.forgeweave.api.combat.DefendedBlow;
import dev.gkissel.forgeweave.config.ForgeweaveConfig; // #968
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.api.modifier.Modifier;
import dev.gkissel.forgeweave.modifier.ModifierApplication.Outcome;
import dev.gkissel.forgeweave.modifier.ModifierEntry;

/**
 * The Apotheosis-free half of Forgeweave's gem-socket compat (issue #969, docs/SCOPE.md M8, D-M8-1):
 * the {@code socketed} modifier, the socket contents on the stack, the station action that seats a
 * gem, and the seam the rest of the mod queries without naming a {@code dev.shadowsoffire} type.
 *
 * <p>Same split {@code compat.draconic.modules.DraconicModules} already uses, and for the same
 * reason. This class is classloaded on every install, Apotheosis or not -- {@link ForgeweaveModifiers}
 * holds {@link #SOCKETED} in its registry and the tooltips walk {@link #gems} unconditionally -- so it
 * names nothing from that mod. {@link ApotheosisGemBonuses} does, and is reached only through
 * {@link #installBridge()}, which {@code Forgeweave} calls from inside a
 * {@code ModList.get().isLoaded(MODID)} guard. With no Apotheosis present nothing installs a bridge
 * and every bonus query below answers zero.
 *
 * <h2>What a socket is</h2>
 *
 * <p>Sockets are the {@code socketed} modifier's <em>level</em>: one socket per level, one modifier
 * slot per socket, competing for the same pool as every other modifier -- including a slot a tool
 * earned by levelling up under M7, which is the specified interaction rather than an accident. The
 * gems themselves live in {@link ForgeweaveDataComponents#SOCKETS}, indexed by socket, and a gem
 * beyond the current socket count is never read. Absent means no sockets, so nothing built before M8
 * needs migrating.
 *
 * <h2>The gem-effect mapping</h2>
 *
 * <p>A gem's bonus reaches the tool through the seams Forgeweave's own traits and modifiers already
 * use, never through a second stat path, and where a gem grants something Forgeweave has no seam for
 * it grants nothing rather than getting a seam invented for it (D-M8-1). {@link #EFFECT_MAP} is that
 * decision written down, one row per bonus type Apotheosis 8.7.0 ships;
 * {@code ApotheosisSocketTest} keeps it total.
 */
public final class ApotheosisSockets {

    /** Apotheosis' mod id -- the {@code ModList} guard and every recipe condition key on it. */
    public static final String MODID = "apotheosis";

    /** The modifier whose level is the socket count. */
    public static final ResourceLocation SOCKETED_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "socketed");

    /**
     * The marker id {@code modifier_recipe/socket_gem.json} names, the way
     * {@code modifier.OverslimeRefill#ID} marks the overslime refill: nothing is ever stored under it
     * on the tool. Seating a gem moves {@link ForgeweaveDataComponents#SOCKETS} and nothing else.
     */
    public static final ResourceLocation SEAT_GEM_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "socket_gem");

    /**
     * The level cap, a hard number rather than "unbounded": three sockets, matching the three
     * modifier slots {@code ForgeweaveModifiers#DEFAULT_SLOTS} gives every assembled tool. Filling
     * all three on a tool that has never levelled therefore spends its whole starting budget, which
     * is the trade the modifier is meant to be. The shipped {@code modifier_recipe/socketed.json}
     * carries the same number as its {@code max_level}; {@code ApotheosisSocketTest} pins the pair
     * together so the two can never drift.
     */
    public static final int MAX_SOCKETS = 3;

    /**
     * One socket per level, so the default per-level slot charge is exactly right and
     * {@link Modifier#occupiedSlots} is left alone. No stat hooks either: a gem's bonus depends on
     * which gem is seated, which is stack state a level-only hook cannot see -- it rides the
     * bonus queries below instead, off the same {@link ItemStack} the Draconic module effects read.
     */
    public static final Modifier SOCKETED = new Modifier() {};

    // ---------------------------------------------------------------- socket contents

    /** How many sockets {@code stack} has, which is {@code socketed}'s level, 0 for a tool with none. */
    public static int socketCount(ItemStack stack) {
        ModifierEntry entry = ForgeweaveModifiers.entry(stack, SOCKETED_ID);
        return entry == null ? 0 : Math.min(MAX_SOCKETS, entry.level());
    }

    /**
     * What sits in each socket, one entry per socket in socket order, {@link ItemStack#EMPTY} for an
     * empty one. Always {@link #socketCount} long, so a component left over from a state where the
     * tool had more sockets can never hand out a gem the tool no longer holds.
     */
    public static List<ItemStack> gems(ItemStack stack) {
        int count = socketCount(stack);
        if (count <= 0) {
            return List.of();
        }
        List<ItemStack> stored = stack.getOrDefault(ForgeweaveDataComponents.SOCKETS.get(),
                ItemContainerContents.EMPTY).stream().toList();
        List<ItemStack> gems = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            gems.add(i < stored.size() ? stored.get(i) : ItemStack.EMPTY);
        }
        return List.copyOf(gems);
    }

    /** Whether {@code stack} has a socket with nothing in it. */
    public static boolean hasEmptySocket(ItemStack stack) {
        return gems(stack).stream().anyMatch(ItemStack::isEmpty);
    }

    /**
     * {@code tool} with one {@code gem} seated in its first empty socket, or the reason there isn't
     * one -- the station outcome {@code ModifierApplication#resolveOne} hands straight back, the same
     * shape {@code OverslimeRefill#apply} has.
     *
     * <p>The gem is stored as a single item however many sit in the slot, and exactly one is spent.
     * A gem Apotheosis itself would refuse on this item -- its {@code Gem#canApplyTo}, which is what
     * keeps a bow gem out of a pickaxe -- is refused here rather than seated inert.
     *
     * @param slots the station's free slots, in station order
     */
    public static Outcome seat(ItemStack tool, List<ItemStack> slots) {
        if (socketCount(tool) <= 0) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.no_sockets"));
        }
        List<ItemStack> gems = gems(tool);
        int socket = 0;
        while (socket < gems.size() && !gems.get(socket).isEmpty()) {
            socket++; // first empty socket wins, so seating fills left to right
        }
        if (socket >= gems.size()) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.sockets_full"));
        }
        int slot = 0;
        while (slot < slots.size() && slots.get(slot).isEmpty()) {
            slot++;
        }
        if (slot >= slots.size()) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.not_enough_reagents", 1));
        }
        if (!canSeat(tool, slots.get(slot))) {
            return Outcome.rejected(Component.translatable("gui.forgeweave.modifier.gem_refused"));
        }

        List<ItemStack> seated = new ArrayList<>(gems);
        seated.set(socket, slots.get(slot).copyWithCount(1));
        ItemStack result = tool.copy();
        result.set(ForgeweaveDataComponents.SOCKETS.get(), ItemContainerContents.fromItems(seated));
        List<Integer> spent = new ArrayList<>(slots.size());
        for (int i = 0; i < slots.size(); i++) {
            spent.add(i == slot ? 1 : 0);
        }
        return Outcome.applied(result, List.copyOf(spent));
    }

    /**
     * The tooltip and station-panel lines for a socketed stack: one row per socket, naming the gem in
     * it or saying the socket is empty. The gem's own name already carries its purity
     * (Apotheosis' {@code GemItem#getName}), and its bonus text is the gem item's own hover text, so
     * nothing here has to reach into that mod to describe what a seated gem does.
     */
    public static List<Component> socketLines(ItemStack stack) {
        List<ItemStack> gems = gems(stack);
        List<Component> lines = new ArrayList<>(gems.size());
        for (int i = 0; i < gems.size(); i++) {
            ItemStack gem = gems.get(i);
            Component contents = gem.isEmpty()
                    ? Component.translatable("tooltip.forgeweave.socket.empty")
                    : gem.getHoverName();
            lines.add(Component.translatable("tooltip.forgeweave.socket", i + 1, contents)
                    .withStyle(ChatFormatting.GRAY));
        }
        return List.copyOf(lines);
    }

    // ---------------------------------------------------------------- the config toggle

    /**
     * Whether the integration is on, i.e. {@code compat.apotheosisSockets} (D-M8-5, wired by issue
     * #968).
     *
     * <p>Every gate reads this: {@link #installBridge}, so an off toggle installs no bridge and every
     * bonus query below answers zero, and {@code ModifierApplication}'s two recipe branches, so an
     * off toggle refuses both the socket and the gem. An already-socketed stack keeps its
     * {@link ForgeweaveDataComponents#SOCKETS} component untouched either way -- D-M7-3's rule
     * applied to compat, and what {@code ApotheosisSocketGameTests} pins.
     */
    public static boolean enabled() {
        return ForgeweaveConfig.enabled(ForgeweaveConfig.APOTHEOSIS_SOCKETS);
    }

    // ---------------------------------------------------------------- the bridge

    /**
     * One attribute modifier a seated gem grants, as {@link #EFFECT_MAP} reads it: the attribute's
     * registry id plus the modifier itself, so the Apotheosis-free side can route it by id without
     * ever holding an Apotheosis or Apothic Attributes type.
     */
    public record AttributeGrant(ResourceLocation attribute, AttributeModifier modifier) {}

    /** What {@link ApotheosisGemBonuses} answers once Apotheosis is installed. */
    public interface Bridge {

        /** Whether Apotheosis would let {@code gem} sit in {@code target} at all. */
        boolean canSeat(ItemStack target, ItemStack gem);

        /** Every attribute modifier {@code stack}'s seated gems grant, in socket order. */
        List<AttributeGrant> attributeGrants(ItemStack stack);

        /** The summed durability bonus fraction, e.g. {@code 0.1} for +10%. */
        float durabilityFraction(ItemStack stack);

        /** The summed protection points against {@code source}, in vanilla Protection-level units. */
        float protection(ItemStack stack, DamageSource source);

        /** {@code amount} after every gem has had its say, chained as Apotheosis chains it. */
        float reduceDamage(ItemStack stack, DamageSource source, LivingEntity defender, float amount);

        /** The gems' post-attack effects, for a blow this stack just landed. */
        void afterAttack(ItemStack stack, LivingEntity attacker, @Nullable Entity target);

        /** The gems' post-hurt effects, for a blow the wearer of this stack just took. */
        void afterHurt(ItemStack stack, LivingEntity defender, DamageSource source);
    }

    @Nullable
    private static volatile Bridge bridge;

    /**
     * Called once, from inside the {@code ModList} guard in {@code Forgeweave}'s constructor. The
     * reference to {@link ApotheosisGemBonuses} is inside a method body rather than a field, so a
     * Forgeweave-only install -- which classloads this class but never runs this method -- never
     * links a {@code dev.shadowsoffire} type at all. Same trick, same reason, as
     * {@code ForgeweaveDraconicCompat#register}.
     */
    public static void installBridge() {
        if (enabled()) {
            install(new ApotheosisGemBonuses());
        }
    }

    /**
     * {@code null} puts the state back where an install without Apotheosis has it, which is what a
     * test that installed a fake bridge restores.
     */
    public static void install(@Nullable Bridge installed) {
        bridge = installed;
    }

    /** Whether a bridge is installed at all, i.e. whether this install can read gems. */
    public static boolean active() {
        return bridge != null;
    }

    /** Whether Apotheosis would seat {@code gem} in {@code target}; false with no Apotheosis. */
    public static boolean canSeat(ItemStack target, ItemStack gem) {
        Bridge installed = bridge;
        return installed != null && installed.canSeat(target, gem);
    }

    // ---------------------------------------------------------------- the mapped quantities

    /**
     * Flat attack damage the seated gems add on top of the tool's own ({@code ToolItem#attackDamage}).
     * Added after {@code ToolItem#cutoffDamage}, the same call the Draconic damage module's bonus
     * makes and for the same reason: that curve bounds Forgeweave's <em>own</em> modifier stacking
     * (issue #295), and running a gem's points through it would quietly eat most of them.
     *
     * <p>Multiplicative operations are applied to the gem total rather than to the tool's damage,
     * since the number they are a fraction of is Apotheosis' own attribute value, not Forgeweave's
     * stat -- see {@link #sum}.
     */
    public static float attackDamageBonus(ItemStack stack) {
        return sum(stack, MAPPED_ATTACK_DAMAGE);
    }

    /**
     * Mining speed the seated gems add to what the tool's stats and traits already worked out
     * ({@code ToolItem#getDestroySpeed}). Additive, because that is what both mapped attributes are:
     * Apothic Attributes' own {@code mining_speed} and vanilla's {@code player.mining_efficiency}
     * both add to a dig speed rather than scaling it.
     */
    public static float miningSpeedBonus(ItemStack stack) {
        return sum(stack, MAPPED_MINING_SPEED);
    }

    /** Armour points the seated gems add to a worn piece's own ({@code ArmorPieceItem}). */
    public static float armorBonus(ItemStack stack) {
        return sum(stack, MAPPED_ARMOR);
    }

    /** Armour toughness the seated gems add, alongside {@code ForgeweaveModifiers#armorToughnessBonus}. */
    public static float armorToughnessBonus(ItemStack stack) {
        return sum(stack, MAPPED_ARMOR_TOUGHNESS);
    }

    /** Knockback resistance the seated gems add, alongside {@code ForgeweaveModifiers#knockbackResistanceBonus}. */
    public static float knockbackResistanceBonus(ItemStack stack) {
        return sum(stack, MAPPED_KNOCKBACK_RESISTANCE);
    }

    /**
     * The extra durability the seated gems grant, as a fraction of {@code baseDurability} -- rounded
     * down, so a gem too weak to buy a whole point buys none. Folded into the same pool
     * {@code ForgeweaveModifiers#modifiedDurability} folds every modifier's bonus into.
     */
    public static int durabilityBonus(ItemStack stack, int baseDurability) {
        Bridge installed = bridge;
        if (installed == null || socketCount(stack) <= 0) {
            return 0;
        }
        return (int) (baseDurability * installed.durabilityFraction(stack));
    }

    /**
     * Sums the mapped grants for one Forgeweave quantity. {@code ADD_VALUE} adds outright;
     * {@code ADD_MULTIPLIED_BASE} and {@code ADD_MULTIPLIED_TOTAL} are read as fractions of the
     * running gem total, which is the only base this class has -- a recorded approximation, since
     * Apotheosis applies them against its own attribute value and Forgeweave has no such value to
     * scale. Every gem Apotheosis 8.7.0 ships uses {@code ADD_VALUE} for the five mapped attributes,
     * so nothing in the shipped set takes that path.
     */
    private static float sum(ItemStack stack, ResourceLocation... attributes) {
        Bridge installed = bridge;
        if (installed == null || socketCount(stack) <= 0) {
            return 0.0F;
        }
        double total = 0.0;
        for (AttributeGrant grant : installed.attributeGrants(stack)) {
            boolean mapped = false;
            for (ResourceLocation attribute : attributes) {
                mapped |= attribute.equals(grant.attribute());
            }
            if (!mapped) {
                continue;
            }
            total = switch (grant.modifier().operation()) {
                case ADD_VALUE -> total + grant.modifier().amount();
                case ADD_MULTIPLIED_BASE, ADD_MULTIPLIED_TOTAL -> total * (1.0 + grant.modifier().amount());
            };
        }
        return (float) total;
    }

    // ---------------------------------------------------------------- the combat seams

    /**
     * Everything a seated gem does to a blow, on the hooks Forgeweave already owns:
     *
     * <ul>
     *   <li><b>Protection</b> ({@code GemBonus#getDamageProtection}) is added to the blow's own
     *       protection total, in the same units {@code combat.Protection} uses -- so a gem's
     *       protection stacks additively with a trait's, a modifier's and vanilla's own Protection
     *       enchantments, and shares their 80% cap.
     *   <li><b>Damage reduction</b> ({@code GemBonus#onHurt}, which is how the damage-reduction and
     *       mageslayer gems work) shaves the blow itself, and only ever downward: a gem that somehow
     *       answered with more damage than it was handed is ignored rather than trusted to raise a
     *       blow against the wearer.
     *   <li><b>Post-attack and post-hurt effects</b> ({@code doPostAttack}/{@code doPostHurt}, the
     *       mob-effect gems' two main targets) fire on the hit and defend passes respectively.
     * </ul>
     */
    private static final CombatSeam GEM_SEAM = new CombatSeam() {

        @Override
        public void onHit(CombatHit hit, float damageDealt) {
            Bridge installed = bridge;
            if (installed != null && hit.attacker() != null) {
                installed.afterAttack(hit.weapon(), hit.attacker(), hit.target());
            }
        }

        @Override
        public void onDefend(CombatDefense defense, DefendedBlow blow) {
            Bridge installed = bridge;
            if (installed == null) {
                return;
            }
            blow.addProtection(installed.protection(defense.tool(), defense.source()));
            float reduced = installed.reduceDamage(defense.tool(), defense.source(), defense.defender(),
                    blow.damage());
            if (reduced < blow.damage()) {
                blow.setDamage(reduced);
            }
            installed.afterHurt(defense.tool(), defense.defender(), defense.source());
        }
    };

    /**
     * The gems as a consumer of the shared per-hit pipeline (ADR-0005 decision 3), registered once in
     * {@code Forgeweave} next to the trait and modifier providers. One provider for the whole socket
     * list, same reasoning as those two: a stack with no sockets costs one component read and stops.
     */
    public static final CombatSeams.Provider COMBAT_SEAMS = (weapon, out) -> {
        if (bridge != null && socketCount(weapon) > 0) {
            out.accept(GEM_SEAM);
        }
    };

    // ---------------------------------------------------------------- the mapping table

    /** Attribute ids, as {@link #EFFECT_MAP}'s rows name them and {@link #sum} routes them. */
    private static final ResourceLocation MAPPED_ATTACK_DAMAGE =
            ResourceLocation.withDefaultNamespace("generic.attack_damage");
    /** Apothic Attributes' own mining speed attribute, which is what Apotheosis' tool gems grant. */
    private static final ResourceLocation MAPPED_APOTHIC_MINING_SPEED =
            ResourceLocation.fromNamespaceAndPath("apothic_attributes", "mining_speed");
    /** Vanilla 1.21's own dig-speed attribute, mapped alongside it so a datapack gem using it lands too. */
    private static final ResourceLocation MAPPED_MINING_EFFICIENCY =
            ResourceLocation.withDefaultNamespace("player.mining_efficiency");
    private static final ResourceLocation MAPPED_ARMOR =
            ResourceLocation.withDefaultNamespace("generic.armor");
    private static final ResourceLocation MAPPED_ARMOR_TOUGHNESS =
            ResourceLocation.withDefaultNamespace("generic.armor_toughness");
    private static final ResourceLocation MAPPED_KNOCKBACK_RESISTANCE =
            ResourceLocation.withDefaultNamespace("generic.knockback_resistance");

    private static final ResourceLocation[] MAPPED_MINING_SPEED =
            {MAPPED_APOTHIC_MINING_SPEED, MAPPED_MINING_EFFICIENCY};

    /**
     * The Forgeweave quantity a mapped gem effect lands on. Every name here is a real seam this class
     * feeds; {@code ApotheosisSocketTest} asserts each one is reached by at least one row, so a
     * quantity cannot be named in the table and then quietly go unwired.
     */
    public enum Quantity {
        /** {@code ToolItem#attackDamage}, after the cutoff curve. */
        ATTACK_DAMAGE,
        /** {@code ToolItem#getDestroySpeed}. */
        MINING_SPEED,
        /** {@code ArmorPieceItem}'s armour, toughness and knockback resistance. */
        ARMOR,
        /** {@code ForgeweaveModifiers#modifiedDurability}'s pool. */
        DURABILITY,
        /** {@code DefendedBlow#addProtection}, shared with traits, modifiers and vanilla Protection. */
        PROTECTION,
        /** {@code DefendedBlow#setDamage} on the defend pass. */
        INCOMING_DAMAGE,
        /** {@code CombatSeam#onHit} and {@code #onDefend}'s side effects. */
        HIT_EFFECTS
    }

    /**
     * One Apotheosis gem-bonus type and what it lands on here. {@code quantities} is empty for a
     * deliberately unmapped effect, whose {@code note} says why -- D-M8-1's rule that an effect
     * Forgeweave has no seam for grants nothing rather than getting a seam invented for it.
     *
     * @param bonus the bonus type's Apotheosis registry id
     * @param hook the {@code GemBonus} method it works through
     * @param quantities the Forgeweave quantities it reaches, empty for unmapped
     * @param note why, in one line
     */
    public record GemEffect(String bonus, String hook, List<Quantity> quantities, String note) {

        /** Whether this effect reaches Forgeweave at all. */
        public boolean mapped() {
            return !quantities.isEmpty();
        }
    }

    /**
     * Every gem-bonus type Apotheosis 8.7.0 ships, and where each one lands. Read the class javadoc
     * first; this is the written mapping D-M8-1 asks for, and it is the table
     * {@code ApotheosisSocketTest} keeps total.
     *
     * <p>The three attribute-shaped bonuses share one row apiece because they share one hook: every
     * attribute modifier they grant is harvested once ({@link Bridge#attributeGrants}) and routed by
     * attribute id. Five attributes are mapped -- attack damage, Apothic Attributes' mining speed,
     * vanilla's mining efficiency, armour, armour toughness and knockback resistance -- and every
     * other attribute a gem can name is unmapped, which covers most of Apothic Attributes' own
     * roster (crit chance, armour pierce, life steal, cold damage, arrow velocity and the rest).
     * Forgeweave expresses none of those, and inventing six attributes to carry them is exactly what
     * D-M8-1 refuses.
     */
    private static final List<Quantity> ATTRIBUTE_QUANTITIES =
            List.of(Quantity.ATTACK_DAMAGE, Quantity.MINING_SPEED, Quantity.ARMOR);

    public static final List<GemEffect> EFFECT_MAP = List.of(
            new GemEffect("apotheosis:attribute", "addModifiers", ATTRIBUTE_QUANTITIES,
                    "one attribute modifier per purity, routed by attribute id: attack damage, "
                            + "Apothic Attributes' mining speed, vanilla's mining efficiency, armour, "
                            + "armour toughness and knockback resistance all land, and every other "
                            + "attribute is unmapped"),
            new GemEffect("apotheosis:multi_attribute", "addModifiers", ATTRIBUTE_QUANTITIES,
                    "several modifiers at once, harvested and routed exactly as the single one is"),
            new GemEffect("apotheosis:all_stats", "addModifiers", ATTRIBUTE_QUANTITIES,
                    "one modifier per attribute in a set, harvested and routed the same way"),
            new GemEffect("apotheosis:durability", "getDurabilityBonusPercentage",
                    List.of(Quantity.DURABILITY),
                    "a percentage of the base durability, added to the same pool every modifier's "
                            + "durability bonus goes into"),
            new GemEffect("apotheosis:damage_reduction", "onHurt", List.of(Quantity.INCOMING_DAMAGE),
                    "scales an incoming blow of one damage type, on the defend pass"),
            new GemEffect("apotheosis:mageslayer", "onHurt", List.of(Quantity.INCOMING_DAMAGE),
                    "the same hook, against magic damage"),
            new GemEffect("apotheosis:mob_effect", "doPostAttack / doPostHurt",
                    List.of(Quantity.HIT_EFFECTS),
                    "applies its effect after a blow landed or taken. Its two other targets, a block "
                            + "break and a projectile impact, are unmapped: Forgeweave's own break and "
                            + "arrow paths carry no gem hook"),
            new GemEffect("apotheosis:*", "getDamageProtection", List.of(Quantity.PROTECTION),
                    "no bonus type Apotheosis 8.7.0 ships overrides this base hook, but it is wired "
                            + "anyway, so a datapack or addon bonus that does lands on the same "
                            + "protection total traits, modifiers and vanilla Protection share"),
            new GemEffect("apotheosis:enchantment", "getEnchantmentLevels", List.of(),
                    "Forgeweave has no enchantment-level seam. Its own Fortune and Looting grants are "
                            + "baked onto the stack at application time, not resolved per query"),
            new GemEffect("apotheosis:drop_transform", "modifyLoot", List.of(),
                    "needs a loot context. Forgeweave's drop seam is NeoForge's block-drops event, "
                            + "which has no loot context to hand over"),
            new GemEffect("apotheosis:frozen_drops", "modifyLoot", List.of(),
                    "the same hook, so the same reason"),
            new GemEffect("apotheosis:leech_block", "onShieldBlock", List.of(),
                    "Forgeweave's blocking seam reduces the blow; it has no hook for healing the "
                            + "blocker off it"),
            new GemEffect("apotheosis:bloody_arrow", "onProjectileFired", List.of(),
                    "Forgeweave's bow builds and fires its own arrow entity, with no gem hook on the "
                            + "shot"),
            new GemEffect("apotheosis:omnetic", "harvest / break-speed events", List.of(),
                    "static handlers on NeoForge events rather than a GemBonus method a socket list "
                            + "can call"),
            new GemEffect("apotheosis:radial", "radial data", List.of(),
                    "Forgeweave's large tools mine a fixed box, not a radial pattern, so there is "
                            + "nothing for a radius to configure"));

    /** {@link #EFFECT_MAP} keyed by bonus id, for the tests and for a quick lookup. */
    public static final Map<String, GemEffect> EFFECTS_BY_BONUS = EFFECT_MAP.stream()
            .collect(Collectors.toUnmodifiableMap(GemEffect::bonus, effect -> effect));

    private ApotheosisSockets() {}
}
