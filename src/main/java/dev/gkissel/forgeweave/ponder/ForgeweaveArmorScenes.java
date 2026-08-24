package dev.gkissel.forgeweave.ponder;

import java.util.List;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.menu.ToolAssemblyRecipes;
import dev.gkissel.forgeweave.tool.ToolConstants;

/**
 * The armor assembly scene (M4-7, issue #682; docs/SCOPE.md D21): a Tool Station on the base plate
 * ({@code assets/forgeweave/ponder/tool_station.nbt}, {@code scripts/generate_ponder_schematics.py}),
 * the chestplate's two parts shown going in, the finished chestplate coming out, and an armor stand
 * wearing it -- Ponder's own way of showing a worn item, since scenes have no player. The piece is
 * built through {@link ToolAssemblyRecipes#assemble}, the station's real call, so the stand wears
 * the iron-tinted two-layer render and not a staged stack.
 *
 * <p>The inline English strings are Ponder's localization idiom (see
 * {@link ForgeweaveSmelteryScenes}): each registers a {@code forgeweave.ponder.armor.*} lang key that
 * {@code ForgeweaveLanguageProvider} extracts through {@code PonderIndex.getLangAccess()}.
 */
public final class ForgeweaveArmorScenes {

    /** Centre of the 5x5 plate. */
    private static final BlockPos STATION = new BlockPos(2, 1, 2);

    /** Where the armor stand appears: west of the station, facing the default camera. */
    private static final BlockPos STAND = new BlockPos(0, 1, 2);

    private static final ResourceLocation IRON = ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, "iron");

    public static void assembly(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("armor", "Assembling Armor");
        scene.configureBasePlate(0, 0, 5);
        scene.showBasePlate();
        scene.idle(10);

        scene.world().showSection(util.select().position(STATION), Direction.DOWN);
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Armor is assembled at the Tool Station or Tool Forge, like any tool")
                .pointAt(util.vector().topOf(STATION))
                .placeNearTarget();
        scene.idle(80);

        ItemStack plating = new ItemStack(ForgeweaveItems.PART_PLATING_CHESTPLATE.get());
        plating.set(ForgeweaveDataComponents.MATERIAL.get(), IRON);
        scene.overlay().showControls(util.vector().blockSurface(STATION, Direction.SOUTH), Pointing.RIGHT, 50)
                .withItem(plating);
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("Each piece takes a plating shaped for it, which sets all of its stats")
                .pointAt(util.vector().blockSurface(STATION, Direction.SOUTH))
                .placeNearTarget();
        scene.idle(80);

        ItemStack maille = new ItemStack(ForgeweaveItems.PART_MAILLE.get());
        maille.set(ForgeweaveDataComponents.MATERIAL.get(), IRON);
        scene.overlay().showControls(util.vector().blockSurface(STATION, Direction.SOUTH), Pointing.RIGHT, 50)
                .withItem(maille);
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("And a maille, which adds its material's traits and shows through the plating")
                .pointAt(util.vector().blockSurface(STATION, Direction.SOUTH))
                .placeNearTarget();
        scene.idle(80);

        scene.overlay().showControls(util.vector().topOf(STATION), Pointing.DOWN, 50)
                .withItem(new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get()));
        scene.effects().indicateSuccess(STATION);
        scene.idle(10);
        scene.overlay().showText(70)
                .attachKeyFrame()
                .text("The station builds the piece from the two parts")
                .pointAt(util.vector().topOf(STATION))
                .placeNearTarget();
        scene.idle(80);

        scene.world().createEntity(ForgeweaveArmorScenes::wearingStand);
        scene.idle(10);
        scene.overlay().showText(90)
                .attachKeyFrame()
                .text("Worn, the plating shows in its material's colour over the maille. Its stats, traits and modifiers only work while it is on")
                .pointAt(util.vector().topOf(STAND))
                .placeNearTarget();
        scene.idle(100);

        scene.markAsFinished();
    }

    /** An armor stand at {@link #STAND} wearing an iron/iron chestplate assembled the station's way. */
    private static ArmorStand wearingStand(Level level) {
        ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
        stand.setPos(STAND.getX() + 0.5, STAND.getY(), STAND.getZ() + 0.5);
        stand.setYRot(180.0F);
        stand.setYBodyRot(180.0F);
        stand.setYHeadRot(180.0F);
        ToolAssemblyRecipes.entryFor(new ItemStack(ForgeweaveItems.ARMOR_CHESTPLATE.get()))
                .flatMap(entry -> ToolAssemblyRecipes.assemble(level.registryAccess(), entry, List.of(IRON, IRON)))
                .ifPresent(piece -> stand.setItemSlot(EquipmentSlot.CHEST, piece));
        return stand;
    }

    private ForgeweaveArmorScenes() {}
}
