package dev.gkissel.forgeweave.client;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.client.renderer.item.properties.numeric.RangeSelectItemModelProperty;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent;
import net.neoforged.neoforge.client.event.RegisterRangeSelectItemModelPropertyEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveClientConfig;
import dev.gkissel.forgeweave.config.HeldBowPose;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.CrossbowItem;
import dev.gkissel.forgeweave.item.ToolItem;

/**
 * The {@code forgeweave:broken} item property every assembled tool's model branches on (issue #284):
 * 1 once the tool's {@code BROKEN} component is set, 0 otherwise. {@code
 * ForgeweaveItemModelProvider} writes the matching {@code condition} node, whose true branch is the
 * tool's {@code <tool>_broken} model -- the same layers, with the one layer {@code
 * ToolArt#brokenLayer} names swapped for its broken art.
 *
 * <p>Upstream 1.12 does the swap inside its own tool model instead ({@code BakedToolModel#
 * getOverrides} picks per-part broken quads on {@code ToolHelper#isBroken}), which vanilla's model
 * format cannot express; this is the mechanism upstream itself adopted once it was on a modern
 * Minecraft ({@code TinkerItemProperties#registerBrokenProperty} in the 1.20 clone).
 *
 * <p>On 26.1 these are model properties any client item can test rather than per-item
 * registrations, so no tool can be left out of them.
 *
 * <p>M3.5 issue #400 added the bows' three: {@code forgeweave:pulling} and {@code forgeweave:pull} (1.21.1's
 * {@code minecraft:pulling}/{@code minecraft:pull}) on every {@link BowItem}, and {@code forgeweave:loaded} on the crossbow. Same mechanism, same
 * provider on the other side -- the difference is only that a bow branches four ways (undrawn plus
 * three pull stages) instead of two, and that the values come from the holder rather than from the
 * stack alone. All three are pure static methods here so a unit test can pin them without a client.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, value = Dist.CLIENT)
public final class ForgeweaveItemProperties {

    /**
     * The tool model's on/off properties, one id each: {@code forgeweave:broken} (issue #284),
     * {@code forgeweave:pulling} (M3.5 issue #400, vanilla's {@code pulling} under Forgeweave's
     * name -- 26.1 has no free-standing {@code minecraft:pulling} property to share any more),
     * {@code forgeweave:loaded} (the crossbow's crank, {@code CrossBow#PROPERTY_IS_LOADED};
     * vanilla's word {@code charged} means a different thing) and {@code forgeweave:modern_pose}
     * (issue #723's held-pose switch, a model property so it needs no resource reload).
     * {@code ForgeweaveItemModelProvider} writes the {@code condition} nodes that test them.
     */
    public enum Flag implements ConditionalItemModelProperty {
        BROKEN("broken"),
        PULLING("pulling"),
        LOADED("loaded"),
        MODERN_POSE("modern_pose");

        private final Identifier id;
        private final MapCodec<Flag> codec = MapCodec.unit(this);

        Flag(String path) {
            this.id = Identifier.fromNamespaceAndPath(Forgeweave.MODID, path);
        }

        public Identifier id() {
            return id;
        }

        @Override
        public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
                ItemDisplayContext displayContext) {
            return switch (this) {
                case BROKEN -> broken(stack) > 0.0F;
                case PULLING -> pulling(stack, owner) > 0.0F;
                case LOADED -> loaded(stack) > 0.0F;
                case MODERN_POSE -> modernPose(ForgeweaveClientConfig.SPEC.isLoaded()
                        ? ForgeweaveClientConfig.HELD_BOW_POSE.get()
                        : HeldBowPose.DEFAULT) > 0.0F;
            };
        }

        @Override
        public MapCodec<Flag> type() {
            return codec;
        }
    }

    /** {@code forgeweave:pull}: the draw progress the {@code range_dispatch} node picks a pull stage by (#400). */
    public enum Pull implements RangeSelectItemModelProperty {
        INSTANCE;

        public static final Identifier ID = Identifier.fromNamespaceAndPath(Forgeweave.MODID, "pull");
        private static final MapCodec<Pull> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public float get(ItemStack stack, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
            return pull(stack, owner == null ? null : owner.asLivingEntity());
        }

        @Override
        public MapCodec<Pull> type() {
            return CODEC;
        }
    }

    @SubscribeEvent
    static void registerConditionalProperties(RegisterConditionalItemModelPropertyEvent event) {
        for (Flag flag : Flag.values()) {
            event.register(flag.id(), flag.type());
        }
    }

    @SubscribeEvent
    static void registerRangeProperties(RegisterRangeSelectItemModelPropertyEvent event) {
        event.register(Pull.ID, Pull.INSTANCE.type());
    }

    /** {@code forgeweave:broken}: 1 once the tool's {@code BROKEN} component is set. */
    static float broken(ItemStack stack) {
        return ToolItem.isBroken(stack) ? 1.0F : 0.0F;
    }

    /**
     * {@code minecraft:pulling}, {@code BowCore}'s {@code isPullingPropertyGetter} verbatim: 1 only
     * while {@code holder} is mid-use on <em>this very stack</em>. Nothing is being drawn when there
     * is no holder at all (an item frame, a JEI slot), so the bow renders undrawn there.
     */
    static float pulling(ItemStack stack, @Nullable LivingEntity holder) {
        return holder != null && holder.isUsingItem() && holder.getUseItem() == stack ? 1.0F : 0.0F;
    }

    /**
     * {@code minecraft:pull}, {@code BowCore}'s {@code pullProgressPropertyGetter}: how far along the
     * draw is, 0 to 1. {@link BowItem#drawbackProgress(ItemStack, LivingEntity)} carries the same
     * "only if this holder is drawing this stack" guard, so this is 0 whenever {@link #pulling} is.
     */
    static float pull(ItemStack stack, @Nullable LivingEntity holder) {
        return holder != null && stack.getItem() instanceof BowItem bow
                ? bow.drawbackProgress(stack, holder)
                : 0.0F;
    }

    /**
     * {@code forgeweave:loaded}: 1 while the crossbow holds a crank.
     *
     * <p>Deviation from 1.12, deliberate: upstream's getter is {@code entityIn != null &&
     * isLoaded(stack)}. The state lives entirely on the stack, so the holder tells it nothing, and
     * dropping the guard is what makes a loaded crossbow read as loaded in an item frame, a JEI slot
     * or any other holder-less render instead of silently showing an uncranked one.
     */
    static float loaded(ItemStack stack) {
        return CrossbowItem.isLoaded(stack) ? 1.0F : 0.0F;
    }

    /** {@code forgeweave:modern_pose}: 1 under the {@code modern} client setting, 0 under {@code classic}. */
    static float modernPose(HeldBowPose pose) {
        return pose == HeldBowPose.MODERN ? 1.0F : 0.0F;
    }

    private ForgeweaveItemProperties() {}
}
