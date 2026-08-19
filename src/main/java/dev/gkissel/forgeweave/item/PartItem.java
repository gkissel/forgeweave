package dev.gkissel.forgeweave.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.client.StationText;
import dev.gkissel.forgeweave.entity.IndestructibleItemEntity;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.material.MaterialDisplay;

/**
 * A tool part item (pickaxe head, tool binding, ...). Carries the material it was crafted from as
 * a {@link ForgeweaveDataComponents#MATERIAL} data component and shows that material's name, the
 * stats that material contributes <em>through this kind of part</em>, and its trait. Per-material
 * rendering is a client-side color tint (see {@code ForgeweaveItemColors}), not a distinct texture
 * per material.
 *
 * <h2>Hover stats (issue #64, NOTICE.md)</h2>
 *
 * <p>Upstream 1.12's {@code library/tools/ToolPart#addInformation} shows the part's traits always
 * and, behind Shift, only the stat blocks the part actually uses -- {@code hasUseForStat} filters
 * {@code material.getAllStats()} down by the {@code PartMaterialType}s that part appears in, so a
 * pickaxe head shows the Head block and a tool rod shows the Handle block. Forgeweave's parts each
 * appear in exactly one role, so {@link Kind} names that role at registration instead of being
 * derived from a recipe table, and the lines themselves come straight from {@link StationText} --
 * the same methods the Part Builder's info panel renders, so the two can't drift apart.
 *
 * <p>Issue #379 reverses the earlier omission of upstream's "Hold Shift for Stats" hint by
 * maintainer decision: both this item and {@code ToolItem} now close their compact tier with it,
 * gated on the same config the Shift tier reads (see {@link ToolTooltip#shiftHint}).
 *
 * <h2>Indestructible when dropped (issue #599)</h2>
 *
 * <p>Upstream 1.12 does <em>not</em> give tool parts this: {@code ToolPart extends MaterialItem}, not
 * {@code TinkersItem}, and neither {@code Shard} nor {@code SharpeningKit} (upstream's only two
 * {@code ToolPart} subclasses) adds {@code hasCustomEntity}/{@code createEntity} either -- #518
 * already verified this hierarchy when it made every dropped <em>tool</em> indestructible
 * ({@link ToolItem#hasCustomEntity}). This is therefore a deliberate deviation from 1.12 parity, by
 * maintainer decision recorded on issue #599: the playtest checklist (0.3.5-alpha.3, item 8.a)
 * promised parts survive lava/fire/explosions the same way tools do, and a part represents the same
 * material investment a tool does. {@link #hasCustomEntity}/{@link #createEntity} mirror {@link
 * ToolItem}'s pair exactly, so every {@code PartItem} instance is covered -- Forgeweave gives every
 * part role (including the sharpening kit) the same class rather than upstream's per-role subclasses.
 */
public class PartItem extends Item {

    /** Which part-stat block a part draws from ({@code PartMaterialType}). */
    public enum Kind {
        HEAD,
        HANDLE,
        /** Upstream's "extra" stat block: bindings, and anything else that only adds durability. */
        EXTRA,
        /** Issue #392: a bow limb, off the material's BOW block ({@code BowMaterialStats}). */
        BOW,
        /** Issue #392: a bow string, off the material's BOWSTRING block ({@code BowStringMaterialStats}). */
        BOWSTRING,
        /** Issue #626: an arrow shaft, off the material's SHAFT block ({@code ArrowShaftMaterialStats}). */
        SHAFT,
        /** Issue #626: a fletching, off the material's FLETCHING block ({@code FletchingMaterialStats}). */
        FLETCHING,
        /** No stat block of its own -- the shard, which is a leftover rather than a buildable part. */
        NONE
    }

    private final Kind kind;

    public PartItem(Properties properties) {
        this(properties, Kind.NONE);
    }

    public PartItem(Properties properties, Kind kind) {
        super(properties);
        this.kind = kind;
    }

    /** Which stat block -- and so which of a material's trait scopes -- this part draws from. */
    public Kind kind() {
        return kind;
    }

