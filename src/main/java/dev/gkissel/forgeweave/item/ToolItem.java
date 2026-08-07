package dev.gkissel.forgeweave.item;

import java.util.List;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import dev.gkissel.forgeweave.config.ForgeweaveConfig;
import dev.gkissel.forgeweave.material.Material;
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
    private final TagKey<Block> mineableBlocks;
    private final float attackSpeed;
    private final float damagePotential;

    /**
     * @param mineableBlocks the vanilla {@code mineable/*} tag this tool type is for
     * @param attackSpeed attacks per second, upstream 1.12's {@code ToolCore#attackSpeed()}
     * @param damagePotential multiplier on the head material's attack damage, upstream 1.12's
     *     {@code ToolCore#damagePotential()}
     */
    public ToolItem(Properties properties, TagKey<Block> mineableBlocks, float attackSpeed, float damagePotential) {
        super(properties);
        this.mineableBlocks = mineableBlocks;
        this.attackSpeed = attackSpeed;
        this.damagePotential = damagePotential;
    }

    public static boolean isBroken(ItemStack stack) {
        return stack.getOrDefault(ForgeweaveDataComponents.BROKEN.get(), false);
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
                        Tool.Rule.minesAndDrops(mineableBlocks, stats.miningSpeed())),
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
        ToolStats.Stats stats = stack.get(ForgeweaveDataComponents.TOOL_STATS.get());
        if (stats == null || isBroken(stack)) {
            return ItemAttributeModifiers.EMPTY;
        }
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(BASE_ATTACK_DAMAGE_ID,
                                stats.attackDamage() * damagePotential + ForgeweaveTraits.attackDamageBonus(stack),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(BASE_ATTACK_SPEED_ID, attackSpeed - 4.0,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    /** See the class javadoc: the one place that keeps a Forgeweave tool from ever being destroyed. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        if (amount <= 0) {
            return amount; // healing; nothing to cap.
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

    /**
     * The seam for traits that act while the tool is simply carried (upstream 1.12's
     * {@code ITrait#onUpdate}, reached from {@code ToolCore#onUpdate}). Server side only, and never
     * while Broken -- upstream's healing path {@code ToolHelper#damageTool} returns early on a broken
     * tool, and no M1 trait is meant to work on one.
     */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level instanceof ServerLevel serverLevel && entity instanceof LivingEntity holder && !isBroken(stack)) {
            ForgeweaveTraits.inventoryTick(stack, serverLevel, holder);
        }
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return isBroken(stack) ? 1.0F : super.getDestroySpeed(stack, state);
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
        }
        return true;
    }

    /** Upstream 1.12 refuses the attack outright while Broken ({@code ToolHelper#attackEntity}). */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return !isBroken(stack) && stack.get(ForgeweaveDataComponents.TOOL_STATS.get()) != null;
    }

    /** Vanilla's flat 2 per hit, which is also what upstream 1.12's {@code reduceDurabilityOnHit} lands on for these tools. */
    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(2, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        if (isBroken(stack)) {
            tooltip.add(Component.translatable("tooltip.forgeweave.broken")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        }
        ToolMaterials materials = stack.get(ForgeweaveDataComponents.TOOL_MATERIALS.get());
        if (materials == null) {
            return;
        }
        tooltip.add(materialName(context.registries(), materials.head()));
        tooltip.add(materialName(context.registries(), materials.binding()));
        tooltip.add(materialName(context.registries(), materials.handle()));
    }

    private static MutableComponent materialName(HolderLookup.Provider registries, ResourceLocation materialId) {
        MutableComponent name =
                Component.translatable("material." + materialId.getNamespace() + "." + materialId.getPath());
        TextColor color = lookupColor(registries, materialId);
        return color != null ? name.withStyle(Style.EMPTY.withColor(color)) : name;
    }

    private static TextColor lookupColor(HolderLookup.Provider registries, ResourceLocation materialId) {
        if (registries == null) {
            return null;
        }
        return registries.lookup(Material.REGISTRY)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Material.REGISTRY, materialId)))
                .map(holder -> holder.value().color())
                .orElse(null);
    }
}
