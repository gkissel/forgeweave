package dev.gkissel.forgeweave.gametest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.material.Fluid;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import dev.gkissel.forgeweave.Forgeweave;
import dev.gkissel.forgeweave.block.CastingBlockEntity;
import dev.gkissel.forgeweave.block.FaucetBlock;
import dev.gkissel.forgeweave.block.FaucetBlockEntity;
import dev.gkissel.forgeweave.block.ForgeweaveBlocks;
import dev.gkissel.forgeweave.block.PartBuilderBlockEntity;
import dev.gkissel.forgeweave.block.SearedTankBlockEntity;
import dev.gkissel.forgeweave.block.SmelteryControllerBlockEntity;
import dev.gkissel.forgeweave.fluid.ForgeweaveFluids;
import dev.gkissel.forgeweave.item.ForgeweaveDataComponents;
import dev.gkissel.forgeweave.item.ForgeweaveItems;
import dev.gkissel.forgeweave.item.PartItem;
import dev.gkissel.forgeweave.material.Material;
import dev.gkissel.forgeweave.menu.PartBuilderMenu;
import dev.gkissel.forgeweave.menu.StationMenu;
import dev.gkissel.forgeweave.tool.ToolConstants;
import dev.gkissel.forgeweave.trait.ForgeweaveTraits;

/**
 * Issue #843 (closes #180): the 1.20-branch material gap's five by-name authorized materials
 * (seared stone, necrotic bone, queen's slime, hepatizon, slimewood) and the four new trait
 * behaviors their material JSONs grant (overgrowth, overlord, restore, recurrent_protection).
 * Follows {@code ModernMaterialGameTests}' shape for the smeltery/casting mechanics and {@code
 * ArmorTraitGameTests}'/{@code StatefulTraitGameTests}' shape for the trait behaviors.
 */
@GameTestHolder(Forgeweave.MODID)
@PrefixGameTestTemplate(false)
public class M615MaterialGameTests {
    private static final BlockPos TANK = new BlockPos(1, 3, 1);
    private static final BlockPos FAUCET = new BlockPos(2, 3, 1);
    private static final BlockPos CASTING = new BlockPos(2, 2, 1);
    private static final BlockPos STATION = new BlockPos(1, 1, 1);
    private static final float BLOW = 4.0F;

