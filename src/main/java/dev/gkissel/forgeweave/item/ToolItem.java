package dev.gkissel.forgeweave.item;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.combat.ToolUseAction;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.entity.IndestructibleItemEntity;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;

import dev.gkissel.forgeweave.tool.AoeHarvest;
import dev.gkissel.forgeweave.tool.AxeStrip;
import dev.gkissel.forgeweave.tool.CropHarvest;
import dev.gkissel.forgeweave.tool.EntityShear;
import dev.gkissel.forgeweave.tool.ShovelPath;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * An assembled tool (pickaxe, shovel, hatchet -- CONTEXT.md glossary). Everything specific to one
 * assembled tool lives in data components set by {@code ToolAssemblyRecipes}: the materials it was
 * built from, its {@link ToolStats.Stats}, vanilla's {@code max_damage}/{@code damage}, and vanilla's
 * {@code tool} component. This class supplies the per-tool-type constants those components can't
 * carry (which blocks the tool is meant for, its attack speed and damage potential) and the
 * behavior around the Broken state.
 *
 * <h2>Broken, never destroyed</h2>
 *
 * <p>CONTEXT.md's hard invariant. {@code ItemStack#hurtAndBreak} is the single choke point for every
 * durability loss in the game (mining, attacking, anvils, third-party code), and it destroys a stack
 * only when {@code damage >= maxDamage} -- but it first routes the amount through NeoForge's
 * {@link #damageItem}. So {@link #damageItem} clamps the damage so it can never reach
 * {@code maxDamage}, which makes destruction unreachable from any caller rather than from the two
 * or three we thought to override. At the clamp the tool gets the
 * {@link ForgeweaveDataComponents#BROKEN} flag, and every behavior below reads that flag: mining
 * falls back to a flat 0.3 penalty speed and drops nothing, attack modifiers disappear, the
 * durability bar hides, and the tooltip says so. Upstream 1.12 does the same thing with an NBT flag
 * plus a {@code ToolCore} that never lets vanilla see a fully-damaged stack
 * ({@code library/utils/ToolHelper.java} {@code #damageTool}/{@code #breakTool}/{@code #isBroken}).
 *
 * <p>A Broken tool therefore rests at {@code damage == maxDamage - 1}; without the
 * {@link #isBarVisible} override the durability bar would still round that to a thin sliver rather
 * than disappearing. Repair (Tool Station, {@code ToolAssemblyRecipes}) is the only thing that
 * clears the flag.
 */
public class ToolItem extends Item {
    /** Id for the trait-driven attack speed modifier {@link #getDefaultAttributeModifiers} adds (issue #102). */
    private static final ResourceLocation TRAIT_ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "trait_attack_speed");
    /** Id for the trait-driven movement speed modifier (issue #230: vintage's mobility cost). */
    private static final ResourceLocation TRAIT_MOVEMENT_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "trait_movement_speed");
    /** Id for the trait-driven knockback resistance heavy grants (issue #229), same idiom. */
    private static final ResourceLocation TRAIT_KNOCKBACK_RESISTANCE_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "trait_knockback_resistance");
    // #108 batch: modern-vanilla attribute modifiers (Aquadynamic, Far Reach -- ForgeweaveModifiers),
    // same idiom as vanilla Item's own BASE_ATTACK_DAMAGE_ID/BASE_ATTACK_SPEED_ID this class already
    // keys its two attribute modifiers on above.
    private static final ResourceLocation SUBMERGED_MINING_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "aquadynamic");
    private static final ResourceLocation BLOCK_INTERACTION_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "far_reach");

    private final List<TagKey<Block>> mineableBlocks;
    private final float attackSpeed;
    private final float damagePotential;
    private final float miningSpeedModifier;
    private final float damageCutoff;
    private final boolean weapon;
    @Nullable
    private final ForgeweaveInnates.Innate innate;
    private final AoeHarvest.Shape aoeShape;
    /** See {@link #abilitiesFor}: this tool's kind, as 1.21 spells upstream's Forge tool class. */
    private final Set<ItemAbility> abilities;

    /** A tool with no innate of its own -- M1's three, until issue #164 retrofits theirs. */
    public ToolItem(Properties properties, TagKey<Block> mineableBlocks, float attackSpeed, float damagePotential,
            boolean weapon) {
        this(properties, mineableBlocks, attackSpeed, damagePotential, 1.0F, weapon, null);
    }

    /**
     * The M3 shape (issue #155): every per-tool-type number comes off the tool's own
     * {@link ToolConstants.Entry}, so the constants the station's stat formula uses and the ones the
     * item's attribute modifiers use are literally the same fields. {@code preAttackMultiplier},
     * {@code flatAttackBonus} and {@code durabilityMultiplier} are not read here -- those apply once,
     * at assembly, inside {@link ToolConstants#compute}.
     */
    public ToolItem(Properties properties, ToolConstants.Entry constants, TagKey<Block> mineableBlocks,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        this(properties, constants, List.of(mineableBlocks), weapon, innate);
    }

    /**
     * As above, for a tool effective on more than one {@code mineable/*} family: the M3 mattock is
     * upstream's axe+shovel dual tool (issue #156, {@code tools/tools/Mattock.java}) and names both.
     * {@link #toolComponent} adds a rule per tag, so the block's own tag decides which one applies.
     */
    public ToolItem(Properties properties, ToolConstants.Entry constants, List<TagKey<Block>> mineableBlocks,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        this(properties, constants, mineableBlocks, weapon, innate, AoeHarvest.Shape.NONE);
    }

    /**
     * As above, for a tool that takes extra blocks along with the one it breaks (issue #157). Every
     * other per-tool constant still comes off the {@link ToolConstants.Entry}; {@code aoeShape} is the
     * one thing that is not a number and so has nowhere to live on that record.
     *
     * @param aoeShape which extra blocks a break with this tool takes along; see {@link AoeHarvest}
     */
    public ToolItem(Properties properties, ToolConstants.Entry constants, TagKey<Block> mineableBlocks,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate, AoeHarvest.Shape aoeShape) {
        this(properties, constants, List.of(mineableBlocks), weapon, innate, aoeShape);
    }

    public ToolItem(Properties properties, ToolConstants.Entry constants, List<TagKey<Block>> mineableBlocks,
            boolean weapon, @Nullable ForgeweaveInnates.Innate innate, AoeHarvest.Shape aoeShape) {
        this(properties, mineableBlocks, constants.attackSpeed(), constants.damagePotential(),
                constants.miningSpeedModifier(), constants.damageCutoff(), weapon, innate, aoeShape);
    }

    /**
     * @param mineableBlocks the vanilla {@code mineable/*} tag this tool type is for
     * @param attackSpeed attacks per second, upstream 1.12's {@code ToolCore#attackSpeed()}
     * @param damagePotential multiplier on the head material's attack damage, upstream 1.12's
     *     {@code ToolCore#damagePotential()}
     * @param weapon whether upstream gives this tool {@code Category.WEAPON} (the hatchet and the
     *     M3 kama do), which halves what a hit costs it -- see {@link #postHurtEnemy}
     * @param innate this tool type's built-in combat behavior (docs/SCOPE.md M3), or {@code null}.
     *     Its {@link CombatSeam} half is picked up by the shared per-hit pipeline through
     *     {@code ForgeweaveInnates#collect}; its {@link ToolUseAction} half is what the four
     *     item-use overrides below forward to, so no tool needs a subclass of its own.
     */
    public ToolItem(Properties properties, TagKey<Block> mineableBlocks, float attackSpeed, float damagePotential,
            float miningSpeedModifier, boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        this(properties, List.of(mineableBlocks), attackSpeed, damagePotential, miningSpeedModifier, weapon, innate,
                AoeHarvest.Shape.NONE);
    }

    /** See above; the multi-tag form every other constructor funnels into. */
    public ToolItem(Properties properties, List<TagKey<Block>> mineableBlocks, float attackSpeed,
            float damagePotential, float miningSpeedModifier, boolean weapon,
            @Nullable ForgeweaveInnates.Innate innate, AoeHarvest.Shape aoeShape) {
        this(properties, mineableBlocks, attackSpeed, damagePotential, miningSpeedModifier,
                ToolConstants.DEFAULT_DAMAGE_CUTOFF, weapon, innate, aoeShape);
    }

    /**
     * As above, with an explicit per-tool damage cutoff (issue #295) -- the M3 {@link ToolConstants.Entry}
     * constructor below is the only caller that ever passes anything but the default, for the four
     * upstream tool classes that override {@code ToolCore#damageCutoff()}.
     */
    public ToolItem(Properties properties, List<TagKey<Block>> mineableBlocks, float attackSpeed,
            float damagePotential, float miningSpeedModifier, float damageCutoff, boolean weapon,
            @Nullable ForgeweaveInnates.Innate innate, AoeHarvest.Shape aoeShape) {
        super(properties);
        this.mineableBlocks = List.copyOf(mineableBlocks);
        this.attackSpeed = attackSpeed;
        this.damagePotential = damagePotential;
        this.miningSpeedModifier = miningSpeedModifier;
        this.damageCutoff = damageCutoff;
        this.weapon = weapon;
        this.innate = innate;
        this.aoeShape = aoeShape;
        this.abilities = abilitiesFor(this.mineableBlocks);
    }

    /**
     * The {@link ItemAbility} set this tool answers {@link #canPerformAction} for, derived from the
     * {@code mineable/*} tags it is registered with (parity audit 2026-08-18 T33, issue #464).
     *
     * <p>Upstream 1.12 declares the same thing with Forge's {@code setHarvestLevel("<tool class>", 0)}
     * -- {@code Pickaxe} "pickaxe", {@code Shovel} "shovel", {@code Hatchet}/{@code LumberAxe}/
     * {@code BattleAxe} "axe", {@code Mattock} both (its {@code getHarvestLevel} override) -- and
     * every one of those tool classes is already the tag the tool is registered with here, so the
     * mapping is a lookup rather than a second per-tool table to keep in sync. A tool registered with
     * no tag at all (the frying pan, the battlesign, the warmace -- upstream gives them no tool class
     * either) answers nothing, which is also plain {@code IItemExtension#canPerformAction}'s default.
     *
     * <p>The rule for what a kind maps to is "claim what the tool actually does", which is also how
     * upstream itself spells it once the mechanic exists: 1.20's {@code ToolDefinitionDataProvider}
     * hands each tool a bare {@code ToolActionsModule.of(<kind>_DIG)} and adds every right-click
     * ability as a separate trait on the tools that have the behaviour -- {@code pathing} on the
     * excavator, {@code tilling} on the mattock and kama, {@code stripping} on its two axes. So:
     *
     * <ul>
     *   <li>every kind contributes its {@code _DIG} ability and nothing else, except
     *   <li>{@code mineable/shovel}, which contributes the whole shovel set, because {@link
     *       ShovelPath} ports upstream 1.12 {@code Shovel#onItemUse}'s grass paths (issue #464) -- the
     *       same pairing 1.20's excavator has. The mattock holds that tag too and
     *       is upstream's one tool that digs as a shovel without pathing, so {@code MattockItem}
     *       states its own set rather than taking this one;
     *   <li>{@code mineable/axe}, which likewise contributes the whole axe set, because {@link
     *       AxeStrip} strips, scrapes and wipes wax off (issue #575) -- 1.20's {@code stripping}
     *       trait, which its {@code HAND_AXE} and {@code BROAD_AXE} both carry, and this file's one
     *       deliberate deviation from 1.12 (see {@code AxeStrip}'s javadoc for why). 1.20's mattock
     *       does <em>not</em> carry it, and {@code MattockItem}'s own set leaves it out for the same
     *       reason it leaves pathing out;
     *   <li>{@code sword_efficient} contributes {@code SWORD_DIG} alone and never {@link
     *       ItemAbilities#SWORD_SWEEP}: upstream's {@code SwordCore} extends {@code TinkerToolCore},
     *       not vanilla's {@code ItemSword}, so 1.12's automatic sweep never reached a Tinkers' sword
     *       and upstream ported the sweep it does want by hand ({@link
     *       dev.gkissel.forgeweave.combat.BroadswordSweep}); answering vanilla's own gate
     *       ({@code Player#attack}) as well would sweep twice per swing. 1.20 agrees -- not one of its
     *       tool definitions names {@code SWORD_SWEEP};
     *   <li>{@code mineable/hoe} contributes {@code HOE_DIG}, matching 1.20's kama exactly. Neither
     *       the tilling nor the shears set is claimed: 1.12's kama tills nothing, and 1.12's shears
     *       interactions are all {@code instanceof ItemShears} checks a kama never passed, so claiming
     *       them would add pumpkin carving, beehive harvesting and tripwire disarming upstream never
     *       had. The kama's own entity shearing is {@code KamaItem#interactLivingEntity}, which needs
     *       no ability.
     * </ul>
     *
     */
    private static Set<ItemAbility> abilitiesFor(List<TagKey<Block>> mineableBlocks) {
        Set<ItemAbility> abilities = new LinkedHashSet<>();
        for (TagKey<Block> tag : mineableBlocks) {
            if (tag == BlockTags.MINEABLE_WITH_PICKAXE) {
                abilities.add(ItemAbilities.PICKAXE_DIG);
            } else if (tag == BlockTags.MINEABLE_WITH_AXE) {
                abilities.addAll(ItemAbilities.DEFAULT_AXE_ACTIONS);
            } else if (tag == BlockTags.MINEABLE_WITH_SHOVEL) {
                abilities.addAll(ItemAbilities.DEFAULT_SHOVEL_ACTIONS);
            } else if (tag == BlockTags.MINEABLE_WITH_HOE) {
                abilities.add(ItemAbilities.HOE_DIG);
            } else if (tag == BlockTags.SWORD_EFFICIENT) {
                abilities.add(ItemAbilities.SWORD_DIG);
            }
        }
        return Set.copyOf(abilities);
    }

    /**
     * See {@link #abilitiesFor}. Broken is a flat refusal, the same one upstream's every
     * {@code onItemUse} opens with ({@code if(ToolHelper.isBroken(stack)) return FAIL}): a Broken tool
     * is a bundle of parts, and {@code BlockState#getToolModifiedState} consults exactly this method
     * before it will till, strip, scrape or flatten anything.
     */
    @Override
    public boolean canPerformAction(ItemStack stack, ItemAbility itemAbility) {
        return !isBroken(stack) && abilities.contains(itemAbility);
    }

    /** Which extra blocks a break with this tool takes along -- see {@link AoeHarvest#onBlockBreak}. */
    public AoeHarvest.Shape aoeShape() {
        return aoeShape;
    }

    /** This tool type's innate, or {@code null}. See the constructor. */
    @Nullable
    public ForgeweaveInnates.Innate innate() {
        return innate;
    }

    @Nullable
    private ToolUseAction useAction() {
        return innate == null ? null : innate.use();
    }

    public static boolean isBroken(ItemStack stack) {
        return stack.getOrDefault(ForgeweaveDataComponents.BROKEN.get(), false);
    }

    /**
     * Whether upstream gives this tool type {@code Category.WEAPON} -- see the constructor javadoc.
     * #106 batch: also gates luck's Looting grant ({@code ModifierApplication#applyEnchantmentGrants}),
     * the same split {@link #effectiveAttackSpeed} already uses for haste's attack-speed bonus.
     */
    public boolean isWeapon() {
        return weapon;
    }

    /**
     * {@code Stone Pickaxe}, {@code Stone-Iron Hammer} -- upstream
     * {@code ToolCore#getItemStackDisplayName} (ToolCore.java:379-393), issue #446.
     *
     * <p>The materials that name a tool are upstream's <em>repair parts</em>
     * ({@code TinkersItem#getRepairParts}, overridden per tool): index 1 for the ordinary
     * three-part tools, {@code {1, 2, 3}} for the hammer, {@code {0, 1}} for the shortbow. That set
     * is a tool's {@link ToolConstants.Role#HEAD} slots for all but two of the roster (a bow limb
     * being the {@link ToolConstants.Role#LIMB} that carries the HEAD stat block), so the role is
     * read off the tool's own part list here.
     *
     * <p>Since issue #462 landed the real table ({@code ToolConstants.Entry#repairSlots()}) the two
     * are visibly not the same question for the scythe, whose repair parts include its tough binding,
     * and the longbow, whose repair part is only its second limb. Naming deliberately keeps the role
     * reading: it is what {@code MaterialDisplay#prefixed} wants ("which materials is this tool made
     * of, visually") and what issue #446 shipped and pinned. Changing it is a separate decision, not
     * a side effect of the repair table existing.
     *
     * <p>A tool with no materials component (creative tab, JEI ghost) and one no assembly row
     * explains both fall back to as much as is known -- the plain item name, and the primary head
     * material respectively. A renamed tool keeps its name: {@code ItemStack#getHoverName} consults
     * {@code CUSTOM_NAME} and {@code ITEM_NAME} before ever reaching here.
     */
    @Override
    public Component getName(ItemStack stack) {
        return MaterialDisplay.prefixed(nameMaterials(stack), super.getName(stack));
    }

    /** The head-slot materials, in slot order -- see {@link #getName}. */
    private static List<ResourceLocation> nameMaterials(ItemStack stack) {
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null) {
            return List.of();
        }
        List<ResourceLocation> materialIds = materials.all();
        List<ToolConstants.PartSlot> slots = ToolAssemblyRecipes.entryFor(stack)
                .map(entry -> entry.constants().parts())
                .orElse(List.of());
        if (materialIds.size() < slots.size()) {
            return List.of(materials.head());
        }
        List<ResourceLocation> heads = new ArrayList<>(slots.size());
        for (int slot = 0; slot < slots.size(); slot++) {
            ToolConstants.Role role = slots.get(slot).role();
            if (role == ToolConstants.Role.HEAD || role == ToolConstants.Role.LIMB
                    || role == ToolConstants.Role.SHURIKEN_BLADE) {
                heads.add(materialIds.get(slot));
            }
        }
        return heads.isEmpty() ? List.of(materials.head()) : heads;
    }

    /**
     * CONTEXT.md invariant: not enchantable at the vanilla enchanting table unless
     * {@code allowVanillaEnchanting} is on. {@code ItemStack#isEnchantable()} (consulted by
     * {@code EnchantmentMenu#slotsChanged} to decide whether to offer enchantments at all) calls
     * straight through to this method, so gating it here rejects the item from the table outright
     * when off rather than merely offering zero applicable enchantments.
     */
    @Override
    public boolean isEnchantable(ItemStack stack) {
        return ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.get() && super.isEnchantable(stack);
    }

    /**
     * Parity audit T81 (issue #512): upstream's {@code TinkersItem#isBookEnchantable} refuses an
     * enchanted book at the anvil unconditionally ("prevents enchanted items to have a different name
     * color"). Forgeweave already gates the enchanting-table path above on {@code allowVanillaEnchanting}
     * rather than upstream's hard "always off", so the anvil path -- {@code AnvilMenu#createResult}
     * calls exactly this method -- follows the same flag instead of upstream's unconditional refusal:
     * with the flag off both paths agree with upstream, and with it on both agree with each other.
     */
    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.get() && super.isBookEnchantable(stack, book);
    }

    /**
     * The other two thirds of the same gate (parity audit T54, issue #485). {@link #isEnchantable}
     * alone only decides whether the enchanting table will look at the item at all; with a value of
     * 0 the table then looks and offers nothing, because {@code EnchantmentHelper#getEnchantmentCost}
     * and {@code #selectEnchantment} both short-circuit on {@code stack.getEnchantmentValue() <= 0}.
     * Vanilla's own {@code Item#getEnchantmentValue} is 0 and Forgeweave tools are not
     * {@code TieredItem}s, so nothing was ever lifting them off it -- the flag's ON side was dead.
     *
     * <p>There is no upstream number to derive: 1.12 has no switch at all
     * ({@code library/tinkering/TinkersItem} overrides {@code isBookEnchantable} to {@code false} and
     * never touches {@code getItemEnchantability}, so a tool sits at vanilla's 0), and 1.20 is
     * stricter still ({@code library/tools/item/ModifiableItem#isEnchantable} is a flat {@code false}
     * and enchantments reach a tool only by being converted into modifiers). The flag is a
     * Forgeweave-only extension, so this is a Forgeweave-only value.
     *
     * <p>Issue #593 (maintainer decision on PR #574's open question) replaced #485's flat iron 14
     * with the material's own: {@code Material#enchantability}, averaged across the tool's parts at
     * assembly and read back off the {@code forgeweave:enchantability} component, because this seam
     * gets an {@code ItemStack} and no registry access. A tool assembled before #593 carries no such
     * component and falls back to {@link Material#DEFAULT_ENCHANTABILITY} -- the same flat 14 it
     * already enchanted at, so no existing world changes.
     */
    @Override
    public int getEnchantmentValue(ItemStack stack) {
        if (!ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.get()) {
            return 0;
        }
        return stack.getOrDefault(ForgeweaveDataComponents.ENCHANTABILITY.get(), Material.DEFAULT_ENCHANTABILITY);
    }

    /**
     * The anvil's half of the same gate. Which enchantments a tool can take at all is data -- the
     * {@code minecraft:enchantable/*} item tags each vanilla enchantment names as its
     * {@code supported_items}, filled in for every Forgeweave tool by {@code ForgeweaveItemTagsProvider}
     * -- but a tag is static and the flag is not, so the config check has to live here. NeoForge routes
     * both application paths through this one method: the anvil asks {@code supportsEnchantment}
     * directly, and the table's {@code isPrimaryItemFor} calls it before consulting {@code primary_items}.
     *
     * <p>Off is the 1.12 behavior exactly: {@code TinkersItem#isBookEnchantable} returns {@code false}
     * for every book. That also closes a hole this ticket found -- the warmace joined
     * {@code #minecraft:enchantable/mace} for issue #223's wind burst modifier gate, which let an anvil
     * put Density/Breach/Wind Burst books on it whatever the flag said. The modifier gate itself is
     * untouched: {@code ModifierApplication} reads the enchantment's supported-items tag directly
     * rather than going through this method.
     */
    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        return ForgeweaveConfig.ALLOW_VANILLA_ENCHANTING.get() && super.supportsEnchantment(stack, enchantment);
    }

    /**
     * CONTEXT.md's "a tool is never destroyed" invariant, extended past the item to the entity that
     * carries it (issue #447, parity audit T16). Upstream 1.12 does exactly this for every
     * {@code ToolCore}, unconditionally, in {@code TinkersItem#hasCustomEntity}: the ItemStack a
     * player spent hours of materials and modifiers on must not be lost to a creeper, a lava flow or
     * a five-minute despawn timer while it lies on the ground. NeoForge calls this from its
     * {@code EntityJoinLevelEvent} handler, so it covers every drop path -- death, a manual toss, a
     * broken block, third-party code -- rather than the two or three we thought to override.
     *
     * <p>Deliberately not gated on a modifier or a material trait: 1.12 gates it on nothing, and
     * upstream 1.20's modifier gate is a later redesign, not the parity target.
     */
    @Override
    public boolean hasCustomEntity(ItemStack stack) {
        return true;
    }

    /** {@link #hasCustomEntity}'s other half -- see {@link IndestructibleItemEntity}. */
    @Override
    public Entity createEntity(Level level, Entity location, ItemStack stack) {
        IndestructibleItemEntity entity =
                new IndestructibleItemEntity(level, location.getX(), location.getY(), location.getZ(), stack);
        entity.copyThrowFrom(location);
        return entity;
    }

    /**
     * The vanilla {@code tool} component for a tool assembled from this head material: tier gating
     * comes from the material's {@code incorrect_for_tool} block tag (CONTEXT.md: vanilla tool-tier
     * tags, never numeric harvest levels), the speed bonus applies to this tool type's
     * {@code mineable/*} tag. Same rule order and shape as vanilla's
     * {@code Tier#createToolProperties}, so an incorrect-tier block is still dug at tool speed but
     * drops nothing.
     */
    public Tool toolComponent(Material head, ToolStats.Stats stats) {
        List<Tool.Rule> rules = new ArrayList<>();
        rules.add(Tool.Rule.deniesDrops(head.incorrectForTool()));
        rules.addAll(miningRules(stats));
        return new Tool(rules, 1.0F, 1);
    }

    /**
     * Every speed-bearing rule this tool type has, in the order {@link #toolComponent} writes them.
     * Split out of it (issue #598) because assembly is not the only thing that builds them:
     * {@code ModifierApplication#retuneStats} rebuilds them from the modified stats on every rebake
     * -- a modifier application, a Tool Station part exchange, {@code Fortification} -- and used to
     * do it by overwriting each rule with the raw stat, which silently dropped both this tool type's
     * {@link #miningSpeed} modifier and the cobweb multiplier below. One method builds them now, so a
     * rebaked tool is the tool assembly built at the new stats and nothing else.
     */
    public List<Tool.Rule> miningRules(ToolStats.Stats stats) {
        List<Tool.Rule> rules = new ArrayList<>();
        float speed = miningSpeed(stats);
        if (minesCobweb()) {
            // Upstream SwordCore#getStrVsBlock: cobweb at 7.5x the tool's own speed, on top of the
            // 0.5 sword modifier (issue #437). Vanilla's own sword spends a flat 15.0 here; keeping
            // it relative preserves upstream's per-material scaling. Tool#getMiningSpeed returns the
            // first matching rule, so this has to precede the tag rule below. The sword set is the
            // only mineable family upstream gives a per-block multiplier at all.
            rules.add(Tool.Rule.minesAndDrops(List.of(Blocks.COBWEB), speed * 7.5F));
        }
        for (TagKey<Block> tag : mineableBlocks) {
            rules.add(Tool.Rule.minesAndDrops(tag, speed));
        }
        return rules;
    }

    /**
     * Upstream's {@code ToolCore#miningSpeedModifier}, applied at read time there and here folded
     * into the vanilla tool component (issue #153's Entry field). Package-visible/overridable so a
     * subclass adding a rule of its own ({@link HatchetItem#miningRules}) can reuse the same value
     * rather than re-deriving it.
     */
    protected float miningSpeed(ToolStats.Stats stats) {
        return stats.miningSpeed() * miningSpeedModifier;
    }

    /**
     * Whether this tool type is a sword, i.e. carries upstream {@code SwordCore}'s
     * {@code effective_materials}. 1.21's {@code #minecraft:sword_efficient} covers that set's VINE,
     * GOURD and LEAVES but not its {@code Material.WEB}, so cobweb is named separately -- as
     * vanilla's own {@code SwordItem#createToolProperties} does (issue #437).
     */
    private boolean minesCobweb() {
        return mineableBlocks.contains(BlockTags.SWORD_EFFICIENT);
    }

    /** Whether {@code state} is in any of this tool type's {@link #mineableBlocks} tags. */
    private boolean isEffective(BlockState state) {
        if (state.is(Blocks.COBWEB) && minesCobweb()) {
            return true;
        }
        for (TagKey<Block> tag : mineableBlocks) {
            if (state.is(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Attack damage and speed, derived per stack rather than stored as an
     * {@code attribute_modifiers} component so the Broken state has a single source of truth: with
     * no component present NeoForge falls back to this method on every query, so clearing the flag
     * at the Tool Station is all a repair has to do. Upstream 1.12 gates the same two modifiers on
     * {@code !isBroken} in {@code ToolCore#getAttributeModifiers}.
     *
     * <p>Flat trait damage (upstream's {@code TraitBonusDamage}, which adds to the tool's own attack
     * stat at build time) is folded into this one modifier rather than added as a second one, so the
     * tooltip shows a single Attack Damage line as it would without traits.
     */
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers(ItemStack stack) {
        if (stack.get(ForgeweaveDataComponents.TOOL_STATS.get()) == null || isBroken(stack)) {
            return ItemAttributeModifiers.EMPTY;
        }
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID, attackDamage(stack),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, effectiveAttackSpeed(stack) - 4.0,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND);
        // Upstream's TraitLightweight (issue #102) scales attack speed by 10%; see ForgeweaveTraits#LIGHTWEIGHT.
        float speedBonus = ForgeweaveTraits.attackSpeedBonus(stack);
        if (speedBonus != 0.0F) {
            builder.add(Attributes.ATTACK_SPEED,
                    new AttributeModifier(TRAIT_ATTACK_SPEED_ID, attackSpeed * speedBonus,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        // Vintage's mobility cost (issue #230 maintainer decision): a fraction of the holder's total
        // movement speed while the tool is in hand, so it composes with Speed/slowness like any
        // vanilla multiplier.
        float movementBonus = ForgeweaveTraits.movementSpeedBonus(stack);
        if (movementBonus != 0.0F) {
            builder.add(Attributes.MOVEMENT_SPEED,
                    new AttributeModifier(TRAIT_MOVEMENT_SPEED_ID, movementBonus,
                            AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL),
                    EquipmentSlotGroup.MAINHAND);
        }

        // heavy (issue #229, upstream TraitHeavy): full knockback resistance while the tool is held.
        float knockbackResistance = ForgeweaveTraits.knockbackResistance(stack);
        if (knockbackResistance != 0.0F) {
            builder.add(Attributes.KNOCKBACK_RESISTANCE,
                    new AttributeModifier(TRAIT_KNOCKBACK_RESISTANCE_ID, knockbackResistance,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }

        // #108 batch: Aquadynamic/Far Reach add their own attribute modifiers only when a tool
        // actually carries them, so an unmodified tool's merged-attribute tooltip stays exactly as
        // it was (contrast with ATTACK_DAMAGE/ATTACK_SPEED above, which every tool always carries).
        float submergedMiningSpeedBonus = ForgeweaveModifiers.submergedMiningSpeedBonus(stack);
        if (submergedMiningSpeedBonus != 0.0F) {
            builder.add(Attributes.SUBMERGED_MINING_SPEED,
                    new AttributeModifier(SUBMERGED_MINING_SPEED_ID, submergedMiningSpeedBonus,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        float blockInteractionRangeBonus = ForgeweaveModifiers.blockInteractionRangeBonus(stack);
        if (blockInteractionRangeBonus != 0.0F) {
            builder.add(Attributes.BLOCK_INTERACTION_RANGE,
                    new AttributeModifier(BLOCK_INTERACTION_RANGE_ID, blockInteractionRangeBonus,
                            AttributeModifier.Operation.ADD_VALUE),
                    EquipmentSlotGroup.MAINHAND);
        }
        return builder.build();
    }

    /**
     * This stack's attack speed with its modifiers applied. Upstream 1.12 gates the haste-family
     * attack-speed bonus on {@code Category.WEAPON}, which of Forgeweave's three tools only the
     * hatchet has, so the multiplier applies here only ({@code ForgeweaveModifiers#HASTE}).
     */
    private float effectiveAttackSpeed(ItemStack stack) {
        return weapon ? attackSpeed * ForgeweaveModifiers.attackSpeedMultiplier(stack) : attackSpeed;
    }

    /**
     * This stack's currently effective attack damage: 0 while Broken (matching
     * {@link #getDefaultAttributeModifiers} returning {@code EMPTY} then) or unassembled, otherwise
     * the head material's attack stat -- with sharpness's bonus already folded in
     * ({@link ForgeweaveModifiers#effectiveStats}, #106 batch) -- scaled by this tool type's damage
     * potential plus flat trait bonus damage. Shared by the attribute modifier above,
     * {@link ToolTooltip}'s Attack Damage line, and spiky's thorns reflect
     * ({@code combat.ThornsReflectSeam}, issue #229 -- upstream reads the same
     * {@code ToolHelper.getActualDamage}), so none of them ever shows or deals a number the tool
     * doesn't actually hit for.
     *
     * <p>The result passes through {@link #calcCutoffDamage} against this tool type's
     * {@link ToolConstants.Entry#damageCutoff}) (issue #295, upstream {@code ToolHelper#getActualDamage}/
     * {@code #attackEntity}): below the cutoff the number is unchanged, above it modifier stacking hits
     * diminishing returns rather than climbing without bound.
     */
    public float attackDamage(ItemStack stack) {
        // Modifier-adjusted, not the raw base component (issue #107: silky takes a flat 3 off this at
        // apply time -- ForgeweaveModifiers#effectiveStats is exactly the seam for that).
        ToolStats.Stats stats = ForgeweaveModifiers.effectiveStats(stack);
        if (stats == null || isBroken(stack)) {
            return 0.0F;
        }
        // #228 squeaky: attack damage hard zero (upstream TraitSqueaky#damage). Zeroed here so the
        // attribute and the tooltip agree with the blow ForgeweaveTraits#COMBAT_SEAM zeroes.
        if (ForgeweaveTraits.zeroesAttackDamage(stack)) {
            return 0.0F;
        }
        float damage = stats.attackDamage() * damagePotential + ForgeweaveTraits.attackDamageBonus(stack);
        return cutoffDamage(damage);
    }

    /**
     * {@code damage} through this tool type's cutoff curve. Applied twice per blow, as upstream does:
     * here on the attribute ({@link #attackDamage}, upstream {@code ToolHelper#getActualDamage}) and
     * again by {@code CombatSeams} on the seam-boosted total (upstream {@code ToolHelper#attackEntity},
     * issue #422), so a flat trait bonus cannot climb past the cutoff either.
     */
    public float cutoffDamage(float damage) {
        return calcCutoffDamage(damage, damageCutoff);
    }

    /**
     * Upstream {@code ToolHelper#calcCutoffDamage}, ported whole (issue #295): below {@code cutoff},
     * unchanged; above it, each cutoff-sized chunk beyond the first counts for 0.9x less than the one
     * before it (a geometric falloff), and any remainder past where that multiplier bottoms out at
     * {@code 0.001} is charged at that floor rate instead of tapering further.
     *
     * <pre>
     * float p = 1f;
     * float d = damage;
     * damage = 0f;
     * while(d &gt; cutoff) {
     *   damage += p * cutoff;
     *   if(p &gt; 0.001f) { p *= 0.9f; } else { damage += p * cutoff * ((d / cutoff) - 1f); return damage; }
     *   d -= cutoff;
     * }
     * damage += p * d;
     * return damage;
     * </pre>
     *
     * Package-private and pure so the curve is unit-testable, {@link #attackDurabilityCost}'s precedent.
     */
    static float calcCutoffDamage(float damage, float cutoff) {
        float p = 1f;
        float d = damage;
        float result = 0f;
        while (d > cutoff) {
            result += p * cutoff;
            if (p > 0.001f) {
                p *= 0.9f;
            } else {
                result += p * cutoff * ((d / cutoff) - 1f);
                return result;
            }
            d -= cutoff;
        }
        result += p * d;
        return result;
    }

    /** See the class javadoc: the one place that keeps a Forgeweave tool from ever being destroyed. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        if (amount <= 0) {
            return amount; // healing; nothing to cap.
        }
        // Reinforced (issue #107): a per-level chance to negate the hit outright, upstream
        // ModReinforced#onToolDamage -- level 5's 100% chance always succeeds, which is what reads as
        // unbreakable rather than a separate flag (ForgeweaveModifiers#REINFORCED's javadoc).
        if (entity != null && negatesDurabilityDamage(stack, entity)) {
            return 0;
        }
        // #228: the durability-economy traits (duritos, dense) reprice the loss at this same choke
        // point -- upstream ITrait#onToolDamage inside ToolHelper#damageTool. Gated on entity like
        // the reinforced roll above, which is also where the RandomSource comes from.
        if (entity != null) {
            amount = ForgeweaveTraits.durabilityDamage(stack, entity.getRandom(), amount);
            if (amount <= 0) {
                return 0;
            }
        }
        int brokenAt = stack.getMaxDamage() - 1;
        int applied = Math.max(0, Math.min(amount, brokenAt - stack.getDamageValue()));
        if (stack.getDamageValue() + applied >= brokenAt && !isBroken(stack)) {
            stack.set(ForgeweaveDataComponents.BROKEN.get(), true);
            if (entity != null) {
                // The only feedback the player gets, since there is no break animation to play.
                // Sound and pitch jitter match upstream 1.12's ToolHelper#breakTool.
                Level level = entity.level();
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_BREAK,
                        entity.getSoundSource(), 0.8F, 0.8F + level.getRandom().nextFloat() * 0.4F);
            }
        }
        return applied;
    }

    /** See {@link #damageItem}'s reinforced check. Package-private and pure-ish so it's easy to unit test. */
    static boolean negatesDurabilityDamage(ItemStack stack, LivingEntity entity) {
        float chance = ForgeweaveModifiers.durabilityNegationChance(stack);
        return chance > 0.0F && entity.getRandom().nextFloat() < chance;
    }

    /**
     * The seam for traits that act while the tool is simply carried (upstream 1.12's
     * {@code ITrait#onUpdate}, reached from {@code ToolCore#onUpdate}). Server side only, and never
     * while Broken -- upstream's healing path {@code ToolHelper#damageTool} returns early on a broken
     * tool, and no M1 trait is meant to work on one. Mending moss's self-repair (issue #107) rides the
     * same seam, one call below traits.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity holder && !isBroken(stack)) {
            ForgeweaveTraits.inventoryTick(stack, serverLevel, holder);
            ForgeweaveModifiers.inventoryTick(stack, serverLevel, holder, isSelected);
        }
    }

    /**
     * Mining speed traits ({@code ForgeweaveTraits#MOMENTUM}/{@code #LIGHTWEIGHT}/{@code #STONEBOUND},
     * issue #102) adjust vanilla's own speed calculation in order, same as upstream 1.12's chained
     * {@code PlayerEvent.BreakSpeed} handlers.
     */
    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        if (isBroken(stack)) {
            // Upstream 1.12's ToolHelper#calcDigSpeed: a real 0.3 penalty, not bare-hand (1.0) speed.
            return 0.3F;
        }
        float base = super.getDestroySpeed(stack, state);
        return ForgeweaveTraits.miningSpeed(stack, isEffective(state), base);
    }

    /**
     * Upstream 1.12's {@code ToolHelper#getActualMiningSpeed}: the tool's stored mining-speed stat --
     * with its modifiers folded in ({@code ForgeweaveModifiers#effectiveStats}, upstream applies them
     * to the stored tag) -- times this tool type's own {@code miningSpeedModifier}. Read only by
     * blasting's break-speed rule (parity audit T24), the one behavior that needs the tool's raw speed
     * rather than the speed vanilla already computed from it.
     */
    public float actualMiningSpeed(ItemStack stack) {
        ToolStats.Stats stats = ForgeweaveModifiers.effectiveStats(stack);
        return stats == null ? 0.0F : miningSpeed(stats);
    }

    /**
     * Vanilla's harvest check, plus blasting's (parity audit T24). Upstream keeps the widening in
     * {@code ToolHelper#isToolEffective2} -- "this will be the only place besides fortify where a
     * modifier is hardcoded. I promise. :L" -- where a tool carrying blasting counts as effective on
     * any non-liquid block whose material {@code isToolNotRequired()}. This method is Forgeweave's
     * counterpart of that one ({@code AoeHarvest}'s javadoc: upstream's effectiveness and harvest
     * checks collapse into {@code ItemStack#isCorrectToolForDrops} here), so the widening lands here.
     *
     * <p>{@code Material#isToolNotRequired()} adapts to {@code !state.requiresCorrectToolForDrops()}:
     * both mean "this block drops without the right tool". Blocks that already drop bare-handed lose
     * nothing by the widening; what it buys is that they now count as an effective break, so a
     * blasting pickaxe sweeps dirt into its area, smelts it, and takes the tier bonuses -- exactly
     * what upstream's widened {@code isToolEffective2} does at its own call sites.
     */
    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        if (isBroken(stack)) {
            return false;
        }
        return super.isCorrectToolForDrops(stack, state)
                || (ForgeweaveModifiers.blastingLevel(stack) > 0
                        && !state.requiresCorrectToolForDrops() && !state.liquid());
    }

    /** Upstream 1.12's {@code ToolCore#showDurabilityBar}: no bar once Broken, however damage sits. */
    @Override
    public boolean isBarVisible(ItemStack stack) {
        return !isBroken(stack) && super.isBarVisible(stack);
    }

    /**
     * Upstream 1.12's {@code ToolCore#hasEffect} (issue #341): {@code return
     * TagUtil.hasEnchantEffect(stack)} -- a Forgeweave tool shimmers only when something explicitly
     * asks it to, never because it happens to carry an enchantment. Several modifiers put real vanilla
     * enchantments on the stack (luck's Fortune/Looting, silky's Silk Touch, wind burst), and vanilla's
     * default {@code isFoil} is {@code stack.isEnchanted()}, so without this every lapis-modified tool
     * would glint over the modifier's own texture overlay ({@code ModifierOverlayModels}) that is meant
     * to be the whole visual.
     *
     * <p>This one override is the seam rather than a suppressed {@code ENCHANTMENT_GLINT_OVERRIDE}
     * written at each grant site, because {@code ItemStack#hasFoil} consults that component first and
     * falls back here: every path that writes enchantments onto a tool -- modifier application, luck's
     * growth-on-use, embossing, part exchange, re-assembly -- routes through this one answer with
     * nothing to keep in step, and upstream's own suppression sits in exactly the same place.
     * {@code ForgeweaveTraits}'s shocking charge still sets that component true at full charge and
     * removes it on discharge, and the removal now lands back on this false rather than on vanilla's
     * "any enchantment glints" default.
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        return false;
    }

    /**
     * The components that make one tool a <em>different</em> tool: what it is built from, what has
     * been done to it, and what it looks like. Upstream 1.12's {@code ToolCore#isEqualTinkersItem}
     * (tinkers-1.12 {@code library/tools/ToolCore.java}:655) compares the same three things out of
     * the whole tag -- base materials, the modifier list, and the unmodified base stats -- and
     * ignores everything else, which is what keeps per-tick trait state from reading as a new item.
     *
     * <p>An allow-list rather than a deny-list on purpose: the default for a component nobody
     * thought about here is "ignore", which is the safe end. A new stateful trait component (#230's
     * momentum/insatiable/katana ramp, alien progress, a launcher's draw state) then costs nothing,
     * and the worst a genuinely identity-bearing component costs by being forgotten is a missing
     * re-equip bob -- against the constant bobbing and reset mining progress a deny-list miss costs.
     */
    private static List<DataComponentType<?>> identityComponents() {
        return List.of(
                ForgeweaveDataComponents.TOOL_MATERIALS.get(), // what it is built from
                ForgeweaveDataComponents.TOOL_STATS.get(), // its built stats
                ForgeweaveDataComponents.LAUNCHER_STATS.get(), // ... and a bow's
                ForgeweaveDataComponents.PROJECTILE_STATS.get(), // ... and an arrow's (#653)
                ForgeweaveDataComponents.MODIFIERS.get(),
                ForgeweaveDataComponents.TRAITS.get(),
                ForgeweaveDataComponents.BROKEN.get(), // swaps the model
                ForgeweaveDataComponents.TEXTURE.get(),
                ForgeweaveDataComponents.CROSSBOW_LOADED.get());
    }

    /**
     * Whether swapping {@code oldStack} for {@code newStack} in hand is a new item as far as the
     * held-item animation and block-breaking progress are concerned. Ported from upstream 1.12's
     * {@code ToolCore#shouldCauseReequipAnimation} (tinkers-1.12
     * {@code library/tools/ToolCore.java}:583-620): a slot change, a glint change, a change in the
     * attribute modifiers the tool grants, or a change in its identity ({@link #identityComponents}).
     * Component churn outside that -- durability, mending moss XP, a trait's per-tick bookkeeping --
     * is not a new item.
     *
     * <p>NeoForge's default is vanilla's {@code !oldStack.equals(newStack)}, i.e. any component
     * differing at all. That made an electrum tool bob and drop its mining progress continuously
     * while walking, because {@code ForgeweaveTraits#SHOCKING} rewrites its charge every 5 ticks
     * (issue #414); every other stateful trait had the same exposure.
     *
     * <p>Upstream's first check is a "reset" flag it sets on the stack to force the animation; there
     * is no Forgeweave equivalent, and nothing here needs one -- the station hands back a whole new
     * stack whose identity components already differ.
     *
     * <p>Two adaptations to modern APIs: glint is {@code ItemStack#hasFoil} (the
     * {@code enchantment_glint_override} component, then {@link #isFoil}) rather than 1.12's
     * {@code hasEffect}, and the attribute comparison is over the whole
     * {@link net.minecraft.world.item.component.ItemAttributeModifiers} value rather than 1.12's
     * hand-rolled walk of the MAINHAND multimap -- a superset, since a tool only ever grants MAINHAND
     * modifiers, and it resolves through {@link #getDefaultAttributeModifiers} so the Broken state and
     * every trait/modifier bonus are included.
     */
    static boolean shouldReequip(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (oldStack == newStack) {
            return false;
        }
        if (slotChanged || oldStack.getItem() != newStack.getItem()) {
            return true;
        }
        if (oldStack.hasFoil() != newStack.hasFoil()) {
            return true;
        }
        if (!oldStack.getAttributeModifiers().equals(newStack.getAttributeModifiers())) {
            return true;
        }
        for (DataComponentType<?> component : identityComponents()) {
            if (!Objects.equals(oldStack.get(component), newStack.get(component))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return shouldReequip(oldStack, newStack, slotChanged);
    }

    /** Upstream 1.12's {@code ToolCore#shouldCauseBlockBreakReset}:577 delegates to the same rule. */
    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return shouldReequip(oldStack, newStack, false);
    }

    /**
     * Vanilla charges a flat {@code tool.damagePerBlock()}; upstream 1.12 charges 1 for a block the
     * tool is meant for and 2 for anything else ({@code ToolCore#onBlockDestroyed}:
     * {@code int damage = effective ? 1 : 2}), which the single-valued {@code tool} component can't
     * express. Returning false while Broken also skips vanilla's "item used" stat, matching
     * upstream's early return.
     */
    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity entity) {
        if (isBroken(stack) || stack.get(DataComponents.TOOL) == null) {
            return false;
        }
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            boolean effective = isEffective(state);
            int cost = miningDurabilityCost(state, effective);
            if (cost > 0) {
                stack.hurtAndBreak(cost, entity, EquipmentSlot.MAINHAND);
            }
            if (level instanceof ServerLevel serverLevel) {
                // Traits that react to an actual block break (issue #102: momentum, petramor;
                // issue #230: shocking, slimey, baconlicious -- the latter two need pos/effective).
                ForgeweaveTraits.afterBlockBreak(stack, serverLevel, state, pos, entity, effective);
            }
        }
        return true;
    }

    /**
     * Durability spent on one block broken -- upstream's {@code effective ? 1 : 2}
     * ({@code ToolCore#onBlockDestroyed}), the default for every tool but the hatchet, which
     * overrides this to charge leaves nothing at all ({@code Hatchet#afterBlockBreak}, parity audit
     * 2026-08-18 T65, issue #496) regardless of {@code effective} -- see {@link HatchetItem}.
     */
    protected int miningDurabilityCost(BlockState state, boolean effective) {
        return effective ? 1 : 2;
    }

    /** Upstream 1.12 refuses the attack outright while Broken ({@code ToolHelper#attackEntity}). */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return !isBroken(stack) && stack.get(ForgeweaveDataComponents.TOOL_STATS.get()) != null;
    }

    /**
     * Upstream 1.12's {@code ToolCore#reduceDurabilityOnHit}, not vanilla's flat 2:
     *
     * <pre>
     * damage = Math.max(1f, damage / 10f);
     * if(!hasCategory(Category.WEAPON)) damage *= 2;
     * ToolHelper.damageTool(stack, (int) damage, player);
     * </pre>
     *
     * <p>Only the hatchet is {@code Category.WEAPON} upstream ({@code tools/tools/Hatchet.java}
     * adds it; {@code Pickaxe}/{@code Shovel} are {@code HARVEST} only), so a hatchet pays half what
     * the other two do -- 1 per hit at M1 damage values rather than 2. The float order matters: the
     * doubling happens before the truncation, so a 15-damage pickaxe costs 3, not 2.
     *
     * @see #attackDurabilityCost
     */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        // Upstream's ToolHelper#attackEntity runs every trait's afterHit before reduceDurabilityOnHit
        // (issue #297 parity fix): a hit that crosses insatiable's stack threshold pays that same
        // hit's own bonus cost, not the cost the stack owed before this swing.
        if (attacker.level() instanceof ServerLevel serverLevel) {
            ForgeweaveTraits.afterHit(stack, serverLevel, attacker, target);
        }
        // Upstream feeds the fully resolved hit damage; 1.21 does not hand that to this hook, so this
        // is the attacker's ATTACK_DAMAGE attribute -- upstream's own `baseDamage`, before its crit
        // and cooldown scaling. Nullable because not every LivingEntity has that attribute; such an
        // attacker lands on the formula's floor, which is where every M1 material lands anyway.
        AttributeInstance attackDamage = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        float damage = attackDamage == null ? 0.0F : (float) attackDamage.getValue();
        int cost = attackDurabilityCost(damage, weapon) + ForgeweaveTraits.attackDurabilityBonus(stack);
        stack.hurtAndBreak(cost, attacker, EquipmentSlot.MAINHAND);
    }

    /** See {@link #postHurtEnemy}. Package-private and pure so the formula is unit-testable. */
    static int attackDurabilityCost(float damage, boolean weapon) {
        float cost = Math.max(1.0F, damage / 10.0F);
        return (int) (weapon ? cost : cost * 2.0F);
    }

    /**
     * Sunder's shield-disable half (docs/SCOPE.md M3 issue #164): vanilla's shield-disable mechanic is
     * entirely automatic once an item opts in ({@code IItemExtension#canDisableShield}, default
     * {@code this instanceof AxeItem}) -- {@code Player#blockUsingShield} calls this on the attacker's
     * main-hand item and, if it returns true, forces the blocker to drop their shield with a 100-tick
     * cooldown ({@code Player#disableShield}). There is no {@link dev.gkissel.forgeweave.combat.CombatSeam}
     * hook for this: it fires before a hit's damage is even resolved (from the shield-block check
     * inside {@code LivingEntity#hurt}, ahead of {@code preHit}), so it isn't a per-hit adjustment a
     * seam could make -- it is a capability the item declares, the same kind of thing
     * {@link #isEnchantable} and {@link #toolComponent} already are. The hatchet opts in here, gated
     * on the same {@link #weapon} flag that already marks it upstream's one {@code Category.WEAPON}
     * tool: "make the hatchet count as an axe for shield-disabling", per the issue.
     */
    @Override
    public boolean canDisableShield(ItemStack stack, ItemStack shield, LivingEntity entity, LivingEntity attacker) {
        return weapon;
    }

    /**
     * Durability/broken state, mining speed, attack damage, tool tier, the three parts and their
     * traits -- see {@link ToolTooltip} for the compact-vs-Shift structure ported from upstream
     * 1.12 (NOTICE.md). {@code flag.hasShiftDown()} is NeoForge's {@code TooltipFlagExtension},
     * real on the client and {@code false} otherwise; {@link ToolTooltip#append} takes that as a
     * plain {@code boolean} so it stays callable with an explicit value from unit tests.
     */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        ToolTooltip.append(stack, context.registries(), ToolTooltip.detailed(flag), attackDamage(stack), tooltip);
        ToolTooltip.appendShiftHint(ToolTooltip.shiftHint(flag), tooltip);
    }

    // ------------------------------------------------------------------ innate item use (issue #155)

    /**
     * Right-click starts the innate's use action, if it has one -- the longsword's leap charge, the
     * broadsword's parry window, the battlesign's blocking stance. Refused while Broken, matching
     * upstream's own {@code BattleSign#onItemRightClick}.
     *
     * <p>An action that answers {@link ToolUseAction#onUse} rather than being held (the rapier's
     * lunge, issue #300) does its work there and its result is returned as-is; only the held ones
     * reach {@code startUsingItem}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        ToolUseAction action = useAction();
        if (action == null || isBroken(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        InteractionResultHolder<ItemStack> instant = action.onUse(stack, level, player, hand);
        if (instant != null) {
            return instant;
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * The two right-click-on-a-block behaviors a tool type can carry, both ports:
     *
     * <ul>
     *   <li>the scythe's harvest (issue #157), upstream 1.12's {@code Kama#onItemRightClick}: only
     *       tools whose {@link #aoeShape} is {@link AoeHarvest.Shape#CUBE_3X3X3} have one. The kama
     *       harvests the same way over one block instead -- {@code KamaItem}, which overrides this;
     *   <li>the shovel family's grass paths (issue #464), upstream {@code Shovel#onItemUse}: gated on
     *       the tool's own {@link ItemAbilities#SHOVEL_FLATTEN}, so it follows the tag-derived tool
     *       kind rather than a second list ({@link ShovelPath});
     *   <li>the axe family's strip/scrape/wax-off (issue #575), upstream 1.20's {@code stripping}
     *       trait: gated the same way on {@link ItemAbilities#AXE_STRIP} ({@link AxeStrip}).
     * </ul>
     *
     * <p>Anything else -- and any Broken tool, upstream's {@code if(isBroken) return FAIL} -- falls
     * through to vanilla's behavior for a right-click on a block.
     */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (isBroken(context.getItemInHand())) {
            return super.useOn(context);
        }
        if (aoeShape == AoeHarvest.Shape.CUBE_3X3X3) {
            return CropHarvest.harvestAround(context);
        }
        if (abilities.contains(ItemAbilities.SHOVEL_FLATTEN)) {
            return ShovelPath.flattenAt(context, aoeShape);
        }
        if (abilities.contains(ItemAbilities.AXE_STRIP)) {
            return AxeStrip.transformAt(context, aoeShape);
        }
        return super.useOn(context);
    }

    /**
     * The scythe's area shear (issue #467), upstream 1.12's {@code Scythe#itemInteractionForEntity}/
     * {@code #getAoeEntities}: every {@link net.neoforged.neoforge.common.IShearable} entity in the
     * 3x3x3 around the one right-clicked, not just that one -- the same {@link
     * AoeHarvest.Shape#CUBE_3X3X3} gate {@link #useOn} above uses for the crop-harvest half of the
     * scythe, so every other tool still falls through to vanilla (no shearing) here too. The kama
     * shears the single entity clicked instead -- {@code KamaItem}, which overrides this.
     */
    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        return aoeShape == AoeHarvest.Shape.CUBE_3X3X3
                ? EntityShear.shearAround(stack, player, target, hand)
                : super.interactLivingEntity(stack, player, target, hand);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        ToolUseAction action = useAction();
        return action == null ? super.getUseAnimation(stack) : action.animation();
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        ToolUseAction action = useAction();
        return action == null ? super.getUseDuration(stack, entity) : action.durationTicks();
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity user, int timeLeft) {
        ToolUseAction action = useAction();
        if (action != null && level instanceof ServerLevel serverLevel) {
            action.onRelease(stack, serverLevel, user, action.durationTicks() - timeLeft);
        }
    }
}
