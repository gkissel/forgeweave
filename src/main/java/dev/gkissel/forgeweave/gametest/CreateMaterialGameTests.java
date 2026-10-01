package dev.gkissel.forgeweave.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.item.ArmorPieceItem;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.CreateMaterialTraits;

@GameTestHolder("forgeweave")
@PrefixGameTestTemplate(false)
public final class CreateMaterialGameTests {
    @GameTest(template = "empty")
    public static void createMaterialsUseTheirAgreedCraftingStations(GameTestHelper helper) {
        for (String name : new String[] {"zinc", "brass", "andesite_alloy", "rose_quartz"}) {
            boolean metal = name.equals("zinc") || name.equals("brass");
            ResourceLocation item = ResourceLocation.fromNamespaceAndPath("create", name + (metal ? "_ingot" : ""));
            ResourceLocation material = ResourceLocation.fromNamespaceAndPath("forgeweave", name);
            var materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
            if (!BuiltInRegistries.ITEM.containsKey(item)) {
                helper.assertTrue(!materials.containsKey(material), name + " must be absent without its Create item");
                continue;
            }
            helper.assertTrue(materials.containsKey(material), name + " must load with Create");
            BlockPos pos = new BlockPos(1, 1, 1);
            helper.setBlock(pos, ForgeweaveBlocks.PART_BUILDER.get());
            PartBuilderBlockEntity block = helper.getBlockEntity(pos);
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), block.container(),
                    ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(pos)), block.findSideInventory());
            menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
            menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(new ItemStack(BuiltInRegistries.ITEM.get(item), 16));
            menu.broadcastChanges();
            ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
            helper.assertTrue(metal ? output.isEmpty() : material.equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                    name + (metal ? " must require casting" : " must craft in the Part Builder"));
            menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_PLATING_CHESTPLATE.get()));
            menu.broadcastChanges();
            output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
            helper.assertTrue(metal ? output.isEmpty() : material.equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                    name + " chest plating must use the same station as its tool parts");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void industrialOnlyAcceleratesStoneOreReplaceableBlocks(GameTestHelper helper) {
        var trait = CreateMaterialTraits.INDUSTRIAL;
        float stone = trait.breakSpeed(ItemStack.EMPTY, null, Blocks.STONE.defaultBlockState(), 10, 10);
        float dirt = trait.breakSpeed(ItemStack.EMPTY, null, Blocks.DIRT.defaultBlockState(), 10, 10);
        helper.assertTrue(Math.abs(stone - 11.5F) < 0.001F && dirt == 10.0F,
                "Industrial must add 15% on stone and leave dirt unchanged");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void galvanizedGuardCountsWornPiecesAndExcludesBrokenArmor(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var slots = List.of(EquipmentSlot.HEAD,
                EquipmentSlot.CHEST, EquipmentSlot.LEGS,
                EquipmentSlot.FEET);
        var pieces = List.of(ToolConstants.HELMET,
                ToolConstants.CHESTPLATE, ToolConstants.LEGGINGS,
                ToolConstants.BOOTS);
        for (int i = 0; i < slots.size(); i++) {
            ItemStack piece = ToolAssembly.assembleAt(helper, player, new BlockPos(1, 1, 1),
                    ForgeweaveBlocks.TOOL_STATION.get(), ToolAssembly.entryOf(pieces.get(i)), List.of("iron", "iron"));
            piece.set(ForgeweaveDataComponents.TRAITS.get(), List.of(
                    ResourceLocation.fromNamespaceAndPath("forgeweave", "galvanized_guard")));
            player.setItemSlot(slots.get(i), piece);
            float chance = ArmorPieceItem.durabilityNegationChance(player);
            helper.assertTrue(Math.abs(chance - (i + 1) * 0.05F) < 0.001F,
                    "each worn piece must contribute 5%, up to 20%");
        }
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        boots.set(ForgeweaveDataComponents.BROKEN.get(), true);
        helper.assertTrue(Math.abs(ArmorPieceItem.durabilityNegationChance(player) - 0.15F) < 0.001F,
                "broken boots must not contribute");
        int loss = CreateMaterialTraits.GALVANIZED.durabilityDamage(
                boots, RandomSource.create(1), 3, 3);
        helper.assertTrue(loss == 3, "a zinc backing plate must not grant the tool's 15% saving to armor");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void focusedIgnoresArmorPointsThroughTheRealDamagePipeline(GameTestHelper helper) {
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack sword = ToolAssembly.assembleAt(helper, player, new BlockPos(1, 1, 1),
                ForgeweaveBlocks.TOOL_STATION.get(), ToolAssembly.entryOf(ToolConstants.BROADSWORD),
                List.of("iron", "iron", "iron"));
        sword.set(ForgeweaveDataComponents.TRAITS.get(), List.of(
                ResourceLocation.fromNamespaceAndPath("forgeweave", "focused")));
        var attacker = helper.spawn(EntityType.ZOMBIE, new BlockPos(2, 2, 2));
        var target = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 2));
        attacker.setNoAi(true);
        target.setNoAi(true);
        attacker.setItemSlot(EquipmentSlot.MAINHAND, sword);
        target.getAttribute(Attributes.ARMOR).setBaseValue(20);
        target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(4);
        var source = helper.getLevel().damageSources().mobAttack(attacker);
        float expected = CombatRules.getDamageAfterAbsorb(target, 4, source, 18, 4);
        float before = target.getHealth();
        target.hurt(source, 4);
        helper.assertTrue(Math.abs(before - target.getHealth() - expected) < 0.01F,
                "Focused must use 18 of 20 armor points while retaining 4 toughness; expected " + expected
                        + ", got " + (before - target.getHealth()));
        helper.succeed();
    }

    private CreateMaterialGameTests() {}
}
