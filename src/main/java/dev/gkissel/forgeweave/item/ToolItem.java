package dev.gkissel.forgeweave.item;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.CombatSeam;
import dev.gkissel.forgeweave.combat.ForgeweaveInnates;
import dev.gkissel.forgeweave.combat.ToolUseAction;
import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.modifier.ForgeweaveModifiers;
import dev.gkissel.forgeweave.tool.ToolConstants;
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
 * falls back to bare-hand speed and drops nothing, attack modifiers disappear, and the tooltip says
 * so. Upstream 1.12 does the same thing with an NBT flag plus a {@code ToolCore} that never lets
 * vanilla see a fully-damaged stack ({@code library/utils/ToolHelper.java}
 * {@code #damageTool}/{@code #breakTool}/{@code #isBroken}).
 *
 * <p>A Broken tool therefore rests at {@code damage == maxDamage - 1}; the durability bar rounds
 * that to zero width, so it reads as empty. Repair (Tool Station, {@code ToolAssemblyRecipes}) is
 * the only thing that clears the flag.
 */
public class ToolItem extends Item {
    /** Id for the trait-driven attack speed modifier {@link #getDefaultAttributeModifiers} adds (issue #102). */
    private static final ResourceLocation TRAIT_ATTACK_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "trait_attack_speed");
    // #108 batch: modern-vanilla attribute modifiers (Aquadynamic, Far Reach -- ForgeweaveModifiers),
    // same idiom as vanilla Item's own BASE_ATTACK_DAMAGE_ID/BASE_ATTACK_SPEED_ID this class already
    // keys its two attribute modifiers on above.
    private static final ResourceLocation SUBMERGED_MINING_SPEED_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "aquadynamic");
    private static final ResourceLocation BLOCK_INTERACTION_RANGE_ID =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "far_reach");

    private final TagKey<Block> mineableBlocks;
    private final float attackSpeed;
    private final float damagePotential;
    private final float miningSpeedModifier;
    private final boolean weapon;
    @Nullable
    private final ForgeweaveInnates.Innate innate;

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
        this(properties, mineableBlocks, constants.attackSpeed(), constants.damagePotential(),
                constants.miningSpeedModifier(), weapon, innate);
    }

    /**
     * @param mineableBlocks the vanilla {@code mineable/*} tag this tool type is for
     * @param attackSpeed attacks per second, upstream 1.12's {@code ToolCore#attackSpeed()}
     * @param damagePotential multiplier on the head material's attack damage, upstream 1.12's
     *     {@code ToolCore#damagePotential()}
     * @param weapon whether upstream gives this tool {@code Category.WEAPON} (only the hatchet
     *     does), which halves what a hit costs it -- see {@link #postHurtEnemy}
     * @param innate this tool type's built-in combat behavior (docs/SCOPE.md M3), or {@code null}.
     *     Its {@link CombatSeam} half is picked up by the shared per-hit pipeline through
     *     {@code ForgeweaveInnates#collect}; its {@link ToolUseAction} half is what the four
     *     item-use overrides below forward to, so no tool needs a subclass of its own.
     */
    public ToolItem(Properties properties, TagKey<Block> mineableBlocks, float attackSpeed, float damagePotential,
            float miningSpeedModifier, boolean weapon, @Nullable ForgeweaveInnates.Innate innate) {
        super(properties);
        this.mineableBlocks = mineableBlocks;
        this.attackSpeed = attackSpeed;
        this.damagePotential = damagePotential;
        this.miningSpeedModifier = miningSpeedModifier;
        this.weapon = weapon;
        this.innate = innate;
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
     * The vanilla {@code tool} component for a tool assembled from this head material: tier gating
     * comes from the material's {@code incorrect_for_tool} block tag (CONTEXT.md: vanilla tool-tier
     * tags, never numeric harvest levels), the speed bonus applies to this tool type's
     * {@code mineable/*} tag. Same rule order and shape as vanilla's
     * {@code Tier#createToolProperties}, so an incorrect-tier block is still dug at tool speed but
     * drops nothing.
     */
    public Tool toolComponent(Material head, ToolStats.Stats stats) {
        return new Tool(
                List.of(Tool.Rule.deniesDrops(head.incorrectForTool()),
                        // Upstream's ToolCore#miningSpeedModifier, applied at read time there and
                        // here folded into the vanilla tool component (issue #153's Entry field).
                        Tool.Rule.minesAndDrops(mineableBlocks, stats.miningSpeed() * miningSpeedModifier)),
                1.0F,
                1);
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
     * potential plus flat trait bonus damage. Shared by the attribute modifier above and
     * {@link ToolTooltip}'s Attack Damage line so the tooltip never shows a number the tool doesn't
     * actually hit for.
     */
    private float attackDamage(ItemStack stack) {
        // Modifier-adjusted, not the raw base component (issue #107: silky takes a flat 3 off this at
        // apply time -- ForgeweaveModifiers#effectiveStats is exactly the seam for that).
        ToolStats.Stats stats = ForgeweaveModifiers.effectiveStats(stack);
        if (stats == null || isBroken(stack)) {
            return 0.0F;
        }
        return stats.attackDamage() * damagePotential + ForgeweaveTraits.attackDamageBonus(stack);
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
            ForgeweaveModifiers.inventoryTick(stack, serverLevel, holder);
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
            return 1.0F;
        }
        float base = super.getDestroySpeed(stack, state);
        return ForgeweaveTraits.miningSpeed(stack, state.is(mineableBlocks), base);
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return !isBroken(stack) && super.isCorrectToolForDrops(stack, state);
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
            stack.hurtAndBreak(state.is(mineableBlocks) ? 1 : 2, entity, EquipmentSlot.MAINHAND);
            if (level instanceof ServerLevel serverLevel) {
                // Traits that react to an actual block break (issue #102: momentum, petramor).
                ForgeweaveTraits.afterBlockBreak(stack, serverLevel, state, entity);
            }
        }
        return true;
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
        // Upstream feeds the fully resolved hit damage; 1.21 does not hand that to this hook, so this
        // is the attacker's ATTACK_DAMAGE attribute -- upstream's own `baseDamage`, before its crit
        // and cooldown scaling. Nullable because not every LivingEntity has that attribute; such an
        // attacker lands on the formula's floor, which is where every M1 material lands anyway.
        AttributeInstance attackDamage = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
        float damage = attackDamage == null ? 0.0F : (float) attackDamage.getValue();
        // Insatiable (issue #102) costs extra durability per stack, read before afterHit grows it.
        int cost = attackDurabilityCost(damage, weapon) + ForgeweaveTraits.attackDurabilityBonus(stack);
        stack.hurtAndBreak(cost, attacker, EquipmentSlot.MAINHAND);
        if (attacker.level() instanceof ServerLevel serverLevel) {
            ForgeweaveTraits.afterHit(stack, serverLevel, attacker, target);
        }
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
        ToolTooltip.append(stack, context.registries(), flag.hasShiftDown(), attackDamage(stack), tooltip);
    }

    // ------------------------------------------------------------------ innate item use (issue #155)

    /**
     * Right-click starts the innate's use action, if it has one -- the longsword's leap charge, the
     * broadsword's parry window, the battlesign's blocking stance. Refused while Broken, matching
     * upstream's own {@code BattleSign#onItemRightClick}.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (useAction() == null || isBroken(stack)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
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