    /** The audit's sourceability table: cobalt + gold + magma cream, the clone's exact 90:90:250 -> 180 ratio. */
    @GameTest(template = "smeltery")
    public static void cobaltGoldAndMagmaCreamAlloyIntoQueensSlime(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        core.tank().fill(new FluidStack(ForgeweaveFluids.COBALT.still().get(), 90), IFluidHandler.FluidAction.EXECUTE);
        core.tank().fill(new FluidStack(ForgeweaveFluids.GOLD.still().get(), 90), IFluidHandler.FluidAction.EXECUTE);
        core.tank().fill(new FluidStack(ForgeweaveFluids.MAGMA_CREAM.still().get(), 250), IFluidHandler.FluidAction.EXECUTE);

        helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left in the tank");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.QUEENS_SLIME.still().get(),
                "expected molten queen's slime, the tank holds " + core.tank().getFluid().getFluid());
        helper.assertValueEqual(core.tank().getFluidAmount(), 180, "queen's slime in the tank");
        helper.succeed();
    }

    /** Copper x2 + cobalt + quartz, the clone's exact 180:90:100 -> 180 ratio. */
    @GameTest(template = "smeltery")
    public static void copperCobaltAndQuartzAlloyIntoHepatizon(GameTestHelper helper) {
        SmelteryControllerBlockEntity core = smeltery(helper);
        core.tank().fill(new FluidStack(ForgeweaveFluids.COPPER.still().get(), 180), IFluidHandler.FluidAction.EXECUTE);
        core.tank().fill(new FluidStack(ForgeweaveFluids.COBALT.still().get(), 90), IFluidHandler.FluidAction.EXECUTE);
        core.tank().fill(new FluidStack(ForgeweaveFluids.QUARTZ.still().get(), 100), IFluidHandler.FluidAction.EXECUTE);

        helper.assertValueEqual(core.tank().fluids().size(), 1, "distinct fluids left in the tank");
        helper.assertTrue(core.tank().getFluid().getFluid() == ForgeweaveFluids.HEPATIZON.still().get(),
                "expected molten hepatizon, the tank holds " + core.tank().getFluid().getFluid());
        helper.assertValueEqual(core.tank().getFluidAmount(), 180, "hepatizon in the tank");
        helper.succeed();
    }

    /** Seared stone -&gt; a pickaxe head cast directly from the existing molten_seared_stone fluid. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void searedStoneCastsAPickaxeHeadFromTheExistingFluid(GameTestHelper helper) {
        assertPartCasts(helper, ForgeweaveFluids.SEARED_STONE.still().get(), "seared_stone");
    }

    /** Slimewood -&gt; a pickaxe head cast directly from the existing molten_slime fluid. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void slimewoodCastsAPickaxeHeadFromMoltenSlime(GameTestHelper helper) {
        assertPartCasts(helper, ForgeweaveFluids.SLIME.still().get(), "slimewood");
    }

    /** Queen's slime -&gt; its ingot casts from the alloy fluid, proving the alloy is actually usable. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void queensSlimeCastsIntoAnIngot(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.QUEENS_SLIME.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> helper.assertTrue(table.output().is(ForgeweaveItems.INGOT_QUEENS_SLIME.get()),
                "expected a queen's slime ingot in the output slot, found " + table.output()));
    }

    /** Hepatizon -&gt; its ingot casts from the alloy fluid. */
    @GameTest(template = "empty", timeoutTicks = 600)
    public static void hepatizonCastsIntoAnIngot(GameTestHelper helper) {
        CastingBlockEntity table = rig(helper, ForgeweaveFluids.HEPATIZON.still().get());
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_INGOT.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> helper.assertTrue(table.output().is(ForgeweaveItems.INGOT_HEPATIZON.get()),
                "expected a hepatizon ingot in the output slot, found " + table.output()));
    }

    /**
     * Necrotic bone's Part Builder obtainability path (the loose end the issue names): the wither
     * skeleton drop is already a Part Builder input by weight (its material JSON crafting_items,
     * value 144 = one ingot-unit), so two of them exactly cover a pickaxe head like any other
     * ingot-tier material (see {@code PartBuilderGameTests#patternAndMaterialProduceMatchingPart}).
     */
    @GameTest(template = "empty")
    public static void necroticBoneBuildsAPickaxeHeadAtThePartBuilder(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.setBlock(STATION, ForgeweaveBlocks.PART_BUILDER.get());
        PartBuilderBlockEntity blockEntity = helper.getBlockEntity(STATION);
        PartBuilderMenu menu = new PartBuilderMenu(0, player.getInventory(), blockEntity.container(),
                net.minecraft.world.inventory.ContainerLevelAccess.create(helper.getLevel(), helper.absolutePos(STATION)),
                blockEntity.findSideInventory());

        menu.getSlot(PartBuilderMenu.PATTERN_SLOT).set(new ItemStack(ForgeweaveItems.PATTERN_PICKAXE_HEAD.get()));
        menu.getSlot(PartBuilderMenu.MATERIAL_SLOT).set(new ItemStack(ForgeweaveItems.NECROTIC_BONE.get(), 2));
        menu.broadcastChanges();

        ItemStack output = menu.getSlot(PartBuilderMenu.OUTPUT_SLOT).getItem();
        helper.assertTrue(output.is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                "expected a pickaxe head part, got " + output);
        helper.assertTrue(materialId("necrotic_bone").equals(output.get(ForgeweaveDataComponents.MATERIAL.get())),
                "expected the pickaxe head's material to be forgeweave:necrotic_bone, got "
                        + output.get(ForgeweaveDataComponents.MATERIAL.get()));
        helper.succeed();
    }

    /** All five materials' trait wiring, read off the same synced registry the Tool Station resolves from. */
    @GameTest(template = "empty")
    public static void newMaterialsExposeTheirTraitWiring(GameTestHelper helper) {
        Registry<Material> materials = helper.getLevel().registryAccess().registryOrThrow(Material.REGISTRY);
        Map<String, String> general = Map.of(
                "seared_stone", "searing",
                "necrotic_bone", "necrotic",
                "queens_slime", "overlord|overslime",
                "hepatizon", "momentum",
                "slimewood", "overgrowth|overslime");
        Map<String, String> armor = Map.of(
                "seared_stone", "fire_protection",
                "necrotic_bone", "restore",
                "hepatizon", "recurrent_protection");

        general.forEach((name, trait) -> {
            Material material = materials.get(materialId(name));
            helper.assertTrue(material != null, name + " should be in the synced material registry");
            List<ResourceLocation> expectedGeneral = split(trait);
            helper.assertTrue(expectedGeneral.equals(material.traits().general()),
                    name + "'s general traits should be " + expectedGeneral + ", got " + material.traits().general());
            for (PartItem.Kind kind : PartItem.Kind.values()) {
                boolean armorPart = kind == PartItem.Kind.PLATING || kind == PartItem.Kind.MAILLE;
                List<ResourceLocation> expected = armorPart && armor.containsKey(name)
                        ? split(armor.get(name)) : expectedGeneral;
                helper.assertTrue(expected.equals(material.traits().forPart(kind)),
                        name + " through a " + kind + " part should grant " + expected
                                + ", got " + material.traits().forPart(kind));
            }
        });
        helper.succeed();
    }

    /** Slimewood -&gt; overgrowth: a 5% chance each second of regenerating one point of overslime. */
    @GameTest(template = "empty")
    public static void overgrowthRegeneratesOverslimeOverTime(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack pickaxe = pickaxe(List.of(traitId("overgrowth"), traitId("overslime")), 100, 2.0F, 1.0F);

        helper.assertTrue(ForgeweaveTraits.overslime(pickaxe) == 0, "a fresh piece has no overslime");
        // P(no proc in 300 one-second steps at 5%) = 0.95^300 ~ 1.3e-7, the thorns-test bar.
        for (int i = 1; i <= 300 && ForgeweaveTraits.overslime(pickaxe) == 0; i++) {
            player.tickCount = i * 20;
            pickaxe.getItem().inventoryTick(pickaxe, level, player, 0, false);
        }
        helper.assertTrue(ForgeweaveTraits.overslime(pickaxe) > 0, "overgrowth must have regenerated overslime at least once");
        helper.succeed();
    }

    /** Queen's slime -&gt; overlord: -15% head durability, folded straight into ToolStats' formula. */
    @GameTest(template = "empty")
    public static void overlordReducesHeadDurability(GameTestHelper helper) {
        int adjusted = ForgeweaveTraits.OVERLORD.headDurability(1000);
        helper.assertTrue(adjusted == 850, "expected 1000 * 0.85 = 850, got " + adjusted);
        helper.succeed();
    }

    /** Necrotic bone -&gt; restore: a 15% chance on taking damage to heal 25% of it back, spending 1 durability. */
    @GameTest(template = "empty")
    public static void restoreHealsAPortionOfDamageTaken(GameTestHelper helper) {
        // necrotic_bone has no plating stats (upstream's SHIELD_CORE, not PLATING -- audit table),
        // same as bone: iron plating, necrotic_bone maille (ArmorTraitGameTests' bone precedent).
        Player player = wearing(helper, "iron", "necrotic_bone");
        ItemStack piece = worn(player);
        piece.setDamageValue(0);
        player.setHealth(player.getMaxHealth() - 8.0F);
        player.invulnerableTime = 0;

        DamageSource explosion = explosion(helper);
        boolean healedOrDamaged = false;
        // P(no proc in 200 hits at 15%) = 0.85^200 ~ 4e-15.
        for (int i = 0; i < 200 && !healedOrDamaged; i++) {
            player.setHealth(player.getMaxHealth() - 8.0F);
            player.invulnerableTime = 0;
            float before = player.getHealth();
            player.hurt(explosion, BLOW);
            healedOrDamaged = player.getHealth() > before - BLOW || piece.getDamageValue() > 0;
        }
        helper.assertTrue(healedOrDamaged, "restore must have healed and worn the piece at least once in 200 hits");
        helper.succeed();
    }

    /** Hepatizon -&gt; recurrent_protection: half of a blow's damage is removed as flat reduction for that hit. */
    @GameTest(template = "empty")
    public static void recurrentProtectionHalvesTheBlow(GameTestHelper helper) {
        Player player = wearing(helper, "hepatizon", "hepatizon");
        float without = lostWithoutTraits(player, explosion(helper), BLOW);
        float with = lost(player, explosion(helper), BLOW);
        // flatReduction is floored at 1 and applied after armor/protection (DefendedBlow javadoc).
        float expected = Math.max(0.0F, without - Math.max(1.0F, 0.5F * without));
        helper.assertTrue(Math.abs(with - expected) < 0.5F,
                "expected roughly " + expected + " (half of " + without + " removed), lost " + with);
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static ResourceLocation materialId(String name) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, name);
    }

    private static ResourceLocation traitId(String path) {
        return ResourceLocation.fromNamespaceAndPath(Forgeweave.MODID, path);
    }

    private static List<ResourceLocation> split(String pipeSeparated) {
        return Arrays.stream(pipeSeparated.split("\\|")).map(M615MaterialGameTests::traitId).toList();
    }

    /** The 1x1x2 minimum smeltery of {@code SmelteryGameTests} with a Standard Core, formed and empty. */
    private static SmelteryControllerBlockEntity smeltery(GameTestHelper helper) {
        SmelteryGameTests.buildWalls(helper, 1, 1, 2);
        BlockPos corePos = SmelteryGameTests.placeCore(helper, ForgeweaveBlocks.STANDARD_CORE.get());
        SmelteryControllerBlockEntity core = helper.getBlockEntity(corePos);
        helper.assertTrue(core.isFormed(), "expected the test smeltery to form: " + core.lastResult().getString());
        return core;
    }

    /** A tank of {@code fluid}, a faucet on its east side pointing back at it, and a casting table below. */
    private static CastingBlockEntity rig(GameTestHelper helper, Fluid fluid) {
        helper.setBlock(TANK, ForgeweaveBlocks.SEARED_TANK.get());
        helper.<SearedTankBlockEntity>getBlockEntity(TANK).tank()
                .fill(new FluidStack(fluid, SearedTankBlockEntity.CAPACITY), IFluidHandler.FluidAction.EXECUTE);
        helper.setBlock(CASTING, ForgeweaveBlocks.CASTING_TABLE.get());
        helper.setBlock(FAUCET, ForgeweaveBlocks.FAUCET.get().defaultBlockState()
                .setValue(FaucetBlock.FACING, Direction.WEST));
        return helper.getBlockEntity(CASTING);
    }

    private static FaucetBlockEntity faucet(GameTestHelper helper) {
        return helper.getBlockEntity(FAUCET);
    }

    private static void insert(GameTestHelper helper, CastingBlockEntity casting, ItemStack stack) {
        ItemStack expected = stack.copy();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        casting.interact(player, InteractionHand.MAIN_HAND);
        helper.assertTrue(ItemStack.isSameItemSameComponents(casting.input(), expected),
                "expected the right-click to put " + expected + " in, found " + casting.input());
    }

    /** Casts a pickaxe head straight from {@code fluid} and checks the result carries {@code material}. */
    private static void assertPartCasts(GameTestHelper helper, Fluid fluid, String material) {
        CastingBlockEntity table = rig(helper, fluid);
        insert(helper, table, new ItemStack(ForgeweaveItems.CAST_PICKAXE_HEAD.get()));
        faucet(helper).activate();

        helper.succeedWhen(() -> {
            helper.assertTrue(table.output().is(ForgeweaveItems.PART_PICKAXE_HEAD.get()),
                    "expected a finished pickaxe head in the output slot, found " + table.output());
            helper.assertTrue(materialId(material).equals(table.output().get(ForgeweaveDataComponents.MATERIAL.get())),
                    "expected the part to come out in " + material + ", got "
                            + table.output().get(ForgeweaveDataComponents.MATERIAL.get()));
        });
    }

    /** A survival mock player wearing a chestplate of {@code plating} over {@code maille}. */
    private static Player wearing(GameTestHelper helper, String plating, String maille) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemStack piece = ToolAssembly.assembleAt(helper, player, STATION, ForgeweaveBlocks.ARMOR_STATION.get(),
                ToolAssembly.entryOf(ToolConstants.CHESTPLATE), List.of(plating, maille));
        player.setItemSlot(EquipmentSlot.CHEST, piece);
        player.tick();
        return player;
    }

    private static ItemStack worn(Player player) {
        return player.getItemBySlot(EquipmentSlot.CHEST);
    }

    private static float lost(Player player, DamageSource source, float amount) {
        player.setHealth(player.getMaxHealth());
        player.invulnerableTime = 0;
        float before = player.getHealth();
        player.hurt(source, amount);
        return before - player.getHealth();
    }

    private static float lostWithoutTraits(Player player, DamageSource source, float amount) {
        ItemStack piece = worn(player);
        List<ResourceLocation> traits = piece.get(ForgeweaveDataComponents.TRAITS.get());
        piece.remove(ForgeweaveDataComponents.TRAITS.get());
        float result = lost(player, source, amount);
        piece.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        return result;
    }

    private static DamageSource explosion(GameTestHelper helper) {
        return helper.getLevel().damageSources().explosion(null, null);
    }

    private static ItemStack pickaxe(List<ResourceLocation> traits, int durability, float miningSpeed, float attackDamage) {
        dev.gkissel.forgeweave.item.ToolItem toolItem = ForgeweaveItems.TOOL_PICKAXE.get();
        dev.gkissel.forgeweave.tool.ToolStats.Stats stats =
                new dev.gkissel.forgeweave.tool.ToolStats.Stats(durability, miningSpeed, attackDamage);
        Material head = new Material(
                new Material.Head(durability, miningSpeed, attackDamage),
                new Material.Handle(1.0F, 0),
                0,
                net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BLOCK,
                        ResourceLocation.withDefaultNamespace("incorrect_for_stone_tool")),
                new Material.Traits(List.of(), List.of()),
                List.of(),
                net.minecraft.world.item.crafting.Ingredient.of(Items.STICK),
                net.minecraft.network.chat.TextColor.fromRgb(0xFFFFFF));

        ItemStack stack = new ItemStack(toolItem);
        stack.set(ForgeweaveDataComponents.TOOL_STATS.get(), stats);
        stack.set(ForgeweaveDataComponents.TRAITS.get(), traits);
        stack.set(net.minecraft.core.component.DataComponents.TOOL, toolItem.toolComponent(head, stats));
        stack.set(net.minecraft.core.component.DataComponents.MAX_DAMAGE, durability);
        stack.set(net.minecraft.core.component.DataComponents.DAMAGE, 0);
        return stack;
    }
}