    /**
     * Whether {@code stack} is a tool part that can build nothing because its material is not one
     * this world defines -- either the component is missing outright or it names an id no loaded
     * datapack has (a shard from a pack that was since removed, a part from another world).
     *
     * <p>This is exactly the condition {@code ToolAssemblyRecipes#assemble} gives up on, and it gives
     * up silently: before issue #378 such a part left the station's output slot empty with every
     * component listed as satisfied. Upstream says it out loud in two places, and both ask this same
     * question -- {@code GuiToolStation}'s {@code gui.error.wrong_material_part} of an input slot and
     * {@code GuiPartBuilder}'s {@code gui.error.useless_tool_part} of the output -- so the question
     * is asked here once rather than once per station.
     */
    public static boolean hasUnusableMaterial(@Nullable HolderLookup.Provider registries, ItemStack stack) {
        if (!(stack.getItem() instanceof PartItem)) {
            return false;
        }
        ResourceLocation materialId = stack.get(ForgeweaveDataComponents.MATERIAL.get());
        if (materialId == null) {
            return true;
        }
        // Issue #392: "defined" is not enough once materials carry only some of the stat blocks --
        // a bowstring material has no head stats, so a pickaxe head stamped from it builds nothing.
        // Upstream asks exactly this (ToolPart#hasUseForStat) at the same two places.
        PartItem.Kind kind = ((PartItem) stack.getItem()).kind();
        return MaterialDisplay.lookup(registries, materialId)
                .filter(material -> material.hasStatsFor(kind))
                .isEmpty();
    }

    /**
     * {@code Stone Pickaxe Head} -- upstream {@code ToolPart#getItemStackDisplayName}
     * (ToolPart.java:190-204), issue #446. A part with no material component (the creative-tab and
     * JEI ghost stacks, and every {@code new ItemStack(part)} the station screens build a component
     * line from) keeps its plain name, which is upstream's {@code Material.UNKNOWN} branch.
     *
     * <p>Upstream first checks a per-material override key ({@code item.<part>.<material>.name});
     * its own {@code en_us.lang} defines none, so that branch is not ported -- it cannot be asked
     * server-side anyway (see {@link MaterialDisplay#prefixed}).
     *
     * <p>A renamed part still shows its custom name: {@code ItemStack#getHoverName} consults
     * {@code CUSTOM_NAME} and {@code ITEM_NAME} before ever reaching here.
     */
    @Override
    public Component getName(ItemStack stack) {
        ResourceLocation materialId = stack.get(ForgeweaveDataComponents.MATERIAL.get());
        Component name = super.getName(stack);
        return materialId == null ? name : MaterialDisplay.prefixed(List.of(materialId), name);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        append(stack, context.registries(), ToolTooltip.detailed(flag), tooltip);
        ToolTooltip.appendShiftHint(ToolTooltip.shiftHint(flag), tooltip);
    }

