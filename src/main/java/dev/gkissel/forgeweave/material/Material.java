package dev.gkissel.forgeweave.material;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Stream;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.PartItem;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;

/**
 * A material a {@code Part} can be made from, defined entirely in datapack JSON under
 * {@code data/<namespace>/forgeweave/material/<name>.json} (ADR-0002).
 *
 * <p>Registered as a NeoForge datapack registry with a network codec, so the server loads it on
 * {@code /reload} and syncs it to connecting clients for free. Look entries up through
 * {@code registryAccess().registryOrThrow(Material.REGISTRY)} on either side.
 *
 * <h2>Existence-gating a cross-mod material (issue #826, M6)</h2>
 *
 * <p>Every datapack-registry element here decodes through NeoForge's own {@code ConditionalOps}
 * (verified against the {@code neoforge-21.1.248-userdev.jar} patch of {@code
 * RegistryDataLoader}), so a top-level {@code "neoforge:conditions": [...]} array on a material
 * JSON makes that material -- and only that material -- not exist at all when the array's
 * condition fails: not registered, not synced, invisible to every consumer that iterates {@link
 * #REGISTRY} ({@code ForgeweaveCreativeTab}, {@code ForgeweaveJeiPlugin}, {@code BookContent},
 * {@code PartBuilderRecipes}) with zero code changes at any of those call sites. This is strictly
 * stronger than gating only {@link #craftingItems}/{@link #repairItem} on a {@code c:} tag (the
 * pre-#826 shape bronze/lead/silver/electrum shipped with) -- a tag gate leaves the material
 * registered but uncraftable, so it still shows up as a ghost entry in creative/JEI/the book
 * (docs/research/m6-material-expansion-references.md &sect;1.3's cautionary tale).
 *
 * <p><b>Which condition primitive.</b> Registry-element conditions evaluate with {@code
 * ICondition.IContext.TAGS_INVALID}: tags are not loaded yet at this point in startup, so any
 * tag-based condition (e.g. {@code neoforge:tag_empty}) <em>throws</em>, not just fails. The usable
 * vocabulary is {@code neoforge:mod_loaded} (a modid), {@code neoforge:item_exists} (a concrete
 * item id -- the item registry is frozen by now) and the {@code and}/{@code or}/{@code not}/{@code
 * true}/{@code false} combinators. One provider mod: a single {@code item_exists} (or {@code
 * mod_loaded}) condition. Several providers of the same metal: an {@code or} of one {@code
 * item_exists} per provider's concrete ingot id -- never a tag, and never one preset per modid
 * (JC2, docs/research/m6-material-expansion-references.md).
 *
 * <p><b>Keep the obtainability gate too.</b> {@link #craftingItems}/{@link #repairItem} stay
 * gated on the same {@code c:} tags they always were -- that is still how the Part Builder accepts
 * another mod's ingot once the existence condition has already let the material register. The two
 * gates answer different questions: existence ("does this metal's mod exist") and obtainability
 * ("does the Part Builder currently have an item to accept").
 *
 * <p><b>Companion registry entries.</b> {@code melting_recipe}, {@code casting_recipe}, {@code
 * alloy_recipe}, {@code entity_melting_recipe}, {@code smeltery_fuel}, {@code modifier_recipe} and
 * {@code embossing_recipe} decode through the same conditional loader and support the same field.
 * Any entry referencing a conditional material's id or its fluid must carry a matching {@code
 * neoforge:conditions} block, or it dangles against something that may not exist. The four M3.2
 * compat metals need none (deliberately: no Forgeweave fluid or casting recipe, Part Builder path
 * only -- JC3), but a metal with a full smeltery chain will.
 */
