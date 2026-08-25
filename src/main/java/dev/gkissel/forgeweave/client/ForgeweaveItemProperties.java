package dev.gkissel.forgeweave.client;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.config.ForgeweaveClientConfig;
import dev.gkissel.forgeweave.config.HeldBowPose;
import dev.gkissel.forgeweave.item.BowItem;
import dev.gkissel.forgeweave.item.CrossbowItem;
import dev.gkissel.forgeweave.item.ToolItem;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;

/**
 * The {@code forgeweave:broken} item property every assembled tool's model branches on (issue #284):
 * 1 once the tool's {@code BROKEN} component is set, 0 otherwise. {@code
 * ForgeweaveItemModelProvider} writes the matching {@code overrides} entry, which points at the
 * tool's {@code <tool>_broken} model -- the same layers, with the one layer {@code
 * ToolArt#brokenLayer} names swapped for its broken art.
 *
 * <p>Upstream 1.12 does the swap inside its own tool model instead ({@code BakedToolModel#
 * getOverrides} picks per-part broken quads on {@code ToolHelper#isBroken}), which vanilla's model
 * format cannot express; this is the mechanism upstream itself adopted once it was on a modern
 * Minecraft ({@code TinkerItemProperties#registerBrokenProperty} in the 1.20 clone).
 *
 * <p>Registered off {@link ToolAssemblyRecipes#ENTRIES} rather than a hand list, for the same reason
 * {@link ForgeweaveItemColors#tintedToolItems} is: a tool cannot be added to the station and left
 * out here.
 *
 * <p>M3.5 issue #400 added the bows' three: {@code minecraft:pulling} and {@code minecraft:pull} on
 * every {@link BowItem}, and {@code forgeweave:loaded} on the crossbow. Same mechanism, same
 * provider on the other side -- the difference is only that a bow branches four ways (undrawn plus
 * three pull stages) instead of two, and that the values come from the holder rather than from the
 * stack alone. All three are pure static methods here so a unit test can pin them without a client.
 */
@EventBusSubscriber(modid = Forgeweave.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ForgeweaveItemProperties {

    /** The property id; {@code ForgeweaveItemModelProvider.BROKEN_PREDICATE} is its other half. */
    private static final ResourceLocation BROKEN =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "broken");

    /**
     * The two draw-stage properties (M3.5 issue #400), in <em>vanilla's</em> namespace rather than
     * Forgeweave's: {@code pulling} and {@code pull} are the names vanilla's own bow models use for
     * exactly this concept, upstream 1.12 registered the same two unqualified
     * ({@code BowCore#PROPERTY_IS_PULLING}/{@code #PROPERTY_PULL_PROGRESS}), and a resource pack
     * re-skinning a Forgeweave bow will reach for them. {@link #BROKEN} stays namespaced because
     * "this tool is Broken" is a Forgeweave concept vanilla has no word for.
     */
    private static final ResourceLocation PULLING = ResourceLocation.withDefaultNamespace("pulling");
    private static final ResourceLocation PULL = ResourceLocation.withDefaultNamespace("pull");

    /**
     * The crossbow's loaded flag ({@code CrossBow#PROPERTY_IS_LOADED}). Namespaced, unlike the two
     * above: vanilla's word for a crossbow holding a shot is {@code charged}, and it means a
     * different thing -- vanilla's crossbow stores the projectile it will fire, while upstream's (and
     * so Forgeweave's) stores nothing but a boolean and finds its ammo at fire time.
     */
    private static final ResourceLocation LOADED =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "loaded");

    /**
     * Issue #723: the held-pose switch. Every bow model carries a second, {@code modern_pose}-gated
     * ladder of its states pointing at {@code *_modern} siblings ({@code
     * ForgeweaveItemModelProvider}); this is what makes it fire. A model property rather than a
     * baked-model swap because it needs no resource reload and no wrapping of the override tree.
     */
    private static final ResourceLocation MODERN_POSE =
            ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "modern_pose");

    @SubscribeEvent
    static void registerItemProperties(FMLClientSetupEvent event) {
        // ItemProperties' map is not thread-safe; enqueueWork is the documented way onto the main thread.
        event.enqueueWork(() -> {
            for (ToolAssemblyRecipes.Entry entry : ToolAssemblyRecipes.ENTRIES) {
                Item tool = entry.tool().get();
                ItemProperties.register(tool, BROKEN, (stack, level, holder, seed) -> broken(stack));
                if (tool instanceof BowItem) {
                    ItemProperties.register(tool, PULLING, (stack, level, holder, seed) -> pulling(stack, holder));
                    ItemProperties.register(tool, PULL, (stack, level, holder, seed) -> pull(stack, holder));
                    ItemProperties.register(tool, MODERN_POSE, (stack, level, holder, seed) -> modernPose(
                            ForgeweaveClientConfig.SPEC.isLoaded()
                                    ? ForgeweaveClientConfig.HELD_BOW_POSE.get()
                                    : HeldBowPose.DEFAULT));
                }
                if (tool instanceof CrossbowItem) {
                    ItemProperties.register(tool, LOADED, (stack, level, holder, seed) -> loaded(stack));
                }
            }
        });
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