    /**
     * The hover lines, with {@code detailed} taken as a parameter rather than read from the Shift key
     * so unit tests can drive both tiers (same split as {@code ToolTooltip#append}).
     *
     * @param registries nullable -- without it the material can't be resolved, so the tooltip stays
     *     at the plain, uncolored material-name line
     */
    void append(ItemStack stack, @Nullable HolderLookup.Provider registries, boolean detailed,
            List<Component> tooltip) {
        // Issue #271: the sharpening kit is the one part that fits no tool, so without a line saying
        // what to do with it the item is a dead end. Upstream says the same thing in the same place
        // (SharpeningKit#addInformation leads with item.tconstruct.sharpening_kit.tooltip), and it
        // leads because it is the only reason to hold one.
        if (stack.is(ForgeweaveItems.PART_SHARPENING_KIT.get())) {
            tooltip.add(Component.translatable("tooltip.forgeweave.sharpening_kit"));
        }
        ResourceLocation materialId = stack.get(ForgeweaveDataComponents.MATERIAL.get());
        if (materialId == null) {
            // Issue #379, upstream ToolPart#checkMissingMaterialTooltip: a part carrying no material
            // at all says so ("Part has no data") instead of hovering as a blank item. Upstream's
            // errors are plain text, and they *replace* the trait block rather than joining it --
            // the return below is that suppression.
            tooltip.add(Component.translatable("tooltip.forgeweave.part.missing_info"));
            return;
        }
        tooltip.add(MaterialDisplay.name(registries, materialId));

        Optional<Material> material = MaterialDisplay.lookup(registries, materialId);
        if (material.isEmpty()) {
            // The same upstream branch's other half: the component names a material this world's
            // datapack does not define. Only when registries were actually available -- without them
            // nothing resolves and the plain name line above is the whole tooltip, as before.
            if (registries != null) {
                tooltip.add(Component.translatable("tooltip.forgeweave.part.missing_material",
                        materialId.toString()));
            }
            return;
        }
        List<Component> stats = detailed ? stats(material.get()) : List.of();
        if (!stats.isEmpty()) {
            tooltip.add(Component.empty());
            // Issue #379: upstream heads each stat block with its own white underlined type name
            // (ToolPart#getTooltipStatsInfo reading stat.<type>.name). Forgeweave parts each play
            // exactly one role, so the heading is this part's Kind -- and Kind.NONE never gets here,
            // since it contributes no stat lines for a heading to introduce.
            tooltip.add(Component.translatable("tooltip.forgeweave.stat_type." + kind.name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE));
            tooltip.addAll(stats);
        } else if (detailed && kind != Kind.NONE && !material.get().hasStatsFor(kind)) {
            // Parity audit T81 (issue #512): upstream's ToolPart#checkMissingMaterialTooltip(stack,
            // tooltip, statIdentifier) overload says so explicitly ("Material is missing the required
            // stats") instead of a silently empty stat section -- SharpeningKit is upstream's only
            // caller of that overload (checking MaterialTypes.HEAD), but Forgeweave's single PartItem
            // class stands in for every one of upstream's per-role ToolPart subtypes, so the same
            // check generalizes to whichever Kind this part is (issue #392 is what made a material
            // missing a whole stat block reachable in the first place: a bowstring-only material
            // stamped into a bow limb, or -- the sharpening kit's own case -- one with no head block).
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.forgeweave.part.missing_stats",
                    MaterialDisplay.name(registries, materialId)));
        }
        tooltip.add(Component.empty());
        // The traits this material grants through this kind of part, which is upstream's own filter
        // (ToolPart#getTooltipTraitInfo passes the part's stat type to getAllTraitsForStats). One
        // line per trait since issue #376, which is also what upstream's part tooltip shows -- names
        // only, no descriptions (the descriptions are hover text, which an item tooltip cannot show).
        tooltip.addAll(StationText.traits(material.get().color(), material.get().traits().forPart(kind)));
    }

    /**
     * The stat block this part draws from its material. Package-private since issue #380:
     * {@code ToolTooltip}'s per-part Shift sections show the same block for the part in each slot,
     * so the two views of "what this part contributes" stay one method.
     */
    List<Component> stats(Material material) {
        return switch (kind) {
            case HEAD -> headStats(material);
            case HANDLE -> StationText.handleStats(material);
            case EXTRA -> StationText.extraStats(material);
            case BOW -> StationText.bowStats(material);
            case BOWSTRING -> StationText.bowstringStats(material);
            case SHAFT -> StationText.shaftStats(material);
            case FLETCHING -> StationText.fletchingStats(material);
            case NONE -> List.of();
        };
    }

    /**
     * The head stat block plus the tool tier the material grants (issue #254) -- heads are what
     * decide a tool's mining capability, so the tier sits with the other head stats behind Shift,
     * in the same {@code Tool Tier: X} shape {@code ToolTooltip} shows for an assembled tool. A
     * material whose {@code incorrect_for_tool} tag is off the vanilla ladder (gold, modded) gets
     * no tier line rather than a guess.
     */
    private static List<Component> headStats(Material material) {
        List<Component> lines = new ArrayList<>(StationText.headStats(material));
        ToolTooltip.tierLine(material.incorrectForTool()).ifPresent(lines::add);
        return lines;
    }

    /** See the "Indestructible when dropped" class javadoc, and {@link ToolItem#hasCustomEntity}. */
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
}