public record Material(
        Optional<Head> head,
        Optional<Handle> handle,
        Optional<Integer> extraDurability,
        TagKey<Block> incorrectForTool,
        Traits traits,
        List<CraftingItem> craftingItems,
        Ingredient repairItem,
        TextColor color,
        Optional<Bow> bow,
        Optional<Bowstring> bowstring,
        boolean castOnly,
        int enchantability,
        Optional<Shaft> shaft,
        Optional<Fletching> fletching,
        Optional<Plating> plating,
        boolean maille) {

    public static final ResourceKey<Registry<Material>> REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "material"));

    /**
     * What a material with no {@code enchantability} of its own is worth (issue #593): vanilla
     * iron's 14, which is exactly the flat value {@code ToolItem#getEnchantmentValue} returned for
     * every tool before this field existed. A pack written against the older shape therefore keeps
     * enchanting the way it did, and the field stays omitted from that pack's sync payload.
     */
    public static final int DEFAULT_ENCHANTABILITY = 14;

    /**
     * The default: a material the Part Builder takes, which is every material Forgeweave shipped
     * before issue #435 added {@link #castOnly}.
     */
    public Material(Optional<Head> head, Optional<Handle> handle, Optional<Integer> extraDurability,
            TagKey<Block> incorrectForTool, Traits traits, List<CraftingItem> craftingItems, Ingredient repairItem,
            TextColor color, Optional<Bow> bow, Optional<Bowstring> bowstring) {
        this(head, handle, extraDurability, incorrectForTool, traits, craftingItems, repairItem, color, bow,
                bowstring, false, DEFAULT_ENCHANTABILITY);
    }

    /**
     * Everything except {@link #enchantability}, which issue #593 added last and which every
     * material Forgeweave shipped before it left at {@link #DEFAULT_ENCHANTABILITY}.
     */
    public Material(Optional<Head> head, Optional<Handle> handle, Optional<Integer> extraDurability,
            TagKey<Block> incorrectForTool, Traits traits, List<CraftingItem> craftingItems, Ingredient repairItem,
            TextColor color, Optional<Bow> bow, Optional<Bowstring> bowstring, boolean castOnly) {
        this(head, handle, extraDurability, incorrectForTool, traits, craftingItems, repairItem, color, bow,
                bowstring, castOnly, DEFAULT_ENCHANTABILITY);
    }

    /**
     * Everything except the SHAFT/FLETCHING blocks, which issue #626 (parity audit T17) added last
     * and which every material Forgeweave shipped before it does not carry.
     */
    public Material(Optional<Head> head, Optional<Handle> handle, Optional<Integer> extraDurability,
            TagKey<Block> incorrectForTool, Traits traits, List<CraftingItem> craftingItems, Ingredient repairItem,
            TextColor color, Optional<Bow> bow, Optional<Bowstring> bowstring, boolean castOnly,
            int enchantability) {
        this(head, handle, extraDurability, incorrectForTool, traits, craftingItems, repairItem, color, bow,
                bowstring, castOnly, enchantability, Optional.empty(), Optional.empty());
    }

    /**
     * Everything except the PLATING block and the MAILLE marker, which issue #676 (M4-1) added last
     * and which every material Forgeweave shipped before it does not carry.
     */
    public Material(Optional<Head> head, Optional<Handle> handle, Optional<Integer> extraDurability,
            TagKey<Block> incorrectForTool, Traits traits, List<CraftingItem> craftingItems, Ingredient repairItem,
            TextColor color, Optional<Bow> bow, Optional<Bowstring> bowstring, boolean castOnly,
            int enchantability, Optional<Shaft> shaft, Optional<Fletching> fletching) {
        this(head, handle, extraDurability, incorrectForTool, traits, craftingItems, repairItem, color, bow,
                bowstring, castOnly, enchantability, shaft, fletching, Optional.empty(), false);
    }

    /**
     * The common case: a material with all three tool stat blocks and no ranged ones, which is every
     * material Forgeweave shipped before issue #392 added the bow blocks.
     */
    public Material(Head head, Handle handle, int extraDurability, TagKey<Block> incorrectForTool,
            Traits traits, List<CraftingItem> craftingItems, Ingredient repairItem, TextColor color) {
        this(Optional.of(head), Optional.of(handle), Optional.of(extraDurability), incorrectForTool, traits,
                craftingItems, repairItem, color, Optional.empty(), Optional.empty());
    }

    /** Stats a head part contributes: the tool's durability pool, mining speed and attack damage. */
    public record Head(int durability, float miningSpeed, float attackDamage) {
        public static final Codec<Head> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("durability").forGetter(Head::durability),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("mining_speed").forGetter(Head::miningSpeed),
                // Non-negative rather than positive: upstream sponge's head is exactly 0.00 attack
                // (TinkerMaterials#registerToolMaterialStats), the stat squeaky then hard-zeroes anyway.
                Codec.floatRange(0.0F, Float.MAX_VALUE).fieldOf("attack_damage").forGetter(Head::attackDamage))
                .apply(instance, Head::new));
    }

    /**
     * Stats a handle part contributes: a multiplier on the head's durability pool plus a flat
     * bonus, which is negative for materials that trade durability for other stats.
     */
    public record Handle(float durabilityModifier, int durability) {
        public static final Codec<Handle> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("durability_modifier").forGetter(Handle::durabilityModifier),
                Codec.INT.fieldOf("durability").forGetter(Handle::durability))
                .apply(instance, Handle::new));
    }

    /**
     * Stats a bow limb contributes ({@code library/materials/BowMaterialStats.java}, pinned commit
     * in NOTICE.md).
     *
     * <p>{@code drawspeed} is upstream's raw field, where higher draws <em>faster</em> -- wood is
     * 1.0, paper 1.5, steel 0.4. Every display of it is inverted ({@code getLocalizedInfo} formats
     * {@code 1f/drawspeed}), which is why the panel shows steel as 2.5 and paper as 0.67; the
     * inversion lives in {@code StationText#bowStats}, not here, so the datapack value stays the one
     * the draw math multiplies by.
     *
     * <p>{@code bonusDamage} is flat extra arrow damage and is <b>signed</b>: upstream pays it out
     * for materials that are slow but springy (steel 9, iron 7) and charges it against materials
     * that have no business being a bow (paper -2, stone -1). Upstream's own note: "think of the
     * bonus damage as a flat damage-reward for using materials that are slower, but flexible, like
     * metals" -- it deliberately does not scale with range.
     */
    public record Bow(float drawspeed, float range, float bonusDamage) {
        public static final Codec<Bow> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("drawspeed").forGetter(Bow::drawspeed),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("range").forGetter(Bow::range),
                Codec.FLOAT.fieldOf("bonus_damage").forGetter(Bow::bonusDamage))
                .apply(instance, Bow::new));
    }

    /**
     * Stats a bow string contributes ({@code library/materials/BowStringMaterialStats.java}): one
     * multiplier, around 1.0. Upstream gives every one of its four bowstring materials exactly 1.0
     * and leaves the field as the hook a pack (or a later material) can differ on.
     */
    public record Bowstring(float modifier) {
        public static final Codec<Bowstring> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("modifier").forGetter(Bowstring::modifier))
                .apply(instance, Bowstring::new));
    }

    /**
     * Stats an arrow shaft contributes ({@code library/materials/ArrowShaftMaterialStats.java},
     * issue #626 / parity audit T17): a multiplier on the arrow's ammo count plus a flat bonus.
     * Upstream's constructor order is {@code (modifier, bonusAmmo)} and its
     * {@code getLocalizedInfo} shows them in that order too.
     */
    public record Shaft(float modifier, int bonusAmmo) {
        public static final Codec<Shaft> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("modifier").forGetter(Shaft::modifier),
                ExtraCodecs.NON_NEGATIVE_INT.fieldOf("bonus_ammo").forGetter(Shaft::bonusAmmo))
                .apply(instance, Shaft::new));
    }

    /**
     * Stats a fletching contributes ({@code library/materials/FletchingMaterialStats.java}, issue
     * #626): flight accuracy (a fraction, displayed as a whole percent -- upstream's
     * {@code formatNumberPercent}) and another ammo multiplier. Upstream's constructor order is
     * {@code (accuracy, modifier)} while its {@code getLocalizedInfo} leads with the modifier; the
     * record keeps the constructor order, the display lives in {@code StationText#fletchingStats}.
     */
    public record Fletching(float accuracy, float modifier) {
        public static final Codec<Fletching> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("accuracy").forGetter(Fletching::accuracy),
                ExtraCodecs.POSITIVE_FLOAT.fieldOf("modifier").forGetter(Fletching::modifier))
                .apply(instance, Fletching::new));
    }

    /**
     * Stats one armor plating part contributes (issue #676, M4-1; SCOPE.md D10/D14), the 1.20 clone's
     * {@code PlatingMaterialStats} -- {@code library/materials/stats/PlatingMaterialStats.java} and
     * the generated {@code tinkering/materials/stats/<m>.json} rows, pinned commit in NOTICE.md.
     * Same four fields and units as vanilla's {@code ArmorMaterial}: {@code armor} is armor points,
     * {@code toughness} and {@code knockback_resistance} are the attribute values, both 0 for most
     * materials, so they default to 0 and stay out of the sync payload when absent.
     */
    public record PlatingPiece(int durability, float armor, float toughness, float knockbackResistance) {
        public static final Codec<PlatingPiece> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.POSITIVE_INT.fieldOf("durability").forGetter(PlatingPiece::durability),
                Codec.floatRange(0.0F, Float.MAX_VALUE).fieldOf("armor").forGetter(PlatingPiece::armor),
                Codec.floatRange(0.0F, Float.MAX_VALUE).optionalFieldOf("toughness", 0.0F)
                        .forGetter(PlatingPiece::toughness),
                Codec.floatRange(0.0F, 1.0F).optionalFieldOf("knockback_resistance", 0.0F)
                        .forGetter(PlatingPiece::knockbackResistance))
                .apply(instance, PlatingPiece::new));
    }

    /**
     * The four per-piece plating rows. Upstream registers one stat type per piece
     * ({@code tconstruct:plating_helmet} ... {@code plating_boots}) and every plating material
     * carries all four, so one block with four required rows is the same data without four optional
     * fields to keep in step; {@link #hasStatsFor} treats the block as one answer for
     * {@link PartItem.Kind#PLATING}.
     */
    public record Plating(PlatingPiece helmet, PlatingPiece chestplate, PlatingPiece leggings, PlatingPiece boots) {
        public static final Codec<Plating> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PlatingPiece.CODEC.fieldOf("helmet").forGetter(Plating::helmet),
                PlatingPiece.CODEC.fieldOf("chestplate").forGetter(Plating::chestplate),
                PlatingPiece.CODEC.fieldOf("leggings").forGetter(Plating::leggings),
                PlatingPiece.CODEC.fieldOf("boots").forGetter(Plating::boots))
                .apply(instance, Plating::new));
    }

    /**
     * A raw item usable as Part Builder crafting input, and how much of a part's cost one of it
     * pays off (upstream 1.12's `Material#addItem`/`addItemIngot`, {@code TinkerMaterials}).
     * {@code value} is expressed in upstream's own {@code Material.VALUE_*} unit ({@code VALUE_Ingot = 144},
     * nugget 16, fragment 36, shard 72) -- see {@code PartBuilderRecipes}'s class javadoc (T58, issue #489).
     */
    public record CraftingItem(Ingredient ingredient, int value) {
        public static final Codec<CraftingItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Ingredient.CODEC.fieldOf("ingredient").forGetter(CraftingItem::ingredient),
                ExtraCodecs.POSITIVE_INT.fieldOf("value").forGetter(CraftingItem::value))
                .apply(instance, CraftingItem::new));
    }

    /**
     * Which traits this material grants, scoped by the kind of part granting them -- upstream 1.12's
     * {@code Material#addTrait(trait, HEAD)} / {@code addTrait(trait)} split, keyed by the same
     * part-stat block {@link PartItem.Kind} names ({@code library/materials/Material.java}, pinned
     * commit in NOTICE.md).
     *
     * <p>{@code general} applies to every part; a part-scoped list <b>replaces</b> it for that part
     * rather than adding to it, exactly as upstream's {@code getAllTraitsForStats} falls back to the
     * default list only when the stat has no list of its own. That is why upstream re-states a
     * general trait under {@code HEAD} when it wants both (prismarine's {@code aquadynamic}), and why
     * a stronger head variant simply replaces the general one (iron: {@code magnetic2} on the head,
     * {@code magnetic} everywhere else).
     *
     * <p>Only {@code head}, {@code shaft} (issue #626: bone re-scopes {@code splitting} to its
     * arrow shafts, {@code TinkerMaterials:272}) and {@code projectile} (issue #653) exist as
     * scopes so far, because those are the only ones Forgeweave's materials use; ponytail: the
     * remaining {@link PartItem.Kind}s -- including issue #392's {@code BOW}/{@code BOWSTRING} and
     * #626's {@code FLETCHING}, which fall back to {@code general} exactly as upstream's
     * {@code getAllTraitsForStats} does -- get a field when a material needs one, and
     * {@link #forPart} is the single place that has to learn about it. Upstream's one
     * PROJECTILE-scoped trait is endstone's {@code enderference}
     * ({@code endstone.addTrait(enderference, PROJECTILE)}, {@code TinkerMaterials:264}), read
     * through the arrow head's two-scope {@code PartMaterialType(HEAD, PROJECTILE)} -- endstone's
     * head list ({@code alien}) would otherwise occlude {@code enderference} on arrow heads.
     */
    public record Traits(List<ResourceLocation> general, List<ResourceLocation> head,
            List<ResourceLocation> shaft, List<ResourceLocation> projectile, List<ResourceLocation> armor) {

        public static final Codec<Traits> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("general", List.of()).forGetter(Traits::general),
                ResourceLocation.CODEC.listOf().optionalFieldOf("head", List.of()).forGetter(Traits::head),
                ResourceLocation.CODEC.listOf().optionalFieldOf("shaft", List.of()).forGetter(Traits::shaft),
                ResourceLocation.CODEC.listOf().optionalFieldOf("projectile", List.of())
                        .forGetter(Traits::projectile),
                ResourceLocation.CODEC.listOf().optionalFieldOf("armor", List.of()).forGetter(Traits::armor))
                .apply(instance, Traits::new));

        /**
         * The pre-#676 shape: no armor-scoped list. {@code armor} (issue #676, SCOPE.md D17) is the
         * 1.20 clone's {@code tconstruct:armor} per-stat scope, read by both PLATING and MAILLE parts
         * -- one list, not one per kind, because upstream keys it by the shared ARMOR stat type.
         */
        public Traits(List<ResourceLocation> general, List<ResourceLocation> head,
                List<ResourceLocation> shaft, List<ResourceLocation> projectile) {
            this(general, head, shaft, projectile, List.of());
        }

        /** The pre-#653 shape: no projectile-scoped list, which is every material but endstone. */
        public Traits(List<ResourceLocation> general, List<ResourceLocation> head,
                List<ResourceLocation> shaft) {
            this(general, head, shaft, List.of());
        }

        /** The pre-#626 shape: no shaft-scoped list, which is every material but bone. */
        public Traits(List<ResourceLocation> general, List<ResourceLocation> head) {
            this(general, head, List.of());
        }

        /** The common case: traits every part of the material grants. */
        public static Traits general(ResourceLocation... ids) {
            return new Traits(List.of(ids), List.of());
        }

        /** The trait ids a part of this {@code kind} grants (see the class javadoc on scoping). */
        public List<ResourceLocation> forPart(PartItem.Kind kind) {
            if (kind == PartItem.Kind.HEAD && !head.isEmpty()) {
                return head;
            }
            if (kind == PartItem.Kind.SHAFT && !shaft.isEmpty()) {
                return shaft;
            }
            if (kind == PartItem.Kind.PROJECTILE && !projectile.isEmpty()) {
                return projectile;
            }
            if ((kind == PartItem.Kind.PLATING || kind == PartItem.Kind.MAILLE) && !armor.isEmpty()) {
                return armor;
            }
            return general;
        }

        /** Every trait id this material can grant through any part, de-duplicated. */
        public List<ResourceLocation> all() {
            return Stream.of(general, head, shaft, projectile, armor).flatMap(List::stream).distinct().toList();
        }
    }

    /**
     * Accepts both the pre-#94 {@code "trait": "<id>"} (one trait, every part) and the current
     * {@code "traits": {...}} object, and always writes the latter -- datapack materials are a public
     * surface (ADR-0002), so a pack written against the old shape keeps loading. A material naming
     * both fields is read as the old shape; naming neither still fails, as it always has.
     *
     * <p>The {@code Traits} lists are {@code optionalFieldOf} with an empty default, which DFU omits
     * on encode, so the registry-sync payload of a material with no head-scoped traits is exactly the
     * one list it actually has (docs/SCOPE.md's material sync-packet budget).
     */
    private static final MapCodec<Traits> TRAITS_CODEC = Codec.mapEither(
            ResourceLocation.CODEC.fieldOf("trait"),
            Traits.CODEC.fieldOf("traits"))
            .xmap(either -> either.map(Traits::general, Function.identity()), Either::right);

    /**
     * Whether this material's parts come out of the Smeltery only, leaving {@link #craftingItems}
     * inert at the Part Builder (issue #435, parity audit T3). Upstream 1.12 spends two booleans on
     * this -- {@code craftable} (Part Builder) and {@code castable} (a cast plus the material's
     * fluid) -- and reads them as {@code isCraftable() = craftable || (Config.craftCastableMaterials
     * && castable)}, with that config defaulting to {@code false}
     * ({@code library/materials/Material.java:173-190}, {@code common/config/Config.java:38,178-180}).
     *
     * <p>Only the combination {@code castable && !craftable} changes any behavior, so that is the
     * one bit stored here: {@code cast_only} is exactly "upstream would refuse this at the Part
     * Builder unless the config says otherwise", and {@link
     * dev.gkissel.forgeweave.menu.PartBuilderRecipes#craftableInPartBuilder} is the single place
     * that asks. Upstream's own {@code castable} needs no field because Forgeweave states castability
     * as casting recipes rather than a flag, and nothing reads it as a boolean.
     *
     * <p>Which materials carry it is upstream's roster, not a rule about metals:
     * {@code MaterialIntegration:100-108} makes any material handed a fluid castable-not-craftable,
     * while obsidian and knightslime set both flags by hand ({@code TinkerMaterials:236-237,299})
     * and stay craftable. The crafting items stay listed either way -- they are what
     * {@code craftCastableMaterials} turns back on, and what repair and JEI keep reading.
     */
    public boolean castOnly() {
        return castOnly;
    }

    /**
     * How well a tool made of this material takes vanilla enchantments -- the number
     * {@code ToolItem#getEnchantmentValue} averages across the tool's parts while
     * {@code allowVanillaEnchanting} is on (issue #593; the flag is off by default, so this is inert
     * in a default game). Same unit as vanilla's {@code Tier#getEnchantmentValue}: wood 15, stone 5,
     * iron 14, diamond 10, gold 22, netherite 15, and higher enchants better.
     *
     * <p>There is no upstream field to derive this from, in either generation. 1.12 never gives a
     * tool an enchantability at all ({@code library/tinkering/TinkersItem} leaves
     * {@code getItemEnchantability} at vanilla's 0 and overrides {@code isBookEnchantable} to
     * {@code false}), and 1.20 is stricter still -- {@code library/tools/item/ModifiableItem#isEnchantable}
     * is a flat {@code false}, {@code TinkerTier#getEnchantmentValue} and
     * {@code ModifiableLauncherItem#getEnchantmentValue} both return 0, and no
     * {@code slimeknights.tconstruct.tools.stats} material stat carries an enchantment value. So the
     * field, its placement (top level rather than inside a stat block -- a bowstring or handle
     * material has no head block to hang it on, and the aggregation reads every part) and every
     * shipped value are Forgeweave-only, seeded by analogy with vanilla's tiers and flagged for
     * maintainer balancing.
     *
     * <p>Top level also means the value is a property of the substance rather than of one part
     * shape, which is what lets {@code cobalt} answer the question identically whether it turned up
     * as a head, a handle or a bow limb.
     */
    public int enchantability() {
        return enchantability;
    }

    /**
     * Every stat block is optional (issue #392). Upstream has always worked this way -- a material
     * carries whichever {@code IMaterialStats} it was registered with and {@code
     * ToolPart#hasUseForStat} decides what can be made of it -- and Forgeweave needs it the moment a
     * bowstring material exists: {@code string} and {@code vine} carry no head, handle or binding
     * stats at all, and inventing some for them would put a string pickaxe head in the Part Builder.
     * {@link #hasStatsFor} is the single place that answers "does this material have that block".
     */
    public static final Codec<Material> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Head.CODEC.optionalFieldOf("head").forGetter(Material::head),
            Handle.CODEC.optionalFieldOf("handle").forGetter(Material::handle),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("extra_durability").forGetter(Material::extraDurability),
            // Vanilla tool tier, expressed as the block tag the tool cannot mine (CONTEXT.md: no numeric harvest levels).
            TagKey.codec(Registries.BLOCK).fieldOf("incorrect_for_tool").forGetter(Material::incorrectForTool),
            // Trait behavior is Java (ADR-0002); data only names which traits this material grants.
            TRAITS_CODEC.forGetter(Material::traits),
            // Part Builder crafting inputs and their values (issue #45; upstream units since #489); repair_item below
            // is a separate, single-ingredient concept used only for Tool Station repair.
            CraftingItem.CODEC.listOf().fieldOf("crafting_items").forGetter(Material::craftingItems),
            Ingredient.CODEC.fieldOf("repair_item").forGetter(Material::repairItem),
            TextColor.CODEC.fieldOf("color").forGetter(Material::color),
            Bow.CODEC.optionalFieldOf("bow").forGetter(Material::bow),
            Bowstring.CODEC.optionalFieldOf("bowstring").forGetter(Material::bowstring),
            Codec.BOOL.optionalFieldOf("cast_only", false).forGetter(Material::castOnly),
            ExtraCodecs.POSITIVE_INT.optionalFieldOf("enchantability", DEFAULT_ENCHANTABILITY)
                    .forGetter(Material::enchantability),
            Shaft.CODEC.optionalFieldOf("shaft").forGetter(Material::shaft),
            Fletching.CODEC.optionalFieldOf("fletching").forGetter(Material::fletching),
            Plating.CODEC.optionalFieldOf("plating").forGetter(Material::plating),
            Codec.BOOL.optionalFieldOf("maille", false).forGetter(Material::maille))
            .apply(instance, Material::new));

    /**
     * Whether this material carries the stat block a part of {@code kind} draws from -- upstream's
     * {@code ToolPart#hasUseForStat}. A Part Builder pattern, a creative-tab part variant and a
     * station slot all ask this same question, so it gets asked here once.
     *
     * <p>{@link PartItem.Kind#NONE} (the shard) draws from no block at all and so is always
     * satisfied.
     */
    public boolean hasStatsFor(PartItem.Kind kind) {
        return switch (kind) {
            case HEAD -> head.isPresent();
            case HANDLE -> handle.isPresent();
            case EXTRA -> extraDurability.isPresent();
            case BOW -> bow.isPresent();
            case BOWSTRING -> bowstring.isPresent();
            case SHAFT -> shaft.isPresent();
            case FLETCHING -> fletching.isPresent();
            // Upstream auto-adds the dummy PROJECTILE stat to every material given HEAD stats
            // (TinkerRegistry#addMaterialStats:260-262), so "has HEAD" is the whole answer.
            case PROJECTILE -> head.isPresent();
            case PLATING -> plating.isPresent();
            case MAILLE -> maille;
            case NONE -> true;
        };
    }
}
