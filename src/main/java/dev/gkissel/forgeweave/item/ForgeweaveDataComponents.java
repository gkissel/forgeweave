package dev.gkissel.forgeweave.item;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;

import net.neoforged.neoforge.fluids.SimpleFluidContent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.combat.DamageRamp;
import dev.gkissel.forgeweave.modifier.ModifierEntry;
import dev.gkissel.forgeweave.tool.ArmorStats;
import dev.gkissel.forgeweave.tool.LauncherStats;
import dev.gkissel.forgeweave.tool.ProjectileStats;
import dev.gkissel.forgeweave.tool.ToolMaterials;
import dev.gkissel.forgeweave.tool.ToolStats;
import dev.gkissel.forgeweave.trait.AlienProgress;
import dev.gkissel.forgeweave.trait.ShockingCharge;
import dev.gkissel.forgeweave.trait.TraitStacks;

/** Data components carried by Forgeweave items. */
public final class ForgeweaveDataComponents {
    public static final DeferredRegister.DataComponents DATA_COMPONENTS =
            DeferredRegister.createDataComponents(Registries.DATA_COMPONENT_TYPE, Forgeweave.MODID);

    /**
     * The id of the {@code dev.gkissel.forgeweave.material.Material} a part item was crafted from
     * (ADR-0002: materials are datapack registry entries, so parts reference them by id rather than
     * embedding their stats).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> MATERIAL =
            DATA_COMPONENTS.registerComponentType("material",
                    builder -> builder.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));

    /** The head/binding/handle materials an assembled tool ({@code ToolItem}) was built from. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolMaterials>> TOOL_MATERIALS =
            DATA_COMPONENTS.registerComponentType("tool_materials",
                    builder -> builder.persistent(ToolMaterials.CODEC).networkSynchronized(ToolMaterials.STREAM_CODEC));

    /**
     * The stats {@code ToolStats#compute} derived from those materials at assembly time. Durability
     * is mirrored onto vanilla's max-damage component and mining speed into vanilla's {@code tool}
     * component; this keeps the whole block so attack damage has a home and so a tool's stats can be
     * read back without a registry lookup.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ToolStats.Stats>> TOOL_STATS =
            DATA_COMPONENTS.registerComponentType("tool_stats",
                    builder -> builder.persistent(ToolStats.Stats.CODEC).networkSynchronized(ToolStats.Stats.STREAM_CODEC));

    /**
     * The ranged half of a bow's stats -- upstream 1.12's {@code ProjectileLauncherNBT} fields
     * (M3.5 issue #394): draw speed, range and bonus damage averaged from the limbs' BOW blocks at
     * assembly. Only bows carry it; {@link #TOOL_STATS} stays the melee/durability half.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<LauncherStats>> LAUNCHER_STATS =
            DATA_COMPONENTS.registerComponentType("launcher_stats",
                    builder -> builder.persistent(LauncherStats.CODEC).networkSynchronized(LauncherStats.STREAM_CODEC));

    /**
     * The ranged half of a projectile's stats -- upstream 1.12's {@code ProjectileNBT#accuracy}
     * (issue #653, parity audit T17), the fletching's flight accuracy averaged at assembly. Only
     * the material arrow carries it; fixture {@code m653_tool_arrow.snbt} pins the shape.
     */
    /**
     * An armor piece's stat block (issue #678, M4-3; SCOPE.md D14) -- the parallel to
     * {@link #LAUNCHER_STATS}: armor never carries {@link #TOOL_STATS}, whose shape stays untouched.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ArmorStats>> ARMOR_STATS =
            DATA_COMPONENTS.registerComponentType("armor_stats",
                    builder -> builder.persistent(ArmorStats.CODEC).networkSynchronized(ArmorStats.STREAM_CODEC));

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ProjectileStats>> PROJECTILE_STATS =
            DATA_COMPONENTS.registerComponentType("projectile_stats",
                    builder -> builder.persistent(ProjectileStats.CODEC)
                            .networkSynchronized(ProjectileStats.STREAM_CODEC));

    /**
     * How well the assembled tool takes vanilla enchantments -- the mean of its parts' materials'
     * {@code Material#enchantability}, computed once at assembly ({@code ToolStats#averageEnchantability},
     * issue #593). Baked onto the stack for the same reason {@link #TRAITS} is: the seam that needs
     * it, {@code ToolItem#getEnchantmentValue(ItemStack)}, receives only an {@code ItemStack} and
     * has no registry access to resolve materials with.
     *
     * <p>Absent on every tool assembled before #593 (and on any hand-built test stack), which
     * {@code ToolItem#getEnchantmentValue} reads as {@code Material#DEFAULT_ENCHANTABILITY} -- the
     * flat 14 those tools already enchanted at.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> ENCHANTABILITY =
            DATA_COMPONENTS.registerComponentType("enchantability",
                    builder -> builder.persistent(ExtraCodecs.POSITIVE_INT)
                            .networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * The ids of the {@code Trait}s an assembled tool has, resolved from its three materials at
     * assembly time and de-duplicated ({@code ForgeweaveTraits#resolve}). Stored rather than looked
     * up per use because the seams traits hook into -- notably
     * {@code ToolItem#getDefaultAttributeModifiers}, which receives only an {@code ItemStack} -- have
     * no registry access to resolve materials with.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ResourceLocation>>> TRAITS =
            DATA_COMPONENTS.registerComponentType("traits",
                    builder -> builder.persistent(ResourceLocation.CODEC.listOf())
                            .networkSynchronized(ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list())));

    /**
     * The Modifiers applied to a tool at the Tool Station, in application order. ADR-0004's hard
     * rule: each entry is nothing but an {@code id} and a {@code level}
     * ({@code modifier.ModifierEntry}) -- never a class reference -- so every save and fixture stays
     * decodable across the M6 migration to datapack-defined modifiers. An id this version doesn't
     * implement is kept as inert data rather than dropped ({@code ForgeweaveModifiers#get}).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<ModifierEntry>>> MODIFIERS =
            DATA_COMPONENTS.registerComponentType("modifiers",
                    builder -> builder.persistent(ModifierEntry.CODEC.listOf())
                            .networkSynchronized(ModifierEntry.STREAM_CODEC.apply(ByteBufCodecs.list())));

    /**
     * Set once a tool runs out of durability. CONTEXT.md: a Broken tool is unusable but never
     * destroyed, and only a Tool Station repair clears this.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> BROKEN =
            DATA_COMPONENTS.registerComponentType("broken",
                    builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /**
     * Whether a crossbow is drawn and holding its charge (M3.5 issue #395) -- upstream 1.12's
     * {@code CrossBow}'s {@code TAG_Loaded} NBT boolean, and nothing more: upstream stores no ammo
     * with it, finding and spending the arrow at fire time instead. A component rather than a
     * transient field so the charge rides the {@code ItemStack} through a hotbar swap, a chest and a
     * save/reload, exactly as the tag did.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> CROSSBOW_LOADED =
            DATA_COMPONENTS.registerComponentType("crossbow_loaded",
                    builder -> builder.persistent(Codec.BOOL).networkSynchronized(ByteBufCodecs.BOOL));

    /** How many times a tool has been repaired; feeds the diminishing returns in {@code ToolRepair}. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> REPAIR_COUNT =
            DATA_COMPONENTS.registerComponentType("repair_count",
                    builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * XP banked on a mending-moss-modified tool, spent one at a time to self-repair (issue #107, ADR
     * -0004: this is state beyond {@code id + level}, so it can't live on {@code ModifierEntry} and
     * instead sits here, the same way {@code BROKEN} and {@code REPAIR_COUNT} carry state that isn't
     * the modifier list itself -- see {@code modifier.ForgeweaveModifiers#MENDING_MOSS}). Absent means
     * zero, same as {@code REPAIR_COUNT}.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> MENDING_MOSS_XP =
            DATA_COMPONENTS.registerComponentType("mending_moss_xp",
                    builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * The registry id of the {@code Block} (a log or planks) a table station item was crafted from,
     * so the placed block can retain that wood's appearance (issue #43). Absent means "unspecified"
     * (e.g. a creative-tab/pick-block item) -- {@code WoodTexturedBlockEntity} falls back to oak.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> TEXTURE =
            DATA_COMPONENTS.registerComponentType("texture",
                    builder -> builder.persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC));

    /**
     * {@code ForgeweaveTraits#MOMENTUM}'s current mining-speed-boost stack and its decay countdown
     * (issue #102): upstream 1.12 stores this as a hidden potion effect on the player, shared by
     * every Momentum tool they hold; Forgeweave has no potion-effect plumbing, so it lives on the
     * tool's own stack instead.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TraitStacks>> MOMENTUM_STACKS =
            DATA_COMPONENTS.registerComponentType("momentum_stacks",
                    builder -> builder.persistent(TraitStacks.CODEC).networkSynchronized(TraitStacks.STREAM_CODEC));

    /**
     * The katana's in-combat damage ramp (docs/SCOPE.md M3 issue #160): how many hits it has landed
     * in a row and when the last one was, as a game-time stamp. See {@code combat.DamageRamp} for why
     * this is a timestamp rather than the countdown {@link #MOMENTUM_STACKS}/{@link
     * #INSATIABLE_STACKS} use, and for the save-compat promise this shape carries.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DamageRamp.State>> KATANA_RAMP =
            DATA_COMPONENTS.registerComponentType("katana_ramp",
                    builder -> builder.persistent(DamageRamp.State.CODEC).networkSynchronized(DamageRamp.State.STREAM_CODEC));

    /** {@code ForgeweaveTraits#INSATIABLE}'s current damage-bonus stack and its decay countdown. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TraitStacks>> INSATIABLE_STACKS =
            DATA_COMPONENTS.registerComponentType("insatiable_stacks",
                    builder -> builder.persistent(TraitStacks.CODEC).networkSynchronized(TraitStacks.STREAM_CODEC));

    /**
     * {@code ForgeweaveTraits#MAGNETIC}/{@code #MAGNETIC2}'s 30-tick after-use pull window (issue
     * #459 parity fix): upstream 1.12 stores this as a hidden potion effect re-applied to the player
     * from {@code afterBlockBreak}/{@code onHit}; Forgeweave has no player-scoped potion-effect
     * plumbing, so it lives on the tool's own stack instead, the same adaptation as
     * {@link #MOMENTUM_STACKS}/{@link #INSATIABLE_STACKS}. {@code level} is unused (always 1) --
     * only the countdown's presence gates the pull; the pull's own range still reads the tool's
     * combined magnetic level fresh each tick.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<TraitStacks>> MAGNETIC_STACKS =
            DATA_COMPONENTS.registerComponentType("magnetic_stacks",
                    builder -> builder.persistent(TraitStacks.CODEC).networkSynchronized(TraitStacks.STREAM_CODEC));

    /**
     * {@code ForgeweaveTraits#ALIEN}'s progressive-stat state (M3.2 issue #230): the 800-point pool
     * rolled the first time the trait ticks and the share of it distributed so far. Upstream 1.12
     * stores the same pair as {@code alienStatPool}/{@code alienStatBonus} NBT
     * ({@code TraitProgressiveStats}); see {@code trait.AlienProgress} for the shape and the
     * save-compat promise it carries.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<AlienProgress>> ALIEN_PROGRESS =
            DATA_COMPONENTS.registerComponentType("alien_progress",
                    builder -> builder.persistent(AlienProgress.CODEC).networkSynchronized(AlienProgress.STREAM_CODEC));

    /**
     * {@code ForgeweaveTraits#SHOCKING}'s 0-100 charge and the last movement sample position (M3.2
     * issue #230). Upstream 1.12 stores the identical four fields in the tool's modifier tag
     * ({@code TraitShocking.Data}); see {@code trait.ShockingCharge} for the shape and the
     * save-compat promise it carries.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ShockingCharge>> SHOCKING_CHARGE =
            DATA_COMPONENTS.registerComponentType("shocking_charge",
                    builder -> builder.persistent(ShockingCharge.CODEC).networkSynchronized(ShockingCharge.STREAM_CODEC));

    /**
     * {@code ForgeweaveTraits#OVERSHIELD}'s banked charge on a knightslime armor piece (issue #680,
     * M4-5): the clone's overshield spends overslime, which Forgeweave has none of (SCOPE.md D17),
     * so the piece banks its own 0..{@code OVERSHIELD_CAPACITY} counter instead. Absent means zero;
     * fixture {@code m4_5_armor_overshield_charge.snbt} pins the shape.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> OVERSHIELD =
            DATA_COMPONENTS.registerComponentType("overshield",
                    builder -> builder.persistent(ExtraCodecs.NON_NEGATIVE_INT).networkSynchronized(ByteBufCodecs.VAR_INT));

    /**
     * The fluid a broken seared tank/gauge/window was holding, so placing it back restores its
     * contents (docs/SCOPE.md M2 issue #95). Upstream 1.12 stores the same thing as raw stack NBT in
     * {@code BlockTank#getDrops}; this is vanilla 1.21's implicit block-entity component equivalent.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<SimpleFluidContent>> FLUID_CONTENT =
            DATA_COMPONENTS.registerComponentType("fluid_content",
                    builder -> builder.persistent(SimpleFluidContent.CODEC).networkSynchronized(SimpleFluidContent.STREAM_CODEC));

    /**
     * The guide book's bookmark (issue #623): the {@code section.page} name of the page the book
     * was last closed on, written by {@code SavedBookPagePayload} when {@code BookScreen} closes
     * and read back by {@code BookOpener} so the book reopens where the reader left off. Upstream
     * 1.12 Mantle persists the identical string as the {@code mantle.book.page} NBT string
     * ({@code BookHelper#writeSavedPage} / {@code #getSavedPage}). Absent means no bookmark (the
     * cover); the string is a plain name with no registry binding, so a bookmark pointing at a
     * page that no longer exists still decodes and simply resolves to the cover at open time
     * ({@code SavedPage#find}).
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> BOOK_PAGE =
            DATA_COMPONENTS.registerComponentType("book_page",
                    builder -> builder.persistent(Codec.STRING)
                            .networkSynchronized(ByteBufCodecs.STRING_UTF8));

    private ForgeweaveDataComponents() {}
}
